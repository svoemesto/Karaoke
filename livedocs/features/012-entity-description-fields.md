---
status: Active
slug: 012-entity-description-fields
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../features/030-add-archive-album-type.md
  - ../../specs/012-entity-description-fields/spec.md
---

# 012 — Доп. поля Author/Album/Song + новый UI Закромов (LiveDoc)

> Drill-down — [specs/012-entity-description-fields/spec.md](../../specs/012-entity-description-fields/spec.md).

## Что делает

Новые поля у Author / Album / Song:
- **Описание** (по умолчанию пустое) — форматированный текст, интересные факты.
- **Короткое описание** (например, «Remastered 2018», «Live»).
- **Предупреждение** (например, «УЧАСТНИК ГРУППЫ ПРИЗНАН ИНОАГЕНТОМ»).

**На проде в Закромах**:
- Информация об альбоме — **из сущности Album** (а не из таблицы песен).
- Тип альбома — под названием.
- Короткое описание — серым через пробел после названия.
- Предупреждение — красным **над** названием.
- Описание — в тултипе на ховере.
- Аналогично для автора (в его блоке вверху закромов) и для песни (в её карточке).
- **Порядок альбомов** — по полю сортировки альбома.
- В шапке Закромов — переключатель «сквозной / группированный по типу» отображения альбомов (с заголовками-разделителями: студийные, синглы, концертные, сборники, бутлеги).
- Быстрые кнопки-фильтры по типу альбома со счётчиком (скрываются при отсутствии).

## User Stories (краткий список)

- **US1** (P1): Поля «Описание / Краткое / Предупреждение» в БД и UI.
- **US2** (P1): Предупреждение красным НАД названием (например, иногент).
- **US3** (P2): Закрома группируют альбомы по типу.

## Functional Requirements (указатель)

- **FR-001**: `description TEXT`, `shortDescription String`, `warning String` в Author/Album/Song.
- **FR-002**: UI — тултип / цвет / позиция по дизайну.
- **FR-003**: Album type — radio (Studio/Single/Live/Compilation/Bootleg + см. `030/031`).
- **FR-004**: Группировка в `ZakromaView.vue`.

## Acceptance Criteria

- [ ] **AC1**: 3 новых поля в БД и DTO.
- [ ] **AC2**: В Закромах — предупреждение красным над названием.
- [ ] **AC3**: Группировка по типу работает + фильтры.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Author, Album, Song), [publishing.md](../publishing.md)
- Feature: [030-add-archive-album-type.md](../features/030-add-archive-album-type.md), [031-add-tribute-cover-album-type.md](../features/031-add-tribute-cover-album-type.md)

## Код

- Backend: `karaoke-app/.../model/{Author,Album,Song}.kt` — 3 поля
- SQL: `deploy/karaoke-db/<NNN>_entity_descriptions.sql`
- Frontend: `webvue3/src/components/{Authors,Albums,Songs}/*EditModal.vue` — 3 поля
- Frontend: `karaoke-public/src/views/ZakromaView.vue` — UI группировки и предупреждения

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14