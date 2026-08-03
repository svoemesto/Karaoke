# Data Model: Шаблоны автоматических новостей сайта

**Branch**: `128-news-publish-templates` | **Date**: 2026-08-03

Phase 1 output. Описывает сущности, поля, отношения и валидации, которые
вводит или использует фича. **Новые таблицы НЕ создаются** — фича
использует существующие `tbl_news` и `tbl_public_settings`, новые
SQL-миграции НЕ требуются (см. research.md R2 — UPSERT вместо seed).

## Сущности

### 1. NewsTemplate (новая — логическая, без отдельной таблицы)

Шаблон авто-новости одной категории (`air` или `premium`): пара
`title`/`body` с плейсхолдерами. **Хранится в `tbl_public_settings`**
как 4 строковых ключа (см. сущность PublicSetting ниже). Заводские
дефолты — в коде (`NewsTemplateService.DEFAULT_*`).

| Поле | Тип | Источник | Описание |
|------|-----|----------|----------|
| `key` | String | `tbl_public_settings.key` | Один из 4: `newsTemplateAirTitle`, `newsTemplateAirBody`, `newsTemplatePremiumTitle`, `newsTemplatePremiumBody` |
| `value` | String (TEXT) | `tbl_public_settings.value` | Текст шаблона с плейсхолдерами; пустая строка → рендер использует дефолт из кода |
| `description` | String | `tbl_public_settings.description` | Описание ключа для generic UI `PublicSettingsTable` (опционально — заполняется при первом UPSERT) |
| `defaultFromCode` | String | `NewsTemplateService.DEFAULT_*` | Заводской дефолт, возвращаемый при пустом/отсутствующем `value` |

**Валидация ключей** (в `NewsTemplateController`):
- `key in {"newsTemplateAirTitle", "newsTemplateAirBody",
  "newsTemplatePremiumTitle", "newsTemplatePremiumBody"}` — иначе
  HTTP 400.
- `value` — любая строка (включая пустую). Несбалансированные скобки
  НЕ блокируют сохранение (FR-005 — literal-treatment при рендере).

**Лимиты длин** (после рендера, при записи в `tbl_news`):
- `title` — VARCHAR(500) в `tbl_news.title`. Превышение → усечение до
  497 + `…` (по образцу `VkTemplateService.truncate`, FR edge case).
- `body` — TEXT в `tbl_news.body` (без лимита Postgres). Усечение НЕ
  требуется.

**Дефолты (byte-идентичны текущим хардкод-строкам)**:
- `newsTemplateAirTitle` = `"Новая песня: {author} — {songName}{albumYearSuffix}"`
- `newsTemplateAirBody` = `"Песня «{songName}» ({bodyDetails}) вышла в эфир."`
- `newsTemplatePremiumTitle` = `"Новая песня: {author} — {songName}{albumYearSuffix}"`
- `newsTemplatePremiumBody` = `"Песня «{songName}» ({bodyDetails}) появилась в коллекции."`

### 2. News (существующая — БЕЗ изменений схемы)

Существующая сущность `tbl_news`. Фича **НЕ меняет схему** — только
заменяет источник значений `title`/`body` (хардкод → рендеринг
шаблона) в `News.createAutoAnnouncement`.

| Поле | Тип | Примечание для фичи |
|------|-----|---------------------|
| `id` | integer (IDENTITY) | без изменений |
| `title` | VARCHAR(500) NOT NULL | теперь из рендера шаблона (с усечением, см. NewsTemplate) |
| `body` | TEXT NOT NULL | теперь из рендера шаблона |
| `category` | VARCHAR(50) NOT NULL DEFAULT 'general' | `"air"` или `"premium"` — без изменений |
| `link` | VARCHAR(1000) | `/song?id={id}` — без изменений |
| `publish_at` | timestamp | `now()` — без изменений |
| `created_at` | timestamp | `now()` — без изменений |
| `recordhash` | VARCHAR(32) | триггер `update_tbl_news_recordhash` — БЕЗ правок (поля те же) |
| `source` | (в модели, не в SQL) | `"auto"` — без изменений; исключает из LOCAL↔SERVER sync |
| `songId` | (в модели) | без изменений |

**Идемпотентность**: `News.existsAnnouncement(songId, link, category)` —
без изменений. Замена хардкод-строк на шаблон НЕ влияет на идемпотентность
(спека Assumptions).

### 3. PublicSetting (существующая — БЕЗ изменений схемы)

`tbl_public_settings` — key/value таблица, читаемая и с admin, и с
прода. Фича добавляет 4 новых строки (через UPSERT при первом
сохранении), без миграции seed.

| Поле | Тип | Примечание |
|------|-----|------------|
| `key` | VARCHAR(255) PK | 4 новых значения (см. NewsTemplate.key) |
| `value` | TEXT NOT NULL DEFAULT '' | текст шаблона |
| `description` | VARCHAR(1024) NOT NULL DEFAULT '' | описание для generic UI |
| `last_update` | timestamp DEFAULT now() | обновляется UPSERT |

**Важные существующие ключи** (не фечи, контекст):
- `newsAutoPublishKillSwitch` — kill-switch auto-новостей
  (specs/125). Если `"true"` — `News.createAutoAnnouncement` возвращает
  `null`, рендеринг шаблона не вызывается. Фича **НЕ трогает** эту
  логику — kill-switch сохраняет приоритет над шаблонами.
- `yandexSmartCaptchaClientKey`, `yandexSmartCaptchaServerKey` — не
  связаны с фичей.

## Отношения

- `NewsTemplate.key` → `PublicSetting.key` (1:1, логическая ссылка, не
  FK — `tbl_public_settings` не имеет FK на `tbl_news`).
- `NewsTemplate` → `Song` (n:1 логически: один шаблон применяется к
  многим песням в момент создания auto-новости).
- `News.songId` → `tbl_songs.id` (существующая ссылка, без изменений).

## State Transitions

**NewsTemplate** — stateless (просто текст в БД). Единственное
состояние: «ключ отсутствует в БД» (→ дефолт из кода) vs «ключ есть со
значением» (→ рендер значения, даже если оно пустое → дефолт из кода
по `.ifBlank`). Формально:
- `key absent in DB` → `templateFor` returns `DEFAULT_*`
- `value == ""` → `templateFor` returns `DEFAULT_*` (через `.ifBlank`)
- `value == "..."` → `templateFor` returns `value` (рендерится с
  плейсхолдерами)

**News** — без изменений. `source="auto"` создаётся однажды
(идемпотентность через `existsAnnouncement`), не переоткрывается при
изменении шаблона (FR-011).

## Валидации (summary)

- `key` ∈ 4 разрешённых значений (иначе 400) — в `NewsTemplateController`
- `value` — любая строка (без валидации плейсхолдеров — literal-treatment)
- `title` после рендера ≤ 500 символов (усечение с `…`, по образцу VK)
- `body` после рендера — без лимита
- несбалансированные скобки — НЕ блокируют сохранение/рендер

## Миграции

**Не требуются.** `tbl_news` и `tbl_public_settings` существуют. 4
новых ключа создаются UPSERT'ом при первом сохранении через
`NewsTemplateController`. Заводские дефолты живут в коде
(`NewsTemplateService.DEFAULT_*`), не в БД.

При первом развёртывании фичи (до первой правки администратора) рендер
читает отсутствие ключа в БД → возвращает дефолт из кода →
byte-идентичный текущим хардкод-строкам результат (FR-015, SC-002).
Вид ленты новостей не меняется.