# Feature Specification: Таймаут доступа к удалённому хранилищу 5s → 20s (Pass 430, #154)

**Feature Branch**: `430-remote-storage-timeout-20s`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Требование владельца: «надо установить таймаут не 5 секунд, а 20»
для доступа к удалённому хранилищу.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#154` («Увеличить таймаут доступа к удалённому хранилищу 5s → 20s»).
- **Title**: «Увеличить таймаут доступа к удалённому хранилищу 5s → 20s».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 154` — выполнено 2026-09-22
     (assignee=`ai agent`, статус `In progress`; PATCH вручную).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 154 --file specs/430-remote-storage-timeout-20s/report.md`.
  3. **Mark review**: `bash tools/tracker.sh mark-review 154`.
  4. **Close** (owner, после merge + рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 154`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3):
  1. `grep -rln "file-exists-timeout\|timeoutSeconds\|watchdog-buffer" knowledge/`
     → `storage/components/storage-api-client.md` (единственный файл).
  2. `grep -rn "timeout|5s|watchdog" knowledge/domains/storage/components/storage-api-client.md`
     → строка 167: «Per-call timeout: `storage.file-exists-timeout-seconds` (default 5s…)».
  3. `grep -rn "default" knowledge/adr/local-0001-karaoke-properties-defaults.md`
     → конвенция дефолтов (non-secret).
  4. `grep -rn "file-exists-timeout-seconds" --include='*.kt' --include='*.yml' karaoke-app/src`
     → 5 мест: `application.yml:71`, `StorageApiClient.kt:140`,
     `StorageCircuitBreaker.kt:60`, `StorageCircuitBreakerConfig.kt:29,48`, тест.
  5. `grep -rn "timeoutSeconds \* 1000\|deadline" StorageCircuitBreaker.kt`
     → watchdog-дедлайн = `timeoutSeconds + watchdogBufferSeconds`.

### Knowledge files consulted

- [`knowledge/domains/storage/components/storage-api-client.md`](../../knowledge/domains/storage/components/storage-api-client.md)
  — Per-call timeout (default 5s), Pass 426/428/429; нужно обновить default → 20s.
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md) —
  remote hot paths.
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)
  — события circuit (не меняются, но timeout влияет на частоту).
- [`knowledge/adr/local-0001-karaoke-properties-defaults.md`](../../knowledge/adr/local-0001-karaoke-properties-defaults.md)
  — конвенция конфигурации.
- [`specs/426-storage-circuit-breaker-blocking-timeout/spec.md`](../../specs/426-storage-circuit-breaker-blocking-timeout/spec.md)
  — почему OkHttp `connect/read` выровнены с этим свойством.

### Прецедент

Production-наблюдение 2026-09-22 (nsa-i9): remote MinIO нестабилен (TCP-connect
то проходит, то таймаутится; время коннекта 0.003s…5.16s). При `connectTimeout=5s`
часть вызовов обрывается на границе, circuit открывается (5 failures → OPEN),
хотя реальный ответ приходит чуть позже. Увеличение таймаута до 20s снижает
ложные срабатывания и даёт медленному, но живому бэкенду шанс ответить.

## Requirements *(mandatory)*

### Functional

- **FR-001**: `storage.file-exists-timeout-seconds` MUST стать **20** (было 5).
- **FR-002**: Изменение MUST применяться к **remote** пути: circuit `.timeout(...)`
  (`StorageCircuitBreaker`) и OkHttp `connectTimeout`/`readTimeout`
  (`StorageApiClientImpl`, Pass 426).
- **FR-003**: **Local** хранилище MUST NOT изменяться (у него свои хардкод-таймауты
  connect 10s / read 30s).
- **FR-004**: Watchdog-дедлайн (`timeoutSeconds + watchdogBufferSeconds`) станет
  30s (20+10) — это допустимо, т.к. собственный `.timeout(20s)` срабатывает раньше.
- **FR-005**: Дефолты в `application.yml` и `@Value` fallback (5 → 20) MUST быть
  синхронны; env-override `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` сохраняется.
- **FR-006**: `writeTimeout` (300s для upload) MUST NOT изменяться.

### Non-Functional

- **NFR-001**: Документация (`storage-api-client.md`) MUST отражать default 20s.
- **NFR-002**: Изменение MUST быть совместимо с Pass 426/428/429 (timeout теперь
  реально прерывает блокирующий loader; 20s > OkHttp 20s).
- **NFR-003**: Существующие тесты MUST остаться PASS (тесты используют явные
  значения конструктора, а не дефолт).

### Key Entities

- **`application.yml`** (MODIFY) — `file-exists-timeout-seconds: 20`, обновить комментарий.
- **`StorageCircuitBreaker.kt` / `StorageCircuitBreakerConfig.kt` /
  `StorageApiClient.kt`** (MODIFY) — `@Value` fallback `:5` → `:20`.

## Success Criteria *(mandatory)*

- **SC-001**: `grep file-exists-timeout-seconds` показывает 20 во всех дефолтах.
- **SC-002**: Эффективный конфиг в контейнере (circuit timeout) = 20s;
  watchdog-дедлайн = 30s.
- **SC-003**: `:karaoke-app:test --tests "*Storage*"` — PASS; `ktlintCheck` 0;
  `bootJar` OK.
- **SC-004**: `check-knowledge-structure.sh` 9/9; `gh pr checks` all PASS.

## Assumptions

1. **Общий config** (Pass 429) — 20s применяется и к local-брейкеру, но local
   read-путь не оборачивается в Mono-таймаут, а его OkHttp-таймауты (10/30s)
   не зависят от этого свойства (решение владельца: только remote).
2. **Env-override** остаётся механизмом смены без пересборки.

## Out of Scope

- **Изменить local OkHttp-таймауты** — нет (решение владельца).
- **Изменить threshold/cooldown/watchdog-buffer** — нет.
- **Чинить нестабильность канала** — инфраструктурная задача.

## Migration Path

### Что нужно изменить

- `application.yml`, `StorageCircuitBreaker.kt`, `StorageCircuitBreakerConfig.kt`,
  `StorageApiClient.kt` — дефолт 5 → 20.
- `knowledge/domains/storage/components/storage-api-client.md` — default 20s.
- `docs/features/storage-metadata-cache.md` — заметка V2.6.

### Что НЕ нужно менять

- FSM/пороги circuit, local таймауты, тесты (используют явные значения).

## Validation

| Проверка | Ожидаемо |
|---|---|
| `grep file-exists-timeout-seconds` | 20 |
| `GET /api/health/circuit-breaker` после рестарта | `timeoutSeconds: 20` |
| `:karaoke-app:test --tests "*Storage*"` | PASS |
| `:karaoke-app:ktlintCheck` / `bootJar` | OK |

## Rollback

`git revert <merge-commit>` — возврат к 5s. Требуется рестарт (значение
читается при старте bean).

## Clarifications

### Session 2026-09-22

- **Q**: Где выставлять 20s?
  - **A**: В `application.yml` (дефолт) + `@Value` fallback, через spec-PR.
- **Q**: Менять ли local?
  - **A**: Нет, только remote.
