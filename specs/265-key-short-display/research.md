# Research: Краткое отображение тональности в онлайн-плеере

**Feature**: 265-key-short-display
**Date**: 2026-08-30
**Related**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md)

Phase 0 research: фиксация технических решений для реализации, обоснование выбора helper-а и точек отрисовки.

## Уже разрешённые неизвестные

В Technical Context (см. [plan.md](./plan.md)) не было `NEEDS CLARIFICATION` — все аспекты известны из предыдущего исследования кодовой базы (`codegraph_explore`):

- Где в коде отображается `data.key` напрямую — определено grep'ом по двум копиям `KaraokePlayer.js` (5 точек).
- Существующий парсер `_parseKey` и хроматическая шкала `CHROMATIC` — переиспользуются.
- Backend-формат `data.key` не меняется — Assumptions в спеке.
- Меню «Тональность» и бейдж transpose не затрагиваются — FR-009 в спеке.

## Дизайн-решения

### D1. Helper `_shortKey` — статический метод в `KaraokePlayer`

**Decision**: Добавить static method `KaraokePlayer._shortKey(key)` рядом с `KaraokePlayer._parseKey(key)`. Сигнатура:

```javascript
// Краткое представление тональности: «G minor» → «Gm», «A major» → «A», «Bb minor» → «A#m»
// (flat→sharp нормализация, как в _parseKey). Используется в сплэше и header.metadata вместо
// «голого» this.data.key. Идемпотентен: на входе «Gm» → на выходе «Gm». При нераспознанном
// формате или пустом входе возвращает исходную строку (fallback) или пустую строку.
// @see archive/docs/features/player-transpose.md
static _shortKey(key) {
  const parsed = KaraokePlayer._parseKey(key)
  if (!parsed) return key || ''
  return KaraokePlayer.CHROMATIC[parsed.index] + parsed.suffix
}
```

**Rationale**:
- Согласуется с паттерном `_parseKey` (уже static) — обе копии плеера повторяют этот код синхронно.
- Не зависит от инстанса (`this`) — чистая функция, легко тестируется визуально и через unit-проверки.
- Fallback на исходную строку (`return key || ''`) согласован с FR-008 (нераспознанный формат) и FR-006 (идемпотентность: пустая строка → пустая строка).

**Alternatives considered**:

| Альтернатива | Почему отвергнута |
|--------------|-------------------|
| **A1**: Переиспользовать `_transposeLabel(0, false)` для получения короткой формы | `_transposeLabel` всегда добавляет «(+N)» в конец (формат «Cm (+0)» при сдвиге 0). На сплэше/header сдвиг не показывается — нужен формат «Gm», без скобок. |
| **A2**: Поместить `_shortKey` в отдельный модуль `keyFormatter.js` и импортировать в обе копии | Нарушает инвариант «две копии плеера» (см. комментарии в `karaoke-public/src/player/KaraokePlayer.js` строки 770+ и в `webvue3/src/player/KaraokePlayer.js` строки 880+). Импорт между двумя `KaraokePlayer.js` создаст runtime-зависимость, которой сейчас нет — потенциальный источник регрессий. Копирование helper-а — канонический паттерн (так же синхронизируются `_parseKey`, `_transposeLabel`, `CHROMATIC`). |
| **A3**: Изменить сигнатуру `_parseKey`, чтобы он возвращал готовую строку | Лишний blast radius: `_parseKey` уже используется в `_transposeLabel` (строки 1004 / 1130), менять его — риск регрессии в существующем коде. Старый формат `{index, suffix}` удобен для `_transposeLabel` (расчёт по `CHROMATIC[(index + n) % 12]`). |

### D2. Точки вызова `_shortKey` в `karaoke-public/src/player/KaraokePlayer.js`

**Decision**: Две правки:

1. **Сплэш (line ~2989)** — внутри `_renderSplash`:
   ```javascript
   // Было:
   const keyStr = this.data.key
     ? `Key: «${this.data.key}», bpm: ${this.data.bpm}`
     : `bpm: ${this.data.bpm}`
   // Стало:
   const shortKey = KaraokePlayer._shortKey(this.data.key)
   const keyStr = shortKey
     ? `Key: «${shortKey}», bpm: ${this.data.bpm}`
     : `bpm: ${this.data.bpm}`
   ```

2. **Header.metadata (line ~3597)** — внутри `_renderHeader`:
   ```javascript
   // Было:
   if (this.data.key) rows.push({ label: 'Тональность: ', value: this.data.key })
   // Стало:
   const shortKey = KaraokePlayer._shortKey(this.data.key)
   if (shortKey) rows.push({ label: 'Тональность: ', value: shortKey })
   ```

**Rationale**: `_shortKey` возвращает `''` для пустого/null входа (как `if (this.data.key)` раньше), поэтому тернарный оператор и `if`-гард работают идентично — поведение для пустого key сохранено (FR-006 в спеке, регрессии нет).

### D3. Точки вызова `_shortKey` в `webvue3/src/player/KaraokePlayer.js`

**Decision**: Три правки:

1. **Сплэш online (line ~3131)** — внутри `_renderSplash` (online-режим):
   ```javascript
   const shortKey = KaraokePlayer._shortKey(this.data.key)
   const keyStr = shortKey
     ? `Key: «${shortKey}», bpm: ${this.data.bpm}`
     : `bpm: ${this.data.bpm}`
   ```

2. **Сплэш MP4 render (line ~3255)** — внутри блока MP4 render mode (та же функция, но в `else`-ветке):
   ```javascript
   const shortKey2 = KaraokePlayer._shortKey(this.data.key)
   const keyStr2 = shortKey2
     ? `Key: «${shortKey2}», bpm: ${this.data.bpm}`
     : `bpm: ${this.data.bpm}`
   ```
   Имя `shortKey2` / `keyStr2` — для отличия от `shortKey` / `keyStr` в online-ветке (в одной функции два разных места с разными локальными именами — текущий код использует суффикс `2` для второго вхождения, сохраняем конвенцию).

3. **Header.metadata (line ~3864)** — внутри `_renderHeader`:
   ```javascript
   const shortKey = KaraokePlayer._shortKey(this.data.key)
   if (shortKey) rows.push({ label: 'Тональность: ', value: shortKey })
   ```

**Rationale**: Аналогично D2. MP4 render использует тот же canvas-рендер, что и online — короткая форма будет и в финальном mp4 (что важно для согласованности публичной и админской версии, FR-004 в спеке).

### D4. Per-feature документ `archive/docs/features/player-transpose.md`

**Decision**: Добавить новый раздел после существующего раздела «Как работает»:

```markdown
## Короткое отображение тональности (added 2026-08-30, FR-265)

Helper `KaraokePlayer._shortKey(key)` возвращает краткую форму тональности:
- Вход `"G minor"` → выход `"Gm"`
- Вход `"A major"` → выход `"A"`
- Вход `"Bb minor"` → выход `"A#m"` (flat→sharp нормализация, как в `_parseKey`)
- Вход `"Gm"` (уже короткий) → выход `"Gm"` (идемпотентно)
- Вход `""` / `null` → выход `""` (пусто, fallback)
- Вход `"C maj7"` (нераспознанный) → выход `"C maj7"` (fallback на исходную строку)

Реализация — однострочная обёртка вокруг `_parseKey` + `CHROMATIC[parsed.index] + parsed.suffix`.

Применяется в 5 точках отрисовки (обе копии `KaraokePlayer.js`):
- `karaoke-public/src/player/KaraokePlayer.js` — `_renderSplash` (~line 2989), `_renderHeader` (~line 3597)
- `webvue3/src/player/KaraokePlayer.js` — `_renderSplash` online (~line 3131), `_renderSplash` MP4 (~line 3255), `_renderHeader` (~line 3864)

Согласовано: `_transposeLabel` (для меню «Тональность» и бейджа transpose) остаётся без изменений —
он уже формирует краткую форму, добавляя «(+N)» для сдвига. На сплэше/header сдвиг не показывается,
используется `_shortKey`.
```

**Rationale**:
- Синхронизирует документ с реальным кодом (governance: Constitution §VI FR-009, FR-012 в спеке).
- Документирует helper как часть подсистемы плеера — следующий разработчик увидит контракт без чтения кода.

### D5. Линтер и baseline

**Decision**: Никаких новых правил ESLint не вводится. Используются существующие правила (`webvue3/.eslintrc.cjs`, `karaoke-public/.eslintrc.cjs`). Проверка перед merge (FR-013):

```bash
cd webvue3 && npm run lint
cd karaoke-public && npm run lint
bash tools/check-eslint-baseline.sh webvue3
bash tools/check-eslint-baseline.sh karaoke-public
```

**Rationale**: Добавление static method с JSDoc — стандартная практика, нарушений добавлять не должно. Baseline останется 0 новых нарушений.

## Список решений (TL;DR)

| # | Решение | Файл(ы) |
|---|---------|---------|
| D1 | Static `_shortKey(key)` рядом с `_parseKey`, fallback на исходную строку | `karaoke-public/src/player/KaraokePlayer.js`, `webvue3/src/player/KaraokePlayer.js` |
| D2 | 2 точки вызова в публичном плеере | `karaoke-public/src/player/KaraokePlayer.js` (сплэш, header) |
| D3 | 3 точки вызова в админском плеере | `webvue3/src/player/KaraokePlayer.js` (сплэш online, сплэш MP4, header) |
| D4 | Новый раздел в per-feature документе | `archive/docs/features/player-transpose.md` |
| D5 | Существующий ESLint + baseline, ноль новых нарушений | `webvue3/.eslintrc.cjs`, `karaoke-public/.eslintrc.cjs` (без изменений) |