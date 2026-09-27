# Mko: Voice Producer'ы (18 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: voice/line/scroll/melody Producer'ы — текст песни,
> скроллеры, мелодия, ноты, аккорды (вокал).


## Ответственность | Responsibility


voice/line/scroll/melody Producer'ы — текст песни, скроллеры, мелодия, ноты, аккорды (вокал).

## Назначение

Producer'ы для **вокала** — отображение текста песни, скроллеры,
melody-ноты, аккорды. Самая большая группа Mko (18 файлов).

## Интерфейсы и Контракты | Interfaces and Contracts

Все классы группы — `data class` с **одинаковой сигнатурой** (этого требует
рефлексия в `Mlt.kt`: тип и порядок аргументов должны совпадать):

```kotlin
data class Mko<Name>(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject
```

- **Регистрация** — карта `producerTypeClass` в `Constants.kt`
  (`ProducerType` $\rightarrow$ класс); тип без записи в карте пропускается.
- **`MltKaraokeObject`** — `producer()`, `producerBlackTrack()`,
  `fileProducer()`, `filePlaylist()`, `trackPlaylist()`, `tractor()`,
  `tractorSequence()`, `template()` (по умолчанию `null`) плюс обязательный
  `mainFilePlaylistTransformProperties(): String`.
- **Имена узлов** (`MltGenerator.name`):
  `<type.text.uppercase()>[_V<voiceId>][_C<childId>][_E<elementId>]`;
  производные — `tractor_<name>`, `producer_<name>`,
  `producer_black_track_<name>`, `playlist_file_<name>`,
  `playlist_track_<name>`, фильтр `filter_qtblend_<name>`.

Публичная поверхность по классам (сверено с `karaoke-app/.../mlt/mko/`):

| Класс | ProducerType | Возвращаемые MLT-узлы |
|---|---|---|
| `MkoVoice` | `VOICE` | `producerBlackTrack` (`color`); `tractor` (songStart..songEnd); `tractorSequence` с фиксированными 3 track'ами: `LINES`, `COUNTERS`, `CHORDSBOARD` |
| `MkoVoices` | `VOICES` | `producerBlackTrack`; `filePlaylist` (blank + entry с qtblend-fade); `tractorSequence` — black-track + track на каждый голос (`countVoices`) |
| `MkoCounter` | `COUNTER` | `producer` (`kdenlivetitle`, `xmldata` из `template()`); `template()` — QGraphicsTextItem, цвет `Karaoke.countersColors[childId]` |
| `MkoCounters` | `COUNTERS` | `tractorSequence` — black-track + 5 track'ов `COUNTER` (ids `4,3,2,1,0`) |
| `MkoScroller` | `SCROLLER` | `producer` (`kdenlivetitle`); `template()` — бегущая строка и запись rect-анимации в `mltProp.setRect(...)` |
| `MkoScrollerTrack` | `SCROLLERTRACK` | `filePlaylist` — blank-паузы + entry `producer_SCROLLER` с qtblend-rect |
| `MkoScrollers` | `SCROLLERS` | `tractorSequence` — black-track + track на каждый `SCROLLERTRACK` (`getCountChilds`) |
| `MkoLine` | `LINE` | `producerBlackTrack` (length в кадрах); `tractor`; `tractorSequence` с одним track'ом `ELEMENT` |
| `MkoLines` | `LINES` | `mainFilePlaylistTransformProperties` из `voice.linesTransformProperties()`; `tractorSequence` по `countLineTracks` |
| `MkoLineTrack` | `LINETRACK` | `filePlaylist` — строки с совпадающим `trackId` и `!isEmptyLine` |
| `MkoElement` | `ELEMENT` | `tractorSequence` — sub-track'и `ELEMENT.childs()` в обратном порядке, отфильтрованные по `songVersion.producers` |
| `MkoString` | `STRING` | `producer` (`kdenlivetitle`); `template()` — слог со сдвигом под ноты/табы/аккорды |
| `MkoSepar` | `SEPAR` | `producer` (`kdenlivetitle`); `template()` — QGraphicsRectItem на каждый rect слога |
| `MkoSongText` | `SONGTEXT` | `producer` (`kdenlivetitle` для всех строк); `template()` — символы с транспонированием аккордов |
| `MkoFill` | `FILL` | `producer` (`mlt_service = "color"`, цвет `voices[0].fill.evenColor`); `mainFilePlaylistTransformProperties` со сдвигом по `deltaY` |
| `MkoFillcolorSongtext` | `FILLCOLORSONGTEXT` | `producer` (color, `mlt_image_format = "rgb"`); rect из `mltProp.getRect(...)` |
| `MkoFillcolorSongtexts` | `FILLCOLORSONGTEXTS` | `tractorSequence` — black-track + 2 track'а `FILLCOLORSONGTEXT` (ids `0,1`) |
| `MkoMelodyNote` | `MELODYNOTE` | `producer` (`kdenlivetitle`); `template()` — нота и октава отдельными QGraphicsTextItem |
| `MkoMelodyTabs` | `MELODYTABS` | `producer` (`kdenlivetitle`); `template()` — 6 линий табулатуры, 4 вертикальные и 6 открытых струн |

## Логика и Алгоритмы | Logic and Algorithms

**Точка входа.** `Mlt.getMisList(mltProp)` обходит `song.voicesForMlt` и
`songVersion.producers` и на каждый найденный `ProducerType` создаёт
`MltInitialStructure(type, voiceId, childId, elementId)`.

**Порядок внутри одного голоса** (по `Mlt.kt`):

1. `ELEMENT` — только дочерние типы из `songVersion.producers`
   (`ELEMENT.childs().asReversed()`), затем сам `LINE`.
2. `LINETRACK` — количество `voice.linesForMlt().size`, затем `CHORDSBOARD`
   и `LINES`.
3. `COUNTER` по ids `4,3,2,1,0`, затем контейнер `COUNTERS`.
4. `FILLCOLORSONGTEXT` по ids `0,1`, затем `FILLCOLORSONGTEXTS`.
5. `SONGTEXT`, затем `SCROLLER` (количество из `mltProp.getCountChilds`),
   `SCROLLERTRACK` и контейнер `SCROLLERS`.
6. Последним для голоса — `VOICE`.

**Верхний уровень** (вне цикла голосов): `VOICES`, `BOOSTY`,
`SPLASHSTART`, `WATERMARK`, `HEADER`, `FINGERBOARD`, `FADERTEXT`.

**Сборка MLT.** Для каждой структуры класс берётся из `producerTypeClass`,
конструируется рефлексией с `(mltProp, type, max(voiceId,0), max(childId,0),
max(elementId,0))`; `producer` / `producerBlackTrack` / `fileProducer`
складываются в `bodyProducers`, а `tractorSequence` / `filePlaylist` /
`trackPlaylist` / `tractor` — в `bodyOthers`. Оба списка уходят в корневой
`<mlt version="7.21.0" producer="main_bin">`.

**Общий контракт времени.** Каждый sequence-тип отдаёт
`producerBlackTrack` — `mlt_service = "color"`, `length = 2147483647`,
`eof = "pause"`, `kdenlive:playlistid = "black_track"`; границы клипа —
`timecodeIn`/`timecodeOut` из `MltProp` (`getSongStartTimecode` —
`getSongEndTimecode`, для контейнеров — timeline/total).

**Краевые случаи:**

- Нулевая длительность строки: `MkoLine`, `MkoElement`, `MkoFill` при
  `getDurationOnScreen(...) <= 0` подменяют её на `getSongEndTimecode()`.
- Отсутствующий элемент: `MkoElement`, `MkoString`, `MkoSepar`, `MkoFill`
  ловят исключение и остаются на размер кадра/`default`-transform.
- `MkoScroller.template()` увеличивает `fontSize`, пока высота строки `"0"`
  меньше `heightScrollerPx`, затем делает `fontSize--` (минимум — 1).
- `MkoSongText.template()` транспонирует аккорды через
  `getTransposingChord(text, capo)`, кроме `capo == 0` или `getIgnoreCapo()`.

## Каталог (20 записей: 19 Mko-классов + интерфейс)

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoVoice.kt` | `VOICE` | Voice (обёртка) |
| 2 | `MkoVoices.kt` | `VOICES` | Voices (level 1) |
| 3 | `MkoCounter.kt` | `COUNTER` | Счётчик (1..5) |
| 4 | `MkoCounters.kt` | `COUNTERS` | Список счётчиков (level 3) |
| 5 | `MkoScroller.kt` (221) | `SCROLLER` | Скроллер текста (бегущая строка) |
| 6 | `MkoScrollerTrack.kt` | `SCROLLERTRACK` | Track скроллера |
| 7 | `MkoScrollers.kt` | `SCROLLERS` | Список скроллеров |
| 8 | `MkoLine.kt` | `LINE` | Линия текста |
| 9 | `MkoLines.kt` | `LINES` | Список линий |
| 10 | `MkoLineTrack.kt` | `LINETRACK` | Track линий |
| 11 | `MkoElement.kt` | `ELEMENT` | Элемент (слог) |
| 12 | `MkoString.kt` (192) | `STRING` | Струна (визуализация) |
| 13 | `MkoSepar.kt` (206) | `SEPAR` | Разделитель |
| 14 | `MkoMelodyNote.kt` (245) | `MELODYNOTE` | Мелодия (ноты) |
| 15 | `MkoMelodyTabs.kt` (313) | `MELODYTABS` | Мелодия + табы (гитара) |
| 16 | `MkoSongText.kt` | `SONGTEXT` | Текст песни |
| 17 | `MkoFill.kt` | `FILL` | Заполнитель |
| 18 | `MkoFillcolorSongtext.kt` | `FILLCOLORSONGTEXT` | Текст с заливкой цветом |
| 19 | `MkoFillcolorSongtexts.kt` | `FILLCOLORSONGTEXTS` | Список таких текстов |
| 20 | `MltKaraokeObject.kt` | (интерфейс) | Базовый интерфейс для всех Mko |

## Иерархия

```
MAINBIN
└── VOICES (1) — MkoVoices
    └── VOICE (2) — MkoVoice
        ├── COUNTERS (3) — MkoCounters
        │   └── COUNTER (4) — MkoCounter
        ├── SCROLLERS (3) — MkoScrollers
        │   └── SCROLLERTRACK (4) — MkoScrollerTrack
        │       └── SCROLLER (5) — MkoScroller
        ├── LINES (3) — MkoLines
        │   └── LINETRACK (4) — MkoLineTrack
        │       └── LINE (5) — MkoLine
        │           └── ELEMENT (6) — MkoElement
        │               ├── STRING (7) — MkoString
        │               ├── SEPAR (7) — MkoSepar
        │               ├── MELODYNOTE (7) → MELODYTABS (8)
        │               ├── CHORDS (7) — MkoChords (см. mko-chord.md)
        │               └── FILL (7) — MkoFill
        ├── SONGTEXT (3) — MkoSongText
        └── FILLCOLORSONGTEXTS (3) — MkoFillcolorSongtexts
            └── FILLCOLORSONGTEXT (4) — MkoFillcolorSongtext
```

## Hot paths

- **Render MP4** — для каждой песни создаётся ~50-100 Mko-объектов
  этой иерархии.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — общая иерархия.
- [mlt-generator.md](mlt-generator.md) — `MltGenerator` (имена узлов,
  `entry`/`producer`/`tractor`, `filter_qtblend`).
- [mlt-karaoke-object.md](mlt-karaoke-object.md) — интерфейс
  `MltKaraokeObject`.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 394** (2026-09-09): Initial. Автор: agent (Karaoke).
