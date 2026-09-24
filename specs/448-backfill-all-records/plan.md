# Implementation Plan: Backfill etag/size по всем записям кеша

**Branch**: `448-backfill-all-records` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

## Summary

Одно изменение в SQL-выборке `loadRowsNeedingBackfill()`: убрать условие
`exists = true` → обрабатывать все строки, требующие проверки. `decideBackfillAction`
и логика прогона уже поддерживают `exists=false` (size<0 → MARK_MISSING,
size>=0 → UPDATE), поэтому функциональные изменения минимальны.

## Technical Context

**Language/Version**: Kotlin (JVM 21)
**Primary Dependencies**: JDBC (LOCAL Postgres), `KaraokeStorageService`, `StorageApiClient`
**Testing**: JUnit 5 (`CacheEtagSizeBackfillTest`)
**Constraints**: не менять сигнатуры; фон + single-flight + SSE (уже есть)
**Scale/Scope**: ~81 213 строк (80 279 LOCAL + 934 REMOTE)

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (spec.md).
- **Tier-1 Git CI-gate** — feature-ветка + PR.
- **Tier-1 Knowledge SSoT** — обновлены `knowledge/` + `docs/features/`.

Нарушений нет.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfill.kt   # MODIFY: SQL-выборка + KDoc
karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/CacheEtagSizeBackfillTest.kt # MODIFY: +2 теста
docs/features/storage-metadata-cache.md   # MODIFY: V2.11 + #181
knowledge/domains/storage/domain.md       # MODIFY: раздел backfill
specs/448-backfill-all-records/           # NEW: spec/plan/tasks/report
```

## Phase 1 — Изменение выборки

1. `loadRowsNeedingBackfill()`: `WHERE NOT exists OR size IS NULL OR etag IS NULL OR etag = ''`.
2. Обновить KDoc `backfillCacheEtagSize`.

**Checkpoint**: `:karaoke-app:compileKotlin` OK.

## Phase 2 — Тесты

3. `CacheEtagSizeBackfillTest`: тесты, фиксирующие exists-независимость решения.

**Checkpoint**: `test --tests "*CacheEtagSizeBackfillTest*"` PASS.

## Phase 3 — Docs & PR

4. `docs/features/storage-metadata-cache.md`, `knowledge/domains/storage/domain.md`, `report.md`.
5. ktlint, pre-commit, PR, CI, merge, tracker.

## Risks

- **Время прогона**: ~81k `getFileInfo`. LOCAL ~10-30ms → минуты; REMOTE circuit-aware.
  Фон + SSE (не блокирует UI).
- **REMOTE circuit OPEN**: часть строк SKIP → повторный запуск доберёт (идемпотентно).
- **Совместимость idempotency**: повторный прогон обрабатывает только строки, снова
  ставшие требующими проверки (exists=false или пустые info).

## Ready for implementation
