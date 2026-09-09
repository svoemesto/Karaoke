# Component: publishing-services (Pass 379)

> **Домен**: [publishing](../domain.md)
> **Компонента**: детальный обзор services, отвечающих за авто-публикацию
> (TG/VK/Sponsr/Premium).

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

## Архитектура

### Шаблоны + Auto-publish

```
News (category + text + picture + dates)
  → NewsTemplateService.render() → готовая строка
  → [VK|Telegram]AutoPublishService.publish(news)
       → VkApiClient / TelegramApiClient (см. external-api-clients)
```

### State (результаты + история)

Каждый `*AutoPublishService` имеет:
- `*Result` (DTO с результатом попытки) — `VkAutoPublishResult`,
  `TelegramAutoPublishResult`.
- `*State` (in-memory state текущих попыток) — `VkAutoPublishState`,
  `TelegramAutoPublishState`.
- `*SchedulerStarter` — Spring `@Component` для запуска scheduler'а.

### `*TemplateService`

- `VkTemplateService` / `TelegramTemplateService` — шаблоны
  (placeholder'ы, defaults).
- `NewsTemplateService` — для 4 категорий (`air` / `premium` /
  `feature` / другое), см. [entities-catalog.md#news](../../catalog/components/entities-catalog.md#news).

## Детально: `VkAutoPublishService` (546 строк)

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

## Детально: `SongReleaseAnnouncementService` (506 строк)

**Файл**: `karaoke-app/.../services/SongReleaseAnnouncementService.kt`.

**Логика** (по KDoc):

1. **Triggered by** `SongReleaseAnnouncementScheduler` (каждые 5 мин).
2. Находит песни с `idStatus >= 3` + `publishDate` в окне.
3. Генерирует анонс-сообщение (через `NewsTemplateService` +
   `KaraokeProperties`).
4. Отправляет через `TelegramApiClient` или `VkApiClient` (в
   зависимости от настроек).

## Детально: `TelegramAutoPublishService` (338 строк)

**Файл**: `karaoke-app/.../services/TelegramAutoPublishService.kt`.

**Логика** (по KDoc + спецификация `telegram-auto-publish`):

1. **Triggered by** `TelegramAutoPublishScheduler` (каждые 60с).
2. Находит новости `category=air + publish_at <= now()` + не
   опубликованные.
3. Отправляет через `TelegramApiClient.sendMessage` /
   `sendPhoto`.
4. Идемпотентность через `news_id_vk/telegram` поле (если
   `news_id_*` уже есть, не публикуем).

## Детально: `NewsTemplateService` (264 строки)

**Файл**: `karaoke-app/.../services/NewsTemplateService.kt`.

**Логика**:

```kotlin
object NewsTemplateService {
    const val DEFAULT_AIR_TITLE = "..."
    const val DEFAULT_AIR_BODY = "..."
    const val DEFAULT_PREMIUM_TITLE = "..."
    const val DEFAULT_PREMIUM_BODY = "..."
    
    fun template(category: String, ...): String { ... }
    fun placeholders(): Map<String, String> { ... }
    fun defaultFor(category: String): Pair<String, String> { ... }
    fun descriptionFor(placeholder: String): String { ... }
    fun fieldFor(placeholder: String): String { ... }
}
```

**Placeholders** — `{songId}`, `{author}`, `{album}`, `{date}` и т.д.

## Детально: `AdminTaskService` (132 строки)

**Файл**: `karaoke-app/.../services/AdminTaskService.kt`.

**Логика**: утилита для admin-задач (helper-методы).

## Hot paths

| Сервис | Частота |
|---|---|
| `VkAutoPublishService` | 60s (scheduler) + per-news (manual) |
| `TelegramAutoPublishService` | 60s + per-news |
| `SongReleaseAnnouncementService` | 5min + per-song |
| `NewsTemplateService` | per-render (cache не используется) |

## Связь с другими компонентами

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

- **Pass 379** (2026-09-09): Initial. Автор: agent (Karaoke).