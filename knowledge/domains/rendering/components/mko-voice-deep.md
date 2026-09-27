# Mko: voice/line deep (13 классов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: детальный обзор 10 Mko-файлов под VOICE/COUNTER/LINE.


## Ответственность | Responsibility


детальный обзор 10 Mko-файлов под VOICE/COUNTER/LINE.

## Интерфейсы и Контракты | Interfaces and Contracts

Общий контракт группы — `MltKaraokeObject` (см.
[mlt-karaoke-object.md](mlt-karaoke-object.md)): `producer()`,
`producerBlackTrack()`, `fileProducer()`, `filePlaylist()`,
`trackPlaylist()`, `tractor()`, `tractorSequence()`, `template()` и
обязательный `mainFilePlaylistTransformProperties(): String`. Классы
конструируются рефлексией, поэтому сигнатура у всех одна:
`(mltProp: MltProp, type: ProducerType, voiceId: Int = 0, childId: Int = 0,
elementId: Int = 0)`.

| Класс | Реализует | Возвращает | Ключевые входы `MltProp` |
|---|---|---|---|
| `MkoCounter` | `producer`, `filePlaylist`, `template`, `tractor`, `trackPlaylist`, `mainFilePlaylistTransformProperties` | `<producer mlt_service="kdenlivetitle">` + `<playlist>`/`<tractor>` из `MltGenerator` | `getSongLengthFr`, `getSongEndTimecode`, `getId(COUNTERS, voiceId)`, `getRect(type, voiceId, childId)`, `getPositionXPx/getPositionYPx`, `getFontSize` |
| `MkoCounters` | `producerBlackTrack`, `filePlaylist`, `tractorSequence`, `template` (пустой `MltNode`) | `<tractor>`-sequence: black-track + 5 track'ов `COUNTER` | `getTimelineStart/EndTimecode`, `getTotalEndTimecode`, `getUUID`, `ProducerType.COUNTER.ids` |
| `MkoScroller` | `producer`, `filePlaylist`, `template`, `tractor`, `mainFilePlaylistTransformProperties` (пустая строка) | `<producer mlt_service="kdenlivetitle">` с `xmldata` из `template()` | `getSongLengthFr`, `getFrameWidthPx/HeightPx`, `getHeightScrollerPx`, `getWidthPxPerMsCoeff`, `getTimeToScrollScreenMs`, `getScrollLines(voiceId)` |
| `MkoScrollers` | `producerBlackTrack`, `filePlaylist`, `tractorSequence`, `template` (пустой `MltNode`) | `<tractor>`-sequence: black-track + track на каждый `SCROLLERTRACK` | `getCountChilds(SCROLLERS, voiceId)`, `getBackgroundEndTimecode` |
| `MkoScrollerTrack` | `filePlaylist`, `tractor`, `mainFilePlaylistTransformProperties` (пустая строка) | `<playlist>`: blank-паузы + `<entry>` на `producer_SCROLLER` | `getScrollTrack(listOf(voiceId, childId))`, `getRect(SCROLLER, voiceId, indexLine)`, `getSongStartTimecode` |
| `MkoLine` | `producerBlackTrack`, `filePlaylist`, `tractor`, `tractorSequence` | black-track (length в кадрах) + `<tractor>`-sequence с одним track'ом `ELEMENT` | `getDurationOnScreen(LINE, voiceId, lineId)`, `getUUID`, `getId(LINE, voiceId)` |
| `MkoLines` | `producerBlackTrack`, `filePlaylist`, `tractor`, `tractorSequence`, `template` (пустой) | `<tractor>`-sequence: black-track + `LINETRACK` по `countLineTracks` | `getSong()`, `getSongStart/EndTimecode` |
| `MkoLineTrack` | `filePlaylist`, `tractor`, `mainFilePlaylistTransformProperties` (пустая строка) | `<playlist>`: строки своего `trackId` с blank-паузами | `getSong().voicesForMlt[voiceId].linesForMlt()`, `getDurationOnScreen(LINE, ...)` |
| `MkoElement` | `producerBlackTrack`, `filePlaylist`, `tractor`, `tractorSequence` | `<tractor>`-sequence: sub-track'и дочерних типов `ELEMENT` из `songVersion.producers` | `getSongVersion()`, `getDurationOnScreen(LINE, ...)`, `getFrameWidthPx/HeightPx` |
| `MkoString` | `producer`, `filePlaylist`, `template`, `tractor`, `mainFilePlaylistTransformProperties` (пустая строка) | `<producer mlt_service="kdenlivetitle">`; `template()` — QGraphicsTextItem слога | `getSongVersion()`, `getDurationOnScreen(LINE, ...)`, `getId(STRING, voiceId)` |
| `MkoSepar` | `producer`, `filePlaylist`, `template`, `tractor`, `mainFilePlaylistTransformProperties` (пустая строка) | `<producer mlt_service="kdenlivetitle">`; `template()` — QGraphicsRectItem на каждый rect | `getSongVersion()`, `element.transformProperties().asRects()`, `Karaoke.separLineColor` |
| `MkoSongText` | `producer`, `filePlaylist`, `template`, `tractor`, `mainFilePlaylistTransformProperties` | `<producer mlt_service="kdenlivetitle">`; `template()` — символы всех строк | `getVoicelines(SONGTEXT, voiceId)`, `getSongCapo`, `getIgnoreCapo`, `getWorkAreaHeightPx` |
| `MkoFill` | `producer`, `filePlaylist`, `mainFilePlaylistTransformProperties`, `tractor` | `<producer mlt_service="color" mlt_image_format="rgb">` + `<playlist>` с qtblend (`distort = 1`) | `getSongVersion()`, `getId(FILL, voiceId)`, `Karaoke.voices[0].fill.evenColor` |

## Логика и Алгоритмы | Logic and Algorithms

**`MkoCounter` / `MkoCounters` (счётчики 1..5).**
`MkoCounter.template()` копирует `MltText` первого голоса
(`Karaoke.voices[0].groups[0].mltText.copy(childId.toString(), fontSize)`),
красит в `Karaoke.countersColors[childId]` и кладёт QGraphicsTextItem в
позицию `(mkoCounterPositionXPx, mkoCounterPositionYPx)`.
`MkoCounters.tractorSequence()` выставляет
`kdenlive:sequenceproperties.tracks`/`tracksCount` в
`ProducerType.COUNTER.ids.size` (= 5) и добавляет track'и в порядке
`4,3,2,1,0` — сверху вниз.

**`MkoScroller` (бегущая строка).**

1. Берёт `initFont` из `getScrollLines(voiceId)[childId].mltText.font` и
   подбирает `fontSize`: цикл `while (getTextWidthHeightPx("0", Font(MAIN_FONT_NAME, style, fontSize)).second < heightScrollerPx) fontSize++`,
   затем `fontSize--`.
2. Для каждого `subtitle` считает `xScrollerPx = (startMs - initStartMs) * widthPxPerMsCoeff`
   и `widthScrollerPx = durationMs * widthPxPerMsCoeff`; суммирует
   `widthAreaPx`.
3. Рисует на subtitle два item'а: QGraphicsRectItem (фон, цвет чередуется
   `8,0,255,255` / `234,255,0,255` по чётности индекса) и QGraphicsTextItem
   (текст слога; `&` заменяется на `&amp;amp;`).
4. `startviewport`/`endviewport` = `0,0,widthAreaPx,heightScrollerPx`.
5. Пишет rect-анимацию в `mltProp.setRect(...)`: из
   `frameWidthPx + offsetX` (opacity 1) в `-(frameWidthPx + widthAreaPx + offsetX)`
   (opacity 1) за `scrollLineDurationMs`; `offsetX = Karaoke.songtextStartPositionXpx`.

**`MkoScrollerTrack` (раскладка скроллеров по таймлайну).** Идёт по
`getScrollTrack(listOf(voiceId, childId))`; для каждой строки добавляет
blank на `scrollLineStartMs - scrollLineEndMsPrev` (время «подхода») и
`<entry>` для `producer_SCROLLER` с qtblend-rect из
`getRect(SCROLLER, voiceId, indexLine)`.

**`MkoLine` / `MkoLines` / `MkoLineTrack` (строки текста).**

- `MkoLine` считает `lineEndTimecode` из
  `getDurationOnScreen(LINE, voiceId, lineId)`; при нуле берёт
  `getSongEndTimecode()`. `tractorSequence()` содержит ровно один подчинённый
  track `ELEMENT` (elementId = 0).
- `MkoLines.mainFilePlaylistTransformProperties()` склеивает
  `voice.linesTransformProperties()` через `;`.
- `MkoLineTrack.filePlaylist()` фильтрует
  `line.trackId == trackId && !line.isEmptyLine`; перед каждой строкой
  добавляет blank на `startVisibleTime - lineEndMsPrev` (только если > 0);
  qtblend-фильтр добавляется лишь при непустом `line.transformProperties`,
  иначе остаётся только `kdenlive:id` с хэшем от
  `[LINE.name, voiceId, line.lineId, 0]`.

**`MkoElement` (слог).** `producerBlackTrack` и `tractorSequence` берут
`widthAreaPx/heightAreaPx` из элемента
(`song.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId].w()/h()`),
при исключении остаются размеры кадра. Подчинённые track'и —
`ProducerType.ELEMENT.childs().asReversed().filter { it in songVersion.producers }`,
то есть набор визуальных слоёв слога зависит от версии песни.

**`MkoString` (текст слога со сдвигом).** Находит элемент типа
`TEXT`/`COMMENT`, определяет `haveNotes` (`MELODYNOTE` в
`songVersion.producers` и есть непустые `note`) и `haveChords`; считает
`deltaY`: при нотах — `noteHeight + tabsHeight`, при аккордах — `chordHeight`,
иначе 0 (коэффициенты — `Karaoke.melodyNoteHeight*`,
`Karaoke.melodyTabsHeight*`, `Karaoke.chordsHeight*`). Текст кладётся в
`(0, deltaY)`, вьюпорт — во весь кадр.

**`MkoSepar` (разделитель).** Считает тот же `deltaY` плюс
`heightAreaPx = element.h() + deltaY`; на каждый `rect` из
`element.transformProperties().asRects()` рисует QGraphicsRectItem шириной 2
и высотой `heightAreaPx - offsetYTabsLines`, цвет — `Karaoke.separLineColor.mlt()`.

**`MkoSongText` (основной текст).** Идёт по `voiceLines` и их `symbols`:
для строки типа `CHORDS` текст транспонируется `getTransposingChord(text, capo)`
(кроме `capo == 0` или `getIgnoreCapo()`), иначе обрезается по `|` для
аккордов и экранируется `&`; `x = lineSymbol.xStartPx + Karaoke.songtextStartPositionXpx`,
`y = voiceLineSongtext.yPx`, z-index 1.
`mainFilePlaylistTransformProperties()` собирает `startTp`/`endTp` строк
типа `TEXT` плюс первой и последней строки.

**`MkoFill` (заливка слоя).** `producer()` — `mlt_service = "color"`,
`resource = Karaoke.voices[0].fill.evenColor.hexRGB()`,
`mlt_image_format = "rgb"`. `mainFilePlaylistTransformProperties()`
пересчитывает transform-properties элемента, добавляя `deltaY` к `y`, и
возвращает `"00:00:00.000=0 0 1 1 0.0"`, если свойств нет или элемент не найден.

## Каталог (13 классов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoCounter.kt` | 118 | `COUNTER` | Счётчик (1..5) внутри counter-container |
| 2 | `MkoCounters.kt` | 158 | `COUNTERS` | Контейнер счётчиков |
| 3 | `MkoScroller.kt` | 221 | `SCROLLER` | Скроллер текста (бегущая строка) |
| 4 | `MkoScrollers.kt` | 125 | `SCROLLERS` | Контейнер скроллеров |
| 5 | `MkoScrollerTrack.kt` | 82 | `SCROLLERTRACK` | Track скроллера |
| 6 | `MkoLine.kt` | 141 | `LINE` | Линия текста |
| 7 | `MkoLines.kt` | 144 | `LINES` | Контейнер линий |
| 8 | `MkoLineTrack.kt` | 109 | `LINETRACK` | Track линий |
| 9 | `MkoElement.kt` | 172 | `ELEMENT` | Элемент (один слог) |
| 10 | `MkoString.kt` | 192 | `STRING` | Струна (визуализация) |
| 11 | `MkoSepar.kt` | 206 | `SEPAR` | Разделитель (вертикальная линия) |
| 12 | `MkoSongText.kt` | 147 | `SONGTEXT` | Текст песни (упрощённый) |
| 13 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |

## Иерархия

```
VOICES → VOICE →
├── COUNTERS → COUNTER (level 3-4) — MkoCounters, MkoCounter
├── SCROLLERS → SCROLLERTRACK → SCROLLER (level 3-5) — MkoScrollers, MkoScrollerTrack, MkoScroller
├── LINES → LINETRACK → LINE → ELEMENT (level 3-6) — MkoLines, MkoLineTrack, MkoLine, MkoElement
│   ELEMENT →
│   ├── STRING (level 7) — MkoString
│   ├── SEPAR (level 7) — MkoSepar
│   ├── MELODYNOTE → MELODYTABS (level 7-8) — MkoMelodyNote, MkoMelodyTabs
│   ├── CHORDS (level 7) — MkoChords
│   └── FILL (level 7) — MkoFill
├── SONGTEXT (level 3) — MkoSongText
└── FILLCOLORSONGTEXTS → FILLCOLORSONGTEXT (level 3-4) — MkoFillcolorSongtext, MkoFillcolorSongtexts
```

## Зависимости | Dependencies

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-voice-small.md](mko-voice-small.md) — Counter/Scroller/Line/etc.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.
- [mlt-karaoke-object.md](mlt-karaoke-object.md) — интерфейс `MltKaraokeObject`.

## Известные TODO

- [ ] **Каждый из 13** — детальный contracts (Pass 343+).

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 471-473** (2026-09-09): Initial. Автор: agent (Karaoke).
