---
status: Active
slug: 295-jira-local-integration
related:
  - ../architecture/decisions/0008-tracker-openproject-migration.md
  - ../../specs/295-jira-local-integration/spec.md
  - ../../docs/tracker-setup.md
---

# 295 — Локальный issue-tracker для AI-агента (LiveDoc)

> Drill-down — [specs/295-jira-local-integration/spec.md](../../specs/295-jira-local-integration/spec.md).
> ADR об использовании OpenProject (вместо Jira DC) — [0008-tracker-openproject-migration.md](../architecture/decisions/0008-tracker-openproject-migration.md).
> Setup-руководство: [docs/tracker-setup.md](../../docs/tracker-setup.md).
> Архитектурная сводка: [docs/features/tracker-local-integration.md](../../docs/features/tracker-local-integration.md).

## Что делает

Поднимает локальный **OpenProject Community Edition** (open-source альтернатива
Jira Data Center) в Docker и даёт двустороннюю интеграцию **пользователь ↔
AI-агент** через REST API v3:

- Пользователь заводит work packages (задачи) через OpenProject UI
  (`http://localhost:8080`).
- AI-агент через bash-CLI (`tools/tracker.sh`) видит задачи, назначенные на
  `ai-agent`, забирает их (`claim-issue`), выполняет работу и публикует
  отчёт-комментарий с автоматическим закрытием (`close-issue`).

Ключевой принцип: **миграция с Jira DC на OpenProject CE** из-за блокировки
Atlassian для пользователей из РФ/РБ. OpenProject — drop-in замена с тем же
покрытием REST API v3 (CRUD work packages, comments, attachments).

## User Stories (краткий список)

- **US1 (P1)**: Пользователь открывает `http://localhost:8080`, создаёт Task с
  subject, markdown-описанием и assignee=`ai-agent` → work package получает
  числовой ID, виден через REST API.
- **US2 (P1)**: AI-агент вызывает `tools/tracker.sh list-issues --assignee
  ai-agent --status open` → берёт задачу `claim-issue 42` → статус
  переходит в "In progress".
- **US3 (P1)**: Агент вызывает `add-comment 42 --file report.md` с markdown
  (секции: Что сделано / Изменённые файлы / Прогон проверок / Известные
  ограничения), затем `close-issue 42`.
- **US4 (P2)**: Если в описании есть указание "создать спецификацию", агент
  запускает `/speckit.specify` и добавляет ссылку как комментарий.
- **US5 (P3)**: Пользователь отслеживает состояние через Kanban-доску в UI,
  фильтруя по `assignee = ai-agent`.

## Functional Requirements (указатель)

- **FR-001 / FR-002**: OpenProject Community 13.x + Postgres 13 в Docker
  (network_mode: host), изолирован от Karaoke (отдельный compose-файл).
- **FR-003**: Пользователь создаёт work packages типов Task / Bug / Feature
  через UI.
- **FR-004 / FR-005**: CLI `tools/tracker.sh` через env
  (`TRACKER_URL`, `TRACKER_USER`, `TRACKER_API_TOKEN`) поддерживает 8
  подкоманд: `list-projects`, `list-issues`, `get-issue`, `claim-issue`,
  `add-comment`, `close-issue`, `reopen-issue`, `create-issue`,
  `healthcheck`.
- **FR-006**: Авто-создание пользователя `ai-agent` через `install-tracker.sh`
  → `rails runner` (с admin-правами для поиска пользователей).
- **FR-009 / FR-011 / FR-012**: Секреты вне git (`deploy/do.env`/`.env.local-tracker`
  в `.gitignore`); compose в `deploy/tracker-docker-compose.yml`; named-volume
  `tracker-postgres-data` сохраняет данные при restart.
- **FR-015 / FR-016**: Ежедневный бэкап Postgres через
  `deploy/tracker-db-backup.sh` (cron / systemd-timer, retention 7 дней) +
  команда восстановления через `pg_restore`.
- **FR-017**: CLI логирует каждое обращение в `logs/tracker-agent.log`
  (JSON, 1 запись на вызов).
- **FR-018**: При `lockVersion` conflict CLI автоматически повторяет
  GET и retry PATCH (≤3 попытки).

## Acceptance Criteria

- [x] **AC1**: Установка одной командой `bash tools/install-tracker.sh [--smoke]`
  поднимает OpenProject + Postgres, создаёт `ai-agent` (admin) и API-токен.
- [x] **AC2**: `./tools/tracker-smoke-test.sh` проходит **8/8** шагов
  (healthcheck, list-projects, create-issue, get-issue, claim-issue,
  add-comment, close-issue, final-healthcheck).
- [x] **AC3**: В `.gitignore` есть `.env.local-tracker`; `git ls-files | grep
  tracker | grep -iE '\.env|\.key'` — пусто.
- [x] **AC4**: HTTP 429 → CLI retry с backoff 2s/4s/8s (до 3 попыток); 401/403
  → exit code 3 с инструкцией обновить токен.
- [x] **AC5**: После `docker compose stop && start` данные **сохраняются**
  (work packages, tokens, комментарии) — verified в volume `tracker-postgres-data`.
- [x] **AC6**: OpenProject CLI использует `username='apikey' +
  password=$TRACKER_API_TOKEN` (не логин пользователя; см. `UserBasicAuth`
  warden-стратегию). `TRACKER_USER` нужен только для фильтров/whoami.

## Связанные LiveDocs

- Architecture ADR: [0008-tracker-openproject-migration.md](../architecture/decisions/0008-tracker-openproject-migration.md) —
  переход с Jira DC на OpenProject CE (Pass 295).
- Domain: [monitoring.md](../domain/monitoring.md) (audit-логи CLI); cross-link
  через `tools/check-livedocs-cross-links.sh`.
- Architecture: [architecture-notes.md](../architecture-notes.md) — упомянуть
  новый контур (OpenProject, deploy/tracker-docker-compose.yml).

## Код

- CLI: `tools/tracker.sh` (8 подкоманд) | `tools/tracker-lib.sh` (HTTP wrapper
  с retry на 429, JSON-логирование, apikey-auth)
- Bootstrap: `tools/install-tracker.sh` (7 шагов: deps → env → port →
  compose → healthcheck → user+token → инструкции)
- Smoke-test: `tools/tracker-smoke-test.sh` (8 шагов end-to-end)
- Compose: `deploy/tracker-docker-compose.yml` (openproject:13 +
  postgres:13-alpine, network_mode: host)
- Backup: `deploy/tracker-db-backup.sh`, `deploy/tracker-db-backup.{service,timer}`
- Конфиг (вне git): `.env.local-tracker` (секреты); шаблон
  `.env.local-tracker.example`
- Логи: `logs/tracker-agent.log` (JSON, rotation)
- Setup: `docs/tracker-setup.md`
- Архитектурная сводка: `docs/features/tracker-local-integration.md`

## История

- Создан: 2026-09-02 (Pass 295)
- Последнее обновление: 2026-09-02 (rev. — auto-bootstrap + apikey-auth)
