# Implementation Plan: Таймаут доступа к удалённому хранилищу 5s → 20s

**Branch**: `430-remote-storage-timeout-20s` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

## Summary

Поднять `storage.file-exists-timeout-seconds` с 5 до 20. Это значение питает
remote-путь: `StorageCircuitBreaker.timeoutSeconds` (Mono-timeout + watchdog-дедлайн
`timeoutSeconds + buffer`) и OkHttp `connectTimeout`/`readTimeout` в
`StorageApiClientImpl`. Local-хранилище не затрагивается (свои 10s/30s).

## Technical Context

**Language/Version**: Kotlin (JVM 21), Spring Boot
**Primary Dependencies**: reactor, MinIO SDK, OkHttp
**Storage**: remote MinIO
**Testing**: JUnit 5
**Target Platform**: Linux, контейнер `karaoke-app`
**Constraints**: env-override `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` сохраняется
**Scale/Scope**: 4 файла дефолтов + knowledge/docs

## Constitution Check

- **Principle IX (Knowledge-first)** — pre-flight выполнен (см. spec.md).
- **Tier-1 Hard Gate — Knowledge SSoT** — обновляем `storage-api-client.md` + docs.
- **Tier-1 Hard Gate — Git CI-gate** — ветка `430-remote-storage-timeout-20s` + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: rebuild ✅, restart ❌.

Нарушений нет.

## Project Structure

```text
karaoke-app/src/main/resources/application.yml           # 5 -> 20
karaoke-app/.../services/StorageCircuitBreaker.kt        # @Value fallback :5 -> :20
karaoke-app/.../services/StorageCircuitBreakerConfig.kt  # fallback x2
karaoke-app/.../services/StorageApiClient.kt             # fallback :5 -> :20
knowledge/domains/storage/components/storage-api-client.md
docs/features/storage-metadata-cache.md
```

## Phase 1 — Design

Значение — единственное свойство `storage.file-exists-timeout-seconds`.
Изменяем только дефолты; логика/F SM не меняется.

| Потребитель | Было | Стало |
|---|---|---|
| circuit `.timeout` | 5s | 20s |
| OkHttp connect/read (remote) | 5s | 20s |
| watchdog deadline | 15s | 30s |
| local OkHttp (10/30s) | — | без изменений |

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).
