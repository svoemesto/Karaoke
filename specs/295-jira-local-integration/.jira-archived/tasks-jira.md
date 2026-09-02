# Tasks: Локальная Jira для AI-агента

**Input**: Design documents from `/specs/295-jira-local-integration/`
- [plan.md](./plan.md) (required) — технический стек, структура, Constitution Check
- [spec.md](./spec.md) (required) — 5 user stories (P1, P1, P1, P2, P3)
- [research.md](./research.md) — 12 technology decisions
- [data-model.md](./data-model.md) — 8 entities
- [contracts/jira-cli.md](./contracts/jira-cli.md) — CLI contract (env, 8 subcommands, HTTP handling)
- [quickstart.md](./quickstart.md) — 3-этапный end-to-end сценарий

**Tests**: НЕ включены (соответствует Karaoke — тесты `@Disabled`, ручная проверка пользователем).

**Organization**: Tasks сгруппированы по user story для независимой реализации и тестирования.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Может выполняться параллельно (разные файлы, нет зависимостей)
- **[Story]**: К какой user story относится задача (US1, US2, ...)
- Включать точные пути файлов в описаниях

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, базовая структура каталогов и секреты вне git.

- [X] T001 Создать каталог `logs/` с `.gitkeep` файлом (для `jira-agent.log`)
- [X] T002 Создать шаблон `.env.local-jira.example` в корне репо с placeholder переменными (JIRA_URL, JIRA_USER, JIRA_TOKEN, JIRA_DB_PASSWORD, JIRA_AGENT_USER)
- [X] T003 Добавить `.env.local-jira` в `.gitignore` явно (дополнительно к существующему паттерну `*.env.*`)
- [X] T004 [P] Создать пустой `deploy/jira-docker-compose.yml` с базовой структурой (2 сервиса: jira-db, jira; 2 named volumes: jira-postgres-data, jira-backups)
- [X] T005 [P] Создать `docs/jira-setup.md` — пошаговая документация (FR-010) с TODO-секциями для последующего заполнения
- [X] T006 [P] Создать `docs/features/jira-local-integration.md` — per-feature документ (Constitution § VI FR-009) с TODO-секциями

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Инфраструктура (Jira + Postgres в Docker), backup, CLI-ядро — MUST complete до любой user story.

**⚠️ CRITICAL**: Никакая user story не может начаться до завершения этой фазы.

- [X] T007 Реализовать `deploy/jira-docker-compose.yml` — сервис `jira-db` (postgres:13-alpine, healthcheck `pg_isready`, volume `jira-postgres-data` + `jira-backups`), сервис `jira` (atlassian/jira-software:9.12.x, env-переменные из `.env.local-jira`, depends_on jira-db healthy, healthcheck `curl /status` start_period=600s interval=30s, port 8080:8080)
- [X] T008 Реализовать `deploy/jira-db-backup.sh` — bash-скрипт по образцу `karaoke-db-backup.sh`: `set -euo pipefail`, загрузка `JIRA_DB_PASSWORD` через `set -a; source .env.local-jira; set +a`, `docker exec jira-db pg_dump -U jira -d jira_db -Fc > /backups/jira-$(date +%F).dump`, retention 7 дней через `find /backups -name 'jira-*.dump' -mtime +7 -delete`
- [X] T009 Реализовать `deploy/jira-db-backup.service` — systemd-user unit с `[Service] Type=oneshot ExecStart=/home/nsa/Karaoke/deploy/jira-db-backup.sh`
- [X] T010 Реализовать `deploy/jira-db-backup.timer` — systemd-user timer с `[Timer] OnCalendar=*-*-* 03:00:00 Persistent=true RandomizedDelaySec=300`, `[Install] WantedBy=timers.target`
- [X] T011 [P] Реализовать `tools/jira-lib.sh` — общие функции: `jira_load_env()` (source .env.local-jira с валидацией обязательных переменных), `jira_http_request(METHOD, ENDPOINT, BODY)` (curl с Basic Auth, retry на 429 с backoff 2s/4s/8s до 3 попыток, exit code mapping по [contracts/jira-cli.md](../specs/295-jira-local-integration/contracts/jira-cli.md) → HTTP Status handling), `jira_log(STATUS, DURATION_MS, ERROR)` (JSON в `$LOG_FILE`), `jira_md_to_adf(MD_FILE)` (минимальный markdown→ADF конвертер для [data-model.md](../specs/295-jira-local-integration/data-model.md) Entity 3), `jira_require_jq()`, `jira_require_curl()`
- [X] T012 [P] Реализовать `tools/jira.sh` — главный CLI: загрузка `jira-lib.sh`, `set -euo pipefail`, парсинг подкоманды (`list-projects`, `list-issues`, `get-issue`, `claim-issue`, `add-comment`, `close-issue`, `reopen-issue`, `create-issue`, `healthcheck`), `--version` флаг, help-функция, маршрутизация на функции из `jira-lib.sh` (пока stub'ы — реализация в Phase 3)
- [X] T013 Реализовать `tools/install-jira.sh` — first-run setup: проверка Docker, создание `.env.local-jira` из `.env.local-jira.example` если нет, автодетект занятого порта 8080 (фолбэк 8090), `docker compose -f deploy/jira-docker-compose.yml up -d`, ожидание healthcheck `/status` (≤10 минут), вывод инструкций для UI-setup и создания API-токена
- [X] T014 [P] Реализовать `tools/jira-smoke-test.sh` — end-to-end проверка по [quickstart.md](../specs/295-jira-local-integration/quickstart.md) Этап 2: 9 шагов (healthcheck → list-projects → create → get → claim → add-comment → close → final healthcheck → cleanup), каждый с проверкой exit code и timeout'ом

**Checkpoint**: Foundation готов — `docker compose -f deploy/jira-docker-compose.yml up -d` стартует Jira, бэкап работает, CLI `--version` отвечает. После прохождения Этапа 1 из `quickstart.md` (установка + first-run UI setup) можно переходить к user stories.

---

## Phase 3: User Story 1 - Пользователь заводит задачу в Jira (Priority: P1) 🎯 MVP

**Goal**: Пользователь через Jira UI создаёт задачу в проекте `KARAOKE`. Задача видна в backlog, имеет ключ `KARAOKE-N`, сохраняется между перезапусками.

**Independent Test**: Открыть `http://localhost:8080/projects/KARAOKE` → Click "Create" → заполнить summary "Test task" + assignee `ai-agent` → Click "Create" → убедиться, что задача появилась с ключом `KARAOKE-1`. Затем `docker restart jira jira-db` → убедиться, что задача на месте (named-volume работает, SC-005).

### Implementation for User Story 1

- [X] T015 [US1] Реализовать `tools/jira-lib.sh::jira_list_projects()` — функция для `jira.sh list-projects`: HTTP `GET /rest/api/3/project/search?maxResults=100`, парсинг через `jq`, вывод в формате "KEY NAME TYPE LEAD" (4 колонки, tab-separated), exit code 0 *(реализовано в T011, Phase 2)*
- [X] T016 [US1] Реализовать `tools/jira-lib.sh::jira_healthcheck()` — функция для `jira.sh healthcheck`: HTTP `GET /status` (без auth), exit code 0 если HTTP 200 + body содержит `"state":"RUNNING"`, exit code 1 иначе *(реализовано в T011, Phase 2)*
- [X] T017 [US1] Реализовать `tools/jira-lib.sh::jira_create_issue()` — функция для `jira.sh create-issue`: аргументы `--project KEY --type TYPE --summary S [--description D|--description @FILE] [--assignee USER] [--priority P] [--label LABEL]`, HTTP `POST /rest/api/3/issue` с ADF-описанием *(реализовано в T011, Phase 2)*
- [X] T018 [US1] Реализовать `tools/jira-lib.sh::jira_get_issue()` — функция для `jira.sh get-issue KEY`: HTTP `GET /rest/api/3/issue/KEY?expand=renderedFields,names`, парсинг через `jq`, вывод JSON-объекта со всеми полями *(реализовано в T011, Phase 2)*
- [ ] T019 [US1] ~~Добавить в `deploy/jira-docker-compose.yml` сервис `jira-proxy` (nginx:stable для проксирования на 80 порт)~~ — **DEFERRED**: пользователь явно не запросил; `jira-proxy` можно добавить позже без влияния на P1 workflow
- [X] T020 [US1] Дополнить `docs/jira-setup.md` Этапом 1 (полная версия) — детальная инструкция: проверка требований, создание `.env.local-jira`, запуск `install-jira.sh`, прохождение first-run setup в UI, создание API token для admin, ручная проверка через `curl /rest/api/3/myself` *(реализовано в T005, Phase 1)*

**Checkpoint**: US1 полностью функциональна. Пользователь может заводить задачи через UI, видеть их в backlog. Задачи сохраняются между перезапусками контейнеров. После прохождения Этапа 2.1 из `quickstart.md` (создание тестовой задачи) — checkpoint reached.

---

## Phase 4: User Story 2 - AI-агент видит задачи и берёт их в работу (Priority: P1)

**Goal**: CLI `tools/jira.sh` позволяет агенту получить список задач, назначенных на `ai-agent`, и взять конкретную задачу (claim → status `In Progress`, assignee подтверждён).

**Independent Test**: Создать 3 задачи в UI (2 без assignee, 1 с assignee=`ai-agent`) → `./tools/jira.sh list-issues --assignee ai-agent --status "To Do"` → получить список с одной задачей → `./tools/jira.sh claim-issue KARAOKE-1` → "OK: KARAOKE-1 claimed by ai-agent (status: In Progress)". В UI убедиться, что статус изменился и assignee = `ai-agent`.

### Implementation for User Story 2

- [X] T021 [US2] Реализовать `tools/jira-lib.sh::jira_list_issues()` — функция для `jira.sh list-issues`: аргументы `--project K --assignee USER --status STATUS --limit N`, построение JQL *(реализовано в T011, Phase 2)*
- [X] T022 [US2] Реализовать `tools/jira-lib.sh::jira_claim_issue()` — функция для `jira.sh claim-issue KEY`: idempotent — assignee + transition *(реализовано в T011, Phase 2)*
- [X] T023 [US2] Дополнить `tools/jira.sh` — добавить роутинг подкоманд `list-issues`, `claim-issue` на соответствующие функции из `jira-lib.sh` *(реализовано в T012, Phase 2)*
- [X] T024 [US2] Дополнить `docs/jira-setup.md` — секция "Получение API-токена для ai-agent" (Этап 1.6) *(реализовано в T005, Phase 1)*

**Checkpoint**: US2 функциональна. Агент может видеть назначенные задачи через CLI и брать их в работу. Задачи в Jira UI корректно переходят в `In Progress`.

---

## Phase 5: User Story 3 - Агент исполняет задачу и пишет отчёт (Priority: P1)

**Goal**: Агент может добавить markdown-комментарий к задаче (конвертируется в ADF) и закрыть задачу. Также — reopen для повторного взятия.

**Independent Test**: `./tools/jira.sh add-comment KARAOKE-1 --file report.md` где `report.md` содержит секции "## Что сделано", "## Изменённые файлы", "## Прогон проверок", "## Известные ограничения" → в UI проверить, что комментарий отображается с корректным форматированием (заголовки, code blocks, списки). Затем `./tools/jira.sh close-issue KARAOKE-1` → в UI проверить status = `Done`.

### Implementation for User Story 3

- [X] T025 [US3] Реализовать `tools/jira-lib.sh::jira_add_comment()` — функция для `jira.sh add-comment KEY --file FILE [--format md|adf]` *(реализовано в T011, Phase 2)*
- [X] T026 [US3] Реализовать `tools/jira-lib.sh::jira_close_issue()` — функция для `jira.sh close-issue KEY` *(реализовано в T011, Phase 2)*
- [X] T027 [US3] Реализовать `tools/jira-lib.sh::jira_reopen_issue()` — функция для `jira.sh reopen-issue KEY` *(реализовано в T011, Phase 2)*
- [ ] T028 [US3] ~~Дополнить `tools/jira-lib.sh::jira_md_to_adf()` (расширить существующую из T011) — добавить поддержку полного набора markdown для FR-008~~ — **DEFERRED to US3-polish**: минимальный md→ADF (один параграф) уже работает для P1; расширенный парсер (H1/H2/H3, code blocks, bullets, links) — задача отдельной итерации, не блокирует MVP
- [X] T029 [US3] Дополнить `tools/jira.sh` — добавить роутинг подкоманд `add-comment`, `close-issue`, `reopen-issue` *(реализовано в T012, Phase 2)*

**Checkpoint**: US3 функциональна. Полный цикл "create → claim → work → report → close" работает end-to-end. Это — основной рабочий цикл AI-агента.

---

## Phase 6: User Story 4 - Агент создаёт спецификацию на основе задачи (Priority: P2)

**Goal**: Если в описании задачи есть фраза "создать спецификацию" (или эквивалент), агент запускает `/speckit.specify` workflow и добавляет ссылку на созданную спеку в задачу как комментарий.

**Independent Test**: Создать задачу с description "Сделай спецификацию для фичи автоматического тегирования" + assignee=`ai-agent` → запустить агента → он создаёт `specs/<NNN>-auto-tagging/spec.md` → добавляет комментарий в задачу со ссылкой. Проверить в UI наличие комментария и наличие новой директории в `specs/`.

### Implementation for User Story 4

- [ ] T030 [US4] Реализовать `tools/jira-detect-spec-request.sh` — helper-скрипт: получает описание задачи (через `jira.sh get-issue KEY`), ищет regex `создать спецификацию|создать спеку|create spec|/speckit.specify` (case-insensitive), exit code 0 если найдено, exit code 1 если нет. Используется как pre-check перед `claim-issue`.
- [ ] T031 [US4] Создать шаблон `tools/jira-spec-link.md` — markdown-шаблон для комментария после создания спеки: "✅ Спецификация создана: `specs/<NNN>-<slug>/spec.md`\n\nСсылка будет добавлена после прохождения `/speckit.plan`." С placeholder `$SPEC_PATH` для подстановки через `sed` или envsubst.
- [ ] T032 [US4] Реализовать `tools/jira-process-spec-task.sh` — orchestrator-скрипт: получает KEY задачи, проверяет наличие spec-request через `jira-detect-spec-request.sh`, если есть — запускает `tools/specify-bootstrap.sh <slug>` (где slug = первые 2-4 слова из summary), затем вызывает `/speckit.specify` с описанием из задачи (через создание feature branch и spec.md), после успеха — публикует комментарий через `jira.sh add-comment KEY --file jira-spec-link.md` (с подставленным `$SPEC_PATH`)
- [ ] T033 [US4] Дополнить `docs/jira-setup.md` — секция "Специальный workflow: создание спецификации из задачи" с инструкцией как пользователю формулировать задачу для авто-создания спеки

**Checkpoint**: US4 функциональна. Агент может из задачи Jira создать новую спецификацию в Karaoke-репо и сослаться на неё в комментарии. Полезно, но не критично — пользователь может сразу прикладывать ссылку на спеку в описание задачи.

---

## Phase 7: User Story 5 - Пользователь отслеживает состояние задач агента (Priority: P3)

**Goal**: В Jira UI есть Kanban-доска для проекта `KARAOKE`, пользователь видит задачи агента в колонках `To Do` / `In Progress` / `Done`, может переоткрыть задачу drag-and-drop'ом.

**Independent Test**: В UI: `Projects → KARAOKE → Board` (или создать Scrum/Kanban board если нет) → убедиться, что видны 3 задачи из Этапа 2 quickstart.md в правильных колонках. Перетащить `KARAOKE-2` из `Done` обратно в `In Progress` → CLI следующего `list-issues` покажет её снова.

### Implementation for User Story 5

- [ ] T034 [US5] Реализовать `tools/jira-create-board.sh` — helper для создания Kanban-доски через REST API (если ещё не создана при first-run setup): HTTP `POST /rest/api/3/agile/board` с `{"name": "KARAOKE Board", "type": "kanban", "projectIdOrKey": "KARAOKE"}`. Если доска уже есть — exit code 0 с warning.
- [ ] T035 [US5] Расширить `tools/install-jira.sh` — добавить вызов `jira-create-board.sh` после создания проекта (Этап 1.7 quickstart.md), чтобы доска создавалась автоматически при first-run
- [ ] T036 [US5] Дополнить `docs/jira-setup.md` — секция "Использование Kanban-доски" с скриншотами-описанием (или ссылкой на Atlassian docs): как открыть доску, как фильтровать по assignee, как переоткрывать задачи drag-and-drop'ом
- [ ] T037 [US5] Реализовать `tools/jira-list-board.sh` — helper для CLI-альтернативы UI: HTTP `GET /rest/api/3/agile/board/{boardId}/issue?jql=assignee=ai-agent`, вывод в формате Kanban-колонок (`To Do:` / `In Progress:` / `Done:` с подсписком задач в каждой)

**Checkpoint**: US5 функциональна. Пользователь имеет UI-доску для отслеживания + CLI-альтернативу. Это — последняя user story.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Улучшения, влияющие на несколько user stories + финальные проверки.

- [ ] T038 [P] Реализовать `tools/jira-restore.sh` — DR drill по FR-016: аргумент `<YYYY-MM-DD>` (default = последний дамп), подтверждение через `read -p "Это перезапишет текущую БД. Продолжить? (yes/no)"`, `docker stop jira`, `docker exec jira-db pg_restore -U jira -d jira_db --clean --if-exists < /backups/jira-YYYY-MM-DD.dump`, `docker start jira`, ожидание healthcheck ≤5 минут
- [ ] T039 [P] Создать `/etc/logrotate.d/jira-agent` (или `~/.logrotate.d/jira-agent`) — конфиг по FR-017: `/home/nsa/Karaoke/logs/jira-agent.log { daily rotate 7 compress missingok notifempty create 0644 nsa nsa }`. Альтернатива — добавить `logrotate` шаг в `tools/jira-backup.sh` (если logrotate нельзя ставить без root).
- [ ] T040 [P] Дополнить `docs/jira-setup.md` — секция "Troubleshooting" с типичными проблемами: Jira не стартует (проверить `docker logs jira`), порт 8080 занят (фолбэк 8090), истёкший токен (обновить `JIRA_TOKEN`), медленный polling (уменьшить частоту), 429 rate-limit (подождать или уменьшить `--limit`)
- [ ] T041 [P] Создать `.env.local-jira.example` финальную версию с полным списком переменных из FR-004 + assumptions + комментариями (# JIRA_URL=... — обязательно, без trailing slash)
- [ ] T042 Добавить pre-commit hook в `.pre-commit-config.yaml` — проверка, что `.env.local-jira` НЕ в git (по образцу существующих секрет-чеков из Constitution § VIII): `git ls-files | grep -E '\.env\.local-jira$|jira.*\.env$|jira.*\.key$|jira.*\.pem$'` должно быть пусто
- [ ] T043 Финальная верификация по `quickstart.md` Этап 2 — запуск `tools/jira-smoke-test.sh`, проверка всех 9 шагов, проверка acceptance checklist (SC-001..SC-012)
- [ ] T044 [P] Дополнить `docs/architecture-notes.md` — запись о PR с summary реализации (Pass 295): что добавлено (jira-docker-compose.yml, tools/jira*.sh, docs/jira-setup.md), какие assumptions подтверждены (Jira DC 9.12.x, bash CLI, systemd-timer для backup)
- [ ] T045 [P] Обновить `AGENTS.md` (если нужно) — добавить упоминание Jira в секцию про workflow (например, в "Где правила для разных AI-агентов" — упомянуть `tools/jira.sh` как стандартный способ забрать задачу)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — может стартовать сразу
- **Phase 2 (Foundational)**: зависит от Setup — **БЛОКИРУЕТ** все user stories
- **Phase 3 (US1, P1)**: зависит от Foundational — может стартовать после T011..T014
- **Phase 4 (US2, P1)**: зависит от Foundational + US1 (нужны задачи для claim) — НО формально независима (можно claim'ить любую существующую задачу)
- **Phase 5 (US3, P1)**: зависит от Foundational + US1 + US2 (полный цикл) — формально независима (можно создать задачу → claim → close без US1 если есть `create-issue` из US1)
- **Phase 6 (US4, P2)**: зависит от Foundational + US1 + US2 + US3 (нужен полный CLI) — может стартовать после US3
- **Phase 7 (US5, P3)**: зависит от Foundational + UI-настройки (Kanban-доска создаётся в UI или через REST API)
- **Phase 8 (Polish)**: зависит от всех desired user stories

### User Story Dependencies

- **US1 (P1)**: Может стартовать после Foundational — нет зависимостей от других stories
- **US2 (P1)**: Может стартовать после Foundational + US1 (нужны задачи для claim), но формально независима (есть существующие задачи в Jira)
- **US3 (P1)**: Может стартовать после Foundational + US1 + US2 (полный цикл)
- **US4 (P2)**: Может стартовать после US3 (нужен полный CLI для spec-detection + add-comment)
- **US5 (P3)**: Может стартовать параллельно с US1 (UI-настройка Kanban-доски)

### Within Each User Story

- Setup → Foundational → Implementation → Verification (smoke-test)
- T011 (jira-lib.sh) — основа для всех CLI функций, должен быть реализован ДО любой US-специфичной функции
- T012 (jira.sh skeleton с роутингом) — должен быть ДО US-специфичных роутингов
- US-функции в `jira-lib.sh` (T015..T018 для US1, T021..T022 для US2, T025..T027 для US3) — могут реализовываться параллельно разными разработчиками
- Documentation обновления (T020, T024, T033, T036) — параллельно с кодом, в конце каждой фазы

### Parallel Opportunities

Все `[P]`-задачи могут выполняться параллельно:
- **Setup**: T004, T005, T006 — параллельно (разные файлы)
- **Foundational**: T008+T009+T010 — последовательно (service зависит от .sh, timer от service); T011, T013, T014 — параллельно с T012 (разные файлы, общая зависимость — T011 для T012)
- **US1**: T015, T016, T017, T018 — параллельно (разные функции в `jira-lib.sh`); T019, T020 — параллельно с кодом
- **US2**: T021, T022 — параллельно; T023, T024 — параллельно после T021+T022
- **US3**: T025, T026, T027 — параллельно; T028, T029 — параллельно
- **US4**: T030, T031 — параллельно; T032, T033 — после T030+T031
- **US5**: T034, T037 — параллельно; T035, T036 — после T034
- **Polish**: T038, T039, T040, T041, T042 — параллельно (разные файлы); T043 — после всех; T044, T045 — параллельно

---

## Parallel Example: Phase 2 (Foundational)

```bash
# T008-T010 — backup subsystem (последовательно: .sh → service → timer)
Task: "Реализовать deploy/jira-db-backup.sh"
Task: "Реализовать deploy/jira-db-backup.service"
Task: "Реализовать deploy/jira-db-backup.timer"

# Параллельно с backup:
Task: "Реализовать tools/jira-lib.sh (общие функции)"
Task: "Реализовать tools/install-jira.sh (first-run setup)"
Task: "Реализовать tools/jira-smoke-test.sh (end-to-end)"

# T012 — после T011 (jira.sh использует функции из jira-lib.sh)
Task: "Реализовать tools/jira.sh (главный CLI с роутингом)"
```

## Parallel Example: User Story 1

```bash
# Параллельно: 4 функции в jira-lib.sh
Task: "Реализовать jira_list_projects() в tools/jira-lib.sh"
Task: "Реализовать jira_healthcheck() в tools/jira-lib.sh"
Task: "Реализовать jira_create_issue() в tools/jira-lib.sh"
Task: "Реализовать jira_get_issue() в tools/jira-lib.sh"

# Параллельно с кодом: документация и опциональный proxy
Task: "Дополнить docs/jira-setup.md Этапом 1"
Task: "Добавить nginx-proxy в deploy/jira-docker-compose.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 + 2 + 3 = P1 set)

1. Complete Phase 1: Setup (T001..T006) — 30 минут
2. Complete Phase 2: Foundational (T007..T014) — 3-4 часа (включая `docker compose up` + ожидание старта Jira)
3. **First-run setup в UI** по `quickstart.md` Этап 1.4..1.7 — 10 минут (пользователь вручную)
4. Complete Phase 3: US1 (T015..T020) — 2-3 часа (после установки)
5. **VALIDATE**: Создать тестовую задачу в UI → убедиться, что она сохраняется после restart контейнера (SC-005)
6. Complete Phase 4: US2 (T021..T024) — 2-3 часа
7. **VALIDATE**: `list-issues --assignee ai-agent` → `claim-issue` → проверить UI
8. Complete Phase 5: US3 (T025..T029) — 2-3 часа (markdown→ADF самый сложный)
9. **VALIDATE**: Полный цикл claim → work → add-comment → close → проверить в UI
10. **MVP готов**: `tools/jira-smoke-test.sh` проходит все 9 шагов ✅
11. **STOP and VALIDATE**: пользователь проверяет end-to-end по `quickstart.md`

### Incremental Delivery (после MVP)

12. Complete Phase 6: US4 (T030..T033) — 1-2 часа
13. **VALIDATE**: Создать задачу с "создать спецификацию" → проверить создание `specs/<NNN>-*/spec.md` + комментарий в Jira
14. Complete Phase 7: US5 (T034..T037) — 1-2 часа (UI-настройка Kanban + CLI-alternative)
15. **VALIDATE**: Открыть board в UI → перетащить задачу → убедиться, что CLI видит изменение
16. Complete Phase 8: Polish (T038..T045) — 2-3 часа
17. **FINAL**: `tools/jira-smoke-test.sh` + acceptance checklist из `quickstart.md` — все PASS

### Parallel Team Strategy

С одним разработчиком (текущая ситуация — пользователь + AI-агент):
- Phase 1 + Phase 2 — последовательно (AI пишет код, пользователь делает first-run UI setup)
- Phase 3..7 — последовательно (US-фазы короткие, 2-3 часа каждая)
- US4 и US5 могут быть пропущены в MVP (P2 + P3 не критичны)

С несколькими разработчиками:
- Dev A: US1 + US2 (core workflow)
- Dev B: US3 (markdown→ADF — самый сложный технический кусок)
- Dev C: US4 + US5 (UI-фичи, менее критичные)

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей — параллелятся.
- **[Story] label** = привязка задачи к user story для traceability.
- Каждая user story должна быть **independently completable и testable** через `tools/jira-smoke-test.sh` или ручную проверку в UI.
- **Verify before implementing**: для CLI-функций — проверить через `curl -u ...` напрямую к Jira, что ожидаемый endpoint работает; потом уже реализовывать в bash.
- **Commit after each task** или логической группы (например, после T007 — коммит "infra: deploy/jira-docker-compose.yml"; после T011 — "tools: jira-lib.sh core functions").
- **Stop at any checkpoint** для валидации story independently — это уменьшает risk переделки.
- **Avoid**:
  - ❌ Vague tasks без file paths (например, "Реализовать CLI" — отклонить, должно быть "Реализовать `tools/jira.sh::jira_list_projects()` в `tools/jira-lib.sh`")
  - ❌ Same file conflicts (например, две задачи "изменить `jira-lib.sh`" — должны быть разные функции или одна — после другой)
  - ❌ Cross-story dependencies, ломающие independence (US2 формально не зависит от US1, но практически нужны задачи для claim — это OK, потому что US2 тестируется на существующих задачах в Jira)
- **Ссылка на FR-номера**: при реализации функций в `jira-lib.sh` ссылаться на конкретные FR из `spec.md` (например, T021 реализует US2 — соответствует неявному FR-007 "Агент MUST уметь опрашивать Jira и получать список задач").
- **AGENTS.md Pass 282 исключения**: на `nsa-i9` / `nsa` разрешено пересобирать `karaoke-app` без согласия, но Jira — это новый сервис (не `karaoke-app`), так что правило не применяется. Запуск/перезапуск `jira` и `jira-db` контейнеров — **можно без согласия** (они не в списке запрещённых).
