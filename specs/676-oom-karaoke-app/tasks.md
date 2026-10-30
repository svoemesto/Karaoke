# Tasks: Устойчивость karaoke-app к исчерпанию памяти

**Input**: Design documents from `specs/676-oom-karaoke-app/`
(**Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Research**: [research.md](research.md) ·
**Data model**: [data-model.md](data-model.md) · **Contracts**: [contracts/](contracts/) ·
**Quickstart**: [quickstart.md](quickstart.md))

**Tests**: включены — спека их требует явно (SC-001, SC-002, FR-004, FR-015…FR-017).

**Organization**: по историям пользователя из спеки (US1…US5), в порядке приоритета.

**Жёсткие ограничения (нарушение = провал задачи)**

| # | Ограничение | Источник |
|---|---|---|
| О1 | Только ветка `676-oom-karaoke-app`; master не трогаем; **без push/PR/merge/деплоя** | задание лида, AGENTS.md § Git |
| О2 | `kara-post-edit` **после** каждой правки кода; `kara-pre-commit` **перед** каждым коммитом (в финальном — **без SKIP**) | AGENTS.md Pass 239+245 |
| О3 | Gradle — с `GRADLE_USER_HOME` от корня репозитория; Docker — с `DOCKER_CONFIG`; контейнеры — **только** через `deploy/do.sh`; фронтенд — `cd <dir> && npm run` | AGENTS.md Pass 372-375 |
| О4 | Секреты не коммитить и не печатать (`deploy/.env`, `deploy/do.env`); `.env` **не трогать** | Constitution VIII, решение лида |
| О5 | Никаких операций с systemd на машине (`systemctl enable/start`) — только по слову владельца | решение лида 2026-10-30 |
| О6 | Read-only-инфраструктура не изменяется (`/home/nsa/agents-team-srv`, `~/.dsh/**`) | AGENTS.md |
| О7 | Grep — только с `--include` или по конкретным каталогам | задание лида |
| О8 | ADR **append-only**: `knowledge/adr/local-0013-…` **не редактировать** | `knowledge/adr/README.md`, решение лида |
| О9 | `popPublicationStacks` остаётся в `model/Publication.kt` с буквальной строкой `while (stacks.any { it.isNotEmpty() })`; `audit/PkgS212SourceContractsTest` не переписывается | решение лида (правка 2) |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: зафиксировать исходное состояние и договорённости, чтобы отличать
свою регрессию от чужой.

- [ ] T001 [x] **R1-правка (факт об образе)** — в `specs/676-oom-karaoke-app/research.md` (R1) и `specs/676-oom-karaoke-app/plan.md` (Technical Context) заменить `eclipse-temurin:22-jre-jammy` на фактический образ `sm-karaoke.ru:5443/library/ubuntu:22.04` + `openjdk-17-jre-headless` (JDK 17), обоснование — «не зависит от состава JRE/JDK и наличия `java.management`» · **выполнено в коммите `b6c2ae7c`**
- [ ] T002 Зафиксировать зелёный baseline: `GRADLE_USER_HOME=… ./gradlew :karaoke-app:test` — вывод сохранить в `specs/676-oom-karaoke-app/report.md` (без baseline нельзя отличить регрессию от исходного сбоя)
- [ ] T003 [P] Выбрать свободный BR-код для нового guard-правила (проверить `knowledge/system/infra/audit-336-registry.md`: используются BR-600…BR-628) и подготовить формат строки реестра по образцу строки BR-188 (`audit-336-registry.md:427`)
- [ ] T004 [P] Зафиксировать перечень 12 документов FR-020 с конкретной правкой в каждом (таблица «документ → что меняем») — в `specs/676-oom-karaoke-app/report.md`, раздел «База знаний»

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: инварианты и обвязка, без которых истории нельзя реализовать
корректно.

- [ ] T005 Зафиксировать инвариант BR-188 (О9): убедиться по коду, что `popPublicationStacks` остаётся в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt` и содержит буквально `while (stacks.any { it.isNotEmpty() })`; `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS212SourceContractsTest.kt` — **не трогать** (fallback-перенос тела раскладки запрещён: выносить можно только сторону вызывающего)
- [ ] T006 Зафиксировать порядок гейтов для реализации (О2): после каждой правки кода — `kara-post-edit`; перед каждым коммитом — `kara-pre-commit`; финальный коммит — без SKIP; в финальном коммите не должно быть файлов `.idea/` (в `.idea/misc.xml` — посторонняя правка IDE, изменилась **до** сессии, в коммит не включается)
- [ ] T007 [P] Подготовить тестовую обвязку подмены JDBC для US1: переиспользовать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/FakeKaraokeConnection.kt` и `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/SqlInjectionTestSupport.kt` (`MockJdbc`); новых зависимостей не добавлять (Mockito уже в `spring-boot-starter-test`)

**Checkpoint**: инварианты и гейты зафиксированы — можно переходить к историям.

---

## Phase 3: User Story 1 — Раскладка публикаций конечна (Priority: P1) 🎯 MVP

**Goal**: закрепить инвариант конечности раскладки **на уровне вызывающих**
(`getUnPublicationList` / `getSkipedPublicationList`) — там, где был OOM.

**Independent Test**: `Publication.getUnPublicationList(...)` на наборе из 500
неопубликованных песен (10 авторов по 50) возвращает сетку 50 строк × 10
столбцов за конечное время; на прежней реализации (захваченный максимум) вызов
не завершается.

**Ничего в product-коде не меняется** — починка уже в master (`4d643554`).

### Tests for User Story 1

- [ ] T008 [P] [US1] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PublicationGridCallerTest.kt` — сценарий A: `Publication.getUnPublicationList(...)` с JDBC-подменой (500 песен, 10 авторов, `tbl_authors` пуст) → сетка 50×10, суммарно 500 `Publication`, все `publish10` заполнены
- [ ] T009 [P] [US1] В том же файле `PublicationGridCallerTest.kt` — сценарий B: `Publication.getSkipedPublicationList(...)` (авторы со `skip = true`) → та же сетка 50×10; сценарий C: пустой набор → 0 строк без зависания (файл `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/PublicationGridCallerTest.kt`)
- [ ] T010 [US1] Проверить «падает на прежней реализации»: локально (вне коммита) вернуть в `model/Publication.kt` захваченный максимум размера стопки, убедиться, что тест не завершается (таймаут), затем **откатить** правку; зафиксировать результат в `report.md`
- [ ] T011 [US1] Прогнать `GRADLE_USER_HOME=… ./gradlew :karaoke-app:test --tests '*PublicationGridCallerTest*' --tests '*PkgS212*'` → зелёные (новый тест + оба существующих `PkgS212*` не сломаны — FR-004)

### Implementation for User Story 1

- [ ] T012 [US1] Проверить ревью диффа: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Publication.kt` **не изменён** (FR-003); `/api/unpublications2` и `/api/publicationsdigest` не затронуты (FR-005); `audit/PkgS212BehaviorTest.kt` и `audit/PkgS212SourceContractsTest.kt` не изменены

**Checkpoint**: US1 закрыт — инвариант конечности закреплён на уровне вызывающих.

---

## Phase 4: User Story 2 — У контейнера есть предел памяти (Priority: P1)

**Goal**: `karaoke-app` ограничен 8 ГБ памяти, heap JVM — 6 ГБ, и **связь между
ними видна в репозитории** (FR-006…FR-008).

**Independent Test**: чтение `deploy/docker-compose-app.yml` + зелёный
`tools/check-deploy-hardening.sh` (новое правило BR-630).

### Implementation for User Story 2

- [ ] T013 [US2] В `deploy/docker-compose-app.yml` (сервис `karaoke-app`) добавить `mem_limit: 8g` с комментарием: предел памяти контейнера, запас ~2 ГБ на метапространство/native/потоки
- [ ] T014 [US2] В `deploy/docker-compose-app.yml` заменить `command: ${APP_JAVA_OPTS} -jar /app.jar` на `command: ${APP_JAVA_OPTS} -Xmx6g -jar /app.jar` — **`-Xmx6g` строго ПОСЛЕ `${APP_JAVA_OPTS}`** (последний `-Xmx` побеждает — проверено: `java -Xmx8g -Xmx6g` → `MaxHeapSize` = 6 GiB), с комментарием про устаревшее `-Xmx8g` в неотслеживаемом `deploy/.env`
- [ ] T015 [P] [US2] В `tools/check-deploy-hardening.sh` добавить правило `check_br629` (по образцу `check_br547`): `mem_limit: 8g` задан; `-Xmx6g` присутствует и стоит **после** `${APP_JAVA_OPTS}`; **heap < предел**; зарегистрировать в массиве `CHECKS`; убедиться, что `--self-test` ловит пустой `deploy/docker-compose-app.yml` (фикстура уже создаётся в self-test)
- [ ] T016 [US2] Прогнать `bash tools/check-deploy-hardening.sh` и `bash tools/check-deploy-hardening.sh --self-test` → зелёные

**Checkpoint**: US2 закрыт — предел памяти и связка «heap ↔ предел» зафиксированы в репозитории.

---

## Phase 5: User Story 3 — Следующий OOM оставляет дамп (Priority: P1)

**Goal**: при OOM JVM пишет дамп heap на том, том не переполняется
(FR-009…FR-013).

**Independent Test**: `deploy/docker-compose-app.yml` содержит
`-XX:+HeapDumpOnOutOfMemoryError`, `-XX:HeapDumpPath=/dumps` и монтирование
`./karaoke-db/dumps:/dumps`; retention-скрипт на временном каталоге оставляет
последние 2 `*.hprof` и не трогает `.sql`.

### Implementation for User Story 3

- [ ] T017 [US3] В `deploy/docker-compose-app.yml` дополнить `command:` флагами `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps` (после `${APP_JAVA_OPTS}` и после `-Xmx6g`) и добавить в `volumes:` строку `- ./karaoke-db/dumps:/dumps` с комментарием (BR-046/BR-047 — тот же каталог, что у `karaoke-db`)
- [ ] T018 [P] [US3] Создать `deploy/heap-dump-retention.sh`: политика «последние 2 `*.hprof`, суммарно ≤ 20 ГБ, вытеснение по возрасту», защита от файла моложе `MIN_AGE_MINUTES`; **строго только `*.hprof`** (`.sql`-дампы БД в этом же каталоге не трогать); ключ каталога — 1-й аргумент или `HEAP_DUMP_DIR`, по умолчанию `$(dirname "$0")/karaoke-db/dumps`; режим `--self-test` (временный каталог); **работает без установки** (О5); `bash -n` зелёный
- [ ] T019 [P] [US3] Создать `deploy/heap-dump-retention.service` и `deploy/heap-dump-retention.timer` по образцу `deploy/karaoke-db-backup.service` / `.timer` (oneshot + таймер); **ничего не устанавливать и не включать** (О5) — активация описана в runbook'е как действие владельца
- [ ] T020 [US3] Расширить `check_br629` в `tools/check-deploy-hardening.sh`: `-XX:+HeapDumpOnOutOfMemoryError`, `-XX:HeapDumpPath=/dumps`, монтирование `./karaoke-db/dumps:/dumps`; прогнать `bash tools/check-deploy-hardening.sh` и `--self-test` → зелёные
- [ ] T021 [US3] Прогнать `bash deploy/heap-dump-retention.sh --self-test` и ручной прогон на временном каталоге с 4 файлами `*.hprof` + 1 файлом `*.sql` → остаются 2 newest `*.hprof`, `.sql` цел, вывод содержит `kept=/removed=/freedBytes=/totalBytes=`
- [ ] T022 [US3] Проверить `git check-ignore -v deploy/karaoke-db/dumps/test.hprof` → правило `/deploy/karaoke-db/dumps/*` (кроме `.gitkeep`) действует; дампы в git не попадают (FR-012)

**Checkpoint**: US3 закрыт — дамп пишется на том, политика хранения реализована и проверяема.

---

## Phase 6: User Story 4 — Загрузка heap видна до OOM (Priority: P1)

**Goal**: 8-я проверка мониторинга публикует загрузку heap раз в минуту и
алертит по порогам 75 % / 90 % (FR-014…FR-019).

**Independent Test**: юнит-тест `HeapUsageCheckTest` (порог → severity,
recovery, стабильность ключа, отсутствие побочных эффектов, время выполнения) +
проверка, что пороги **явно** заданы в `deploy/docker-compose-app.yml`.

### Implementation for User Story 4

- [ ] T023 [US4] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/checks/HeapUsageCheck.kt` (`object : MonitorCheck`): чтение `Runtime` (`maxMemory`, `totalMemory - freeMemory`), пороги из env `MONITOR_HEAP_WARN_PERCENT` / `MONITOR_HEAP_CRITICAL_PERCENT` (дефолты 75 / 90, разбор по ADR `local-0001`), алерт с ключом `monitor.heap.usage`, severity WARNING/CRITICAL по порогу, категория логов `infra.monitor.heap`, формат `event:key=value`, строка INFO о загрузке на каждом тике, INFO-recovery на переходе из «алерт» в «норма», состояние `@Volatile` в памяти (без новых персистентных ключей), `resolveAction = null`, KDoc + `@see knowledge/product/features/heap-memory-guardrails.md`
- [ ] T024 [US4] Зарегистрировать `HeapUsageCheck` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/monitor/MonitorRegistry.kt` — одна строка в `MonitorRegistry.checks` (8-я проверка)
- [ ] T025 [P] [US4] Создать `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/monitor/HeapUsageCheckTest.kt`: ниже порога → пустой список; в [warn, critical) → WARNING; ≥ critical → CRITICAL; два вызова подряд → один и тот же `key`; переход в норму → recovery-сообщение и пустой список; повторное превышение → снова алерт; время выполнения < 1 с; пороги из env (подстановка через `System.getenv` в тесте — переиспользовать приём существующих тестов конфигурации)
- [ ] T026 [US4] В `deploy/docker-compose-app.yml` (блок `environment:`, список) добавить `- MONITOR_HEAP_WARN_PERCENT=75` и `- MONITOR_HEAP_CRITICAL_PERCENT=90` с комментарием «пороги алерта загрузки heap (FR-015)»
- [ ] T027 [US4] Прогнать `GRADLE_USER_HOME=… ./gradlew :karaoke-app:test --tests '*HeapUsageCheckTest*'` и затем полный `:karaoke-app:test` → зелёные
- [ ] T028 [US4] Выполнить `kara-post-edit` после правок кода (compile + ktlint + eslint + bootJar) — по чеклисту скилла; результат в `specs/676-oom-karaoke-app/report.md`

**Checkpoint**: US4 закрыт — загрузка heap видна до OOM.

---

## Phase 7: User Story 5 — База знаний и runbook соответствуют системе (Priority: P2)

**Goal**: 12 документов FR-020 приведены в соответствие фактам, заведён ADR и
per-feature документ, runbook описывает порядок действий при алерте (FR-020,
FR-021, FR-013).

**Independent Test**: `python3 tools/lint-knowledge.py`,
`bash tools/check-knowledge-structure.sh`, `python3 tools/check-ssot-impact.py`,
`bash tools/check-feature-doc.sh knowledge/product/features/heap-memory-guardrails.md`
— зелёные; ссылки валидны.

### Implementation for User Story 5

- [ ] T029 [P] [US5] `knowledge/domains/monitoring/domain.md`: 8-я проверка в «Структура компонентов»/Ubiquitous Language; фактические «метрики — TODO» остаются; Linking Protocol L3→L2 (проверка перечислена)
- [ ] T030 [P] [US5] `knowledge/domains/monitoring/components/monitor-core.md`: регистрация `HeapUsageCheck` в `MonitorRegistry.checks`; **исправить устаревшее «fixedRate» → фактический `fixedDelay`** (`MonitoringService.kt:61`)
- [ ] T031 [P] [US5] `knowledge/domains/monitoring/components/monitor-checks.md`: строка 8-й проверки (пороги, канал, ключ алерта); **исправить дрейф**: интерфейс `run(ctx): List<MonitorAlert>` (не `val name` + `check()`), алгоритм тика — по факту (`snapshot = alerts.associateBy { it.key }`)
- [ ] T032 [P] [US5] `knowledge/domains/monitoring/components/monitor-checks-detailed.md`: детальное описание `HeapUsageCheck` (источник данных, пороги, severity, recovery, инварианты)
- [ ] T033 [P] [US5] `knowledge/domains/monitoring/components/log-categories.md`: категория `infra.monitor.heap`; **привести «recovery — не INFO» в соответствие с `domain.md:90` и FR-017 → INFO**
- [ ] T034 [P] [US5] `knowledge/domains/catalog/components/remaining-models.md`: сигнатура и строка `popPublicationStacks`; инвариант «строки = максимум песен у автора / столбцы = авторы»; остаточный риск объёма раскладки; указание на существующие **и новые** тесты (`PkgS212*`, `PublicationGridCallerTest`); факт отсутствия авторизации на легаси-странице
- [ ] T035 [P] [US5] `knowledge/system/infra/deploy-overview.md`: где живёт `karaoke-app`; фактические лимиты (контейнер 8 ГБ / heap 6 ГБ); том дампов и путь; «`-Xmx` теперь зафиксирован в репозитории» (было: только в неотслеживаемом `.env`)
- [ ] T036 [P] [US5] `knowledge/system/02-containers.md`: лимиты и JVM-опции контейнера `karaoke-app` (там сейчас ни слова о памяти)
- [ ] T037 [P] [US5] `knowledge/system/infra/audit-336-registry.md`: синхронизировать формулировку BR-188 + добавить строку нового BR-кода (T003)
- [ ] T038 [P] [US5] `knowledge/guidelines/runbooks/log-correlation.md`: runbook по дампам и алерту памяти (FR-013) — порядок действий: (1) найти алерт `infra.monitor.heap` / `monitor.heap.usage`, (2) снять дамп из `deploy/karaoke-db/dumps` **до** перезапуска, (3) проверить фактические лимиты контейнера, (4) безопасно перезапустить (Pass 380: сначала остановить очередь!), (5) ручная уборка дампов `bash deploy/heap-dump-retention.sh`; отдельно: «фактический лимит задаётся compose, значение в неотслеживаемом `deploy/.env` на него не влияет»; «активация таймера retention'а — действие владельца» (О5)
- [ ] T039 [P] [US5] `archive/docs/architecture-notes.md`: запись об инциденте 2026-10-23 (её нет вовсе) — по формату файла
- [ ] T040 [US5] Создать `knowledge/adr/local-0014-heap-memory-guardrails-karaoke-app.md` по шаблону `knowledge/templates/adr.md` (источник данных о heap, место хранения дампов и исполнитель политики, способ фиксации пары «heap ↔ предел») + внести его в `knowledge/adr/README.md`; **`knowledge/adr/local-0013-…` НЕ редактировать** (О8) — уточнение области решения («journal-only — про infra-сервер; для `karaoke-app` канал — журнал/SLF4J по FR-018») фиксируется в `local-0014` и в домене `monitoring`
- [ ] T041 [US5] Создать `knowledge/product/features/heap-memory-guardrails.md` (6 обязательных секций по `tools/check-feature-doc.sh`, `slug` == имя файла, `status` ∈ {active, deprecated, experimental}) + добавить строку в индекс `knowledge/product/README.md`; обновить `knowledge/product/features/publications-filters.md` (FR-021)
- [ ] T042 [US5] Прогнать гейты базы знаний: `python3 tools/lint-knowledge.py`, `bash tools/check-knowledge-structure.sh`, `python3 tools/check-ssot-impact.py`, `bash tools/check-feature-doc.sh knowledge/product/features/heap-memory-guardrails.md` → зелёные; Linking Protocol L3→L2→L1 соблюдён

**Checkpoint**: US5 закрыт — база знаний соответствует системе.

---

## Phase 8: Отчёт, гейты, финальный коммит

**Purpose**: зафиксировать результат и закрыть ветку коммитом.

- [ ] T043 Создать `specs/676-oom-karaoke-app/report.md` по образцу `specs/675-merge-docs-into-knowledge/report.md`: Issue/ветка/дата · что сделано по блокам A–E · таблица FR→где закрыто · фактический вывод гейтов (команда → результат) · **отклонение от буквы FR-020** (`local-0013` не правится — О8) · факты/решения для владельца: (1) устаревшее `APP_JAVA_OPTS=-Xmx8g` в неотслеживаемом `deploy/.env` — compose перекрывает, файл не трогали; (2) `cpus` не добавляли — возможное отдельное решение; (3) активация systemd-таймера retention'а — действие владельца; (4) факт расхождения `constitution.md` (образ `eclipse-temurin:22-jre-jammy`) с фактическим `deploy/karaoke-app/Dockerfile` (`ubuntu:22.04` + `openjdk-17-jre-headless`) — кандидат на отдельное решение владельца · diffstat · список коммитов
- [ ] T044 Выполнить `kara-post-edit` (полный чеклист: compile, ktlint, eslint, bootJar) и `pre-commit run --all-files` — все хуки зелёные, **без SKIP**; вывод в `specs/676-oom-karaoke-app/report.md`
- [ ] T045 Прогнать `specs/676-oom-karaoke-app/quickstart.md` § 1–4 (модульный прогон, гейты, конфигурация, база знаний) — § 5 (стенд: контейнеры) **не выполняется** без согласия владельца (О1/О3)
- [ ] T046 Отметить выполненные задачи в `specs/676-oom-karaoke-app/tasks.md` (чекбоксы) и обновить раздел «Состояние» в `plan.md`, если фактический порядок разошёлся
- [ ] T047 Финальный коммит в ветку `676-oom-karaoke-app` (сообщение в стиле репозитория, с объяснением «почему»); **без** `--no-verify`, **без** push/PR/merge

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → без зависимостей
- **Foundational (Phase 2)** → зависит от Setup; **блокирует** все истории
- **US1…US4 (P1)** → зависят от Foundational; US2/US3/US4 трогают **один и тот же** файл `deploy/docker-compose-app.yml` → выполнять **последовательно** (US2 → US3 → US4), параллелить нельзя
- **US5 (P2)** → зависит от кода и конфигурации (T023…T028) — править базу знаний по факту
- **Polish (Phase 8)** → зависит от всех историй

### User Story Dependencies

- **US1**: не зависит от других; самостоятельный MVP-инкремент
- **US2**: не зависит; но делит файл compose с US3/US4 → последовательно
- **US3**: зависит от US2 (тот же файл compose: `mem_limit` + `-Xmx6g` + флаги дампа идут одной правкой)
- **US4**: зависит от US2/US3 (тот же файл compose: пороги добавляются тем же блоком `environment:`)
- **US5**: зависит от US1…US4 (документы пишутся по факту реализации)

### Within Each User Story

- Тесты пишутся/готовятся **до** или одновременно с реализацией и обязаны падать на прежнем поведении (SC-001)
- Модель/конфигурация → реализация → регистрация → тест
- Story complete before moving to next priority

### Parallel Opportunities

- T003, T004 (Phase 1) — параллельно
- T007 (Phase 2) — параллельно с T005/T006
- T008, T009 (US1) — параллельно (один файл, но независимые методы)
- T015 (US2, `tools/`) — параллельно с правкой compose
- T018, T019 (US3) — параллельно (разные файлы)
- T023, T025 (US4) — T025 после T023 (тест зовёт класс); T026 (compose) — параллельно
- T029…T041 (US5) — **все [P]**: разные файлы `knowledge/`, идут параллельно

---

## Parallel Example: User Story 3

```bash
# Параллельно (разные файлы):
Task: "Создать deploy/heap-dump-retention.sh"
Task: "Создать deploy/heap-dump-retention.service и .timer"

# Затем последовательно (общий файл compose):
Task: "Правка deploy/docker-compose-app.yml: флаги дампа + том"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 + Phase 2
2. Phase 3 (US1) — тест уровня вызывающих
3. **СТОП и валидация**: `./gradlew :karaoke-app:test` зелёный

### Incremental Delivery (рекомендуемый порядок реализации)

1. Phase 1 + 2 → фундамент
2. US1 → `:karaoke-app:test` зелёный
3. US2 → `check-deploy-hardening.sh` зелёный
4. US3 → retention self-test зелёный
5. US4 → `:karaoke-app:test` зелёный
6. **СТОП — доклад лиду** (код + тесты + конфигурация готовы; до правок базы знаний)
7. US5 → гейты базы знаний зелёные
8. Phase 8 → `report.md` + финальный коммит (без SKIP)

---

## Notes

- [P] = разные файлы, нет зависимостей
- [Story] = трассировка к историям спеки
- Существующие тесты `audit/PkgS212BehaviorTest.kt` и `audit/PkgS212SourceContractsTest.kt` — **не трогать** (FR-004)
- `deploy/.env`, `deploy/do.env` — **не коммитить, не печатать, не править** (О4)
- Контейнеры и systemd — **только по слову владельца** (О1, О5)
- Контракт спеки (`spec.md`) — **не редактируется**; расхождения фиксируются в `report.md`
