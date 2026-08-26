# Specification Quality Checklist: Батч-синхронизация sync-записей в KaraokeProcessWorker

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-1 / FR-101

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека сознательно ссылается на конкретные классы (`KaraokeProcessWorker.doStart`, `Song.loadListFromDbByIds`, `KaraokeDbTable.deleteBatch`) — это **fix конкретного hotspot** из parent спеки (file:line указаны в parent, A.1, H-2). Никаких JPA/Hibernate — сохранён Constitution § II.
- [x] Focused on user value and business needs
  - Цель: снизить нагрузку sync-цикла на БД в 20–40× при большом push LOCAL→SERVER.
- [x] Written for non-technical stakeholders
  - User Stories и SC — на языке бизнеса (SQL-запросы, latency, нагрузка на БД). Технические детали (chunk size, socketTimeout) — в Assumptions.
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
  - FR-001 … FR-006 — каждое с конкретным file:line (или file:line-range) и измеримым поведением.
- [x] Success criteria are measurable
  - SC-001: ≤ 11 SQL при 100 sync (vs 201), SC-002: ≤ 82 при 1000 (vs 2001), SC-003: ≥ 30% снижение в `pg_log`.
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001/SC-002 привязаны к «SQL-запросам» и `pg_log` — это намеренно: спека — fix hotspot в БД, метрики привязаны к PostgreSQL (Constitution § Технологический стек).
- [x] All acceptance scenarios are defined
  - 2 US × 1–3 сценария = 4 acceptance scenarios.
- [x] Edge cases are identified
  - 4 edge case'а: socketTimeout, sync-removed-between-queries, RENDER-тег для больших batch, исключение посередине.
- [x] Scope is clearly bounded
  - In scope: sync-блок в `KaraokeProcessWorker.doStart()`. Out of scope: рефакторинг всего doStart, INSERT-оптимизация, изменение RENDER-side-effect, `intervalCheckFiles`.
- [x] Dependencies and assumptions identified
  - Зависимости: `Song.loadListFromDbByIds` (есть), `KaraokeDbTable.deleteBatch` (возможно, нужно добавить). 6 assumptions описаны.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001/SC-002/SC-003 напрямую измеримы через `pg_log` после деплоя.
- [x] No implementation details leak into specification
  - Исключение: FR-003 ссылается на `SongSyncTarget.rowChunkSize = 25` — это часть контракта из parent спеки, обязательно для согласованности.

## Notes

- **Зависимость от parent спеки**: эта фича — Tier-1 / FR-101 из parent спеки `241-db-storage-perf-audit`. Все ссылки на hotspots, sync-паттерн, Constitutional Principle II — в parent.
- **Рекомендуется** проверить наличие `KaraokeDbTable.deleteBatch` ДО начала реализации (см. H-12 в parent спеке). Если отсутствует — добавить в этой же фиче как утилиту.
- **Риск**: при batch-DELETE в remote БД через `id = ANY(?)` нужно убедиться, что PostgreSQL JDBC драйвер поддерживает `Array` bind (должен по умолчанию, но проверить в тестах на реальной remote-БД).
- **Regression-риск**: side-effect для `tags = "RENDER"` (создание karaoke-процессов) остаётся per-record. После рефакторинга ОБЯЗАТЕЛЬНО проверить, что `KaraokeProcess.createProcess(songLocal, action = MELT_LYRICS, ...)` всё ещё вызывается для каждой RENDER-записи (см. SC-005).
- **Backward compatibility**: контракт `KaraokeDbTable.loadList(whereList = listOf("id IN (...)"))` сохраняется (это паттерн `loadListFromDbByIds`). Никаких изменений в публичных API.
- **Тестирование**: автоматических тестов нет (см. Constitution § Тесты — `@Disabled`). Проверка — пользователем через deploy + `pg_log`.
