# Feature Specification: Массовые действия с процессами

**Feature Branch**: `317-process-bulk-actions`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Работа над задачей #67 в трекере OpenProject"

**Source**: OpenProject WP #67 — `tracker.sh get-issue 67` (см. описание задачи).

## Context

В admin-SPA (`webvue3`) для сущности «Песни» (`SongsTable.vue`) уже реализован набор
массовых действий над выборкой из текущего фильтра: создать караоке для всех,
создать DEMUCS2/5 для всех, найти тексты для всех, добавить sync для всех — все
через `<custom-confirm>` диалог → `dispatch` в Vuex-store → backend-endpoint.

Для сущности «Процессы» (`ProcessesTable.vue`, реализован в spec #315 / итерация 5)
аналогичных массовых действий НЕТ: администратор не может одним жестом изменить
поле (например, приоритет или статус) сразу у группы процессов или удалить их —
только по одному через `ProcessEditModal` / `ProcessDeleteModal`. Это неудобно
при работе с большими пачками legacy-хвостов (13 169+ процессов из миграции
`48_chain_id_backfill` — прецедент iter #4 спеки #315), ручных чистках и
массовых ретраях упавших задач.

Задача WP #67: добавить **первые два** массовых действия (явно помечено как
«для начала»), симметрично паттерну песен:

1. **Изменение поля** — выбрать поле (приоритет / статус / threadId), ввести
   новое значение, применить ко всем процессам в выборке.
2. **Удаление** — подтвердить, применить ко всем процессам в выборке.

Дизайн — расширяемый: новые массовые действия (например, смена `chainId`,
ретрай, восстановление из `deleted_at`) добавляются позже без переделки
каркаса.

## User Scenarios & Testing *(mandatory)*

<!--
  **rev 2 changes (per Кирилл's REQUEST CHANGES Р-1..Р-6, #181, sha f681f454…):**
  - Р-1: убраны ссылки на MIN_PRIORITY/MAX_PRIORITY (не существуют). Валидация priority = целое ≥ 0 (без верхней границы).
  - Р-2: убран маппинг admin↔enum. UI использует прямые enum-значения CREATING/WAITING/WORKING/DONE/ERROR (прецедент ProcessEdit.vue:184-185). Маппинг упразднён.
  - Р-3: добавлен вопрос владельцу про transition-валидацию (Stage 3).
  - Р-4: схема audit-trail приведена к фактической tbl_processes_audit (action/old_value::jsonb/new_value::jsonb/created_at).
  - Р-5: endpoints переименованы на /api/admin/processes/bulk-update и bulk-delete; placement в существующий KaraokeProcessAdminController + Service.
  - Р-6: bulk-delete через POST (не DELETE с телом); добавлены computed countRows и группа bulk-кнопок в ProcessesTable.
-->

### User Story 1 — Массовое изменение поля (Priority: P1)

Администратор Karaoke работает с таблицей процессов (`ProcessesView` →
`ProcessesTable.vue`), фильтр уже применён (например, все процессы со статусом
ERROR + threadId = 5). Администратор хочет одним жестом **изменить одно поле**
(приоритет, статус, threadId) сразу у всей выборки — например, перевести
упавшие процессы в WAITING для повторной обработки, или понизить приоритет
фоновых задач с 0 на -1, или переназначить threadId.

Для этого администратель нажимает в шапке таблицы новую кнопку **«Массовое
изменение поля»**, видит **существующий** `<custom-confirm>`-диалог с тремя
полями: (а) выбор поля (приоритет / статус / threadId — через `<select>` с
прямыми enum-значениями статуса (CREATING/WAITING/WORKING/DONE/ERROR), (б) новое значение (тип поля зависит от выбора
в (а): для приоритета — целое число (валидация Int; диапазон не ограничивается в v1), для статуса — `<select>` с прямыми enum-значениями
`CREATING / WAITING / WORKING / DONE / ERROR` (прецедент ProcessEdit.vue:184-185), для threadId —
целое число), (в) подтверждение количества процессов в выборке. После «Да»
backend применяет изменение ко всем процессам, отвечает количеством изменённых.
Администратор видит обновлённую таблицу (Vuex-store обновляет список).

**Why this priority**: Это первое из двух действий, явно запрошенных в WP #67.
Без него администратор вынужден вручную править каждый процесс через
`ProcessEditModal` — при 100+ упавших процессах это часы ручной работы.
Блокирует эффективную работу с legacy-хвостами и ручные ретраи.

**Independent Test**: Можно проверить изолированно: (1) открыть
`ProcessesTable`, применить фильтр, дающий 5–10 процессов (например, статус
ERROR); (2) нажать «Массовое изменение поля», выбрать поле «Статус», новое
значение «WAITING», нажать «Да»; (3) backend отвечает `{"updated": 7}` (или
сколько в выборке); (4) таблица обновляется — у всех 7 процессов статус
отображается как WAITING; (5) повторить с полем «Приоритет» (новое значение
`-1`) и «threadId» (новое значение `42`) — каждое работает.

**Acceptance Scenarios**:

1. **Given** фильтр применён, в выборке есть процессы (≥ 1), **When**
   администратор нажимает кнопку «Массовое изменение поля» в шапке таблицы,
   **Then** открывается `<custom-confirm>`-диалог с тремя полями: (а) выбор
   поля, (б) новое значение (тип адаптивный к выбору в (а)), (в) текст
   подтверждения вида «Будет изменено процессов: N».
2. **Given** в диалоге выбрано поле «Статус» и значение «WAITING», **When**
   администратор нажимает «Да», **Then** backend отвечает `{"updated": N}`
   (N = число процессов в выборке), таблица обновляется — у всех N процессов
   `processStatus` стал WAITING, сообщение в диалоге показывает «Изменено процессов: N».
3. **Given** в диалоге выбрано поле «Приоритет» и введено значение `-1`,
   **When** администратор нажимает «Да», **Then** backend отвечает
   `{"updated": N}`, у всех N процессов `processPriority` стал `-1`.
5. **Given** в диалоге выбрано поле «threadId» и введено значение `42`,
   **When** администратор нажимает «Да», **Then** backend отвечает
   `{"updated": N}`, у всех N процессов `processThreadId` стал `42`.
6. **Given** в диалоге введено невалидное значение для выбранного поля
   (например, `abc` для приоритета), **When** администратор нажимает «Да»,
   **Then** диалог отвергает ввод с понятным сообщением, ничего не
   применяется.
7. **Given** фильтр дал 0 процессов (пустая выборка), **When** администратор
   нажимает кнопку «Массовое изменение поля», **Then** кнопка заблокирована
   (`disabled` по аналогии с `createKaraokeForAll` в SongsTable.vue — там
   `:disabled="countRows === 0"`).

---

### User Story 2 — Массовое удаление (Priority: P1)

Администратор хочет удалить все процессы в выборке — например, ошибочно
созданные хвосты legacy-данных (без полезной нагрузки) или завершённые
процессы старше 30 дней (retention-policy, см. US2 конституции / 315 spec,
FR-006). Действие должно быть **подтверждаемым**, чтобы случайный клик не
уничтожил нужные данные.

Администратор нажимает в шапке таблицы новую кнопку **«Массовое удаление»»,
видит **существующий** `<custom-confirm>`-диалог с текстом «Будет удалено
процессов: N. Действие необратимо. Продолжить?» и кнопками «Да» / «Нет».
После «Да» backend выполняет soft-delete (см. Assumptions) для всех
процессов в выборке, отвечает количеством удалённых. Таблица обновляется —
удалённые процессы исчезают из текущей выборки (фильтр не включает
`processDeletedAt`).

**Why this priority**: Это второе из двух действий, явно запрошенных в WP
#67. Без него администратор вынужден удалять каждый процесс через
`ProcessDeleteModal` — при большом количестве legacy-хвостов это непрактично.
Симметрично с US1: оба действия запрошены явно, оба P1.

**Independent Test**: Можно проверить изолированно: (1) применить фильтр,
дающий 5–10 процессов, у которых `processDeletedAt IS NULL`; (2) нажать
«Массовое удаление», подтвердить; (3) backend отвечает `{"deleted": N}`;
(4) таблица обновляется — все N процессов исчезли из выборки; (5) в БД у этих
N процессов `processDeletedAt` стал NOT NULL (soft-delete, см. Assumptions).

**Acceptance Scenarios**:

1. **Given** фильтр применён, в выборке ≥ 1 процесс с `processDeletedAt IS
   NULL`, **When** администратор нажимает кнопку «Массовое удаление»,
   **Then** открывается `<custom-confirm>`-диалог с текстом «Будет удалено
   процессов: N. Действие необратимо. Продолжить?».
2. **Given** администратор подтвердил диалог кнопкой «Да», **When** запрос
   отправляется на backend, **Then** backend выполняет soft-delete (UPDATE
   `tbl_processes SET process_deleted_at = NOW() WHERE id IN (...)`) для всех
   процессов в выборке, отвечает `{"deleted": N}`.
3. **Given** удаление выполнено, **When** таблица обновляется, **Then**
   все N удалённых процессов исчезают из текущей выборки (фильтр не
   включает `processDeletedAt IS NOT NULL`).
4. **Given** фильтр дал 0 процессов с `processDeletedAt IS NULL` (всё
   либо удалено, либо фильтр пуст), **When** администратор открывает
   страницу, **Then** кнопка «Массовое удаление» заблокирована
   (`disabled`).
5. **Given** администратор нажал «Нет» в диалоге подтверждения, **When**
   диалог закрывается, **Then** ничего не применяется, таблица остаётся
   без изменений.

---

### Edge Cases

- **Что если в выборке тысячи процессов (например, 5000)?** Backend должен
  выполнять UPDATE пакетно (`WHERE id IN (...)`), не по одной строке в
  цикле (Constitution II). Прецедент: паттерн `WHERE id IN (..)` уже
  используется в Karaoke (см. spec #315 FR по targeted UPDATE). Для
  очень больших выборок (>10000) возможно разбиение на батчи — out of
  scope для v1, фиксируется в US1 как «best-effort, batch при
  необходимости».
- **Что если часть процессов в выборке уже `processDeletedAt IS NOT NULL`?**
  Backend должен игнорировать их (UPDATE применяется только к
  `processDeletedAt IS NULL`) — иначе idempotency ломается.
- **Что если выборка содержит head-процессы с дочерними (processChainId)?**
  Массовое удаление должно проверять наличие детей. Стратегия v1: **НЕ
  удалять** процессы, у которых есть дочерние (FK-логика на уровне
  приложения, см. FR-007 спеки #315). Возвращать
  `{"deleted": N, "skipped": M}` где M — число процессов с детьми.
  Документировать в UI: «Пропущено процессов с дочерними: M».
- **Что если backend-эндпоинт упал по 5xx посреди UPDATE?** Атомарность:
  если возможно — обернуть в одну транзакцию (`Connection.setAutoCommit(false)`
  + commit/rollback). На уровне MySQL InnoDB — единый UPDATE
  `WHERE id IN (..)` атомарен по умолчанию. Если пакетный UPDATE (батчи) —
  логировать частичный успех, возвращать `{"updated": X, "failed": Y, ...}`.
- **Что если администратор случайно дважды нажал «Да» подряд?** Защита:
  после нажатия «Да» диалог блокируется (`disabled` на кнопке), запрос
  идёт; второй клик игнорируется (state guard в компоненте).
- **Что если поле «Статус» хочется перевести в значение, недопустимое по
  workflow (например, DONE → ERROR)?** v1 — допускаем любой переход
  (admin-привилегия). Если в будущем понадобятся workflow-правила —
  расширение (out of scope для v1).
- **Что если пользователь ввёл невалидный приоритет (не-целое)?**
  Валидация на стороне клиента: целое число (диапазон не ограничивается в v1 —
  как в существующем ProcessEdit.vue, `type="number"` без min/max; AdminService
  `coerceValue`-паттерн). При выходе — отказ с понятным сообщением.
- **Что если admin попытался bulk-update статуса с переходом, нарушающим
  workflow (например, DONE → ERROR)?** Backend применяет те же transition-правила,
  что и single-edit (`KaraokeProcessAdminService:259` `validateTransition`),
  бросает `InvalidStatusTransitionException` для каждого процесса, у которого
  переход невалиден. Процесс попадает в `skipped` с причиной в ответе
  (`{"updated": N, "skipped": M, "skippedReasons": [{id: 5, reason: "DONE→ERROR"}]}`).
  Валидные переходы применяются. UI показывает сводку: «Изменено: N. Пропущено: M»
  с расшифровкой причин по клику.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В шапке `ProcessesTable.vue` система MUST добавить **две новые
  кнопки** в группу bulk-действий (по прецеденту `SongsTable.vue` —
  `createKaraokeForAll`, `createDemucs2ForAll`):
  (а) «Массовое изменение поля» с иконкой `icon_field.svg` (или существующей
  иконкой редактирования), `title="Изменить поле у всех процессов из выборки"`,
  `@click="bulkChangeField"`.
  (б) «Массовое удаление» с иконкой `icon_delete.svg` (или существующей
  иконкой удаления), `title="Удалить все процессы из выборки"`,
  `@click="bulkDelete"`.
  Обе кнопки MUST быть заблокированы (`:disabled="countRows === 0"` —
  прецедент `SongsTable.vue:410/:421`) когда фильтр дал 0 строк.
  **TODO для implementer'а**: в текущем `ProcessesTable.vue` НЕТ computed `countRows`
  (grep = 0) — нужно добавить по прецеденту `SongsTable.vue`. Кнопки образуют первую
  bulk-группу шапки (по прецеденту SongsTable).
- **FR-002**: Кнопка «Массовое изменение поля» MUST открывать
  `<custom-confirm>`-диалог (по прецеденту `createKaraokeForAll`,
  `SongsTable.vue:1326-1374` — `customConfirmParams.fields`) с тремя полями:
  (а) `fldName="field"`, `fldLabel="Поле:"`, тип — `<select>` с тремя
  опциями: `priority` → «Приоритет», `status` → «Статус»,
  `threadId` → «threadId». (б) `fldName="value"`, `fldLabel="Новое значение:"`,
  тип — адаптивный к (а):
  - `priority` → `text` (input type="number") с валидацией целого **без диапазона** (см. Assumptions «Приоритет без диапазона» — симметрия с ProcessEdit.vue, `type="number"` без min/max);
  - `status` → `<select>` с **прямыми enum-значениями** `CREATING, WAITING, WORKING, DONE, ERROR` (прецедент ProcessEdit.vue:184-185, симметрия «как рядом»; admin-friendly лейблы НЕ используются, см. Assumptions «Прямые enum-значения»);
  - `threadId` → `text` (input type="number") с валидацией целого ≥ 0.
  (в) `fldName="confirmText"` (read-only) с текстом «Будет изменено процессов: {N}».
- **FR-003**: После «Да» в диалоге FR-002 система MUST вызвать новый
  Vuex-action `processesBulkUpdate` (в модуле `processes` store), который
  отправляет HTTP POST на backend-endpoint `/api/admin/processes/bulk-update` (новый,
  в существующем KaraokeProcessAdminController.kt, см. FR-005) с телом `{ ids: [...], field: "priority"|"status"|"threadId",
  value: <new value> }`. После успешного ответа система MUST обновить
  локальный список процессов (Vuex `setItems`) с применёнными изменениями
  и закрыть диалог с показом сообщения «Изменено процессов: N» (по
  прецеденту `createKaraokeForAll`, `SongsTable.vue:1393-1406`).
- **FR-004**: Кнопка «Массовое удаление» MUST открывать
  `<custom-confirm>`-диалог **без** полей ввода, только с текстом
  подтверждения «Будет удалено процессов: {N}. Действие необратимо.
  Продолжить?» и кнопками «Да» / «Нет». После «Да» — вызов Vuex-action
  `processesBulkDelete` (новый, в модуле `processes`), HTTP POST на
  backend-endpoint `/api/admin/processes/bulk-delete` с телом `{ ids: [...] }`
  (POST, не DELETE — прецедент webvue3: все admin-действия POST, нет DELETE-with-body паттернов;
  utils.js:18 generic `xhr.open(obj.method…)`, body уйдёт, но семантика bulk-delete как
  «действие» лучше ложится на POST). После успешного ответа — обновить локальный список
  (Vuex `setItems` без удалённых id) и закрыть диалог с сообщением «Удалено процессов: N».
- **FR-005**: Backend MUST реализовать **два новых endpoint** в
  **существующем** `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`
  (прецедент 6 endpoints из spec #315, `@RequestMapping("/api/admin/processes")`,
  auth = `permitAll()` как весь контроллер — прецедент KaraokeProcessAdminController.kt:22/:30/:37):
  - `POST /api/admin/processes/bulk-update` — тело `{ ids: [Long], field: String,
    value: <any> }`. Возвращает `{"updated": Int, "skipped": Int}`. `field` — одно из
    `priority`/`status`/`threadId` (whitelist); `value` — типизированный
    (priority — Int, status — enum CREATING/WAITING/WORKING/DONE/ERROR, threadId — Long ≥ 0).
    Для status — bulk-update применяет те же transition-правила, что и single-edit
    (FR-017 315, AdminService InvalidStatusTransitionException); недопустимый переход
    → процесс попадает в `skipped` с причиной в ответе.
  - `POST /api/admin/processes/bulk-delete` — тело `{ ids: [Long] }`.
    Возвращает `{"deleted": Int, "skipped": Int}`. Soft-delete (`process_deleted_at = NOW()`).
  Bulk-логика — в `KaraokeProcessAdminService.kt` (reuse INSERT :643, фильтр
  `WHERE ... process_deleted_at IS NULL` :155, guard `:249`). Оба endpoint MUST использовать
  сырой JDBC (Constitution II) и паттерн `WHERE id IN (...)` для UPDATE
  (Constitution II — O(n) батч, не цикл). Auth — как весь контроллер: `permitAll()`
  (Assumption Single-user уже это отражает).
- **FR-006**: Backend MUST проверять на уровне приложения, что
  `processDeletedAt IS NULL` для всех id перед UPDATE/DELETE (защита от
  двойного удаления и случайного применения к уже удалённым). Если
  какие-то id не прошли проверку — они попадают в `skipped`, ответ
  содержит оба счётчика.
- **FR-007**: Backend MUST при bulk-delete проверять наличие дочерних
  процессов (`SELECT COUNT(*) FROM tbl_processes WHERE process_chain_id
  = ? AND process_deleted_at IS NULL`) для каждого id в head-позиции
  (т.е. для процессов, у которых самих `processChainId IS NULL` или
  которые являются head цепочки — прецедент логики в spec #315 FR-005).
  Процессы с дочерними MUST попадать в `skipped`, логироваться отдельно
  с предупреждением.
- **FR-008**: Backend MUST валидировать входные данные по списку
  допустимых полей (whitelist: `priority`, `status`, `threadId` для
  bulk-update). Любое другое поле → 400 Bad Request с понятным
  сообщением. Значения полей — типизированная валидация (priority —
  Int без диапазона, status — enum `CREATING`/`WAITING`/`WORKING`/`DONE`/`ERROR`,
  threadId — Long ≥ 0).
- **FR-009**: Backend MUST возвращать 4xx с описанием ошибки, если
  `ids` пустой массив, длина > 10000 (превышение лимита), или
  валидация поля/value не прошла. UI MUST корректно показывать
  ошибку в диалоге.

### Key Entities *(include if feature involves data)*

- **ProcessBulkUpdateRequest**: DTO для backend-endpoint
  `POST /api/admin/processes/bulk-update`.
  Атрибуты:
  - `ids: List<Long>` — список id процессов для изменения (обязательно,
    non-empty, ≤ 10000);
  - `field: String` — одно из `"priority"`, `"status"`, `"threadId"`
    (whitelist, см. FR-008);
  - `value: Any` — новое значение (тип зависит от `field`: Int без диапазона,
    enum `CREATING`/`WAITING`/`WORKING`/`DONE`/`ERROR`, Long ≥ 0).
- **ProcessBulkUpdateResponse**: ответ backend-endpoint.
  Атрибуты:
  - `updated: Int` — количество успешно обновлённых процессов;
  - `skipped: Int` — количество пропущенных (например, уже
    `processDeletedAt IS NOT NULL`, см. FR-006).
- **ProcessBulkDeleteRequest**: DTO для backend-endpoint
  `POST /api/admin/processes/bulk-delete`.
  Атрибуты:
  - `ids: List<Long>` — список id процессов для soft-delete (обязательно,
    non-empty, ≤ 10000).
- **ProcessBulkDeleteResponse**: ответ backend-endpoint.
  Атрибуты:
  - `deleted: Int` — количество успешно soft-deleted процессов;
  - `skipped: Int` — количество пропущенных (уже удалённые, либо
    процессы с дочерними, см. FR-007).
- **Использует существующие сущности**:
  - `KaraokeProcess` (`karaoke-app/.../KaraokeProcess.kt`) — без
    изменений в схеме класса; добавляются только новые bulk-методы
    (на усмотрение Алины в design.md, паттерн — companion object
    static methods).
  - `KaraokeProcessStatuses` (enum, прецедент — spec #315 US4) — без
    изменений.
  - `tbl_processes_audit` (миграция 47, spec #315) — логирование
    bulk-операций пишется сюда.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% запусков массового изменения поля начинаются с показа
  `<custom-confirm>`-диалога с тремя полями (FR-002); 0% запусков
  минуют диалог.
- **SC-002**: 100% запусков массового удаления начинаются с показа
  `<custom-confirm>`-диалога с подтверждением (FR-004); 0% запусков
  минуют диалог.
- **SC-003**: Backend применяет операцию ко **всем** процессам в выборке
  корректно — для каждой операции возвращаемое `updated + skipped`
  равно длине входного массива `ids` (или меньше, если был лимит 4xx
  по FR-009). Проверяется на тестовой выборке из 1, 10, 100 процессов.
- **SC-004**: После успешного bulk-update в `tbl_processes_audit`
  появляется запись для каждого изменённого процесса (прецедент
  audit-trail из spec #315 миграции 47). Проверяется через
  `SELECT COUNT(*) FROM tbl_processes_audit WHERE process_id = ? AND
  action = 'bulk-update' AND created_at >= ?` — счётчик равен
  `updated` из ответа backend.
- **SC-005**: После успешного bulk-delete в выборке фильтра (которая
  по умолчанию исключает `processDeletedAt IS NOT NULL`)
  удалённые процессы исчезают. В БД `processDeletedAt` у этих
  процессов стал NOT NULL (soft-delete). Проверяется через
  `docker exec karaoke-db psql -c "SELECT id, process_deleted_at FROM
  tbl_processes WHERE id = ANY(...)"` — все id в списке имеют
  `process_deleted_at NOT NULL`.
- **SC-006**: При попытке bulk-delete процесса с дочерними — этот
  процесс попадает в `skipped`, дочерние остаются нетронутыми.
  Проверяется на тестовой цепочке из 3 процессов (head + 2 child) —
  bulk-delete head → `{"deleted": 0, "skipped": 1}`, дочерние остались
  с `processDeletedAt IS NULL`.
- **SC-007**: Диалог отвергает невалидный ввод (priority — не целое число,
  status не из enum, threadId < 0, пустой ids, ids > 10000) с
  понятным сообщением, ничего не применяется на backend (проверяется
  через отсутствие записей в `tbl_processes_audit` и неизменность
  строк в `tbl_processes`).
- **SC-008**: Производительность — bulk-update/bulk-delete для 1000
  процессов выполняется за ≤ 2 секунд (Constitution II — паттерн
  `WHERE id IN (..)`, O(n) UPDATE, не цикл).

## Assumptions

- **Single-user**: в текущей инсталляции Karaoke один admin-пользователь,
  никакой дополнительной авторизации для bulk-операций не требуется
  (Constitution V — `webvue3` SPA без auth). Audit-trail в
  `tbl_processes_audit` — единственный способ отследить, кто/когда
  сделал изменение (по user-agent из HTTP-запроса — прецедент spec #315).
- **UI placement (принцип «сделай так же, как рядом»)**: новые кнопки —
  **в существующей** группе bulk-действий в шапке `ProcessesTable.vue`
  (по прецеденту `SongsTable.vue` — `createKaraokeForAll`,
  `createDemucs2ForAll`). **Никаких** отдельных модальных окон. Диалог
  — **существующий** `<custom-confirm>` (поддерживает `fields` с типами
  `text/select/textarea/boolean` — прецедент spec #316 FR-007).
- **Soft-delete (FR-004, FR-005)**: процессы удаляются через
  `process_deleted_at = NOW()`, НЕ через `DELETE FROM tbl_processes`
  (прецедент spec #315 FR-006 — soft-delete по конституции III и audit
  через миграцию 47). Hard-delete out of scope.
- **Прямые enum-значения статуса (CREATING/WAITING/WORKING/DONE/ERROR)**
   в UI и API (прецедент ProcessEdit.vue:184-185, симметрия «как рядом»).
   Admin-friendly лейблы (NEW/QUEUED/RUNNING/SUCCEEDED/FAILED) — не
   используются в v1. Backend не делает маппинг — enum передаётся как есть.
   Если в будущем понадобятся дружелюбные лейблы — это отдельное
   согласованное изменение в ProcessEdit тоже (out of scope).
- **Поля для bulk-update (FR-002)**: только три поля в v1 —
  `priority`, `status`, `threadId`. `processChainId`, `processDeletedAt`,
  `processName`, `processStart`, `processEnd` — out of scope для v1
  (можно добавить позже тем же паттерном — design extensible).
- **Backend placement**: эндпоинты `/api/admin/processes/bulk-update` и
  `/api/admin/processes/bulk-delete` — в **существующем**
  `karaoke-app/.../controllers/KaraokeProcessAdminController.kt`
  (`@RequestMapping("/api/admin/processes")`, auth = `permitAll()` как весь
  контроллер — прецедент 6 endpoints из spec #315). Bulk-логика — в
  `KaraokeProcessAdminService.kt` (reuse INSERT :643, фильтр :155, guard :249).
  Главное — соблюсти Constitution V (placement в `karaoke-app`,
  `WORKING_DATABASE = com.svoemesto.karaokeapp.WORKING_DATABASE`, **НЕ**
  `karaokeweb.WORKING_DATABASE` — иначе class init `Constants.kt`
  падает с `ExceptionInInitializerError`).
- **Лимит 10000 (FR-009)**: разумный верх для UI-операции. Для
  больших выборок — out of scope v1 (можно расширить через
  pagination/batching в следующей итерации).
- **Приоритет без диапазона (v1)**: целое число без ограничений MIN/MAX.
   Согласовано с существующим ProcessEdit.vue (`type="number"` без min/max)
   и AdminService `coerceValue`-паттерном. Если в будущем понадобятся
   MIN/MAX — добавить в Constants.kt + plan.md (отдельное согласование).
- **Audit через tbl_processes_audit**: каждое bulk-изменение пишет
  одну запись на процесс в `tbl_processes_audit` (фактическая схема: `process_id, actor, action,
  old_value::jsonb, new_value::jsonb, created_at` — прецедент
  KaraokeProcessAdminService.kt:643 INSERT-паттерн, миграция 47 spec #315).
  Поля `operation/field/timestamp/user_agent` НЕ существуют — старые
  значения и новые упаковываются в jsonb (например, `old_value = {"status": "DONE"}`,
  `new_value = {"status": "ERROR"}`). Это требование продиктовано Constitution III
  (двух-БД синхронизация — sync должен видеть изменения) и FR-007
  спеки #315 (audit-trail обязателен).
- **Владелец делает runtime-проверки** (Constitution Karaoke
  «Категорически запрещено агенту», п.1 + AGENTS.md Boss урок #11):
  перезапуск `karaoke-app`, smoke-test через curl против реальной БД,
  visual verify в браузере — **только владелец**. Агент делает:
  grep, schema lookup через `docker exec karaoke-db psql -c
  information_schema.columns`, чтение SQL руками, 5-step verification
  (compile / ktlint / lint / bootJar / build / docker build).
- **5-step verification канон `brief.md:59-63`**: перед передачей
  владельцу — compileKotlin, ktlintCheck, npm run lint (для
  webvue3), bootJar (app + web), Vite build, Docker build
  webvue3 — все зелёные (Constitution Principle II/VI).
- **Git workflow (NON-NEGOTIABLE по Karaoke AGENTS.md)**: вся работа
  — в working tree до самого финала. Один squash-коммит на фиче,
  push + merge через PR. Без push в origin до явного указания
  владельца.
- **Backend bulk-endpoint — НЕ перезапускать контейнер karaoke-app**
  (governance п.1 категорически запрещено агенту). Агент собирает
  jar через `./gradlew karaoke-app:bootJar` (разрешено), но
  перезапуск и smoke-test — за владельцем.

## Out of scope (для v1)

- Hard-delete (только soft-delete через `process_deleted_at`).
- Bulk-изменение других полей (`chainId`, `name`, `start`, `end`,
  `deleted_at` direct manipulation) — паттерн extensible, но эти
  поля не в скоупе.
- Bulk-retry (повторный запуск упавших процессов) — отдельная фича.
- Bulk-restore (снятие `process_deleted_at`) — отдельная фича.
- Undo bulk-операций (откат) — audit-trail даёт возможность
  ручного восстановления, но автоматического undo нет.
- Workflow-обход transition-валидации в bulk-update (DONE → ERROR как admin-привилегия) —
  v1 применяет те же правила, что и single-edit (FR-017 315). Если в будущем
  понадобится осознанный обход — отдельное согласование.
- Атомарность для батчей > 10000 — отдельная задача с pagination.
- Per-user authorization (в текущей инсталляции один admin,
  Constitution V).