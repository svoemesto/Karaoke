# Mko: Visual Producer'ы (8 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: visual Mko — фоновые/визуальные Producer'ы для видео.

## Назначение

Visual Producer'ы — фоновый слой, watermark, прогресс-бар, flash,
splash, и т.д. Создают **визуальные** элементы (не аудио).

## Каталог (8 файлов)

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

## Связь

- [mko-producers.md](mko-producers.md) — общая иерархия.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.

## Известные TODO

- [ ] **Каждый из 8** — детальный state/contracts (Pass 343+).

## Changelog

- **Pass 392** (2026-09-09): Initial. Автор: agent (Karaoke).