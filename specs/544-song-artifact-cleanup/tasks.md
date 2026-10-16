---

description: "Task list for 544-song-artifact-cleanup"
---

# Tasks: Автоуборка артефактов песни

**Input**: Design documents from `/specs/544-song-artifact-cleanup/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/cleanup-api.md](contracts/cleanup-api.md),
[quickstart.md](quickstart.md)

**Issue**: OpenProject [#321](http://localhost:8080/work_packages/321) · **Ветка**: `544-song-artifact-cleanup`

**Tests**: включены (план § Testing: unit-тесты на чистые части — реестр классов, отчёт,
журнал, модель ожиданий). В CI тестов нет, приёмка — вручную по `quickstart.md`.
Тест-задачи следует писать **до** соответствующей реализации (падающий тест → код);
для тестов, целиком опирающихся на новый код (реестр, журнал, планировщик), это означает
«сначала тест на контракт/инвариант, затем реализация».

**Организация**: фазы по пользовательским историям (US1…US5 из spec.md).
Все строки файлов в описаниях сверены по репозиторию (`grep`/`sed` на ветке
`544-song-artifact-cleanup`, 2026-10-16); расхождения research/plan зафиксированы
в разделе «Примечания по фактам кода» в конце файла.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимости от незакрытых задач)
- **[Story]**: US1…US5 — привязка к пользовательской истории
- У каждой задачи — точный путь файла

## Path Conventions

Монорепозиторий Gradle; вся работа — в модуле `karaoke-app` (Constitution § «Технологический стек»).

- Код: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Новый подпакет разовой чистки: `.../services/cleanup/`
- Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/`
- Документация: `docs/features/`, `knowledge/domains/`, `specs/544-song-artifact-cleanup/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: зафиксировать исходное состояние гейтов, чтобы отличать свои регрессы от чужих.

- [x] T001 Зафиксировать зелёный baseline сборки и линтера на ветке (до правок): `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` и `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck`; вывод сохранить — он войдёт в `specs/544-song-artifact-cleanup/report.md`
- [x] T002 [P] Зафиксировать baseline гейтов документации: `bash tools/check-no-mp4-mentions.sh --quiet`, `python3 tools/lint-knowledge.py`, `bash tools/check-knowledge-structure.sh` — все три обязаны быть зелёными ДО правок

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: реестр классов, журнал и флаги — общий фундамент для автоматической уборки (US1, US2, US3, US5) и разовой чистки (US4). FR-011 требует, чтобы **каждое** автоматическое удаление было выключено по умолчанию и имело сухой прогон, а FR-012 — чтобы **каждое** удаление попадало в журнал; поэтому эти три части идут до всех историй.

**⚠️ CRITICAL**: ни одна пользовательская история не начинается до конца этой фазы.

- [x] T003 [P] Создать реестр классов `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupClass.kt`: перечисление 16 идентификаторов из `data-model.md` §1 (`SOURCE_FLAC`, `STEMS_FLAC`, `STEMS_MP3_LOCAL`, `DEMO_MP4`, `PUBLICATION_PNG`, `CHORDS_PNG`, `SONG_TXT`, `SHEETSAGE`, `DONE_PROJECTS`, `SRT`, `SONG_SIDECAR`, `KEY_JSON`, `HYGIENE_WAV`, `HYGIENE_JUNK`, `KEY_BPM_CSV`, `MANUAL_FILES`) с полями `verdict` (`PROTECTED`/`KEEP`/`STOP_CREATING`/`DELETE_AFTER_STAGE`/`DELETE_EXISTING`/`NOT_TOUCH`), `cleanupOrder`, ожидаемыми `expectedFileCount`/`expectedBytes` и KDoc
- [x] T004 [P] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/CleanupJournal.kt`: TSV-журнал `/sm-karaoke/system/cleanup-logs/<YYYY-MM-DD>-<classId>.tsv` с колонками `path`, `sizeBytes`, `classId`, `result` (`deleted`|`failed:<причина>`), `deletedAt`, `approvedBy`, `runId`; дописывание строки сразу после обработки файла (`data-model.md` §4, `contracts/cleanup-api.md` § «Формат журнала»)
- [x] T005 Добавить 4 параметра в `listKaraokeProperties` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (список начинается на строке 267): `cleanupEnabled=false`, `cleanupDryRun=true`, `cleanupJournalDir="/sm-karaoke/system/cleanup-logs"`, `cleanupPlanVolumeTolerancePercent=10` — по конвенции `knowledge/adr/local-0001-karaoke-properties-defaults.md` (`KaraokeProperty(key=…, defaultValue=…, description=…)`)
- [x] T006 [P] Тест реестра `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupRegistryTest.kt`: `SOURCE_FLAC`, `STEMS_FLAC` — `PROTECTED`; `KEY_BPM_CSV`, `MANUAL_FILES` — `NOT_TOUCH`; `cleanupOrder` классов `DELETE_EXISTING` строго возрастает (1→7); инвариант 1 из `data-model.md` §2
- [x] T007 [P] Тест журнала `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/CleanupJournalTest.kt`: заголовок TSV, строка `deleted`, строка `failed:<причина>`, дописывание в существующий файл, `approvedBy`/`runId` в каждой строке (FR-012)

**Checkpoint**: фундамент готов — истории можно вести параллельно.

---

## Phase 3: User Story 1 — Демо-ролики не копятся на диске (Priority: P1) 🎯 MVP

**Goal**: демо-ролик удаляется в конце шага публикации (при успехе и при провале), публикационные картинки — после успешной публикации; при повторной публикации система рендерит демо заново без ручных действий.

**Independent Test**: публикация тестовой песни → файла `* [demo].mp4` на диске нет, публикация состоялась; повторная публикация рендерит демо заново (quickstart.md сценарий 1).

- [x] T008 [US1] Удалять демо-ролик в `KaraokeApp/.../services/PremiumAutoPublishScheduler.kt` внутри `closeIfBothChannelsDone(song: Song)` (объявление на строке 303): после того как закрылись оба канала, удалить `song.pathToFileRenderMp4ForVersion(RenderVersion.DEMO)`, записать удаление в `CleanupJournal` (T004), подчиниться `cleanupEnabled`/`cleanupDryRun` (T005). Удаления внутри `TelegramAutoPublishService`/`VkAutoPublishService` НЕ добавлять (research.md R1)
- [x] T009 [US1] Проверить и при необходимости восстановить перерендер отсутствующего демо (FR-002): точки «нужен готовый файл» — `.../services/TelegramAutoPublishService.kt:114,201` и `.../services/VkAutoPublishService.kt:126,191`; сам рендер — `.../services/PlayerMp4RenderService.kt`; правку вносить только если после T008 путь «файла нет → рендер → публикация» не покрыт
- [x] T010 [P] [US1] Тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/DemoMp4CleanupTest.kt`: демо удаляется и при успехе, и при ошибке публикации; запись в журнал есть в обоих случаях (FR-001, FR-012)
- [x] T011 [P] [US1] Тест там же: при `cleanupEnabled=false` файл остаётся, при `cleanupDryRun=true` — тоже (FR-011); SC-001 проверяется только с `cleanupEnabled=true` + `cleanupDryRun=false` (см. «Примечания по фактам кода» §4)
- [x] T012 [US1] Удалять публикационные картинки после **успешной** публикации (FR-005): та же точка завершения шага публикации, что у демо (T008) — `services/PremiumAutoPublishScheduler.kt`; писатели — `UtilsPictures.kt:559` (`createSongPicture`), `:937` (`createSponsrTeaserPicture`), вызовы в `Functions.kt:486,494`, `controllers/ApiController.kt:4339,4365,4391,4417`, `model/Song.kt:6635,6643,6651,6659`; удаляются классы `PUBLICATION_PNG` и `CHORDS_PNG` из реестра (T003) с журналированием (T004). В отличие от демо — удаление только при успехе. Учесть факт: `done_chords/*.png` (`PICTURECHORDS`) производится `createSongChordsPicture` (`UtilsPictures.kt:841`), у которого в репозитории остался только закомментированный вызов `Functions.kt:498` — для этого класса автоматического удаления нет, работает только разовая чистка (`DELETE_EXISTING`). Проверить, что картинки остаются воссоздаваемыми из данных песни (`canCreate` сохраняется)

**Checkpoint**: US1 работает сама по себе; MVP достигнут.

---

## Phase 4: User Story 2 — Локальные mp3-стемы не занимают место дважды (Priority: P1)

**Goal**: локальные mp3 удаляются после успешной загрузки в **оба** MinIO, и проверка здоровья больше не требует локальной копии и не ставит `FF_MP3_*`-задачи.

**Independent Test**: шаг конвертации/загрузки тестовой песни → локальных mp3 нет, объекты в обоих хранилищах на месте, в отчёте здоровья нет `FILE_VIOLATION` по mp3 (quickstart.md сценарий 2).

**⚠️ ПОРЯДОК ОБЯЗАТЕЛЕН (FR-013, US4 сценарий 3)**: T013+T014 — до T016/T017; чистка класса `STEMS_MP3_LOCAL` (T030) — после T016.

- [x] T013 [P] [US2] МОДЕЛЬ ОЖИДАЕМЫХ ФАЙЛОВ — ПЕРВОЙ: в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeFileType.kt` убрать `KaraokeFileTypeLocations.LOCAL_FILESYSTEM` из `MP3_ACCOMPANIMENT` (строка 65), `MP3_VOCAL` (88), `MP3_BASS` (111), `MP3_DRUMS` (134), `MP3_OTHER` (157), оставив `LOCAL_STORAGE` + `REMOTE_STORAGE` (research.md R2, FR-004)
- [x] T014 [P] [US2] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` в ветках `MP3_ACCOMPANIMENT` (1725), `MP3_VOCAL` (1829), `MP3_BASS` (1933), `MP3_DRUMS` (2037), `MP3_OTHER` (2141) перестать использовать `existsInLocalFileSystem` как критерий нарушения; блок `canCreate` → `FF_MP3_*` (например, 1740-1748) сохранить как ручной/восстановительный путь, автоматически он больше не запускается
- [x] T015 [P] [US2] Тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportMp3LocationsTest.kt`: у `MP3_*` в `locations` нет `LOCAL_FILESYSTEM`; при отсутствующем локальном mp3 отчёт не содержит `FILE_VIOLATION` по этому типу и не порождает `FF_MP3_*` (research.md R2, SC-006)
- [x] T016 [US2] Удалять локальную копию mp3 только при успехе **обеих** загрузок: в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` — `executeUploadToLocalStore` (объявление 4328, удаление 4370) и `executeUploadToRemoteStore` (4387, ветки 4416/4435); контекст загрузки с диска в хранилище задаётся в `HealthReport.kt` (локальное хранилище — 798-818, удалённое — 1152-1172), где сегодня `deleteAfterUpload` НЕ выставляется, в отличие от временных файлов (932, 1287). Нужно условие «все целевые загрузки успешны» (research.md R3, FR-003)
- [x] T017 [P] [US2] Тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/StemsMp3LocalDeleteTest.kt`: полный успех → локальная копия удалена; частичный успех (удалённая загрузка не удалась) → копия остаётся (FR-003)

**Checkpoint**: US1 и US2 работают; класс `STEMS_MP3_LOCAL` разблокирован для разовой чистки.

---

## Phase 5: User Story 3 — Мёртвые артефакты больше не создаются (Priority: P2)

**Goal**: не пишутся `.song`/`.settings`, `.srt`/`.srt.backup`, файлы проектов рендера; признак аккордов считается по БД.

**Independent Test**: импорт новой песни + разметка → перечисленных файлов нет, признак аккордов истинен (quickstart.md сценарий 3).

- [x] T018 [US3] Убрать писателей sidecar-состояния в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`: объявление `fun saveToFile()` (5970) и вызовы `Song.kt:5277`, `controllers/ApiController.kt:2987`, `controllers/MainController.kt:1838`, `:2027`; мёртвый `fun loadFromFile(...)` (8361, вызовов в репозитории нет); осиротевшие пути `fileSongAbsolutePath` (1804), `fileSongAbsolutePathLegacy` (1807), `fileSongOrLegacySettingsAbsolutePath` (1814) и их использование в `saveToDb` (5943), переименовании (6030, 6608-6616); упростить `renameCascadeExtraArtifacts` (6007) (research.md R5, FR-006)
- [x] T019 [US3] Вырезать чтение `.srt` и запись `.srt.backup` из `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongRenderContext.kt` (строки 101-147). `Song.convertMarkersToSrt(voice: Int)` (`model/Song.kt:5147`) СОХРАНИТЬ как чистую функцию экспорта — FR-008 требует доступности разметки для экспорта по запросу
- [x] T020 [US3] Удалить 7 мест записи `.srt` (все — `File(pathToFile).writeText(srt)`): `controllers/ApiController.kt:3338` и `:3398`, `controllers/MainController.kt:696`, `controllers/SongEditorController.kt:390`, `SyncAudioDescendants.kt:406`, `KaraokeProcessWorker.kt:1329`, `Utils.kt:5190` (внутри `autoAssignOriginalByWaveform`) (FR-008)
- [x] T021 [US3] Считать `hasChords` по маркерам из БД: заменить `SongRenderContext(song, SongVersion.LYRICS).hasChords` в `controllers/MainController.kt:508`, `:530`, `:564` на вычисление по `sourceMarkers`/`getSourceMarkers` (research.md R4, FR-007)
- [x] T022 [US3] (ЗАКРЫТА в суженном объёме по слову владельца 2026-10-16: `.kdenlive`/`.run` — сделано; `.mlt` оставлен по FR-009, см. примечание §11) Убрать писателей мёртвых проектов рендера: `fun createKdenliveFiles(...)` (`model/Song.kt:6090-6102`), запись `*.run` в `Song.createKaraoke(...)` (`model/Song.kt:6119-6157`), запись `.kdenlive`/`.mlt`/`.run` в `Functions.kt:450-477`; мёртвое имя `nameFileRenderMp4` (`model/Song.kt:555`, строка `"$fileName [render].mp4"`); в `model/SongOutputFile.kt` удалить осиротевшие члены (`PROJECT`, `SUBTITLE`, `RUN`, `MLT` — только те, у которых после правки не осталось читателей) (research.md R5, FR-009)
- [x] T023 [P] [US3] Тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/HasChordsFromDbTest.kt`: `hasChords` истинен по маркерам типа `CHORDS` и ложен без них — без обращения к файлам `.srt` (FR-007)
- [x] T024 [P] [US3] Тест `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/RenameWithoutSidecarTest.kt`: переименование песни при отсутствии `.song`/`.settings`/`.srt` не падает и не создаёт файлы заново (edge case spec.md, FR-006)
- [x] T025 [US3] Компиляционный гейт после удаления писателей: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin` — убрать осиротевшие ссылки/импорты (`createKdenliveFiles`, `saveToFile`, `loadFromFile` и пр.)

**Checkpoint**: новые песни больше не порождают мёртвые артефакты.

---

## Phase 6: User Story 4 — Разовая чистка истории освобождает место (Priority: P2)

**Goal**: отчёт сухого прогона по классу → утверждение владельцем → удаление с журналом; классы по порядку «крупные и низкорисковые первыми».

**Independent Test**: сухой прогон на классе `DEMO_MP4`, сверка объёма, утверждение, удаление на тестовом подмножестве, проверка журнала (quickstart.md сценарий 4).

- [x] T026 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupPlanner.kt`: обход каталогов по классу из реестра, отчёт (`classId`, `generatedAt`, `entries`, `fileCount`, `totalBytes`, `fingerprint`, `warnings`) и проверка объёма против `expectedBytes` с допуском `cleanupPlanVolumeTolerancePercent`; **ни одного вызова удаления** (FR-011, `data-model.md` §3)
- [x] T027 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupService.kt`: применение класса вне HTTP-потока (Constitution IV), состояния `PLANNED → APPROVED → RUNNING → DONE|PARTIAL|FAILED`, журнал на каждый обработанный путь (включая `failed:`), отказ для `PROTECTED`/`NOT_TOUCH` **всегда**, отказ для класса `STEMS_MP3_LOCAL`, если правка модели здоровья (T013+T014) не выполнена (FR-004/FR-013, `data-model.md` §5)
- [x] T028 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/CleanupController.kt` c `@RequestMapping("/api/cleanup")`: `GET /classes`, `POST /plan`, `GET /plan/{planId}/entries.csv`, `POST /apply` (ответ 202 + `runId`), `GET /status`; коды ошибок 400/404/409/503 — строго по `contracts/cleanup-api.md`; публичные методы с KDoc (Constitution VI). Эндпоинты доступны напрямую на бэкенде `:8898` (через nginx `:7906` не проксируются — AGENTS.md Pass 381)
- [x] T029 [P] [US4] Тесты предохранителей `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupGuardTest.kt`: `plan` на `SOURCE_FLAC`/`STEMS_FLAC` → 400 и ни одного обращения к удалению; `apply` без отпечатка → 400, с чужим отпечатком → 409, ничего не удалено; `plan` не удаляет ни одного файла (число файлов в каталоге до/после); журнал содержит `failed:`; `apply` для `STEMS_MP3_LOCAL` до T016 → 409 (таблица «Тестовые обязательства контракта»). Дополнительно — FR-014: чистка не вызывает действий `HealthReport` и не порождает задач авторемонта (удаление идёт только через `ArtifactCleanupService`); FR-016: загрузки в локальное и удалённое хранилище симметричны, и в MinIO не появляется путь загрузки для `.srt`, `.song`/`.settings` и видео
- [ ] T030 [US4] Разовый прогон по классам в порядке реестра (1→7): `plan` → утверждение владельцем → `apply`; **оператор — владелец** (у агента нет прав записи на `/sm-karaoke`), агент готовит прогон и проверяет отчёты/журнал; класс `STEMS_MP3_LOCAL` — строго после T016. По `quickstart.md` сценарий 4

**Checkpoint**: место на дисках возвращено, каждое удаление с утвердившим в журнале.

---

## Phase 7: User Story 5 — Гигиена и честная политика хранилищ (Priority: P3)

**Goal**: мелкий мусор убирается, проверка здоровья перестаёт быть слепой к каталогам ссылок, документация совпадает с кодом.

**Independent Test**: чистка мусора → проверка здоровья замечает битые ссылки и посторонние файлы в `symlink_*` (quickstart.md сценарий 5).

- [x] T031 [US5] Расширить покрытие проверки здоровья на каталоги ссылок целиком в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`: обнаруживать ссылки без цели и посторонние файлы в `symlink_mp4`/`symlink_png`/`symlink_sponsr`. Учесть факт: `PICTURE_PUBLICATION`, `PICTURE_SONGVERSION`, `VIDEO_SONGVERSION_1080P/720P`, `PROJECT_*` входят в `LEGACY_MLT_FILE_TYPES` (1363-1375) и пропускаются в цикле `KaraokeFileType.entries` (1504-1505) — поэтому нужен отдельный обход каталогов (пути: `model/Song.kt:1777-1779`), а не правка существующих веток; нынешнее покрытие — только при существующем файле (395-435), ветка удаления ссылки без цели закомментирована (621-639) (research.md R7, FR-015)
- [x] T032 [P] [US5] Мутация Knowledge (research.md R8, FR-017): `knowledge/domains/catalog/components/song-lifecycle.md` (строка 26 — «MP4 готов, лежит в MinIO»; 74-82 — шаг 5), `knowledge/domains/rendering/components/mlt-pipeline.md` (63, 86 — «MP4 → MinIO»), `knowledge/domains/rendering/domain.md` (28), `knowledge/domains/health/domain.md` (35 — что именно аудирует HealthReport), `knowledge/domains/storage/domain.md` (политика MinIO: mp3-стемы и картинки — источник истины; видео/исходники/субтитры/состояние не загружаются; мини-словарь buckets; отсутствие автоочистки)
- [x] T033 [P] [US5] Мутация Knowledge по обязательству SSoT-гейта: `knowledge/domains/catalog/components/dictionaries.md` — правило `.ssot-map.yml:41-43` требует его изменения при правке `model/Song.kt`; при правке `services/PlayerMp4RenderService.kt` (T009) — `knowledge/domains/processing/components/playwright-rendering.md`
- [x] T034 [P] [US5] Создать `docs/features/song-artifact-cleanup.md` (FR-009) с 6 обязательными секциями (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`, `## Известные ловушки`, `## Ссылки`), slug == имени файла, `status: active`, ссылка на `specs/544-song-artifact-cleanup/spec.md`; проверить `bash tools/check-feature-doc.sh docs/features/song-artifact-cleanup.md`
- [x] T035 [US5] Довести классы гигиены в реестре (T003) до рабочего состояния: `HYGIENE_WAV` (осиротевшие `hdemucs_mmi/*.wav`), `HYGIENE_JUNK` (битые симлинки, посторонние файлы в `symlink_*`, `*.kdenlive_back*`, `result_key_bpm_finder.json`, мёртвое имя `[render].mp4` — `model/Song.kt:555`), `KEY_BPM_CSV` и `MANUAL_FILES` — `NOT_TOUCH`; удаление — через `ArtifactCleanupService` (T027)

**Checkpoint**: мусор не возвращается, документация соответствует коду.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: обязательные гейты и артефакты трекера.

- [x] T036 Обязательные проверки после изменения (AGENTS.md Pass 239+245, `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`): `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`; `./gradlew :karaoke-web:ktlintCheck`; `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel`. Фронтенды (`webvue3/`, `karaoke-public/`) в этой поставке не меняются — `npm run lint`/`build` для них не запускаются (зафиксировать факт в `report.md`)
- [x] T037 [P] Прогнать гейты документации и SSoT: `bash tools/check-no-mp4-mentions.sh --quiet` (R-11; новые строки с `mp4` в `karaoke-app/src/**/*.kt` вне тестов не должны появиться — baseline НЕ перегенерировать без слова владельца), `python3 tools/check-ssot-impact.py`, `python3 tools/lint-knowledge.py`, `bash tools/check-knowledge-structure.sh`, `bash tools/check-feature-doc.sh docs/features/*.md`
- [x] T038 [P] Добавить запись о поставке в `docs/architecture-notes.md` (Constitution VI/VII)
- [x] T039 [P] Создать черновик `specs/544-song-artifact-cleanup/report.md` — REQUIRED-артефакт трекера (#321): что сделано, факты с диска (объёмы до/после), результаты гейтов, что осталось за владельцем (разовый прогон, перезапуск `karaoke-app`); это же — источник для `tools/tracker.sh add-comment 321 --file …`
- [ ] T040 Приёмка по `quickstart.md` и замер эффекта: `df -h` до/после, сверка SC-001…SC-009 из `spec.md`, дозапись результатов в `report.md`; передать лиду на ревью

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: после Setup — БЛОКИРУЕТ все истории (реестр/журнал/флаги нужны всем удалениям)
- **US1 (Phase 3, P1)** и **US2 (Phase 4, P1)**: после Foundational, независимы друг от друга
- **US3 (Phase 5, P2)**: после Foundational; не зависит от US1/US2
- **US4 (Phase 6, P2)**: после Foundational; `apply` класса `STEMS_MP3_LOCAL` (T030) — строго после T016
- **US5 (Phase 7, P3)**: после Foundational; T035 опирается на реестр (T003) и сервис (T027)
- **Polish (Phase 8)**: после всех выбранных историй

### Внутри историй

- US1: T008, T009, T012 — реализация; T010 и T011 параллельно. T012 (картинки) опирается на ту же точку завершения публикации, что T008 — при правке одного файла `PremiumAutoPublishScheduler.kt` порядок T008 → T012
- US2: **T013+T014 (модель ожиданий) → T016 (удаление локального mp3) → T017** — порядок обязателен (FR-004/FR-013)
- US3: T019/T020 → T025 (компиляция после удаления писателей)
- US4: T003 → T026 → T027 → T028 → T029; T030 — после T016 и T029

### Parallel Opportunities

- Foundational: T003, T004, T006, T007 — параллельно (T005 — отдельный файл, тоже может идти параллельно)
- US1: T010 и T011 параллельно; T012 — после T008 (тот же файл)
- US2: T013 и T014 параллельно; T015 — параллельно T016/T017
- US3: T018, T022 (разные файлы) — параллельно; T023, T024 параллельно
- US5: T032, T033, T034 + T031 — параллельно по файлам
- Polish: T037, T038, T039 параллельно

```bash
# Пример: параллельный запуск тестов историй
Task: "T010 [US1] DemoMp4CleanupTest.kt"
Task: "T011 [US1] cleanuEnabled=false тест"
Task: "T015 [US2] HealthReportMp3LocationsTest.kt"
Task: "T023 [US3] HasChordsFromDbTest.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP и VALIDATE**: SC-001/SC-007 по `quickstart.md` сценарий 1 (с `cleanupEnabled=true`)
3. Затем US2 (SC-002/SC-006) — вторая P1-история, тоже самостоятельная

### Incremental Delivery

US1 (демо) → US2 (mp3, самая крупная экономия 445 ГБ) → US4 (возврат накопленного) →
US3 (прекращение производства мёртвого) → US5 (гигиена + документация).

Порядок экономии: US2 + US4 дают основную часть SC-003 (≥600 ГБ), US3 предотвращает
возврат мусора, US5 закрывает хвост.

---

## Примечания по фактам кода

Расхождения, найденные при сверке `research.md`/`plan.md`/`data-model.md`/`quickstart.md`
с репозиторием (2026-10-16). Спека (`spec.md`) не правилась — она контракт.

1. **`Song.saveToFile` — объявление на `model/Song.kt:5970`**, а не 5277: `5277` — это
   вызов `newSett.saveToFile()` внутри `saveToDb`. В T018 перечислены и объявление, и все
   вызовы (`ApiController.kt:2987`, `MainController.kt:1838,2027`, `Song.kt:5277`).
2. **Веток `PICTURE_*` в `HealthReport` нет.** `PICTURE_PUBLICATION` и `PICTURE_SONGVERSION`
   входят в `LEGACY_MLT_FILE_TYPES` (`HealthReport.kt:1363-1375`) и пропускаются циклом
   (`:1504-1505`), поэтому фраза `plan.md` «ветки MP3_*, PICTURE_*» и оговорка
   `data-model.md` §2 «снять ожидание в ветках `HealthReport` (см. FR-005)» описывают уже
   выполненное. Реальный объём правки `HealthReport.kt`: ветки `MP3_*` (T014) + новый
   обход каталогов `symlink_*` (T031).
3. **`quickstart.md` § Предусловия, шаг 3**: `curl http://localhost:8898/api/properties`
   такого эндпоинта не существует; реальные — `POST /api/properties/getproperties`
   (`controllers/ApiController.kt:6082`) и `POST /api/properties/getproperty` (`:6104`).
4. **SC-001/SC-002 предполагают включённую уборку.** FR-011 требует «выключено по
   умолчанию», а edge case spec.md — «поведение идентично сегодняшнему» при выключенном
   флаге. Значит, сценарий 1 `quickstart.md` (как и сценарий 2) должен явно требовать
   `cleanupEnabled=true`, `cleanupDryRun=false`, иначе демо-файл останется по замыслу.
5. **Гейт R-11 (`tools/check-no-mp4-mentions.sh`)**: сканирует `karaoke-app/src/**/*.kt`
   (кроме тестов) регэкспом `(\bmp4\b|скачивани)` с `-i`. Новые строки с `mp4`/`MP4` вне
   baseline валят гейт. Идентификатор `DEMO_MP4` безопасен (перед `MP4` — `_`, границы
   слова нет), а литерал `".mp4"` и комментарий вида «демо-ролик mp4» — нет. Правку
   baseline вправе решать только владелец (T037).
6. **SSoT-обязательство шире списка R8**: `.ssot-map.yml:41-43` требует изменения
   `knowledge/domains/catalog/components/dictionaries.md` при правке `model/Song.kt`
   (US3, T018) — в R8 файла нет, добавлен задачей T033; при правке
   `services/PlayerMp4RenderService.kt` (T009) — `playwright-rendering.md`.
7. **`deleteAfterUpload` сегодня = временный файл.** Флаг выставляется только на загрузку
   скачанного во временный путь файла (`HealthReport.kt:932` — локальное хранилище,
   `:1287` — удалённое). Загрузка реального локального mp3 (`HealthReport.kt:798-818` и
   `1152-1172`) флаг не выставляет — именно там появляется условие R3 (T016).
8. **Прецедент юнит-тестов**: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KaraokeProcessCleanupTest.kt`
   (спека 310) — JUnit 5 + `@TempDir`, без `@Disabled`; шаблон для тестов T006/T007/T010/T011/T017/T023/T024/T029.
9. **FR-005 не имел своей пользовательской истории.** Удаление публикационных картинок
   после публикации (FR-005) не принадлежит ни US1 (там только демо), ни US3 (там только
   «не создавать»), ни US4 (там только разовая чистка накопленного). Закрыто задачей T012
   в фазе US1 — это та же точка жизненного цикла («конец шага публикации»), что и у демо.
   `spec.md` не менялся: FR-005 уже содержит требование, добавлена только его задача.
   Родственный пробел — FR-016 (симметрия загрузок, запрет загрузки видео/исходников/
   субтитров/состояния в MinIO) не имеет отдельной кодовой задачи: закрыт документацией
   (T032) и проверками в T029.
10. **`CHORDS_PNG` недостижим как «удаление после этапа».** `data-model.md` §1 даёт классу
   `CHORDS_PNG` вердикт `DELETE_AFTER_STAGE`, но его писатель `createSongChordsPicture`
   (`UtilsPictures.kt:841`) вызывается только из закомментированной строки
   `Functions.kt:498` — то есть `done_chords/*.png` сегодня не производятся, и
   автоматического удаления для них нет. Для класса работает только разовая чистка
   (`DELETE_EXISTING`), что и зафиксировано в T012.
11. **T022 остановлена на `.mlt`: FR-009 конфликтует с живым кодом.** `Functions.createKaraoke`
   (`Functions.kt:450-477`) пишет `.mlt`, а его читает замороженный, но ЖИВОЙ melt-конвейер:
   тип процесса `MELT_LYRICS`/`MELT_KARAOKE`/`MELT_CHORDS` создаётся из `MainController.kt:470,489,511,534`
   и из `KaraokeProcessWorker.applyRenderSideEffect` (который сам вызывает
   `Song.createKaraoke` → `Functions.createKaraoke`), а аргументы melt ссылаются на
   `done_projects/*.mlt` (`KaraokeProcess.kt:1135-1147`). Прекратить писать `.mlt`, не
   выведя из эксплуатации MELT-процессы, значит поставить их в заведомо падающее состояние
   (SC-008 «ноль регрессий»). Выполнено безопасное подмножество T022: удалены писатели
   `*.run` (читателей нет) и `Song.createKdenliveFiles` вместе с эндпоинтом
   `/song/{id}/createkdenlivefiles` (вызовов во фронтендах нет).

   **РЕШЕНО владельцем 2026-10-16 (вариант A): FR-009 сужен до `.kdenlive`/`.run`.**
   `.mlt` остаётся и продолжает создаваться — его читает живой melt-конвейер; вывод
   MELT-процессов из эксплуатации — отдельная работа, в эту поставку не входит.
   `spec.md` FR-009 приведён в соответствие; класс `MLT_PROJECTS` в реестре получил
   вердикт `KEEP` (`data-model.md` §1). T022 закрыта в суженном объёме.
