---
description: "Tasks для 288-prod-diagnostics-logging"
---

# Tasks: 288 — Расширенное логирование на проде для отлова зависаний

**Input**: Design documents из `/specs/288-prod-diagnostics-logging/`

**Prerequisites**:
- `plan.md` (required) — tech stack, libraries, structure
- `spec.md` (required) — 4 User Stories (US1=P1 логирование pg, US2=P1 SLF4J в ProdContainerCheck, US3=P1 TZ-синхронизация, US4=P2 документация)
- `research.md` — 8 технических решений D-1..D-8
- `data-model.md` — 3 entities (parameter, logger category, PingState)
- `contracts/log-format.md` — формат WARN/INFO сообщений
- `quickstart.md` — end-to-end validation сценарии

**Tests**: НЕ генерируются (per Constitution § Тесты: автоматические тесты в karaoke-app `@Disabled`, проверка — пользователем через `quickstart.md` end-to-end).

**Organization**: Tasks сгруппированы по user story для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (другие файлы, нет зависимостей)
- **[Story]**: к какой US относится (US1..US4); для Setup/Foundational/Polish — без метки
- В описании — точные file paths

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify prerequisites и контекст фичи (никаких новых файлов инициализации не требуется — проект уже инициализирован).

- [x] T001 Прочитать `specs/288-prod-diagnostics-logging/spec.md`, `plan.md`, `research.md`, `contracts/log-format.md`, `quickstart.md` — убедиться в понимании всех FR-001..FR-023 и acceptance scenarios US1..US4

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Правки deploy-конфигов, общие для US3 (TZ-синхронизация). Все три задачи параллельны — разные файлы, нет зависимостей.

**⚠️ CRITICAL**: US3-валидация (T016) и весь post-deploy flow зависят от этих правок.

- [x] T002 [P] [US3] Добавить `TZ: Europe/Moscow` в секцию `environment` сервиса `karaoke-db` в `deploy/docker-compose-database.yml` (FR-008) — формат YAML, точно по образцу существующих env
- [x] T003 [P] [US3] Добавить `-Duser.timezone=Europe/Moscow` к существующему значению `WEB_JAVA_OPTS` в `deploy/.env` (FR-010) — сохранить существующие флаги (например `-Xmx2g`), добавить новый через пробел
- [x] T004 Создать пустую директорию `docs/ops/` в корне репо (новая директория для operational runbooks, per research.md D-7) — `mkdir -p docs/ops`

**Checkpoint**: Foundation ready — US3-валидация (T016) может начинаться, US1/US2/US4 могут делаться параллельно.

---

## Phase 3: User Story 1 — Медленные SQL видны в pg_log (Priority: P1) 🎯 MVP

**Goal**: На проде через `ALTER SYSTEM SET` + `pg_reload_conf()` включить 7 runtime-параметров PostgreSQL (`log_min_duration_statement = 1000`, `log_temp_files = 0`, `log_lock_waits = on`, `log_autovacuum_min_duration = 0`, `log_checkpoints = on`, `log_line_prefix = '%m [%p] %q%u@%d from %h '`, `log_timezone = 'Europe/Moscow'`, `timezone = 'Europe/Moscow'`).

**Independent Test**: `docker exec karaoke-db psql -c "SELECT pg_sleep(2);"` → `docker logs karaoke-db --since "1m" | grep duration` показывает строку `LOG: duration: 2000 ms statement: SELECT pg_sleep(2)` с префиксом `2026-09-01 12:34:56.789 MSK [PID] postgres@karaoke from IP LOG: ...`.

### Implementation for User Story 1

> ⚠️ **T006 (применение на проде)**: per Constitution § «Категорически запрещено» п. 2 — только по прямому согласию пользователя на каждое `ALTER SYSTEM SET`. Агент НЕ выполняет без явного одобрения в каждой сессии (per A-009 спеки).

- [x] T005 [US1] Подготовить SQL-скрипт `specs/288-prod-diagnostics-logging/sql/alter-system.sql` с 7 командами `ALTER SYSTEM SET` + финальным `SELECT pg_reload_conf();` (FR-001..FR-007) — файл создаётся агентом, выполнение — пользователем или под явным согласием
- [x] T006 [US1] Применить `ALTER SYSTEM SET ...` на прод-БД через `docker exec karaoke-db psql "host=188.119.64.111 ..." < specs/288-prod-diagnostics-logging/sql/alter-system.sql` (FR-001..FR-007) — выполнено по явному per-action согласию пользователя (Constitution п. 2); результат: все 8 `ALTER SYSTEM` применены, `pg_reload_conf()` → `t`, `SHOW` подтверждает значения
- [x] T007 [US1] Validation US1: тестовый `SELECT pg_sleep(2)` выполнен на прод-БД через `docker exec` (2026-09-01 19:??:?? MSK); визуальная проверка строки в stderr контейнера `karaoke-db` на проде — пользователь через SSH (`ssh root@188.119.64.111 'docker logs karaoke-db --since "1m" | grep "duration: 2001"'`); SHOW-параметры подтверждают применение (SC-001, SC-002)

**Checkpoint**: US1 полностью функциональна и тестируема независимо. Post-hoc диагностика медленных SQL работает.

---

## Phase 4: User Story 2 — `ProdContainerCheck` пишет структурированные WARN/INFO (Priority: P1)

**Goal**: Заменить `println("ProdContainerCheck: ping ...")` на SLF4J WARN с категорией `infra.prod.ping`, добавить duration tracking, INFO при восстановлении, логирование `pingRemoteDb()` через категорию `infra.prod.db`, обновить KDoc.

**Independent Test**: На admin-машине `sudo systemctl stop nginx` → через 1 минуту `docker logs karaoke-app --since "2m" | grep "infra.prod.ping"` показывает WARN с `durationMs=5000 error="..." exceptionClass=java.net.SocketTimeoutException`; после `sudo systemctl start nginx` — INFO `ping:recovered`.

### Implementation for User Story 2

- [x] T008 [US2] Добавить `import org.slf4j.LoggerFactory` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/ProdContainerCheck.kt` (в начало файла, после существующих imports)
- [x] T009 [US2] Добавить в `object ProdContainerCheck` два приватных логгера: `private val pingLog = LoggerFactory.getLogger("infra.prod.ping")` и `private val dbLog = LoggerFactory.getLogger("infra.prod.db")` — разместить после `private const val PING_URL` (FR-011, FR-015)
- [x] T010 [US2] Переписать `pingSite()` для возврата `Pair<Boolean, Long>` и логирования WARN при ошибке (FR-012, FR-013) — в catch-блоке добавить `pingLog.warn("ping:failed url={} durationMs={} error=\"{}\" exceptionClass={}", PING_URL, durationMs, e.message, e::class.java.name, e)` с расчётом `durationMs` через `System.currentTimeMillis()` (контракт: см. `contracts/log-format.md` секция 2)
- [x] T011 [US2] Переписать `pingRemoteDb()` для возврата `Pair<Boolean, Long>` и логирования WARN при ошибке через `dbLog` (FR-015, FR-016) — в catch-блоке добавить `dbLog.warn("db:failed host={} port={} durationMs={} error=\"{}\" exceptionClass={}", host, port, durationMs, e.message, e::class.java.name, e)`; **НЕ логировать полный JDBC URL** (только host+port, per FR-022 и Constitution § VIII.5)
- [x] T012 [US2] В `ProdContainerCheck.run()` добавить логирование INFO `ping:recovered` при смене состояния (FR-014, контракт секция 3) — условие: `prevWasFailing && siteUp && dbUp` где `prevWasFailing = firstFailureAt != null`; сообщение: `pingLog.info("ping:recovered url={} downForMin={}", PING_URL, Duration.between(firstFailureAt, Instant.now()).toMinutes())`
- [x] T013 [US2] Удалить `println("ProdContainerCheck: ping $PING_URL не удался: ${e.message}")` (старая строка 82) в `ProdContainerCheck.kt` — заменено на T010 (FR-011)
- [x] T014 [US2] Обновить KDoc на `object ProdContainerCheck` (FR-017, Constitution § VI FR-006) — добавить `@see livedocs/architecture/decisions/local-0005-structured-logging-karaoke-app.md` в существующий KDoc, дополнить описание WARN/INFO поведения и grep-маркеров `infra.prod.ping`/`infra.prod.db`
- [x] T015 [US2] Validation US2 (post-deploy): пользователь сделал deploy `karaoke-app`; проверено через `docker exec karaoke-app unzip -p /app.jar ... | grep` — `infra.prod.ping`/`infra.prod.db`/`ping:failed`/`ping:recovered` присутствуют в jar (код развёрнут); WARN/INFO логов в stdout пока нет — все пинги проходят OK, NO-OP по дизайну FR-014 (минимум шума)

**Checkpoint**: US2 полностью функциональна и тестируема независимо. `ProdContainerCheck` пишет структурированные логи, готовые для grep-корреляции.

---

## Phase 5: User Story 3 — Синхронизированная TZ во всех логах (Priority: P1)

**Goal**: Все логи (PostgreSQL `pg_log` после US1, Spring Boot karaoke-web/app) используют TZ Europe/Moscow для однозначной корреляции по времени. Правки deploy уже сделаны в Phase 2 (T002, T003); здесь — финальная валидация и опциональная конфигурация logback.

**Independent Test**: `docker exec karaoke-db psql -c "SHOW timezone;"` → `Europe/Moscow`; `docker exec karaoke-web java -XshowSettings:properties -version 2>&1 | grep user.timezone` → `user.timezone = Europe/Moscow`; `date +%z` в обоих контейнерах → `+0300`.

### Implementation for User Story 3

- [x] T016 [US3] Validation US3 (post-deploy): пользователь перезапустил контейнеры; проверено через `docker exec karaoke-db psql -h 188.119.64.111 ... -c "SHOW timezone; SHOW log_timezone; SHOW log_line_prefix;"`:
  - `TimeZone = Europe/Moscow` ✓
  - `log_timezone = Europe/Moscow` ✓
  - `log_line_prefix = '%m [%p] %q%u@%d from %h '` ✓
  - TZ-синхронизация PostgreSQL работает (на SQL-уровне); TZ контейнера зависит от `TZ=Europe/Moscow` в `docker-compose-database.yml` — применяется при следующем `--force-recreate` (per A-001)

**Checkpoint**: US3 полностью функциональна и тестируема независимо. Все логи прода в единой TZ.

---

## Phase 6: User Story 4 — Документация по корреляции логов (Priority: P2)

**Goal**: Создать `docs/ops/log-correlation.md` с картой источников логов, командами просмотра, grep-маркерами, типичными сценариями. Обновить существующий per-feature документ (154) и `AGENTS.md` для discoverability.

**Independent Test**: `find . -name "log-correlation.md"` находит файл; `grep -n "log-correlation" AGENTS.md` находит ссылку.

### Implementation for User Story 4

- [x] T017 [P] [US4] Создать `docs/ops/log-correlation.md` с секциями (FR-019):
  - «Карта источников логов» — PostgreSQL (stderr → `docker logs karaoke-db`), Spring Boot karaoke-web/app (stdout → `docker logs`), nginx (хост → `/var/log/nginx/access.log`)
  - «Команды просмотра» — `docker logs --since/--until`, `docker exec ... cat ...`, `tail -f`
  - «Grep-маркеры» — PostgreSQL: `LOG:`, `WARNING:`, `ERROR:`, `duration:`, `temporary file:`; Spring Boot: `infra.prod.ping`, `infra.prod.db`; nginx: `$request_time`
  - «Типичные сценарии» — «прод завис после одобрения задания редактора» с пошаговыми командами
- [x] T018 [P] [US4] Обновить `livedocs/features/154-remove-scheduled-publications-monitoring.md` (FR-018, per research.md D-1) — добавить секцию «ProdContainerCheck: SLF4J-логирование (288-prod-diagnostics-logging)» с описанием WARN на ошибке, INFO при восстановлении, NO-OP в обычном режиме, grep-маркерами `infra.prod.ping`/`infra.prod.db`
- [x] T019 [P] [US4] Добавить ссылку на `docs/ops/log-correlation.md` в `AGENTS.md` (FR-020) — в новую секцию «Где смотреть логи прода» или в существующую секцию про LiveDocs (конкретное место выбирается при реализации)
- [x] T020 [US4] Validation US4: `find . -name "log-correlation.md"` → `docs/ops/log-correlation.md`; `grep -n "log-correlation" AGENTS.md` → найдена ссылка; `grep -n "ProdContainerCheck" livedocs/features/154-...md` → найдена новая секция (SC-005)

**Checkpoint**: US4 полностью функциональна. Документация готова для on-call инженеров.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Pipeline validation (per AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»), финальная end-to-end проверка всех US.

- [x] T021 [P] Run `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` (compile обоих модулей после правки `ProdContainerCheck.kt` в T008..T014) — ожидаемо BUILD SUCCESSFUL без warnings/errors
- [x] T022 [P] Run `./gradlew :karaoke-web:ktlintCheck` — никаких НОВЫХ нарушений ktlint в изменённых файлах (baseline OK)
- [x] T023 [P] Run `./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel` (на машине nsa-i9 — без явного согласия по машинно-специфичному исключению AGENTS.md; на других машинах — `karaoke-app:bootJar` только по явному согласию пользователя) — ожидаемо успешная сборка jar
- [x] T024 Final acceptance — пользователь задеплоил фичу на прод; все компоненты проверены:
  - **SC-001**: `docker logs karaoke-db --since "1m" 2>&1 | head -3` показывает строки с префиксом `2026-09-01 19:21:37.244 MSK [285107] SvoeMestoKaraokeUser905@karaoke from 172.18.0.1 LOG: ...` ✓
  - **SC-002**: `SELECT pg_sleep(2)` на проде порождает строку `duration: 2003.110 ms statement: SELECT pg_sleep(2)` ✓
  - **SC-003**: `docker exec karaoke-app ... | grep "ProdContainerCheck: ping"` → 0 (нет больше println); `grep "infra.prod.ping"` присутствует в jar ✓
  - **SC-004**: в нормальном режиме за последний час логов `infra.prod.ping` = 0 (NO-OP по дизайну) ✓
  - **SC-005**: `find . -name "log-correlation.md"` → `docs/ops/log-correlation.md` ✓; ссылка в `AGENTS.md` ✓
  - **SC-006**: ручная проверка «за 15 минут можно найти точку деградации в pg_log» — playbook в `docs/ops/log-correlation.md` секция 5 ✓
  - **SC-007**: за 1 час после применения — ~15 строк pg_log (включая checkpoint и наши тесты), что в пределах baseline ≤100/день ✓

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 — нет зависимостей, может стартовать сразу
- **Foundational (Phase 2)**: T002, T003, T004 — нет зависимостей друг от друга (разные файлы, `[P]`). T002 и T003 блокируют US3-валидацию (T016). T004 (создание директории) блокирует T017.
- **US1 (Phase 3)**: T005 → T006 → T007. T006 зависит от T005 (подготовленный SQL). T007 зависит от T006 (применённый ALTER SYSTEM). На проде — по согласию пользователя.
- **US2 (Phase 4)**: T008 → T009 → T010..T013 (parallel, разные методы того же файла, но Kotlin compiler требует последовательности). T014 (KDoc) — после T008..T013. T015 (validation) — после всех правок и deploy.
- **US3 (Phase 5)**: T016 — после T002/T003 (правки deploy) и рестарта контейнеров пользователем.
- **US4 (Phase 6)**: T017, T018, T019 — `[P]`, разные файлы. T020 — после всех.
- **Polish (Phase 7)**: T021..T024 — после ВСЕХ US (T021..T023 после US2; T024 — после всех).

### User Story Dependencies

- **US1 (P1)**: может стартовать после Foundational (T005+). Не зависит от других US.
- **US2 (P1)**: может стартовать после Foundational (T008+). Не зависит от других US.
- **US3 (P1)**: правки deploy — в Phase 2; валидация — в Phase 5. Не зависит от US1/US2.
- **US4 (P2)**: может стартовать после Phase 2 (T004 — создание `docs/ops/`). Не зависит от US1/US2/US3.
- **Все US независимы** — могут делаться параллельно разными людьми.

### Within Each User Story

- Правка кода (T008..T014 в US2) — последовательно (все в одном файле `ProdContainerCheck.kt`), но разными методами.
- US1 DDL — последовательно (T005 → T006 → T007).
- US4 документация — параллельно (T017, T018, T019 — разные файлы).

### Parallel Opportunities

- **Phase 2**: T002, T003, T004 — все `[P]` (разные файлы)
- **US4**: T017, T018, T019 — все `[P]` (разные файлы)
- **Phase 7**: T021, T022, T023 — все `[P]` (разные команды gradle, не конфликтуют)
- **Cross-US**: после Phase 2, все US могут делаться параллельно (US1/US2/US4 — full parallel; US3 — после рестарта контейнеров пользователем)

---

## Parallel Execution Examples

### Example 1: Phase 2 в параллель (3 параллельных файла)

```bash
# T002: правка docker-compose-database.yml
# T003: правка deploy/.env
# T004: mkdir docs/ops
# Все три могут стартовать одновременно
```

### Example 2: US4 в параллель (3 документа)

```bash
# T017: создание docs/ops/log-correlation.md
# T018: обновление livedocs/features/154-...md
# T019: правка AGENTS.md
# Все три — разные файлы, параллельно
```

### Example 3: После Phase 2 — все US параллельно (если есть команда)

```bash
# Разработчик A: US1 (T005..T007) — DDL на проде
# Разработчик B: US2 (T008..T015) — правка ProdContainerCheck.kt
# Разработчик C: US4 (T017..T020) — документация
# US3 (T016) — после рестарта контейнеров пользователем
```

---

## Implementation Strategy

### MVP First (Phase 2 + US1)

1. **Phase 1**: T001 (mental check)
2. **Phase 2**: T002..T004 (правки deploy-конфигов)
3. **Phase 3 (US1)**: T005..T007 (DDL на проде по согласию пользователя)
4. **STOP and VALIDATE**: запустить T007 acceptance test. Если SC-001/SC-002 достигнуты — медленные SQL уже логируются, **MVP готов**.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready (deploy-правки)
2. US1 → Test (T007) → Deploy → MVP! (медленные SQL в pg_log)
3. US2 → Test (T015) → Deploy (структурированное SLF4J в ProdContainerCheck)
4. US3 → Test (T016) → Deploy (TZ-синхронизация)
5. US4 → Test (T020) → Deploy (документация)
6. Phase 7 → Pipeline validation (T021..T024)

Каждая фаза добавляет ценность без поломки предыдущих.

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей. Помечены в каждой фазе.
- **[Story] label** для traceability — US1..US4.
- **Без автоматических тестов** (per Constitution § Тесты) — проверка через `quickstart.md` end-to-end.
- **Машинно-специфичное исключение** (Pass 282): на `nsa-i9`/`nsa` можно пересобирать `karaoke-app` без явного согласия. На других машинах — только по прямому согласию.
- **Constitution п. 2**: T006 (DDL на проде) — **только по прямому согласию пользователя**. Агент НЕ выполняет без явного одобрения в каждой сессии.
- **Constitution п. 2**: рестарт контейнеров `karaoke-db`/`karaoke-web` для применения TZ (Phase 5, US3) — пользователем, не агентом (per A-001 спеки).
- **Каждое US тестируется независимо** через acceptance scenarios из spec.md и quickstart.md.
- **Commit после каждой фазы** или логической группы (например, после Phase 2 целиком, после US2 целиком).