# Управление заданиями онлайн-редактора (ЛК + админка)

> **Status**: active
> **Feature Key**: `editor-tasks`
> **Branch**: `154-editor-tasks-manage`
> **Spec**: [`specs/154-editor-tasks-manage/spec.md`](../../specs/154-editor-tasks-manage/spec.md)
> **Plan**: [`specs/154-editor-tasks-manage/plan.md`](../../specs/154-editor-tasks-manage/plan.md)
> **Last Updated**: 2026-08-05

## Что делает

Добавляет управление списком заданий онлайн-редактора караоке-разметки в двух местах:

- **Личный кабинет редактора** (`karaoke-public/src/views/EditorTasksView.vue`):
  - Сортировка карточек — одобренные (`approved`) строго после активных (`assigned` / `in_progress` / `submitted` / `rejected`).
  - Кнопка «Отказаться» на каждой активной карточке → удаление задания + связанного черновика.
  - Кнопка «Удалить» на каждой одобренной карточке → удаление только записи задания (песня и разметка не трогаются).
  - Кнопка «Удалить все одобренные» рядом с заголовком → массовое удаление одобренных заданий одного клика.

- **Админка «Задания редактора»** (`webvue3/src/components/SongEditor/SongEditorTable.vue`):
  - Кнопка «Удалить все одобренные» в тулбаре → массовое удаление одобренных заданий с учётом активных фильтров (`filterStatus`, `filterAssigneeId`, `filterAuthor`) и `target` (local/remote).

## Зачем

Редактору нужно видеть только АКТИВНЫЕ задания в первую очередь (а не «свалку» из одобренных, на которые уже не надо реагировать). Админу нужна возможность быстро почистить одобренные задания в выборке (например, после большого батч-апрува).

Без этой фичи редактор видит свои задания в случайном порядке (по id DESC), без возможности отказаться от задания или удалить его — задание накапливается в его списке навсегда.

## Как работает

### Эндпоинты

**Публичные** (karaoke-web, `PublicSongEditorController.kt`):

| Endpoint | Назначение | Удаляет |
|---|---|---|
| `POST /api/public/account/editor/tasks/{id}/refuse` | Отказаться от активного | `tbl_song_assignments[id]` + `tbl_song_assignment_drafts[assignment_id=id]` |
| `POST /api/public/account/editor/tasks/{id}/delete` | Удалить одобренное | Только `tbl_song_assignments[id]` |
| `POST /api/public/account/editor/tasks/delete-approved` | Удалить все свои одобренные | `tbl_song_assignments WHERE assignee_id=user.id AND status=approved` |

**Админские** (karaoke-app, `SongEditorController.kt`):

| Endpoint | Назначение | Фильтры |
|---|---|---|
| `POST /api/songeditor/delete-approved` | Удалить одобренные в выборке | `target`, `filterAssigneeId`, `filterStatus`, `filterAuthor` |

### Сортировка в ЛК — на клиенте (Vue computed)

В `EditorTasksView.vue` модульный `STATUS_ORDER` и computed `sortedTasks` сортируют массив `tasks` так, чтобы карточки шли в порядке `assigned → in_progress → submitted → rejected → approved`. Никаких изменений `ORDER BY` на бэкенде, никакого перевыпуска jar ради презентационного изменения.

Шаблон повторяет уже существующий `STATUS_ORDER` в `webvue3/src/components/SongEditor/SongEditorTable.vue:117-123` — но с `approved: 4` (последним) и `rejected: 3` перед ним.

### Батч-удаление — один SQL через `KaraokeDbTable.deleteIn`

Новый helper `KaraokeDbTable.deleteIn(tableName, ids, database, sync)` (рядом с существующим `delete(...)`) делает один SQL `DELETE FROM tbl_song_assignments WHERE id = ANY(?)` для PostgreSQL через `connection.createArrayOf("BIGINT", ids.toTypedArray())`. Один round-trip вместо N → SC-004 (≤3 сек при N=10) и SC-005 (≤5 сек при N=100) легко выполняются.

Возвращает количество ФАКТИЧЕСКИ удалённых строк (PostgreSQL `executeUpdate()`). Пустой список → 0 без SQL.

### Композитный статус

Для админского батч-эндпоинта «одобренное» определяется через `SongAssignmentStatus.resolve(adminStatus, draft?.userStatus, reviewedAt, submittedAt) == APPROVED` — та же логика, что в существующем `digest()` (`SongEditorController.kt:215-262`) и в `SongAssignment.composeStatusesForSongIds` (`SongAssignment.kt:179-192`). Никаких новых определений.

### Подтверждение и `disabled`

- Одиночные кнопки — диалог подтверждения через `confirm()` (паттерн `SongEditorTable.vue:288`).
- Массовые кнопки — `disabled` (НЕ `v-if`) при пустой выборке + тултип «Нет одобренных заданий» (паттерн `SongEditorTable.vue:339-343` `.set-btn:disabled`).

## Инварианты / правила

1. **Сортировка в ЛК только на клиенте** — никаких изменений `ORDER BY` на бэкенде (изменение в одном Vue computed vs. перевыпуск jar + рестарт karaoke-web).
2. **`POST` для всех удалений** (не `DELETE`) — консистентно с существующими `POST /api/songeditor/delete`, `POST /api/songeditor/revoke`, `POST /tasks/{id}/save/submit/recall`. Конвенция проекта важнее REST-чистоты.
3. **Черновик удаляется ТОЛЬКО при «Отказаться»** (ДО удаления задания, orphan-cleanup). При «Удалить» одобренного или «Удалить все одобренные» — НЕ трогаем. Архив безвреден.
4. **Песня (`tbl_songs`) НЕ затрагивается НИКОГДА**. Удаление задания НЕ откатывает разметку, НЕ удаляет песню, НЕ отзывает публикацию (FR-030 спеки, SC-007).
5. **Авторизация**: задание MUST принадлежать текущему пользователю (public) — `loadOwnedAssignment(id, user.id)` возвращает `null` → 404. Нет утечки информации о чужих заданиях.
6. **Идемпотентность**: повторный клик по уже удалённому заданию → `{ok: false, error: "assignment_not_found"}` для одиночных, `{ok: true, deleted: 0}` для батча. UI обрабатывает «тихо» (SC-006).
7. **target-aware для админа**: пишем в ту же БД, что и `delete()`/`revoke()` через `withDb(target)`.
8. **Логирование — `println` минимум** (`FR-034`): `user.id`, размер батча, target. Полноценный SLF4J/Logback — на усмотрение (в спеке не блокирует).
9. **Никаких новых таблиц / колонок / индексов / миграций** — фича носит поведенческий характер.
10. **Никаких изменений в sync-целях** — удаление проходит через обычный diff-sync автоматически (sync `songassignments: SERVER_TO_LOCAL` уже настроено).

## Известные ловушки

- **`setArray(... ids.toTypedArray())`** — обязательно `Long` массив, иначе PG `ERROR: invalid input syntax for type bigint`. Контрпример: попытка скормить `IntArray` даст ошибку приведения.
- **Проверка композитного статуса в `/delete`** — НЕЛЬЗЯ удалять одобренные по «active» кнопкам, иначе дублируется логика. Проверка `statusOf(a, draft) == APPROVED` обязательна на сервере, иначе фронт может «обмануть» через прямой POST.
- **Имя `delete(...)` в Kotlin-методе** — допустимо как уникальное имя эндпоинта, но если возникнет конфликт с базовым классом — переименовать в `deleteApprovedAssignment(id, request)`. Mapping не зависит от имени метода.
- **`ps.close()` ВНУТРИ try-блока** — паттерн существующего `delete()` оставлен для консистентности. На длинных батчах формально лучше try-with-resources через `.use {}`, но копируем исходный паттерн.
- **target для админского батча** — UI передаёт `ctx.state.assignmentsTarget` (то же, что у `digest()`), НЕ `defaultTarget` (как у `loadSubmittedAssignmentsCount`). Это разные state'ы, и смешение ломает работу на проде.
- **«Все одобренные» ≠ все одобренные в БД** — это все одобренные в ТЕКУЩЕЙ ВЫБОРКЕ админа (с учётом фильтров), а не глобальная кнопка «убить всё одобренное». Фильтры — на клиенте, выбор ID — на сервере (`filterAssigneeId`/`filterStatus`/`filterAuthor` → `KaraokeDbTable.deleteIn`).
- **`STATUS_ORDER` в ЛК** vs. **`STATUS_ORDER` в админке** — обе таблицы имеют свою константу, пересекающуюся по структуре, но с разным весом `rejected`/`approved`. Не объединять в общую утилиту — каждая таблица может развиваться независимо.
- **`isBusy` при батче** — после клика и до ответа сервера `isBusy = true`, чтобы блокировать двойной клик. Реализовано через `try/finally` в `onDeleteApproved()` (admin) и `onDeleteAllApproved()` (ЛК).

## Ссылки

- Спека: [`specs/154-editor-tasks-manage/spec.md`](../../specs/154-editor-tasks-manage/spec.md)
- План: [`specs/154-editor-tasks-manage/plan.md`](../../specs/154-editor-tasks-manage/plan.md)
- Research: [`specs/154-editor-tasks-manage/research.md`](../../specs/154-editor-tasks-manage/research.md)
- Data Model: [`specs/154-editor-tasks-manage/data-model.md`](../../specs/154-editor-tasks-manage/data-model.md)
- Контракты: [`specs/154-editor-tasks-manage/contracts/`](../../specs/154-editor-tasks-manage/contracts/)
- Quickstart (16 сценариев ручной валидации): [`specs/154-editor-tasks-manage/quickstart.md`](../../specs/154-editor-tasks-manage/quickstart.md)
- Существующая таблица редакторов (UI-паттерн): [`songs-table.md`](./songs-table.md)
- Dual-DB Sync (sync-цель `songassignments`): [`dual-db-sync.md`](./dual-db-sync.md)

## Контракт per-feature документа

Структура соответствует [`specs/001-code-standards-docs/contracts/per-feature-doc.md`](../../specs/001-code-standards-docs/contracts/per-feature-doc.md):

- **Что делает** — обзор (1-2 параграфа).
- **Зачем** — решаемая проблема.
- **Как работает** — endpoints, поток, ключевые решения.
- **Инварианты / правила** — что MUST соблюдаться (10 пунктов выше).
- **Известные ловушки** — частые ошибки и анти-паттерны.
- **Ссылки** — спека, план, контракты, смежные документы.

CI-проверка: `tools/check-feature-doc.sh docs/features/editor-tasks.md` (валидирует наличие всех секций).
