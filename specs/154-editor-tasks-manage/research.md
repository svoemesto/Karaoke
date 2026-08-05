# Research: 154-editor-tasks-manage

**Дата**: 2026-08-05
**Спека**: [spec.md](./spec.md)
**План**: [plan.md](./plan.md)

## Контекст

Фича — управление списком заданий онлайн-редактора караоке-разметки на двух сторонах:

- **Личный кабинет** (`karaoke-public`, `EditorTasksView.vue`): сортировка карточек (одобренные внизу), кнопки «Отказаться» / «Удалить» для каждой карточки, массовая «Удалить все одобренные».
- **Админка** (`webvue3`, `SongEditorTable.vue`): массовая «Удалить все одобренные» в тулбаре с учётом активных фильтров.

4 новых HTTP-эндпоинта (2 в `PublicSongEditorController`, 2 в `SongEditorController`); 1 новый утилитарный метод в `KaraokeDbTable.deleteIn(...)` для батч-удаления одним SQL.

Никаких новых таблиц, колонок, индексов, миграций; никаких изменений в `SyncRegistry` / `tbl_song_assignments` / `tbl_song_assignment_drafts` / sync-целях; никаких изменений в `Karaoke.properties`.

Все архитектурные решения зафиксированы на этапе спеки (`Assumptions`); открытых `NEEDS CLARIFICATION` нет. Все 8 принципов конституции проходят.

---

## Решения

### 1. HTTP-метод для эндпоинтов удаления — `POST` (не `DELETE`)

**Decision**: все 4 новых эндпоинта используют `POST` (не REST-чистый `DELETE`):
- `POST /api/public/account/editor/tasks/{id}/refuse`
- `POST /api/public/account/editor/tasks/{id}/delete`
- `POST /api/public/account/editor/tasks/delete-approved`
- `POST /api/songeditor/delete-approved`

**Rationale**:
- В проекте преобладает `POST` даже для операций удаления — см. существующие `POST /api/songeditor/delete` (`SongEditorController.kt:514`), `POST /api/songeditor/revoke` (`SongEditorController.kt:533`).
- `POST` даёт возможность передать JSON-параметры в теле (для батч-эндпоинтов с фильтрами), что неудобно делать через `DELETE` с query-string.
- Конвенция проекта важнее REST-чистоты — для пользователей фронта это всё равно выглядит как «удалить», HTTP-метод невидим.

**Alternatives considered**:
- **`DELETE` для одиночных, `POST` для батча** — отвергнут: непоследовательно.
- **`DELETE` для всех** — отвергнут: ломает конвенцию проекта, требует переписывать существующие `delete`/`revoke` (вне скоупа этой фичи).

### 2. Сортировка «активные выше одобренных» — на клиенте (Vue computed)

**Decision**: сортировка реализуется в `EditorTasksView.vue` через `computed sortedTasks()` поверх существующего `tasks`. Никаких изменений в `SongAssignment.loadByAssignee` (остаётся `.sorted()` по `id`).

**Rationale**:
- Минимизирует изменения на бэкенде (только 4 новых эндпоинта; никаких правок существующих методов модели).
- Шаблон `STATUS_ORDER` уже есть в `webvue3/src/components/SongEditor/SongEditorTable.vue:117-123` — переиспользуем тот же подход.
- Внутри каждой группы порядок остаётся по `id DESC` (как в `.sorted()`).
- Альтернатива (добавить `ORDER BY CASE WHEN admin_status='approved' THEN 1 ELSE 0 END, id DESC` в `SongAssignment.loadByAssignee`) — рассматривалась, но требует перевыпуска jar + рестарта karaoke-web на проде ради изменения, которое можно сделать одним computed на фронте (а jar-обновление триггерит docker-pull, ~10-30 сек даунтайма).

**Alternatives considered**:
- **Сортировка на бэкенде через `ORDER BY`** — отвергнут: overkill ради презентационного изменения (см. выше).
- **Клиентская сортировка через `lodash.orderBy`** — отвергнут: новая зависимость ради 5 строк нативного JS.

### 3. Реализация батч-удаления — один SQL через `KaraokeDbTable.deleteIn(...)`

**Decision**: новый helper-метод `KaraokeDbTable.deleteIn(tableName, ids, database)` (рядом с существующим `KaraokeDbTable.delete(tableName, id, database)`) делает один SQL `DELETE FROM ... WHERE id = ANY(?)` для PostgreSQL. Для батч-эндпоинтов используется именно он.

**Rationale**:
- Один SQL round-trip вместо N → SC-004 (≤3 сек при N=10) и SC-005 (≤5 сек при N=100) легко выполняются.
- Паттерн «`id IN (...)` / `id = ANY(?)`» уже используется в `SongAssignment.loadBySongIds` (`SongAssignment.kt:166-172`) — консистентно.
- `pgjdbc` поддерживает `connection.createArrayOf("BIGINT", ids.toTypedArray())` — стандартный паттерн.
- На уровне БД нет FK `tbl_song_assignment_drafts.assignment_id → tbl_song_assignments.id` (см. комментарий в `SongEditorController.kt:528-533`) — удаление задания НЕ ломает FK-цепочку.

**Alternatives considered**:
- **Итерация `SongAssignment.delete(id, db)` в цикле** — допустимо для N≤100 (запас по SC), но лишние round-trip'ы при N=100 (до 100 запросов вместо 1) — отвергнут.
- **`PreparedStatement.addBatch() / executeBatch()`** — сложнее (нужно управлять размером батча, обрабатывать `BatchUpdateException`); overkill.

### 4. Удаление черновика при «Отказаться» — ДО удаления задания

**Decision**: для одиночного `refuse` (публичный) и одиночного `revoke` (админский, без изменений) — `SongAssignmentDraft.deleteByAssignment(id, db)` вызывается ДО `SongAssignment.delete(id, db)`. Это та же логика, что в существующем `SongEditorController.revoke()` (`SongEditorController.kt:533-548`).

**Rationale**:
- Защита от «висящих» orphan-записей в `tbl_song_assignment_drafts` (если FK когда-нибудь появится — порядок уже правильный; если FK нет — порядок безвреден).
- Существующий `revoke()` уже делает именно так — повторяем паттерн, не изобретаем новый.

**Alternatives considered**:
- **Удалять черновик ПОСЛЕ задания** — отвергнут: ровно тот же эффект, но комментарий в `revoke()` явно говорит «ДО удаления задания — на случай, если БД ловит FK наоборот».
- **Не удалять черновик** — отвергнут: orphan-запись сбивает с толку при аудите/отладке (см. комментарий в `SongEditorController.revoke()`).

### 5. Черновики при «Удалить» / «Удалить все одобренные» — НЕ трогаем

**Decision**: одиночное «Удалить» для одобренной карточки и массовое «Удалить все одобренные» НЕ удаляют связанные записи в `tbl_song_assignment_drafts`. Удаляется только `tbl_song_assignments`.

**Rationale**:
- После апрува черновик уже «сожжён» — разметка применена к `tbl_songs` через `setSourceMarkers()` (см. `SongEditorController.approve()`, `SongEditorController.kt:315-487`); сам `tbl_song_assignment_drafts` остаётся как архив (не удаляется при апруве).
- Удаление архива ради «чистоты БД» — не требуется в спеке (FR-012 явно говорит «НЕ трогается»).
- Упрощает логику батч-эндпоинта: один `DELETE FROM tbl_song_assignments WHERE ...` без JOIN/cascade.

**Alternatives considered**:
- **Удалять черновик заодно с заданием** — отвергнут: добавляет второй SQL, без функционального выигрыша (архив не мешает).
- **`ON DELETE CASCADE` на уровне БД** — отвергнут: требует миграции (вне скоупа), плюс нарушает Principle II «минимальные изменения схемы» и неконсистентно с существующим кодом `revoke()` (который явно делает ручной `deleteByAssignment`).

### 6. `disabled` vs. `hidden` для пустой выборки

**Decision**: кнопка «Удалить все одобренные» отображается **всегда**, но получает атрибут `disabled` (`setDisabled(true)`), если у редактора/в выборке админа нет ни одного одобренного задания. С тултипом «Нет одобренных заданий для удаления».

**Rationale**:
- UX-прозрачно: пользователь видит, что такая кнопка существует, и понимает причину её неактивности.
- Меньше «прыжков» в DOM при изменении состояния (hidden/show — лишний re-render).
- Паттерн уже используется в `SongEditorTable.vue:339-343` (`.set-btn:disabled`).

**Alternatives considered**:
- **`v-if="approvedCount > 0"` (hidden)** — отвергнут: UX чуть хуже (пользователь не знает, что кнопка существует, пока не появится первое одобренное).

### 7. Что считать «одобренным» в админской выборке — композитный статус через `SongAssignmentStatus.resolve`

**Decision**: для админского батч-эндпоинта «одобренное» определяется через `SongAssignmentStatus.resolve(adminStatus, draft?.userStatus, reviewedAt, submittedAt) == APPROVED` — та же логика, что в существующем `digest()` (`SongEditorController.kt:215-262`) и в `SongAssignment.composeStatusesForSongIds` (`SongAssignment.kt:179-192`). Никаких новых определений.

**Rationale**:
- Единая точка вычисления композитного статуса (уже в `SongAssignmentStatus.resolve`, см. Q&A про `SongAssignmentStatus` в `AGENTS.md`).
- Фильтр по `filterStatus='approved'` в UI уже работает по этой же логике — батч-эндпоинт даст согласованный результат.

**Alternatives considered**:
- **`WHERE admin_status = 'approved'`** — отвергнут: НЕ эквивалентно композитному статусу (есть `in_progress + admin_status=approved` после `recall()` — теоретически; плюс граничные случаи с reviewedAt/submittedAt).

### 8. Таймаут / батчинг при больших N

**Decision**: при N≤500 (типичный сценарий — N≤100 у одного редактора, N≤500 у админа в фильтре) — единичный SQL. При N>1000 — на этапе реализации решить, нужен ли батчинг с прогресс-баром; в спеке не блокирует (FR не упоминает; см. Assumptions спеки).

**Rationale**:
- `DELETE FROM tbl_song_assignments WHERE id = ANY(?)` для 500 строк — единицы мс на PostgreSQL.
- UI обновляется одним `reload()` после успеха — никакого прогресса не требуется при N≤500.
- При экстремальных N (например, N=10000 — маловероятно, но возможно если у одного редактора 10000 одобренных) — UX без прогресса плохой; решается отдельно.

**Alternatives considered**:
- **Батчинг с прогрессом сразу** — отвергнут: overkill для типичных объёмов, добавляет сложность (eventsource / polling), которая не нужна сейчас.

### 9. Логирование — минимум `println` на сервере

**Decision**: каждый новый эндпоинт логирует:
```kotlin
println("[editor-tasks/<action>] user=${user.id} id=$id deleted=$ok")
println("[editor-tasks/delete-approved] user=${user.id} target=$target deleted=$N")
```

**Rationale**:
- FR-034 спеки требует «минимум — ID удалённого задания, кто запросил, размер батча».
- Существующий код использует `println` повсеместно (`SongEditorController.kt:386-389`, `SongEditorController.kt:415-419`) — не вводим logger как новую зависимость.
- Полноценный `Logger` через SLF4J/Logback — на этапе реализации как улучшение, в спеке не блокирует.

**Alternatives considered**:
- **`Logger` через SLF4J** — отвергнут как обязательный: требует `@Autowired` + `import org.slf4j.LoggerFactory`; можно сделать, но не обязательно для этой фичи.
- **`System.out.println`** — эквивалентно `println` в Kotlin.

### 10. Защита от двойного клика / гонок — `disabled` после клика + идемпотентность на бэкенде

**Decision**:
- UI: после клика «Удалить все одобренные» (или одиночных кнопок) — кнопка получает `disabled` до прихода ответа сервера (паттерн `isBusy` уже используется в `SongEditorTable.vue:135-137`).
- Бэкенд: идемпотентность — повторный `DELETE WHERE id = X` для уже удалённой записи возвращает 0 затронутых строк (не ошибка). Для батч-эндпоинта — `DELETE WHERE id IN (...)` для частично удалённых ранее возвращает количество оставшихся — это согласуется с SC-006 «повторный клик — не ошибка, UI показывает «Удалено 0»».

**Rationale**:
- FR-008 спеки: после успешного «Отказаться» карточка исчезает без перезагрузки; двойной клик уже невозможен.
- SC-006: идемпотентность обязательна.
- Не нужны транзакции / блокировки — операция «уже удалено» безопасна и атомарна на уровне одной строки.

**Alternatives considered**:
- **`SELECT … FOR UPDATE` + проверка** — отвергнут: лишний round-trip, не нужен для идемпотентной операции.
- **`RETURNING` clause для возврата фактически удалённых id** — рассматривается как опциональное улучшение (фронт мог бы обновить только их), но усложняет код без функционального выигрыша (после успеха UI просто делает `reload()`, что эквивалентно по времени).

---

## Альтернативы, рассмотренные и отвергнутые (сводно)

| Альтернатива | Отвергнута потому что |
|---|---|
| `DELETE` HTTP-метод для эндпоинтов | Ломает конвенцию проекта (`POST /api/songeditor/delete` уже есть) |
| Сортировка через `ORDER BY` на бэкенде | Требует перевыпуска jar + рестарта karaoke-web ради изменения, которое делается одним Vue computed |
| Батч через `executeBatch()` | Overkill — `id = ANY(?)` решает задачу в один SQL |
| Удалять черновик при «Удалить» для одобренных | Архив не мешает; FR-012 явно говорит «НЕ трогается» |
| `v-if` (hidden) для пустой выборки | UX чуть хуже — пользователь не знает, что кнопка существует |
| `WHERE admin_status = 'approved'` для админа | Не эквивалентно композитному статусу |
| Батчинг с прогрессом сразу | Overkill для типичных объёмов (N≤500) |
| `Logger` через SLF4J | Не обязательно для этой фичи; `println` консистентен с существующим кодом |
| `SELECT FOR UPDATE` для защиты от гонок | Идемпотентность `DELETE` достаточна |

---

## Открытые вопросы

Нет — все NEEDS CLARIFICATION разрешены на этапе спеки (Assumptions).

---

## Что НЕ входит в эту фичу (явные «не делать»)

- Никаких изменений в `tbl_song_assignments` / `tbl_song_assignment_drafts` / их триггерах.
- Никаких изменений в SyncRegistry, sync-цели `songassignments`, `KaraokeProperties`.
- Никаких изменений в существующих эндпоинтах (`delete`, `revoke` в admin; `save`, `submit`, `recall` в public).
- Никаких изменений в `tbl_songs` / новостях / премиум-логике / разметке.
- Никаких изменений в MLT-генераторе / рендере / Telegram-пайплайне.
- Никаких новых Gradle/NPM зависимостей.
- Никаких новых Docker-образов / миграций БД.
- Никаких изменений в `constitution.md` / `AGENTS.md` / `CONTRIBUTING.md`.
- Никаких изменений в `nginx` / `deploy/`.