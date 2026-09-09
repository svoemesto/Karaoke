# Mko: CHORDPICTUREFADER (fader для chord-картинок)

> **Домен**: [rendering](../domain.md)
> **Компонента**: `MkoChordPictureFader` — плавное появление chord-картинок.

## Файл

`karaoke-app/.../mlt/mko/MkoChordPictureFader.kt` (468 строк)

## Назначение

**Fader** — плавное появление **chord-картинок** при показе аккордов.
Level 4 в иерархии `CHORDSBOARD` → `CHORDPICTUREFADER`.

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

## Почему такой большой (468 строк)

- `CHORDPICTUREFADER` — **начало** иерархии chord-картинок.
- Содержит **мастер-тайминг** для всей иерархии ниже.
- Возможно, содержит кеш/оптимизации (Pass 343+ для деталей).

## Связь

- [mko-producers.md](mko-producers.md) — иерархия.

## Changelog

- **Pass 388** (2026-09-09): Initial. Автор: agent (Karaoke).