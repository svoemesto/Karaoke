# Specification Quality Checklist: Schema-cache в KaraokeDbTable.loadList (FR-102)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-26
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Примечание: спека упоминает конкретные символы (`KaraokeDbTable.Companion.columns()`,
    `KaraokeProperties.getBoolean`, `ConcurrentHashMap`, `Pair<String, String>`) — это **имена
    существующих единиц**, на которые накладываются требования, а не выбор технологического стека.
    Никаких «использовать Caffeine», «Redis», «Spring Cache» — фреймворк не навязан, выбран
    `java.util.concurrent.ConcurrentHashMap` из стандартной библиотеки (минимум зависимостей).

- [x] Focused on user value and business needs
  - Сценарии US1/US2 сформулированы от лица разработчика/админа (вызывающий код loadList;
    пост-миграционный хук), метрики — пользовательские для прод-сайта (отсутствие лишних
    SQL round-trip'ов на каждое обращение, актуальность кеша после миграции).

- [x] Written for non-technical stakeholders
  - Описания сценариев в терминах поведения («на втором вызове SQL к `information_schema.columns`
    НЕ выполняется», «TTL=1ч»), а не кода. Технические термины (TTL, schema-cache,
    `information_schema.columns`) — устоявшаяся БД-терминология, понятная любому
    разработчику/админу.

- [x] All mandatory sections completed
  - User Scenarios & Testing (US1 P1, US2 P2 + 6 Edge Cases), Requirements (FR-001..FR-010),
    Key Entities (SchemaCacheEntry, schemaCache, SCHEMA_CACHE_TTL_MS), Success Criteria
    (SC-001..SC-005), Assumptions (A-001..A-004), Out of Scope — все заполнены.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все спорные моменты (поведение при пустом результате, поведение `ConcurrentModificationException`,
    fallback при недоступности `KaraokeProperties`) вынесены в FR-003, FR-007 (try/catch),
    Assumptions A-001..A-004 с разумными дефолтами. Дополнительных уточнений не требуется.

- [x] Requirements are testable and unambiguous
  - FR-001..FR-010 сформулированы MUST-формой, каждое проверяемо (лог PostgreSQL, ручной вызов
    `invalidateSchemaCache`, изменение свойства `karaoke.db.schema_cache.enabled`).

- [x] Success criteria are measurable
  - SC-001 (SQL отсутствует на 2-м вызове — лог PG), SC-002 (поведение при `enabled=false` —
    лог PG), SC-003 (поведение после `invalidateSchemaCache` — лог PG), SC-004 (compile/ktlint/
    bootJar — PASS/FAIL), SC-005 (бинарная совместимость — компиляция 41 caller без правок).

- [x] Success criteria are technology-agnostic (no implementation details)
  - SC-001..SC-003 ссылаются на «лог PostgreSQL» — это observability-инструмент, не фреймворк.
    Метрика «SQL отсутствует на 2-м вызове» может быть проверена любым способом (логи,
    tcpdump, метрики).

- [x] All acceptance scenarios are defined
  - US1 — 5 сценариев (cache miss, cache hit, TTL expiry, `enabled=false`, пустой результат).
    US2 — 4 сценария (clear, by tableName, by database, by pair). Покрыты основной поток +
    ключевые ветки (TTL, ошибки, отключение).

- [x] Edge cases are identified
  - 6 edge cases: `loadList` с `ignoreUseInList=true` (columns() не вызывается), ошибка
    подключения к БД, `database.name` совпадает у разных инстансов, TTL и долгоживущий
    процесс (без фоновой очистки), параллельные первые обращения (ConcurrentHashMap),
    отключение кеша через свойство.

- [x] Scope is clearly bounded
  - Фича покрывает ТОЛЬКО кеширование результата SQL к `information_schema.columns` в
    `KaraokeDbTable.Companion.columns()`. Все остальные SQL-операции в companion object
    (`getTotalCount`, `getListHashes`, `delete`, `deleteIn`) явно вне scope. Hot-reload
    настроек, персистентный кеш, метрики попаданий, авто-инвалидация через
    PostgreSQL `LISTEN/NOTIFY` — в Out of Scope.

- [x] Dependencies and assumptions identified
  - Assumptions: `KaraokeProperties` уже инициализирован к моменту первого обращения (A-001),
    TTL=1ч — приемлемый компромисс (A-002), `ConcurrentHashMap` достаточен (A-003),
    `database.name` — стабильный идентификатор (A-004). Зависимости: только стандартная
    библиотека Kotlin/Java (нет новых gradle-зависимостей).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001 (cache check) → SC-001 + US1 acceptance scenarios 1-2.
    FR-002 (cache put) → US1 scenario 1.
    FR-003 (no cache on empty/error) → US1 scenario 5.
    FR-004 (process-local) → дизайн-требование, проверяется через Plan § Структуры данных.
    FR-005 (4 режима invalidate) → US2 scenarios 1-4.
    FR-006 (вынос в `columnsFromDb`) → дизайн-требование, проверяется через Plan § Алгоритм.
    FR-007 (опциональное отключение) → US1 scenario 4 + SC-002.
    FR-008 (KDoc) → review checklist.
    FR-009 (прозрачность для 41 caller) → SC-005 (бинарная совместимость через компиляцию).
    FR-010 (immutability) → дизайн-требование, проверяется через `data class` + `val`-only.

- [x] User scenarios cover primary flows
  - US1 (P1 — основной поток кеширования), US2 (P2 — управляемая инвалидация). Дополнительные
    stories не нужны: фича внутренняя, observability через SC-метрики достаточна.

- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-005 — все проверяемы без знания внутренностей реализации (компиляция,
    логи PostgreSQL, ручной вызов методов).

- [x] No implementation details leak into specification
  - Упоминания файлов/символов — это **контрактные единицы**, на которые накладываются
    требования (например, «FR-001: `columns()` MUST проверить кеш перед SQL» — это требование
    к контракту метода, а не к его реализации). Никаких «использовать `Map.computeIfAbsent`»,
    «через Spring `@Cacheable`», «через Coroutine `Mutex`» — это уровень реализации, оставлен
    на усмотрение plan.md.

## Notes

- Спека готова к `/speckit.clarify` (если потребуются уточнения) или `/speckit.plan`.
- Главный риск — реальная сигнатура `KaraokeProperties.getBoolean(key)` в текущей кодовой базе:
  если она изменится между сейчас и реализацией, plan.md нужно будет скорректировать. Это
  проверено в Phase 1 (T003).
- Регистрация нового свойства `karaoke.db.schema_cache.enabled` в `listKaraokeProperties` —
  формальность, но без неё default = `false` (через `getBoolean` fallback), что не соответствует
  FR-007 (дефолт `true`). Поэтому T014 — обязательная часть US1.
- LiveDocs обновление (`livedocs/architecture-notes.md` + новая фича в `livedocs/features/`) —
  out of scope этого PR, отдельная задача T030 после мержа (FR-014 из AGENTS.md).
