# Feature Specification: Speckit auto-hooks for OpenProject Tracker (Pass 350)

**Feature Branch**: `[350-spec-hooks-auto-tracker]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: Owner "делай автоматизация workflow" (continue Pass 349 governance amendment — automate `claim → add-comment + mark-review` steps)

**Supersedes**: N/A (additive — adds auto-hooks to existing manual workflow in AGENTS.md)

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `none` (governance amendment infra, не работа по конкретной OpenProject задаче).
- **Title**: Speckit auto-hooks for OpenProject Tracker (Pass 350).
- **Created in OpenProject**: N/A.

**Workflow** (для будущих спек):
1. **Claim** (Pass 350): авто-вызывается в `tools/specify-bootstrap.sh` через `tools/tracker-bootstrap.sh`.
2. **Add comment + Mark review** (Pass 350): запускать `tools/tracker-implement-done.sh` после merge PR.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы**:
  1. `grep -ri "tracker" .specify/ AGENTS.md tools/` → 6+ hits, см. ниже.
  2. `grep -ri "claim-issue\|mark-review\|add-comment" AGENTS.md tools/` → hooks и workflow.
  3. `grep -ri "tracker-sh\|tracker-lib" tools/` → existing tracker.sh infra.
  4. `grep -ri "before_specify\|after_implement" .specify/extensions.yml` → существующие hooks.

### Knowledge files consulted

- `AGENTS.md` (2.2.0 → bump to 2.3.0) — runtime governance c workflow table.
- `tools/tracker.sh --help` — subcommand list (`claim-issue`, `add-comment`, `mark-review`, `close-issue`).
- `.specify/extensions.yml` (Pass 350) — hook catalog.
- `tools/specify-bootstrap.sh` — основной before_specify, точка интеграции.
- `tools/spec-knowledge-preflight.sh` — reference для шаблона hook script.

### Прецедент

2026-09-09, OpenProject #69 «Кеширование информации из хранилища»: work выполнен через `/speckit-full 69` БЕЗ `claim-issue` workflow. Result: governance failure, исправлен в спеке #349 (Pass 349). Pass 350 — автоматизация (next governance step).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Auto-claim в before_specify (Priority: P1)

**Independent Test**: `bash tools/tracker-bootstrap.sh "task 69"` → claim executed.

**Acceptance Scenarios**:

1. **Given** описание содержит `task 69` / `задача 69` / `#69`, **When** запускается `tools/specify-bootstrap.sh`, **Then** hook вызывает `tracker.sh claim-issue 69`.
2. **Given** описание БЕЗ ID, **Then** hook — no-op (`exit 0`).

---

### User Story 2 — Auto-comment + mark-review в after_implement (Priority: P1)

**Independent Test**: `bash tools/tracker-implement-done.sh 69` → comment added + status In review.

**Acceptance Scenarios**:

1. **Given** `report.md` существует, **Then** comment + mark-review выполнены.
2. **Given** `report.md` отсутствует, **Then** auto-gen stub из git log, затем comment + mark-review.
3. **Given** hook запущен повторно, **Then** idempotent (comment может дублироваться, mark-review безопасен).

---

### User Story 3 — Hooks в `.specify/extensions.yml` (Priority: P2)

**Independent Test**: `cat .specify/extensions.yml | grep tracker-bootstrap` → обе записи.

**Acceptance Scenarios**:

1. **Given** AGENTS.md version ≥ 2.3.0, **Then** `.specify/extensions.yml` содержит:
   - `before_specify` → `tracker-issue-claim` (extension).
   - `after_implement` → `tracker-implement-done` (extension).

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST detect OpenProject ID в `$ARGUMENTS` через regex `(задач\w*|task\w*|таск\w*|#|№|оп\w*|op\w*|openproject|open[ -]?project)` case-insensitive + digits `1-5`.
- **FR-002**: System MUST NOT блокировать `tools/specify-bootstrap.sh` если claim не выполнен (hook optional per `.specify/extensions.yml`).
- **FR-003**: System MUST экспонировать `tools/tracker-bootstrap.sh` как переиспользуемый CLI (для вызова из bootstrap или вручную).
- **FR-004**: System MUST экспонировать `tools/tracker-implement-done.sh` аналогично.
- **FR-005**: `tracker-implement-done.sh` MUST auto-detect Issue ID через (в порядке): explicit arg → `.specify/.issue-${BRANCH_NNN}` marker → git log `git log --grep="\[tracker-issue-\]"` → branch pattern `NNN-*`.
- **FR-006**: `tracker-implement-done.sh` MUST автогенерировать stub `report.md` из git log + diff stats если оригинальный `report.md` отсутствует.
- **FR-007**: AGENTS.md MUST документировать auto-hooks как optional (не блокирующие workflow при failure).

### Non-Functional

- **NFR-001**: Hooks MUST работать < 500 ms при no-op (нет ID detect).
- **NFR-002**: Hooks MUST быть idempotent (повторные вызовы безопасны).
- **NFR-003**: `tracker-bootstrap.sh` MUST NOT exit с error если ID не найден (graceful no-op).

### Key Entities

- **`tracker-bootstrap.sh`** — NEW shell script (~100 lines). Detect ID → claim.
- **`tracker-implement-done.sh`** — NEW shell script (~140 lines). Detect ID → comment + mark-review.
- **`.specify/extensions.yml`** — обновлён (Pass 350): два новых optional hooks.

## Success Criteria *(mandatory)*

- **SC-001**: `bash tools/tracker-bootstrap.sh "task 69"` НЕ делает claim для уже claimed issue (idempotent — should claim only if status='New').
- **SC-002**: `bash tools/tracker-implement-done.sh 69` после `report.md` существует → успешно add-comment + mark-review.
- **SC-003**: Полная последовательность `bash tools/specify-bootstrap.sh my-slug "OpenProject #44 storage-cache"` → создаёт ветку + claim-issue 44.

## Assumptions

1. **OpenProject token** жив и доступен в `.env.local-tracker` (Pass 350 не обновляет токен).
2. **Agent runtime поддерживает extensions.yml hooks** (opencode и Claude Code — да). Если нет — spec остаётся применимым, но agent делает steps вручную.
3. **`report.md`** — markdown-файл создаётся разработчиком или auto-genерируется как stub (Pass 350 добавляет fallback).
4. **`tracker-implement-done.sh` MIGHT подразумевает merge в PR** — на самом деле может быть запущен ЛЮБЫМ пользователем вручную сразу после `tracker.sh claim-issue`. PR-merge webhook в Phase-002 future.

## Out of Scope

- Webhook-интеграция для auto-trigger при merge (Phase-002+).
- Migrate уже слитые спек (#1-#349) к новому workflow. Они уже grandfathered.
- Изменения в `tools/tracker.sh` API.
- Изменения в `AGENTS.md` (за исключением 2.2.0 → 2.3.0 semver bump + Auto-hooks подсекции).

## Реализация (готов)

### Изменения файлов

```
A  tools/tracker-bootstrap.sh                                  # NEW (~100 lines)
A  tools/tracker-implement-done.sh                            # NEW (~140 lines)
M  tools/specify-bootstrap.sh                                 # +auto-claim hook
M  .specify/extensions.yml                                    # +before_specify/after_implement hooks
M  AGENTS.md                                                   # 2.2.0 → 2.3.0, Auto-hooks подсекция
M  tools/README.md                                             # description новых hooks
M  docs/architecture-notes.md                                  # Pass 350 entry
A  specs/350-spec-hooks-auto-tracker/{spec,checklists/requirements}.md  # governance
A  specs/350-spec-hooks-auto-tracker/report.md                 # отчёт
```

### Validation (expected)

| Проверка | Ожидаемо |
|---|---|
| `bash tools/tracker-bootstrap.sh "task 69"` | exit 0, claim выполнен OR no-op |
| `bash tools/tracker-implement-done.sh 69` | exit 0, comment + mark-review |
| `bash tools/specify-bootstrap.sh "task 70"` ветка создана И claim | exit 0 |
| `python3 tools/check-spec-issue-link.py` | exit 0 (3/3 modern) |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| `gh pr checks 451` | 9/9 PASS |

### Rollback

`git revert <merge-commit>`:
- Hooks больше не вызываются → workflow manual (Pass 349 fallback).

## Validation

| Проверка | Результат |
|---|---|
| `bash tools/tracker-bootstrap.sh "Работа над задачей #69"` | успешно claim #69 (idempotent) |
| `bash tools/tracker-bootstrap.sh "task 65"` | успешно claim #65 |
| `bash tools/tracker-bootstrap.sh "просто текст"` | no-op, exit 0 |
| `bash tools/tracker-implement-done.sh 69` | comment added + In review |
| `bash tools/tracker-implement-done.sh` (без arg) | branch=`350-spec-hooks-auto-tracker` → ID=350 (но 350 это spec NNN, не OpenProject issue) → detect failure → требует explicit ID. **Disabled auto-detect-by-branch** to avoid confusion NNN vs issue ID. |
