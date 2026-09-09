# Feature Specification: OpenProject Tracker MUST-link gate (Pass 349)

**Feature Branch**: `[349-tracker-must-link]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: Owner feedback "в #69 ничего не зафиксировано; внести необходимые изменения в правила работы с openproject в рамках speckit, чтобы описание проделанной работы было MUST, его наличие проверялось и валидировалось (CI, линтеры и т.п.)"

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `none` (governance amendment в рамках реализации, не работа по конкретной задаче OpenProject).
- **Title**: OpenProject Tracker MUST-link gate (Pass 349).
- **Created in OpenProject**: N/A.

**Workflow**:
1. **Add comment** (после merge PR #449): `bash tools/tracker.sh add-comment <69> --file specs/344-storage-metadata-cache/report.md` — кросс-ссылка на основной task.
2. **Mark review** (после merge PR #449): `bash tools/tracker.sh mark-review 69` (повторно, для прозрачности).
3. **Close** (owner): не наша зона — владелец reviewает PR и закрывает #69.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09
- **Grep-запросы**:
  1. `grep -ri "tracker" .specify/ AGENTS.md tools/` → 6 hits (см. выше).
  2. `grep -ri "claim-issue\|mark-review" AGENTS.md tools/` → 2 hits (AGENTS.md § OpenProject + tools/tracker.sh).
  3. `grep -ri "tools/spec-template.md\|lint-knowledge" .specify/ AGENTS.md` → spec-template.md, lint-knowledge.py.
  4. `git log --oneline --all -- AGENTS.md` → PASS 340 commit (must #0 governance).

### Knowledge files consulted

- `AGENTS.md` (2.2.0, Pass 349) — runtime governance с policy `tracker.sh claim-issue`.
- `tools/tracker.sh --help` — subcommand list (`claim-issue`, `add-comment`, `mark-review`, `close-issue`).
- `.specify/templates/spec-template.md` (Pass 343+) — секция «Knowledge References» mandatory для Constitution IX.
- `.specify/extensions.yml` — существующие хуки (нет pre/post для tracker).

### Прецедент

2026-09-09, OpenProject #69 «Кеширование информации из хранилища»:
work выполнен через `/speckit-full 69` БЕЗ `claim-issue`/`add-comment`/`mark-review`.
Отчёт опубликован задним числом после merge PR #448 (commit `81a3d1e1`).
Причина: workflow `tracker.sh claim-issue → ...` НЕ был enforced'нут в CI.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Спека ОБЯЗАНА иметь OpenProject Tracking (Priority: P1)

**Описание**: При создании новой спеки через `/speckit.specify <description>` поле **OpenProject Tracking** в spec.md заполняется сразу. Секция содержит Issue ID, Title, Workflow. CI блокирует merge без неё.

**Independent Test**: `python3 tools/check-spec-issue-link.py` для `specs/NNN-new-spec/spec.md` без секции → exit 1.

**Acceptance Scenarios**:

1. **Given** новая спека создаётся, **When** запускается `python3 tools/check-spec-issue-link.py`, **Then** exit 1 с сообщением «missing required heading '## OpenProject Tracking'» для modern-спек (с `## Knowledge References`).
2. **Given** старая спека без `## Knowledge References` (pre-Phase-002), **When** запускается `python3 tools/check-spec-issue-link.py`, **Then** OK (grandfathered).
3. **Given** modern-спека содержит секцию с Issue ID = `none`, **When** запускается линтер, **Then** OK (claim-issue не требуется для spontaneous work).

---

### User Story 2 — AGENTS.md workflow documented (Priority: P1)

**Описание**: AGENTS.md содержит explicit 4-step workflow table с командами и моментами вызова. Указано, что `claim` — ПЕРЕД первой строкой кода, `add-comment + mark-review` — после merge.

**Independent Test**: grep `AGENTS.md` для `tracker.sh claim-issue|tracker.sh mark-review|tracker.sh add-comment` → все три встречаются.

**Acceptance Scenarios**:

1. **Given** README проекта, **When** AI-агент стартует сессию, **Then** AGENTS.md § «Issue-tracker OpenProject» содержит WORKFLOW с 4 шагами и командами.
2. **Given** новая сессия, **When** агент начинает спекy с Issue ID, **Then** spec.md § OpenProject Tracking содержит команды claim/add-comment/mark-review.

---

### User Story 3 — CI gate в `lint.yml` (Priority: P2)

**Описание**: `.github/workflows/lint.yml` запускает `python3 tools/check-spec-issue-link.py` на каждом push. Если линтер падает — merge blocked (или red на PR).

**Independent Test**: PR без обновлённой спеки → 1/9 checks FAIL.

**Acceptance Scenarios**:

1. **Given** PR без OpenProject Tracking в новой спеке, **When** CI запускается, **Then** `Lint spec ↔ OpenProject link` check → FAIL.

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST требовать секцию `## OpenProject Tracking` в каждой modern-спеке (с `## Knowledge References`). CI gate enforces.
- **FR-002**: System MUST содержать `Issue ID`, `Title`, `Workflow` поля в секции. Хотя бы одно из обязательных команд `tracker.sh claim-issue`, `tracker.sh add-comment`, `tracker.sh mark-review` должно быть упомянуто в Workflow (если Issue ID != `none`).
- **FR-003**: System MUST grandfather старевшие спеки (без `## Knowledge References`) — линтер для них no-op.
- **FR-004**: System MUST НЕ менять публичные контракты spec-template.md (только добавить секцию `## OpenProject Tracking` в REQUIRED блок).
- **FR-005**: System MUST обновить AGENTS.md § «Issue-tracker OpenProject» до explicit 4-step workflow table (CLAIM + ADD-COMMENT + MARK-REVIEW + CLOSE).
- **FR-006**: System MUST добавить в `.github/workflows/lint.yml` step `Lint spec ↔ OpenProject link`.
- **FR-007**: System MUST добавить tool `tools/check-spec-issue-link.py` в `tools/README.md`.

### Non-Functional

- **NFR-001**: Linter должен работать < 100 ms на полном `specs/` (153+ спецификаций).
- **NFR-002**: Linter MUST НЕ требовать внешних зависимостей (только Python stdlib + `pathlib`).
- **NFR-003**: Линтер exit code: 0 = OK, 1 = FAIL с детальным отчётом в stderr.

### Key Entities

- **`Spec.md`** — markdown-файл в `specs/NNN-slug/`. Должен содержать `## OpenProject Tracking` секцию для modern-спек.
- **`OpenProject Issue ID`** — числовой ID work package. Issue ID = `none` допустимо для spontaneous-работы без issue.
- **`CI Step`** — entry в `.github/workflows/lint.yml`, выполняется на push + PR.

## Success Criteria *(mandatory)*

- **SC-001**: `python3 tools/check-spec-issue-link.py` на текущем master → exit 0 (после grandfather существующих modern-спек и обновления #344/#348).
- **SC-002**: AGENTS.md содержит таблицу WORKFLOW с 4 строками (claim, add-comment, mark-review, close).
- **SC-003**: `tools/lint-knowledge.py` baseline-файл **НЕ** затрагивается этой спекой (только новый lint added).
- **SC-004**: Все 9/9 CI checks проходят на PR #449 (этот PR).
- **SC-005**: В OpenProject #69 comment с report.md опубликован ДО merge этого PR (см. governance note в spec).

## Assumptions

1. **OpenProject token** (`TRACKER_API_TOKEN`) уже доступен через `.env.local-tracker` (установлен 2026-09-02, см. `tools/install-tracker.sh`).
2. **Все specs с `## Knowledge References`** — это Phase-002+ (Pass 340+) — нуждаются в OpenProject Tracking. До этого grandfathered.
3. **`report.md`** — markdown-файл с полным описанием проделанной работы. Шаблон — минимум: что сделано, validation-результаты, файлы изменены, follow-up.
4. **CI baseline** — `config/knowledge/baseline-knowledge-lint.txt` не нуждается в обновлении (эта спека — governance, не lint-knowledge).

## Out of Scope

- Автоматизация workflow через хуки в `.specify/extensions.yml` (например, `before_specify` → auto-claim). Это ОТДЕЛЬНАЯ задача потому, что требует изменений в workflow обработке спекификации. **Планируется как #350-spec-hooks**.
- Изменения в `tools/tracker.sh` (его API достаточен).
- Миграция 151 старых спек. (Grandfathered.)
- Изменения в `tools/spec-knowledge-preflight.sh`.

## Migration Path

### Что нужно сделать для существующих modern-спек

Сейчас таких всего 2 (`#344` и `#348`):
1. **`#344`** — pre-fix этого PR. Будет обновлён: добавлена `## OpenProject Tracking` с Issue ID `#69`, Workflow, фактическим статусом (claim/add-comment/mark-review выполнено задним числом).
2. **`#348`** — также обновлена.

После merge этого PR → 0 modern-спек не соответствует gate'у.

### Что НЕ нужно делать

- 151 pre-Phase-002 спек остаются без `## OpenProject Tracking` — grandfather политика.

## Реализация (План будет в следующей фазе /speckit.plan)

### Шаги

1. ✅ Создать `tools/check-spec-issue-link.py` (Pass 349, готов).
2. ✅ Добавить секцию `## OpenProject Tracking` в `.specify/templates/spec-template.md`.
3. ✅ Добавить step в `.github/workflows/lint.yml`.
4. ✅ Обновить `AGENTS.md` § «Issue-tracker OpenProject» (2.1.0 → 2.2.0).
5. ✅ Обновить обе современные спеки (#344, #348) с OpenProject Tracking.
6. ✅ Backend link #69: claim-issue + add-comment (report.md) + mark-review → In review (готов).
7. Update `tools/README.md` с описанием `check-spec-issue-link.py` (если есть место).
8. Push branch, создать PR #449.

### Файлы изменены (готовы для коммита)

```
M AGENTS.md                                                                  (2.1.0 → 2.2.0)
M .github/workflows/lint.yml                                                (добавлен step)
A tools/check-spec-issue-link.py                                            (новый)
A tools/check-spec-issue-link.py symlink-доступ через chmod +x
M .specify/templates/spec-template.md                                       (добавлена секция)
M specs/344-storage-metadata-cache/spec.md                                  (OpenProject Tracking + governance note)
M specs/348-storage-cache-eternal/spec.md                                   (OpenProject Tracking + governance note)
A specs/344-storage-metadata-cache/report.md                                (10133 chars)
```

## Validation

| Проверка | Ожидаемо |
|---|---|
| `python3 tools/check-spec-issue-link.py` | exit 0, OK: 2/2 modern spec(s) |
| `bash tools/check-knowledge-structure.sh` | exit 0, 9/9 checks |
| `python3 tools/lint-knowledge.py --baseline ...` | exit 0, no NEW violations |
| `gh pr checks 449` | 9/9 PASS |

## Rollback

`git revert <merge-commit>`:
- Удаляет step `Lint spec ↔ OpenProject link` из CI.
- Возвращает AGENTS.md к старой версии (2.1.0).
- Удаляет `tools/check-spec-issue-link.py`.
- Существующие спеки **сохранят** свои OpenProject Tracking секции (additive, not destructive).
- OpenProject #69 остаётся в `In review` (не откатывается).
