# Управление заданиями онлайн-редактора (ЛК + админка)

> **Status**: active
> **Feature Key**: `editor-tasks`
> **Branch**: `154-editor-tasks-manage`
> **Spec**: [`specs/154-editor-tasks-manage/spec.md`](../../specs/154-editor-tasks-manage/spec.md)
> **Plan**: [`specs/154-editor-tasks-manage/plan.md`](../../specs/154-editor-tasks-manage/plan.md)
> **Last Updated**: 2026-08-13 (Pass 51-3 — фича 184: добавлен radio-выбор статуса песни 5/6 в `ReviewModal`; см. секцию «Дополнение: выбор статуса при апруве (spec 184)» ниже)

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

## Дополнение: Self-assign заданий (spec 182)

> **Status**: active
> **Branch**: `182-editor-self-assign-tasks`
> **Spec**: [`specs/182-editor-self-assign-tasks/spec.md`](../../specs/182-editor-self-assign-tasks/spec.md)

### Что делает
Даёт активным редакторам возможность самостоятельно брать свободные песни в работу прямо из публичного каталога «Закрома» — без участия админа. Снижает нагрузку на админа и ускоряет реакцию редакторов.

### Зачем
Без фичи каждый wave заданий требует ручного действия админа: открыть SongsTable → назначить → редактор видит в ЛК. При батче из 30-50 песен это ≈5-10 минут на одно распределение. Self-assign даёт редактору самому выбрать интересную песню.

### Как работает

**Новые/изменённые эндпоинты**:

| Endpoint | Где | Что делает |
|---|---|---|
| `POST /api/public/songeditor/assign-self?songId=N` | `PublicSongEditorController` (karaoke-web) | Атомарное создание `SongAssignment` для текущего редактора |
| `POST /api/siteusers/update` (новый `@RequestParam canSelfAssignTasks`) | `SiteUsersController` (karaoke-app) | Админская простановка флага пользователю |
| `GET /api/public/auth/me` (поле `canSelfAssignTasks`) | `PublicAuthController` | Текущий пользователь для UI-логики во Vue |
| `GET /api/public/song/{id}` (новое поле `assignment`) | `PublicApiController` | Фронт узнаёт, свободна ли конкретная песня |

**Схема работы** (Pass 51-2 — placement РАЗМЕЩЁН на странице песни, а не в Закромах):
1. Админ в `webvue3 → SiteUsers → SiteUserEdit` включает флаг `canSelfAssignTasks`. Колонка `tbl_site_users.can_self_assign_tasks`, попадает в `recordhash` (sync LOCAL↔SERVER).
2. Редактор заходит на `/song/{id}` (страница конкретной песни). Бэкенд вычисляет `isSelfAssignEditor = user.isEditor && user.canSelfAssignTasks`. Если да — делает ОДНУ `SongAssignment.loadBySongIds([id])` и возвращает поле `assignment` в `SongPublicDto`. Иначе — `null`.
3. Во Vue-фронте `SongView.vue` под секцией `km-meta-actions` (где Favorite/Playlist/Share) появляется кнопка «Взять в работу» (если `assignment:null`) или «Открыть задание» (если `assignment.assigneeId === userId`). Кнопка видна только `canSelfAssignEditor`.
4. Клик «Взять в работу» → POST `/api/public/songeditor/assign-self`:
   - **Атомарная транзакция** `SELECT … FOR UPDATE` + INSERT через `RETURNING id` (FR-006 / US3).
   - 200 + `{ok, id, idempotent:false}` на создание.
   - 200 + `{ok, id, idempotent:true}` если уже наше задание (повторный клик после таймаута — норма).
   - 409 + `song_already_taken` если чужое задание — UI тост + локальный сброс `assignment`.
   - 403 + `forbidden_not_self_assign_editor` если флаг сняли, пока редактор был онлайн.

**Снятие флага НЕ отзывает уже взятые задания**. Админу нужна явная команда — снятие флага только запрещает брать НОВЫЕ (см. контракт ` specs/182-editor-self-assign-tasks/contracts/C1`).

### Инварианты / правила (для self-assign)

- Флаг живёт ТОЛЬКО в `tbl_site_users.can_self_assign_tasks`, входит в `recordhash` (иначе sync LOCAL↔SERVER с разных машин рассинхронизирует). При добавлении колонки триггер ОБЯЗАН быть пересоздан (конституция III).
- Idempotency: идемпотентно по `(song_id, assignee_id)` — повторный клик того же редактора на ту же песню возвращает 200 OK без новой строки. UNIQUE-индекс `idx_tbl_song_assignments_uniq (song_id, assignee_id)` подстраховывает на уровне БД.
- Race protection: `SELECT FOR UPDATE` на ВСЕ строки `song_id` (а не `LIMIT 1`) — на одну песню теоретически может быть несколько `SongAssignment` от разных редакторов (если старое не отозвали); мы блокируем все.
- DTO `SongAssignmentBriefDto` (id/assigneeId/assignedAt/adminStatus) — это УЗНАВАЕМАЯ краткая форма, не полный `SongAssignmentDto`. Создание через reflection-loader для такой формы не нужно (нет CRUD-операций).
- Поле `assignment` есть ТОЛЬКО в публичных DTO (`ZakromaAlbumSongPublicDto`). В админских (`SongAssignmentDigest`, `SiteUserDto`) оно НЕ нужно — админ видит всё через стандартный `digest()`.
- Снятие флага НЕ само-отзывает задания, чтобы избежать «админ случайно снял флаг → у редактора пропали 5 недоделанных заданий». Это сделано сознательно (FR-002, clarification Q2 — ратифицировано).

## Дополнение: Выбор статуса песни при апруве (spec 184)

> **Status**: active
> **Branch**: `184-approve-status-choice`
> **Spec**: [`specs/184-approve-status-choice/spec.md`](../../specs/184-approve-status-choice/spec.md)

### Что делает
В `ReviewModal` (админская модалка ревью задания) появляется radio-group **«Финальный статус песни»** с двумя опциями: «5 — Маркеры проверены» и «6 — Готова» (default 6). При выборе 5 — `POST /api/songeditor/approve?idStatus=5`; бэкенд выставляет `tbl_songs.id_status=5` и **не запускает** рендер DEMO и sync related-таблиц (но пушит одобренную разметку на PROD как обычно).

### Зачем
Раньше, чтобы отложить релиз (правка обложки, ожидание альбома, пересмотр вокала), админу приходилось после апрува вручную понижать статус в `SongEdit` — с риском, что в промежутке успеют сработать автотриггеры (рендер DEMO, sync, новости). Ради одного параметра — двойная ручная работа + race-condition. Фича 184 убирает костыль.

### Как работает

**Изменённые/новые эндпоинты** (`SongEditorController.kt`, `karaoke-app`):

| Endpoint | Изменение | Поведение |
|---|---|---|
| `POST /api/songeditor/approve` | +`@RequestParam idStatus: Int?` (5/6/null) | null → 6 (backward-compat). Невалидное → 400 `invalid_idstatus`. Downgrade-ignore — silently оставляет выше. |
| `POST /api/songeditor/byId` | +поле `idStatus` (Long) в ответе | Текущий `id_status` ПЕСНИ (не задания). Источник для UI-гейта US2.1. |

**UI-гейт (US2.1, исправлено в Pass 51-3.1)**: radio ВСЕГДА виден, когда `songIdStatus !== null` (бэкенд вернул поле `idStatus` в `/byId`). Pass 51-3 первой итерации скрывал radio для `idStatus >= 5` «чтобы админ случайно не downgrade'нул» — это убило фичу: при апруве задания с уже-готовой песнёй (после предыдущего одобрения в 6 или при workflow через авто-пайплайн) radio пропадал. Решение: показывать radio ВСЕГДА, безопасность downgrade — на бэкенде (`idStatus downgrade IGNORED`, см. Edge Cases). В `.se-meta` (шапка модалки) ВСЕГДА висит информационный бейдж «idStatus песни: N (...)» с текущим значением, независимо от radio.

**Гейт рендера (backend, research D-2)**: `triggerRenderMp4DemoIfNeeded` и `thread { sync related }` обёрнуты в `if (song.idStatus >= 6L) { ... }` (по ФАКТИЧЕСКОМУ статусу, не по запрошенному). Push самой песни (`updateRemoteSongFromLocalDatabase`) — всегда.

**Логирование (US3)**: префикс `[approve/feature-184]` — строки `idStatus=5 reason=manual_choice`, `idStatus=6 reason=default`, `idStatus downgrade IGNORED ...`, `render-demo SKIPPED ...`, `sync-related SKIPPED ...`, `news SKIPPED ...`, `INVALID idStatus=...`.

**Идемпотентность**: повторный апрув возвращает `already_approved` (short-circuit ДО валидации `idStatus`, см. specs/094), никаких побочных эффектов и строк `feature-184`.

**`ReviewModal` общий** для 3 точек входа: «Задания редактора» (`SongEditorTable`), таблица песен (`SongsTable`), карточка песни (`SongEdit`) — во всех трёх radio появляется без правок вызывающих компонентов.

### Инварианты / правила (для feature 184)

- **INV-1** (data-model): `id_status` при апруве никогда не понижается (`if (song.idStatus < targetIdStatus)`). Downgrade-ignore тихо — без 400.
- **INV-2**: запрос с `idStatus=5` к УЖЕ одобренному заданию → `already_approved` (без `idStatus` в ответе, без новых строк лога `feature-184`).
- **INV-3**: гейт `idStatus >= 6L` — по ФАКТИЧЕСКОМУ значению после применения, не по запрошенному (иначе регрессия в `requested=5, current=6` → downgrade-ignore).
- **INV-4**: push самой песни (`updateRemoteSongFromLocalDatabase`) ВСЕГДА, независимо от статуса. Иначе одобренная разметка не попадёт на PROD.
- **INV-5**: `notify/sync` related-таблиц, рендер DEMO, новости — все три гейтятся одинаково. Нельзя гейтить только одно.
- **INV-6**: Vuex action `approveAssignment` принимает ОБА формата: `Number` (старые вызовы, backward-compat) и `{id, idStatus}` (новые). Параметр не отправляется, если не передан (`if (idStatus !== undefined) params.idStatus = idStatus`).
- **INV-7**: `watch: a()` в `ReviewModal` сбрасывает `selectedIdStatus = 6` при смене `a.id` — иначе выбор «залипает» между разными заданиями (ловушка D-7).

### Известные ловушки

- **Гейт по запрошенному значению** (`if (requestedIdStatus == 6) { ... }`) — НЕПРАВИЛЬНО. Используем `if (song.idStatus >= 6L)` после применения, иначе `requested=5, current=6` (downgrade-ignore) сломает текущий авто-конвейер для финальной песни.
- **Гейтить push песни** (`updateRemoteSongFromLocalDatabase`) — НЕЛЬЗЯ. Без push одобренная разметка редактора «зависнет» только в LOCAL, и admin увидит песню одобренной только в админке, а в проде — старую.
- **Без `watch: a()`** в `ReviewModal` — выбор статуса сохраняется при переиспользовании модалки. Например, в `SongsTable` модалка может оставаться смонтированной при переходе от одной строки задания к другой.
- **Поле `status` в `/byId` — это НЕ `idStatus`**. `status` — статус ЗАДАНИЯ (`SongAssignmentStatus`, e.g. `submitted`/`approved`); `idStatus` (новое) — статус ПЕСНИ (`tbl_songs.id_status`, 0..6). Не путать при чтении фронта.
- **Нет `bootstrap-vue-next` в `ReviewModal.vue`** — модалка со своим `<style scoped>` и `se-*` дизайн-системой. Использование `BFormRadio` ломает стиль и конвенцию модалки. Нативные `<input type="radio">` + свои CSS-классы.
- **Без `if (idStatus !== undefined) params.idStatus = idStatus`** в `store.js` — параметр отправится как пустая строка или `null`, бэкенд может спарсить как 0 (или пробросить исключение).
- **Без `defaults` в `lastUpdated` поле** per-feature doc — `git blame` не подсветит автора правки, история теряется.


### Известные ловушки

- **UNIQUE не строгий по `song_id`** — индекс только по `(song_id, assignee_id)`. Без блокировки `SELECT FOR UPDATE` два редактора могут создать две строки на одну песню. По спеке US3 — это race, должна быть 409.
- **`canSelfAssignTasks` живёт в `SiteUserDto`** — НЕ в `PublicAccountDto` или подобном. Если кто-то добавит отдельный Profile-DTO для фронта — он НЕ должен пробрасывать это поле (принцип наименьших привилегий для анонимов/обычных посетителей).
- **`SET_AUTOCOMMIT FALSE` только в `assignSelf`** — глобальные подключения в `KaraokeConnection` имеют thread-local кеш. После commit/rollback ОБЯЗАТЕЛЬНО вернуть `autoCommit = previousAutoCommit` в `finally`. Без `finally` следующий запрос на этом потоке может неявно открыть транзакцию (см. T016 / phase 5 / US3 — lock навсегда).
- **`RETURNING id`** в INSERT — нам нужен id СРАЗУ для ответа. Без `RETURNING` пришлось бы делать отдельный `SELECT currval()` или `lastval()` — лишний round-trip.
- **Контракт `/api/public/zakroma` — стабильный JSON**. Добавление поля `assignment: null` для НЕ-self-assign-редакторов безопасно для всех существующих фронтов (Vue/JsPatches читают через опциональную цепочку `?.`, и не падают на `null`).

### Ссылки
- Спецификация: [`specs/182-editor-self-assign-tasks/spec.md`](../../specs/182-editor-self-assign-tasks/spec.md)
- Контракты: [`specs/182-editor-self-assign-tasks/contracts/`](../../specs/182-editor-self-assign-tasks/contracts/)
- Quickstart (10 сценариев ручной валидации): [`specs/182-editor-self-assign-tasks/quickstart.md`](../../specs/182-editor-self-assign-tasks/quickstart.md)
- Существующая таблица редакторов (UI-паттерн): [`songs-table.md`](./songs-table.md)
- Dual-DB Sync (sync-цель `songassignments`): [`dual-db-sync.md`](./dual-db-sync.md)

## Дополнение: кнопка «Типограф» в тулбаре редактора (spec 155)

В самом онлайн-редакторе разметки (не в списке заданий, а в UI работы над конкретной песней —
`webvue3/src/components/SongEditor/SongKaraokeEditorView.vue` в админке,
`karaoke-public/src/views/EditorWorkView.vue` на публичном сайте) добавлена кнопка «Типограф»
рядом с «Очистить маркеры».

- **Что делает**: применяет к тексту ТЕКУЩЕГО голоса те же правила типографской замены символов,
  что и одноимённая кнопка в классическом редакторе (`SubsEdit.vue` → `doReplaceText()`) — тот же
  backend-эндпоинт `POST /api/replacesymbolsinsong`. Ответ — сырая строка, НЕ JSON.
- **Backend**: в `karaoke-app` endpoint не менялся (`ApiController.kt:5052` + `MainController.kt:970`,
  вызывает `Utils.replaceSymbolsInSong()`, `permitAll` в `SecurityConfig.kt`). Для публичного
  сайта добавлен **тонкий дубль** в `karaoke-web`:
  - `PublicTypographController.kt` (PR #205) — `@PostMapping("/api/replacesymbolsinsong")`.
  - `TypographUtils.kt` (PR #206) — **локальная копия** `replaceSymbolsInSong` + 6
    String-extensions + 2 константы (`RUSSIAN_LETTERS`, `CHORDS_LETTERS`). Скопирована
    из `karaoke-app/Utils.kt:1460` и `Extentions.kt` потому, что прямой вызов
    `com.svoemesto.karaokeapp.replaceSymbolsInSong` из `karaoke-web` тянет за собой
    class init `Constants.kt` (карта `ProducerType → Mko*::class.java` → загрузка всех
    MLT-классов, часть которых при init лезет в `APP_WORK_ON_SERVER`/`WORKING_DATABASE`,
    настроенные только в `karaoke-app`) — `NoClassDefFoundError` в рантайме. Ё-словарь
    читается прямым SQL к `tbl_dictionaries` через локальный `WORKING_DATABASE`.
  - В nginx `karaoke-public/nginx_karaoke-public.conf` добавлен
    `location /api/replacesymbolsinsong` (PR #206) — иначе nginx отдаёт 405
    `Method Not Allowed` на POST (статика через `try_files` не принимает не-GET).
  - Без обоих PR: nginx 405 → после PR #205: Spring 500 (class init упал) → после
    PR #206: Spring 200 + корректный текст.
- **Без диалога подтверждения** — в отличие от соседней «Очистить маркеры» (действие обратимо
  через обычный undo текстового поля, правки правил не удаляют структуру).
- **Пересинхронизация маркеров**: после замены вызывается уже существующий `onTextInput()` (тот
  же обработчик, что и при ручном вводе) — пересчитывает `syllables`/`markers` из нового текста,
  а не пытается точечно чинить старые маркеры.
- **Ошибки сети**: `sourceText` не меняется; сообщение показывается через отдельное поле
  `typographError` (инлайн рядом с кнопкой), а не через общий индикатор автосохранения
  (`saveState`/«Ошибка сохранения») — переиспользование `saveState` вводило бы в заблуждение,
  так как ничего не сохранялось.
- Реализовано отдельно в каждом из двух фронтендов (Principle V Конституции — admin/public не
  делят код), но идентично по поведению.

Детали: [`specs/155-editor-typograph-button/`](../../specs/155-editor-typograph-button/)
(`spec.md`, `plan.md`, `research.md`, `contracts/replacesymbolsinsong.md`).

## Дополнение: фикс регрессий спецтегов в редакторах разметки (spec 163)

После внедрения спецтегов (`~Куплет~`, `~Припев~` и т.п., `specs/010-lyrics-spec-tags`) обе
НЕЗАВИСИМЫЕ копии `syncMarkersFromSpecTags` — в облегчённом редакторе
(`webvue3/src/composables/useKaraokeEditor.js`) и в полноценном (`SubsEdit.vue`) — оказались
подвержены одному структурному дефекту неидемпотентности, с разными наблюдаемыми последствиями.

- **Облегчённый редактор перестал сохранять правки (P1, потеря данных)**. Причина — НЕ backend и
  не потеря запроса: `syncMarkersFromSpecTags` вставляла тег-маркер повторно на КАЖДЫЙ вызов
  `onTextInput()` (то есть на каждое нажатие клавиши), если вычисленное время нового маркера точно
  совпадало со временем соседнего слогового маркера (`gap === 0` либо `gap === 1.0` в формуле
  `gap >= 1.0 ? nextStartTime - 1.0 : prevEndTime + gap / 2`) — `sortMarkers()` при равенстве
  времени сортирует по `markertype` (`'setting' < 'syllables'`) и выталкивает уже вставленный
  маркер за пределы индексного окна дедупликации, из-за чего он "не находился" на следующем вызове
  и вставлялся заново. Массив маркеров раздувался без ограничений при обычном наборе текста.
  **Фикс**: дедупликация переведена с индексного окна (`markers.slice(windowStart, insertPos)`) на
  временной диапазон (`m.time >= prevEndTime && m.time <= nextStartTime`) между соседними
  слоговыми маркерами — устойчив к тому, куда сортировка фактически поместила ранее вставленный
  маркер, поскольку `prevEndTime`/`nextStartTime` определяются временами СЛОГОВЫХ маркеров,
  которые сами никогда не двигаются (аддитивный инвариант, FR-007). Отдельно закрыт
  пред-существующий (не специфичный для спецтегов) баг: `SongKaraokeEditorModal.saveNow()` не
  проверял поле `ok` в JSON-ответе `/api/songeditor/edit/save` — HTTP 200 с `{"ok": false}`
  (например `song_not_found`) молча показывался как «Сохранено ✓». Плюс новое поведение: если
  последнее сохранение завершилось ошибкой, закрытие редактора («×» / «Отмена») блокируется
  явным предупреждением (`CustomConfirm`) — без автоматического повтора попытки сохранения.
- **SubsEdit стал тормозить и показывать вспышку «слипания в нуле» на вейвформе (P2)**. Тот же
  структурный дефект неидемпотентности физически присутствует и в копии `syncMarkersFromSpecTags`
  внутри `SubsEdit.vue`, но там он смягчён (не устранён) более ранним фиксом `#018` (гарантирует
  минимум 0.5с от `prevEndTime`) — поэтому на реальных, полученных через forced-alignment
  временных метках регрессия проявлялась не как раздувание массива, а как учащение уже известного
  класса бага «маркеры слипаются в нуле» (specs/016-019): вставки `syncMarkersFromSpecTags`
  сдвигают ординальные индексы, по которым `updateMarkersBySyllables()` сопоставляет слоговые
  маркеры с текстом, поэтому больше регионов проходят через `setContent`/`setOptions`
  (WaveSurfer-квирк, из-за которого регион визуально сбрасывается в позицию 0 до следующего
  `redrawMarkers()`). Отдельно обнаружено и устранено НЕ связанное со спецтегами удвоение работы:
  `sourceSyllables` имел свой отдельный watcher с тем же набором вызовов
  (`updateMarkersBySyllables()`+`redrawMarkers()`), который срабатывал ВТОРОЙ раз на каждое
  нажатие клавиши, потому что `sourceText`-watcher сам присваивает `this.sourceSyllables` (новый
  массив — Vue всегда считает его изменившимся). Оба watcher'а объединены в один проход. Заодно
  портирован тот же временной-диапазон фикс идемпотентности, что и в облегчённом редакторе (та же
  проблема, другая формула #018 для верхней границы диапазона).
- **Инвариант**: обе копии `syncMarkersFromSpecTags` теперь идемпотентны — повторный вызов на
  неизменных `markers`/`sourceText` не меняет длину массива. Формализовано отдельным
  контрактом-дополнением, чтобы разрыв между копиями (одна получала фиксы, другая — нет) не
  повторился в третий раз.

Детали: [`specs/163-fix-song-editor-regressions/`](../../specs/163-fix-song-editor-regressions/)
(`spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/sync-idempotency-invariant.md`, `quickstart.md`).

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
