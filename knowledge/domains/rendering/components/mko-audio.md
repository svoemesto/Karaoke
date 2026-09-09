# Mko: AUDIO* (аудио Producer'ы)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoAudio` — Producer для аудио-слоёв в karaoke-видео.

## Файл

`karaoke-app/.../mlt/mko/MkoAudio.kt`

## Назначение

Producer для **аудио-слоя** (vocals / accompaniment / mix / source) в
karaoke-видео. Создаёт MLT-блоки для загрузки FLAC-стемов и их
синхронизации с таймлайном.

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

## Логика

- `mltProp.getPath(listOf(type))` — путь к FLAC-файлу.
- `mltProp.getVolume(listOf(type))` — громкость слоя.
- Создаёт `<producer>` с `<property name="length">` + `<property name="resource">`
  + `<property name="mlt_service">avformat</property>`.

## Hot paths

- **Render MP4** — для каждой песни, для каждой 5 (или 3) аудио-стемы.

## Связь

- [mko-producers.md](mko-producers.md) — иерархия.
- [storage-flow.md](../../storage/components/storage-flow.md) — пути к FLAC в MinIO.

## Changelog

- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).