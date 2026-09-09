# Component: mlt-generator

> **Домен**: [rendering](../domain.md)
> **Компонента**: MLT-генератор XML-проекта для melt/MLT-движка.
> Pass 366-370 Knowledge-аудита.

## Назначение

`karaoke-app/.../mlt/` — **генератор XML-проекта для melt/MLT-движка**.
Караоке-видео = MLT XML + FLAC-стемы → `melt` → MP4 (см.
[rendering domain](../domain.md)).

MLT (MediaLovinToolkit) — открытый XML-формат для нелинейного
видеомонтажа. Используется в Karaoke для генерации караоке-видео.

## Структура (52 Kotlin файла, ~7700 строк)

```
karaoke-app/.../mlt/
├── Mlt.kt                     # MltInitialStructure + helpers
├── MltProp.kt                 # 724 строки — параметры рендера (~150)
├── MltPropBuilder.kt          # Builder для MltProp
├── MltClasses.kt              # 220 строк — MltObject, MltShape, MltObjectAlignment
├── MltGenerator.kt            # 325 строк — генератор одного Producer'а
├── BlackTrack.kt              # 29 строк — black track producer
├── Consumer.kt                # 31 строка — consumer (mp4 output)
├── Profile.kt                 # 27 строк — MLT profile (HD 1080p 60fps)
├── mko/                       # 41 файл (Mko*) — конкретные Producers
│   ├── MkoAudio.kt            # Аудио-слои (vocals/accomp/mix/source)
│   ├── MkoMainBin.kt          # MAINBIN (главный бинарник)
│   ├── MkoHeader.kt           # Заголовок (471 строк)
│   ├── MkoSplashStart.kt      # Splash start (359)
│   ├── MkoMelodyTabs.kt       # Melody tabs (313)
│   ├── MkoChordPictureFader.kt # Chord picture fader (468)
│   ├── MkoFlash.kt            # Flash (229)
│   ├── MkoProgress.kt         # Progress bar (227)
│   └── ... (ещё 33 файла, см. таблицу)
└── mko2/                      # 1 файл — новый формат (Pass 343+)
    └── Mlt2KaraokeObject.kt    # Интерфейс для Mlt2 v2
```

## Mko файлы (каталог)

| # | Mko | Строк | Что |
|---|---|---|---|
| 1 | `MkoHeader` | 471 | Заголовок видео (верхняя панель) |
| 2 | `MkoChordPictureFader` | 468 | Fader для chord-картинок |
| 3 | `MkoSplashStart` | 359 | Splash-экран в начале |
| 4 | `MkoMelodyTabs` | 313 | Мелодия + табы (гитара) |
| 5 | `MkoMainBin` | 310 | MAINBIN (главный бинарник) |
| 6 | `MkoHorizon` | 276 | Горизонт (фон) |
| 7 | `MkoMelodyNote` | 245 | Мелодия (ноты) |
| 8 | `MkoFlash` | 229 | Flash-эффекты |
| 9 | `MkoProgress` | 227 | Прогресс-бар |
| 10 | ... ещё 31 файл (текст, аккорды, фоны, watermark, и др.) | 50-250 каждый | См. `ls karaoke-app/.../mlt/mko/` |

**NB**: большинство Mko файлов имеют конструкторы с **одинаковой
сигнатурой** (см. `Mlt.kt`):

> Все классы продюссеров должны иметь конструкторы с одинаковым
> количеством, типом и последовательностью аргументов, чтобы их
> можно было вызвать с помощью рефлексии.

```kotlin
data class Mko<...>(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject
```

## Архитектура

### `Mlt.kt` (MltInitialStructure)

**Назначение**: основной класс — обёртка над MLT-структурой.

```kotlin
data class MltInitialStructure(
    var mltProp: MltProp,
    var type: ProducerType,
    var voiceId: Int = -1,
    var childId: Int = -1,
    ...
)
```

### `MltProp` (724 строки)

**Назначение**: **150 параметров рендера** (см.
[processing/karaoke-properties](../../processing/components/karaoke-properties.md)).

**Поля** (по grep):

- Размеры (`width`, `height`, `frameWidthPx`, `frameHeightPx`).
- Шрифты (`fontFamily`, `fontSize`, `fontColor`, `fontStroke`).
- Цвета (`bgColor`, `textColor`, `accentColor`).
- Тайминги (`prerollMs`, `fadeInMs`, `fadeOutMs`).
- И многое другое.

**Методы** (по grep):

- `getSongStartTimecode()`, `getSongEndTimecode()`.
- `getLengthFr(suffix: String)` — длина в frames.
- `getPath(listOf(type))` — путь к файлу.
- `getVolume(listOf(type))` — громкость слоя.

### `MltGenerator` (325 строк)

**Назначение**: генератор **одного Producer'а** (визуального слоя).

**Алгоритм**:

1. На вход: `MltProp`, `ProducerType`, `voiceId`, `childId`,
   `elementId`.
2. Использует `MltProp` (параметры) и `MltNodeBuilder` (DSL для XML).
3. Конкретный Producer — через `mko` (например, `MkoHeader`,
   `MkoAudio`).

### `Mko` файлы (конкретные Producers)

Каждый Mko — это `data class` с одинаковой сигнатурой, который
через `MltKaraokeObject` интерфейс возвращает `MltNode` (XML).

**Иерархия `ProducerType`** (см.
[entities-catalog.md#producer](../../catalog/components/entities-catalog.md#producer)):

```
MAINBIN (level 0)
├── AUDIOVOCAL (1)
├── AUDIOMUSIC (1)
├── AUDIOSONG (1)
├── AUDIOBASS (1)
├── AUDIODRUMS (1)
├── BACKGROUND (1)
├── HORIZON (1)
├── FLASH (1)
├── PROGRESS (1)
├── FADERTEXT (1)
├── BACKCHORDS (1)
├── FINGERBOARD (1)
├── HEADER (1)
├── WATERMARK (1)
├── SPLASHSTART (1)
├── BOOSTY (1)
└── VOICES (1)
    └── VOICE (2)
        ├── COUNTERS (3)
        ├── SCROLLERS (3)
        ├── LINES (3)
        ├── CHORDSBOARD (3) (отдельно от MAINBIN)
        └── FILLCOLORSONGTEXTS (3)
```

Каждый Mko соответствует одному `ProducerType`. ~50 типов × 1
Mko = ~50 файлов (но многие Mko переиспользуются для группы).

### `MltPropBuilder` (13 строк)

Тривиальный builder. Параметры накапливаются в `MutableMap<String, Any>`.

### `BlackTrack` (29 строк)

Producer для **чёрной дорожки** (заполнение пустот в видео).

### `Consumer` (31 строка)

MLT consumer (mp4 output). Настройки: `x265-medium`, CRF=15,
`preset=ultrafast`, `vcodec=libx265`, `acodec=aac`.

### `Profile` (27 строк)

MLT profile: HD 1080p 60fps, `colorspace=709`.

## MltNode и MltNodeBuilder (в `model/`)

**Файл**: `karaoke-app/.../model/MltNode.kt` (см.
[utilities.md](../../../system/utilities.md) — hotspots).

`MltNode` — обёртка над XML-узлом:
```kotlin
data class MltNode(
    val name: String,
    val fields: MutableMap<String, String> = mutableMapOf(),
    val body: Any? = null,  // String или List<MltNode>
)
```

`MltNodeBuilder` — DSL для построения:
```kotlin
MltNodeBuilder().apply {
    node("tractor") {
        field("in", "0")
        field("out", "1234")
        ...
    }
}
```

`MltNode.apply` — сериализация в XML-строку.

## mko2 — следующее поколение

`mko2/Mlt2KaraokeObject.kt` — **интерфейс** для будущего v2
формата. Сейчас — 1 файл, видимо, в разработке. ProducerType
enum уже есть (см. entities-catalog.md), но конкретные
`Mlt2*Producer` классы ещё не реализованы.

## Hot paths

- **MLT generation** — для каждой песни при `RENDER_MP4_*` задаче
  (см. [async-process-queue](../../processing/components/async-process-queue.md)).
- **150 параметров** рендера — каждое изменение требует пересборки MLT.

## Ловушки

1. **Все классы продюссеров** должны иметь **одинаковую сигнатуру
   конструктора** — иначе reflection сломается.
2. **`mltProp.getLengthFr("Suffix")`** — Suffix должен точно
   совпадать с одним из `SongOutputFile` (например,
   `Song`, `Background`, `SongVocal`).
3. **ProducerType иерархия** — `level` важен для правильного
   порядка XML.
4. **Tractor vs playlist** — разные структуры для разных
   ProducerType.
5. **mko2 в разработке** — НЕ использовать в production.

## Связь с другими компонентами

- **ProducerType** ([entities-catalog.md#producer](../../catalog/components/entities-catalog.md#producer)) —
  enum иерархии.
- **MltNode / MltNodeBuilder** ([utilities.md](../../../system/utilities.md)) —
  XML-обёртка.
- **KaraokeProperties** ([karaoke-properties.md](../../processing/components/karaoke-properties.md)) —
  150 параметров.
- **Async Process Queue** ([async-process-queue](../../processing/components/async-process-queue.md)) —
  `RENDER_MP4_*` использует MLT-генератор.
- **RenderMp4** — `PlayerMp4RenderService` (см.
  [external-api-clients.md](../../integration/components/external-api-clients.md))
  использует Playwright для рендера, не melt.

## Известные TODO

- [ ] **Каждый из 41 Mko** — детальный purpose (Pass 343+).
- [ ] **Mlt2 v2 (mko2)** — планируется ли миграция?
- [ ] **150 параметров MltProp** — какие из них используются
      часто, какие устарели.
- [ ] **Тесты на MLT** — есть ли unit-тесты (Pass 343+)?
- [ ] **Сравнение с `rendering/components/mlt-pipeline.md`** — это
      общий overview, не детальный. См.
      [rendering/components/mlt-pipeline.md](../components/mlt-pipeline.md).

## Changelog

- **Pass 366-370** (2026-09-09): Initial detailed. Автор: agent (Karaoke).