# Отчёт по #152 — StorageCircuitBreaker: убрать onErrorDropped/InterruptedException (follow-up #150)

> **Спека**: [spec.md](spec.md) | **Ветка**: `428-storage-timeout-interrupt-noise` |
> **Дата**: 2026-09-22 | **Pass**: 428.

## Симптом (после Pass 426 / PR #519)

```
ERROR [oundedElastic-10] reactor.core.publisher.Operators : Operator called default onErrorDropped
java.lang.RuntimeException: java.lang.InterruptedException
    at io.minio.MinioClient.statObject(MinioClient.java:169)
    at ...StorageApiClientImpl.statObjectOrNull(StorageApiClient.kt:502)
    at ...StorageApiClientImpl.getFileInfo$lambda$0(StorageApiClient.kt:480)
  Caused by: java.lang.InterruptedException: null
```

15 таких ERROR за инцидент, каждый со стектрейсом. Circuit при этом работал
корректно (`TimeoutException` → `recordFailure` → OPEN).

## Root cause

Pass 426 добавил `loader().subscribeOn(boundedElastic).timeout(5s)`. При истечении
timeout:

1. Mono терминализуется `TimeoutException` → circuit открывается (by design).
2. Reactor **отменяет** подписку `subscribeOn` → `Future.cancel(true)` → **прерывает**
   worker-поток.
3. `MinioClient.statObject` (внутри `CompletableFuture.get()`) получает
   `InterruptedException` и заворачивает его в `RuntimeException` — **не** в `MinioException`.
4. `statObjectOrNull` ловил только `MinioException` → `RuntimeException` улетал после
   терминации Mono → reactor `onErrorDropped` (ERROR + стек).

## Что сделано

- **`StorageApiClient.kt`**:
  - `runBlockingMinioOrNull { ... }` — блокирующие MinIO-вызовы: `MinioException` → `null`;
    `InterruptedException` (в т.ч. завёрнутое в `RuntimeException`) → `null` +
    `Thread.currentThread().interrupt()`; прочие `RuntimeException` пробрасываются.
  - `isInterruptWrapped(t)` — распознаёт interrupt в цепочке `cause`.
  - `statObjectOrNull` переведён на `runBlockingMinioOrNull`.
- **`StorageTimeoutInterruptTest.kt`** (NEW): 9 тестов — распознавание interrupt-wrapper,
  восстановление флага, `MinioException`→null, проброс прочих ошибок, и интеграционный
  `Hooks.onErrorDropped`-тест (timeout блокирующего вызова не даёт dropped-error).
- **Knowledge/docs**: `storage-api-client.md` (Pass 428), `docs/features/storage-metadata-cache.md` (V2.4).

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | OK (0 violations) |
| `StorageTimeoutInterruptTest` | 9/9 PASS |
| `StorageCircuitBreakerTest` | 14/14 PASS |
| `:karaoke-app:bootJar` | OK |
| `tools/check-knowledge-structure.sh` | 9/9 OK |
| `tools/lint-knowledge.py --baseline ...` | PASS (no new) |
| `tools/check-no-mp4-mentions.sh` | OK |

## Почему не откатили Pass 426

`subscribeOn(boundedElastic)` нужен, чтобы `timeout(5s)` реально срабатывал (иначе
circuit вечно залипал в HALF_OPEN). Правильное решение — обработать interrupt, а не
убирать `subscribeOn`.

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9: агент не перезапускает).
- Проверить после рестарта: `grep onErrorDropped` — 0 строк; `cache:network:failure
  error="TimeoutException"` остаётся.
