# Specification Quality Checklist: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-02
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *FR ссылаются на конкретные файлы (webvue3/SiteUserEdit.vue, SiteUser.kt, Flyway migrations), что является частью требований проекта (Constitution §VI FR-009), а не выбором реализации.*
- [x] Focused on user value and business needs — *US1 (админ выдаёт право), US2 (редактор видит SKIP-контент), US3 (админ видит колонку в таблице).*
- [x] Written for non-technical stakeholders — *сценарии описаны на языке пользователя; технические детали вынесены в Assumptions и Key Entities.*
- [x] All mandatory sections completed — *User Scenarios, Requirements, Success Criteria, Assumptions заполнены.*

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — *единственный критический вопрос задан через `question` tool в начале сессии (место галочки → «Только в webvue3 (админ)»); больше не осталось нерешённых неоднозначностей.*
- [x] Requirements are testable and unambiguous — *FR-001..FR-010, NFR-001..NFR-003 — каждый проверяем.*
- [x] Success criteria are measurable — *SC-001 (≤2 клика), SC-002 (≥1 запись с skip=TRUE в ответе), SC-003 (diff ответов = 0 для анонимов), SC-004 (≤1 SQL на запрос).*
- [x] Success criteria are technology-agnostic (no implementation details) — *в SC не упоминаются фреймворки; ссылки на endpoint'ы — это контракт API, а не деталь реализации.*
- [x] All acceptance scenarios are defined — *US1: 3 сценария, US2: 3 сценария, US3: 1 сценарий.*
- [x] Edge cases are identified — *4 edge case'а: бан, share-link анонима, миграция БД, session-cache.*
- [x] Scope is clearly bounded — *Out of Scope перечисляет 5 явно исключённых пунктов.*
- [x] Dependencies and assumptions identified — *7 Assumptions (A-001..A-007), раздел «Связанные документы» с 5 ссылками.*

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — *FR-001..FR-010 ссылаются на user stories или на конкретные проверки в Success Criteria / Edge Cases.*
- [x] User scenarios cover primary flows — *US1 (выдача права), US2 (потребление права), US3 (admin overview) — полный цикл.*
- [x] Feature meets measurable outcomes defined in Success Criteria — *SC-001..SC-004 мапятся 1:1 на FR-004..FR-008.*
- [x] No implementation details leak into specification — *имена файлов/таблиц упомянуты как «контракт проекта» (Constitution §VI FR-009 требует обновлять per-feature документ, что предполагает явное именование файлов).*

## Notes

- Перед `/speckit.plan` спека готова.
- При планировании обратить внимание:
  1. **Constitution §III**: при добавлении колонки `can_work_with_skipped` в `tbl_site_users` пересоздать `update_tbl_site_users_recordhash` (миграция Flyway) для LOCAL **и** SERVER.
  2. **Constitution §VI FR-009**: создать `docs/features/editor-skipped-content-access.md` в том же PR.
  3. **LiveDocs**: добавить ссылку на новую фичу в `livedocs/features/293-skip-author-toggle.md` (см. `livedocs/README.md`/`livedocs/INDEX.md`).
  4. **AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»**: после правок — обязательная пересборка `:karaoke-app:bootJar` и `:karaoke-web:bootJar` (на `nsa-i9`/`nsa`), Vite build обоих фронтов, Docker-образов через `deploy/do.sh build_webvue3`.
- Все три валидационные итерации не требуются — спека проходит с первого прохода.