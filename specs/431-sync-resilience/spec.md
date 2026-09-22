# Feature Specification: Устойчивость синхронизации с прод-сайтом (Pass 431, #155)

**Feature Branch**: `431-sync-resilience`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Логи `karaoke-app` 2026-09-22 13:10 — `SSLHandshakeException: Remote host
terminated the handshake` (cause `IOException: Connection timed out`) при
`POST https://sm-karaoke.ru/changerecords` из `postSyncOneClick` валит весь запрос
в HTTP 500 со стектрейсом.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#155` («Устойчивость синхронизации с прод-сайтом»).
- **Title**: «Устойчивость синхронизации: таймауты + retry + изоляция ошибок changerecords».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 155` — выполнено 2026-09-22
     (assignee=`ai agent`, status `In progress`; PATCH вручную).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 155 --file specs/431-sync-resilience/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 155`.
  4. **Close** (owner, после merge + рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 155`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3):
  1. `grep -rln "updateDatabases\|runEntitySync\|changerecords\|two-db-sync" knowledge/`
     → `processing/components/two-db-sync.md`, `run-entity-sync.md`, `schedulers.md`,
     `integration/components/external-api-clients.md`, `system/utilities.md` и др.
  2. `grep -rln "retry\|timeout\|SSL\|IOException\|resilience" knowledge/domains/processing knowledge/domains/integration knowledge/guidelines`
     → `run-entity-sync.md`, `schedulers.md`, `external-api-clients.md`.
  3. `grep -rln "changerecords\|updateDatabases\|SSLHandshake" specs/` → pre-modern
     `specs/235-auto-sync-3h/*` (FR-012 per-target изоляция, FR-016 fail-fast без retry).
  4. `grep -rn "HttpClient.newBuilder\|client.send" karaoke-app/src/main/kotlin/.../Utils.kt`
     → 6 call sites (936, 963, 1160, 1188, 1216, 1253) без `try/catch`/timeout.
  5. `grep -rn "try\|catch\|runEntitySync" ApiController.kt` (postSyncOneClick)
     → `try/finally` без `catch` — исключение уходит в DispatcherServlet.

### Knowledge files consulted

- [`knowledge/domains/processing/components/two-db-sync.md`](../../knowledge/domains/processing/components/two-db-sync.md)
  — модель sync LOCAL↔SERVER, `updateDatabases`.
- [`knowledge/domains/processing/components/run-entity-sync.md`](../../knowledge/domains/processing/components/run-entity-sync.md)
  — `runEntitySync`, SyncRegistry.
- [`knowledge/domains/processing/components/schedulers.md`](../../knowledge/domains/processing/components/schedulers.md)
  — `AutoOneClickSyncScheduler` (FR-012 per-target, FR-016 fail-fast).
- [`knowledge/domains/integration/components/external-api-clients.md`](../../knowledge/domains/integration/components/external-api-clients.md)
  — как описаны внешние HTTP-вызовы.
- [`knowledge/system/utilities.md`](../../knowledge/system/utilities.md) — `Utils.kt` (hot file).
- [`specs/235-auto-sync-3h/spec.md`](../../specs/235-auto-sync-3h/spec.md) — FR-012/FR-016.

### Прецедент

2026-09-22 13:10 (nsa-i9): кратковременный сетевой сбой до прод-сайта
(`Connection timed out` на TLS-рукопожатии) → `SSLHandshakeException` из
`client.send(...)` в `updateDatabases` (`Utils.kt:1261`) → исключение прошло через
`postSyncOneClick` (нет `catch`) → Spring DispatcherServlet залогировал ERROR со
стектрейсом и вернул HTTP 500. Синхронизация «в 1 клик» полностью падала, хотя
сбой был транзиентным и разовым.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Транзиентный сбой не валит всю синхронизацию (Priority: P1)

**Описание**: При кратковременной сетевой ошибке к `sm-karaoke.ru/changerecords`
синхронизация делает одну повторную попытку; если и она не удалась — конкретная
сущность помечается ошибкой, остальные продолжают, пользователь видит понятное
сообщение (не HTTP 500 со стеком).

**Why P1**: именно это сейчас ломает «Синхронизацию в 1 клик».

**Independent Test**: Unit-тест с мок-loader'ом, бросающим `SSLHandshakeException`
на первой попытке и успешным на второй → результат успешен, retry зафиксирован.
Тест, где обе попытки падают → `error` заполнен, остальные targets продолжаются.

**Acceptance Scenarios**:

1. **Given** первая попытка `POST /changerecords` падает `SSLHandshakeException`,
   **When** вторая успешна, **Then** операция считается успешной (без ошибки в UI).
2. **Given** обе попытки упали, **When** `postSyncOneClick`, **Then** HTTP 200 со
   `perTarget[].error` заполненным для этой сущности; остальные сущности обработаны.
3. **Given** сбой, **When** смотрим лог, **Then** ошибка логируется без полного
   стектрейса в ERROR (одна WARN-строка с типом/сообщением), HTTP — не 500.

### User Story 2 — Ограниченное время ожидания (Priority: P2)

**Описание**: HTTP-вызовы к прод-сайту имеют конечные таймауты (connect 10s,
request 60s), чтобы зависание не блокировало поток надолго.

**Independent Test**: `HttpClient` строится с заданными таймаутами (проверка через
конфиг/константы), тест на `requestTimeout`.

### User Story 3 — Ошибка видна в UI (Priority: P2)

**Описание**: `SyncOneClickResultDto` получает поле `error: String?`; фронт в
`showResultAlert` показывает ошибку сущности вместо общего «см. лог сервера».

## Requirements *(mandatory)*

### Functional

- **FR-001**: Все сетевые вызовы `POST https://sm-karaoke.ru/changerecords` MUST
  выполняться через единый helper с `try/catch` (никакие `IOException`/`SSLException`
  не должны уходить в HTTP-слой).
- **FR-002**: Helper MUST задавать `connectTimeout=10s` и `requestTimeout=60s`
  (`HttpClient.newBuilder()...`).
- **FR-003**: Helper MUST делать **1 retry** с задержкой 2s при транзиентной ошибке
  (`SSLHandshakeException`, `SSLException`, `ConnectException`, `HttpTimeoutException`,
  `IOException` с "timed out"). При успехе второй попытки — результат успешен.
- **FR-004**: При исчерпании попыток helper MUST вернуть неуспех (не бросать),
  залогировать WARN `infra.sync.remote` `sync:remote:failure url=... attempt=2 error=...`.
- **FR-005**: `updateDatabases` MUST NOT падать при сетевом сбое: возвращает
  `SyncResult` (возможно с частичным успехом), ошибка не пропагируется наружу.
- **FR-006**: `postSyncOneClick` MUST иметь per-target `try/catch` (как
  `AutoOneClickSyncScheduler` FR-012): одна упавшая сущность не ломает остальные.
- **FR-007**: `SyncOneClickResultDto` MUST получить `error: String?` (null = успех);
  заполняется при сбое сущности.
- **FR-008**: `AutoOneClickSyncScheduler` MUST получить те же гарантии (через общий
  helper; per-target изоляция уже есть).
- **FR-009**: Успешные ответы MUST логироваться как раньше (`println(response.body())`
  сохранить или заменить структурным INFO `infra.sync.remote`).

### Non-Functional

- **NFR-001**: Общий helper един для всех 6 call sites (устранение copy-paste).
- **NFR-002**: Retry MUST NOT превышать 1 попытку (без лавины).
- **NFR-003**: Изменение MUST NOT ломать существующий контракт `SyncResult`/
  `SyncOneClickResultDto` (поле `error` — аддитивно).
- **NFR-004**: Unit-тесты на retry-логику и на изоляцию ошибок.
- **NFR-005**: Логирование сбоя — в категорию `infra.sync.remote` (новая),
  зарегистрировать в `log-categories.md`.

### Key Entities

- **`SyncRemoteClient`** (NEW, `services/` или top-level `Utils`) — helper
  `postChangeRecords(url, body): Boolean` с таймаутами + retry + логом.
- **`Utils.kt`** (MODIFY) — 6 call sites → helper; `updateDatabases` не пропагирует.
- **`ApiController.postSyncOneClick`** (MODIFY) — per-target `try/catch`.
- **`SyncOneClickResultDto`** (MODIFY) — `error: String?`.
- **`webvue3 SyncTable.vue`** (MODIFY) — показать `error`.

## Success Criteria *(mandatory)*

- **SC-001**: Unit-тест: 1-я попытка падает, 2-я успешна → success (retry работает).
- **SC-002**: Unit-тест: 2 попытки падают → error-результат, без исключения.
- **SC-003**: `postSyncOneClick` при сбое одной сущности обрабатывает остальные.
- **SC-004**: `:karaoke-app:test` PASS; `ktlintCheck` 0; `bootJar` OK;
  `check-knowledge-structure.sh` 9/9; `gh pr checks` all PASS.

## Assumptions

1. **Общий helper** — правильное место `Utils.kt` (top-level) рядом с вызовами;
   вынесем в отдельный файл `SyncRemoteClient.kt` для тестируемости.
2. **Retry 2s** — достаточно для транзиента; без exponential backoff (1 попытка).
3. **`error` в DTO** — аддитивное поле, фронт читает опционально.
4. **Таймауты 10s/60s** — по решению владельца.

## Out of Scope

- **Изменение FR-016 спеки #235** (fail-fast scheduler) — scheduler по-прежнему
  изолирует per-target; внешний retry helper применяется и к нему, но «без retry
  тика» остаётся (retry внутри HTTP-вызова, не тика).
- **Чинить нестабильность канала до прод-сайта** — инфраструктура.
- **Менять `SyncResult` структуру** — только аддитивно `error` в DTO.
- **Другие внешние API** (VK, TG, Yandex) — не трогаем.

## Migration Path

### Что нужно изменить

- `SyncRemoteClient.kt` (NEW) — helper + retry + timeouts + лог.
- `Utils.kt` (MODIFY) — 6 call sites → helper; не пропагировать.
- `ApiController.kt` (MODIFY) — per-target `try/catch` + `error` в DTO.
- `SyncTable.vue` (MODIFY) — вывод `error`.
- `knowledge/.../two-db-sync.md`, `run-entity-sync.md`, `log-categories.md` (MODIFY).
- `docs/features/sync-resilience.md` (NEW) — per-feature doc.

### Что НЕ нужно менять

- `SyncResult`, `SyncRegistry`, FSM scheduler.

## Validation

| Проверка | Ожидаемо |
|---|---|
| Unit retry-тест | success after 1 fail |
| Unit all-fail-тест | error-результат, no throw |
| `:karaoke-app:test` | PASS |
| `ktlintCheck` / `bootJar` | OK |

## Rollback

`git revert <merge-commit>` — возврат к прежнему поведению (HTTP 500 на транзиенте).

## Clarifications

### Session 2026-09-22

- **Q**: Retry? → **A**: 1 повтор, задержка 2s, только транзиентные.
- **Q**: Таймауты? → **A**: connect 10s, request 60s.
- **Q**: Изоляция? → **A**: per-target в postSyncOneClick (как scheduler) + updateDatabases не пропагирует.
- **Q**: Форма ошибки? → **A**: `error: String?` в `SyncOneClickResultDto`.
