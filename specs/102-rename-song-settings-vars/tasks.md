---
description: "Task list for feature 102-rename-song-settings-vars"
---

# Tasks: Переименование параметров/переменных типа Song с имени `settings` на `song`

**Input**: Design documents from `/specs/102-rename-song-settings-vars/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/rename-contracts.md, quickstart.md

**Tests**: Не запрошены явно (spec.md не требует TDD; проект не полагается на
автотесты как gate — см. constitution.md «Рабочий процесс»). Верификация —
компиляция/линт после каждой группы задач + ручные сценарии `quickstart.md`.

**Delivery note (FR-016)**: Несмотря на разбивку по user story ниже, весь
результат мержится и деплоится **одним PR** (явное решение пользователя,
`spec.md` → Clarifications, вопрос 4). Разбивка по фазам — это порядок
работы и внутренние чекпоинты, а не отдельные релизы.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1 / US2 / US3 — соответствие user story в `spec.md`
- Пути указаны от корня репозитория

---

## Phase 1: Setup

**Purpose**: Зафиксировать «чистый» baseline до начала переименования

- [X] T001 Убедиться, что перед началом работы backend и frontend собираются
      и линтуются без ошибок: `./gradlew ktlintCheck`,
      `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`,
      `cd webvue3 && npm run lint:check && cd ..` — если что-то уже красное
      до этой задачи, остановиться и разобраться отдельно (это не относится
      к рефакторингу).
- [X] T002 [P] Прогнать grep-команды из `quickstart.md` (Сценарий 7) на
      текущем `HEAD` и зафиксировать baseline-числа (54 сигнатуры
      `settings: Song` в 14 файлах, 227 `val`/`var settings` в 30 файлах,
      54 `@RequestParam settings_xxx` в `MainController.kt` по 27 в двух
      методах — счётчики по файлам из `data-model.md`) — они понадобятся для
      сравнения «до/после» на финальной проверке (T040).

**Checkpoint**: Известно, что перед рефакторингом всё зелёное и посчитан baseline.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Подтвердить границы исключений (Категория 6, `data-model.md`)
прямо перед началом правок — если что-то в репозитории изменилось со времени
`/speckit-plan`, лучше узнать сейчас, а не после переименования 35 файлов.

**⚠️ CRITICAL**: Не начинать Phase 3/4, пока эта проверка не пройдена.

- [X] T003 Grep-подтвердить, что границы исключений из `data-model.md`
      (Категория 6) всё ещё верны на текущем `HEAD`:
      `KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber`
      (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt`),
      `LS_SETTINGS_KEY` в `karaoke-public/src/player/KaraokePlayer.js`,
      колонка `settings_id` (`@KaraokeDbTableField(name = "settings_id")` в
      `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`,
      `deploy/karaoke-db/28_rename_settings_to_songs.sql`),
      `SyncTarget.key = "settings"`
      (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt:221`).

**Checkpoint**: Границы задачи подтверждены — можно приступать к User Story 1.

---

## Phase 3: User Story 1 - Понятное имя параметра при чтении кода (Priority: P1) 🎯 MVP-срез

**Goal**: Все параметры/локальные переменные/поля с типом (или производным
значением) `Song`, не пересекающие границу backend↔frontend, переименованы
`settings*` → `song*` (FR-001, FR-002, FR-003, FR-013).

**Independent Test**: Открыть любой из файлов ниже — параметры и локальные
переменные типа `Song` названы `song` (или явным производным именем при
конфликте), внутренние `settingsId`/`settingsList`-подобные переменные —
`songId`/`songList`; `./gradlew build` и `ktlintCheck` проходят.

### Implementation for User Story 1

- [X] T004 [P] [US1] Переименовать `settings: Song` → `song` (12 сигнатур + тела функций) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt`
- [X] T005 [P] [US1] Переименовать `settings: Song` → `song` (5 сигнатур) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`
- [X] T006 [P] [US1] Переименовать `settings: Song` → `song` (2 сигнатуры) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Functions.kt`
- [X] T007 [P] [US1] Переименовать `settings: Song` → `song` (2 сигнатуры) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt`
- [X] T008 [P] [US1] Переименовать `settings: Song` → `song` (1 сигнатура) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatformPublication.kt`
- [X] T009 [P] [US1] Переименовать `settings: Song` → `song` (1 сигнатура, ~строка 188) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt` — **НЕ трогать** `settingsFieldPublicationId`/`settingsFieldVersionNumber` (Категория 6, `data-model.md`)
- [X] T010 [P] [US1] Переименовать локальную `settings: Song` → `song` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/Mlt.kt`
- [X] T011 [P] [US1] Переименовать по одному вхождению `settings: Song` → `song` в каждом из 13 файлов `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/{MkoChordPictureElement,MkoChordPictureFader,MkoChordPictureImage,MkoChordPictureLines,MkoChords,MkoElement,MkoFill,MkoLines,MkoLineTrack,MkoMelodyNote,MkoMelodyTabs,MkoSepar,MkoString}.kt`
- [X] T012 [US1] Переименовать `settings: Song` → `song` (1 сигнатура + 4 локальных) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`; grep-проверить и обновить именованные вызовы вида `(settings = ...)` этих компаньон-методов по всему репозиторию
- [X] T013 [US1] Переименовать конструкторское свойство `val settings: Song` → `song` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongRenderContext.kt`; найти и обновить все места создания `SongRenderContext(settings = ...)` по всему репозиторию (именованный аргумент → `song = ...`)
- [X] T014 [US1] Переименовать конструкторское свойство `val settings: Song` → `song` (+ 9 сигнатур функций + 2 локальных) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt`; найти и обновить все места `HealthReport(settings = ...)` по репозиторию. **НЕ трогать** здесь `settingsId`/`settingsFileName` (это `HealthReportDTO`, US2/T025) и не менять `docs/features/premium-stems.md` (Polish/T041)
- [X] T015 [US1] Переименовать поле `var settingsId: Int` → `songId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`, включая все внутренние обращения (конструктор, SQL bind по индексу, логирование); строковый аргумент `@KaraokeDbTableField(name = "settings_id")` и литерал `"settings_id"` в SQL/логике оставить БЕЗ изменений (Категория 6)
- [X] T016 [US1] Обновить все внешние обращения к `KaraokeProcess.settingsId` → `.songId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobPollScheduler.kt`, `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/StemJobProcessing.kt` и локальные переменные `settingsId`/`params["settingsId"]` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (область `executeRenderMp4` и аналогичные, ~строки 3694-4006) → `songId` (зависит от T015; это внутренние переменные, не HTTP-параметры — убедиться в этом перед переименованием, см. `research.md`)
- [X] T017 [P] [US1] Переименовать `settingsByAuthor` → `songsByAuthor`, `settingsByAlbum` → `songsByAlbum` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`
- [X] T018 [US1] Переименовать 2 сигнатуры `settings: Song` и все 111 внутренних `val`/`var settings` (не относящихся к DTO/HTTP, см. US2) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
- [X] T019 [US1] Переименовать внутренние (не wire-параметры) `val`/`var settings` (60 вхождений) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt`; **НЕ трогать** `@RequestParam settingsId` (`/changesettingsstatus`) и `@RequestParam settings_xxx` (`/songs_update`-семейство) — они переименовываются отдельно в US2 (T028, T031, T032)
- [X] T020 [P] [US1] Переименовать `settings: Song` (2 сигнатуры + 7 локальных) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt`
- [X] T021 [P] [US1] Переименовать `settings: Song` (5 сигнатур + 8 локальных) в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicPlayerController.kt`
- [X] T022 [P] [US1] Переименовать оставшиеся единичные `val settings` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/{PublicCartController,PublicApiController,PublicSubscriptionController,PublicSongEditorController,MainController}.kt`
- [X] T023 [P] [US1] Переименовать оставшиеся единичные `settings`-идентификаторы (не связанные с `KaraokeProcess.settingsId`, уже покрытым T016) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (`settingsLocal`) и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ExportAlignmentDataset.kt`
- [X] T024 [US1] Чекпоинт: `./gradlew ktlintCheck` и `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` проходят без ошибок (зависит от T004-T023)

**Checkpoint**: User Story 1 полностью выполнена и самостоятельно
проверяема — внутренний код читается корректно, ничего внешнего ещё не
менялось.

---

## Phase 4: User Story 2 - Переименование распространяется на контракт с фронтендом (Priority: P1)

**Goal**: DTO-поля, HTTP wire-параметры, SSE-ключ и legacy Thymeleaf-формы
переименованы синхронно с их потребителями (FR-010…FR-012, FR-015).

**Independent Test**: `POST /changesettingsstatus`, `POST /songs_update` и
SSE-поток health-report используют одно и то же новое имя на backend и
frontend; старое имя нигде в этой цепочке не осталось (см.
`contracts/rename-contracts.md`).

### Implementation for User Story 2

- [X] T025 [P] [US2] Переименовать `HealthReportDTO.settingsId` → `songId`, `settingsFileName` → `songFileName` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportDTO.kt`; обновить все места создания DTO в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
- [X] T026 [US2] Обновить потребителей `HealthReportDTO` во `webvue3`: `webvue3/src/components/Common/HealthReport/store.js` (`item.settingsId` → `item.songId`), `webvue3/src/components/Common/HealthReport/components/HealthReportTableBody.vue` (`:key="healthReport.settingsId"` → `songId`), `webvue3/src/components/Common/HealthReport/components/HealthReportTableHeader.vue` (зависит от T025)
- [X] T027 [P] [US2] Переименовать `KaraokeProcessDTO.settingsId` → `songId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessDTO.kt`; обновить место создания DTO в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`; grep-подтвердить (`research.md`, Решение 1), что во `webvue3` потребителя нет и правка фронтенда не нужна
- [X] T028 [US2] Переименовать `@RequestParam(required = true) settingsId: Long` → `songId` на `POST /changesettingsstatus` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (~строка 163); обновить `karaoke-app/src/main/resources/static/settings_context.js`, чтобы запрос отправлял ключ `songId`
- [X] T029 [US2] Переименовать SSE map-ключ `"settingsId" to settingsId` → `"songId" to songId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SseNotification.kt`; обновить обработчик в `webvue3/src/components/Songs/store.js` (~строка 1779, `userEventData.settingsId` → `userEventData.songId`)
- [X] T030 [US2] Переименовать локальные переменные `settingsId` → `songId` (циклы по id песни, строки ~1611-1642) в `webvue3/src/components/Songs/SongsTable.vue` — внутреннее имя без JSON/HTTP-контракта, но относится к тому же контуру webvue3, что и остальной срез US2 (находка `/speckit-analyze`, C2)
- [X] T031 [US2] Переименовать все 27 `@RequestParam settings_xxx` → `song_xxx` в первом методе `/songs_update`-семейства в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (строки ~1728-1754), включая построение `args`-map внутри метода
- [X] T032 [US2] Переименовать все 27 `@RequestParam settings_xxx` → `song_xxx` во втором методе того же семейства в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (строки ~1917-1943)
- [X] T033 [P] [US2] Обновить имена form-полей `settings_xxx` → `song_xxx` в `karaoke-app/src/main/resources/templates/songs.html` (зависит от T031, T032)
- [X] T034 [P] [US2] Обновить имена form-полей `settings_xxx` → `song_xxx` в `karaoke-app/src/main/resources/templates/songs2.html` (зависит от T031, T032)
- [X] T035 [P] [US2] Обновить имена form-полей `settings_xxx` → `song_xxx` в `karaoke-app/src/main/resources/templates/area_center_column.html` (зависит от T031, T032)
- [X] T036 [US2] Чекпоинт: `./gradlew ktlintCheck`, полная сборка backend, `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..` проходят без ошибок (зависит от T025-T035)

**Checkpoint**: User Story 1 и 2 вместе работают — внутренний код и все
контракты backend↔frontend синхронизированы.

---

## Phase 5: User Story 3 - Не затронуты понятия "настроек", не связанные с Song (Priority: P2)

**Goal**: Подтвердить, что исключения (Категория 6, `data-model.md`) остались
нетронутыми после Phase 3-4 (FR-004, FR-005, FR-014).

**Independent Test**: Все проверки ниже возвращают «без изменений» —
сравнение с baseline из T002/T003.

### Verification for User Story 3

- [X] T037 [P] [US3] Проверить, что `KaraokePlatform.settingsFieldPublicationId`/`settingsFieldVersionNumber` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt` не изменились (`git diff` на файл показывает только строку из T009, не эти поля)
- [X] T038 [P] [US3] Проверить, что модуль `karaoke-public` не затронут: `git diff --stat -- karaoke-public` пуст
- [X] T039 [P] [US3] Проверить, что физическая БД не затронута: `git diff --stat -- deploy/karaoke-db` пуст (новых `.sql`-миграций нет), `@KaraokeDbTableField(name = "settings_id")` в `KaraokeProcess.kt` не изменился, `SyncTarget.key = "settings"` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` не изменился
- [X] T040 [US3] Прогнать полный grep-регресс из `quickstart.md` (Сценарий 7) и сравнить с baseline из T002: 0 совпадений `settings: Song` и известных wire-имён; исключения (Категория 6) присутствуют и идентичны baseline (зависит от T024, T036)

**Checkpoint**: Все три user story подтверждены независимо; готово к Polish.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Обязательные по конституции завершающие шаги, общие для всей фичи

- [X] T041 Обновить `docs/features/premium-stems.md:44` — `settingsId=0` → `songId=0`, «нет привязки к `Settings`/`tbl_settings`» → «нет привязки к `Song`/`tbl_songs`» (FR-009, Constitution VI; см. `plan.md` Constitution Check)
- [X] T042 [P] Проверить KDoc/JSDoc на всех переименованных публичных сигнатурах на предмет устаревших упоминаний `settings` в `@param`/тексте комментария; прогнать `bash tools/check-kdoc-coverage.sh` и `bash tools/check-jsdoc-coverage.sh webvue3`
- [X] T043 Выполнить вручную сценарии 3-6 из `quickstart.md` (Health Report UI, `/changesettingsstatus`, legacy-форма `/songs_update`, регрессия таблицы песен/async-очереди) — по правилу проекта «тестов в CI нет», проверка делается пользователем/агентом вручную
- [X] T044 Прогнать полный pre-commit gate: `pre-commit run --all-files`; `./gradlew ktlintCheck`; `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..`; `bash tools/check-kdoc-coverage.sh`; `bash tools/check-jsdoc-coverage.sh webvue3`
- [X] T045 Подготовить единый PR по всем изменениям (FR-016) — один коммит/набор коммитов, покрывающий Phase 3-6 целиком
- [X] T046 После мержа PR — добавить хэш(и) финального коммита(ов) в `.git-blame-ignore-revs` (Constitution VII.2) — follow-up действие, выполняется отдельно пользователем/агентом после факта мержа

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей
- **Foundational (Phase 2)**: зависит от Setup; блокирует Phase 3 и 4
- **User Story 1 (Phase 3)**: зависит от Foundational; не зависит от US2/US3
- **User Story 2 (Phase 4)**: зависит от Foundational; частично зависит от
  US1 только там, где один и тот же файл правится дважды
  (`MainController.kt` — сначала внутренние переменные T019, затем wire-
  параметры T028/T031/T032, чтобы не конфликтовать построчно)
- **User Story 3 (Phase 5)**: зависит от завершения Phase 3 и 4 (это
  верификация результата, не независимая ветка работы)
- **Polish (Phase 6)**: зависит от Phase 5

### Within Each User Story

- Независимые файлы — параллельно (`[P]`)
- Файлы с общим переименовываемым символом, используемым по всему
  репозиторию (`Song.kt`, `SongRenderContext.kt`, `HealthReport.kt`,
  `KaraokeProcess.kt`) — сначала сам файл-источник, затем все внешние
  обращения к нему
- Чекпоинт компиляции/линта — в конце каждой Phase, перед переходом к
  следующей

### Parallel Opportunities

- Setup: T001, T002 — параллельно
- User Story 1: T004-T011, T017, T020-T023 — параллельно (разные файлы, нет
  общих символов); T012 → T013/T014 не параллельны друг другу, если задевают
  одни и те же вызывающие файлы — проверять по ходу; T015 → T016
  последовательно
- User Story 2: T025 и T027 — параллельно (разные DTO); T026 зависит от
  T025; T028, T029, T030 — параллельно друг другу и с T025/T027 (разные
  файлы); T031 → T032 (один файл) → T033/T034/T035 параллельно друг другу
- User Story 3: T037, T038, T039 — параллельно; T040 — после всех

---

## Parallel Example: User Story 1

```bash
# Независимые "листовые" файлы можно переименовывать параллельно:
Task: "Переименовать settings: Song -> song в UtilsPictures.kt"
Task: "Переименовать settings: Song -> song в UtilsAI.kt"
Task: "Переименовать settings: Song -> song в Functions.kt"
Task: "Переименовать settings: Song -> song в KaraokePlatformPublication.kt"
Task: "Переименовать settings: Song -> song в 11 файлах mlt/mko/*.kt"
```

## Parallel Example: User Story 2

```bash
# Независимые контракты можно переименовывать параллельно:
Task: "HealthReportDTO.settingsId/settingsFileName -> songId/songFileName"
Task: "KaraokeProcessDTO.settingsId -> songId"
Task: "@RequestParam settingsId -> songId на /changesettingsstatus + settings_context.js"
Task: "SSE-ключ settingsId -> songId в SseNotification.kt + Songs/store.js"
```

---

## Implementation Strategy

### Порядок работы (внутри единого PR, FR-016)

1. Phase 1 (Setup) + Phase 2 (Foundational) — обязательный старт.
2. Phase 3 (US1) — самый большой по числу файлов, но наименее рискованный
   срез (чисто внутренний код, ноль контрактных изменений). Чекпоинт T024
   должен быть зелёным перед продолжением.
3. Phase 4 (US2) — рискованный срез (задевает контракт backend↔frontend);
   каждая контрактная пара (backend-файл + frontend/шаблон-потребитель)
   правится и проверяется вместе, не раздельными коммитами с разрывом.
4. Phase 5 (US3) — чистая верификация, не производит новых изменений (если
   что-то не совпало с baseline — это находка бага в Phase 3/4, требующая
   точечного исправления, а не новой задачи).
5. Phase 6 (Polish) — документация, финальные гейты, единый PR, пост-мерж
   `.git-blame-ignore-revs`.

### Инкрементальная проверка (не инкрементальный релиз)

В отличие от типичного MVP-флоу, здесь нет «задеплоить после US1» — весь
результат уходит одним PR (FR-016). Тем не менее чекпоинты T024/T036/T040
дают возможность остановиться и проверить прогресс независимо на каждом
этапе перед тем, как двигаться дальше.

---

## Notes

- `[P]` — разные файлы, нет зависимости от незавершённых задач.
- `[US1]`/`[US2]`/`[US3]` — трассировка к user story в `spec.md`.
- Коммитить можно после каждой задачи или логической группы — в единый PR
  войдут все коммиты ветки `102-rename-song-settings-vars` (см. `plan.md`).
- Перед началом каждой задачи, трогающей файл с уже переименованными в этой
  же фиче символами (например, `HealthReport.kt` после T012 `Song.kt`),
  сверяться с `data-model.md`, чтобы не переименовать то, что уже
  переименовано, дважды или с расхождением в новом имени.
- Избегать: переименования вслепую по текстовому совпадению `settings` без
  проверки по `data-model.md`/`Категория 6` — граница задачи неоднократно
  уточнялась в `spec.md` именно потому, что текстовое совпадение вводит в
  заблуждение.
