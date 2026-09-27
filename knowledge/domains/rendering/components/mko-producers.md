# Mko Producers (каталог всех 41 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: каталог всех 41 Mko-файлов (конкретные Producer'ы
> для MLT-генерации).


## Ответственность | Responsibility


каталог всех 41 Mko-файлов (конкретные Producer'ы для MLT-генерации).

## Назначение

`karaoke-app/.../mlt/mko/Mko*.kt` — **40 Mko-классов** (41 `.kt`-файл
вместе с интерфейсом `MltKaraokeObject`) — конкретные Producer'ы для
MLT-генерации (см. [mlt-generator.md](mlt-generator.md)). Каждый
Mko соответствует одному или нескольким `ProducerType` (см.
[entities-catalog.md#producer](../../catalog/components/entities-catalog.md#producer)).

## Интерфейсы и Контракты | Interfaces and Contracts

### Унифицированный конструктор

Каждый Mko — `data class` с одинаковой сигнатурой:

```kotlin
data class Mko<Name>(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject
```

Одинаковое количество, тип и порядок аргументов **обязательны** — по ним
Mko создаётся рефлексией в `Mlt.getMlt` (см. `Mlt.kt`):

> Все классы продюссеров должны иметь конструкторы с одинаковым
> количеством, типом и последовательностью аргументов, чтобы их
> можно было вызвать с помощью рефлексии.

### Интерфейс `MltKaraokeObject`

`mlt/mko/MltKaraokeObject.kt` — методы с `null`-дефолтами; конкретный Mko
переопределяет только нужные:

| Метод | Что возвращает |
|---|---|
| `producer()` | `<producer>` слоя |
| `producerBlackTrack()` | black-track producer (использует `MkoMainBin`) |
| `fileProducer()` | producer для file-playlist |
| `filePlaylist()` | playlist слоя |
| `trackPlaylist()` | track-playlist (`main_bin`) |
| `tractor()` | tractor слоя |
| `tractorSequence()` | sequence-tractor (использует `MkoMainBin`) |
| `template()` | `kdenlivetitle`-шаблон (`xmldata`) |
| `mainFilePlaylistTransformProperties()` | строка `TransformProperty` (opacity/fade); без дефолта — обязателен |

### Регистрация типов

`producerTypeClass` (`Constants.kt`) — map `ProducerType -> KClass<Mko>`
на **44 записи**; несколько audio-типов (`AUDIOVOCAL`, `AUDIOMUSIC`,
`AUDIOSONG`, `AUDIOBASS`, `AUDIODRUMS`) указывают на один `MkoAudio`,
поэтому классов меньше, чем записей.

### Каталог (по размеру, от большего к меньшему)

| # | Файл | Строк | ProducerType | Назначение |
|---|---|---|---|---|
| 1 | `MkoHeader.kt` | 471 | `HEADER` | Заголовок видео (верхняя панель с названием, автором) |
| 2 | `MkoChordPictureFader.kt` | 468 | `CHORDPICTUREFADER` | Fader для chord-картинок (плавное появление) |
| 3 | `MkoSplashStart.kt` | 359 | `SPLASHSTART` | Splash-экран в начале (логотип + "Karaoke by svoemesto") |
| 4 | `MkoMelodyTabs.kt` | 313 | `MELODYTABS` | Мелодия + табы (гитара, текст табулатуры) |
| 5 | `MkoMainBin.kt` | 310 | `MAINBIN` | Главный бинарник (root MLT-блок) |
| 6 | `MkoHorizon.kt` | 276 | `HORIZON` | Горизонт (фон видео) |
| 7 | `MkoMelodyNote.kt` | 245 | `MELODYNOTE` | Мелодия (ноты) |
| 8 | `MkoFlash.kt` | 229 | `FLASH` | Flash-эффекты (вспышки при смене секции) |
| 9 | `MkoProgress.kt` | 227 | `PROGRESS` | Прогресс-бар (полоса прогресса песни) |
| 10 | `MkoScroller.kt` | 221 | `SCROLLER` | Скроллер текста (бегущая строка) |
| 11 | `MkoChords.kt` | 211 | `CHORDS` | Аккорды (буквенные обозначения) |
| 12 | `MkoSepar.kt` | 206 | `SEPAR` | Разделитель (вертикальная линия) |
| 13 | `MkoChordBoard.kt` | 199 | `CHORDSBOARD` | Доска аккордов (гриппы) |
| 14 | `MkoString.kt` | 192 | `STRING` | Струна (визуализация) |
| 15 | ... ещё 27 файлов (каждый < 200 строк) | | | См. `ls karaoke-app/.../mlt/mko/` |

## Логика и Алгоритмы | Logic and Algorithms

### Поток создания и связывания

1. `getMisList(mltProp)` (`Mlt.kt`) формирует список `MltInitialStructure`
   по песне: голоса (`voice.linesForMlt()`), строки, элементы; UUID каждой
   структуры хранится в `MltProp` (ключ — `listOf(type, voiceId, childId,
   elementId)`), затем в конец списка добавляется `ProducerType.MAINBIN`.
2. `Mlt.getMlt` для каждой структуры берёт класс из `producerTypeClass`
   и вызывает
   `getDeclaredConstructor(MltProp, ProducerType, Int, Int, Int).newInstance(mltProp, type, voiceId, childId, elementId)`.
3. По рефлексии дёргаются `producer`, `producerBlackTrack`, `fileProducer`,
   `filePlaylist`, `trackPlaylist`, `tractor`, `tractorSequence`;
   непустые узлы (`producer*` → `bodyProducers`, playlist/tractor →
   `bodyOthers`) добавляются в тело `<mlt producer="main_bin">`.
4. `getMltProfile()` и `getMltConsumer(mltProp)` добавляются в начало тела;
   итоговый корневой узел — `<mlt version="7.21.0">`.

### Иерархия ProducerType ↔ Mko

```
MAINBIN (level 0) — MkoMainBin
├── AUDIO* (5 типов, level 1) — MkoAudio
├── VIDEO* (background, horizon, flash, progress, и т.д.) — MkoBackground, MkoHorizon, ...
├── VOICES (1) → VOICE (2) — MkoVoices, MkoVoice
│   ├── COUNTERS (3) → COUNTER (4) — MkoCounters, MkoCounter
│   ├── SCROLLERS (3) → SCROLLERTRACK (4) → SCROLLER (5) — MkoScroller, MkoScrollerTrack
│   ├── LINES (3) → LINE (4) → ELEMENT (5) → STRING (6) — MkoLines, MkoLine, MkoElement, MkoString
│   │   └── SEPAR (6) — MkoSepar
│   │   └── MELODYNOTE (6) → MELODYTABS (7) — MkoMelodyNote, MkoMelodyTabs
│   │   └── CHORDS (6) — MkoChords
│   │   └── FILL (6) — MkoFill
│   └── FILLCOLORSONGTEXTS (3) → FILLCOLORSONGTEXT (4) — MkoFillColorSongTexts
├── SONGTEXT (3) — MkoSongText
└── CHORDSBOARD (3) → CHORDPICTUREFADER (4) → ... → CHORDPICTUREIMAGE (9)
```

Plus отдельные:
- `MkoHeader`, `MkoWatermark`, `MkoSplashStart`, `MkoProgress`,
  `MkoFlash`, `MkoBackground`, `MkoHorizon`, `MkoBoosty`.

### Связывание через `ProducerType`

- `MAINBIN` — корень (`level = 0`, `onlyOne = true`, `isSequence = true`);
  его `trackPlaylist()` строит `main_bin` со всеми `<entry>`, а
  `tractorSequence()` — основной клип (см. [mko-main-bin.md](mko-main-bin.md)).
- Родитель/дети задаются полями enum: `parent`, `level`, `isSequence`,
  `onlyOne`; `ProducerType.childs()` возвращает типы с
  `parent == this`.
- Sequence-типы связываются по UUID (`{uuid}`), не-sequence — по имени
  producer'а (`MltGenerator.nameProducer`).

### Hot paths

- **MLT generation** — для каждой песни при `RENDER_MP4_*`
  (см. [async-process-queue.md](../../processing/components/async-process-queue.md)).
- Mko создаются через reflection для каждого `ProducerType` в
  иерархии.

## Известные TODO

- [ ] **Каждый из 41 Mko** — детальный purpose (Pass 343+).
- [ ] **`MkoChordPictureFader`** (468 строк) — почему самый большой?
- [ ] **`producerTypeClass`** в `Mlt.kt` — как reflection находит Mko.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).
