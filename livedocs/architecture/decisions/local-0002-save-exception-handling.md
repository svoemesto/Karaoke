# Local ADR-0002: Паттерн обработки исключений в `KaraokeDbTable.save()`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0002-save-exception-handling.md](../../../livedocs-en/decisions/local-0002-save-exception-handling.md)
>
> **Note**: this is **local** ADR — описывает конкретный паттерн в коде
> (а не глобальное архитектурное решение).

## Context

`karaoke-app/.../KaraokeDbTable.kt` — единая точка save() для всех сущностей.
Используется в сотнях мест:

```kotlin
songList.forEach { song ->
    song.saveToDb()  // внутри: KARAOKE_CONNECTION.getConnection().save()
}
```

Проблема: при падении (например, dead-lock, broken FK, network error),
исключение выбрасывается наверх — и:
- Следующие записи в forEach НЕ сохраняются.
- UI получает 500 (если из HTTP).
- Воркер стопит `doStart()` цикл (см. [087-fix-shared-db-connection](../../features/087-fix-shared-db-connection.md)).

Исторически — **разная обработка** в разных местах:
- `KaraokeProcess.save()` — пробрасывает `SQLException` (правильно).
- `getCountWaiting()` — глотает, возвращает 0 (см. [088](../../features/088-fix-queue-swallowed-errors.md)).
- `getProcessesToStart()` — глотает, возвращает пустую карту.

Это привело к **двум разным сценариям** при одном и том же БД-failure:
- Если save() падает → воркер уходит в retry.
- Если getCountWaiting() падает → воркер тихо крутится без дела.

## Decision

**Единый паттерн** для всех обращений к БД внутри воркера:

```kotlin
// ✅ ПРАВИЛЬНО — единообразная обработка (retry trigger)
class KaraokeProcessWorker {
    fun doStart() {
        while (true) {
            try {
                val processes = getProcessesToStart()  // может throw
                processes.forEach { process ->
                    try {
                        process.saveToDb()  // может throw
                        // ...
                    } catch (e: Exception) {
                        logger.error("Save failed for ${process.id}", e)
                        // Don't propagate — process will be retried via recordhash
                    }
                }
                Thread.sleep(1000)
            } catch (e: Exception) {  // воркер-level
                logger.error("doStart failed, retry in 5s", e)
                Thread.sleep(5000)
            }
        }
    }
}

// ❌ НЕПРАВИЛЬНО
fun getCountWaiting(): Int {
    try {
        return jdbcTemplate.queryForObject("SELECT count...", Integer::class)
    } catch (e: Exception) {
        return 0  // ГЛОТАЕМ — неправильно (см. ADR-0007)
    }
}
```

**Правила**:

1. **Все методы, возвращающие данные, ДОЛЖНЫ пробрасывать исключения наверх**.
2. **Воркер doStart()** ловит Exception → логирует → retry (см.
   [architecture/dual-db-access.md](../../dual-db-access.md) — retry 5x).
3. **UI (webvue3)** ловит Exception → показывает friendly error.
4. **catch в проде (HTTP)** — для diagnostics logging, не для fallback.
5. **НЕТ silent fallback'ов в слое service** — это скрывает проблемы.

## Consequences

### Positive
- Единообразная обработка (не "проблема проявляется случайно").
- Retry воркера работает предсказуемо (5 попыток, нарастающая пауза).
- Падение одного save() в forEach не стопит весь цикл (graceful skip).
- Логи показывают **полную картину** ошибок, а не "silent 0".

### Negative
- Больше try/catch в коде.
- Нужна дисциплина — каждый новый метод БД ОБЯЗАН пробрасывать.

### Neutral
- Логи могут быть шумными (если БД падает часто). В woker'е — `WARN` после 5 retry, потом `ERROR`.

## Alternatives Considered

- **Catch-all в service, fallback к empty/0/null**: rejected (см.
  ADR-0007 vs эту спецификацию) — diagnostic-friendly fallback.
- **Panic-style — все throws обязательны, много try-catch в forEach**:
  это и есть текущий рекомендуемый подход.

## References

- ADR-0007 (strict-исключения) — эквивалент ADR-0001 для Java/Kotlin.
- [architecture/dual-db-access.md](../../dual-db-access.md) — retry behavior.
- [architecture/observability.md](../../observability.md) — где логируем.
- [features/088-fix-queue-swallowed-errors.md](../../features/088-fix-queue-swallowed-errors.md) — оригинальная
  диагностика проблемы «тихих fallback'ов».
- Конституция § IV «Async-очередь задач» — retry и обработка ошибок.