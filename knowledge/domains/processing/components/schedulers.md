# Component: schedulers (auto-publish, sync, retention, cleanup)

> **Домен**: [processing](../domain.md)
> **Компонента**: детальный каталог всех `@Scheduled` фоновых
> задач проекта (12 штук, не считая `StatsCacheScheduler`).

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
| 8 | `EventsRetentionScheduler` | karaoke-web | каждый день 03:00 UTC (`0 0 3 * * *`) | Удаление старых `tbl_web_events` |
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

**Использует**: `TelegramApiClient`, `TelegramAutoPublishService`.

**Ловушка**: только пока работает karaoke-app. Если процесс не
запущен — задачи теряются (нет persistence queue).

---

## 2. `VkAutoPublishScheduler`

**Файл**: `karaoke-app/.../services/VkAutoPublishScheduler.kt`.

**Триггер**: `@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)`
— каждые 60 секунд.

**Что делает**: авто-постинг новостей в VK.

**Алгоритм** (по KDoc):

- Тик каждые 60с, проверка новоов `category="air"` + `publish_at <= now()`.
- Идемпотентность для `air`-новостей без `song_id` — через
  in-memory `Set` в `VkAutoPublishScheduler` (см.
  [catalog/components/entities-catalog.md](../../catalog/components/entities-catalog.md#news)).

**Использует**: `VkApiClient`, `VkAutoPublishService`,
`VkPhotoUploadClient`, `VkTemplateService`.

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

**Использует**: `PremiumAutoPublishService`.

---

## 5. `VkIdTokenRefreshScheduler`

**Файл**: `karaoke-app/.../services/VkIdTokenRefreshScheduler.kt`.

**Триггер**: `@Scheduled(cron = "0 0 * * * *")` — в 0 минут каждого
часа.

**Что делает**: фоновое обновление VK ID access_token.

**Логирование**: SLF4J `LoggerFactory.getLogger`.

---

## 7. `StemJobPollScheduler`

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

**Что делает**: удаление старых `tbl_web_events` (см. [entities-catalog.md](../../catalog/components/entities-catalog.md#webevent)).

Retention period: `KaraokeProperties` (нужно уточнить — Pass 343+).

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

## Архитектурные решения

### Решение 1: scheduler — single-instance only

`karaoke-app` — desktop, однопроцессный. Никаких cluster lock'ов.

Следствие: если процесс не запущен — задачи теряются. Это by design
(см. ADR `local-0003`).

### Решение 2: `fixedDelay` vs `fixedRate`

- `fixedDelay` — следующая задача стартует **N мс ПОСЛЕ завершения
  предыдущей**. Используется для задач, которые не должны накапливаться.
- `fixedRate` — следующая задача стартует **строго по расписанию**.
  Используется для задач, где важна регулярность (даже ценой overlap).
- `cron` — точно по времени суток.

### Решение 3: SpEL `${...}` не работает для динамических интервалов

Spring `@Scheduled` не позволяет ссылаться на `KaraokeProperties`
через SpEL (это наш собственный base64-properties, не Spring
Environment). Решение — паттерн `fixedDelay` (хардкод) + ручная
проверка `now - lastRunMs >= intervalMs` внутри тика (по образцу
`AutoOneClickSyncScheduler`, см. [two-db-sync.md](two-db-sync.md)).

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

- [ ] **`EventsRetentionScheduler.retentionDays`** — точное значение
      и настройка через KaraokeProperties.
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

## Changelog

- **Pass 344** (2026-09-09): Initial. Прецедент: задачи #65, #69 +
  общий Knowledge-аудит. Автор: agent (Karaoke).