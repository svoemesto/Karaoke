# Mko: MAINBIN (главный бинарник)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoMainBin` — root MLT-блок.


## Ответственность | Responsibility


`MkoMainBin` — root MLT-блок.

## Файл

`karaoke-app/.../mlt/mko/MkoMainBin.kt` (310 строк)

## Назначение

**Главный бинарник (MAINBIN)** — root MLT-блок. Содержит timeline,
все audio- и video-треки, скроллеры, чарт-плоты.

В иерархии `ProducerType` (level 0) — от него зависят все
остальные типы.

## Интерфейсы и Контракты | Interfaces and Contracts

`MkoMainBin` — `data class`, реализующий `MltKaraokeObject`; создаётся
рефлексией по унифицированному конструктору (см.
[mko-producers.md](mko-producers.md)).

**Конструктор** (одинаковый у всех Mko):

```kotlin
MkoMainBin(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject
```

**Переопределённые методы `MltKaraokeObject`**:

| Метод | Возврат | Что отдаёт |
|---|---|---|
| `producerBlackTrack()` | `MltNode` | `<producer id=nameProducerBlackTrack(type)>`: `in/out = timelineStartTimecode/timelineEndTimecode`, `length = 2147483647`, `eof = pause`, `resource = black`, `aspect_ratio = 1`, `mlt_service = color`, `kdenlive:duration = totalEndTimecode`, `mlt_image_format = rgba`, `kdenlive:playlistid = black_track` |
| `trackPlaylist()` | `MltNode` | `<playlist id="main_bin">`: `kdenlive:folder.*` на каждый голос (`VOICE`, `COUNTERS`, `LINES`, `STRING`, `LINE`, `FILL`, `ELEMENT`, `CHORDS`, `CHORDPICTURELINES/IMAGE/ELEMENT/LINE`), `kdenlive:docproperties.*` и по `<entry>` на каждую структуру из `getMisList(mltProp)`: sequence-типы ссылаются на `{uuid}`, остальные — на `MltGenerator.nameProducer(...)`; `out = totalEndTimecode` только для MAINBIN, иначе `songEndTimecode` |
| `mainFilePlaylistTransformProperties()` | `String` | пустая строка (`""`) |
| `tractor()` | `MltNode` | `<tractor id="tractor_project">` (`in = 00:00:00.000`, `out = totalEndTimecode`) с одним `<track producer="{$mainBinUUID}">` |
| `tractorSequence()` | `MltNode` | `mltGenerator.tractor(id = "{$mainBinUUID}", timelineStartTimecode..timelineEndTimecode)`: `kdenlive:producer_type = 17`, `kdenlive:sequenceproperties.tracks(-Count) = countAllTracks`, `kdenlive:docproperties.renderurl = songOutputFileName`; внутри — black-track и по `<track producer = nameTractor(it, voiceId)>` на каждый `songVersion.producersInMainBin`, затем `transitionsAndFilters(name, countAudioTracks, countAllTracks - countAudioTracks)` |

`producer()`, `fileProducer()` и `filePlaylist()` не переопределяются —
берут `null`-дефолт интерфейса.

### Состояние (private)

```kotlin
private val timelineStartTimecode = mltProp.getTimelineStartTimecode()
private val timelineEndTimecode = mltProp.getTimelineEndTimecode()
private val totalEndTimecode = mltProp.getBackgroundEndTimecode()
private val songEndTimecode = mltProp.getSongEndTimecode()
private val countAllTracks = mltProp.getCountAllTracks()
private val countAudioTracks = mltProp.getCountAudioTracks()
private val songRootFolder = mltProp.getRootFolder("Song")
private var mainBinUUID = mltProp.getUUID(listOf(ProducerType.MAINBIN))
private var songOutputFileName = mltProp.getFileName(SongOutputFile.VIDEO)
private var songVersion = mltProp.getSongVersion()
```

## Логика и Алгоритмы | Logic and Algorithms

1. Конструктор читает таймкоды (`getTimelineStartTimecode`,
   `getTimelineEndTimecode`, `getBackgroundEndTimecode`, `getSongEndTimecode`),
   счётчики треков (`getCountAllTracks`, `getCountAudioTracks`), корневую
   папку `getRootFolder("Song")`, UUID `getUUID(listOf(ProducerType.MAINBIN))`,
   имя выходного файла `getFileName(SongOutputFile.VIDEO)` и `songVersion`.
2. `producerBlackTrack()` создаёт чёрный фон timeline
   (`resource = black`, `mlt_service = color`, длина `2147483647`) —
   нулевой слой.
3. `trackPlaylist()` строит `main_bin`: сначала folder-свойства по голосам
   (`for (voiceI in 0 until mltProp.getCountVoices())`), затем обходит
   `getMisList(mltProp)` и на каждую структуру создаёт `<entry>`.
   UUID берётся из `MltProp` по ключу
   `listOf(type[, voiceId[, childId[, elementId]]])`; для sequence-типа
   с пустым UUID в stdout печатается `ПУСТОЙ UUID для ...`.
4. `tractorSequence()` собирает основной клип: black-track + по треку на
   каждый producer из `songVersion.producersInMainBin` + transitions/filters
   (audio — `countAudioTracks`, video — `countAllTracks - countAudioTracks`).
5. `tractor()` оборачивает `main_bin` в проектный tractor
   (`kdenlive:projectTractor = 1`).
6. Краевые случаи: не-sequence типы ссылаются на producer по имени
   (`nameProducer`), sequence — по UUID; `out` entry зависит от типа
   (MAINBIN → `totalEndTimecode`, остальные → `songEndTimecode`).

## Зависимости | Dependencies

- [mlt-generator.md](mlt-generator.md) — общая архитектура.
- [mko-producers.md](mko-producers.md) — иерархия.
- [ProducerType](../../catalog/components/entities-catalog.md#producer) — enum
  (`level`, `isSequence`, `onlyOne`, `childs()`).
- `Mlt.getMisList` (`mlt/Mlt.kt`) — список `MltInitialStructure`.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).
