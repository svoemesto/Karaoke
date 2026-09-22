# Implementation Plan: Устойчивость синхронизации с прод-сайтом

**Branch**: `431-sync-resilience` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

Ввести единый helper `SyncRemoteClient.postChangeRecords(...)` для всех сетевых
вызовов `POST https://sm-karaoke.ru/changerecords`: `HttpClient` с
`connectTimeout=10s`/`requestTimeout=60s`, **1 retry** (2s) на транзиентные ошибки,
`try/catch` внутри (наружу не бросает), лог `infra.sync.remote`. Заменить 6
copy-paste call sites в `Utils.kt`. `postSyncOneClick` — per-target `try/catch` +
`error: String?` в `SyncOneClickResultDto`. Фронт показывает `error`.

## Technical Context

**Language/Version**: Kotlin (JVM 21), Spring Boot
**Primary Dependencies**: `java.net.http.HttpClient`, Jackson `ObjectMapper`
**Testing**: JUnit 5 (helper с инъектируемым «sender» для тестов)
**Target Platform**: Linux, контейнер `karaoke-app`
**Constraints**: не менять `SyncResult`; аддитивное `error` в DTO
**Scale/Scope**: 1 new file + `Utils.kt` (6 sites) + `ApiController` + `SyncTable.vue`

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (см. spec.md).
- **Tier-1 Hard Gate — Knowledge SSoT** — `two-db-sync.md`, `run-entity-sync.md`,
  `log-categories.md`, новый `docs/features/sync-resilience.md`.
- **Tier-1 Hard Gate — Git CI-gate** — ветка `431-sync-resilience` + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.
- **R-43/R-44** — не затрагиваются.

Нарушений нет.

## Project Structure

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/
└── SyncRemoteClient.kt          # NEW: postChangeRecords + retry + timeouts + log

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                     # MODIFY: 6 call sites -> helper; не пропагировать
└── controllers/ApiController.kt # MODIFY: per-target catch + error в DTO

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/
└── SyncRemoteClientTest.kt      # NEW

webvue3/src/components/Sync/SyncTable.vue  # MODIFY: показать error
```

## Phase 0 — Research

- `HttpClient.newBuilder().connectTimeout(Duration).build()`; per-request
  `HttpRequest.newBuilder().timeout(Duration)` = requestTimeout.
- Транзиентные: `SSLHandshakeException`, `SSLException`, `ConnectException`,
  `HttpTimeoutException`, `IOException` с "timed out"/"Connection reset".
- Тестируемость: helper принимает `sender: (HttpRequest) -> HttpResponse<String>`
  (default — реальный `client.send`), чтобы подменить в тестах.

## Phase 1 — Design

```kotlin
object SyncRemoteClient {
    private val log = LoggerFactory.getLogger("infra.sync.remote")
    const val URL = "https://sm-karaoke.ru/changerecords"
    private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
    private val REQUEST_TIMEOUT = Duration.ofSeconds(60)
    private const val RETRY_DELAY_MS = 2_000L

    fun postChangeRecords(body: String): Boolean = postChangeRecords(body) { realSend(it) }

    internal fun postChangeRecords(body: String, send: (HttpRequest) -> HttpResponse<String>): Boolean {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .timeout(REQUEST_TIMEOUT)
                    .POST(BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .build()
                val resp = send(req)
                log.info("sync:remote:ok status={} attempt={}", resp.statusCode(), attempt + 1)
                return resp.statusCode() in 200..299
            } catch (e: Exception) {
                lastError = e
                if (!isTransient(e) || attempt == 1) {
                    log.warn("sync:remote:failure attempt={} error={}", attempt + 1, "${e::class.simpleName}: ${e.message}")
                    return false
                }
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
        return false
    }
}
```

`updateDatabases`: заменить блоки `client.send` на `SyncRemoteClient.postChangeRecords(requestBody)`;
при `false` — не бросать (логируется внутри), продолжать остальные чанки/сущности.

`ApiController.postSyncOneClick`: обернуть `runEntitySync` в per-target `try/catch`,
заполнить `error`.

### Контракт DTO

```kotlin
data class SyncOneClickResultDto(
    ..., val skipped: Boolean, ..., val error: String? = null,
)
```

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).
