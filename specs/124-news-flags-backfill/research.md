# Research: Backfill флагов публикаций готовых песен

**Branch**: `124-news-flags-backfill` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

## Контекст

Спецификация уже прошла clarify-сессию (см. `spec.md` → `## Clarifications`). Все NEEDS CLARIFICATION разрешены. Этот документ фиксирует технические решения, принятые на основе изучения существующего кода (`Song.kt`, `SongReleaseAnnouncementService.kt`, `ApiController.doBackfillNewsAvailable`, `MainController.doChangeRecords`, `PremiumAutoPublishScheduler`), и best practices для каждого спорного момента.

## Решение 1: Механизм backfill — `saveToDb()` vs raw SQL

**Decision**: Backfill выполняется через `Song.saveToDb()` (существующий метод `Song.kt:5119`), НЕ через прямой SQL UPDATE.

**Rationale**: 
- `saveToDb()` уже корректно персистит изменения `player_readiness_flags` через reflection-diff (`getDiff` → UPDATE только изменившихся полей), порождает SSE-событие (`SseNotification.recordChange`) для webvue3 (требование FR-018), пересчитывает recordhash через существующий БД-триггер (Constitution Principle II).
- Прямой SQL UPDATE обошёл бы SSE-уведомления (нарушение FR-018) и потребовал бы дублирования логики сериализации JSON, уже реализованной в `setReadinessFlag`/`setReadinessStringFlag` (`Song.kt:916`/`961`).
- Существующий образец `SongReleaseAnnouncementService.backfillNewsAvailableFlag` (`SongReleaseAnnouncementService.kt:226`) использует именно `saveToDb()` — сохраняется консистентность паттерна.

**Alternatives considered**:
- Raw SQL `UPDATE tbl_songs SET player_readiness_flags = ? WHERE id_status = 6` одним батчем — быстрее, но: (a) обходит SSE (FR-018), (b) дублирует JSON-сериализацию, (c) не проходит через recordhash-триггер корректно (триггер всё равно сработает, но без diff/SSE), (d) нарушение принципа «один путь записи» (Constitution Principle II — «доступ к БД только через сырой JDBC в `KaraokeDbTable.save()`»).

## Решение 2: Идемпотентность backfill

**Decision**: Backfill проверяет текущее состояние перед записью — меняет только незакрытые флаги. Повторный запуск на уже-исправленных песнях не вызывает `saveToDb()` (diff пуст → early return в `saveToDb`).

**Rationale**:
- `Song.saveToDb()` (`Song.kt:5280-5282`) уже имеет early-return: `val diff = getDiff(this, savedSong); if (diff.isEmpty()) return`. Если backfill устанавливает флаги, которые уже имеют целевые значения, `getDiff` вернёт пустой список → записи нет → идемпотентность.
- Backfill должен: (a) `newsAvailableAnnounced=true` — ставить только если сейчас `false`, (b) premium-флаги в complete — ставить только если хотя бы один из них не в complete-состоянии, (c) не трогать `idTelegramDemo`/`idVk` (факт публикации сохраняется как есть).

**Alternatives considered**:
- Безусловная запись всех флагов всем песням — `getDiff` всё равно отсечёт пустые изменения, но потратит время на сравнение. Для 15000 песен × 6 флагов = 90000 сравнений — приемлемо, но явная проверка делает логику прозрачнее и отчёт точнее.

## Решение 3: Точка kill-switch — `News.createAutoAnnouncement` (единая)

**Decision**: Kill-switch проверяется в `News.createAutoAnnouncement` (`News.kt:337`) — единой точке создания всех auto-новостей. При `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch") == true` — метод возвращает `null` без INSERT в `tbl_news`.

**Rationale**:
- FR-011 требует блокировать обе точки: (a) `detectAndAnnouncementService.detectAndAnnounceAvailability` (sync-точка, premium) и (b) `SongReleaseAnnouncementService.checkOnAirWindow` (scheduler, air). Обе точки вызывают `News.createAutoAnnouncement` для создания новости — единая блокировка в `createAutoAnnouncement` покрывает оба пути одной правкой.
- Альтернатива — проверять kill-switch в каждой из двух точек отдельно — дублирует логику и риск забыть одну из точек при будущих правках.
- `News.createAutoAnnouncement` уже существует и используется ТОЛЬКО для auto-новостей (`source="auto"`). Ручные новости (`NewsController.create` → `News.createNew`, `source="manual"`) не затрагиваются — админ может создавать ручные новости во время kill-switch.
- `KaraokeProperties.getBoolean` читается из base64-настроек через `/api/properties/setproperty` — без рестарта контейнера (Constitution Principle VIII — kill-switch не секрет, управляется админом).

**Alternatives considered**:
- Kill-switch в `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability` + в `checkOnAirWindow` отдельно — дублирование, риск рассинхрона.
- Kill-switch в `MainController.doChangeRecords` перед циклом `forEach` — блокирует только sync-путь, не покрывает `checkOnAirWindow` scheduler'а.
- Kill-switch как Spring-`@ConditionalOnProperty` — требует рестарта контейнера, неприемлемо (админ включает/снимает в окне sync).

## Решение 4: Порядок флагов в complete-наборе

**Decision**: Для каждой готовой песни (`id_status=6` + непустые `source_markers`), не в активной публикации, backfill выставляет:
- `newsAvailableAnnounced = true`
- `newsPremiumPublishPending = false`
- `newsPremiumTelegramSent = true`
- `newsPremiumVkSent = true`
- `premiumAutoPublishState = "COMPLETE"`
- `premiumAutoPublishLastError = ""`
- `premiumAttemptCount = 0`
- НЕ трогает: `idTelegramDemo`, `idVk`, `telegramAutoPublishState`, `vkAutoPublishState` (факт/состояние публикации сохраняется как есть — если публикация была, id остаётся; если не было, остаётся пустым, но каналы помечены как «отправленные» через `newsPremiumTelegramSent`/`newsPremiumVkSent`).

**Rationale**:
- `markNewsAvailableIfReady` (`Song.kt:5101-5116`) блокирует повторную установку `newsPremiumPublishPending=true` условием `premiumAutoPublishState.isBlank() || premiumAutoPublishState == "RUNNING"`. После backfill `premiumAutoPublishState="COMPLETE"` → условие ложно → повтор не срабатывает → рецидив невозможен (см. spec FR-012, FR-016).
- `PremiumAutoPublishScheduler.loadPendingIds` (`PremiumAutoPublishScheduler.kt:193`) ищет песни по `"newsPremiumPublishPending":true` в JSON. После backfill этого ключа нет у готовых песен → scheduler их не находит → автопубликация не активируется.
- `newsPremiumTelegramSent=true` / `newsPremiumVkSent=true` — если впоследствии админ вручную попытается опубликовать через `PremiumAutoPublishScheduler.processSong` (что не должно случиться, т.к. `newsPremiumPublishPending=false`), `closeIfBothChannelsDone` сразу закроет задачу.

**Alternatives considered**:
- Не трогать `newsPremiumTelegramSent`/`newsPremiumVkSent`, ставить только `premiumAutoPublishState=COMPLETE` — но тогда `closeIfBothChannelsDone` в `PremiumAutoPublishScheduler` не сработает (он проверяет `idTelegramDemo.isNotEmpty() || newsPremiumTelegramSent`), и если `idTelegramDemo` пуст, канал считается незакрытым — потенциальный рецидив через scheduler. Согласованный complete-набор надёжнее.
- Переводить `premiumAutoPublishState=FAILED` песни в `COMPLETE` — да, по spec edge case «что если `premiumAutoPublishState=FAILED`» — backfill переводит в COMPLETE, не оставляя в FAILED (иначе scheduler будет бесконечно пытаться повторить после сброса `newsPremiumPublishPending`).

## Решение 5: Пропуск активных публикаций

**Decision**: Backfill пропускает песни, у которых `telegramAutoPublishState in ("rendering", "publishing")` ИЛИ `vkAutoPublishState in ("rendering", "publishing")`. Эти песни помечаются в отчёте как `skippedActivePublishing`.

**Rationale**:
- `telegramAutoPublishState`/`vkAutoPublishState` в rendering/publishing означают, что прямо сейчас идёт рендер DEMO-видео или отправка поста. Сброс `newsPremiumPublishPending=false` в этот момент сломал бы `PremiumAutoPublishScheduler.processSong` (он проверяет `!song.newsPremiumPublishPending` → return на строке 84, но уже запущенный рендер может завершиться и попытаться записать результат в флаги, которые мы только что сбросили).
- Это редкий случай (по spec Assumptions — 0-5 шт.), админ видит их в отчёте и дождётся завершения перед повторным backfill.

**Alternatives considered**:
- Блокировать все песни с непустым `telegramAutoPublishState`/`vkAutoPublishState` — слишком широко, блокирует SCHEDULED/CANCELLED/SEND_FAILED, которые как раз нужно привести в COMPLETE.

## Решение 6: Пропуск песен без source_markers

**Decision**: Backfill пропускает песни с `id_status >= 6`, но пустыми `source_markers` (`sourceMarkersList.isEmpty()`). Помечаются в отчёте как `skippedNoMarkers`.

**Rationale**:
- `isContentReady` (`Song.kt:1091-1098`) требует `sourceMarkersList.isNotEmpty()`. Песня со статусом 6, но без маркеров — некорректно-готовая, аномалия. Backfill НЕ должен маскировать эту проблему (FR-008, spec edge case).
- `markNewsAvailableIfReady` (`Song.kt:5088-5098`) тоже проверяет `sourceMarkersList.isNotEmpty()` — для таких песен `newsAvailableAnnounced` никогда не установится в true обычным путём. Если backfill насильно поставит true, это создаст несогласованность с `isContentReady`.

**Alternatives considered**:
- Выставлять флаги всем `id_status=6` без проверки маркеров — маскирует проблему, нарушает FR-008.

## Решение 7: Формат отчёта (dry-run и real)

**Decision**: Отчёт — JSON-объект с числами по категориям, возвращается в финальном SSE-тосте:

```json
{
  "totalCandidates": 15000,
  "fixedNewsAvailableAnnounced": 12000,
  "fixedPremiumComplete": 11000,
  "alreadyOk": 3000,
  "skippedActivePublishing": 2,
  "skippedNoMarkers": 5,
  "durationMs": 480000,
  "dryRun": false
}
```

Dry-run возвращает тот же JSON с `dryRun=true` и `durationMs=0` (без записи), числа `fixed*` показывают сколько БЫЛО БЫ исправлено.

**Rationale**:
- Финальный SSE-тост (по образцу `doBackfillNewsAvailable` → `SseNotification.message`) с текстом — неудобен для парсинга; JSON-тело в `Message.body` позволяет webvue3 красиво отрисовать таблицу.
- Прогресс по чанкам — отдельные SSE-тосты каждые ~500 песен: `"Backfill publish flags: обработано 500/15000..."` (текстовый, как в существующих прогресс-тостах проекта).

**Alternatives considered**:
- Только финальный отчёт без пошагового прогресса — для 15 минут слишком долго без обратной связи, админ может подумать что зависло.
- SSE-stream с JSON-событиями `{"type":"progress","processed":500}` — переусложнение для разовой операции; текстовые тосты каждые 500 песен достаточно.

## Решение 8: Кнопка в webvue3 — где разместить

**Decision**: Кнопка «Backfill флагов публикаций» + переключатель «Dry run» размещаются в webvue3 на странице, где уже есть `doBackfillNewsAvailable` (по образцу — рядом с «Recalc Player Readiness» / «Backfill флага «доступна»»). Точное место — определится на фазе tasks при изучении UI; предположительно Dashboard/Tools-страница в `webvue3/src/components/`.

**Rationale**:
- AGENTS.md «Following conventions» —模仿 существующего паттерна. `doBackfillNewsAvailable` уже вызывается кнопкой из webvue3 — новый backfill размещается рядом, тот же UI-паттерн (кнопка + подтверждение + SSE-тост результата).
- Без новой страницы/роута — кнопка в существующем наборе «утилит».

**Alternatives considered**:
- Отдельная страница «Backfill» — переусложнение для разовой операции.
- CLI/SQL-скрипт — отклонено на clarify (Q2 → A: HTTP-endpoint + SSE).

## Решение 9: Валидация `player_readiness_flags` JSON

**Decision**: Backfill использует `setReadinessFlag`/`setReadinessStringFlag` (существующие методы `Song.kt:916`/`961`), которые уже корректно обрабатывают пустой/невалидный JSON (трактуют как `{}` и пересоздают валидный JSON). Дополнительной валидации не требуется (FR-017).

**Rationale**:
- `setReadinessFlag` (`Song.kt:933-934`): `catch (_: Exception) { mutableMapOf() }` — при невалидном JSON начинает с пустой map и записывает валидный JSON.
- `setReadinessStringFlag` (`Song.kt:973-974`): тот же паттерн.
- Backfill устанавливает флаги через эти сеттеры → JSON всегда валиден после backfill, даже если был пуст/битым до.

**Alternatives considered**:
- Отдельная валидация JSON перед backfill — дублирует логику, уже реализованную в сеттерах.

## Итог

Все 9 решений приняты на основе существующих паттернов кода (`Song.kt`, `SongReleaseAnnouncementService.kt`, `ApiController.doBackfillNewsAvailable`, `News.createAutoAnnouncement`) и Constitution Check (Phase 0 gate passed). NEEDS CLARIFICATION не осталось. Переход к Phase 1: design & contracts.