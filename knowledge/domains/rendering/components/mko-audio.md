# Mko: AUDIO* (аудио Producer'ы)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoAudio` — Producer для аудио-слоёв в karaoke-видео.


## Ответственность | Responsibility


`MkoAudio` — Producer для аудио-слоёв в karaoke-видео.

## Файл

`karaoke-app/.../mlt/mko/MkoAudio.kt`

## Назначение

Producer для **аудио-слоя** (vocals / accompaniment / mix / source) в
karaoke-видео. Создаёт MLT-блоки для загрузки FLAC-стемов и их
синхронизации с таймлайном.

## Интерфейсы и Контракты | Interfaces and Contracts

```kotlin
data class MkoAudio(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)
}
```

Переопределённые методы `MltKaraokeObject` (остальные — дефолт `null`):

| Метод | Контракт |
|---|---|
| `producer(): MltNode` | `<producer>` с `length = audioLengthFr`, `resource = mkoAudioPath`, `eof = pause`, `mlt_service = avformat`, `audio_index = 0`, `video_index = -1`, `kdenlive:clip_type = if (type.isAudio) 1 else 2` |
| `fileProducer(): MltNode` | `<producer id = nameFileProducer>`: `in = songStartTimecode`, `out = audioEndTimecode`, `mlt_service = avformat-novalidate` |
| `filePlaylist(): MltNode` | `<playlist>` от `mltGenerator.filePlaylist()` + `blank(voiceBlankTimecode)` + `entry(id = nameFileProducer)` с `filterVolume(nameFilterVolume, mainFilePlaylistTransformProperties())` |
| `mainFilePlaylistTransformProperties(): String` | возвращает `volume` |
| `trackPlaylist(): MltNode` | делегирует в `mltGenerator.trackPlaylist()` |
| `tractor(): MltNode` | делегирует в `mltGenerator.tractor()` |

Встраивание в MLT-граф: `producer` — сам аудио-источник (`avformat`);
`fileProducer` — облегчённая файловая ссылка (`avformat-novalidate`) для
плейлиста; `filePlaylist`/`trackPlaylist` — дорожки таймлайна; `tractor` —
контейнер дорожки. Так как `type.isAudio == true`, `MltGenerator.tractorBody()`
добавит к tractor фильтры `filter_volume_*`, `filter_panner_*`,
`filter_audiolevel_*`. `producerBlackTrack`, `tractorSequence`, `template`
не переопределены.

## ProducerType

Соответствует 5 типам (см.
[entities-catalog.md#producer](../../catalog/components/entities-catalog.md#producer)):

- `AUDIOVOCAL` — вокал (vocals.flac).
- `AUDIOMUSIC` — минусовка (accompaniment.flac).
- `AUDIOSONG` — микс (mix.flac).
- `AUDIOBASS` — бас (bass.flac, если есть).
- `AUDIODRUMS` — ударные (drums.flac, если есть).

Plus **`SOURCE`** — оригинальный mp3 (для справки, не в видео).

## State (private)

```kotlin
private val audioLengthFr = mltProp.getAudioLengthFr()
private val mkoAudioPath = mltProp.getPath(listOf(type))
private val volume = mltProp.getVolume(listOf(type))
private val songStartTimecode = mltProp.getSongStartTimecode()
```

## Логика и Алгоритмы | Logic and Algorithms

1. В конструкторе создаётся `mltGenerator = MltGenerator(mltProp, type)` и
   вычисляются private-значения (см. раздел `State`): `audioLengthFr =
   mltProp.getAudioLengthFr()`, `mkoAudioPath = mltProp.getPath(listOf(type))`,
   `volume = mltProp.getVolume(listOf(type))`, `songStartTimecode =
   mltProp.getSongStartTimecode()`, `audioEndTimecode =
   mltProp.getAudioEndTimecode()`, `voiceBlankTimecode =
   mltProp.getVoiceBlankTimecode()`.
2. `producer()` строит аудио-источник: `length = audioLengthFr` (длина
   аудио в кадрах), `resource = mkoAudioPath` (FLAC-стем), `mlt_service =
   avformat`, `seekable = 1`, `eof = pause`, `audio_index = 0`,
   `video_index = -1`, `mute_on_pause = 0`, `astream = 0`.
3. `fileProducer()` строит отдельный `<producer>` для файлового слоя:
   `id = mltGenerator.nameFileProducer`, `in = songStartTimecode`,
   `out = audioEndTimecode`, `mlt_service = avformat-novalidate` (без
   полной валидации файла), `kdenlive:clip_type = 1`.
4. `filePlaylist()` берёт `mltGenerator.filePlaylist()` и наполняет тело:
   `blank(voiceBlankTimecode)` (начальное положение трека) и `entry(id =
   nameFileProducer)` с `kdenlive:id`, `kdenlive:activeeffect = 0` и
   `filterVolume(nameFilterVolume, mainFilePlaylistTransformProperties())`.
5. `mainFilePlaylistTransformProperties()` возвращает `volume` — строку
   громкости слоя из `mltProp.getVolume(listOf(type))`; она и есть
   transform-properties для `filterVolume`.
6. `trackPlaylist()` и `tractor()` просто делегируют в `MltGenerator`;
   для аудио (`type.isAudio`) tractor дополнительно получает фильтры
   `filter_volume_*`, `filter_panner_*`, `filter_audiolevel_*`.
7. `producerBlackTrack()`, `tractorSequence()`, `template()` не
   переопределены — возвращают `null` и в MLT-документ не попадают.

## Hot paths

- **Render MP4** — для каждой песни, для каждой 5 (или 3) аудио-стемы.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — иерархия.
- [storage-flow.md](../../storage/components/storage-flow.md) — пути к FLAC в MinIO.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).