---
status: Active
slug: 002-ci-lint-enforcement
related:
  - ../architecture/L1-system-context.md
  - ../../specs/002-ci-lint-enforcement/spec.md
  - ../../archive/docs/features/ci-lint-enforcement.md
---

# 002 — CI lint enforcement (GitHub Actions) (LiveDoc)

> Drill-down — [specs/002-ci-lint-enforcement/spec.md](../../specs/002-ci-lint-enforcement/spec.md).

## Что делает

GitHub Actions workflow `.github/workflows/lint.yml` на каждый push в master и
каждый PR в master прогоняет линтеры (ktlint, ESLint, Prettier, lychee,
per-feature structure, baseline-stats, KDoc coverage, JSDoc coverage, **LiveDocs
structure**).

**Главное правило (governance)** — см. `AGENTS.md` секцию «CI-gate для master»:
прямые пуши в master запрещены, каждое изменение через feature-ветку +
PR + CI 7/7 (теперь 8/8 — добавлен `livedocs-structure`).

Зависит от Phase 001 (`001-code-standards-docs`).

## User Stories (краткий список)

- **US1** (P1): PR блокируется, если хоть один check не проходит.

## Functional Requirements (указатель]

- **FR-001**: `.github/workflows/lint.yml` — 8 jobs.
- **FR-002**: Branch protection rules — требуется «Lint / Lint 7/7 SUCCESS».

## Acceptance Criteria

- [ ] **AC1**: PR с ошибкой линтера — блокируется (merge недоступен).
- [ ] **AC2**: PR после фикса — может быть смержен.

## Связанные LiveDocs

- Architecture: [L1-system-context.md](../architecture/L1-system-context.md) (GitHub Actions)

## Код

- `.github/workflows/lint.yml` — 8 jobs
- Branch protection rules (на стороне GitHub): admin требует 7/7 SUCCESS для merge

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14