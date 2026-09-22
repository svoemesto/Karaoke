# Feature Specification: StorageCircuitBreaker — реальный timeout для блокирующего loader (Pass 426, #150)

**Feature Branch**: `426-storage-circuit-breaker-blocking-timeout`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Production-наблюдение 2026-09-22 (nsa-i9) — `circuit=OPEN storage=local`
бесконечным потоком; circuit breaker не может выйти из HALF_OPEN, потому что
per-call `timeout(5s)` физически не прерывает блокирующий вызов MinIO.
Follow-up на Pass 351 (спека #352) и Pass 372 (спека #405).

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#150` («StorageCircuitBreaker: timeout не работает для блокирующего loader»).
- **Title**: «StorageCircuitBreaker: timeout не работает для блокирующего loader → вечное залипание в HALF_OPEN».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 150` — выполнен 2026-09-22
     (assignee=`ai agent`; статус переведён в `In progress` вручную через PATCH,
     т.к. `tracker.sh claim-issue` падает на смене assignee для Task в проекте Karaoke).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 150 --file specs/426-storage-circuit-breaker-blocking-timeout/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 150`.
  4. **Close** (owner, после merge + согласия на рестарт `karaoke-app`):
     `bash tools/tracker.sh close-issue 150`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rn "circuit\|CircuitBreaker\|HALF_OPEN" knowledge/` → 24 hits, 4 файла:
     `storage/components/storage-api-client.md`, `storage/domain.md`,
     `monitoring/components/log-categories.md`, `health/components/health-report.md`.
  2. `grep -rln "SocketTimeout\|decorate\|FastFail" knowledge/ specs/` → knowledge-файл
     один (`storage-api-client.md`) + спеки `352-storage-graceful-degradation/*`
     (`spec.md`, `tasks.md`, `research.md`, `data-model.md`,
     `contracts/circuit-breaker-state.md`).
  3. `grep -rln "timeout\|Mono.fromCallable\|subscribeOn\|boundedElastic" knowledge/domains/storage knowledge/domains/caching`
     → `karaoke-storage-service.md`, `storage-api-client.md`, `storage/domain.md`,
     `caching/components/caching-patterns.md`.
  4. `grep -rln "blocking\|block()" knowledge/domains/storage knowledge/domains/caching`
     → `storage/domain.md` (раздел «Решение 2: blocking vs reactive»).
  5. `grep -rn "watchdog\|probe stuck\|HALF_OPEN" specs/405-storage-circuit-breaker-watchdog/`
     → подтверждает, что спека #405 **осознанно** оставила root cause
     (Assumption #4: «probe не запускается» / Out of Scope: «HealthReport использует
     `acquire()` напрямую»).

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — Pass 351 graceful degradation, FSM circuit breaker, `decorate` pattern;
  Edge cases: hardcoded OkHttp timeout (connect 15s) vs `storage.file-exists-timeout-seconds` (5s).
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — «Решение 2: `StorageApiClient` reactive (Mono), `KaraokeStorageService` blocking», hot paths #75.
- [`knowledge/domains/health/components/health-report.md`](../../knowledge/domains/health/components/health-report.md)
  — `actionsLocalStorage` использует `cb.acquire()` напрямую (fast-fail без MinIO-call).
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — категории `infra.cache.storage`, `infra.health.circuit`; события circuit.
- [`specs/405-storage-circuit-breaker-watchdog/spec.md`](../../specs/405-storage-circuit-breaker-watchdog/spec.md)
  — watchdog + reset; Assumption #4 и Out of Scope явно откладывают этот root cause.
- [`specs/352-storage-graceful-degradation/spec.md`](../../specs/352-storage-graceful-degradation/spec.md)
  — исходные FR circuit breaker (per-call timeout 5s).
- [`knowledge/adr/local-0005-structured-logging-karaoke-app.md`](../../knowledge/adr/local-0005-structured-logging-karaoke-app.md)
  — строковые SLF4J-категории `infra.*`, формат событий.

### Прецедент

2026-09-22 (nsa-i9), логи `karaoke-app`:

```
09:46:02 cache:network:failure error="SocketTimeoutException" failureCount=5 threshold=5
09:46:02 cache:circuit:state from=CLOSED to=OPEN failureCount=5
09:46:32 cache:circuit:state from=OPEN to=HALF_OPEN
09:46:48 cache:circuit:watchdog state=HALF_OPEN->OPEN (probe stuck) durationMs=15887
... повторяется каждые ~45s бесконечно ...
```

Проверено вживую: TCP до remote MinIO (`89.125.103.63:9000`) нестабилен
(20 проб → 5–6 таймаутов), connect из контейнера `karaoke-app` скачет
0.003s → 1s → 2s → **5.16s**. При этом `timeoutSeconds=5`, watchdog-buffer=10s,
а OkHttp `connectTimeout=15s` — проба «висит» ровно ~15s и watchdog её убивает.

**Root cause**: `StorageCircuitBreaker.decorate` оборачивает
`loader()` (= `Mono.fromCallable { blockirующий MinIO-вызов }`) в `.timeout(5s)`
**на том же потоке**, где потом вызывается `.block()`. Оператор `timeout` не может
прервать блокирующий вызов, занимающий поток подписчика; фактическое время
ожидания определяется OkHttp (`connectTimeout=15s`), что больше
`timeoutSeconds + watchdogBufferSeconds = 15s`. Circuit **никогда** не закрывается,
даже когда сеть доступна — вечный цикл OPEN→HALF_OPEN→OPEN.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Circuit восстанавливается после сетевого сбоя (Priority: P1)

**Описание**: Когда remote MinIO временно недоступен, а затем снова доступен,
circuit breaker в течение одного cooldown-цикла (30s) закрывается, и
`fileExists`/`getFileInfo` снова возвращают реальный результат, а не `FastFail`.

**Why P1**: это root cause текущей production-проблемы. Без фикса `circuit=OPEN`
висит вечно, `UPLOAD_TO_LOCAL_STORE` каскадом падает с `ERROR (данные не найдены)`,
админ не может работать.

**Independent Test**: unit-тест с «медленным» (блокирующим) loader'ом:
1. circuit в HALF_OPEN (после threshold failures + cooldown).
2. loader блокируется дольше `timeoutSeconds`.
3. `decorate(...).block()` возвращает `emptyValue` за ≈`timeoutSeconds`, а не за
   фактическое время блокировки.
4. `recordFailure` вызван → circuit корректно уходит в OPEN и на следующем
   cooldown снова может пробовать probe (а при успешном probe — CLOSED).

**Acceptance Scenarios**:

1. **Given** circuit CLOSED, **When** loader блокируется > `timeoutSeconds`,
   **Then** `decorate(...).block()` возвращает `emptyValue` не позже
   `timeoutSeconds + jitter`, state → OPEN по threshold, failure залогирован.
2. **Given** circuit HALF_OPEN, **When** probe-loader блокируется >
   `timeoutSeconds`, **Then** probe завершается по timeout (не по факту
   блокировки), watchdog не срабатывает как «probe stuck».
3. **Given** circuit HALF_OPEN, **When** probe-loader завершается успешно быстро,
   **Then** state → CLOSED (восстановление реально работает).
4. **Given** 100 параллельных `decorate(...).block()`, **When** loader блокируется,
   **Then** вызывающие потоки не исчерпываются (blocking-работа вынесена на
   `boundedElastic`), нет дедлока.

### User Story 2 — Диагностика сбоя remote MinIO отделена от локального хранилища (Priority: P2)

**Описание**: Логи текущего инцидента вводят в заблуждение: `circuit=OPEN storage=local`
печатается в `HealthReport.actionsLocalStorage`, хотя circuit защищает **remote**
MinIO. Плюс `UPLOAD_TO_LOCAL_STORE` падает «(данные не найдены)», хотя локальное
хранилище живо — просто `actionsLocalStorage` тоже смотрит в общий circuit.

**Why P2**: не влияет на исправление root cause, но замедляет диагностику
следующего инцидента (потерянные итерации на гипотезы).

**Independent Test**: `grep "circuit=OPEN" logs/karaoke-app.log` содержит
однозначный `storage=remote`, совпадающий с endpoint'ом, к которому
фактически идёт вызов circuit-breaker'а (`StorageApiClientImpl` → remote MinIO).

**Acceptance Scenarios**:

1. **Given** circuit открыт из-за remote MinIO, **When** логируется `circuit=OPEN`,
   **Then** в сообщении указан `storage=remote` (не `local`).
2. **Given** тот же circuit консультируется в `actionsLocalStorage`,
   **When** лог печатается, **Then** текст сообщения (`problemText`/`solutionText`)
   говорит про remote-хранилище, а не про «локальное».

> **NB (граница скоупа)**: поведенческое разъединение проверки circuit в
> `actionsLocalStorage` (чтобы remote-сбой не валил локальную заливку) —
> **отдельная задача**, см. Out of Scope. В этом PR исправляется только
> диагностика.

## Requirements *(mandatory)*

### Functional

- **FR-001**: `StorageCircuitBreaker.decorate` и `decorateOrEmpty` MUST выполнять
  `loader()` на отдельном scheduler'е (`reactor.core.scheduler.Schedulers.boundedElastic()`)
  через `subscribeOn`, чтобы оператор `timeout(timeoutSeconds)` имел реальную силу
  для блокирующих loader'ов.
- **FR-002**: Фактическое время ожидания `decorate(...).block()` при блокирующем
  loader'е MUST быть ≈ `storage.file-exists-timeout-seconds` (default 5s) с
  разумным jitter, а НЕ временем блокировки (OkHttp `connectTimeout`).
- **FR-003**: По истечении `timeoutSeconds` MUST вызываться `recordFailure`
  (как и сейчас), а результат — `emptyValue`/`Mono.empty()`.
- **FR-004**: Дополнительно MUST быть ограничен сам OkHttp-таймаут
  (`connectTimeout`/`readTimeout`) в `StorageApiClientImpl` так, чтобы он был
  ≤ `timeoutSeconds` (default: `connectTimeout = timeoutSeconds`, `readTimeout`
  для getFileInfo — `timeoutSeconds`), устраняя «двойной» бюджет ожидания.
- **FR-005**: Диагностический лог FastFail в `HealthReport` MUST явно указывать
  `storage=remote` (circuit защищает remote MinIO через `StorageApiClientImpl`),
  а текст проблемы/решения MUST говорить про удалённое хранилище. Поведенческое
  разъединение локального и удалённого путей — Out of Scope.
- **FR-006**: `StorageCircuitBreaker` MUST использовать единый `Scheduler`,
  создаваемый один раз (не per-call), и корректно освобождать ресурсы при shutdown
  (`@PreDestroy`) — без утечки потоков.
- **FR-007**: Поведение при CLOSED и `Decision.Allow` MUST сохраниться: при
  реально успешном быстром вызове overhead не увеличивается заметно
  (NFR-002 спеки #352: ≤200ms для LOCAL_STORAGE hot path).

### Non-Functional

- **NFR-001**: `decorate` overhead в CLOSED state — не более +1 переключения
  потока на вызов; hot path `fileExists` не должен деградировать по latency
  более чем на единицы миллисекунд.
- **NFR-002**: Ни один блокирующий вызов MinIO не MUST оставаться на
  HTTP/вызывающем потоке бесконечно: верхняя граница — `timeoutSeconds + jitter`.
- **NFR-003**: Все изменения MUST быть покрыты unit-тестами (минимум: блокирующий
  loader укладывается в timeout; HALF_OPEN+успешный probe → CLOSED).
- **NFR-004**: Изменение MUST быть backward compatible по публичному API
  (`decorate`/`decorateOrEmpty` сигнатуры не меняются).

### Key Entities

- **`StorageCircuitBreaker`** (MODIFY) — `decorate`/`decorateOrEmpty`: добавить
  `subscribeOn(Schedulers.boundedElastic())`; добавить разделяемый `Scheduler`.
- **`StorageApiClientImpl`** (MODIFY) — OkHttp `connectTimeout`/`readTimeout`
  выровнять с `storage.file-exists-timeout-seconds`.
- **`HealthReport`** (MODIFY) — `actionsLocalStorage`/`actionsRemoteStorage`:
  корректный `storage=local|remote` в диагностическом логе FastFail.
- **`Application.yml`** (MODIFY, при необходимости) — комментарии/дефолты таймаутов.

## Success Criteria *(mandatory)*

- **SC-001**: Unit-тест с блокирующим loader'ом: `decorate` возвращает `emptyValue`
  за ≈`timeoutSeconds` (не за время блокировки). Verified в `StorageCircuitBreakerTest`.
- **SC-002**: Circuit в HALF_OPEN + успешный быстрый probe → CLOSED (unit-тест).
- **SC-003**: Все существующие тесты `StorageCircuitBreakerTest` (11) + новые — PASS.
- **SC-004**: `grep "circuit=OPEN"` в логах после инцидента содержит корректный
  `storage=local|remote`; вечного цикла HALF_OPEN→OPEN нет (наблюдение после рестарта).
- **SC-005**: `:karaoke-app:ktlintCheck` — 0 violations, `:karaoke-app:bootJar` — OK,
  `bash tools/check-ssot-impact.py` — 0 violations, `gh pr checks` — all PASS.

## Assumptions

1. **Remote MinIO-канал нестабилен** (5–6 таймаутов из 20 TCP-проб) — это внешняя
   причина сбоя; спека исправляет **устойчивость** (circuit должен восстанавливаться),
   а не сам канал.
2. **`Schedulers.boundedElastic()`** доступен (reactor-core уже в зависимостях
   `karaoke-app`, используется `Mono`).
3. **Single replica** `karaoke-app` — распределённый circuit не требуется
   (как в спеке #405).
4. **`HealthReport` использует `cb.acquire()` напрямую** (без loader) — это by design
   для fast-fail; спека #426 не меняет этот контракт, но уточняет диагностику (FR-005).

## Out of Scope

- **Исправление самого сетевого канала** до remote MinIO (провайдер/маршрут) —
  инфраструктурная задача, не код.
- **Distributed circuit breaker** (multi-replica) — не актуально.
- **`KaraokeStorageService.fileExists` (local MinIO) circuit integration** —
  отдельная задача (как в Out of Scope спеки #405).
- **Изменение семантики single-probe HALF_OPEN** — сохраняется (Q3 спеки #352).
- **Переписывание `StorageApiClientImpl` на fully-reactive путь** — вне рамок.

## Migration Path

### Что нужно изменить

- `StorageCircuitBreaker.kt` (MODIFY) — `subscribeOn(boundedElastic)`, общий Scheduler.
- `StorageApiClientImpl` в `StorageApiClient.kt` (MODIFY) — OkHttp timeout'ы.
- `HealthReport.kt` (MODIFY) — корректный `storage=...` в логе.
- `StorageCircuitBreakerTest.kt` (MODIFY) — +2 теста (блокирующий loader; HALF_OPEN success).
- `knowledge/domains/storage/components/storage-api-client.md` (MODIFY) — Pass 426.
- `knowledge/domains/storage/domain.md` (MODIFY) — hot paths (#75) + root cause.
- `knowledge/domains/monitoring/components/log-categories.md` (MODIFY) — уточнение `storage=`.
- `docs/features/storage-metadata-cache.md` (MODIFY) — Pass 426 секция.

### Что НЕ нужно менять

- Публичные сигнатуры `decorate`/`decorateOrEmpty` — backward compatible.
- `CircuitBreakerController` / `reset()` / watchdog — остаются как есть.
- `StorageMetadataCache` — не затронута.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `:karaoke-app:test --tests "*StorageCircuitBreakerTest"` | all PASS (existing + new) |
| `:karaoke-app:test --tests "*StorageApiClient*"` (если есть) | PASS |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:bootJar` | OK |
| `bash tools/check-ssot-impact.py` | 0 violations |
| `bash tools/check-knowledge-structure.sh` | 9/9 OK |
| `curl http://localhost:8898/api/health/circuit-breaker` | state корректный, цикл не залипает |

## Rollback

`git revert <merge-commit>` — `decorate` снова выполняется на вызывающем потоке;
проблема вечного HALF_OPEN возвращается, но рестарт `karaoke-app` временно помогает.
Другие изменения (логи, OkHttp timeout) безопасны к откату по отдельности.

## Clarifications

### Session 2026-09-22

- **Q**: Почему не увеличить `timeoutSeconds` до 15s, чтобы он совпал с OkHttp?
  - **A**: Это маскирует баг: timeout как раз должен **прерывать** медленный вызов
    раньше сетевого уровня. Увеличение до 15s сделает hot path в 3× медленнее и
    всё равно не даст настоящего прерывания, пока loader блокирует поток.
- **Q**: Достаточно ли только `subscribeOn(boundedElastic)`, или менять `timeout`?
  - **A**: `subscribeOn` + существующий `.timeout(timeoutSeconds)` — достаточная
    комбинация: `timeout` теперь стартует таймер на другом scheduler'е и может
    отменить `Mono`, вернув управление. Отмену самого блокирующего вызова
    гарантирует нижний уровень (OkHttp) — поэтому FR-004 выравнивает его таймаут.
- **Q**: Не сломает ли `subscribeOn` fast path (успешные вызовы)?
  - **A**: Добавляет одно переключение потока; при кэше (`StorageMetadataCache`)
    loader вообще не вызывается. Замерим в рамках NFR-001.
