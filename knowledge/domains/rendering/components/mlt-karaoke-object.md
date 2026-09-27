# Component: MltKaraokeObject (базовый интерфейс)

> **Домен**: [rendering](../domain.md)
> **Компонента**: базовый интерфейс для всех Mko-классов.


## Ответственность | Responsibility


базовый интерфейс для всех Mko-классов.

## Файл

`karaoke-app/.../mlt/mko/MltKaraokeObject.kt`

## Назначение

Базовый интерфейс для **всех 41 Mko-классов**. Каждый Mko реализует
его и возвращает соответствующий `MltNode` (XML-узел).

## Интерфейсы и Контракты | Interfaces and Contracts

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

Контракт:

- Все методы, кроме `mainFilePlaylistTransformProperties()`, объявлены с
  дефолтной реализацией `= null` — конкретный Mko переопределяет только
  нужные части MLT-проекта, остальные возвращают `null` и в документ не
  попадают.
- `mainFilePlaylistTransformProperties(): String` — без дефолта, обязателен
  для каждого Mko: transform-properties главного file-playlist (у аудио —
  строка громкости из `MltProp.getVolume(...)`, у `MkoChordBoard` — список
  `TransformProperty`, соединённый через `;`).
- Все Mko обязаны иметь конструктор одинаковой сигнатуры
  `(MltProp, ProducerType, Int, Int, Int)` — по ней их инстанцирует
  рефлексия в `getMlt` (см. [mlt-generator.md](mlt-generator.md)).

### Что возвращает каждая часть контракта (по KDoc)

Каждый Mko возвращает одну или несколько частей MLT-проекта:

- **`producer`** — `<producer>` блок (визуальный/аудио-источник).
- **`producerBlackTrack`** — `<producer>` для чёрной дорожки.
- **`fileProducer`** — `<producer>` ссылка на файл.
- **`filePlaylist`** — `<playlist>` (точки входа/выхода).
- **`trackPlaylist`** — `<playlist>` для track.
- **`tractor`** — `<tractor>` (главный бинарник).
- **`tractorSequence`** — `<tractor>` для sequence.
- **`template`** — разметка `kdenlivetitle` (или иной узел), которую
  Producer кладёт в `property name="xmldata"`.
- **`mainFilePlaylistTransformProperties`** — transform properties
  string.

`filePlaylist` body:
- blank — начальное положение трека на таймлайне.
- entry — timecodeIn/Out (длительность трека).

## Логика и Алгоритмы | Logic and Algorithms

1. `getMlt(mltProp)` (`Mlt.kt`) получает плоский список
   `MltInitialStructure` из `getMisList(mltProp)`, берёт класс по
   `producerTypeClass[type]` и создаёт инстанс рефлексией через
   конструктор из 5 аргументов `(MltProp, ProducerType, Int, Int, Int)` —
   поэтому сигнатура конструктора у всех Mko одинаковая.
2. У инстанса через `declaredMethods` по имени находятся и вызываются
   `producer()`, `producerBlackTrack()`, `fileProducer()`,
   `filePlaylist()`, `trackPlaylist()`, `tractor()`,
   `tractorSequence()`. Каждый вызов обёрнут в `let { ... as MltNode? }`:
   `null` (в том числе дефолтная реализация) просто не добавляется в
   документ. `template()` рефлексией не вызывается — его вызывает сам
   Producer внутри `producer()`.
3. Результаты раскладываются на две группы: `bodyProducers`
   (`producerBlackTrack`, `producer`, `fileProducer`) и `bodyOthers`
   (`tractorSequence`, `filePlaylist`, `trackPlaylist`, `tractor`);
   сначала в `<mlt>` идут все producers, затем остальные узлы.
4. `template()` используется Producer'ами типа `kdenlivetitle`: его
   `MltNode.toString()` превращается в XML-строку, а `String.xmldata()`
   (`Extentions.kt`) добавляет `<?xml version="1.0"?>` и экранирует `<`;
   результат кладётся в `property name="xmldata"` (например,
   `MkoChordPictureFader.producer()`).
5. `filePlaylist()` собирает таймлайн трека: `blank` задаёт начальное
   положение, `entry` — длительность (`timecodeIn`/`timecodeOut`); пример —
   `MkoAudio.filePlaylist()`: `MltNodeBuilder().blank(voiceBlankTimecode)`
   плюс `mltGenerator.entry(id = mltGenerator.nameFileProducer, ...)`.

## Hot paths

- Каждый Mko создаётся через reflection в `Mlt.kt` (см.
  [mlt-generator.md](mlt-generator.md)).
- Используется для построения `<multitrack>` / `<tractor>`.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — общая иерархия.
- [mlt-generator.md](mlt-generator.md) — `Mlt.kt` вызывает
  MltKaraokeObject.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 395** (2026-09-09): Initial. Автор: agent (Karaoke).