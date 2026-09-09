# Mko: Chord Producer'ы (10 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: chord-related Mko — доска аккордов, chord-картинки,
> chord-текст.

## Назначение

Producer'ы для отображения **аккордов** на экране — chord-картинки
(гриппы), chord-текст (буквенные обозначения), chord-board (доска).

## Каталог (10 файлов)

| # | Mko | ProducerType | Что |
|---|---|---|---|
| 1 | `MkoBackChords.kt` | `BACKCHORDS` | Фоновые аккорды |
| 2 | `MkoChordBoard.kt` (199) | `CHORDSBOARD` | Доска аккордов (гриппы) |
| 3 | `MkoChordPictureFader.kt` (468) | `CHORDPICTUREFADER` | Fader chord-картинок |
| 4 | `MkoChordPictureLines.kt` | `CHORDPICTURELINES` | Список chord-линий |
| 5 | `MkoChordPictureLineTrack.kt` | `CHORDPICTURELINETRACK` | Track для chord-линий |
| 6 | `MkoChordPictureLine.kt` | `CHORDPICTURELINE` | Одна chord-линия |
| 7 | `MkoChordPictureElement.kt` | `CHORDPICTUREELEMENT` | Элемент chord-линии |
| 8 | `MkoChordPictureImage.kt` | `CHORDPICTUREIMAGE` | Картинка аккорда (level 9) |
| 9 | `MkoChords.kt` (211) | `CHORDS` | Аккорды (буквенные обозначения) |

## Иерархия (по [mlt-generator.md](mlt-generator.md))

```
MAINBIN
├── CHORDSBOARD (level 3) — MkoChordBoard
│   └── CHORDPICTUREFADER (level 4) — MkoChordPictureFader
│       └── CHORDPICTURELINES (level 5) — MkoChordPictureLines
│           └── CHORDPICTURELINETRACK (level 6) — MkoChordPictureLineTrack
│               └── CHORDPICTURELINE (level 7) — MkoChordPictureLine
│                   └── CHORDPICTUREELEMENT (level 8) — MkoChordPictureElement
│                       └── CHORDPICTUREIMAGE (level 9) — MkoChordPictureImage
└── BACKCHORDS (level 1) — MkoBackChords
```

Plus **`CHORDS`** (level 6 в VOICE) — MkoChords (буквенные).

## Связь

- [mko-producers.md](mko-producers.md) — общая иерархия.

## Changelog

- **Pass 393** (2026-09-09): Initial. Автор: agent (Karaoke).