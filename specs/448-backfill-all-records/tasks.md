# Tasks: Backfill etag/size по всем записям кеша (Pass 448, #181)

**Input**: `specs/448-backfill-all-records/{spec,plan}.md`

## Phase 1: Изменение выборки

- [x] T001 `CacheEtagSizeBackfill.kt` — `loadRowsNeedingBackfill()`: `WHERE NOT exists OR size IS NULL OR etag IS NULL OR etag = ''`.
- [x] T002 `CacheEtagSizeBackfill.kt` — обновить KDoc `backfillCacheEtagSize`.

**Checkpoint**: `:karaoke-app:compileKotlin` — OK.

## Phase 2: Тесты

- [x] T003 `CacheEtagSizeBackfillTest` — тесты exists-независимости решения.

**Checkpoint**: test 6/6 PASS.

## Phase 3: Docs & PR

- [x] T004 `docs/features/storage-metadata-cache.md` — V2.11 + #181.
- [x] T005 `knowledge/domains/storage/domain.md` — раздел backfill.
- [x] T006 `report.md`.
- [ ] T007 ktlint + pre-commit + PR; CI; merge.
- [ ] T008 tracker: add-comment + mark-review.

## Dependencies

Phase 1 → 2 → 3.
