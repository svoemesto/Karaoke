# Mko: Voice Producer'ы (18 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: voice/line/scroll/melody Producer'ы — текст песни,
> скроллеры, мелодия, ноты, аккорды (вокал).

## Назначение

Producer'ы для **вокала** — отображение текста песни, скроллеры,
melody-ноты, аккорды. Самая большая группа Mko (18 файлов).

## Каталог (18 файлов)

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoVoice.kt` | `VOICE` | Voice (обёртка) |
| 2 | `MkoVoices.kt` | `VOICES` | Voices (level 1) |
| 3 | `MkoCounter.kt` | `COUNTER` | Счётчик (1..5) |
| 4 | `MkoCounters.kt` | `COUNTERS` | Список счётчиков (level 3) |
| 5 | `MkoScroller.kt` (221) | `SCROLLER` | Скроллер текста (бегущая строка) |
| 6 | `MkoScrollerTrack.kt` | `SCROLLERTRACK` | Track скроллера |
| 7 | `MkoScrollers.kt` | `SCROLLERS` | Список скроллеров |
| 8 | `MkoLine.kt` | `LINE` | Линия текста |
| 9 | `MkoLines.kt` | `LINES` | Список линий |
| 10 | `MkoLineTrack.kt` | `LINETRACK` | Track линий |
| 11 | `MkoElement.kt` | `ELEMENT` | Элемент (слог) |
| 12 | `MkoString.kt` (192) | `STRING` | Струна (визуализация) |
| 13 | `MkoSepar.kt` (206) | `SEPAR` | Разделитель |
| 14 | `MkoMelodyNote.kt` (245) | `MELODYNOTE` | Мелодия (ноты) |
| 15 | `MkoMelodyTabs.kt` (313) | `MELODYTABS` | Мелодия + табы (гитара) |
| 16 | `MkoSongText.kt` | `SONGTEXT` | Текст песни |
| 17 | `MkoFill.kt` | `FILL` | Заполнитель |
| 18 | `MkoFillcolorSongtext.kt` | `FILLCOLORSONGTEXT` | Текст с заливкой цветом |
| 19 | `MkoFillcolorSongtexts.kt` | `FILLCOLORSONGTEXTS` | Список таких текстов |
| 20 | `MltKaraokeObject.kt` | (интерфейс) | Базовый интерфейс для всех Mko |

## Иерархия

```
MAINBIN
└── VOICES (1) — MkoVoices
    └── VOICE (2) — MkoVoice
        ├── COUNTERS (3) — MkoCounters
        │   └── COUNTER (4) — MkoCounter
        ├── SCROLLERS (3) — MkoScrollers
        │   └── SCROLLERTRACK (4) — MkoScrollerTrack
        │       └── SCROLLER (5) — MkoScroller
        ├── LINES (3) — MkoLines
        │   └── LINETRACK (4) — MkoLineTrack
        │       └── LINE (5) — MkoLine
        │           └── ELEMENT (6) — MkoElement
        │               ├── STRING (7) — MkoString
        │               ├── SEPAR (7) — MkoSepar
        │               ├── MELODYNOTE (7) → MELODYTABS (8)
        │               ├── CHORDS (7) — MkoChords (см. mko-chord.md)
        │               └── FILL (7) — MkoFill
        ├── SONGTEXT (3) — MkoSongText
        └── FILLCOLORSONGTEXTS (3) — MkoFillcolorSongtexts
            └── FILLCOLORSONGTEXT (4) — MkoFillcolorSongtext
```

## Hot paths

- **Render MP4** — для каждой песни создаётся ~50-100 Mko-объектов
  этой иерархии.

## Связь

- [mko-producers.md](mko-producers.md) — общая иерархия.

## Changelog

- **Pass 394** (2026-09-09): Initial. Автор: agent (Karaoke).