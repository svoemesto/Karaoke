# Local ADR-0005: Structured logging in `karaoke-app`

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md](../../../livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md)
>
> **Note**: this is **local** ADR — describes logging convention
> (not global architecture decision).

## Context

`karaoke-app` logs events to `stdout` via SLF4J (Kotlin) →
`Logback` (default). Without explicit rules:

- **Unstructured format** — hard to parse via grep/ELK.
- **Different levels** — someone uses `info`, someone `warn` for the same.
- **No request-id** — can't trace one operation through logs.
- **No context** — where (thread, queue, threadId), what (Song id, command)?

## Decision

**Structured logging** for `karaoke-app`:

```kotlin
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class KaraokeProcess(
    private val cmd: List<String>,
    private val threadId: Int = 0
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun start() {
        // 1. Set MDC context
        MDC.put("threadId", threadId.toString())
        MDC.put("cmd", cmd.joinToString(" "))
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)

        try {
            log.info("process:start")
            val process = pb.start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    parseProgress(line)
                    if (log.isDebugEnabled) log.debug("process:stdout:{}", line)
                }
            }
            val exitCode = process.waitFor()
            log.info("process:end:exitCode={}", exitCode)
        } catch (e: Exception) {
            log.error("process:failed:cmd={}", cmd.joinToString(" "), e)
            throw e
        } finally {
            // 2. Clear MDC (important!)
            MDC.clear()
        }
    }
}
```

**Structured log format** (key=value through space):

```
2026-08-14 21:00:00 [INFO] KaraokeProcess - process:start threadId=0 cmd="ffmpeg -i in.mp4 ..." requestId=abc-123
2026-08-14 21:00:01 [INFO] KaraokeProcess - process:stdout:time=00:00:23.45 threadId=0 requestId=abc-123
2026-08-14 21:00:30 [INFO] KaraokeProcess - process:end:exitCode=0 threadId=0 requestId=abc-123
2026-08-14 21:00:30 [WARN] KaraokeProcess - retry:attempt=2 threadId=0 requestId=abc-123
```

### Rules

1. **MDC for thread-local context**: `requestId`, `threadId`, `songId`, `command`.
2. **Structured format**: `event:key=value` (key:value — action, key=value — params).
3. **Levels**:
   - `INFO` — normal event (start/end).
   - `WARN` — recoverable issue (retry, recoverable error).
   - `ERROR` — unrecoverable (uncaught exception).
   - `DEBUG` — details (stdout lines, parsing).
4. **MDC.clear()** in `finally` — REQUIRED (memory leak otherwise).
5. **No credentials in logs** — see Constitution § VIII.5.

### Logger naming convention

```kotlin
// Good
private val log = LoggerFactory.getLogger(javaClass)
// → "com.svoemesto.karaokeapp.process.KaraokeProcess"

// Bad
private val log = LoggerFactory.getLogger("process")
// → doesn't show full class name, harder to find
```

### Logback configuration (logback-spring.xml)

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg %X%n</pattern>
        </encoder>
    </appender>

    <!-- Levels by package -->
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
- **Grep-friendly**: `grep "process:end" log.txt` finds all completions.
- **Traceable**: `requestId` in log → can trace one operation.
- **Contextual**: `threadId=0` (HEAVY_RENDER), `songId=12345` — what and where.
- **Performance**: levels (DEBUG disabled in prod) reduce volume.

### Negative
- **Discipline**: every new logger should follow convention.
- **MDC cleanup**: easy to forget `MDC.clear()` → memory leak.

### Neutral
- **Logback** (Spring default) — no new dependencies.

## Alternatives Considered

- **Logstash / Kibana (ELK stack)**: rejected — requires infrastructure,
  overkill for current volume.
- **Plain text without MDC**: rejected — can't trace.
- **JSON structured logs**: rejected — requires logstash-logback-encoder.

## References

- Constitution § VIII.5 — secrets via env (not in logs).
- [architecture/observability.md](../../observability.md) — where logs are observed (RenderQueueStalledCheck, alerts).
- MDC: https://logback.qos.ch/manual/mdc.html

## Code

- `karaoke-app/src/main/resources/logback-spring.xml` — configuration.
- All `*Service.kt` and `*Worker.kt` — should follow convention.
- `KaraokeProcess.kt` — example (with MDC + finally).

## History

- Created: 2026-08-14
- Last updated: 2026-08-14