# Component: publishing-services (Pass 379)

> **Домен**: [publishing](../domain.md)
> **Компонента**: детальный обзор services, отвечающих за авто-публикацию
> (TG/VK/Sponsr/Premium).


## Ответственность | Responsibility


детальный обзор services, отвечающих за авто-публикацию (TG/VK/Sponsr/Premium).

## Назначение

Сервисы, которые публикуют контент во внешние системы
(см. [external-api-clients.md](../../integration/components/external-api-clients.md)).
Связаны со [schedulers.md](../../processing/components/schedulers.md) — каждый
имеет свой @Scheduled триггер.

## Каталог

| # | Сервис | Строк | Что |
|---|---|---|---|
| 1 | `VkAutoPublishService` | 546 | **Самый большой** — авто-постинг в VK (preview warmup + photo upload + wall.post) |
| 2 | `SongReleaseAnnouncementService` | 506 | Анонсы релизов песен |
| 3 | `TelegramAutoPublishService` | 338 | Авто-постинг в Telegram |
| 4 | `NewsTemplateService` | 264 | Шаблоны новостей (4 категории) |
| 5 | `AdminTaskService` | 132 | Админ-задачи (UI helpers) |
| 6 | `TelegramTemplateService` | ? | Шаблоны TG (Pass 343+) |
| 7 | `VkTemplateService` | ? | Шаблоны VK (Pass 343+) |

## Интерфейсы и Контракты | Interfaces and Contracts

### DTO и state

Каждый `*AutoPublishService` имеет:

- `*Result` (DTO с результатом попытки) — `VkAutoPublishResult`,
  `TelegramAutoPublishResult` (`state`, `messageId`, `error`).
- `*State` (in-memory state текущих попыток) — `VkAutoPublishState`,
  `TelegramAutoPublishState`; enum-значения одинаковые:
  `SCHEDULED`, `RENDERING`, `PUBLISHING`, `PUBLISHED`, `SEND_FAILED`,
  `CANCELLED`.
- `*SchedulerStarter` — Spring `@Component`
  (`VkAutoPublishSchedulerStarter`, `TelegramAutoPublishSchedulerStarter`),
  поднимает scheduler при старте приложения.

### Точки входа

| Сервис | Метод |
|---|---|
| `VkAutoPublishService` (object) | `publishToVk(song, type = PublicationType.AIR, persistPostId = true): VkAutoPublishResult`; `onRenderCompleted(...)` |
| `TelegramAutoPublishService` (object) | `publishToTelegram(song, allowPastDate = false, publicationType = AIR, persistMessageId = true): TelegramAutoPublishResult`; `onRenderCompleted(songId, publicationType, persistMessageId, success, error)` |
| `SongReleaseAnnouncementService` (object) | `detectAndAnnounceAvailability(...)`, `checkOnAirWindow(...)`, `backfillNewsAvailableFlag(...)`, `backfillPublishFlags(...)` |
| `NewsTemplateService` (object) | `template(key, database)`, `render(template, song, news, truncate, database)`, `placeholders()`, `defaultFor(key)`, `descriptionFor(key)`, `categoryFor(key)`, `fieldFor(key)`, `albumYearSuffix(song)`, `bodyDetails(song)` |
| `TelegramTemplateService` (object) | `templateFor(type: PublicationType)`, `render(template, song, database)`, `placeholders()` |
| `AdminTaskService` (`@Service`) | `startTask(action, totalCount, block): UUID`, `getTask(taskId): TaskStatus?`; `TaskStatus` (`RUNNING`/`COMPLETED`/`PARTIAL`/`FAILED`) |

### `*TemplateService`

- `VkTemplateService` / `TelegramTemplateService` — шаблоны
  (placeholder'ы, defaults); `KaraokeProperties` хранит переопределения.
- `NewsTemplateService` — для 4 категорий (`air` / `premium` /
  `feature` / другое), см. [entities-catalog.md#news](../../catalog/components/entities-catalog.md#news).
  Ключи — `newsTemplateAirTitle`/`AirBody`/`PremiumTitle`/`PremiumBody`
  (`ALLOWED_KEYS`), читаются из `tbl_public_settings` (fail-open на дефолт).

## Логика и Алгоритмы | Logic and Algorithms

### Шаблоны + Auto-publish

```
News (category + text + picture + dates)
  → NewsTemplateService.render() → готовая строка
  → [VK|Telegram]AutoPublishService.publish(news)
       → VkApiClient / TelegramApiClient (см. external-api-clients)
```

### Детально: `VkAutoPublishService` (546 строк)

**Файл**: `karaoke-app/.../services/VkAutoPublishService.kt`.

**Логика** (по KDoc + grep):

1. **Preview warmup** — `VkPreviewWarmupClient` генерирует preview
   (см. [external-api-clients.md](../../integration/components/external-api-clients.md#3-vkpreviewwarmupclient)).
2. **Photo upload** — `VkPhotoUploadClient` загружает обложку в группу
   (с fallback на docs.*).
3. **wall.post** — `VkApiClient.wallPost(attachments=...)` публикует
   пост.
4. **Error handling** — `*Result` содержит `error` (краткое
   описание).
5. **Idempotency** — для `air` новостей без `song_id` через
   in-memory `Set` (Pass 341 P0).

**NB**: каждый шаг — отдельный HTTP-вызов через nginx-proxy.
Failure modes: preview timeout, photo upload rate limit, wall.post
group blocked.

**Resilience** (specs/437, #161): `publishTextOnly` / `publishFile` ловят
исключения от `VkApiClient` (в т.ч. `VkNetworkException` — «VK недоступен
напрямую, прокси не задан») и штатно записывают `SEND_FAILED` через
`writeFailure` (`vkAutoPublishLastError`), не пробрасывая исключение в
`@Scheduled`-тик. `VkAutoPublishScheduler.publishNewsWithoutVideo` — то же,
пост не помечается опубликованным (повтор на следующем тике).

### Детально: `SongReleaseAnnouncementService` (506 строк)

**Файл**: `karaoke-app/.../services/SongReleaseAnnouncementService.kt`.

**Логика** (по KDoc):

1. **Triggered by** `SongReleaseAnnouncementScheduler` (каждые 5 мин).
2. Находит песни с `idStatus >= 3` + `publishDate` в окне.
3. Генерирует анонс-сообщение (через `NewsTemplateService` +
   `KaraokeProperties`).
4. Отправляет через `TelegramApiClient` или `VkApiClient` (в
   зависимости от настроек).

### Детально: `TelegramAutoPublishService` (338 строк)

**Файл**: `karaoke-app/.../services/TelegramAutoPublishService.kt`.

**Логика** (по KDoc + спецификация `telegram-auto-publish`):

1. **Triggered by** `TelegramAutoPublishScheduler` (каждые 60с,
   `fixedDelay`; `resumeRenderingSongs()` + `publishScheduledSongs()`).
2. Идемпотентность: непустой `song.idTelegramDemo` → `PUBLISHED` без
   отправки; для `PREMIUM` дополнительно `song.newsPremiumTelegramSent`
   (страховка от дублей между тиками и ручными вызовами).
3. `dateTimePublish` в прошлом при `allowPastDate = false` (для
   `PREMIUM` всегда `true`) → `SCHEDULED` «опоздавшая публикация».
4. `!song.isContentReady` → `SCHEDULED`.
5. Нет демо-MP4 нужного размера (лимит
   `telegramAutoPublishMaxFileSizeMb`, дефолт 50 МБ) → рендер
   (`RENDER_MP4_DEMO`), состояние `RENDERING`, продолжение через
   `onRenderCompleted`.
6. Иначе — `publishFile`: `TelegramApiClient` (`sendVideo`/`sendPhoto`),
   при `persistMessageId = true` запись `idTelegramDemo` через
   `Song.saveToDb` (FR-006).

### Детально: `NewsTemplateService` (264 строки)

**Файл**: `karaoke-app/.../services/NewsTemplateService.kt`.

**Логика**:

```kotlin
object NewsTemplateService {
    const val NEWS_TITLE_MAX_LENGTH = 500
    const val DEFAULT_AIR_TITLE = "..."
    const val DEFAULT_AIR_BODY = "..."
    const val DEFAULT_PREMIUM_TITLE = "..."
    const val DEFAULT_PREMIUM_BODY = "..."
    val ALLOWED_KEYS: Set<String> = setOf(/* 4 ключа */)
    val PLACEHOLDERS: List<PlaceholderInfo> = listOf(/* ... */)

    fun template(key: String, database: KaraokeConnection): String
    fun render(template: String, song: Song, news: News? = null,
               truncate: Boolean = true, database: KaraokeConnection = WORKING_DATABASE): String
    fun placeholders(): List<Map<String, String>>
    fun defaultFor(key: String): String
    fun descriptionFor(key: String): String
    fun categoryFor(key: String): String
    fun fieldFor(key: String): String
    fun albumYearSuffix(song: Song): String
    fun bodyDetails(song: Song): String
}
```

**Ключи**: `newsTemplateAirTitle` / `newsTemplateAirBody` /
`newsTemplatePremiumTitle` / `newsTemplatePremiumBody` (`ALLOWED_KEYS`);
значение читается из `tbl_public_settings`, при пустом/ошибке JDBC —
дефолт (fail-open). `descriptionFor`/`categoryFor`/`fieldFor` —
метаданные для generic-UI. `render` заменяет `{placeholder}` по
регулярке, неизвестные оставляет literal-текстом, `truncate = true`
усекает до `NEWS_TITLE_MAX_LENGTH = 500` (для `body` — `false`).

**Placeholders** — `{author}`, `{songName}`, `{songNameCensored}`,
`{year}`, `{album}`, `{albumYearSuffix}`, `{bodyDetails}`, `{link}`,
`{id}`, `{newsBody}`, `{descriptionHeader}`, `{descriptionFooter}`,
`{description}`, `{descriptionWithTimecodes}` (см. `PLACEHOLDERS`).

### Детально: `AdminTaskService` (132 строки)

**Файл**: `karaoke-app/.../services/AdminTaskService.kt` (Kotlin, `@Service`).

**Логика**: `startTask(action, totalCount, block)` создаёт `UUID`,
кладёт `TaskStatus` в `ConcurrentHashMap` и запускает `block(progress)`
в `Executors.newFixedThreadPool(2)`; финальный статус —
`COMPLETED` (0 ошибок и все успешны) / `PARTIAL` (есть успешные) /
`FAILED` (иначе); прогресс читается через `getTask(taskId)`.
Используется bulk-операциями админки (specs/319 US4).

## Hot paths

| Сервис | Частота |
|---|---|
| `VkAutoPublishService` | 60s (scheduler) + per-news (manual) |
| `TelegramAutoPublishService` | 60s + per-news |
| `SongReleaseAnnouncementService` | 5min + per-song |
| `NewsTemplateService` | per-render (cache не используется) |

## Зависимости | Dependencies

- **Schedulers** ([schedulers.md](../../processing/components/schedulers.md)) —
  триггеры.
- **External API** ([external-api-clients.md](../../integration/components/external-api-clients.md)) —
  HTTP-клиенты.
- **Catalog/News** ([entities-catalog.md#news](../../catalog/components/entities-catalog.md#news)) —
  news entity.
- **Monetization** ([monetization domain](../../monetization/domain.md)) —
  premium-публикация.

## Известные TODO

- [ ] **`TelegramTemplateService`** / `VkTemplateService` — детали
      (Pass 343+).
- [ ] **Каждое template** — полный список placeholders.
- [ ] **Error handling** — retry policy.
- [ ] **Идемпотентность** — точные механизмы.
- [ ] **Premium-публикация** — отдельный сервис или часть
      `VkAutoPublish`?

## Changelog

- **Pass 486** (2026-09-27, spec `486-knowledge-domains-others`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 437** (2026-09-23, issue #161): resilience `VkAutoPublishService` /
  `VkAutoPublishScheduler` — сетевые ошибки VK → `SEND_FAILED`, не исключение.
- **Pass 379** (2026-09-09): Initial. Автор: agent (Karaoke).