# Contract: Формат лог-сообщений `infra.cache.statbysong`

**Привязка**: [specs/289-fix-statbysong-cache-on-cold-start/spec.md](../spec.md) — FR-006, FR-007, FR-008

> «Контракт» в этой фиче — формат log-сообщений для cold-start refresh и связанных событий. Соглашение между кодом `StatBySong` и инструментами диагностики.

---

## 1. Категория логгера

| Имя категории | Назначение | Уровни | Класс-владелец |
|---------------|------------|--------|----------------|
| `infra.cache.statbysong` | Логирование cold-start refresh и ошибок `StatBySong` | WARN, INFO | `StatBySong` (singleton object) |

### Идинификация через SLF4J

```kotlin
import org.slf4j.LoggerFactory

private val cacheLog = LoggerFactory.getLogger("infra.cache.statbysong")
```

Категория новая, не пересекается с существующими:
- `infra.prod.ping` / `infra.prod.db` (фича 288) — для пингов прода.
- `com.svoemesto.karaokeapp.*` — дефолтные категории.

---

## 2. WARN при cold-start

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание |
|------|-------------|-----|----------|
| `TIMESTAMP` | yes | ISO 8601 / Logback pattern | `yyyy-MM-dd HH:mm:ss.SSS TZ` (Europe/Moscow после 288-) |
| `LEVEL` | yes | WARN | — |
| `CATEGORY` | yes | String | `infra.cache.statbysong` |
| `EVENT_KEY` | yes | String | `cache:coldStart triggering background refresh` |

### Пример

```
2026-09-01 20:30:00.123 MSK WARN infra.cache.statbysong - cache:coldStart triggering background refresh
```

### Условие записи (FR-006)

```kotlin
private fun ensureCacheInitialized() {
    if (cachedTotal.get() < 0 && refreshing.compareAndSet(false, true)) {
        cacheLog.warn("cache:coldStart triggering background refresh")
        bgExecutor.submit { try { refreshCache() } finally { refreshing.set(false) } }
    }
}
```

Пишется **ровно 1 раз** при первом cold-start (благодаря `AtomicBoolean refreshing`). Последующие запросы, приходящие в течение background refresh, **НЕ** пишут WARN (single-flight pattern).

---

## 3. INFO после успешного refresh

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание |
|------|-------------|-----|----------|
| `EVENT_KEY` | yes | String | `cache:refreshed` |
| `total` | yes | Int | Общее количество песен |
| `collection` | yes | Int | Песен с id_status >= 6 AND source_markers непуст |
| `freeNow` | yes | Int | Бесплатно сейчас |
| `subscriptionOnly` | yes | Int | Только по подписке |
| `inWork` | yes | Int | В работе (не collection) |
| `durationMs` | yes | Long | Длительность refresh в мс |

### Пример

```
2026-09-01 20:30:12.456 MSK INFO infra.cache.statbysong - cache:refreshed total=18500 collection=12345 freeNow=8500 subscriptionOnly=3845 inWork=6155 durationMs=12333
```

### Условие записи (FR-007)

После успешного завершения `refreshCache()` (async background ИЛИ scheduler-initiated).

```kotlin
fun refreshCache() {
    val startMs = System.currentTimeMillis()
    // ... existing logic ...
    val durationMs = System.currentTimeMillis() - startMs
    cacheLog.info(
        "cache:refreshed total={} collection={} freeNow={} subscriptionOnly={} inWork={} durationMs={}",
        total, collection, freeNow, subscriptionOnly, inWork, durationMs,
    )
}
```

---

## 4. WARN при ошибке refresh

### Шаблон

```
{TIMESTAMP} {LEVEL} {CATEGORY} - {EVENT_KEY} {KEY=VALUE} ...
```

### Поля

| Поле | Обязательно | Тип | Описание |
|------|-------------|-----|----------|
| `EVENT_KEY` | yes | String | `cache:refreshFailed` |
| `error` | yes | String | Сообщение exception (в кавычках) |
| `exceptionClass` | yes | String | Класс exception (FQN) |

### Пример

```
2026-09-01 20:30:05.789 MSK WARN infra.cache.statbysong - cache:refreshFailed error="Connection refused" exceptionClass=org.postgresql.util.PSQLException
java.sql.SQLException: Connection refused
    at org.postgresql.core.v3.ConnectionFactoryImpl.openConnectionImpl(...)
    ...
```

### Условие записи (FR-008)

В `finally` блоке background refresh (или в `catch` основного потока). Stacktrace передаётся через SLF4J `Throwable`-параметр.

```kotlin
bgExecutor.submit {
    try {
        refreshCache()
    } catch (e: Exception) {
        cacheLog.warn("cache:refreshFailed error=\"{}\" exceptionClass={}",
            e.message, e::class.java.name, e)
    } finally {
        refreshing.set(false)
    }
}
```

---

## 5. Совместимость с существующими инструментами

| Инструмент | Поддержка |
|-------------|-----------|
| `grep "infra.cache.statbysong"` | ✓ substring match |
| `grep "cache:refreshFailed"` | ✓ конкретное событие |
| `grep "duration:"` (общий) | ✓ в сочетании с WARN |
| Logback appender `<logger name="infra.cache.*" level="..."/>` | ✓ если будет настроен |

---

## История

- Создан: 2026-09-01 (Phase 1)