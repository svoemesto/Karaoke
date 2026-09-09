# Mko: Voice misc (5 файлов)

> **Домен**: [rendering](../domain.md)
> **Компонента**: 5 Mko-файлов voice-иерархии (misc).

## Каталог (5 файлов)

| # | Mko | Строк | ProducerType | Что |
|---|---|---|---|---|
| 1 | `MkoVoices.kt` | 175 | `VOICES` | Контейнер голосов (level 1) |
| 2 | `MkoVoice.kt` | 130 | `VOICE` | Один голос (level 2) — обёртка |
| 3 | `MkoFill.kt` | 167 | `FILL` | Заполнитель (пустой слой) |
| 4 | `MkoFillcolorSongtext.kt` | 76 | `FILLCOLORSONGTEXT` | Текст с заливкой цветом (один) |
| 5 | `MkoFillcolorSongtexts.kt` | 131 | `FILLCOLORSONGTEXTS` | Контейнер |

## Иерархия

```
MAINBIN
└── VOICES (1) — MkoVoices
    └── VOICE (2) — MkoVoice
        ├── COUNTERS → COUNTER (см. mko-voice-small.md)
        ├── SCROLLERS → ... (см. mko-voice-small.md)
        ├── LINES → ... (см. mko-voice-small.md)
        ├── SONGTEXT (см. mko-voice-small.md)
        └── FILLCOLORSONGTEXTS (3) — MkoFillcolorSongtexts
            └── FILLCOLORSONGTEXT (4) — MkoFillcolorSongtext
```

`FILL` — внутри `ELEMENT` (level 7) — пустой слой.

## Связь

- [mko-voice.md](mko-voice.md) — обзор voice-иерархии (Pass 394).
- [mko-voice-small.md](mko-voice-small.md) — Counter/Scroller/Line/etc.
- [mko-producers.md](mko-producers.md) — каталог всех 41 Mko.

## Известные TODO

- [ ] **`MkoVoices`** — почему 175 строк (Pass 343+)?
- [ ] **`MkoFill`** — зачем нужен (Pass 343+)?

## Changelog

- **Pass 433-435** (2026-09-09): Initial. Автор: agent (Karaoke).