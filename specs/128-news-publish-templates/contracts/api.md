# API Contract: Шаблоны автоматических новостей сайта

**Branch**: `128-news-publish-templates` | **Date**: 2026-08-03

Phase 1 output. Контракт REST endpoints для админ-UI (`webvue3`).
Зеркалирует существующие `/api/vk/templates/*` и
`/api/telegram/templates/*`, с отличиями: (1) `target=local|remote`
параметр (по образцу `/api/publicsettings/*`), (2) UPSERT-запись (R2),
(3) 4 ключа вместо 2 (title+body отдельно для каждой категории),
(4) preview возвращает два поля (`title` + `body`), не одно.

Все endpoints живут в новом контроллере `NewsTemplateController` в
`karaoke-app` (пакет `com.svoemesto.karaokeapp.controllers`). Базовый
путь: `/api/news/templates`.

## Общие принципы

- **Content-Type**: `application/x-www-form-urlencoded` (как у VK/Telegram
  endpoints — `@RequestParam`, не JSON body).
- **target**: `local` (default) | `remote` — выбор БД (по образцу
  `PublicSettingsController.resolveDb`). `remote` пишет/читает
  напрямую в prod-БД (см. research.md R1).
- **Аутентификация**: `permitAll()` — как все admin-API (см.
  `SecurityConfig.kt`). Не добавляем новую авторизацию.
- **Коды возврата**: 200 + JSON-тело с `success: true|false`. Ошибки
  валидации — 200 с `success: false` и полем `error` (по образцу VK
  `saveVkTemplate`), НЕ 4xx (UI шлёт `promisedXMLHttpRequest`, не
  различает 200 и 400 в общем коде).
- **withDb**-хелпер: `resolveDb(target)` + `close()` в `finally` (по
  образцу `PublicSettingsController.withDb`, line 42-55) — чтобы не
  исчерпать пул Postgres.

## Разрешённые ключи

```text
newsTemplateAirTitle
newsTemplateAirBody
newsTemplatePremiumTitle
newsTemplatePremiumBody
```

Любой другой `key` в `/api/news/templates` (POST) → `{success: false,
error: "unknown key: ... (allowed: ...)"}`.

## Endpoints

### 1. GET `/api/news/templates`

Возвращает все 4 шаблона и список плейсхолдеров для UI-редактора.

**Request**:
- Query: `target` (опц., `local`|`remote`, default `local`)

**Response 200**:
```json
{
  "templates": [
    { "key": "newsTemplateAirTitle",       "category": "air",     "field": "title", "value": "...", "default": "Новая песня: {author} — {songName}{albumYearSuffix}" },
    { "key": "newsTemplateAirBody",        "category": "air",     "field": "body",  "value": "...", "default": "Песня «{songName}» ({bodyDetails}) вышла в эфир." },
    { "key": "newsTemplatePremiumTitle",   "category": "premium", "field": "title", "value": "...", "default": "Новая песня: {author} — {songName}{albumYearSuffix}" },
    { "key": "newsTemplatePremiumBody",    "category": "premium", "field": "body",  "value": "...", "default": "Песня «{songName}» ({bodyDetails}) появилась в коллекции." }
  ],
  "placeholders": [
    { "name": "author",                  "description": "Song.author — автор песни" },
    { "name": "songName",                "description": "Song.songName — название песни (сырое)" },
    { "name": "songNameCensored",        "description": "Song.songName.censored() — цензурированное название" },
    { "name": "year",                    "description": "Song.year — год" },
    { "name": "album",                   "description": "Song.album — название альбома" },
    { "name": "albumYearSuffix",         "description": "суффикс \" (альбом «X\", Y)\" — пустой, если альбом и год не заполнены (byte-идентичен хардкод)" },
    { "name": "bodyDetails",             "description": "author, альбом «X», Y одной строкой (byte-идентичен хардкод)" },
    { "name": "link",                    "description": "https://sm-karaoke.ru/song?id={id} — ссылка на песню" },
    { "name": "id",                      "description": "Song.id — идентификатор песни" },
    { "name": "newsBody",                "description": "News.body — текст связанной новости (для air; пусто для premium)" },
    { "name": "descriptionHeader",       "description": "Song.getTextForDescriptionHeader() — заголовок-описание песни" },
    { "name": "descriptionFooter",       "description": "Song.getTextForDescriptionFooter() — подвал со ссылками/хештегами" },
    { "name": "description",             "description": "Song.getTextForDescription() — текст-описание песни" },
    { "name": "descriptionWithTimecodes","description": "Song.getTextForDescriptionWithTimecodes() — описание с таймкодами" }
  ]
}
```

`value` — значение из `tbl_public_settings`, или пустая строка если
ключ отсутствует (UI показывает `default` как placeholder, когда
`value` пусто).

### 2. POST `/api/news/templates`

Сохраняет значение одного ключа (UPSERT, FR-008 — без перезапуска).

**Request**:
- Form: `key` (обяз.), `value` (обяз., может быть пустой строкой), `target` (опц.)

**Response 200 (success)**:
```json
{ "success": true, "key": "newsTemplateAirTitle" }
```

**Response 200 (invalid key)**:
```json
{ "success": false, "error": "unknown key: newsTemplateFoo (allowed: newsTemplateAirTitle, newsTemplateAirBody, newsTemplatePremiumTitle, newsTemplatePremiumBody)" }
```

**SQL** (UPSERT, R2):
```sql
INSERT INTO tbl_public_settings (key, value, description, last_update)
VALUES (?, ?, ?, now())
ON CONFLICT (key) DO UPDATE
SET value = EXCLUDED.value, description = EXCLUDED.description, last_update = now()
```

`description` — строка вида `"Шаблон авто-новости «в эфире» — заголовок
(плейсхолдеры: {author}, {songName}, ...)"` (для generic UI
`PublicSettingsTable`, если кто-то откроет тот же ключ там).

### 3. POST `/api/news/templates/preview`

Рендерит незасейвленный шаблон на тестовой песне (FR-009).

**Request**:
- Form: `titleTemplate` (обяз.), `bodyTemplate` (обяз.), `id` (обяз., Long — Song.id), `target` (опц.)

**Response 200 (success)**:
```json
{
  "success": true,
  "title": "Новая песня: Группа — Песня (альбом «Альбом», 2024)",
  "body": "Песня «Песня» (Группа, альбом «Альбом», 2024) вышла в эфир.",
  "titleLength": 52,
  "titleTruncated": false,
  "titleMaxLength": 500,
  "bodyLength": 71,
  "bodyTruncated": false
}
```

**Response 200 (song not found)**:
```json
{ "success": false, "error": "Песня не найдена: id=12345" }
```

`titleTruncated = true` если `title.length > 500` (усечение с `…`).
`bodyTruncated` всегда `false` (body — TEXT без лимита).

### 4. GET `/api/news/templates/defaults`

Возвращает заводские дефолты (FR-013, для кнопки «Сбросить к дефолту»).

**Request**: (без параметров)

**Response 200**:
```json
{
  "defaults": {
    "newsTemplateAirTitle":     "Новая песня: {author} — {songName}{albumYearSuffix}",
    "newsTemplateAirBody":      "Песня «{songName}» ({bodyDetails}) вышла в эфир.",
    "newsTemplatePremiumTitle": "Новая песня: {author} — {songName}{albumYearSuffix}",
    "newsTemplatePremiumBody":  "Песня «{songName}» ({bodyDetails}) появилась в коллекции."
  }
}
```

Значения — `NewsTemplateService.DEFAULT_*` из кода (не из БД).

## Внутренний контракт: `NewsTemplateService` (Kotlin)

Не HTTP, для использования в `SongReleaseAnnouncementService` (prod
рендеринг).

```kotlin
object NewsTemplateService {
    const val DEFAULT_AIR_TITLE: String
    const val DEFAULT_AIR_BODY: String
    const val DEFAULT_PREMIUM_TITLE: String
    const val DEFAULT_PREMIUM_BODY: String
    const val NEWS_TITLE_MAX_LENGTH: Int = 500

    val PLACEHOLDERS: List<PlaceholderInfo>

    // Читает шаблон из tbl_public_settings (по образцу isNewsAutoPublishKillSwitchActive).
    // При отсутствии/пустом значении → DEFAULT_*. При ошибке JDBC → DEFAULT_* (fail-open).
    fun template(key: String, database: KaraokeConnection): String

    // Рендерит шаблон: заменяет {placeholder} на значения из song.
    // Неизвестные плейсхолдеры остаются literal (FR-005).
    // title усекается до NEWS_TITLE_MAX_LENGTH с … (по образцу VkTemplateService.truncate).
    fun render(template: String, song: Song, news: News? = null): String

    // Список плейсхолдеров для UI (повторяет VkTemplateService.placeholders()).
    fun placeholders(): List<Map<String, String>>

    // Перенесённые из SongReleaseAnnouncementService (стали public, byte-идентичны):
    fun albumYearSuffix(song: Song): String
    fun bodyDetails(song: Song): String
}
```

**Вызывающие точки** (правки в `SongReleaseAnnouncementService`):
- `checkOnAirWindow` (line ~198-199): `title = NewsTemplateService.render(NewsTemplateService.template("newsTemplateAirTitle", database), song)`,
  `body = NewsTemplateService.render(NewsTemplateService.template("newsTemplateAirBody", database), song)`
- `detectAndAnnounceAvailability` (line ~88-89): аналогично для
  `newsTemplatePremiumTitle`/`newsTemplatePremiumBody`.

`News.createAutoAnnouncement` (News.kt:337) — **без правок** (уже
принимает `title`/`body` как строки).

## Не-контракты (явно не покрываемые фичей)

- `News.createAutoAnnouncement` — без изменений сигнатуры/логики.
  Kill-switch `newsAutoPublishKillSwitch` сохраняет приоритет (если
  `"true"`, новость не создаётся, шаблон не рендерится — фича НЕ
  обходит kill-switch).
- `News.existsAnnouncement` — без изменений (идемпотентность).
- `tbl_news`/`tbl_public_settings` схема — без миграций (UPSERT создаёт
  строки при первом сохранении).
- `PublicSettingsController` — без правок (generic UI остаётся, но
  administratoр должен редактировать шаблоны новостей через новый
  `/api/news/templates/*`, а не через generic `PublicSettingsTable`).