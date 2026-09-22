# Feature Specification: Circuit probe не должен съедаться диагностикой (Pass 432, #156)

**Feature Branch**: `432-circuit-probe-not-consumed-by-healthcheck`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Production-наблюдение 2026-09-22 (nsa-i9): circuit=remote бесконечно
крутится `OPEN → HALF_OPEN → watchdog OPEN (probe stuck)` каждые ~30s, хотя remote
MinIO полностью доступен (3/3 health-пробы 200 за ~9 ms). Follow-up на Pass 405
(#131), 429 (#153).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#156` («StorageCircuitBreaker: диагностический acquire() съедает probe»).
- **Title**: «StorageCircuitBreaker: probe не должен съедаться диагностикой (вечный цикл OPEN→HALF_OPEN→OPEN)».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 156` — выполнено 2026-09-22.
  2. **Add comment**: `bash tools/tracker.sh add-comment 156 --file specs/432-circuit-probe-consumed-by-healthcheck/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 156`.
  4. **Close** (owner, после merge + рестарт): `bash tools/tracker.sh close-issue 156`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3):
  1. `grep -rn "acquire|HALF_OPEN|Probe|probe" knowledge/domains/storage/components/storage-api-client.md`
     → FSM `CLOSED→OPEN→HALF_OPEN→CLOSED`, «Half-open probe pattern: первый call после cooldown — single probe».
  2. `grep -n "Out of Scope|acquire|HealthReport" specs/405-storage-circuit-breaker-watchdog/spec.md`
     → **прямое указание** (строка 153): «HealthReport integration с `decorate` — сейчас `cb.acquire()` без loader… нужно рефакторить `HealthReport.actionsLocalStorage` — это отдельная задача». Это и есть данная спека.
  3. `grep -rn "acquire()" karaoke-app/src/main` → только 2 места: `HealthReport.kt:657` (local), `HealthReport.kt:978` (remote).
  4. `grep -rn "Probe" karaoke-app/src/main/kotlin/.../StorageCircuitBreaker.kt`
     → `acquire()` делает CAS `OPEN→HALF_OPEN` и возвращает `Decision.Probe`; `decorate`/`executeBlocking` — единственные, кто реально исполняет probe.
  5. `grep` логов → `probe failed`=0, `HALF_OPEN→CLOSED`=0, `probe stuck`≥2; remote MinIO health=200.

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — FSM, Pass 351/372/426/428/429; нужно дополнить Pass 432.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — circuit-проверка в `actionsLocalStorage`/`actionsRemoteStorage`.
- [`specs/405-storage-circuit-breaker-watchdog/spec.md`](../../specs/405-storage-circuit-breaker-watchdog/spec.md)
  — Out of Scope, Assumption #4 (описывает root cause).
- [`specs/429-split-local-remote-circuit-breakers/spec.md`](../../specs/429-split-local-remote-circuit-breakers/spec.md)
  — два брейкера (изоляция сделала баг заметным).

### Прецедент

2026-09-22 15:05–15:08 (nsa-i9):

```
15:05:56 remote CLOSED -> OPEN (failureCount=5)
15:06:27 remote OPEN -> HALF_OPEN          ← CAS сделан диагностикой
15:06:57 watchdog remote HALF_OPEN->OPEN (probe stuck) durationMs=30106
15:07:38 remote OPEN -> HALF_OPEN
15:08:08 watchdog remote HALF_OPEN->OPEN (probe stuck)
```

remote MinIO health = 200 (9 ms), `probe failed`=0, `HALF_OPEN→CLOSED`=0.

## Root cause

`HealthReport.actionsLocalStorage`/`actionsRemoteStorage` вызывают `cb.acquire()`
для fail-fast. Когда cooldown истёк, **этот диагностический вызов выигрывает CAS
`OPEN→HALF_OPEN`** и получает `Decision.Probe` — но HealthReport в ветке `Probe`
ничего не делает (не запускает реальный MinIO-вызов). Реальный probe живёт в
`decorate` (`StorageApiClientImpl`) / `executeBlocking`, но он придёт **позже** и
получит уже `FastFail` (state=HALF_OPEN). Итог: probe не исполняется **ни разу** →
`recordSuccess`/`recordFailure` не вызываются → watchdog через
`timeout+buffer` (30s) принудительно возвращает OPEN. **Вечный цикл, даже когда
хранилище живо.**

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Circuit восстанавливается после реального probe (Priority: P1)

**Описание**: Когда remote MinIO снова доступен, circuit закрывается через
штатный probe (в `decorate`/`executeBlocking`), а не зацикливается.

**Why P1**: это blocking-баг — circuit не восстанавливается самостоятельно.

**Independent Test**: unit-тест: `OPEN` + cooldown elapsed; **диагностическая**
проверка (новый метод) НЕ переводит в HALF_OPEN; первый `decorate`-вызов получает
`Probe`, выполняет loader, `recordSuccess` → CLOSED.

**Acceptance Scenarios**:

1. **Given** circuit OPEN, cooldown elapsed, **When** диагностический чекер
   (HealthReport) проверяет состояние, **Then** state остаётся OPEN (не HALF_OPEN),
   чекер не получает `Probe`.
2. **Given** circuit OPEN, cooldown elapsed, **When** `decorate(...)` (реальный
   вызов), **Then** получает `Probe`, loader исполняется, success → CLOSED.
3. **Given** circuit HALF_OPEN (probe в полёте), **When** диагностический чекер,
   **Then** fast-fail (не вмешивается).
4. **Given** circuit CLOSED, **When** диагностический чекер, **Then** «можно
   продолжать» (не fast-fail).

### User Story 2 — Диагностика сохраняет fail-fast (Priority: P1)

**Описание**: HealthReport по-прежнему делает fail-fast (без MinIO-вызова), когда
circuit OPEN и cooldown не истёк.

**Acceptance Scenarios**:

1. **Given** circuit OPEN, cooldown НЕ истёк, **When** HealthReport, **Then**
   `FATAL_ERROR` без MinIO-вызова.
2. **Given** circuit OPEN, cooldown истёк (но probe ещё не исполнен), **When**
   HealthReport, **Then** тоже `FATAL_ERROR` (не пытается стать probe'ом) — реальный
   probe сделает `decorate` при следующем storage-вызове.

## Requirements *(mandatory)*

### Functional

- **FR-001**: `StorageCircuitBreaker` MUST предоставить **нетранзишн-метод**
  (например `isFastFail(): Boolean`), который возвращает `true` только если circuit
  сейчас отвергнет вызов (`OPEN` или `HALF_OPEN`), **без** CAS `OPEN→HALF_OPEN` и
  без запуска probe.
- **FR-002**: `HealthReport.actionsLocalStorage`/`actionsRemoteStorage` MUST
  использовать этот нетранзишн-метод вместо `acquire()` — диагностика MUST NOT
  потреблять probe.
- **FR-003**: `decorate`/`decorateOrEmpty`/`executeBlocking` MUST остаться
  единственными, кто переводит `OPEN→HALF_OPEN` (через `acquire()`), чтобы probe
  всегда исполнялся реальным storage-вызовом.
- **FR-004**: Поведение fail-fast для диагностики MUST сохраниться (при OPEN или
  HALF_OPEN — `FATAL_ERROR`).
- **FR-005**: Все три состояния (CLOSED/OPEN/HALF_OPEN) MUST обрабатываться
  нетранзишн-методом: CLOSED → не fast-fail; OPEN → fast-fail; HALF_OPEN → fast-fail.
- **FR-006**: watchdog и `reset()` MUST NOT затрагиваться.

### Non-Functional

- **NFR-001**: Нетранзишн-метод — O(1), без блокировок (один `state.get()`).
- **NFR-002**: Unit-тесты: диагностика не потребляет probe; реальный probe
  закрывает circuit.
- **NFR-003**: Публичный API `acquire()` сохраняется (используется `decorate`).

### Key Entities

- **`StorageCircuitBreaker`** (MODIFY) — добавить `isFastFail()` (или
  `Decision.Diagnostics`), не меняя `acquire()`.
- **`HealthReport`** (MODIFY) — заменить `acquire()` на `isFastFail()` в двух местах.

## Success Criteria *(mandatory)*

- **SC-001**: Unit-тест: диагностическая проверка при OPEN+cooldown НЕ переводит в
  HALF_OPEN.
- **SC-002**: Unit-тест: после диагностики `decorate` получает `Probe` и закрывает
  circuit при успехе.
- **SC-003**: Все существующие `StorageCircuitBreaker*` тесты — PASS.
- **SC-004**: После рестарта: цикл `OPEN→HALF_OPEN→watchdog OPEN` исчезает;
  `grep 'probe stuck'` не растёт при живом MinIO.
- **SC-005**: `ktlintCheck` 0, `bootJar` OK, knowledge 9/9, `gh pr checks` all PASS.

## Assumptions

1. **Диагностике не нужен probe** — ей достаточно «fast-fail или нет».
2. **Реальный probe** сделает ближайший `fileExists`/`getFileInfo` через
   `decorate`/`executeBlocking` (в HealthReport это происходит в том же проходе,
   после проверки).
3. **Единственный probe** (single-flight) сохраняется.

## Out of Scope

- **Изменение FSM/порогов/cooldown** — нет.
- **Изменение watchdog** — нет.
- **Переписывание HealthReport на decorate с loader** — не требуется; достаточно
  нетранзишн-проверки.

## Migration Path

### Что нужно изменить

- `StorageCircuitBreaker.kt` (MODIFY) — `isFastFail()` (нетранзишн).
- `HealthReport.kt` (MODIFY) — 2 места `acquire()` → `isFastFail()`.
- Tests (ADD) — изоляция диагностики и probe.
- `knowledge/domains/storage/components/storage-api-client.md` (MODIFY) — Pass 432.
- `knowledge/domains/health/components/health-report.md` (MODIFY).
- `docs/features/storage-metadata-cache.md` (MODIFY) — V2.7.

### Что НЕ нужно менять

- `acquire()`/`decorate`/`executeBlocking`/watchdog/reset.

## Validation

| Проверка | Ожидаемо |
|---|---|
| Unit: диагностика не потребляет probe | PASS |
| Unit: probe через decorate → CLOSED | PASS |
| `:karaoke-app:test --tests "*StorageCircuit*"` | PASS |
| После рестарта `grep 'probe stuck'` | не растёт при живом MinIO |

## Rollback

`git revert <merge-commit>` — возврат к `acquire()` в HealthReport; вечный цикл
возвращается при нестабильном remote.

## Clarifications

### Session 2026-09-22

- **Q**: Почему не дать HealthReport исполнять probe?
  - **A**: HealthReport не должен делать MinIO-вызовы (это его контракт fail-fast);
    правильнее — не потреблять probe и отдать его `decorate`.
- **Q**: Не сломает ли это fast-fail?
  - **A**: Нет: `isFastFail` возвращает true для OPEN и HALF_OPEN — диагностика
    получает то же поведение, только без побочного CAS.
