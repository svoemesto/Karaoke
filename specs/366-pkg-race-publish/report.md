# #366 — PKG-RACE-PUBLISH: гонка публикации (BR-062, BR-138)

> **Ветка**: `589-pkg-race-publish` (worktree `/home/nsa/Karaoke/.worktrees/waveD-race-publish`, база `origin/master` @ `07126462`)
> **Пакет**: `PKG-RACE-PUBLISH` бэклога аудита #336, волна D
> **Записи**: BR-062 (`publish-check-then-act:api`), BR-138 (`publish-no-atomic-claim:telegram`)
> **Статус**: код + тесты + документы готовы, проверки зелёные

## 1. Гонка: где, кто, окно, чем опасно

### BR-062 — `ApiController.kt:6938–6980` (`POST /api/song/publishToTelegramNow`)

**Два участника** (оба в одном процессе `karaoke-app`):

1. HTTP-поток ручной кнопки: `publishToTelegramNow` (`ApiController.kt:6968` — `Song.loadFromDbById`, `:6983` — проверка `idTelegramDemo`) → `TelegramAutoPublishService.publishToTelegram` (`:6993`).
2. Поток планировщика: `TelegramAutoPublishScheduler.tick` (`TelegramAutoPublishScheduler.kt:53`, `fixedDelay = 60_000`) → `publishScheduledSongs` (`:102–121`) → тот же `publishToTelegram`.

Дополнительный участник — post-hook воркера `thread { onRenderCompleted(...) }` (`KaraokeProcessWorker.kt:451,465`), а для premium-пути — `PremiumAutoPublishScheduler` (`PremiumAutoPublishScheduler.kt:225`).

**Окно**: от чтения `idTelegramDemo` из загруженного объекта (`ApiController.kt:6983` и повторно `TelegramAutoPublishService.kt:75`) до первого `commit` состояния `publishing` (было — `TelegramAutoPublishService.kt:291–295`, `saveToDbLocked`). Внутри окна — весь `sendVideo` с ретраями FR-010: до 3 попыток × 60 с таймаута + паузы 30 с/2 мин/5 мин, то есть **минуты**. Двойной клик по кнопке даёт тот же эффект без всякого планировщика.

**Чем опасно / какое состояние остаётся**: оба потока видят пустой `id_telegram_demo`, оба вызывают `sendVideo` → **два одинаковых поста в публичном канале**. Для AIR второй пост перезатирает `id_telegram_demo` первого (в слоте остаётся один id из двух); для PREMIUM (`persistMessageId=false`) `message_id` не сохраняется вовсе, поэтому следующий тик после снятия/устаревания состояния снова отправит видео. Откат — только ручное удаление поста в Telegram. Комментарий в коде «сервер всё равно проверяет idTelegramDemo для защиты от гонок» (`ApiController.kt:6957`) был неверным.

### BR-138 — `TelegramAutoPublishService.kt:74–80` (`publishToTelegram`)

Тот же дефект на уровень ниже: «идемпотентность» — это чтение `song.idTelegramDemo` / `song.newsPremiumTelegramSent` из **загруженного объекта** (`:75`, `:84–91`), а состояние «отправка уже идёт» (`PUBLISHING`) писалось перед `sendVideo` (`:291–295`), но **никем не читалось как guard**: ни `publishToTelegram`, ни `publishFile`. Любой из трёх вызывающих (scheduler, HTTP-кнопка, post-render hook) мог загрузить песню до записи `PUBLISHING` и пройти проверку.

**Окно** — то же (минуты, весь `sendVideo` с ретраями). **Состояние после гонки** — дубль поста; при premium — ещё и отсутствие `message_id` в БД, то есть повтор на следующем тике.

## 2. Что сделано

### Выбранный механизм: атомарный захват строки в БД (`SELECT … FOR NO KEY UPDATE` + точечный UPDATE)

Новый файл `karaoke-app/.../services/TelegramPublishClaim.kt`:

- `TelegramPublishClaimPolicy.decide(idTelegramDemo, state, lastAttemptAt, nowMs)` — чистая политика: `AlreadyPublished` (свежий id), `Busy` (состояние `publishing` не старше `CLAIM_TTL_MS = 15 мин`), иначе `Claimed`;
- `JdbcTelegramPublishClaimStore.claim(songId, nowMs, nowIso)` — короткая транзакция на ThreadLocal-соединении (`ADR-0001`, ручные commit/rollback): `SET LOCAL lock_timeout` (как в `Song.saveToDbLocked`), `SELECT id … FOR NO KEY UPDATE` через `Song.loadFromDbByIdForUpdate` (примитив specs/299), свежее чтение `id_telegram_demo` и `player_readiness_flags`, при `Claimed` — точечный `UPDATE tbl_songs SET player_readiness_flags = ? WHERE id = ?` и `commit`. `sendVideo` идёт уже **после** commit, поэтому блокировка строки не удерживается минутами;
- `Busy`/`AlreadyPublished`/`NotFound`/`Failed` возвращаются без сетевого вызова (**fail-closed**).

`TelegramAutoPublishService.publishFile` вызывает `acquirePublishClaim(song)` непосредственно перед `sendVideo`; при занятом захвате возвращает `PUBLISHING` (UI «Публикуется»), при уже опубликованной — `PUBLISHED` со свежим `messageId`, при ошибке захвата — `SEND_FAILED`. Прежняя запись `publishing` через `saveToDbLocked()` перед отправкой удалена — её заменил захват; состояние в памяти синхронизируется с тем, что зафиксировано в БД.

**Почему не `UPDATE … WHERE state = …`**: состояние публикации хранится не отдельной колонкой, а строковым ключом в JSON-блобе `player_readiness_flags` (`text`, `deploy/karaoke-db/26_player_readiness_flags.sql`); условный UPDATE потребовал бы `jsonb`-приведения и разбора ISO-таймстампа в SQL. Блокировка строки даёт ту же атомарность, переиспользует уже проверенный примитив и позволяет читать состояние штатными аксессорами `Song`. **Почему TTL**: `publishing`, оставшийся после падения процесса посреди `sendVideo`, иначе заблокировал бы песню навсегда; 15 мин больше максимального времени отправки с ретраями. **Почему fail-closed**: при SQL-ошибке лучше не отправить и показать ошибку, чем отправить второй пост.

Новых зависимостей нет, схема БД не меняется.

### Файлы

| Файл | Изменение |
|---|---|
| `karaoke-app/.../services/TelegramPublishClaim.kt` | **новый**: политика + JDBC-захват + шов `TelegramPublishClaimStore` |
| `karaoke-app/.../services/TelegramAutoPublishService.kt` | `acquirePublishClaim` перед `sendVideo`; прежняя запись `publishing`/`saveToDbLocked` до отправки заменена захватом; `iso8601(epochMs)` |
| `karaoke-app/.../controllers/ApiController.kt` | комментарий у `publishToTelegramNow`: проверка оптимистичная, защита — захват в сервисе (поведение endpoint'а не менялось) |
| `karaoke-app/src/test/.../services/TelegramPublishClaimRaceTest.kt` | **новый**: регрессия «до/после» |
| `knowledge/domains/publishing/components/publishing-services.md` | SSoT: атомарный захват, TTL, fail-closed, новый компонент |
| `docs/features/telegram-auto-publish.md` | **новый** per-feature документ (FR-009), 6 секций контракта |

## 3. Регрессионный тест «до/после»

Тест воспроизводит гонку через шов `TelegramAutoPublishService.claimStore`: 8 потоков одновременно вызывают production-функцию `acquirePublishClaim` для одной песни; in-memory хранилище с тем же контрактом, что у JDBC-захвата, сериализует «чтение строки + запись `publishing`» per-song lock'ом (аналог `FOR NO KEY UPDATE`). Решение принимает production-политика.

```bash
DISPLAY= GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  :karaoke-app:test --tests "com.svoemesto.karaokeapp.services.TelegramPublishClaimRaceTest"
```

**До** (временно восстановлена старая проверка по загруженному объекту вместо захвата; затем код возвращён):

```
TelegramPublishClaimRaceTest > acquirePublishClaim допускает ровно одного из конкурирующих участников() FAILED
    org.opentest4j.AssertionFailedError: в sendVideo должен уйти ровно один поток, победителей=8 ==> expected: <1> but was: <8>
5 tests completed, 3 failed
BUILD FAILED
```

**После** (текущий код):

```
> Task :karaoke-app:test
BUILD SUCCESSFUL in 28s
```

Тест также фиксирует окно гонки отдельным кейсом: `старый check-then-act по снапшоту пропускает обоих` — обе операции читают пустое состояние до записи и обе получают `Claimed` (2 «победителя»), что и было поведением BR-062/BR-138; после правки атомарный захват оставляет одного.

## 4. Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:test` (с `DISPLAY=`) | **BUILD SUCCESSFUL**, 469 тестов |
| `:karaoke-app:test` без `DISPLAY=` | 4 падения в `VkPreviewWarmupClientTest` — `java.awt.AWTError: Can't connect to X11 window server using ':0'`; к правке отношения не имеют (в окружении выставлен `DISPLAY=:0` без X-сервера), с `DISPLAY=` проходят |
| `:karaoke-web:test` | **BUILD SUCCESSFUL** |
| `ktlintCheck` | **BUILD SUCCESSFUL** |
| `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| `python3 tools/check-doc-references.py` | после `git add` новых файлов — без проблем (до этого новые `.kt` не видны `git ls-files`) |
| `bash tools/check-subagent-isolation.sh` | exit 0; предупреждения только про master-мерж-коммиты, общие с чужими PR-ветками (`40e5b92c`, `d94185ee`, …), — ожидаемо для ветки от master, работа идёт в отдельном worktree |

Команды запускались из worktree с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (общий кэш главного checkout'а), `--max-workers=2`, JVM `-Xmx1536m -XX:MaxMetaspaceSize=512m` — как предписано для падающего под нагрузкой демона.

## 5. Что осталось за владельцем (вне BR-062/BR-138 и этого пакета)

- **BR-063** (`#373`, P-09-16): premium-эндпоинты `ApiController.kt:7042–7108` сохраняют песню через `saveToDb()` после многоминутного `sendVideo`; устаревший объект может затереть `id_telegram_demo`, записанный параллельной AIR-публикацией. Лечится `saveToDbLocked()`/точечным UPDATE — отдельная задача.
- **BR-139** (`#374`, P-23-02): `saveToDbLocked()` возвращает `Boolean`, результат игнорируется (`TelegramAutoPublishService.kt:301–315`); при `lock_timeout` успешный `message_id` теряется, состояние остаётся `publishing`, после TTL — повторный пост.
- **VK-ветки** `publishToVkNow` / `publishPremiumVk` (`ApiController.kt:6987+`, `VkAutoPublishService`): в реестре BR-062 зарегистрирован диапазон 6938–6980 (только TG), VK-путь сохраняет собственный check-then-act. Требует отдельной записи/задачи.
- Дубль рендера `RENDER_MP4_DEMO` при одновременном ручном и плановом триггере: захват стоит только перед `sendVideo` (как в ТЗ BR-062/BR-138); `startRenderAndReturn` по-прежнему не захватывает `rendering`. Дубль поста это не даёт (ранний выход по отсутствию файла), но задачу рендера может поставить дважды, если `KaraokeProcess.createProcess` не дедуплицирует.
- Схема/миграция БД не менялась; поведение `publishToTelegramNow` для уже опубликованной песни (HTTP 200, `success:false`) сохранено.

## 6. Инварианты, которые теперь держатся

1. Ровно один поток отправляет видео по песне: захват `publishing` фиксируется в БД до `sendVideo`; конкуренты получают `Busy` без сети.
2. `id_telegram_demo` — терминальный признак публикации; при гонке второй поток получает `AlreadyPublished` со свежим id из-под блокировки, а не затирает его пустым значением из устаревшего объекта.
3. Захват не держится дольше короткой транзакции; сетевой вызов идёт без блокировки строки.
4. `publishing` без результата живёт не дольше 15 мин; после этого публикация может быть перехвачена (защита от вечной блокировки после падения процесса).
5. Ошибка захвата (нет соединения / lock timeout) не приводит к повторной отправке — только к `SEND_FAILED`.
