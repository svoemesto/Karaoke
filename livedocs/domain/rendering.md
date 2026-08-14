---
status: Active
slug: rendering
type: bounded-context
related:
  - ../domain/processing.md
  - ../architecture/L3-components.md
  - ../architecture/mlt-pipeline.md
  - ../architecture/queue-lanes.md
  - ../architecture/decisions/0002-mlt-instead-of-ffmpeg.md
  - ../features/184-approve-status-choice.md
  - ../features/131-fix-approve-demo-render-telegram-sync.md
---

# Bounded Context: rendering (Производство караоке-видео)

> Детальный drill-down для [processing.md](processing.md) —
> подмножество про рендеринг видео через MLT/melt + Playwright + ffmpeg.

## Назначение

**rendering** — подсистема рендеринга финальных MP4-видео из песен + разметки.
Караоке-видео — уникальный артефакт проекта. Эта подсистема:
- Принимает Song (idStatus=6 + маркеры + стемы) и выбранную версию (`LYRICS` / `KARAOKE` / `DEMO`).
- Возвращает MP4 в MinIO (см. [L2-containers.md](../architecture/L2-containers.md)).
- Включает CPU-лимиты для предотвращения starvation UI.

**Почему выделено из processing**:
- Рендеринг — отдельная подсистема (vs Sheetsage + Demucs + авто-имена стемов,
  которые тоже про обработку).
- Детальная документация MLT-конфигурации в одном месте (см.
  [mlt-pipeline.md](../architecture/mlt-pipeline.md)).
- ADR-0002 покрывает именно этот контекст.

**Не путать** с `processing` (DMucs/Sheetsage/имена стемов) — это другой
concern (музыкальная обработка входных файлов).

## Aggregate Roots

- **KaraokeVideo** (file, не entity в БД): результат рендера — три файла
  (LYRICS/KARAOKE/DEMO) лежат в MinIO.
- **MLTProject**: проект melt/MLT, описывающий слои караоке-видео.

## Entities

- **RenderMp4Params**: параметры одного рендера (`taskId`, `songId`,
  `renderVersion`, `startMs`, `endMs`).

## Value Objects

- **RenderVersion**: enum `LYRICS | KARAOKE | DEMO` (см. подробную таблицу в
  [mlt-pipeline.md](../architecture/mlt-pipeline.md)).
- **MltProp** (~150 параметров): все в `KaraokeProperties.kt` (admin-side).

## Domain Events

- **MLTProjectCreated**: `MLTProject` сохранён в БД (через `saveToDb()`).
- **VideoRendered**: MP4 готов, лежит в MinIO `done_files/`.
- **RenderStarted**: рендер поставлен в очередь (через `KaraokeProcess.submit`).

## Ubiquitous Language

См. также [processing.md](processing.md) для полного списка терминов.
Здесь — специфические для рендеринга:

| Термин | Определение | Пример |
|--------|-------------|--------|
| **Render** | Задача KaraokeProcess с типом `RENDER_MP4_*` | `RENDER_MP4_LYRICS` |
| **mko** | Melt object — Kotlin-класс, генерирующий MLT-property | `BackgroundMko.asMltProp()` |
| **MLT project** | composition слоёв (mko) + аудио | файл описания mlt |
| **JPEG quality 95** | Трюк ускорения text-рендера (×3 быстрее PNG) | `canvas.toDataURL('image/jpeg', 0.95)` |
| **MLT_CPU_LIMIT** | Per-render CPU limit (через Docker `--cpus`) | на проде — 2 ядра |
| **HEAVY_RENDER lane** | `threadId = 0` — основной lane для тяжёлого рендера | см. [architecture/queue-lanes.md](../architecture/queue-lanes.md) |
| **MltProject id** | Id в БД = Song.id (1-к-1) | `MLTProject(id=12345)` |

## Отличие от `processing`

| Concern | `processing` | `rendering` |
|---------|--------------|-------------|
| **Что** | DMucs/Sheetsage/авто-имена стемов | MP4-видео через MLT/melt |
| **Вход** | mp3-аудио из папки импорта | Song (idStatus=6 + маркеры + стемы) |
| **Выход** | 5 стемов в MinIO | 1 MP4 (одна из версий) в MinIO |
| **Compute** | Heavy GPU/CPU (Demucs — самый тяжёлый) | Medium CPU (melt — менее тяжёлый) |
| **Lane** | STEM_JOBS | HEAVY_RENDER |
| **ADR** | общий принцип (см. 0001) | [ADR-0002](../architecture/decisions/0002-mlt-instead-of-ffmpeg.md) |

## Архитектура

Подробно — в [architecture/L3-components.md](../architecture/L3-components.md)
(компонент «MLT Generator») и [architecture/mlt-pipeline.md](../architecture/mlt-pipeline.md)
(детальный pipeline).

См. также: [architecture/queue-lanes.md](../architecture/queue-lanes.md) —
lane `HEAVY_RENDER`.

## Код

- `karaoke-app/.../mlt/` — mko/MLT-проект.
- `karaoke-app/.../KaraokeProcess*.kt` — `RENDER_MP4_*` задачи.
- `karaoke-app/.../KaraokeProperties.kt` — ~150 параметров.

## Связанные LiveDocs

- [Bounded context: processing](processing.md) — «родительский» контекст.
- [architecture/L3-components.md](../architecture/L3-components.md) — где MLT
  Generator живёт в Karaoke-app.
- [architecture/mlt-pipeline.md](../architecture/mlt-pipeline.md) — drill-down.
- [architecture/queue-lanes.md](../architecture/queue-lanes.md) — HEAVY_RENDER lane.
- [ADR-0002](../architecture/decisions/0002-mlt-instead-of-ffmpeg.md) — почему MLT.
- [features/184-approve-status-choice.md](../features/184-approve-status-choice.md),
  [features/131-fix-approve-demo-render-telegram-sync.md](../features/131-fix-approve-demo-render-telegram-sync.md) — пайплайн после approve.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14