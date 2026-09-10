# Specification Quality Checklist: 414 Request-URI Too Large на /playlists/membership у крупных авторов

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-10
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  (1 question resolved in § Clarifications: аутентификация через JWT → CSRF невозможен)
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Knowledge Compliance *(MANDATORY — see Constitution Principle IX)*

- [x] Knowledge pre-flight выполнен ДО `codegraph_explore` / grep по `src/`
- [x] `knowledge/README.md` + `knowledge/domains/README.md` прочитаны **полностью**
- [x] Релевантные домены определены через `grep -r '<keyword>' knowledge/` (минимум 3 запроса)
- [x] Все `domain.md` + `components/*.md` релевантных доменов прочитаны
  (karaoke-web/components/public-controllers.md, public-controllers-6.md,
  system/frontend/composable-playlist-membership.md,
  adr/local-0006-logging-and-error-handling-karaoke-web.md,
  specs/239-zakroma-author-songs-batch-render/spec.md — прецедент)
- [x] Все `local-*.md` ADR в `knowledge/adr/` прочитаны — `local-0006` прочитан;
  остальные ADR не содержат релевантного контента (см. spec.md § Knowledge References)
- [x] В `spec.md` заполнена секция «Knowledge References» с конкретными путями
- [x] Если Knowledge противоречит реквесту — это явно зафиксировано в «Open Questions» (NEEDS CLARIFICATION)
  — нет противоречий, явно зафиксировано в spec.md § Assumptions + Edge Cases
- [x] Если grep по `knowledge/` ничего не дал — зафиксировано явное «no relevant docs»
  (см. spec.md «Searched: 414 / URI Too Long / GET→POST → no relevant docs»)

## Notes

- Спека покрывает прецедент Issue #77 (414 на крупном авторе «Машина Времени»)
- Решение: переход с GET (`?ids=<csv>`) на POST (JSON body `{"ids": [...]}`)
- Backward-compat сохранён (US3): старый GET продолжает работать
- Edge case: ~1350 id — граничный лимит nginx `large_client_header_buffers` (8 КБ);
  на новом POST лимит снят (≤ client_max_body_size, по умолчанию 1m)
- Items are numbered sequentially for easy reference
- Все требования тестируемые; все критерии измеримы