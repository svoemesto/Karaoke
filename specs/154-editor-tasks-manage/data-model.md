# Data Model: 154-editor-tasks-manage

**Дата**: 2026-08-05
**Спека**: [spec.md](./spec.md)
**План**: [plan.md](./plan.md)
**Research**: [research.md](./research.md)

## TL;DR

Фича **не вводит** новых таблиц, колонок или индексов. Используются уже существующие `SongAssignment` (`tbl_song_assignments`) и `SongAssignmentDraft` (`tbl_song_assignment_drafts`) — только операции **DELETE** (одиночная и батч).

Этот документ фиксирует **как** фича работает с данными (что удаляется, что остаётся, что проверяется), а не новую схему БД.

---

## Существующие сущности (затрагиваемые, без изменений)

### `SongAssignment` (`tbl_song_assignments`)

Источник: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignment.kt`. Схема — `deploy/karaoke-db/10_song_assignments.sql` (синхронизирована с `tbl_song_assignments_sync`).

| Поле | Тип | Nullable | Что делает фича |
|---|---|---|---|
| `id` | `Long` (PK) | no | Используется как ключ для `WHERE id = ?` (одиночное удаление) или для `WHERE id = ANY(?)` (батч) |
| `assignee_id` | `Long` | no | Используется в `WHERE assignee_id = ?` для батч-эндпоинта public (только свои) |
| `song_id` | `Long` | no | **НЕ затрагивается** фичей — песня (`tbl_songs`) живёт независимо; удаление задания НЕ удаляет песню |
| `admin_status` | `String` | no | Читается через `SongAssignmentStatus.resolve()` для определения композитного статуса (в админском батче) |
| `review_comment` | `String` | no | **НЕ затрагивается** фичей |
| `reviewed_at` | `Timestamp?` | yes | **НЕ затрагивается** фичей |
| `assigned_at` | `Timestamp?` | yes | **НЕ затрагивается** фичей |
| `last_update` | `Timestamp?` | yes | **НЕ затрагивается** фичей (и так `useInDiff = false` в `SongAssignment.kt:71`) |

**Операции фичи** (через `KaraokeDbTable.delete` / `KaraokeDbTable.deleteIn`):
- Одиночная: `DELETE FROM tbl_song_assignments WHERE id = ?` (публичные `/refuse` и `/delete`; админский — без изменений).
- Батч: `DELETE FROM tbl_song_assignments WHERE id = ANY(?)` (публичный `/delete-approved`; админский `/api/songeditor/delete-approved`).

**Гарантии**:
- `id` — PK, индексирован → `WHERE id = ANY(?)` работает за единицы мс.
- `assignee_id` — индексирован (см. `10_song_assignments.sql`) → `WHERE assignee_id = ?` тоже быстро.
- Никаких FK на эту таблицу со стороны других таблиц (см. комментарий в `SongEditorController.kt:528-533`) — удаление безопасно.

### `SongAssignmentDraft` (`tbl_song_assignment_drafts`)

Источник: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongAssignmentDraft.kt`.

| Поле | Тип | Nullable | Что делает фича |
|---|---|---|---|
| `id` | `Long` (PK) | no | Используется как ключ для удаления через `SongAssignmentDraft.deleteByAssignment(...)` |
| `assignment_id` | `Long` | no | **Ключ для orphan-cleanup**: при «Отказаться» удаляем ВСЕ draft-записи с этим `assignment_id` ПЕРЕД удалением самого задания |
| `edited_source_text` / `edited_markers` / `user_status` / ... | — | — | **НЕ затрагиваются** фичей |

**Операции фичи**:
- Одиночная (только при «Отказаться»): `SongAssignmentDraft.deleteByAssignment(id, db)` — `DELETE FROM tbl_song_assignment_drafts WHERE assignment_id = ?` (см. существующий `SongEditorController.revoke()`, `SongEditorController.kt:545`).
- **НЕ выполняется** при «Удалить» (одобренных) и «Удалить все одобренные» — архив остаётся в `tbl_song_assignment_drafts` (см. research.md п.5).

**Гарантии**:
- `assignment_id` индексирован (см. `10_song_assignments.sql`) → `WHERE assignment_id = ?` быстро.
- Нет FK на `tbl_song_assignments.id` (комментарий в `SongEditorController.kt:528-533`) — порядок «draft → assignment» безопасен в обе стороны; мы выбираем «draft ПЕРВЫМ» для будущей совместимости с FK.

### `tbl_songs` (НЕ затрагивается)

**КРИТИЧНАЯ ГАРАНТИЯ** (FR-030 спеки, SC-007):
- Удаление задания НЕ откатывает разметку (`source_markers` / `source_text`).
- Удаление задания НЕ меняет `id_status` песни.
- Удаление задания НЕ удаляет саму песню.
- Удаление задания НЕ отзывает публикацию / новости / премиум-флаги.
- Удаление задания НЕ триггерит никаких side-effects на `tbl_songs` / `tbl_news` / `tbl_pictures` / `tbl_authors` / `tbl_albums`.

Это исключительно «снять карточку из списка редактора».

### `tbl_news` / `tbl_pictures` / `tbl_authors` / `tbl_albums` / `tbl_settings` (НЕ затрагиваются)

Фича не пишет ни в одну из этих таблиц. Удаление задания — изолированная операция.

---

## Связи между сущностями (затрагиваемые этой фичей)

```
SongAssignment (id)
    └── (orphan FK, нет реального FK)
        SongAssignmentDraft (assignment_id)

SongAssignment (song_id)
    └── (НЕ удаляется, НЕ модифицируется)
        Song (id)
```

Фича удаляет только верхний узел (`SongAssignment`) — и опционально сирот-зависимый (`SongAssignmentDraft`) при «Отказаться».

---

## Жизненный цикл / состояния

`SongAssignment` имеет композитный статус через `SongAssignmentStatus.resolve(admin_status, draft?.user_status, reviewed_at, submitted_at)` — см. `SongAssignmentStatus.kt:39-57`. Эта фича НЕ добавляет новых состояний — она **завершает** жизненный цикл задания удалением записи (переход → «не существует»).

**Переходы, которые фича делает возможными** (через удаление):

| Текущий статус | Действие пользователя | Что удаляется | Результат |
|---|---|---|---|
| `assigned` | «Отказаться» | `tbl_song_assignments[id]`, `tbl_song_assignment_drafts[assignment_id=id]` (если есть) | Песня снова «не назначена», доступна через «Назначить…» |
| `in_progress` | «Отказаться» | То же | То же |
| `submitted` | «Отказаться» | То же | То же (админ не успеет одобрить/отклонить) |
| `rejected` | «Отказаться» | То же | То же |
| `approved` | «Удалить» | Только `tbl_song_assignments[id]` | Песня остаётся в каталоге как была (разметка применена) |

| Текущий статус | Массовое действие | Что удаляется |
|---|---|---|
| `approved` (все) | «Удалить все одобренные» (редактор) | `tbl_song_assignments` где `assignee_id = current_user.id AND композитный статус = approved` |
| `approved` (в фильтре) | «Удалить все одобренные» (админ) | `tbl_song_assignments` где фильтры по композитному статусу = `approved` |

---

## Объём данных (производительность)

- На проде: ~19000+ записей в `tbl_song_assignments` (по аналогии с `tbl_news`); у одного редактора — десятки-сотни заданий; одобренных — обычно <50.
- Удаление по `id = ANY(?)` (PK-индекс) — единицы мс при любом N ≤ 1000.
- Удаление по `id IN (SELECT … WHERE admin_status = 'approved' AND assignee_id = ?)` — десятки мс при N=100.
- SC-004 (N=10 у редактора, ≤3 сек) и SC-005 (N=100 у админа, ≤5 сек) — комфортно укладываются.

---

## Что НЕ меняется (контракт «не трогать»)

- `tbl_song_assignments` — ни схемы, ни индексов, ни триггеров.
- `tbl_song_assignment_drafts` — ни схемы, ни индексов, ни триггеров.
- `SongAssignment` / `SongAssignmentDraft` (Kotlin-модели) — поля, геттеры, `loadByAssignee`, `composeStatusesForSongIds` — без изменений. Только **новая** утилита `KaraokeDbTable.deleteIn(...)`.
- `SyncRegistry.all` (sync-цель `songassignments`) — без изменений. Удаление проходит через обычный diff-sync автоматически.
- `KaraokeProperties.kt` — никаких новых флагов.
- Существующие эндпоинты — без изменений.

---

## Валидация (на стороне сервера)

| Условие | Действие | Код ответа |
|---|---|---|
| Задание не существует | Возврат `{ok: false, error: "assignment_not_found"}` | 200 (как у существующего `revoke`/`delete`) |
| Задание не принадлежит текущему пользователю (public) | Возврат `{ok: false, error: "not_found"}` | 404 (как у существующего `PublicSongEditorController.tasks()`, `:86`) |
| Пользователь — не редактор (public, `!user.isEditor`) | Возврат `notFound()` | 404 |
| target невалиден (admin) | `resolveDb(target)` → `Connection.local()` (fallback, как у существующих эндпоинтов) | — |
| Батч пустой (0 одобренных) | Возврат `{ok: true, deleted: 0}` | 200 |
| БД недоступна | `KaraokeDbTable.delete` ловит исключение, возвращает `false`; контроллер возвращает `{ok: false, error: "db_error"}` | 200 |
| Одобренных 0 у редактора / в выборке | Кнопка `disabled` на фронте; если запрос всё-таки ушёл — `{ok: true, deleted: 0}` | 200 |

---

## Версионирование

Не применимо — фича не меняет схему/API в смысле версионирования. Контракт эндпоинтов — новые маршруты, не ломающие обратной совместимости (старые маршруты продолжают работать).