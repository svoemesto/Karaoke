---
description: "Task list — багфикс потери маркеров после «Точные маркеры → Apply → Save → reopen»"
---

# Tasks: Спецтеги — сохранение маркеров после «Точные маркеры → Apply → Save → reopen»

**Input**: Design documents from `/specs/015-fix-spec-tags-marker-loss-on-reopen/`
- `plan.md` (required) — Technical Context + Constitution Check 7/7 PASS + Project Structure
- `spec.md` (required) — 3 user stories: US1 (P1), US2 (P2), US3 (P3)
- `research.md` — локализация первопричины (13-шаговый trace + 4-шаговый fix)
- `data-model.md` — внутреннее состояние `SubsEdit.vue`, 5 инвариантов
- `contracts/README.md` — наследует `specs/010-lyrics-spec-tags/contracts/tag-registry.md`
- `quickstart.md` — 7 ручных сценариев A-G

**Tests**: НЕ запрошены явно (см. `plan.md` "Testing" + спека 010 `tasks.md` T024 — «ручная проверка в браузере»). Юнит-тестов на Vue-стороне `SubsEdit.vue` в проекте нет; `quickstart.md` этой спеки формализует ЧТО проверяется вручную.

**Organization**: Задачи сгруппированы по user story из `spec.md` (US1=P1 reopen-fix, US2=P2 updateMarkersBySyllables-fix, US3=P3 защитный сценарий с уведомлением). Все правки — в **одном файле** `webvue3/src/components/Songs/edit/SubsEdit.vue`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные секции одного файла или разные файлы без пересечения)
- **[Story]**: какой user story (US1/US2/US3) принадлежит задача
- В описаниях — точные пути и номера строк из `research.md` §2

## Path Conventions

- **Единственный модифицируемый файл**: `webvue3/src/components/Songs/edit/SubsEdit.vue` (~30-50 строк diff в худшем случае)
- **Без изменений**: backend (`karaoke-app/`, `karaoke-web/`), `karaoke-public/`, `alignment-ml/`, схема БД, Vuex-стор (`webvue3/src/components/Songs/store.js`), контракт `specs/010-lyrics-spec-tags/contracts/tag-registry.md`
- **Регрессия**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SpecTagsTest.kt` + `WhisperMarkerAlignerSpecTagsTest.kt` — должны проходить без изменений

---

## Phase 1: Setup (Подготовка к фиксу)

**Purpose**: убедиться, что мы в нужной ветке, baseline-файлы на месте, существующие тесты проходят (регрессия).

- [x] T001 [P] Проверить `git status` — должна быть чистая ветка `015-fix-spec-tags-marker-loss-on-reopen` от свежего `master` (`git log --oneline -1` показывает `master` HEAD)
- [x] T002 [P] Убедиться, что `webvue3/src/components/Songs/edit/SubsEdit.vue` существует и `wc -l` показывает ~5711 строк (текущий размер файла на момент планирования)
- [x] T003 Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.model.SpecTagsTest"` — должен пройти (регрессия для backend-контракта спецтегов)
- [x] T004 [P] Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.model.WhisperMarkerAlignerSpecTagsTest"` (если класс существует) — должен пройти

**Checkpoint**: среда готова, регрессионные тесты backend зелёные — можно приступать к фиксу `SubsEdit.vue`.

---

## Phase 2: Foundational (Защитные гарды)

**Purpose**: добавить **защитные** гарды (П2 из `research.md` §4.1 Шаг 3) в `syncMarkersFromSpecTags`, чтобы фикс US1 не сломал поведение в edge-cases. Эти правки минимальны, локальны, не зависят от US1 — могут идти параллельно.

- [x] T005 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в функции `syncMarkersFromSpecTags()` (метод, строки 3412-3466) добавить **самой первой строкой** тела функции (после `if (!this.wsRegions) return` на строке 3413) дополнительный гард:
  ```js
  if (this.sourceMarkers.length === 0) return
  ```
  с JSDoc-комментарием: «Защитный гард: если `sourceMarkers` ещё не заполнен из `loadedMarkers` (например, watcher `sourceText` сработал на первом открытии до загрузки маркеров из БД), не вставлять маркеры — иначе они окажутся в позиции 0 и сломают последующее наполнение `sourceMarkers` в `ws.on('decode')`».

**Checkpoint**: `syncMarkersFromSpecTags()` стал устойчив к вызову с пустым `sourceMarkers`. Сам по себе этот фикс НЕ устраняет наблюдаемый баг (для этого нужен Шаг 1+2 в US1) — это страховка от регрессии в будущем.

---

## Phase 3: User Story 1 — Маркеры переживают цикл Save→close→reopen на песне со спецтегами (Priority: P1) 🎯 MVP

**Goal**: на ПЕРВОМ открытии голоса со спецтегами в `SubsEdit.vue` все маркеры из БД (`loadedMarkers`) оказываются в `sourceMarkers` ДО того, как watcher на `sourceText` попытается сделать что-либо с `sourceMarkers`. Это устраняет наблюдаемый пользователем баг: «после Apply + Save + reopen маркеры пропадают, остаются только spec tag-маркеры».

**Independent Test**: пройти `quickstart.md` Сценарии A (баг-репро), B (регрессия без тегов), C (spec tag без маркера), D (spec tag на той же позиции), E (переключение голоса) — все должны давать описанный результат.

### Implementation for User Story 1

- [x] T006 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в `mounted()` (строки 2619-2657) **перенести загрузку маркеров из `ws.on('decode')` в тело `mounted()` и поставить её ДО `this.sourceText = await this.$store.getters.getSourceText(this.currentVoice)`**:
  - Сохранить строки `this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice)` (2624) и весь цикл заполнения `sourceMarkers` из `loadedMarkers` (2643-2654) — но вставить их **сразу после** строки 2624 (т.е. ещё до `this.indexTabsVariant = ...`).
  - Сразу после цикла добавить вызов `this.createBeatMarkers()` (он сейчас находится в `ws.on('decode')` после цикла, строка 2655) — перенести в `mounted()` после цикла.
  - В результате в `mounted()` порядок становится: `initWavesurfer()` → `isEditMode = true` → `loadedMarkers = await ...` → **НОВОЕ: заполнить `sourceMarkers` из `loadedMarkers` + `createBeatMarkers()`** → `sourceText = await ...` → регистрация `ws.on('decode', ...)` (без цикла загрузки маркеров) → ... остальное.
- [x] T007 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в обработчике `this.ws.on('decode', () => { ... })` (строки 2634-2657) **удалить цикл загрузки маркеров** (строки 2642-2656 целиком, включая условие `if (this.loadedMarkers.length > 0 && this.sourceMarkers.length === 0)`), оставив только:
  - `this.wsRegions.clearRegions()` — очистка регионов на re-decode (на случай повторного декодирования того же трека)
  - блок с `this.duration = this.ws.getDuration()` + `this.visibleStartTime/EndTime` (строки 2635-2637) — он не связан с маркерами
  - **Опционально**: добавить комментарий «Маркеры загружаются синхронно в `mounted()` (см. комментарий там) — здесь только очистка регионов и расчёт видимой области»
- [x] T008 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в watcher'е `currentVoice` (строки 2105-2138) **применить тот же приём**: заполнить `sourceMarkers` из `loadedMarkers` ДО `this.sourceText = await this.$store.getters.getSourceText(this.currentVoice)`. Текущий код уже делает почти правильную вещь (строки 2114-2136), но `this.sourceText` ставится ДО `this.loadedMarkers` — переставить местами:
  - Строка 2114 (`this.sourceText = await ...`) и строка 2115 (`this.loadedMarkers = await ...`) — поменять местами, чтобы `loadedMarkers` грузился первым
  - Цикл `for (let index = 0; index < this.loadedMarkers.length; ...)` (строки 2122-2133) и `this.createBeatMarkers()` (строка 2134) — оставить как есть (они уже заполняют `sourceMarkers` корректно)
  - Явный вызов `this.syncMarkersFromSpecTags()` в конце (строка 2136) — оставить как есть, он уже корректен
- [x] T009 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` обновить JSDoc-комментарий к `syncMarkersFromSpecTags()` (строки 3407-3411), чтобы явно отразить новый инвариант: «Вызывается СТРОГО после того, как `sourceMarkers` заполнен из `loadedMarkers` (в `mounted()` или в `currentVoice` watcher'е). На первом открытии голоса это гарантируется порядком присваиваний в `mounted()`. Защитный гард `if (this.sourceMarkers.length === 0) return` (Шаг 2 фикса) — страховка от вызова в неожиданных местах».
- [x] T010 [US1] В `webvue3/src/components/Songs/edit/SubsEdit.vue` обновить JSDoc-комментарий к `mounted()` (строки ~2619) — добавить пояснение о новом порядке: «Порядок присваиваний КРИТИЧЕН: `loadedMarkers` + заполнение `sourceMarkers` ДО `sourceText` (иначе watcher `sourceText` сработает с пустым `sourceMarkers` и спецтег-маркеры встанут в позицию 0, что сломает загрузку реальных маркеров из БД). См. спеку 015 / research.md §2».

**Checkpoint**: на ПЕРВОМ открытии `sourceMarkers` заполнен ДО срабатывания watcher'а `sourceText`. `syncMarkersFromSpecTags()` отрабатывает корректно с уже заполненным `sourceMarkers`: spec tag-маркеры добавляются аддитивно, реальные маркеры из БД не теряются. Песни БЕЗ спецтегов работают байт-в-байт как раньше (регрессия отсутствует).

---

## Phase 4: User Story 2 — Рассинхрон `sourceSyllables` и syllables-маркеров не приводит к потере (Priority: P2)

**Goal**: в `updateMarkersBySyllables()` НЕ обнулять `label` syllables-маркера, если `index >= sourceSyllables.length` (т.е. если syllables-маркеров в `sourceMarkers` больше, чем `sourceSyllables` показывает). Лишние syllables-маркеры остаются нетронутыми по `label` и `color`. Это защита от US2 спеки 015: рассинхрон счётчика слогов (старые маркеры из БД, новый `getSyllables` для текста со спецтегом и т.п.) не должен молча стирать данные.

**Independent Test**: пройти `quickstart.md` Сценарий F (ручная правка `~newline~`); дополнительно — ручной сценарий US2: открыть песню, в `sourceText` которой есть `~newline~`, принудительно добавить в БД лишний syllables-маркер (через SQL или иной способ), открыть редактор — лишний syllables-маркер НЕ должен превратиться в пустой (`label === ''`).

### Implementation for User Story 2

- [ ] T011 [US2] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в `updateMarkersBySyllables()` (строки 3348-3406) **заменить блок «if (index >= this.sourceSyllables.length)»** (строки 3370-3375):
  ```js
  // БЫЛО (текущее поведение — стирает label):
  if (index >= this.sourceSyllables.length) {
    marker.label = ''
    marker.region.setContent(this.getRegionContentFromMarker(marker))
    this.sourceMarkers.splice(i, 1, marker)
    counter++
  }
  ```
  на:
  ```js
  // СТАЛО (защитное поведение — оставляет лишние syllables-маркеры нетронутыми):
  if (index >= this.sourceSyllables.length) {
    // Рассинхрон счётчика слогов (например, старые маркеры в БД при изменённом
    // getSyllables для текста со спецтегом) — НЕ обнуляем label, чтобы не терять
    // данные без явного уведомления. Лишние syllables-маркеры остаются на месте
    // с оригинальным label/color; UI отобразит их как было. См. FR-011 спеки 015.
    index++
    color = MARKER_COLOR_SYLLABLES
    prevEndOfLine = false
    continue
  }
  ```
  **Важно**: `continue` требует, чтобы `for` стал `for...of this.sourceMarkers.entries()` или явная трансформация. Альтернативный вариант без `continue` — заменить тело `if` на инкремент `index` + `color` + `prevEndOfLine` БЕЗ модификации `marker`, и оставить штатное `index++/color=.../prevEndOfLine=...` ниже (тогда нужно убрать `else` и переписать структуру if/else — см. детали в коде).
- [ ] T012 [P] [US2] В `webvue3/src/components/Songs/edit/SubsEdit.vue` обновить JSDoc-комментарий к `updateMarkersBySyllables()` (строки ~3348) — добавить явный инвариант: «Рассинхрон `sourceSyllables.length` с числом syllables-маркеров в `sourceMarkers` НЕ приводит к потере данных: лишние syllables-маркеры остаются нетронутыми (label/color не сбрасываются). Это защитное поведение (US2 спеки 015), т.к. в отличие от auto-разметки, ручной редактор НЕ должен «исправлять» чужие данные молча».

**Checkpoint**: US2 — лишние syllables-маркеры не теряются при рассинхроне. Защита актуальна для случая «старые маркеры в БД + новый `getSyllables`» (например, после деплоя фикса спецтегов, если в БД остались маркеры из старого билда).

---

## Phase 5: User Story 3 — Явное сообщение пользователю при подозрительной потере маркеров (Priority: P3)

**Goal**: если по какой-то причине `applyAutoMarkersToEditor`, `Save` или другая операция массово меняет `sourceMarkers` — пользователь видит ненавязчивое уведомление вида «Маркеры были обновлены: было X, стало Y. Если что-то пропало — отмените (Ctrl+Z) или пересохраните».

**Independent Test**: ручной сценарий — инициировать ситуацию, при которой `sourceMarkers.length` уменьшается/увеличивается более чем на количество добавленных `beat`-маркеров; убедиться, что появляется уведомление.

### Implementation for User Story 3

- [ ] T013 [US3] В `webvue3/src/components/Songs/edit/SubsEdit.vue` **добавить data-поле** `markerCountBeforeApply: null` (в `data()`, рядом с `autoMarkersDebug`, строки ~1449). Используется для сравнения «было → стало» при `applyAutoMarkersToEditor`.
- [ ] T014 [US3] В `webvue3/src/components/Songs/edit/SubsEdit.vue` в `applyAutoMarkersToEditor()` (строки 4525-4536) **в самом начале (до `this.wsRegions.clearRegions()`) сохранить** `this.markerCountBeforeApply = this.sourceMarkers.length`, а **в конце (после `this.createBeatMarkers()`) добавить проверку и вызов уведомления**:
  ```js
  const expectedDelta = this.createBeatMarkers.length // приблизительно; точное значение — из фактического числа beat-маркеров
  const actualDelta = this.sourceMarkers.length - this.markerCountBeforeApply
  if (Math.abs(actualDelta) > 5) {  // порог — «подозрительно много»
    this.customConfirmParams = {
      header: 'Маркеры были обновлены',
      body: `Было ${this.markerCountBeforeApply} маркеров, стало ${this.sourceMarkers.length}. ` +
            `Если что-то пропало — закройте редактор без Save, затем откройте снова.`,
      isSimple: true,
    }
    this.isCustomConfirmVisible = true
  }
  ```
  **Точные пороги и формула `expectedDelta` — на усмотрение реализатора** (нужно учесть, что `applyAutoMarkersToEditor` добавляет `beat`-маркеры через `createBeatMarkers`, число которых зависит от BPM-маркера и длительности трека; разумный порог — `> 5` от ожидаемого).
- [ ] T015 [P] [US3] В `webvue3/src/components/Songs/edit/SubsEdit.vue` добавить JSDoc-комментарий к новому data-полю `markerCountBeforeApply`: «Используется в `applyAutoMarkersToEditor()` для защитного уведомления (US3 спеки 015): если число маркеров изменилось более чем на ожидаемое число `beat`-маркеров, показать пользователю, что произошла подозрительная операция».

**Checkpoint**: US3 — пользователь видит уведомление при подозрительной потере/добавлении маркеров. Это **защитный сценарий** — может быть пропущен в первом раунде (MVP US1+US2 достаточно для устранения наблюдаемого бага).

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: финальная проверка, что фикс не сломал ничего, JSDoc актуален, ESLint чистый, ручные сценарии прошли.

- [x] T016 [P] Запустить `cd webvue3 && npm run lint:check` — должно быть зелёным (без новых baseline-нарушений)
- [x] T017 [P] Запустить `./gradlew ktlintCheck` — должно быть зелёным (Kotlin не затрагивается, но это регрессия для уверенности)
- [x] T018 [P] Запустить `cd webvue3 && npm run build` — должен собраться без ошибок и warnings (Smoke test сборки)
- [x] T019 [P] Запустить `bash tools/check-jsdoc-coverage.sh webvue3` — KDoc/JSDoc coverage должен остаться 100% (или в пределах baseline)
- [x] T020 Запустить `./gradlew :karaoke-app:test --tests "com.svoemesto.karaokeapp.model.SpecTagsTest" --tests "com.svoemesto.karaokeapp.model.WhisperMarkerAlignerSpecTagsTest"` — должны пройти (регрессия backend-контракта спецтегов)
- [x] T021 [P] Обновить `specs/010-lyrics-spec-tags/tasks.md` — поставить `[X]` (завершено) на T024 («Ручная проверка в браузере по `quickstart.md` Сценариям D и E»), заменив его ссылкой на `specs/015-fix-spec-tags-marker-loss-on-reopen/quickstart.md` (новые сценарии A-G расширяют и формализуют старую проверку)
- [x] T022 [P] Добавить запись в `docs/architecture-notes.md` (Pass 26+) — короткий changelog-блок про 015-фикс: первопричина (асимметрия watcher'ов в `mounted()`), что изменилось (порядок в `mounted()` + `currentVoice` watcher'е, опциональные гарды), какие сценарии покрывает (`quickstart.md` A-G), какие задачи остаются (`T023`/`T024` из спеки 010 закрыты этим PR; `T025`/`T026` — KDoc/JSDoc-синхронизация)
- [x] T023 [P] В `AGENTS.md` секции «Q&A (Pass 24)» добавить новый вопрос «Какая первопричина потери маркеров на reopen в `SubsEdit.vue`?» с кратким ответом (1-2 предложения) и ссылкой на `specs/015-fix-spec-tags-marker-loss-on-reopen/`
- [ ] T024 Пройти `quickstart.md` Сценарий A (баг-репро: открыть → Точные маркеры → Apply → Save → close → reopen) — все маркеры должны быть на месте, без дубликатов
- [ ] T025 Пройти `quickstart.md` Сценарий B (регрессия: та же последовательность на песне БЕЗ спецтегов) — поведение должно быть байт-в-байт как до фикса
- [ ] T026 Пройти `quickstart.md` Сценарии C, D, E, F, G — все должны проходить

**Checkpoint**: фикс готов к PR. CI 7/7 PASS, регрессионные сценарии зелёные, документация синхронизирована.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно стартовать сразу
- **Foundational (Phase 2)**: зависит от Setup — единственная задача T005 (гард в `syncMarkersFromSpecTags`)
- **User Story 1 (Phase 3)**: зависит от Foundational (T005) — основной фикс
- **User Story 2 (Phase 4)**: зависит от Foundational (T005) — может идти параллельно с US1, **но редактирует тот же файл** (`SubsEdit.vue`) в разных секциях — рекомендуется ПОСЛЕ US1
- **User Story 3 (Phase 5)**: зависит от Foundational (T005) — **может быть пропущен** в MVP; если делается, рекомендуется ПОСЛЕ US1
- **Polish (Phase 6)**: зависит от всех желаемых user story

### User Story Dependencies

- **User Story 1 (P1)**: MVP, не зависит от других US. Покрывает наблюдаемый баг целиком.
- **User Story 2 (P2)**: Независим от US1 логически, но редактирует ту же функцию `updateMarkersBySyllables` в том же файле. Можно мержить отдельно после US1.
- **User Story 3 (P3)**: Независим. Защитный сценарий. Можно пропустить в первом раунде.

### Within Each User Story

- Тесты НЕ пишутся (Vue-юнит-тестов в проекте нет; проверка — ручная через `quickstart.md`).
- Внутри US1: T006 + T007 + T008 — последовательно в одном файле (порядок критичен, т.к. это одна и та же секция кода). T009 + T010 — JSDoc, можно в конце.
- Внутри US2: T011 — основная правка; T012 — JSDoc.
- Внутри US3: T013 + T014 — последовательно (одна и та же функция); T015 — JSDoc.

### Parallel Opportunities

- **T001 + T002 + T003 + T004** (Phase 1) — все `[P]`, можно параллельно
- **T016 + T017 + T018 + T019 + T020** (Phase 6) — все `[P]`, можно параллельно (это просто запуск линтеров/тестов/сборки)
- **T021 + T022 + T023** (Phase 6) — `[P]`, можно параллельно (это разные файлы документации)
- **T024 + T025 + T026** (Phase 6) — `[P]`, можно параллельно (разные сценарии `quickstart.md`)

**Критическая сериализация внутри US1** (T006 → T007 → T008): все три задачи редактируют ОДИН файл `SubsEdit.vue` в разных функциях, но логически они образуют **один** атомарный фикс. Рекомендуется делать их в одной ветке последовательно, в одном коммите (или в трёх коммитах сразу).

---

## Parallel Example: User Story 1 (MVP)

```bash
# Все Setup-задачи — параллельно (но тривиальные):
Task: "T001 — git status проверка"
Task: "T002 — wc -l SubsEdit.vue"
Task: "T003 — backend regression test SpecTagsTest"
Task: "T004 — backend regression test WhisperMarkerAlignerSpecTagsTest"

# Foundational — один гард (T005), перед US1:
Task: "T005 — гард в syncMarkersFromSpecTags"

# US1 — последовательно в одном файле (атомарный фикс):
Task: "T006 — загрузка loadedMarkers в mounted() ДО sourceText"
Task: "T007 — убрать цикл загрузки из ws.on('decode')"
Task: "T008 — то же в currentVoice watcher'е"
Task: "T009 — JSDoc для syncMarkersFromSpecTags"
Task: "T010 — JSDoc для mounted()"

# После US1 (опционально) — US2 и US3 (тоже в том же файле, но в разных функциях):
Task: "T011 — updateMarkersBySyllables не обнуляет label лишних"
Task: "T012 — JSDoc для updateMarkersBySyllables"
Task: "T013 — data-поле markerCountBeforeApply"
Task: "T014 — уведомление в applyAutoMarkersToEditor"
Task: "T015 — JSDoc для markerCountBeforeApply"

# Polish — все параллельно:
Task: "T016 — ESLint"
Task: "T017 — ktlint"
Task: "T018 — npm run build"
Task: "T019 — JSDoc coverage"
Task: "T020 — backend regression tests"
Task: "T021 — обновить specs/010-lyrics-spec-tags/tasks.md T024"
Task: "T022 — docs/architecture-notes.md changelog"
Task: "T023 — AGENTS.md Q&A"
Task: "T024 — quickstart.md Сценарий A"
Task: "T025 — quickstart.md Сценарий B"
Task: "T026 — quickstart.md Сценарии C-G"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Выполнить Phase 1: T001-T004 (Setup, smoke + backend regression)
2. Выполнить Phase 2: T005 (Foundational, гард в `syncMarkersFromSpecTags`)
3. Выполнить Phase 3: T006-T010 (US1, атомарный фикс `mounted` + `currentVoice` + JSDoc)
4. **STOP and VALIDATE**:
   - Запустить T016-T020 (линтеры + тесты + сборка)
   - Пройти `quickstart.md` Сценарии A и B (P1 + регрессия)
5. Готово к PR. US2 и US3 — опционально следующим PR'ом.

### Incremental Delivery

1. Setup + Foundational → гард на месте (страховка от регрессии)
2. US1 → атомарный фикс, наблюдаемый баг устранён → **MVP PR**
3. (Опционально) US2 → защита от рассинхрона счётчика слогов
4. (Опционально) US3 → защитное уведомление пользователю
5. Каждая фаза — атомарный коммит + прохождение quickstart-сценариев

### Parallel Team Strategy

Для одного разработчика: сериализация по фазам (US1 → US2 → US3 → Polish).

Для нескольких: US1 — критический путь, US2/US3 могут идти параллельно (после завершения US1) разными разработчиками, если готовы мержить конфликты в одном файле `SubsEdit.vue` (на практике для ~30 строк diff это не критично).

---

## Notes

- `[P]` = задачи, которые можно делать параллельно (разные файлы или непересекающиеся секции одного файла).
- `[Story]` лейбл (US1/US2/US3) — для traceability, какая user story покрывается.
- **Главное архитектурное решение**: фикс — bugfix-уровень правка в одном файле, без изменений контрактов/API/БД. Это соответствует FR-008 спеки 015 (семантика «Точные маркеры + Apply» остаётся by design) и FR-007 (регрессия недопустима).
- **MVP = US1** (T005+T006+T007+T008+T009+T010). После него фикс уже устраняет наблюдаемый баг. US2/US3 — опциональные улучшения.
- **Порядок T006 → T007 → T008 важен**: нельзя T007 (удалить цикл из `ws.on('decode')`) делать ДО T006 (добавить загрузку в `mounted()`) — иначе на время между T006 и T007 маркеры вообще нигде не загружаются. Поэтому рекомендуется **один коммит** на все три.
- **Критично**: после US1 НЕ должна сломаться регрессия на песнях БЕЗ спецтегов (Сценарий B из `quickstart.md`). Если ломается — откатить и переделать.
- **Линтер/тесты**: запускать `npm run lint:check` в `webvue3/` после каждой правки (Vue-сторона) и `./gradlew ktlintCheck` для регрессии (Kotlin не затрагивается, но проверить).
- **Юнит-тесты на Vue-стороне** SubsEdit.vue не пишутся — `quickstart.md` формализует ЧТО проверяется вручную (по образцу `tasks.md` спеки 010 T024).
