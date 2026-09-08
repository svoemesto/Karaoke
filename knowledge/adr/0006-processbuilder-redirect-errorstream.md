# ADR-0006: ProcessBuilder + redirectErrorStream(true) для async-задач

* **Status**: Accepted
* **Date**: 2026-08-13 (Pass 26 — после инцидента с stderr-блокировкой)
* **Deciders**: команда Karaoke

## Context

`karaoke-app` запускает **OS-подпроцессы** через `KaraokeProcess` (см.
[queue-lanes.md](../queue-lanes.md)):

- **ffmpeg** — мультиплексирование видео.
- **melt (MLT/melt)** — рендеринг караоке-видео.
- **Sheetsage** — анализ key/BPM/chords.
- **Demucs** — стем-сепарация.
- **Playwright headless** — рендер кадра текста.

Все они **многословные** в `stderr` (warnings, debug, intermediate state).

### Инцидент (август 2026)

Однажды `ffmpeg` начал печатать **большие warnings** в stderr (про deprecated
опции). Буфер stderr (default ~64 KB **pipe**, не OS) **переполнился** →
подпроцесс заблокировался на `write(stderr)`. Воркер `KaraokeProcessWorker.doStart()`
не мог продолжить — `proc.waitFor()` висел.

Это было **потому что `redirectErrorStream(false)`**: stderr и stdout шли
в разные pipe'ы, и **stderr-pipe никто не читал**, только stdout.

## Decision

Везде, где `karaoke-app` запускает OS-подпроцесс:

```kotlin
// ✅ ПРАВИЛЬНО — ВСЕГДА
val pb = ProcessBuilder(cmd).redirectErrorStream(true)

// ❌ ЗАПРЕЩЕНО — приводит к stderr-блокировке
val pb = ProcessBuilder(cmd).redirectErrorStream(false)
```

**`redirectErrorStream(true)` сливает stderr в stdout**, который мы **уже**
читаем через `BufferedReader` (для прогресса `time=`, `NN%`).

**NON-NEGOTIABLE**: см. [Constitution § IV](.specify/memory/constitution.md).

## Прогресс-парсинг через stdout

См. [mlt-pipeline.md](../mlt-pipeline.md) и [queue-lanes.md](../queue-lanes.md):

| Tool | Pattern | Пример |
|------|---------|--------|
| **ffmpeg** | `time=HH:MM:SS.ms` | `time=00:01:23.45` |
| **melt** | `NN%` | `50%` |
| **Sheetsage** | `NN%\|` | `45%\|` |
| **Demucs** | `NN%` | `30%` |

Это всё **stdout-стримы**. stderr содержит warnings (важные для отладки),
но не парсится.

### Пример кода

```kotlin
// karaoke-app/.../KaraokeProcess.kt
class KaraokeProcess(
    private val cmd: List<String>,
    private val priority: Int = 0,
    private val threadId: Int = 0
) {
    fun start() {
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)  // ← ОБЯЗАТЕЛЬНО!
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

## Что НЕ делать

- ❌ **Не** разделять stderr и stdout (`redirectErrorStream(false)`) — ловушка.
- ❌ **Не** игнорировать stderr (warnings важны — пишем в лог).
- ❌ **Не** читать stdout без `try-with-resources`/`useLines` — утечка.

## Контроль

- ✅ CI: `bash tools/lint-*.sh` — НЕ проверяет (статический анализ тут
  не поможет).
- ✅ Code review: каждый новый `ProcessBuilder()` ОБЯЗАН иметь
  `.redirectErrorStream(true)`.
- ✅ Runtime: monitoring watch на stalled процессы (см. [observability.md](../observability.md)).

## Alternatives Considered

- **ProcessBuilder + отдельный поток для stderr**: сложнее, смысла нет
  (stderr warnings идут в логи).
- **`java.lang.Runtime.exec` + Thread-потоки на stdout/stderr**: legacy API,
  больше boilerplate.
- **Subprocess API из Python** (через py4j): overkill для нашего usage.

## Тест / пример для регрессии

Если добавляем новый `ProcessBuilder` — добавляем тест-кейс:

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
    process.inputStream.bufferedReader().useLines { /* читаем */ }
    val exitCode = process.waitFor()  // < 5 сек, не блокируется
    assertEquals(0, exitCode)
}
```

Без `redirectErrorStream(true)` этот тест зависнет через ~30 сек (когда
stderr-pipe переполнится).

## Ссылки

- Constitution § IV — async-очередь задач с парсингом stdout.
- [architecture/queue-lanes.md](../queue-lanes.md) — паттерн прогресс-парсинга.
- [architecture/mlt-pipeline.md](../mlt-pipeline.md) — примеры использования.
- [architecture/observability.md](../observability.md) — мониторинг stalled процессов.