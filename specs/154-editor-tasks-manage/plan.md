# Implementation Plan: 154-editor-tasks-manage

**Branch**: `154-editor-tasks-manage` | **Date**: 2026-08-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/154-editor-tasks-manage/spec.md`

## Summary

Добавить управление списком заданий онлайн-редактора караоке-разметки на двух сторонах:

1. **Личный кабинет редактора** (`karaoke-public`, `EditorTasksView.vue`):
   - Сортировка карточек: одобренные (`approved`) строго после активных (`assigned` / `in_progress` / `submitted` / `rejected`).
   - Кнопка «Отказаться» на активных карточках → удаление задания + связанного черновика.
   - Кнопка «Удалить» на одобренных карточках → удаление только записи о задании (песня и разметка не трогаются).
   - Кнопка «Удалить все одобренные» рядом с заголовком списка.

2. **Админка «Задания редактора»** (`webvue3`, `SongEditorTable.vue`):
   - Кнопка «Удалить все одобренные» в тулбаре, с учётом активных фильтров (`filterStatus`, `filterAssigneeId`, `filterAuthor`) и `target` (local/remote).

4 новых эндпоинта (без новых таблиц/колонок/sync-целей):
- `POST /api/public/account/editor/tasks/{id}/refuse` (public; удаление задания + черновика)
- `POST /api/public/account/editor/tasks/{id}/delete` (public; удаление только задания)
- `POST /api/public/account/editor/tasks/delete-approved` (public; батч для текущего пользователя)
- `POST /api/songeditor/delete-approved` (admin; батч с фильтрами + target)

Никаких изменений схемы БД, sync-флагов, новостной ленты, премиум-логики, MLT/KaraokeProperties. Существующие эндпоинты (`delete`, `revoke` в `SongEditorController`; `save`/`submit`/`recall` в `PublicSongEditorController`) не трогаются — фича только ДОБАВЛЯЕТ операции, не ломает обратной совместимости.

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x (JDK 17), Spring Boot 3.x.
- Frontend: JavaScript / Vue 3 (`karaoke-public` — Vue 3 + Vite + Bootstrap 5; `webvue3` — Vue 3 + Vite + Bootstrap-vue-next + Vuex).

**Primary Dependencies**:
- Backend: Spring Web MVC, kotlinx.serialization (для парсинга JSON-параметров, уже подключён в `SongEditorController`).
- Frontend: Vue 3 Composition API + Options API (по образцу существующего кода в `EditorTasksView.vue`); `bootstrap-vue-next` (`BSpinner`, `BTable`); Vuex 4 (только `webvue3`).
- Без новых Gradle/NPM зависимостей.

**Storage**: PostgreSQL через сырой JDBC (таблицы `tbl_song_assignments` и `tbl_song_assignment_drafts` уже существуют; новых миграций не требуется — `SongAssignment.delete` / `SongAssignmentDraft.deleteByAssignment` уже реализованы).

**Testing**: ручная проверка пользователем на dev/staging/prod (см. AGENTS.md — тестов в CI нет, существующие интеграционные тесты `@Disabled`). Автоматизированная валидация — quickstart.md (dev-сценарии).

**Target Platform**: Linux server (admin + прод), Docker + nginx. Контейнеры не меняются — добавляются только новые HTTP-эндпоинты в существующие war/jar-артефакты (`karaoke-app`, `karaoke-web`).

**Project Type**: web-service (бэкенд `karaoke-web` + `karaoke-app`) + 2 SPA-фронтенда (`karaoke-public`, `webvue3`).

**Performance Goals**:
- SC-002 спеки: одиночное «Отказаться» / «Удалить» ≤2 сек (round-trip + UI update).
- SC-004 спеки: «Удалить все одобренные» для редактора при N=10 ≤3 сек.
- SC-005 спеки: «Удалить все одобренные» для админа при N=100 ≤5 сек.
- Удаление реализуется через `DELETE FROM tbl_song_assignments WHERE id IN (...) AND assignee_id = ?` (один SQL) — `id` индексирован (PRIMARY KEY), `assignee_id` индексирован (см. существующий индекс в `10_song_assignments.sql`). Производительность не зависит от объёма `tbl_song_assignments` (19000+ записей) — все запросы точечные по `id` или по `(assignee_id, admin_status)`.

**Constraints**:
- Никаких новых полей/таблиц/индексов/миграций — фича носит поведенческий характер (UI + 4 новых эндпоинта).
- Никаких изменений в SyncRegistry, sync-цели `songassignments` (SERVER_TO_LOCAL, уже настроена) — удаление проходит через обычный diff-sync (запись в `tbl_song_assignments` → запись на другой стороне через уже существующий механизм).
- Никаких изменений в `tbl_songs` / новостях / премиум-флагах / разметке — это гарантия FR-030 спеки (удаление задания НЕ откатывает разметку).
- Никаких изменений в существующих эндпоинтах — фича только ДОБАВЛЯЕТ (FR-033 спеки).
- Сортировка на клиенте (Vue computed) — никакого нового `ORDER BY` на бэкенде (см. Assumptions спеки).

**Scale/Scope**: 4 новых эндпоинта (2 в `PublicSongEditorController`, 2 в `SongEditorController`), 2 фронтенд-компонента (правка `EditorTasksView.vue`, правка `SongEditorTable.vue`), 2 файла-клиента (правка `karaoke-public/src/services/songEditorApi.js`, правка `webvue3/src/components/SongEditor/store.js`). Никаких изменений в БД, никаких новых сущностей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Релевантность | Решение |
|---|---|---|
| I. Self-contained автопайплайн | Не применимо — фича не трогает hot-path обработки медиа, не запускает ffmpeg/Demucs/Sheetsage | ✅ pass |
| II. Сырой JDBC + дифф по хэшам | **Применимо** — фича пишет в `tbl_song_assignments` / `tbl_song_assignment_drafts` через сырой JDBC (`KaraokeDbTable.delete` уже реализован), никаких JPA/Hibernate. Сравнение `id IN (...)` для батча — пакетное, не N+1 (см. Assumptions спеки). Никаких изменений в recordhash/sync-триггерах (удаление — обычный DELETE, проходит через существующий diff-sync автоматически). | ✅ pass |
| III. Двух-БД синхронизация через SyncRegistry | **Применимо** — `tbl_song_assignments` уже синкается (`SERVER_TO_LOCAL`); никаких изменений в `SyncRegistry.all` / `sync/SyncTarget.kt` / `KaraokeProperties.kt` (sync-флаги уже настроены). Никаких новых sync-целей. | ✅ pass |
| IV. Async-очередь задач с парсингом stdout | Не применимо — фича не запускает новых OS-подпроцессов | ✅ pass |
| V. Двух-фронтенд: админка и публичный сайт — разные приложения | **Применимо** — фича трогает и `karaoke-public` (Vue 3 SPA, `EditorTasksView.vue`), и `webvue3` (Vue 3 + Vuex, `SongEditorTable.vue`). Разделение ответственностей сохраняется: личный кабинет редактора живёт в `karaoke-public` (с `songEditorApi.js`), админка — в `webvue3` (с `store.js`). Кнопка «Удалить все одобренные» появляется в ОБОИХ местах, но с разной семантикой (редактор — только свои, админ — с фильтрами), как и было зафиксировано в спеке (FR-022..FR-028). | ✅ pass |
| VI. Code Standards | **Применимо** — новые публичные функции Kotlin MUST иметь KDoc с `@see docs/features/editor-tasks.md` (FR-006 конституции); новый per-feature документ `docs/features/editor-tasks.md` MUST быть создан в этом PR (FR-009 конституции, добавить в `docs/features/README.md`). ktlint/ESLint baseline — без новых нарушений (тест `tools/baseline-stats.sh`). JSON-ключи без `is`-префикса (см. AGENTS.md Q&A про Jackson-Kotlin binding) — НЕ применимо (нет boolean-полей в новых DTO, удаление возвращает `{ok, deleted, error}`). | ✅ pass (планируется выполнить в Phase 1) |
| VII. Cross-Machine Setup | Не применимо напрямую — никаких новых AI-конфигов, никаких изменений в `.gitattributes`/`.git-blame-ignore-revs` | ✅ pass |
| VIII. Секреты и git-гигиена | Не применимо — никаких секретов, никаких новых env-переменных, никаких изменений в `.env`/`do.env` | ✅ pass |

**Итог**: все 8 принципов проходят. Complexity Tracking остаётся пустым (нет нарушений, которые нужно обосновывать).

## Project Structure

### Documentation (this feature)

```text
specs/154-editor-tasks-manage/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── public-editor-tasks-api.md   # контракт 3 публичных эндпоинтов
│   └── admin-editor-tasks-api.md    # контракт админского батч-эндпоинта
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся /speckit.plan)
```

### Source Code (repository root)

Фича затрагивает 6 мест в 4 модулях:

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/controllers/
    └── SongEditorController.kt           # ПРАВКА: добавить POST /api/songeditor/delete-approved

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/controllers/
    └── PublicSongEditorController.kt     # ПРАВКА: добавить POST /tasks/{id}/refuse, POST /tasks/{id}/delete, POST /tasks/delete-approved

karaoke-public/
└── src/
    ├── services/
    │   └── songEditorApi.js              # ПРАВКА: добавить refuseTask(), deleteTask(), deleteAllApprovedTasks()
    └── views/
        └── EditorTasksView.vue           # ПРАВКА: сортировка tasks (approved внизу), кнопки «Отказаться» / «Удалить» / «Удалить все одобренные», диалоги подтверждения

webvue3/
└── src/components/SongEditor/
    ├── SongEditorTable.vue               # ПРАВКА: кнопка «Удалить все одобренные» в тулбаре, диалог подтверждения
    └── store.js                          # ПРАВКА: добавить deleteApprovedAssignments() action

docs/features/
├── README.md                             # ПРАВКА: добавить запись editor-tasks.md (22 → 23 фич)
└── editor-tasks.md                       # НОВЫЙ: per-feature документ (FR-006/FR-009 конституции)
```

**`tbl_song_assignments` / `tbl_song_assignment_drafts` НЕ правятся** (никаких новых колонок/индексов/миграций).

**SyncRegistry НЕ правится** (sync-цель `songassignments` остаётся как есть).

**Karaoke.properties НЕ правится** (никаких новых настроек).

**Structure Decision**: фича не вводит новых проектов/модулей/Gradle-модулей/Docker-образов. Используем существующую структуру `karaoke-app` (Spring Boot admin) + `karaoke-web` (Spring Boot public) + `karaoke-public` (Vue 3 SPA) + `webvue3` (Vue 3 SPA). Все правки — additive (новые методы контроллеров, новые кнопки в существующих view, новый per-feature документ).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нарушений нет — таблица пустая (Constitution Check все 8 принципов прошли).

---

## Phase 0: Outline & Research

См. [research.md](./research.md) — все NEEDS CLARIFICATION уже разрешены на этапе спеки (Assumptions), открытых вопросов нет.

Ключевые решения (для фиксации в research.md):

1. **Контракты эндпоинтов** — `POST` (не `DELETE`) для консистентности с существующими `delete`/`revoke` (см. AGENTS.md Q&A про паттерн `redirectErrorStream(true)` — аналогично, в этом проекте для «удаления» используется `POST`). Имена: `/tasks/{id}/refuse`, `/tasks/{id}/delete`, `/tasks/delete-approved` (public); `/songeditor/delete-approved` (admin).
2. **Сортировка** — на клиенте (Vue computed) в `EditorTasksView.vue`. Никаких изменений `ORDER BY` в `SongAssignment.loadByAssignee`. Дефолтный ключ сортировки внутри групп — `id DESC` (уже используется в существующем `.sorted()`).
3. **Батч vs. один SQL для «Удалить все одобренные»** — один SQL: `DELETE FROM tbl_song_assignments WHERE id IN (SELECT id FROM ...) AND assignee_id = ?`. Для админа — `DELETE FROM tbl_song_assignments WHERE id IN (SELECT id FROM ...) AND assignee_id = ?` с дополнительными фильтрами. Реализация через `SongAssignment.loadByAssignee` + фильтрация по композитному статусу + `KaraokeDbTable.deleteIn(tableName, ids, db)` — НОВЫЙ helper-метод в `KaraokeDbTable` (см. ниже).
4. **Черновики при удалении одобренных (массовое)** — НЕ удаляем (архив не мешает; см. Assumptions спеки). При одиночном «Удалить» для одобренных — тоже не трогаем (черновик уже не играет роли после апрува).
5. **Черновики при «Отказаться»** — удаляем ДО задания (orphan-cleanup, как в существующем `revoke()` в `SongEditorController.kt:533-548`).
6. **`disabled` vs. `hidden` для пустой выборки** — `disabled` (видна серая кнопка + тултип «нет одобренных заданий») — UX-прозрачно.
7. **Что считать «одобренным» в админской выборке** — композитный статус из `SongAssignmentStatus.resolve(adminStatus, draft?.userStatus, ...) == APPROVED`. Уже используется в `digest()` — никаких новых определений.
8. **Таймаут/батчинг** — для типичных объёмов (≤100 одобренных у одного редактора, ≤500 у админа в фильтре) — единичный DELETE. Батов на N>1000 — на этапе реализации решается отдельно (в спеке не блокирует).
9. **Логирование** — `println("[editor-tasks/delete] id=$id user=${user.id} batch=$N")` минимум (см. FR-034 спеки). Полноценный `log.info`/`log.warn` — на усмотрение реализации (в спеке не требуется).

## Phase 1: Design & Contracts

См. [data-model.md](./data-model.md), [contracts/public-editor-tasks-api.md](./contracts/public-editor-tasks-api.md), [contracts/admin-editor-tasks-api.md](./contracts/admin-editor-tasks-api.md), [quickstart.md](./quickstart.md).

**Новый helper в `KaraokeDbTable`** (необходим для батч-удаления):

```kotlin
// В companion object KaraokeDbTable — рядом с существующим fun delete(...)
fun deleteIn(
    tableName: String,
    ids: List<Long>,
    database: KaraokeConnection,
    sync: Boolean = false,
): Int {
    if (ids.isEmpty()) return 0
    val connection = database.getConnection() ?: return 0
    val sql = "DELETE FROM $tableName${if (sync) "_sync" else ""} WHERE id = ANY(?)"
    val ps = connection.prepareStatement(sql)
    // PG-specific: setArray с LongArray. Иначе — fallback на "id IN (1,2,3)" с экранированием.
    // Конкретный способ — на этапе реализации (оба варианта есть в кодовой базе, см. SongAssignment.loadBySongIds).
    ...
}
```

(Это не «новая сущность», а утилита рядом с существующим `delete()` — нужна для одного SQL-запроса вместо N+1 цикла. Альтернатива — итерация `SongAssignment.delete(id, db)` для каждого id; допустимо для N≤100, см. Assumptions.)

**Новый Vue-computed в `EditorTasksView.vue`**:

```js
computed: {
  sortedTasks() {
    const STATUS_ORDER = { submitted: 0, in_progress: 1, assigned: 2, rejected: 3, approved: 4 }
    return [...this.tasks].sort((a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99))
  },
  approvedCount() {
    return this.tasks.filter((t) => t.status === 'approved').length
  },
}
```

(Шаблон повторяет уже существующий `STATUS_ORDER` в `SongEditorTable.vue:117-123` — консистентно.)

**Новые actions в `webvue3/src/components/SongEditor/store.js`**:

```js
deleteApprovedAssignments(ctx, params) {
  return promisedXMLHttpRequest({
    method: 'POST',
    url: '/api/songeditor/delete-approved',
    params: {
      target: ctx.state.assignmentsTarget,
      filterAssigneeId: params.filterAssigneeId,
      filterStatus: params.filterStatus,
      filterAuthor: params.filterAuthor,
    },
  }).then((data) => JSON.parse(data))
}
```

---

## Re-evaluate Constitution Check post-design

После Phase 1 дизайн не вводит новых зависимостей/таблиц/sync-целей/проектов — все 8 принципов остаются в `pass`. Никаких дополнительных обоснований не требуется.

Единственное замечание — новый `KaraokeDbTable.deleteIn(...)` это утилита, не сущность; он подчиняется тому же Principle II (сырой JDBC + паттерн «один SQL для батча»); никаких новых sync-флагов, никаких изменений в recordhash/sync-триггерах.