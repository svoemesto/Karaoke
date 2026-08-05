# Contract: Публичные эндпоинты управления заданиями редактора

**Дата**: 2026-08-05
**Спека**: [../spec.md](../spec.md)
**План**: [../plan.md](../plan.md)
**Research**: [../research.md](../research.md)
**Data Model**: [../data-model.md](../data-model.md)

## TL;DR

Фича добавляет **3 новых эндпоинта** к существующему контроллеру `PublicSongEditorController` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSongEditorController.kt`):

1. `POST /api/public/account/editor/tasks/{id}/refuse` — «Отказаться» от активного задания.
2. `POST /api/public/account/editor/tasks/{id}/delete` — «Удалить» одобренное задание.
3. `POST /api/public/account/editor/tasks/delete-approved` — «Удалить все одобренные» (батч).

Все три защищены той же авторизацией, что и существующие эндпоинты (`SiteAuthInterceptor` → `request.getAttribute(SITE_USER_ATTR) as SiteUser`). Никаких изменений в существующих `tasks`, `task`, `save`, `submit`, `recall`.

---

## Конвенция проекта

В проекте преобладает `POST` для операций удаления (см. существующие `POST /api/songeditor/delete`, `POST /api/songeditor/revoke` в `SongEditorController.kt`). Новые эндпоинты наследуют эту конвенцию — REST-чистый `DELETE` не используется.

Все запросы и ответы — JSON. Параметры передаются через `@RequestParam` (form-encoded) для консистентности с существующим кодом.

---

## Эндпоинт 1: `POST /api/public/account/editor/tasks/{id}/refuse`

**Назначение**: редактор отказывается от задания в активном статусе (`assigned` / `in_progress` / `submitted` / `rejected`). Удаляется запись задания И связанный черновик.

**Path параметр**: `id: Long` — `SongAssignment.id`.

**Тело запроса**: нет (все параметры — в URL).

**Поведение**:
1. Проверка `user.isEditor == true` (иначе — 404).
2. `loadOwnedAssignment(id, user.id)` — задание должно существовать И принадлежать текущему пользователю (иначе — 404 `not_found`).
3. `SongAssignmentDraft.deleteByAssignment(id, db)` — orphan-cleanup (тот же порядок, что в `SongEditorController.revoke()`).
4. `SongAssignment.delete(id, db)` через `KaraokeDbTable.delete(...)`.

**Ответ** (200 OK):
```json
{ "ok": true, "deleted": 1 }
```

**Ответ при ошибке** (200 OK, как у существующих эндпоинтов):
```json
{ "ok": false, "error": "assignment_not_found" }
```

или (404 Not Found, как у существующего `tasks()`):
```json
{ "error": "not_found" }
```

**Гарантии**:
- Идемпотентность: повторный клик по уже удалённому заданию → `{ok: false, error: "assignment_not_found"}` (UI обрабатывает «тихо» — карточка просто исчезает, см. FR-006 спеки и SC-006).
- Авторизация: задание MUST принадлежать текущему пользователю (нет утечки информации о чужих заданиях).
- Никакого возврата `canEdit` / проверки текущего статуса — семантика «Отказаться» разрешена в любом активном статусе; для одобренных используется `/delete` (FR-004 спеки).

**Ссылка на реализацию**: новый метод `refuse(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>>` в `PublicSongEditorController.kt` (рядом с существующими `save`/`submit`/`recall`).

---

## Эндпоинт 2: `POST /api/public/account/editor/tasks/{id}/delete`

**Назначение**: редактор удаляет одобренное задание (`approved`). Удаляется ТОЛЬКО запись задания — песня и разметка не трогаются.

**Path параметр**: `id: Long` — `SongAssignment.id`.

**Тело запроса**: нет.

**Поведение**:
1. Проверка `user.isEditor == true` (иначе — 404).
2. `loadOwnedAssignment(id, user.id)` — задание должно существовать И принадлежать текущему пользователю (иначе — 404 `not_found`).
3. **Проверка статуса**: `statusOf(assignment, draft) == APPROVED` (композитный статус через `SongAssignmentStatus.resolve()`). Если НЕ одобрено — `{ok: false, error: "not_approved"}` (UI должен показать «можно удалять только одобренные»). Семантика: для активных заданий используется `/refuse`.
4. `SongAssignment.delete(id, db)` через `KaraokeDbTable.delete(...)`. **Черновик НЕ удаляется** (архив).

**Ответ** (200 OK):
```json
{ "ok": true, "deleted": 1 }
```

**Ответ при ошибке** (200 OK):
```json
{ "ok": false, "error": "assignment_not_found" }
{ "ok": false, "error": "not_approved" }
```

**Гарантии**:
- Идемпотентность: повторный клик по уже удалённому → `{ok: false, error: "assignment_not_found"}`.
- Песня (`tbl_songs`) НЕ затрагивается.
- Разметка (`tbl_songs.source_markers` / `source_text`) НЕ откатывается.
- `tbl_song_assignment_drafts` НЕ трогается.

**Ссылка на реализацию**: новый метод `delete(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Map<String, Any?>>` в `PublicSongEditorController.kt`.

---

## Эндпоинт 3: `POST /api/public/account/editor/tasks/delete-approved`

**Назначение**: редактор удаляет все свои одобренные задания одним запросом (батч).

**Path параметр**: нет.

**Тело запроса**: нет.

**Поведение**:
1. Проверка `user.isEditor == true` (иначе — 404).
2. Загрузить все задания редактора: `SongAssignment.loadByAssignee(user.id, db, ...)`.
3. Загрузить все черновики: `SongAssignmentDraft.loadByAssignments(...)`.
4. Вычислить композитный статус для каждого через `SongAssignmentStatus.resolve(adminStatus, draft?.userStatus, reviewedAt, submittedAt)`.
5. Отфильтровать те, у которых `status == APPROVED`.
6. Получить список `id` отфильтрованных.
7. Если список пуст → `{ok: true, deleted: 0}` (без SQL).
8. Если не пуст → `KaraokeDbTable.deleteIn(tableName = "tbl_song_assignments", ids, db)` — один SQL `DELETE FROM tbl_song_assignments WHERE id = ANY(?)`.
9. Возвращается количество фактически удалённых строк (PostgreSQL `executeUpdate()`).

**Ответ** (200 OK):
```json
{ "ok": true, "deleted": 3 }
```

**Ответ при ошибке** (200 OK):
```json
{ "ok": false, "error": "db_error" }
```

**Гарантии**:
- Идемпотентность: повторный клик → `{ok: true, deleted: 0}` (все уже удалены) — UI обрабатывает «тихо» (SC-006 спеки).
- Никакого `task.assigneeId == user.id` check — фильтр через `loadByAssignee(user.id, ...)` уже гарантирует, что работаем только со СВОИМИ.
- Никакого удаления черновиков (FR-019 спеки, research.md п.5).
- Никаких side-effects на `tbl_songs` / `tbl_news` / премиум-флагах (FR-030 спеки).

**Ссылка на реализацию**: новый метод `deleteApproved(request: HttpServletRequest): ResponseEntity<Map<String, Any?>>` в `PublicSongEditorController.kt`.

---

## Авторизация (общая для всех трёх)

Все три эндпоинта наследуют защиту, реализованную через `SiteAuthInterceptor`:

- `SiteAuthInterceptor.SITE_USER_ATTR` уже установлен в `request.getAttribute(...)` к моменту вызова (см. существующий `tasks()` / `submit()` / `recall()`).
- Если `user.isEditor == false` → 404 `not_found` (предотвращает разведку эндпоинтов не-редакторами).
- Если задание не принадлежит текущему пользователю → 404 `not_found` (нет утечки существования чужих заданий).

Токен авторизации — `Bearer ${token}` из `localStorage.getItem('km_auth_token')` (см. `songEditorApi.js:6-8`).

---

## Клиентский API (использование с фронта)

В `karaoke-public/src/services/songEditorApi.js` добавляются 3 функции (рядом с существующими `fetchTasks`/`fetchTask`/`saveTask`/`submitTask`/`recallTask`):

```js
export function refuseTask(id) {
  return authPost(`${BASE}/tasks/${id}/refuse`, {}, token())
}

export function deleteTask(id) {
  return authPost(`${BASE}/tasks/${id}/delete`, {}, token())
}

export function deleteApprovedTasks() {
  return authPost(`${BASE}/tasks/delete-approved`, {}, token())
}
```

Возвращают `{ status: xhr.status, body: parsedJSON }` — паттерн уже зафиксирован в `authApi.js:20`.

---

## Что НЕ входит в контракт этой фичи

- Никаких изменений в существующих эндпоинтах (`tasks`, `task`, `save`, `submit`, `recall`).
- Никаких изменений в `SiteAuthInterceptor`.
- Никаких изменений в `WORKING_DATABASE` — все три эндпоинта пишут в локальную БД (ту же, что и существующие).
- Никаких новых DTO — возвращают `Map<String, Any?>` (как и существующие эндпоинты).
- Никаких изменений в публичной ленте задач `/tasks` — фича только ДОБАВЛЯЕТ операции удаления.

---

## Версионирование

Не применимо — новые эндпоинты аддитивные, не ломают обратной совместимости. Существующие потребители (`EditorTasksView.vue`) могут переходить на новые эндпоинты постепенно (миграция фронта — в том же PR, что и бэкенд; см. plan.md, Project Structure).