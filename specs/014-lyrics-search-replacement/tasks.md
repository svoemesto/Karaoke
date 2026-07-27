---

description: "Task list template for feature implementation"
---

# Tasks: Замена поискового движка для поиска текстов песен

**Input**: Design documents from `/specs/014-lyrics-search-replacement/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/lyrics-search-backend.md, quickstart.md (все присутствуют)

**Tests**: Автоматизированные тесты явно НЕ запрошены в spec.md; проект по конституции (`constitution.md` → «Рабочий процесс» → «Тесты») полагается на ручную/интеграционную проверку. Вместо test-тасков ниже — задачи на ручной прогон сценариев из `quickstart.md`.

**Organization**: Задачи сгруппированы по user story (`spec.md`) для независимой реализации и проверки каждой истории.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: К какой user story относится задача (US1, US2)
- Указаны точные пути к файлам

---

## Phase 1: Setup

**Purpose**: Зафиксировать выбор конкретного образа self-hosted бэкенда перед тем, как заводить инфраструктуру

- [X] T001 Зафиксировать конкретный self-hosted образ/тег поискового бэкенда (4get, см. `research.md` → Вопрос 1) и внутренний порт контейнера — добавить как комментарий в `deploy/docker-compose.yml` рядом с будущим сервисом (используется в T002-T004)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Инфраструктура и конфигурация, без которых ни одна user story не может быть реализована/протестирована

**⚠️ CRITICAL**: Ни одна user story не начинается до завершения этой фазы

- [X] T002 [P] Добавить новый сервис self-hosted поискового бэкенда (4get) в `deploy/docker-compose.yml` (по образцу существующего сервиса `searxng` в этом же файле) + добавить env-переменную `LYRICS_SEARCH_BASE_URL` в блок `environment` сервиса `karaoke-app` в том же файле; сервис/свойство `searxng` НЕ трогать (нужен для обложек альбомов, FR-007)
- [X] T003 [P] То же самое (новый сервис + `LYRICS_SEARCH_BASE_URL`) в `deploy/docker-compose-app.yml`
- [X] T004 [P] То же самое (новый сервис + `LYRICS_SEARCH_BASE_URL`) в `deploy/new_comp/sm-karaoke-system/deploy/docker-compose-app-new-comp.yml`
- [X] T005 [P] Добавить свойство `lyrics-search.base-url` (со значением по умолчанию, аналогичным `LYRICS_SEARCH_BASE_URL`) в `karaoke-app/src/main/resources/application.yml`, рядом с существующим `searxng.base-url` (строки 50-51) — не изменяя последнее

**Checkpoint**: Новый поисковый бэкенд поднимается инфраструктурно и виден приложению через конфигурацию — можно начинать реализацию user stories.

---

## Phase 3: User Story 1 - Пайплайн находит тексты песен там, где раньше находил мало или ничего (Priority: P1) 🎯 MVP

**Goal**: Заменить фактический вызов SearXNG в `SearchTool.searchUrls` на вызов нового self-hosted бэкенда, сохранив внешний контракт (FR-001, FR-004).

**Independent Test**: Сценарии 1 и 2 из `quickstart.md` — изолированный вызов нового бэкенда для песни с исторически низким hit rate, затем сквозной прогон через `getSearXNGSearch`/`LyricsFinderService`.

### Implementation for User Story 1

- [X] T006 [P] [US1] Создать data-классы `LyricsSearchResponse`/`LyricsSearchResult` (см. `data-model.md`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`, заменяющие сегодняшние SearXNG-специфичные `SearchResponse`/`SearchResult` в этом файле
- [X] T007 [US1] Изменить `SearchTool` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt`: заменить `@Value("\${searxng.base-url:...}")` на `@Value("\${lyrics-search.base-url:...}")`, поменять URL/параметры HTTP-запроса на новый бэкенд (см. `contracts/lyrics-search-backend.md`), парсить ответ через `LyricsSearchResponse`/`LyricsSearchResult` из T006 (depends on T006)
- [X] T008 [US1] Сохранить защитное поведение `searchUrls` в том же файле: try/catch вокруг HTTP-вызова, логирование не-200-статусов/исключений, возврат `emptyList()` при любой ошибке вместо исключения (FR-006), обновить текст логов под новый бэкенд (depends on T007)
- [X] T009 [US1] Добавить/обновить KDoc для `SearchTool` и новых data-классов в `llm/Tools.kt` с `@see docs/features/llm-lyrics-search.md` (Principle VI / FR-006 конституции) (depends on T006-T008)
- [X] T010 [US1] Обновить `docs/features/llm-lyrics-search.md`: заменить упоминания SearXNG на новый бэкенд в разделах «Как работает» (шаг 2) и «Известные ловушки» («SearXNG недоступен» → актуальное поведение нового бэкенда) — обязательно в этом же PR (FR-009 конституции) (depends on T007-T008)
- [ ] T011 [US1] **ТРЕБУЕТ ПОЛЬЗОВАТЕЛЯ**: вручную прогнать Сценарии 1 и 2 из `quickstart.md`: прямой запрос к новому бэкенду для песни с исторически низким hit rate + сквозной прогон через `getSearXNGSearch` (`karaoke-app/.../UtilsAI.kt:88`), включая проверку graceful-деградации при остановленном контейнере бэкенда — зафиксировать результат (depends on T002-T005, T007-T008). Не выполнено агентом: требует `docker compose up -d fourget` (новый контейнер) и **пересборки/перезапуска `karaoke-app`**, что по правилам проекта (`constitution.md` → «Ограничения и доступы агента») делает только пользователь.

**Checkpoint**: User Story 1 полностью работает и тестируется независимо (MVP).

---

## Phase 4: User Story 2 - Смена бэкенда не требует правок в местах вызова (Priority: P2)

**Goal**: Подтвердить, что адрес поискового бэкенда полностью конфигурируем и не зашит в код, а вызывающие места (`LyricsFinderService`, `UtilsAI.kt`) не требуют изменений.

**Independent Test**: Сценарий 3 из `quickstart.md` — смена `LYRICS_SEARCH_BASE_URL` на другой адрес без правок кода.

### Implementation for User Story 2

- [X] T012 [US2] Проверить `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/llm/Tools.kt` на отсутствие захардкоженного адреса бэкенда вне `@Value`-свойства (grep по `Tools.kt` на `http://`/`fourget`/`4get`) (depends on T007) — единственное вхождение `http://` — дефолт внутри `@Value("\${lyrics-search.base-url:http://fourget:80}")`, полностью переопределяемо конфигурацией
- [X] T013 [US2] Убедиться, что `LyricsFinderService` (`karaoke-app/.../llm/LyricsFinderService.kt`) и поток `getSearXNGSearch` (`karaoke-app/.../UtilsAI.kt:88`) не изменены по сигнатурам/логике (diff-ревью) — интерфейс `searchUrls(query: String): List<String>` идентичен (FR-004) (depends on T007-T008) — `git diff --stat` подтвердил нулевые изменения в обоих файлах
- [ ] T014 [US2] **ТРЕБУЕТ ПОЛЬЗОВАТЕЛЯ**: вручную прогнать Сценарий 3 из `quickstart.md`: изменить `LYRICS_SEARCH_BASE_URL` в docker-compose на тестовый адрес, перезапустить `karaoke-app` (только с согласия пользователя), убедиться по логам, что используется новый адрес без правок кода (depends on T002-T005, T012-T013). Не выполнено агентом — требует перезапуска `karaoke-app`.

**Checkpoint**: US1 и US2 обе работают независимо.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Проверки, затрагивающие обе истории и требования вне конкретной user story (FR-007 — регрессия по обложкам альбомов)

- [X] T015 [P] Прогнать `./gradlew ktlintCheck` — новых нарушений сверх `config/ktlint/baseline-*.xml` нет (Principle VI конституции) — BUILD SUCCESSFUL, 0 нарушений
- [X] T016 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — покрытие KDoc не упало после T009 (Principle VI / FR-006 конституции) — karaoke-app 96.3% (338/351), TOTAL 96.7%, выше порога ≥50%
- [ ] T017 **ТРЕБУЕТ ПОЛЬЗОВАТЕЛЯ**: вручную прогнать Сценарий 4 из `quickstart.md`: поиск обложки альбома (`AlbumCoverService.search` / `POST /api/song/searchalbumcover`) по-прежнему использует контейнер `searxng`/свойство `searxng.base-url` без изменений и без регрессий (FR-007, SC-005). Статическая проверка (нулевой diff в `AlbumCoverFinder.kt`, см. T018) выполнена агентом; живой прогон через запущенный `karaoke-app` — нет.
- [X] T018 Финальное diff-ревью: подтвердить, что `AlbumCoverFinder.kt` не тронут по существу (кроме, возможно, комментариев), а весь функциональный контракт (`FR-001`-`FR-007`, `spec.md`) закрыт — `git diff --stat` подтвердил: `AlbumCoverFinder.kt` отсутствует в списке изменённых файлов (0 диффа); изменены только `llm/Tools.kt`, `application.yml`, 3 docker-compose файла, `docs/features/llm-lyrics-search.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу
- **Foundational (Phase 2)**: зависит от Setup (T001 — выбор образа нужен для T002-T004) — БЛОКИРУЕТ обе user story
- **User Story 1 (Phase 3)**: зависит от завершения Foundational
- **User Story 2 (Phase 4)**: зависит от завершения Foundational и от T007-T008 (US1) — по сути валидирует конфигурационное решение, принятое во время реализации US1
- **Polish (Phase 5)**: зависит от завершения US1 и US2

### User Story Dependencies

- **User Story 1 (P1)**: может начинаться сразу после Foundational; не зависит от US2
- **User Story 2 (P2)**: технически зависит от кода, написанного в US1 (T007-T008), т.к. проверяет именно то, что конфигурация из этого кода реально работает без хардкода — но не блокирует и не изменяет US1

### Parallel Opportunities

- T002, T003, T004, T005 (Foundational) — разные файлы, можно параллельно
- T006 (US1, data-классы) можно начать параллельно с T002-T005 (разные файлы), но T007 требует T006
- T015, T016 (Polish, линтеры) — независимы друг от друга, можно параллельно

---

## Parallel Example: Foundational (Phase 2)

```bash
Task: "Добавить сервис 4get + LYRICS_SEARCH_BASE_URL в deploy/docker-compose.yml"
Task: "Добавить сервис 4get + LYRICS_SEARCH_BASE_URL в deploy/docker-compose-app.yml"
Task: "Добавить сервис 4get + LYRICS_SEARCH_BASE_URL в deploy/new_comp/sm-karaoke-system/deploy/docker-compose-app-new-comp.yml"
Task: "Добавить свойство lyrics-search.base-url в karaoke-app/src/main/resources/application.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002-T005) — КРИТИЧНО, блокирует всё остальное
3. Phase 3: User Story 1 (T006-T011)
4. **STOP и ПРОВЕРИТЬ**: прогнать Сценарии 1-2 из `quickstart.md` независимо
5. При успехе — это уже закрывает основную боль запроса пользователя («SearXNG не справляется»)

### Incremental Delivery

1. Setup + Foundational → инфраструктура готова
2. User Story 1 → проверить независимо → это MVP (решает саму проблему)
3. User Story 2 → проверить независимо (по сути — валидация того, что решение из US1 уже конфигурируемо)
4. Polish (T015-T018) → линтеры, docs coverage, регрессия по обложкам альбомов

---

## Notes

- [P] задачи = разные файлы, нет зависимостей
- [Story]-метка привязывает задачу к конкретной user story
- US2 в этой фиче тонкая по объёму кода: её основной механизм (вынесение адреса в конфигурацию) реализуется в рамках US1 (T007), т.к. иначе US1 нарушила бы FR-005 с первого дня; задачи US2 — это верификация и ручной прогон конфигурационного сценария, а не отдельная реализация
- FR-007 (обложки альбомов не трогаем) не привязан к user story — вынесен в Polish (T017-T018) как cross-cutting non-regression проверка
- Тесты не запрашивались явно — вместо test-тасков используются ручные прогоны сценариев `quickstart.md`, что соответствует принятой в проекте практике (`constitution.md`)
