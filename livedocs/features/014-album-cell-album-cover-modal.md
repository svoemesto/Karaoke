---
status: Active
slug: 014-album-cell-album-cover-modal
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/014-album-cell-album-cover-modal/spec.md
---

# 014 — Альбомы: клик по ячейке открывает модалку обложки (LiveDoc)

> Drill-down — [specs/014-album-cell-album-cover-modal/spec.md](../../specs/014-album-cell-album-cover-modal/spec.md).

## Что делает

Админ в `webvue3` → «Альбомы» → клик по preview обложки в колонке
«(альбом)» → открывается модалка «Обложка альбома» (тот же UI, что
в `SongEdit.vue` → кнопка «Изменить обложку альбома»).

Модалка: превью + поле поиска + чекбокс «Не искать в Яндекс.Музыке» +
кнопки «Найти в интернете» / «Загрузить с диска» + кадрирование +
сохранение `LogoAlbum.png`.

## User Stories (краткий список)

- **US1** (P1): Клик по preview в «Альбомы» открывает модалку обложки.

## Functional Requirements (указатель)

- **FR-001**: `AlbumsTable.vue` — click handler по `.album-cover-cell`.
- **FR-002**: Переиспользование компонента `AlbumCoverModal.vue`.

## Acceptance Criteria

- [ ] **AC1**: Клик по preview открывает модалку.
- [ ] **AC2**: Логика модалки идентична SongEdit.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Album)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Frontend: `webvue3/src/components/Albums/AlbumsTable.vue` — click handler
- Frontend: `webvue3/src/components/Albums/AlbumCoverModal.vue` (вынести из SongEdit)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14