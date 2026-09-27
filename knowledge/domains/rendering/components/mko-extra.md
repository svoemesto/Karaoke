# Mko: extra (Boosty, Fingerboard, FaderText)

> **Домен**: [rendering](../domain.md)
> **Компонента**: обзор 3 Mko-файлов (Boosty, Fingerboard, FaderText).


## Ответственность | Responsibility


обзор 3 Mko-файлов (Boosty, Fingerboard, FaderText).

## Интерфейсы и Контракты | Interfaces and Contracts

Три независимых level-1 Producer'а (их `ProducerType.parent` — `MAINBIN`,
`onlyOne = true`, `isSequence = false`), не входящих в иерархию `VOICE`.
Все реализуют `MltKaraokeObject`
([mlt-karaoke-object.md](mlt-karaoke-object.md)) и конструируются рефлексией
с одинаковой сигнатурой
`(mltProp: MltProp, type: ProducerType, voiceId: Int = 0, childId: Int = 0,
elementId: Int = 0)`.

| Класс | ProducerType | Контракт |
|---|---|---|
| `MkoBoosty` | `BOOSTY` | `producer()` $\rightarrow$ `kdenlivetitle` (`length = getBoostyLengthMs()`, `xmldata` из `template()`); `filePlaylist()` $\rightarrow$ blank(`getBoostyBlankTimecode()`) + `<entry>` boostyStart..boostyEnd с qtblend-fade; `tractor()` $\rightarrow$ boostyStart..boostyEnd; `template()` $\rightarrow$ QGraphicsPixmapItem (base64 из `mltProp.getBase64(BOOSTY)`) |
| `MkoFingerboard` | `FINGERBOARD` | `producer()` $\rightarrow$ `kdenlivetitle` (`kdenlive:duration = getSongLengthFr()`) с qtblend-rect в props; `filePlaylist()` $\rightarrow$ blank(`getInOffsetVideo()`) + entry; `template()` $\rightarrow$ чёрный фон + QGraphicsPixmapItem на каждый аккорд |
| `MkoFaderText` | `FADERTEXT` | `producer()` $\rightarrow$ `kdenlivetitle` (`length = getSongLengthFr()`); `filePlaylist()` $\rightarrow$ blank(`getInOffsetVideo()`) + entry с qtblend-fade; `template()` $\rightarrow$ два QGraphicsRectItem с градиентом (верх/низ) |

**Точка подключения.** `Mlt.getMisList` добавляет `BOOSTY`, `FINGERBOARD`
и `FADERTEXT` на верхнем уровне (вне цикла голосов) и только если тип есть
в `songVersion.producers`; имена узлов даёт `MltGenerator`
(`tractor_<name>`, `producer_<name>`, `filter_qtblend_<name>`).

## Логика и Алгоритмы | Logic and Algorithms

**`MkoBoosty` — картинка Boosty-поста с фейдом.**
`template()` собирает `kdenlivetitle` размером кадра с единственным item'ом
`QGraphicsPixmapItem` (`z-index = 6`) в позиции `(0, 0)`; содержимое —
`content/base64` из `mltProp.getBase64(ProducerType.BOOSTY)`.
Видимость анимируется через `filterQtblend` по четырём точкам:
`boostyStartTimecode` (opacity 0) $\rightarrow$ `boostyFadeInTimecode` (1)
$\rightarrow$ `boostyFadeOutTimecode` (1) $\rightarrow$ `boostyEndTimecode` (0).
Перед entry в playlist вставляется `blank(boostyBlankTimecode)` — сдвиг
клипа по таймлайну.

**`MkoFingerboard` — гриф с аппликатурами аккордов.**

1. Геометрия: `chordW = 270`, `fingerboardW = 270 * chords.size`
   (`mltProp.getChords()`), `fingerboardH = 270`; viewport по высоте
   `fingerboardH + 50`.
2. `template()` сначала кладёт чёрный QGraphicsRectItem
   `0,0,fingerboardW,fingerboardH`, затем по каждому аккорду:
   `generateChordLayout(chord.chord, capo)` $\rightarrow$
   `getChordLayoutPicture(layouts)` $\rightarrow$ `ImageIO.write(..., "png")`
   $\rightarrow$ Base64 $\rightarrow$ `QGraphicsPixmapItem` в
   `x = indexChord * chordW`, `y = 0`.
3. `producer()` дополнительно вешает `filterQtblend` с rect'ом из
   `mltProp.getRect(listOf(type, voiceId, childId))` и указывает
   `meta.media.width = fingerboardW`, `meta.media.height = fingerboardH + 50`.

**`MkoFaderText` — градиентные полосы сверху и снизу.**

1. Высота полосы `h = getTextWidthHeightPx("W", fontSize).second * 2`
   (`Karaoke.voices[0].groups[0].mltText.copy("W", fontSize).h()`), ширина —
   `frameWidthPx`.
2. Верхний QGraphicsRectItem ставится в `y = 0`, нижний — в
   `y = frameHeightPx - h`; у обоих градиент
   `#ff000000;#00bf4040;0;100;90` (верх) и `...;100;0;90` (низ).
3. Прозрачность всего слоя анимируется qtblend'ом по границам песни:
   `songStartTimecode` (0) $\rightarrow$ `songFadeInTimecode` (1)
   $\rightarrow$ `songFadeOutTimecode` (1) $\rightarrow$ `songEndTimecode` (0);
   перед entry — `blank(getInOffsetVideo())`.

**Отличие от остальных Mko.** Ни один из трёх классов не реализует
`tractorSequence()`/`producerBlackTrack()`, поэтому в MLT они дают только
`<producer>` и `<playlist>` (плюс `<tractor>` обычного уровня) — без
собственной sequence-обёртки.

## Каталог (3 файла)

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoBoosty.kt` | `BOOSTY` | Ссылка на Boosty-пост (если песня оттуда) |
| 2 | `MkoFingerboard.kt` | `FINGERBOARD` | Гриф гитары (визуализация позиций) |
| 3 | `MkoFaderText.kt` | `FADERTEXT` | Текст с плавным появлением/исчезновением |

## Зависимости | Dependencies

- [mko-visual.md](mko-visual.md) — Background/Horizon/Flash/Progress/
  Watermark/SplashStart/Boosty (visual Mko).
- [mko-chord.md](mko-chord.md) — ChordPictureFader.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-karaoke-object.md](mlt-karaoke-object.md) — интерфейс `MltKaraokeObject`.

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 474** (2026-09-09): Initial. Автор: agent (Karaoke).
