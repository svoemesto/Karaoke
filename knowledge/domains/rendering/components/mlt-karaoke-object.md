# Component: MltKaraokeObject (базовый интерфейс)

> **Домен**: [rendering](../domain.md)
> **Компонента**: базовый интерфейс для всех Mko-классов.

## Файл

`karaoke-app/.../mlt/mko/MltKaraokeObject.kt`

## Назначение

Базовый интерфейс для **всех 41 Mko-классов**. Каждый Mko реализует
его и возвращает соответствующий `MltNode` (XML-узел).

## Интерфейс

```kotlin
interface MltKaraokeObject {
    fun producer(): MltNode? = null
    fun producerBlackTrack(): MltNode? = null
    fun fileProducer(): MltNode? = null
    fun filePlaylist(): MltNode? = null
    fun trackPlaylist(): MltNode? = null
    fun tractor(): MltNode? = null
    fun tractorSequence(): MltNode? = null
    fun template(): MltNode? = null
    fun mainFilePlaylistTransformProperties(): String
}
```

## MLT-структура (по KDoc)

Каждый Mko возвращает одну или несколько частей MLT-проекта:

- **`producer`** — `<producer>` блок (визуальный/аудио-источник).
- **`producerBlackTrack`** — `<producer>` для чёрной дорожки.
- **`fileProducer`** — `<producer>` ссылка на файл.
- **`filePlaylist`** — `<playlist>` (точки входа/выхода).
- **`trackPlaylist`** — `<playlist>` для track.
- **`tractor`** — `<tractor>` (главный бинарник).
- **`tractorSequence`** — `<tractor>` для sequence.
- **`template`** — `<template>` (повторное использование).
- **`mainFilePlaylistTransformProperties`** — transform properties
  string.

`filePlaylist` body:
- blank — начальное положение трека на таймлайне.
- entry — timecodeIn/Out (длительность трека).

## Hot paths

- Каждый Mko создаётся через reflection в `Mlt.kt` (см.
  [mlt-generator.md](mlt-generator.md)).
- Используется для построения `<multitrack>` / `<tractor>`.

## Связь

- [mko-producers.md](mko-producers.md) — общая иерархия.
- [mlt-generator.md](mlt-generator.md) — `Mlt.kt` вызывает
  MltKaraokeObject.

## Changelog

- **Pass 395** (2026-09-09): Initial. Автор: agent (Karaoke).