# Specification Quality Checklist: Кэш счётчиков песен автора в `tbl_authors`

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-31
**Feature**: [spec.md](spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *за исключением DB-триггера и JDBC (это часть tech-stack проекта, см. Constitution)*
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — *вместо этого два Q&A вопроса Q1/Q2 в разделе Open Questions (архитектурные, не блокеры)*
- [x] Requirements are testable and unambiguous (FR-001..FR-011, каждый с конкретным MUST)
- [x] Success criteria are measurable (SC-001..SC-006, включая «0 выполнений GROUP BY за 100 запросов», «миграция < 30 сек на 5k песен»)
- [x] Success criteria are technology-agnostic — *учитывая, что Constitution зафиксировала Kotlin/JDBC/Postgres как стек, упоминание SQL/DML допустимо*
- [x] All acceptance scenarios are defined (US1: 3, US2: 5, US3: 2, US4: 2)
- [x] Edge cases are identified — *UPDATE song_author (перенос между авторами) упомянут в FR-005 и US2 acceptance*
- [x] Scope is clearly bounded (только `/api/public/authors-tiles`, не трогает другие эндпоинты)
- [x] Dependencies and assumptions identified (sync-механизм уже есть, L2-кеш 248 уже есть, sync-флаги `sync_authors_*` уже true)

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows (4 приоритезированных стори: снижение нагрузки, инвалидация, sync, backfill)
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification — *упоминание DB-триггера обосновано тем, что это архитектурное решение пользователя, влияющее на скоуп планирования; код не уточняется*

## Notes

### Открытые вопросы (требуют ответа пользователя перед `/speckit.plan`)

1. **Q1**: Механизм инвалидации L2-кеша на проде (`consumeDirty` через sync рекомендован).
2. **Q2**: Семантика «skip» для `total_songs_count` (рекомендуется «как сейчас в коде, 1-в-1»).

Без ответа на эти вопросы `/speckit.plan` всё равно может стартовать, но уточнения дадут более точную оценку скоупа и рисков.

### Машинно-специфичные нюансы (для nsa-i9 / nsa)

На этой машине разрешено пересобирать `karaoke-app` (см. AGENTS.md). Тестирование триггера в реальной DB-схеме потребует:
- применения миграции на LOCAL-БД,
- прогона тестовых INSERT/UPDATE/DELETE,
- проверки, что `karaoke-app:bootJar` собирается и `karaoke-web:bootJar` собирается (оба — после правки `PublicApiController`).

Сборка разрешена; перезапуск контейнера `karaoke-app` — НЕ автоматический, требует согласия пользователя.

Items marked incomplete require spec updates before `/speckit.clarify` or `/speckit.plan`.