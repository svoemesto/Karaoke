# Feature Specification: StorageCircuitBreaker — убрать onErrorDropped/InterruptedException при timeout-отмене (Pass 428, #152)

**Feature Branch**: `428-storage-timeout-interrupt-noise`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Логи `karaoke-app` после Pass 426 (PR #519):
`java.lang.RuntimeException: java.lang.InterruptedException ... statObjectOrNull(StorageApiClient.kt:502)` +
`reactor.core.publisher.Operators: Operator called default onErrorDropped` (ERROR, 15×).
Follow-up на Pass 426 (спека #426, OpenProject #150).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#152` («StorageCircuitBreaker: onErrorDropped/InterruptedException при timeout-отмене»).
- **Title**: «StorageCircuitBreaker: onErrorDropped/InterruptedException при timeout-отмене (follow-up #150)».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 152` — выполнено 2026-09-22
     (assignee=`ai agent`, статус `In progress`; PATCH вручную, т.к.
     `tracker.sh claim-issue` падает на смене assignee для Task в проекте Karaoke).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 152 --file specs/428-storage-timeout-interrupt-noise/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 152`.
  4. **Close** (owner, после merge + согласия на рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 152`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rln "onErrorDropped\|InterruptedException\|timeout\|subscribeOn" knowledge/`
     → `storage/components/storage-api-client.md`, `storage/domain.md`,
     `integration/components/external-api-clients.md`, `caching/components/caching-patterns.md`,
     `karaoke-web/components/config.md`, `sse/domain.md`, `publishing/components/publishing-services.md`,
     `storage/components/karaoke-storage-service.md`.
  2. `grep -rn "Pass 426\|blocking\|subscribeOn" knowledge/domains/storage/components/storage-api-client.md`
     → Pass 426 секция (`.subscribeOn(boundedElastic)`, root cause) — источник регрессии.
  3. `grep -rln "exception\|обработка ошибок" knowledge/adr/` → `local-0002-save-exception-handling.md`,
     `local-0005-structured-logging-karaoke-app.md`, `local-0006-...`.
  4. `grep -rn "statObject" karaoke-app/src/main/kotlin/.../StorageApiClient.kt`
     → `statObjectOrNull` (строка 496) ловит только `MinioException`.
  5. `grep -oE 'StorageApiClientImpl\.[a-zA-Z]+' logs` → все 15 падений — `getFileInfo`/`statObjectOrNull`
     (единственный путь, где `getFileInfo` обёрнут `decorateOrEmpty`).
  6. `grep -rn "InterruptedException\|isInterrupted" karaoke-app/src/main karaoke-web/src/main`
     → есть конвенция `Thread.currentThread().interrupt()` при catch (Vk*, Telegram, GeoIpService).

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — Pass 426 fix (subscribeOn), где появился регресс; Edge cases (timeouts).
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — hot paths, circuit breaker.
- [`knowledge/adr/local-0005-structured-logging-karaoke-app.md`](../../knowledge/adr/local-0005-structured-logging-karaoke-app.md)
  — structured logging / log levels.
- [`knowledge/adr/local-0002-save-exception-handling.md`](../../knowledge/adr/local-0002-save-exception-handling.md)
  — паттерн обработки исключений (не глотать, но и не спамить).

### Прецедент

2026-09-22 (nsa-i9), после рестарта с PR #519 (Pass 426):

```
10:53:31 ERROR [oundedElastic-10] reactor.core.publisher.Operators : Operator called default onErrorDropped
java.lang.RuntimeException: java.lang.InterruptedException
    at io.minio.MinioClient.statObject(MinioClient.java:169)
    at ...StorageApiClientImpl.statObjectOrNull(StorageApiClient.kt:502)
    at ...StorageApiClientImpl.getFileInfo$lambda$0(StorageApiClient.kt:480)
  Caused by: java.lang.InterruptedException: null
```

Итого 15 таких ERROR + 15 `cache:network:failure error="TimeoutException"` + 14× `circuit=OPEN ...`.

## Диагностика (root cause)

Pass 426 добавил в `StorageCircuitBreaker.decorate`/`decorateOrEmpty`:

```kotlin
loader().subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(timeoutSeconds))...
```

1. `timeout(5s)` истекает → Mono завершается `TimeoutException` **терминальным** сигналом
   → `recordFailure` → circuit корректно открывается (это работает как задумано).
2. Одновременно reactor **отменяет** подписку `subscribeOn` → `Future.cancel(true)`
   → **прерывает** worker-поток `boundedElastic`.
3. Блокирующий `MinioClient.statObject` (внутри — `CompletableFuture.get()`)
   получает `InterruptedException` и заворачивает его в `RuntimeException` (`MinioClient.java:169`).
4. `statObjectOrNull` ловит **только** `MinioException` → `RuntimeException` улетает наверх.
5. Mono уже терминализирован таймаутом → исключение некуда доставить
   → reactor вызывает `Operators.onErrorDropped` → печатает **ERROR со стеком**.

**Итог**: circuit breaker работает корректно; баг — только в **шуме** (ERROR + stacktrace)
и в том, что `InterruptedException` не восстанавливает флаг прерывания.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Нет onErrorDropped-ERROR при timeout (Priority: P1)

**Описание**: Когда блокирующий MinIO-вызов прерывается из-за timeout, в логах
не появляется `Operator called default onErrorDropped` и стека `InterruptedException`.
`TimeoutException` по-прежнему логируется (WARN `cache:network:failure`) и circuit
по-прежнему открывается.

**Why P1**: это регресс, порождённый Pass 426; ERROR со стеком засоряет логи и
маскирует реальные проблемы, хотя сам circuit работает.

**Independent Test**: Unit-тест: loader, который при interrupt бросает
`RuntimeException(InterruptedException)` (или `Mono` прерывается по timeout);
`decorate(...)` возвращает `emptyValue`; через `StepVerifier`/`onErrorDropped`-hook
не зафиксировано dropped-error.

**Acceptance Scenarios**:

1. **Given** блокирующий loader длится > `timeoutSeconds`, **When** срабатывает timeout,
   **Then** `decorate(...).block()` возвращает `emptyValue`, `failureCount` инкрементируется,
   **без** `onErrorDropped`.
2. **Given** worker прерван отменой, **When** `InterruptedException` выброшено из loader,
   **Then** оно трактуется как нормальное завершение probe (не логируется ERROR-стеком),
   флаг `Thread.currentThread().interrupt()` восстановлен.
3. **Given** реальная `MinioException` (не interrupt), **When** loader падает,
   **Then** поведение прежнее (тихо → null/empty, без ERROR-спама).

### User Story 2 — Диагностика остаётся достаточной (Priority: P2)

**Описание**: Информация о таймауте не теряется: остаётся WARN
`cache:network:failure error="TimeoutException"` и переход `cache:circuit:state`.

**Acceptance Scenarios**:

1. **Given** timeout, **When** смотрим логи, **Then** есть ровно одна WARN-строка
   про network failure (не ERROR + стек).

## Requirements *(mandatory)*

### Functional

- **FR-001**: `statObjectOrNull` (и аналогичные блокирующие wrapper'ы, вызываемые из
  `decorate`/`decorateOrEmpty`) MUST NOT пропускать `RuntimeException`-обёртку
  `InterruptedException` наружу; interrupt MUST трактоваться как «вызов отменён» → `null`.
- **FR-002**: При перехвате `InterruptedException` (в т.ч. внутри `RuntimeException`
  с cause) MUST восстанавливаться флаг прерывания `Thread.currentThread().interrupt()`.
- **FR-003**: `StorageCircuitBreaker.decorate`/`decorateOrEmpty` MUST предотвращать
  `onErrorDropped` при штатном timeout: ошибка, пришедшая после терминального сигнала,
  не должна уходить в reactor как dropped (например, `timeout` + `onErrorResume`/
  обработка interrupt до терминации).
- **FR-004**: Штатный timeout MUST по-прежнему: (а) инкрементировать `recordFailure`,
  (б) возвращать `emptyValue`/`Mono.empty()`, (в) логировать WARN `cache:network:failure`.
- **FR-005**: Ошибки, не связанные с interrupt/timeout, MUST обрабатываться как раньше
  (без изменения поведения).

### Non-Functional

- **NFR-001**: При timeout в логах MUST NOT появляться `Operator called default onErrorDropped`
  и стектрейсы от `MinioClient.statObject`.
- **NFR-002**: WARN-диагностика (`cache:network:failure`, `cache:circuit:state`) сохраняется.
- **NFR-003**: Изменение MUST быть покрыто unit-тестами (timeout без onErrorDropped;
  interrupt → null).
- **NFR-004**: Публичные сигнатуры методов не меняются.

### Key Entities

- **`StorageApiClientImpl.statObjectOrNull`** (MODIFY) — ловить interrupt-обёртку,
  восстанавливать флаг.
- **`StorageCircuitBreaker`** (MODIFY) — `decorate`/`decorateOrEmpty`: не допускать
  dropped-error при timeout-отмене.
- **`StorageApiClientImpl`** прочие блокирующие обёртки (`getFileStat`, `checkIfExists`) (MODIFY) —
  согласованная обработка interrupt.

## Success Criteria *(mandatory)*

- **SC-001**: Unit-тест: timeout блокирующего loader'а → `decorate` возвращает fallback,
  dropped-error hook НЕ срабатывает, `failureCount` = 1.
- **SC-002**: Unit-тест: `statObjectOrNull` при `InterruptedException` (в `RuntimeException`) → `null`
  + флаг `Thread.interrupted()` восстановлен.
- **SC-003**: Все существующие `StorageCircuitBreakerTest` + `StorageApiClient`-тесты — PASS.
- **SC-004**: После рестарта: `grep onErrorDropped` — 0; `cache:network:failure TimeoutException`
  остаётся; circuit открывается/закрывается как прежде.
- **SC-005**: `:karaoke-app:ktlintCheck` — 0, `:karaoke-app:bootJar` — OK,
  `tools/check-knowledge-structure.sh` — 9/9, `gh pr checks` — all PASS.

## Assumptions

1. **`timeout` уже терминализирует Mono** — `InterruptedException` приходит «вдогонку»;
   именно поэтому реактор считает ошибку dropped.
2. **`boundedElastic` worker переиспользуется** — восстановление флага прерывания важно,
   чтобы не «отравить» поток для следующих задач.
3. **Circuit works as intended** — менять FSM/пороги не нужно, только убрать шум.

## Out of Scope

- **Нестабильность сети до remote MinIO** — внешняя причина, отдельная инфраструктурная задача.
- **Переход на полностью reactive MinIO-клиент** — вне рамок.
- **Изменение порогов/cooldown circuit breaker** — нет.
- **Изменение `HealthReport` circuit-проверки** — нет.

## Migration Path

### Что нужно изменить

- `StorageApiClient.kt` (MODIFY) — `statObjectOrNull`, `getFileStat`, `checkIfExists`
  (interrupt-aware обработка).
- `StorageCircuitBreaker.kt` (MODIFY) — `decorate`/`decorateOrEmpty` (не допускать onErrorDropped).
- `StorageCircuitBreakerTest.kt` / новый test (ADD) — тесты timeout без dropped + interrupt→null.
- `knowledge/domains/storage/components/storage-api-client.md` (MODIFY) — секция Pass 428.
- `docs/features/storage-metadata-cache.md` (MODIFY) — V2.4.

### Что НЕ нужно менять

- FSM/пороги circuit breaker, `CircuitBreakerController`, `reset()`.
- Публичные сигнатуры `StorageApiClient`.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` | all PASS |
| `:karaoke-app:test` (новый interrupt-тест) | PASS |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:bootJar` | OK |
| `tools/check-knowledge-structure.sh` | 9/9 OK |
| После рестарта: `grep onErrorDropped` | 0 строк |

## Rollback

`git revert <merge-commit>` — возвращается ERROR-шум `onErrorDropped`, но circuit
и данные не страдают (регресс только косметический).

## Clarifications

### Session 2026-09-22

- **Q**: Почему `MinioException` не ловит этот случай?
  - **A**: MinIO заворачивает `InterruptedException` из `CompletableFuture.get()`
    в `RuntimeException`, а не в подкласс `MinioException`. Нужна явная обработка interrupt.
- **Q**: Не проще ли убрать `subscribeOn` (откат Pass 426)?
  - **A**: Нет — `subscribeOn` нужен, чтобы `timeout` реально срабатывал (Pass 426).
    Откат вернёт вечное залипание circuit. Правильно — обработать interrupt.
- **Q**: Логировать ли сам факт timeout громче?
  - **A**: Нет; достаточно существующей WARN `cache:network:failure error="TimeoutException"`.
