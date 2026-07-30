---

description: "Task list template for feature implementation"
---

# Tasks: Устранить утечку соединений с БД от одноразовых потоков очереди

**Input**: Design documents from `/specs/091-fix-connection-leak/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Автотестов нет — поведение зависит от реального PostgreSQL и реального количества обработанных заданий. Верификация — ручная, по `quickstart.md` (`pg_stat_activity`).

**Organization**: Одна user story (US1, P1) — активный продакшн-инцидент, единственный приоритет.

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

- [X] T001 Создать и переключиться на git-ветку `091-fix-connection-leak` от `master` (`git checkout -b 091-fix-connection-leak`) — номер уже зарезервирован через `tools/reserve-branch-number.sh` (см. `.specify/feature.json`).
- [X] T002 [P] Убедиться, что модуль `karaoke-app` собирается без ошибок на текущем коде (baseline, без правок): `./gradlew karaoke-app:compileKotlin` из корня репозитория. `BUILD SUCCESSFUL`.
- [X] T003 [P] Убедиться, что локальный стенд (`db`, `karaoke-app`) поднят и доступен; замерить baseline числа соединений: `docker exec karaoke-db psql -U postgres -d karaoke -t -c "select count(*) from pg_stat_activity;"` — записать значение для сравнения в T007. Baseline = **15** соединений.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Единственная user story — но снимок кода в `research.md` (номера строк) мог устареть между планированием и реализацией из-за параллельной работы других веток в этом же репозитории (за время этой сессии в master уже влилось несколько посторонних PR).

**⚠️ CRITICAL**: Реализация не начинается до завершения этой фазы.

- [X] T004 Сверить текущее содержимое и номера строк из `research.md`/`plan.md` с фактическим кодом — при расхождении обновить номера строк в этих документах:
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`: `getConnection()` (после specs/087/088 — строки ~1-48, `ThreadLocal`-поле)
  - `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt`: `class KaraokeProcessThread` (строка ~62), `override fun run()` (строка ~74, тело `if (karaokeProcess != null) { ... }` строки ~76-375)

  Оба файла совпадают с находками `research.md` дословно, расхождений нет.

**Checkpoint**: Находки актуальны — можно приступать к реализации.

---

## Phase 3: User Story 1 - Длительная работа очереди не исчерпывает лимит соединений с БД (Priority: P1) 🎯 MVP

**Goal**: Одноразовые потоки очереди (`KaraokeProcessThread`) явно освобождают своё физическое соединение с БД по завершении работы — число одновременно открытых соединений перестаёт расти пропорционально общему числу обработанных заданий, а переиспользуемые потоки (Tomcat, `doStart()`) продолжают работать как в specs/087, без изменений.

**Independent Test**: Сценарии 2 и 3 из `specs/091-fix-connection-leak/quickstart.md` — прогнать через очередь несколько десятков-сотен заданий подряд и убедиться, что `pg_stat_activity` не растёт монотонно; отдельно подтвердить, что повторные HTTP-запросы по-прежнему переиспользуют одно соединение.

### Implementation for User Story 1

- [X] T005 [US1] Добавить метод `closeThreadConnection()` в `KaraokeConnection` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`): закрывает `java.sql.Connection` из `ThreadLocal` **вызывающего потока** (если он там есть) и удаляет запись (`threadLocalConnection.remove()`); при ошибке `close()` — залогировать и всё равно выполнить `remove()` (в `finally`). KDoc должен явно предупреждать: вызывать только из одноразовых/недолговечных потоков, НЕ из переиспользуемых (Tomcat, `doStart()`).

  Реализовано дословно по `research.md`. `./gradlew karaoke-app:compileKotlin` — `BUILD SUCCESSFUL`.
- [X] T006 [US1] В `KaraokeProcessWorker.kt` обернуть тело `if (karaokeProcess != null) { ... }` внутри `KaraokeProcessThread.run()` (строки ~76-375, зависит от T005) в `try { ... } finally { karaokeProcess.database.closeThreadConnection() }` — гарантирует закрытие соединения потока ровно один раз, в самом конце, независимо от исхода (успех/ошибка/форс-стоп/непредвиденное исключение). Не менять поведение внутри `try`-блока — только добавить обёртку снаружи уже существующей логики.

  Обёртка добавлена (`try` сразу после `if (karaokeProcess != null) {`, `finally` перед закрывающей `}` этого блока), логика внутри не менялась. Индентация тела поправлена автоформаттером `./gradlew karaoke-app:ktlintFormat` (механическое смещение на 4 пробела, без изменения кода). `compileKotlin` и `ktlintCheck` — `BUILD SUCCESSFUL`.
- [X] T007 [US1] Ручная проверка: Сценарий 2 из `specs/091-fix-connection-leak/quickstart.md` на dev-pc — запустить очередь, дать обработать несколько десятков-сотен заданий, сравнить `pg_stat_activity` с baseline из T003 — число соединений должно оставаться стабильным, не расти пропорционально числу обработанных заданий; в логе не должно быть `too many clients`.

  Пересобран и перезапущен модуль `app` (`do.sh build_app` + `do.sh start_app`). Baseline после
  рестарта = 16 соединений. Очередь запущена через `POST /api/processes/workerstartstop` (2735
  заданий `WAITING`). За ~4 минуты непрерывной работы обработано **946** заданий (`DONE`/`ERROR`),
  число соединений (`pg_stat_activity`) стабильно держалось на **20** на всём протяжении (замеры
  каждые ~20с) — не росло пропорционально числу обработанных заданий. В логе `karaoke-app` за это
  время `grep -ic "too many clients"` = **0**. Утечка устранена.
- [X] T008 [US1] Ручная проверка: Сценарий 3 из `specs/091-fix-connection-leak/quickstart.md` на dev-pc — 20-30 последовательных HTTP-запросов к админке не увеличивают число соединений (переиспользование из specs/087 не сломано); очередь продолжает нормально работать параллельно с HTTP-нагрузкой (собственное соединение `doStart()` не закрывается ошибочно).

  30 последовательных `POST /api/processes/workerstatus` во время активной работы очереди: число
  соединений до = 20, после = 20 (не выросло — переиспользование Tomcat-потоков не сломано).
  Спустя 15с после нагрузки очередь продолжила обработку без сбоев (946 done/error к этому
  моменту), `doStart()` не потерял своё соединение. После штатной остановки очереди
  (`stopAfterThreadIsDone`) число соединений снизилось до 18 (не осталось расти).

**Checkpoint**: User Story 1 полностью работает и проверяема независимо — это единственная и вся фича, устраняющая активный инцидент.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Стандартный пред-коммитный чек-лист проекта (`CLAUDE.md`) и финальная регрессия.

- [X] T009 [P] Обновить `docs/features/async-process-queue.md`: задокументировать новый инвариант — одноразовые потоки очереди обязаны освобождать своё соединение по завершении через `KaraokeConnection.closeThreadConnection()` (FR-009 `constitution.md`, per-feature документ).

  Добавлен новый **MUST**-инвариант, запись в «Известные ловушки» с результатами живой проверки, и ссылка на `closeThreadConnection()` в списке ключевых файлов.
- [X] T010 [P] Прогнать `./gradlew ktlintCheck` и поправить нарушения в изменённых файлах (`KaraokeConnection.kt`, `KaraokeProcessWorker.kt`).

  `./gradlew ktlintCheck` (весь репозиторий) — `BUILD SUCCESSFUL`, нарушений нет.
- [X] T011 [P] Прогнать `bash tools/check-kdoc-coverage.sh` — добавить/поправить KDoc для нового публичного метода `closeThreadConnection()`, `@see docs/features/async-process-queue.md`.

  `karaoke-app`: 96.4% (348/361), TOTAL 96.8% — выше порога ≥50%. `closeThreadConnection()` уже снабжён полным KDoc с `@see docs/features/async-process-queue.md` (добавлен в T005).
- [X] T012 Прогнать `pre-commit run --all-files` из корня репозитория и устранить замечания перед PR.

  Все 7 проверок пройдены: ktlint, eslint (webvue3/karaoke-public), prettier (webvue3/karaoke-public), lychee (ссылки в документации), структура per-feature документа.
- [X] T013 Финальный прогон всех сценариев `specs/091-fix-connection-leak/quickstart.md` подряд на одном поднятом стенде — сквозная регресс-проверка перед PR.

  Сценарии 2 и 3 прогнаны подряд на одном непрерывно поднятом стенде (T007/T008, без пересборки/перезапуска между ними) — итог: 946 заданий обработано, соединения стабильны на 20, HTTP-переиспользование не сломано, `too many clients` — 0 в логе. Сценарий 1 (опциональный baseline ДО фикса) пропущен — фикс уже применён к коду до пересборки образа, воспроизводить регрессию намеренно не требовалось (реальный инцидент на admin-машине уже задокументирован в описании фичи как источник задачи).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей.
- **Foundational (Phase 2)**: зависит от Setup; блокирует реализацию только в части «свежести» находок (T004 — сверка, не код-правка).
- **User Story 1 (Phase 3)**: зависит от Foundational. Единственная история.
- **Polish (Phase 4)**: зависит от завершения Phase 3.

### Within User Story 1

- T005 → T006 (T006 использует метод из T005, один и тот же логический фикс, разные файлы).
- T007 и T008 — после T006, можно параллельно друг с другом (разные проверки, не конфликтуют).

### Parallel Opportunities

- T002 и T003 (Setup) — параллельно.
- T007 и T008 — параллельно.
- T009, T010, T011 (Polish) — параллельно.

---

## Parallel Example: Polish

```bash
Task: "T009 — обновить docs/features/async-process-queue.md"
Task: "T010 — ktlintCheck"
Task: "T011 — KDoc coverage"
```

---

## Implementation Strategy

### MVP First (и единственная история)

1. Phase 1: Setup (включая замер baseline соединений).
2. Phase 2: Foundational (сверка находок).
3. Phase 3: User Story 1 (T005-T008).
4. **Остановиться и проверить**: Сценарии 2/3 `quickstart.md` — сравнить с baseline.
5. Phase 4: Polish → PR (учитывая активность инцидента — по возможности без задержки).

### Solo-исполнитель

T005 → T006 → T007/T008 (параллельно) → Polish.

---

## Notes

- [P] задачи = разные файлы/проверки, нет прямой зависимости.
- [Story] маппит задачу на единственную user story для трассируемости.
- Автотестов нет — вся верификация ручная, по `quickstart.md` и `pg_stat_activity`.
- Коммитить после каждой задачи или логической группы.
- Избегать: вызова `closeThreadConnection()` из `doStart()`/HTTP-обработчиков (сломает specs/087); закрытия соединения ДО завершения всех обращений потока к БД внутри `run()`.
