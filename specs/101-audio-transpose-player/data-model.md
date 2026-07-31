# Data Model: Транспонирование аудио в онлайн-плеере (админка)

**Feature**: 101-audio-transpose-player
**Date**: 2026-07-31

## Обзор

Фича не вводит новых серверных сущностей и не меняет схему БД. Поле `key` песни (существующее, `tbl_settings.key`) уже отдаётся обоими playerdata-эндпоинтами:
- admin: `GET /api/song/{id}/playerdata` → `data.key` (`ApiController.kt:6864`)
- public: `GET /api/public/player/{id}/playerdata` → `data.key` (`PublicPlayerController.kt:397`)

В обеих карта — `"key" to settings.key.takeIf { it.isNotBlank() }`, т.е. `null` при пустом поле (FR-013).

Все новые данные — клиентские, в браузере (localStorage), per-song. Сервер не получает сдвиг тональности и не хранит его.

## Сущности

### Сдвиг тональности (Transpose shift)

Клиентское состояние плеера; не сущность БД.

| Атрибут | Тип | Диапазон / формат | Описание |
|---------|-----|------------------|----------|
| `_transpose` | Integer | −12 … +12 | Сдвиг в полутонах от базовой тональности. 0 = базовая (без транспонирования). Значение по умолчанию — 0 (если для песни нет сохранённого). |
| Персистентность | localStorage | Ключ `kp_transpose_<songId>`, значение — целое число (строкой) | Per-song: по идентификатору песни (`data.id`). Не отправляется на сервер. `null`/отсутствие ключа = 0. |

**Идентичность и уникальность**: ключ localStorage = `kp_transpose_<songId>` (например, `kp_transpose_12345`). Уникален по `songId`. Не конфликтует с существующим глобальным `KaraokePlayer.LS_SETTINGS_KEY` (там `accVol/vocVol/anchored/playbackRate` — без `songId`).

**Lifecycle / state transitions**:
- `init` плеера → чтение `localStorage.getItem('kp_transpose_<data.id>')` → `this._transpose` (или 0 если нет).
- `setTranspose(n)` → запись `localStorage.setItem('kp_transpose_<data.id>', n)` → `this._transpose = n`.
- `playSong(newSongId)` → сброс `this._transpose` перед `init()`, затем чтение по новому `data.id` после `init()`.
- Ручное очищение пользователем localStorage → следующая загрузка: `_transpose = 0`.

**Валидация** (из FR-002):
- `n ∈ [-12, +12]` целое; иначе игнорируется (no-op), как `setPlaybackRate` игнорирует невалидный rate.
- Граничные значения ±12 = октава (результирующая тональность = базовая, но бейдж показывается — см. Edge Cases в spec).

### Результирующая тональность (Display label)

Вычисляемое, не хранится.

| Атрибут | Тип | Формат | Описание |
|---------|-----|--------|----------|
| `_transposeLabel(n)` | String | `(±N) <note><suffix>` или `(±N)` | Подпись пункта подменю / бейджа. |

**Правила вычисления** (FR-003, FR-004, FR-013):
- Хроматическая шкала: `['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']`.
- Разбор базовой `key`: нота (`C`..`B`, с опциональным `#`/`b`) + суффикс (`m` для минора, `` для мажора, иначе наследуется как есть).
- `index = (baseIndex + n) mod 12` (нормализация в [0, 11], с учётом отрицательного `n`).
- Результирующая подпись: `<chromatic[index]><suffix>`.
- Сдвиг в подписи: `+N` при `n > 0`, `-N` при `n < 0`, `0` (без знака) при `n = 0`.
- При пустом `data.key` (`null`): подпись = `(±N)` без результирующей тональности (база неизвестна — FR-013, Assumption). При `n = 0` — подпись `(0)` (бейдж не показывается по FR-010).

**Нестандартный `key`** (Edge Case): если разбор не удался (нота не распознана) — аудио-сдвиг всё равно применяется (чистый сдвиг на N полутонов), подпись показывается «как есть» или только сдвигом `(±N)`. Плеер не падает (FR-013 расширенно, Assumption).

## Аудио-граф (изменения)

Существующий граф (без изменений для gain/playbackRate):
```
accSource (AudioBufferSourceNode, playbackRate=_playbackRate) → accGain (GainNode) → destination
vocSource (AudioBufferSourceNode, playbackRate=_playbackRate) → vocGain (GainNode) → destination
```

Новый граф (с pitch-shift, по стемам):
```
accSource → _pitchShifts.get('acc') (Tone.PitchShift, pitch=_transpose) → accGain → destination
vocSource → _pitchShifts.get('voc') (Tone.PitchShift, pitch=_transpose) → vocGain → destination
```

В первой реализации плеер воспроизводит **только acc + voc** (bass/drums используются лишь в экспорте, не имеют `bassSource`/`drumsGain` в `KaraokePlayer.js`). Однако архитектура — map-based: `_pitchShifts: Map<stemKey, Tone.PitchShift>`. Добавление bass/drums/прочих стемов в будущем (когда станут проигрываемыми) = запись в Map + wiring в `_startAudio`, **без** изменения `setTranspose`/`_renderTransposeBadge`/меню (FR-005: перебор по всем стемам, не хардкод acc/voc).

Pitch-shift узлы создаются один раз на инстанс плеера (в `init` или lazy при первом появлении стема), переиспользуются между стартами/seek/сменой песни. `pitch` (в полутонах) меняется на всех узлах одновременно через `setTranspose` — `for (const ps of this._pitchShifts.values()) ps.pitch = n`.

## Состояние плеера (новые поля `KaraokePlayer`)

| Поле | Тип | Default | Описание |
|------|-----|---------|---------|
| `_transpose` | Integer | 0 | Текущий сдвиг для активной песни. |
| `_transposeSupported` | Boolean | true | Результат feature-detect Tone.PitchShift в `init`. Если false — подменю блокируется (FR-018). |
| `_pitchShifts` | Map<String, Tone.PitchShift> | `new Map()` | Map pitch-shift узлов по ключу стема (`'acc'`, `'voc'`; в будущем `'bass'`, `'drums'`, ...). Создаются lazy при появлении проигрываемого стема; переиспользуются. Перебор в `setTranspose` применяет сдвиг ко всем. |

Существующие поля без изменений: `_playbackRate`, `_accVol`, `_vocVol`, `_volumeAnchored`, `accSource`, `vocSource`, `accGain`, `vocGain`, `accBuffer`, `vocBuffer`, `data`, `audioCtx`, `startedAt`, `_rateAnchorPos`, `pausedAt`, `isPlaying`, `_isPrerolling`, `duration`. (При добавлении воспроизведения bass/drums в будущем — `bassSource`/`bassGain`/`drumsSource`/`drumsGain` войдут в тот же Map-based механизм транспонирования.)

## Валидационные правила (из spec)

| FR | Правило |
|----|---------|
| FR-002 | `_transpose ∈ [-12, +12]`, целое; 25 вариантов в подменю. |
| FR-005, SC-002 | `setTranspose` не делает HTTP-запросов; только localStorage + аудио-граф. Применяется ко **всем** стемам в `_pitchShifts` (Map-based, не хардкод acc/voc). |
| FR-006 | `ps.pitch = _transpose` (для каждого стема) НЕ меняет `playbackRate` → темп неизменен → `_getCurrentTime()` формула остаётся корректной. |
| FR-011 | localStorage-ключ содержит `songId` → per-song. |
| FR-012 | `playSong` сбрасывает `_transpose` перед `init`, читает заново по новому `data.id` после `init` → другая песня стартует в своём (или 0). |
| FR-013 | `data.key === null` → `_transposeLabel` возвращает `(±N)` без ноты. |
| FR-018 | `_transposeSupported === false` → подменю заблокировано с подсказкой. |