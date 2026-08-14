# Local ADR-0005: Структурированное логирование в `karaoke-app`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0005-structured-logging-karaoke-app.md](../../../livedocs-en/decisions/local-0005-structured-logging-karaoke-app.md)
>
> **Note**: this is **local** ADR — описывает конвенцию логирования
> (а не глобальное архитектурное решение).

## Context

`karaoke-app` логирует события в `stdout` через SLF4J (Kotlin) →
`Logback` (default). Без явных правил:

- **Неструктурированный формат** — сложно парсить через grep/ELK.
- **Разные уровни** — кто-то использует `info`, кто-то `warn` для того же.
- **Нет request-id** — нельзя проследить одну операцию через логи.
- **Нет контекста** — где (thread, queue, threadId), что (Song id, command)?

## Decision

**Структурированное логирование** для `karaoke-app`:

```kotlin
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class KaraokeProcess(
    private val cmd: List<String>,
    private val threadId: Int = 0
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun start() {
        // 1. Установить MDC context
        MDC.put("threadId", threadId.toString())
        MDC.put("cmd", cmd.joinToString(" "))
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)

        try {
            log.info("process:start")  // structured key
            val process = pb.start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    parseProgress(line)  // тихий парсинг
                    if (log.isDebugEnabled) log.debug("process:stdout:{}", line)
                }
            }
            val exitCode = process.waitFor()
            log.info("process:end:exitCode={}", exitCode)  // structured value
        } catch (e: Exception) {
            log.error("process:failed:cmd={}", cmd.joinToString(" "), e)
            throw e
        } finally {
            // 2. Очистить MDC (важно!)
            MDC.clear()
        }
    }
}
```

**Структурированный формат лога** (key=value через пробел):

```
2026-08-14 21:00:00 [INFO] KaraokeProcess - process:start threadId=0 cmd="ffmpeg -i in.mp4 ..." requestId=abc-123
2026-08-14 21:00:01 [INFO] KaraokeProcess - process:stdout:time=00:00:23.45 threadId=0 requestId=abc-123
2026-08-14 21:00:30 [INFO] KaraokeProcess - process:end:exitCode=0 threadId=0 requestId=abc-123
2026-08-14 21:00:30 [WARN] KaraokeProcess - retry:attempt=2 threadId=0 requestId=abc-123
```

### Правила

1. **MDC для thread-local контекста**: `requestId`, `threadId`, `songId`, `command`.
2. **Структурированный format**: `event:key=value` (key:value — действие, key=value — параметры).
3. **Уровни**:
   - `INFO` — нормальное событие (start/end).
   - `WARN` — recoverable issue (retry, recoverable error).
   - `ERROR` — невосстановимое (uncaught exception).
   - `DEBUG` — детали (stdout lines, parsing).
4. **MDC.clear()** в `finally` — обязательно (memory leak иначе).
5. **Никаких credentials в логах** — см. Constitution § VIII.5.

### Конвенция имён для logger

```kotlin
// Хорошо
private val log = LoggerFactory.getLogger(javaClass)
// → "com.svoemesto.karaokeapp.process.KaraokeProcess"

// Плохо
private val log = LoggerFactory.getLogger("process")
// → не показывает полное имя класса, сложнее искать
```

### Конфигурация logback (logback-spring.xml)

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg %X%n</pattern>
        </encoder>
    </appender>

    <!-- Уровни по пакетам -->
    <logger name="com.svoemesto.karaokeapp" level="INFO"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="org.springframework" level="WARN"/>
    <logger name="org.postgresql" level="WARN"/>

    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Consequences

### Positive
- **Grep-friendly**: `grep "process:end" log.txt` находит все завершения.
- **Traceable**: `requestId` в логе → можно проследить одну операцию.
- **Contextual**: `threadId=0` (HEAVY_RENDER), `songId=12345` — что и где.
- **Performance**: уровни (DEBUG отключён в prod) уменьшают объём.

### Negative
- **Discipline**: каждый new logger должен следовать convention.
- **MDC cleanup**: легко забыть `MDC.clear()` → memory leak.

### Neutral
- **Logback** (Spring default) — никаких новых зависимостей.

## Alternatives Considered

- **Logstash / Kibana (ELK stack)**: rejected — требует инфраструктуры,
  overkill для текущего объёма.
- **Plain text без MDC**: rejected — нельзя trace.
- **JSON structured logs**: rejected — требует logstash-logback-encoder.

## References

- Constitution § VIII.5 — секреты через env (не в логах).
- [architecture/observability.md](../../observability.md) — где логи
  наблюдаются (RenderQueueStalledCheck, алерты).
- MDC: https://logback.qos.ch/manual/mdc.html

## Код

- `karaoke-app/src/main/resources/logback-spring.xml` — конфигурация.
- Все `*Service.kt` и `*Worker.kt` — должны следовать convention.
- `KaraokeProcess.kt` — образец (с MDC + finally).

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14