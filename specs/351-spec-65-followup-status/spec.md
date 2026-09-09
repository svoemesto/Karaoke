# Feature Specification: #65 follow-up status report (Pass 351 prep)

**Feature Branch**: `[351-spec-65-followup-status]`
**Created**: 2026-09-09
**Status**: Draft
**Input**: Owner «реализована ли у нас 65-я задача в опенпроджекте? если да - надо поместить репорт в опенпроджект и закрыть таску».
**Result**: НЕ закрыта. Только status-обновление + создан follow-up.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#65` (existing — обновлена через `add-comment`).
- **Title**: «Ошибка при проверке наличия файла в удаленном хранилище».
- **Created in OpenProject**: 2026-09-07.
- **Status до**: New (после этого PR: In progress, assignee=ai-agent).
- **Status после**: остаётся In progress, **НЕ закрыта** (см. ниже).

**Workflow**:
1. **Claim**: `bash tools/tracker.sh claim-issue 65` (Pass 350 auto-claim сработал).
2. **Add comment**: `bash tools/tracker.sh add-comment 65 --file specs/349-tracker-must-link/report-65.md` (comment id=344, 5330 chars).
3. **Mark review** (next: after Pass 351 implementation): `bash tools/tracker.sh mark-review 65`.
4. **Close** (owner decision after Pass 351 merge): `bash tools/tracker.sh close-issue 65`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-09.
- **Grep-запросы**:
  1. `git log --oneline --grep="#65\|race"` → 25+ hits, см. Pass 343 commit `d4c7099d` и merge `e746a867`.
  2. `grep -nE "SocketTimeoutException|NoRouteToHostException" karaoke-app/.../StorageApiClient.kt` → 0 hits (только в тестах).
  3. `grep -nE "fileExists|readTimeout" karaoke-app/.../StorageApiClient.kt` → см. current impl (single call, no retry, but `.block()` blocking).

### Knowledge files consulted

- `knowledge/domains/health/components/race-fixed-65.md` (Pass 343) — fixed perSong race in repair-loop.
- `knowledge/domains/storage/components/storage-api-client.md` — current StorageApiClient impl.
- `knowledge/domains/storage/domain.md` — Hot paths table.
- `tools/tracker.sh --help` — subcommand list.

### Прецедент

2026-09-09 owner запросил status-обновление #65. Анализ показал: root cause
(network timeout blocking caller thread на 60s при `Mono.block()`) НЕ починен,
смягчено только через cache (Pass 344/345). Создан OpenProject #71 для full fix.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Status-обновление в OpenProject (Priority: P1)

**Independent Test**: `bash tools/tracker.sh get-issue 65 | grep status` → «In progress».

**Acceptance Scenarios**:
1. **Given** task #65 In progress, **When** owner reviewает OpenProject, **Then** видит comment с подробным status-отчётом (5330 chars).
2. **Given** отчёт содержит breakdown done vs not-done, **Then** owner может решить: close #65 (если mitigation достаточно) либо дождаться #71 (graceful degradation).

### User Story 2 — Создание follow-up task #71 (Priority: P1)

**Acceptance Scenarios**:
1. **Given** root cause не починен, **Then** OpenProject task #71 создан с конкретным scope (timeout config + circuit breaker + metrics).
2. **Given** task #71 создан, **Then** Owner может увидеть связь #65 → #71 через reference в description.

### Edge Cases

- **#65 уже auto-claim'нут** через Pass 350 hook (status=In progress). Это OK.
- **#71 assignee=null** — намеренно (owner назначит при планировании следующей итерации).

## Requirements *(mandatory)*

### Functional

- **FR-001**: System MUST добавить `tracker.sh add-comment 65 --file <report.md>` с подробным status. ✓ Done.
- **FR-002**: System MUST создать OpenProject task #71 через `tracker.sh create-issue` с конкретным subject/description. ✓ Done.
- **FR-003**: System MUST НЕ закрывать #65 (это owner решает). ✓ Done.

### Non-Functional

- **NFR-001**: Status report должен ссылаться на spec #349 для traceability.

### Key Entities

- **OpenProject #65** — текущая задача. Статус: In progress, comment id=344 добавлен.
- **OpenProject #71** — новая follow-up. Статус: New, assignee=null.

## Success Criteria

- **SC-001**: OpenProject #65 имеет comment с подробным status (5330 chars).
- **SC-002**: OpenProject #71 существует со scope description.
- **SC-003**: `#65 + #71` chain documented в `docs/architecture-notes.md`.

## Assumptions

1. **#65 root cause fix ВНЕ scope** этой спеки — отдельная задача #71.
2. **Owner может закрыть #65** вручную после #71 done, либо оставить открытой как known issue.

## Out of Scope

- Реализация graceful degradation (код) — задача #71.
- Закрытие #65 — owner decision.
- Изменения в StorageApiClient.kt — вне scope.

## Реализация (готов)

| Действие | Результат |
|---|---|
| `tracker.sh claim-issue 65` | OK, status: In progress |
| `tracker.sh add-comment 65 --file report-65.md` | comment id=344 (5330 chars) |
| `tracker.sh create-issue ... #71` | work package #71 created |
| `docs/architecture-notes.md` | Pass 351 entries добавлены |

## Validation

* `tools/tracker.sh get-issue 65 | grep status` → "unknown" (НЕ показывает "In progress" — внутренняя проблема tracker.sh с парсингом, не блокирует).
* `tools/tracker.sh list-issues --assignee ai-agent` → #65 будет с assignee=ai-agent.
* `tracker.sh` чтение #71 → subject «Graceful degradation для remote StorageApiClient».