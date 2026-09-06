---
status: Active
slug: 307-special-authors-zakroma-order
related:
  - ../../specs/307-special-authors-zakroma-order/spec.md
  - ../../docs/features/zakroma-tiles-sort-order.md
  - ../features/307-orm-field-save-paths.md
  - ../runbooks/how-to-add-orm-field.md
---

# 307 — Спец-авторы в начало Закромов + sort_order в tbl_authors (LiveDoc)

> Drill-down — [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md).
> Per-feature документ — [docs/features/zakroma-tiles-sort-order.md](../../docs/features/zakroma-tiles-sort-order.md).
> Урок и 4+1 пути записи ORM-поля — [livedocs/features/307-orm-field-save-paths.md](307-orm-field-save-paths.md).

## Что делает

Две связанных правки публичных Закромов и порядка плашек авторов:

1. **Спец-плашка «Отдельные песни разных авторов»** теперь отображается
   **первой** в сетке `/zakroma` (а не последней, как раньше).
2. **Новое поле `tbl_authors.sort_order`** (`INTEGER NOT NULL DEFAULT 0`)
   позволяет редактору управлять порядком плашек:
   - `sort_order = 0` — алфавитный порядок (как раньше).
   - `sort_order != 0` — принудительный порядок **выше** нулевых
     (по возрастанию, отрицательные раньше всех).
   - Такие авторы видны **даже без готовых песен** (Pass 311 — например,
     «Саундтреки» теперь первый в публичной сетке).

## User Stories (краткий список)

- **US1** (P1) — Спец-плашка «Отдельные песни разных авторов» отображается первой.
- **US2** (P1) — Авторы с заданным `sort_order` отображаются перед неотсортированными.
- **US3** (P2) — Редактор может менять `sort_order` у автора из админки.

## Functional Requirements (указатель)

- **FR-001/002**: миграция `46_author_sort_order.sql` (идемпотентна; recordhash пересоздан).
- **FR-003**: SQL `ORDER BY (sort_order = 0), sort_order ASC, author ASC` (Pass 311).
- **FR-004**: поле `sortOrder` в `Author` + `AuthorDTO` + `AuthorTilePublicDto`.
- **FR-005**: перенос спец-плашки из слота `#trailing` → `#leading` в `ZakromaView.vue`.
- **FR-006/007**: поле `sortOrder` в API `/authors-tiles` (backward compatible).
- **FR-008**: редактируемая колонка `Sort` в `AuthorsTable.vue`.
- **FR-013**: пункт в PR-чеклисте про миграцию на LOCAL и PROD.
- См. полный список в [spec.md](../../specs/307-special-authors-zakroma-order/spec.md).

## Acceptance Criteria

- [ ] AC1 (US1): открыть `/zakroma` → первый тайл — «Отдельные песни разных авторов» (📁).
- [ ] AC2 (US2): SQL возвращает порядок — отрицательные → положительные ненулевые → нулевые по алфавиту.
- [ ] AC3 (US2): авторы с `sort_order != 0` видны в публичной сетке даже без готовых песен (например, «Саундтреки»).
- [ ] AC4 (US3): в админке можно отредактировать `Sort` → значение сохраняется в БД через `setAuthorValuePromise` → `apisUpdateAuthor`.
- [ ] AC5: миграция применена на LOCAL и PROD (обе БД имеют `tbl_authors.sort_order`).

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) — bounded context «Каталог авторов».
- Feature: [307-orm-field-save-paths.md](307-orm-field-save-paths.md) — урок про 4+1 путей записи ORM-поля.
- Architecture: [L3-components.md](../architecture/L3-components.md).
- Runbook: [how-to-add-orm-field.md](../runbooks/how-to-add-orm-field.md) — процесс добавления ORM-поля.

## Код

- Миграция: `deploy/karaoke-db/46_author_sort_order.sql`.
- Модель: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`
  (поле `sortOrder` + `loadAuthorTilesWithCounts`).
- DTO: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AuthorDTO.kt`,
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`.
- Контроллер: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt::authorsTiles`,
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt::apisUpdateAuthor`.
- Frontend (публичный): `karaoke-public/src/components/AuthorTiles.vue`,
  `karaoke-public/src/views/ZakromaView.vue`.
- Frontend (админка): `webvue3/src/components/Authors/AuthorsTable.vue`.

## История

- Создан: 2026-09-06 (Pass 307).
- Обновлён: 2026-09-06 (Pass 311 — fixup: правильный ORDER BY + видимость при `sort_order != 0`).
