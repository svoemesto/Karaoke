# Data Model: Краткое отображение тональности в онлайн-плеере

**Feature**: 265-key-short-display
**Date**: 2026-08-30
**Related**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md), [contracts/key-display-contract.md](./contracts/key-display-contract.md)

Фича не вводит новых сущностей в БД — это чисто клиентское отображение существующего поля `data.key` (string), которое уже приходит из playerdata-эндпоинтов.

## Сущности

### `data.key` (input)

**Где живёт**: поле ответа playerdata-эндпоинта, передаётся в `KaraokePlayer` через аргумент `playSong(songId, token, authToken, …)` → затем сохраняется в `this.data.key`.

**Тип**: `string | null` (в JSON ответе — строка, на клиенте может быть `undefined` если поле отсутствует).

**Допустимые значения** (формат хранения в БД, не меняется фичей):

| Формат | Пример | Источник |
|--------|--------|----------|
| Длинный (человеческий) | `"G minor"`, `"A major"`, `"F# minor"` | Редактор при сохранении песни |
| Короткий | `"Am"`, `"Gm"`, `"F#m"`, `"C"`, `"Bb"` | Редактор при сохранении песни (или legacy-данные) |
| Пустой / отсутствует | `""`, `null`, `undefined` | Песня без указанной тональности |
| Нераспознанный | `"?"`, `"unknown"`, `"C maj7"`, `"major"` | Нестандартный ввод (не валидируется на бэкенде) |

**Контракт чтения**: backend отдаёт `data.key` как есть, без преобразований; клиент сам нормализует для отображения.

### `KaraokePlayer._shortKey(key)` (derived, не stored)

**Где живёт**: статический метод в `KaraokePlayer` (обе копии — `karaoke-public/src/player/KaraokePlayer.js` и `webvue3/src/player/KaraokePlayer.js`). Чистая функция, не имеет состояния, не пишет в БД.

**Тип возврата**: `string`.

**Логика** (см. также [contracts/key-display-contract.md](./contracts/key-display-contract.md)):

```text
input: any (string | null | undefined | ... )
  ↓ _parseKey (existing)
parsed: { index: 0..11, suffix: '' | 'm' } | null
  ↓ if parsed
output: CHROMATIC[parsed.index] + parsed.suffix     # e.g. "Gm"
  ↓ else (fallback)
output: input || ''                                 # original or empty
```

**Где используется** (5 точек отрисовки в обеих копиях плеера):

| Файл | Функция | Line | Назначение |
|------|---------|------|------------|
| `karaoke-public/src/player/KaraokePlayer.js` | `_renderSplash` | ~2989 | Сплэш: `Key: «<shortKey>», bpm: <bpm>` |
| `karaoke-public/src/player/KaraokePlayer.js` | `_renderHeader` | ~3597 | Header metadata: `Тональность: <shortKey>` |
| `webvue3/src/player/KaraokePlayer.js` | `_renderSplash` (online) | ~3131 | Сплэш online: `Key: «<shortKey>», bpm: <bpm>` |
| `webvue3/src/player/KaraokePlayer.js` | `_renderSplash` (MP4) | ~3255 | Сплэш MP4 render: `Key: «<shortKey>», bpm: <bpm>` |
| `webvue3/src/player/KaraokePlayer.js` | `_renderHeader` | ~3864 | Header metadata: `Тональность: <shortKey>` |

### `KaraokePlayer._parseKey(key)` (existing, переиспользуется)

**Где живёт**: статический метод в `KaraokePlayer` (обе копии). Не меняется фичей.

**Тип возврата**: `{ index: number (0..11), suffix: '' | 'm' } | null`.

**Семантика**:
- `index` — позиция в хроматической шкале `CHROMATIC` (0 = C, 11 = B).
- `suffix` — `'m'` для минора, `''` для мажора (или нераспознанного модификатора, отличного от "m").
- `null` — если нота не распознана.

**Используется**:
- `_shortKey` (новый, в рамках этой фичи).
- `_transposeLabel(n, forMenu)` (существующий — для меню «Тональность» и бейджа transpose; не затрагивается фичей).

### `KaraokePlayer.CHROMATIC` (existing, переиспользуется)

**Где живёт**: статическое поле в `KaraokePlayer` (обе копии). Не меняется фичей.

```javascript
static CHROMATIC = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
```

## Связи (relationships)

```text
playerdata endpoint  ───►  KaraokePlayer.data.key  ───►  KaraokePlayer._shortKey(key)  ───►  canvas rendering
                                                       │
                                                       └──►  KaraokePlayer._parseKey(key) (existing)
                                                             │
                                                             └──►  KaraokePlayer.CHROMATIC[index] + parsed.suffix
```

Никаких новых сущностей, полей, таблиц в БД. Никаких изменений в API/JSON-схеме.

## Валидация (validation rules)

Валидация — на стороне бэкенда (формат `data.key` хранится и отдаётся как есть, без изменений). Клиент только **отображает**, не валидирует.

**На клиенте**:
- Если `data.key` пуст (`""`, `null`, `undefined`) → `_shortKey` возвращает `''`, тернарный оператор `if (shortKey)` скрывает строку.
- Если `data.key` нераспознан → `_shortKey` возвращает исходную строку (fallback).
- Если `data.key` распознан → `_shortKey` возвращает короткую форму (Cm/Gm/F#m/A и т.д.).

## Состояния (lifecycle / state transitions)

N/A — фича stateless, нет переходов состояний. `_shortKey` — чистая функция от входа.

## Объём данных (data volume / scale)

N/A — фича не масштабируется по объёму данных; обрабатывает одну строку за раз в момент отрисовки сплэша/header (статические слои, не пересчитываются каждый кадр).

## Что НЕ входит (out of model scope)

- **Backend DB schema** — НЕ меняется. Поле `song.key` в таблице песен хранится в исходном формате.
- **Playerdata JSON API** — НЕ меняется. Ответ содержит `data.key` как строку.
- **SongEdit.vue форма редактирования** (`<input v-model="song.key">`) — НЕ затрагивается. Пользовательская задача ограничена онлайн-плеером.
- **Меню «Тональность»** — НЕ затрагивается (использует `_transposeLabel`, не `_shortKey`).
- **Бейдж transpose** — НЕ затрагивается (использует `_transposeLabel`, не `_shortKey`).