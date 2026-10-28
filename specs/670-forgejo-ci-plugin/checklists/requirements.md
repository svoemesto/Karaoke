# Specification Quality Checklist: Плагин «Forgejo CI»

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *FR упоминают «кеш», «интервал опроса» и «ссылку на секрет» как поведенческие контракты; имена API, слотов, классов и схем в спеку не перенесены (они остаются в карте и плане)*
- [x] Focused on user value and business needs — *каждая история отвечает на боль «не видно, идёт ли CI» и «агент крутит watcher'ы»*
- [x] Written for non-technical stakeholders — *термины «прогон», «раннер», «задача» объяснены в Key Entities*
- [x] All mandatory sections completed — *OpenProject Tracking, Knowledge References, User Scenarios, Requirements, Success Criteria, Assumptions заполнены; добавлен Out of Scope*

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — *0 маркеров: все спорные вопросы закрыты владельцем 2026-10-28 (объём v1, два инструмента, read-only токен, 15 с / 5 прогонов, без dispatch, отдельный репозиторий, уведомления и бейдж)*
- [x] Requirements are testable and unambiguous — *FR-001…FR-018 проверяются конкретным действием и наблюдаемым результатом*
- [x] Success criteria are measurable — *SC-001 (15 с), SC-002 (один вызов), SC-003 (<2 с, 100 %), SC-005 (0 вхождений), SC-006 (подтверждение владельцем), SC-007 (0 следов)*
- [x] Success criteria are technology-agnostic — *ни одного имени библиотеки, фреймворка или эндпоинта; «живой GUI по адресу» — это место проверки, а не технология*
- [x] All acceptance scenarios are defined — *4 истории, 14 сценариев Given/When/Then*
- [x] Edge cases are identified — *8 краевых случаев: пурж, устаревшие статусы задач, `waiting` при живом раннере, `active` ≠ работает, несколько вкладок, пустой список, длинные имена, отозванный токен*
- [x] Scope is clearly bounded — *раздел Out of Scope: dispatch, джоб-логи, разбивка по джобам, несколько репозиториев, история/тренды, вебхуки, починка CI*
- [x] Dependencies and assumptions identified — *раздел Assumptions: доступность Forgejo, read-only токен, эфемерность истории, отсутствие джоб-детализации, отдельный репозиторий, профиль `web`*

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — *FR-001…FR-018 покрыты сценариями историй 1–4 или критериями приёмки карты (тикеты 10–13)*
- [x] User scenarios cover primary flows — *наблюдение человеком, ответ агенту, уведомление, деградация*
- [x] Feature meets measurable outcomes defined in Success Criteria — *SC-001…SC-007 проверяемы в живом GUI и по артефактам*
- [x] No implementation details leak into specification — *проверено повторно; единственное техническое ограничение — «опрос не реже 15 с», это контракт свежести, а не выбор технологии*

## Notes

- Проверка Knowledge-first: `tools/spec-knowledge-preflight.sh --create-marker forgejo-ci-plugin`
  → `OK: 7 ссылок`, маркер `.specify/.preflight-forgejo-ci-plugin.ok` создан 2026-10-28.
- Спека намеренно фиксирует **три ограничения, которые нельзя обещать** (пурж
  истории, отсутствие джоб-разбивки, отставание статуса прогона): они записаны
  как контракт, чтобы план не «дорисовал» несуществующие возможности.
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan` — *неполных нет*.
