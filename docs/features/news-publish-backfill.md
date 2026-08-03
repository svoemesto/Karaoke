# Backfill флагов публикации готовых песен + kill-switch

> **Status**: active (разовый backfill + постоянный kill-switch на период sync-окна)
> **Feature Key**: news-publish-backfill
> **Last Updated**: 2026-08-03

## Что делает

**Backfill** (`POST /api/utils/backfillpublishflags?target=local&dryRun=false`)
проставляет готовым песням на LOCAL **полный complete-набор** из 7 флагов в
`player_readiness_flags` через обычный `Song.saveToDb()` (а не raw SQL — иначе
разойдётся recordhash):

| JSON-ключ | Было (старая готовая песня) | Стало (после backfill) |
|-----------|----------------------------|------------------------|
| `newsAvailableAnnounced` | `false` | `true` |
| `newsPremiumPublishPending` | `false`/`true` | `false` |
| `newsPremiumTelegramSent` | `false` | `true` |
| `newsPremiumVkSent` | `false` | `true` |
| `premiumAutoPublishState` | `""`/`"RUNNING"`/`"FAILED"` | `"COMPLETE"` |
| `premiumAutoPublishLastError` | любой текст | `""` |
| `premiumAttemptCount` | 0..N | `0` |

**Kill-switch** (`newsAutoPublishKillSwitch` в `KaraokeProperties`, default
`false`) при `true` блокирует `News.createAutoAnnouncement` — единственная
точка создания auto-новостей. Покрывает обе ветки:

- `SongReleaseAnnouncementService.detectAndAnnounceAvailability` — вызывается
  из `karaoke-web/MainController.doChangeRecords` при применении синхронизации.
- `SongReleaseAnnouncementService.checkOnAirWindow` — вызывается из
  `SongReleaseAnnouncementScheduler` (~раз в 5 минут на проде).

**Ручные новости** (`News.createNew`, `source="manual"`) намеренно НЕ
блокируются — администратор может публиковать их во время sync-окна.

## Зачем

До feature 122 (Pass 33) премиум-автопубликация в TG+VK не существовала —
эти 15000 готовых песен были опубликованы по другой схеме (например, прямо
постились вручную при выходе в эфир), и флаги `premiumAutoPublish*` для них
**никогда не выставлялись**.

После развёртывания feature 122 на admin-машине хук `markNewsAvailableIfReady`
(Song.kt:5088) на любом `saveToDb()` уже-готовой песни триггерит переход:

```kotlin
if (!newsPremiumPublishPending &&
    (premiumAutoPublishState.isBlank() || premiumAutoPublishState == "RUNNING") &&
    idTelegramDemo.isEmpty() && idVk.isEmpty() &&
    idStatus == 6L && стемы/картинки/маркеры готовы) {
    newsPremiumPublishPending = true
    premiumAutoPublishState = "RUNNING"   // ← запуск лавины PremiumAutoPublishScheduler
    premiumAttemptCount = 0
    premiumAutoPublishLastError = ""
}
```

За этим `PremiumAutoPublishScheduler` (`@Scheduled(fixedDelay=30s)`) подхватывает
**все** песни с `newsPremiumPublishPending=true` и отправляет их в TG+VK —
получили бы лавину из ~15000 премиум-постов вместо аккуратных публикаций по
выходу в эфир.

Backfill защищает от этого, заранее записывая для уже-опубликованных песен
**завершённое состояние** (`premiumAutoPublishState="COMPLETE"` +
`newsPremium*Sent=true` + `pending=false`), минуя state RUNNING. Следующий
`saveToDb()` таких песен хук `markNewsAvailableIfReady` уже не сработает,
потому что Block 2 требует `premiumAutoPublishState.isBlank() || =="RUNNING"`.

Kill-switch нужен для самого момента **синхронизации** флагов с LOCAL на PROD:
recordhash-механизм применит `playerReadinessFlags` строка-за-строкой, и если
бы на PROD в этот момент ничего не блокировало — `SongReleaseAnnouncementService`
увидел бы «новую готовую песню с `newsAvailableAnnounced=false→true`» и
создал бы auto-новость. Kill-switch включается через `/api/properties/setproperty`
**до** sync, выключается **после**.

## Как работает

### Endpoint

```
POST /api/utils/backfillpublishflags
  ?target=local|remote        # local (default) — единственный рекомендуемый сценарий
  &dryRun=true|false          # dryRun=false (default) — записать; true — только отчёт
```

- Возвращает `true` немедленно (фоновое выполнение, по образцу `doRecalcPlayerReadiness`).
- Тяжёлая операция идёт в `thread { ... }` — основной поток контроллера
  освобождается сразу.
- Прогресс: SSE-тосты каждые 500 обработанных песен (полностью на нём, без
  polling) с заголовком «Backfill флагов публикации» и телом
  «Обработано N / total». Точка 100% пропускается (финальный тост с отчётом
  придёт отдельно).
- Финальный тост: `head = "Backfill флагов публикации (LOCAL)"`,
  `body` — многострочный `BackfillReport.toBody()`.
- При исключении — тост ошибки с типом `error` и текстом `e.message`.

### BackfillReport (data class в `SongReleaseAnnouncementService.kt`)

```kotlin
data class BackfillReport(
    val totalCandidates: Int,
    val fixedNewsAvailableAnnounced: Int,   // сколько переходов false→true случилось
    val fixedPremiumComplete: Int,          // сколько переходов «!=COMPLETE → COMPLETE»
    val alreadyOk: Int,                     // сколько уже были в полном complete до вызова
    val skippedActivePublishing: Int,       // пропущены (tg/vkAutoPublishState in [rendering,publishing])
    val skippedNoMarkers: Int,              // пропущены (isContentReady=false)
    val durationMs: Long,
    val dryRun: Boolean,
)
```

Числа dryRun = числа real run на тех же данных (SC-010) — потому что
счётчики считаются до `saveToDb()`, по значениям полей **до** изменения.
Идемпотентность: повторный запуск → `fixed*=0`, `alreadyOk=N`.

### Фильтр кандидатов

1. SQL: `Song.listHashes(whereText = "WHERE id_status = 6")` —
   дешёвый первичный отсев.
2. In-memory (чанк по 25 песен через `Song.loadListFromDb`):
   - `isContentReady` — `idStatus >= 6 && стемы/картинки/маркеры готовы`.
   - **Не** активная публикация: `telegramAutoPublishState ∉ {rendering, publishing}`
     И `vkAutoPublishState ∉ {rendering, publishing}`. Для таких песен менять
     `newsPremium*Sent` было бы гонкой с уже-идущим рендером.
3. Idempotency-skip: уже полный complete (все 7 флагов) → `alreadyOk++`,
   без `saveToDb()`.

### Kill-switch

```kotlin
fun createAutoAnnouncement(...): News? {
    if (KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")) return null
    // ... обычная логика INSERT в tbl_news
}
```

Включение/снимание (через `/api/properties/setproperty`, без рестарта):

```bash
# Включить (ДО sync):
curl -X POST "https://prod.host/api/properties/setproperty" \
  -d "key=newsAutoPublishKillSwitch&stringValue=true"

# Выполнить sync LOCAL→PROD (через админский UI)

# Снять (ПОСЛЕ sync):
curl -X POST "https://prod.host/api/properties/setproperty" \
  -d "key=newsAutoPublishKillSwitch&stringValue=false"
```

## Инварианты / правила

1. **Idempotентность backfill** — повторный запуск = `alreadyOk` (а не
   дубликаты записей). Полная проверка состояния: `premiumAutoPublishState
   =="COMPLETE" && newsAvailableAnnounced && newsPremiumTelegramSent &&
   newsPremiumVkSent && !newsPremiumPublishPending &&
   premiumAutoPublishLastError.isEmpty() && premiumAttemptCount == 0`.

2. **Запись только через `saveToDb()`** — raw SQL бы разошёлся с формулой
   `recordhash`-триггера, синхронизация LOCAL↔SERVER сломалась бы (constitution II).

3. **Только LOCAL** — `target=remote` технически допустим, но НЕ рекомендуется:
   флаги должны приехать на PROD через sync под защитой kill-switch, иначе
   синхронизация создаст лавину auto-новостей.

4. **Kill-switch не блокирует ручные новости** — `News.createNew` (source="manual")
   не проверяет kill-switch. Это нужно, чтобы администратор мог публиковать
   новости вручную во время sync-окна.

5. **Kill-switch не нужен постоянно** — после завершения backfill+sync он
   снимается (default `false`). Не требуется удалить свойство или код —
   это операционный инструмент, который просто остаётся неактивным.

6. **Если песня НЕ готова** (`isContentReady=false`) — она не затрагивается
   вообще. Она попадёт в backfill автоматически, когда дозреет (через
   стандартный `markNewsAvailableIfReady` Block 2).

7. **Если у песни активная публикация** в TG/VK (`rendering`/`publishing`/`scheduled`)
   на момент backfill — она пропускается и появится в `skippedActivePublishing`.
   Когда публикация завершится и `idTelegramDemo`/`idVk` заполнятся,
   `markNewsAvailableIfReady` не запустит новую (так как `idTelegramDemo`/
   `idVk` уже заполнены → Block 2 skip). Состояние «pending=true, sent=false»
   не теряется — она уже в нужном состоянии.

8. **`markNewsAvailableIfReady` НЕ модифицируется** — после backfill
   `Block 1` skip (newsAvailableAnnounced уже true), `Block 2` skip
   (premiumAutoPublishState=COMPLETE, не blank/RUNNING).

## Известные ловушки

1. **`setReadinessFlag` через MapSerializer(String, Boolean) ОТБРАСЫВАЛ
   string-значения** (баг 02.08.2026, см. KDoc на `Song.playerReadinessFlagsMap`).
   Исправлено парсингом JSON как `JsonObject`, но если кто-то перепишет
   `setReadinessFlag` обратно на `MapSerializer` — все `string`-флаги
   (`telegramAutoPublishState`, `vkAutoPublishState`, `premiumAutoPublishState`,
   `premiumAutoPublishLastError`, `premiumAttemptCount`) тихо исчезнут при
   первой же записи boolean-флага. Бэкфилл смешанных boolean+string флагов
   особенно чувствителен.

2. **Порядок вызовов setter'ов в backfill**: каждый setter парсит JSON,
   модифицирует ОДИН ключ и перекодирует весь JSON-блоб. При установке
   7 флагов подряд это 7× parse + encode. Для 15000 песен = 105000 циклов
   парсинга. Альтернатива (писать напрямую `fields[PLAYER_READINESS_FLAGS]` =
   одной строкой) не используется — выигрыш в скорости < 10%, а риск
   рассинхрона с JSON-форматом слишком высок.

3. **`premiumAutoPublishMaxAttempts` (default 3)** НЕ сбрасывается backfill'ом
   до нуля — backfill ставит `premiumAttemptCount=0` напрямую, но если
   feature 122 переделает этот счётчик (например, на «неудачные попытки за
   сессию»), нужно обновить и backfill. Сейчас `premiumAttemptCount == 0`
   включён в `alreadyComplete`-проверку.

4. **`Message.toString()` не сериализуется в JSON** — `Message.body`
   для backfill передаётся как multi-line plain text (`BackfillReport.toBody()`),
   а не JSON-строка. UI отображает как есть. Если потребуется парсить на
   фронте — нужно добавить JSON-кодирование через `kotlinx.serialization.json.JsonObject`
   (уже импортирован в `Song.kt` для `playerReadinessFlags`).

5. **Контракт параметра `dryRun`** — для эндпоинта: `stringValue=true` → `true`,
   `stringValue=false` → `false`, любое другое → `false` (Kotlin default).
   Spring `@RequestParam(defaultValue = "false")` парсит строку автоматически;
   на UI-кнопке `dryRun` НЕ передаётся (всегда запись — для ad-hoc диагностики
   достаточно тоста с финальным отчётом; для dry-сценария → curl).

## Запуск (разовый, при развёртывании feature 122 на проде)

1. **Включить kill-switch на PROD**:
   ```bash
   curl -X POST "https://prod.host/api/properties/setproperty" \
     -d "key=newsAutoPublishKillSwitch&stringValue=true"
   ```
2. **Запустить backfill на LOCAL** (через UI или curl):
   ```bash
   curl -X POST "http://localhost:8898/api/utils/backfillpublishflags" \
     -d "target=local&dryRun=false"
   ```
   Дождаться SSE-тоста с финальным отчётом.
3. **Запустить sync LOCAL→PROD** (из админского UI, обычным flow).
4. **Снять kill-switch на PROD**:
   ```bash
   curl -X POST "https://prod.host/api/properties/setproperty" \
     -d "key=newsAutoPublishKillSwitch&stringValue=false"
   ```
5. **Проверить**: `tbl_news` на PROD — нет новых строк за последний час с
   `source="auto"` (за исключением AIR-новостей по вновь опубликованным
   песням, у которых date/time наступило в окне `checkOnAirWindow`).

Подробные скриншоты UI-подтверждений и альтернативные пути отката — в
[`specs/124-news-flags-backfill/quickstart.md`](../../specs/124-news-flags-backfill/quickstart.md).

## Ссылки

- [`specs/124-news-flags-backfill/`](../../specs/124-news-flags-backfill/) —
  полная спека: spec.md (3 user stories, 18 FR, 11 SC), plan.md, research.md
  (9 технических решений), data-model.md (полный JSON-инвентарь), contracts/api.md
  (контракт endpoint'а + kill-switch), quickstart.md (пошаговая валидация).
- [`docs/architecture-notes.md`](../architecture-notes.md), **Pass 33** —
  автопремиум-публикация (Pass 122), ради которой появилась эта фича.
- [`docs/architecture-notes.md`](../architecture-notes.md), **Pass 34** —
  запись этой фичи.
- [`docs/features/telegram-auto-publish.md`](./telegram-auto-publish.md) —
  секция «Премиум-публикация».
- [`docs/features/vk-news-auto-publish.md`](./vk-news-auto-publish.md) —
  секция «Премиум-публикация».
