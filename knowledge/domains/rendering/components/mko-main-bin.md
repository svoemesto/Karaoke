# Mko: MAINBIN (главный бинарник)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoMainBin` — root MLT-блок.

## Файл

`karaoke-app/.../mlt/mko/MkoMainBin.kt` (310 строк)

## Назначение

**Главный бинарник (MAINBIN)** — root MLT-блок. Содержит timeline,
все audio- и video-треки, скроллеры, чарт-плоты.

В иерархии `ProducerType` (level 0) — от него зависят все
остальные типы.

## State (private)

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
```

## Логика

Создаёт root MLT-узел (`<multitrack>` или `<tractor>`) с:
- Timeline (`<tractor in=... out=...>`).
- Все audio-треки (vocals, accompaniment, mix, source).
- Все video-треки (см. `ProducerType.levels`).

## Связь

- [mlt-generator.md](mlt-generator.md) — общая архитектура.
- [mko-producers.md](mko-producers.md) — иерархия.
- [ProducerType](../../catalog/components/entities-catalog.md#producer) — enum.

## Changelog

- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).