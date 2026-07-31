# Phase 0 Research: Транспонирование аудио в браузере

**Feature**: 101-audio-transpose-player
**Date**: 2026-07-31

Исследование ключевого технического решения — выбор pitch-shift подхода/библиотеки для клиентского транспонирования аудио с сохранением темпа (time-stretch), бесшовного переключения «на лету» и совместимости с существующим аудио-движком плеера `webvue3/src/player/KaraokePlayer.js`.

## Контекст существующей архитектуры

Аудио-граф плеера (из `_startAudio`, `webvue3/src/player/KaraokePlayer.js:1475`):

```
accBuffer (AudioBuffer) → accSource (AudioBufferSourceNode) → accGain (GainNode) → destination
vocBuffer (AudioBuffer) → vocSource (AudioBufferSourceNode) → vocGain (GainNode) → destination
```

- `accSource.playbackRate.value = this._playbackRate` — скорость (меняет темп и высоту одновременно, по нативному behaviour `AudioBufferSourceNode`).
- `setPlaybackRate(rate)` меняет `playbackRate` прямо на уже запущенных `accSource`/`vocSource` без рестарта — бесшовно.
- `_getCurrentTime()` считает позицию через `(audioCtx.currentTime - startedAt) * _playbackRate + _rateAnchorPos` — скорость интегрирована в таймлайн.
- Два независимых источника (acc + voc) — pitch-shift должен применяться к обоим синхронно. В первой реализации плеер **воспроизводит только acc + voc** (bass/drums используются лишь в экспорте, см. `KaraokePlayer.js:1039-1040` — `_updateExportMenuAvailability`, не имеют `bassSource`/`drumsGain`). Однако архитектура должна быть готова к добавлению bass/drums/прочих стемов в будущем (FR-005) — pitch-shift должен перебирать **все проигрываемые стемы**, а не хардкод acc/voc.

## Решение 1 (REJECTED): `AudioBufferSourceNode.detune`

**Что**: Нативное свойство `detune` (в центах, 100 центов = 1 полутон) на `AudioBufferSourceNode`. Аналогично `playbackRate` применяется на лету без рестарта.

**Почему отвергнуто**: `detune` — это `playbackRate` в других единицах (cent = 1200·log2(rate)). Как и `playbackRate`, оно меняет высоту **и** темп одновременно (resampling), а не выполняет настоящий time-stretch. FR-006 явно требует: «сдвиг тональности не должен менять длительность/темп», «аудио остаётся синхронным с разметкой». Использование `detune` разъезжается с маркерами на N полутонов — песня ускоряется/замедляется, текст уезжает. Неприемлемо.

## Решение 2 (REJECTED): `playbackRate` компенсация + `detune`

**Что**: комбинировать `playbackRate` (для темпа) и `detune` (для высоты) так, чтобы темп остался неизменным, а высота сместилась.

**Почему отвергнуто**: `playbackRate` и `detune` на одном `AudioBufferSourceNode` **мультипликативны** по эффекту на темп (оба влияют на скорость воспроизведения сэмпла). Нельзя «скомпенсировать» темп, задрав `playbackRate` в обратную сторону — итоговый темп всё равно изменится, а высота вычисляется из произведения, не из разности. Математически не даёт чистого pitch-shift-without-time-stretch на нативном узле. Это распространённое заблуждение; подтверждается MDN-описанием `detune` («combined with playbackRate to determine overall rate»). Не работает.

## Решение 3 (SELECTED): Web Audio `AudioWorkletNode` / JS-DSP pitch-shift

**Что**: специализированный pitch-shift с time-stretch через отдельный аудио-узел, вставляемый **между** `accSource` и `accGain` (и `vocSource`/`vocGain`):
```
accSource → pitchShiftNode(acc) → accGain → destination
vocSource → pitchShiftNode(voc) → vocGain → destination
```
Pitch-shift узел сохраняет темп (time-stretch) — длительность/темп не меняются, меняется только высота. Таймлайн `_getCurrentTime()` остаётся корректным (т.к. темп не меняется, `playbackRate` остаётся как есть).

**Подходы внутри решения**:

### 3a. Tone.js `Tone.PitchShift` (RECOMMENDED)

**Что**: `Tone.PitchShift` из библиотеки [Tone.js](https://tonejs.github.io/) — готовый pitch-shift эффект (на базе GrainDelay/PSOLA-подобного алгоритма), управляется параметром `pitch` в полутонах (может быть и дробным). Реализован как `AudioWorkletNode`/`ToneAudioNode`, вставляется в аудио-граф как обычный узел: `source.connect(pitchShift); pitchShift.connect(gain)`.

**Плюсы**:
- Минимальный API: `new Tone.PitchShift(pitch); pitchShift.pitch = 3` — смена «на лету» без рестарта источника.
- Зрелая, поддерживаемая библиотека (Tone.js 15+, активная разработка, ~30k stars).
- Time-stretch по умолчанию (не меняет темп) — соответствует FR-006.
- Совместима с Web Audio API — `ToneAudioNode` оборачивает нативные `AudioNode`.
- Дробные полутоны — пригодится в будущем (fine-tuning).

**Минусы**:
- Добавляет ~150-200 KB в bundle (Tone.js). Смягчение: tree-shaking Vite — `import { PitchShift } from 'tone'` тянет только нужный модуль (Tone.js v15 поддерживает ESM tree-shaking), реально ~40-60 KB.
- Алгоритм GrainDelay-based имеет лёгкие артефакты на транспонировании голоса (фазовое «плавание»), но для караоке-целей (подбор под голос) допустимо — качество достаточно.
- **N инстансов `Tone.PitchShift`** (по одному на каждый проигрываемый стем — 2 сейчас: acc + voc; больше, если добавятся bass/drums). Каждый инстанс — отдельный DSP-узел; CPU растёт линейно по числу стемов. Для 2 стемов — пренебрежимо; для будущих 4-5 — проверить fps. Map-based подход (`Map<stemKey, Tone.PitchShift>`) — добавление стема = один `set(stemKey, new Tone.PitchShift(...))`.

**Решение по размеру bundle**: Vite + Tone.js v15 ESM — tree-shaking должен вытащить только `PitchShift` + его зависимости (Context, ToneAudioNode, GrainDelay). Проверить в research-фазе имплементации через `vite build --report` (или `rollup-plugin-visualizer`, если уже в проекте). Если bundle превысит разумный порог — рассмотреть 3b.

### 3b. SoundTouch.js (ALTERNATIVE)

**Что**: [SoundTouch.js](https://github.com/cutterpy/SoundTouch) — порг JS-библиотеки SoundTouch (C++), классическая для pitch-shift/time-stretch. `PitchShifter` + `SimpleFilter` поверх `WebAudioBufferSource`.

**Плюсы**: хорошее качество time-stretch (алгоритм WSOLA), проверенная.
**Минусы**: старая API (не идиоматичный Web Audio узел — требует ручного wiring через `AudioBuffer`→`SimpleFilter`→`ScriptProcessor`/`AudioWorklet`), менее поддерживаемая (последний коммит годы назад), плохо интегрируется с уже запущенными `AudioBufferSourceNode` (нужно перегонять через `AudioBuffer` целиком) — нарушит бесшовность FR-006. Сложнее.

### 3c. Собственный AudioWorklet (ALTERNATIVE, rejected)

**Что**: написать собственный AudioWorklet-процессор (PSOLA/WSOLA) — максимальный контроль, нулевая зависимость.
**Почему отвергнуто**: несоразмерно сложнее (недели разработки DSP-алгоритма), high risk артефактов, не основная компетенция проекта. Готовая библиотека (3a) решает задачу за часы интеграции. Constitution Principle I не нарушается (Tone.js — локальная npm-зависимость, не SaaS).

## Выбор

**Decision**: Tone.js `Tone.PitchShift` (3a).

**Rationale**:
1. Time-stretch by default → FR-006 (не меняет темп, синхронность с маркерами сохраняется).
2. Идиоматичный Web Audio узел → вставляется в существующий граф `source → pitchShift → gain` без переписывания `_startAudio`-логики (минимум кода).
3. `pitchShift.pitch = N` — смена «на лету» без рестарта источника → FR-006 (без остановки воспроизведения).
4. Зрелая, поддерживаемая, ESM + Vite tree-shaking — разумный размер bundle.
5. Альтернативы (1, 2) математически не дают чистого time-stretch; 3b сложнее в интеграции; 3c — несоразмерный объём работы.

**Alternatives considered**: `AudioBufferSourceNode.detune` (reject: меняет темп), `playbackRate`+`detune` (reject: мультипликативны), SoundTouch.js (alternative: сложнее интеграция), собственный AudioWorklet (reject: объём).

## Перечень остальных технических решений (без альтернатив)

| Вопрос | Решение | Обоснование |
|--------|---------|------------|
| Как хранить per-song сдвиг | localStorage, ключ `kp_transpose_<songId>` (число −12..+12) | Параллель существующему `KaraokePlayer.LS_SETTINGS_KEY` (глобальные настройки), но per-song по `data.id`. Не отправляется на сервер. |
| Восстановление при загрузке | В `init()`/после `data` готовности: `this._transpose = localStorage.getItem(key) \|\| 0`; применить при следующем `_startAudio` | Если читать до готовности `data.id` — невозможно (id ещё не загружен). Ждать `data`. |
| Применение при смене песни (`playSong`) | Сбросить `this._transpose` перед `init()`, после `init()` прочитать из localStorage по новому `data.id` | Per-song: новая песня → свой сдвиг (или 0 если не сохранён). Соответствует FR-012. |
| Бесшовная смена на лету | `setTranspose(n)`: `this._transpose = n; for (const ps of this._pitchShifts.values()) ps.pitch = n; _updateTransposeMenu(); _saveTranspose()` | Аналог `setPlaybackRate`. Не трогает источники (узлы живут между source и gain, `pitch` меняется на инстансах). Map-based: перебор всех pitch-shift узлов по стемам. |
| Создание pitch-shift узлов | Map-based: `this._pitchShifts = new Map()`. При появлении проигрываемого стема (acc/voc сейчас; bass/drums — когда станут проигрываемыми): `this._pitchShifts.set(stemKey, new Tone.PitchShift({ context: this.audioCtx, pitch: this._transpose }))`. В `_startAudio` для каждого стема: `source.connect(this._pitchShifts.get(stemKey)); this._pitchShifts.get(stemKey).connect(gain)`. | Узлы персистентны (создаются один раз на инстанс плеера по стему), переиспользуются между `_startAudio`-вызовами. Добавление нового стема в будущем = запись в Map + wiring в `_startAudio`, без изменения `setTranspose`/`_renderTransposeBadge`/меню. |
| Бейдж | Новый метод `_renderTransposeBadge(ctx, W, H)` рядом с `_renderSpeedBadge`; рисуется под бейджем скорости (Y-смещение = высота бейджа скорости + gap) | Параллель существующему `_renderSpeedBadge`. Синий (`#08f`) вместо оранжевого (`#f80`). Бейдж один на весь микс (не по стему) — отображает сдвиг, применённый ко всем стемам. |
| Подменю «Тональность» | Новый блок в `_buildUI` HTML-шаблоне (рядом с `#kp-submenu-speed`), wiring в `_buildMenu` (параллельно `speedItem`), обновление в `_updateTransposeMenu` | Параллель существующего speed-меню (FR-001: «там же, где скорость»). |
| Подписи пунктов подменю | Вычисляются от `data.key`: `_transposeLabel(n)` → `(±N) <результирующая>`. При пустом `key` → `(±N)` без результирующей. Хроматическая шкала `['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']` + наследование суффикса `m`/`` | FR-003/FR-004/FR-013 (пустой key). |
| Деградация без поддержки | Feature-detect: `typeof AudioWorkletNode !== 'undefined' && this.audioCtx.audioWorklet` (или try-create `Tone.PitchShift` в `init`). Если не получилось — `_transposeSupported = false`, подменю рендерится с заблокированными пунктами + подсказкой | FR-018. |
| Per-feature документ | `docs/features/player-transpose.md` (новый) — «Что делает / Зачем / Как работает / Инварианты / Ловушки / Ссылки» по шаблону `tools/check-feature-doc.sh` | Constitution FR-009 + AGENTS.md Q&A «Как добавить per-feature документ». |

## Риски и митигации

| Риск | Митигация |
|------|-----------|
| Tone.js `PitchShift` артефакты на голосе (фазовое «плавание») | Допустимо для караоке-подбора-под-голос; при явной жалобе — переключиться на 3b (SoundTouch) в отдельном PR. |
| Bundle size роста после Tone.js | Vite tree-shaking + визуализация bundle; если > +100KB gzip — рассмотреть 3b. |
| `Tone.PitchShift` требует `Tone.getContext()`/`Tone.start()` — конфликт с существующим `this.audioCtx` | Создать `Tone.PitchShift` с явной передачей контекста (`new Tone.PitchShift({ context: this.audioCtx, pitch: 0 })`) или использовать `Tone.setContext(this.audioCtx)` в `init` — проверить в реализации. |
| Бесшовность смены `pitch` на лету | `Tone.PitchShift.pitch` — `Param`, плавный transition по умолчанию (без щелчка). Если щелчки — `pitchShift.pitch.rampTo(n, 0.02)`. |
| N pitch-shift узлов (по числу стемов) увеличивают CPU | 2 стема сейчас — пренебрежимо. Map-based: добавление стема = +1 узел; мониторить fps при будущих 4-5 стемах. При деградации — рассмотреть один общий pitch-shift на mix-bus (но меняет архитектуру — отложить). |
| Рассинхрон тональности внутри микса (acc транспонирован, бас — нет) | Map-based гарантирует, что `setTranspose` применяет сдвиг ко **всем** стемам в `_pitchShifts` за один перебор — физически невозможно «забыть» стем при добавлении (FR-005). |