# Research: Self-Assign Tasks для редакторов

**Phase 0 output** — `/speckit.plan`  
**Created**: 2026-08-13

## Summary

Фича добавляет self-assign-возможность для редакторов: флаг `canSelfAssignTasks` в `tbl_site_users` + публичный endpoint `POST /api/public/songeditor/assign-self` + встроенное поле `assignment` в стрим `/api/public/zakroma` + кнопка «Взять в работу» в `karaoke-public/ZakromaView`.

Все нужные анкоры в существующем коде найдены. Никаких greenfield-решений не требуется — фича расширяет уже работающие паттерны.

## Resolved Unknowns (бывшие [NEEDS CLARIFICATION] / открытые вопросы по спеке)

### 1. SyncRegistry: участвуют ли `tbl_song_assignments` / `tbl_song_assignment_drafts`?

**Decision**: Да, уже участвуют. `SyncTarget.kt` импортирует `SongAssignment` и `SongAssignmentDraft` (и, судя по паттерну `GenericKaraokeDbTableSyncTarget`, оба обёрнуты в `SyncRegistry.all` через generic-механизм). `lastUpdate` помечено `useInDiff = false` (спека `SongAssignmentDraft.kt:69-70`).

**Rationale**: Sync-направление — `LOCAL_TO_SERVER` или `SERVER_TO_LOCAL` для нашей задачи неважно: self-assign пишет в `WORKING_DATABASE` (тот же singleton, что админский `assign`), поэтому запись автоматически попадёт в sync-очередь через `SongAssignment.save()` (он использует `KaraokeDbTable.createDbInstance()` — стандартный путь).

**Alternatives**:
- Явно включить `sync_songassignments_*` флаги в `KaraokeProperties.kt` — НЕ требуется, уже включено.
- Создать отдельный флаг — НЕ нужно, переиспользуем существующий sync.

**Implication**: Никаких изменений в `SyncRegistry` / `KaraokeProperties` не нужно. Достаточно следовать стандартному пути `KaraokeDbTable.createDbInstance()`.

### 2. URL/авторизация для self-assign endpoint

**Decision**: `POST /api/public/songeditor/assign-self` в `PublicSongEditorController` (рядом с существующими `/tasks/...`).

**Rationale**:
- Префикс `/api/public/songeditor/...` уже под `SiteAuthInterceptor` (наследуется автоматически по конфигу `SecurityConfig`).
- `currentUser(request)` + `if (!user.isEditor) return notFound()` — стандартный паттерн защиты в контроллере, не в interceptor.
- Извлечение `siteUser.id` из session/cookie — внутри метода.

**Alternatives**:
- `POST /api/songeditor/assign-self` в `karaoke-app` (как админский `/assign`) — НЕ подходит, требует CORS/CSRF админки, в публичной части не работает.
- Отдельный контроллер для self-assign — избыточно, расширяем существующий.

### 3. Как защититься от race condition (двойной клик)

**Decision**: Атомарная транзакция `BEGIN; SELECT ... WHERE song_id=? FOR UPDATE; ...INSERT...; COMMIT;` в SQL (через `KaraokeConnection.getConnection()`).

**Rationale**:
- Существующий `SongAssignmentController.assign` использует `findExisting(...)` (без `FOR UPDATE`) — это работает только потому, что assign — админский, низкая вероятность гонки. Для self-assign двух публичных редакторов — нужен явный row lock.
- В коде уже есть паттерн работы с прямой `Connection` через `database.getConnection()` (см. `SongAssignmentDraft.deleteByAssignment()`).
- Альтернативы — PostgreSQL advisory lock (`pg_advisory_xact_lock(song_id)`) — проще, но привязан к PostgreSQL. `SELECT FOR UPDATE` — стандарт.

**Alternatives**:
- Нет блокировки, ловим `UNIQUE` constraint — НЕ работает, в `tbl_song_assignments` нет уникального индекса на `song_id` (только на `id`).
- Optimistic locking через дополнительную колонку `version` — избыточно.
- PostgreSQL advisory lock (`pg_advisory_xact_lock(song_id)`) — оставляем как fallback план, если `FOR UPDATE` будет ломаться под нагрузкой.

**Implication**: один SQL запрос (`SELECT id FROM tbl_song_assignments WHERE song_id=? FOR UPDATE`) внутри транзакции. Если пусто — INSERT. Если есть — 409.

### 4. Структура `ZakromaAlbumSongPublicDto.assignment`

**Decision**: Отдельный простой DTO `SongAssignmentBriefDto` с полями `id`, `assigneeId`, `assignedAt`, `adminStatus` (для UI-логики «у вас» vs «у другого редактора»).

**Rationale**:
- Стрим уже сериализует 30+ полей в `ZakromaAlbumSongPublicDto` через Jackson — добавить ещё одно null-по умолчанию поле безопасно (обратная совместимость).
- `SongAssignmentDto` (10 полей) перегружен для клиента — нам нужно 3-4 поля для UI.
- `adminStatus` нужен чтобы отличать активные (`assigned`/`in_progress`/`submitted`/`rejected`) от завершённых (`approved`). Текущая «свободная» семантика — `adminStatus in ('open', 'in_progress')` (см. `SongAssignmentStatus`).

**Alternatives**:
- Не `adminStatus`, а композитный `status` через `SongAssignmentStatus.resolve()` — но он требует draft, а draft таблица для stрима не догружается. Допустимо вернуть RAW `adminStatus` и UI не показывает кнопку для approved (т.к. это «завершённая» история; в нашей бизнес-логике approved = задание выполнено, песня снова свободна).

**Implication**: добавить 1 файл `SongAssignmentBriefDto.kt` + 1 поле в `ZakromaAlbumSongPublicDto` + 1 SQL JOIN в `zakromaStream`.

### 5. SiteUserEdit.vue — паттерн добавления нового чекбокса

**Decision**: Скопировать паттерн `isEditor`-checkbox (см. `webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue`), добавить новое поле `siteUserCurrent.canSelfAssignTasks` в `data()` (через Vuex-mutation после `setSiteUserCurrent`), добавить чекбокс в HTML рядом с `isEditor` (в «Секции прав редактора»), добавить в `store.saveSiteUser` строку `if (diffs.canSelfAssignTasks !== undefined) params.canSelfAssignTasks = diffs.canSelfAssignTasks`.

**Rationale**:
- `if (diffs.editor !== undefined) params.isEditor = diffs.editor` — стандартный паттерн для optional-параметров.
- `data.siteUserCurrent` с булевыми полями — стандартная структура.
- Vuex action не трогаем, только `store.saveSiteUser` (handler `if (diffs.X !== undefined)`).

**Alternatives**:
- Создать новый endpoint `/api/siteusers/set-can-self-assign` — избыточно, переиспользуем существующий `/api/siteusers/update`.

### 6. KaraokeProperties: нужны ли новые sync-флаги?

**Decision**: Нет. Self-assign пишет через стандартный `KaraokeDbTable.createDbInstance()`, который уже покрыт существующими sync-флагами для `tbl_song_assignments`.

**Rationale**: см. п.1. Все sync-флаги уже настроены.

### 7. JSON-имя поля для `canSelfAssignTasks`

**Decision**: `canSelfAssignTasks` (без префикса `is`, как `canSelfAssignTasks` — Java/Kotlin-стиль). Используем `@get:JsonProperty("canSelfAssignTasks")` явно для совместимости с другими аннотациями.

**Rationale**:
- В Kotlin boolean field `canSelfAssignTasks` Kotlin-геттер — `isCanSelfAssignTasks()`, но Jackson для не-`is`-prefixed boolean читает имя поля как есть (без `is`-stripping — отбрасывание `is` применяется только к полям, начинающимся с `is`, см. AGENTS.md Q&A «Jackson отбрасывает is»).
- Явная `@get:JsonProperty("canSelfAssignTasks")` гарантирует правильное JSON-имя независимо от рефлексии.

**Alternatives**:
- `isSelfAssignTasks` с `@get:JsonProperty("isSelfAssignTasks")` — тоже работает, но `can*` яснее передаёт семантику «может ли».

### 8. Где хранить `canSelfAssignTasks` в `SiteUser` entity

**Decision**: Добавить поле в `SiteUser` рядом с `isEditor`/`isPremium`. Аннотация `@KaraokeDbTableField(name = "can_self_assign_tasks")`.

**Rationale**:
- Поле — атрибут сущности → живёт в `SiteUser`.
- Миграция: `ALTER TABLE tbl_site_users ADD COLUMN can_self_assign_tasks BOOLEAN NOT NULL DEFAULT FALSE`.
- `recordhash` должен учитывать новое поле — иначе sync LOCAL↔SERVER сломается (см. AGENTS.md `III. Двух-БД синхронизация`).

**Implication**: миграция должна **пересоздать триггер `recordhash`** в `tbl_site_users` (по конституции III) — это нельзя забыть.

### 9. Permissions: кто может включить флаг?

**Decision**: `SiteUsersController.update` уже не имеет проверки ролей (предполагается, что endpoint вызывается только из admin SPA). Если в будущем планируется вызывать из публичной части — добавить проверку `isAdmin` (отдельный флаг, не в этой задаче).

**Rationale**: существующий `SiteUsersController.update` уже принимает `isEditor` без доп. проверки — копируем тот же паттерн.

### 10. Где живёт endpoint: локальная БД или серверная?

**Decision**: `WORKING_DATABASE` (локальная БД), как и все остальные `PublicSongEditorController` endpoints и админский `SongEditorController.assign`.

**Rationale**: `WORKING_DATABASE` в `karaoke-web` смотрит на текущую активную БД (в проде — на SERVER, на dev-машине — на LOCAL). Sync между LOCAL и SERVER — отдельный layer.

## Architecture Decisions

| Aspect | Decision | Альтернатива |
|---|---|---|
| Endpoint URL | `POST /api/public/songeditor/assign-self` | `POST /api/songeditor/assign-self` |
| Контроллер | `PublicSongEditorController` (karaoke-web) | новый `SelfAssignController` |
| Авторизация | `currentUser(request)` + проверка `isEditor && canSelfAssignTasks` | `SiteAuthInterceptor` + per-endpoint `preAuthorize` |
| Race protection | `SELECT ... FOR UPDATE` в транзакции | PostgreSQL advisory lock |
| DTO для payload | новый `SongAssignmentBriefDto` | переиспользовать `SongAssignmentDto` |
| Payload-поле | `assignment: SongAssignmentBriefDto?` в `ZakromaAlbumSongPublicDto` | см. clarifying Q1 (выбран этот вариант) |
| Миграция | `ALTER TABLE + DROP/CREATE TRIGGER для recordhash` | отдельная таблица |
| Логика assignment | использовать `KaraokeDbTable.createDbInstance()` (стандартный save) | прямой INSERT через JDBC |
| Draft | НЕ создавать (создаётся при первом save) | создать сразу |

## Risk Analysis

| Risk | Mitigation |
|---|---|
| Race condition на двух одновременных кликах | `SELECT FOR UPDATE` в одной транзакции; проверено вручную в 2 браузерах |
| `recordhash` не учитывает новое поле → sync ломает | пересоздать триггер в миграции (см. AGENTS.md III) |
| Поле `canSelfAssignTasks` попадёт в публичный `SiteUserDto` (утечка флага) | НЕ добавлять в `SiteUserDto` публичного контроллера; только в DTO для admin (см. текущую структуру `SiteUserDto` — флаг `isEditor` уже там, добавляем рядом) |
| UI путает `isSelfAssignEditor` (computed) и `canSelfAssignTasks` (поле) | вынести в `computed` Vuex-store, как делают для `isEffectivePremium` |
| Транзакция с `FOR UPDATE` может залипнуть на минуты при deadlock | добавить `try/catch` + retry на `SQLException` (см. существующие паттерны) |
| `ZLIB` картинок/медиа в payload (heavy) | НЕ включаем в brief DTO, только metadata |

## Implementation Order (для tasks.md)

1. **DDL**: миграция `deploy/karaoke-db/XX_add_can_self_assign_tasks.sql` (column + recordhash-trigger recreate).
2. **Backend entity**: `SiteUser.canSelfAssignTasks` + `SiteUserDto` (для admin SPA).
3. **Backend update**: `SiteUsersController.update` принимает `canSelfAssignTasks`.
4. **Backend brief DTO**: `SongAssignmentBriefDto` (id, assigneeId, assignedAt, adminStatus).
5. **Backend brief DTO в стриме**: `ZakromaAlbumSongPublicDto.assignment: SongAssignmentBriefDto?` (default null).
6. **Backend query**: `PublicApiController.zakromaStream` подмешивает `assignment` через `SongAssignment.composeStatusesForSongIds` (already exists!), только для self-assign-редакторов.
7. **Backend endpoint**: `PublicSongEditorController.assignSelf` — POST `/assign-self` с атомарной транзакцией.
8. **Frontend SPA-public**: 
   - `useZakromaStreamProgress` (или новый composable) — load assignments встроено в стрим, никаких отдельных вызовов.
   - `ZakromaView.vue` — кнопка «Взять в работу» рядом с названием песни, новый action `assignSelf`.
   - `EditorWorkView.vue` (или `EditorTasksView.vue`) — кнопка «Открыть задание» заменяет «Взять в работу» для уже своих.
9. **Frontend admin SPA**:
   - `siteUsers/store.js` — `if (diffs.canSelfAssignTasks !== undefined) params.canSelfAssignTasks = diffs.canSelfAssignTasks`.
   - `SiteUserEdit.vue` — чекбокс в секции прав редактора, `v-if="!siteUserCurrent.isEditor"` для disabled+tooltip.
10. **Тесты**: ручные сценарии (AdminUI + 2 редактора + race + RepeatedUI).

## Что вынесено в plan.md (не research)

- Структура `plan.md` (Summary, Technical Context, Constitution Check, Project Structure, Complexity Tracking).
- Решение о структуре SQL миграции (точно — будет уточнено в tasks.md).
- Решение о композитном `status` vs raw `adminStatus` (см. research п.4 — выбран raw).
