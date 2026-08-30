---

description: "Task list template for feature implementation"
---

# Tasks: Поле `song_name_censored` в `tbl_songs`

**Input**: Design documents from `/specs/277-song-name-censored/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md (все присутствуют)

**Tests**: В проекте нет CI-тестового слоя для этой части стека (см. plan.md Technical Context) — тестовые задачи не генерируются; проверка — через `quickstart.md` (Полировка, последняя задача).

**Organization**: Задачи сгруппированы по user story из spec.md (US1-US4, все P1; миграция US4 вынесена в Phase 1 Setup, остальные — Phase 3-5 в порядке P1 → P1 → P1).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: К какой user story относится задача (US1...US4)
- Пути указаны точные, от корня репозитория

## Path Conventions

Web-приложение, двух-фронтенд (Принцип V конституции):
- Backend ядро/БД-слой: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- Публичный API: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...` (НЕ затрагивается данной фичей)
- Admin frontend: `webvue3/src/components/...`, `webvue3/src/views/...`
- Public frontend: `karaoke-public/src/views/...` (НЕ затрагивается данной фичей)
- Миграции: `deploy/karaoke-db/...`

---

## Phase 1: Setup (миграция БД)

**Purpose**: Колонка `tbl_songs.song_name_censored` должна физически существовать в БД раньше, чем к ней обратится любой код (Foundational/User Story). Эта фаза закрывает US4 (безопасная миграция) — после неё LOCAL и PROD имеют колонку, бэкфилл копией `song_name`, пересобранный recordhash-триггер и обратно совместимы со старым кодом.

- [X] T001 Создать миграцию `deploy/karaoke-db/42_song_name_censored.sql`:
  1. `ALTER TABLE public.tbl_songs ADD COLUMN IF NOT EXISTS song_name_censored VARCHAR(255) NOT NULL DEFAULT ''` (по образцу `31_entity_description_fields.sql:72-74`);
  2. `CREATE OR REPLACE FUNCTION public.update_tbl_songs_recordhash()` — включить `COALESCE(NEW.song_name_censored, '')` в md5-конкатенацию (после `NEW.song_name`, по образцу `31_entity_description_fields.sql:76-182`);
  3. Backfill колонки: `UPDATE public.tbl_songs SET song_name_censored = song_name WHERE id > 0` (по образцу `27_author_special_order.sql`);
  4. Backfill recordhash: `UPDATE public.tbl_songs SET recordhash = md5(...)` (новый md5, включающий `song_name_censored` — по образцу `31_entity_description_fields.sql:220-318`).
  Применить вручную на LOCAL (PROD — только по прямому согласию пользователя, см. AGENTS.md).

**Checkpoint**: US4 — выполнено; колонка существует, бэкфилл копией завершён, recordhash корректен, LOCAL↔SERVER sync работает.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Колонка должна быть проброшена в entity-слой (`SongField` + `Song.kt` свойства/INSERT/load/diff) до того, как любая user story сможет с ней работать (чтение через `songNameCensored` getter, запись через `saveToDb()`).

**⚠️ CRITICAL**: Ни одна user story не может стартовать до завершения этой фазы.

- [X] T002 [P] Добавить `NAME_CENSORED` в `enum class SongField` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongField.kt` (рядом с `NAME`/`NAME_CENSORED` логически связан с `NAME`, см. data-model.md)
- [X] T003 [P] Заменить `val songNameCensored: String get() = songName.censored(database)` (текущая строка `Song.kt:608`) на `var songNameCensored: String` с геттером `fields[SongField.SONG_NAME_CENSORED] ?: ""` и сеттером `fields[SongField.SONG_NAME_CENSORED] = value` — зависит от T002. Добавить KDoc с `@see specs/277-song-name-censored/spec.md` (FR-006 конституции)
- [X] T004 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`:
  1. `getSqlToInsert(sync: Boolean)` (~строки 5867-5970): добавить `fieldsValues.add(Pair("song_name_censored", song.songNameCensored))` сразу после `("song_name", song.songName)` (~строка 5872);
  2. `loadListFromDb` (~строки 7689-7701, цикл `while (rs.next())`): добавить `rs.getString("song_name_censored")?.let { song.fields[SongField.SONG_NAME_CENSORED] = it }` сразу после `song_name` (~строка 7709);
  3. `loadFromDbById` (~строки 7888+): то же чтение `song_name_censored` колонки.
  Зависит от T002, T003.
- [X] T005 В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` `saveToDb()` (~строка 5157, формирование diff перед `getDiff(this, savedSong)`): если `fields[SongField.SONG_NAME_CENSORED]` пустое/отсутствует И `fields[SongField.NAME]` непустое — установить `fields[SongField.SONG_NAME_CENSORED] = songName.censored(database)` (baseline-автозаполнение по FR-003, см. research.md §3) — зависит от T003
- [X] T006 [P] В `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`: проверить, что поле `songNameCensored` присутствует (Out of Scope спеки — НЕ добавлять новое поле, если отсутствует; значение `songName` остаётся без цензурирования на публичном сайте по политике проекта)

**Checkpoint**: слой сущностей/DTO готов — можно начинать любую user story.

---

## Phase 3: User Story 1 - Админ разово пересчитывает цензурированные названия по словарю (Priority: P1) 🎯 MVP

**Goal**: На главной странице webvue3 есть кнопка «Пересканировать цензурированные названия песен»; нажатие запускает фоновую функцию, которая для всех 18k+ строк `tbl_songs` пересчитывает `song_name_censored` по актуальному словарю «Censored» (`tbl_dictionaries`). По завершении — SSE-уведомление с числом обработанных строк.

**Independent Test**: quickstart.md, Сценарии 2 + 3 — нажать кнопку, дождаться SSE-тоста, проверить, что слова из словаря заменились на маски `█` в `song_name_censored`; проверить гонки (параллельный запуск → «ALREADY_RUNNING»).

### Implementation for User Story 1

- [X] T007 [US1] Добавить `fun rescanAllCensoredNames(storageService, lyricsFinderService, storageApiClient): String` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (по соседству с существующей `customFunction()`, ~строка 96):
  - `@Volatile private var isCensoredRescanInProgress: Boolean = false` (рядом с функцией);
  - защита от гонок: `if (isCensoredRescanInProgress) return "ALREADY_RUNNING"`; `isCensoredRescanInProgress = true`; в `finally` — `false`;
  - фоновая обработка через `thread { … }`;
  - алгоритм (см. research.md §5): сначала `SELECT id FROM tbl_songs ORDER BY id` (одним запросом); затем для каждого `id` — лёгкий `SELECT song_name, song_name_censored FROM tbl_songs WHERE id = ?` (НЕ `loadFromDbById` — без FK-джойнов); сравнить `songName.censored(database)` с текущим `song_name_censored`; если отличается — `UPDATE tbl_songs SET song_name_censored = ? WHERE id = ?`;
  - по завершении — `SseNotification.send(...)` с заголовком «Пересканирование цензурированных названий» и телом «Обработано N песен за M секунд, обновлено K»;
  - KDoc с `@see specs/277-song-name-censored/spec.md` (FR-006 конституции)
- [X] T008 [P] [US1] Добавить endpoint `POST /api/utils/rescanallcensorednames` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (рядом с `customfunction`, ~строка 5876) — делегирует в `Utils.rescanAllCensoredNames(...)` и возвращает строку (`"OK"` / `"ALREADY_RUNNING"`)
- [X] T009 [P] [US1] Добавить endpoint `GET /utils/rescanallcensorednames` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt` (рядом с `customfunction`, ~строка 155) — зеркало для Thymeleaf-страниц, по образцу существующего GET-зеркала `customfunction`
- [X] T010 [P] [US1] Добавить action `rescanAllCensoredNamesPromise()` в `webvue3/src/components/Songs/store.js` (рядом с `customFunctionPromise`, ~строка 2430) — `POST /api/utils/rescanallcensorednames` через `promisedXMLHttpRequest`
- [X] T011 [US1] В `webvue3/src/views/HomeView.vue` (рядом с существующим `customFunction()`, ~строки 742-765):
  - добавить кнопку `<button class="btn btn-warning" @click="rescanAllCensoredNames">Пересканировать цензурированные названия песен</button>` (по образцу существующих кнопок);
  - метод `rescanAllCensoredNames()` — формирует `customConfirmParams` с подтверждением «Пересканировать цензурированные названия ВСЕХ песен (≈18k строк) по актуальному словарю «Censored»? Операция перезапишет ВСЕ цензурированные названия, включая ручные правки в SongEdit.» и `timeout: 15`;
  - метод `doRescanAllCensoredNames()` — диспатчит `rescanAllCensoredNamesPromise` и показывает тост-подтверждение `«Операция запущена в фоне. Итог придёт SSE-уведомлением»`;
  - зависит от T008, T010

**Checkpoint**: US1 функциональна и тестируема независимо (quickstart Сценарии 2, 3).

---

## Phase 4: User Story 2 - Редактор вручную правит цензурированное название в SongEdit (Priority: P1)

**Goal**: В `SongEdit.vue` под полем «Композиция» появляется поле «Композиция (цензурированная)» с авто-заполненным значением из БД, кнопками undo/copy/paste и tooltip-предупреждением о политике «доверие редактору». Редактор может переопределить значение; ручная правка переживает перезагрузку, переименование `song_name` и повторные сохранения.

**Independent Test**: quickstart.md, Сценарии 4 + 5 + 6 — ввести «Кастомное Название», сохранить, перезагрузить; изменить `songName` — ручное значение `songNameCensored` остаётся.

### Implementation for User Story 2

- [X] T012 [US2] В `webvue3/src/components/Songs/edit/SongEdit.vue` (сразу под полем «Композиция», строки 106-126):
  - добавить `<div class="label-and-input">` с `<input v-model="song.songNameCensored" class="input-field">` и тремя кнопками (undo/copy/paste) по образцу существующего поля «Композиция»;
  - label `«Композиция (цензурированная):»` и `<input>` — оба с `title="Ручное значение используется в публикациях (VK/Telegram/News) и публичном API БЕЗ повторной фильтрации. Редактируйте на свой страх и риск."` (FR-008 спеки, политика «доверие редактору»);
  - зависит от T003 (геттер `songNameCensored` уже работает через `fields[SongField.SONG_NAME_CENSORED]`)

**Checkpoint**: US2 функциональна независимо (quickstart Сценарии 4, 5, 6).

---

## Phase 5: User Story 3 - Чтение цензурированного названия не обращается к `tbl_dictionaries` (Priority: P1)

**Goal**: На горячем пути выборки песен (DTO-список, шаблоны VK/Telegram/News, картинки для публикаций, top-песни в `Publication.kt`) `songNameCensored` берётся из БД-поля `tbl_songs.song_name_censored` без вызова `song.songName.censored(database)` (который тянет словарь из `tbl_dictionaries`). Это основная мотивация фичи.

**Independent Test**: quickstart.md, Сценарий 7 — выполнить `GET /api/public/songs?limit=100`, в логах `karaoke-web`/`karaoke-app` 0 вызовов `SELECT … FROM tbl_dictionaries WHERE dict_name='Censored'` на этапе сборки DTO-списка.

### Implementation for User Story 3

- [X] T013 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` — заменить 11 вхождений `songName.censored(database)` → `songNameCensored` (полный список строк см. в data-model.md «Точки замены»; строки: 4651, 4663, 4665, 4707, 4715, 4724, 4778, 4781, 4784, 4789, 4822). Зависит от T003
- [X] T014 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt` — заменить 14 вхождений `publishNNN!!.songName.censored(publishNNN!!.database)` → `publishNNN!!.songNameCensored` для `publish10..publish23` (строки: 91, 99, 107, 115, 123, 131, 139, 147, 155, 163, 171, 179, 187, 195). Зависит от T003
- [X] T015 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkTemplateService.kt` — в `buildReplacements` (~строка 146): заменить `"songNameCensored" to song.songName.censored(database)` → `"songNameCensored" to song.songNameCensored`; обновить KDoc-плейсхолдер (~строки 24, 72): «Song.songNameCensored — цензурированное название (из БД)». Зависит от T003
- [X] T016 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramTemplateService.kt` — аналогично T015 (~строки 30, 59, 103). Зависит от T003
- [X] T017 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/NewsTemplateService.kt` — аналогично T015 (~строки 33, 87, 252). Зависит от T003
- [X] T018 [P] [US3] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsPictures.kt` — заменить 7 вхождений `song.songName.censored(song.database)` → `song.songNameCensored` (строки: 189, 263, 330, 399, 482, 597, 976). Зависит от T003

**Checkpoint**: US3 функциональна независимо (quickstart Сценарий 7). 0 вызовов `censored()` на горячем пути — фича достигает своей основной цели.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, линтеры, ручная валидация end-to-end.

- [X] T019 [P] Обновить `docs/features/dual-db-sync.md`: зафиксировать, что `tbl_songs` получила колонку `song_name_censored` и пересобран `update_tbl_songs_recordhash` (per-feature-doc FR-009 конституции — не путать с локальным FR-009 этой спеки, см. Constitution Check плана)
- [X] T020 [P] Обновить `docs/features/censored-words.md` (если существует, см. `archive/docs/features/`): зафиксировать, что `songNameCensored` теперь читается из БД без запроса в `tbl_dictionaries` на горячем пути; добавить ссылку на `specs/277-song-name-censored/`
- [X] T021 [P] Обновить `docs/features/censored-web-storage-globals.md` (specs/141) если там есть упоминания `song.songName.censored(database)` в горячих путях — заменить на описание чтения из БД-поля
- [X] T022 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — добавить KDoc с `@see specs/277-song-name-censored/spec.md` к новым публичным символам: `Song.songNameCensored` (геттер/сеттер, T003), `Utils.rescanAllCensoredNames` (T007), новые endpoint-методы в `ApiController`/`MainController` (T008, T009). По факту проверки часть может быть уже покрыта — доработать недостающее
- [X] T023 [P] Прогнать `bash tools/check-jsdoc-coverage.sh webvue3` — добавить JSDoc к новым/изменённым компонентам: `HomeView.vue` (методы `rescanAllCensoredNames`/`doRescanAllCensoredNames`, T011), `SongEdit.vue` (новое поле, T012), `store.js` (action `rescanAllCensoredNamesPromise`, T010)
- [X] T024 Прогнать `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` + `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` + `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` (обязательный чек-лист перед коммитом, см. AGENTS.md). Никаких НОВЫХ нарушений (baseline OK)
- [X] T025 Вручную пройти все 9 сценариев `specs/277-song-name-censored/quickstart.md` end-to-end на LOCAL-окружении (миграция, реckan, гонки, ручная правка, переименование без потери правки, пустое значение, устранение `tbl_dictionaries` запросов, авто-заполнение для новых песен, recordhash)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу (закрывает US4)
- **Foundational (Phase 2)**: зависит от Setup (T001) — БЛОКИРУЕТ все user stories
- **User Stories (Phase 3-5)**: все зависят от завершения Foundational
  - **US1 (P1) — CustomFunction rescan**: зависит от Phase 2 (нужен entity-слой, чтобы `Utils.rescanAllCensoredNames` мог работать через существующие геттеры); независим от US2/US3
  - **US2 (P1) — SongEdit UI**: зависит от Phase 2 (нужен `var songNameCensored` через `fields[SongField.SONG_NAME_CENSORED]`, иначе v-model не запишется в `currentSong`); независим от US1/US3
  - **US3 (P1) — замена вызовов**: зависит от Phase 2 (нужен getter `songNameCensored` через поле, иначе замена сломает компиляцию); независим от US1/US2
  - Все три можно делать параллельно разными разработчиками после Phase 2
- **Polish (Phase 6)**: после всех выбранных user stories

### Within Each User Story

- **US1**: T007 (функция) → T008+T009 (endpoint'ы, параллельно с T010) → T011 (UI, зависит от T008+T010)
- **US2**: T012 — единственная задача (UI-поле)
- **US3**: T013-T018 — все [P], разные файлы, параллельно

### Parallel Opportunities

- **Phase 2 Foundational**: T002 ([P]) параллельно с T003 ([P], зависит от T002 → сериализуется); T004-T006 — после T003, T006 [P] отдельно
- **Phase 3 US1**: T008 [P] || T009 [P] || T010 [P] — все разные файлы, можно параллельно после T007; T011 — после T008+T010
- **Phase 4 US2**: T012 — единственная задача, независима
- **Phase 5 US3**: T013-T018 — все [P], разные файлы, можно все параллельно после Phase 2
- **Phase 6 Polish**: T019 [P] || T020 [P] || T021 [P] || T022 [P] || T023 [P] — разные файлы, параллельно; T024 — последовательно после T019-T023 (зависит от прогона линтеров); T025 — после T024 (ручная валидация)
- **Кросс-US параллелизм**: после Phase 2 — US1, US2, US3 могут идти параллельно (3 разработчика)

---

## Parallel Example: Phase 5 User Story 3

```bash
# Все 6 задач US3 — разные файлы, нет зависимостей между ними (только от T003),
# можно запускать параллельно:
Task: "T013 Song.kt — 11 замен songName.censored → songNameCensored"
Task: "T014 Publication.kt — 14 замен в publish10..publish23"
Task: "T015 VkTemplateService.kt — 1 замена + 2 правки KDoc"
Task: "T016 TelegramTemplateService.kt — 1 замена + 2 правки KDoc"
Task: "T017 NewsTemplateService.kt — 1 замена + 2 правки KDoc"
Task: "T018 UtilsPictures.kt — 7 замен"
```

## Parallel Example: после Phase 2 — кросс-US

```bash
# Если есть 3 разработчика, US1/US2/US3 можно делать одновременно:
Developer A: "US1 — T007 (Utils.rescanAllCensoredNames) → T008/T009/T010 (параллельно) → T011 (HomeView)"
Developer B: "US2 — T012 (SongEdit поле)"
Developer C: "US3 — T013-T018 (параллельно)"
```

---

## Implementation Strategy

### MVP First (User Story 1 + Foundational)

1. Phase 1: Setup (T001) — миграция закрывает US4
2. Phase 2: Foundational (T002-T006) — КРИТИЧНО, блокирует всё
3. Phase 3: US1 (T007-T011) — CustomFunction реckan
4. **STOP и проверить**: quickstart Сценарий 2 (миграция + реckan работает)

Минимальная ценность: можно пересканировать цензурированные названия по
словарю. Поле `song_name_censored` существует и обновляется по
словарю.

### Incremental Delivery

1. Setup (T001) + Foundational (T002-T006) → готова основа, есть `songNameCensored` геттер/сеттер, запись в БД работает
2. US1 (T007-T011) → CustomFunction реckan; тест: слова из словаря заменяются на маски (MVP!)
3. US2 (T012) → ручной ввод в SongEdit; тест: ручная правка переживает перезагрузку
4. US3 (T013-T018) → устраняем все вызовы `censored()` на горячем пути; тест: 0 запросов к `tbl_dictionaries` на этапе DTO
5. Phase 6 (T019-T025) → документация, линтеры, ручная валидация

### Independent Test Criteria (для каждой истории — из spec.md)

- **US1**: quickstart Сценарии 2 (реckan), 3 (гонки)
- **US2**: quickstart Сценарии 4 (ручная правка), 5 (переименование без потери правки), 6 (пустое значение)
- **US3**: quickstart Сценарий 7 (0 словарных запросов на горячем пути)
- **US4** (закрыта Phase 1): quickstart Сценарии 1 (миграция), 9 (recordhash)
- **Бонус**: quickstart Сценарий 8 (авто-заполнение для новых песен — покрывается Phase 2 T005)

## Notes

- [P]-задачи = разные файлы, нет зависимостей между ними
- [Story]-метка — трассируемость к конкретной user story
- Каждая user story должна быть независимо завершаемой и тестируемой
- Коммитить после каждой завершённой задачи или логической группы (только по явному запросу пользователя — см. AGENTS.md workflow)
- Избегать: расплывчатых формулировок задач, конфликтов на одном файле без явного указания порядка, кросс-стори зависимостей, ломающих независимость
- **Миграцию T001 применять ТОЛЬКО после rebasing на master и проверки, что код из Phase 2 уже задеплоен** — иначе старый код попытается работать с новой колонкой (хотя DEFAULT '' совместим, см. research.md §2, но `songNameCensored` getter в старом коде всё ещё вызывает `.censored()`, что даст лишний запрос к словарю). Рекомендуемый порядок: PR с кодом → CI → merge → деплой на LOCAL → миграция T001 → деплой на PROD (по согласованию).