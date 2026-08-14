---
status: Active
slug: 001-code-standards-docs
related:
  - ../features/002-ci-lint-enforcement.md
  - ../architecture/L1-system-context.md
  - ../../specs/001-code-standards-docs/spec.md
---

# 001 — Приведение кода к стандартам + документирование фич (Phase 001) (LiveDoc)

> Drill-down — [specs/001-code-standards-docs/spec.md](../../specs/001-code-standards-docs/spec.md).

## Что делает

Phase 001 (2026-07-20, PR #12) — большое мероприятие по приведению проекта в
production-grade состояние:

1. **Линтеры**:
   - ktlint для Kotlin.
   - ESLint + Prettier для Vue/JS/TS.
   - Baselines (~30k проблем начальные, темп уменьшения ≥10%/мес).
2. **Документация**:
   - KDoc coverage ≥ 50% для публичных классов (NON-NEGOTIABLE, FR-006).
   - JSDoc coverage ≥ 50% (Vue 3 + typedoc).
3. **Per-feature документы** в `docs/features/<slug>.md` (FR-009).
4. **HTML-документация**: Dokka (`docs/api/dokka/`) и typedoc.
5. **CI** (см. `002-ci-lint-enforcement`).
6. **Конституция** v2.0.0 (semver MINOR amend).

## User Stories (краткий список)

- **US1** (P1): Разработчик понимает стандарты кода.
- **US2** (P1): Per-feature документ описывает фичу.

## Functional Requirements (указатель]

- **FR-006**: KDoc/JSDoc на публичных API с `@see` ссылкой на per-feature документ.
- **FR-007**: Линтеры в pre-commit + CI.
- **FR-009**: При правке кода — обновление per-feature документа.

## Acceptance Criteria

- [ ] **AC1**: Линтеры в pre-commit hooks.
- [ ] **AC2**: KDoc coverage ≥ 50% (baseline).
- [ ] **AC3**: Per-feature документы для ключевых фич.

## Связанные LiveDocs

- Это **Phase 001** — база для всего дальнейшего (Phase 002 cross-machine, Phase 003+).
- Feature: [002-ci-lint-enforcement.md](../features/002-ci-lint-enforcement.md)
- Architecture: [L1-system-context.md](../architecture/L1-system-context.md)

## Код

- `.pre-commit-config.yaml`
- `config/ktlint/baseline-*.xml`
- `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json`
- `docs/api/dokka/`, `docs/api/typedoc-*/`
- `tools/check-kdoc-coverage.sh`, `tools/check-jsdoc-coverage.sh`
- `tools/check-feature-doc.sh`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14