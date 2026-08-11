# Specification Quality Checklist: Воспроизводимая настройка Linux Mint для проекта Karaoke

**Purpose**: Validate specification completeness and quality before proceeding to planning (`/speckit.plan`) or implementation.
**Created**: 2026-08-11
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] **CHK001** No implementation details leak into specification (no concrete apt package versions in requirements; no specific bash one-liners in functional requirements — only in artifacts/assumptions)
- [x] **CHK002** Focused on user value and business needs (3 user stories: «разработчик с нуля», «воспроизведение на 2-й машине», «AI-агент на этой машине»)
- [x] **CHK003** Written for non-technical stakeholders where possible (FR-001..FR-013 описаны в терминах «что скрипт делает», а не «какие строки в bash»)
- [x] **CHK004** All mandatory sections completed (User Scenarios, Requirements, Success Criteria, Assumptions, Key Entities)

## Requirement Completeness

- [x] **CHK005** No `[NEEDS CLARIFICATION]` markers remain (все спорные вопросы закрыты reasonable defaults с явным указанием в Assumptions: целевая ОС = Linux Mint 22.2, x86_64, пользователь с sudo)
- [x] **CHK006** Requirements are testable and unambiguous (FR-001..FR-013 — каждое проверяемо: либо через smoke-test, либо через `git ls-files`, либо через ручной запуск скрипта на чистой VM)
- [x] **CHK007** Success criteria are measurable (SC-001 — время в часах, SC-002 — воспроизводимость на 2-й VM, SC-003 — точное число контейнеров, SC-004 — время отклика 200 в секундах, SC-005 — строки кода, SC-006 — grep-выражение, SC-007 — вывод `git ls-files`, SC-008 — число ловушек)
- [x] **CHK008** Success criteria are technology-agnostic (нет упоминания «apt», «docker», «bash» в SC; SC-001/002/003/004/005/007/008 — пользовательские, SC-006 — security-чек)
- [x] **CHK009** All acceptance scenarios defined (4 сценария в P1, 3 в P1 воспроизводимости, 2 в P2 AI-агента)
- [x] **CHK010** Edge cases identified (7 edge cases: snap docker, LUKS, нет группы docker, разные пароли Postgres, Mint 21.x, нет Docker Hub, конфликт имён контейнеров, занятые порты)
- [x] **CHK011** Scope is clearly bounded (Assumptions явно фиксируют: Linux Mint 22.2 Zara, x86_64, не root, есть интернет; out of scope — Mint 21.x/23.x, ARM, оффлайн, пересборка образов)
- [x] **CHK012** Dependencies and assumptions identified (Assumptions: §1-§9 покрывают ОС, арх-ру, права, сеть, credentials, пути из do.env, пересборка, AI-агенты, Ollama)

## Feature Readiness

- [x] **CHK013** All functional requirements have clear acceptance criteria (FR-001..FR-013 — для каждого есть либо ссылка на smoke-test (FR-008), либо на grep-выражение (FR-006, FR-007), либо на конкретное поведение (FR-005, FR-009, FR-010, FR-013))
- [x] **CHK014** User scenarios cover primary flows (User Story 1 = setup, User Story 2 = воспроизводимость — обе P1; User Story 3 = AI-агент — P2)
- [x] **CHK015** Feature meets measurable outcomes defined in Success Criteria (SC-001..SC-008 выводятся из FR-001..FR-013: 9 контейнеров ↔ FR-004; smoke-test ↔ FR-008; идемпотентность ↔ FR-013; секреты ↔ FR-012+SC-006; rollback ↔ FR-009)
- [x] **CHK016** No implementation details leak into specification (только в разделе Assumptions упомянуты `apt`, `apt install`, `NodeSource`, `docker-ce` — но это **обязательные** для артефакта-скрипта prerequisites, а не implementation самой спеки; сама спека оперирует «что должна делать настройка», а не «какие команды»)

## Согласованность с Constitution

- [x] **CHK017** Принцип VII.4 (Cross-machine документация) соблюдён: спека дополняет `docs/onboarding.md`, не дублирует; явно ссылается (FR-011)
- [x] **CHK018** Принцип VIII.1 (секреты НЕ в индексе git) соблюдён: FR-005 создаёт `do.env.template` без реальных секретов, FR-012 + SC-006 + SC-007 явно проверяют
- [x] **CHK019** Принцип VIII.2 (список запрещённых паттернов) соблюдён: `do.env` явно в `.gitignore` (AGENTS.md), скрипт копирует из `do.env.template`, pre-commit-check зелёный
- [x] **CHK020** «Ограничения агента» соблюдены: User Story 3 + FR-007 явно разделяют, что агенту разрешено и что запрещено на этой машине (per Constitution, п. 1 «Категорически запрещено»)

## Артефакты спеки (должны быть созданы в /speckit.plan или /speckit.tasks)

- [X] **CHK021** `deploy/do.env.template` создан (задача плана)
- [X] **CHK022** `specs/170-mint-dev-setup/setup-mint.sh` создан (задача плана)
- [X] **CHK023** Связь с `docs/onboarding.md` обновлена (добавить ссылку на спек-папку, как Linux Mint-специфичный гайд)
- [X] **CHK024** `docs/features/docker-deploy.md` (per-feature документ) обновлён или создан (per FR-009 Constitution: правка docker-стека = обновление per-feature дока в том же PR)
- [X] **CHK025** `docs/architecture-notes.md` получит запись о PR (после merge)

## Notes

- Чеклист — **полный** по 4 секциям content/requirements/feature-readiness/constitution-check + 5 tasks для /speckit.plan.
- Спека НЕ требует дополнительных clarification (нет `[NEEDS CLARIFICATION]` markers).
- Готова к `/speckit.plan` — все требования FR-001..FR-013 имеют однозначные acceptance criteria.
- Главные риски (для /speckit.plan): (1) набор переменных в `do.env.template` — нужно аккуратно собрать из `do.sh` + всех `docker-compose*.yml`; (2) идемпотентность `setup-mint.sh` — нужен `command -v` + `dpkg -s` для каждого пакета; (3) smoke-test после `docker compose up` — БД инициализируется не мгновенно (10-30 сек), нужен retry-loop.
