# Specification Quality Checklist: Устранение утечки соединений с БД (BR-627)

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-26
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — требования описаны через наблюдаемое поведение: «соединение закрыто», «проверка падает с указанием файла и строки»; конкретные имена классов приведены только как ссылки на **уже существующий** канонический механизм (ADR local-0009) и на проверяемое нарушение (файлы с `thread {`).
- [x] Focused on user value and business needs — три истории: оператор не теряет систему; новая утечка не проскочит; разработчик понимает границы контракта.
- [x] Written for non-technical stakeholders — суть читается без знания JDBC; термины раскрыты в Key Entities.
- [x] All mandatory sections completed — OpenProject Tracking, Knowledge References, User Scenarios, Requirements, Success Criteria, Assumptions, Clarifications.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — открытый вопрос оформлен как **Clarification Q1** с вариантами и рекомендацией (по регламенту проекта спека уходит владельцу с одним вопросом по объёму).
- [x] Requirements are testable and unambiguous — каждое FR проверяемо: FR-001/002 — греп/тест, FR-003/004 — регресс-тест с негативной фикстурой, FR-005/006 — поведенческий тест «до/после», FR-007/008 — наличие разделов в SSoT, FR-009 — регламент, FR-010/011 — отсутствие миграций и API-изменений (проверяется diff'ом).
- [x] Success criteria are measurable — SC-001 (число соединений возвращается к исходному), SC-002 (нет сообщения об исчерпании), SC-003 (покрытие 5 из 5 файлов), SC-004 (очередь отзывчива), SC-005 (один документ вместо поиска).
- [x] Success criteria are technology-agnostic — сформулированы через наблюдаемые состояния системы и её пользователя, без имён классов и фреймворков.
- [x] All acceptance scenarios are defined — по 2–3 сценария на каждую из трёх историй, плюс шесть edge cases.
- [x] Edge cases are identified — падение до получения соединения; двойное закрытие; смешанный файл; вложенный поток; поведение тестового JVM.
- [x] Scope is clearly bounded — FR-010 явно исключает переход на пул соединений; FR-011 исключает изменения API и миграции данных; Assumptions фиксируют, что отдельная БД для тестов вне объёма.
- [x] Dependencies and assumptions identified — канонический механизм `startOneShotThread` уже существует и покрыт тестом; компенсирующая мера (`max_connections=300`) уже применена; зависимость от владельца — одно решение по объёму (Q1).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001…FR-012 связаны со сценариями историй и критериями SC-001…SC-005.
- [x] User scenarios cover primary flows — экспорт/синхронизация/прогрев/бэкфилл (история 1), новый код (история 2), понимание документации (история 3).
- [x] Feature meets measurable outcomes defined in Success Criteria — каждое SC отображается на конкретную проверку.
- [x] No implementation details leak into specification — имена файлов приведены как **факты расследования** (что найдено на диске), а не как предписание реализации; выбор способа закрыт FR-001 через уже принятый ADR.

## Notes

- Спека опирается на измеренные факты, а не на предположения: 4 сырых `thread { }` найдены в прикладном коде, все 4 — в файлах с обращением к БД; существующая проверка `OneShotThreadWiringTest` покрывает только 2 файла; корутинных диспетчеров в прикладном коде нет, все пулы — фиксированного размера или одноразовые; на боевой БД соединения не растут (38 соединений, старейшее — со старта контейнера).
- Вопрос Q1 — единственный, требующий решения владельца: объём задачи (точечный фикс против перехода на пул соединений). Рекомендация — вариант A.
- Проверка `tools/check-spec-issue-link.py` требует наличия секции `## OpenProject Tracking` с полями `Issue ID`, `Title`, `Workflow` — все заполнены.
