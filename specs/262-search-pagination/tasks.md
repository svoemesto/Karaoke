---

description: "Task list template for feature implementation"
---

# Tasks: 262-search-pagination

**Input**: Design documents from `/specs/262-search-pagination/`
- [spec.md](./spec.md) — User Stories (US1–US4), 18 FR, 10 SC
- [plan.md](./plan.md) — Technical Context, Constitution Check, Project Structure
- [research.md](./research.md) — 7 решений по инфраструктуре
- [data-model.md](./data-model.md) — PagedSongsDto, searchPagination, Song.countMatchingAttr
- [contracts/api-songs.md](./contracts/api-songs.md) — расширенный контракт `/api/public/songs`
- [quickstart.md](./quickstart.md) — 19 сценариев ручной валидации (V1–V19)

**Tests**: тесты НЕ генерируются (см. AGENTS.md «Тесты: в CI нет»);
валидация через ручные сценарии в `quickstart.md`. Все валидационные
задачи ссылаются на конкретные V-сценарии.

**Organization**: задачи сгруппированы по user story (US1 = main feature,
US2 = регресс спеки 261, US3 = backend contract edge cases, US4 = error UX).
US2 и US3 состоят преимущественно из валидационных шагов (без нового кода).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимостей)
- **[Story]**: какая user story (US1, US2, US3, US4)
- Указаны точные пути файлов

## Path Conventions

- **Backend**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...` (контроллеры, DTO)
  и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/...` (Song.kt companion)
- **Frontend**: `karaoke-public/src/store/modules/songs.js` (Vuex) и
  `karaoke-public/src/views/SearchView.vue` (UI)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка feature-ветки и загрузка контекста.

- [x] T001 Убедиться, что feature-ветка `262-search-pagination` активна:
  `git branch --show-current` → `262-search-pagination`. Если нет —
  `git checkout 262-search-pagination` (см. AGENTS.md «CI-gate для master»).
- [x] T002 Прочитать контекстные документы (быстрая загрузка):
  `specs/262-search-pagination/spec.md` (FR-001..FR-018), `plan.md` (Technical
  Context), `research.md` (§1–7), `data-model.md` (PagedSongsDto, searchPagination),
  `contracts/api-songs.md` (запрос/ответ), `quickstart.md` (V1–V19).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Backend-каркас (DTO + SQL helper), без которого US1/US3 невозможны.
Frontend-стор (songs.js) относится к US1 (там, где он потребляется), не сюда —
это даёт более чистый MVP-инкремент.

- [x] T003 [P] Создать новый DTO `PagedSongsDto` в файле
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/PagedSongsDto.kt`.
  Поля: `items: List<SongPublicDto> = emptyList()`, `totalCount: Long = 0`,
  `page: Int = 1`, `pageSize: Int = 35`, `hasMore: Boolean = false`.
  Поля без `is`-префикса (инвариант Jackson проекта — Constitution VI).
  KDoc с `@see specs/262-search-pagination/contracts/api-songs.md`.
- [x] T004 [P] Добавить companion-метод `Song.countMatchingAttr` в
  `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt`
  (внутри `object Song`). Сигнатура:
  ```kotlin
  fun countMatchingAttr(
      args: Map<String, String> = emptyMap(),
      database: KaraokeConnection,
      sync: Boolean = false,
  ): Int
  ```
  Реализация — `SELECT COUNT(*) FROM tbl_songs WHERE <getWhereList(args)>`,
  паттерн из `Author.countWithNewAlbum`
  (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:384`).
  KDoc с `@see specs/262-search-pagination/contracts/api-songs.md`.
- [x] T005 Backend compile-проверка: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel`.
  Должны компилироваться без ошибок (T003, T004 синтаксически валидны).

**Checkpoint**: Foundation ready — T003 (PagedSongsDto) и T004 (countMatchingAttr)
готовы, можно расширять контроллер и фронт.

---

## Phase 3: User Story 1 — Поиск с большим числом результатов остаётся отзывчивым (Priority: P1) 🎯 MVP

**Goal**: Бэкенд возвращает порции по 35 песен с `totalCount` и `hasMore`;
фронт показывает счётчик «X из Y» и кнопку «Загрузить ещё»; URL хранит
`?page=N` для F5-устойчивости.

**Independent Test**: открыть `/search?author=Михайлов` → первая порция
(35 песен) появляется быстро, счётчик «Показано 35 из NN» в верхней части
списка, кнопка «Загрузить ещё» внизу. Клик подгружает следующие 35 без
перезагрузки списка. F5 на `?page=3` восстанавливает срез.

### Implementation for User Story 1

- [x] T006 [US1] Расширить `PublicApiController.songs(...)` в
  `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`:
  - Добавить параметры `@RequestParam(required = false) page: Int?`,
    `@RequestParam(required = false) pageSize: Int?` (оба опциональны —
  триггер обёртки).
  - Нормализовать: `page = if (page == null || page < 1) 1 else page`;
    `pageSize = if (pageSize in listOf(10, 25, 35, 50, 100)) pageSize else 35`.
  - Передать в `Song.loadListFromDb(attr, ...)` дополнительно
    `"limit" to pageSize.toString()` и `"offset" to ((page - 1) * pageSize).toString()`
    (поддержка уже есть в `Song.kt:7650-7657`).
  - Подсчитать `totalCount = Song.countMatchingAttr(attr, WORKING_DATABASE)`.
  - Если **оба** параметра отсутствуют — вернуть `List<SongPublicDto>`
    (старый формат, FR-003 спеки); иначе — вернуть `PagedSongsDto`.
  - Не трогать блок `mainController.doRegisterEvent(...)` (`PublicApiController.kt:703-712`);
    `page`/`pageSize` НЕ логируются в `tbl_events`.
  - KDoc с `@see specs/262-search-pagination/contracts/api-songs.md`.
- [x] T007 [US1] Добавить state в Vuex-store
  `karaoke-public/src/store/modules/songs.js`: в `state` — поле
  `searchPagination: { page: 1, pageSize: 35, totalCount: 0, hasMore: false, isLoadingMore: false }`.
  В `getters` — `searchPagination`, `searchHasMore`, `searchIsLoadingMore`,
  `searchTotalCount` (все через `state => state.searchPagination.<field>`).
- [x] T008 [US1] Добавить mutations в
  `karaoke-public/src/store/modules/songs.js`: `setSearchPagination(state, pagination)` —
  заменяет `state.searchPagination` целиком (для простоты);
  `appendSearchResults(state, items)` — если `items.length` и `state.searchResults.length`
  совпадают со стартовой страницей (или просто при `page === 1`), перезаписывает
  `state.searchResults = items`, иначе дописывает `[...state.searchResults, ...items]`;
  `setSearchPaginationLoadingMore(state, value)` —
  `state.searchPagination.isLoadingMore = value`.
- [x] T009 [US1] Модифицировать action `search` в
  `karaoke-public/src/store/modules/songs.js`:
  - Принимать `params` с полями `songName`, `author`, `text`, `album`, `page`, `pageSize`
    (последние два с дефолтами 1 и 35).
  - При смене фильтров (`songName`/`author`/`text`/`album`) —
    сбрасывать `page` в 1 и `searchResults` в `[]` (FR-011 спеки).
  - При успехе: `commit('setSearchResults', result.items || result)` +
    `commit('setSearchPagination', { ...result, isLoadingMore: false })`.
  - Сохранить существующий паттерн `latestSearchId` для race-condition.
- [x] T010 [US1] Добавить action `loadMoreSearchResults` в
  `karaoke-public/src/store/modules/songs.js`:
  - Guards: `if (!state.searchPagination.hasMore || state.searchPagination.isLoadingMore) return`.
  - `requestId = ++latestLoadMoreId` (отдельный счётчик от `latestSearchId`).
  - `commit('setSearchPaginationLoadingMore', true)`.
  - `await apiGet('/api/public/songs', { ...currentFilters, page: state.searchPagination.page + 1, pageSize: state.searchPagination.pageSize })`.
  - В `.then`: если `requestId === latestLoadMoreId` —
    `commit('appendSearchResults', result.items)` +
    `commit('setSearchPagination', { ...state.searchPagination, page: result.page, pageSize: result.pageSize, totalCount: result.totalCount, hasMore: result.hasMore, isLoadingMore: false })`.
  - В `.catch`: установить `state.searchPagination.error = true` (новое поле),
    оставить `isLoadingMore = false`.
  - В `.finally`: гарантировать `setSearchPaginationLoadingMore(false)`.
- [x] T011 [US1] Модифицировать `SearchView.vue` — добавить счётчик
  «Показано X из Y» в верхней части списка (после формы поиска, перед
  `<div class="km-song-list">`). Текст через computed `counterText`,
  показывать только при `searchResults.length > 0` (т.е. не при empty state).
  CSS-класс `km-counter` с `aria-live="polite"`. Использовать существующие
  CSS-переменные `--km-text2` для цвета.
- [x] T012 [US1] Модифицировать `SearchView.vue` — добавить кнопку «Загрузить
  ещё» после `<div class="km-song-list">`. Атрибуты:
  `:disabled="!searchHasMore || searchIsLoadingMore"`,
  `@click="onLoadMore"`, `:class="{ 'km-load-more-btn--loading': searchIsLoadingMore }"`.
  Под кнопкой — inline-спиннер (`<div class="km-spinner" v-if="searchIsLoadingMore">`).
  Скрывать блок кнопки целиком, если `!searchHasMore && !searchIsLoadingMore && !searchError`.
- [x] T013 [US1] Модифицировать `SearchView.vue` — добавить блок ошибки
  подгрузки (после кнопки, `v-if="searchError"`):
  `<div class="km-load-more-error" role="alert">Не удалось загрузить ещё.
  <button @click="retryLoadMore">Повторить</button></div>`.
  Метод `retryLoadMore`: `commit('setSearchPaginationError', false)` +
  `dispatch('loadMoreSearchResults')`.
  Новая mutation `setSearchPaginationError(state, value)` —
  `state.searchPagination.error = value`.
- [x] T014 [US1] Модифицировать `SearchView.vue` — добавить URL-sync:
  - Computed `pageFromUrl` / `pageSizeFromUrl` через `Number(this.$route.query.page) || 1`.
  - При монтировании компонента: если `pageFromUrl > 1`, дозагрузить
    страницы 2..pageFromUrl (вызвать `loadMoreSearchResults` нужное число
    раз, **последовательно** для предотвращения race на одной сессии).
  - Watch на `searchPagination.page`: `$router.replace({ query: { ...this.$route.query, page: state.songs.searchPagination.page, pageSize: state.songs.searchPagination.pageSize } })`.
  - В `onSearch()`: очистить `?page` из URL перед запуском (`delete $route.query.page`).
- [x] T015 [US1] Запустить frontend-проверки:
  `cd karaoke-public && npm run lint && npm run build`.
  Никаких НОВЫХ ESLint-нарушений (baseline OK через
  `tools/check-eslint-baseline.sh karaoke-public`).
  Build должен пройти успешно.
- [x] T016 [US1] Запустить backend-проверки:
  `./gradlew :karaoke-web:ktlintCheck` — никаких НОВЫХ ktlint-нарушений.
  `./gradlew :karaoke-web:bootJar --parallel` — успешная сборка.

**Checkpoint**: US1 полностью функциональна — бэкенд возвращает порции,
фронт показывает счётчик + кнопку, URL синхронизирован, F5 восстанавливает
срез. Можно задеплоить и протестировать на staging.

---

## Phase 4: User Story 2 — Существующие сценарии поиска не ломаются (Priority: P1)

**Goal**: Все компоненты строки из спеки 261 (PlayerIcon, FavoriteIcon,
PlaylistIcon, PremiumIcon, CartIcon, превью альбома/автора, эфир-даты,
ссылки на /song и /zakroma, модалка подписки, состояние «Ничего не найдено»)
работают на любой странице без регрессий.

**Independent Test**: открыть `/search` с запросом, дающим 1–5 песен
(одна страница), и с запросом, дающим ≥70 (несколько страниц). Проверить
**каждую** функцию спеки 261 на странице 1, странице 2 и последней странице.

### Validation for User Story 2

- [ ] T017 [P] [US2] Прогнать quickstart V18 (регресс спеки 261) на странице 1
  запроса с 1–5 песнями: проверить иконку плеера (зелёная/золотая/серая),
  превью альбома/автора (с плейсхолдером при ошибке), подпись «Автор - год,
  альбом», эфир-даты, иконку корзины, клики по названию/имени автора.
- [ ] T018 [P] [US2] Прогнать quickstart V18 на странице 2 запроса с ≥70
  песнями (после клика «Загрузить ещё»): те же проверки, что в T017.
- [ ] T019 [P] [US2] Прогнать quickstart V18 на последней странице
  (`hasMore === false`): те же проверки, что в T017.
- [ ] T020 [P] [US2] Прогнать quickstart V19 (мобильный вьюпорт) —
  DevTools → iPhone SE: повторить V10–V17 на узком экране.
  Адаптивная разметка строки (как в спеке 261) должна сохраняться;
  кнопка «Загрузить ещё» — полная ширина.

**Checkpoint**: US2 пройдена — ни одного регресса в UI спеки 261
ни на одной странице результатов.

---

## Phase 5: User Story 3 — Бэкенд отдаёт данные порциями и считает общее число (Priority: P2)

**Goal**: Контракт эндпоинта `/api/public/songs` (с параметрами `page`/
`pageSize`) удовлетворяет всем edge-cases из спеки: стабильная сортировка,
непересечение страниц, консистентный `totalCount`, обратная совместимость,
нормализация параметров.

**Independent Test**: набор curl-сценариев quickstart V1–V9.

### Validation for User Story 3

- [ ] T021 [P] [US3] Прогнать quickstart V1 (базовый запрос с пагинацией):
  curl `?songName=X&page=1&pageSize=10` → JSON-объект, `items.length <= 10`,
  `totalCount` — целое число.
- [ ] T022 [P] [US3] Прогнать quickstart V2 (стабильность `totalCount`):
  `totalCount` для `page=1` и `page=2` идентичен → `PASS`.
- [ ] T023 [P] [US3] Прогнать quickstart V3 (непересечение страниц):
  объединение `id` из `page=1` и `page=2` не имеет дубликатов → `PASS`.
- [ ] T024 [P] [US3] Прогнать quickstart V4 (стабильная сортировка):
  повторный curl `?page=1` даёт тот же массив `id` → `PASS`.
- [ ] T025 [P] [US3] Прогнать quickstart V5 (`hasMore` на границе):
  запрос с ровно `pageSize` элементов → `hasMore === false` → `PASS`.
- [ ] T026 [P] [US3] Прогнать quickstart V6 (обратная совместимость):
  curl **без** `page`/`pageSize` → ответ — массив (`type === "array"`),
  не объект. Существующие потребители не сломаны.
- [ ] T027 [P] [US3] Прогнать quickstart V7 (нормализация `pageSize`):
  `?pageSize=99` → `pageSize: 35` в ответе.
- [ ] T028 [P] [US3] Прогнать quickstart V8 (пустой результат):
  `?songName=абвгдеж` → `items: []`, `totalCount: 0`, `hasMore: false`.
- [ ] T029 [P] [US3] Прогнать quickstart V9 (скорость первой порции):
  запрос с `text=любовь` (≥500 результатов на проде) → `time` <1s,
  заметно быстрее baseline (полный возврат всех песен).

**Checkpoint**: US3 пройдена — бэкенд-контракт полностью соответствует
спецификации, edge-cases покрыты, обратная совместимость сохранена.

---

## Phase 6: User Story 4 — Контроль ошибок и пустых страниц (Priority: P3)

**Goal**: Пользователь видит понятное поведение при сетевых ошибках,
выходе за пределы (`page > totalPages`) и rapid-click на «Загрузить ещё».

**Independent Test**: DevTools Network → блокировать запрос следующей
порции → кликнуть «Загрузить ещё» → видно inline-сообщение + retry.
Быстрый двойной клик → подгружается только одна порция.

### Validation for User Story 4

- [ ] T030 [P] [US4] Прогнать quickstart V12 (rapid-click protection):
  два быстрых клика на «Загрузить ещё» → подгружается ровно одна
  порция (35 строк), кнопка `disabled` во время запроса.
- [ ] T031 [P] [US4] Прогнать quickstart V14 (F5-устойчивость):
  загрузить 3 страницы → F5 → URL восстановлен, срез соответствует
  состоянию до перезагрузки.
- [ ] T032 [P] [US4] Прогнать quickstart V15 (shareable URL):
  скопировать URL `/search?author=X&page=2`, открыть в приватном окне →
  страница 2 открывается с тем же фильтром.
- [ ] T033 [P] [US4] Прогнать quickstart V16 (ошибка + retry):
  DevTools → Block request → клик «Загрузить ещё» → inline-сообщение
  «Не удалось загрузить ещё. Повторить?» с кнопкой retry; разблокировать →
  retry работает.
- [ ] T034 [P] [US4] Прогнать quickstart V17 (смена фильтра сбрасывает
  пагинацию): загрузить 3 страницы для автора «Михайлов», сменить на
  «Петров», нажать «Искать» → новая первая страница, URL `?page=1`.
- [ ] T035 [P] [US4] Edge-case V8-extra (`page > totalPages`):
  curl `?songName=X&page=999` для запроса с 50 результатами → ответ
  с `items=[]`, `totalCount=50`, `hasMore=false`. В UI — блок
  «Страница не найдена» со ссылкой «К первой странице».

**Checkpoint**: US4 пройдена — error UX соответствует спецификации,
race-conditions защищены, edge-cases обрабатываются корректно.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Финальные проверки, документация, линт, деплой.

- [ ] T036 Запустить полный набор quickstart V1–V19 end-to-end (если
  ещё не все прогнаны в US2/US3/US4). Все 19 пунктов должны быть `PASS`.
- [ ] T037 Backend compile + lint: `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin :karaoke-web:ktlintCheck --parallel`.
  Никаких НОВЫХ нарушений (baseline OK через
  `tools/check-eslint-baseline.sh karaoke-public` + ktlint baseline).
- [ ] T038 Backend bootJar: `./gradlew :karaoke-web:bootJar --parallel`.
  Должен успешно собраться.
- [ ] T039 Frontend lint + build: `cd karaoke-public && npm run lint && npm run build`.
  Никаких НОВЫХ ESLint-нарушений, build OK.
- [ ] T040 Создать новый LiveDoc `livedocs/features/262-search-pagination.md`
  (per FR-014 governance: при изменении bounded context или C4 уровня —
    обновить LiveDoc). Содержимое: ссылка на spec, contracts, ссылка на
  пример curl + ссылка на пример URL `/search?page=N`.
- [ ] T041 Проверить наличие секрет-файлов в git: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$'`
  → должно вернуть пусто (Constitution VIII.3, AGENTS.md).
- [ ] T042 Открыть PR: `git push -u origin 262-search-pagination`,
  `gh pr create --base master`, `gh pr checks` (дождаться PASS),
  `gh pr merge --merge` (БЕЗ `--delete-branch`, lifecycle: ветка живёт
  после мёрджа, см. AGENTS.md «CI-gate для master»).

**Checkpoint**: фича полностью валидирована, задеплоена и доступна
на проде. Все 19 пунктов quickstart PASS, никаких регрессий спеки 261.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует немедленно.
- **Foundational (Phase 2)**: зависит от Setup — **БЛОКИРУЕТ** все user stories.
- **User Stories (Phase 3–6)**: зависят от Foundational phase completion.
  - **US1 (P1)**: может стартовать после Foundational (Phase 2) — **MVP**.
  - **US2 (P1)**: валидация регресса — может стартовать только после
    завершения US1 (требуется работающий код для проверки).
  - **US3 (P2)**: валидация контракта — может стартовать после
    завершения US1 (требуется работающий бэкенд для curl-проверок).
  - **US4 (P3)**: валидация error UX — может стартовать после
    завершения US1 (требуется работающий UI для проверок).
- **Polish (Phase 7)**: зависит от завершения US1–US4.

### User Story Dependencies

- **US1 (P1)**: может стартовать после Phase 2 — **не зависит от других stories**.
- **US2 (P1)**: может стартовать после US1 (P1) — должна валидировать
  работу реализации US1 на разных страницах.
- **US3 (P2)**: может стартовать после US1 (P1) — должна валидировать
  контракт бэкенда, реализованный в US1.
- **US4 (P3)**: может стартовать после US1 (P1) — должна валидировать
  error UX, реализованный в US1.

### Within Each User Story

- Backend DTO (T003) → backend compile (T005) → controller changes (T006) → backend lint/build (T016).
- Vuex state (T007) → mutations (T008) → actions (T009, T010) → SearchView.vue (T011–T014) → frontend lint/build (T015).
- Все validation tasks (US2/US3/US4) могут выполняться **параллельно** в
  пределах своей фазы (разные сценарии — разные curl-команды / браузерные
  вкладки).

### Parallel Opportunities

- T003 и T004 могут выполняться параллельно (разные файлы, нет зависимостей).
- T006 (контроллер) и T007–T010 (Vuex store) могут выполняться параллельно
  (разные файлы: backend Kotlin vs frontend JS).
- T011, T012, T013, T014 (SearchView.vue) — все изменения в одном файле,
  выполняются последовательно (но каждое — небольшая локализованная правка).
- T017–T020 (US2), T021–T029 (US3), T030–T035 (US4) — все `[P]`,
  могут выполняться параллельно (разные сценарии / curl-команды).

---

## Parallel Example: User Story 1

```bash
# Параллельно: backend DTO + SQL helper
Task: "T003 Создать PagedSongsDto в karaoke-web/.../dto/PagedSongsDto.kt"
Task: "T004 Добавить Song.countMatchingAttr в karaoke-app/.../model/Song.kt"

# Параллельно: backend controller + frontend Vuex (после T003+T004)
Task: "T006 Расширить PublicApiController.songs(...)"
Task: "T007 Добавить searchPagination state в songs.js"

# Последовательно: SearchView.vue — один файл
Task: "T011 Добавить счётчик X из Y"
Task: "T012 Добавить кнопку Загрузить ещё"
Task: "T013 Добавить блок ошибки + retry"
Task: "T014 Добавить URL-sync"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete **Phase 1**: Setup (T001–T002).
2. Complete **Phase 2**: Foundational (T003–T005).
3. Complete **Phase 3**: User Story 1 (T006–T016).
4. **STOP and VALIDATE**: ручной сценарий V10–V11 (quickstart.md) —
   первая порция подгружается, кнопка работает.
5. **Deploy/demo** — MVP готов.

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready (T001–T005).
2. Add User Story 1 (T006–T016) → Test independently (V10–V11)
   → **Deploy/Demo (MVP!)**.
3. Add User Story 2 (T017–T020) → Test V18–V19 → Deploy/Demo.
4. Add User Story 3 (T021–T029) → Test V1–V9 → Deploy/Demo.
5. Add User Story 4 (T030–T035) → Test V12–V17 → Deploy/Demo.
6. Polish (T036–T042) → финальный деплой.

### Parallel Team Strategy

С одним разработчиком рекомендуется строгая последовательность US1 → US2 →
US3 → US4 (фазы последовательны по зависимостям). С двумя разработчиками:

- **Dev A**: Phase 1 + Phase 2 + US1 (T001–T016).
- **Dev B** (после T005): может параллельно готовить US2 (validation tasks)
  — хотя физически валидировать можно только после US1, **спецификации
  US2** можно готовить заранее.

В рамках **одной** US1 — backend (T006) и frontend store (T007–T010) могут
делаться параллельно двумя людьми.

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей — безопасно параллелить.
- **[Story] label** привязывает задачу к user story для трассировки.
- Каждая user story **независимо завершаема и тестируема** (после US1).
- **Тесты не пишутся** (в CI нет); валидация через ручные сценарии
  `quickstart.md` V1–V19 — конкретные ссылки на V-сценарии даны в задачах.
- **Коммит после каждой задачи** или логической группы (T003, T004
  коммитятся отдельно; T007–T010 — группой «Vuex store»).
- **Остановиться на любом checkpoint** для валидации story независимо.
- **Избегать**: расплывчатых формулировок, конфликтов в одном файле
  (T011–T014 все в `SearchView.vue` — последовательно), cross-story
  зависимостей, ломающих независимость US2/US3/US4 от US1.
- **Constitution compliance** — все 8 принципов соблюдены (см. `plan.md`):
  сырой JDBC (T004), без новых зависимостей, без секретов, минимальный дифф.