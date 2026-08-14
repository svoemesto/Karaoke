# ADR-0006: ProcessBuilder + redirectErrorStream(true) for async tasks

* **Status**: Accepted
* **Date**: 2026-08-13 (Pass 26 — after incident with stderr-blocking)
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0006-processbuilder-redirect-errorstream.md`](../../livedocs/architecture/decisions/0006-processbuilder-redirect-errorstream.md)

## Context

`karaoke-app` launches **OS subprocesses** through `KaraokeProcess` (see
[queue-lanes.md](../../livedocs/architecture/queue-lanes.md)):

- **ffmpeg** — video multiplexing.
- **melt (MLT/melt)** — karaoke video rendering.
- **Sheetsage** — key/BPM/chords analysis.
- **Demucs** — stem separation.
- **Playwright headless** — text frame rendering.

All of them are **verbose in `stderr`** (warnings, debug, intermediate state).

### Incident (August 2026)

Once `ffmpeg` started printing **large warnings** to stderr (about deprecated
options). stderr buffer (default ~64 KB **pipe**, not OS) **overflowed** →
subprocess blocked on `write(stderr)`. Worker `KaraokeProcessWorker.doStart()`
couldn't continue — `proc.waitFor()` hung.

This was **because `redirectErrorStream(false)`**: stderr and stdout went
to different pipes, and **stderr-pipe nobody was reading**, only stdout.

## Decision

Wherever `karaoke-app` launches an OS subprocess:

```kotlin
// ✅ CORRECT — ALWAYS
val pb = ProcessBuilder(cmd).redirectErrorStream(true)

// ❌ FORBIDDEN — causes stderr-blocking
val pb = ProcessBuilder(cmd).redirectErrorStream(false)
```

**`redirectErrorStream(true)` merges stderr into stdout**, which we **already**
read through `BufferedReader` (for progress `time=`, `NN%`).

**NON-NEGOTIABLE**: see [Constitution § IV](.specify/memory/constitution.md).

## Progress parsing via stdout

See [mlt-pipeline.md](../../livedocs/architecture/mlt-pipeline.md) and
[queue-lanes.md](../../livedocs/architecture/queue-lanes.md):

| Tool | Pattern | Example |
|------|---------|--------|
| **ffmpeg** | `time=HH:MM:SS.ms` | `time=00:01:23.45` |
| **melt** | `NN%` | `50%` |
| **Sheetsage** | `NN%\|` | `45%\|` |
| **Demucs** | `NN%` | `30%` |

All are **stdout-streams**. stderr contains warnings (important for
debugging), but is not parsed.

### Code example

```kotlin
// karaoke-app/.../KaraokeProcess.kt
class KaraokeProcess(
    private val cmd: List<String>,
    private val priority: Int = 0,
    private val threadId: Int = 0
) {
    fun start() {
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)  // ← MANDATORY!
        val process = pb.start()
        // Read stdout (merged with stderr)
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                parseProgress(line)  // time= / NN% / NN%|
                logger.info("[$threadId] $line")
            }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Non-zero exit: $exitCode")
        }
    }
}
```

## What NOT to do

- ❌ **Don't** separate stderr and stdout (`redirectErrorStream(false)`) — trap.
- ❌ **Don't** ignore stderr (warnings important — write to log).
- ❌ **Don't** read stdout without `try-with-resources`/`useLines` — leak.

## Control

- ✅ CI: `bash tools/lint-*.sh` — NOT checked (static analysis won't help here).
- ✅ Code review: every new `ProcessBuilder()` MUST have
  `.redirectErrorStream(true)`.
- ✅ Runtime: monitoring watch for stalled processes (see [observability.md](../../livedocs/architecture/observability.md)).

## Alternatives Considered

- **ProcessBuilder + separate thread for stderr**: more complex, no point
  (stderr warnings go to logs anyway).
- **`java.lang.Runtime.exec` + Thread-threads on stdout/stderr**: legacy API,
  more boilerplate.
- **Subprocess API from Python** (via py4j): overkill for our usage.

## Test / example for regression

If we add new `ProcessBuilder` — add test case:

```kotlin
@Test
fun `stderr warnings do not block process`() {
    val cmd = listOf("bash", "-c", """
        for i in {1..1000}; do
            echo "stdout $i"
            echo "stderr noise $i" >&2
        done
    """)
    val pb = ProcessBuilder(cmd).redirectErrorStream(true)
    val process = pb.start()
    process.inputStream.bufferedReader().useLines { /* read */ }
    val exitCode = process.waitFor()  // < 5 sec, doesn't block
    assertEquals(0, exitCode)
}
```

Without `redirectErrorStream(true)` this test would hang after ~30 sec
(when stderr-pipe overflows).

## References

- Constitution § IV — async task queue with stdout parsing.
- [architecture/queue-lanes.md](../../livedocs/architecture/queue-lanes.md) — progress-parsing pattern.
- [architecture/mlt-pipeline.md](../../livedocs/architecture/mlt-pipeline.md) — usage examples.
- [architecture/observability.md](../../livedocs/architecture/observability.md) — monitoring stalled processes.