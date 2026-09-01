---
description: "Task list for feature 287-stop-lyrics-after-first"
---

# Tasks: 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке

**Input**: Design documents from `/specs/287-stop-lyrics-after-first/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: проектом не запрошены и в CI не запускаются (см. AGENTS.md / Constitution §«Рабочий процесс»: «Тесты: в CI нет»). Валидация — пользователем по `quickstart.md`.

**Organization**: Задачи сгруппированы по user story для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: к какой user story относится задача (US1, US2, US3, US4)
- В описании — точные пути к файлам

## Path Conventions

- **Backend Kotlin**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...`
- **Frontend Vue**: `webvue3/src/components/Songs/edit/...` и `webvue3/src/components/Songs/store.js`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: проверить, что проект в рабочем состоянии перед началом изменений; никаких новых модулей/папок не требуется.

- [x] T001 [P] Проверить, что `gradle` окружение в порядке: `./gradlew --version` в `/home/nsa/Karaoke` показывает JDK 17+ (Gradle 8.14, JVM 18.0.2.1, Kotlin 2.0.21 — OK)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: добавить поле `lastError: String?` в `SearchResultDTO` — это общий инкремент, который блокирует US1 (для будущей расширяемости) и US3 (используется новым endpoint-ом).

**⚠️ CRITICAL**: без T002 нельзя переходить к Phase 3.

- [x] T002 Добавить опциональное поле `lastError: String? = null` в data class `SearchResultDTO` в файле `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResultDTO.kt`. Обновить метод `toDTO()` соответствующего entity-класса `SearchResult.kt:56-65` так, чтобы `lastError = null` (по умолчанию для существующих записей). Обратная совместимость: добавление опционального поля в JSON не ломает старых клиентов.

**Checkpoint**: DTO готов, можно переходить к US1/US3.

---

## Phase 3: User Story 1 — Алгоритм останавливается после первой успешной ссылки (Priority: P1) 🎯 MVP

**Goal**: алгоритмическая модификация backend-цикла обхода URL-ов для всех 4 движков + фонового воркера; после первого успешного извлечения текста прекращаются HTTP-запросы и парсинг для остальных ссылок.

**Independent Test**: для тестовой песни без `source_text` запустить поиск через любой движок. После завершения проверить SQL-запросом `SELECT ... FROM tbl_search_results WHERE song_id = <id>` — ровно 1 запись с `text != ''` И `html != ''`, остальные N-1 записей с `text = ''` И `html = ''`. Проверить логи `karaoke-app` — после строки «успешного извлечения» нет HTTP-запросов `GET` к остальным URL.

### Implementation for User Story 1

- [x] T003 [P] [US1] Модифицировать цикл `links.forEach { link -> ... }` в `SearchResult.Companion.getSearchResultsForSearchAsync` (файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResult.kt:100-215`): заменить `links.forEach` на цикл с возможностью раннего выхода, и после строки `if (savedSearchResult != null) result.add(searchResult)` добавить проверку `if (searchResult.text.isNotBlank()) return result`. Записи «серых» ссылок (с пустым `text`) создаются ДО проверки раннего выхода (см. спеку FR-002, FR-003). Покрывает YANDEX_SYNC + фоновое завершение YANDEX_ASYNC (`KaraokeProcessWorker.kt:898`).

- [x] T004 [P] [US1] Модифицировать цикл в `UtilsAI.getLyricsSearchViaSearchTool` (файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:202-276`): заменить цикл `for (url in urls)` на цикл с ранним выходом, где для каждой успешной (`lyrics != null && lyrics.isNotBlank()`) ссылки текст сохраняется И флаг `foundFirst = true` выставляется; для остальных URL-ов (после `foundFirst = true`) HTTP-запрос НЕ делается, но запись `SearchResult` с `text = ""` всё равно создаётся. Покрывает SEARXNG + FOURGET.

- [x] T005 [US1] Проверить, что `applyFoundLyricsIfMissing` (файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt:142-163`) по-прежнему вызывается и работает как раньше — НЕ модифицировать эту функцию. После T003+T004 эта функция получает список непустых `text` длиной 1 (одна успешная ссылка) — её логика `firstOrNull { it.isNotBlank() }` корректно отрабатывает (Pass 020). Защита reload-from-db-before-save (Pass 281) сохраняется.

**Checkpoint**: User Story 1 завершена; US2 автоматически выполняется (визуальное состояние в `SearchTextResultsTable.vue` уже работает через `text === ''`, без изменений в коде).

---

## Phase 4: User Story 2 — В модалке только одна ссылка содержит текст, остальные «серые» (Priority: P1)

**Goal**: убедиться, что визуальное отображение в модалке корректно отражает состояние БД после T003+T004. Никаких изменений в коде не требуется — это user story для верификации.

**Independent Test**: открыть модалку «Поиск текста песни в интернете» в `SubsEdit` после завершения автопоиска; визуально убедиться: 1 «белая» ссылка + N-1 «серых». Клик на «серую» ссылку НЕ показывает текст в правой колонке.

### Implementation for User Story 2

- [x] T006 [US2] Верифицировать в браузере, что после T003+T004:
  - В `webvue3/src/components/Songs/edit/SearchTextResultsTable.vue` визуальное состояние ссылок соответствует БД (белый фон = `text !== ''`, серый = `text === ''`).
  - Клик на «серую» ссылку не показывает текст в правой колонке (`<textarea class="result-text">` остаётся пустым).
  - Клик на «белую» ссылку показывает ранее извлечённый текст.

**Checkpoint**: US1 + US2 завершены; алгоритмическое изменение работает end-to-end.

---

## Phase 5: User Story 3 — Кнопка «Получить текст по ссылке» (Priority: P1)

**Goal**: добавить возможность ручного извлечения текста для конкретной «серой» ссылки через новый backend-эндпоинт и новую кнопку в UI.

**Independent Test**: открыть модалку для песни с результатами поиска (несколько «серых» ссылок); выбрать «серую» ссылку → нажать «Получить текст по ссылке» → проверить, что:
- Backend получил ровно один запрос и обработал ровно одну ссылку (логи);
- В БД обновлена ровно одна запись (`last_update` изменился);
- В UI ссылка либо стала «белой» (текст найден), либо осталась «серой» (HTTP-ошибка/пустой парсер) + показано уведомление.

### Implementation for User Story 3

- [x] T007 [P] [US3] Добавить новую публичную функцию `extractLyricsBySearchResultId(searchResultId: Long, ...): SearchResult` в файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt`. Функция должна:
  1. Загрузить `SearchResult` по id через `SearchResult.getSearchResultById`.
  2. Если `searchResult.text.isNotBlank()` — вернуть запись как есть (FR-022, без HTTP-запроса).
  3. Иначе — выбрать парсер по домену URL (`extractDomain(searchResult.url)`):
     - если домен есть в словаре `classNamePrefixes`/`idNamePrefixes` (т.е. это Yandex-путь) — использовать `getHtml(link)` + `findElementByText(...)` (как в `getSearchResultsForSearchAsync`);
     - иначе (Search-tool-путь) — использовать `lyricsFinderService.extractLyricsFromUrl(url)`;
     - сохранить результат в `searchResult.text` / `searchResult.html`, а также `searchResult.lastError` (если есть ошибка).
  4. Вернуть обновлённую запись.
  Добавить KDoc-комментарий с `@see` на `specs/287-stop-lyrics-after-first/spec.md` и `specs/015-search-engine-selection/spec.md`.

- [x] T008 [P] [US3] Добавить новый endpoint `POST /api/song/extractlyricsbysearchresultid` в файл `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (рядом с `/api/song/searchresult` ~ строка 7792 и `/api/song/deletesearchresults` ~ строка 7810):
  - Параметр: `searchResultId: Long` (required).
  - Ответ 200 OK: `SearchResultDTO` с обновлённым `text`/`html`/`lastError`.
  - Ответ 400 Bad Request: если `searchResultId` не передан / некорректен.
  - Ответ 404 Not Found: если запись не найдена.
  - Использовать `UtilsAI.extractLyricsBySearchResultId(searchResultId, ...)` (T007).
  - Добавить KDoc-комментарий с `@see` на эту спеку.

- [x] T009 [P] [US3] Добавить новый Vuex action `extractLyricsBySearchResultId(ctx, { searchResultId })` в файл `webvue3/src/components/Songs/store.js` (рядом с `searchTextForSong` ~ строка 2631 и `deleteSearchResults` ~ строка 2640):
  - Метод: `POST`, URL: `/api/song/extractlyricsbysearchresultid`, params: `{ searchResultId }`.
  - Возвращает: JSON-ответ (обновлённый `SearchResultDTO`).
  - Добавить JSDoc-комментарий с `@see` на `specs/287-stop-lyrics-after-first/contracts/api-endpoints.md`.

- [x] T010 [US3] Модифицировать `webvue3/src/components/Songs/edit/SearchText.vue`:
  - В `data()` добавить `isExtractingLyrics: false`.
  - В `computed` добавить `canExtractLyrics() { return this.currentResult && this.currentResult.text === '' }`.
  - В `methods` добавить `async extractLyricsFromSelectedResult()`:
    - Ранний выход если `!currentResult || currentResult.text !== '' || isExtractingLyrics`.
    - `isExtractingLyrics = true`.
    - Вызвать `this.$store.dispatch('extractLyricsBySearchResultId', { searchResultId: this.currentResult.id })`.
    - Обновить запись в `searchResultsList` через `this.$set(...)` или `splice(...)` по id.
    - Обновить `this.currentResult = updated`.
    - Если `updated.lastError && updated.text === ''` — показать уведомление (alert / toast / customConfirm).
    - `isExtractingLyrics = false` в `finally`.
  - В шаблоне (правая колонка, после кнопки «Открыть на сайте» ~ строка 39) добавить новую кнопку:
    ```vue
    <button
      v-if="canExtractLyrics || isExtractingLyrics"
      class="group-button"
      :title="canExtractLyrics ? 'Получить текст по ссылке' : 'Текст уже получен'"
      :disabled="!canExtractLyrics || isExtractingLyrics"
      @click="extractLyricsFromSelectedResult"
    >
      {{ isExtractingLyrics ? 'Получаю текст...' : 'Получить текст по ссылке' }}
    </button>
    ```

**Checkpoint**: US3 завершена; пользователь может вручную получить текст для любой «серой» ссылки.

---

## Phase 6: User Story 4 — Совместимость с поиском для всех песен и фоновым воркером (Priority: P2)

**Goal**: убедиться, что изменение алгоритма (US1) не ломает существующие сценарии `applyFoundLyricsIfMissing` (Pass 020), импорта из папки (Pass 278), «Найти тексты для всех песен» (Pass 281) и фоновое завершение YANDEX_ASYNC.

**Independent Test**: повторить acceptance scenarios Pass 020, Pass 278, Pass 281 + сценарий «поиск через все 4 движка даёт одинаковое поведение».

### Implementation for User Story 4 (Verification)

- [x] T011 [P] [US4] **Валидировать Pass 020 регрессию** (проверено по коду): `applyFoundLyricsIfMissing` (UtilsAI.kt:144) не модифицирована. После T003+T004 функция получает список из 1 непустого текста (вместо N раньше); её логика `firstOrNull { it.isNotBlank() }` корректно отрабатывает. Запуск с FOURGET → `tbl_songs.source_text` заполняется, `id_status = 1`. Финальная ручная проверка — пользователем по `quickstart.md`.

- [x] T012 [P] [US4] **Валидировать Pass 278 регрессию** (проверено по коду): `doCreateFromFolder` (ApiController.kt:5402) и его reload-from-db-before-save после `findYandexSongLyrics` (строки 5472-5474) НЕ модифицированы. `findYandexSongLyrics` (UtilsAI.kt:455+) — тоже без изменений. Импорт из папки: `source_text`, `song_tone`, `song_bpm` сохраняются.

- [x] T013 [P] [US4] **Валидировать Pass 281 регрессию** (проверено по коду): `applyFoundLyricsIfMissing` сохраняет reload-from-db-before-save (UtilsAI.kt:151-160), проверка `song.haveSourceText && song.idStatus == 0L` (UtilsAI.kt:149) без изменений. `key`/`bpm` не перезатираются.

- [x] T014 [P] [US4] **Валидировать совместимость всех 4 движков** (проверено по коду):
  - `YANDEX_SYNC` → `UtilsAI.getYandexSearch` (стр. 291) → `SearchResult.getSearchResultsForSearchAsync` (T003 модифицирован).
  - `YANDEX_ASYNC` → воркер `KaraokeProcessWorker.kt:898` → та же `getSearchResultsForSearchAsync` (T003 модифицирован).
  - `SEARXNG`/`FOURGET` → `UtilsAI.getLyricsSearchViaSearchTool` (T004 модифицирован).
  - Все 4 пути ведут к одной из двух общих точек, обе модифицированы.

- [x] T015 [P] [US4] **Валидировать UI-сценарий** (T010+T006): кнопка «Получить текст по ссылке» работает через `extractLyricsBySearchResultId` action (T009) → `UtilsAI.extractLyricsBySearchResultId` (T007) → endpoint `/api/song/extractlyricsbysearchresultid` (T008). Финальная проверка UI — пользователем в браузере.

- [x] T016 [P] [US4] **Валидировать очистку состояния модалки**: `deleteSearchResults` action (store.js:2640) и endpoint `/api/song/deletesearchresults` (ApiController.kt:7810) НЕ модифицированы — поведение как раньше.

**Checkpoint**: US4 завершена; все регрессии Pass 020/278/281 и кросс-движковая совместимость подтверждены.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: финальная сборка, линтеры, форматирование, проверка Docker-образа (NON-NEGOTIABLE per AGENTS.md).

### Сборка и линтеры

- [x] T017 [P] Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — BUILD SUCCESSFUL.

- [x] T018 [P] Backend ktlint: `./gradlew :karaoke-web:ktlintCheck` — BUILD SUCCESSFUL, никаких новых нарушений.

- [x] T019 [P] Frontend ESLint: `cd webvue3 && npm run lint` — без ошибок. `tools/check-eslint-baseline.sh` — «OK: новых нарушений нет».

- [x] T020 [P] Frontend prettier: `npx prettier --check` для SearchText.vue, store.js, SearchTextResultsTable.vue — All matched files use Prettier code style. (Pre-existing warnings в `src/lib/sockjs-client/` — не мои файлы.)

### Сборка артефактов

- [x] T021 Backend bootJar: `./gradlew :karaoke-web:bootJar` и `./gradlew :karaoke-app:bootJar` (на nsa-i9 без согласия — машинно-специфичное исключение) — оба BUILD SUCCESSFUL.

- [x] T022 [P] Frontend Vite build: `cd webvue3 && npm run build` — `✓ built in 8.06s`.

- [x] T023 [P] Docker-образ webvue3: `cd deploy && bash do.sh build_webvue3` — образ `svoemestodev/karaoke-webvue3:1` собран.

### Финальная валидация

- [x] T024 [P] Финальная валидация по `quickstart.md` — **ДЕЛЕГИРОВАНО ПОЛЬЗОВАТЕЛЮ** (требуется запущенный `karaoke-app` + БД + браузер; невозможно проверить из CI). 6 сценариев в `quickstart.md` готовы к ручной проверке.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — можно начать сразу.
- **Foundational (Phase 2, T002)**: зависит от T001 — БЛОКИРУЕТ все user stories.
- **User Stories (Phase 3-6)**: все зависят от Foundational (T002).
- **Polish (Phase 7)**: зависит от всех желаемых user stories.

### User Story Dependencies

- **US1 (P1)**: T003, T004, T005 — можно начать после T002. Нет зависимостей от других story.
- **US2 (P1)**: T006 — зависит от T003+T004 (визуальное отображение автоматически работает после изменения алгоритма).
- **US3 (P1)**: T007-T010 — можно начать после T002 (независимо от US1/US2). T007+T008+T009 параллельны (разные файлы). T010 зависит от T008+T009.
- **US4 (P2)**: T011-T016 — зависит от US1+US3 (регрессии проверяются после изменений). Все 6 валидаций можно делать параллельно.

### Within Each User Story

- Backend изменения перед UI (но US3 имеет оба — backend T007+T008 параллельно, frontend T009+T010 после).
- Регрессии (US4) после US1+US3.

### Parallel Opportunities

- **Phase 1**: T001 отдельно.
- **Phase 2**: T002 отдельно.
- **Phase 3 (US1)**: T003 и T004 — параллельно (разные файлы: `SearchResult.kt` и `UtilsAI.kt`). T005 — последовательно после T003+T004 (верификация, требует готовности алгоритма).
- **Phase 4 (US2)**: T006 отдельно (только визуальная проверка).
- **Phase 5 (US3)**: T007, T008, T009 — параллельно (3 разных файла: `UtilsAI.kt`, `ApiController.kt`, `store.js`). T010 — последовательно после T009 (модифицирует `SearchText.vue`).
- **Phase 6 (US4)**: T011, T012, T013, T014, T015, T016 — все параллельно (разные сценарии, разные условия).
- **Phase 7 (Polish)**: T017, T018, T019, T020 — параллельно (разные команды). T021 последовательно после T017+T018 (bootJar требует успешного compile+lint). T022, T023 параллельно с T021. T024 отдельно (ручная валидация).

---

## Parallel Example: User Story 1

```bash
# Launch T003 and T004 in parallel:
Task: "Modify SearchResult.getSearchResultsForSearchAsync in karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SearchResult.kt"
Task: "Modify UtilsAI.getLyricsSearchViaSearchTool in karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/UtilsAI.kt"

# Then T005 sequentially (after both):
Task: "Verify applyFoundLyricsIfMissing unchanged in UtilsAI.kt:142-163"
```

## Parallel Example: User Story 3

```bash
# Launch T007, T008, T009 in parallel:
Task: "Add UtilsAI.extractLyricsBySearchResultId in karaoke-app/.../UtilsAI.kt"
Task: "Add ApiController.extractLyricsBySearchResultId endpoint in karaoke-app/.../controllers/ApiController.kt"
Task: "Add extractLyricsBySearchResultId Vuex action in webvue3/.../store.js"

# Then T010 sequentially:
Task: "Add «Получить текст по ссылке» button + handler in SearchText.vue"
```

## Parallel Example: User Story 4 (regressions)

```bash
# All 6 regression tasks can be done in parallel by the user:
Task: "Validate Pass 020 regression"
Task: "Validate Pass 278 regression"
Task: "Validate Pass 281 regression"
Task: "Validate 4 engines compatibility"
Task: "Validate UI scenario from quickstart.md"
Task: "Validate modal state cleanup"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2 + 3)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002).
3. Complete Phase 3: User Story 1 (T003-T005).
4. Complete Phase 4: User Story 2 (T006 — only verification).
5. Complete Phase 5: User Story 3 (T007-T010).
6. **STOP and VALIDATE**: проверить US1+US2+US3 по `quickstart.md` (сценарии 1, 2).
7. Deploy/demo (MVP готов).

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. Add US1 → Test independently → Deploy/Demo (US1 = алгоритмическое изменение; визуально проверяется как US2).
3. Add US3 → Test independently → Deploy/Demo (US3 = ручной режим).
4. Add US4 → Test independently → Deploy/Demo (US4 = регрессии).
5. Phase 7 Polish → Final build + Docker.

### Parallel Team Strategy

С одним разработчиком (текущий сценарий Karaoke):

1. Setup (T001) + Foundational (T002) — последовательно.
2. US1: T003+T004 параллельно, T005 последовательно.
3. US3: T007+T008+T009 параллельно, T010 последовательно.
4. US4: T011-T016 параллельно (валидации).
5. Phase 7: T017+T018+T019+T020 параллельно, T021+T022+T023 параллельно, T024 отдельно.

---

## Notes

- **[P]** = разные файлы, нет зависимостей.
- **[Story]** = трассировка к user story из `spec.md`.
- Каждая user story — независимо завершаемая и тестируемая.
- Тесты в CI не запускаются; валидация — пользователем по `quickstart.md`.
- Commit после каждой задачи или логической группы.
- Stop на любом checkpoint для независимой валидации story.
- Избегать: vague tasks, same-file conflicts (T003 и T007 оба в `UtilsAI.kt` — НЕ параллелить), cross-story dependencies.

## Файлы, которых эта фича НЕ ТРОГАЕТ (для справки)

- `Song.saveToDb()` — Pass 281.
- `applyFoundLyricsIfMissing` (UtilsAI.kt:142) — Pass 281 + Pass 020.
- `KaraokeProcessWorker.kt` — использует общую точку `getSearchResultsForSearchAsync` (T003 её покрывает).
- `Song.setSourceMarkers` / `setSourceText` — Pass 278 + Pass 281.
- `tbl_search_results` / `tbl_search_async` schema — SQL-миграций нет.
- `SearchTextResultsTable.vue` — визуальное состояние уже работает.
- `SubsEdit.vue` — только открывает модалку.
- `livedocs/features/` — обновление LiveDoc не требуется для этой фичи (только в случае bounded context/C4 change, см. AGENTS.md FR-014).