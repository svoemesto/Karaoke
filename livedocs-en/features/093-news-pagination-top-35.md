---
status: Active
slug: 093-news-pagination-top-35
related:
  - ../domain/publishing.md
  - ../features/090-news-pagination.md
  - ../architecture/L3-components.md
  - ../../specs/093-news-pagination-top-35/spec.md
---

# 093 — Новости: пагинация НАД таблицей, ≤35 строк на страницу (LiveDoc)

> Drill-down — [specs/093-news-pagination-top-35/spec.md](../../specs/093-news-pagination-top-35/spec.md).

## What it does

В админ-таблице «Новости» (`webvue3`) пагинация переехала **над таблицей**
(не под ней), и **≤ 35 строк на страницу**.

**Причина**: при 19000+ новостях (из `089-auto-news-song-release`) — пагинация
под таблицей заставляет админа скроллить к подвалу таблицы для переключения
страницы (неудобно при работе с верхней частью таблицы).

**Поведение остаётся** то же: создание / редактирование / удаление / переключение
LOCAL↔REMOTE / переход на конкретную страницу.

## User Stories (краткий список)

- **US1** (P1): Элемент пагинации виден до прокрутки (над таблицей).
- **US2** (P2): ≤ 35 строк на странице (19000 строк сейчас по умолчанию 50).

## Functional Requirements (указатель)

- **FR-001**: Элемент пагинации — выше `<table>`.
- **FR-002**: Default `per_page = 35` в `NewsTable.vue` (вместо 50).

## Acceptance Criteria

- [ ] **AC1**: Пагинация видна до прокрутки (над таблицей).
- [ ] **AC2**: При 100 новостях — > 1 страница, на каждой ≤ 35 строк.
- [ ] **AC3**: При 35 новостях — 1 страница.

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news lifecycle)
- Feature: [090-news-pagination.md](../features/090-news-pagination.md) (базовая пагинация)

## Code

- Frontend: `webvue3/src/components/News/NewsTable.vue` — пагинация над таблицей
- Frontend: `webvue3/src/components/News/NewsPagination.vue` (вынести в отдельный компонент)

## History

- Created: 2026-08-14
- Last updated: 2026-08-14