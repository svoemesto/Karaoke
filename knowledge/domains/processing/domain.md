---
id: domain-processing
title: "Domain: Processing (Обработка)"
status: Active
slug: processing
related:
  - ../catalog/domain.md
  - ../rendering/domain.md
  - ../identity/domain.md
  - ../../adr/0001-raw-jdbc.md
  - ../../adr/0006-processbuilder-redirect-errorstream.md
---

# Domain: Processing (Обработка)

> Подсистема производства караоке-видео — MLT, AudioAnalize (локальный CLI), async-очередь.
>
> **Drift note** (Pass 341): предыдущая версия упоминала Demucs/Sheetsage
> как «ML-модели стем-сепарации/key/BPM». В текущей кодовой базе
> эти сервисы НЕ существуют — есть локальный CLI `AudioAnalize` +
> `AudioAnalize2`. См. [Known gaps](#known-gaps).
>

## Обзор контекста (Bounded Context)

Processing — контекст, отвечающий за превращение песни ([catalog](../catalog/domain.md))
в готовое караоке-видео. Это **уникальная для проекта Karaoke** подсистема,
которая запускает ffmpeg / MLT / AudioAnalize как OS-подпроцессы
через `ProcessBuilder` с парсингом stdout.

Контекст **write-light / compute-heavy** — записи в БД редки (смена
статуса), но вычисления тяжёлые (стем-сепарация, генерация MLT, рендер видео).

**Не путать** с [rendering](../rendering/domain.md): processing — это
**производство** (AudioAnalize + генерация MP4 через melt), а
rendering — это **конечный** рендер MP4 из маркеров. В текущей
архитектуре они частично перекрываются (MLT/melt живёт в обоих);
деление — логическое, не физическое.

**Граница**: контекст НЕ отвечает за:

- хранение маркеров и метаданных песни (→ [catalog](../catalog/domain.md));
- публикацию и подписки (→ [publishing](../publishing/domain.md));
- аутентификацию пользователей (→ [identity](../identity/domain.md)).

## Ubiquitous Language | Единый язык

| Термин | Определение | Пример в коде |
| --- | --- | --- |
| **MLT (melt)** | Формат проекта видеоредактора melt | `mlt/mko/*.kt`, `MLTProject.kt` |
| **Стем (Stem)** | Разделённая аудио-дорожка (vocals / acc) | `Stems.kt`, `vocals.flac` |
| **AudioAnalize** | Локальный CLI для аудио-анализа (заменяет Demucs/Sheetsage в текущей кодовой базе) | `AudioAnalize.kt`, `AudioAnalize2.kt` |
| **LYRICS** | Версия рендера: acc(1.0)+voc(1.0), 1920×1080@60fps | `RenderVersion.LYRICS` |
| **KARAOKE** | Версия рендера: acc(1.0)+voc(0.0), 1920×1080@60fps | `RenderVersion.KARAOKE` |
| **DEMO** | Версия рендера: acc(1.0)+voc(0.0), 1280×720@30fps, фрагмент | `RenderVersion.DEMO` |
| **RENDER_MP4_** | Очередь задач (HEAVY_RENDER lane, threadId=0) | `KaraokeProcess.kt` |
| **HEAVY_RENDER** | Lane для тяжёлого рендера (threadId=0) | `Utils.kt` |
| **MltProp** | Один MLT-свойство из ~150 | `MltProp.kt` |
| **Playwright** | Headless Chromium для рендера кадров | `PlayerMp4RenderService.kt` |
| **JPEG quality 95** | Оптимизация: PNG → JPEG = x3 скорость | `canvas.toDataURL('image/jpeg', 0.95)` |
| **out_time_ms** | ffmpeg прогресс через `-progress pipe:1` | `PlayerMp4MuxService.kt` |

Полный словарь `RenderVersion` — см. [rendering domain](../rendering/domain.md)
(раздел dictionaries). Детали по Playwright-интеграции — см.
[playwright-rendering](components/playwright-rendering.md). Детали
по `KaraokeProperties` — см. [karaoke-properties](components/karaoke-properties.md).

## Aggregate Roots

- **KaraokeVideo (Караоке-видео)**: результат обработки песни. Identity = `id`.
  Содержит пути к видео-файлам (LYRICS / KARAOKE / DEMO версии), параметры рендера.
  Инварианты:
  - ровно 3 версии (LYRICS, KARAOKE, DEMO) для каждой финальной песни;
  - файлы лежат в MinIO `done_files/<songId>/`.

- **MLTProject (MLT-проект)**: melt/MLT-проект, описывающий слои караоке-видео.
  Identity = `id`. Содержит ~150 настраиваемых параметров из `KaraokeProperties`.

- **RenderMp4Params (Параметры рендера)**: параметры одного рендера. Identity = `id`.
  Включает размер (1280×720 / 1920×1080), fps, codec, fragment start/end, fade.

## Entities

- **AudioAnalizeJob (Задача аудио-анализа)**: задача локального CLI
  `AudioAnalize` для распознавания BPM/key/длительности (см.
  [Known gaps](#known-gaps) — Drift note).
- **MltJob**: задача генерации MLT-проекта.
- **RenderJob**: задача рендера MP4.

## Value Objects

- **RenderVersion (LYRICS | KARAOKE | DEMO)**: какую версию рендерить
  (см. [rendering dictionaries](../rendering/components/dictionaries.md)).
- **AudioResult**: результат `AudioAnalize` — BPM, key, duration.
- **VideoFragment (startSeconds, endSeconds, fadeIn, fadeOut)**: фрагмент для DEMO.
- **MltProp (~150 параметров)**: MLT-свойства (размер шрифта, цвет, позиция, ...).

## Domain Events

- **AudioAnalizeCompleted**: `AudioAnalize` закончил, известны BPM/key/duration.
- **MltGenerated**: MLT-проект создан.
- **VideoRendered**: MP4 готов, лежит в `done_files/`.
- **RenderStarted**: рендер поставлен в очередь.

## Domain Invariants | Инварианты и правила бизнеса

1. **`ProcessBuilder` всегда с `redirectErrorStream(true)`**: stdout и
   stderr должны быть объединены, иначе процесс блокируется (см.
   [ADR-0006](../../adr/0006-processbuilder-redirect-errorstream.md)).
2. **AudioAnalize → MLT → render строго последовательны**: каждый
   следующий шаг ждёт события от предыдущего (`AudioAnalizeCompleted`,
   `MltGenerated`).
3. **MinIO — единственное место хранения MP4**: после обработки
   локальные файлы удаляются, MinIO хранит версии (`done_files/`).
   (NB: предыдущая версия упоминала `stems/<songId>/`, но в текущей
   кодовой базе стемов нет — см. [Known gaps](#known-gaps).)
4. **`KaraokeProperties` редактируется через admin UI, не напрямую в коде**:
   изменения должны проходить через UI чтобы UI-state был синхронизирован.
5. **Одна песня = одна MLTProject**: `MLTProject.id == Song.id` (1-к-1).

## Публичные контракты (API)

### Internal API

- `KaraokeProcess.submit(...)` — постановка задачи в очередь (lane, threadId).
- `MLTProject.saveToDb()` — сохранение проекта после успешного рендера.
- `AudioAnalize.run(songId)` — запуск локального CLI аудио-анализа.

### Внешний артефакт

- MP4 в MinIO `done_files/<songId>/<version>.mp4`.

## Структура компонентов (C4 L3)

- [playwright-rendering](components/playwright-rendering.md) — интеграция
  с Playwright (headless Chromium) для рендера кадров: трюки с
  `JPEG quality 95`, `canvas.toDataURL()`, `out_time_ms` для прогресса.
- [karaoke-properties](components/karaoke-properties.md) — единая точка
  конфигурации рендера (`KaraokeProperties.kt`, ~150 параметров).

## Связанные фичи

- `184-approve-status-choice` — выбор LYRICS/DEMO при approve.

## Связанные ADR

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — БД для хранения `MLTProject`.
- [0006-processbuilder-redirect-errorstream](../../adr/0006-processbuilder-redirect-errorstream.md) —
  правило `redirectErrorStream(true)`.

## Код (физическая реализация)

- MLT-генератор: `karaoke-app/src/main/kotlin/.../mlt/`
- Очередь: `karaoke-app/src/main/kotlin/.../KaraokeProcess.kt`, `KaraokeProcessRenderMp4*.kt`
- Рендер: `PlayerMp4RenderService.kt`, `PlayerMp4MuxService.kt`
- Аудио-анализ: `AudioAnalize.kt`, `AudioAnalize2.kt`
- Свойства: `KaraokeProperties.kt` (~150 параметров), `/sm-karaoke/system/Karaoke.properties`

## Known gaps

- [ ] **Drift**: `DemucsService.kt` и `SheetsageService.kt` упоминались
      в предыдущей версии этого документа, но в текущей кодовой базе
      не существуют. Заменены на `AudioAnalize.kt` (Pass 341).
      Следует проверить:
      - Что именно делает `AudioAnalize` (где запускается, какие
        аргументы принимает).
      - Где находится «управляющий код» бывших Demucs/Sheetsage —
        может, переименован.
      - Какая часть `StemJob` (premium фича «Создать минусовку»)
        использует Demucs напрямую, а какая — `AudioAnalize`.
- [ ] **MLT-генератор internals** (50 файлов в `karaoke-app/.../mlt/`):
      требует отдельной документации (P3).
- [ ] **ProcessBuilder + `redirectErrorStream(true)`** — все ли
      вызовы следуют правилу. (TODO: grep.)
- [ ] **Thread-lanes в `KaraokeProcess`** — детальное описание
      (Pass 342).
