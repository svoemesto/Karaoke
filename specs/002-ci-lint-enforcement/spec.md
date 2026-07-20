# Спецификация: CI lint enforcement

**Feature Key**: `ci-lint-enforcement`
**Дата**: 2026-07-21
**Зависит от**: PR #12 (001-code-standards-docs) — merged.

## Цель

Добавить GitHub Actions workflow, который на каждый push в master и
каждый pull_request в master прогоняет линтеры (ktlint, ESLint,
Prettier) и per-feature структурные проверки, чтобы baseline-стратегия
из PR #12 была действительно enforced, а не добровольной.

## Зачем

- PR #12 ввёл 8 baseline-файлов (3 ktlint, 2 eslint, 3 spec.md
  для test) + Prettier + 9 per-feature документов + 8 tools
  скриптов. Но `gh pr checks 12` показал `no checks reported on
  the '001-code-standards-docs' branch` — стандарты ничем не
  enforced.
- Любой PR может молча увеличить baseline или сломать структуру
  per-feature документа, и ревьюер увидит это только если заметит.
- Constitution v1.1.0 (Principle VI) объявил Code Standards
  NON-NEGOTIABLE — это означает автоматический, не ручной gate.

## Scope

### In

- Один workflow `.github/workflows/lint.yml` с 4 job-ами:
  - `backend-lint` (ktlintCheck, baseline-aware)
  - `frontend-lint` (matrix webvue3 + karaoke-public: ESLint baseline-aware + Prettier strict)
  - `docs-lint` (lychee offline + per-feature structure)
  - `baseline-stats` (informational, `continue-on-error: true`)
- Per-feature документ `docs/features/ci-lint-enforcement.md`.
- Обновления CONTRIBUTING.md (раздел «Поток разработки»: CI flow).
- Обновления docs/features/README.md (новый пункт в оглавлении).
- Прогон `prettier --write` на обоих SPA + регенерация eslint-baselines
  (чтобы Prettier-чек в CI был strict, не warning-only).

### Out (явно)

- Branch protection rules (делается вручную в GitHub UI).
- Build verification (bootJar / npm run build) — отдельный workflow.
- Линтинг тестов — ktlint по test source set уже включён в `ktlintCheck`,
  detekt (когда выйдет) будет отдельным job-ом.
- Авто-merge, auto-label, auto-assign — отдельная фича.

## Functional Requirements (FR-001..FR-005)

- **FR-001 (MUST)**: workflow запускается на push в master и pull_request в master.
- **FR-002 (MUST)**: `backend-lint` запускает `./gradlew ktlintCheck`
  и валит workflow при exit non-zero. Baseline-файлы
  (`config/ktlint/baseline-*.xml`) подхватываются автоматически
  через `build.gradle.kts`.
- **FR-003 (MUST)**: `frontend-lint` для каждого SPA
  (matrix: `webvue3`, `karaoke-public`) прогоняет
  `tools/check-eslint-baseline.sh <spa>` + `npx prettier --check`.
  Оба SPA прогоняются параллельно (`fail-fast: false`).
- **FR-004 (MUST)**: `docs-lint` прогоняет lychee в `--offline` режиме
  (только локальные файлы) + `tools/check-feature-doc.sh`.
- **FR-005 (SHOULD)**: `baseline-stats` job — informational,
  `continue-on-error: true`, выводит таблицу счётчиков.

## Non-Functional Requirements (NFR-001..NFR-003)

- **NFR-001 (MUST)**: `permissions: contents: read` — workflow
  не пишет в репо. Минимизация blast radius.
- **NFR-002 (MUST)**: кэширование Gradle (`setup-java@v4 cache=gradle`)
  и npm (`setup-node@v4 cache=npm` + `cache-dependency-path: $spa/package-lock.json`).
- **NFR-003 (SHOULD)**: `concurrency` с `cancel-in-progress: true`
  для отмены устаревших запусков при force-push.

## Success Criteria (SC-001..SC-002)

- **SC-001**: На PR с заведомо плохим кодом (например, добавлен
  `// noinspection` или неиспользуемая переменная в Kotlin) —
  workflow валится с понятным сообщением.
- **SC-002**: Прогон `bash tools/baseline-stats.sh` на master
  после мержа показывает те же 426 (или меньше) нарушений, что
  и до мержа PR #13. Любое увеличение — регрессия.

## Связь с другими фичами

- Зависит от: `code-standards` (FR-006..FR-009 в PR #12).
- Включает: `ci-lint-enforcement` (эта фича).
- Блокирует: `branch-protection-rules` (нужны зелёные checks
  для required-check).

## Открытые вопросы

- **Q1**: Должна ли быть отдельная job-а для `npm run build`? —
  Out of scope для этой фичи (см. Scope). Возможно в
  `003-ci-build-verification` (новая фича).
- **Q2**: lychee `--accept 200,203,206,301,302,303,304,307,308,403,429`
  — нужно ли принимать 404? — Нет, 404 = сломанная ссылка.
- **Q3**: Запускать ли workflow на `workflow_dispatch` для
  ручного прогона? — Да, добавим — `on: workflow_dispatch: {}`
  ничего не стоит.

## Out of Scope (явно не в этой фиче)

- Branch protection rules — настраиваются вручную через
  GitHub UI, требуют admin permissions.
- Авто-merge после зелёного CI — отдельная фича
  `auto-merge-on-green`.
- Запуск CI на Docker-контейнерах (self-hosted runners) —
  GitHub-hosted достаточно для текущего объёма.
- Test coverage reporting — нет тестов в проекте.
- Dependabot/Renovate — отдельная фича.
