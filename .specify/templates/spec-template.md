# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`

**Created**: [DATE]

**Status**: Draft

**Input**: User description: "$ARGUMENTS"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

> Эта секция REQUIRED для каждой спецификации. Без неё спека НЕ ДОЛЖНА
> переходить в `/speckit.plan`. CI gate `tools/check-spec-issue-link.py`
> валидирует наличие всех полей. Workflow `claim → report → mark-review →
> close` — обязателен (см. tools/tracker.sh и AGENTS.md).

### Идентификация

- **Issue ID**: `#<NNN>` (OpenProject work package id, например `#69`).
  - Если спека пришла из OpenProject: указать конкретный ID.
  - Если спонтанная (без issue): указать `none`, workflow tracking не применяется.
- **Title**: `<Title of work package>`.
- **Created in OpenProject**: `<YYYY-MM-DD>` (если есть).

### Workflow (NON-NEGOTIABLE при наличии Issue ID)

| Шаг | Команда | Когда | Кто |
|---|---|---|---|
| 1. **Claim** | `source .env.local-tracker && bash tools/tracker.sh claim-issue <NNN>` | ПЕРЕД первой строкой кода спеки. Переводит `New` → `In progress`, assignee=ai-agent. | Agent |
| 2. **Pre-flight Knowledge** | `spec.md § Knowledge References` | Согласно Constitution Principle IX (см. ниже). | Agent |
| 3. **Work** | код, tests, knowledge updates | `/speckit.implement` | Agent |
| 4. **Add comment с отчётом** | `bash tools/tracker.sh add-comment <NNN> --file specs/<NNN>-<slug>/report.md` | После merge. Файл `report.md` — REQUIRED. | Agent |
| 5. **Mark review** | `bash tools/tracker.sh mark-review <NNN>` | После публикации комментария. Переводит `In progress` → `In review`. | Agent |
| 6. **Close** | `bash tools/tracker.sh close-issue <NNN>` | После ревью владельцем. (Может пропустить, если владелец закрывает сам.) | Agent или Owner |

### Проверки (validation)

- `tools/check-spec-issue-link.py` (NEW, Pass 349) проверяет в CI:
  - Наличие секции `## OpenProject Tracking` в `spec.md`.
  - Наличие полей `Issue ID`, `Title`, `Workflow` (с пунктами claim/report/mark-review).
  - **Не валидирует** сам факт claim/mark-review в OpenProject (требует API),
    но через `git log` проверяет что коммиты в этой spec dir содержат
    маркеры `[tracker-claim-NNN]` / `[tracker-review-NNN]` (см. workflow ниже).

- ВАЖНО: если Issue ID = `none`, секция остаётся, но проверка Issue-link
  скипа (нет issue → нет claim). Остальные поля — опциональные.

### Прецедент

2026-09-09, issue #69 (Pass 344/345): work выполнен через `/speckit-full 69` БЕЗ
claim. Отчёт опубликован задним числом после merge PR #448.
Governance failure, исправлен в спеке #349 (Pass 349).


## Knowledge References *(MANDATORY — see Constitution Principle IX)*

> **Прецедент**: 2026-09-09, spec #339 — агент пропустил Knowledge-first
> pre-flight и изобрёл форму кеша вместо паттернов из
> `knowledge/domains/caching/components/caching-patterns.md`.
> Без заполненной секции спека **НЕ ДОЛЖНА** переходить в
> `/speckit.plan`. См. `AGENTS.md` MUST #0, Constitution Principle IX.

Перечислите **ВСЕ** документы `knowledge/`, прочитанные перед написанием
этой спеки. Если релевантных не нашлось — перечислите grep-запросы и
файлы, по которым искали (явное «nothing found» — это валидный ответ).

### Pre-flight log

- **Дата pre-flight**: [YYYY-MM-DD]
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `<query 1>` → файлы: `<results>`
  2. `<query 2>` → файлы: `<results>`
  3. `<query 3>` → файлы: `<results>`

### Knowledge files consulted

- [`knowledge/domains/<X>/domain.md`](../../knowledge/domains/<X>/domain.md)
  — зачем прочитан: <1 фраза>
- [`knowledge/domains/<X>/components/<Y>.md`](../../knowledge/domains/<X>/components/<Y>.md)
  — зачем прочитан: <1 фраза>
- [`knowledge/adr/local-NNNN-<slug>.md`](../../knowledge/adr/local-NNNN-<slug>.md)
  — принятое решение, влияющее на фичу: <1 фраза>

### Если ничего не нашлось (явный no-op)

> «Searched: `<queries>` (3+ попытки с разными ключевыми словами) →
> `<files checked>` → no relevant docs. Continuing with code-first
> exploration only after exhaustive search.»

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when [boundary condition]?
- How does system handle [error scenario]?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST [specific capability, e.g., "allow users to create accounts"]
- **FR-002**: System MUST [specific capability, e.g., "validate email addresses"]
- **FR-003**: Users MUST be able to [key interaction, e.g., "reset their password"]
- **FR-004**: System MUST [data requirement, e.g., "persist user preferences"]
- **FR-005**: System MUST [behavior, e.g., "log all security events"]

*Example of marking unclear requirements:*

- **FR-006**: System MUST authenticate users via [NEEDS CLARIFICATION: auth method not specified - email/password, SSO, OAuth?]
- **FR-007**: System MUST retain user data for [NEEDS CLARIFICATION: retention period not specified]

### Key Entities *(include if feature involves data)*

- **[Entity 1]**: [What it represents, key attributes without implementation]
- **[Entity 2]**: [What it represents, relationships to other entities]

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: [Measurable metric, e.g., "Users can complete account creation in under 2 minutes"]
- **SC-002**: [Measurable metric, e.g., "System handles 1000 concurrent users without degradation"]
- **SC-003**: [User satisfaction metric, e.g., "90% of users successfully complete primary task on first attempt"]
- **SC-004**: [Business metric, e.g., "Reduce support tickets related to [X] by 50%"]

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- [Assumption about target users, e.g., "Users have stable internet connectivity"]
- [Assumption about scope boundaries, e.g., "Mobile support is out of scope for v1"]
- [Assumption about data/environment, e.g., "Existing authentication system will be reused"]
- [Dependency on existing system/service, e.g., "Requires access to the existing user profile API"]
