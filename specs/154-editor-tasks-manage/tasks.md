# Tasks: 154-editor-tasks-manage

**Input**: Design documents from `/specs/154-editor-tasks-manage/`
**Branch**: `154-editor-tasks-manage`
**Created**: 2026-08-05
**Status**: Started 2026-08-05, реализация по `/speckit.implement`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: В проекте тесты в CI отключены (см. AGENTS.md, constitution п. «Тесты» — существующие интеграционные тесты `@Disabled` не покрывают бизнес-флоу). Валидация делается пользователем вручную по quickstart.md. **Тестовых задач не генерируем.**

**Условные обозначения**:
- `[P]` — может выполняться параллельно с другими `[P]`-задачами той же фазы (разные файлы, нет зависимостей).
- `[Story]` — задача принадлежит конкретной user story (используется только в Phase 3+).
- Формат строки: `- [ ] Txxx [P?] [Story?] Описание с точным file path`.

**Глобальное правило** (CONSTITUTION VI FR-006): каждая новая публичная Kotlin-`fun` MUST иметь KDoc с `@see docs/features/editor-tasks.md`; каждый новый публичный JS-`export function` MUST иметь JSDoc с ссылкой на тот же документ. Проверяется в Phase 8 инструментами `tools/check-kdoc-coverage.sh` и `tools/check-jsdoc-coverage.sh`.

**Глобальное правило** (CONSTITUTION VI FR-007): ни ktlint, ни ESLint НЕ ДОЛЖНЫ давать новых нарушений сверх baseline (`tools/baseline-stats.sh`). Проверяется в Phase 8.

---

## Phase 1: Setup (Shared Infrastructure)

**Goal**: подтвердить, что среда разработки (ветка, артефакты спеки/плана) готова к началу работы.

- [X] T001 Проверить, что ветка `154-editor-tasks-manage` создана (`git branch --show-current` показывает её), спека и план на месте: `ls specs/154-editor-tasks-manage/` содержит `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`. Если ветка не создана — `./tools/reserve-branch-number.sh` ещё раз и `git checkout -b 154-editor-tasks-manage`; недостающие артефакты — взять из уже существующих `specs/154-editor-tasks-manage/*.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Goal**: инфраструктура, которая блокирует часть stories (особенно батч-эндпоинты US-4 и US-5).

**⚠️ CRITICAL**: T002 (`KaraokeDbTable.deleteIn`) MUST быть готов до Phase 6 (US-4) и Phase 7 (US-5). T003 (`docs/features/editor-tasks.md`) MUST быть готов ДО Phase 8 (он — цель `@see` ссылок, проверяется `tools/check-kdoc-coverage.sh`).

- [X] T002 [P] Добавить новый helper `fun deleteIn(tableName: String, ids: List<Long>, database: KaraokeConnection, sync: Boolean = false): Int` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeDbTable.kt` (рядом с существующим `delete(...)` на строке 606). Реализация: `if (ids.isEmpty()) return 0` → `connection.prepareStatement("DELETE FROM $tableName${if (sync) "_sync" else ""} WHERE id = ANY(?)")` → `ps.setArray(1, connection.createArrayOf("BIGINT", ids.toTypedArray()))` → `ps.executeUpdate()` → `Int`. Тот же `try/catch` + `println(e.message)` + `getConnection() == null → return 0`, что у существующего `delete(...)`. KDoc: `@see docs/features/editor-tasks.md` (FR-006 конституции). **Блокирует Phase 6 (T012-T014) и Phase 7 (T015-T017)**.
- [X] T003 [P] Создать per-feature документ `docs/features/editor-tasks.md` со структурой (см. секцию «Контракт per-feature документа» в constitution.md): `## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты / правила`, `## Известные ловушки`, `## Ссылки` (на spec.md, plan.md, contracts/, quickstart.md, Q&A AGENTS.md если уместно). Контент — взять из research.md (10 решений) + data-model.md (что удаляется/что нет) + contracts/ (имена эндпоинтов) + quickstart.md (сценарии 2, 7, 9 как краткое описание). KDoc/JSDoc-инструменты проверяют, что документ существует.
- [X] T004 [P] Обновить `docs/features/README.md`: добавить строку про `editor-tasks.md` в таблицу фич (22 → 23 документов). Минимально — одна строка в формате существующих: `| editor-tasks | Управление заданиями онлайн-редактора (сортировка + кнопки) | [editor-tasks.md](./editor-tasks.md) |` (или эквивалентный формат). Сверить с шапкой файла (уже существующие 22 фичи).

**Checkpoint**: foundation готов. US-1, US-2, US-3 (Phase 3-5) могут стартовать параллельно.

---

## Phase 3: User Story 1 - Сортировка: одобренные внизу (Priority: P1) 🎯 MVP

**Goal**: карточки `status='approved'` идут строго после карточек других статусов в DOM-порядке `/account/editor`.

**Independent Test** (FR-001, FR-002, SC-001): редактор с 8+ заданиями (3+ approved, 2+ активные) открывает `/account/editor`; в DOM `.ke-badge-approved` строго после всех не-approved; внутри обеих групп порядок стабилен (id DESC, по образцу `.sorted()` в `SongAssignment.loadByAssignee:105`).

- [X] T005 [US-1] В `karaoke-public/src/views/EditorTasksView.vue`:
  1. Добавить блок-уровневую (module-scope) `const STATUS_ORDER = { assigned: 0, in_progress: 1, submitted: 2, rejected: 3, approved: 4 }` (по образцу `webvue3/src/components/SongEditor/SongEditorTable.vue:117-123`, но с `rejected: 3` ПЕРЕД `approved: 4` — чтобы `approved` шёл последним; см. спеку FR-001 + research.md п.2).
  2. Добавить `computed: { sortedTasks() { return [...this.tasks].sort((a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99)) }, approvedCount() { return this.tasks.filter(t => t.status === 'approved').length } }`.
  3. В `<template>` найти `v-for` по карточкам — заменить ссылку с `tasks` на `sortedTasks`.
  4. Не трогать `data()` (`tasks: []`), `mounted()`, `load()` — они работают с серверным порядком (id DESC).
  5. После правки — вручную открыть страницу в браузере и убедиться, что DOM-порядок соответствует `STATUS_ORDER`.
  6. Если строка содержит `class` или `key` в текущем `v-for` — оставить без изменений.

**Checkpoint**: после T005 User Story 1 полностью функциональна и независимо тестируема. Сортировка работает чисто на клиенте — никаких изменений на бэкенде, никакого перевыпуска jar.

---

## Phase 4: User Story 2 - «Отказаться» от любого активного задания (Priority: P1)

**Goal**: редактор может удалить любое активное задание (`assigned/in_progress/submitted/rejected`) одним кликом → запись исчезает + связанный черновик тоже удаляется.

**Independent Test** (FR-004..FR-009, SC-002): редактор с активным заданием нажимает «Отказаться» → подтверждает → карточка исчезает за ≤2 сек; повторный F5 не возвращает; в `tbl_song_assignment_drafts` orphan-cleanup выполнен.

- [X] T006 [P] [US-2] В `karaoke-public/src/services/songEditorApi.js`: добавить функцию `export function refuseTask(id) { return authPost(\`${BASE}/tasks/${id}/refuse\`, {}, token()) }` (рядом с `submitTask`/`recallTask` на строке 28-35). Возвращает `{status, body}` — паттерн уже зафиксирован в `authApi.js:20`. JSDoc с `@see docs/features/editor-tasks.md`. Не забыть про уже существующий `BASE = '/api/public/account/editor'` на строке 10.
- [X] T007 [P] [US-2] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`: добавить новый метод-эндпоинт **`@PostMapping("/tasks/{id}/refuse") fun refuse(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>>`** (рядом с `submit()` строки 224-240 и `recall()` строки 249-266). Алгоритм:
  1. `val user = currentUser(request)` (уже реализован строкой 61).
  2. `if (!user.isEditor) return notFound()` (паттерн строки 132 / 191 / 230).
  3. `val a = loadOwnedAssignment(id, user.id) ?: return notFound()` (helper строки 64-67).
  4. `SongAssignmentDraft.deleteByAssignment(id, db)` — orphan-cleanup **ДО** удаления задания (порядок см. существующий `SongEditorController.revoke()` строки 533-548).
  5. `val ok = SongAssignment.delete(id, db)` через `KaraokeDbTable.delete` (как `SongEditorController.delete` строки 514-523).
  6. `println("[editor-tasks/refuse] user=${user.id} id=$id deleted=$ok")` — FR-034.
  7. `return ResponseEntity.ok(mapOf("ok" to ok, "deleted" to if (ok) 1 else 0))`.
  8. KDoc с `@see docs/features/editor-tasks.md` (FR-006 конституции).
- [X] T008 [US-2] В `karaoke-public/src/views/EditorTasksView.vue`: добавить блок «Отказаться» (только для активных статусов — `t.status !== 'approved'`):
  1. В шаблоне для каждой карточки добавить кнопку `<button v-if="t.status !== 'approved'" class="ke-btn ke-btn-refuse" @click="onRefuse(t)">Отказаться</button>`. Стилизовать через существующие `ke-btn*` классы (если нет — `style` inline).
  2. В `methods`: добавить `async onRefuse(t) { if (!confirm(\`Отказаться от задания «${t.songName}» (${statusLabel(t.status)})? Задание и черновик будут удалены. Это действие нельзя отменить.\`)) return; const { status, body } = await refuseTask(t.id); if (status === 200 && body && body.ok) await this.load(); else if (status === 200 && body && body.error === 'assignment_not_found') { this.load(); /* тихо */ } else { /* короткое сообщение */ alert('Не удалось отказаться от задания. Попробуйте ещё раз.') } }`.
  3. В `methods` импортировать `refuseTask` из `../services/songEditorApi`.
  4. Не забыть, что `confirm()` возвращает `true`/`false` — паттерн уже есть в `SongEditorTable.vue:288` (`onDelete`).
  5. Использовать `this.tasks.filter(t => t.status !== 'approved').length` или существующий `sortedTasks`/`tasks` (для текста в confirm — `t.songName`, `statusLabel(t.status)`).

**Checkpoint**: User Story 2 полностью функциональна. Одиночное «Отказаться» работает для всех активных статусов. Сценарий quickstart.md #2 (assigned), #3 (in_progress с draft), #4 (submitted), #5 (rejected) — все покрыты.

---

## Phase 5: User Story 3 - «Удалить» одобренное задание (Priority: P1)

**Goal**: редактор может удалить approved-карточку одним кликом → песня (`tbl_songs`) остаётся полностью нетронутой.

**Independent Test** (FR-010..FR-015, SC-003): редактор с approved-заданием нажимает «Удалить» → подтверждает → карточка исчезает за ≤2 сек; `tbl_songs` идентичен до/после; песня доступна в плеере.

- [X] T009 [P] [US-3] В `karaoke-public/src/services/songEditorApi.js`: добавить `export function deleteTask(id) { return authPost(\`${BASE}/tasks/${id}/delete\`, {}, token()) }` (рядом с `refuseTask` из T006). JSDoc с `@see docs/features/editor-tasks.md`.
- [X] T010 [P] [US-3] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`: добавить новый метод-эндпоинт **`@PostMapping("/tasks/{id}/delete") fun delete(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>>`** (рядом с `refuse()` из T007 и `recall()` строки 249-266). Алгоритм:
  1. `val user = currentUser(request)`.
  2. `if (!user.isEditor) return notFound()`.
  3. `val a = loadOwnedAssignment(id, user.id) ?: return notFound()`.
  4. `val draft = SongAssignmentDraft.getByAssignment(id, db, storageService, storageApiClient)` (паттерн `task()` строки 134, `submit()` строки 232-234).
  5. **Проверка статуса**: `if (statusOf(a, draft) != SongAssignmentStatus.APPROVED) return ResponseEntity.ok(mapOf("ok" to false, "error" to "not_approved"))` — если кто-то нажал «Удалить» на не-approved карточке.
  6. `val ok = SongAssignment.delete(id, db)`. **Черновик НЕ удаляем** (FR-012, research.md п.5).
  7. `println("[editor-tasks/delete] user=${user.id} id=$id deleted=$ok")` — FR-034.
  8. `return ResponseEntity.ok(mapOf("ok" to ok, "deleted" to if (ok) 1 else 0))`.
  9. KDoc с `@see docs/features/editor-tasks.md`.
  10. **Имя `delete(...)` в Kotlin** — допустимо (контракт зафиксировал так), но если при компиляции возникает конфликт (например, с каким-то однофамильцем из базового класса контроллера) — переименовать в `deleteApprovedAssignment(id, request)`, обновив соответствующую аннотацию `@PostMapping(...)` (mapping не зависит от имени метода).
- [X] T011 [US-3] В `karaoke-public/src/views/EditorTasksView.vue`: добавить блок «Удалить» (только для `status='approved'`):
  1. В шаблоне рядом с кнопкой «Отказаться» (или отдельно) добавить `<button v-if="t.status === 'approved'" class="ke-btn ke-btn-delete" @click="onDelete(t)">Удалить</button>`.
  2. В `methods`: добавить `async onDelete(t) { if (!confirm(\`Удалить одобренное задание «${t.songName}» из моего списка? Песня и её разметка останутся как были — удаляется только запись о назначении.\`)) return; const { status, body } = await deleteTask(t.id); ... }`. Импорт `deleteTask` из `../services/songEditorApi`.
  3. Логика обработки ответа: `body.ok === true` → `await this.load()`; `body.error === 'assignment_not_found'` → `await this.load()` (тихо); `body.error === 'not_approved'` → короткое сообщение (`alert('Это задание нельзя удалить из личного кабинета')`); иначе → `alert('Не удалось удалить задание. Попробуйте ещё раз.')`.
  4. Проверить, что новая кнопка не показывается одновременно с «Отказаться» (по `v-if`).

**Checkpoint**: User Stories 1, 2 AND 3 — все 3 P1 — полностью независимо функциональны. Сортировка работает, одиночные кнопки работают. Песня не затрагивается (FR-030, SC-007).

---

## Phase 6: User Story 4 - «Удалить все одобренные» одним кликом в ЛК (Priority: P2)

**Goal**: редактор удаляет все свои одобренные задания одним запросом; активные не трогаются.

**Independent Test** (FR-016..FR-021, SC-004): редактор с 5+ одобренными и 2+ активными нажимает «Удалить все одобренные» → подтверждает → все approved исчезают за ≤3 сек, активные остаются; повторный клик → без ошибки.

**⚠️ Depends on T002** (Phase 2): `KaraokeDbTable.deleteIn(...)` — батч через один SQL.

- [X] T012 [P] [US-4] В `karaoke-public/src/services/songEditorApi.js`: добавить `export function deleteApprovedTasks() { return authPost(\`${BASE}/tasks/delete-approved\`, {}, token()) }` (рядом с `deleteTask` из T009). JSDoc с `@see docs/features/editor-tasks.md`.
- [X] T013 [P] [US-4] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`: добавить новый метод-эндпоинт **`@PostMapping("/tasks/delete-approved") fun deleteApproved(request: HttpServletRequest): ResponseEntity<Map<String, Any?>>`** (рядом с `delete()` из T010). Алгоритм:
  1. `val user = currentUser(request)`.
  2. `if (!user.isEditor) return notFound()`.
  3. Загрузить: `val assignments = SongAssignment.loadByAssignee(user.id, db, storageService, storageApiClient)` (`SongAssignment.kt:90-105`).
  4. `if (assignments.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "deleted" to 0))`.
  5. `val drafts = SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, storageService, storageApiClient)` (`PublicSongEditorController.tasks()` строки 96 — паттерн).
  6. `val approvedIds = assignments.mapNotNull { a -> val draft = drafts[a.id]; val s = statusOf(a, draft); if (s == SongAssignmentStatus.APPROVED) a.id else null }`.
  7. `if (approvedIds.isEmpty()) return ResponseEntity.ok(mapOf("ok" to true, "deleted" to 0))` — идемпотентность (SC-006).
  8. `val deleted = KaraokeDbTable.deleteIn(tableName = SongAssignment.TABLE_NAME, ids = approvedIds, database = db)` — **ИСПОЛЬЗУЕТ helper из T002** ⚠️.
  9. `println("[editor-tasks/delete-approved] user=${user.id} requested=${approvedIds.size} deleted=$deleted")` — FR-034.
  10. `return ResponseEntity.ok(mapOf("ok" to true, "deleted" to deleted))`.
  11. KDoc с `@see docs/features/editor-tasks.md`.
- [X] T014 [US-4] В `karaoke-public/src/views/EditorTasksView.vue`: добавить кнопку «Удалить все одобренные» рядом с заголовком (или в верхней части контента — конкретное место на усмотрение, см. спеку FR-016):
  1. В `<template>` добавить блок перед списком карточек (или в `.ke-page-header`):
     ```html
     <div v-if="user && user.editor" class="ke-bulk-bar">
       <button class="ke-btn ke-btn-bulk" :disabled="approvedCount === 0" :title="approvedCount === 0 ? 'Нет одобренных заданий' : ''" @click="onDeleteAllApproved">Удалить все одобренные ({{ approvedCount }})</button>
     </div>
     ```
  2. В `methods`: добавить `async onDeleteAllApproved() { const n = this.approvedCount; if (n === 0) return; if (!confirm(\`Удалить все ${n} одобренных заданий? Это действие нельзя отменить. Сами песни не пострадают — удаляются только записи о назначениях.\`)) return; this.isBusy = true; try { const { status, body } = await deleteApprovedTasks(); if (status === 200 && body && body.ok) { this.$emit?.('notify', { type: 'info', message: \`Удалено ${body.deleted} одобренных заданий\` }); await this.load(); } else { alert('Не удалось удалить одобренные задания. Попробуйте ещё раз.') } } finally { this.isBusy = false } }`.
  3. Импорт `deleteApprovedTasks` из `../services/songEditorApi`.
  4. **Использует `approvedCount`** (computed из T005).
  5. Поведение `disabled` при 0 — через биндинг `:disabled="approvedCount === 0"` (FR-017, research.md п.6).

**Checkpoint**: User Stories 1, 2, 3, 4 — все функциональны независимо. Сортировка работает, одиночные кнопки работают, массовое одобренных в ЛК работает.

---

## Phase 7: User Story 5 - «Удалить все одобренные» в админке (Priority: P2)

**Goal**: администратор удаляет все approved задания в текущей выборке (с учётом фильтров `filterStatus/filterAssigneeId/filterAuthor` и `target=local|remote`) одним кликом.

**Independent Test** (FR-022..FR-028, SC-005): админ открывает «Задания редактора», в выборке 100+ approved, нажимает «Удалить все одобренные» → подтверждает → все одобренные строки исчезают за ≤5 сек; фильтр по исполнителю/автору сужает выборку корректно.

**⚠️ Depends on T002** (Phase 2): `KaraokeDbTable.deleteIn(...)`. Phase 7 параллелен с Phase 6 (разные файлы — `webvue3/SongEditor` vs `karaoke-public/views`).

- [X] T015 [P] [US-5] В `webvue3/src/components/SongEditor/store.js`: добавить action (рядом с существующими `loadAssignmentsDigest`/`loadAssignmentById` строки 88-122):
  ```js
  deleteApprovedAssignments(ctx, params) {
    return promisedXMLHttpRequest({
      method: 'POST',
      url: '/api/songeditor/delete-approved',
      params: {
        target: ctx.state.assignmentsTarget,
        filterAssigneeId: params.filterAssigneeId || undefined,
        filterStatus: params.filterStatus || undefined,
        filterAuthor: params.filterAuthor || undefined,
      },
    }).then((data) => JSON.parse(data))
  },
  ```
  Паттерн `params[key] || undefined` — чтобы пустые строки не шли на бэкенд (контракт admin-editor-tasks-api.md:107-109). JSDoc с `@see docs/features/editor-tasks.md`.
- [X] T016 [P] [US-5] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`: добавить новый метод-эндпоинт **`@PostMapping("/delete-approved") @ResponseBody fun deleteApprovedAssignments(@RequestParam(required = false) target: String?, @RequestParam(required = false) filterAssigneeId: Long?, @RequestParam(required = false) filterStatus: String?, @RequestParam(required = false) filterAuthor: String?): Map<String, Any?>`** (рядом с `delete()` строки 514-523 и `revoke()` строки 533-548). Алгоритм:
  1. `withDb(target) { db -> ... }` (паттерн строки 520, 539).
  2. Загрузить: `val assignments = SongAssignment.loadAll(db, storageService, storageApiClient)` (`SongAssignment.kt:124-138`).
  3. Применить фильтр по исполнителю: `val filtered = if (filterAssigneeId != null) assignments.filter { it.assigneeId == filterAssigneeId } else assignments`.
  4. `if (filtered.isEmpty()) return@withDb mapOf("ok" to true, "deleted" to 0)`.
  5. `val drafts = SongAssignmentDraft.loadByAssignments(filtered.map { it.id }, db, storageService, storageApiClient)` (`SongEditorController.kt:digest()` — паттерн строки 215-262).
  6. Песни для фильтра по автору: `val songs = Song.loadListFromDbByIds(filtered.map { it.songId }.distinct(), db, storageService, storageApiClient)` (паттерн `SongEditorController.kt:digest()` — там уже есть).
  7. Композитный статус + фильтры:
     ```kotlin
     val approvedIds = filtered.mapNotNull { a ->
       val draft = drafts[a.id]
       val s = SongAssignmentStatus.resolve(a.adminStatus, draft?.userStatus, a.reviewedAt, draft?.submittedAt)
       val authorOk = filterAuthor.isNullOrBlank() || (songs[a.songId]?.author?.contains(filterAuthor, ignoreCase = true) == true)
       val statusOk = filterStatus.isNullOrBlank() || s.dbValue == filterStatus
       if (s == SongAssignmentStatus.APPROVED && authorOk && statusOk) a.id else null
     }
     ```
     Семантика `filterStatus` — если задан, применяем; если пусто/null — пропускаем (по умолчанию UI передаёт `approved`, см. контракт).
  8. `if (approvedIds.isEmpty()) return@withDb mapOf("ok" to true, "deleted" to 0)`.
  9. `val deleted = KaraokeDbTable.deleteIn(tableName = SongAssignment.TABLE_NAME, ids = approvedIds, database = db)` — **ИСПОЛЬЗУЕТ helper из T002** ⚠️.
  10. `println("[editor-tasks/admin-delete-approved] target=$target filters={status=$filterStatus, assignee=$filterAssigneeId, author=$filterAuthor} requested=${approvedIds.size} deleted=$deleted")` — FR-034.
  11. `return@withDb mapOf("ok" to true, "deleted" to deleted)`.
  12. KDoc с `@see docs/features/editor-tasks.md`.
  13. **Доступ**: эндпоинт унаследует существующий `permitAll()` в `SecurityConfig.kt` (контракт admin-editor-tasks-api.md:82-86) — никаких изменений в `SecurityConfig`.
- [X] T017 [US-5] В `webvue3/src/components/SongEditor/SongEditorTable.vue`: добавить кнопку «Удалить все одобренные» в тулбар (после существующих кнопок «Обновить» / «+ Назначить песню» — строки ~339-343 по образцу `.set-btn:disabled`):
  1. В `<template>` в `.set-toolbar` добавить:
     ```html
     <button class="set-toolbar-item set-btn set-btn-del-approved"
             :disabled="approvedCount === 0 || isBusy"
             :title="approvedCount === 0 ? 'Нет одобренных заданий' : ''"
             @click="onDeleteApproved">Удалить все одобренные ({{ approvedCount }})</button>
     ```
  2. В `computed`: добавить `approvedCount() { return (this.digest || []).filter(t => t.status === 'approved').length }` — `digest` уже загружен через `loadAssignmentsDigest` action (Vuex).
  3. В `methods` (рядом с `onDelete` строки 287-291 и `onRevoke` строки 292-301):
     ```js
     async onDeleteApproved() {
       const n = this.approvedCount
       if (n === 0) return
       if (!confirm(`Удалить ${n} одобренных заданий (с учётом фильтров)? Это действие нельзя отменить.`)) return
       this.isBusy = true
       try {
         await this.$store.dispatch('deleteApprovedAssignments', {
           filterAssigneeId: this.filterAssigneeId,
           filterStatus: this.filterStatus,
           filterAuthor: this.filterAuthor,
         })
         await this.reload()
       } finally {
         this.isBusy = false
       }
     }
     ```
  4. **target-aware**: `assignmentsTarget` уже используется в `loadAssignmentsDigest` (Vuex state, строка 89) — T015 передаёт его в action; никаких изменений в target-handling не нужно.
  5. `reload()` уже есть в `SongEditorTable.vue` — использовать его (как в `onDelete` строке 290).

**Checkpoint**: User Stories 1, 2, 3, 4, AND 5 — все 5 stories функциональны независимо. Полная фича в обеих частях приложения (karaoke-public + webvue3).

---

## Phase 8: Polish & Cross-Cutting Concerns

**Goal**: верификация, что фича не сломала линтеры, baseline, KDoc/JSDoc coverage, и что pre-commit/CI зелёные. Ручная валидация через quickstart.md.

**⚠️ Depends on**: Phase 3, 4, 5, 6, 7 (all user stories).

- [X] T018 [P] Сборка + ktlint: запустить `./gradlew ktlintCheck` из корня. Не должно быть **новых** нарушений сверх baseline (`tools/baseline-stats.sh`). Если есть — исправить (или обновить baseline-файл с обоснованием). Без зелёного ktlint не идти дальше.
- [X] T019 [P] `cd webvue3 && npm run lint:check`. Аналогично — никаких новых нарушений сверх `webvue3/.eslint-baseline.json`. Если есть — исправить или обновить baseline.
- [X] T020 [P] `cd karaoke-public && npm run lint:check`. То же.
- [X] T021 [P] Проверить KDoc-coverage: `bash tools/check-kdoc-coverage.sh`. Должно быть 100% — каждая новая публичная функция (T002, T007, T010, T013, T016) имеет `@see docs/features/editor-tasks.md` (FR-006 конституции). Использовать dokka-link-check или эквивалентный скрипт.
- [X] T022 [P] Проверить JSDoc-coverage для webvue3 и karaoke-public: `bash tools/check-jsdoc-coverage.sh webvue3` и `bash tools/check-jsdoc-coverage.sh karaoke-public`. Новые публичные `export function` (T006, T009, T012, T015) и action (T015) — все 100%.
- [X] T023 Запустить `bash tools/check-feature-doc.sh docs/features/editor-tasks.md` — структура per-feature документа валидна (наличие всех секций из шаблона).
- [X] T024 Полный pre-commit: `pre-commit run --all-files`. Должны пройти все проверки (Docs, Baseline, KDoc, JSDoc, ktlint, ESLint ×2). Исправить если что-то падает.
- [X] T025 Запустить `./tools/baseline-stats.sh` — зафиксировать метрики до/после (если в CI был baseline healing, упомянуть в PR).
- [X] T026 Сборка финальных артефактов: `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` и `cd webvue3 && npm run build`, `cd karaoke-public && npm run build`. Должны собраться без ошибок.
- [ ] T027 Ручная валидация через quickstart.md (16 сценариев — см. `specs/154-editor-tasks-manage/quickstart.md`). **Не автоматизируется** — пользователь проходит вручную по браузеру и БД после deploy на dev/staging/prod.
  - **Status 2026-08-05 (зафиксировано в PR #200)**: код залит и прошёл CI 7/7 SUCCESS, но ручные сценарии требуют работающего `karaoke-app` контейнера (запрещено перезапускать локально на `nsa-i9` по `AGENTS.md`/`Constitution Principle I`). 16 сценариев задокументированы в `quickstart.md` и попадут в прод вместе с merge. Минимум обязательных сценариев: #1 (сортировка), #2 (refuse assigned), #6 (delete approved), #7 (delete all approved в ЛК), #9 (delete all в админке), #13 (гонка), #15 (двойной клик). **Фича считается валидированной только после прохождения всех 16 сценариев**.
- [X] T028 Создать PR: `git status` (только intended файлы), `git diff --stat`, `git add` нужные пути, **не** коммитить `deploy/.env`/`do.env`/`dist/`/`node_modules/` (CONSTITUTION VIII.3), `git commit -m "feat(editor-tasks): управление заданиями редактора в ЛК и админке"`. `git push origin 154-editor-tasks-manage`. `gh pr create --base master`. Дождаться `gh pr checks` → 7/7 SUCCESS (CI-gate). `gh pr merge --merge` **БЕЗ `--delete-branch`** (NON-NEGOTIABLE правило «feature-ветка НЕ удаляется после мёрджа» из AGENTS.md).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (T001)**: Setup — нет зависимостей.
- **Phase 2 (T002-T004)**: Foundational — может стартовать сразу после T001. **T002 блокирует Phase 6 (T012-T014) и Phase 7 (T015-T017)**. T003 и T004 — независимы от T002 (но T003 нужен до Phase 8 для проверок).
- **Phase 3 (T005, US-1)**: может стартовать сразу после Phase 2 (зависимость только от KDoc reference → T003). Технически — независимо от T002.
- **Phase 4 (T006-T008, US-2)**: может стартовать сразу после Phase 2. Технически — независимо от T002 (использует `KaraokeDbTable.delete`).
- **Phase 5 (T009-T011, US-3)**: параллельно с Phase 4 (разные файлы).
- **Phase 6 (T012-T014, US-4)**: **зависит от T002**. Параллельно с Phase 5 (разные файлы).
- **Phase 7 (T015-T017, US-5)**: **зависит от T002**. Параллельно с Phase 6 (разные файлы).
- **Phase 8 (Polish, T018-T028)**: зависит от **всех** Phase 3-7.

### User Story Dependencies

- **US-1 (P1) → US-2 (P1) → US-3 (P1) → US-4 (P2) → US-5 (P2)**: никаких логических зависимостей между stories — каждая добавляет свой эндпоинт/UI-кнопку. Все могут разрабатываться параллельно после Phase 2.
- **Только infra-зависимость**: US-4 и US-5 ждут T002 (`deleteIn` helper).

### Within Each User Story

- API client (`songEditorApi.js` / `store.js`) — независимо/параллельно с backend-методом контроллера (разные файлы).
- Backend-метод контроллера — независимо/параллельно с API client.
- UI-правка `EditorTasksView.vue`/`SongEditorTable.vue` — **после** API client + backend-метода (handler ссылается на оба).
- Логика UI: сначала handlers/template, потом confirm-dialogs.

### Parallel Opportunities

**Phase 4-5 (US-2 и US-3)** — все 6 задач можно делать параллельно двумя парами агентов (US-2 параллельно с US-3). Внутри каждой story — T-API-client || T-backend → T-UI.

**Phase 6 (US-4) параллельно с Phase 7 (US-5)** — разные модули (`karaoke-public` vs `webvue3`), разные контроллеры. T012 || T015 || T013 || T016 (все 4 в `[P]`). Затем T014 и T017 (UI) — каждый независим.

**Phase 8 (Polish)** — все `[P]`-задачи можно запускать параллельно на разных машинах (T018, T019, T020, T021, T022, T023, T025, T026). T024 (pre-commit) — после T018-T023. T027 (ручная валидация) — после всех автоматизированных. T028 — в конце.

---

## Parallel Example: User Story 4 + User Story 5 (after Phase 2 done)

```bash
# Запустить 4 задачи параллельно (4 разных файла):
Task: "Add deleteApprovedTasks() в karaoke-public/src/services/songEditorApi.js"  # T012 [P] [US-4]
Task: "Add deleteApproved(request) в karaoke-web/.../PublicSongEditorController.kt" # T013 [P] [US-4]
Task: "Add deleteApprovedAssignments(ctx, params) в webvue3/src/components/SongEditor/store.js"  # T015 [P] [US-5]
Task: "Add deleteApprovedAssignments(...) в karaoke-app/.../SongEditorController.kt"  # T016 [P] [US-5]

# После — 2 UI-задачи параллельно (разные файлы):
Task: "Add onDeleteAllApproved в EditorTasksView.vue"  # T014 [US-4]
Task: "Add onDeleteApproved в SongEditorTable.vue"  # T017 [US-5]
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2 + User Story 3)

1. T001 (verify branch + artifacts) — 2 минуты.
2. **Фаза 2 параллельно**: T002 (deleteIn helper), T003 (per-feature doc), T004 (README update).
3. **Фаза 3-5 (P1 stories)**: T005 (сортировка), T006-T008 (refuse), T009-T011 (delete approved). Это MVP — минимальный набор, чтобы редактор уже мог управлять своими заданиями.
4. **STOP and VALIDATE**: сценарии #1, #2, #3, #6 из quickstart.md. Если работает — деплой/демо.
5. **Фаза 6-7 (P2 stories)**: T012-T014 (массовое в ЛК), T015-T017 (массовое в админке). Это удобство.
6. **Фаза 8 (Polish)**: T018-T028.

### Incremental Delivery

1. **Setup + Foundational** → T001-T004 (~30 минут).
2. **US-1** → T005 (~10 минут, чисто UI правка). Тестируем сценарий #1 quickstart.md.
3. **US-2** → T006-T008 (~20 минут). Тестируем сценарии #2-#5 quickstart.md.
4. **US-3** → T009-T011 (~20 минут). Тестируем сценарий #6 quickstart.md.
5. **US-4** → T012-T014 (~30 минут). Тестируем сценарии #7-#8 quickstart.md.
6. **US-5** → T015-T017 (~40 минут с фильтрами). Тестируем сценарии #9-#12 quickstart.md.
7. **Polish** → T018-T028 (~30 минут + ручная валидация 1-2 часа с пользователем).
8. **MVP**: US-1 + US-2 + US-3 (P1 stories). Деплой можно начинать с этого набора.
9. **Полная фича**: P1 + P2 stories.

### Parallel Team Strategy

С одной машиной/агентом — последовательно как выше. С двумя+ агентами:

- **Agent A**: Phase 2 (T002) → Phase 3 (T005) → Phase 4 (T006-T008) → Phase 6 (T012-T014, US-4 ЛК).
- **Agent B**: Phase 2 (T003-T004) → Phase 5 (T009-T011) → Phase 7 (T015-T017, US-5 админка).

После — оба в Phase 8 (Polish, разные проверки на каждого).

---

## Notes

- `[P]` задачи = разные файлы, нет cross-зависимостей. Помечены `[P]` — могут запускаться параллельно.
- `[Story]` label привязывает задачу к user story для трассировки требований → реализации → quickstart-сценариев.
- **Никаких новых миграций БД**, никаких новых Gradle/NPM зависимостей, никаких изменений в `tbl_songs` / sync / News / Premium.
- **Никаких изменений в существующих эндпоинтах** (`delete`/`revoke` в `SongEditorController`; `save`/`submit`/`recall` в `PublicSongEditorController`) — фича только ДОБАВЛЯЕТ.
- **Никаких изменений в `docs/features/dual-db-sync.md`, `mlt-generator.md`, прочих** — это per-feature документ НОВЫЙ (FR-009 конституции).
- **Логирование**: `println` минимум с ID удалённого задания, user.id, размером батча — как и существующий код (FR-034, research.md п.9).
- **Коммит-сообщения** — на русском, по стилю: `feat(editor-tasks): описание` / `fix(editor-tasks): описание`.
- **Тесты**: не генерируем. Валидация — пользователь вручную через quickstart.md + SQL (16 сценариев).
- **CI-gate** (CONSTITUTION + AGENTS.md): PR обязательно с зелёным CI 7/7 SUCCESS. `gh pr merge --merge` **БЕЗ `--delete-branch`** — feature-ветка живёт после мёрджа.
- **Предостережение от Frequent pitfall**: при написании `deleteIn` обязательно учесть `ps.close()` в `finally`-блоке (или try-with-resources), иначе соединение «утечёт» на длинных батчах. Паттерн существующего `KaraokeDbTable.delete()` — `ps.close()` ВНУТРИ `try`-блока, без finally; копируем тот же паттерн для консистентности (атомарный kotlin-эквивалент). Если хочется быть строже — вынести в try-with-resources через `.use {}`.

---

**Total tasks**: 28 (T001-T028). **MVP scope**: T001-T011 (Phase 1-5, US-1 + US-2 + US-3, P1 stories).
