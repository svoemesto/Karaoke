---
description: "Task list for feature 235 — автозапуск «Синхронизации в 1 клик» каждые 3 часа"
---

# Tasks: 235 — Автозапуск «Синхронизации в 1 клик» каждые 3 часа

**Input**: Design documents from `/specs/235-auto-sync-3h/`
- [`spec.md`](./spec.md) — user stories P1/P2/P3
- [`plan.md`](./plan.md) — tech stack, structure
- [`research.md`](./research.md) — Spring `@Scheduled` patterns
- [`data-model.md`](./data-model.md) — entities, DTOs, KaraokeProperties
- [`contracts/api-contracts.md`](./contracts/api-contracts.md) — REST API
- [`quickstart.md`](./quickstart.md) — 7 validation scenarios

**Tests**: В CI нет (constitution §«Тесты»). Существующие `@Disabled`-тесты не запускаются. Проверка — пользователем по `quickstart.md`. Tests-phase в tasks **пропущен** явно (OPTIONAL по правилам шаблона + спецификация не запрашивала TDD).

**Branch**: `235-auto-sync-3h`

**Project layout** (см. `plan.md §Project Structure`): multi-module Gradle (Kotlin backend + Vue admin SPA). Karaoke-конвенции — см. `AGENTS.md` (≤100 строк governance), `CONTRIBUTING.md` (стиль), `DEVELOPMENT.md` (архитектура).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no deps)
- **[Story]**: `[US1]` / `[US2]` / `[US3]` (только в story-фазах)
- Setup / Foundational / Polish: **без** story-лейбла
- Каждый task — конкретный файл + действие, выполнимое LLM без дополнительного контекста

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Baseline для всего — добавить 3 property в `KaraokeProperties.kt` (нужны и для US1, и для US2, и для US3).

- [ ] T001 Добавить 3 записи `KaraokeProperty` (`autoOneClickSyncEnabled=true`, `autoOneClickSyncIntervalMs=10800000L`, `autoOneClickSyncInitialDelayMs=300000L`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` рядом с `editorAssignmentDefaultTarget` (~строка 319). KDoc на каждую запись с `@see` ссылкой на будущий `livedocs/features/235-auto-sync-3h.md` (см. Polish T037).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Каркас, без которого ни одна user story не работает. Backend-классы (scheduler, run, controller, DTO) + UI store action.

**⚠️ CRITICAL**: US1/US2/US3 не стартуют, пока не закрыты T002..T008.

- [ ] T002 [P] Создать value-класс `AutoOneClickSyncRun` (поля: `startedAt: Instant`, `finishedAt: Instant?`, `status: String`, `reason: String?`, `totals: Totals`, `perTarget: List<SyncOneClickResultDto>`) + вложенный `Totals(created, updated, deleted, moved: Int)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncRun.kt`. KDoc с `@see livedocs/features/235-auto-sync-3h.md` (см. T037).
- [ ] T003 [P] Создать 3 DTO: `AutoOneClickSyncStatusDto`, `AutoOneClickSyncRunDto`, `TotalsDto` (поля по `data-model.md §4-5`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/dto/AutoOneClickSyncDtos.kt`. KDoc с `@see`.
- [ ] T004 [P] Создать singleton `@Component AutoOneClickSyncScheduler` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/AutoOneClickSyncScheduler.kt`. Поля: `@Volatile var lastRunMs: Long = 0L`, `private val running: AtomicBoolean = AtomicBoolean(false)`, `private val history: ConcurrentLinkedDeque<AutoOneClickSyncRun> = ConcurrentLinkedDeque()`. Метод `getStatus(): AutoOneClickSyncStatusDto` (читает `KaraokeProperties` + `history`, возвращает DTO; вычисляет `nextRunEstimate = if (lastRun != null) lastRun.finishedAt + intervalMs else appStartTime + initialDelayMs`). **Без** `@Scheduled`-метода пока (добавится в T005). KDoc с `@see`.
- [ ] T005 В том же `AutoOneClickSyncScheduler.kt` добавить `@Scheduled(fixedDelay = 60_000L, initialDelay = 5_000L) fun tick()` по псевдокоду из `research.md §2`. Тело: `if (!KaraokeProperties.getBoolean("autoOneClickSyncEnabled")) return` → `if (now - lastRunMs < intervalMs) return` → `if (!running.compareAndSet(false, true)) return` → per-target `for { try { … } catch(Throwable) { log+record } }` → внешний `try { … } catch(Throwable) { FAILED + reason }` → `finally { history.addLast(run); if (history.size > 10) history.pollFirst(); running.set(false); lastRunMs = now }`. Per-target: `if (target.oneClickDirection == null) skippedResult else runEntitySync(target.key, target.oneClickDirection)`. Логи: `[AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)` при старте если выключено; `[AutoOneClickSyncScheduler] tick=<ISO> RUNNING/SUCCESS/FAILED totals=…` на тиках; `[AutoOneClickSyncScheduler] target=<key> failed: <message>` на per-target падениях. Использует `org.slf4j.LoggerFactory.getLogger(...)` (как в `SponsrSyncScheduler.kt`). `intervalMs = KaraokeProperties.getLong("autoOneClickSyncIntervalMs").coerceAtLeast(60_000L)`.
- [ ] T006 [P] Создать `@RestController AutoOneClickSyncStatusController` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/AutoOneClickSyncStatusController.kt` с одним endpoint `GET /api/sync/auto-status`, возвращающим `scheduler.getStatus()` (`AutoOneClickSyncStatusDto`). KDoc с `@see`. `permitAll()` (как у остальных `/api/sync/*`).
- [ ] T007 [P] Модифицировать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt:5284` (`postSyncOneClick`): обернуть тело в `if (scheduler.running.compareAndSet(false, true)) { try { … existing body … } finally { scheduler.running.set(false) } } else { return ResponseEntity.status(409).body(mapOf("error" to "sync_in_progress", "message" to "Автосинхронизация уже выполняется в фоне, дождитесь завершения")) }`. Добавить `@Autowired` (или constructor injection) `AutoOneClickSyncScheduler` в `ApiController`.
- [ ] T008 [P] Модифицировать `webvue3/src/components/Sync/store.js`: добавить `loadSyncAutoStatusPromise(ctx)` action (GET `/api/sync/auto-status`, парсит JSON, кладёт в state через `setSyncAutoStatus`), добавить `getSyncAutoStatus` getter, `setSyncAutoStatus` mutation, начальное `state.autoStatus = null`. JSDoc с `@see` (как у `loadSyncEntitiesPromise`).

**Checkpoint**: backend компилируется (`./gradlew karaoke-app:compileKotlin`), UI store готов. Можно стартовать US1.

---

## Phase 3: User Story 1 — Автозапуск «Синхронизации в 1 клик» каждые 3 часа (Priority: P1) 🎯 MVP

**Goal**: Существующая бизнес-логика `POST /api/sync/oneclick` запускается автоматически каждые `autoOneClickSyncIntervalMs` (default 3 ч). При `enabled=true` фича работает «из коробки».

**Independent Test** (по `quickstart.md Сценарий 1`):
1. Установить `autoOneClickSyncIntervalMs = 60_000` через UI Properties; `autoOneClickSyncEnabled = true`.
2. Перезапустить `karaoke-app`.
3. Подождать ≥ 5 мин (initialDelay).
4. `tail -F karaoke-app.log | grep AutoOneClickSyncScheduler` → `[AutoOneClickSyncScheduler] tick=… SUCCESS totals=…`.
5. Сделать правку в `tbl_songs` SERVER-БД; подождать 1 мин; в LOCAL-БД правка появилась.

**Зависит от**: Phase 2 (T002..T008) полностью.

### Implementation for User Story 1

- [ ] T009 [US1] (зависит от T005) — **верификация вручную**: запустить `karaoke-app`, дождаться первого автотика, проверить, что в логах появилась запись `[AutoOneClickSyncScheduler] tick=... SUCCESS` или `FAILED` (в зависимости от состояния БД). Если тика нет — debug: проверить, что `@EnableScheduling` в `KaraokeAppApplication.kt:18` и `ConcurrentTaskScheduler` bean зарегистрирован; проверить, что `autoOneClickSyncEnabled=true` в `Karaoke.properties` (через `getWebvueProp('autoOneClickSyncEnabled')` или `KaraokeProperties.getBoolean(...)`); добавить `log.info("scheduler bean loaded")` в `@PostConstruct` для диагностики.
- [ ] T010 [US1] (зависит от T007) — **верификация вручную**: проверить, что `ApiController.postSyncOneClick` возвращает 409 Conflict. Сценарий: установить `autoOneClickSyncIntervalMs = 30_000`, дождаться начала автотика (`RUNNING` в логах), **сразу** `curl -X POST http://localhost:8080/api/sync/oneclick` → должен вернуть `{"error":"sync_in_progress", …}` с HTTP 409. Если нет — debug: проверить, что `scheduler.running` инжектится (или `static` singleton bean) и `compareAndSet` ловит гонку.
- [ ] T011 [US1] — **верификация FR-002/FR-003 (3 ч, initialDelay 5 мин, fixedDelay от завершения)**: установить `autoOneClickSyncIntervalMs = 10_000` (10 сек, для ускорения); в логах подтвердить, что тики идут с интервалом `≥ 10 сек` от завершения предыдущего (не точно 10 сек, если sync длинный). Также подтвердить, что первый тик появляется через `≥ 5 мин` после старта (временно установить `autoOneClickSyncInitialDelayMs = 5_000` для теста).
- [ ] T012 [US1] — **верификация FR-016 / SC-009 (fail-fast при сбое БД)**: запустить karaoke-app, дождаться 1 SUCCESS; `docker stop karaoke-db-local`; подождать 1 мин; в логах должно быть `[AutoOneClickSyncScheduler] tick=… FAILED` с полным стеком `SQLException`; `docker start karaoke-db-local`; следующий тик — `SUCCESS` (scheduler **не остановлен**). См. `quickstart.md Сценарий 5`.

**Checkpoint**: User Story 1 полностью функциональна и независимо тестируема.

---

## Phase 4: User Story 2 — Админ может отключить автозапуск (Priority: P2)

**Goal**: Настройка `autoOneClickSyncEnabled=false` останавливает автозапуск; ручная кнопка продолжает работать.

**Independent Test** (по `quickstart.md Сценарий 3`):
1. `autoOneClickSyncEnabled = false` через UI Properties.
2. Перезапустить `karaoke-app`.
3. В логах при старте: `[AutoOneClickSyncScheduler] disabled by config (autoOneClickSyncEnabled=false)`.
4. Подождать ≥ 1 тик (с дефолтным интервалом 3 ч — неделя, для теста выставить `intervalMs=30_000`).
5. В логах **нет** записей `tick=…`.
6. Кликнуть «🔄 Синхронизация в 1 клик» в UI → работает (200 OK).

**Зависит от**: US1 (T009..T012). Логика `enabled` уже реализована в `tick()` (T005), но требует ручной верификации.

### Implementation for User Story 2

- [ ] T013 [US2] (зависит от T009) — **верификация US2 AC1/AC2**: установить `autoOneClickSyncEnabled = false`, перезапустить, дождаться 1 цикла тика. Проверить, что в логах **нет** записей `tick=…` (только `disabled by config` при старте). Проверить, что `curl -X POST /api/sync/oneclick` возвращает 200 OK (ручной клик работает, scheduler.running не задействован, так как scheduler вообще не тикает).
- [ ] T014 [US2] (зависит от T013) — **верификация US2 AC3 (re-enable)**: вернуть `autoOneClickSyncEnabled = true`, перезапустить, дождаться 1 цикла тика. Проверить, что автозапуск возобновился (`tick=… SUCCESS` в логах). Подтвердить, что `running` lock из US1 не «залип» в `true` — если есть сомнения, добавить лог `log.info("scheduler.running=${scheduler.running.get()}")` в `@PostConstruct` для верификации.

**Checkpoint**: User Stories 1 AND 2 работают независимо.

---

## Phase 5: User Story 3 — UI-блок «Автозапуск» на странице `/sync` (Priority: P3)

**Goal**: Админ открывает `/sync` и сразу видит: `enabled`/`disabled`, `lastRun` (время + сводка), `nextRunEstimate`, история пропусков/сбоев.

**Independent Test** (по `quickstart.md Сценарий 4`):
1. После прохождения US1 (≥ 1 автотик прошёл).
2. Открыть `http://localhost:5173/sync`.
3. В верхней части страницы (над таблицей `SyncTable`) — блок «Автозапуск» с полями.
4. Проверить `enabled`, `lastRun.startedAt`, `lastRun.totals`, `nextRunEstimate`.
5. F5 — блок обновляется.

**Зависит от**: US1 (T009..T012). UI-блок читает данные через `loadSyncAutoStatusPromise` (T008).

### Implementation for User Story 3

- [ ] T015 [P] [US3] (зависит от T008) — добавить в `webvue3/src/components/Sync/SyncTable.vue` новый `<template>`-блок «Автозапуск» **перед** `<table class="sync-table">` (т.е. между кнопкой `🔄 Синхронизация в 1 клик` и таблицей сущностей). Содержимое:
  - Заголовок: `<h4>Автозапуск</h4>`
  - Статус: `✅ включён` / `❌ выключен` (привязка к `getSyncAutoStatus.enabled`)
  - Интервал: `${(intervalMs / 3600000).toFixed(1)} ч (${intervalMs} мс)`
  - Начальная задержка: `${(initialDelayMs / 60000).toFixed(1)} мин (${initialDelayMs} мс)`
  - Последний запуск: `lastRun.startedAt` (формат через `new Date(lastRun.startedAt).toLocaleString('ru-RU')`), `lastRun.status`, краткая сводка `добавлено X, изменено Y, удалено Z`. Если `lastRun === null` — «ещё не было». Если `lastRun.status === 'FAILED'` — показать `lastRun.reason` красным.
  - Следующий (оценка): `nextRunEstimate` (через `new Date(nextRunEstimate).toLocaleString('ru-RU')`); если `nextRunEstimate === null` (enabled=false) — скрыть.
  - JSDoc на новый sub-компонент (если выделяется в `:is="AutoOneClickSyncBlock"`) со ссылкой на LiveDoc (T037).
- [ ] T016 [US3] (зависит от T015) — в `webvue3/src/components/Sync/SyncTable.vue` добавить в `mounted()` после `this.$store.dispatch('loadSyncEntitiesPromise')` строку `this.$store.dispatch('loadSyncAutoStatusPromise')`. JSDoc.
- [ ] T017 [US3] (зависит от T016) — **верификация US3 AC1**: после ≥ 1 автотика, открыть `/sync`, проверить, что блок отображает `lastRun.startedAt` (не `null`), `lastRun.totals`. F5 — данные обновляются.
- [ ] T018 [US3] (зависит от T017) — **верификация US3 AC2** (UI при свежем старте): `autoOneClickSyncIntervalMs = 3600000` (1 ч), перезапустить `karaoke-app`, **сразу** открыть `/sync` — блок показывает «Последний запуск: ещё не было». Через 1 час — `lastRun` заполнен.
- [ ] T019 [US3] (зависит от T018) — **верификация US3 AC3** (UI показывает пропуски): запустить `autoOneClickSyncIntervalMs = 30_000`, дождаться первого тика. Запустить второй тик (через 30 сек). Если первый ещё выполняется — в логах `[AutoOneClickSyncScheduler] skipped — previous run still in progress` (должно быть в `history.reason`). В UI блоке `lastRun.reason` = `previous run still in progress`.

**Checkpoint**: Все три user story работают независимо.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация (FR-014 NON-NEGOTIABLE), финальная верификация, опциональные улучшения.

- [ ] T020 (зависит от T019) — **FR-014 (NON-NEGOTIABLE)**: создать `livedocs/features/235-auto-sync-3h.md` (новый LiveDoc) с frontmatter (`status: Active`, `slug: 235-auto-sync-3h`, `related:` ссылки на `domain/catalog.md`, `architecture/data-sync.md`, `specs/235-auto-sync-3h/spec.md`). Содержимое: «Что делает» (1-2 строки), «Почему», «Что изменилось» (таблица before/after), User Stories (краткий список с приоритетами), Functional Requirements (указатель на spec.md), Acceptance Criteria (чеклист), «Связанные LiveDocs», «Код» (список изменённых/новых файлов с file:line), «История» (создан/обновлён).
- [ ] T021 (зависит от T020) — добавить запись в `livedocs/architecture-notes.md` в секцию последнего Pass (Pass 63+) с однострочным описанием фичи и ссылкой на LiveDoc. Формат: `Pass 63+: 235-auto-sync-3h — автозапуск «Синхронизации в 1 клик» каждые 3 ч. LiveDoc: [livedocs/features/235-auto-sync-3h.md](../livedocs/features/235-auto-sync-3h.md).`
- [ ] T022 [P] (зависит от T021) — запустить `bash tools/check-livedocs-structure.sh` локально → должно быть `0` failures. Если `≥5 фич` в `livedocs/features/` — уже выполнено (проверить счётчик до и после T020).
- [ ] T023 [P] (зависит от T022) — запустить `bash tools/check-livedocs-cross-links.sh` локально → `0` failures. Все cross-links из нового LiveDoc (FR-014) валидны.
- [ ] T024 [P] (зависит от T014) — **Code Standards (FR-006, FR-007)**: убедиться, что все новые публичные API (`AutoOneClickSyncScheduler`, `AutoOneClickSyncRun`, `AutoOneClickSyncStatusController`, 3 DTO, Vue sub-block, Vuex action) имеют KDoc/JSDoc с `@see livedocs/features/235-auto-sync-3h.md`. Запустить `./gradlew ktlintCheck` и `cd webvue3 && npx eslint src/components/Sync/`. **Цель**: 0 новых нарушений в `baseline-*.xml` / `.eslint-baseline.json`.
- [ ] T025 (зависит от T019) — **финальный quickstart-run** (см. `quickstart.md`): пройти все 7 сценариев, записать результат в `docs/features/235-auto-sync-3h.md` (если выделяется) или в PR-description. Особенно: **Сценарий 7** (метрика 8 тиков/24ч) — можно проверить за 1 час с `intervalMs=45000` (получить 80 тиков), экстраполировать. Или принять на веру по результатам Сценария 1.
- [ ] T026 [P] (зависит от T020) — **FR-009 обновление списка фич**: если в `livedocs/features/INDEX.md` (или аналог) есть auto-generated список фич — обновить. Проверить `bash tools/check-livedocs-structure.sh` ещё раз после T022.
- [ ] T027 (зависит от T019, опционально) — **добавить unit-тест** в `karaoke-app/src/test/.../AutoOneClickSyncSchedulerTest.kt` для проверки логики `intervalMs = getLong(...).coerceAtLeast(60_000L)` (если значение в property меньше 60_000, должно приводиться к 60_000). **Примечание**: по constitution §«Тесты» CI-тестов нет; этот unit-тест НЕ будет запускаться в CI, но поможет при ручном рефакторинге. **OPTIONAL** — можно пропустить, если scope не позволяет.
- [ ] T028 (зависит от T019, опционально) — **README/DEVELOPMENT.md**: если в `DEVELOPMENT.md` есть секция «Список scheduler'ов» — добавить туда `AutoOneClickSyncScheduler` с однострочным описанием. Поиск: `grep -n "SponsrSyncScheduler\|VkAutoPublishScheduler" DEVELOPMENT.md`. **OPTIONAL**.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 — без зависимостей, можно стартовать немедленно.
- **Foundational (Phase 2)**: T002..T008 — зависят от T001 (свойства должны быть в `KaraokeProperties.kt` до того, как их начнёт читать scheduler). **Блокируют** все user stories.
- **User Stories (Phase 3-5)**: US1, US2, US3 — зависят от Foundational. Между собой — US2 зависит от US1 (нужен логирующий scheduler, чтобы US2 смог проверить его отсутствие при `enabled=false`); US3 зависит от US1 (нужен заполненный `lastRun`, чтобы проверить отображение в UI). Все stories **могут** стартовать параллельно после Foundational, но для **ручной верификации** US2 → US1 → US3 последовательно.
- **Polish (Phase 6)**: T020..T028 — зависят от всех user stories (нужны рабочие scheduler + UI + log-строки, на которые ссылается LiveDoc).

### User Story Dependencies (граф)

```
T001 (Setup)
  ↓
T002..T008 (Foundational) — все [P] кроме T005, T007
  ↓
T009..T012 (US1: P1) — последовательная верификация
  ↓
T013, T014 (US2: P2) — последовательная верификация после US1
  ↓
T015, T016 [P] (US3: P3) — параллельные правки в разных местах
  ↓
T017..T019 (US3 verification) — последовательная верификация
  ↓
T020..T028 (Polish) — T020-T024 последовательно, T022-T024, T026 [P] параллельно, T027-T028 опциональны
```

### Within Each User Story

- **Тесты не пишутся** (CI-тестов нет, constitution §«Тесты»; проверка — пользователем по `quickstart.md`).
- **Implementation до verification** — T005 (scheduler-метод) пишется до T009 (верификация), чтобы было что верифицировать.
- **Backend до UI** — T005, T007 (backend) до T015, T016 (UI), чтобы UI мог читать данные из реального endpoint.
- **Verification последней** — каждая story завершается верификацией (T009-T012, T013-T014, T017-T019), которые могут выявить баги и вернуть к правке implementation-tasks.

### Parallel Opportunities

| Группа | Задачи | Файлы |
|---|---|---|
| **Foundational создание классов** | T002, T003, T004, T006, T008 | 5 разных файлов, нет зависимостей между ними |
| **Foundational backend ↔ UI** | T002-T007 (backend) ‖ T008 (UI store) | backend Kotlin ≠ frontend JS |
| **US3 implementation** | T015 (UI block), T016 (mounted hook) | Один файл `SyncTable.vue`, но разные секции; можно править одной правкой — НЕ [P] (см. шаблон: «разные файлы, нет зависимостей» — здесь один файл) |
| **Polish** | T022, T023, T024, T026 | 4 разных lint-скрипта, независимы |

### Within-Story Parallel Example (User Story 3)

```bash
# US3 implementation — фактически последовательно (один файл SyncTable.vue):
# T015: добавить <template>-блок «Автозапуск»
# T016: добавить this.$store.dispatch('loadSyncAutoStatusPromise') в mounted()
# Обе правки в одном файле — НЕ [P].
# 
# T017, T018, T019 — последовательная ручная верификация, не код.
```

### Cross-Story Parallel Example

```bash
# После завершения Phase 2 (Foundational) три story могут идти параллельно
# (если есть несколько разработчиков), но для одного разработчика
# рекомендуется порядок: US1 → US2 → US3 (P1 → P2 → P3).
# 
# US1: 4 задачи верификации, ~30 мин реального времени (с intervalMs=30_000)
# US2: 2 задачи верификации, ~10 мин
# US3: 5 задач (2 код + 3 верификация), ~45 мин
```

---

## Implementation Strategy

### MVP First (User Story 1 Only) — рекомендуемый путь

1. **Phase 1** (T001): +1 файл правки (`KaraokeProperties.kt`).
2. **Phase 2** (T002..T008): 4 новых backend-файла + 1 правка (`ApiController.kt`) + 1 правка (`store.js`). Самый объёмный этап.
3. **Phase 3** (T009..T012): только верификация US1. **STOP and VALIDATE**: пройти `quickstart.md` Сценарий 1. Если работает — это **MVP**: автозапуск уже тикает, ручной клик получает 409, scheduler не падает.
4. **Deploy/demo if ready** — можно продемонстрировать пользователю до закрытия US2/US3.

### Incremental Delivery

1. **Setup + Foundational** → Foundation ready (Phase 1 + 2).
2. **+ US1** → MVP! Автозапуск работает; можно деплоить и тестировать с реальными данными.
3. **+ US2** → Можно выключать фичу (важно для миграций БД). Тест.
4. **+ US3** → Видимость в UI. Тест.
5. **+ Polish** → LiveDoc + lint. Готово к merge.

### Parallel Team Strategy

С одним разработчиком — последовательно (US1 → US2 → US3). С 2+ разработчиками:
- **Dev A**: Foundational (Phase 2) целиком.
- После Foundational — **Dev A**: US1 verification; **Dev B**: US3 implementation (T015, T016).
- После US1 — **Dev A**: US2; **Dev B**: US3 verification.
- **Dev A**: Polish (T020..T024); **Dev B**: опциональные T027, T028.

---

## Notes

- **Тесты не пишутся** по constitution §«Тесты» (CI-тестов в проекте нет; `@Disabled` существующие). Вся верификация — вручную через `quickstart.md`.
- **[P] tasks** = разные файлы + нет зависимостей. T015 + T016 (оба про `SyncTable.vue`) — НЕ [P], хоть и в одной story.
- **Каждая story независимо завершаема** — US2 не сломается, если US3 ещё не сделан; US3 работает, даже если US2 не реализован (но `enabled` чекбокс в US3 — это просто отображение, не переключатель).
- **Verify before commit** — после T008, T012, T014, T019, T024 — точка верификации, можно коммитить (если пользователь явно попросит — agent НЕ коммитит, см. AGENTS.md).
- **Avoid**: изменение существующих sync-флагов в `KaraokeProperties.kt` (те 32 флага `sync_*_*_*_allowed`); правка `runEntitySync` в `Utils.kt:629` (переиспользуется как есть); добавление новой БД-таблицы (in-memory, constitution §«БД» не разрешает ad-hoc таблицы).
- **Не делать (out of scope)**: 
  - `nginx`/`docker-compose` правки (фича только в `karaoke-app` + `webvue3`).
  - Persist'енция истории в БД (A-007, in-memory by design).
  - SSE-push для UI-обновления (Q2 в Clarifications — REST-only by design).
  - Cluster lock (Karaoke-app — desktop, однопроцессный).
  - Новые `sync_*` properties — `autoOneClickSync*` достаточно.
  - Изменение `SyncRegistry.all` (конституция III NON-NEGOTIABLE — только через новые `SyncTarget<T>`-классы, не в скоупе).
- **FR-014 NON-NEGOTIABLE**: T020 + T021 + T022 + T023 обязательны до merge. Без них CI `tools/check-livedocs-*.sh` упадёт.
- **Constitution VI (Code Standards)**: T024 обязателен; новый код не должен увеличивать baseline-файлы.
