# Mko: Voice misc (5 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: 5 Mko-файлов voice-иерархии (misc).


## Ответственность | Responsibility


5 Mko-файлов voice-иерархии (misc).

## Интерфейсы и Контракты | Interfaces and Contracts

Все пять классов реализуют `MltKaraokeObject`
([mlt-karaoke-object.md](mlt-karaoke-object.md)) и конструируются рефлексией
с одинаковой сигнатурой
`(mltProp: MltProp, type: ProducerType, voiceId: Int = 0, childId: Int = 0,
elementId: Int = 0)`.

| Класс | ProducerType | Контракт: методы $\rightarrow$ результат |
|---|---|---|
| `MkoVoices` | `VOICES` | `producerBlackTrack` $\rightarrow$ `<producer mlt_service="color">`; `filePlaylist` $\rightarrow$ blank(`inOffsetVideo`) + `<entry>` с qtblend-fade; `mainFilePlaylistTransformProperties` $\rightarrow$ 4 точки `TransformProperty`; `tractorSequence` $\rightarrow$ `<tractor>` с black-track и track'ами `VOICE` по `countVoices` |
| `MkoVoice` | `VOICE` | `producerBlackTrack`; `filePlaylist` $\rightarrow$ `<entry id="{UUID(VOICE,voiceId)}">`; `tractor` $\rightarrow$ songStart..songEnd; `tractorSequence` $\rightarrow$ ровно 3 track'а: `LINES`, `COUNTERS`, `CHORDSBOARD` |
| `MkoFill` | `FILL` | `producer` $\rightarrow$ `mlt_service = "color"`, `resource = Karaoke.voices[0].fill.evenColor.hexRGB()`, `mlt_image_format = "rgb"`; `filePlaylist` $\rightarrow$ entry с qtblend (`distort = 1`); `mainFilePlaylistTransformProperties` $\rightarrow$ transform с сдвигом `y + deltaY` |
| `MkoFillcolorSongtext` | `FILLCOLORSONGTEXT` | `producer` $\rightarrow$ `color`-producer (`length = getSongLengthFr()`); `mainFilePlaylistTransformProperties` $\rightarrow$ `mltProp.getRect(type, voiceId, childId)`; entry с qtblend (`distort = 1`) |
| `MkoFillcolorSongtexts` | `FILLCOLORSONGTEXTS` | `producerBlackTrack`; `filePlaylist` $\rightarrow$ `<entry id="{UUID}">`; `tractorSequence` $\rightarrow$ black-track + 2 track'а `FILLCOLORSONGTEXT` (ids `0,1`) |

Общие имена узлов даёт `MltGenerator`:
`playlist_file_<name>`, `playlist_track_<name>`, `tractor_<name>`,
`producer_black_track_<name>`, `filter_qtblend_<name>`.

## Логика и Алгоритмы | Logic and Algorithms

**`MkoVoices` — фейд всего блока голосов.**
`mainFilePlaylistTransformProperties()` строит четыре `TransformProperty` на
весь кадр (`frameWidthPx` $\times$ `frameHeightPx`):

| Точка | Время | opacity |
|---|---|---|
| `tpStart` | `songStartTimecode + 1000 мс` | 0.0 |
| `tpFadeIn` | `songFadeInTimecode + 1000 мс` | 1.0 |
| `tpFadeOut` | `songFadeOutTimecode - 1000 мс` | 1.0 |
| `tpEnd` | `songEndTimecode - 1000 мс` | 0.0 |

Точки склеиваются через `;` и уходят в `filterQtblend` entry.
`filePlaylist()` перед entry добавляет `blank(inOffsetVideo)`.
`tractorSequence()` выставляет `kdenlive:folderid = -1`,
`kdenlive:sequenceproperties.tracks`/`tracksCount = countVoices` и кладёт
black-track + по track'у на каждый голос
(`MltGenerator.nameTractor(ProducerType.VOICE, it)`).

**`MkoVoice` — обёртка одного голоса.** `producerBlackTrack` покрывает
timeline (`getTimelineStartTimecode`..`getTimelineEndTimecode`),
`tractor` — только песню. `tractorSequence()` жёстко собирает три
подчинённых track'а — `LINES`, `COUNTERS`, `CHORDSBOARD` — и берёт
`kdenlive:folderid = getId(VOICE, voiceId)`; `tracks`/`tracksCount` = 3.

**`MkoFill` — цветной слой с трансформом элемента.**
`mainFilePlaylistTransformProperties()` берёт
`element.transformProperties()` и пересобирает каждую точку с
`y = it.y + deltaY`, где `deltaY` зависит от наличия нот/табов
(`Karaoke.melodyNoteHeight*`, `Karaoke.melodyTabsHeight*`) или аккордов
(`Karaoke.chordsHeight*`). Если свойств нет или элемент не найден —
возвращается `"00:00:00.000=0 0 1 1 0.0"`.

**`MkoFillcolorSongtext` — одна цветная заливка.** Прямоугольник берётся из
`mltProp.getRect(listOf(type, voiceId, childId))` (записывается туда при
предыдущих проходах), producer — `mlt_service = "color"` с ресурсом
`evenColor`. Отличие от `MkoFill`: длительность — вся песня
(`getSongLengthFr`/`getSongEndTimecode`), а не строка.

**`MkoFillcolorSongtexts` — контейнер двух заливок.** По образцу остальных
sequence-типов: black-track, затем track'и `FILLCOLORSONGTEXT` с childId
`0` и `1` (`ProducerType.FILLCOLORSONGTEXT.ids`), `tracks`/`tracksCount` =
`ids.size` (= 2).

**Позиция в общем потоке.** `Mlt.getMisList` добавляет
`FILLCOLORSONGTEXT` (ids `0,1`) и `FILLCOLORSONGTEXTS` внутри цикла голосов
перед `SONGTEXT`, а `VOICES` — вне цикла голосов, после `VOICE`; `FILL`
входит в набор дочерних типов `ELEMENT`
(`ProducerType.ELEMENT.childs()`), если присутствует в
`songVersion.producers`.

## Каталог (5 файлов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoVoices.kt` | 175 | `VOICES` | Контейнер голосов (level 1) |
| 2 | `MkoVoice.kt` | 130 | `VOICE` | Один голос (level 2) — обёртка |
| 3 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |
| 4 | `MkoFillcolorSongtext.kt` | 76 | `FILLCOLORSONGTEXT` | Текст с заливкой цветом (один) |
| 5 | `MkoFillcolorSongtexts.kt` | 131 | `FILLCOLORSONGTEXTS` | Контейнер |

## Иерархия

```
MAINBIN
└── VOICES (1) — MkoVoices
    └── VOICE (2) — MkoVoice
        ├── COUNTERS → COUNTER (см. mko-voice-small.md)
        ├── SCROLLERS → ... (см. mko-voice-small.md)
        ├── LINES → ... (см. mko-voice-small.md)
        ├── SONGTEXT (см. mko-voice-small.md)
        └── FILLCOLORSONGTEXTS (3) — MkoFillcolorSongtexts
            └── FILLCOLORSONGTEXT (4) — MkoFillcolorSongtext
```

`FILL` — внутри `ELEMENT` (level 7) — пустой слой.

## Зависимости | Dependencies

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-voice-small.md](mko-voice-small.md) — Counter/Scroller/Line/etc.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-karaoke-object.md](mlt-karaoke-object.md) — интерфейс `MltKaraokeObject`.

## Известные TODO

- [ ] **`MkoVoices`** — почему 175 строк (Pass 343+)?
- [ ] **`MkoFill`** — зачем нужен (Pass 343+)?

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 433-435** (2026-09-09): Initial. Автор: agent (Karaoke).
