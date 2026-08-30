# Contract: Короткое отображение тональности (UI)

**Feature**: 265-key-short-display
**Date**: 2026-08-30
**Related**: [spec.md](../spec.md), [plan.md](../plan.md), [research.md](../research.md), [data-model.md](../data-model.md)

UI-контракт для helper-а `KaraokePlayer._shortKey(key)` и его потребителей (canvas-рендер в `_renderSplash` / `_renderHeader` обеих копий плеера). Контракт описывает вход/выход, инварианты, edge-cases и привязку к FR/SC спеки.

---

## API: `KaraokePlayer._shortKey(key)`

### Сигнатура

```javascript
/**
 * Краткое представление тональности: «G minor» → «Gm», «A major» → «A», «Bb minor» → «A#m»
 * (flat→sharp нормализация, как в _parseKey). Используется в сплэше и header.metadata вместо
 * «голого» this.data.key. Идемпотентен: на входе «Gm» → на выходе «Gm». При нераспознанном
 * формате или пустом входе возвращает исходную строку (fallback) или пустую строку.
 * @see archive/docs/features/player-transpose.md
 */
static _shortKey(key) { … }
```

### Вход

| Параметр | Тип | Описание |
|----------|-----|----------|
| `key` | `string \| null \| undefined` (или любой тип — нестроковые значения трактуются как нераспознанные) | Тональность из `data.key` (playerdata) |

### Выход

| Тип | Описание |
|-----|----------|
| `string` | Краткая форма тональности, исходная строка (fallback) или пустая строка |

### Таблица соответствия (input → output)

| Input `data.key` | Output `_shortKey(...)` | Комментарий |
|------------------|-------------------------|-------------|
| `null` | `''` | Пустой → пустой; сплэш показывает только `bpm: N` |
| `undefined` | `''` | То же |
| `''` | `''` | То же |
| `"G minor"` | `"Gm"` | Длинный → короткий (FR-001, FR-002, FR-003, FR-004, FR-005) |
| `"A major"` | `"A"` | То же |
| `"F# minor"` | `"F#m"` | Диез в ноте сохраняется |
| `"Bb minor"` | `"A#m"` | Бемоль нормализуется в шарп-эквивалент (FR-007) |
| `"Db"` | `"C#"` | Бемоль без суффикса |
| `"Am"` | `"Am"` | Уже короткий → короткий (FR-006, идемпотентность) |
| `"A"` | `"A"` | То же |
| `"C#m"` | `"C#m"` | То же |
| `"?"` | `"?"` | Нераспознанный → fallback на исходную строку (FR-008, SC-007) |
| `"C maj7"` | `"C maj7"` | Нераспознанный модификатор (не `m`/`minor`/`major`/пустой) → fallback |
| `"major"` (без ноты) | `"major"` | Только модификатор без ноты — `_parseKey` не распарсит → fallback |
| `"  G minor  "` (пробелы) | `"Gm"` | `_parseKey` тримит вход (line 965 в обеих копиях) |
| `"Gminor"` (без пробела) | `"Gminor"` | `_parseKey` не распарсит (regex требует `[A-G][#b]?`); fallback |

### Инварианты

1. **Идемпотентность**: `_shortKey(_shortKey(key)) === _shortKey(key)` для всех валидных входов.
2. **Детерминированность**: один и тот же вход → один и тот же выход (без побочных эффектов).
3. **Отсутствие побочных эффектов**: чистая функция, не пишет в `this`, не делает HTTP-запросов.
4. **Согласованность с `_parseKey` / `_transposeLabel`**: flat→sharp нормализация идентична — `Bb` всегда → `A#`, `Eb` всегда → `D#`, и т.д. (FR-007).
5. **Fallback для нераспознанного**: не возвращает `null`, не бросает — пользователь видит хоть что-то осмысленное (FR-008).

### Привязка к FR

| FR | Как покрывается |
|----|-----------------|
| FR-001 (короткий формат везде) | `_shortKey` — единственная точка преобразования, переиспользуется во всех 5 местах |
| FR-002/003/004/005 (5 точек отрисовки) | Прямой вызов в `_renderSplash` / `_renderHeader` обеих копий |
| FR-006 (идемпотентность) | `_parseKey` уже идемпотентен; `CHROMATIC` — массив; результат стабилен |
| FR-007 (flat→sharp) | Делегировано в `_parseKey` |
| FR-008 (fallback) | `if (!parsed) return key || ''` |
| FR-009 (не трогать меню/бейдж) | `_shortKey` НЕ используется в `_transposeLabel`; `_transposeLabel` остаётся без изменений |
| FR-010 (две копии синхронно) | Helper добавляется в обе копии идентично (см. [research.md](../research.md) D1, D2, D3) |
| FR-011 (backend не меняется) | `_shortKey` — клиентский, не трогает API |

---

## Потребители (consumers)

### Сплэш публичного плеера (`karaoke-public/src/player/KaraokePlayer.js`)

```javascript
// _renderSplash, ~line 2989
const shortKey = KaraokePlayer._shortKey(this.data.key)
const keyStr = shortKey
  ? `Key: «${shortKey}», bpm: ${this.data.bpm}`
  : `bpm: ${this.data.bpm}`
ctx.fillText(keyStr, ox + 960 * sc, chordY)
```

**Отображаемая строка**:
- `data.key = "G minor"`, `data.bpm = 120` → `Key: «Gm», bpm: 120` (SC-001)
- `data.key = ""` → `bpm: 120` (SC-006)

### Header.metadata публичного плеера (`karaoke-public/src/player/KaraokePlayer.js`)

```javascript
// _renderHeader, ~line 3597
const shortKey = KaraokePlayer._shortKey(this.data.key)
if (shortKey) rows.push({ label: 'Тональность: ', value: shortKey })
```

**Отображаемая строка** (в блоке метаданных хедера):
- `data.key = "G minor"` → `Тональность: Gm` (SC-002)
- `data.key = ""` → строка `Тональность:` отсутствует в `rows` (SC-006)

### Сплэш админ-плеера online (`webvue3/src/player/KaraokePlayer.js`)

```javascript
// _renderSplash, online-ветка, ~line 3131
const shortKey = KaraokePlayer._shortKey(this.data.key)
const keyStr = shortKey
  ? `Key: «${shortKey}», bpm: ${this.data.bpm}`
  : `bpm: ${this.data.bpm}`
ctx.fillText(keyStr, ox + 960 * sc, chordY)
```

**Отображаемая строка**: то же, что и в публичном плеере.

### Сплэш админ-плеера MP4 render (`webvue3/src/player/KaraokePlayer.js`)

```javascript
// _renderSplash, MP4-ветка, ~line 3255
const shortKey2 = KaraokePlayer._shortKey(this.data.key)
const keyStr2 = shortKey2
  ? `Key: «${shortKey2}», bpm: ${this.data.bpm}`
  : `bpm: ${this.data.bpm}`
```

**Отображаемая строка**: то же, что и в online-режиме (для согласованности экспортируемого mp4 с публичной версией, FR-004, SC-003).

**Имена `shortKey2` / `keyStr2`**: текущий код использует суффикс `2` для второго вхождения в одной функции (online и MP4 — две разные ветки одного `_renderSplash`); сохраняем конвенцию для читаемости.

### Header.metadata админ-плеера (`webvue3/src/player/KaraokePlayer.js`)

```javascript
// _renderHeader, ~line 3864
const shortKey = KaraokePlayer._shortKey(this.data.key)
if (shortKey) rows.push({ label: 'Тональность: ', value: shortKey })
```

**Отображаемая строка**: то же, что и в публичном плеере (SC-004).

---

## Что НЕ входит (out of contract)

- `KaraokePlayer._transposeLabel(n, forMenu)` — НЕ меняется. Используется в меню «Тональность» и бейдже transpose. Возвращает формат «Cm (+N)» / «(+N)» — не подходит для сплэша/header (где сдвиг не показывается).
- `KaraokePlayer._parseKey(key)` — НЕ меняется. Сигнатура `{index, suffix}` сохраняется (используется `_transposeLabel`).
- `KaraokePlayer.CHROMATIC` — НЕ меняется.
- Backend-формат `data.key` — НЕ меняется.
- `SongEdit.vue` (форма редактирования) — НЕ затрагивается.