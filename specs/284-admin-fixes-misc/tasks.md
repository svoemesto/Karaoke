# Tasks: Админка — мелкие правки UI (SongEdit label, описание, пагинация истории)

**Input**: Design documents from `/specs/284-admin-fixes-misc/`
**Prerequisites**: plan.md (required), spec.md (required), quickstart.md, contracts/listeninghistory-pagination.md — все на месте
**Tests**: НЕ запрошены (Constitution § «Рабочий процесс»). Валидация — по `quickstart.md`.

**Organization**: 2 фиксовые истории (US1 P1 текст, US2 P2 rows) и 1 фиксовая история (US3 P1 пагинация).
US1+US2 касаются одного файла (`SongEdit.vue`) — реализуются последовательно в Phase 3.
US3 — отдельная фаза (Phase 4): правка `ListeningHistoryTable.vue` + `ListeningHistory/store.js`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths

## Path Conventions

Web SPA — все пути относительно корня репозитория `webvue3/src/...`. Backend
`karaoke-app/src/main/kotlin/.../ListeningHistoryController.kt` уже поддерживает
параметры `page`/`pageSize`, в этой фиче **не трогается**.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить ветку и окружение.

- [x] T001 Подтвердить активную ветку `284-admin-fixes-misc` и чистый `git status`
  (`bash -c 'cd /home/nsa/Karaoke && git rev-parse --abbrev-ref HEAD && git status --porcelain'`
  — должен быть пустым кроме нового `specs/284-admin-fixes-misc/`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Нет бэк-зависимостей (контракт уже есть), нет shared-стейта между US.
Phase 2 — фиксация согласованного контракта пагинации (ничего не кодим).

- [x] T002 Прочитать `contracts/listeninghistory-pagination.md` — зафиксированы имена
  параметров (`page`), поведение clamp'а на бэке и watcher-стратегия на фронте

**Checkpoint**: можно начинать US1+US2 (фронт-only правки `SongEdit.vue`).

---

## Phase 3: User Story 1+2 — Текстовые правки SongEdit.vue (Priority: P1+P2)

**Goal**: Лейбл `Censored:` + `rows="2"` — обе правки в одном файле, реализуются одной
правкой + одним коммитом. Независимо тестируемы (Сценарии 1, 2, 9 из `quickstart.md`).

**Independent Test**: Сценарии 1, 2, 9 (`quickstart.md`).

### Implementation

- [x] T003 [US1+US2] В файле `webvue3/src/components/Songs/edit/SongEdit.vue` строка 136 —
  заменить текст лейбла `Композиция (цензурированная):` на `Censored:`. `title`-тултип
  (атрибут `title=""` на div лейбла, строки 132-134) и `v-model="song.songNameCensored"`
  не трогать. Это чисто текстовая правка, остальная разметка блока без изменений.
- [x] T004 [US2] В том же файле `webvue3/src/components/Songs/edit/SongEdit.vue` строка 326 —
  заменить атрибут `rows="4"` на `rows="2"` у `<textarea v-model="song.description" class="input-field" rows="N" />`.
  `v-model` и кнопка undo не трогать.

**Checkpoint**: US1 и US2 функциональны независимо. Карточка песни показывает
новый лейбл и более короткое поле «Описание»; остальные поля формы не задеты
(Сценарий 9 `quickstart.md`).

---

## Phase 4: User Story 3 — Пагинация «Истории прослушиваний» (Priority: P1)

**Goal**: При клике по `<b-pagination>` на бэк уходит `POST /api/listeninghistory/digest`
с актуальным `page`, таблица обновляется. Первая страница загружается при mount;
смена target сбрасывает на 1; `currentPage > 1` сохраняется через Vuex в пределах сессии.

**Independent Test**: Сценарии 3, 4, 5, 6, 7, 8 из `quickstart.md`.

### Implementation

#### Backend — ListeningHistoryController.kt

- [x] T005 [US3] **Без изменений.** Контроллер уже принимает `page` и `pageSize`
  (ListeningHistoryController.kt:66-67, 85-87, 146), бэк ничего не трогаем. Контракт
  зафиксирован в `contracts/listeninghistory-pagination.md`.

#### Frontend — ListeningHistory/store.js

- [x] T006 [P] [US3] В файле `webvue3/src/components/ListeningHistory/store.js`,
  action `loadListeningHistoryDigest(ctx, params = {})` (строки 65-80): изменить
  построение `fullParams` так, чтобы он включал `page: params.page || 1` наряду с
  `target`. Текущий код: `const fullParams = Object.assign({}, params, { target: ctx.state.listeningHistoryTarget })`.
  Нужно: `const fullParams = Object.assign({}, params, { target: ctx.state.listeningHistoryTarget, page: params.page || 1 })`.
  Никаких других правок в action. Без новых KDoc — сигнатура публичного API не меняется.

#### Frontend — ListeningHistoryTable.vue

- [x] T007 [US3] В файле `webvue3/src/components/ListeningHistory/ListeningHistoryTable.vue`,
  watcher `currentPage(newPage, oldPage)` (строки 179-182): после `commit('setListeningHistoryTableCurrentPage', newPage)`
  добавить **триггер загрузки** — `if (newPage !== oldPage) { this.$store.dispatch('loadListeningHistoryDigest', { page: newPage }) }`.
  Это починка root-cause: текущий watcher только сохраняет, но не диспатчит reload.
- [x] T008 [US3] В том же файле, метод `reload()` (строки 188-190): явно передавать
  текущую страницу — `this.$store.dispatch('loadListeningHistoryDigest', { page: this.currentPage })`
  (вместо `{}`). Это гарантирует, что кнопка «Обновить» в тулбаре и `mounted()`
  загружают именно восстановленную страницу, а не всегда 1.
- [x] T009 [US3] Метод `onTargetChange()` (строки 191-194) — без изменений (already resets to 1 + reload).

**Checkpoint**: US3 функциональна. Клик по странице → запрос на бэк → данные обновляются.
Mount / кнопка «Обновить» / смена target — все три пути загрузки работают с правильной страницей
(Сценарии 3-7 `quickstart.md`).

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Линтеры, формат, Vite-сборка, Docker-образ, ручная валидация.

**⚠️ CRITICAL** (AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода», порядок non-negotiable):

- [x] T010 Backend compile: `./gradlew :karaoke-web:compileKotlin --parallel` (на `nsa-i9`/`nsa`
  дополнительно `:karaoke-app:compileKotlin` — для контроля нетронутости ListeningHistoryController). Должен пройти без ошибок после T006 (контракт API тот же).
- [x] T011 [P] Линтеры: `cd webvue3 && npm run lint` + `cd karaoke-public && npm run lint` —
  никаких НОВЫХ нарушений (baseline OK).
- [x] T012 [P] Frontend Vite + prettier (Pass 244): `cd webvue3 && npm run build && npm run format:check`;
  `cd karaoke-public && npm run build && npm run format:check`.
- [x] T013 [P] Docker-образ webvue3 (Pass 245 NON-NEGOTIABLE): `cd deploy && bash do.sh build_webvue3`.
  Vite-build ≠ multi-stage Dockerfile — даже если `npm run build` зелёный, образ может не собраться
  (для пагинации мы не трогали Dockerfile, но всё равно — обязательный шаг).
- [x] T014 Ручная валидация по `quickstart.md` — все 10 сценариев. Особенно:
  Сценарий 3 (базовый клик по странице → 1 POST), Сценарий 7 (mount → 1 POST, без дребезга),
  Сценарий 9 (регрессия остальных полей), Сценарий 10 (регрессия соседних таблиц).

**Checkpoint**: фича готова к деплою (выполняется пользователем, не агентом).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — T001 можно выполнить сразу.
- **Phase 2 (Foundational)**: T002 — чтение контракта, без кода.
- **Phase 3 (US1+US2, SongEdit.vue)**: T003, T004 — независимая правка одного файла, последовательно
  (в одном коммите можно одной правкой, но как две задачи для трассировки).
- **Phase 4 (US3, пагинация)**: T006 (store.js) и T007, T008, T009 (Table.vue) — **последовательно**,
  но в разных файлах. T006 формально независим, но в задачах выше — единый коммит.
- **Phase 5 (Polish)**: зависит от Phase 3+4.

### User Story Dependencies

- **US1 (P1 Censored label)**: после Phase 2 — независимая, в одном файле с US2.
- **US2 (P2 description rows)**: после Phase 2 — независимая, в одном файле с US1.
  В коде обе реализуются одной правкой в `SongEdit.vue`.
- **US3 (P1 пагинация)**: после Phase 2 — независимая от US1/US2, касается других файлов.

### Within Each User Story

- `SongEdit.vue` (Phase 3): T003, T004 — последовательно (один файл), обе в одном коммите.
- `store.js` (Phase 4): T006 — независимое изменение.
- `Table.vue` (Phase 4): T007, T008 — последовательно (один файл), в одном коммите.
- `Table.vue.onTargetChange` (Phase 4 T009) — без изменений, проверить что не сломали.

### Parallel Opportunities

- Phase 3 (`SongEdit.vue`) и Phase 4 (`store.js`+`Table.vue`) — **параллельно** (разные файлы,
  нет зависимостей между пользовательскими историями).
- Внутри Phase 5 (Polish) T011, T012, T013 — **параллельно** (разные области — линтер vs Vite vs Docker).
- T010 — отдельный шаг (compile должен пройти до Vite/Docker — практически не зависят, но идут
  первыми для быстрого fail-fast).

---

## Parallel Examples

### Phase 3+4 (после T002)

```bash
# Параллельно: SongEdit.vue И ListeningHistory-правки (разные файлы, разные истории)
Task T003+T004: "Текстовые правки в SongEdit.vue"
Task T006+T007+T008: "Правки в ListeningHistory/store.js и ListeningHistoryTable.vue"
```

### Phase 5 (Polish)

```bash
# Все три области — параллельно (разные стеки)
Task T011: "Линтеры"
Task T012: "Vite-build + prettier"
Task T013: "Docker-образ webvue3"
```

---

## Implementation Strategy

### MVP First (US3 Only)

1. ✅ Phase 1: Setup (T001)
2. ✅ Phase 2: Foundational (T002)
3. ✅ Phase 4: US3 (T006, T007, T008, T009) — починка пагинации
4. ⏸ **STOP and VALIDATE**: запустить Phase 5 (T010-T014) → пройти Сценарии 3-8 из `quickstart.md`
5. Готово к деплою

Затем US1+US2 (T003, T004) — это лёгкий cleanup, не блокирует US3.

### Incremental Delivery

В рамках этой фичи — 2 инкремента (US3 отдельно → US1+US2 отдельно), оба независимы,
оба готовы к деплою по отдельности. Можно закоммитить одним PR, но в `git diff` это
разные логические изменения (правка `SongEdit.vue` — чистый текст, правка
`ListeningHistoryTable.vue`+store — багфикс).

### Parallel Team Strategy

Один разработчик достаточен. При наличии двух:
1. Разработчик A: Phase 3 (US1+US2 — `SongEdit.vue`).
2. Разработчик B: параллельно Phase 4 (US3 — `ListeningHistoryTable.vue` + `store.js`).

---

## Notes

- **[P] tasks**: разные файлы, нет зависимостей.
- **[Story] label**: T003+T004 — `[US1+US2]`; T006+T007+T008+T009 — `[US3]`; T010-T014 — Polish
  (без story label).
- **Тесты**: НЕ генерируются (Constitution § «Рабочий процесс»; см. `quickstart.md` — ручная
  валидация).
- **Деплой**: НЕ в скоупе задач (выполняется пользователем вручную — Constitution §
  «Ограничения и доступы агента» п. 2).
- **recordhash-триггеры**: НЕ требуют пересоздания (Constitution § III) — нет изменений в БД.
- **F5 cross-session персистенция для `listeningHistoryTableCurrentPage` через `setWebvueProp`**:
  НЕ включается в этот тикет. FR-009 спеки говорит про «F5/перезаход», что покрывается
  Vuex-in-memory текущей реализацией (в пределах одной вкладки SPA). Если потребуется
  кросс-сессионная персистенция — отдельная фича с подзадачей hydrate в store.js (см.
  `contracts/listeninghistory-pagination.md` § 6).
- **Прерывание после US3 (MVP)**: допустимо — это и есть «починка пагинации»,
  основное болевое место из тикета. US1+US2 — лёгкие визуальные правки, делаются
  в одну строку каждая.
- **Build `karaoke-app`**: на `nsa-i9`/`nsa` разрешён (Pass 282) — T010 это подтверждает,
  и формально нужен из-за T006 (хоть contract не меняется, мы хотим убедиться, что
  бэк собирается без ошибок после правки frontend).
