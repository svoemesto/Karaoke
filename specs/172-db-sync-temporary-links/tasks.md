---

description: "План задач для синхронизации временных ссылок"
---

# Tasks: Временные ссылки в синхронизации БД

**Input**: Design documents from `/specs/172-db-sync-temporary-links/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовить границы изменения существующего механизма синхронизации.

- [x] T001 Проверить текущий контракт `KaraokeDbTable` и generic sync target перед добавлением модели в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/`
- [x] T002 [P] Сопоставить полный набор колонок `tbl_song_share_links` из `deploy/karaoke-db/38_song_share_links.sql` и `deploy/karaoke-db/39_song_share_recordhash.sql` с полями будущей модели
- [x] T003 [P] Проверить существующие правила per-feature документации в `docs/features/dual-db-sync.md` и требования KDoc из `.specify/memory/constitution.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Зафиксировать безопасные общие правила перед реализацией пользовательских сценариев.

- [x] T004 Уточнить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`, что `GenericKaraokeDbTableSyncTarget` поддерживает nullable-поля, generated identity и связи временной ссылки без специального контроллера
- [x] T005 [P] Определить безопасные значения по умолчанию для восьми свойств `sync_sharelinks_<push|pull>_<insert|update|delete|move>_allowed` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
- [x] T006 [P] Зафиксировать публичные поля, допустимые в label и результате sync, исключив `token_hash` и session hashes, в `specs/172-db-sync-temporary-links/data-model.md` и `specs/172-db-sync-temporary-links/contracts/sync-temporary-links.md`

**Checkpoint**: Общие правила модели, разрешений и защиты секретных значений определены.

---

## Phase 3: User Story 1 - Синхронизация временных ссылок с сервера (Priority: P1) 🎯 MVP

**Goal**: Временные ссылки участвуют в one-click sync с направлением SERVER → LOCAL и корректно обрабатывают insert/update/delete без дубликатов.

**Independent Test**: На сервере создать, изменить и отозвать/удалить тестовую ссылку, запустить one-click без ручного выбора направления и проверить локальную БД, отсутствие дублей и безопасный summary.

### Implementation for User Story 1

- [x] T007 [P] [US1] Создать модель `SongShareLink` для `tbl_song_share_links` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShareLink.kt` с точными nullable/non-nullable типами и `TABLE_NAME`
- [x] T008 [US1] Реализовать в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShareLink.kt` контракт `KaraokeDbTable`, загрузку строк и SQL для insert/update/diff без раскрытия token-related полей в пользовательских представлениях
- [x] T009 [US1] Добавить `ShareLinksSyncTarget` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt` с key `sharelinks`, таблицей `tbl_song_share_links`, display name «Временные ссылки», `SERVER_TO_LOCAL` и подходящим размером batch
- [x] T010 [US1] Зарегистрировать `ShareLinksSyncTarget` в `SyncRegistry.all` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`
- [x] T011 [US1] Добавить восемь sync properties для `sharelinks` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`, оставив destructive/push операции выключенными по умолчанию
- [x] T012 [US1] Проверить и при необходимости скорректировать формирование entity summary и результата операций в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`, чтобы секретные поля не попадали в UI/logs
- [x] T013 [US1] Проверить динамическое отображение новой сущности, направления и флагов в `webvue3/src/components/Sync/SyncTable.vue` без добавления отдельного hardcoded списка
- [x] T014 [US1] Добавить KDoc с `@see docs/features/dual-db-sync.md` для новых публичных Kotlin-символов в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongShareLink.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt`

**Checkpoint**: User Story 1 полностью работает через `/api/sync/entities` и `/api/sync/oneclick`, а ручной quickstart-сценарий server → local проходит.

---

## Phase 4: User Story 2 - Выбор обратного направления (Priority: P2)

**Goal**: Администратор может явно запустить local → server для временных ссылок, не изменяя безопасный one-click default.

**Independent Test**: Включить необходимые push flags, создать/изменить ссылку на локали, явно запустить entity sync local → server и проверить серверную БД.

### Implementation for User Story 2

- [x] T015 [US2] Проверить обработку `direction=LOCAL_TO_SERVER` для `key=sharelinks` в существующем endpoint `/api/sync/run` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
- [x] T016 [US2] Проверить, что `/api/sync/setflag` сохраняет независимые push/pull operation flags для `sharelinks` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
- [x] T017 [US2] Проверить отображение явного направления local → server и блокировку неразрешённых операций в `webvue3/src/components/Sync/SyncTable.vue`
- [x] T018 [US2] Выполнить сценарии update/delete/move local → server из `specs/172-db-sync-temporary-links/quickstart.md`, не раскрывая секретные поля в результатах

**Checkpoint**: User Stories 1 и 2 независимо проверяемы; one-click остаётся server → local, а local → server требует явного выбора и разрешений.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Документация, валидация и контроль регрессий.

- [x] T019 [P] Обновить `docs/features/dual-db-sync.md` описанием `sharelinks`, исключения `tbl_song_share_sessions`, направления по умолчанию и правил безопасных summary
- [x] T020 [P] Добавить или обновить проверку mapping/hash/diff для `SongShareLink` в `karaoke-app/src/test/` с использованием изолированного тестового окружения, если текущие тестовые фикстуры поддерживают обе БД
- [x] T021 Проверить отсутствие утечки token/session hashes в sync summary и логах по `specs/172-db-sync-temporary-links/contracts/sync-temporary-links.md`
- [x] T022 Выполнить `./gradlew ktlintCheck` и `./gradlew :karaoke-app:compileKotlin`, устранить только новые нарушения
- [x] T023 Выполнить все runnable-сценарии из `specs/172-db-sync-temporary-links/quickstart.md` и зафиксировать результаты перед PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001–T003 можно начать сразу; T002 и T003 параллельны.
- **Foundational (Phase 2)**: зависит от T001; T005 и T006 могут выполняться параллельно с T004.
- **User Story 1 (Phase 3)**: зависит от Phase 2; T007–T008 должны завершиться до T009–T012, T013 может идти параллельно с backend-регистрацией.
- **User Story 2 (Phase 4)**: зависит от завершения US1, потому что использует зарегистрированный `sharelinks` target.
- **Polish (Phase 5)**: T019–T021 после US1/US2; T022–T023 после изменений.

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational; независима от US2.
- **User Story 2 (P2)**: после US1; расширяет тот же target и проверяет обратное направление.

### Parallel Opportunities

- T002, T003 и T005/T006 — разные документы/области и могут выполняться параллельно.
- После создания модели T013 может выполняться параллельно с T009–T012.
- T019 и T020 — разные файлы и могут выполняться параллельно.
- В одной рабочей копии задачи, изменяющие `SyncTarget.kt` или `KaraokeProperties.kt`, нужно сериализовать во избежание конфликтов.

## Parallel Example: User Story 1

```text
После T007–T008:
Task A: T009–T010 — target и регистрация в SyncTarget.kt
Task B: T011 — 8 properties в KaraokeProperties.kt
Task C: T013 — проверка/минимальная корректировка SyncTable.vue
После завершения A/B/C: T012 и T014
```

## Parallel Example: User Story 2

```text
После US1:
Task A: T015 — endpoint run direction
Task B: T016 — setflag и конфигурация разрешений
После A/B: T017, затем T018
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Выполнить Setup и Foundational.
2. Реализовать модель, target, registry и permissions для US1.
3. Проверить server → local через one-click и повторный запуск.
4. Остановиться на checkpoint и продемонстрировать MVP.

### Incremental Delivery

1. US1: безопасный server → local one-click.
2. US2: явный local → server при включённых разрешениях.
3. Polish: документация, проверки, quickstart и lint/compile gate.
