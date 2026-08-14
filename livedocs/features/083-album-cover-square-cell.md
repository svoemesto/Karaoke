---
status: Active
slug: 083-album-cover-square-cell
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/083-album-cover-square-cell/spec.md
---

# 083 — Альбомы: квадратная ячейка обложки (LiveDoc)

> Drill-down — [specs/083-album-cover-square-cell/spec.md](../../specs/083-album-cover-square-cell/spec.md).

## Что делает

В админ-таблице «Альбомы» (`webvue3`) ширина колонки с обложкой альбома
(= ширина ячейки) теперь **равна высоте строки** (квадрат). Изображение (или
плейсхолдер «Нет изображения») вписано в квадрат без искажений.

Колонка с обложкой автора — без изменений (current behavior).

**Эффект**: картинки разного соотношения сторон больше не «прыгают» по ширине.

## User Stories (краткий список)

- **US1** (P1): Ячейка обложки альбома — квадрат.

## Functional Requirements (указатель)

- **FR-001**: CSS: `width = height = <row-height>` для `.album-cover-cell`.
- **FR-002**: `<img>` `object-fit: cover` (cover, не contain).

## Acceptance Criteria

- [ ] **AC1**: Таблица «Альбомы» → ячейка обложки квадратная.
- [ ] **AC2**: Изображения вписаны без искажений.
- [ ] **AC3**: Колонка автора — без изменений.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Album)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Frontend: `webvue3/src/components/Albums/AlbumsTable.vue` — cell width = row height
- CSS: `webvue3/src/assets/main.css` — `.album-cover-cell { width: 36px; height: 36px; object-fit: cover; }` (пример)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14