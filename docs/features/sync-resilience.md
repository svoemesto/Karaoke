# Feature: Устойчивость синхронизации с прод-сайтом (sync resilience)

**Spec**: [`specs/431-sync-resilience/spec.md`](../../specs/431-sync-resilience/spec.md)
**Status**: active (Pass 431, feature-ветка `431-sync-resilience`)
**Created**: 2026-09-22
**Last updated**: 2026-09-22
**Source**: OpenProject #155 — «Устойчивость синхронизации с прод-сайтом»
**Feature Key**: `sync-resilience`

## Что делает

Вводит единый устойчивый HTTP-клиент `SyncRemoteClient` для всех вызовов
`POST https://sm-karaoke.ru/changerecords` в контуре синхронизации LOCAL↔SERVER,
и изолирует ошибки по сущностям: транзиентный сетевой сбой больше не валит всю
«Синхронизацию в 1 клик» в HTTP 500.

## Зачем

Прецедент 2026-09-22: кратковременный сбой до прод-сайта
(`SSLHandshakeException: Remote host terminated the handshake`, cause
`IOException: Connection timed out`) при `POST /changerecords` из
`postSyncOneClick` валил весь запрос в HTTP 500 со стектрейсом. Причина — вызовы
строились одноразовым `HttpClient.newBuilder().build()` **без таймаутов** и
**без `try/catch`**, а `postSyncOneClick` не имел `catch` вовсе.

## Как работает

### Backend (Kotlin / Spring Boot)

- `karaoke-app/.../services/SyncRemoteClient.kt` — единый helper:
  - `connectTimeout=10s`, `requestTimeout=60s`;
  - **1 retry** (2s) на транзиентные ошибки (`SSLException`, `ConnectException`,
    `HttpTimeoutException`, `IOException` timeout/reset);
  - внутренний `try/catch` — наружу не бросает, возвращает `true`/`false`;
  - логи категории `infra.sync.remote`.
- `karaoke-app/.../Utils.kt` — 6 call sites `POST /changerecords` заменены на
  `SyncRemoteClient.postChangeRecords(requestBody)`.
- `karaoke-app/.../controllers/ApiController.kt` — `postSyncOneClick`: per-target
  `try/catch` (как `AutoOneClickSyncScheduler`, FR-012 спеки #235) + `error` в
  `SyncOneClickResultDto`.

### Frontend (webvue3)

- `webvue3/src/components/Sync/SyncTable.vue` — `showResultAlert` показывает `error`.

### API контракт

`POST /api/sync/oneclick` → массив `SyncOneClickResultDto`:

```kotlin
data class SyncOneClickResultDto(
    val key: String, val displayName: String, val direction: String,
    val skipped: Boolean,
    val created: List<String>, val updated: List<String>,
    val deleted: List<String>, val moved: List<String>,
    val error: String? = null,   // Pass 431 (#155): null = успех/пропуск
)
```

### Принятые решения

- **Retry**: 1 повтор (2s), только транзиентные.
- **Таймауты**: connect 10s / request 60s.
- **Изоляция**: per-target `try/catch` в `postSyncOneClick`; `updateDatabases` не
  пропагирует.

## Инварианты

1. Ни один сетевой сбой до прод-сайта НЕ должен приводить к HTTP 5xx в
   `postSyncOneClick` — ошибка изолируется в `perTarget[].error`.
2. `SyncRemoteClient.postChangeRecords` не бросает исключений — только `true`/`false`.
3. Retry — не более 1 повтора (без лавины).
4. `SyncResult` структура не меняется; `error` в DTO — только аддитивно.

## Известные ловушки

- **Retry 2s блокирует вызывающий поток** — для ручного клика и тика scheduler
  (каждые 3ч) приемлемо; при большом числе чанков суммарная задержка растёт.
- **`HttpClient` создаётся один раз** (статический) — переиспользование соединений;
  не создавать per-call (было так в старом коде).
- **Не путать** категорию `infra.sync.remote` с `infra.prod.ping` (другой бэкенд
  мониторинга).

## Ссылки

- [`specs/431-sync-resilience/spec.md`](../../specs/431-sync-resilience/spec.md)
- [`specs/235-auto-sync-3h/spec.md`](../../specs/235-auto-sync-3h/spec.md)
- [`knowledge/domains/processing/components/run-entity-sync.md`](../../knowledge/domains/processing/components/run-entity-sync.md)
- [`knowledge/domains/processing/components/two-db-sync.md`](../../knowledge/domains/processing/components/two-db-sync.md)
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md)

## Связанные ADR

- ADR нет (изменение локальное, в рамках существующего sync-контура).
