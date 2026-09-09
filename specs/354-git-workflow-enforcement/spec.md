# Feature Specification: Git workflow enforcement (Pass 353)

**Feature Branch**: `[354-git-workflow-enforcement]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: Owner «как сделать так, чтобы я больше не комитил в master? Какими БОЛЬШИМИ БУКВАМИ или MUST или ещё как и что надо прописать в AGENTS.md или в конституцию или ещё куда, чтобы 100% заблокировать?»

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `none` (governance infra amendment — не работа по конкретной OpenProject задаче).
- **Title**: Git workflow enforcement.
- **Created in OpenProject**: N/A.

**Workflow** (для будущих спек):
1. **Claim** (Pass 350 hook): `bash tools/tracker.sh claim-issue <NNN>` — при наличии OpenProject issue.
2. **Add comment** (после merge): `bash tools/tracker.sh add-comment <NNN> --file specs/<NNN>-<slug>/report.md`.
3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review <NNN>`.
4. **Close** (owner): `bash tools/tracker.sh close-issue <NNN>`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы**:
  1. `grep -ri "прям.е коммит.\|git workflow\|branch protection" knowledge/ AGENTS.md` → existing rules found.
  2. `grep -rn "tools/git-hooks\|pre-commit\|.pre-commit-config" .pre-commit-config.yaml tools/` → existing pattern.
  3. `cat .github/workflows/lint.yml | grep -A2 "name:" | head -10` → existing CI structure.
  4. `git log --all --oneline | grep -i "master.*commit\|direct.*master" | head` → history of governance violations (Pass 353 = my own mistake).
- **Knowledge files consulted**:
  - `AGENTS.md` (v2.3.0) — runtime governance. **Updagraded в этом PR** (Pass 353 секция).
  - `.pre-commit-config.yaml` (existing) — pre-commit hooks (ktlint, eslint, prettier, etc.). **Updated в этом PR** (добавлен `block-master-commit` hook).
  - `.github/workflows/lint.yml` (existing) — CI lint workflow. **Updated в этом PR** (добавлен "No direct commits to master" step).
  - `tools/git-hooks/` (NEW) — NEW directory с `pre-commit-block-master.sh` (Pass 353).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Pre-commit hook блокирует local commit в master (Priority: P1)

**Описание**: Agent пытается `git commit` на ветке `master` (например, случайно через `git checkout master && git commit`). Pre-commit hook (через pre-commit framework) выводит понятное сообщение с инструкцией правильного workflow.

**Independent Test**: `pre-commit run --all-files` (или просто hook) на ветке master → exit 1 + понятное сообщение.

**Acceptance Scenarios**:

1. **Given** `git branch --show-current` = `master`, **When** agent выполняет `git commit -m "test"`, **Then** hook выводит «⛔ BLOCKED: прямой commit в 'master' ЗАПРЕЩЁН» и exit 1.
2. **Given** agent на feature-ветке `354-...`, **When** agent выполняет `git commit -m "test"`, **Then** hook НЕ срабатывает, commit проходит.

---

### User Story 2 — CI lint step ловит direct commits bypass'нутые через branch protection (Priority: P1)

**Описание**: Если каким-то образом (admin bypass до branch protection, force-push, прямой push через `gh api`) на master попал non-merge commit, CI lint step обнаруживает это в течение 24h и падает.

**Independent Test**: `git log --first-parent origin/master --since=24.hours --no-merges` → если > 0 → fail.

**Acceptance Scenarios**:

1. **Given** `git log --first-parent origin/master --since=24.hours --no-merges` возвращает 0, **Then** CI step prints "✅ No direct commits to master".
2. **Given** кто-то вручную push'нул commit в master (force-push bypass), **Then** CI step fails с "❌ FAIL: N direct commit(s) on master" + выводит список.

---

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST иметь pre-commit hook `tools/git-hooks/pre-commit-block-master.sh` (bash script, executable), который блокирует `git commit` если `git branch --show-current` равен `master` или `main`.
- **FR-002**: Hook MUST быть зарегистрирован в `.pre-commit-config.yaml` под id `block-master-commit` (always_run=true).
- **FR-003**: CI workflow `lint.yml` MUST содержать step "No direct commits to master (Pass 353)" который запускает `git log --first-parent origin/master --since=24.hours --no-merges` и fail при > 0.
- **FR-004**: `AGENTS.md` § "Git — CI-gate для master" MUST явно перечислять 3 enforcement layers (server-side, client-side, safety net) со ссылками на конкретные скрипты/файлы.
- **FR-005**: Hook MUST выводить понятное сообщение (на русском) с инструкцией правильного workflow (`reserve-branch-number.sh`).
- **FR-006**: Hook MUST быть symlink-friendly (можно `ln -s ../../tools/git-hooks/pre-commit-block-master.sh .git/hooks/pre-commit`) для manual install, но основной путь — через `pre-commit install`.

### Non-Functional

- **NFR-001**: Hook выполняется < 50ms (одна `git rev-parse` команда).
- **NFR-002**: CI step выполняется < 30s (одна `git log` команда).
- **NFR-003**: Hook работает на всех POSIX системах (bash, `git`).
- **NFR-004**: Hook поддерживает как `master`, так и `main` (на случай переименования).

### Key Entities

- **`tools/git-hooks/pre-commit-block-master.sh`** — bash script, ~70 lines, executable.
- **`.pre-commit-config.yaml` (existing, modified)** — добавлен `block-master-commit` hook.
- **`.github/workflows/lint.yml` (existing, modified)** — добавлен "No direct commits to master" step.
- **`AGENTS.md` (existing, modified)** — секция "Git — CI-gate для master" обновлена с явным перечислением 3 enforcement layers.

## Success Criteria

- **SC-001**: Pre-commit hook успешно блокирует `git commit` на master (тест: `git checkout master && git commit --allow-empty -m "test"` → exit 1, hook output виден).
- **SC-002**: CI step `No direct commits to master` на master в текущем виде (все коммиты — merge commits через PR) → PASS (exit 0, "✅ No direct commits").
- **SC-003**: `AGENTS.md` § "Git — CI-gate для master" содержит explicit ссылки на 3 enforcement layers + ссылку на прецедент (Pass 353).

## Assumptions

1. **GitHub branch protection** (server-side, primary defense) — **вне scope этой спеки** (управляется через GitHub UI/Settings владельцем). Тем не менее, AGENTS.md упоминает его как primary layer — он ДОЛЖЕН быть настроен.
2. **Pre-commit framework** уже используется в проекте (см. `.pre-commit-config.yaml`). Добавление `block-master-commit` hook — естественное расширение.
3. **CI workflow** уже запускается на каждый push/PR (existing). Добавление нового step — additive.
4. **Force-push prevention** через GitHub branch protection "Allow force pushes: OFF" — вне scope этой спеки.

## Out of Scope

- **Сам факт настройки GitHub branch protection** (это делает владелец через Settings → Branches).
- Изменения в `tools/tracker.sh` API.
- Изменения в `.specify/extensions.yml` (governance hooks — Pass 350 уже настроен).

## Реализация (готов)

### Изменения файлов

```
A  tools/git-hooks/pre-commit-block-master.sh                    # NEW ~70 lines
M  .pre-commit-config.yaml                                       # +block-master-commit hook
M  .github/workflows/lint.yml                                    # +No direct commits step
M  AGENTS.md                                                      # v2.3.0: enforcement layers
A  specs/354-git-workflow-enforcement/{spec,checklists}.md        # governance traceability
```

### Validation

| Проверка | Ожидаемо |
|---|---|
| `pre-commit run --all-files` на feature-ветке | PASS (hook N/A) |
| `pre-commit run --all-files` на master branch (временно) | exit 1, hook output виден |
| `bash tools/lint-knowledge.py --baseline ...` | OK, no NEW violations |
| `bash tools/check-spec-issue-link.py` | OK (1/1 modern) |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| `gh pr checks <#354>` | 9/9 PASS |

### Rollback

`git revert <merge-commit>` — hook скрипт + .pre-commit-config + .lint.yml откатываются. AGENTS.md секция удаляется.

## Clarifications

- Q1: «Какие 3 уровня защиты?» Решено: GitHub branch protection (server-side, primary) + pre-commit hook (client-side) + CI lint (server-side safety net).
- Q2: «Что делать если GitHub branch protection не настроен?» Решено: CI step + pre-commit hook покрывают 80% случаев. Branch protection — финальный 100%.
- Q3: «Hook для main vs master?» Решено: оба (`PROTECTED_BRANCHES=("master" "main")` в hook).
