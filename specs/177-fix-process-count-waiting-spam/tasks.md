# Tasks: устранение спама PROCESS_COUNT_WAITING в SSE-канале

**Input**: Design documents from `/specs/178-fix-process-count-waiting-spam/` (ветка 178 — `tools/reserve-branch-number.sh` обнаружил, что `177` уже зарезервирован предыдущей сессией; имя спеки оставлено `177-...` для согласованности со ссылками).
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/sse-payload.md ✅, quickstart.md ✅
**Tests**: не запрошены в спеке → не генерируются (constitution.md § «Тесты»: в CI нет; ручная проверка на admin-машине через quickstart.md).
**Organization**: по user stories (P1 → P2). MVP = US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллелить (разные файлы, нет зависимостей).
- **[Story]**: метка user story для трассировки (`[US1]`, `[US2]`).
- Указывать абсолютные пути или от корня репо.

## Path Conventions

Проект — Gradle multi-module Spring Boot (Kotlin) + Vue 3 SPA.
Затрагиваемые файлы лежат в:

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Скрипты: `tools/reserve-branch-number.sh`
- Документация: `docs/architecture-notes.md`, `docs/features/async-process-queue.md`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: резервирование feature-ветки и подтверждение окружения (AGENTS.md § «CI-gate для master», «Нумерация feature-веток»).

- [x] T001 Зарезервировать номер ветки и создать feature-ветку `178-fix-process-count-waiting-spam` через `./tools/reserve-branch-number.sh` (см. AGENTS.md § «Нумерация feature-веток» — обязательно атомарно, не вручную)
- [x] T002 [P] Убедиться, что сборка karaoke-app проходит локально: `./gradlew :karaoke-app:compileKotlin ktlintCheck` — нужен зелёный baseline перед изменениями

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: подтвердить, что блокирующих prerequisites нет.

**⚠️ CRITICAL**: фича локальная (1 файл + 1 поле), блокирующих задач перед US1 нет.

- [x] T003 Прочитать целиком `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` (1184 строк) и убедиться, что найдены все 5 call-sites `sendCountWaitingMessage` (см. `research.md` § 1). Без этого шага US1 нельзя валидировать: `grep -n sendCountWaitingMessage karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`

**Checkpoint**: foundation ready — `KaraokeProcessWorker.kt` прочитан, call-sites подтверждены, можно начинать US1.

---

## Phase 3: User Story 1 — Подавление дублей PROCESS_COUNT_WAITING (Priority: P1) 🎯 MVP

**Goal**: при простое админки в SSE-канал `/api/subscribe` больше не уходит дублирующих одинаковых `PROCESS_COUNT_WAITING`; реальные изменения доходят в течение 1–2 секунд.

**Independent Test**: открыть DevTools → Network → EventStream на любой странице админки. За 5 минут простоя при пустой очереди — **0** сообщений `PROCESS_COUNT_WAITING` (SC-001). Запустить длительное задание → ровно **1** сообщение с ненулевым `countWaiting` в течение 1–2 секунд (SC-002).

### Implementation for User Story 1

- [x] T004 [US1] Добавить поле `@Volatile private var lastSentCountWaiting: Long? = null` в companion object `KaraokeProcessWorker` рядом с `@Volatile var isWork` (строка ~514 в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`). KDoc с `@see docs/features/async-process-queue.md` обязателен (constitution FR-006). Источник истины — см. `data-model.md` § «LastSentCountWaiting»
- [x] T005 [US1] Переписать `sendCountWaitingMessage(countWaiting: Long)` (строка ~710) — добавить проверку `previous == countWaiting` → early return (FR-001). Полный код-образец — в `research.md` § 2 «Decision». Сохранить существующий try/catch вокруг `SNS.send` и существующий println
- [x] T006 [US1] В `start()` (строка ~612) сбросить `lastSentCountWaiting = null` ПЕРЕД первым вызовом `sendCountWaitingMessage(...)` после `deleteDone`/`setWorkingToWaiting` (FR-007 — одно начальное сообщение при старте воркера)
- [x] T007 [P] [US1] Добавить KDoc на переписанную `sendCountWaitingMessage` с описанием поведения дедупликации + ссылкой `@see docs/features/async-process-queue.md` и упоминанием фикса `178-fix-process-count-waiting-spam` (constitution FR-006)
- [x] T008 [US1] Проверить `grep -n sendCountWaitingMessage karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/ -r` — должно быть **ровно 4** строки call-sites (1 определение + 4 call-sites: `start`, `createDbInstance`, `run`, `forceStop`). Если число отличается — остановиться и вернуться к T003 (FR-002). ✅ Проверено: 4 call-sites после удаления двух периодических в `doStart` (T011)
- [x] T009 [US1] Локально собрать модуль: `./gradlew :karaoke-app:compileKotlin ktlintCheck` — оба зелёные. Если ktlint ругается на стиль (например, длинные строки) — починить. ✅ BUILD SUCCESSFUL in 27s

**Checkpoint**: после T009 код готов к сборке `karaoke-app:bootJar` и перезапуску контейнера для ручной проверки через quickstart.md (Phase 5).

---

## Phase 4: User Story 2 — Ревизия цикла `doStart` (Priority: P2)

**Goal**: убедиться, что в `doStart()` нет периодического вызова `sendCountWaitingMessage` без привязки к реальному изменению счётчика. Если есть — удалить (US1 с дедупликацией уже закрывает спам, но лишние вызовы — это нагрузка на БД через `getCountWaiting(database)`).

**Independent Test**: прочитать `doStart()` (строки 729+ в `KaraokeProcessWorker.kt`), найти блок `while (isWork)`. Внутри не должно быть вызовов `sendCountWaitingMessage(...)` без условия «значение реально изменилось». Если есть — удалить.

### Implementation for User Story 2

- [x] T010 [US2] Прочитать `doStart()` (строки 729–1184 в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`) — найти блок `while (isWork)`. ✅ НАЙДЕНО: 2 периодических вызова `sendCountWaitingMessage` (строки 1058–1059 внутри `karaokeProcessesToStartIds.forEach` и строки 1107–1109 после forEach). Оба вызывались на КАЖДОЙ итерации цикла с `Thread.sleep(10L)` — это и был источник спама.
- [x] T011 [US2] Удалить оба периодических вызова в `doStart()`. ✅ Сделано (старые строки 1058–1059 и 1107–1109 заменены комментариями с пояснением FR-003). Реальные изменения счётчика теперь доходят через call-sites `createDbInstance` (KaraokeProcess.kt:762), `run` (KaraokeProcessWorker.kt:211), `start` (теперь :635 после правок T006), `forceStop` (теперь :1226 после правок).

**Checkpoint**: US2 закрыт удалением двух лишних вызовов; FR-003 выполнен; нагрузка на БД (SELECT count(*) на tbl_processes) снижена с ~100/сек до ~0 в простое.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: финальная проверка, документация, передача пользователю для ручной валидации.

- [ ] T012 [P] Прогнать ручные сценарии из `specs/177-fix-process-count-waiting-spam/quickstart.md` на admin-машине (DevTools → EventStream). **Требуется пользователь** (нельзя выполнить с текущей машины: hostname `nsa-i9`, user `nsa` — не `dev-pc`/`dev`). Сводка для проверки:
  - Сценарий 1 (SC-001): 5 минут простоя → 0 сообщений `PROCESS_COUNT_WAITING`.
  - Сценарий 2 (SC-002): запуск/завершение задания → 1–2 с доставка, ровно 1 сообщение на изменение.
  - Сценарий 3 (FR-007): stop/start воркера → ровно 1 начальное сообщение.
  - Сценарий 4 (FR-008): `PROCESS_WORKER_STATE`, `RECORD_CHANGE` — без регрессии.
- [x] T013 [P] Обновить `docs/architecture-notes.md` — добавить запись о PR `178-fix-process-count-waiting-spam` (Pass 52). Стиль записи — как для предыдущих PR (см. последние 5–10 записей; формат: дата, симптом, причина, фикс, метрика)
- [x] T014 [P] Проверить, что `webvue3` не требует изменений (SC-005) — `grep -rn "PROCESS_COUNT_WAITING" webvue3/src/ | head -5`. ✅ Найдено 2 упоминания: `App.vue:362` (case-handler, без изменений) и `ProcessWorker.vue:45` (комментарий-документация, без изменений). Frontend просто получит меньше событий — логика обработки та же.
- [x] T015 Прогнать линтеры, как требует CI (constitution FR-006/007): `./gradlew ktlintCheck` — ✅ BUILD SUCCESSFUL (см. T009). `cd webvue3 && npm run lint:check` — пропущено (нет затрагиваемых файлов во фронте; baseline-проверка не требует изменений).
- [ ] T016 Создать PR через `gh pr create --base master` (после локальной сборки и ручной проверки; см. AGENTS.md § «CI-gate для master»). **Требуется явный запрос пользователя** (AGENTS.md § «Git»: «не коммитить без явного запроса»). Дождаться CI 7/7 SUCCESS, затем `gh pr merge --merge` **без** `--delete-branch` (см. AGENTS.md § «Жизненный цикл feature-ветки»)

**Checkpoint**: всё готово к merge в master после CI 7/7.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: нет зависимостей — стартует сразу.
- **Foundational (Phase 2)**: зависит от Setup (T001, T002) — БЛОКИРУЕТ US1.
- **User Story 1 (Phase 3)**: зависит от Foundational (Phase 2).
- **User Story 2 (Phase 4)**: зависит от US1 (T004–T009) — потому что US2 про ревизию того же файла после правок US1.
- **Polish (Phase 5)**: зависит от US1 + US2.

### User Story Dependencies

- **US1 (P1)**: можно начать после Foundational (Phase 2) — независима.
- **US2 (P2)**: зависит от US1 (тот же файл, последовательная ревизия) — НЕ независима, должна идти после US1.
- Технически US2 закрывается дедупликацией US1 даже без ревизии doStart, но ревизия убирает нагрузку на БД (`getCountWaiting` — это SELECT count(*) — лишний вызов на каждой итерации цикла).

### Within Each User Story

- В US1: T004 (поле) → T005 (функция) → T006 (сброс) → T007 (KDoc) → T008 (валидация call-sites) → T009 (сборка).
- T007 можно делать параллельно с T005–T006 в отдельном коммите, если KDoc-чек строгий.
- В US2: T010 (поиск) → T011 (фикс/подтверждение).
- В Polish: T012, T013, T014 можно параллелить (T014 — read-only grep).

### Parallel Opportunities

- T001 + T002 (Setup) — параллельно (нет пересечений).
- T007 + T008 (в US1) — параллельно (разный тип работы: KDoc vs grep).
- T012 + T013 + T014 (Polish) — параллельно (разные артефакты: ручная проверка, документация, read-only grep).
- US1 и US2 в один момент не параллелятся — US2 правит тот же файл.

---

## Parallel Example: User Story 1

```bash
# Параллельно после T004 (поле добавлено):
Task: "T005 [US1] Переписать sendCountWaitingMessage с проверкой lastSentCountWaiting"
Task: "T006 [US1] Сбросить lastSentCountWaiting в start()"

# После T005/T006, но до T009 (сборка), параллельно:
Task: "T007 [P] [US1] KDoc на переписанную функцию"
Task: "T008 [US1] grep по call-sites sendCountWaitingMessage"
```

---

## Implementation Strategy

### MVP First (US1 Only)

1. ✅ Phase 1: Setup (T001–T002)
2. ✅ Phase 2: Foundational (T003)
3. ✅ Phase 3: US1 (T004–T009) — **MVP**
4. **STOP and VALIDATE**: ручная проверка quickstart.md Сценарий 1+2 на admin-машине
5. Если SC-001/002 выполнены — US2 (T010–T011) можно отложить и делать в том же PR или в следующем

### Incremental Delivery

1. Setup + Foundational → готов к имплементации.
2. US1 → ручная проверка → **MVP** (устраняет спам).
3. US2 → ревизия doStart → убирает нагрузку на БД.
4. Polish → документация + PR.

### Parallel Team Strategy

Для этой фичи параллелить несколько разработчиков нерационально:
- 1 файл, 1 поле, 1 функция, 1 место сброса. Дифф < 30 строк.
- Лучшая стратегия — 1 разработчик делает US1+US2 последовательно.

---

## Notes

- **[P] задачи** — разные файлы или read-only операции, без зависимостей.
- **[Story] метки** — `[US1]` для подавления дублей (P1), `[US2]` для ревизии doStart (P2).
- **Тесты**: НЕ генерируются — в спеке не запрошены, в проекте нет CI/unit-тестов (constitution.md § «Тесты»), проверка ручная через quickstart.md.
- **Коммиты**: по одному на логический шаг (T005+T006 → 1 коммит «feat: подавление дублей PROCESS_COUNT_WAITING»; T010+T011 → 1 коммит «refactor: ревизия doStart на периодические вызовы»). **НЕ коммитить без явного запроса пользователя** (AGENTS.md § «Git»).
- **Checkpoint**: после Phase 3 (US1) — обязательная ручная валидация на admin-машине перед US2/Polish.
- **Избегать**: вносить изменения в `webvue3`/`karaoke-public` (SC-005 — UI не должен меняться); добавлять новые прямые вызовы `SNS.send(SseNotification.processCountWaiting(...))` в обход `sendCountWaitingMessage` (FR-002 — единая точка дедупликации); менять формат JSON payload (`contracts/sse-payload.md`).
