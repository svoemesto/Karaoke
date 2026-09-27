# Component: schedulers (auto-publish, sync, retention, cleanup)

> **Домен**: [processing](../domain.md)
> **Компонента**: детальный каталог всех `@Scheduled` фоновых
> задач проекта (12 штук, не считая `StatsCacheScheduler`).


## Ответственность | Responsibility


детальный каталог всех `@Scheduled` фоновых задач проекта (12 штук, не считая `StatsCacheScheduler`).

## Назначение

Это **все фоновые процессы**, которые Spring запускает по
расписанию. Каждый scheduler:

- Имеет `@Scheduled(cron|fixedDelay|fixedRate)` триггер.
- Работает **только пока запущен процесс** (karaoke-app или
  karaoke-web). При остановке задачи теряются.
- Логирует через SLF4J (некоторые — через `println`, см. gaps).
- Не имеет cluster lock (single-instance only, см. ADR `local-0003`).

**NB**: `AutoOneClickSyncScheduler` описан в
[two-db-sync.md](two-db-sync.md), `StatsCacheScheduler` — в
[caching/domain.md](../../caching/domain.md). Здесь — 12 остальных.

## Интерфейсы и Контракты | Interfaces and Contracts

Точка входа каждого scheduler'а — единственный публичный `@Scheduled`-метод
(остальные методы приватные вспомогательные). Spring вызывает их сам; ручного
API у планировщиков нет.

| Scheduler | Точка входа | Триггер | Ключевой вызов |
|---|---|---|---|
| `TelegramAutoPublishScheduler` | `tick()` | `fixedDelay = 60_000L` | `TelegramAutoPublishService.publishToTelegram(song, allowPastDate = true)` |
| `VkAutoPublishScheduler` | `tick()` | `fixedDelay = 60_000L` | `VkAutoPublishService.publishToVk(song, PublicationType.AIR)` |
| `SponsrSyncScheduler` | `run()` | `fixedRate = 12 * 3600_000L` | `SponsrSyncService.syncViaScraping(db, KSS_APP, SAC_APP)` |
| `PremiumAutoPublishScheduler` | `tick()` | `fixedDelay = 30_000L` | `TelegramAutoPublishService` / `VkAutoPublishService` (публикация и резюм рендера, `PublicationType.PREMIUM`) |
| `VkIdTokenRefreshScheduler` | `refreshIfNeeded()` | `cron = "0 0 * * * *"` | `VkApiClient.refreshVkIdAccessToken()` |
| `StemJobPollScheduler` | `pollWaiting()` | `fixedDelay = 45_000L` | `StemJob.loadWaiting(...)` → `takeJob` |
| `StemJobPollScheduler` | `cleanup()` | `fixedDelay = 5 * 60_000L` | `StemJob.loadPendingCleanup(...)` |
| `EventsRetentionScheduler` | `cleanup()` | `cron = "0 0 3 * * *"` | `DELETE FROM tbl_events WHERE last_update < ?` |
| `ShareLinkSweeper` | `sweep()` | `fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000"` | `SongShareLinkService` (expired/lease/premium/unavailable) |
| `SongReleaseAnnouncementScheduler` | `checkOnAir()` | `fixedDelay = 5 * 60_000L` | `SongReleaseAnnouncementService` |
| `StemJobTempCleanupScheduler` | `cleanup()` | `fixedDelay = 30 * 60_000L` | удаление файлов старше `STALE_HOURS = 2` в `stemjobs.temp-dir` |
| `SubscriptionRenewalScheduler` | `renewExpiringSiteSubscriptions()` | `cron = "0 0 3 * * *"` | `PaymentService.chargeRecurring(...)` |

## Сводная таблица

| # | Scheduler | Процесс | Триггер | Что делает |
|---|---|---|---|---|
| 1 | `TelegramAutoPublishScheduler` | karaoke-app | каждые 60s (fixedDelay) | Авто-постинг новостей о новых песнях в Telegram-канал |
| 2 | `VkAutoPublishScheduler` | karaoke-app | каждые 60s (fixedDelay) | Авто-постинг новостей в VK |
| 3 | `SponsrSyncScheduler` | karaoke-app | каждые 12h (fixedRate) | Sync контента karaoke с Sponsr (scraping) |
| 4 | `PremiumAutoPublishScheduler` | karaoke-app | каждые 30s (fixedDelay) | Авто-публикация premium-контента (Boosty и т.п.) |
| 5 | `VkIdTokenRefreshScheduler` | karaoke-app | каждый час (`0 0 * * * *`) | Обновление VK ID access_token |
| 6 | `StemJobPollScheduler.pollWaiting()` | karaoke-app | каждые 45s (fixedDelay) | Polling WAITING-StemJob из karaoke-web |
| 7 | `StemJobPollScheduler.cleanup()` | karaoke-app | каждые 5min (fixedDelay) | Cleanup expired/delete-requested StemJob |
| 8 | `EventsRetentionScheduler` | karaoke-web | каждый день 03:00 UTC (`0 0 3 * * *`) | Удаление старых `tbl_events` |
| 9 | `ShareLinkSweeper` | karaoke-web | каждые 60s (default, configurable) | Sweep expired share-ссылок |
| 10 | `SongReleaseAnnouncementScheduler` | karaoke-web | каждые 5min (fixedDelay) | Анонсы релизов песен |
| 11 | `StemJobTempCleanupScheduler` | karaoke-web | каждые 30min (fixedDelay) | Очистка temp-файлов загрузок StemJob |
| 12 | `SubscriptionRenewalScheduler` | karaoke-web | каждый день 03:00 UTC (`0 0 3 * * *`) | Авто-продление подписок |

---

## 1. `TelegramAutoPublishScheduler`

**Файл**: `karaoke-app/.../services/TelegramAutoPublishScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)` —
каждые 60 секунд.

**Что делает**: авто-постинг новостей о новых песнях в Telegram-канал.

**Алгоритм** (по KDoc):

- Каждый тик — проверка окон `datePublicate` (по образцу
  `SongReleaseAnnouncementScheduler`).
- `fixedDelay` (не `fixedRate`) — задачи не накапливаются.
- Window читается из `KaraokeProperties` (через `@Scheduled` SpEL
  не работает, поэтому fixedDelay=60s, а внутри — ручная проверка
  окна).

**Использует**: `TelegramAutoPublishService` (`TelegramApiClient` — внутри
сервиса, не в самом scheduler'е).

**Ловушка**: только пока работает karaoke-app. Если процесс не
запущен — задачи теряются (нет persistence queue).

---

## 2. `VkAutoPublishScheduler`

**Файл**: `karaoke-app/.../services/VkAutoPublishScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)`
— каждые 60 секунд.

**Что делает**: авто-постинг новостей в VK.

**Алгоритм** (по KDoc):

- Тик каждые 60с, проверка новостей `category="air"` + `publish_at <= now()`.
- Идемпотентность для `air`-новостей без `song_id` — через
  in-memory `Set` в `VkAutoPublishScheduler` (см.
  [catalog/components/entities-catalog.md](../../catalog/components/entities-catalog.md#news)).

**Использует**: `VkAutoPublishService`, `VkApiClient` (пост без видео и
rate-limit); `VkPhotoUploadClient`/`VkTemplateService` — внутри
`VkAutoPublishService`.

---

## 3. `SponsrSyncScheduler`

**Файл**: `karaoke-app/.../services/SponsrSyncScheduler.kt`.

**Триггер**: `@Scheduled(fixedRate = 12 * 3600_000L, initialDelay = 5 * 60_000L)`
— каждые 12 часов.

**Что делает**: периодическая синхронизация контента karaoke с
Sponsr через **scraping** (без API).

**Алгоритм** (по KDoc):

- Запускает `SponsrSyncService.syncViaScraping()`.
- Работает только пока запущен karaoke-app (тот же инвариант, что и
  у `checkLastAlbum` / Yandex-музыки — это **desktop-only**
  background).
- `fixedRate` (не fixedDelay) — задачи стартуют **строго по
  расписанию**, даже если предыдущая не завершилась.

**Ловушка**: scraping медленный и хрупкий (сетевые ошибки,
изменения HTML-разметки). Не критично для проекта, но создаёт
flaky-тесты.

---

## 4. `PremiumAutoPublishScheduler`

**Файл**: `karaoke-app/.../services/PremiumAutoPublishScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)`
— каждые 30 секунд (дефолт).

**Что делает**: авто-публикация premium-контента (Boosty и т.п.).

**Алгоритм** (по KDoc):

- Каждые 30с — проверка песен с `PublicationType` для premium.
- `fixedDelay` через KaraokeProperties — Spring SpEL `${...}` не
  работает для динамических интервалов (см. AutoOneClickSyncScheduler
  pattern).

**Использует**: `TelegramAutoPublishService` и `VkAutoPublishService`
(отдельного `PremiumAutoPublishService` в коде нет).

---

## 5. `VkIdTokenRefreshScheduler`

**Файл**: `karaoke-app/.../services/VkIdTokenRefreshScheduler.kt`.

**Триггер**: `@Scheduled(cron = "0 0 * * * *")` — в 0 минут каждого
часа.

**Что делает**: фоновое обновление VK ID access_token.

**Логирование**: SLF4J `LoggerFactory.getLogger`.

---

## 6. `StemJobPollScheduler`

**Файл**: `karaoke-app/.../StemJobPollScheduler.kt`.

### `pollWaiting()` — `@Scheduled(fixedDelay = 45_000L, initialDelay = 30_000L)`

Каждые 45 секунд:

1. Если `Karaoke.stemJobsWebInternalUrl` пуст — no-op (scheduler
   отключён).
2. `StemJob.loadWaiting(...)` — загружает `WAITING` задания с
   удалённой БД (`Connection.remote()`).
3. Для каждого:
   - `runCatching { takeJob(job) }` — защита от per-job exception.
   - `takeJob`:
     - Скачивает сырой файл через InternalStemJobController (внутренний
       HTTP).
     - Проверяет длительность через `ffprobe` ДО постановки в
       очередь — защита от заведомо длинных файлов (не тратить GPU).
     - Создаёт `KaraokeProcess` (LOCAL БД, `THREAD_LANE_STEM_JOBS`).
     - `KaraokeProcessWorker.doStart()` подхватит через `Connection.local()`.

### `cleanup()` — `@Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 60_000L)`

Каждые 5 минут:

- Удаляет `DONE + expires_at < now` задания.
- Удаляет `deleteRequested = true` задания.
- Чистит файлы из MinIO (через `SAC_APP`) + строку из `tbl_stem_jobs`.

---

## 8. `EventsRetentionScheduler`

**Файл**: `karaoke-web/.../services/EventsRetentionScheduler.kt`.

**Триггер**: `@Scheduled(cron = "0 0 3 * * *")` — ежедневно в 03:00 UTC.

**Что делает**: удаление старых `tbl_events` (append-only event log; в
SyncRegistry намеренно отсутствует, см. [entities-catalog.md](../../catalog/components/entities-catalog.md#webevent)).

Retention: `KaraokeProperties.eventsRetentionDays` (default `7`, env
`KARAOKE_WEB_EVENTS_RETENTION_DAYS`, минимум `1`). SQL:
`DELETE FROM tbl_events WHERE last_update < ?` по cutoff
`now - retentionDays`.

**Логирование**: SLF4J.

---

## 9. `ShareLinkSweeper`

**Файл**: `karaoke-web/.../services/ShareLinkSweeper.kt`.

**Триггер**: `@Scheduled(fixedDelayString = "${karaoke.share.sweep-interval-seconds:60}000")` — каждые 60 секунд (default, configurable).

**Что делает**: sweep expired share-ссылок (см.
[entities-catalog.md](../../catalog/components/entities-catalog.md#songsharelink)).

Алгоритм (по KDoc):

- Загружает `SongShareLink` с `expires_at < now()`.
- Удаляет их (soft или hard — нужно проверить).

---

## 10. `SongReleaseAnnouncementScheduler`

**Файл**: `karaoke-web/.../services/SongReleaseAnnouncementScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 60_000L)`
— каждые 5 минут.

**Что делает**: анонсы релизов песен (генерирует сообщения и
отправляет через `SongReleaseAnnouncementService`).

**Использует**: `SongReleaseAnnouncementService`.

---

## 11. `StemJobTempCleanupScheduler`

**Файл**: `karaoke-web/.../services/StemJobTempCleanupScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 30 * 60_000L, initialDelay = 60_000L)`
— каждые 30 минут.

**Что делает**: **safety-net очистка temp-директории** загрузок
«Создать минусовку» (см. `PublicStemJobController` в
[storage-flow.md](../../storage/components/storage-flow.md)).

**Ловушка**: temp-файлы могли бы остаться при сбое flow
upload → processing. Sweeper — последняя линия защиты.

---

## 12. `SubscriptionRenewalScheduler`

**Файл**: `karaoke-web/.../services/SubscriptionRenewalScheduler.kt`.

**Триггер**: `@Scheduled(cron = "0 0 3 * * *")` — ежедневно в 03:00 UTC.

**Что делает**: авто-продление подписок (`Subscription` с
`autoRenew=true`).

**Использует**: `Subscription`, `SiteUser`, `KaraokeStorageService`,
`StorageApiClient`.

**Логика** (по контексту):

- Загружает подписки с `autoRenew=true + expiresAt < now() + grace`.
- Через YooKassa пытается списать (сохранённый `yookassaPaymentMethodId`).
- При успехе — продлевает `SiteUser.sitePremiumUntil`.

---

## Логика и Алгоритмы | Logic and Algorithms

### Общий тик

Каждый scheduler — Spring-бин (`@Component`/`@Service`) с одним публичным
`@Scheduled`-методом на задачу; сам планировщик потоков не создаёт.

- **Планировщик karaoke-app** — `KaraokeAppApplication` реализует
  `SchedulingConfigurer` и отдаёт явный бин `taskScheduler`:
  `ConcurrentTaskScheduler` над `Executors.newScheduledThreadPool(4)` с
  daemon-потоками `karaoke-scheduler`. До 4 тиков могут идти параллельно,
  длинный тик не блокирует все остальные.
- **Планировщик karaoke-web** — в `KaraokeWebApplication` только
  `@EnableScheduling`; явного `TaskScheduler` в коде нет.
- **Порядок внутри тика**: триггер → публичный метод → загрузка кандидатов
  из БД → работа с сервисом/внешним API → логирование.
- **Изоляция ошибок неоднородна**: `TelegramAutoPublishScheduler.tick()`,
  `PremiumAutoPublishScheduler.tick()`, `SponsrSyncScheduler.run()`,
  `SongReleaseAnnouncementScheduler.checkOnAir()` оборачивают работу в
  `try/catch`; `StemJobPollScheduler.pollWaiting()` — `runCatching` на каждое
  задание; `VkAutoPublishScheduler` гасит сбой VK API (specs/437 #161), чтобы
  не валить тик. А `SubscriptionRenewalScheduler.renewExpiringSiteSubscriptions()`
  идёт `users.forEach { tryRenew(user) }` без `try/catch` — исключение на
  одном пользователе прерывает весь тик.

### `fixedDelay` vs `fixedRate` vs `cron`

- `fixedDelay` — следующая задача стартует **N мс ПОСЛЕ завершения
  предыдущей**. Используется для задач, которые не должны накапливаться.
- `fixedRate` — следующая задача стартует **строго по расписанию**.
  Используется для задач, где важна регулярность (даже ценой overlap).
- `cron` — точно по времени суток.

### Динамические интервалы и SpEL

Spring `@Scheduled` не позволяет ссылаться на `KaraokeProperties`
через SpEL (это наш собственный base64-properties, не Spring
Environment). Решение — паттерн `fixedDelay` (хардкод) + ручная
проверка `now - lastRunMs >= intervalMs` внутри тика (по образцу
`AutoOneClickSyncScheduler`, см. [two-db-sync.md](two-db-sync.md)).
Исключение — `ShareLinkSweeper`: там `fixedDelayString` смотрит в
настоящий Spring-property `${karaoke.share.sweep-interval-seconds:60}`.

---

## Архитектурные решения

### Решение 1: scheduler — single-instance only

`karaoke-app` — desktop, однопроцессный. Никаких cluster lock'ов.

Следствие: если процесс не запущен — задачи теряются. Это by design:
`@Scheduled`-методы `karaoke-app` не работают на проде, см.
[ADR-0004](../../../adr/0004-karaoke-app-admin-only.md).

---

## Зависимости | Dependencies

### Spring-планировщик

- → `KaraokeAppApplication.taskScheduler` — `ConcurrentTaskScheduler` над
  `Executors.newScheduledThreadPool(4)`, daemon-потоки `karaoke-scheduler`
  (karaoke-app).
- → `@EnableScheduling` в `KaraokeWebApplication` — karaoke-web (явного
  `TaskScheduler` нет).
- → `KaraokeProperties` — интервалы, окна и лимиты
  (`telegramAutoPublishWindowMinutes`, `vkAutoPublishRateLimitPerHour`,
  `eventsRetentionDays`); `@Value("${stemjobs.temp-dir:/tmp/stemjobs}")` и
  `WebShareProperties` — у соответствующих scheduler'ов.

### Внешние сервисы и клиенты (karaoke-app)

| Scheduler | Что дёргает |
|---|---|
| `TelegramAutoPublishScheduler` | `TelegramAutoPublishService` (`publishToTelegram`, `onRenderCompleted`); `WORKING_DATABASE`, `KSS_APP`, `SAC_APP` |
| `VkAutoPublishScheduler` | `VkApiClient`, `VkAutoPublishService` (`publishToVk`, `onRenderCompleted`); `WORKING_DATABASE`, `KSS_APP`, `SAC_APP` |
| `SponsrSyncScheduler` | `Connection.remote()`, `SponsrSyncService.syncViaScraping(db, KSS_APP, SAC_APP)` |
| `PremiumAutoPublishScheduler` | `TelegramAutoPublishService`, `VkAutoPublishService`, `Song.loadFromDbById`; `WORKING_DATABASE`, `KSS_APP`, `SAC_APP` |
| `VkIdTokenRefreshScheduler` | `VkApiClient.refreshVkIdAccessToken()`, `KaraokeProperties` |
| `StemJobPollScheduler` | `Connection.remote()` (`StemJob.loadWaiting`/`loadPendingCleanup`), внутренний HTTP `Karaoke.stemJobsWebInternalUrl` (`/api/internal/stemjobs/{id}/raw` и `/ack`), `ffprobe` (subprocess), `KaraokeProcess` + `THREAD_LANE_STEM_JOBS` |

### Внешние сервисы и клиенты (karaoke-web)

| Scheduler | Что дёргает |
|---|---|
| `EventsRetentionScheduler` | `WORKING_DATABASE`, `KaraokeProperties.eventsRetentionDays` — `DELETE FROM tbl_events` |
| `ShareLinkSweeper` | `SongShareLinkService`, `WebShareProperties`, `KaraokeStorageService`, `StorageApiClient`, `WORKING_DATABASE` |
| `SongReleaseAnnouncementScheduler` | `SongReleaseAnnouncementService`, `KaraokeStorageService`, `StorageApiClient` |
| `StemJobTempCleanupScheduler` | локальная ФС `stemjobs.temp-dir` |
| `SubscriptionRenewalScheduler` | `SiteUser.loadSitePremiumExpiringBefore`, `PaymentService.chargeRecurring` (по сохранённому `yookassaPaymentMethodId`), `KaraokeStorageService`, `StorageApiClient` |

### Локи и общие ресурсы

- **Нет cluster lock** ни у одного scheduler'а этой компоненты: karaoke-app —
  desktop, однопроцессный (см. Решение 1).
- `VkAutoPublishScheduler` — `synchronized(postTimestamps)` (часовой
  rate-limit слотов) и in-memory `publishedNewsIdsWithoutSong`
  (идемпотентность air-новостей без `song_id`).
- `AutoOneClickSyncScheduler.running` (`AtomicBoolean`) — общий лок с ручным
  `POST /sync/oneclick` (409 Conflict), см. [two-db-sync.md](two-db-sync.md).
- `KaraokeProcessWorker.startStopLock` — не даёт поднять второй воркер
  очереди, см. [async-process-queue.md](async-process-queue.md).

---

## Ловушки

1. **Schedulers не запускаются, если процесс не работает.** Никакой
   persistence queue.
2. **Логирование**: некоторые используют SLF4J (`VkIdTokenRefreshScheduler`,
   `EventsRetentionScheduler`), другие — `println`
   (`StemJobPollScheduler.pollWaiting()`). Нужна унификация (Pass 343+).
3. **Concurrency**: scheduler + ручной триггер могут race.
   Некоторые защищены (`AutoOneClickSyncScheduler.running` +
   `409 Conflict`), другие — нет.
4. **Long-running tick**: если задача в scheduler занимает > N секунд,
   а следующий тик уже запланирован — `fixedDelay` не запустит параллельно,
   но и не пропустит. `fixedRate` запустит параллельно — может
   быть overlap.

---

## Известные TODO

- [x] **`EventsRetentionScheduler.retentionDays`** — `KaraokeProperties.eventsRetentionDays`
      (default `7`, env `KARAOKE_WEB_EVENTS_RETENTION_DAYS`), см. раздел 8.
- [ ] **`ShareLinkSweeper`** — soft или hard delete?
- [ ] **`StemJobPollScheduler.takeJob`** — полный алгоритм скачивания
      + ffprobe-проверки длительности.
- [ ] **`SubscriptionRenewalScheduler`** — grace period, retry
      policy при сбое списания.
- [ ] **Concurrency защита** для каждого scheduler.
- [ ] **Persistence queue** для scheduler'ов — Pass 343+ решение
      (нужно ли вообще?).
- [ ] **Логирование**: унифицировать println → SLF4J.

## Код (физическая реализация)

| Scheduler | Файл |
|---|---|
| `TelegramAutoPublishScheduler` | `karaoke-app/.../services/TelegramAutoPublishScheduler.kt` |
| `VkAutoPublishScheduler` | `karaoke-app/.../services/VkAutoPublishScheduler.kt` |
| `SponsrSyncScheduler` | `karaoke-app/.../services/SponsrSyncScheduler.kt` |
| `PremiumAutoPublishScheduler` | `karaoke-app/.../services/PremiumAutoPublishScheduler.kt` |
| `VkIdTokenRefreshScheduler` | `karaoke-app/.../services/VkIdTokenRefreshScheduler.kt` |
| `StemJobPollScheduler` | `karaoke-app/.../StemJobPollScheduler.kt` |
| `EventsRetentionScheduler` | `karaoke-web/.../services/EventsRetentionScheduler.kt` |
| `ShareLinkSweeper` | `karaoke-web/.../services/ShareLinkSweeper.kt` |
| `SongReleaseAnnouncementScheduler` | `karaoke-web/.../services/SongReleaseAnnouncementScheduler.kt` |
| `StemJobTempCleanupScheduler` | `karaoke-web/.../services/StemJobTempCleanupScheduler.kt` |
| `SubscriptionRenewalScheduler` | `karaoke-web/.../services/SubscriptionRenewalScheduler.kt` |

## Связанные ADR | Related ADRs

- [0004-karaoke-app-admin-only](../../../adr/0004-karaoke-app-admin-only.md) —
  `karaoke-app` только на admin-машине; его `@Scheduled`-методы не работают
  на проде (отсюда desktop-only характер app-планировщиков).
- [local-0005-structured-logging-karaoke-app](../../../adr/local-0005-structured-logging-karaoke-app.md) —
  конвенция логирования для app-планировщиков (см. Ловушку 2).
- [local-0006-logging-and-error-handling-karaoke-web](../../../adr/local-0006-logging-and-error-handling-karaoke-web.md) —
  логирование/error handling для web-планировщиков.

Примечание: прежняя ссылка на `local-0003` в этом файле была ошибочной —
этот ADR про shared MinIO image cache, не про cluster lock.

## Changelog

- **Pass 485** (2026-09-27, spec `485-knowledge-domain-processing`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 344** (2026-09-09): Initial. Прецедент: задачи #65, #69 +
  общий Knowledge-аудит. Автор: agent (Karaoke).