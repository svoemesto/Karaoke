# Транспонирование аудио в онлайн-плеере (админка)

> **Status**: experimental
> **Feature Key**: player-transpose
> **Last Updated**: 2026-07-31

## Что делает

Позволяет пользователю админки транспонировать аудио песни ±12 полутонов
(октава вверх/вниз) относительно базовой тональности прямо во время
воспроизведения в онлайн-плеере, без обращения к серверу и без
перекодирования файлов. Выбор — через подменю «Тональность» в меню плеера
(рядом со скоростью). При сдвиге ≠ 0 — синий бейдж в правом верхнем углу
экрана под бейджем скорости.

## Зачем

Подбор тональности под голос — частая потребность при работе с караоке:
одна песня удобна в +3, другая в −2, третья в базовой. До этой фичи
транспонирование требовало внешних инструментов (Audacity и т.п.) и
перекодирования. Клиентское транспонирование в браузере даёт мгновенный
результат без потерь исходного файла и без нагрузки на сервер.

## Как работает

**Pitch-shift с сохранением темпа (time-stretch)** через `Tone.PitchShift`
(библиотека Tone.js, ESM-импорт с tree-shaking). Узел вставляется в
существующий аудио-граф плеера **между** `AudioBufferSourceNode` и
`GainNode` для каждого проигрываемого стема:

```
accSource → Tone.PitchShift('acc') → accGain → destination
vocSource → Tone.PitchShift('voc') → vocGain → destination
```

**Map-based архитектура**: `_pitchShifts: Map<stemKey, Tone.PitchShift>`.
`_ensurePitchShift(stemKey)` lazy-создаёт узел. `setTranspose(n)` применяет
`pitch = n` ко **всем** узлам в Map синхронно — смесь остаётся в одной
тональности. Добавление bass/drums/прочих стемов в будущем = вызов
`_ensurePitchShift('bass')` без изменения `setTranspose`/меню/бейджа.

**Бесшовность**: `setTranspose` НЕ трогает `accSource`/`vocSource` — узлы
живут между source и gain, `pitch` меняется на инстансах (как
`setPlaybackRate` меняет `playbackRate`). Не меняет `_playbackRate` → темп
неизменен → `_getCurrentTime()` формула корректна → синхронность с
караоке-маркерами сохраняется.

**Базовая тональность** — `data.key` (отдаётся playerdata-эндпоинтами без
изменений бэкенда). `_transposeLabel(n)` вычисляет результирующую подпись
по хроматической шкале `['C','C#',...,'B']` с наследованием суффикса
(`m` для минора). При пустом `key` — подпись только сдвигом (`+3`/`-2`).

**Персистентность** — per-song: `localStorage['kp_transpose_<data.id>']`.
В отличие от скорости (глобальная настройка плеера), тональность
индивидуальна для каждой песни. Восстановление — `_restoreTranspose()` в
`init()`; сброс — в `playSong()`/`_loadNewFile()` перед `init()`
(новая песня стартует в базовой).

**Деградация**: feature-detect `Tone.PitchShift` в `_loadAudio`. Если
браузер не поддерживает — `_transposeSupported = false`, подменю
«Тональность» видно, но пункты заблокированы с подсказкой «Браузер не
поддерживает».

## Инварианты

- Сдвиг применяется ко **всем** проигрываемым стемам синхронно (acc + voc;
  bass/drums при появлении) — рассинхрон тональности внутри микса
  невозможен (Map-перебор в `setTranspose`).
- Темп не меняется при транспонировании — `_playbackRate` не затрагивается
  pitch-shift'ом; `_getCurrentTime()` формула остаётся корректной.
- Транспонирование полностью клиентское — нет HTTP-запросов к серверу
  при смене тональности (проверяется в DevTools Network).
- Сдвиг хранится per-song по `data.id`, не глобально (отличие от скорости).
- Бейдж тональности (синий, `#08f`) — под бейджем скорости (оранжевый,
  `#f80`), не перекрывает его; оба видны одновременно при активных обоих.
- Бейдж показывается при сдвиге ≠ 0, исчезает при 0; ±12 (октава) — бейдж
  показывается, т.к. физически другой режим воспроизведения.

## Известные ловушки

- **`AudioBufferSourceNode.detune` НЕ подходит** для транспонирования —
  это `playbackRate` в центах, меняет высоту **и** темп одновременно
  (resampling), ломает синхронность с маркерами (FR-006). Нужен именно
  time-stretch pitch-shift (Tone.PitchShift). `playbackRate`+`detune`
  компенсация тоже не работает — они мультипликативны по эффекту на темп.
- **Pitch-shift узлы привязаны к `AudioContext`**. `playSong`/`_loadNewFile`
  закрывают `audioCtx` — `_disposePitchShifts()` обязан вызываться ДО
  `audioCtx.close()`, иначе узлы уже невалидны. При новом `_startAudio`
  `_ensurePitchShift` создаст свежие узлы на новом context.
- **`Tone.setContext(this.audioCtx)` в `_loadAudio`** — обязателен, иначе
  Tone.PitchShift создаст свой собственный context и подключение
  source→pitchShift→gain跨 contexts будет невозможно. При множественных
  инстансах плеера `setContext` может выбросить ( Tone уже привязан) —
  тогда `new Tone.PitchShift({ context: this.audioCtx })` с явным
  контекстом спасает (см. `_ensurePitchShift`).
- **Bundle size**: Tone.js добавляет ~+60 KB gzip (tree-shaking `PitchShift`
  + зависимости). Порог +100 KB gzip (research.md) — не превышен.
- **Подписи пунктов меню** рендерятся в `_buildUI` ДО загрузки `data.key`
  (data ещё null) — без тональности (`(+3)`). `_updateTransposeMenu`
  пересчитывает их после `init()` с реальным `data.key`. Не «кэшировать»
  подписи в HTML — всегда пересчитывать при смене песни.
- **`_saveTranspose` вызывается ТОЛЬКО из `setTranspose`** (явный выбор
  пользователя). Не вызывать из `init`/`playSong` (восстановление не
  должно перезаписывать localStorage мусором).
- **Per-song ≠ global**: НЕ расширять `LS_SETTINGS_KEY`/`_savePersistedSettings`
  тональностью — там глобальные настройки (скорость/громкость). Тональность
  — отдельный ключ `kp_transpose_<songId>`.
- **Pitch-shift артефакты**: GrainDelay-based алгоритм Tone.PitchShift
  имеет лёгкое фазовое «плавание» на голосе. Для караоке-подбора-под-голос
  допустимо. При явной жалобе — рассмотреть SoundTouch.js (research.md §3b).

## Ссылки

- [spec.md](../../specs/101-audio-transpose-player/spec.md) — спецификация
  фичи (FR-001..FR-018, success criteria, edge cases)
- [plan.md](../../specs/101-audio-transpose-player/plan.md) — план
  реализации (Technical Context, Constitution Check)
- [research.md](../../specs/101-audio-transpose-player/research.md) —
  выбор pitch-shift библиотеки (Tone.PitchShift vs detune vs SoundTouch)
- [data-model.md](../../specs/101-audio-transpose-player/data-model.md) —
  сущности (`_transpose`, `_pitchShifts` Map, аудио-граф)
- [contracts/player-transpose-ui-contract.md](../../specs/101-audio-transpose-player/contracts/player-transpose-ui-contract.md) —
  контракт UI (меню, бейдж, JS-API, серверный контракт БЕЗ изменений)
- [quickstart.md](../../specs/101-audio-transpose-player/quickstart.md) —
  7 сценариев ручной валидации (SC-001..SC-006 + FR-018)
- `webvue3/src/player/KaraokePlayer.js` — реализация (меню «Тональность»,
  `_ensurePitchShift`, `setTranspose`, `_renderTransposeBadge`,
  `_restoreTranspose`/`_saveTranspose`)
- `webvue3/package.json` — зависимость `tone`