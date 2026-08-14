# ADR-0002: MLT/melt as primary stack for karaoke video generation

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, implementation evolves)
* **Deciders**: Karaoke team

> **Russian version**: [`../../livedocs/architecture/decisions/0002-mlt-instead-of-ffmpeg.md`](../../livedocs/architecture/decisions/0002-mlt-instead-of-ffmpeg.md)

## Context

Karaoke video in the project is assembled **server-side**, not in the browser.
Initially only ffmpeg was used, but limitations were discovered:

1. **Compositing complexity**. Karaoke video consists of **layers**:
   - Background image (album cover).
   - Video background (optional).
   - Text with markers (timing, fonts, size, color).
   - Audio (acc + vocals + bass + drums).
   - **Effects** (fade in/out, KARAOKE-mode, blur).
2. **Live parameter changes**. Parameters (~150 of them) change often
   without recompiling: font size, alignment, fade.
3. **Multiple versions** for one song: LYRICS / KARAOKE / DEMO
   (1280×720@30fps, 1920×1080@60fps) — each with own parameters.
4. **Render by timecodes** (e.g., 30 sec fragment for DEMO).
5. **Playwright** for rendering text frames in PNG/JPEG (with alpha channel).

## Decision

We use **MLT (Media Lovin' Toolkit, https://mltframework.org)** —
melt/MLT composition framework, as the primary stack:

- **For each video layer** — separate `mlt/mko/*` object (mko = melt object).
- **Layer parameters** — mlt-property attributes, read via `melt query`.
- **Render** — `melt -consumer avformat:output.mp4` + `-profile ...` (HDV, etc).
- **Playwright headless** — render text frame (HTML+CSS) to PNG/JPEG
  (alpha through `canvas.toDataURL('image/jpeg', 0.95)`).
- **ffmpeg** — for video+audio multiplexing, progress via `-progress pipe:1`
  (regex parsing `time=HH:MM:SS.ms`).

### Three versions of final video

| Version | Size | FPS | Audio mix | Purpose |
|---------|------|-----|-----------|---------|
| **LYRICS** | 1920×1080 | 60 | acc(1.0)+voc(1.0) | Full playback with vocals |
| **KARAOKE** | 1920×1080 | 60 | acc(1.0)+voc(0.0) | Without vocals |
| **DEMO** | 1280×720 | 30 | acc(1.0)+voc(0.0), 30sec fragment | Preview for sharing |

Each version is a separate `melt` call with different `-profile`.

### mko/ — objects by layers

Video layer = one `mko` object (in Kotlin):

```kotlin
// mlt/mko/BackgroundMko.kt
class BackgroundMko(
    private val imagePath: String,  // album cover
    private val opacity: Float = 1.0f,
    private val width: Int = 1920,
    private val height: Int = 1080
) : Mko {
    override fun asMltProp(): String =
        """image=$imagePath opacity=$opacity in=$width out=$height"""
}
```

Layers:
- `BackgroundMko` — background (album cover or video background).
- `VideoBackgroundMko` — optional animated background.
- `TextMko` — text with markers (see below).
- `AudioMko` — audio layer (acc / voc / bass / drums).
- `EffectsMko` — fade in/out, blur, blend.

### TextMko + Playwright (drill-down)

Text layer is the most complex. MLT can't nicely render text with
transparency + all CSS effects. We use Playwright:

```kotlin
// mlt/TextMko.kt
class TextMko(private val lyrics: Lyrics, private val timecode: Timecode) : Mko {
    override fun asMltProp(): String {
        // Step 1: Playwright headless renders HTML+CSS to PNG/JPEG
        val png = playwright.renderFrame(
            html = templates.lyricsHtml(lyrics, timecode),
            format = "image/jpeg",
            quality = 0.95  // JPEG quality 95 = 3x faster than PNG without visible loss
        )
        // Step 2: save to MinIO / local cache
        val path = cache.put(png, "text-${songId}-${timecode}")
        // Step 3: MLT-property
        return """image=$path start=${timecode.startMs} end=${timecode.endMs}"""
    }
}
```

### "JPEG quality 95" trick (note)

```
PNG quality 100: ~280 KB per frame, 4 sec per frame
JPEG quality 95:  ~90 KB per frame, 1.5 sec per frame (visually indistinguishable)
```

**Measured by user** (during pre-release of Pass 14+). Real win: 3-4×
faster without visible quality loss.

## Properties KaraokeProperties (~150 configurable)

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
stores parameters in file `/sm-karaoke/system/Karaoke.properties` (on
admin-machine). Edited through Properties UI/API without recompiling.

**Property groups**:
- `MLT_FONT_FAMILY`, `MLT_FONT_SIZE`, `MLT_FONT_COLOR` — text.
- `MLT_TEXT_X`, `MLT_TEXT_Y` — position.
- `MLT_FADE_IN_MS`, `MLT_FADE_OUT_MS` — fade.
- `RENDER_DEMO_SECONDS=30`, `RENDER_LYRICS_FPS=60` — versions.
- `PLAYWRIGHT_HEADLESS=true`, `PLAYWRIGHT_QUALITY=0.95` — render.
- etc. (~150 in total).

## Progress parsing

Progress is read through **stdout** of current process (see queue-lanes.md):

| Tool | Regex | Example |
|------|-------|--------|
| **ffmpeg** | `time=HH:MM:SS.ms` | `time=00:01:23.45` |
| **melt** | `NN%` | `50%` |
| **Sheetsage** | `NN%\|` | `45%\|` |
| **Demucs** | `NN%` | `30%` |

Parsed in `KaraokeProcess*:XX` → `ProgressInfo(currentPercent, etaSeconds)`
→ forwarded via SSE to `webvue3` for UI.

### `redirectErrorStream(true)` ALWAYS

```kotlin
// ✅ CORRECT
val pb = ProcessBuilder(cmd).redirectErrorStream(true)

// ❌ FORBIDDEN (stderr buffer ~64KB overflows → process blocks)
val pb = ProcessBuilder(cmd).redirectErrorStream(false)
```

See ADR-0006 and Constitution § IV.

## CPU limit

Three layers of CPU limits:
1. **Docker `--cpus`** in `docker-compose.yml` (per-container).
2. **`MLT_CPU_LIMIT`** in `KaraokeProperties`.
3. **`docker update`** dynamic.

## Alternatives Considered

- **Only ffmpeg** (complex graph through `-filter_complex`): simpler stack, but
  creating complex compositions with transitions and effects is very
  confusing ffmpeg-script, hard to debug.
- **HTML5 Canvas in browser + MediaRecorder**: rejected — moving render to
  client = slower + platform-dependent (Safari/Firefox different Canvas behavior).
- **Adobe After Effects / server-side video render**: rejected — external
  SaaS-service, violates Constitution § I.
- **MoviePy / OpenCV (Python)**: rejected — Python environment + limited
  effects support.

## References

- [Constitution § I](.specify/memory/constitution.md) — self-contained
  aut-pipeline (no SaaS dependency).
- [Constitution § IV](.specify/memory/constitution.md) — async task queue
  with stdout parsing (ffmpeg `time=`, melt `NN%`, Sheetsage `NN%|`).
- [livedocs/domain/processing.md](../../livedocs/domain/processing.md) — bounded
  context `processing` (Ubiquitous Language MLT/Demucs/Sheetsage).
- [livedocs/architecture/queue-lanes.md](../../livedocs/architecture/queue-lanes.md) —
  `HEAVY_RENDER` lane for heavy rendering.
- [livedocs/architecture/L3-components.md](../../livedocs/architecture/L3-components.md) — MLT
  Generator component.