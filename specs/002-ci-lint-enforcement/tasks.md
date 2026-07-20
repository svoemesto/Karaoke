# Tasks: CI lint enforcement

**Feature Key**: `ci-lint-enforcement`
**Branch**: `002-ci-lint-enforcement`
**Commits**: 2 (prettier+baseline → workflow+docs)

## Phase 0: Подготовка

- [x] **T001**: Проверить `tools/verify-doc-links.sh` (lychee --offline default).
- [x] **T002**: Локально прогнать `ktlintCheck` (BUILD SUCCESSFUL).
- [x] **T003**: Локально прогнать `check-eslint-baseline.sh` (оба SPA OK).
- [x] **T004**: Локально прогнать `prettier --check` (был 212+60 нарушений).
- [x] **T005**: Создать ветку `002-ci-lint-enforcement`.

## Phase 1: Prettier-fix + baseline rebase

- [x] **T011**: `cd webvue3 && npx prettier --write "src/**/*.{vue,js,ts,json}"` (212 файлов).
- [x] **T012**: `cd karaoke-public && npx prettier --write "src/**/*.{vue,js,ts,json}"` (60 файлов).
- [x] **T013**: `bash tools/check-eslint-baseline.sh webvue3` → 2 новых нарушения.
- [x] **T014**: `bash tools/check-eslint-baseline.sh karaoke-public` → 2 новых нарушения.
- [x] **T015**: `bash tools/generate-eslint-baseline.sh webvue3` (позиции строк сместились).
- [x] **T016**: `bash tools/generate-eslint-baseline.sh karaoke-public`.
- [x] **T017**: Повторная проверка — оба `check-eslint-baseline.sh` → OK.
- [x] **T018**: `bash tools/baseline-stats.sh` → 426 (было 436, −10).

## Phase 2: Workflow

- [x] **T021**: Создать `.github/workflows/lint.yml` (4 job-а: backend, frontend, docs, baseline-stats).
- [x] **T022**: Валидация YAML (`python3 -c "import yaml; yaml.safe_load(...)"`).
- [x] **T023**: Проверить что `permissions: contents: read` стоит.
- [x] **T024**: Проверить что `concurrency: cancel-in-progress: true` стоит.
- [x] **T025**: Проверить что `cache: gradle` / `cache: npm` настроены.
- [x] **T026**: Проверить что `lycheeverse/lychee-action@v1.9.0` pinned.
- [x] **T027**: Добавить `on: workflow_dispatch: {}` (Q3 из spec.md).

## Phase 3: Per-feature документ (FR-009)

- [x] **T031**: Создать `docs/features/ci-lint-enforcement.md`.
- [x] **T032**: Шапка: `> **Feature Key**: ci-lint-enforcement`, `> **Status**: active`, slug = имя файла.
- [x] **T033**: 6 обязательных секций: Что делает / Зачем / Как работает / Инварианты / Известные ловушки / Ссылки.
- [x] **T034**: `bash tools/check-feature-doc.sh docs/features/ci-lint-enforcement.md` → OK.

## Phase 4: Speckit-артефакты

- [x] **T041**: Создать `specs/002-ci-lint-enforcement/spec.md` (FR-001..FR-005, NFR-001..NFR-003, SC-001..SC-002).
- [x] **T042**: Создать `specs/002-ci-lint-enforcement/plan.md` (архитектура, ключевые решения D1-D5, риски R1-R4).
- [x] **T043**: Создать `specs/002-ci-lint-enforcement/tasks.md` (этот файл).

## Phase 5: Документация

- [ ] **T051**: Обновить `docs/features/README.md` — добавить `ci-lint-enforcement` в оглавление.
- [ ] **T052**: Обновить `CONTRIBUTING.md` — раздел «Поток разработки: CI flow» (3-5 строк).
- [ ] **T053**: Обновить `README.md` — badge GitHub Actions + ссылка на workflow.

## Phase 6: Commit + Push + PR

- [ ] **T061**: `git add webvue3/src karaoke-public/src webvue3/.eslint-baseline.json karaoke-public/.eslint-baseline.json`.
- [ ] **T062**: Commit 1: `style: apply prettier formatting + regenerate eslint baselines`.
- [ ] **T063**: `git add .github/workflows/lint.yml docs/features/ specs/002-ci-lint-enforcement/`.
- [ ] **T064**: Commit 2: `feat(ci): add GitHub Actions lint enforcement workflow`.
- [ ] **T065**: `git push origin 002-ci-lint-enforcement`.
- [ ] **T066**: Показать пользователю URL PR (НЕ создавать автоматически).

## Phase 7: Verify (post-merge, ручной)

- [ ] **T071**: Добавить branch protection rule в GitHub UI (required check: `Lint`).
- [ ] **T072**: Создать тестовый PR с `// noinspection` в Kotlin → убедиться что CI валится.
- [ ] **T073**: Обновить `AGENTS.md` — добавить ссылку на workflow + per-feature doc.
