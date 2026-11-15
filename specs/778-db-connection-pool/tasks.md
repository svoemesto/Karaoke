---

description: "Task list template for feature implementation"
---

# Tasks: Ограниченный пул соединений к БД

**Input**: Design documents from `/specs/778-db-connection-pool/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: тесты включены — спека требует покрытия FR-001…FR-016 (§ «Стратегия тестов» плана). **В CI нет живой БД**: логика проверяется через `PoolProvider` (подставная реализация) и `java.lang.reflect.Proxy` для поддельного `java.sql.Connection`. Hikari-специфика — на стенде по `quickstart.md`.

**Organization**: задачи сгруппированы по пользовательским историям спеки; этапы 1–3 плана (каркас → подключение к `KaraokeConnection` → новый контракт рейпера) — основной объём. Этапы 4–8 плана (включение на стенде/проде, деплой, 7 суток наблюдения) вынесены в **Фазу 9 «Только по согласию владельца»** — они НЕ выполняются без отдельного слова.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: история спеки, к которой относится задача
- Пути указаны точно

## Пути

- Модули: `karaoke-app/` (ядро; от него зависит `karaoke-web/`), тесты — `karaoke-app/src/test/...`, `karaoke-web/src/test/...`
- Новый пакет: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/`
- Знание: `knowledge/adr/`, `knowledge/domains/persistence/domain.md`, `knowledge/product/features/`

---

## Phase 1: Setup (Общая инфраструктура)

**Purpose**: зависимость, конфигурация, дефолты.

- [ ] T001 Добавить зависимость `implementation("com.zaxxer:HikariCP")` (версия из BOM Spring Boot 3.5.6 → 6.3.3, без ручного pin) в `karaoke-app/build.gradle.kts`
- [ ] T002 [P] Добавить свойства пула с дефолтами (app: `max-local=128`, `max-server=20`) в `karaoke-app/src/main/resources/application.yml`
- [ ] T003 [P] Добавить свойства пула с дефолтами (web: `max-local=96`, `max-server=10`) в `karaoke-web/src/main/resources/application.yml`
- [ ] T004 [P] Прокинуть `KARAOKE_DB_POOL_*` в `deploy/docker-compose-web.yml` и `deploy/docker-compose-app.yml`
- [ ] T005 [P] Прокинуть `KARAOKE_DB_POOL_*` в `deploy/prod-single-host/docker-compose-web.yml` и `deploy/prod-single-host/docker-compose-app.yml`

**Checkpoint**: зависимость подключена, конфигурация объявлена, пул ещё не используется.

---

## Phase 2: Foundational (Каркас пула — этап 1 плана)

**Purpose**: инфраструктура пула. `KaraokeConnection` **не тронут**; пул создаётся лениво и никем не используется.

**⚠️ CRITICAL**: работа над историями не начинается, пока эта фаза не завершена.

- [ ] T006 Создать `PoolConfig` (разбор env/свойств → параметры пула; валидация: `maxPoolSize ≥ 1`, `minIdle ≤ maxPoolSize`, `keepalive < maxLifetime`, сумма потолков < 300) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PoolConfig.kt`
- [ ] T007 Создать интерфейс `PoolProvider` (`borrow()`, `release(channel)`, `totalConnections()`, `activeConnections()`, `idleConnections()`, `waitingThreads()`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PoolProvider.kt`
- [ ] T008 Создать `PooledHandle` — делегирующий прокси `java.sql.Connection`; `close()` → `provider.release(channel)`, **не** закрывает физический канал; защита от двойного возврата через `released` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PooledHandle.kt`
- [ ] T009 Создать `PoolOwnerRegistry` (`handle → OwnerRecord(owner, borrowedAt, lastUsed)`; метод поиска handle'ов с мёртвым владельцем через `Thread.isAlive()`) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PoolOwnerRegistry.kt`
- [ ] T010 Создать `DbPool` — обёртка над HikariCP: ленивое создание, `borrow()`/`release()`, `maximumPoolSize`/`minimumIdle`/`connectionTimeout`/`validationTimeout`/`keepaliveTime`/`maxLifetime` из `PoolConfig`, **URL передаётся как есть** (без разбора — mTLS) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/DbPool.kt`
- [ ] T011 [P] Тест `PoolConfig`: разбор параметров, дефолты, отказ на некорректных значениях в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/PoolConfigTest.kt`
- [ ] T012 [P] Тест `PooledHandle`: `close()` возвращает канал в пул ровно один раз и **не** закрывает физический канал; подделка `java.sql.Connection` через `java.lang.reflect.Proxy` в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/PooledHandleTest.kt`
- [ ] T013 [P] Тест `PoolOwnerRegistry`: handle взят потоком, поток завершён → registry находит его (`isAlive() == false`); handle живого потока не находится в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/PoolOwnerRegistryTest.kt`
- [ ] T014 Тест `DbPool` через подставной `PoolProvider` (без сети): потолок не превышается, повторное взятие не создаёт новый канал, исчерпание → таймаут ожидания, `DB-POOL-002` с `waiting`/`busy` в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/DbPoolTest.kt`

**Checkpoint**: каркас готов, тесты зелёные в CI без БД; пул ещё не подключён к `KaraokeConnection`.

---

## Phase 3: User Story 1 — Сайт не отказывает посетителям из-за БД (Priority: P1) 🎯 MVP

**Goal**: число физических соединений к БД ограничено потолком пула; приложение при исчерпании пула отказывает быстро (503 + событие), а не роняет БД для всех.

**Independent Test**: при включённом пуле — взять `maxPoolSize` handle'ов, попытаться взять ещё → ожидание до `acquire-timeout-ms`, затем отказ с событием `DB-POOL-002`; при выключенном пуле — поведение идентично сегодняшнему. Проверяется юнит-тестами через `PoolProvider` (без БД).

### Tests for User Story 1

- [ ] T015 [P] [US1] Тест контракта `getConnection()`: сигнатура не изменилась, ThreadLocal-кеш сохранён (два вызова из одного потока → тот же объект), поведение при выключенном пуле идентично прежнему (прямой `DriverManager`) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/KaraokeConnectionPoolTest.kt`

### Implementation for User Story 1

- [ ] T016 [US1] Переключить `getConnection()` в `KaraokeConnection` между «как раньше» (прямой `DriverManager`) и «через пул» по флагу `karaoke.db.pool.enabled`; в пуловом режиме `ThreadLocal` хранит `PooledHandle` вместо физического соединения; handle регистрируется в `PoolOwnerRegistry` с владельцем `Thread.currentThread()` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`
- [ ] T017 [US1] Изменить `closeThreadConnection()` в `KaraokeConnection`: в пуловом режиме — возврат handle'а в пул и снятие с реестра; в режиме без пула — прежнее поведение в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`
- [ ] T018 [US1] Создать пулы для ролей LOCAL и SERVER в фабриках `local()`/`remote()`; роль VIRTUAL — **без пула** (не используется в проде) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Connection.kt`
- [ ] T019 [US1] Создать пулы для ролей LOCAL и SERVER в фабриках web; роль VIRTUAL — без пула в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/Connection.kt`
- [ ] T020 [US1] Проверить, что **все 284 места вызова `getConnection()` в прикладном коде скомпилировались без правок** (это и есть проверка FR-005): `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`

**Checkpoint**: пул подключён под ThreadLocal; прикладной код не тронут; пул выключен по умолчанию → поведение прежнее.

---

## Phase 4: User Story 2 — Утечка больше не растёт (Priority: P1)

**Goal**: соединение, оставшееся от мёртвого потока, возвращается в пул (не закрывается и не копится); рейпер перестаёт закрывать рабочие каналы пула.

**Independent Test**: handle взят потоком → поток завершён → рейпер возвращает handle в пул → слот освобождён, число каналов не растёт. Проверяется юнит-тестом через `PoolProvider` (без БД).

### Tests for User Story 2

- [ ] T021 [P] [US2] Тест: рейпер возвращает handle'ы мёртвых владельцев и **не трогает** handle'ы живых (FR-012/FR-013); счётчик возвращённых считает только возвращённые в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/KaraokeReaperPoolTest.kt`
- [ ] T022 [P] [US2] Тест web-рейпера: симметричное поведение (тот же контракт, что и в app — разъезд поведения запрещён) в `karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/db/LocalConnectionReaperPoolTest.kt`

### Implementation for User Story 2

- [ ] T023 [US2] Переделать `reapIdleConnections()` в `KaraokeConnection`: в пуловом режиме — возврат handle'ов **мёртвых** владельцев в пул (живые не трогаются; простаивающие лишь помечаются «осиротевшими»); в режиме без пула — прежнее поведение (закрытие каналов по простою) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`
- [ ] T024 [US2] Обновить рейпер app: в пуловом режиме считать и логировать **возвращённые handle'ы**, а не закрытые каналы; сохранить режим без пула без изменений в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/LocalConnectionReaper.kt`
- [ ] T025 [US2] Обновить рейпер web симметрично app в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/LocalConnectionReaper.kt`

**Checkpoint**: утечка ограничена потолком; рейпер не портит рабочие соединения пула.

---

## Phase 5: User Story 3 — Скорость не хуже (Priority: P2)

**Goal**: латентность эндпоинтов, ходящих в БД, не хуже базовой; физический коннект не появляется в горячем пути.

**Independent Test**: юнит-тест «N выдач из пула → число созданий физического канала == 1»; фактические цифры — на стенде (Фаза 9).

### Tests for User Story 3

- [ ] T026 [P] [US3] Тест: повторное взятие handle'а не создаёт новый физический канал (счётчик созданий == 1 при N операциях) — FR-004 в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/KaraokeConnectionPoolTest.kt`
- [ ] T027 [P] [US3] Тест: self-healing сохранён — после `SQLException` на канале следующий `getConnection()` берёт новый handle, исключения «соединение уже было закрыто» нет (specs/236, BR-274) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/KaraokeConnectionPoolTest.kt`

### Implementation for User Story 3

- [ ] T028 [US3] Убедиться, что `markConnectionBroken()` и прокси `wrapForBrokenDetection` работают поверх `PooledHandle` без изменений семантики; при необходимости — скорректировать обработку `SQLException` в `PooledHandle` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PooledHandle.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt`

**Checkpoint**: латентностные инварианты зафиксированы тестами; фактические замеры — в Фазе 9.

---

## Phase 6: User Story 4 — Оператор видит, где именно течёт (Priority: P3)

**Goal**: отказ приложения отличим от отказа БД; место утечки видно по стеку вызовов.

**Independent Test**: юнит-тест — при исчерпании пула пишется `DB-POOL-002` с `target`/`waiting`/`busy`; при долгом удержании — `DB-POOL-003` со стеком.

### Tests for User Story 4

- [ ] T029 [P] [US4] Тест событий: `DB-POOL-002` при исчерпании (поля `target`, `waiting`, `busy`, `maxPoolSize`, `timeoutMs`); код события отличается от `DB-CONNECT-001` (отказ БД) в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/DbPoolEventsTest.kt`
- [ ] T030 [P] [US4] Тест детектора утечки: handle удерживается дольше порога → `DB-POOL-003` со стеком вызовов; при этом handle **не** отбирается у живого владельца в `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/db/DbPoolEventsTest.kt`

### Implementation for User Story 4

- [ ] T031 [US4] Писать `DB-POOL-002` (исчерпание пула) при отказе по `acquire-timeout-ms` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/DbPool.kt`
- [ ] T032 [US4] Реализовать детектор `DB-POOL-003` (долгое удержание handle со стеком вызовов места взятия) в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/PoolOwnerRegistry.kt` и `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/db/DbPool.kt`
- [ ] T033 [US4] Писать `DB-POOL-004` (рейпер вернул handle'ы) в рейперах — метрика того, что FR-003 работает в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/LocalConnectionReaper.kt` и `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/LocalConnectionReaper.kt`
- [ ] T034 [US4] Сверить реализованные события с контрактом и обновить его при расхождениях в `specs/778-db-connection-pool/contracts/db-pool-events.md`

**Checkpoint**: наблюдаемость полна; отказ приложения отличим от отказа БД.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T035 [P] Обновить ADR: пул под ThreadLocal-контрактом, «третий тип потока» снимается, рейпер возвращает handle'ы, ограничение «пул ограничивает, а не устраняет» — явно в `knowledge/adr/local-0009-thread-local-connection-lifecycle.md`
- [ ] T036 [P] Обновить таблицу типов потока и параметры рейпера/пула в `knowledge/domains/persistence/domain.md`
- [ ] T037 Создать per-feature документ (FR-009) в `knowledge/product/features/db-connection-pool.md`
- [ ] T038 Проверить, что конфигурация пула попала в compose-файлы обоих стеков и прод-варианты (T004, T005) и что `KARAOKE_DB_POOL_ENABLED` по умолчанию `false` во всех четырёх файлах
- [ ] T039 Прогнать полные наборы тестов: `./gradlew :karaoke-app:test :karaoke-web:test` — оба зелёные, без живой БД
- [ ] T040 Прогнать `python3 tools/lint-knowledge.py`, `python3 tools/check-doc-references.py`, `python3 tools/check-ssot-impact.py` — без ошибок
- [ ] T041 Подготовить `specs/778-db-connection-pool/report.md` (REQUIRED-артефакт Plane-workflow) с описанием реализации, отклонений и результатов тестов
- [ ] T042 Коммит на ветку `778-db-connection-pool` через `git commit -F -` (никаких коммитов в master)

**Checkpoint**: код, знание и отчёт готовы; можно переходить к Фазе 9 — **только по согласию владельца**.

---

## Phase 9: Только по согласию владельца (этапы 4–8 плана)

**⚠️ НЕ ВЫПОЛНЯТЬ без отдельного словного согласия владельца.** Деплой, рестарт контейнеров, включение на стенде и проде, суточное/7-суточное наблюдение — зона лида.

- [ ] T043 Получить решение владельца по расхождению **SC-001** (утверждённая спека: «не более 120 на процесс»; при sizing от конфигурации app даёт 148) — поднять порог в спеке или уменьшить потолки; зафиксировать в `specs/778-db-connection-pool/spec.md`
- [ ] T044 Получить решение владельца по расхождению **SC-005** («не позднее 15 минут» против фактического критерия «≤ idle-ms + interval-ms = 20 минут»)
- [ ] T045 Включить пул на стенде: `KARAOKE_DB_POOL_ENABLED=true`, рестарт через `deploy/do.sh start_web` / `start_app`; прогнать сценарии `quickstart.md` (шаги 2–7), снять замеры M1–M7
- [ ] T046 Перед включением на проде — сверить размер пула с фактическим числом потоков на целевой машине (`nproc`, `server.tomcat.threads.max`, размеры пулов HealthReport); при другом `nproc` — пересчитать дефолт по формуле `plan.md` § «Размеры пулов»
- [ ] T047 Включить пул на проде (web), наблюдение 24–72 ч; замеры P1–P3
- [ ] T048 Включить пул на админ-машине (app) — **по Pass 380**: сначала остановить очередь `KaraokeProcess`, дождаться завершения активных заданий, только потом рестарт; после рестарта поднять очередь кнопкой «Старт»; проверить, что задания доходят до `DONE`
- [ ] T049 Наблюдение 7 суток на проде (SC-003, SC-004): ни одного `too many clients`, счётчик соединений стабилен
- [ ] T050 Обновить `specs/778-db-connection-pool/report.md` результатами замеров; затем `bash tools/tracker.sh add-comment 430 --file specs/778-db-connection-pool/report.md` и `bash tools/tracker.sh mark-review 430`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → без зависимостей, стартует сразу
- **Foundational (Phase 2)** → зависит от Phase 1; **блокирует** все истории
- **US1 (Phase 3)** → зависит от Phase 2; подключение пула к `KaraokeConnection`
- **US2 (Phase 4)** → зависит от US1 (нужен подключённый пул, чтобы менять рейпер)
- **US3 (Phase 5)** → зависит от US1; тесты латентностных инвариантов независимы от US2
- **US4 (Phase 6)** → зависит от US1; события наблюдаемости
- **Polish (Phase 7)** → зависит от US1–US4
- **Phase 9 (гейт владельца)** → зависит от Phase 7 + решений по SC-001/SC-005

### User Story Dependencies

- **US1 (P1)**: старт после Phase 2 — без зависимостей от других историй
- **US2 (P1)**: старт после US1 — использует подключённый пул
- **US3 (P2)**: старт после US1 — независима от US2
- **US4 (P3)**: старт после US1 — независима от US2/US3

### Within Each User Story

- Тесты пишутся и **падают** до реализации
- Модели/сущности → сервисы → интеграция
- Стори завершена → переход к следующей

### Parallel Opportunities

- Все задачи Setup с `[P]` (T002–T005) — параллельно
- Тесты Phase 2 с `[P]` (T011–T014) — параллельно; каркас (T006–T010) — последовательно, так как тесты зависят от API
- T015, T021, T022, T026, T027, T029, T030 — тесты разных историй, параллельно после Phase 2
- T035, T036 — обновления знания, параллельно

---

## Parallel Example: Phase 2 (каркас пула)

```bash
# Сначала каркас (последовательно — тесты зависят от API):
Task: "T006 Создать PoolConfig"
Task: "T007 Создать интерфейс PoolProvider"
Task: "T008 Создать PooledHandle"
Task: "T009 Создать PoolOwnerRegistry"
Task: "T010 Создать DbPool"

# Затем тесты параллельно:
Task: "T011 Тест PoolConfig"
Task: "T012 Тест PooledHandle"
Task: "T013 Тест PoolOwnerRegistry"
```

## Parallel Example: User Story 1

```bash
Task: "T015 [US1] Тест контракта getConnection()"
# после реализации T016–T019:
Task: "T020 [US1] Проверить компиляцию 284 мест вызова"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup
2. Phase 2: Foundational (каркас) — **блокирует всё**
3. Phase 3: US1 — пул подключён под ThreadLocal
4. **СТОП и ПРОВЕРКА**: пул выключен по умолчанию → поведение прежнее; при включении — потолок работает
5. Phase 4: US2 — возврат по смерти потока (ядро фичи)
6. Phase 5–6: US3, US4 — латентность и наблюдаемость
7. Phase 7: Polish (знание, отчёт)
8. Phase 9 — **только по согласию владельца**

### Incremental Delivery

Каждая история даёт самостоятельную ценность и проверяется независимо. Порядок — по приоритету (P1 → P1 → P2 → P3). Фича **не деплоится** до Phase 9.

---

## Notes

- **[P]** = разные файлы, нет зависимостей
- **[Story]** = история спеки; Setup/Foundational/Polish — без метки
- **Тесты в CI не требуют БД**: `PoolProvider` (подставная реализация) + `java.lang.reflect.Proxy` для `java.sql.Connection`. Запрещены `mockStatic`/`mockito-inline`/`mockk` — не используются
- Пул **выключен по умолчанию** (`KARAOKE_DB_POOL_ENABLED=false`) — безопасная доставка по шагам и первая линия отката
- **Никаких деплоев, рестартов контейнеров, коммитов в master и merge** — зона лида (Фаза 9)
- Коммиты — только на ветке `778-db-connection-pool`, через `git commit -F -`
- Pre-commit: `PRE_COMMIT_HOME=/home/nsa/Karaoke/.scratch/karaoke/pre-commit-cache` (обычный `~/.cache/pre-commit` в песочнице read-only)
