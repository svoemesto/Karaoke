---
status: Active
slug: 265-key-short-display
related:
  - ../../specs/265-key-short-display/spec.md
  - ../../specs/265-key-short-display/plan.md
  - ../../specs/265-key-short-display/tasks.md
  - ../../archive/docs/features/player-transpose.md
---

# 265 — Краткое отображение тональности в онлайн-плеере (LiveDoc)

> Drill-down — [specs/265-key-short-display/spec.md](../../specs/265-key-short-display/spec.md),
> [plan.md](../../specs/265-key-short-display/plan.md),
> [tasks.md](../../specs/265-key-short-display/tasks.md).

## Что делает

В онлайн-плеере (`karaoke-public` + `webvue3`) тональность (`data.key`) на сплэш-экране и в блоке
метаданных хедера отображается в **кратком** формате — согласованно с подписями в меню «Тональность»
(«(+3) Gm» / «Am (+0)»). Раньше на сплэше была длинная форма «Key: «G minor», bpm: 120»,
в header — «Тональность: G minor».

| Вход `data.key` | Краткая форма (на экране) |
|-----------------|---------------------------|
| `"G minor"` | `Gm` |
| `"A major"` | `A` |
| `"F# minor"` | `F#m` |
| `"Bb minor"` | `A#m` (flat→sharp) |
| `"Am"` (уже короткий) | `Am` (идемпотентно) |
| `""` / `null` | (строка скрыта, регрессии нет) |
| `"C maj7"` (нераспознанный) | `C maj7` (fallback на исходное) |

## Где применяется (5 точек, обе копии `KaraokePlayer.js`)

- `karaoke-public/src/player/KaraokePlayer.js` — `_renderSplash` (chord desc), `_renderHeader` (metadata rows).
- `webvue3/src/player/KaraokePlayer.js` — `_renderSplash` online-режим, `_renderSplash` MP4 render-режим
  (для согласованности экспортируемого mp4 с публичной версией), `_renderHeader` (metadata rows).

## Главное решение: новый static `_shortKey(key)`

Helper `KaraokePlayer._shortKey(key)` — однострочная обёртка вокруг существующих
`_parseKey(key)` (existing) и `CHROMATIC[parsed.index] + parsed.suffix`. Идемпотентен, fallback на
исходную строку для нераспознанных форматов (FR-008). НЕ зависит от инстанса (`this`), pure-функция.

Добавлен **идентично в обе копии плеера** рядом с `_parseKey` (инвариант «две копии плеера»,
см. комментарии в коде `KaraokePlayer.js` строки 770+ / 880+).

## Что НЕ затронуто (out of scope)

- **Меню «Тональность»** (родительский пункт + подменю) — использует `_transposeLabel`, уже
  формирует краткую форму «(+N) Cm» / «Am». Без изменений (FR-009).
- **Бейдж transpose** (синий, правый верхний угол) — `_transposeLabel(this._transpose, false)`,
  формат «Am (+3)». Без изменений (FR-009).
- **`_transposeLabel`, `_parseKey`, `CHROMATIC`** — сигнатуры не меняются.
- **`SongEdit.vue:235-243`** (форма редактирования песни) — пользователь вводит `song.key` руками,
  формат остаётся «as is» (out of scope).
- **Backend API / БД** — формат поля `key` в playerdata не меняется (FR-011).

## Проверки (после правок)

- ESLint baseline `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json` — 0 → 0
  (FR-013, §VI FR-007).
- Prettier `--check` оба пакета — ✓.
- Vite `npm run build` оба пакета — ✓.
- Docker `do.sh build_webvue3` + `do.sh build_public` (Pass 245 multi-stage Dockerfile — Vite-build ≠
  Docker-image, обязательная проверка) — ✓ оба образа собраны.
- Manual: 11 сценариев из `specs/265-key-short-display/quickstart.md` (SC-001..SC-008 + FR-012/FR-013).

## Связанные документы

- Спека: [specs/265-key-short-display/spec.md](../../specs/265-key-short-display/spec.md) —
  4 user story (P1, P1, P1, P2), 13 FR, 8 SC, 3 clarifications (per-feature doc sync, lint-baseline,
  out-of-scope для SongEdit.vue).
- Plan: [plan.md](../../specs/265-key-short-display/plan.md) — Technical Context, Constitution Check,
  Project Structure.
- Contract: [contracts/key-display-contract.md](../../specs/265-key-short-display/contracts/key-display-contract.md) —
  таблица input→output, инварианты, 5 потребителей.
- Per-feature документ: [archive/docs/features/player-transpose.md](../../archive/docs/features/player-transpose.md) —
  раздел «Короткое отображение тональности (added 2026-08-30, FR-265)» (FR-012, §VI FR-009).