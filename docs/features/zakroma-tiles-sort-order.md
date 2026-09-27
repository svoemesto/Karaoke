# Zakroma Tiles Sort Order (спец-авторы в начало + sort_order в tbl_authors)

> **Status**: active
> **Feature Key**: zakroma-tiles-sort-order
> **Last Updated**: 2026-09-27
> **Branch**: `307-special-authors-zakroma-order`
> **Spec**: [`specs/307-special-authors-zakroma-order/spec.md`](../../specs/307-special-authors-zakroma-order/spec.md)

## Что делает

1. **Плашка «Отдельные песни разных авторов»** отображается **первой** в сетке публичной страницы `/zakroma` (а не последней, как было раньше).
2. Редактор может задавать **явный порядок плашек авторов** через поле `tbl_authors.sort_order` (`INTEGER NOT NULL DEFAULT 0`):
   - `sort_order = 0` — алфавитный порядок по имени автора (как раньше).
   - `sort_order != 0` — принудительный порядок **выше** нулевых (по возрастанию `sort_order`, затем по алфавиту).
   - Отрицательные значения допустимы и идут раньше нуля (Clarification Q1 спеки).

## Зачем

Открытая задача №56 (OpenProject): спец-плашка «Отдельные песни разных авторов»
висела **последней** в сетке Закромов и воспринималась как «случайный остаток», а
не как легитимная категория. Требовалось сделать её первой — чтобы композиция
страницы честно читалась как «обычные авторы + отдельная группа спецзаказных».

Второй запрос: у редактора не было способа управлять порядком плашек авторов —
только алфавит. Поле `sort_order` даёт явный приоритет и позволяет поднять нужного
автора (например, тематический раздел вроде «Саундтреки») наверх ещё до того, как
его первая песня пройдёт пайплайн: ненулевой `sort_order` делает автора видимым на
публичной поверхности даже при `ready_songs_count = 0`.

## Как работает

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

В `Author.loadAuthorTilesWithCounts`
([`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt)):

```sql
SELECT id, author, ready_songs_count, total_songs_count, is_special_order, sort_order
FROM tbl_authors
WHERE skip = false                                    -- или TRUE для редакторов с includeSkipped=true
  AND is_special_order = false                        -- зависит от scope
  AND (
    ready_songs_count > 0                             -- обычные авторы (как раньше)
    OR sort_order != 0                                -- принудительный порядок (Pass 310/311)
  )                                                   -- для onlyPublished=true;
                                                      -- для onlyPublished=false — total_songs_count > 0
ORDER BY (sort_order = 0), sort_order ASC, author ASC  -- Pass 311: ненулевые ПЕРЕД нулевыми
```

**Важно**: `(sort_order = 0)` — boolean-выражение, Postgres сортирует `false < true`,
поэтому ненулевые (`false`) идут **раньше** нулевых (`true`). Внутри обеих групп —
`sort_order ASC`, тай-брейкер — `author ASC`.

### Публичный API: `GET /api/public/authors-tiles`

- **Endpoint**: `GET /api/public/authors-tiles?scope=main`
- **Response**: `List<AuthorTilePublicDto>`
  ([`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AuthorTilePublicDto.kt))
- **Новое поле** в каждом элементе: `sortOrder: int` (default `0`).
- **Backward compatible**: добавление поля не ломает существующих клиентов.

### Виртуальная плашка «Отдельные песни разных авторов»

- В [`karaoke-public/src/components/AuthorTiles.vue`](../../karaoke-public/src/components/AuthorTiles.vue) добавлен слот `<slot name="leading" />` (рендерится ПЕРЕД обычными тайлами).
- В [`karaoke-public/src/views/ZakromaView.vue`](../../karaoke-public/src/views/ZakromaView.vue) плашка переехала из `<template #trailing>` в `<template #leading>`.
- Визуальное отличие сохранено: иконка папки вместо `<img>` автора. Никаких новых визуальных эффектов (Clarification Q6).

### Админка: колонка `sort_order` в AuthorsTable.vue

[`webvue3/src/components/Authors/AuthorsTable.vue`](../../webvue3/src/components/Authors/AuthorsTable.vue):

- Колонка отображает `sortOrder` как редактируемый `<input type="number">`.
- Изменение сохраняется через существующий `setAuthorValuePromise` action (`POST /api/authors/updateauthor`).
- Валидация: целое число, опционально отрицательное, clamp в диапазон `INTEGER`.
- Стилизация: рамка при фокусе/редактировании (`.fld-sortOrder:focus`, `.fld-sortOrder-editing`).

## Инварианты

Правила проекта, релевантные фиче: [constitution.md](../../.specify/memory/constitution.md)
(принцип IX — Knowledge SSoT) и [AGENTS.md](../../AGENTS.md) (sync LOCAL↔SERVER,
обязательная проверка после изменения).

- **MUST**: порядок в публичной сетке — `(sort_order = 0), sort_order ASC, author ASC`;
  ненулевые значения идут перед нулевыми
  ([spec 307 Quickstart](../../specs/307-special-authors-zakroma-order/quickstart.md)).
- **MUST**: `sort_order != 0` делает автора видимым на публичной поверхности даже
  при `ready_songs_count = 0` (принудительная видимость).
- **MUST**: `sort_order` входит в `recordhash` `tbl_authors` — иначе sync
  LOCAL↔SERVER не переносил бы изменение
  ([двух-БД sync](../../knowledge/domains/processing/components/two-db-sync.md)).
- **MUST**: миграция идемпотентна (`ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION`).
- **MUST**: миграция применяется **вручную на LOCAL и на PROD** в одном PR;
  рассинхрон схемы проявляется как `column "sort_order" does not exist` при первом
  sync.
- **MUST**: API `/api/public/authors-tiles` остаётся backward compatible —
  `sortOrder` только добавляется.
- **SHOULD**: админ-таблица `AuthorsTable.vue` сохраняет свою серверную
  сортировку; `sort_order` там — только редактируемая колонка (Clarification Q3).
- **SHOULD**: инвалидация кеша — только по TTL ≤60с, нового hook'а не вводится
  (Clarification Q5).

## Известные ловушки

- **Наивный `ORDER BY sort_order ASC` ставит нули перед отрицательными** —
  противоречит спеке. Работает только форма `(sort_order = 0), sort_order ASC`:
  Postgres сортирует `false < true`, поэтому нулевые уезжают в конец.
- **KDoc `Author.kt` (строки 97-104) описывает старую формулировку**
  (`ORDER BY sort_order ASC, author ASC`) — читать её как источник истины нельзя;
  фактический SQL в `loadAuthorTilesWithCounts` другой.
- **`sort_order = 0` — это «по алфавиту», а не «без сортировки»**: чтобы
  зафиксировать автора в произвольной позиции, нужно ненулевое значение (в т.ч.
  отрицательное).
- **Кеш `authorsTilesCache` (TTL ≤60с)** — изменение `sort_order` в админке
  видно на `/zakroma` не мгновенно, а в пределах минуты.
- **Схема LOCAL и PROD расходится, если миграцию применили только с одной
  стороны** — первый же sync `tbl_authors` падает с
  `column "sort_order" does not exist`.
- **Спец-плашка не отображается, если нет ни одного автора с
  `is_special_order = true`** (например, в редакторской выборке) — гейт по
  непустому `specialBucket` сохранён, это не регресс.

## Ссылки

- [`specs/307-special-authors-zakroma-order/spec.md`](../../specs/307-special-authors-zakroma-order/spec.md) — полная спецификация (User Stories, FR, SC, Clarifications).
- [`specs/307-special-authors-zakroma-order/research.md`](../../specs/307-special-authors-zakroma-order/research.md) — технические решения (R1–R7).
- [`specs/307-special-authors-zakroma-order/data-model.md`](../../specs/307-special-authors-zakroma-order/data-model.md) — модель данных.
- [`specs/307-special-authors-zakroma-order/contracts/authors-tiles-api.md`](../../specs/307-special-authors-zakroma-order/contracts/authors-tiles-api.md) — контракт API.
- [`specs/307-special-authors-zakroma-order/quickstart.md`](../../specs/307-special-authors-zakroma-order/quickstart.md) — пошаговая валидация.
- [`deploy/karaoke-db/46_author_sort_order.sql`](../../deploy/karaoke-db/46_author_sort_order.sql) — миграция.
- [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt) — `loadAuthorTilesWithCounts` (SQL ORDER BY).
- [`karaoke-public/src/components/AuthorTiles.vue`](../../karaoke-public/src/components/AuthorTiles.vue) — слот `leading`.
- [`karaoke-public/src/views/ZakromaView.vue`](../../karaoke-public/src/views/ZakromaView.vue) — перенос плашки в `#leading`.
- [`webvue3/src/components/Authors/AuthorsTable.vue`](../../webvue3/src/components/Authors/AuthorsTable.vue) — колонка `Sort`.
- [`specs/008-special-orders/spec.md`](../../specs/008-special-orders/spec.md) — историческая спека по спец-плашке.

## Сценарии

- **Спец-плашка первой в сетке** — открыть `/zakroma`, первая ячейка в первой строке — «Отдельные песни разных авторов» (иконка папки).
- **Sort_order влияет на порядок** — задать `sort_order` автора в БД, обновить `/zakroma` (после ≤60с TTL кеша), автор появится перед всеми `sort_order=0` или в нужной позиции.
- **Редактирование в админке** — в `webvue3/src/components/Authors/AuthorsTable.vue` ввести новое значение в колонке `Sort`, нажать Enter/Tab — значение сохраняется в БД.

## Контрактные точки (для будущих фич)

- Кеш `authorsTilesCache` (`PublicApiController.getCachedAuthorsTiles`) — TTL ≤60с; нового hook'а не введено (Clarification Q5).
- Sync LOCAL↔SERVER — `sort_order` входит в `recordhash`; sync автоматически работает.
- API `/api/public/authors-tiles` — backward compatible (только добавление поля).
