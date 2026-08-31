# Tasks: Админка webvue3 — кнопка «Поиск родителя» для автора

**Input**: Design documents from `/specs/283-admin-find-parent/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md — все на месте
**Tests**: НЕ запрошены (Constitution § «Рабочий процесс»: «В CI тестов нет, существующие `@Disabled`. Проверка делается пользователем вручную или в production-like окружении»). Валидация — по `quickstart.md`.

**Organization**: 2 user story (US1 P1, US2 P2). Идемпотентность/гонки (US2) реализуются в Phase 3 как часть US1 (через `@Volatile`-флаг и SQL-фильтр), отдельная фаза нужна только для UX-части (warning-тост `ALREADY_RUNNING`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Include exact file paths in descriptions

## Path Conventions

Это web-app с Kotlin backend и Vue 3 frontend. Все пути — относительно корня репозитория.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить ветку и окружение.

- [x] T001 Подтвердить активную ветку `283-admin-find-parent` и чистый `git status` (`bash -c 'cd /home/nsa/Karaoke && git rev-parse --abbrev-ref HEAD && git status --porcelain'` — должен быть пустым)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Расширение `findParentCandidateId` параметром `crossAuthor` — пререквизит для US1 (новая функция `findParentForAuthor` будет вызывать `findParentCandidateId(song, db, crossAuthor = …)`). Без этого US1 нельзя реализовать корректно.

**⚠️ CRITICAL**: US1 и US2 нельзя начинать до завершения этой фазы.

- [x] T002 Изменить сигнатуру `findParentCandidateId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4475` — добавить параметр `crossAuthor: Boolean = true` (значение по умолчанию сохраняет текущее поведение, чтобы существующий вызов из `customFunction` остался binary-compatible)
- [x] T003 Изменить логику выбора финального пула в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4504-4508`: при `crossAuthor = false` — `finalPool = sameAuthor` (без фоллбэка на `pool`); при `crossAuthor = true` — существующее поведение `sameAuthor.ifEmpty { pool }`. Конкретный код — см. `research.md` R-002.

**Checkpoint**: Foundation ready — `findParentCandidateId` готов принимать оба режима, существующая `customFunction` продолжает работать без изменений.

- [x] T020 **[Добавлено 2026-08-31 по замечанию пользователя]** Убрать фильтр `withText` из `findParentCandidateId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` — родитель должен искаться среди **всех** песен автора, независимо от `source_text`. Конкретно:
  - удалить `withText`/`pool` логику (~3 строки);
  - убрать поле `hasText` из `data class ParentCandidate`;
  - убрать `source_text` из SELECT (`SELECT id, song_name, song_author FROM tbl_songs WHERE id <> ?`);
  - обновить KDoc `findParentCandidateId` (убрать упоминание приоритета по тексту);
  - обновить `specs/283-admin-find-parent/spec.md` (FR-007, A-010), `research.md` (R-002), `data-model.md`, `contracts/http-endpoint.md`, `quickstart.md` (новый Сценарий 9).
  - **Это меняет поведение `customFunction`** — она тоже теперь ищет родителя среди всех песен автора (по решению пользователя это правильное поведение).
  - **Проверено**: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` ✅ BUILD SUCCESSFUL; `./gradlew :karaoke-web:ktlintCheck` ✅.

---

## Phase 3: User Story 1 — Поиск родителя для всех песен одного автора (Priority: P1) 🎯 MVP

**Goal**: Кнопка «Поиск родителя» в `HomeView.vue` над «Автопривязать оригинал по аудио…», `disabled` при пустом авторе, модалка `CustomConfirm` с булевым полем `crossAuthor` (default `false`), фоновый запуск через `Utils.findParentForAuthor`, SSE-уведомление.

**Independent Test**: Сценарий 1 + Сценарий 2 + Сценарий 4 из `quickstart.md` (базовый с `crossAuthor=false`, с `crossAuthor=true`, `disabled` кнопки).

### Implementation for User Story 1

#### Backend — `Utils.kt`

- [x] T004 [US1] Добавить `@Volatile private var isFindParentInProgress: Boolean = false` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` рядом с `isCensoredRescanInProgress` (~строка 236), с KDoc-комментарием по образцу `isCensoredRescanInProgress`
- [x] T005 [US1] Реализовать top-level функцию `findParentForAuthor(author: String, crossAuthor: Boolean, storageService: KaraokeStorageService, storageApiClient: StorageApiClient): String` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` по образцу `customFunction` (Utils.kt:96-229): (1) проверка `isFindParentInProgress` → вернуть `"ALREADY_RUNNING"` или выставить `true`; (2) в `thread { … }` — `SELECT id FROM tbl_songs WHERE root_id = 0 AND LOWER(song_author) = LOWER(?) ORDER BY id`; (3) для каждого `id` — `Song.loadFromDbById` + `findParentCandidateId(song, WORKING_DATABASE, crossAuthor)`; (4) логика применения — копия фазы 1 `customFunction` (Utils.kt:118-186): если кандидат найден И у песни нет проверенного текста (`sourceText.isBlank() || idStatus < 2`) — `song.rootId = candidateId; song.saveToDb()`; иначе — пропуск; (5) `finally { isFindParentInProgress = false }`; (6) SSE-тост с заголовком `"Поиск родителя (автор «$author»)"` и телом `"Обработано N, родитель назначен M (найдено, но пропущено из-за текста: K)"`
- [x] T006 [US1] Добавить KDoc для `findParentForAuthor` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` с `@see specs/283-admin-find-parent/spec.md` (Constitution § VI FR-006)
- [x] T007 [US1] Обновить KDoc для `findParentCandidateId` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:4467-4474` — упомянуть новый параметр `crossAuthor` (семантика и поведение при `false` — только sameAuthor)

#### Backend — `ApiController.kt`

- [x] T008 [P] [US1] Добавить endpoint `@PostMapping("/utils/findparentforauthor") fun doFindParentForAuthor(@RequestParam(required = true) author: String, @RequestParam(required = false, defaultValue = "false") crossAuthor: Boolean): String` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` рядом с `doCustomFunction` (~строка 5890). Тело: `if (author.isBlank()) throw IllegalArgumentException("author must not be blank")` → возврат `findParentForAuthor(author.trim(), crossAuthor, storageService, storageApiClient)`. KDoc со ссылкой на `specs/283-admin-find-parent/spec.md`.

#### Frontend — `store.js`

- [x] T009 [P] [US1] Добавить Vuex-action `findParentForAuthorPromise(ctx, payload)` в `webvue3/src/components/Songs/store.js` рядом с `autoAssignOriginalAllPromise` (~строка 2416). Тело: `let params = { author: payload.author, crossAuthor: !!payload.crossAuthor }; let request = { method: 'POST', url: '/api/utils/findparentforauthor', params: params }; return promisedXMLHttpRequest(request)`. JSDoc со ссылкой на `specs/283-admin-find-parent/spec.md`.

#### Frontend — `HomeView.vue`

- [x] T010 [US1] Добавить `<button class="button-action" :disabled="!author" @click="findParentForAuthor">Поиск родителя</button>` в `webvue3/src/views/HomeView.vue` **непосредственно перед** существующей кнопкой «Автопривязать оригинал по аудио (статус 1 → 2)» (HomeView.vue:60-62), внутри того же блока `<div class="field-and-buttons-wrapper">`
- [x] T011 [US1] Добавить метод `findParentForAuthor()` в `webvue3/src/views/HomeView.vue` (в блоке `methods`, рядом с `autoAssignOriginalAll`): открывает модалку `CustomConfirm` с заголовком «Подтвердите действие», телом-описанием операции (автор, какие песни будут затронуты), и полем `fields: [{ fldName: 'crossAuthor', fldLabel: 'Искать среди песен других авторов:', fldValue: false, fldIsBoolean: true, ... }]`. По образцу `autoAssignOriginalAll` (HomeView.vue:469-480) и `customFunction` (HomeView.vue:752-763)
- [x] T012 [US1] Добавить метод `doFindParentForAuthor(result)` в `webvue3/src/views/HomeView.vue`: вызывает `this.$store.dispatch('findParentForAuthorPromise', { author: this.author, crossAuthor: result.crossAuthor === 'true' || result.crossAuthor === true })`; по ответу — info-тост «Операция запущена в фоне. Итог придёт уведомлением по завершении.» (или warning-тост «Уже запущено — дождитесь завершения текущего прогона.» если ответ `=== 'ALREADY_RUNNING'`)

**Checkpoint**: US1 полностью функциональна и тестируется независимо по Сценариям 1, 2, 4 из `quickstart.md`.

---

## Phase 4: User Story 2 — Повторный запуск безопасен и идемпотентен (Priority: P2)

**Goal**: Повторный запуск во время работы возвращает `ALREADY_RUNNING` + warning-тост; повторный запуск после завершения — только строки с `root_id = 0`. Защита от гонок уже реализована в Phase 3 через `@Volatile isFindParentInProgress` (T004) и SQL-фильтр в `findParentForAuthor` (T005).

**Independent Test**: Сценарии 3, 7 из `quickstart.md` (гонка + повторный запуск после завершения).

### Implementation for User Story 2

- [x] T013 [US1] Убедиться, что метод `doFindParentForAuthor` (T012) корректно обрабатывает ответ `=== 'ALREADY_RUNNING'` — показать warning-тост `alertType: 'warning'` с телом «Уже запущено — дождитесь завершения текущего прогона.» (по образцу `doRescanAllCensoredNames`, HomeView.vue:790-803). Если в T12 это уже сделано — отметить задачу выполненной без правок.

**Checkpoint**: US1 и US2 работают независимо. Повторный клик во время работы → warning, повторный клик после завершения → только строки с `root_id = 0`.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, формат, сборка, Docker-образ, ручная валидация по `quickstart.md`.

**⚠️ CRITICAL** (AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода», порядок non-negotiable):

- [x] T014 [P] Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` из `/home/nsa/Karaoke` (должен пройти без ошибок после T002–T008)
- [x] T015 [P] Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint` — никаких НОВЫХ нарушений (baseline OK). Если появились новые — либо исправить, либо обоснованно добавить в baseline через `tools/check-eslint-baseline.sh <pkg>`
- [x] T016 Backend bootJar (только на `nsa-i9`/`nsa` — машинно-специфичное исключение из AGENTS.md): `./gradlew :karaoke-web:bootJar --parallel` и `./gradlew :karaoke-app:bootJar` — оба должны собраться без ошибок
- [x] T017 [P] Frontend Vite + prettier: `cd webvue3 && npm run build && npm run format:check`; `cd karaoke-public && npm run build && npm run format:check` (prettier — всегда, Pass 244)
- [x] T018 [P] Docker-образ (NON-NEGOTIABLE, Pass 245): `cd deploy && bash do.sh build_webvue3`. Vite-build ≠ multi-stage Dockerfile — даже если `npm run build` зелёный, образ может не собраться
- [x] T019 Ручная валидация по `quickstart.md` (8 сценариев): Сценарий 1 (базовый), Сценарий 2 (`crossAuthor=true`), Сценарий 3 (`ALREADY_RUNNING`), Сценарий 4 (`disabled`), Сценарий 5 (trim), Сценарий 6 (пустой результат), Сценарий 7 (идемпотентность), Сценарий 8 (пропуск песни с проверенным текстом). Все должны дать ожидаемый результат

**Checkpoint**: Фича готова к деплою (выполняется пользователем, не агентом — Constitution § «Ограничения и доступы агента» п. 2).

---

## Phase 6: User Story 3 — Кнопка «Найти аудио-родителя» (Priority: P1)

**Goal**: Кнопка «Найти аудио-родителя» в `HomeView.vue` **между** «Поиск родителя» и «Автопривязать оригинал по аудио…», `disabled` при пустом авторе, модалка `CustomConfirm` без дополнительных полей, фоновый запуск через `Utils.findAudioParentForAuthor`. Ищет аудио-родителя **только в семье** (`findFamilySongIds`) для песен автора с `root_id <> 0` И `audio_parent_id = 0`. Только запись `audio_parent_id` (без копирования маркеров/текста/статуса).

**Independent Test**: Сценарии A1–A5 из `quickstart.md`.

### Implementation for User Story 3

#### Backend — `Utils.kt`

- [x] T021 [US3] Расширить сигнатуру `findAudioParentByWaveform` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:5117+` — добавить параметр `searchOtherAuthors: Boolean = true` (default сохраняет обратную совместимость с `customFunction` и существующим endpoint `/song/findaudioparent`). При `searchOtherAuthors = false` исключить `searchSongsByNormalizedName` из `candidateIds`. Обновить KDoc.
- [x] T022 [US3] Реализовать `findAudioParentForAuthor(author: String, storageService, storageApiClient): String` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` по образцу `findParentForAuthor`: (1) проверка `isFindAudioParentInProgress` → вернуть `"ALREADY_RUNNING"` или выставить `true`; (2) в `thread { … }` — `SELECT id FROM tbl_songs WHERE root_id <> 0 AND audio_parent_id = 0 AND LOWER(song_author) = LOWER(?) ORDER BY id` (по замечанию пользователя 2026-08-31 — песни с уже найденным `audio_parent_id` повторно не обрабатываются); (3) для каждого `id` — `Song.loadFromDbById` + `findAudioParentByWaveform(song, …, searchOtherAuthors = false)`; (4) `finally { isFindAudioParentInProgress = false }`; (5) SSE-тост с заголовком `"Поиск аудио-родителя (автор «$author»)"` и телом `"Обработано N, аудио-родитель назначен M, пропущено K"`. Добавить `@Volatile isFindAudioParentInProgress` (отдельный от `isFindParentInProgress`, чтобы аудио и текст могли идти параллельно). KDoc со ссылкой на `specs/283-admin-find-parent/spec.md`.

#### Backend — `ApiController.kt`

- [x] T023 [P] [US3] Добавить endpoint `@PostMapping("/utils/findaudioparentforauthor") fun doFindAudioParentForAuthor(@RequestParam(required = true) author: String): String` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` рядом с `doFindParentForAuthor`. Тело: `author.trim().isEmpty()` → `IllegalArgumentException` → 400; иначе `findAudioParentForAuthor(author.trim(), storageService, storageApiClient)`. KDoc со ссылкой на `specs/283-admin-find-parent/spec.md`.

#### Frontend — `store.js`

- [x] T024 [P] [US3] Добавить Vuex-action `findAudioParentForAuthorPromise(ctx, payload)` в `webvue3/src/components/Songs/store.js` рядом с `findParentForAuthorPromise`. Тело: `let params = { author: payload.author }; let request = { method: 'POST', url: '/api/utils/findaudioparentforauthor', params: params }; return promisedXMLHttpRequest(request)`. JSDoc со ссылкой на `specs/283-admin-find-parent/spec.md`.

#### Frontend — `HomeView.vue`

- [x] T025 [US3] Добавить `<button class="button-action" :disabled="!author" @click="findAudioParentForAuthor">Найти аудио-родителя</button>` в `webvue3/src/views/HomeView.vue` **непосредственно между** кнопкой «Поиск родителя» и кнопкой «Автопривязать оригинал по аудио (статус 1 → 2)».
- [x] T026 [US3] Добавить метод `findAudioParentForAuthor()` в `webvue3/src/views/HomeView.vue`: открывает модалку `CustomConfirm` с заголовком «Подтвердите действие», телом-описанием (автор, критерий `root_id ≠ 0` И `audio_parent_id = 0`, фразы «текст/маркеры/статус НЕ изменяются» и «операция очень тяжёлая»), без дополнительных полей. По образцу `customFunction` (HomeView.vue:752-763).
- [x] T027 [US3] Добавить метод `doFindAudioParentForAuthor()` в `webvue3/src/views/HomeView.vue`: вызывает `this.$store.dispatch('findAudioParentForAuthorPromise', { author: this.author })`; по ответу — info-тост или warning-тост `ALREADY_RUNNING` (по образцу `doFindParentForAuthor`).

**Checkpoint**: US3 полностью функциональна. Кнопка «Найти аудио-родителя» работает независимо от US1 (можно запускать параллельно — отдельные `@Volatile`-флаги).

---

## Phase 7: Polish (повторно, после добавления US3)

**Purpose**: повторный цикл проверок после добавления аудио-кнопки.

**⚠️ CRITICAL**: тот же порядок, что в Phase 5.

- [x] T028 [P] Backend compile: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`
- [x] T029 [P] Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint`
- [x] T030 Backend bootJar (на `nsa-i9`/`nsa`): `./gradlew :karaoke-web:bootJar :karaoke-app:bootJar --parallel`
- [x] T031 [P] Frontend Vite + prettier: `cd webvue3 && npm run build && npm run format:check` (webvue3 менялся — добавлены кнопка и методы); `cd karaoke-public && npm run build && npm run format:check` (karaoke-public не менялся)
- [x] T032 [P] Docker-образ webvue3: `cd deploy && bash do.sh build_webvue3` (Vite-build ≠ Docker, обязательно)
- [x] T033 Ручная валидация по `quickstart.md` (5 новых сценариев): A1 (базовый аудио), A2 (не меняет текст/маркеры/статус), A3 (идемпотентность по `audio_parent_id`), A4 (`ALREADY_RUNNING` для аудио), A5 (параллельный запуск текст+аудио)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — T001 можно выполнить сразу.
- **Phase 2 (Foundational)**: зависит от Phase 1 → **блокирует** US1 и US2.
- **Phase 3 (US1)**: зависит от Phase 2 (T002–T003 — пререквизит для T005).
- **Phase 4 (US2)**: реализация US2 уже встроена в Phase 3 (T004–T005), отдельная задача T013 — только проверка/уточнение UX warning-тоста.
- **Phase 5 (Polish)**: зависит от US1 (и проверки US2).

### User Story Dependencies

- **US1 (P1)**: после Phase 2 — независима от других stories.
- **US2 (P2)**: после Phase 2 — реализуется внутри US1 (через `@Volatile`-флаг + SQL-фильтр); отдельная фаза нужна только для явной UX-проверки `ALREADY_RUNNING`-тоста.

### Within Each User Story

- Backend (T002–T008) → Frontend (T009–T012).
- `findParentForAuthor` (T005) зависит от `findParentCandidateId` (T002–T003).
- Эндпоинт (T008) зависит от функции `findParentForAuthor` (T005).
- Кнопка (T010) зависит от обработчиков (T011–T012), которые зависят от Vuex-action (T009), который зависит от эндпоинта (T008).

### Parallel Opportunities

- T008 (эндпоинт `ApiController.kt`) и T009 (Vuex-action `store.js`) — **параллельно** (разные файлы, нет зависимостей).
- T011 и T012 — **последовательно** в одном файле (`HomeView.vue`), но можно одной правкой.
- Phase 5 (Polish): T014, T015, T017, T018 — **параллельно** (разные области — Kotlin vs JS vs Docker).
- T016 — после T014 (compile должен пройти до bootJar).

---

## Parallel Examples

### Phase 3 (US1) — после T002–T005 готовы

```bash
# Параллельно: эндпоинт + Vuex-action (разные файлы)
Task T008: "Добавить endpoint /utils/findparentforauthor в karaoke-app/.../ApiController.kt"
Task T009: "Добавить Vuex-action findParentForAuthorPromise в webvue3/src/components/Songs/store.js"

# Параллельно: KDoc-обновления (разные функции в одном файле, но без пересечений)
Task T006: "KDoc для findParentForAuthor в Utils.kt"
Task T007: "Обновить KDoc findParentCandidateId в Utils.kt"
```

### Phase 5 (Polish)

```bash
# Все четыре области — параллельно (разные стеки, разные машины)
Task T014: "./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel"
Task T015: "Линтеры ktlintCheck + npm run lint"
Task T017: "cd webvue3 && npm run build && npm run format:check"
Task T018: "cd deploy && bash do.sh build_webvue3"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup (T001)
2. ✅ Phase 2: Foundational (T002–T003) — расширение `findParentCandidateId`
3. ✅ Phase 3: US1 (T004–T012) — кнопка + эндпоинт + Vuex-action + модалка
4. ✅ Phase 4: US2 проверка (T013) — обычно уже сделано в Phase 3
5. ⏸ **STOP and VALIDATE**: запустить Phase 5 (T014–T019) → пройти 8 сценариев из `quickstart.md`
6. Готово к деплою (запускает пользователь, не агент)

### Incremental Delivery

В рамках этой фичи — один инкремент (US1 + US2). Разделение не нужно: US2 реализуется «бесплатно» в Phase 3.

### Parallel Team Strategy

Один разработчик достаточен (4 файла, ~80 строк кода). При наличии двух:

1. Разработчик A: Phase 2 (T002–T003) + Phase 3 backend (T004–T008).
2. Разработчик B: параллельно Phase 3 frontend (T009–T012) — Vuex-action и UI можно писать по контракту `contracts/http-endpoint.md` без ожидания эндпоинта.
3. Сборка: Phase 5 — оба вместе.

---

## Notes

- **[P] tasks**: разные файлы, нет зависимостей.
- **[Story] label**: T002, T003 — Foundational (без story label); T004–T012 — `[US1]`; T013 — `[US1]` (проверка UX-части US2, реализованной в Phase 3); T014–T019 — Polish (без story label).
- **Тесты**: НЕ генерируются (Constitution § «Рабочий процесс»; см. также `quickstart.md` — ручная валидация).
- **Деплой**: НЕ в скоупе задач (выполняется пользователем вручную — Constitution § «Ограничения и доступы агента» п. 2).
- **Per-feature документ `docs/features/…`**: НЕ создаётся в этой фиче (research.md R-008). Если в `/speckit.implement` выяснится, что есть смысл — добавить отдельной задачей вне `tasks.md`.
- **recordhash-триггер для `tbl_songs`**: НЕ требует пересоздания (Constitution § III) — мы пишем только `root_id` через существующий `Song.saveToDb()`, который уже учитывается в триггере.
- **Прерывание после US1 (MVP)**: допустимо — фича ценна сама по себе (точечный поиск родителя для одного автора). US2 — это «бесплатная» защита от гонок, без неё фича не сломается, но UX будет хуже.
