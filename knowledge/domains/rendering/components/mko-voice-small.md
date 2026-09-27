# Mko: Voice/Element subcomponents (13 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: обзор 13 Mko-файлов голосовой иерархии.


## Ответственность | Responsibility


обзор 13 Mko-файлов голосовой иерархии.

## Назначение

Подкомпоненты **VOICE → ELEMENT** (см. [mko-voice.md](mko-voice.md)):
текст, скроллеры, ноты, разделители, аккорды.

## Интерфейсы и Контракты | Interfaces and Contracts

Единый контракт — интерфейс `MltKaraokeObject`
([mlt-karaoke-object.md](mlt-karaoke-object.md)):
`producer()`, `producerBlackTrack()`, `fileProducer()`, `filePlaylist()`,
`trackPlaylist()`, `tractor()`, `tractorSequence()`, `template()` (у всех
базовая реализация — `null`) и обязательный
`mainFilePlaylistTransformProperties(): String`. Конструктор у всех классов
одинаковый — рефлексия в `Mlt.kt` вызывает его по позициям:

```kotlin
data class Mko<Name>(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject
```

| Класс | ProducerType | Тип | Что порождает |
|---|---|---|---|
| `MkoCounter` | `COUNTER` | лист | `<producer mlt_service="kdenlivetitle">` (счётчик 1..5, QGraphicsTextItem) |
| `MkoCounters` | `COUNTERS` | контейнер | `<tractor>`-sequence: black-track + track'и `COUNTER` (5 шт.) |
| `MkoScroller` | `SCROLLER` | лист | `<producer mlt_service="kdenlivetitle">` (бегущая строка) |
| `MkoScrollers` | `SCROLLERS` | контейнер | `<tractor>`-sequence: black-track + track'и `SCROLLERTRACK` |
| `MkoScrollerTrack` | `SCROLLERTRACK` | контейнер | `<playlist>`: blank-паузы + `<entry>` `producer_SCROLLER` с qtblend-rect |
| `MkoLine` | `LINE` | контейнер | black-track (length в кадрах) + `<tractor>`-sequence с track'ом `ELEMENT` |
| `MkoLines` | `LINES` | контейнер | `<tractor>`-sequence: black-track + track'и `LINETRACK` |
| `MkoLineTrack` | `LINETRACK` | контейнер | `<playlist>` из строк своего `trackId` |
| `MkoElement` | `ELEMENT` | контейнер | `<tractor>`-sequence: sub-track'и дочерних типов `ELEMENT` из `songVersion.producers` |
| `MkoString` | `STRING` | лист | `<producer mlt_service="kdenlivetitle">` (текст слога со сдвигом под ноты/табы/аккорды) |
| `MkoSepar` | `SEPAR` | лист | `<producer mlt_service="kdenlivetitle">` (QGraphicsRectItem на каждый rect слога) |
| `MkoSongText` | `SONGTEXT` | лист | `<producer mlt_service="kdenlivetitle">` (символы всех строк, транспонирование аккордов) |
| `MkoFill` | `FILL` | лист | `<producer mlt_service="color">` + `<playlist>` с qtblend (`distort = 1`) |

Имена MLT-узлов задаёт `MltGenerator.name`:
`<type.text.uppercase()>[_V<voiceId>][_C<childId>][_E<elementId>]`;
фильтр прозрачности — `filter_qtblend_<name>`.

## Логика и Алгоритмы | Logic and Algorithms

**Порядок обработки внутри голоса.** `Mlt.getMisList(mltProp)` обходит
`song.voicesForMlt` и добавляет структуры в порядке: `ELEMENT` (дочерние
типы из `songVersion.producers`, в обратном порядке) → `LINE` → `LINETRACK`
→ `LINES` → `COUNTER` (ids `4,3,2,1,0`) → `COUNTERS` → `SONGTEXT` →
`SCROLLER` → `SCROLLERTRACK` → `SCROLLERS` → `VOICE`. Затем вне цикла
добавляются `VOICES` и прочие level-1 типы.

**Кто считает детей.** У `SCROLLERTRACK`, `SCROLLER`, `LINETRACK`, `LINE`,
`ELEMENT` в `ProducerType` стоит `isCalculatedCount = true` — количество
берётся из данных песни (`mltProp.getCountChilds(...)`,
`voice.linesForMlt().size`, `element.getElements(songVersion).size`), а не из
`ids`. У `COUNTER` и `FILLCOLORSONGTEXT` список `ids` фиксирован
(`[4,3,2,1,0]` и `[0,1]`).

**Общий каркас.** Каждый sequence-тип отдаёт `producerBlackTrack` —
`mlt_service = "color"`, `kdenlive:playlistid = "black_track"`,
`length = 2147483647`, `eof = "pause"`; клипы — `<entry>` в
`filePlaylist`/`trackPlaylist` (границы из `MltProp`), а `tractorSequence`
выставляет `kdenlive:producer_type = 17` и
`kdenlive:sequenceproperties.tracks`/`tracksCount`.

**Краевые случаи.**

- Нулевая длительность строки (`getDurationOnScreen(...) <= 0`):
  `MkoLine`, `MkoElement`, `MkoFill` подменяют её на `getSongEndTimecode()`.
- Пропавший элемент/строка: `MkoString`, `MkoSepar`, `MkoFill`,
  `MkoElement` ловят исключение и возвращают `MltNode()`, размер кадра или
  `default`-transform `"00:00:00.000=0 0 1 1 0.0"`.
- `MkoScroller` подбирает `fontSize` циклом до превышения
  `heightScrollerPx`, затем уменьшает на 1; rect-анимация пишется в
  `mltProp.setRect(...)` (`type, voiceId, childId`).
- `MkoLineTrack` добавляет blank перед строкой только при
  `blankDurationMs > 0` и qtblend — только при непустом
  `line.transformProperties`.

Детальный разбор по каждому классу — в
[mko-voice-deep.md](mko-voice-deep.md).

## Каталог (13 файлов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoCounter.kt` | 118 | `COUNTER` | Счётчик (1..5) внутри counter-container |
| 2 | `MkoCounters.kt` | 158 | `COUNTERS` | Контейнер счётчиков (level 3) |
| 3 | `MkoScroller.kt` | 221 | `SCROLLER` | Скроллер текста (бегущая строка) |
| 4 | `MkoScrollers.kt` | 125 | `SCROLLERS` | Контейнер скроллеров |
| 5 | `MkoScrollerTrack.kt` | 82 | `SCROLLERTRACK` | Track для скроллера |
| 6 | `MkoLine.kt` | 141 | `LINE` | Линия текста |
| 7 | `MkoLines.kt` | 144 | `LINES` | Контейнер линий |
| 8 | `MkoLineTrack.kt` | 109 | `LINETRACK` | Track линий |
| 9 | `MkoElement.kt` | 172 | `ELEMENT` | Элемент (один слог) |
| 10 | `MkoString.kt` | 192 | `STRING` | Струна (визуализация — горизонтальная линия) |
| 11 | `MkoSepar.kt` | 206 | `SEPAR` | Разделитель (вертикальная линия) |
| 12 | `MkoSongText.kt` | 147 | `SONGTEXT` | Текст песни (упрощённый) |
| 13 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |

## Иерархия (по [mko-voice.md](mko-voice.md))

```
VOICES → VOICE →
├── COUNTERS → COUNTER (MkoCounters, MkoCounter)
├── SCROLLERS → SCROLLERTRACK → SCROLLER (MkoScrollers, MkoScrollerTrack, MkoScroller)
├── LINES → LINETRACK → LINE → ELEMENT → STRING/SEPAR/MELODYNOTE/CHORDS/FILL
│   (MkoLines, MkoLineTrack, MkoLine, MkoElement, MkoString, MkoSepar, MkoChords, MkoFill, MkoMelodyNote, MkoMelodyTabs)
├── SONGTEXT (MkoSongText)
└── FILLCOLORSONGTEXTS → FILLCOLORSONGTEXT (MkoFillcolorSongtext, MkoFillcolorSongtexts)
```

## Зависимости | Dependencies

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-voice-deep.md](mko-voice-deep.md) — детальный разбор классов.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.
- [mlt-karaoke-object.md](mlt-karaoke-object.md) — интерфейс `MltKaraokeObject`.

## Известные TODO

- [ ] **Каждый из 13** — детальный contracts (Pass 343+).

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 422-425** (2026-09-09): Initial. Автор: agent (Karaoke).
