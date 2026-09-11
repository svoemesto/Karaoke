# Zakroma Tiles Sort Order (спец-авторы в начало + sort_order в tbl_authors)

> **Slug**: `zakroma-tiles-sort-order`
> **Feature branch**: `307-special-authors-zakroma-order`
> **Spec**: [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md)
> **Status**: Implemented (Pass 310+)

## Что делает фича

1. **Плашка «Отдельные песни разных авторов»** теперь отображается **первой** в сетке публичной страницы `/zakroma` (а не последней, как было раньше).
2. Редактор может задавать **явный порядок плашек авторов** через новое поле `tbl_authors.sort_order` (`INTEGER NOT NULL DEFAULT 0`):
   - `sort_order = 0` — алфавитный порядок по имени автора (как раньше).
   - `sort_order != 0` — принудительный порядок **выше** нулевых (по возрастанию `sort_order`, затем по алфавиту).
   - Отрицательные значения допустимы и идут раньше нуля (Clarification Q1 спеки).

## Контракт

### SQL: `tbl_authors.sort_order`

```sql
-- Миграция: deploy/karaoke-db/46_author_sort_order.sql
ALTER TABLE public.tbl_authors
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
```

- Тип: `INTEGER` (4 байта, диапазон `-2147483648` … `2147483647`).
- Default: `0`.
- NOT NULL.
- Колонка входит в `recordhash` `tbl_authors` (обновлённый триггер `update_tbl_authors_recordhash` — см. миграцию).
- Миграция **идемпотентна** (`ADD COLUMN IF NOT EXISTS` + `CREATE OR REPLACE FUNCTION`).

### SQL ORDER BY

В `Author.loadAuthorTilesWithCounts` (`karaoke-app/.../model/Author.kt`):

```sql
SELECT id, author, ready_songs_count, total_songs_count, is_special_order, sort_order
FROM tbl_authors
WHERE skip = false                                    -- или TRUE для редакторов с includeSkipped=true
  AND is_special_order = false                        -- зависит от scope
  AND (
    ready_songs_count > 0                             -- обычные авторы (как раньше)
    OR sort_order != 0                                -- принудительный порядок (Pass 311)
  )                                                   -- для onlyPublished=true;
                                                      -- для onlyPublished=false — total_songs_count > 0
ORDER BY (sort_order = 0), sort_order ASC, author ASC  -- Pass 311: ненулевые ПЕРЕД нулевыми
```

**Важно**: `(sort_order = 0)` — boolean-выражение, Postgres сортирует `false < true`,
поэтому ненулевые (`false`) идут **раньше** нулевых (`true`). Внутри обеих групп —
`sort_order ASC`, тай-брейкер — `author ASC`.

### Публичный API: `GET /api/public/authors-tiles`

- **Endpoint**: `GET /api/public/authors-tiles?scope=main`
- **Response**: `List<AuthorTilePublicDto>` (см. `karaoke-web/.../dto/AuthorTilePublicDto.kt`)
- **Новое поле** в каждом элементе: `sortOrder: int` (default `0`).
- **Backward compatible**: добавление поля не ломает существующих клиентов.

### Виртуальная плашка «Отдельные песни разных авторов»

- В `karaoke-public/src/components/AuthorTiles.vue` добавлен слот `<slot name="leading" />` (рендерится ПЕРЕД обычными тайлами).
- В `karaoke-public/src/views/ZakromaView.vue` плашка переехала из `<template #trailing>` в `<template #leading>`.
- Визуальное отличие сохранено: иконка 📁 вместо `<img>` автора. Никаких новых визуальных эффектов (Clarification Q6).

### Админка: колонка `sort_order` в `webvue3/src/components/Authors/AuthorsTable.vue`

- Колонка отображает `sortOrder` как редактируемый `<input type="number">`.
- Изменение сохраняется через существующий `setAuthorValuePromise` action (`POST /api/authors/updateauthor`).
- Валидация: целое число, опционально отрицательное, clamp в диапазон `INTEGER`.
- Стилизация: рамка при фокусе/редактировании (`.fld-sortOrder:focus`, `.fld-sortOrder-editing`).

## Сценарии

- **Спец-плашка первой в сетке** — открыть `/zakroma`, первая ячейка в первой строке — «Отдельные песни разных авторов» (📁).
- **Sort_order влияет на порядок** — задать `sort_order` автора в БД, обновить `/zakroma` (после ≤60с TTL кеша), автор появится перед всеми `sort_order=0` или в нужной позиции.
- **Редактирование в админке** — в `webvue3/src/components/Authors/AuthorsTable.vue` ввести новое значение в колонке `Sort`, нажать Enter/Tab — значение сохраняется в БД.

## Связанные документы

- [specs/307-special-authors-zakroma-order/spec.md](../../specs/307-special-authors-zakroma-order/spec.md) — полная спецификация (User Stories, FR, SC, Clarifications).
- [specs/307-special-authors-zakroma-order/research.md](../../specs/307-special-authors-zakroma-order/research.md) — технические решения (R1–R7).
- [specs/307-special-authors-zakroma-order/data-model.md](../../specs/307-special-authors-zakroma-order/data-model.md) — модель данных.
- [specs/307-special-authors-zakroma-order/contracts/authors-tiles-api.md](../../specs/307-special-authors-zakroma-order/contracts/authors-tiles-api.md) — контракт API.
- [specs/307-special-authors-zakroma-order/quickstart.md](../../specs/307-special-authors-zakroma-order/quickstart.md) — пошаговая валидация.
- [deploy/karaoke-db/46_author_sort_order.sql](../../deploy/karaoke-db/46_author_sort_order.sql) — миграция.
- [specs/008-special-orders/spec.md](../../specs/008-special-orders/spec.md) — историческая спека по спец-плашке.

## Контрактные точки (для будущих фич)

- Кеш `authorsTilesCache` (`PublicApiController.getCachedAuthorsTiles`) — TTL ≤60с; нового hook'а не введено (Clarification Q5).
- Sync LOCAL↔SERVER — `sort_order` входит в `recordhash`; sync автоматически работает.
- API `/api/public/authors-tiles` — backward compatible (только добавление поля).
