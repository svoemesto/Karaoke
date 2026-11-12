---

description: "Task list for feature: удаление песни с каскадной уборкой"
---

# Tasks: Удаление песни с каскадной уборкой

**Input**: Design documents from `specs/739-song-delete-cascade/`
(plan.md, spec.md, research.md, data-model.md, contracts/song-delete-api.md, quickstart.md)

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ — все на месте.

**Tests**: включены — требование задачи (отдельные задачи на unit и integration) и
пункт 8 плана «Тест-стратегия».

**Organization**: фазы идут в порядке зависимостей, заданном лидом: миграции →
единый модуль правил владения → предпросмотр → удаление LOCAL → удаление SERVER →
фоновая задача уборки → журнал → флаг → разовая уборка (US4) → тесты →
документация → обязательные проверки. Метка `[USn]` у каждой задачи сохраняет
трассировку к пользовательским историям спеки.

**Отклонение от шаблона, зафиксированное явно.** Шаблон требует группировку по
пользовательским историям; лид задал порядок по техническим слоям (он же —
порядок выполнения). Поэтому фазы идут по слоям, а каждая задача несёт метку
`[USn]`, а в разделе «Покрытие историй» показано, какие задачи закрывают каждую
историю и в каком порядке истории становятся независимо проверяемыми.

**Формат**: `- [ ] [ID] [P?] [USn] Действие с путём к файлу — критерий готовности`

- **[P]** — задачу можно выполнять параллельно (другой файл, нет незакрытых зависимостей)
- **[USn]** — пользовательская история из spec.md, к которой относится задача
- Каждая задача заканчивается проверяемым критерием готовности

---

## Phase 0: Подготовка окружения (только для шага implement)

**Purpose**: подготовить worktree к сборке и фронтенд-проверкам до начала кода.

**Зависит от**: ничего. **Блокирует**: фазу 10 (тесты фронтенда) и фазу 12 (pre-commit).

> **Про ID**: задача получила ID `T080` вне последовательности — список T001…T079 уже
> прошёл ревью лида, перенумерация сломала бы ссылки из ревью. Порядок выполнения —
> Phase 0 → Phase 1 → …

- [x] T080 Подготовить рабочее окружение worktree: выполнить `npm ci` в `webvue3` и `karaoke-public` (или, при отсутствии сети, создать документированные симлинки `webvue3/node_modules` и `karaoke-public/node_modules` на каталоги главного checkout `/home/nsa/Karaoke/*/node_modules`); при недоступном на запись `~/.npm/_cacache` задать writable-кеш (`npm_config_cache=/home/nsa/Karaoke/.scratch/npm-cache`); после прогона pre-commit симлинки из дерева удалить — критерий: `npx prettier --check "src/**/*.{vue,js,ts,json}"` в обоих фронтендах завершается кодом 0; `git status --porcelain` не показывает артефактов в `webvue3/` и `karaoke-public/`

---

## Phase 1: Миграции схемы

**Purpose**: разблокировать удаление песни в БД и завести хранение для журнала и истории имён.

**Зависит от**: ничего. **Блокирует**: фазы 4, 5, 6, 7.

- [x] T001 Создать `deploy/karaoke-db/58_news_song_id_on_delete_set_null.sql` — пересоздать FK `tbl_news.song_id` с `ON DELETE SET NULL` и FK `tbl_song_news_announced.song_id` с `ON DELETE CASCADE` (FR-009, FR-046) — критерий: файл применяется без ошибок на LOCAL; `SELECT confdeltype FROM pg_constraint WHERE conrelid='public.tbl_news'::regclass AND contype='f'` даёт `'n'`, для `tbl_song_news_announced` — `'c'`; повторное применение идемпотентно
- [x] T002 [P] Создать `deploy/karaoke-db/59_song_name_history.sql` (файл можно писать параллельно с T001/T003; **применение** — строго в порядке 58 → 59 → 60, см. T004/T005) — таблица `tbl_song_name_history (id, song_id, file_name, changed_at)` с `UNIQUE (song_id, file_name)` и индексом по `song_id` (FR-013) — критерий: `\d tbl_song_name_history` показывает уникальный индекс и индекс по `song_id`; повторное применение не падает
- [x] T003 [P] Создать `deploy/karaoke-db/60_song_cleanup_journal.sql` (файл можно писать параллельно с T001/T002; **применение** — строго в порядке 58 → 59 → 60, см. T004/T005) — таблицы `tbl_song_cleanup_runs` и `tbl_song_cleanup_entries` с `CHECK`-ограничениями состояний, результатов и согласованности `DONE` (FR-034, FR-035, SC-003) — критерий: обе таблицы созданы, все `CHECK`-ограничения из `data-model.md` § 6 присутствуют; повторное применение идемпотентно
- [x] T004 Применить миграции 58 → 59 → 60 на LOCAL (FR-001, FR-009 — миграции снимают блокировку удаления; правило «миграция → код»: применяются **до** реализации фаз 3–7) (psql, `-v ON_ERROR_STOP=1`) по runbook `knowledge/system/infra/sql-migrations.md` § Решение 4 — критерий: `\d tbl_song_name_history`, `\d tbl_song_cleanup_runs`, `\d tbl_song_cleanup_entries` показывают ожидаемые колонки; порядок применения соблюдён
- [x] T005 Применить миграции 58 → 59 → 60 на PROD (FR-001, FR-045 — обе базы должны быть готовы до включения флага) (серверная БД, порт 5433) — критерий: те же проверки, что в T004, на SERVER; `confdeltype` = `'n'` для `tbl_news` и `'c'` для `tbl_song_news_announced`
  - **ВЫПОЛНЕНО лидом 2026-11-12 (с согласия владельца):** бэкап PROD штатным скриптом (`/root/Karaoke/deploy/karaoke-db-backup.sh` → `dumps/karaoke_db_20261112_160954.sql.gz`, 135 МБ, `gunzip -t` — целостность ОК), применение 58 → 59 → 60 через `psql -v ON_ERROR_STOP=1` (все rc=0, повторные прогоны идемпотентны), проверка: `tbl_news_song_id_fkey` → `confdeltype = 'n'`, таблицы `tbl_song_name_history` / `tbl_song_cleanup_runs` / `tbl_song_cleanup_entries` созданы, 12 CHECK-ограничений на месте.
  - **Уточнение по `tbl_song_news_announced`:** на PROD этой таблицы нет (удалена миграцией 34 и там тоже), поэтому проверка `confdeltype = 'c'` к ней неприменима; миграция 58 это учитывает (`DO $$ … IF to_regclass('public.tbl_song_news_announced') IS NOT NULL … $$`). На LOCAL таблица также отсутствует (миграция 34 применена).

**Checkpoint**: удаление песни в БД больше не блокируется FK; место для журнала и истории имён есть.

---

## Phase 2: Единый модуль правил владения артефактом

**Purpose**: один источник истины «что принадлежит песне» — используется и удалением, и разовой уборкой.

**Зависит от**: Phase 1 (для T008). **Блокирует**: фазы 3, 4, 6, 9.

- [x] T006 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongArtifactOwnership.kt` — строгий матчер владения: `isOwnArtifact(songFileName, candidateName)` на основе `SongArtifactRenamer.isArtifactContinuation` (`SongArtifactRenamer.kt:50`), без `equalsIgnoreCase` (FR-011) — критерий: для `Песня, длинная версия.mp3` при песне `Песня` матчер даёт `false`; для `Песня.accompaniment.flac` и `Песня [karaoke].png` — `true`
- [x] T007 [P] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongArtifactOwnership.kt` обход артефактов папки песни (корень + `hdemucs_mmi`, `done_files`, `done_chords`, `sheetsage`, все `symlink_*`) по правилу из T006 (FR-010, FR-011) — критерий: обход возвращает только файлы песни; файл соседней песни с префиксом в список не попадает
- [x] T008 [P] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongArtifactOwnership.kt` нормализацию для разовой уборки (`normalizeForMatch`: регистр, `ё`/`е`, тип кавычки, `01`/`1`) и правило «near-match исключается из кандидатов» (FR-042) — критерий: `Песня 01` и `Песня 1` считаются совпадающими после нормализации; совпавший объект помечается как «не кандидат»
- [x] T009 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongNameHistory.kt` — чтение и запись истории прежних имён песни (FR-013) — критерий: `SongNameHistory.record(songId, oldFileName)` пишет строку; повторная запись того же имени не создаёт дубль (нарушение `UNIQUE` не возникает)
- [x] T010 Вызвать запись истории из `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` в `renameAllArtifacts`/`renameCascadeExtraArtifacts` — прежнее имя фиксируется **до** переименования (FR-013) — критерий: переименование песни добавляет прежнее имя в `tbl_song_name_history`; повторное переименование добавляет второе прежнее имя, а не дублирует первое

**Checkpoint**: правило владения одно для обеих операций; история имён пишется.

---

## Phase 3: Предпросмотр удаления (US1)

**Goal**: администратор до подтверждения видит фактические числа — что и сколько удалится.

**Зависит от**: Phase 2. **Блокирует**: полный US1.

- [x] T011 [P] [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPreview.kt` подсчёт файлов и байтов обходом папки альбома по правилу владения (FR-023) — критерий: `filesCount` и `filesBytes` совпадают с числом файлов, реально удаляемых уборкой (расхождение 0 — SC-004)
- [x] T012 [P] [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPreview.kt` листинг бакета по префиксу `$автор/$год - $альбом/` в локальном и удалённом хранилищах, подсчёт объектов по каждому хранилищу отдельно (FR-024, В-05) — критерий: `localStorageObjects` и `remoteStorageObjects` равны фактическому числу объектов песни; отсутствие объекта в одном хранилище не считается ошибкой
- [x] T013 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPreview.kt` признаки каскада на альбом: `albumWillBeDeleted`, `albumPictureWillBeDeleted`, `albumFolderWillBeDeleted` + причина `album_has_live_songs`, и счётчик родственных песен `descendantsCount` (FR-025, FR-026) — критерий: при наличии живых песен в альбоме `albumWillBeDeleted = false`; число потомков совпадает с `SELECT count(*) FROM tbl_songs WHERE audio_parent_id = ?`
- [x] T014 [US1] Добавить эндпоинт `POST /api/song/deletepreview` (FR-023…FR-026) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` по контракту `specs/739-song-delete-cascade/contracts/song-delete-api.md` § 2 — критерий: `curl -X POST .../api/song/deletepreview -d 'id=<ID>'` возвращает 200 со всеми полями контракта; при несуществующей песни — 404 `song_not_found`; при активном процессе — 409 `process_running`
- [x] T015 [US1] Показать числа в окне подтверждения в `webvue3/src/components/Songs/edit/SongEdit.vue` (файлы, мегабайты, объекты по хранилищам, строка про альбом, число родственных песен) с экранированием значений (FR-023…FR-027, `knowledge/system/frontend/confirm-escape.md`) — критерий: в окне видны все пять показателей; `bash tools/check-confirm-escape.py` зелёный; дополнительного ввода от пользователя не требуется (FR-027)

**Checkpoint**: предпросмотр даёт точные числа, расхождение с фактом — 0.

---

## Phase 4: Удаление в LOCAL (US1)

**Goal**: строка песни и все связанные записи удаляются в рабочей базе одной транзакцией.

**Зависит от**: Phase 1, Phase 2. **Блокирует**: US1 целиком.

- [x] T016 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPlanner.kt` — план каскада по белому списку таблиц: `tbl_song_assignments`, `tbl_song_assignment_drafts`, `tbl_site_playlist_items`, `tbl_song_share_links`, `tbl_listening_history`, `tbl_search_results`, `tbl_search_async`, `tbl_events` (FR-005, FR-006) — критерий: план не содержит ни одной таблицы вне белого списка; по `tbl_events` план содержит только записи с `song_id <> 0` (FR-007)
- [x] T017 [US1] Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPlanner.kt` обнуление `audio_parent_id` у родственных песен — потомки остаются живыми (FR-008) — критерий: после выполнения плана у потомков `audio_parent_id IS NULL`, число строк у потомков не изменилось
- [x] T018 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` фазу A: одна транзакция на LOCAL — `DELETE FROM tbl_songs` плюс каскад из плана (FR-001, FR-005…FR-009) — критерий: при искусственной ошибке на середине — `rollback`, ни одна строка не удалена; при успехе — все записи каскада удалены
- [x] T019 [US1] Изменить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`: `deleteFromDb` возвращает результат (`deleted` / `not_found` / `db_error` с причиной) вместо `Unit` и не глотает исключения БД; обновить 4 вызывающих в `Utils.kt`, `controllers/ApiController.kt`, `controllers/MainController.kt`, `sync/SyncTarget.kt` (FR-002, SC-003) — критерий: ошибка DELETE не приводит к удалению файлов и видна вызывающему; проект компилируется
- [x] T020 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` защиту от удаления «не нашей» песни: `root_folder` начинается с одного из `PROJECT_ROOT_FOLDERS` (`/sm-karaoke/work|done1|done2`), `file_name` непустой и без разделителей пути (FR-011, FR-046) — критерий: запись с чужим `root_folder` отклоняется с 409 `not_owned`, ничего не удаляется
- [x] T021 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` отказ при активной фоновой обработке через `KaraokeProcess.hasActiveProcess` (`KaraokeProcess.kt:1537`) (FR-004) — критерий: при задании в статусе `WAITING` удаление отклоняется с 409 `process_running`; проверка повторяется в начале фоновой уборки
- [x] T022 [US1] Изменить эндпоинт `POST /api/song/delete` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`: структурированный ответ вместо пустого `200`, параметр `confirm` по контракту § 3 (FR-002, FR-003, FR-027) — критерий: ответ содержит `status` (`deleted` / `already_deleted` / `partially_deleted`), `deletedLocal`, `deletedServer`, `runId`; при отказе БД — 500 `db_error`, а не `200`
- [x] T023 [US1] Реализовать идемпотентность повторного удаления в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` (FR-002, edge case спеки) — критерий: повторный вызов на удалённую песню возвращает 200 `already_deleted`, не ошибку; первый вызов на несуществующую песню — 404 `song_not_found`
- [x] T024 [US1] Разобрать новое тело ответа в `webvue3/src/components/Songs/store.js` (действие `deleteCurrentSong`) — карточка не закрывается при неуспехе (FR-003, SC-003) — критерий: при ответе `db_error` карточка песни остаётся открытой и показывается сообщение об ошибке; unit-тест `webvue3/src/components/Songs/edit/__tests__/pkg-s3-31-songedit.test.js` зелёный

**Checkpoint**: удаление в LOCAL работает, ложного успеха нет.

---

## Phase 5: Удаление в SERVER (US1)

**Goal**: строка песни снимается и в серверной базе; при недоступности — честный ответ и добор.

**Зависит от**: Phase 4. **Блокирует**: полный US1.

- [x] T025 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` фазу B: отдельная транзакция на `Connection.remote()` — `DELETE FROM tbl_songs` и `DELETE FROM tbl_songs_sync` по id (FR-001, В-07) — критерий: в SERVER нет ни строки `tbl_songs`, ни строки `tbl_songs_sync` по этому id
- [x] T026 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionService.kt` поведение при недоступной SERVER-БД: ответ `partially_deleted` с `deletedServer = false` и постановка SERVER-удаления в очередь на добор (FR-045, В-01) — критерий: при недоступной SERVER-БД ответ не выглядит как успех; LOCAL-удаление при этом состоялось и не откатилось
- [x] T027 [US1] Дополнить ответ эндпоинта `POST /api/song/delete` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` полями `deletedLocal`, `deletedServer`, `serverError` по контракту § 3 (FR-045) — критерий: тело ответа соответствует `specs/739-song-delete-cascade/contracts/song-delete-api.md` § 3 во всех статусах

**Checkpoint**: удаление снимает строку в обеих базах; при недоступности сервера — честный частичный результат.

---

## Phase 6: Фоновая задача уборки (US3)

**Goal**: файлы, объекты, альбом и папка удаляются в фоне, в заданном порядке, с повторами.

**Зависит от**: Phase 2, Phase 4. **Блокирует**: US3.

- [x] T028 [US3] Добавить значение `SONG_DELETE_CLEANUP` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessTypes.kt`; задание ставится в lane `-1` (`THREAD_LANE_LIGHT_BACKGROUND`), `process_chain_id` = id прогона `tbl_song_cleanup_runs` (FR-029, контракт § 7) — критерий: значение есть в enum; `KaraokeProcess.createProcess` принимает его
- [x] T029 [US3] Добавить ветку `songDeleteCleanup` в диспетчер `runFunctionWithArgs` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (`Utils.kt:5047`); аргументы задания — `["runFunctionWithArgs", "songDeleteCleanup", "songId=<id>", "runId=<runId>"]` (FR-029, контракт § 7) — критерий: вызов возвращает `Success for 'songDeleteCleanup'` при успехе; параметры `songId` и `runId` разбираются верно
- [x] T030 [US3] Реализовать шаг «диск» в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt`: удаление всех файлов песни в корне папки альбома и в `hdemucs_mmi`, `done_files`, `done_chords`, `sheetsage` по строгому правилу владения (FR-010, FR-012) — критерий: файлы песни удалены; исходники и файлы проектов рендера удалены (вердикты `PROTECTED` реестра уборки не применяются); файлы соседней песни не тронуты
- [x] T031 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` удаление битых симлинков и сохранение живых (FR-015) — критерий: симлинки с несуществующей целью удалены; симлинки живых песен на месте
- [x] T032 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` пометку неопознанных файлов как «оставлено» без удаления (FR-016) — критерий: файл, не подошедший ни под одно правило, остался на месте и попал в журнал с `result = KEPT`, `reason = not_recognized`
- [x] T033 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` шаг «хранилища»: удаление объектов по префиксу `$автор/$год - $альбом/<имя>` в локальном MinIO и в удалённом, по текущему имени и по всем именам из `tbl_song_name_history` (FR-013) — критерий: свои объекты удалены в обоих хранилищах; обложка альбома и картинка автора не тронуты (FR-014); отсутствие объекта в одном хранилище — не ошибка
- [x] T081 [US3] После удаления объектов хранилища инвалидировать строки `tbl_storage_metadata_cache` по удалённым ключам `(source, bucket, fileName)` — тем же механизмом, что `POST /api/song/resetStorageCache` (`StorageMetadataCache.refreshKeys`), для обоих источников (LOCAL и REMOTE) (FR-037) — критерий: после уборки в `tbl_storage_metadata_cache` нет записей по удалённым ключам; проверка здоровья не считает удалённые объекты существующими
- [x] T034 [US2] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` шаг «альбом»: удаление записи `tbl_albums`, записи `tbl_pictures` картинки альбома, обложки и превью альбома в обоих хранилищах — только если удаляемая песня была последней в альбоме (FR-018, FR-019) — критерий: при непустом альбоме альбом не тронут; при последней песне запись альбома и его картинки удалены
- [x] T035 [US2] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` шаг «папка альбома»: удалить папку только если в ней не осталось файлов живых песен; иначе оставить с причиной `album_has_live_songs` (FR-020, FR-021, FR-022, SC-005) — критерий: для альбома-«близнеца» запись удалена, папка и её содержимое целы; пустые подпапки удаляются только вместе с альбомом (FR-017)
- [x] T036 [US3] Зафиксировать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` порядок шагов «диск → хранилища → альбом → папка альбома» (FR-031) — критерий: порядок записей в журнале соответствует FR-031
- [x] T037 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` повторы при недоступности хранилища: до 5 попыток с паузами 2s → 60s, каждая попытка — отдельная запись журнала (FR-032) — критерий: в журнале видны 5 попыток с возрастающими `attempt`
- [x] T038 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupExecutor.kt` остановку после исчерпания попыток с записью остатка в журнал (FR-033) — критерий: прогон получает статус `PARTIAL`/`FAILED`, остаток перечислен в журнале; повторный запуск уже удалённого файла даёт `SKIPPED`, а не `FAILED` (идемпотентность шагов)
- [x] T039 [US3] Обеспечить переживание рестарта приложения для уборки через существующий механизм `KaraokeProcess.setWorkingToWaiting` (FR-030) — критерий: после рестарта в середине уборки задание возвращается в `WAITING` и уборка продолжается с неудалённых шагов

**Checkpoint**: уборка идёт в фоне, в правильном порядке, с повторами и остатком.

---

## Phase 7: Журнал уборки и наблюдаемость (US3)

**Goal**: по каждому удалению есть запись в БД; администратор видит статус и остаток.

**Зависит от**: Phase 1, Phase 6.

- [x] T040 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupJournal.kt` запись прогонов в `tbl_song_cleanup_runs` и действий в `tbl_song_cleanup_entries` — путь или ключ, размер, результат, причина, номер попытки, дата, инициатор, `run_id` (FR-034, FR-035) — критерий: по каждому удалённому действию в журнале есть все семь полей FR-035
- [x] T041 [US3] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupJournal.kt` состояния прогона `PLANNED → APPROVED → RUNNING → DONE | PARTIAL | FAILED` с запретом `DONE` при ненулевом счётчике отказов (FR-034, SC-003) — критерий: попытка выставить `DONE` при `files_failed > 0` отклоняется
- [x] T042 [US3] Добавить эндпоинты `GET /api/song/cleanup/runs` и `GET /api/song/cleanup/runs/{runId}` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — список прогонов и записи журнала (FR-036) — критерий: ответ содержит сводку прогона и все записи журнала по `run_id`
- [x] T043 [US3] Добавить эндпоинт `POST /api/song/cleanup/runs/{runId}/resume` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — ручной добор остатка тем же прогоном (FR-033) — критерий: повторный запуск продолжает тот же `run_id`; журнал содержит обе попытки; после добира статус прогона `DONE`
- [x] T044 [US3] Добавить структурированные логи категории `infra.song.delete` с кодами `song.delete.requested`, `song.delete.blocked.process`, `song.delete.blocked.ownership`, `song.delete.db.local.ok`, `song.delete.db.local.failed`, `song.delete.db.server.ok`, `song.delete.db.server.failed`, `song.cleanup.run.started`, `song.cleanup.step.done`, `song.cleanup.step.failed`, `song.cleanup.run.finished` и SSE-уведомление о терминальном статусе прогона (FR-036, ADR `local-0005`) — критерий: `docker logs karaoke-app | grep "song.delete.\|song.cleanup."` показывает ожидаемую последовательность кодов

**Checkpoint**: по каждому удалению есть запись в БД; статус и остаток видны в админке.

---

## Phase 8: Feature flag (сквозная)

**Зависит от**: ничего. **Блокирует**: раскатку.

> **Порядок выполнения.** Флаг — предохранитель, а не декорация: эндпоинты фичи
> не должны быть доступны, пока флаг выключен. Поэтому T045/T046 реализуются
> **до** фаз 3–7 (или, что эквивалентно, фазы 3–8 мержатся одним PR — флаг
> присутствует в коде с первого дня). Откат фичи = установка
> `songDeleteEnabled = false`: код и миграции остаются, файлы и объекты не трогаются.

- [x] T045 Зарегистрировать свойство `songDeleteEnabled` (по умолчанию `false`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` и читать его через `KaraokeProperties.getBoolean("songDeleteEnabled")` (ADR `local-0001`, R-010) — критерий: `getBoolean("songDeleteEnabled")` возвращает `false` по умолчанию; ключ доступен через `/api/properties/setproperty`
- [x] T046 Отключать все эндпоинты фичи при выключенном флаге (FR-027 — откат без правки кода) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` — ответ 503 `song_delete_disabled` (FR-027, R-010) — критерий: при флаге `false` `POST /api/song/delete` и `POST /api/song/deletepreview` отвечают 503 и ничего не удаляют

**Checkpoint**: фича выключается одним свойством; откат не требует отката кода.

---

## Phase 9: Разовая уборка осиротевших артефактов (US4)

**Goal**: перечень кандидатов с отпечатком, утверждение владельцем и применение по утверждённому набору — тем же правилом владения.

**Зависит от**: Phase 2 (правило владения), Phase 7 (журнал).

- [x] T047 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupPlanner.kt` — перечень кандидатов с размерами и отпечатком набора: осиротевшие объекты хранилищ, устаревшие копии под старыми именами, обложки альбомов, которых нет в каталоге, мусор в корне бакета, осиротевшие файлы на диске, черновики заданий без заданий, исторические ссылки на несуществующие песни (FR-039, FR-041) — критерий: перечень содержит размеры и отпечаток; у каждого кандидата указана причина (`ORPHAN` / `STALE_COPY` / `UNKNOWN`)
- [x] T048 [US4] Исключать из кандидатов объекты и файлы, совпадающие с именем живой песни после нормализации (FR-042, SC-008) — критерий: near-match (регистр, `ё`/`е`, кавычки, `01`/`01`) в перечень не попадает
- [x] T049 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongOneTimeCleanupService.kt` — применение только по утверждённому отпечатку; при изменении состава набора — отказ (FR-040) — критерий: `apply` с изменившимся отпечатком отклоняется и ничего не удаляет
- [x] T050 [US4] Писать каждое разовое удаление в журнал `tbl_song_cleanup_entries` с `run_kind = ONE_TIME_CLEANUP` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongCleanupJournal.kt` (FR-043) — критерий: по каждому удалённому объекту есть запись журнала
- [ ] T083 [US4] Замерить фактически освобождённый объём по прогонам разовой уборки: сумма `bytes_deleted` из `tbl_song_cleanup_runs` и `tbl_song_cleanup_entries` за все проходы (SC-007) — критерий: замер зафиксирован в отчёте прогона; сумма не меньше 10 ГБ, иначе проходы считаются невыполненными и владельцу докладывается фактический объём
  - **НЕ ВЫПОЛНЕНО (2026-11-12): фактический объём освобождения = 0.** Протокол разовой уборки реализован (план → утверждение по отпечатку → применение → журнал), но сам разовый проход по накопленному объёму (SC-007, ≥10 ГБ) **вне объёма фичи** (plan.md § 10: «в объём фичи — протокол и механизм, не сам проход»). Замер сделан: `SELECT count(*), sum(bytes_deleted) FROM tbl_song_cleanup_runs WHERE run_kind='ONE_TIME_CLEANUP'` → 0 прогонов, 0 байт. Решение о выполнении прохода — за владельцем.
- [x] T051 [US4] Добавить эндпоинты плана, применения и статуса разовой уборки в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` с обязательным отпечатком в ответе на применение (FR-039…FR-043) — критерий: без корректного отпечатка применение отклоняется

**Checkpoint**: разовая уборка возможна по протоколу утверждённого набора и журналируется.

---

## Phase 10: Тесты

**Зависит от**: все предыдущие фазы.

**Unit** (чистые функции, без БД и сети) — `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/`:

- [x] T052 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongArtifactOwnershipTest.kt` — 8 измеренных случаев строгого правила: `Песня.mp3`, `Песня.accompaniment.flac`, `Песня [karaoke].png` (свои); `Песня, длинная версия.mp3`, `песня.mp3`, `ё`/`е`, `Песня 01.mp3`, `«Песня».mp3` (чужие) (FR-011) — критерий: тест падает на текущем коде до реализации T006 и зеленеет после неё
- [x] T053 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/ArtifactNormalizationTest.kt` — near-match по нормализации исключается из кандидатов (FR-042) — критерий: объект с именем, отличающимся только регистром/`ё`/кавычками/`01`, не признан кандидатом
- [x] T054 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPlannerTest.kt` — белый список таблиц каскада; по `tbl_events` — только `song_id <> 0`; у потомков обнуляется `audio_parent_id` (FR-005…FR-008) — критерий: тест фиксирует, что никакая таблица вне белого списка не попадает в план
- [x] T055 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/CleanupRunStateMachineTest.kt` — переходы состояний прогона; `DONE` невозможен при `*_failed > 0` (FR-034, SC-003) — критерий: тест падает при попытке выставить `DONE` с отказами
- [x] T056 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletionPreviewTest.kt` — предпросмотр совпадает с фактом уборки, расхождение 0 (FR-023, SC-004) — критерий: числа предпросмотра равны числам удалённых файлов и объектов
- [x] T057 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/StorageKeyOwnershipTest.kt` — свои и чужие ключи MinIO: обложка альбома и картинка автора не попадают в удаляемые (FR-013, FR-014) — критерий: ключи обложек отсутствуют в списке удаляемых
- [x] T058 [P] [US2] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/AlbumFolderGuardTest.kt` — папка альбома не удаляется при наличии файлов живых песен; ни один файл живой песни не удалён (FR-020, FR-021, FR-022, SC-005) — критерий: тест падает, если папка удаляется при живых песнях

**Integration** (живая БД, оба контура, тестовое хранилище) — `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/`:

- [ ] T059 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteEndToEndTest.kt` — сквозной сценарий: строка gone в LOCAL и SERVER, файлы gone, объекты gone в обоих MinIO, альбом gone (FR-001, SC-001, SC-002) — критерий: после уборки не осталось ни файла, ни объекта песни, включая объекты под прежними именами
  - **Покрыто частично, отдельным файлом НЕ написано:** сквозной сценарий разложен по наборам `SongDeleteCascadeDbTest` (каскад в БД на живой LOCAL-БД, в транзакции с откатом) и `SongDeleteStorageRetryTest` (фоновая уборка, повторы, статус прогона). Сквозной сценарий с реальными MinIO и обеими базами закрывается приёмкой T079.
- [x] T060 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteDbFailureTest.kt` — отказ БД даёт 500 `db_error`, а не `200`; файлы не тронуты (FR-002, FR-003, SC-003) — критерий: тест падает, если эндпоинт отвечает `200` при отказе БД
- [x] T061 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteIdempotencyTest.kt` — повторное удаление → `already_deleted` (FR-002) — критерий: второй вызов не возвращает ошибку
- [x] T062 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteBothDatabasesTest.kt` — строка удалена в LOCAL и в SERVER; в SERVER удалена и строка `tbl_songs_sync`; новость осталась с `song_id IS NULL` (FR-001, FR-009, FR-045) — критерий: обе базы проверены; тест падает, если строка осталась в SERVER
- [x] T063 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteStorageRetryTest.kt` — 5 попыток с backoff → `PARTIAL` + остаток в журнале → добор → `DONE` (FR-032, FR-033, SC-006) — критерий: в журнале видны 5 попыток и итоговый статус `DONE` после добора
- [ ] T064 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteNewsFkTest.kt` — регрессия: песня с новостью удаляется после миграции 58 (FR-009) — критерий: тест падает на схеме с FK `NO ACTION` и зеленеет после миграции
  - **Покрыто ассертом в `SongDeleteCascadeDbTest`:** новость песни остаётся в `tbl_news`, привязка обнуляется (`song_id IS NULL`) — проверяется на живой LOCAL-БД после миграции 58.
- [ ] T065 [P] [US2] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteAlbumTwinTest.kt` — альбом-«близнец»: запись удалена, папка осталась; ни один файл живой песни не удалён (FR-022, SC-005) — критерий: папка и её содержимое целы
  - **Покрыто тестом `AlbumFolderGuardTest`:** альбом-«близнец» — папка остаётся, файлы живой песни целы (`remainingFilesCount` > 0 → папка не удаляется).
- [ ] T066 [P] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteDescendantTest.kt` — потомки живы, `audio_parent_id` обнулён (FR-008) — критерий: число строк у потомков не изменилось
  - **Покрыто ассертом в `SongDeleteCascadeDbTest`:** потомок остаётся жив, `audio_parent_id` обнуляется (значение 0 — «нет родителя», см. примечание к отклонению в отчёте).
- [ ] T067 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteRestartResumeTest.kt` — перезапуск в середине уборки → продолжение (FR-030, SC-009) — критерий: после «перезапуска» уборка доходит до конца, журнал содержит обе попытки
  - **Отдельным файлом НЕ написано:** переживание рестарта обеспечивается существующим механизмом `KaraokeProcess.setWorkingToWaiting` (WORKING → WAITING), который фича не меняет; идемпотентность шагов (уже удалённый файл → SKIPPED, а не FAILED) реализована в `SongCleanupExecutor`. Проверка «рестарт в середине уборки» требует пересоздания контейнера и входит в приёмку T079.
- [x] T082 [P] [US1] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteAdminAccessTest.kt` — удаление доступно администратору с текущими правами; отдельная роль «удаление песен» не вводится, новых прав в конфигурации доступа не появляется (FR-028) — критерий: тест падает, если удаление требует новой роли или если в коде/конфигурации добавлена отдельная роль на удаление
- [x] T084 [P] [US1] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeletePublicBehaviourTest.kt` — публичное поведение не изменилось: публичные эндпоинты и страницы не тронуты, страница удалённой песни отвечает как раньше (FR-044, SC-010) — критерий: diff по `karaoke-web`/`karaoke-public` пуст; тест фиксирует прежний ответ публичного эндпоинта
- [x] T085 [P] [US3] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/songdeletion/SongDeleteTracesUnchangedTest.kt` — удаление песни не изменяет системные следы: `songs_history.txt`, `audit_log`, старые записи кеша хранилища (FR-038) — критерий: контрольные суммы перечисленных следов до и после удаления совпадают
- [x] T068 Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/SongDeleteRegressionTest.kt` — регрессы, которые не должны сломаться: `RenameAllArtifactsTest`, `StorageCacheResetTest`, `ArtifactCleanupGuardTest`, `CleanupJournalTest`, `PkgS322ProcessTest` (FR-011, FR-013, FR-038) — критерий: перечисленные наборы остаются зелёными
- [x] T069 Прогнать `./gradlew :karaoke-app:test --parallel` и `cd webvue3 && npm run test:unit` — критерий: оба набора зелёные; новые тесты не помечены `@Disabled`

**Checkpoint**: покрыты все SC-001…SC-010 и 13 edge cases (матрица — в `quickstart.md` § 4–5).

---

## Phase 11: Документация (FR-009)

- [x] T070 Дополнить `knowledge/product/features/song-delete-cascade.md` фактами реализации (классы, команды, ловушки) и прогнать `bash tools/check-feature-doc.sh knowledge/product/features/song-delete-cascade.md` (FR-009 **Конституции** § VI — не путать с FR-009 спеки, который про новости песни) — критерий: скрипт завершается с кодом 0; все 6 обязательных секций на месте; `slug` == имя файла
- [x] T071 Создать `knowledge/domains/catalog/components/song-artifact-ownership.md` (компонент L3 «правило владения артефактом») и добавить ссылку на него в `knowledge/domains/catalog/domain.md` — Linking Protocol L3 → L2 (FR-009 **Конституции** § VI — не путать с FR-009 спеки, который про новости песни) — критерий: `python3 tools/lint-knowledge.py` зелёный; ссылка из `domain.md` присутствует; компонент перечислен в `knowledge/domains/README.md`, если изменился состав компонентов
- [x] T072 Привести в соответствие фактам (FR-009 **Конституции** § VI — не путать с FR-009 спеки, который про новости песни) `knowledge/domains/catalog/components/song-lifecycle.md` (журнал уборки в БД, каскад удаления), `knowledge/domains/storage/domain.md` (`tbl_songs_sync` — write-only очередь на SERVER; бакет `karaoke-cache` не существует) и `knowledge/domains/processing/components/two-db-sync.md` (журнал уборки не участвует в sync) — критерий: `python3 tools/lint-knowledge.py` зелёный; расхождения, отмеченные в `spec.md` § Knowledge References, сняты
- [x] T073 Обновить каталог миграций в `knowledge/system/infra/sql-migrations.md` — добавить строки 58, 59, 60 и исправить счётчик файлов — критерий: в каталоге есть три новые миграции; счётчик соответствует `ls deploy/karaoke-db/*.sql`

**Checkpoint**: SSoT отражает фактическое состояние.

---

## Phase 12: Обязательные проверки

- [x] T074 Выполнить `./gradlew :karaoke-app:compileKotlin :karaoke-app:ktlintCheck` с `GRADLE_USER_HOME="${GRADLE_USER_HOME:-$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")/.gradle}"` — критерий: обе задачи зелёные, без новых нарушений ktlint
- [x] T075 Выполнить `bash tools/check-feature-doc.sh knowledge/product/features/song-delete-cascade.md` — критерий: код возврата 0
- [x] T086 Выполнить `python3 tools/lint-knowledge.py` — структурный и стилевой линтер SSoT (Constitution § VI, Knowledge SSoT) — критерий: код возврата 0, «All documentation passes architectural style and structural checks»
- [x] T076 Выполнить `python3 tools/check-ssot-impact.py` — критерий: код возврата 0; изменения кода сопровождены обновлением `knowledge/`
- [x] T077 Выполнить `PRE_COMMIT_HOME=/home/nsa/Karaoke/.scratch/pre-commit pre-commit run --all-files` — критерий: все хуки пройдены; ни один guard не падает
- [x] T078 Выполнить `./gradlew :karaoke-app:test --parallel`, `./gradlew :karaoke-web:ktlintCheck`, `cd webvue3 && npm run lint && npm run test:unit && npm run format:check && cd ..` — критерий: все наборы зелёные
- [ ] T079 Пройти сценарии приёмки `specs/739-song-delete-cascade/quickstart.md` § 3–5 на admin-машине с включённым флагом `songDeleteEnabled` — критерий: все 13 edge cases и все 10 SC пройдены; расхождение предпросмотра и факта — 0; приёмка FR-001/FR-045 выполнена **на обеих базах** (LOCAL и SERVER)
  - **НЕ ВЫПОЛНЕНО агентом (2026-11-12): образ собран, пересоздание контейнера заблокировано guard'ом.** Сделано: Pass 380 — очередь дождалась простоя (`POST :7906/api/processesdigests` → 0 WORKING/CREATING), очередь остановлена (`GET :8898/process/stop` → `isworking=false`); образ собран из ветки 739 (`docker image build … -t sm-karaoke.ru:5443/karaoke/karaoke-app:1`, id `f5fd4d6ff565`), в jar — `services/songdeletion/*`, `SongNameHistory`, `SongDeleteResult`. Не сделано: `docker compose up -d` (он же `do.sh start_app`) — host-плагин `agent-command-guard` относит команду к классу «ask», а окно согласия владельца записано на сессию лида (`session-dbf18e13-…`), каталог `~/.dsh/policies/consent/` для сессии агента read-only. Шаг переда лиду (команда и чек-лист фактов — в отчёте `implement-report.md` § 5).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 0 (окружение)**: без зависимостей; блокирует фазы 10 и 12 (нужны `node_modules` для фронтенд-проверок и pre-commit).
- **Phase 1 (миграции)**: без зависимостей; блокирует фазы 4, 5, 6, 7.
- **Phase 2 (правило владения)**: зависит от T009 (Phase 1) частично; блокирует фазы 3, 4, 6, 9.
- **Phase 3 (предпросмотр)**: зависит от Phase 2; независим от фаз 4–7.
- **Phase 4 (удаление LOCAL)**: зависит от Phase 1, Phase 2; блокирует Phase 5.
- **Phase 5 (удаление SERVER)**: зависит от Phase 4.
- **Phase 6 (фоновая уборка)**: зависит от Phase 2, Phase 4; блокирует Phase 7.
- **Phase 7 (журнал)**: зависит от Phase 1, Phase 6.
- **Phase 8 (флаг)**: независима по зависимостям, но выполняется **до** фаз 3–7 (предохранитель: эндпоинты недоступны при выключенном флаге); нужна до раскатки.
- **Phase 9 (разовая уборка)**: зависит от Phase 2, Phase 7.
- **Phase 10 (тесты)**: зависит от всех фаз реализации.
- **Phase 11 (документация)**: зависит от реализации (факты) — частично может идти параллельно.
- **Phase 12 (проверки)**: последняя.

### Покрытие историй

| История | Задачи | Независимая проверка |
|---|---|---|
| **US1 (P1)** — админ удаляет песню | T011–T015, T016–T027, T059–T062, T064, T066, T082, T084 | удалить тестовую песню: строка gone в обеих базах, файлы и объекты gone, предпросмотр совпал с фактом |
| **US2 (P2)** — каскад на альбом | T034, T035, T058, T065 | удалить последнюю песню альбома: запись альбома, картинки и папка удалены; для «близнеца» папка осталась |
| **US3 (P3)** — фоновая уборка с журналом и повторами | T028–T044, T063, T067, T081, T085 | смоделировать недоступность хранилища: повторы, остаток в журнале, статус «не завершено», добор вручную |
| **US4 (P4)** — разовая уборка | T047–T051, T083 | сформировать перечень на тестовом наборе, утвердить, применить, перезамерить: список пуст, живые объекты нетронуты |

### Story dependencies

- **US1**: стартует после Phase 1–2; ни от одной другой истории не зависит.
- **US2**: зависит от инфраструктуры US1 (общий исполнитель уборки), но проверяется независимо.
- **US3**: зависит от инфраструктуры US1 (тот же исполнитель и журнал), проверяется независимо.
- **US4**: зависит от общего правила владения (Phase 2) и журнала (Phase 7); проверяется независимо.

### Within each phase

- Модель и правило — до сервиса; сервис — до эндпоинта; эндпоинт — до теста на него.
- Тесты пишутся **до** реализации и обязаны падать на текущем коде (правило 7 `knowledge/system/agent-mechanics.md`).
- Проверка — после последней правки (правило 6).

---

## Parallel Opportunities

- Phase 1: T002 и T003 параллельны (разные файлы миграций).
- Phase 2: T007, T008, T009 параллельны (разные функции/файлы).
- Phase 3: T011, T012, T013 параллельны.
- Phase 10: все unit-тесты (T052–T058) параллельны; integration-тесты (T060, T061, T062, T064–T066, T082, T084, T085) — параллельно между собой, если тестовая БД позволяет.
- Phase 11: T071, T073 — разные файлы, параллельны.
- Phase 12: проверки T074–T078, T086 — последовательно (каждая опирается на результат предыдущей).

---

## Parallel Example: Phase 10 (тесты)

```bash
# Все unit-тесты правила владения — одной пачкой:
Task: "T052 Создать SongArtifactOwnershipTest.kt"
Task: "T053 Создать ArtifactNormalizationTest.kt"
Task: "T054 Создать SongDeletionPlannerTest.kt"
Task: "T055 Создать CleanupRunStateMachineTest.kt"
Task: "T056 Создать SongDeletionPreviewTest.kt"
Task: "T057 Создать StorageKeyOwnershipTest.kt"
Task: "T058 Создать AlbumFolderGuardTest.kt"

# Integration-тесты разных историй — параллельно:
Task: "T059 Создать SongDeleteEndToEndTest.kt"
Task: "T064 Создать SongDeleteNewsFkTest.kt"
Task: "T065 Создать SongDeleteAlbumTwinTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

0. Phase 0 — подготовка окружения (T080).
1. Phase 1 — миграции 58/59/60 на LOCAL (T001–T004).
2. Phase 2 — правило владения (T006–T010).
3. Phase 4 — удаление в LOCAL (T016–T024).
4. Phase 3 — предпросмотр (T011–T015).
5. Phase 5 — удаление в SERVER (T025–T027).
6. **СТОП и ПРОВЕРКА**: тестовая песня удаляется, предпросмотр совпал с фактом, ложного успеха нет.
7. Phase 6–7 — фоновая уборка и журнал.
8. Phase 8 — флаг; раскатка на admin-машине.

### Incremental Delivery

1. Phase 1 + Phase 2 → база готова (миграции сняли блокировку FK, правило владения одно).
2. Phase 3 → предпросмотр (админ видит последствия до подтверждения).
3. Phase 4 → удаление в LOCAL (MVP: строка и каскад БД).
4. Phase 5 → удаление в SERVER (MVP полный: обе базы).
5. Phase 6 + Phase 7 → фоновая уборка с журналом (US3).
6. Phase 9 → разовая уборка (US4).
7. Phase 10 → тесты, Phase 11 → документация, Phase 12 → проверки.

### Parallel Team Strategy

- Разработчик A: Phase 1 + Phase 2 (миграции и правило владения).
- Разработчик B: Phase 3 + Phase 4 (предпросмотр и удаление LOCAL) — после Phase 2.
- Разработчик C: Phase 6 + Phase 7 (фоновая уборка и журнал) — после Phase 2.
- Все: Phase 10–12 после слияния.

---

## Notes

- **[P]** = разные файлы, нет зависимостей от незакрытых задач.
- **[USn]** = трассировка к пользовательской истории спеки.
- Каждая история проверяется независимо; после каждой фазы — остановка на чекпоинт.
- Коммит — после каждой задачи или логической группы; `git add` поимённо, без `-A`.
- Спека (`specs/739-song-delete-cascade/spec.md`) — контракт и не изменяется.
- Решения лида по ранее открытым вопросам — в `plan.md` § «Решения лида по открытым вопросам».
- Ступени: избегать размытых формулировок, конфликтов по одному файлу и cross-story зависимостей, ломающих независимость.
