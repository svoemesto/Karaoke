---

description: "Task list template for feature implementation"
---

# Tasks: Единообразная обработка сбоев БД в главном цикле очереди

**Input**: Design documents from `/specs/088-fix-queue-swallowed-errors/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Автотестов нет — обе функции требуют реального PostgreSQL (нет мока для `DriverManager`/`Statement`). Верификация — ручная, по `quickstart.md`.

**Organization**: Одна user story (US1, P1) — единообразная обработка сбоев БД в главном цикле очереди; ограничения «не сломать остальные 5 вызывающих мест» оформлены как требования внутри той же истории, не отдельная история.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: US1
- Указаны точные пути к файлам

## Path Conventions

Однопроектная структура, весь код — в модуле
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`. Документация фичи
— `docs/features/async-process-queue.md`. Фронтенд и HTTP-контракт не
меняются.

---

## Phase 1: Setup

**Purpose**: Зафиксировать чистую точку отсчёта перед правками существующего кода.

- [X] T001 Создать и переключиться на git-ветку `088-fix-queue-swallowed-errors` от `master` (`git checkout -b 088-fix-queue-swallowed-errors`) — номер уже зарезервирован через `tools/reserve-branch-number.sh` (см. `.specify/feature.json`).
- [X] T002 [P] Убедиться, что модуль `karaoke-app` собирается без ошибок на текущем коде (baseline, без правок): `./gradlew karaoke-app:compileKotlin` из корня репозитория. `BUILD SUCCESSFUL`.
- [X] T003 [P] Убедиться, что локальный стенд (`db`, `karaoke-app`) поднят и доступен через `deploy/do.sh` (scoped-команды `build_app`/`start_app`) — нужен для ручной проверки по `specs/088-fix-queue-swallowed-errors/quickstart.md`, включая `docker pause`/`unpause karaoke-db`. Подтверждено: оба контейнера `Up`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Единственная user story — но снимок кода в `research.md` (номера строк вызывающих мест) мог устареть между планированием и реализацией из-за параллельной работы других веток в этом же репозитории.

**⚠️ CRITICAL**: Реализация не начинается до завершения этой фазы.

- [X] T004 Сверить текущее содержимое и номера строк всех вызывающих мест из таблицы `research.md` (Кандидат A) с фактическим кодом — при расхождении обновить номера строк в `research.md`/`data-model.md`/`plan.md`:
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`: `getCountWaiting()` (строки 479-506), `getProcessesToStart()` (строки 750-814), вызов внутри `createDbInstance()` (строка 744)
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`: `getKaraokeProcessesToStart()` (строка 648), вызовы внутри `doStart()` (строки 955, 979, 1028), вызов в `KaraokeProcessThread.run()` (строка 204), вызов в `forceStop()` (строка 1090)

  Найдено **7-е вызывающее место**, не учтённое в первом проходе планирования: `KaraokeProcessWorker.kt:536` — внутри `start()`, синхронно на HTTP-потоке `/api/processes/workerstartstop`, **до** `Thread { ... }` (не в retry-защищённом цикле). `research.md`/`data-model.md`/`plan.md` обновлены — это место остаётся на дефолте `throwOnError=false`, по той же логике, что и `:1090` (`forceStop()`). Остальные 6 мест совпадают с находками `research.md` дословно.
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/RenderQueueStalledCheck.kt` (строка 18), `LaneStalledCheck.kt` (строка 42)

**Checkpoint**: Находки актуальны — можно приступать к реализации.

---

## Phase 3: User Story 1 - Любой сбой БД в главном цикле очереди ведёт себя одинаково предсказуемо (Priority: P1) 🎯 MVP

**Goal**: Сбой БД в `getCountWaiting()`/`getProcessesToStart()` внутри `doStart()` теперь так же заметен и так же запускает retry-механизм (specs/087), как уже сегодня заметен сбой в `.save()` — при этом остальные 5 вызывающих мест этих же функций (HTTP-путь создания задания, per-job уведомление, `forceStop()`, 2 мониторинг-чека) продолжают работать побайтово как раньше.

**Independent Test**: Сценарий 1 из `specs/088-fix-queue-swallowed-errors/quickstart.md` — многоминутный `docker pause karaoke-db` во время активной очереди теперь ВСЕГДА (независимо от того, какая функция первой столкнётся со сбоем) приводит к видимому retry-логу и автоматическому восстановлению, а не иногда к тихому простою.

### Implementation for User Story 1

- [X] T005 [US1] Добавить параметр `throwOnError: Boolean = false` в `KaraokeProcess.getCountWaiting()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`, строки 479-506): при `connection == null` и `throwOnError=true` — бросить `SQLException("Невозможно установить соединение с базой данных ${database.name}")` вместо `return 0L`; при `throwOnError=true` в `catch (e: SQLException)` вокруг запроса — `throw e` вместо `e.printStackTrace()` + возврат заглушки. Блок закрытия `Statement`/`ResultSet` в `finally` не менять. При `throwOnError=false` (дефолт) — поведение побайтово не меняется. `BUILD SUCCESSFUL`.
- [X] T006 [P] [US1] Добавить параметр `throwOnError: Boolean = false` в `KaraokeProcess.getProcessesToStart()` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt`, строки 750-814) — та же логика, что в T005 (`connection == null` → `throw` вместо `return emptyMap()`; `catch (e: SQLException)` → `throw e` вместо `printStackTrace()`). Разные функции, можно параллельно с T005. `BUILD SUCCESSFUL`.
- [X] T007 [US1] В `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt` передать `throwOnError = true` в трёх местах внутри `doStart()` (зависит от T005/T006): приватный `getKaraokeProcessesToStart()` (строка ~648, единственный вызывающий — `doStart()` на строке ~955, можно проставить `throwOnError=true` прямо в теле wrapper'а) и оба прямых вызова `KaraokeProcess.getCountWaiting()` (строки ~979, ~1028). Четыре остальных вызывающих места в этом же файле (`start()` строка ~536, `KaraokeProcessThread.run()` строка ~204, `forceStop()` строка ~1090) НЕ трогать — остаются на дефолте `false`. `BUILD SUCCESSFUL`; `grep` подтвердил, что все 4 незащищённых места не изменились.
- [X] T008 [US1] Ручная проверка: Сценарий 1 из `specs/088-fix-queue-swallowed-errors/quickstart.md` на dev-pc — многоминутный `docker pause karaoke-db` во время активной очереди теперь всегда даёт видимый retry-лог и автовосстановление (не только когда сбой достаётся `.save()`). `docker pause karaoke-db` на 4 минуты против реальной очереди (2787 `WAITING`) — в логе последовательно `попытка 1/5` (пауза 2с) → `2/5` (5с) → `3/5` (15с) → `4/5` (30с) → `5/5` → `«попытки восстановления (5) исчерпаны, очередь остановлена»`. Впервые за всю сессию живьём воспроизведена ветка исчерпания retry (в specs/087 T010 её не удалось поймать именно из-за этого бага). После этого `isWork=false`, через ~70с появился алерт `queue.stalled` (`RenderQueueStalledCheck`, «ждёт заданий: 299»), one-click `POST /api/monitor/resolve` вернул `isWork=true`. Полный цикл (сбой → retry ×5 → исчерпание → safety-net → алерт → восстановление) подтверждён от начала до конца. Стенд возвращён в исходное состояние (`isWork=false`).
- [X] T009 [US1] Проверка (code review, без правок): подтвердить, что `KaraokeProcess.kt:744` (`createDbInstance()`), `KaraokeProcessWorker.kt:204` (`KaraokeProcessThread.run()`), `:536` (`start()`, до `Thread{...}`), `:1090` (`forceStop()`), `RenderQueueStalledCheck.kt:18`, `LaneStalledCheck.kt:42` не изменились в рамках T005-T007 и по-прежнему вызывают обе функции без `throwOnError = true` (FR-004/FR-005; Сценарии 2 и 3 из `quickstart.md`). `grep` по всем 4 файлам подтвердил: все 6 мест по-прежнему без параметра `throwOnError` (дефолт `false`).

**Checkpoint**: User Story 1 полностью работает и проверяема независимо — это единственная и вся фича.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Стандартный пред-коммитный чек-лист проекта (`CLAUDE.md`) и финальная регрессия.

- [X] T010 [P] Обновить `docs/features/async-process-queue.md`: заменить «Известную ловушку» про непоследовательную обработку ошибок БД (добавленную specs/087) на описание устранённого инварианта — обе функции теперь единообразно пробрасывают сбой при вызове из `doStart()` (FR-009 `constitution.md`, per-feature документ). Также расширен основной инвариант retry, чтобы явно упомянуть все 3 throwOnError-вызова внутри doStart() и 6 незащищённых мест.
- [X] T011 [P] Прогнать `./gradlew ktlintCheck` и поправить нарушения в изменённых файлах (`KaraokeProcess.kt`, `KaraokeProcessWorker.kt`). Чисто с первого прогона, `BUILD SUCCESSFUL`.
- [X] T012 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — добавить/поправить KDoc с `@see docs/features/async-process-queue.md` для изменённых публичных функций (`getCountWaiting`, `getProcessesToStart`), задокументировать новый параметр `throwOnError`. KDoc добавлен на этапе реализации (T005/T006). `--strict` → 96.8% (391/404), exit 0.
- [X] T013 Прогнать `pre-commit run --all-files` из корня репозитория и устранить замечания перед PR. Все 7 проверок Passed с первого прогона.
- [X] T014 Финальный прогон Сценария 1 (и code-review Сценариев 2/3) из `specs/088-fix-queue-swallowed-errors/quickstart.md` на одном поднятом стенде — сквозная регресс-проверка перед PR. Т.к. ktlint/KDoc-правки (T011/T012) не потребовали изменений кода после T005-T007, уже запущенный и пересобранный (после T007) контейнер — финальная версия; Сценарий 1 уже подтверждён живьём в T008 на этой же сборке (включая полный цикл: 5 ретраев → исчерпание → safety-net → алерт `queue.stalled` → one-click resolve), Сценарии 2/3 подтверждены code-review в T009. Дополнительный пересобор не требуется.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей.
- **Foundational (Phase 2)**: зависит от Setup; блокирует реализацию только в части «свежести» находок (T004 — сверка, не код-правка).
- **User Story 1 (Phase 3)**: зависит от Foundational. Единственная история — параллельности между историями нет.
- **Polish (Phase 4)**: зависит от завершения Phase 3.

### Within User Story 1

- T005 и T006 — [P] относительно друг друга (разные функции, но оба в одном файле `KaraokeProcess.kt` — при параллельной работе разных исполнителей нужна координация слияния, несмотря на формальную независимость).
- T007 зависит от T005 и T006 (использует новый параметр).
- T008 (ручная проверка) — после T007.
- T009 (code review остальных вызывающих мест) — после T007, можно параллельно с T008.

### Parallel Opportunities

- T002 и T003 (Setup) — параллельно.
- T005 и T006 — параллельно (см. выше про координацию при слиянии).
- T008 и T009 — параллельно.
- T010, T011, T012 (Polish) — параллельно.

---

## Parallel Example: User Story 1

```bash
Task: "T005 — throwOnError в getCountWaiting()"
Task: "T006 — throwOnError в getProcessesToStart()"
```

---

## Implementation Strategy

### MVP First (и единственная история)

1. Phase 1: Setup.
2. Phase 2: Foundational (сверка находок).
3. Phase 3: User Story 1 (T005-T009).
4. **Остановиться и проверить**: Сценарий 1 `quickstart.md`.
5. Phase 4: Polish → PR.

### Solo-исполнитель

T005 → T006 (можно параллельно, один файл) → T007 (один файл, другой) →
T008/T009 (параллельно) → Polish.

---

## Notes

- [P] задачи = разные файлы/функции, нет прямой зависимости.
- [Story] маппит задачу на единственную user story для трассируемости.
- Автотестов нет — вся верификация ручная, по `quickstart.md`.
- Коммитить после каждой задачи или логической группы.
- Избегать: правок в 6 «незащищённых» вызывающих местах (T009 их только
  проверяет, не меняет); расширения `throwOnError` на что-либо, кроме
  `SQLException`/`connection == null`.
