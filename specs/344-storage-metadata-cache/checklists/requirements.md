# Specification Quality Checklist: Storage metadata cache (OpenProject #69)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-09
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *implementation notes (PollingCache, Spring DI) зафиксированы в Knowledge References, а не как требования; user scenarios — на уровне business outcome.*
- [x] Focused on user value and business needs — *P1 stories отвечают на «почему админ не может работать», привязаны к OpenProject #69.*
- [x] Written for non-technical stakeholders — *User Story 1 описан на языке админа (открыл страницу → ждал → обновил), без упоминания Spring/HTTP.*
- [x] All mandatory sections completed — *Knowledge References, User Scenarios & Testing, Requirements, Success Criteria, Assumptions — все заполнены.*

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — *0 маркеров; 8 разумных дефолтов вынесены в Assumptions.*
- [x] Requirements are testable and unambiguous — *FR-001..FR-014 + NFR-001..NFR-003 — каждый с конкретным поведением/числом/форматом.*
- [x] Success criteria are measurable — *SC-001: «≤5 секунд», SC-002: «≥90% hit-rate», SC-001: «улучшение ≥60×».*
- [x] Success criteria are technology-agnostic (no implementation details) — *«in-memory lookup < 1ms p99», «5 minutes TTL» — не упоминают Spring/PollingCache.*
- [x] All acceptance scenarios are defined — *5 сценариев в Given/When/Then для User Story 1, 2 — для Story 2, 2 — для Story 3.*
- [x] Edge cases are identified — *TTL=0, TTL>>5min, MinIO down, URL-encoded имена, key collision local/remote, race #65.*
- [x] Scope is clearly bounded — *только metadata cache; repair-loop race (#65) — НЕ в скоупе; bulk-checkIfExists — НЕ в скоупе; persistence (P3) — НЕ в первом раунде.*
- [x] Dependencies and assumptions identified — *8 явных Assumptions (TTL=300s, копирование PollingCache, без AOP, etc.).*

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — *FR-001..FR-014 привязаны к User Stories 1-3.*
- [x] User scenarios cover primary flows — *Story 1 — web UI, Story 2 — repair-loop, Story 3 — observability, Story 4 — future extension.*
- [x] Feature meets measurable outcomes defined in Success Criteria — *SC-001..SC-005 конкретно измеримы и проверяемы через /api/health/cacheStats + grep по infra.cache.storage.*
- [x] No implementation details leak into specification — *Implementation отделён в Knowledge References (PollingCache, Spring DI, AOP). FR-011 явно фиксирует «не менять сигнатуру публичных методов».*

## Knowledge Compliance (MANDATORY per Constitution Principle IX + AGENTS.md MUST #0)

- [x] Knowledge pre-flight выполнен ДО написания спеки (5 grep-запросов, 13 документов прочитаны, см. секцию «Pre-flight log» в spec.md).
- [x] Не изобретена новая форма кеша: использован существующий **готовый `PollingCache<V>`** из `knowledge/domains/caching/components/web-caches.md` (явная рекомендация Knowledge, в отличие от провалившейся спеки #339).
- [x] Соблюдены Domain Invariants из `knowledge/domains/storage/domain.md`:
  - `fileExists` и `fileIsActual` остаются single source of truth (FR-011).
  - Upload/download через существующие сервисы (cache — metadata-only).
  - `bucketName` НЕ хардкодится (идёт как параметр).
  - MinIO-операции допустимы в блокирующем контексте для metadata (FR-014).
- [x] Соблюдены Domain Invariants из `knowledge/domains/caching/domain.md`:
  - Cron не нужен (по требованию lazy).
  - Single-flight guard НЕ нужен для metadata (race редок для ~50ms round-trip).
  - Async cold-start — допустимо (lенивый режим).
- [x] Регистрация новой SLF4J-категории `infra.cache.storage` в `knowledge/domains/monitoring/components/log-categories.md` зафиксирована как FR-010.
- [x] Конвенция структурированного логирования из ADR `local-0005-structured-logging-karaoke-app.md` соблюдена (FR-007: structured key=value через пробел).

## Constitution Check (NON-NEGOTIABLE per Constitution § Governance)

- [x] **Principle I (Self-contained pipeline)**: фича не вводит внешних зависимостей; использует существующий MinIO + in-memory ConcurrentHashMap. **OK**.
- [x] **Principle II (raw JDBC, O(n) diff)**: фича НЕ трогает persistence. Никакого JPA. **OK**.
- [x] **Principle III (SyncRegistry)**: фича НЕ трогает sync между LOCAL↔SERVER (metadata cache об одном instance). **OK**.
- [x] **Principle IV (Async-очередь / ProcessBuilder)**: фича НЕ использует ProcessBuilder напрямую (metadata cache — чисто in-memory). `fileExists` блокирующий — это существующий контракт, спека НЕ меняет его. **OK**.
- [x] **Principle V (двух-фронтенд)**: фича живёт только в `karaoke-app` (admin-side metadata), НЕ трогает `webvue3`/`karaoke-public`. **OK**.
- [x] **Principle VI (Code Standards, FR-006/FR-009)**: KDoc + per-feature doc обязательны. FR-009 явно требует обновить `docs/features/<slug>.md` в одном PR. **OK**.
- [x] **Principle VII (Cross-Machine Setup)**: фича не требует локальных AI-конфигов, не меняет line endings. **OK**.
- [x] **Principle VIII (Secrets)**: фича НЕ логирует секретов, не работает с `.env`. Cache key — это `bucket/name`, не секрет. **OK**.
- [x] **Principle IX (Knowledge-first)**: см. «Knowledge Compliance» выше. **OK**.

## Notes

- **Прецедент**: Спека #339 (2026-09-09) провалилась потому, что агент изобрёл `storage_file_cache` таблицу в БД, не зная про `PollingCache`. В данной спеке это явно избегается через FR-009.
- **Связь с задачей #65**: Pass 343 уже починил repair-loop race; `fileExists` race остаётся. Кеш смягчает последствия race, но НЕ чинит root cause.
- **Расширения (P3)**: persisted write-through — отдельная будущая спека, не входит в scope #344.
- Все пункты **passed** — спека готова к `/speckit.plan`.
- Items marked incomplete require spec updates before `/speckit.clarify` or `/speckit.plan`.
