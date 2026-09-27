# Mko: Visual Producer'ы (8 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: visual Mko — фоновые/визуальные Producer'ы для видео.


## Ответственность | Responsibility


visual Mko — фоновые/визуальные Producer'ы для видео.

## Назначение

Visual Producer'ы — фоновый слой, watermark, прогресс-бар, flash,
splash, и т.д. Создают **визуальные** элементы (не аудио).

## Интерфейсы и Контракты | Interfaces and Contracts

Все 8 — `data class` с унифицированным конструктором
`(mltProp, type, voiceId, childId, elementId) : MltKaraokeObject`
(см. [mko-producers.md](mko-producers.md)); экземпляры создаются
рефлексией по `producerTypeClass`. Наружу отдают MLT-узлы через
`producer()`, `template()`, `filePlaylist()`, `trackPlaylist()`,
`tractor()` и `mainFilePlaylistTransformProperties()`.

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoBackground.kt` | `BACKGROUND` | Фон видео (заливка цветом или картинкой) |
| 2 | `MkoHorizon.kt` (276) | `HORIZON` | Горизонт — нижняя декоративная полоса |
| 3 | `MkoFlash.kt` (229) | `FLASH` | Flash-вспышки при смене секции |
| 4 | `MkoProgress.kt` (227) | `PROGRESS` | Прогресс-бар (полоса прогресса песни) |
| 5 | `MkoWatermark.kt` | `WATERMARK` | Watermark (логотип) |
| 6 | `MkoSplashStart.kt` (359) | `SPLASHSTART` | Splash-экран в начале видео |
| 7 | `MkoBoosty.kt` | `BOOSTY` | Ссылка на Boosty-пост (если песня оттуда) |
| 8 | `MkoFaderText.kt` | `FADERTEXT` | Текст с плавным появлением/исчезновением |

Контракт по `mlt_service`:

- `MkoBackground` — `producer()` с `mlt_service = qimage` и фильтром
  `lift_gamma_gain`.
- Остальные семь (`Horizon`, `Flash`, `Progress`, `Watermark`,
  `SplashStart`, `Boosty`, `FaderText`) — `mlt_service = kdenlivetitle`
  (`defaultProducerPropertiesForMltService("kdenlivetitle")`) плюс
  `xmldata = template()`.

Все 8 задают `meta.media.width`/`meta.media.height` и навешивают
`filterQtblend` в `filePlaylist()` / `producer()`.

## Логика и Алгоритмы | Logic and Algorithms

Общий конвейер визуального Mko:

1. `producer()` — `<producer>` с длиной (`length`, `kdenlive:duration`) и
   `meta.media.width`/`meta.media.height`; у семи kdenlivetitle-Mko
   свойства берутся из `defaultProducerPropertiesForMltService(...)`
   плюс `xmldata` из `template()` и размеры кадра
   (`frameWidthPx`/`frameHeightPx`); у `MkoBackground` — собственный
   набор свойств (`qimage`, `meta.media.* = 4096`).
2. `template()` — `kdenlivetitle`-шаблон с `item`-элементами
   (`QGraphicsTextItem`/`QGraphicsPixmapItem`); позиции считаются через
   `getTextWidthHeightPx`, шрифты берутся из `Karaoke.*Font`.
3. `filePlaylist()` — playlist + blank-пауза + `entry` с
   `kdenlive:id = filePlaylist<id>` и
   `filterQtblend(nameFilterQtblend, mainFilePlaylistTransformProperties())`.
4. `mainFilePlaylistTransformProperties()` задаёт fade: список
   `TransformProperty` с opacity 0 → 1 → 1 → 0 на границах слоя.
5. `tractor()` — `Horizon`, `Flash`, `Progress`, `Watermark`, `FaderText`
   делегируют в `mltGenerator.tractor()`; `Background`, `SplashStart`,
   `Boosty` — с явными `timecodeIn`/`timecodeOut`.
6. `trackPlaylist()` — общий `mltGenerator.trackPlaylist()`.
7. Краевые случаи: `MkoSplashStart` и `MkoBoosty` живут на собственных
   таймкодах (`splashStartTimecode..splashEndTimecode`,
   `boostyStartTimecode..boostyEndTimecode`), а `MkoBackground` — на
   `totalStartTimecode..totalEndTimecode`, а не на границах песни;
   `MkoBackground` использует `qimage`-фон и `lift_gamma_gain` вместо
   `kdenlivetitle`.

## Зависимости | Dependencies

- [mko-producers.md](mko-producers.md) — общая иерархия.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.

## Известные TODO

- [ ] **Каждый из 8** — детальный state/contracts (Pass 343+).

## Changelog

- **Pass 482** (2026-09-27, spec `482-knowledge-domain-rendering`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 392** (2026-09-09): Initial. Автор: agent (Karaoke).
