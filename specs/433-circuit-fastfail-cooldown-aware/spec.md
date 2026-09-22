# Feature Specification: isFastFail cooldown-aware — circuit не залипает в OPEN (Pass 433, #157)

**Feature Branch**: `433-circuit-fastfail-cooldown-aware`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Production-наблюдение 2026-09-22 (nsa-i9) после Pass 432 (#156): circuit
`remote` залип в `OPEN` — перехода `OPEN→HALF_OPEN` **нет вообще**, хотя cooldown
(30s) давно истёк. `grep decision=FastFail` от `decorate` = 0. Регресс от #156.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#157` («isFastFail слишком грубый — circuit залипает в OPEN»).
- **Title**: «StorageCircuitBreaker: isFastFail cooldown-aware (follow-up #156)».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 157` — выполнено 2026-09-22.
  2. **Add comment**: `bash tools/tracker.sh add-comment 157 --file specs/433-circuit-fastfail-cooldown-aware/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 157`.
  4. **Close** (owner, после merge + рестарт): `bash tools/tracker.sh close-issue 157`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы**:
  1. `grep -rn "isFastFail|acquire|cooldown" knowledge/domains/storage/components/storage-api-client.md`
     → FSM: «OPEN → (cooldown elapsed) → HALF_OPEN → (probe success) → CLOSED».
  2. `grep -rn "isFastFail" karaoke-app/src/main` → `StorageCircuitBreaker.kt` (метод),
     `HealthReport.kt` (2 вызова).
  3. `grep -n "Out of Scope|acquire" specs/432-circuit-probe-consumed-by-healthcheck/spec.md`
     → Pass 432 убрал потребление probe, но не учёл cooldown.
  4. Логи: `OPEN` в 18:15:04, cooldown 30s, `OPEN→HALF_OPEN`=0, `probe stuck`=0,
     `decision=FastFail`=0.

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — Pass 351/372/426/428/429/432.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — circuit-проверка.
- [`specs/432-circuit-probe-consumed-by-healthcheck/spec.md`](../../specs/432-circuit-probe-consumed-by-healthcheck/spec.md)
  — предыдущий фикс (источник регресса).

### Прецедент

2026-09-22 18:15 (nsa-i9): `remote OPEN failureCount=5` в 18:15:04; через 3+ минуты
state всё ещё `OPEN`, `OPEN→HALF_OPEN`=0, `decision=FastFail` (decorate)=0,
`probe stuck`=0. Circuit не может восстановиться.

## Root cause

Pass 432 (#156) ввёл `isFastFail() = (state == OPEN || state == HALF_OPEN)`. Это
fast-fail'ит диагностику **и после истечения cooldown**. Тогда:
1. HealthReport всегда возвращает `FATAL_ERROR` для remote, не доходя до storage-вызова;
2. реальный вызов (`decorate`/`executeBlocking`) не запускается;
3. `acquire()` (единственный, кто делает `OPEN→HALF_OPEN`) не вызывается **никем**;
4. probe никогда не стартует → circuit залипает в OPEN навсегда.

До Pass 432 диагностический `acquire()` хотя бы переводил `OPEN→HALF_OPEN` (но терял
probe). Pass 432 убрал потерю probe, но и убрал сам переход.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Circuit восстанавливается после cooldown (Priority: P1)

**Описание**: После истечения cooldown первый реальный storage-вызов становится
probe'ом; успешный probe закрывает circuit.

**Independent Test**: unit-тест: OPEN + cooldown истёк → `isFastFail()`=false
(диагностика не блокирует); `acquire()`=Probe → `recordSuccess` → CLOSED.

**Acceptance Scenarios**:

1. **Given** circuit OPEN, cooldown истёк, **When** `isFastFail()`, **Then** `false`
   (диагностика не блокирует путь к probe); state всё ещё OPEN.
2. **Given** circuit OPEN, cooldown истёк, **When** `acquire()` (реальный вызов),
   **Then** `Probe` → loader → `recordSuccess` → CLOSED.
3. **Given** circuit OPEN, cooldown НЕ истёк, **When** `isFastFail()`, **Then** `true`
   (fail-fast, без MinIO-вызова).
4. **Given** circuit HALF_OPEN, **When** `isFastFail()`, **Then** `true` (probe в
   полёте — не добавляем нагрузку).
5. **Given** circuit CLOSED, **When** `isFastFail()`, **Then** `false`.

## Requirements *(mandatory)*

### Functional

- **FR-001**: `isFastFail()` MUST быть **cooldown-aware**: возвращать `true` только
  если вызов сейчас действительно отвергнется:
  - `CLOSED` → `false`;
  - `HALF_OPEN` → `true`;
  - `OPEN` → `true` **пока cooldown не истёк**, иначе `false`.
- **FR-002**: При `OPEN` + истёкшем cooldown диагностика MUST пропустить вызов,
  чтобы он дошёл до `decorate`/`executeBlocking` и стал probe'ом.
- **FR-003**: `acquire()` MUST NOT изменяться (по-прежнему делает CAS `OPEN→HALF_OPEN`).
- **FR-004**: Поведение Pass 432 (диагностика НЕ потребляет probe) MUST сохраниться:
  `isFastFail()` не делает CAS и не запускает probe.
- **FR-005**: `isFastFail()` MUST NOT менять state (только чтение).

### Non-Functional

- **NFR-001**: O(1), без блокировок.
- **NFR-002**: Unit-тесты на все 4 состояния/случая (CLOSED/OPEN до cooldown/OPEN
  после/HALF_OPEN).
- **NFR-003**: Существующие тесты обновить под новую семантику.

### Key Entities

- **`StorageCircuitBreaker.isFastFail()`** (MODIFY) — cooldown-aware.

## Success Criteria *(mandatory)*

- **SC-001**: Unit: OPEN + cooldown истёк → `isFastFail()`=false; затем `acquire()`
  =Probe → CLOSED.
- **SC-002**: Unit: OPEN + cooldown НЕ истёк → `isFastFail()`=true.
- **SC-003**: `StorageCircuitBreakerTest` — PASS (обновлённые).
- **SC-004**: После рестарта: circuit remote восстанавливается (`OPEN→HALF_OPEN→CLOSED`)
  при живом MinIO; `probe stuck` не растёт.
- **SC-005**: `ktlintCheck` 0, `bootJar` OK, knowledge 9/9, `gh pr checks` all PASS.

## Assumptions

1. **Диагностика не должна блокировать probe-путь** — после cooldown вызов пропускается.
2. **Единственный probe** (single-flight) сохраняется.
3. **`HALF_OPEN` всегда fast-fail** для диагностики.

## Out of Scope

- **Изменение FSM/порогов/cooldown** — нет.
- **Изменение watchdog** — нет.
- **Изменение `acquire()`** — нет.

## Migration Path

### Что нужно изменить

- `StorageCircuitBreaker.kt` (MODIFY) — `isFastFail()` cooldown-aware.
- `StorageCircuitBreakerTest.kt` (MODIFY) — семантика.
- `knowledge/domains/storage/components/storage-api-client.md` (MODIFY) — Pass 433.
- `docs/features/storage-metadata-cache.md` (MODIFY) — V2.8.

### Что НЕ нужно менять

- `HealthReport` (вызовы `isFastFail()` остаются), `acquire`, watchdog, reset.

## Validation

| Проверка | Ожидаемо |
|---|---|
| Unit: OPEN после cooldown → isFastFail=false | PASS |
| Unit: OPEN до cooldown → isFastFail=true | PASS |
| `:karaoke-app:test --tests "*StorageCircuit*"` | PASS |
| После рестарта circuit восстанавливается | OPEN→HALF_OPEN→CLOSED |

## Rollback

`git revert <merge-commit>` — возврат к грубому `isFastFail` (circuit залипает) или,
через revert обоих (#156+#157), к `acquire()` в HealthReport.

## Clarifications

### Session 2026-09-22

- **Q**: Почему не вернуть `acquire()` в HealthReport?
  - **A**: `acquire()` в диагностике потребляет probe (баг #156). Cooldown-aware
    `isFastFail()` даёт и fail-fast (до cooldown), и пропуск probe-пути (после), не
    потребляя probe.
