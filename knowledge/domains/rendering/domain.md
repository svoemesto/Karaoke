---
id: domain-rendering
title: "Domain: Rendering (Производство караоке-видео)"
status: Active
slug: rendering
related:
  - ../catalog/domain.md
  - ../processing/domain.md
  - ../identity/domain.md
  - ../../adr/0001-raw-jdbc.md
  - ../../adr/0002-mlt-instead-of-ffmpeg.md
---

# Domain: Rendering (Производство караоке-видео)

> Детальный drill-down для [processing.md](../processing/domain.md) —
> подмножество про рендеринг видео через MLT/melt + Playwright + ffmpeg.
>
> Drill-down (legacy): [livedocs/domain/rendering.md](../../../livedocs/domain/rendering.md).

## Обзор контекста (Bounded Context)

**rendering** — подсистема рендеринга финальных MP4-видео из песен + разметки.
Караоке-видео — уникальный артефакт проекта. Эта подсистема:

- Принимает Song (idStatus=6 + маркеры + стемы) и выбранную версию
  (`LYRICS` / `KARAOKE` / `DEMO`).
- Возвращает MP4 в MinIO (см. [L2-containers](../../system/02-containers.md)).
- Включает CPU-лимиты для предотвращения starvation UI.

**Почему выделено из processing**:

- Рендеринг — отдельная подсистема (vs Sheetsage + Demucs + авто-имена стемов,
  которые тоже про обработку).
- Детальная документация MLT-конфигурации в одном месте.
- ADR-0002 покрывает именно этот контекст.

**Не путать** с [processing](../processing/domain.md) (Demucs/Sheetsage/
имена стемов) — это другой concern (музыкальная обработка входных файлов).

**Граница**: контекст НЕ отвечает за:

- обработку mp3 → стемы (→ [processing](../processing/domain.md));
- хранение маркеров и метаданных песни (→ [catalog](../catalog/domain.md));
- публикацию и подписки (→ publishing context).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример |
| --- | --- | --- |
| **Render** | Задача KaraokeProcess с типом `RENDER_MP4_*` | `RENDER_MP4_LYRICS` |
| **mko** | Melt object — Kotlin-класс, генерирующий MLT-property | `BackgroundMko.asMltProp()` |
| **MLT project** | composition слоёв (mko) + аудио | файл описания mlt |
| **JPEG quality 95** | Трюк ускорения text-рендера (×3 быстрее PNG) | `canvas.toDataURL('image/jpeg', 0.95)` |
| **MLT_CPU_LIMIT** | Per-render CPU limit (через Docker `--cpus`) | на проде — 2 ядра |
| **HEAVY_RENDER lane** | `threadId = 0` — основной lane для тяжёлого рендера | см. конвенция queue lanes |
| **MltProject id** | Id в БД = Song.id (1-к-1) | `MLTProject(id=12345)` |

См. также [processing](../processing/domain.md) для полного списка
терминов об обработке. Полный словарь `RenderVersion` — см.
[dictionaries](components/dictionaries.md).

## Aggregate Roots

- **KaraokeVideo** (file, не entity в БД): результат рендера — три файла
  (LYRICS/KARAOKE/DEMO) лежат в MinIO.
- **MLTProject**: проект melt/MLT, описывающий слои караоке-видео.

## Entities

- **RenderMp4Params**: параметры одного рендера (`taskId`, `songId`,
  `renderVersion`, `startMs`, `endMs`).

## Value Objects

- **RenderVersion**: enum `LYRICS | KARAOKE | DEMO` (см. подробную
  таблицу в [dictionaries](components/dictionaries.md)).
- **MltProp** (~150 параметров): все в `KaraokeProperties.kt` (admin-side).

## Domain Events

- **MLTProjectCreated**: `MLTProject` сохранён в БД (через `saveToDb()`).
- **VideoRendered**: MP4 готов, лежит в MinIO `done_files/`.
- **RenderStarted**: рендер поставлен в очередь (через `KaraokeProcess.submit`).

## Domain Invariants | Инварианты и правила бизнеса

1. **`RenderVersion` — enum, не string**: коды `LYRICS`/`KARAOKE`/`DEMO`
   стабильны, используются в URLах, MinIO-ключах и JS-фильтрах.
2. **MP4 всегда три версии**: LYRICS (только текст), KARAOKE (текст +
   фон), DEMO (полный микс). Исключение `DEMO` для отдельных песен —
   через отдельный эпик.
3. **`MLT_CPU_LIMIT` обязателен на проде**: per-render CPU limit через
   Docker `--cpus`, иначе melt забивает CPU и UI отвечает с задержкой.
4. **Render через `HEAVY_RENDER` lane** (`threadId = 0`): лёгкие задачи
   (CRUD, поиск) идут через обычный lane; тяжёлый рендер — через
   выделенный, чтобы не блокировать UI.
5. **`MLTProject.id == Song.id`**: 1-к-1 связь, упрощает поиск и
   обновление.

## Публичные контракты (API)

### Internal API

- `KaraokeProcess.submit(...)` — постановка задачи рендера в очередь.
- `MltProject.saveToDb()` — сохранение проекта после успешного рендера.

### Внешний артефакт

- MP4 в MinIO `done_files/<songId>/<version>.mp4`.
- Доступ через `SongPublicDTO.videoUrl` (см. [catalog](../catalog/domain.md)).

## Структура компонентов (C4 L3)

- [mlt-pipeline](components/mlt-pipeline.md) — детальный MLT-пайплайн:
  mko-классы, JPEG quality трюк, CPU-limit, lane.
- [dictionaries](components/dictionaries.md) — `RenderVersion` enum и
  связанные константы.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — БД для хранения `MLTProject`.
- [0002-mlt-instead-of-ffmpeg](../../adr/0002-mlt-instead-of-ffmpeg.md) —
  почему MLT, а не ffmpeg.

## Связанные фичи

- `184-approve-status-choice` — выбор `idStatus` 5/6 (выход рендера).
- `131-fix-approve-demo-render-telegram-sync` — синхронизация DEMO-рендера
  с Telegram.

## Код (физическая реализация)

- `karaoke-app/.../mlt/` — mko/MLT-проект.
- `karaoke-app/.../KaraokeProcess*.kt` — `RENDER_MP4_*` задачи.
- `karaoke-app/.../KaraokeProperties.kt` — ~150 параметров.
