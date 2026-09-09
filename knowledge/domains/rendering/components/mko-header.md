# Mko: HEADER (заголовок видео)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoHeader` — заголовок видео (верхняя панель).

## Файл

`karaoke-app/.../mlt/mko/MkoHeader.kt` (471 строк, **самый большой Mko**)

## Назначение

**Заголовок видео** — верхняя панель с названием песни, автором,
другой метаданными. Виден на всём протяжении видео.

## State

```kotlin
private val frameWidthPx = mltProp.getFrameWidthPx()
private val frameHeightPx = mltProp.getFrameHeightPx()
private val songLengthFr = mltProp.getSongLengthFr()
private val songStartTimecode = mltProp.getSongStartTimecode()
private val songEndTimecode = mltProp.getSongEndTimecode()
// + font/text layout from MltProp
```

## Логика

- Создаёт `<producer>` с `<property name="length">`, `<property name="mlt_service">pango</property>`.
- Layout текста — через `getTextWidthHeightPx` (см.
  [system/utilities.md](../../../system/utilities.md)).
- Font — из `MltProp` (см.
  [karaoke-properties.md](../../processing/components/karaoke-properties.md)).

## Связь

- [mko-producers.md](mko-producers.md) — иерархия.
- [utilities.md](../../../system/utilities.md) — `getTextWidthHeightPx`.

## Changelog

- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).