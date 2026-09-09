# Task #65 — Статус по `2026-09-09`

> Публикуется в OpenProject #65 как status-обновление. **НЕ закрывает #65** —
> owner решает, достаточно ли смягчения по опыту эксплуатации, либо открывать
> follow-up спеку для полного graceful-degradation фикса.

## Что сделано (частичное смягчение)

| Когда | Что | Комментарий |
|---|---|---|
| **Pass 343** (ДО моей сессии, merge `e746a867`) | `PerSong single-flight guard` в `HealthReport.repair-loop` | Уже в master. См. `knowledge/domains/health/components/race-fixed-65.md`. Устраняет **cascading retries** в `startRepairAll` / `onRepairProcessFinished` для одной песни. |
| Уже существующее в коде | `StorageApiClient.fileExists:339-351` имеет `try { ... }.catch { null }` | Возвращает `false` без `throw`. **Cascading retries НЕ происходит** — каскад уже исключен на уровне одного `Mono.block()`-вызова. |
| **Pass 344/345 (эта сессия)** | Storage metadata cache в `HealthReport.Companion` (Pass 344 → Pass 345) | `cachedFileExists` в companion object + `StorageMetadataCache` (Postgres DAO). При cache hit → **0 MinIO round-trip**. Снижает ЧАСТОТУ вызова `fileExists` до cold start (≈1 раз на ключ за 5 мин TTL). |

## Что НЕ сделано (root-cause fix)

Полное требование из #65: «при такой ошибке не было повторной попытки
и программа шла дальше и не затормаживала остальное».

| Что | Почему не сделано | Где именно |
|---|---|---|
| **Per-call timeout в `StorageApiClient.fileExists`** | Текущий `.block()` ждёт `readTimeout=60s` (см. `StorageApiClient.kt:147`) при network outage. **Single-thread blocked** на всё время. | `StorageApiClient.kt:343-349` |
| **Circuit breaker** | Каждый cache-miss во время dead network = ещё 60s блокировки. На 18k песен × cold start = часы ожидания. | НОВАЯ фича. |
| **Semantic: false on network error** | Текущий код возвращает `false` SEMANTIC неверно (файл может существовать но сеть не отвечает). Пользователь не получает метрик/сигнал о network degradation. | `StorageApiClient.kt:347-350` + отсутствие метрик. |
| **Async / non-blocking `fileExists`** | Возврат `Mono<Boolean>` (без `.block()` в admin thread) для параллельной обработки пачек. | Полный refactor; сложно оценить. |
| **Timeout-config** | Текущий timeout — hardcoded в `StorageApiClientImpl` (`connectTimeout=15s`, `readTimeout=60s`, `writeTimeout=300s`). Не вынесен в `KaraokeProperties`. | Отдельная задача. |

## Рекомендация

Открыть **follow-up спеку #351-storage-graceful-degradation** для полного
фикса root cause. Скоуп:
1. Configurable `fileExistsTimeoutSeconds` в `KaraokeProperties`.
2. `Mono<Boolean>` с явным `.timeout(...)` в `StorageApiClient.fileExists`.
3. Circuit breaker (open after N consecutive failures, half-open after cooldown).
4. Метрика `infra.cache.storage.network.failure` (counter) для observability.
5. Опциональная поддержка `cb.state` endpoint `/api/health/circuit-breaker`.

После реализации #351 — закрыть #65 со ссылкой на follow-up.

## Альтернатива: оставить как есть

Если операционно существующее поведение достаточно (текущий single-flight
guard в Pass 343 + кеш из Pass 345 сильно снижают частоту вызовов), owner
может ЗАКРЫТЬ #65 со ссылкой на этот отчёт. **Однако** я бы рекомендовал
всё-таки дождаться #351 — root cause остаётся.

## Файлы (для traceability)

* `karaoke-app/.../services/StorageApiClient.kt:339-351` — current `fileExists` impl.
* `karaoke-app/.../services/StorageMetadataCache.kt` — Pass 344/345 cache (mitigation).
* `karaoke-app/.../HealthReport.kt` (companion `cachedFileExists` — Pass 344 wiring).
* `knowledge/domains/health/components/race-fixed-65.md` — Pass 343 single-flight docs.

## Status (OpenProject)

* **Status**: New (was unknown in latest get-issue response; был auto-claim через Pass 350 hook в одном из тестов, но эффект не подтверждён).
* **Assignee**: `ai-agent` (после тестового auto-claim).
* **Created**: 2026-09-07 (3 дня назад).
* **Updated**: 2026-09-09.
* **Closed**: false (этот отчёт НЕ закрывает #65).

## Подпись

Этот отчёт добавлен в OpenProject #65 через `tracker.sh add-comment`. Автор:
agent (Karaoke), Pass 350 governance compliance.
