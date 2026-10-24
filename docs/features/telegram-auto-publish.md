# Автопубликация в Telegram-канал

> **Status**: active
> **Feature Key**: telegram-auto-publish
> **Last Updated**: 2026-10-24
> **Spec**: [`specs/366-pkg-race-publish/report.md`](../../specs/366-pkg-race-publish/report.md)
> **Source**: OpenProject #366 — пакет `PKG-RACE-PUBLISH` (BR-062, BR-138) аудита #336

## Что делает

Публикует демо-видео песни в Telegram-канал: по расписанию (`date`/`time` песни),
по завершении рендера демо-MP4 и вручную кнопкой «Опубликовать сейчас» в админке.
Ведёт состояние попытки (`scheduled` / `rendering` / `publishing` / `published` /
`send_failed`) в JSON-поле песни и сохраняет `message_id` в `id_telegram_demo`.

## Зачем

Публичный Telegram-канал — канал дистрибуции песен и трафика на сайт. Ручной постинг
не масштабируется, а плановый бот должен переживать отсутствие готового файла
(сначала рендер) и временные ошибки Telegram (ретраи). При этом дубль поста в
публичном канале виден подписчикам и убирается только вручную — поэтому публикация
обязана быть идемпотентной.

## Как работает

- `TelegramAutoPublishScheduler.tick()` (каждые 60 с) — фаза `resumeRenderingSongs()`
  (песни в `rendering`, чей `RENDER_MP4_DEMO` завершён → `onRenderCompleted`) и фаза
  `publishScheduledSongs()` (песни в окне `telegramAutoPublishWindowMinutes` →
  `publishToTelegram`).
- `TelegramAutoPublishService.publishToTelegram(song, …)` — проверка идемпотентности,
  даты, `isContentReady`; нет демо-файла или он больше лимита →
  `startRenderAndReturn` (ставит `RENDER_MP4_DEMO`, пишет `rendering`); иначе
  `publishFile`.
- `publishFile` — **атомарный захват** (`acquirePublishClaim` →
  `JdbcTelegramPublishClaimStore`: `SELECT … FOR NO KEY UPDATE` по строке
  `tbl_songs`, свежее чтение `id_telegram_demo` и `player_readiness_flags`,
  точечный `UPDATE player_readiness_flags`, `commit`) → `sendVideo` с ретраями
  FR-010 → запись `published` + `idTelegramDemo` через `Song.saveToDbLocked`.
- Ручной endpoint `POST /api/song/publishToTelegramNow` идёт тем же путём сервиса,
  поэтому захват защищает и пару «кнопка + scheduler».
- Для `PREMIUM` (`publishPremiumTelegram`, `PremiumAutoPublishScheduler`)
  `persistMessageId=false`: `message_id` не пишется в `id_telegram_demo`, чтобы слот
  заняла будущая AIR-публикация; факт отправки отмечается `newsPremiumTelegramSent`.

## Инварианты

- **MUST**: ровно один поток отправляет видео по песне — захват `publishing`
  выполняется в транзакции БД до `sendVideo`; при `Busy`/`Failed`/`NotFound`
  сетевого вызова нет (fail-closed) ([TelegramPublishClaim.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramPublishClaim.kt)).
- **MUST**: непустой `id_telegram_demo` — терминальное «опубликовано»; повторная
  публикация только после ручной очистки поля.
- **MUST**: состояние публикации живёт в `player_readiness_flags` (`text`-JSON),
  отдельной колонки нет; новые состояния добавляются кодом без миграции
  ([26_player_readiness_flags.sql](../../deploy/karaoke-db/26_player_readiness_flags.sql)).
- **MUST**: захват `publishing` считается живым не дольше
  `TelegramPublishClaimPolicy.CLAIM_TTL_MS` (15 мин) — иначе упавший процесс
  заблокировал бы песню навсегда.
- **MUST**: `TelegramApiClient.sendVideo` — до 3 попыток с backoff; non-retryable
  коды (400/403/404) не повторяются.

## Известные ловушки

- **Гонка check-then-act (BR-062/BR-138, #366)**: до правки проверка
  `idTelegramDemo`/`publishing` читала загруженный объект, а `publishing` писался
  уже после проверки; HTTP-поток и scheduler одновременно проходили проверку и
  отправляли два поста. Комментарий «сервер проверяет idTelegramDemo для защиты от
  гонок» был неверным. Регресс — `TelegramPublishClaimRaceTest` (8 потоков → один
  победитель).
- **`saveToDb()` вместо `saveToDbLocked()`** в ручных premium-endpoint'ах
  (`ApiController.kt`): объект загружен до многоминутного `sendVideo`, diff пишет
  устаревшие поля и может затереть `id_telegram_demo`, полученный параллельно
  (P-09-16 аудита #336, вне этого пакета).
- **Потеря `message_id` при `saveToDbLocked() == false`** (lock timeout): состояние
  остаётся `publishing`, следующий тик после TTL отправит видео повторно (P-23-02,
  вне этого пакета).
- **`persistMessageId=false` для PREMIUM**: `message_id` не сохраняется, поэтому
  идемпотентность premium-отправки держится только на `newsPremiumTelegramSent`.

## Ссылки на ключевые классы/файлы

- [`TelegramAutoPublishService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt) — оркестратор публикации, `publishToTelegram` / `onRenderCompleted` / `publishFile`.
- [`TelegramPublishClaim.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramPublishClaim.kt) — атомарный захват: `TelegramPublishClaimPolicy`, `JdbcTelegramPublishClaimStore`.
- [`TelegramAutoPublishScheduler.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt) — плановый тик.
- [`TelegramApiClient.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt) — `sendVideo`, ретраи.
- [`TelegramAutoPublishState.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishState.kt) — коды состояний.
- [`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — `POST /api/song/publishToTelegramNow`, `/publishPremiumTelegram`.
- [`TelegramPublishClaimRaceTest.kt`](../../karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/TelegramPublishClaimRaceTest.kt) — регрессия на гонку.
- [`publishing-services.md`](../../knowledge/domains/publishing/components/publishing-services.md) — SSoT домена публикации.
