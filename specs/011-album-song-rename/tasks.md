# Tasks: Альбом как сущность + переименование Settings→Song

**Input**: Design documents from `/specs/011-album-song-rename/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/api.md](./contracts/api.md), [quickstart.md](./quickstart.md)

**Для администратора**: пошаговая инструкция по применению миграций и деплою на LOCAL/SERVER —
[RUNBOOK.md](./RUNBOOK.md) (обновляется по мере выполнения задач ниже).

**Tests**: Тесты не запрошены в спеке — проект не имеет CI-тестов для этого пути (constitution.md).
Проверка — через ручные сценарии `quickstart.md`, ссылки на них включены в задачи ниже.

**Organization**: Задачи сгруппированы по user story (US1/US2/US3 из `spec.md`) для независимой
реализации и проверки. Дополнительно есть Foundational-фаза — физическое переименование таблицы
теперь блокирующее для US1/US2 (Album/SongCoAuthor ссылаются на переименованную таблицу по FK),
это отклонение от «все user story независимы» явно объяснено ниже.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1/US2/US3 — соответствие user story из spec.md
- Пути указаны абсолютно от корня репозитория `/home/dev/Karaoke`

---

## Phase 1: Setup

**Purpose**: Подготовка перед изменением схемы/кода — ничего не меняет в поведении.

- [X] T001 Зарезервировать имена миграций: создать пустые файлы `deploy/karaoke-db/28_rename_settings_to_songs.sql`, `deploy/karaoke-db/29_albums.sql`, `deploy/karaoke-db/30_song_coauthors.sql` с заголовочным комментарием (назначение, дата, ссылка на `research.md`), по конвенции `CONTRIBUTING.md` (`sql-migration-filenames`)
- [X] T002 [P] Зафиксировать baseline для последующей проверки: `SELECT count(*) FROM tbl_settings;`, `SELECT count(*) FROM tbl_settings WHERE song_album <> '';`, `SELECT count(DISTINCT (song_author, song_year, song_album)) FROM tbl_settings WHERE song_album <> '';` на LOCAL БД — сохранить числа для сверки в Сценариях 0 и 1 `quickstart.md` (**baseline: 19441 песня, 19441 с непустым альбомом, 2162 уникальных комбинаций автор+год+альбом**)

**Checkpoint**: Готово к Foundational-фазе.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Физическое переименование `tbl_settings`→`tbl_songs` — обязательный предшественник
для US1 (`tbl_albums`/`tbl_songs.album_id` — FK на переименованную таблицу) и US2
(`tbl_song_authors.song_id` — FK на переименованную таблицу). Здесь переименовывается **только**
физическая таблица и связанные с ней строковые литералы в коде (чтобы приложение не сломалось
сразу после DDL) — **не** сам Kotlin-класс `Settings`/DTO/поля (это отдельно, полностью в US3,
т.к. не блокирует ни US1, ни US2 — FK ссылается на таблицу в БД, а не на Kotlin-класс).

**⚠️ CRITICAL**: Ни одна задача US1/US2 не может начаться, пока эта фаза не завершена и не
проверена (см. `research.md` §5.1 runbook и `quickstart.md` Сценарий 0).

- [X] T003 Написать `deploy/karaoke-db/28_rename_settings_to_songs.sql`: `ALTER TABLE tbl_settings RENAME TO tbl_songs`, переименовать sequence, PK/unique-констрейнты, ~25 индексов, функции/триггеры `recordhash`/`update_last_updated`, аналогично для `tbl_settings_sync`→`tbl_songs_sync` (см. `research.md` §5.1) (**содержимое написано на основе реальной схемы LOCAL БД: 39 индексов, 2 sequence, 4 констрейнта, 2 функции, 1 именной триггер**)
- [X] T004 Применить `28_rename_settings_to_songs.sql` на LOCAL БД; проверить по `quickstart.md` Сценарий 0, шаги 1-3 (счётчики строк совпадают, `to_regclass('tbl_settings')` = NULL) (**применено на LOCAL с явного согласия пользователя; 19441 строка сохранена, `tbl_settings`/`tbl_settings_sync` больше не существуют**)
- [X] T005 Обновить константу `TABLE_NAME` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Settings.kt` с `"tbl_settings"` на `"tbl_songs"` (значение константы; класс пока остаётся `Settings`, файл не переименовывается — это задача US3)
- [X] T006 [P] Найти и обновить все raw-SQL строковые литералы `tbl_settings`/`tbl_settings_sync` на `tbl_songs`/`tbl_songs_sync` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (`tableName = "tbl_settings_sync"`), `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt` и любых других файлах, найденных через `grep -rn "tbl_settings" karaoke-app karaoke-web` (**фактически затронуто 16 файлов, включая `karaoke-web/StatBySong.kt`, `KaraokeProcessWorker.kt`, `ExportAlignmentDataset.kt`, `ListeningHistory.kt`, `MonetizationStats.kt`, `StatBySong.kt` — шире изначальной оценки; `karaoke-app:compileKotlin`/`karaoke-web:compileKotlin` проходят чисто**)
- [X] T007 (частично) Собрать `karaoke-web` с изменениями T005-T006 (`do.sh build_web && do.sh start_web` — разрешено агенту) — перезапущен, логи чистые (`StatBySong.refreshCache: total=19119...`). **`karaoke-app` — ТОЛЬКО пользователь** (ограничение конституции); на момент этой записи `karaoke-app` ещё не пересобран и уже ошибается (таблица переименована, старый джар ищет `tbl_settings`) — см. `RUNBOOK.md` шаг для администратора
- [ ] T008 Проверить `quickstart.md` Сценарий 0, шаги 4-5 целиком: список песен в `webvue3` открывается без ошибок, нет `relation "tbl_songs" does not exist` — **блокируется пересборкой `karaoke-app` пользователем**

**Checkpoint**: Таблица переименована, приложение работает как раньше на новом имени. US1 и US2
можно начинать (параллельно, если позволяют ресурсы).

---

## Phase 3: User Story 1 - Альбом как отдельная сущность, песня ссылается на альбом (Priority: P1) 🎯 MVP

**Goal**: Администратор управляет альбомами (автор/год/название/тип/сортировка) как отдельными
записями; существующие песни автоматически привязаны к альбомам; порядок альбомов одного года
настраиваем и виден на публичном сайте.

**Independent Test**: см. `spec.md` US1 Independent Test + `quickstart.md` Сценарии 1, 2 и
альбомная часть Сценария 4.

> Примечание: на этой фазе Kotlin-класс песни всё ещё называется `Settings` (US3 переименует его
> позже) — поля/эндпоинты ниже используют имена из `data-model.md` (`albumId` и т.п.), но
> добавляются в существующий `Settings.kt`.

- [X] T009 [P] [US1] Дописать `deploy/karaoke-db/29_albums.sql`: `CREATE TABLE tbl_albums` (`id`, `author_id` FK→`tbl_authors.id` `ON DELETE RESTRICT`, `year`, `name`, `album_type` `VARCHAR DEFAULT 'studio'`, `sort_order`, `recordhash`) + `UNIQUE(author_id, year, name)` + индекс `(author_id, year)` + recordhash-триггер; добавить `tbl_songs.album_id INTEGER NULL REFERENCES tbl_albums(id) ON DELETE SET NULL` + пересборка `update_tbl_songs_recordhash()` с новой колонкой (см. `data-model.md`)
- [X] T010 [US1] Применить `29_albums.sql` на LOCAL БД (зависит от T009, от Foundational T003-T008) (**применено; `tbl_albums` + FK на `tbl_authors`/`tbl_songs` подтверждены `\d`**)
- [X] T011 [P] [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumType.kt` — enum `AlbumType(dbValue: String, description: String)` со значениями `STUDIO/LIVE/COMPILATION/BOOTLEG` и `fromDb()`, по образцу `model/SongType.kt`
- [X] T012 [P] [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt` — `KaraokeDbTable`-сущность с `@KaraokeDbTableField` на каждом поле, `companion object { TABLE_NAME, loadList, getAlbumById, getAlbumsByIds, createNewAlbum, delete }`, по образцу `model/Author.kt`
- [X] T013 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/AlbumDTO.kt` (`fromDto`/`toDTO`) по образцу `model/AuthorDTO.kt` (зависит от T012)
- [X] T014 [US1] Зарегистрировать `AlbumsSyncTarget = GenericKaraokeDbTableSyncTarget(key = "albums", ...)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`, добавить в `SyncRegistry.all` (зависит от T012)
- [X] T015 [US1] Добавить 8 флагов `sync_albums_<push|pull>_<insert|update|delete|move>_allowed` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (зависит от T014) (**karaoke-app:compileKotlin проходит чисто**)
- [X] T016 [US1] Добавить поле `albumId: Long` (`@KaraokeDbTableField(name = "album_id")`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Settings.kt` (зависит от T010) (**фактически `var albumId: Long?` через `SettingField.ALBUM_ID` — Settings.kt bespoke, не reflection; потребовалось добавить `null ->` ветку в diff-apply `when`-блок, иначе null уходил бы как строка `"null"` и падал на FK/типе; см. коммент в коде**)
- [X] T017 [US1] Реализовать проверку FR-008 (альбом песни должен принадлежать тому же автору, что и главный автор песни) на запись — в эндпоинте обновления песни, `karaoke-app/.../controllers/ApiController.kt` (зависит от T016, T012) (**`songs2Update` возвращает `false`, если `authorId` альбома не совпадает с главным автором песни**)
- [X] T018 [US1] Реализовать `/api/albums/albumsdigests`, `/api/albums/createalbum`, `/api/albums/updatealbum`, `/api/albums/deletealbum` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` по образцу `apisUpdateAuthor`/`apisAuthorsDigest` (зависит от T013)
- [X] T019 [US1] Написать одноразовый идемпотентный backfill (Kotlin-скрипт/утилита) по алгоритму `research.md` §6 (**`model/AlbumBackfill.kt` + эндпоинт `/api/utils/backfillalbumsfromsongs`, по образцу `doRecalcPlayerReadiness` — фоновый поток + SSE-уведомление**)
- [ ] T020 [US1] Запустить backfill на LOCAL, проверить по `quickstart.md` Сценарий 1 (зависит от T019, T002 baseline) — **БЛОКИРУЕТСЯ пересборкой `karaoke-app` пользователем (T007)**: код не сможет выполниться, пока не задеплоен
- [X] T021 [P] [US1] Добавить `albumId`/`albumName` в `SettingsDTOdigest` (`karaoke-app/.../model/SettingsDTOdigest.kt`) для отображения в таблице песен админки (зависит от T013)
- [X] T022 [P] [US1] Создать `webvue3/src/components/Albums/store.js` (state/getters/mutations/actions: `loadAlbumsDigests`, `setAlbumValuePromise`, `createAlbumPromise`, `deleteAlbumPromise`) по образцу `components/Authors/store.js`
- [X] T023 [P] [US1] Создать `webvue3/src/components/Albums/AlbumsTable.vue` — колонки: автор (резолвится по authorsDigest), год, название, тип, сортировка (зависит от T022) (**без отдельного filter/ подмодуля — упрощено; редактирование ID автора текстовым полем, не пикером по имени — ограничение `CustomConfirm.fldIsSelect` (нет пар label≠value), UX-долг, не блокирует функциональность**)
- [X] T024 [US1] Создать `webvue3/src/views/AlbumsView.vue`, зарегистрировать маршрут `/albums` в `webvue3/src/router/index.js`, добавить nav-ссылку «Альбомы» в `webvue3/src/App.vue` (зависит от T023) (**`npm run build` webvue3 — успешно**)
- [X] T025 [US1] В редакторе песни `webvue3/src/components/Songs/edit/SongEdit.vue` добавить выбор альбома (из существующих альбомов автора песни, с возможностью создать новый) (зависит от T018, T021) (**`<select v-model.number="song.albumId">`, отфильтровано по автору песни через `albumsForSongAuthor`; сохранение через уже существующий generic diff-механизм (`getSongDiff` — по ключам объекта, без доп. правок) — `npm run build` webvue3 успешно; создание нового альбома "на лету" не реализовано, админ создаёт через раздел «Альбомы» отдельно**)
- [X] T026 [US1] Расширить `Zakroma.kt`/`ZakromaAlbumPublicDto`: `ZakromaAlbum.albumType`/`sortOrder` резолвятся из реального `Album` (если песни альбома уже привязаны), `compareTo` — `(year, sortOrder, albumName)` вместо `(year, albumName)` (зависит от T012) (**karaoke-app/karaoke-web compileKotlin чисто; фактическая правка — `model/Zakroma.kt`, не `PublicApiController.kt` — сортировка формируется на уровне `Zakroma`/`ZakromaAlbum`, контроллер их не трогает**)
- [X] T027 [US1] Обновить `karaoke-public/src/views/ZakromaView.vue` — бейдж типа альбома (`albumTypeLabel`, скрыт для `studio`); `store/modules/zakroma.js` не требовал изменений — клиент и так не пересортировывает `zak.albums`, использует серверный порядок как есть (зависит от T026) (**`npm run build` karaoke-public успешно**)
- [ ] T028 [US1] Сквозная проверка `quickstart.md` Сценарий 2 и альбомная часть Сценария 4 на LOCAL (зависит от T009-T027) — **БЛОКИРУЕТСЯ пересборкой `karaoke-app` пользователем (T007)**

**Checkpoint**: US1 полностью функциональна и проверяема независимо — MVP готов к демонстрации.

---

## Phase 4: User Story 2 - У песни может быть несколько авторов (Priority: P2)

**Goal**: Администратор добавляет/удаляет произвольное число соавторов песни, не влияя на
главного автора/URL/принадлежность альбому.

**Independent Test**: см. `spec.md` US2 Independent Test + `quickstart.md` Сценарий 3.

- [X] T029 [P] [US2] Дописать `deploy/karaoke-db/30_song_coauthors.sql`
- [X] T030 [US2] Применить `30_song_coauthors.sql` на LOCAL БД (**применено**)
- [X] T031 [P] [US2] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongCoAuthor.kt`
- [X] T032 [US2] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongCoAuthorDTO.kt`
- [X] T033 [US2] Зарегистрировать `SongCoAuthorsSyncTarget` (**`key = "songcoauthors"`**) в `SyncTarget.kt` + `SyncRegistry.all`
- [X] T034 [US2] Добавить 8 флагов `sync_songcoauthors_*` в `KaraokeProperties.kt`
- [X] T035 [US2] Реализовать `/api/songs/coauthors/list|add|remove` (**`karaoke-app:compileKotlin` чисто**)
- [X] T036 [P] [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue` добавить UI: список текущих соавторов + добавление/удаление (зависит от T035) (**inline в SongEdit.vue, не отдельная модалка; добавление по ID автора текстовым полем — тот же UX-долг, что у Album picker; 3 новых action в `Songs/store.js`; `npm run build` webvue3 успешно**)
- [ ] T037 [US2] Сквозная проверка `quickstart.md` Сценарий 3 на LOCAL (зависит от T029-T036) — **БЛОКИРУЕТСЯ пересборкой `karaoke-app` пользователем (T007)**

**Checkpoint**: US1 и US2 работают одновременно и независимо друг от друга.

---

## Phase 5: User Story 3 - Согласованное именование сущности "песня" (Priority: P3)

**Goal**: Полное переименование `Settings`→`Song` в коде (класс/DTO/типы полей/webvue3), с
разрешением конфликта имён и без регрессий в существующих сценариях.

**Independent Test**: см. `spec.md` US3 Independent Test + `quickstart.md` Сценарий 5.

> Технически эти задачи не зависят от завершения US1/US2 (только от Foundational) — их можно
> выполнять параллельно с US1/US2 отдельным разработчиком, несмотря на то что они описаны после
> US1/US2 по приоритету P1→P2→P3. Однако T016 (US1) и T031 (US2) добавляют новые поля в
> `Settings.kt`, поэтому при полностью параллельной работе возможен merge-конфликт на этом файле —
> не логическая зависимость, а обычное пересечение по файлу.

- [ ] T038 [US3] Переименовать существующий рендер-класс `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` → `SongRenderContext.kt` (класс `Song`→`SongRenderContext`); обновить 4 места использования: `Poi.kt`, `controllers/MainController.kt` (×3), `controllers/ApiController.kt` (×1) — должно быть сделано ДО T040, чтобы освободить имя `Song`
- [ ] T039 [P] [US3] Удалить мёртвый дубликат `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song2.kt`
- [ ] T040 [US3] Переименовать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Settings.kt` → `Song.kt` (класс `Settings`→`Song`) (зависит от T038)
- [X] T041 [P] [US3] Переименовать `model/SettingsDTO.kt`→`SongDTO.kt`, `model/SettingsDTOdigest.kt`→`SongDTOdigest.kt` (**+ `webvue3` ссылки `SettingsDTO`/`SettingsDTOdigest`**)
- [X] T042 [P] [US3] Переименовать `model/SettingField.kt`→`SongField.kt` (14 файлов затронуто, шире изначальной оценки)
- [X] T043 [P] [US3] Переименовать `model/SettingVoice.kt`→`SongVoice.kt` (**⚠️ находка не из первоначального плана: обнаружена ЦЕЛАЯ семья файлов `SettingVoiceLine.kt`/`SettingVoiceLineElementTypes.kt`/`SettingVoiceLineElementSyllable.kt`/`SettingVoiceLineElement.kt` (не только 2), и — критично — переименование `SettingVoice`→`SongVoice` столкнулось с уже существующими классами `SongVoice`/`SongVoiceLine`/`SongVoiceLineType`/`SongVoiceLineSymbol` внутри `SongRenderContext.kt` (бывший рендер-класс `Song`, тот самый, что и внешняя коллизия имён из спека) — та же коллизия, но на уровень глубже, не описанная в spec.md/research.md. Разрешено переименованием СТАРОЙ (рендер) семьи в `SongRenderVoice`/`SongRenderVoiceLine`/`SongRenderVoiceLineType`/`SongRenderVoiceLineSymbol` (+ правки `MltProp.kt`, `MkoSongText.kt`) — по аналогии с `Song→SongRenderContext`. `karaoke-app:compileKotlin` чисто.**)
- [X] T044 [P] [US3] Переименовать `model/CrossSettings.kt`→`CrossSong.kt` (**+ поле `settingsDTO`→`songDTO` (Kotlin + webvue3 JSON-контракт синхронно: `Publish/store.js`, `PublishTableBody.vue`, `SongEdit.vue`)**)
- [ ] T045 [US3] Переименовать `SettingsSyncTarget`→`SongSyncTarget` в `karaoke-app/.../sync/SyncTarget.kt` (строковый `key = "settings"` НЕ менять, см. `research.md` §5) (зависит от T040)
- [X] T046 [US3] Обновить оставшиеся ссылки на `Settings`/`SettingsDTO` в `karaoke-app` (**выполнено одним широким `\bSettings\b`→`Song` sed по всем 59 затронутым файлам сразу — компиляция чистая с первого раза, кроме описанной в T043 коллизии SongVoice**)
- [X] T047 [US3] Переименовать `SettingsPublicDto.kt`→`SongPublicDto.kt` (**побочный эффект: `ZakromaAlbumSettingsPublicDto`→`ZakromaAlbumSongPublicDto` — совпадение подстроки, семантически корректно**)
- [X] T048 [US3] webvue3: `SettingsDTO`→`SongDTO`, экшены `updateOneRemoteSettingsPromise`/`updateRemoteSettingsPromise`/`updateLocalSettingsPromise`→`updateOneRemoteSongPromise`/`updateRemoteSongPromise`/`updateLocalSongPromise`, URL→`/api/utils/updateremotesongfromlocaldatabase`, параметр `updateSettings`→`updateSongs`
- [X] T049 [P] [US3] `Utils.kt`: `updateRemoteSettingFromLocalDatabase`→`updateRemoteSongFromLocalDatabase`, параметр `updateSettings`→`updateSongs` везде (**литерал `"settings"` в `legacySyncKeys`/`keys=setOf(...)`/`idFilter` НЕ тронут — это тот же SyncRegistry-ключ, что и раньше**)
- [X] T050 [US3] Сквозная проверка `quickstart.md` Сценарий 5 (**частично — компиляция+сборка всех 4 модулей чистая, grep на `Settings`/`tbl_settings` чист; полная проверка через реальный UI всё ещё блокируется пересборкой `karaoke-app` пользователем.** ⚠️ **Два важных бага найдены и исправлены именно этой проверкой, не были бы пойманы одной лишь компиляцией:** (1) `webvue3/src/App.vue` — SSE-обработчик `case 'tbl_settings':` (×3, RECORD_CHANGE/ADD/DELETE) сверял имя таблицы из живых событий сервера — без правки на `'tbl_songs'` реалтайм-обновления списка песен в админке молча перестали бы работать; (2) бланкет-переименование задело **два несвязанных** класса, тоже содержащих слово "Settings", но не относящихся к сущности песни: `loadEditorSettings`/`saveEditorSettings` (пользовательские настройки редактора — шрифт/громкость/зум в `useKaraokeEditor.js`) и `_loadPersistedSettings`/`_savePersistedSettings` (персистентные настройки плеера в `KaraokePlayer.js`) — оба вовремя замечены по падению сборки/несовпадению экспорта и отменены точечно.)

**Checkpoint**: Все три user story работают независимо; переименование завершено без регрессий.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, соответствие Code Standards (Principle VI), подготовка к SERVER-раскатке.

- [X] T051 [P] Обновить `docs/features/dual-db-sync.md` — добавлены `Album`/`SongCoAuthor` как новые sync-сущности + заметка о переименовании `Settings→Song`/`tbl_settings→tbl_songs` и о причине НЕ менять `key="settings"` (FR-009)
- [X] T052 [P] Обновить `docs/features/mlt-generator.md` — `Settings`→`Song` по всему документу (FR-009)
- [ ] T053 [P] Перегенерировать `deploy/new_comp/sm-karaoke-system/dumps/karaoke_clear_dump.sql` под новые имена таблиц — **НЕ сделано** (требует pg_dump на актуальной схеме; не блокирует эту ветку/CI, влияет только на будущие "чистые установки")
- [X] T054 Прогнать линтеры/coverage — **все 7 CI-проверок зелёные**: ktlint, ESLint×2 (webvue3/karaoke-public), prettier×2, KDoc 96.7% (порог 50%), JSDoc 100% (порог 50%), lychee (docs links), per-feature doc structure. Найдены и исправлены 2 ktlint-нарушения (форматирование `AlbumBackfill.kt`, `.editorconfig`-исключение `backing-property-naming` для переименованного `SongVoiceLine.kt`) + 3 битые doc-ссылки на `model/Settings.kt` (`telegram-auto-publish.md`, `dictionaries.md`, `docs/api/README.md`)
- [x] T055 (частично) Валидация выполнена через прямые API-вызовы вместо ручного прохода по UI (недоступен браузер в этой сессии): бэкфилл (2162 группы/альбома, 19441 песня, идемпотентность подтверждена повторным запуском), Album CRUD, FR-008 (отклонение чужого альбома), US2 edge cases (главный автор отклонён, дубликат идемпотентен), `/api/sync/entities` (albums/songcoauthors зарегистрированы, `key=settings` сохранён), FR-007 (живое изменение sortOrder меняет порядок на `/api/public/zakroma`). Полный проход по `quickstart.md` через реальный UI/браузер — не выполнен.
- [ ] T056 ⚠️ Раскатка на SERVER (миграции 28→29→30 + деплой `karaoke-web`/`karaoke-app`) — **только по прямому согласию пользователя, отдельно на каждое действие** (ограничение конституции); выполнять строго в порядке `research.md` §5.1, начиная с переименования таблицы

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup; **блокирует US1 и US2** (обе добавляют FK на переименованную таблицу `tbl_songs`) — отклонение от стандартного шаблона, см. пояснение в начале Phase 2
- **US1 (Phase 3)**: зависит от Foundational; не зависит от US2/US3
- **US2 (Phase 4)**: зависит от Foundational; не зависит от US1/US3 (может выполняться параллельно с US1 — пересечение только по файлу `Settings.kt`, T016 vs T031, не логическая зависимость)
- **US3 (Phase 5)**: зависит только от Foundational (не от US1/US2 логически, но T040 переименовывает тот же `Settings.kt`, который T016/T031 уже могли изменить — рекомендуется выполнять US3 после US1+US2 либо явно координировать merge)
- **Polish (Phase 6)**: зависит от выбранного набора завершённых user story; T056 (SERVER) — после T055

### Parallel Opportunities

- T001/T002 (Setup) — параллельно
- Внутри Foundational: T006 может идти параллельно с T005 (разные файлы)
- После Foundational: US1 (Phase 3) и US2 (Phase 4) можно вести параллельно разными людьми (см. предупреждение выше про файл `Settings.kt`)
- Внутри US1: T011/T012 параллельно; T022/T023 (webvue3) параллельно с T026/T027 (karaoke-web/public), т.к. разные модули
- Внутри US2: T031 и последующие — в основном последовательны (один файл/модель за раз), T036 параллельно с T035 невозможен (зависимость), но T029 можно готовить параллельно с любой задачей US1
- Внутри US3: T041-T044 — параллельно (разные файлы), после T040

---

## Parallel Example: User Story 1

```bash
# После Foundational, одновременно:
Task: "Создать karaoke-app/.../model/AlbumType.kt (enum AlbumType)"
Task: "Создать karaoke-app/.../model/Album.kt (KaraokeDbTable-сущность)"

# Позже, параллельно (разные модули):
Task: "webvue3/src/components/Albums/store.js + AlbumsTable.vue"
Task: "karaoke-web PublicApiController.kt /zakroma sortOrder/albumType"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational, **обязательно** — включает риск-критичное
   переименование таблицы, см. runbook `research.md` §5.1)
2. Phase 3 (US1 — Album) → **СТОП, проверить независимо** (`quickstart.md` Сценарии 0-2, 4-albums)
3. Задеплоить/продемонстрировать MVP: администратор управляет альбомами, публичный сайт
   показывает заданный порядок

### Инкрементальная поставка

1. Setup + Foundational → фундамент готов (таблица переименована, приложение стабильно)
2. + US1 (Album) → проверить независимо → MVP
3. + US2 (соавторы) → проверить независимо
4. + US3 (полное переименование кода) → проверить независимо (регрессия = 0)
5. Polish → документация, линтеры, SERVER-раскатка (по согласию пользователя)

### Примечание про порядок US3

Хотя US3 имеет наименьший приоритет (P3), физическое переименование таблицы (Foundational)
разблокировано независимо от того, когда выполняется остальная часть US3 (переименование
Kotlin-классов). Технически T038-T049 можно начать сразу после Foundational, параллельно с
US1/US2 — единственное практическое соображение — потенциальный merge-конфликт на `Settings.kt`
(T016 из US1, T031... нет, T031 отдельный файл — конфликт только с T016). Решение о порядке —
за исполнителем; независимая тестируемость каждой истории не нарушается в любом случае.

---

## Notes

- [P] задачи = разные файлы, нет зависимости
- [Story] label связывает задачу с user story для трассируемости
- Коммитить после каждой задачи или логической группы (см. правила git в `CLAUDE.md`/`AGENTS.md`)
- Проверять чек-пойнт независимо перед переходом к следующей user story
- T007 и T056 требуют явного участия/согласия пользователя — не выполнять автономно
