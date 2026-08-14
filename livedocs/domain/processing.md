---
status: Active
slug: processing
type: bounded-context
related:
  - ../features/184-approve-status-choice.md
  - ../architecture/L3-components.md
  - ../architecture/queue-lanes.md
---

# Bounded Context: processing (Обработка)

> Подсистема производства караоке-видео — MLT, Demucs, Sheetsage, async-очередь.

## Назначение

Processing — контекст, отвечающий за превращение песни (catalog) в готовое
караоке-видео. Это **уникальная для проекта Karaoke** подсистема, которая
запускает ffmpeg / MLT / Demucs / Sheetsage как OS-подпроцессы через
`ProcessBuilder` с парсингом stdout.

Контекст **write-light / compute-heavy** — записи в БД редки (смена статуса),
но вычисления тяжёлые (стем-сепарация, генерация MLT, рендер видео).

## Aggregate Roots

- **KaraokeVideo (Караоке-видео)**: результат обработки песни. Identity = `id`.
  Содержит пути к видео-файлам (LYRICS / KARAOKE / DEMO версии), параметры рендера.
  Инварианты: ровно 3 версии (LYRICS, KARAOKE, DEMO) для каждой финальной песни.

- **MLTProject (MLT-проект)**: melt/MLT-проект, описывающий слои караоке-видео.
  Identity = `id`. Содержит ~150 настраиваемых параметров из `KaraokeProperties`.

- **RenderMp4Params (Параметры рендера)**: параметры одного рендера. Identity = `id`.
  Включает размер (1280×720 / 1920×1080), fps, codec, fragment start/end, fade.

## Entities

- **StemsJob (Задача стем-сепарации)**: задача Demucs для разделения вокала.
- **SheetsageJob**: задача распознавания аккордов/BPM/key.
- **MltJob**: задача генерации MLT-проекта.
- **RenderJob**: задача рендера MP4.

## Value Objects

- **RenderVersion (LYRICS | KARAOKE | DEMO)**: какую версию рендерить.
- **Stems (vocals, accompaniment, drums, bass, other)**: 5 дорожек от Demucs.
- **VideoFragment (startSeconds, endSeconds, fadeIn, fadeOut)**: фрагмент для DEMO.
- **MltProp (~150 параметров)**: MLT-свойства (размер шрифта, цвет, позиция, ...).

## Domain Events

- **StemsSeparated**: Demucs закончил, готовы вокал + аккомпанемент.
- **KeyBpMDetected**: Sheetsage закончил, известны key/BPM/chords.
- **MltGenerated**: MLT-проект создан.
- **VideoRendered**: MP4 готов, лежит в `done_files/`.
- **RenderStarted**: рендер поставлен в очередь.

## Ubiquitous Language (глоссарий)

| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| **MLT (melt)** | Формат проекта видеоредактора melt | `mlt/mko/*.kt`, `MLTProject.kt` |
| **Стем (Stem)** | Разделённая аудио-дорожка (vocals / acc) | `Stems.kt`, `vocals.flac` |
| **Demucs** | ML-модель стем-сепарации | `DemucsService.kt` |
| **Sheetsage** | ML-модель key/BPM/chords | `SheetsageService.kt` |
| **LYRICS** | Версия рендера: acc(1.0)+voc(1.0), 1920×1080@60fps | `RenderVersion.LYRICS` |
| **KARAOKE** | Версия рендера: acc(1.0)+voc(0.0), 1920×1080@60fps | `RenderVersion.KARAOKE` |
| **DEMO** | Версия рендера: acc(1.0)+voc(0.0), 1280×720@30fps, фрагмент | `RenderVersion.DEMO` |
| **RENDER_MP4_** | Очередь задач (HEAVY_RENDER lane, threadId=0) | `KaraokeProcess.kt` |
| **HEAVY_RENDER** | Lane для тяжёлого рендера (threadId=0) | `Utils.kt` |
| **MltProp** | Один MLT-свойство из ~150 | `MltProp.kt` |
| **Playwright** | Headless Chromium для рендера кадров | `PlayerMp4RenderService.kt` |
| **JPEG quality 95** | Оптимизация: PNG → JPEG = x3 скорость | `canvas.toDataURL('image/jpeg', 0.95)` |
| **out_time_ms** | ffmpeg прогресс через `-progress pipe:1` | `PlayerMp4MuxService.kt` |

## Связанные фичи

- [184-approve-status-choice.md](../features/184-approve-status-choice.md) — выбор LYRICS/DEMO при апруве

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md) (MLT Generator, Async Queue)
- Architecture: [queue-lanes.md](../architecture/queue-lanes.md) (threadId lanes, priority)

## Код

- MLT-генератор: `karaoke-app/src/main/kotlin/.../mlt/`
- Очередь: `karaoke-app/src/main/kotlin/.../KaraokeProcess.kt`, `KaraokeProcessRenderMp4*.kt`
- Рендер: `PlayerMp4RenderService.kt`, `PlayerMp4MuxService.kt`
- Demucs: `DemucsService.kt`
- Sheetsage: `SheetsageService.kt`
- Свойства: `KaraokeProperties.kt` (~150 параметров), `/sm-karaoke/system/Karaoke.properties`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14