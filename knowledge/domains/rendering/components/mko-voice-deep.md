# Mko: voice/line deep (10 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: детальный обзор 10 Mko-файлов под VOICE/COUNTER/LINE.

## Каталог (10 файлов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoCounter.kt` | 118 | `COUNTER` | Счётчик (1..5) внутри counter-container |
| 2 | `MkoCounters.kt` | 158 | `COUNTERS` | Контейнер счётчиков |
| 3 | `MkoScroller.kt` | 221 | `SCROLLER` | Скроллер текста (бегущая строка) |
| 4 | `MkoScrollers.kt` | 125 | `SCROLLERS` | Контейнер скроллеров |
| 5 | `MkoScrollerTrack.kt` | 82 | `SCROLLERTRACK` | Track скроллера |
| 6 | `MkoLine.kt` | 141 | `LINE` | Линия текста |
| 7 | `MkoLines.kt` | 144 | `LINES` | Контейнер линий |
| 8 | `MkoLineTrack.kt` | 109 | `LINETRACK` | Track линий |
| 9 | `MkoElement.kt` | 172 | `ELEMENT` | Элемент (один слог) |
| 10 | `MkoString.kt` | 192 | `STRING` | Струна (визуализация) |
| 11 | `MkoSepar.kt` | 206 | `SEPAR` | Разделитель (вертикальная линия) |
| 12 | `MkoSongText.kt` | 147 | `SONGTEXT` | Текст песни (упрощённый) |
| 13 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |

## Иерархия

```
VOICES → VOICE →
├── COUNTERS → COUNTER (level 3-4) — MkoCounters, MkoCounter
├── SCROLLERS → SCROLLERTRACK → SCROLLER (level 3-5) — MkoScrollers, MkoScrollerTrack, MkoScroller
├── LINES → LINETRACK → LINE → ELEMENT (level 3-6) — MkoLines, MkoLineTrack, MkoLine, MkoElement
│   ELEMENT →
│   ├── STRING (level 7) — MkoString
│   ├── SEPAR (level 7) — MkoSepar
│   ├── MELODYNOTE → MELODYTABS (level 7-8) — MkoMelodyNote, MkoMelodyTabs
│   ├── CHORDS (level 7) — MkoChords
│   └── FILL (level 7) — MkoFill
├── SONGTEXT (level 3) — MkoSongText
└── FILLCOLORSONGTEXTS → FILLCOLORSONGTEXT (level 3-4) — MkoFillcolorSongtext, MkoFillcolorSongtexts
```

## Связь

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-voice-small.md](mko-voice-small.md) — Counter/Scroller/Line/etc.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.
- [mlt-generator.md](mlt-generator.md) — общая архитектура.

## Известные TODO

- [ ] **Каждый из 13** — детальный contracts (Pass 343+).

## Changelog

- **Pass 471-473** (2026-09-09): Initial. Автор: agent (Karaoke).