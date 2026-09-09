# Mko: Voice/Element subcomponents (13 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: обзор 13 Mko-файлов голосовой иерархии.

## Назначение

Подкомпоненты **VOICE → ELEMENT** (см. [mko-voice.md](mko-voice.md)):
текст, скроллеры, ноты, разделители, аккорды.

## Каталог (13 файлов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoCounter.kt` | 118 | `COUNTER` | Счётчик (1..5) внутри counter-container |
| 2 | `MkoCounters.kt` | 158 | `COUNTERS` | Контейнер счётчиков (level 3) |
| 3 | `MkoScroller.kt` | 221 | `SCROLLER` | Скроллер текста (бегущая строка) |
| 4 | `MkoScrollers.kt` | 125 | `SCROLLERS` | Контейнер скроллеров |
| 5 | `MkoScrollerTrack.kt` | 82 | `SCROLLERTRACK` | Track для скроллера |
| 6 | `MkoLine.kt` | 141 | `LINE` | Линия текста |
| 7 | `MkoLines.kt` | 144 | `LINES` | Контейнер линий |
| 8 | `MkoLineTrack.kt` | 109 | `LINETRACK` | Track линий |
| 9 | `MkoElement.kt` | 172 | `ELEMENT` | Элемент (один слог) |
| 10 | `MkoString.kt` | 192 | `STRING` | Струна (визуализация — горизонтальная линия) |
| 11 | `MkoSepar.kt` | 206 | `SEPAR` | Разделитель (вертикальная линия) |
| 12 | `MkoSongText.kt` | 147 | `SONGTEXT` | Текст песни (упрощённый) |
| 13 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |

## Иерархия (по [mko-voice.md](mko-voice.md))

```
VOICES → VOICE →
├── COUNTERS → COUNTER (MkoCounters, MkoCounter)
├── SCROLLERS → SCROLLERTRACK → SCROLLER (MkoScrollers, MkoScrollerTrack, MkoScroller)
├── LINES → LINETRACK → LINE → ELEMENT → STRING/SEPAR/MELODYNOTE/CHORDS/FILL
│   (MkoLines, MkoLineTrack, MkoLine, MkoElement, MkoString, MkoSepar, MkoChords, MkoFill, MkoMelodyNote, MkoMelodyTabs)
├── SONGTEXT (MkoSongText)
└── FILLCOLORSONGTEXTS → FILLCOLORSONGTEXT (MkoFillcolorSongtext, MkoFillcolorSongtexts)
```

## Связь

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.

## Известные TODO

- [ ] **Каждый из 13** — детальный contracts (Pass 343+).

## Changelog

- **Pass 422-425** (2026-09-09): Initial. Автор: agent (Karaoke).