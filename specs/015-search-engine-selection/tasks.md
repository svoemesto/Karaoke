---

description: "Task list template for feature implementation"
---

# Tasks: Выбор поискового движка для текстов песен и обложек альбомов

**Input**: Design documents from `/specs/015-search-engine-selection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-endpoints.md, contracts/ui-modal.md, quickstart.md (все присутствуют)

**Tests**: Автоматизированные тесты явно НЕ запрошены в spec.md; по принятой в проекте практике (`constitution.md` → «Рабочий процесс» → «Тесты», см. также фичу 014) — ручные прогоны сценариев `quickstart.md` вместо test-тасков.

**Organization**: Задачи сгруппированы по user story (`spec.md`) для независимой реализации и проверки каждой истории.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: К какой user story относится задача (US1, US2, US3, US4)
- Указаны точные пути к файлам

---

## Phase 1: Setup

**Purpose**: Снять единственную неопределённость, зафиксированную в `research.md` (Вопрос 3, "Note for implementation"), перед реализацией FOURGET-варианта поиска обложек

- [X] T001 Curl-запросом к реальному `fourget` (`GET /api/v1/images?s=test&scraper=brave`, по аналогии с фичой 014) уточнить точный формат ответа (имя обёртывающего ключа — `image`/`images`, поля картинки-результата) — зафиксировать находку в `specs/015-search-engine-selection/research.md` (Вопрос 3)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Общие типы/настройки/методы удаления, на которых строятся все 4 user story

**⚠️ CRITICAL**: Ни одна user story не начинается до завершения этой фазы

- [X] T002 [P] Добавить enum `LyricsSearchEngine { YANDEX_SYNC, YANDEX_ASYNC, SEARXNG, FOURGET }` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`
- [X] T003 [P] Добавить enum `AlbumCoverSearchEngine { SEARXNG, FOURGET }` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt`
- [X] T004 [P] Добавить свойства `lyricsSearchEngine` (default `"FOURGET"`) и `albumCoverSearchEngine` (default `"SEARXNG"`) в `listKaraokeProperties` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
- [X] T005 [P] Добавить `SearchAsync.deleteBySongId(songId, database, storageService, storageApiClient)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchAsync.kt` (по образцу `CartItem.deleteByUserAndSongs`, через `getSearchAsyncListBySongId` + `delete` в цикле)
- [X] T006 [P] Добавить `SearchResult.deleteBySongId(songId, database, storageService, storageApiClient)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResult.kt` (аналогично, через `getSearchResultListBySongId`)

**Checkpoint**: Enum'ы, настройки и методы удаления готовы — можно начинать любую user story.

---

## Phase 3: User Story 1 - Настройка движка поиска по умолчанию (Priority: P1) 🎯 MVP

**Goal**: Дать возможность сменить движок поиска текстов песен (4 варианта) и обложек альбомов (2 варианта) через уже существующий механизм настроек, и чтобы новый поиск каждого типа реально использовал выбранный движок.

**Independent Test**: Сценарии 1 и 2 из `quickstart.md` — сменить настройку, запустить новый поиск (текста и обложки) для записи без сохранённых результатов, убедиться по логам, что используется именно выбранный движок.

### Implementation for User Story 1

- [X] T007 [P] [US1] Добавить `SearchTool.searchUrlsViaSearxng(query): List<String>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` — прямой запрос к `searxng.base-url` (`/search?q=...&format=json&language=ru`), с тем же защитным поведением (try/catch, лог ошибок, `emptyList()`), что и у `searchUrls` (fourget)
- [X] T008 [US1] Переименовать `getSearXNGSearch` → `getLyricsSearch(settings, lyricsFinderService, engine: LyricsSearchEngine, forceResearch: Boolean = false)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt` — диспетчер: `YANDEX_SYNC`/`YANDEX_ASYNC` → `getYandexSearch(async=false/true)`, `SEARXNG` → `SearchTool.searchUrlsViaSearxng`, `FOURGET` → `SearchTool.searchUrls` (без изменений в самих `getYandexSearch`/fourget-пути) (depends on T002, T007)
- [X] T009 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` → `getSearchSongTextAll`: заменить прямой вызов `getSearXNGSearch(...)` на `getLyricsSearch(..., engine = enumValueOf(KaraokeProperties.getString("lyricsSearchEngine")), forceResearch = false)` (фолбэк на `LyricsSearchEngine.FOURGET` при некорректном значении настройки) (depends on T004, T008)
- [X] T010 [P] [US1] Добавить `AlbumCoverService.searchFourgetImages(query): List<AlbumCoverCandidate>` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinder.kt` — запрос к `fourget` `/api/v1/images?s=...&scraper=brave` по формату, уточнённому в T001 (depends on T001, T003)
- [X] T011 [US1] В `AlbumCoverFinder.kt` добавить параметр `engine: AlbumCoverSearchEngine` в `AlbumCoverService.search(...)`, диспетчинг `SEARXNG` → `searchSearxngImages` (без изменений), `FOURGET` → `searchFourgetImages` (T010); значение по умолчанию — `KaraokeProperties.getString("albumCoverSearchEngine")` с фолбэком на `SEARXNG` (depends on T003, T004, T010)
- [X] T012 [US1] В `ApiController.kt` → `searchAlbumCover`: принять опциональный `@RequestParam engine: String?`, передать в `albumCoverService.search(..., engine = ...)` (depends on T011)
- [X] T013 [US1] Добавить/обновить KDoc (с `@see docs/features/llm-lyrics-search.md`) для всех новых/изменённых публичных символов из T002-T012 (Principle VI конституции)
- [X] T014 [US1] Вручную прогнать Сценарии 1 и 2 из `quickstart.md` — смена `lyricsSearchEngine`/`albumCoverSearchEngine` в UI «Свойства», проверка по логам, что используется выбранный движок (depends on T009, T012). Выполнено с разрешения пользователя («в этой песочнице разрешаю пересобирать и перезапускать karaoke-app») — `bash do.sh build_app && start_app`, живой прогон через curl на реальных песнях («Король и Шут»): `engine=SEARXNG` для текста реально дошёл до `searxng` и нашёл рабочие URL (genius.com, korol-i-shut.su и т.д.); `engine=FOURGET`/`SEARXNG` для обложки альбома оба вернули реальные релевантные картинки с корректной меткой `source`.

**Checkpoint**: User Story 1 полностью работает и тестируется независимо (MVP).

---

## Phase 4: User Story 2 - Повторный поиск текста песни с выбором движка (Priority: P2)

**Goal**: Дать оператору явное действие «Искать заново» с очисткой старых результатов и выбором движка на этот конкретный запуск.

**Independent Test**: Сценарий 3 из `quickstart.md` — для песни с существующими результатами запросить повторный поиск, выбрать другой движок, подтвердить; убедиться, что старые результаты удалены, новые получены выбранным движком; при отмене — ничего не меняется.

### Implementation for User Story 2

- [X] T015 [P] [US2] В `ApiController.kt` → `getSearchSongTextAll`: добавить опциональные параметры `@RequestParam engine: String?` и `@RequestParam forceResearch: Boolean = false`; при `forceResearch=true` — вызвать `SearchResult.deleteBySongId`/`SearchAsync.deleteBySongId` (T005, T006) ДО вызова `getLyricsSearch` для каждой песни из `songsIds` (depends on T005, T006, T008, T009)
- [X] T016 [P] [US2] В `webvue3/src/components/Songs/store.js` изменить action `searchTextForSong(ctx, payload)` — передавать `engine`/`forceResearch` из `payload` в params запроса `/api/songs/searchsongtextall` (обратная совместимость: без `payload` — поведение как сегодня) (контракт см. `contracts/api-endpoints.md`, можно писать параллельно с T015)
- [X] T017 [US2] В `webvue3/src/components/Songs/edit/SearchText.vue` добавить кнопку «Искать заново» в `.st-footer` + `CustomConfirm` с `fields: [{fldName: 'engine', fldIsSelect: true, fldOptions: [...4 движка], fldValue: <текущий дефолт>}]` (см. `contracts/ui-modal.md`), метод `doResearch(engine)` → dispatch `searchTextForSong({forceResearch: true, engine})`, затем перечитать список результатов (depends on T016)
- [X] T018 [US2] Вручную прогнать Сценарий 3 из `quickstart.md` (depends on T015, T017). Основной путь (forceResearch удаляет старые результаты и ищет заново другим движком) подтверждён напрямую через API на реальной песне (SearchAsync id 732→734, старые 30 SearchResult удалены). UI-путь отмены диалога отдельно не нажимал глазами (это стандартное поведение уже существующего `CustomConfirm`, не новый код).

**Checkpoint**: User Story 1 и 2 работают независимо.

---

## Phase 5: User Story 3 - Удаление результатов поиска без повторного поиска (Priority: P3)

**Goal**: Дать оператору простое действие «Удалить результаты поиска» — без запуска нового поиска.

**Independent Test**: Сценарий 4 из `quickstart.md` — для песни с результатами нажать «Удалить результаты поиска», подтвердить; результаты исчезают, новый поиск не запускается; при отмене — ничего не меняется.

### Implementation for User Story 3

- [X] T019 [P] [US3] Добавить эндпоинт `POST /api/song/deletesearchresults` (`@RequestParam songId: Long`) в `ApiController.kt` — вызывает `SearchResult.deleteBySongId`/`SearchAsync.deleteBySongId` (T005, T006), возвращает `Boolean` (depends on T005, T006)
- [X] T020 [P] [US3] Добавить action `deleteSearchResults(ctx, songId)` в `webvue3/src/components/Songs/store.js` → `POST /api/song/deletesearchresults` (контракт см. `contracts/api-endpoints.md`, можно писать параллельно с T019)
- [X] T021 [US3] В `SearchText.vue` добавить кнопку «Удалить результаты поиска» в `.st-footer` + простой `CustomConfirm` (без полей), метод `doDeleteResults()` → dispatch `deleteSearchResults(songId)`, затем очистить локальное состояние (`searchResultsList = []`, `currentSearchAsync = undefined`) без запуска нового поиска (depends on T020)
- [X] T022 [US3] Вручную прогнать Сценарий 4 из `quickstart.md` (depends on T019, T021). Подтверждено напрямую через API: `/api/song/deletesearchresults` удалил SearchAsync+SearchResult для песни без запуска нового поиска. Отмена диалога — стандартное поведение существующего `CustomConfirm`, отдельно глазами не проверял.

**Checkpoint**: User Story 1, 2 и 3 работают независимо.

---

## Phase 6: User Story 4 - Очистка результатов поиска для готовых песен (Priority: P4)

**Goal**: Автоматически чистить результаты поиска при достижении песней статуса готовности (≥3) и дать администратору кнопку массовой очистки для уже готовых песен.

**Independent Test**: Сценарии 6 и 7 из `quickstart.md` — перевести песню в статус ≥3 и убедиться, что результаты удалены автоматически; отдельно — нажать кнопку массовой очистки и убедиться, что затронуты только песни со статусом ≥3.

### Implementation for User Story 4

- [X] T023 [P] [US4] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` → `saveToDb()`, рядом с существующим блоком `if (crossedReadyThreshold) { HealthReport.recomputeAndBroadcast(...) }` (строка ~4991/~5078), добавить вызовы `SearchResult.deleteBySongId(id, ...)` и `SearchAsync.deleteBySongId(id, ...)` (depends on T005, T006)
- [X] T024 [P] [US4] Добавить функцию `deleteSearchResultsForReadySongs(database, storageService, storageApiClient): Int` (по образцу `HealthReport.recalculatePlayerReadiness`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReport.kt` — выборка всех `Song` с `idStatus >= 3`, `deleteBySongId` для каждой, возврат количества обработанных (depends on T005, T006)
- [X] T025 [US4] Добавить эндпоинт `POST /api/utils/deletesearchresultsforreadysongs` в `ApiController.kt` — по образцу `doRecalcPlayerReadiness` (фоновый `thread {}`, вызов T024, по завершении `SNS.send(SseNotification.message(...))` с количеством) (depends on T024)
- [X] T026 [US4] Добавить action `deleteSearchResultsForReadySongsPromise(ctx)` в `webvue3/src/components/Songs/store.js` → `POST /api/utils/deletesearchresultsforreadysongs` (depends on T025)
- [X] T027 [US4] В `webvue3/src/views/HomeView.vue` добавить кнопку «Удалить результаты поиска готовых песен» рядом с `recalcPlayerReadiness` + `CustomConfirm` подтверждение (по образцу существующей кнопки) (depends on T026)
- [X] T028 [US4] Вручную прогнать Сценарии 6 и 7 из `quickstart.md` (depends on T023, T027). Сценарий 6 (авто-очистка при переходе статуса ≥3) подтверждён на реальной песне (id=22693, 1→3→1). Сценарий 7 (массовая кнопка) подтверждён с разрешения пользователя на реальной локальной БД: до — 658 `tbl_search_async` (237 из них у песен со статусом ≥3), после — 421 (ровно 658-237, 0 у песен со статусом ≥3, 410 у статуса &lt;3 не тронуты); `tbl_search_results` 63777→39433. Лог: «обработано песен: 14884».

**Checkpoint**: Все 4 user story работают независимо.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Документация фичи (FR-009 конституции), линтеры/покрытие документацией, финальная сквозная проверка

- [X] T029 [P] Дополнить `docs/features/llm-lyrics-search.md` — описать 4 движка поиска текста, 2 движка поиска обложек, `forceResearch`/`deleteBySongId`, автоочистку по статусу готовности (FR-009 конституции, обязательно в этом же PR)
- [X] T030 [P] Прогнать `./gradlew ktlintCheck` — новых нарушений сверх baseline нет
- [X] T031 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — покрытие не упало
- [X] T032 [P] Прогнать `bash tools/check-jsdoc-coverage.sh webvue3` — покрытие не упало (новые методы `SearchText.vue`, `HomeView.vue`, `store.js`)
- [X] T033 Вручную прогнать Сценарий 5 из `quickstart.md` — смена настройки движка по умолчанию НЕ меняет уже сохранённые результаты других песен (FR-010). Подтверждено: сменил `lyricsSearchEngine` на `SEARXNG` и обратно на `FOURGET` через `/api/properties/setproperty` — количество строк `tbl_search_async`/`tbl_search_results` (660/63777) не изменилось.
- [X] T034 Финальное diff-ревью: подтвердить, что `karaoke-public`/`karaoke-web` не затронуты (Principle V конституции), и что весь функциональный контракт (`FR-001`-`FR-013`, `spec.md`) закрыт — `git diff --stat` подтвердил: изменены только `karaoke-app` (backend) и `webvue3` (`SearchText.vue`, `store.js`, `HomeView.vue`) + `docs/features/llm-lyrics-search.md`; `karaoke-public`/`karaoke-web` отсутствуют в списке изменённых файлов

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: T002-T006 не зависят от T001 (разные области), но T001 нужен ДО T010 (US1) — БЛОКИРУЕТ все user story
- **User Story 1 (Phase 3)**: зависит от Foundational (T002-T006) и T001 (для T010)
- **User Story 2 (Phase 4)**: зависит от Foundational И от US1 (T008/T009 — диспетчер, который расширяется в T015)
- **User Story 3 (Phase 5)**: зависит только от Foundational (T005/T006) — НЕ зависит от US1/US2 (отдельный простой эндпоинт)
- **User Story 4 (Phase 6)**: зависит только от Foundational (T005/T006) — НЕ зависит от US1/US2/US3
- **Polish (Phase 7)**: зависит от завершения всех 4 user story

### User Story Dependencies

- **US1 (P1)**: зависит от Foundational; не зависит от US2/US3/US4
- **US2 (P2)**: технически зависит от кода US1 (диспетчер `getLyricsSearch`, T008/T009), т.к. `forceResearch` расширяет тот же эндпоинт — но не блокирует и не меняет поведение US1 по умолчанию
- **US3 (P3)**: полностью независима от US1/US2 — отдельный новый эндпоинт, использует только Foundational (`deleteBySongId`)
- **US4 (P4)**: полностью независима от US1/US2/US3 — использует только Foundational (`deleteBySongId`)

### Parallel Opportunities

- T002, T003, T004, T005, T006 (Foundational) — разные файлы, можно параллельно
- T007 и T010 (US1) — разные файлы, оба зависят только от Foundational — параллельно
- T015 и T016 (US2) — контракт уже зафиксирован в `contracts/api-endpoints.md`, backend/frontend можно писать параллельно
- T019 и T020 (US3) — аналогично, контракт зафиксирован
- T023 и T024 (US4) — разные файлы (`Song.kt` / `HealthReport.kt`), независимы друг от друга
- US3 и US4 могут разрабатываться параллельно с US1/US2 (независимы) — если позволяет команда/время
- T029-T032 (Polish) — независимы друг от друга

---

## Parallel Example: Foundational (Phase 2)

```bash
Task: "Добавить enum LyricsSearchEngine в UtilsAI.kt"
Task: "Добавить enum AlbumCoverSearchEngine в AlbumCoverFinder.kt"
Task: "Добавить свойства lyricsSearchEngine/albumCoverSearchEngine в KaraokeProperties.kt"
Task: "Добавить SearchAsync.deleteBySongId в model/SearchAsync.kt"
Task: "Добавить SearchResult.deleteBySongId в model/SearchResult.kt"
```

## Parallel Example: User Story 3 (независима от US1/US2)

```bash
Task: "Добавить эндпоинт POST /api/song/deletesearchresults в ApiController.kt"
Task: "Добавить action deleteSearchResults в webvue3/src/components/Songs/store.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002-T006) — КРИТИЧНО, блокирует всё остальное
3. Phase 3: User Story 1 (T007-T014)
4. **STOP и ПРОВЕРИТЬ**: прогнать Сценарии 1-2 из `quickstart.md` независимо
5. Это уже закрывает основной запрос («переключаемость движков через настройки»)

### Incremental Delivery

1. Setup + Foundational → инфраструктура готова
2. User Story 1 → проверить независимо → MVP (переключаемость движков)
3. User Story 2 → проверить независимо → «Искать заново» с выбором движка
4. User Story 3 → проверить независимо → «Удалить результаты поиска» (можно параллельно с US2, не зависит)
5. User Story 4 → проверить независимо → автоочистка + массовая кнопка (можно в любой момент после Foundational, не зависит от US1-3)
6. Polish (T029-T034) → документация, линтеры, финальная сверка

### Parallel Team Strategy

С несколькими разработчиками, после Foundational:
- Разработчик A: User Story 1 → затем User Story 2 (зависит от US1)
- Разработчик B: User Story 3 (полностью независима)
- Разработчик C: User Story 4 (полностью независима)

---

## Notes

- [P] задачи = разные файлы, нет зависимостей
- [Story]-метка привязывает задачу к конкретной user story
- US3 и US4 спроектированы независимыми от US1/US2 (используют только Foundational `deleteBySongId`) — можно реализовать в любом порядке после Foundational
- FR-010 (смена настройки не трогает старые результаты) не привязан к отдельной user story — проверяется в Polish (T033), т.к. это инвариант, а не отдельная функциональность
- Тесты не запрашивались явно — вместо test-тасков используются ручные прогоны сценариев `quickstart.md`, что соответствует принятой в проекте практике (см. также фичу 014)
