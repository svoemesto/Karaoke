---

description: "Task list for feature 101-audio-transpose-player"
---

# Tasks: Транспонирование аудио в онлайн-плеере (админка)

**Input**: Design documents from `/specs/101-audio-transpose-player/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/player-transpose-ui-contract.md, quickstart.md

**Tests**: Тестов не запрашивалось (CI-тестов в проекте нет, см. AGENTS.md). Валидация — ручная по `quickstart.md`. Тестовые задачи не генерируются.

**Organization**: Tasks grouped by user story (US1 P1 — выбор тональности; US2 P2 — бейдж; US3 P3 — per-song персистентность). Бэкенд НЕ меняется; все правки в `webvue3/src/player/KaraokePlayer.js` (+ per-feature документ).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app, frontend-only**: `webvue3/` (admin SPA). Бэкенд (`karaoke-app`, `karaoke-web`) НЕ меняется — `data.key` уже отдаётся обоими playerdata-эндпоинтами без изменений (см. data-model.md).
- Основной файл правок: `webvue3/src/player/KaraokePlayer.js`.
- Per-feature документ: `docs/features/player-transpose.md`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: npm-зависимость pitch-shift библиотеки (Tone.js, см. research.md §3a) и подготовка bundle.

- [X] T001 Добавить `tone` в `webvue3/package.json` dependencies (ESM, tree-shakeable), запустить `cd webvue3 && npm install` и зафиксировать `package-lock.json`. Версия — последняя стабильная Tone.js v15+.
- [X] T002 [P] Проверить размер bundle после добавления tone: `cd webvue3 && npm run build`, убедиться что tree-shaking вытащил только `PitchShift` + зависимости (ожидается ~40-60 KB gzip, не вся Tone.js). При превышении +100 KB gzip — записать находку в research.md и рассмотреть альтернативу (SoundTouch.js).

**Checkpoint**: Tone.js доступен для импорта в `webvue3/src/player/KaraokePlayer.js`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pitch-shift аудио-инфраструктура (map-based, по стемам), на которую опираются ВСЕ user stories. БЕЗ этой фазы ни меню (US1), ни бейдж (US2), ни персистентность (US3) не имеют аудио-эффекта.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 В `webvue3/src/player/KaraokePlayer.js` конструктора/`init()`: привязать Tone.js к существующему `AudioContext` плеера (`Tone.setContext(this.audioCtx)` ИЛИ создавать `Tone.PitchShift` с явным `{ context: this.audioCtx }`). Цель: Tone-узлы живут в том же аудио-контексте, что `accSource`/`vocSource`/`accGain`/`vocGain`. Проверить, что `audioCtx` уже создан на момент вызова.
- [X] T004 В `webvue3/src/player/KaraokePlayer.js`: feature-detect `_transposeSupported`. Попытаться создать тестовый `Tone.PitchShift({ context: this.audioCtx, pitch: 0 })` в `init`; если выбросило — `_transposeSupported = false` (используется US1 для блокировки подменю, FR-018). Dispose тестовый узел сразу после. JSDoc на `_transposeSupported` с `@see docs/features/player-transpose.md`.
- [X] T005 В `webvue3/src/player/KaraokePlayer.js`: добавить поле `_pitchShifts = new Map()` (Map<stemKey, Tone.PitchShift>) и метод `_ensurePitchShift(stemKey)`: если `_pitchShifts.has(stemKey)` — вернуть существующий; иначе `new Tone.PitchShift({ context: this.audioCtx, pitch: this._transpose })`, `set(stemKey, ps)`, вернуть. Lazy-создание по стему — добавление bass/drums в будущем = вызов `_ensurePitchShift('bass')` без изменения `setTranspose`. JSDoc на `_ensurePitchShift` с `@see docs/features/player-transpose.md`.
- [X] T006 В `webvue3/src/player/KaraokePlayer.js`, метод `_startAudio(offset)`: для каждого проигрываемого стема (acc, voc — в первой реализации; см. data-model.md аудио-граф) вставить pitch-shift узел между source и gain: `accSrc.connect(this._ensurePitchShift('acc')); this._ensurePitchShift('acc').connect(this.accGain)` (аналогично voc). `accSrc.playbackRate.value = this._playbackRate` — БЕЗ изменений (темп не затрагивается pitch-shift'ом). Сохранить существующий DEMO-bufOffset-logic без изменений. JSDoc на `_startAudio` обновить с упоминанием pitch-shift wiring.
- [X] T007 В `webvue3/src/player/KaraokePlayer.js`: добавить поле `_transpose = 0` (текущий сдвиг для активной песни, Integer −12..+12) и public-метод `setTranspose(n)` с JSDoc (`@see docs/features/player-transpose.md`): валидация `Number.isInteger(n) && n >= -12 && n <= 12`, иначе no-op (как `setPlaybackRate`); `this._transpose = n`; `for (const ps of this._pitchShifts.values()) ps.pitch = n` (применить ко всем стемам в Map синхронно, FR-005); вызвать `_updateTransposeMenu()` и `_saveTranspose()`. Бесшовно — НЕ трогает `accSource`/`vocSource` (узлы живы, `pitch` меняется на инстансах). Если `pitch`-transition щёлкает — `ps.pitch.rampTo(n, 0.02)`.
- [X] T008 В `webvue3/src/player/KaraokePlayer.js`: добавить public getter `get transpose() { return this._transpose }` с JSDoc (`@see docs/features/player-transpose.md`).

**Checkpoint**: Foundation ready — pitch-shift применяется ко всем стемам через `setTranspose`; user story implementation can now begin.

---

## Phase 3: User Story 1 - Выбор тональности транспонирования (Priority: P1) 🎯 MVP

**Goal**: Пользователь открывает меню плеера → «Тональность» → выбирает сдвиг ±12 полутонов; аудио немедленно транспонируется без остановки, синхронно по всем стемам (acc + voc).

**Independent Test**: Открыть плеер песни с `key=Am`, открыть меню, выбрать «+3» — услышать повышение на 3 полутона; выбрать «0» — вернулась исходная. Воспроизведение не прерывается (см. quickstart.md SC-001).

### Implementation for User Story 1

- [X] T009 [US1] В `webvue3/src/player/KaraokePlayer.js`: добавить статический массив `KaraokePlayer.TRANSPOSE_OPTIONS = Array.from({length:25}, (_,i) => i - 12)` (−12 … +12). Параллель существующему `SPEED_OPTIONS`. JSDoc на `TRANSPOSE_OPTIONS` с `@see docs/features/player-transpose.md`.
- [X] T010 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `_transposeLabel(n)` (новый, JSDoc с `@see docs/features/player-transpose.md`): вычисляет подпись пункта/бейджа от `this.data?.key` и `n`. Хроматическая шкала `['C','C#','D','D#','E','F','F#','G','G#','A','A#','B']`; разбор базовой `key` (нота + суффикс `m`/``); `index = (baseIndex + n) mod 12` (нормализация с отрицательным `n`); подпись `+N`/`-N`/`0` + ` <note><suffix>` если `key` непуст, иначе только `+N`/`-N`/`0` (FR-013). При невозможности разобрать `key` — аудио-сдвиг всё равно работает, подпись только сдвигом. Возвращает строку для бейджа (без скобок) ИЛИ строку для меню (в скобках) — параметр-флаг `forMenu`.
- [X] T011 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `_buildUI` (HTML-шаблон): добавить блок `#kp-menu-transpose` (параллель `#kp-menu-speed`) — родительский пункт «Тональность» с `#kp-transpose-label`, подменю `#kp-submenu-transpose` с 25 `<div data-transpose="-12">…+12</div>`, текст каждого — `this._transposeLabel(n, true)` (в скобках). CSS-классы переиспользовать от speed-блока (hover/click). При `_transposeSupported === false` — пункты рендерятся заблокированными (`pointer-events:none; opacity:.5`) + над подменю подсказка «Браузер не поддерживает» (FR-018).
- [X] T012 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `_buildMenu()`: добавить wiring (параллельно `speedItem`): `const transposeItem = this.container.querySelector('#kp-menu-transpose'); const transposeSubmenu = this.container.querySelector('#kp-submenu-transpose');` — toggle `kp-submenu-open` по клику на `transposeItem`; для каждого `[data-transpose]` в `transposeSubmenu` — `el.addEventListener('click', () => { if (this._transposeSupported) { this._closeMenu(); this.setTranspose(Number(el.dataset.transpose)) } })`. Вызвать `this._updateTransposeMenu()` в конце `_buildMenu`.
- [X] T013 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `_updateTransposeMenu()` (новый, параллель `_updateSpeedMenu`, JSDoc с `@see docs/features/player-transpose.md`): обновить `#kp-transpose-label` = `this._transposeLabel(this._transpose, false)` (краткая подпись); для каждого `#kp-submenu-transpose [data-transpose]` — `active = Number(el.dataset.transpose) === this._transpose`; `el.style.background = active ? '#08f' : 'none'; el.style.color = active ? '#fff' : '#eee'`. Пересчитать подписи пунктов (текст) от текущего `data.key` — вызывается из `setTranspose` и после `init`/`playSong` (когда `data.key` может измениться).
- [X] T014 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `init()` (после готовности `this.data`): вызвать `_updateTransposeMenu()` (пересчитать подписи от `data.key`, подсветить текущий сдвиг). Убедиться, что `_transpose` уже инициализирован значением по умолчанию 0 (per-song восстановление — задача US3, здесь только текущее состояние).
- [X] T015 [US1] В `webvue3/src/player/KaraokePlayer.js`, метод `_saveTranspose()` (новый, JSDoc с `@see docs/features/player-transpose.md`): `try { localStorage.setItem('kp_transpose_' + this.data.id, String(this._transpose)) } catch {}` — per-song по `data.id` (FR-011). НЕ расширять существующий `LS_SETTINGS_KEY`/`_savePersistedSettings` (там глобальные настройки). Вызывается из `setTranspose`.

**Checkpoint**: User Story 1 функционален — меню «Тональность», выбор ±12, бесшовное применение ко всем стемам. Валидация: quickstart.md SC-001, SC-002, SC-002a, SC-006.

---

## Phase 4: User Story 2 - Бейдж тональности на экране плеера (Priority: P2)

**Goal**: При сдвиге ≠ 0 в правом верхнем углу экрана — постоянный синий бейдж `+3 Cm` (или `+3` при пустом key), под бейджем скорости; при сдвиге 0 — исчезает.

**Independent Test**: Выбрать «+3» — синий бейдж появляется справа сверху под бейджем скорости; выбрать «0» — исчезает (см. quickstart.md SC-004).

### Implementation for User Story 2

- [X] T016 [US2] В `webvue3/src/player/KaraokePlayer.js`, метод `_renderTransposeBadge(ctx, W, H)` (новый, параллель `_renderSpeedBadge`, JSDoc с `@see docs/features/player-transpose.md`): если `this._transpose === 0` — `return` (FR-010). Цвет `#08f` (синий, FR-008), фон `rgba(0,0,0,0.55)`, шрифт/форма/`roundRect` — как в `_renderSpeedBadge`. Подпись = `this._transposeLabel(this._transpose, false)` (без скобок: `+3 Cm` / `+3`). Позиция Y: если `_playbackRate !== 1` (бейдж скорости активен) — `margin + speedBadgeHeight + gap` (под бейджем скорости, FR-009); иначе `margin` (как у speed-бейджа). Позиция X: `W - boxW - margin` (правый край). Чтобы знать высоту бейджа скорости — либо вынести её расчёт в общее состояние, либо `_renderTransposeBadge` вызывается после `_renderSpeedBadge` и сам пересчитывает (формула идентична).
- [X] T017 [US2] В `webvue3/src/player/KaraokePlayer.js`, метод `_renderFrame` (или где вызывается `_renderSpeedBadge`): добавить вызов `this._renderTransposeBadge(ctx, W, H)` **после** `this._renderSpeedBadge(ctx, W, H)`, чтобы позиционирование под бейджем скорости было корректным (FR-009 — не перекрывать).

**Checkpoint**: User Story 2 функционален — бейдж тональности под бейджем скорости. Валидация: quickstart.md SC-004.

---

## Phase 5: User Story 3 - Сохранение выбора тональности для конкретной песни (Priority: P3)

**Goal**: Сдвиг хранится per-song (localStorage `kp_transpose_<songId>`); новая песня стартует в базовой (сдвиг 0); возврат к прежней — восстанавливает её сдвиг.

**Independent Test**: Выбрать «+3» для песни A, открыть песню B — B стартует в базовой; вернуться к A — восстанавливается «+3» (см. quickstart.md SC-005).

### Implementation for User Story 3

- [X] T018 [US3] В `webvue3/src/player/KaraokePlayer.js`, метод `init()` (после готовности `this.data`): восстановить `this._transpose` из localStorage: `const saved = localStorage.getItem('kp_transpose_' + this.data.id); this._transpose = saved !== null && Number.isInteger(Number(saved)) && Number(saved) >= -12 && Number(saved) <= 12 ? Number(saved) : 0`. Применить к существующим pitch-shift узлам: `for (const ps of this._pitchShifts.values()) ps.pitch = this._transpose`. Это решает восстановление при перезагрузке той же песни (FR-011).
- [X] T019 [US3] В `webvue3/src/player/KaraokePlayer.js`, метод `playSong(songId, token, authToken, autoplay)`: ПЕРЕД `await this.init()` — `this._transpose = 0; this._updateTransposeMenu()` (сброс к базовой, чтобы до загрузки данных новой песни не светился старый сдвиг). ПОСЛЕ `await this.init()` (когда `this.data.id` нового трека загружен) — `init()` уже восстановит `this._transpose` из localStorage по новому `data.id` (см. T018), и `_updateTransposeMenu()` пересчитает подписи от нового `data.key`. Результат: другая песня стартует в своём (или 0 если не сохранён) — FR-012.
- [X] T020 [US3] В `webvue3/src/player/KaraokePlayer.js`: убедиться, что `_saveTranspose()` (T015) вызывается ТОЛЬКО из `setTranspose` (явный выбор пользователя), а НЕ из `init`/`playSong` (восстановление не должно перезаписывать localStorage мусором). Проверить отсутствие случайных вызовов `_saveTranspose` в путях восстановления.

**Checkpoint**: User Story 3 функционален — per-song персистентность, сброс при смене песни, восстановление. Валидация: quickstart.md SC-005.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Per-feature документ (Constitution FR-009), lint-чистота, деградация (FR-018), итоговая валидация.

- [X] T021 [P] Создать `docs/features/player-transpose.md` по структуре `tools/check-feature-doc.sh`: секции `## Что делает`, `## Зачем`, `## Как работает` (map-based `_pitchShifts`, Tone.PitchShift, per-song localStorage), `## Инварианты` (сдвиг ко всем стемам; темп не меняется; бейдж под бейджем скорости), `## Известные ловушки` (detune меняет темп — не использовать; per-song vs global speed; рассинхрон если не все стемы в Map), `## Ссылки` (spec.md, plan.md, research.md, contracts/player-transpose-ui-contract.md). Проверить: `bash tools/check-feature-doc.sh docs/features/player-transpose.md`.
- [X] T022 [P] Добавить запись о новом per-feature документе в таблицу `docs/features/README.md` (11 → 12 фич, см. AGENTS.md Q&A «Как добавить per-feature документ»).
- [X] T023 Запустить линт-проверки из корня репо: `cd webvue3 && npm run lint:check && cd ..` (ESLint без новых нарушений — или зафиксировать в `webvue3/.eslint-baseline.json` если необходимо), `bash tools/check-jsdoc-coverage.sh webvue3` (100% coverage — новые public-методы `setTranspose`/`transpose` и JSDoc-комментарии с `@see`). Все должны быть зелёными.
- [X] T024 Проверить FR-018 (деградация без поддержки): эмулировать отсутствие поддержки — временно закомментировать создание `Tone.PitchShift` в T004 (или DevTools-эмуляция) — убедиться, что подменю «Тональность» видно, пункты заблокированы, подсказка «Браузер не поддерживает» показана, песня играет в базовой. Восстановить код.
- [X] T025 Прогнать все сценарии валидации из `specs/101-audio-transpose-player/quickstart.md` вручную: SC-001 (≤1 с, без остановки), SC-002 (без сетевых запросов), SC-002a (все стемы синхронно), SC-003 (синхронность ≤100 мс), SC-004 (бейдж под бейджем скорости), SC-005 (per-song персистентность), SC-006 (пустой key / нестандартный / все типы), FR-018 (деградация). Все должны пройти.
- [X] T026 Проверить bundle size повторно после всех правок: `cd webvue3 && npm run build`, убедиться что Tone.js tree-shaking работает (прирост gzip в разумных пределах). Сравнить с T002.
- [X] T027 Проверить интеграцию с существующей фичей скорости: одновременно выбрать скорость 0.75x и тональность +3 — оба эффекта применяются одновременно и независимо (FR-014), оба бейджа видны (FR-009), `_getCurrentTime()` формула корректна (темп от `playbackRate`, не затронут pitch-shift). Валидация: quickstart.md SC-004 шаг 4-5.

**Checkpoint**: Фича готова к PR. Per-feature документ создан, lint зелёный, валидация пройдена.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — `npm install tone`, bundle check.
- **Foundational (Phase 2)**: Depends on Phase 1 (Tone.js установлен) — BLOCKS все user stories. Создаёт `_pitchShifts` Map, `_ensurePitchShift`, `setTranspose`, `_startAudio` wiring.
- **User Story 1 (Phase 3)**: Depends on Foundational — добавляет меню, подписи, `_saveTranspose`. MVP.
- **User Story 2 (Phase 4)**: Depends on US1 (`_transpose` и `_transposeLabel` существуют) — бейдж использует те же данные.
- **User Story 3 (Phase 5)**: Depends on US1 (`setTranspose`/`_saveTranspose`/`_updateTransposeMenu` существуют) — добавляет восстановление/сброс.
- **Polish (Phase 6)**: Depends on всех user stories — per-feature документ описывает готовую реализацию, lint проверяет весь код, валидация прогоняет все сценарии.

### User Story Dependencies

- **User Story 1 (P1)**: MVP. Можно начать после Foundational. Независимо тестируем (меню + применение ко всем стемам).
- **User Story 2 (P2)**: Технически зависит от US1 (`_transpose`/`_transposeLabel`), но independently testable (бейдж виден/скрыт по сдвигу).
- **User Story 3 (P3)**: Технически зависит от US1 (`setTranspose`/`_saveTranspose`), но independently testable (сброс/восстановление per-song).

### Within Each User Story

- Infrastructure (Map, `_ensurePitchShift`) → wiring (`_startAudio`) → API (`setTranspose`) → UI (меню, бейдж) → персистентность.
- Все задачи в одной user story — один файл (`KaraokePlayer.js`), кроме per-feature документа (T021) и `docs/features/README.md` (T022).

### Parallel Opportunities

- **Phase 1**: T001 (npm install) блокирует T002 (bundle check) — последовательно.
- **Phase 2**: T003–T008 — один файл (`KaraokePlayer.js`), НЕ параллельны (конфликт редактирования). Последовательны.
- **Phase 3 (US1)**: T009–T015 — один файл, последовательны (каждая следующая зависит от предыдущей: `TRANSPOSE_OPTIONS` → `_transposeLabel` → `_buildUI` → `_buildMenu` → `_updateTransposeMenu` → `init` → `_saveTranspose`).
- **Phase 4 (US2)**: T016 → T017 (T17 вызывает T16-метод) — последовательны.
- **Phase 5 (US3)**: T018 → T019 → T020 — последовательны (все правки `init`/`playSong`).
- **Phase 6 (Polish)**: T021 (per-feature doc) ∥ T022 (README) — [P], разные файлы. T023–T027 — проверки, последовательны после кода.
- **Между stories**: US1 → US2 → US3 строго последовательно (зависимости данных), НЕ параллельны.

---

## Parallel Example: Phase 6 (Polish)

```bash
# Запускать параллельно (разные файлы, нет зависимостей):
Task: "Создать docs/features/player-transpose.md per tools/check-feature-doc.sh" (T021)
Task: "Добавить запись в docs/features/README.md таблицу" (T022)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002) — Tone.js установлен.
2. Complete Phase 2: Foundational (T003–T008) — pitch-shift инфраструктура готова.
3. Complete Phase 3: User Story 1 (T009–T015) — меню «Тональность», применение ко всем стемам.
4. **STOP and VALIDATE**: quickstart.md SC-001, SC-002, SC-002a, SC-006 — выбор сдвига, без остановки, без запросов, все стемы, пустой key.
5. Demo MVP пользователю.

### Incremental Delivery

1. Setup + Foundational → Foundation ready (pitch-shift применяется через `setTranspose`, но без UI).
2. Add User Story 1 → Test independently → Demo (MVP: меню + транспонирование всех стемов).
3. Add User Story 2 → Test independently → Demo (бейдж под бейджем скорости).
4. Add User Story 3 → Test independently → Demo (per-song персистентность, сброс при смене песни).
5. Polish → per-feature документ, lint, полная валидация, PR с CI 7/7 (AGENTS.md «CI-gate для master»).

### Single-Developer (последовательно)

Все задачи — один файл (`KaraokePlayer.js`) в US-фазах, плюс npm и docs. Последовательное выполнение по фазам; параллельность только в Polish (T021 ∥ T022).

---

## Notes

- [P] задачи — разные файлы, без зависимостей. В US-фазах параллельности почти нет (один `KaraokePlayer.js`).
- [Story] label: US1 (меню + применение), US2 (бейдж), US3 (перcистентность). Setup/Foundational/Polish — без story-label.
- Бэкенд НЕ меняется (data-model.md §5 — серверный контракт БЕЗ изменений; `data.key` уже отдаётся).
- Per-feature документ `docs/features/player-transpose.md` обязателен (Constitution FR-009 + AGENTS.md Q&A).
- JSDoc на все новые public-методы/свойства `KaraokePlayer` (`setTranspose`, `transpose`, `TRANSPOSE_OPTIONS`) с `@see docs/features/player-transpose.md` (Constitution VI FR-006).
- ESLint без новых нарушений (Constitution VI FR-007); при необходимости — baseline `webvue3/.eslint-baseline.json`.
- Валидация — ручная по `quickstart.md` (CI-тестов нет). PR — через feature-ветку `101-audio-transpose-player` + `gh pr create --base master` + дождаться CI 7/7 (AGENTS.md «CI-gate для master»).
- Commit-сообщения на русском, `area: краткое описание` (Constitution «Git»).