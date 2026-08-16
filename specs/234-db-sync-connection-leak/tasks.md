# Tasks: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Input**: Design documents from `/specs/234-db-sync-connection-leak/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅
**Tests**: Не включаются (Constitution § «Рабочий процесс» — в CI тестов нет, существующие интеграционные `@Disabled`; проверка — пользователем по quickstart.md)

**Organization**: Tasks grouped by user story (US1=«1 клик без каскада», US2=«структурированный warn», US3=«174+ вызовов не сломаны»).

**Path Conventions** (multi-module Gradle, web service):
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/` — бэкенд-движок
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/` — публичный API
- `archive/docs/features/` — per-feature документация (FR-009 spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Подготовка к фиксу — нет новых зависимостей (всё уже в classpath через Spring Boot). Только проверка окружения и создание feature-ветки.

- [x] T001 Подтвердить активную ветку `234-db-sync-connection-leak` через `git branch --show-current` (выполнено: ветка активна)
- [x] T002 [P] Подтвердить, что контейнеры `karaoke-app`, `karaoke-web`, `karaoke-db` запущены через `docker ps | grep karaoke` (выполнено: все 3 контейнера `Up`)
- [x] T003 [P] Подтвердить Postgres `max_connections = 100` через `docker exec karaoke-db psql -U postgres -c "SHOW max_connections;"` (выполнено: `100`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Каркас singleton-инфраструктуры. Без этой фазы не имеет смысла переходить к user stories — все они зависят от singleton-фабрик и SLF4J logger'а.

**⚠️ CRITICAL**: User story work не может начаться, пока эта фаза не завершена.

- [x] T004 [P] Добавить singleton-фабрики `LOCAL_INSTANCE`/`REMOTE_INSTANCE`/`VIRTUAL_INSTANCE` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt` через `by lazy(LazyThreadSafetyMode.SYNCHRONIZED)` (FR-001 spec.md, R-001 research.md)
- [x] T005 [P] Заменить `fun local()/remote()/virtual()` в `karaoke-app/.../Connection.kt` на возврат singleton-инстансов (FR-001 spec.md)
- [x] T006 Обновить KDoc в `karaoke-app/.../Connection.kt` — явно указать, что фабрики возвращают singleton, а не «новый инстанс на каждый вызов»; добавить ссылку на спеку `087-fix-shared-db-connection` (FR-010 spec.md)
- [x] T007 [P] Симметричный фикс: добавить singleton-фабрики `LOCAL_INSTANCE`/`REMOTE_INSTANCE`/`VIRTUAL_INSTANCE` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/Connection.kt` (FR-008 spec.md, R-003 research.md)
- [x] T008 [P] Заменить `fun local()/remote()/virtual()` в `karaoke-web/.../Connection.kt` на возврат singleton-инстансов (FR-008 spec.md)
- [x] T009 Добавить `private val log = LoggerFactory.getLogger(KaraokeConnection::class.java)` и импорт `org.slf4j.LoggerFactory` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt` (FR-005 spec.md, R-002 research.md)
- [x] T010 Добавить SLF4J `log.warn(...)` с placeholder'ами `target={} thread={} cause={}` в `getConnection()` после существующего `println(...)` в `karaoke-app/.../KaraokeConnection.kt` (FR-004 spec.md)
- [x] T011 Добавить симметричный SLF4J `log.warn(...)` в `closeThreadConnection()` после существующего `println(...)` в `karaoke-app/.../KaraokeConnection.kt` (FR-004 spec.md, для единообразия)

**Checkpoint**: Foundation ready — singleton `Connection` готов, SLF4J логгер добавлен. Можно собирать и тестировать user stories.

---

## Phase 3: User Story 1 — «Синхронизация БД в 1 клик» без каскада «too many clients» (Priority: P1) 🎯 MVP

**Goal**: Главный симптом задачи устранён — при нажатии «Синхронизация БД в 1 клик» нет каскада сообщений `FATAL: sorry, too many clients already` в логе `karaoke-app`.

**Independent Test**: 10 раз подряд нажать «Синхронизация БД в 1 клик» после чистого старта `karaoke-app`; `docker logs karaoke-app --since 5m | grep -c "too many clients"` = 0; `pg_stat_activity WHERE application_name='karaoke-app'` ≤ 10 (SC-001, SC-002 spec.md).

### Implementation for User Story 1

- [x] T012 [P] [US1] Запустить `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel` для сборки bootJar'ов с новым кодом (Phase 1+2) (выполнено: BUILD SUCCESSFUL, `karaoke-app-1.jar` 320M и `karaoke-web-1.jar` 321M созданы 2026-08-16 08:53)
- [ ] T013 [US1] **Пользователь** перезапускает контейнеры `karaoke-app` + `karaoke-web` через `cd deploy && bash do.sh start_app && bash do.sh start_web` (Constitution § «Ограничения агента» — на машине НЕ `dev-pc` под `dev` контейнеры перезапускает только пользователь; требует явного действия пользователя)
- [ ] T014 [US1] Открыть `http://localhost:8080/` → Sync admin → нажать «Синхронизация БД в 1 клик» 10 раз подряд; в соседнем терминале выполнить `docker logs karaoke-app --since 5m | grep -c "too many clients"` → ожидаемый результат **0** (SC-001) — **зависит от T013** (перезапуск контейнеров)
- [ ] T015 [US1] Проверить `pg_stat_activity` во время 10 кликов: `echo "SELECT count(*) FROM pg_stat_activity WHERE application_name='karaoke-app';" | docker exec -i karaoke-db psql -U postgres -d karaoke` → ожидаемый результат **≤10** (SC-002) — **зависит от T013**

**Checkpoint**: User Story 1 должен быть полностью функционален и тестируем независимо.

---

## Phase 4: User Story 2 — Структурированный SLF4J warn при перегрузке БД (Priority: P2)

**Goal**: При искусственной перегрузке Postgres (`pg_terminate_backend` / снижение `max_connections` до 5) в логе `karaoke-app` появляется структурированный `WARN KaraokeConnection connect failure target=... thread=... cause=...` через SLF4J, а не только голый `println`.

**Independent Test**: Занять 99 соединений в Postgres через `SELECT pg_sleep(60) FROM generate_series(1, 99);`, нажать «Синхронизация БД в 1 клик», проверить `docker logs karaoke-app --since 1m | grep "KaraokeConnection connect failure"` — должны быть записи с `target=LOCAL|SERVER`, `thread=<имя>`, `cause=FATAL: ...` (SC-003 spec.md).

### Implementation for User Story 2

> Зависит от Phase 2 (T009, T010 — SLF4J logger и `log.warn` в `getConnection()`).

- [ ] T016 [US2] **Пользователь** открывает отдельный терминал и выполняет `docker exec -it karaoke-db psql -U postgres -d karaoke` → `SELECT pg_sleep(60) FROM generate_series(1, 99);` (терминал оставить открытым на 60 секунд) — **зависит от T013** (перезапуск контейнеров с новым кодом для проверки SLF4J warn)
- [ ] T017 [US2] Пока psql-терминал открыт, в браузере нажать «Синхронизация БД в 1 клик» — операция должна вызвать `too many clients` (т.к. 99 соединений уже занято + 1 на psql) — **зависит от T013**
- [ ] T018 [US2] Проверить лог: `docker logs karaoke-app --since 1m | grep "KaraokeConnection connect failure"` → ожидаемый результат: запись вида `WARN ... KaraokeConnection connect failure target=LOCAL thread=http-nio-8080-exec-N cause=FATAL: sorry, too many clients already` (SC-003) — **зависит от T013**
- [ ] T019 [US2] Закрыть psql-терминал (`Ctrl+D` или `\q`), убедиться, что следующий клик «Синхронизация БД в 1 клик» проходит без ошибок (не остаточные эффекты) — **зависит от T013**

**Checkpoint**: User Story 2 должен быть полностью функционален и тестируем независимо.

---

## Phase 5: User Story 3 — Существующие 174+ вызовов `getConnection()` продолжают работать (Priority: P1)

**Goal**: Никаких регрессий — после перехода `Connection.local()/remote()` на singleton-инстансы все существующие сценарии (Статистика, редактор песни, sync по одной сущности, фоновая задача) работают без изменений.

**Independent Test**: Smoke-тест из quickstart.md — открыть Статистику, создать/обновить песню в редакторе, синхронизировать одну сущность (`songs`), запустить фоновую задачу, синхронизировать «1 клик» — все 5 шагов проходят без ошибок (SC-004 spec.md).

### Implementation for User Story 3

> Зависит от Phase 2 (singleton фикс).

- [ ] T020 [US3] Открыть `http://localhost:8080/` → «Статистика» → дождаться загрузки графиков KPI (≤5 секунд) — нет `too many clients` (SC-004, регрессия спеки 174) — **зависит от T013**
- [ ] T021 [US3] Открыть любую песню в админ-редакторе → отредактировать → сохранить → убедиться, что данные в БД (`SELECT id, song_name FROM tbl_songs WHERE id = <id>;`) — нет `SocketTimeoutException` от конкурентного использования канала (спека `087`) — **зависит от T013**
- [ ] T022 [US3] Открыть `Sync admin` → выбрать только сущность `songs` → синхронизировать → результат `SyncOneClickResultDto` идентичен pre-fix поведению (created/updated/deleted/moved) — **зависит от T013**
- [ ] T023 [US3] Запустить любую фоновую задачу из `KaraokeProcessQueue` (например, пересчёт статистики или renderMp4 для существующей песни) → дождаться завершения → нет `SocketTimeoutException` — **зависит от T013**
- [ ] T024 [US3] **Финальный smoke**: повторить «Синхронизация БД в 1 клик» 3 раза подряд — нет `too many clients` (SC-004 + SC-001 регрессия) — **зависит от T013**

**Checkpoint**: User Stories 1, 2 И 3 должны работать независимо.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Документация, метрики, cleanup.

- [x] T025 [P] Обновить `archive/docs/features/dual-db-sync.md`: добавить секцию «Singleton Connection-фабрики» с описанием нового поведения (FR-012 spec.md) (выполнено: секция добавлена)
- [x] T026 [P] Обновить секцию «Известные ловушки» в `archive/docs/features/dual-db-sync.md`: убрать ловушку «новый `Connection` → утечка `ThreadLocal` → `too many clients`» (решена singleton'ом) и добавить новую ловушку «мутация ThreadLocal в одном потоке видна только этому потоку — не использовать для cross-thread коммуникации» (FR-012 spec.md) (выполнено: добавлен пункт в «Известные ловушки» со ссылкой на спеку 234)
- [x] T027 [P] Запустить `tools/baseline-stats.sh` для проверки, что ktlint baseline не вырос (Constitution § VI FR-007 — сокращение baseline ≥10%/мес) (выполнено: baseline = 0 во всех 5 baseline'ах)
- [x] T028 Запустить pre-commit проверку секретов: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` → MUST быть пусто (Constitution § VIII.3) (выполнено: ✓ no secrets tracked)
- [x] T029 Запустить `./gradlew ktlintCheck` для проверки code style (Constitution § VI FR-007) (выполнено: BUILD SUCCESSFUL для karaoke-app и karaoke-web)
- [ ] T030 Создать commit: `fix(connection): singleton Connection-фабрики + SLF4J warn для 'too many clients'` — на русском, коротко, в стиле проекта (см. `git log --oneline -10`) — **требует явного запроса пользователя** (Constitution § «Рабочий процесс» — «не коммитить без явного запроса пользователя»)
- [ ] T031 Создать PR через `gh pr create --base master`, дождаться `gh pr checks` (PASS), смерджить `gh pr merge --merge` (см. AGENTS.md § «Git — CI-gate для master») — **зависит от T030** и требует явного согласия пользователя

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Нет зависимостей — стартует немедленно (проверка окружения).
- **Foundational (Phase 2)**: Зависит от Setup (Phase 1) — BLOCKS все user stories. T004-T011 — критическая инфраструктура (singleton + SLF4J).
- **User Stories (Phase 3+)**: Все зависят от Foundational (Phase 2) completion.
  - US1, US2, US3 могут выполняться последовательно в порядке P1→P2→P1.
  - US1 + US3 оба P1, но US1 — главный симптом (MVP), US3 — регрессионный smoke.
- **Polish (Phase 6)**: Зависит от завершения всех user stories.

### User Story Dependencies

- **User Story 1 (P1)**: Стартует после Foundational (Phase 2). Нет зависимостей от других stories.
- **User Story 2 (P2)**: Стартует после Foundational (Phase 2). Зависит от T009-T011 (SLF4J в `KaraokeConnection.kt`). Может идти параллельно с US1 (нет конфликта файлов).
- **User Story 3 (P1)**: Стартует после Foundational (Phase 2). Регрессионный smoke для US1 — естественно идёт после US1.

### Within Each User Story

- Внутренних зависимостей нет — каждый таск = один шаг верификации.
- Tests не пишутся (см. Constitution § «Рабочий процесс» — в CI тестов нет; проверка пользователем по quickstart.md).
- После каждого таска — коммит или группа тасков (T012-T013 — сборка + перезапуск).

### Parallel Opportunities

- **Phase 2 (T004-T011)**: 8 тасков, можно параллелить:
  - T004-T006 (3 таска в `karaoke-app/.../Connection.kt`) — последовательно внутри одного файла.
  - T007-T008 (2 таска в `karaoke-web/.../Connection.kt`) — параллельно с T004-T006 (разные файлы).
  - T009-T011 (3 таска в `karaoke-app/.../KaraokeConnection.kt`) — последовательно внутри одного файла.
- **Phase 3-5 (US1, US2, US3)**: тестирование может идти параллельно (открыть несколько браузеров/psql-терминалов).
- **Phase 6 (Polish)**: T025-T029 — `[P]` помечены таски, могут идти параллельно.

---

## Parallel Example: User Story 1 + User Story 2 (одновременно)

US1 (MVP, проверка «нет каскада») и US2 (проверка «структурированный warn») затрагивают разные проверки и могут частично пересекаться:

```bash
# Терминал 1 (US1): сборка + перезапуск
Task: "Запустить ./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel"
Task: "Пользователь перезапускает контейнеры karaoke-app + karaoke-web через deploy/do.sh"

# Терминал 2 (US2): подготовить psql для перегрузки Postgres
Task: "Открыть docker exec -it karaoke-db psql ... SELECT pg_sleep(60) FROM generate_series(1, 99);"

# Терминал 3 (US3 параллельно): smoke-тест других сценариев
Task: "Открыть Статистику → проверить загрузку KPI"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003 — проверка окружения).
2. Complete Phase 2: Foundational (T004-T011 — singleton + SLF4J).
3. Complete Phase 3: User Story 1 (T012-T015 — сборка, перезапуск, проверка «нет каскада»).
4. **STOP and VALIDATE**: Проверить SC-001 + SC-002 вручную. Если провалилось — откатить, проверить `Connection.kt`.
5. Deploy/demo готов (PR можно мерджить).

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test SC-001/SC-002 independently → Deploy/Demo (MVP!).
3. Add User Story 2 → Test SC-003 independently → Deploy/Demo.
4. Add User Story 3 → Test SC-004 (smoke regression) → Deploy/Demo.
5. Polish (Phase 6) → документация, ktlint baseline, commit, PR.

### Parallel Team Strategy

В этой задаче одна developer (admin) — поэтому последовательная стратегия. Если несколько человек:

- **Developer A**: Phase 2 (T004-T008, `Connection.kt` в обоих модулях) + Phase 6 (T025-T026, документация).
- **Developer B**: Phase 2 (T009-T011, `KaraokeConnection.kt` + SLF4J).
- **Developer C**: Phase 3 (US1) + Phase 5 (US3) — verification.
- Все сходятся в Phase 6 (T030-T031) — commit + PR.

---

## Notes

- [P] tasks = разные файлы, нет зависимостей (или разные секции одного файла).
- [Story] label привязывает таск к user story для трассировки.
- Каждая user story должна быть независимо завершаемой и тестируемой.
- **Тесты не пишутся** — проверка пользователем по quickstart.md (Constitution § «Рабочий процесс»).
- Commit после каждого таска или логической группы (T004-T008 — один коммит «Connection singleton»; T009-T011 — второй «SLF4J warn»; T012-T013 — третий «build + deploy»).
- Stop at any checkpoint to validate story independently (Phase 3, 4, 5 — каждый имеет свой checkpoint).
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.

---

## Summary

- **Total tasks**: 31
- **By user story**: US1 (4 таска: T012-T015), US2 (4 таска: T016-T019), US3 (5 тасков: T020-T024)
- **By phase**: Setup (3), Foundational (8), US1 (4), US2 (4), US3 (5), Polish (7)
- **Parallel opportunities**: Phase 2 (T004-T011 частично), Phase 3+5 (US1 + US2 + US3 тестирование параллельно), Phase 6 ([P] таски).
- **MVP scope**: US1 (Phase 1 + Phase 2 + Phase 3) — главный симптом устранён.
- **Format validation**: ✅ Все 31 тасков следуют формату `- [ ] [TaskID] [P?] [Story?] Description with file path`.
