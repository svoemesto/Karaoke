---
description: "Task list for 286-author-song-counts-cache implementation"
---

# Tasks: Кэш счётчиков песен автора в `tbl_authors`

**Input**: Design documents from `/home/nsa/Karaoke/specs/286-author-song-counts-cache/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/authors-tiles-api.md`, `quickstart.md`
**Tests**: Не генерируются — Constitution: «в CI тестов нет, существующие помечены `@Disabled`»; проверка через ручные сценарии в `quickstart.md`.

**Organization**: Задачи сгруппированы по user story (US1..US4 из `spec.md`) для независимой имплементации и валидации. Phase 2 (Foundational) — общий блок (миграция + новый SQL-метод), который покрывает US1/US2/US3/US4; Phase 3+ — финальная правка контроллеров и валидация.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: может выполняться параллельно (разные файлы, нет зависимостей).
- **[Story]**: задача относится к user story (US1/US2/US3/US4). Только в Phase 3+.
- Файл-пути указаны явно для каждой задачи.

## Path Conventions

Это backend-only фича для существующего multi-module Gradle-проекта:
- `deploy/karaoke-db/NN_*.sql` — SQL-миграции
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/...` — admin-слой (`karaoke-app` + модели данных)
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...` — публичный API-слой (`karaoke-web`)
- `docs/features/*.md` — per-feature документы (Constitution VI FR-009)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подтвердить окружение и существующие зависимости перед началом работ.

- [x] T001 Подтвердить наличие существующих миграций 01-43 в `deploy/karaoke-db/` (порядковый номер для новой миграции — 44)
- [x] T002 [P] Подтвердить регистрацию `AuthorsSyncTarget` в `SyncRegistry.all` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt:281`)
- [x] T003 [P] Подтвердить, что `StatBySong.consumeDirty()` подключён в `PublicApiController.getCachedAuthorsTiles()` (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:137`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Создать общий блок (SQL миграция + новый метод чтения), который покрывает US1, US2, US3, US4. Без этого блока user story задачи не могут стартовать.

**⚠️ CRITICAL**: Phase 3+ работы не могут начаться, пока этот phase не завершён.

- [x] T004 Создать SQL миграцию `deploy/karaoke-db/44_author_song_counts.sql` (ADD COLUMN `ready_songs_count`/`total_songs_count` + backfill одним UPDATE + пересоздание `update_tbl_authors_recordhash()` + backfill recordhash + функция и триггер `trg_tbl_songs_update_author_counts` по образцу `deploy/karaoke-db/27_author_special_order.sql`)
- [ ] T005 Применить миграцию `44_author_song_counts.sql` на LOCAL-БД через `psql -f` и проверить, что колонки созданы и триггер активен (запросы из `quickstart.md` шаг 1) — **требует одобрения пользователя** (изменение БД)
- [x] T006 [P] Добавить `data class AuthorTileRow(id, author, readySongsCount, totalSongsCount, isSpecialOrder)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` с KDoc и `@see specs/286-author-song-counts-cache`
- [x] T007 [P] Добавить `companion object fun Author.loadAuthorTilesWithCounts(onlyPublished, isSpecialOrder, database)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` — один SQL-запрос с условной фильтрацией (паттерн как `Author.loadIdsByNames`), возвращает `List<AuthorTileRow>` (depends on T006)

**Checkpoint**: Foundation ready — миграция применена, новый SQL-метод доступен, можно приступать к правке контроллеров.

---

## Phase 3: User Story 1 — Снижение нагрузки на БД при заходе в `/zakroma` (Priority: P1) 🎯 MVP

**Goal**: Эндпоинт `/api/public/authors-tiles` читает счётчики из `tbl_authors` одной выборкой вместо GROUP BY по `tbl_songs`.
**Independent Test**: `curl /api/public/authors-tiles?scope=main` 100 раз подряд — лог `karaoke-web` НЕ содержит `group by song_author` (SC-001), числа идентичны предыдущей реализации (SC-002).

### Implementation for User Story 1

- [x] T008 [US1] Заменить блок `Song.loadAuthorSongCounts(...) + Song.loadListAuthors(...) + Author.loadIdsByNames(...)` на `Author.loadAuthorTilesWithCounts(onlyPublished, isSpecialOrderFilter, WORKING_DATABASE)` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:272-313` (метод `authorsTiles()`) — маппинг одной строки в `AuthorTilePublicDto.fromAuthorName(...)` с `songCount = if (onlyPublished) row.readySongsCount else row.totalSongsCount` (depends on T007)

**Checkpoint**: US1 готов — `/api/public/authors-tiles` отвечает одной выборкой из `tbl_authors`, числа идентичны предыдущей реализации.

---

## Phase 4: User Story 2 — Автоматическая инвалидация счётчиков при изменении песен (Priority: P1)

**Goal**: Изменение статуса песни (`id_status` → 6 «готово») сбрасывает кэш `authorsTilesCache` на проде через `notifyStatsDirty()`.
**Independent Test**: Изменить `id_status` песни через админку → следующий запрос `/api/public/authors-tiles` отдаёт обновлённый `songCount` без ожидания TTL=30 мин (лог `[notifyStatsDirty` в karaoke-app.log).

### Implementation for User Story 2

- [x] T009 [US2] Добавить вызов `notifyStatsDirty()` после `Song.saveToDb()` при изменении `id_status` в эндпоинте обновления песни в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (рядом со строкой 3166, где уже есть аналогичный вызов для `free`-флага) — найти переменную, хранящую старое значение `id_status` до save, и добавить `if (newIdStatus != oldIdStatus) notifyStatsDirty()` (depends on T004, T008)

**Checkpoint**: US2 готов — изменение `id_status` песни триггерит cache invalidation на проде в течение одного sync-прохода.

---

## Phase 5: User Story 3 — Синхронизация счётчиков между LOCAL и SERVER (Priority: P1)

**Goal**: После sync LOCAL → SERVER значения `ready_songs_count`/`total_songs_count` и `recordhash` совпадают на обеих БД.
**Independent Test**: Изменить счётчик автора на LOCAL → sync → на SERVER значение совпадает с LOCAL; `recordhash` совпадает (SC-005).

### Implementation for User Story 3

- [ ] T010 Применить миграцию `44_author_song_counts.sql` на SERVER-БД через `psql -h <prod-host>` (требует одобрения пользователя согласно AGENTS.md «Машинно-специфичные исключения» / Constitution п.2 «Категорически запрещено»). Триггер на SERVER создаётся, но остаётся no-op (песен там нет) (depends on T005)
- [ ] T011 [P] Проверить флаги `sync_authors_push_update_allowed` и `sync_authors_pull_update_allowed` = `true` в `Karaoke.properties` на проде (должны быть `true` по умолчанию)
- [ ] T012 [US3] Запустить sync LOCAL → SERVER (`bash /home/nsa/Karaoke/deploy/do.sh sync` или эквивалентная команда, требует одобрения пользователя) и сравнить значения `ready_songs_count`/`total_songs_count`/`recordhash` для нескольких авторов между LOCAL и SERVER (depends on T010, T011)

**Checkpoint**: US3 готов — sync прокатывает счётчики и recordhash на SERVER; SC-005 satisfied.

---

## Phase 6: User Story 4 — Backfill существующих авторов при миграции (Priority: P2)

**Goal**: Миграция `44_author_song_counts.sql` корректно заполняет счётчики для всех существующих авторов за один проход.
**Independent Test**: `SELECT SUM(ready_songs_count) = SELECT COUNT(*) FROM tbl_songs WHERE id_status >= 6` (допуск ±1); аналогично для `total_songs_count` (SC-006).

### Implementation for User Story 4

- [ ] T013 [US4] Выполнить проверочные запросы из `quickstart.md` шаг 1 на LOCAL-БД: `SUM(ready_songs_count) = COUNT(*) FROM tbl_songs WHERE id_status >= 6`, отсутствие NULL в `ready_songs_count`/`total_songs_count`, корректность recordhash после backfill (depends on T005)

**Checkpoint**: US4 готов — backfill корректен для всех авторов на LOCAL; на SERVER сработает во время sync (US3).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Per-feature документ (Constitution VI FR-009), линтеры, сборка, end-to-end валидация.

- [x] T014 [P] Создать per-feature документ `docs/features/author-song-counts-cache.md` со ссылками на спеку, миграцию, описание триггера, изменения в `PublicApiController.authorsTiles()` и `ApiController.notifyStatsDirty()` (Constitution VI FR-006/009)
- [x] T015 [P] Добавить KDoc с `@see docs/features/author-song-counts-cache.md` к `fun Author.loadAuthorTilesWithCounts()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt` (Constitution VI FR-006)
- [x] T016 Запустить `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (обязательная проверка после изменения кода, AGENTS.md)
- [x] T017 [P] Запустить `./gradlew :karaoke-web:ktlintCheck` — никаких новых нарушений baseline
- [x] T018 [P] Запустить `cd webvue3 && npm run lint && npm run build && npm run format:check`
- [x] T019 [P] Запустить `cd karaoke-public && npm run lint && npm run build && npm run format:check`
- [x] T020 [P] Собрать Docker-образ webvue3: `cd deploy && bash do.sh build_webvue3`
- [x] T021 Собрать `karaoke-app:bootJar` через `./gradlew :karaoke-app:bootJar` (на `nsa-i9`/`nsa` разрешено без явного согласия пользователя — см. AGENTS.md «Машинно-специфичные исключения»)
- [x] T022 Собрать `karaoke-web:bootJar` через `./gradlew :karaoke-web:bootJar`
- [ ] T023 [P] Если менялся `karaoke-public` (или есть кросс-импорты с webvue3): собрать Docker-образ public: `cd deploy && bash do.sh build_public` (Pass 245 — multi-stage Dockerfile копирует только свой каталог; кросс-импорты падают внутри контейнера)
- [ ] T024 Прогнать ручные сценарии из `quickstart.md`: Шаг 1 (миграция) → Шаг 2 (8 тестов триггера) → Шаг 3 (5 тестов API) → Шаг 4 (sync LOCAL → SERVER) → Шаг 5 (cleanup)

**Checkpoint**: все обязательные проверки AGENTS.md после изменения кода выполнены, end-to-end валидация по `quickstart.md` пройдена.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: без зависимостей — можно начать сразу.
- **Phase 2 (Foundational)**: зависит от завершения Phase 1 — **БЛОКИРУЕТ** все user stories.
- **Phase 3 (US1)**: зависит от T007 (`loadAuthorTilesWithCounts` готов).
- **Phase 4 (US2)**: зависит от T004 (миграция с триггером) + T008 (контроллер использует новый метод).
- **Phase 5 (US3)**: зависит от T005 (миграция применена на LOCAL).
- **Phase 6 (US4)**: зависит от T005 (миграция применена на LOCAL).
- **Phase 7 (Polish)**: зависит от завершения всех предыдущих US (3, 4, 5, 6).

### User Story Dependencies

- **US1 (P1)**: может стартовать после T007 (Foundational). Нет зависимостей от других US.
- **US2 (P1)**: может стартовать после T004 + T008 (триггер из миграции + контроллер US1).
- **US3 (P1)**: может стартовать после T005 (миграция на LOCAL). Параллельно с US1/US2.
- **US4 (P2)**: может стартовать после T005 (миграция на LOCAL). Параллельно с US1/US2/US3.

### Within Each User Story

- Реализация → ручная валидация по `quickstart.md`.
- Миграция → применение → проверка backfill.
- Sync (US3) требует одобрения пользователя.

### Parallel Opportunities

- **Phase 1**: T001, T002, T003 — параллельно (read-only проверки).
- **Phase 2**: T006 + T007 — параллельно после T005; T004 отдельно.
- **Phase 7**: T014, T015, T017, T018, T019, T020, T023 — параллельно после T016. T016 → T021, T022 последовательно.
- US3 и US4 могут выполняться параллельно с US1/US2 (после T005).

---

## Parallel Examples

### Phase 2: Foundational (parallel checks)

```bash
# После T004 (создание миграции), T005 (применение на LOCAL) — параллельно:
Task: "T006 [P] data class AuthorTileRow in Author.kt"
Task: "T007 [P] fun Author.loadAuthorTilesWithCounts() in Author.kt"
```

### Phase 7: Polish (parallel linting/building)

```bash
# После T016 (compileKotlin), параллельно:
Task: "T017 [P] ./gradlew :karaoke-web:ktlintCheck"
Task: "T018 [P] cd webvue3 && npm run lint && npm run build && npm run format:check"
Task: "T019 [P] cd karaoke-public && npm run lint && npm run build && npm run format:check"
Task: "T020 [P] cd deploy && bash do.sh build_webvue3"
```

---

## Implementation Strategy

### MVP First (Phase 1 → Phase 2 → Phase 3 = User Story 1)

1. Phase 1 (Setup) — проверка окружения.
2. Phase 2 (Foundational) — миграция + новый SQL-метод. **Без этого US1 невозможен.**
3. Phase 3 (US1) — заменить 3 SQL на 1 в контроллере. **MVP готов: `/api/public/authors-tiles` работает без GROUP BY.**
4. **STOP и VALIDATE**: `curl` 100 раз + сравнение чисел с предыдущей реализацией.
5. Деплой/демо возможен.

### Incremental Delivery

1. Setup + Foundational → готовность foundation.
2. US1 → MVP (замена SQL).
3. US2 → cache invalidation при изменении `id_status`.
4. US3 → sync на SERVER.
5. US4 → backfill валидация.
6. Polish → per-feature документ, линтеры, bootJar, e2e.

### Parallel Team Strategy

С одним разработчиком — последовательно. С двумя:
- Developer A: Phase 1 + Phase 2 (T001..T007).
- Developer B: US1 (T008) после T007.
- Далее параллельно: US2 (T009), US3 (T010..T012), US4 (T013), затем Phase 7 (Polish).

---

## Notes

- [P] задачи = разные файлы, нет зависимостей — могут выполняться параллельно.
- [Story] метка (US1..US4) для traceability и возможности остановиться после любой стори.
- Каждая US независимо завершаема и валидируема по `quickstart.md`.
- На этой машине (`nsa-i9`/`nsa`) сборка `karaoke-app:bootJar` разрешена без согласия, перезапуск контейнера — нет.
- Применение миграции на SERVER (T010) и sync LOCAL → SERVER (T012) требуют явного согласия пользователя согласно Constitution п.2 «Категорически запрещено».
- Миграция применяется через `psql -f`, без перезапуска контейнера.