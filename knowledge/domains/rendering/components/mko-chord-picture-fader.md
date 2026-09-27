# Mko: CHORDPICTUREFADER (fader для chord-картинок)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoChordPictureFader` — плавное появление chord-картинок.


## Ответственность | Responsibility


`MkoChordPictureFader` — плавное появление chord-картинок.

## Файл

`karaoke-app/.../mlt/mko/MkoChordPictureFader.kt` (468 строк)

## Назначение

**Fader** — плавное появление **chord-картинок** при показе аккордов.
Level 4 в иерархии `CHORDSBOARD` → `CHORDPICTUREFADER`.

## Интерфейсы и Контракты | Interfaces and Contracts

```kotlin
data class MkoChordPictureFader(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)
}
```

Переопределённые методы `MltKaraokeObject`:

| Метод | Контракт |
|---|---|
| `producer(): MltNode` | `<producer>` типа `kdenlivetitle`: база — `mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle")`; `kdenlive:folderid = folderIdVoice`, `length = songLengthFr`, `kdenlive:duration = songEndTimecode`, `xmldata = template().toString().xmldata()`, `meta.media.width = frameWidthPx`, `meta.media.height = fingerboardH + 50` |
| `filePlaylist(): MltNode` | `<playlist>` от `mltGenerator.filePlaylist()` + `entry` с `kdenlive:id = "filePlaylist${mltGenerator.id}"` |
| `template(): MltNode` | узел `kdenlivetitle` — разметка области аккордов (см. «Логика и Алгоритмы») |
| `mainFilePlaylistTransformProperties(): String` | `""` |
| `trackPlaylist(): MltNode` | `mltGenerator.trackPlaylist()` |
| `tractor(): MltNode` | `mltGenerator.tractor()` — `in = mltProp.getTotalStartTimecode()`, `out = mltProp.getBackgroundEndTimecode()` |

Private-состояние из конструктора: `frameWidthPx`, `frameHeightPx`,
`songEndTimecode`, `songLengthFr`, `fingerboardH = mltProp.getFingerboardH(0)`,
`songCapo`, `songChordDescription`, `song = mltProp.getSong()`,
`folderIdVoice = mltProp.getId(listOf(ProducerType.VOICE, voiceId))`.

`producerBlackTrack`, `fileProducer`, `tractorSequence` не переопределены —
дефолтная реализация `null` (см.
[mlt-karaoke-object.md](mlt-karaoke-object.md)).

## Иерархия

```
MAINBIN
└── CHORDSBOARD (level 3)
    └── CHORDPICTUREFADER (level 4) ← этот файл
        └── CHORDPICTURELINES (level 5)
            └── CHORDPICTURELINETRACK (level 6)
                └── CHORDPICTURELINE (level 7)
                    └── CHORDPICTUREELEMENT (level 8)
                        └── CHORDPICTUREIMAGE (level 9)
```

## Логика и Алгоритмы | Logic and Algorithms

`template()` собирает статичную `kdenlivetitle`-картинку области аккордов.
Объём файла (468 строк) — это в основном буквальная сборка XML-элементов:
геометрия и тексты считаются один раз, дальше идут литеральные `MltNode`
`QGraphicsRectItem` / `QGraphicsTextItem`.

1. Геометрия: `w = frameWidthPx / 4`, `h = frameHeightPx / 4 + 75`,
   `x = 0`, `y = 0`, `xRight = frameWidthPx - w`.
2. Шесть `QGraphicsRectItem` (`z-index = 0`) с
   `brushcolor = pencolor = "0,0,0,255"`, `penwidth = 0`:
   - верхняя полоса `rect = 0,0,frameWidthPx,75`;
   - левая панель `rect = 0,0,w,h` (x = 0);
   - панель с `gradient = "#ff000000;#00bf4040;0;100;0"` (x = w);
   - панель с `gradient = "#ff000000;#00bf4040;0;100;180"` (x = `xRight - w`);
   - правая панель (x = `xRight`);
   - нижняя полоса `rect = 0,0,frameWidthPx,50` (y = h) с
     `gradient = "#ff000000;#00bf4040;0;100;90"`.
3. Тексты `QGraphicsTextItem` (блок «темп → тональность → каподастр»
   смещается по y на высоту предыдущей строки через `MltText.h()`):
   - `songChordDescription` шрифтом `Karaoke.chordsCapoFont` (z-index = 6,
     позиция 10/10);
   - `"Темп: ${song!!.bpm} bpm"` — `Karaoke.chordsFont.copy(..., 36)`, y = 100;
   - `"Оригинальная тональность: $originalKey"`, где `originalKey =
     song.key.replace(" minor", "m").replace(" major", "")`;
   - только при `capo > 0`: `"Аккорды для тональности: $newKey"`
     (`newKey = getTransposingChord(originalKey, capo)`) и
     `"Каподастр на $capo-м ладу"` (шрифт 48).
4. Завершение: `startviewport` и `endviewport` с
   `rect = 0,0,frameWidthPx,frameHeightPx`, `background` с
   `color = "0,0,0,0"` (прозрачный фон).
5. Корневой узел: `MltNode(name = "kdenlivetitle", fields = duration="0",
   LC_NUMERIC="C", width/height = frameWidthPx/frameHeightPx, out="0")`.
6. `producer()` превращает разметку в `template().toString().xmldata()`
   (XML-строка с `<?xml version="1.0"?>` и экранированными `<`) и кладёт
   в `property name="xmldata"`; высота producer'а — `fingerboardH + 50`.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — иерархия.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).