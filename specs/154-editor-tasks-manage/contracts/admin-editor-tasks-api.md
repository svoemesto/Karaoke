# Contract: Админский эндпоинт «Удалить все одобренные» (`POST /api/songeditor/delete-approved`)

**Дата**: 2026-08-05
**Спека**: [../spec.md](../spec.md)
**План**: [../plan.md](../plan.md)
**Research**: [../research.md](../research.md)
**Data Model**: [../data-model.md](../data-model.md)

## TL;DR

Фича добавляет **1 новый эндпоинт** к существующему контроллеру `SongEditorController` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`):

- `POST /api/songeditor/delete-approved` — массовое удаление одобренных заданий с учётом активных фильтров и `target` (local/remote).

Это симметричный аналог публичного `/api/public/account/editor/tasks/delete-approved`, но с фильтрами по `assignee_id` / `author` / `status` (как у существующего `digest()`).

Никаких изменений в существующих `assign`, `digest`, `byId`, `approve`, `reject`, `delete`, `revoke`, `submittedcount`, `statusBySongIds`, `editById`, `editSave`, `triggerRenderMp4DemoIfNeeded` и т.п.

---

## Конвенция проекта

`POST` (не `DELETE`) для консистентности с существующими `delete`/`revoke` в `SongEditorController.kt:514` и `:533`. Параметры передаются через `@RequestParam` (form-encoded).

`target` — обязательный параметр для всех записывающих эндпоинтов (default `local`); реальный рабочий цикл часто идёт на PROD (см. комментарий в `SongEditorController.kt:42-56`). Чтение фильтруется по той же логике, что у `digest()` (`SongEditorController.kt:208-262`).

---

## Эндпоинт: `POST /api/songeditor/delete-approved`

**Назначение**: администратор удаляет все одобренные задания, попадающие под активные фильтры (`filterStatus` / `filterAssigneeId` / `filterAuthor`), в выбранной БД (`target=local|remote`).

**Path параметр**: нет.

**Параметры запроса** (`@RequestParam`, form-encoded):

| Параметр | Тип | Обязательный | Default | Описание |
|---|---|---|---|---|
| `target` | `String` (enum `local`/`remote`) | нет | `local` | В какой БД удалять. Семантика та же, что у `delete()`/`revoke()` — пишем в реальную БД задания, иначе на ней задание останется висеть нетронутым. |
| `filterAssigneeId` | `Long?` | нет | `null` | Фильтр по исполнителю (если задан — удаляются только одобренные задания этого исполнителя) |
| `filterStatus` | `String?` | нет | `null` | Фильтр по композитному статусу. Для «Удалить все одобренные» UI передаёт `approved` (после проверки, что выборка не пуста). |
| `filterAuthor` | `String?` | нет | `null` | Подстрока автора (case-insensitive), как в существующем `digest()` (`SongEditorController.kt:258-260`). |

**Тело запроса**: нет.

**Поведение**:
1. `withDb(target) { db -> ... }` (стандартный паттерн, см. `SongEditorController.kt:76-89`).
2. Загрузить все задания: `SongAssignment.loadAll(db, storageService, storageApiClient)`.
3. Применить `filterAssigneeId` (если задан): `assignments = assignments.filter { it.assigneeId == filterAssigneeId }`.
4. Загрузить все черновики: `SongAssignmentDraft.loadByAssignments(assignments.map { it.id }, db, ...)`.
5. Загрузить всех пользователей: `assignments.map { it.assigneeId }.distinct().associateWith { SiteUser.getSiteUserById(...) }` — для трекинга в логе (необязательно, но полезно).
6. Вычислить композитный статус для каждого через `SongAssignmentStatus.resolve(adminStatus, draft?.userStatus, reviewedAt, submittedAt)`.
7. Отфильтровать те, у которых `status == APPROVED` (или по `filterStatus`, если задан — но UI передаёт `approved`).
8. Применить `filterAuthor` (если задан): `assignments = assignments.filter { songByAssignment[it.id]?.author?.contains(filterAuthor, ignoreCase = true) == true }` — требует загрузки песен батчем (как в существующем `digest()`, `SongEditorController.kt:226-231`).
9. Получить список `id` отфильтрованных.
10. Если список пуст → `{ok: true, deleted: 0}` (без SQL).
11. Если не пуст → `KaraokeDbTable.deleteIn(tableName = "tbl_song_assignments", ids, db)`.
12. **Логирование**: `println("[editor-tasks/admin-delete-approved] target=$target filters={status=$filterStatus, assignee=$filterAssigneeId, author=$filterAuthor} deleted=$N")`.

**Ответ** (200 OK):
```json
{ "ok": true, "deleted": 5 }
```

**Ответ при ошибке** (200 OK):
```json
{ "ok": false, "error": "db_error" }
```

**Гарантии**:
- **target-aware**: пишем в ту же БД, что и существующие `delete()`/`revoke()` — для админки на remote это означает реальное удаление на сервере.
- **Фильтры**: результат симметричен тому, что видит админ в таблице (композитный статус через `SongAssignmentStatus.resolve` + фильтры по исполнителю/автору).
- **Идемпотентность**: повторный клик → `{ok: true, deleted: 0}` (все уже удалены) — UI обрабатывает «тихо» (SC-006 спеки).
- **Без N+1**: задания, черновики, пользователи, песни — батчами через `loadByAssignments` / `loadListFromDbByIds` / `associateWith` (как в существующем `digest()`, `SongEditorController.kt:215-262`).
- **Никаких side-effects на `tbl_songs` / `tbl_news`** (FR-030 спеки).
- **Никакого удаления черновиков** (research.md п.5).

**Ссылка на реализацию**: новый метод `deleteApprovedAssignments(@RequestParam target: String?, @RequestParam filterAssigneeId: Long?, @RequestParam filterStatus: String?, @RequestParam filterAuthor: String?): Map<String, Any?>` в `SongEditorController.kt` (рядом с существующим `delete()` / `revoke()`).

---

## Авторизация

`permitAll()` в `SecurityConfig.kt` (см. `webvue3/src/components/SongEditor/SongEditorTable.vue:128` — KDoc явно говорит «`permitAll()` в `SecurityConfig.kt`, без авторизации»). Доступ к эндпоинту есть у всех, у кого есть доступ к `webvue3` (то есть у любого, кто открыл админку — модель безопасности «trust internal network»).

Никакой проверки ролей не добавляется.

---

## Клиентский API (использование с фронта)

В `webvue3/src/components/SongEditor/store.js` добавляется 1 action (рядом с существующими `deleteAssignment`/`revokeAssignment`):

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
}
```

Паттерн `params[key] || undefined` — чтобы пустые строки не отправлялись на бэкенд (иначе Spring биндит их как `""`, а не как `null`).

---

## Использование в UI

В `webvue3/src/components/SongEditor/SongEditorTable.vue` добавляется кнопка в тулбар (после «+ Назначить песню»):

```html
<button class="set-toolbar-item set-btn set-btn-del-approved"
        :disabled="approvedCount === 0"
        @click="onDeleteApproved">Удалить все одобренные</button>
```

`approvedCount` — computed из `digest`:

```js
approvedCount() {
  return this.digest.filter(t => t.status === 'approved').length
}
```

`onDeleteApproved` — метод с диалогом подтверждения:

```js
async onDeleteApproved() {
  const n = this.approvedCount
  if (n === 0) return
  if (!confirm(`Удалить ${n} одобренных заданий (с учётом фильтров)? Это действие нельзя отменить.`)) return
  await this.$store.dispatch('deleteApprovedAssignments', {
    filterAssigneeId: this.filterAssigneeId,
    filterStatus: this.filterStatus,  // обычно '' или 'approved'
    filterAuthor: this.filterAuthor,
  })
  this.reload()
}
```

После успешного `deleteApprovedAssignments` — `reload()` для перезагрузки таблицы.

---

## Что НЕ входит в контракт этой фичи

- Никаких изменений в существующих эндпоинтах (`assign`, `digest`, `byId`, `approve`, `reject`, `delete`, `revoke`, `submittedcount`, `statusBySongIds`, `editById`, `editSave`).
- Никаких изменений в `permitAll()` / `SecurityConfig.kt` — фича не вводит ролей.
- Никаких изменений в sync-целях (удаление проходит через обычный diff-sync автоматически).
- Никаких изменений в `KaraokeProperties` — нет новых флагов.

---

## Версионирование

Не применимо — новый эндпоинт аддитивный, не ломает обратной совместимости.