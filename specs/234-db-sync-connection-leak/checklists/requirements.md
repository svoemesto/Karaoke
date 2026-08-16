# Specification Quality Checklist: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-16
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — упоминаются Kotlin/SLF4J/JDBC как контекст существующего кода, не как стек для новой функциональности
- [x] Focused on user value and business needs — фикс утечки соединений, понятный лог, отсутствие регрессий
- [x] Written for non-technical stakeholders — User Stories описаны с точки зрения администратора («нажал кнопку → нет каскада ошибок»)
- [x] All mandatory sections completed — User Scenarios, Requirements, Key Entities, Success Criteria, Assumptions, Clarifications

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — все 3 закрыты решениями пользователя (Singleton + scope=Connection + логирование)
- [x] Requirements are testable and unambiguous — FR-001..FR-013 проверяемы (singleton-фабрика возвращает один инстанс; log.warn структурирован; etc.)
- [x] Success criteria are measurable — SC-001..SC-005 содержат конкретные метрики (`grep -c "too many clients" → 0`, `pg_stat_activity count ≤ 10`, и т.д.)
- [x] Success criteria are technology-agnostic — «нет каскада ошибок», «pg_stat_activity ≤ 10» (через Postgres, но как метрика наблюдаемости, не как «использовать Postgres-specific решение»)
- [x] All acceptance scenarios are defined — для каждой User Story есть конкретные Given/When/Then
- [x] Edge cases are identified — Postgres недоступен, Tomcat перезапуск, dev-pc запуск, симметричный фикс karaoke-web
- [x] Scope is clearly bounded — фикс ТОЛЬКО `Connection` + логирование, HikariCP/пул — отдельная задача
- [x] Dependencies and assumptions identified — singleton + ThreadLocal не нарушает спек 087; симметричный фикс karaoke-web обязателен

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001..FR-013 привязаны к US1/US2/US3 и SC-001..SC-005
- [x] User scenarios cover primary flows — открытие вкладки, нажатие кнопки, инцидент перегрузки БД
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001..SC-005 измеримы через `docker logs`, `pg_stat_activity`, ручной smoke
- [x] No implementation details leak into specification — упоминается `lazy {}`, `ThreadLocal`, SLF4J — но только как «существующий код, который нужно отрефакторить», не как «новый стек»

## Notes

- Все 3 clarification-вопроса закрыты пользователем (см. секцию `Clarifications` в spec.md): Singleton Connection.local()/remote(), scope только Connection, логирование через SLF4J warn.
- Спека готова к `/speckit.plan`. Смежные спеки для контекста: 087-fix-shared-db-connection, 091-fix-connection-leak, 174-fix-stats-connection-leak.
- Дополнительное замечание (НЕ блокирует): `withDb { ... }` в `NewsController`/`DictionariesController`/`SponsrSyncController` после FR-001 станет избыточным — закрывать закрытое соединение no-op, но и не вредит. Опциональная чистка — отдельная задача.
