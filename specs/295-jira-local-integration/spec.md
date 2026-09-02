# Feature Specification: Локальный issue-tracker для AI-агента

**Feature Branch**: `295-jira-local-integration`
**Created**: 2026-09-02
**Status**: Draft (v2 — миграция с Jira DC на OpenProject Community Edition)
**Input**: User description: "Работа с Jira. Надо локально установить и настроить Jira так, чтобы я мог заводить в ней задания, а ты их видеть, брать, исполнять в рамках отдельных спецификаций, а по окончанию - писать в джире отчёт по проделанной работе и закрывать задачу."

> **История миграции**: первоначально планировалось использовать **Jira Software Data Center**, но из-за блокировки регистрации evaluation-license для пользователей из РФ/РБ со стороны Atlassian (см. Constitution § I, self-contained), было принято решение использовать **OpenProject Community Edition** — полностью бесплатную open-source альтернативу с REST API v3. Старая спецификация на Jira сохранена в `.jira-archived/spec-jira.md` для истории.

## Clarifications

### Session 2026-09-02

- Q: Какой issue-tracker использовать? → A: **OpenProject Community Edition** (MIT-лицензия, без лицензионных ограничений, REST API v3 совместим с Jira по операциям).
- Q: Backup-стратегия для Postgres? → A: **pg_dump в volume `tracker-backups` с ротацией 7 дней** через `tools/tracker-db-backup.sh`.
- Q: Какой уровень логирования нужен для MVP? → A: **`docker logs openproject` + структурированный JSON-лог CLI-агента `logs/tracker-agent.log`** с ротацией.
- Q: Как CLI должен реагировать на HTTP 429? → A: **Экспоненциальный backoff 2s → 4s → 8s, до 3 retry, затем ошибка**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Пользователь заводит задачу в OpenProject (Priority: P1)

Пользователь открывает локальный веб-интерфейс OpenProject (`http://localhost:7081`), создаёт новый work package (тип: Task), заполняет subject, description (с markdown-разметкой и ссылками на spec), assignee = `ai-agent`. Work package создаётся в проекте по умолчанию и получает числовой ID.

**Independent Test**: Открыть `http://localhost:7081/projects/karaoke` → + Create new work package → тип `Task` → заполнить subject → Save → получить числовой ID (например, `42`).

**Acceptance Scenarios**:

1. **Given** OpenProject запущен, **When** пользователь создаёт Task с subject "Spec X: реализовать Y", **Then** work package получает числовой ID, появляется в backlog, виден через REST API.
2. **Given** OpenProject запущен, **When** пользователь вводит описание с markdown-разметкой, **Then** описание рендерится корректно в UI и доступно через REST API в виде JSON с `description.raw`.
3. **Given** OpenProject запущен, **When** пользователь перезапускает docker-контейнер, **Then** все ранее созданные work packages сохраняются (внешний Postgres).

---

### User Story 2 - AI-агент видит задачи, назначенные на него, и берёт их в работу (Priority: P1)

AI-агент через CLI `tools/tracker.sh` обращается к OpenProject REST API v3 с Basic Auth, получает список work packages с assignee = `ai-agent` и статусом `New` / `In progress`.

**Acceptance Scenarios**:

1. **Given** у агента есть валидный API token, **When** агент запрашивает список с assignee = `ai-agent` и status = `open`, **Then** возвращается структурированный список.
2. **Given** агент получил задачу, **When** агент выполняет `claim-issue 42`, **Then** HTTP `PATCH /api/v3/work_packages/42` обновляет assignee и status на `In progress`.
3. **Given** агент взял задачу, **When** пользователь открывает её в UI, **Then** он видит статус `In progress` и assignee = `ai-agent`.

---

### User Story 3 - Агент исполняет задачу и пишет отчёт по окончанию (Priority: P1)

Агент выполняет задачу. По окончанию публикует комментарий-отчёт в markdown и переводит статус в `Closed`.

**Acceptance Scenarios**:

1. **Given** агент выполнил задачу, **When** агент публикует отчёт, **Then** в work package появляется комментарий.
2. **Given** отчёт опубликован, **When** пользователь открывает задачу, **Then** он видит комментарий с markdown-рендерингом.
3. **Given** отчёт опубликован, **When** агент вызывает `close-issue 42`, **Then** статус меняется на `Closed` через PATCH с актуальным `lockVersion`.

---

### User Story 4 - Агент создаёт спецификацию на основе задачи (Priority: P2)

Если в описании есть указание "создать спецификацию", агент запускает `/speckit.specify` и добавляет ссылку как комментарий.

---

### User Story 5 - Пользователь отслеживает состояние задач агента (Priority: P3)

Пользователь открывает Kanban-доску в OpenProject UI, фильтрует по assignee = `ai-agent`.

---

### Edge Cases

- **OpenProject-контейнер упал?** — CLI возвращает ошибку с просьбой проверить `docker ps`.
- **У work package нет description?** — Агент отказывается брать, оставляет комментарий "Описание пустое".
- **Две задачи ссылаются на одну спецификацию?** — Агент обрабатывает последовательно по ID.
- **API token истёк?** — CLI получает HTTP 401/403 → exit code 3.
- **Postgres повреждён?** — `docker logs openproject-db` показывает ошибку.
- **lockVersion conflict?** — CLI автоматически делает повторный GET и retry PATCH (≤3 попыток).
- **HTTP 429?** — CLI retry с backoff 2s/4s/8s (до 3 попыток).
- **Postgres-том повреждён?** — Восстановление через `pg_restore` из `tracker-backups`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: OpenProject Community 13.x в Docker (network_mode: host) с Postgres 13+ в named-volume.
- **FR-002**: Используем **OpenProject Community** (MIT-лицензия).
- **FR-003**: Пользователь создаёт work packages типов Task / Bug / Feature через UI.
- **FR-004**: CLI `tools/tracker.sh` работает с REST API v3 через env: `TRACKER_URL`, `TRACKER_USER`, `TRACKER_API_TOKEN`.
- **FR-005**: CLI поддерживает: `list-projects`, `list-issues [--project-id ID] [--assignee USER] [--status open|closed] [--limit N]`, `get-issue ID`, `claim-issue ID`, `add-comment ID --file FILE`, `close-issue ID`, `reopen-issue ID`, `create-issue --project-id ID --type-id ID --subject S [--description D]`.
- **FR-006**: Создание учётной записи `ai-agent` при first-run.
- **FR-007**: Агент опрашивает OpenProject для work packages с assignee = `ai-agent` и status = `open`.
- **FR-008**: Отчёт содержит секции: "Что сделано", "Изменённые файлы", "Прогон проверок", "Известные ограничения".
- **FR-009**: Секреты вне git (Constitution § VIII): `TRACKER_API_TOKEN`, `TRACKER_DB_PASSWORD`, `TRACKER_SECRET_KEY_BASE` — в `.env.local-tracker` (в `.gitignore`).
- **FR-010**: Документация в `docs/tracker-setup.md`.
- **FR-011**: docker-compose в `deploy/tracker-docker-compose.yml`, изолирован от Karaoke.
- **FR-012**: Устойчивость к restart (named-volume `tracker-postgres-data`).
- **FR-013**: Агент запускает проверки Karaoke (AGENTS.md) и упоминает в отчёте.
- **FR-014**: Webhook OpenProject → агент — SHOULD, не MVP.
- **FR-015**: Ежедневный бэкап Postgres через `deploy/tracker-db-backup.sh` (cron 03:00, rotation 7 дней).
- **FR-016**: Команда восстановления из дампа: stop → pg_restore → start.
- **FR-017**: CLI пишет JSON-лог в `logs/tracker-agent.log`.
- **FR-018**: При lockVersion conflict CLI делает повторный GET и retry PATCH (≤3 попыток).

### Key Entities

- **OpenProject Project**: `id`, `identifier`, `name`, `created_at`.
- **Work Package**: `id`, `subject`, `description.raw` (markdown), `type`, `status`, `priority`, `assignee`, `author`, `createdAt`, `updatedAt`, `lockVersion`.
- **Work Package Activity** (комментарий): `id`, `author`, `comment.raw` (markdown), `comment.format` ("markdown").
- **OpenProject User**: `username`, `email`, `admin` (bool), `status`.
- **OpenProject Status**: `id`, `name` ("New"/"In progress"/"Closed"), `isClosed`.
- **API Token**: персональный токен, в env.
- **CLI Audit Log Entry**: JSON в `logs/tracker-agent.log`.
- **DB Backup Artifact**: `pg_dump -Fc` в `tracker-backups/tracker-YYYY-MM-DD.dump`.

## Success Criteria *(mandatory)*

- **SC-001**: Установка ≤30 минут.
- **SC-002**: `list-issues` ≤5 секунд @ ≤1000 задач.
- **SC-003**: Полный цикл ≤времени задачи + 10%.
- **SC-004**: 100% закрытых задач содержат комментарий с "Что сделано".
- **SC-005**: ≥99% данных сохраняются при restart.
- **SC-006**: 0 секретов в git.
- **SC-007**: Документация self-contained.
- **SC-008**: Healthcheck ≤3 секунды.
- **SC-009**: Бэкап ≤1 раз в сутки.
- **SC-010**: ≥99% данных при restore.
- **SC-011**: 1 JSON-запись на вызов CLI.
- **SC-012**: Retry 429 с backoff 2-4-8s.

## Assumptions

- **Окружение**: Linux x86_64, Docker 20.10+, bash 4.4+.
- **Сеть**: интернет для скачивания образа ~2 GB.
- **Хранилище**: ≥10 GB.
- **Безопасность**: OpenProject слушает на 127.0.0.1.
- **AI-аккаунт**: `ai-agent`.
- **API-токен**: генерируется в UI (`My Account → Access Tokens`).
- **Cron / polling**: ручной или systemd-timer.
- **Репозиторий**: код в Karaoke-репо, `.env.local-tracker` — в `.gitignore`.
- **Связь с Karaoke**: OpenProject — отдельный сервис.
- **Backup**: systemd-timer или crontab.
- **Logrotate**: `~/.logrotate.d/tracker-agent`.
- **Rate-limit**: ~100 req/min; polling ≤1 req/30s.

## Out of Scope (MVP)

- **Webhooks** — отложены, polling достаточен.
- **Agile-доски** — настраиваются вручную через UI.
- **Custom fields** — стандартного набора достаточно.
- **Multi-project** — MVP работает с одним проектом.
- **Attachments** — пользователь прикладывает через UI.
- **Workflow customization** — стандартный Kanban.
