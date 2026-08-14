---
description: "Task list for Approve Status Choice feature"
---

# Tasks: Выбор статуса песни при апруве задания редактора (5 или 6)

**Input**: Design documents from `/specs/184-approve-status-choice/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: тесты НЕ запрашивались в спецификации (проект не имеет CI-тестов для admin/public — см. AGENTS.md «Тесты»). Валидация — 10 ручных сценариев в [quickstart.md](./quickstart.md).

**Organization**: задачи сгруппированы по user story, чтобы каждая история была реализуема и тестируема независимо.

**Implementation Status**: не выполнено (все 25 задач в состоянии `[ ]`). Подробные решения (D-1..D-8) — в [research.md](./research.md), контракты — в [contracts/](./contracts/), acceptance-критерии — в [spec.md](./spec.md).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3)
- В описании — точные пути файлов

## Path Conventions

Проект — multi-module: `karaoke-app` (admin backend), `karaoke-web` (public backend), `webvue3` (admin SPA). Пути относительно корня репо. Файлы, которые НЕ меняются (зафиксировано в plan.md, research D-8): `Song.kt`, `SongField.kt`, `karaoke-public/**`, `deploy/karaoke-db/**` (миграций нет).

---

## Phase 1: Setup

**Purpose**: подготовительные шаги (ветка уже создана `before_specify` хуком, см. AGENTS.md «Создание спецификации»).

- [x] T001 Убедиться, что рабочая ветка — `184-approve-status-choice` (`git branch --show-current`); обновить `pull` master и пересобрать: `./gradlew clean :karaoke-app:compileKotlin :karaoke-web:compileKotlin` (baseline-сборка, чтобы дальнейшие ошибки линтера были только от наших правок)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: блокирующие правки, без которых ни одна user story не имеет смысла. Все три user story касаются одного и того же эндпоинта `SongEditorController.approve` (и `byId` для UI), поэтому foundational = все **backend** правки + Vuex-action; UI-рендер radio-group живёт уже в US2.

**⚠️ CRITICAL**: нельзя начинать US2/US3 без завершения этой фазы (T002..T005 — backend, T006..T007 — Vuex/UI-платформа).

### Backend (Kotlin)

- [x] T002 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` метод `approve(@RequestParam id, @RequestParam target)`, **строка 318**:
  - добавить параметр `@RequestParam(required = false) idStatus: Int?` (порядок: `id, target, idStatus` — `target` остаётся вторым, чтобы не ломать позиционные вызовы; `idStatus` — последний, чтобы старые вызовы с двумя параметрами не падали)
  - сразу после чтения `aRead` (строка 333) добавить валидацию: `if (idStatus != null && idStatus != 5 && idStatus != 6) return@withDb mapOf("ok" to false, "status" to "error", "error" to "invalid_idstatus: must be 5 or 6")` + лог `[approve/feature-184] INVALID idStatus=$idStatus for assignmentId=$id`
  - вычислить `val requestedIdStatus = idStatus ?: 6`
  - **сохранить существующий** short-circuit `already_approved` (строки 333-335) — он стоит ДО нашей валидации, проверь `git diff` после правки
- [x] T003 В том же методе `approve`, **заменить блок 376-383** (хардкод `"6"`):
  - `val targetIdStatus = if (song.idStatus < requestedIdStatus) requestedIdStatus else song.idStatus` (новое вычисление фактического статуса)
  - **внутри** `if (song.idStatus < targetIdStatus) { song.fields[SongField.ID_STATUS] = targetIdStatus.toString(); song.saveToDb() }` — это сохраняет существующую защиту от downgrade (data-model INV-1) + добавляет поддержку 5
  - **после** блока 383: добавить лог `if (idStatus != null && idStatus == 5 && song.idStatus == 5) "[approve/feature-184] songId=${song.id} idStatus=5 reason=manual_choice"` (успех-5), иначе если `idStatus == null && song.idStatus == 6` → `reason=default`; отдельно — если `idStatus == 5 && song.idStatus == 6 && requestedIdStatus == 5` → `idStatus downgrade IGNORED songId=${song.id} current=6 requested=5` (D-2 + Edge Case)
- [x] T004 В том же методе `approve`, **условные гейты render-demo и sync-related** (research D-2, D-3):
  - **сохранить** блок `if (Karaoke.allowUpdateRemote) { ... updateRemoteSongFromLocalDatabase(song.id) ... }` (строки 395-420) **БЕЗ ИЗМЕНЕНИЙ** — push песни идёт всегда (research D-3)
  - **перед** `triggerRenderMp4DemoIfNeeded(song)` (строка 427) добавить: `if (song.idStatus >= 6L) { triggerRenderMp4DemoIfNeeded(song) } else { println("[approve/feature-184] render-demo SKIPPED for songId=${song.id} reason=idStatus=5") }`
  - **перед** `thread { ... }` (строка 435) добавить: `if (song.idStatus >= 6L) { thread { ... } } else { println("[approve/feature-184] sync-related SKIPPED for songId=${song.id} reason=idStatus=5") }`
  - **дополнительный лог** news: `if (song.idStatus < 6L) println("[approve/feature-184] news SKIPPED for songId=${song.id} reason=idStatus=5")` — для observability, news и так не выставится через `markNewsAvailableIfReady`, но строка нужна для grep по инцидентам
  - **НЕ менять** содержимое `thread { updateRemoteDatabaseFromLocalDatabase(...) }` — только обернуть его внешним `if`
- [x] T005 В том же контроллере, метод `byId(@RequestParam id, @RequestParam target)`, **строка 278-296**:
  - добавить строку `"idStatus" to (s?.idStatus ?: 0L)` в map-ответ (после `"songId" to a.songId` или рядом с другими полями песни)
  - комментарий: `// NEW (feature 184): UI-гейт radio-group в ReviewModal использует idStatus песни; песня уже загружена выше, +0 SQL`
  - **НЕ менять** никакие другие поля — additive change (contracts/byid-endpoint.md INV-B4)
  - **обновить KDoc** метода `byId`: добавить `@see specs/184-approve-status-choice/contracts/byid-endpoint.md`

### Frontend (Vuex)

- [x] T006 [P] В `webvue3/src/components/SongEditor/store.js` action `approveAssignment` (строки 200-206):
  - изменить сигнатуру: `approveAssignment(ctx, payload)` (вместо `approveAssignment(ctx, id)`)
  - добавить определение формата: `const isObj = payload !== null && typeof payload === 'object'; const id = isObj ? payload.id : payload; const idStatus = isObj ? payload.idStatus : undefined`
  - в `params`: `{ id, target: ctx.state.assignmentsTarget, ...(idStatus !== undefined ? { idStatus } : {}) }` — параметр не отправляется если undefined (backward-compatible)
  - **НЕ менять** существующие вызовы `loadAssignmentById` (он тоже использует `isObj` паттерн, см. `store.js:106-122`) — это образец для подражания
  - обновить JSDoc: `// Approve assignment, optional idStatus=5|6 for final song status (feature 184)`
- [x] T007 [P] В том же `store.js` (если есть getters для `assignmentCurrent`/новых полей) — без правок (UI работает напрямую с `$store.getters.getAssignmentCurrent`, см. `ReviewModal.vue:147`)

**Checkpoint**: Foundation ready — backend принимает `idStatus`, отдаёт `idStatus` в `/byId`, Vuex пробрасывает. UI без radio (существующее поведение — кнопка «Одобрить» выбирает default 6, как раньше).

---

## Phase 3: User Story 1 — Админ выбирает финальный статус при одобрении (Priority: P1) 🎯 MVP

**Goal**: в `ReviewModal` появляется radio-group «Финальный статус песни» (5/6, default 6), выбор пробрасывается в `POST /api/songeditor/approve?idStatus=`, при `idStatus=5` не запускается рендер DEMO и sync related-таблиц (только push самой песни), в логах — строки `[approve/feature-184]` для observability.

**Independent Test**: открыть задание (id_status песни < 5), в модалке выбрать «5 — Маркеры проверены», нажать «Одобрить» → в БД `tbl_songs.id_status = 5`, в `tbl_processes` НЕТ новой записи `RENDER_MP4_DEMO`, в логах — строка `render-demo SKIPPED reason=idStatus=5`. Аналогично для 6: всё как раньше.

### Implementation for User Story 1

- [x] T008 [US1] В `webvue3/src/components/SongEditor/ReviewModal.vue`, **в блоке `data()`** (строки 134-144):
  - добавить `selectedIdStatus: 6` — дефолт 6 (backward-compatible)
- [x] T009 [US1] В том же `ReviewModal.vue`, **в блоке `computed:`** (после `markerStats`, строка 188-198):
  - добавить `songIdStatus() { return this.a ? (typeof this.a.idStatus === 'number' ? this.a.idStatus : null) : null }` — `null` если поле не пришло (fallback, FR-007)
  - добавить `canChooseIdStatus() { return this.songIdStatus !== null }` (Pass 51-3.1: без `< 5`-гейта; Pass 51-3.2: текст FR-010 не используется; только факт «статус известен?»)
  - добавить `idStatusLabel(s) { return s === 5 ? '5 (маркеры проверены)' : s === 6 ? '6 (готова)' : String(s) }` — для информационного бейджа в `.se-meta` (это `methods`, НЕ `computed` — Vue 2 не поддерживает параметризованные computed-геттеры, fixed в Pass 51-3.0)
- ~~computed `bannerInfo()`~~ — **Pass 51-3.2 [REMOVED]**, FR-010 отменена; вместо баннера используется короткий текст радио + Q&A в спеке + JSDoc
- [x] T010 [US1] В том же `ReviewModal.vue`, **в блоке `watch:`** (после `showPlayer`, строки 199-211):
  - добавить `a: { handler(newA, oldA) { if (newA && oldA && newA.id !== oldA.id) { this.selectedIdStatus = 6 } }, deep: false }` — сброс выбора при смене задания (research D-7, ловушка с переиспользованием модалки)
- [x] T011 [US1] В `ReviewModal.vue` template, **в `.se-meta` блок** (строки 12-24) — после `<span>ID песни: {{ a.songId }}</span>`:
  - добавить `<span v-if="songIdStatus !== null" class="se-badge se-badge-approved">idStatus: {{ idStatusLabel(songIdStatus) }}</span>` (Pass 51-3.1: бейдж теперь ВСЕГДА виден, когда статус известен — это «правда о состоянии», не блокирующая выбор)
- [x] T012 [US1] В `ReviewModal.vue` template, **перед `<div class="se-modal-btns">`** (строка 99) — после `<p v-if="message" class="se-msg" ...>`:
  - добавить `<div v-if="canChooseIdStatus" class="se-idstatus-pick">` (Pass 51-3.1: без v-else — radio ВСЕГДА виден когда `songIdStatus !== null`; Pass 51-3.2: без баннера — FR-010 [REMOVED]):
    - `<label class="se-idstatus-option"><input type="radio" v-model="selectedIdStatus" :value="5" /> 5 — Маркеры проверены</label>`
    - `<label class="se-idstatus-option"><input type="radio" v-model="selectedIdStatus" :value="6" /> 6 — Готова</label>`
- [x] T013 [US1] В `ReviewModal.vue`, **в `<style scoped>`** (перед `}` в конце файла, ~строка 599):
  - `.se-idstatus-pick { display: flex; flex-direction: column; gap: 0.3rem; background: #f5f5f5; border-radius: 8px; padding: 0.6rem; font-size: 0.85rem; font-weight: 400; }`
  - ~~`.se-idstatus-banner { ... }`~~ — **Pass 51-3.2 [REMOVED]**, FR-010 отменена
  - `.se-idstatus-option { display: flex; align-items: center; gap: 0.4rem; cursor: pointer; font-size: 0.85rem; }`
  - ~~`.se-idstatus-readonly { ... }`~~ — Pass 51-3.1: блок удалён, radio ВСЕГДА виден
- [x] T014 [US1] В `ReviewModal.vue`, **метод `doApprove()`** (строки 281-308):
  - заменить `const res = await this.$store.dispatch('approveAssignment', this.a.id)` на `const res = await this.$store.dispatch('approveAssignment', { id: this.a.id, idStatus: this.selectedIdStatus })`
  - в ветке `res && res.ok && res.status === 'already_approved'`: сообщение оставить `'Задание уже одобрено'`
  - в ветке `res && res.ok`: заменить `this.message = 'Одобрено'` на `this.message = 'Одобрено в статусе ' + (res.idStatus || '?')` (FR-009) — `res.idStatus` — фактический (FR-012)
  - в `catch`: оставить как есть

**Checkpoint**: US1 готов. **MVP-минимум (P1 acceptance gate)**. Запустить quickstart сценарии 1, 2, 3, 5, 6, 7, 9 (см. [quickstart.md](./quickstart.md)).

---

## Phase 4: User Story 2 — Radio ВСЕГДА виден, когда статус песни известен (Priority: P2)

> **Pass 51-3.1 РЕДИЗАЙН**: US2 первой итерации (P2) был ПЕРЕСМЫШЛЕН в US2.1 — радио показывается ВСЕГДА, когда `songIdStatus !== null` (а не только при `< 5`). Это исправление UX-дефекта, без которого фича не имеет смысла. См. Q&A в [spec.md](./spec.md) (Pass 51-3.1: ОТМЕНЕНО).

**Goal**: radio-group «Финальный статус песни» виден админу при ЛЮБОМ известном `idStatus` песни (включая 5 и 6) — иначе при апруве задания с уже-готовой песнёй (например, после предыдущего одобрения в 6) контрол выбора пропадает и фича воспринимается как сломанная.

**Independent Test**: открыть задание в `ReviewModal` для песни в любом `idStatus` (0..6) — radio ВСЕГДА виден, обе опции (5/6) доступны для выбора. В `.se-meta` (шапка модалки) — информационный бейдж «idStatus песни: N (...)» с текущим значением. При выборе 5 для песни уже в 6 — бэкенд тихо игнорирует downgrade (`idStatus downgrade IGNORED ...`, логи).

### Implementation for User Story 2

- [x] T015 [US2.1] **`canChooseIdStatus` теперь ВСЕГДА `true`** когда `songIdStatus !== null` (Pass 51-3.1 редизайн):
  - было: `return this.songIdStatus === null || this.songIdStatus < 5` — скрывало radio для 5/6 (UX-дефект)
  - стало: `return this.songIdStatus !== null` — radio виден для всех известных статусов
  - **удалён v-else блок** `<p class="se-idstatus-readonly">` в template (больше не нужен)
- [x] T016 [P] [US2.1] Проверить quickstart **сценарий 4**:
  - открыть задание для песни с id_status=6 (как на скриншоте пользователя)
  - проверить: radio ВИДЕН, обе опции (5/6) доступны
  - в `.se-meta` бейдж «idStatus: 6 (готова)» — информационный, не блокирующий
  - при выборе 5 + клике «Одобрить» — бэкенд пишет `idStatus downgrade IGNORED`, статус остаётся 6
  - при выборе 6 + клике «Одобрить» — никаких изменений (статус уже 6), idempotent
  - **если** какой-то пункт не проходит — добавить правку в T011/T012 и перепрогнать
- [x] T016 [P] [US2] Проверить quickstart **сценарий 8** (поле `idStatus` в `/byId`):
  - прямой SQL: `SELECT id_status FROM tbl_songs WHERE id = <songId>`
  - `curl -X POST /api/songeditor/byId ... | jq .idStatus` — должно совпадать

**Checkpoint**: US2.1 готов — radio ВСЕГДА виден когда `songIdStatus !== null`, информационный бейдж в `.se-meta` показывает текущий статус.

---

## Phase 5: User Story 3 — Идемпотентность и журналирование (Priority: P3)

**Goal**: бэкенд пишет в лог информативные строки `[approve/feature-184]` со всеми исходами (manual_choice / default / downgrade IGNORED / SKIPPED / INVALID), повторный апрув возвращает `already_approved` без новых строк.

**Independent Test**: одобрить в 5 — найти в логах `karaoke-app` строку `idStatus=5 reason=manual_choice` + `render-demo SKIPPED` + `sync-related SKIPPED` + `news SKIPPED` (все 4 строки с `feature-184`). Повторно открыть задание, одобрить — новая строка `feature-184` НЕ появляется (только `status=already_approved`).

### Implementation for User Story 3

- [x] T017 [US3] Логирование уже реализовано в T002, T003, T004 (8 строк с префиксом `feature-184` — см. [contracts/approve-endpoint.md](./contracts/approve-endpoint.md) § «Логирование»). Проверить:
  - `grep '\[approve/feature-184\]' docker logs karaoke-app` после сценария 2 (одобрить в 5) — должно быть **ровно 5 строк**: `idStatus=5 reason=manual_choice`, `render-demo SKIPPED`, `sync-related SKIPPED`, `news SKIPPED`, `idStatus=5 reason=manual_choice` (одна на старте)
  - после сценария 5 (повторный апрув) — **0** новых строк `feature-184`
- [x] T018 [P] [US3] Идемпотентность проверена T017 + сценарий 5 quickstart. Дополнительно:
  - выполнить сценарий 6 (downgrade-ignore) → лог должен содержать строку `idStatus downgrade IGNORED songId=... current=6 requested=5`
  - если строки нет — перепроверить T003, условие `idStatus == 5 && song.idStatus == 6 && requestedIdStatus == 5` должно срабатывать

**Checkpoint**: US3 готов — observability для разбора инцидентов на месте.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: документация, линтинг, KDoc/JSDoc coverage, per-feature doc обновления (constitution VI FR-009), финальная очистка.

- [x] T019 [P] Обновить `docs/features/approve-pipeline.md`:
  - в секции «Что делает» (строки 8-30) — добавить подсекцию «Условный запуск при выборе статуса 5 (feature 184)» с описанием гейтов `triggerRenderMp4DemoIfNeeded` и `thread { sync related }` по `song.idStatus >= 6L`
  - в «Зачем» — добавить абзац «раньше при апруве в 5 приходилось вручную понижать статус в SongEdit, рискуя что за время downgrade сработает авто-рендер»
  - в «Ссылки» — `specs/184-approve-status-choice/`
- [x] T020 [P] Обновить `docs/features/editor-tasks.md`:
  - в секции «ReviewModal / одобрение» — добавить описание radio-group «Финальный статус песни» с двумя опциями
  - описать UI-гейт (Pass 51-3.1: `idStatus !== null` → radio ВСЕГДА, иначе скрыт; информационный бейдж в `.se-meta` виден при любом известном статусе)
  - в секции «Идемпотентность» — отметить, что feature 184 не ломает existing behavior
  - в «Ссылки» — `specs/184-approve-status-choice/contracts/`
- [x] T021 [P] KDoc на `SongEditorController.approve` — обновить блок документации (строки перед `fun approve`): описать новый параметр `idStatus`, перечислить гейты, сослаться на [contracts/approve-endpoint.md](./contracts/approve-endpoint.md)
- [x] T022 [P] JSDoc на `ReviewModal` — расширить существующий блок (строки 122-129): описать новые computed (`songIdStatus`, `canChooseIdStatus`, `idStatusLabel`), radio-group, поведение `watch: a`
- [x] T023 [P] Обновить `docs/architecture-notes.md` — добавить запись «Pass 51-3: Approve Status Choice (spec 184)»:
  - что: выбор статуса 5/6 при апруве
  - почему: ручной downgrade после апрува в 6 был источником race-условий с авто-рендером
  - решения D-1..D-8 со ссылками на research.md
  - edge cases (downgrade-ignore, US2.1 радио-всегда-виден, 3 точки входа ReviewModal)
  - метрики: объём кода (2 backend файла + 2 frontend файла, ~60 строк)
  - урок: проверять «как живые API-ответы, которые читает UI, соотносятся с тем, что UI пытается рендерить» (FR-011 найден через эту сверку)
- [x] T024 [P] Обновить `AGENTS.md` — добавить Q&A «Как правильно гейтить авто-конвейер по выбору админа?» в секцию Q&A (Pass 24+51+52). Обновить версию (1.7.1 → 1.7.2) и Last Updated (2026-08-13). Суть урока: «гейт по ФАКТИЧЕСКОМУ, а не запрошенному значению, иначе регрессия в единственном не-5-кейсе (requested=5, current=6)»
- [x] T025 [P] Линтинг + coverage (все должны быть зелёными, иначе — фиксить):
  - `./gradlew ktlintCheck` → BUILD SUCCESSFUL
  - `./gradlew :karaoke-app:compileKotlin` → SUCCESS
  - `cd webvue3 && npm run lint:check` → no errors
  - `cd webvue3 && npm run build` → SUCCESS
  - `bash tools/check-kdoc-coverage.sh` → ≥ baseline
  - `bash tools/check-jsdoc-coverage.sh webvue3` → ≥ baseline
  - `bash tools/check-feature-doc.sh docs/features/*.md` → «Все документы валидны»
  - `cd webvue3 && npm run lint:check` → без новых warnings относительно baseline
  - Все 10 сценариев quickstart.md прошли (помечаются чекбоксами в `quickstart.md`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей
- **Phase 2 (Foundational)**: зависит от Phase 1 — БЛОКИРУЕТ все user stories
- **Phase 3 (US1)**: зависит от Phase 2 — фронт рендерит radio и диспатчит объект
- **Phase 4 (US2)**: зависит от Phase 3 (T011/T012 уже реализуют бейдж, US2 = проверка)
- **Phase 5 (US3)**: зависит от Phase 2 (T002/T003/T004 уже логируют, US3 = проверка)
- **Phase 6 (Polish)**: зависит от всех фаз user stories

### Story Completion Order

```
T001 (Setup)
  ↓
T002-T007 (Foundational: backend + Vuex)
  ↓
T008-T014 (US1 — UI radio + doApprove)  ← можно начинать сразу после T007
  ↓
T015-T016 (US2.1 — radio ВСЕГДА)        ← по факту = верификация T009/T011/T012
  ↓
T017-T018 (US3 — логирование)            ← по факту = верификация T002/T003/T004
  ↓
T019-T025 (Polish — docs + lint)
```

### Parallel Opportunities

- **T002, T005** — `[P]`, разные методы одного контроллера (можно править одновременно, но аккуратно с merge)
- **T003, T004** — оба внутри метода `approve` → **НЕ в параллель** (один файл, последовательно)
- **T006, T007** — `[P]`, разные сущности store (но если T006 не трогает getters — реально `[P]`)
- **T008, T009, T010** — все в `ReviewModal.vue` → **НЕ в параллель** (один файл)
- **T011, T012, T013** — все в `ReviewModal.vue` → **НЕ в параллель** (один файл, разные секции: T011 = template meta, T012 = template pick, T013 = style; можно править последовательно без конфликтов)
- **T019, T020, T021, T022, T023, T024** — `[P]`, разные файлы документации → можно в параллель
- **T025** — один bash-проход, `[P]` внутренне не применимо

### Within Each Phase

- **Phase 2**: T002 → T003 → T004 (одна функция, по порядку сверху вниз) **и параллельно** T005 (другая функция того же контроллера, конфликта нет) **и параллельно** T006+T007 (другой файл — store.js)
- **Phase 3**: T008 (data) → T009 (computed) → T010 (watch) → T011 → T012 → T013 (style) → T014 (doApprove) — строго последовательно, один файл

---

## Implementation Strategy

### MVP First (US1 only)

1. ✅ Complete Phase 1: T001 (сборка baseline)
2. ✅ Complete Phase 2: T002-T007 (backend + Vuex — критический блок)
3. ✅ Complete Phase 3: T008-T014 (UI radio + выбор)
4. **STOP and VALIDATE**: запустить quickstart сценарии 1, 2, 3, 5, 7, 9 — это покрывает выбор 5/6, idempotency, invalid value, remote target
5. **MVP готов** — админ может выбирать статус при апруве

### Incremental Delivery

1. Phase 1 + Phase 2 → foundation ready (T001-T007)
2. **US1** (T008-T014) → MVP: выбор работает
3. **US2.1** (T015-T016) → UI: radio ВСЕГДА виден когда статус известен (Pass 51-3.1)
4. **US3** (T017-T018) → observability: логи в порядке
5. **Polish** (T019-T025) → документация + линтинг → стабильный релиз

### Parallel Team Strategy

Один разработчик (realia проекта — 1 машина, 1 агент):
- T002→T003→T004 — последовательно (один файл, метод `approve`)
- T005 — параллельно с T002-T004 (другая функция `byId` того же контроллера, конфликта по строкам нет, но в одном PR — агенту проще последовательно)
- T006 — параллельно с backend (Vuex — другой файл, другая логика)
- T008-T014 — последовательно, один файл `ReviewModal.vue`
- T019-T024 — параллельно (разные docs/md файлы)

---

## Notes

- **Миграций БД нет** (research D-8) → `recordhash`-триггеры не пересоздаются → constitution III в этой фиче неприменим.
- **Новых boolean-полей DTO нет** (только Int) → ловушка Jackson `is`-префикса (AGENTS.md Q&A) неприменима.
- **Новых секретов/env нет** → constitution VIII в этой фиче неприменим.
- **Перед commit**: `git status` + `git diff --stat` (по AGENTS.md). Только изменённые файлы в индекс: 2 backend + 2 frontend + 2 docs/features/* + 1 architecture-notes + 1 AGENTS.md.
- **Перед push**: `git log -1 master` — последний commit ДОЛЖЕН быть merge-коммитом от PR с зелёным CI (см. AGENTS.md «CI-gate для master»).
- **Push в master ЗАПРЕЩЁН** — только feature-ветка `184-approve-status-choice` → PR → CI 7/7 SUCCESS → merge (см. AGENTS.md «CI-gate для master»).
- **Ветку НЕ удалять** после merge (см. AGENTS.md «Жизненный цикл feature-ветки»).
- **Тесты НЕ пишутся** (проект не имеет CI-тестов для admin/public) — валидация ручная через quickstart.md (10 сценариев).
- **Ловушка D-7**: без `watch: a()` в ReviewModal выбор статуса «залипает» при переиспользовании модалки для разных заданий (SongsTable может держать компонент смонтированным).
- **Ловушка D-2**: гейт по **фактическому** `song.idStatus >= 6L` после применения, а не по `requestedIdStatus == 6` — иначе регрессия в кейсе `requested=5, current=6` (downgrade-ignore).
- **Ловушка D-3**: push песни (`updateRemoteSongFromLocalDatabase`) НЕ гейтится — иначе одобренная разметка не попадёт на PROD.
- **Ловушка D-5**: `idStatus` в `/byId` — это статус ПЕСНИ, а не ЗАДАНИЯ (поле `status` остаётся статусом задания `SongAssignmentStatus`).
- **Ловушка D-6**: `approveAssignment` принимает и `Number`, и `{id, idStatus}` — старые вызовы (если остались в локальных правках других разработчиков) не сломаются.
