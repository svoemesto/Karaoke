# Mko: HEADER (заголовок видео)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoHeader` — заголовок видео (верхняя панель).


## Ответственность | Responsibility


`MkoHeader` — заголовок видео (верхняя панель).

## Файл

`karaoke-app/.../mlt/mko/MkoHeader.kt` (471 строк, **самый большой Mko**)

## Назначение

**Заголовок видео** — верхняя панель с названием песни, автором,
другой метаданными. Виден на всём протяжении видео.

## Интерфейсы и Контракты | Interfaces and Contracts

`MkoHeader` — `data class`, реализующий `MltKaraokeObject`
(`mlt/mko/MltKaraokeObject.kt`). Экземпляр создаётся рефлексией по
унифицированному конструктору (см. [mko-producers.md](mko-producers.md)).

**Конструктор** (одинаковый у всех Mko):

```kotlin
MkoHeader(
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
| `producer()` | `MltNode` | `<producer id=nameProducer>`; свойства из `defaultProducerPropertiesForMltService("kdenlivetitle")` (`mlt_service = kdenlivetitle`, `eof`, `aspect_ratio`, `kdenlive:clipname`/`clip_type`/`id`), плюс `length = songLengthFr`, `kdenlive:duration = songEndTimecode`, `xmldata = template()`, `meta.media.width/height = frameWidthPx/frameHeightPx`; фильтр `filterQtblend(nameFilterQtblend, mkoHeaderProducerRect)`. Таймкоды — дефолтные (`getSongStartTimecode()`/`getSongEndTimecode()`) |
| `filePlaylist()` | `MltNode` | playlist + `blank(inOffsetVideo)` + `entry` с `kdenlive:id = filePlaylist<id>` и `filterQtblend(nameFilterQtblend, mainFilePlaylistTransformProperties())` |
| `mainFilePlaylistTransformProperties()` | `String` | 4 `TransformProperty` (start, fadeIn, fadeOut, end) с opacity 0/1/1/0 на `songStartTimecode`, `songFadeInTimecode`, `songFadeOutTimecode`, `songEndTimecode` |
| `template()` | `MltNode` | `kdenlivetitle`-шаблон: `QGraphicsTextItem` для song name, author, album, tone, bpm (позиции из `getTextWidthHeightPx`, шрифты `Karaoke.header*Font`); при `Karaoke.createLogotype` — два `QGraphicsPixmapItem` с base64-логотипами `LogoAuthor`/`LogoAlbum` |
| `trackPlaylist()` | `MltNode` | делегирует в `mltGenerator.trackPlaylist()` |
| `tractor()` | `MltNode` | делегирует в `mltGenerator.tractor()` |

`producerBlackTrack()`, `fileProducer()` и `tractorSequence()` не
переопределяются — берут `null`-дефолт интерфейса.

### Состояние (private)

```kotlin
private val frameWidthPx = mltProp.getFrameWidthPx()
private val frameHeightPx = mltProp.getFrameHeightPx()
private val songLengthFr = mltProp.getSongLengthFr()
private val songStartTimecode = mltProp.getSongStartTimecode()
private val songEndTimecode = mltProp.getSongEndTimecode()
private val songFadeInTimecode = mltProp.getSongFadeInTimecode()
private val songFadeOutTimecode = mltProp.getSongFadeOutTimecode()
private val mkoHeaderProducerRect = mltProp.getRect(listOf(type))
private val inOffsetVideo = mltProp.getInOffsetVideo()
private val songVersion = mltProp.getSongVersion()
private val songName = mltProp.getSongName(ProducerType.HEADER)
private val author = mltProp.getAuthor(ProducerType.HEADER)
private val album = mltProp.getAlbum(ProducerType.HEADER)
private val tone = mltProp.getTone(ProducerType.HEADER)
private val year = mltProp.getYear(ProducerType.HEADER)
private val bpm = mltProp.getBpm(ProducerType.HEADER)
private val logoAuthorBase64 = mltProp.getBase64("LogoAuthor")
private val logoAlbumBase64 = mltProp.getBase64("LogoAlbum")
```

## Логика и Алгоритмы | Logic and Algorithms

1. Конструктор читает параметры рендера и песни: размеры кадра
   (`getFrameWidthPx`, `getFrameHeightPx`), `getSongLengthFr`, таймкоды
   (`songStartTimecode`, `songEndTimecode`, `songFadeInTimecode`,
   `songFadeOutTimecode`), прямоугольник producer'а (`getRect(listOf(type))`),
   `getInOffsetVideo`, `getSongVersion`, метаданные заголовка
   (`getSongName`/`getAuthor`/`getAlbum`/`getTone`/`getYear`/`getBpm` с
   `ProducerType.HEADER`) и base64-логотипы `getBase64("LogoAuthor")` /
   `getBase64("LogoAlbum")`.
2. `template()` считает раскладку по ширине/высоте текста
   (`getTextWidthHeightPx`) и собирает `kdenlivetitle`-шаблон; логотипы
   добавляются только при `Karaoke.createLogotype`.
3. `producer()` сериализует шаблон в `xmldata`
   (`template().toString().xmldata()`) и оборачивает в `<producer>`;
   навешивает `filterQtblend` с прямоугольником `mkoHeaderProducerRect`.
4. `filePlaylist()` добавляет blank-паузу `inOffsetVideo` и `entry` с
   fade-параметрами; `trackPlaylist()`/`tractor()` — общие из `MltGenerator`.
5. Краевой случай: при `Karaoke.createLogotype = false` блоки
   `QGraphicsPixmapItem` (логотипы автора/альбома) в шаблон не попадают.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — иерархия.
- [utilities.md](../../../system/utilities.md) — `getTextWidthHeightPx`.
- `MltGenerator` (`mlt/MltGenerator.kt`) — `producer()`, `trackPlaylist()`,
  `tractor()`, `filterQtblend`, `defaultProducerPropertiesForMltService`.
- `MltProp` (`mlt/MltProp.kt`) — параметры рендера и метаданные песни.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).
