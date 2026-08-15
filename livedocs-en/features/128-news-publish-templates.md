---
status: Active
slug: 128-news-publish-templates
related:
  - ../domain/publishing.md
  - ../features/089-auto-news-song-release.md
  - ../architecture/L3-components.md
  - ../../specs/128-news-publish-templates/spec.md
  - ../../archive/docs/features/news-templates.md
---

# 128 — Шаблоны автоматических новостей (LiveDoc)

> Drill-down — [specs/128-news-publish-templates/spec.md](../../specs/128-news-publish-templates/spec.md).

## What it does

Авто-новости сайта (`specs/089-auto-news-song-release`, `category="air"`,
`category="collection"`) сейчас формируются **хардкод-строками** в Kotlin
(`title = "Новая песня: ${author} — ${songName}${albumYearSuffix}"`).

**Фикс**: добавить в админ-компонент «Шаблоны публикаций» (`/templates`)
шаблоны для авто-новостей, с поддержкой плейсхолдеров — отдельно для
**заголовка** и **текста** новости:
- `{author}`, `{songName}`, `{album}`, `{year}`, `{playlist}`,
  `{publishedAt}`, `{link}`, и т.п.

Хранение — в `tbl_templates` (как существующие шаблоны Telegram/ВК).

## User Stories (краткий список)

- **US1** (P1): Шаблон «Новая песня в эфире» редактируется через админку.
- **US2** (P1): Шаблон «Новая песня в коллекции» редактируется через админку.

## Functional Requirements (указатель)

- **FR-001**: 2 новых шаблона в `tbl_templates` (id + category).
- **FR-002**: Поддержка плейсхолдеров `{...}`.
- **FR-003**: UI редактирования в `TemplatesView.vue`.
- **FR-004**: `SongReleaseAnnouncementService` использует шаблон вместо хардкода.

## Acceptance Criteria

- [ ] **AC1**: Изменить шаблон «В эфире» → следующие новости используют новый формат.
- [ ] **AC2**: Плейсхолдер `{author}` подставляет имя исполнителя.
- [ ] **AC3**: Шаблоны сохраняются и применяются без перезапуска.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news lifecycle)
- Feature: предыдущая — auto-news-song-release (см. `specs/089-auto-news-song-release`)
- Architecture: [L3-components.md](../architecture/L3-components.md) (scheduler + template renderer)

## Code

- Backend: `karaoke-app/.../service/SongReleaseAnnouncementService.kt` — заменить хардкод на `TemplateService.render()`
- Backend: `karaoke-app/.../service/TemplateService.kt` — рендер плейсхолдеров
- Backend: `karaoke-app/.../controller/TemplatesController.kt` — CRUD
- Frontend: `webvue3/src/views/TemplatesView.vue` — две новых формы
- SQL: миграции в `deploy/karaoke-db/` (если `tbl_templates` уже есть — не нужно)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14