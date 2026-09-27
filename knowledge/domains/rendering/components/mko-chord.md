# Mko: Chord Producer'ы (10 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: chord-related Mko — доска аккордов, chord-картинки,
> chord-текст.


## Ответственность | Responsibility


chord-related Mko — доска аккордов, chord-картинки, chord-текст.

## Назначение

Producer'ы для отображения **аккордов** на экране — chord-картинки
(гриппы), chord-текст (буквенные обозначения), chord-board (доска).

## Интерфейсы и Контракты | Interfaces and Contracts

Все chord-Mko — `data class` с одинаковой по форме сигнатурой
конструктора `(MltProp, ProducerType, Int, Int, Int)` (рефлексия в
`getMlt`, см. [mlt-karaoke-object.md](mlt-karaoke-object.md)) и
`: MltKaraokeObject`; каждый создаёт свой `val mltGenerator = MltGenerator(...)`:

| Mko | `type` в конструкторе | `MltGenerator(...)` | Переопределено |
|---|---|---|---|
| `MkoBackChords` | обязателен | `(mltProp, type)` | `producer`, `filePlaylist`, `trackPlaylist`, `tractor`, `template`, `mainFilePlaylistTransformProperties` |
| `MkoChordBoard` | обязателен | `(mltProp, type, voiceId)` | `producerBlackTrack`, `filePlaylist`, `trackPlaylist`, `tractor`, `tractorSequence`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureFader` | обязателен | `(mltProp, type)` | `producer`, `filePlaylist`, `trackPlaylist`, `tractor`, `template`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureLines` | `= ProducerType.CHORDPICTURELINES` | `(mltProp, type, voiceId)` | `producerBlackTrack`, `filePlaylist`, `trackPlaylist`, `tractor`, `tractorSequence`, `template`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureLineTrack` | обязателен | `(mltProp, type, voiceId, trackId)` | `filePlaylist`, `trackPlaylist`, `tractor`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureLine` | `= ProducerType.CHORDPICTURELINE` | `(mltProp, type, voiceId, lineId)` | `producerBlackTrack`, `filePlaylist`, `trackPlaylist`, `tractor`, `tractorSequence`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureElement` | `= ProducerType.CHORDPICTUREELEMENT` | `(mltProp, type, voiceId, lineId)` | `producerBlackTrack`, `filePlaylist`, `trackPlaylist`, `tractor`, `tractorSequence`, `mainFilePlaylistTransformProperties` |
| `MkoChordPictureImage` | `= ProducerType.CHORDPICTUREIMAGE` | `(mltProp, type, voiceId, lineId)` | `producer`, `filePlaylist`, `trackPlaylist`, `tractor`, `template`, `mainFilePlaylistTransformProperties` |
| `MkoChords` | `= ProducerType.CHORDS` | `(mltProp, type, voiceId, lineId, elementId)` | `producer`, `filePlaylist`, `trackPlaylist`, `tractor`, `template`, `mainFilePlaylistTransformProperties` |

Детали контракта:

- Четвёртый аргумент конструктора — слот `childId` рефлексии; в коде он
  назван по-разному: `childId` (`MkoBackChords`, `MkoChordBoard`,
  `MkoChordPictureFader`), `lineId` (все `MkoChordPicture*` кроме
  `...LineTrack`, и `MkoChords`), `trackId` (`MkoChordPictureLineTrack`).
- `mainFilePlaylistTransformProperties()` у всех возвращает `""`, кроме
  `MkoChordBoard` — там список `TransformProperty`, соединённый `;`.
- `producerBlackTrack` (там, где переопределён) — цветной producer
  (`mlt_service = color`, `resource = 0`, `kdenlive:playlistid = black_track`),
  а `template()` у `MkoChordPictureImage`, `MkoChords`, `MkoBackChords`,
  `MkoChordPictureFader` даёт `kdenlivetitle`-разметку, которая через
  `String.xmldata()` уходит в `property name="xmldata"`.

## Логика и Алгоритмы | Logic and Algorithms

Цепочка «доска → fader → линии → трек → линия → элемент → картинка»
(см. иерархию ниже) собирается из независимых Producer'ов:

1. **`MkoChordBoard`** — корень цепочки. `producerBlackTrack()` даёт
   чёрную дорожку (`length = 2147483647`, `mlt_service = color`).
   `mainFilePlaylistTransformProperties()` строит 5 точек
   `TransformProperty`: `zero` (y = `-chordHeightPx`), `start`
   (firstChordStartTimecode - 1000 мс), `fadeIn` (firstChordStartTimecode,
   y = 0), `fadeOut` (lastChordEndTimecode), `end`
   (lastChordEndTimecode + 1000 мс, y = `-chordHeightPx`) — доска
   выезжает сверху и уезжает обратно. Границы — `chords.first().syllableStartMs
   + startSilentOffsetMs` и `chords.last().syllableEndMs +
   startSilentOffsetMs`. `tractorSequence()` перечисляет
   `<track producer="tractor_*">` для child-типов
   (`type.childs().asReversed()`, только входящих в
   `songVersion.producers`) плюс дорожку black-track.
2. **`MkoChordPictureFader`** — статичная «шапка» аккордовой области
   (детально — в [mko-chord-picture-fader.md](mko-chord-picture-fader.md)).
3. **`MkoChordPictureLines`** — `tractorSequence()` берёт
   `targetSong.voicesForMlt[0]`, `voice.countChordPictureTracks` и строит
   по `<track producer="tractor_*">` на каждый трек
   (`MltGenerator.nameTractor(CHORDPICTURELINETRACK, voiceId, trackI)`);
   `template()` возвращает пустой `MltNode()`.
4. **`MkoChordPictureLineTrack`** — раскладка по таймлайну:
   `chords.filter { it.chordPictureTrackId == trackId }`, затем для
   каждого аккорда `blank` на паузу
   (`startChordVisibleTime - chordPictureEndMsPrev`) и `entry` с
   `in = songStartTimecode` и `out = getDurationOnScreen(listOf(
   CHORDPICTURELINE, voiceId, chord.chordId))` (если длительность 0 —
   `songEndTimecode`); при непустом `chord.transformChordProperties` в
   `entry` добавляется `filterQtblend`.
5. **`MkoChordPictureLine` / `MkoChordPictureElement`** — `entry` с
   `timecodeOut = lineEndTimecode` (Line) / `chordEndTimecode` (Element) и
   `producerBlackTrack()` для чёрной дорожки; `MkoChordPictureElement`
   дополнительно кладёт в `entry` `filterQtblend` с
   `mainFilePlaylistTransformProperties()` — одна точка `TransformProperty`
   (`y = -(frameHeightPx / 4 + frameHeightPx / 8) + 75`). `tractor(...)`
   ограничен `lineEndTimecode` / `chordEndTimecode`, а `tractorSequence()`
   добавляет под-треки (`MkoChordPictureLine` — один трек
   `CHORDPICTUREELEMENT`; `MkoChordPictureElement` — child-типы
   `CHORDPICTUREELEMENT.childs().asReversed()`).
6. **`MkoChordPictureImage`** — `template()` вызывает
   `generateChordLayout(chord.chord, capo)` и `getChordLayoutPicture(layouts)`,
   пишет PNG в `ByteArrayOutputStream` через `ImageIO.write`, кодирует в
   Base64 и кладёт в `xmldata`; `length` =
   `convertMillisecondsToFrames(chordDurationOnScreen)`.
7. **`MkoChords`** — буквенные аккорды в строке текста: берёт элемент
   `targetSong.voicesForMlt[voiceId].getLines()[lineId].getElements(songVersion)[elementId]`,
   строит `textSyllablesBeforeChord` (предыдущие слоги + слог до первой
   гласной, локальная `String.firstVowelIndex()`), транспонирует текст
   `getTransposingChord(chordElement.chord, capo)`; `chordX` — ширина
   `textSyllablesBeforeChord` в шрифте
   `Karaoke.voices[0].groups[chordElement.groupId].mltText`, шрифт аккорда —
   `Karaoke.chordsFont.copy(chordElementText, chordsFontSize)`, где
   `chordsFontSize = chordElement.fontSize * Karaoke.chordsHeightCoefficient`.
8. **`MkoBackChords`** — фон на всю песню: `producer` с `xmldata =
   template()` и `filterQtblend(mkoBackChordsProducerRect)`; `filePlaylist()`
   начинается с `blank(inOffsetVideo)`.

## Каталог (9 файлов)

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoBackChords.kt` | `BACKCHORDS` | Фоновые аккорды |
| 2 | `MkoChordBoard.kt` (199) | `CHORDSBOARD` | Доска аккордов (гриппы) |
| 3 | `MkoChordPictureFader.kt` (468) | `CHORDPICTUREFADER` | Fader chord-картинок |
| 4 | `MkoChordPictureLines.kt` | `CHORDPICTURELINES` | Список chord-линий |
| 5 | `MkoChordPictureLineTrack.kt` | `CHORDPICTURELINETRACK` | Track для chord-линий |
| 6 | `MkoChordPictureLine.kt` | `CHORDPICTURELINE` | Одна chord-линия |
| 7 | `MkoChordPictureElement.kt` | `CHORDPICTUREELEMENT` | Элемент chord-линии |
| 8 | `MkoChordPictureImage.kt` | `CHORDPICTUREIMAGE` | Картинка аккорда (level 9) |
| 9 | `MkoChords.kt` (211) | `CHORDS` | Аккорды (буквенные обозначения) |

## Иерархия (по [mlt-generator.md](mlt-generator.md))

```
MAINBIN
├── CHORDSBOARD (level 3) — MkoChordBoard
│   └── CHORDPICTUREFADER (level 4) — MkoChordPictureFader
│       └── CHORDPICTURELINES (level 5) — MkoChordPictureLines
│           └── CHORDPICTURELINETRACK (level 6) — MkoChordPictureLineTrack
│               └── CHORDPICTURELINE (level 7) — MkoChordPictureLine
│                   └── CHORDPICTUREELEMENT (level 8) — MkoChordPictureElement
│                       └── CHORDPICTUREIMAGE (level 9) — MkoChordPictureImage
└── BACKCHORDS (level 1) — MkoBackChords
```

Plus **`CHORDS`** (level 6 в VOICE) — MkoChords (буквенные).

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — общая иерархия.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 393** (2026-09-09): Initial. Автор: agent (Karaoke).