# Feature: Локальный issue-tracker для AI-агента (spec 295, OpenProject)

**Spec**: [`specs/295-jira-local-integration/spec.md`](../../specs/295-jira-local-integration/spec.md)
**Status**: Implemented (Pass 295, v295.2.0 — миграция с Jira на OpenProject)
**Created**: 2026-09-02
**Last updated**: 2026-09-02

## Назначение

Локальный **OpenProject Community Edition** в Docker-контейнере для
двусторонней интеграции пользователь ↔ AI-агент через work packages (задачи).
Пользователь заводит work packages в OpenProject UI, AI-агент через bash
CLI (`tools/tracker.sh`) забирает их, выполняет и публикует отчёт-комментарий
с автоматическим закрытием.

## Почему OpenProject, а не Jira Data Center

Изначально планировалось использовать **Jira Software Data Center** (см.
`.jira-archived/`), но:
- Atlassian блокирует регистрацию evaluation-license для пользователей из РФ/РБ.
- Jira DC без license работает в read-only mode через 30 дней.

**OpenProject Community Edition** — drop-in замена с теми же возможностями:
- MIT-лицензия, полностью бесплатный.
- REST API v3 (CRUD для work packages, comments, attachments).
- Agile-доски (Scrum + Kanban), workflow, custom fields.
- Self-hosted через Docker.

## Архитектура

```
┌─────────────────────────┐      ┌─────────────────────────┐
│  Пользователь           │      │  AI-агент               │
│  (OpenProject UI в      │      │  (tools/tracker.sh)     │
│   браузере)             │      │                         │
└───────────┬─────────────┘      └────────────┬────────────┘
            │                                  │
            │ HTTP (UI)                        │ REST API v3
            │                                  │ (Basic Auth: user + api_token)
            ▼                                  ▼
   ┌────────────────────────────────────────────────────┐
   │  OpenProject Community 13.x                         │
   │  (Docker: openproject/community:13)                 │
   │  ─────────────────────────────────────────────────  │
   │  • Projects / Work Packages / Comments / Workflows  │
   │  • REST API v3 (Basic Auth + API token)            │
   │  • Markdown для описаний и комментариев             │
   └────────────────────────┬───────────────────────────┘
                            │ JDBC
                            ▼
   ┌────────────────────────────────────────────────────┐
   │  PostgreSQL 13                                      │
   │  (Docker: postgres:13-alpine, named volume)        │
   │  ─────────────────────────────────────────────────  │
   │  • Схема openproject                                │
   │  • Ежедневный backup через tracker-db-backup.sh     │
   │  • Retention 7 дней через systemd-timer              │
   └────────────────────────────────────────────────────┘
```

## Компоненты

### Инфраструктура (`deploy/`)

| Файл | Назначение |
|------|-----------|
| `deploy/tracker-docker-compose.yml` | Compose для OpenProject + Postgres (изолирован от Karaoke) |
| `deploy/tracker-db-backup.sh` | Bash-скрипт для ежедневного бэкапа через `pg_dump -Fc` |
| `deploy/tracker-db-backup.service` | systemd-user unit для backup |
| `deploy/tracker-db-backup.timer` | systemd-user timer (03:00 daily) |

### CLI (`tools/`)

| Файл | Подкоманды |
|------|-----------|
| `tools/tracker.sh` | Главный CLI (8 подкоманд): `list-projects`, `list-issues`, `get-issue`, `claim-issue`, `add-comment`, `close-issue`, `reopen-issue`, `create-issue`, `healthcheck` |
| `tools/tracker-lib.sh` | Общие функции: HTTP wrapper с retry на 429, JSON-логирование, Basic Auth |
| `tools/install-tracker.sh` | First-run setup: проверка Docker, создание `.env.local-tracker`, `docker compose up`, ожидание healthcheck |
| `tools/tracker-smoke-test.sh` | End-to-end проверка (8 шагов) |

### Конфигурация (вне git)

| Файл | Назначение |
|------|-----------|
| `.env.local-tracker` | Секреты: `TRACKER_URL`, `TRACKER_USER`, `TRACKER_API_TOKEN`, `TRACKER_DB_PASSWORD`, `TRACKER_SECRET_KEY_BASE` |
| `.env.local-tracker.example` | Шаблон (коммитится, реальный файл — в `.gitignore`) |

### Документация (`docs/`)

| Файл | Назначение |
|------|-----------|
| `docs/tracker-setup.md` | Пошаговое руководство по установке |
| `docs/features/tracker-local-integration.md` | Этот файл |

## Workflow

### Создание work package (пользователь)

1. Пользователь открывает OpenProject UI: `http://localhost:7081`.
2. Projects → Karaoke → + Create new work package → Type `Task`.
3. Заполняет subject, description (markdown с ссылкой на `specs/<NNN>-*/spec.md`), assignee = `ai-agent`.
4. Save → получает числовой ID (например, `42`).

### Забор work package (агент)

1. Агент вызывает `tools/tracker.sh list-issues --assignee ai-agent --status open`.
2. Получает список задач со статусом `New` или `In progress`.
3. Вызывает `tools/tracker.sh claim-issue 42`:
   - HTTP `PATCH /api/v3/work_packages/42` с `lockVersion` + assignee + status.
   - OpenProject оптимистично проверяет `lockVersion` (важно: каждое изменение инкрементирует).
   - Статус становится `In progress`, assignee = `ai-agent`.

### Исполнение + отчёт (агент)

1. Агент читает описание work package и прикреплённую спеку.
2. Выполняет работу (обычно — реализует спеку, правит код).
3. Формирует отчёт в формате markdown (секции: Что сделано / Изменённые файлы / Прогон проверок / Известные ограничения).
4. Вызывает `tools/tracker.sh add-comment 42 --file report.md`:
   - OpenProject принимает markdown нативно (format: "markdown").
   - HTTP `POST /api/v3/work_packages/42/activities` с `{"comment": {"raw": "...", "format": "markdown"}}`.

### Закрытие (агент)

1. Вызывает `tools/tracker.sh close-issue 42`:
   - HTTP `PATCH /api/v3/work_packages/42` с `lockVersion` + status = `Closed`.
2. Work package в статусе `Closed`, пользователь видит отчёт в UI.

## Отличия от исходной спеки (Jira DC)

| Аспект | Jira DC (план) | OpenProject (факт) |
|--------|---------------|-------------------|
| Лицензия | Требуется (блокировка для РФ/РБ) | Не требуется (MIT) |
| Контейнер | `atlassian/jira-software:9.12.13` (1.4 GB) | `openproject/community:13` (~2 GB) |
| Сроки старта | 5-10 минут | 3-5 минут |
| REST API | v3 (ADF-формат) | v3 (JSON нативно) |
| Аутентификация | Basic Auth (email:api_token) | Basic Auth (username:api_token) |
| Identifier | `KARAOKE-N` (текстовый) | `42` (числовой ID) |
| Workflow | Через transitions API | Через PATCH с `_links.status.href` |
| Комментарии | `POST /issue/{key}/comment` + ADF | `POST /work_packages/{id}/activities` + markdown |
| Web UI | `localhost:8080` (через host network) | `localhost:7081` |

## Связь с другими фичами

| Связь | Описание |
|-------|----------|
| `specs/` workflow | Work package может содержать ссылку на `specs/<NNN>-*/spec.md`; агент реализует спеку |
| `tools/specify-bootstrap.sh` | Используется для авто-создания спеки из work package (будущее расширение) |
| `deploy/karaoke-db-backup.{sh,service,timer}` | Шаблон для `tracker-db-backup.{sh,service,timer}` |
| Constitution § VIII | Все секреты в `.env.local-tracker` (вне git), pre-commit проверка |

## Известные ограничения

- **Markdown в комментариях** — OpenProject поддерживает markdown нативно, но **рендеринг отличается от Jira** (нет ADF). Адаптация markdown-отчётов не требуется.
- **lockVersion** — каждый PATCH требует актуальной версии; при конкурентных изменениях CLI автоматически делает повторный GET. Это медленнее, чем Jira (которая использует transitions API).
- **Agile-доски** — OpenProject имеет Scrum и Kanban доски, но они не используются в MVP. CLI работает через API.
- **Webhooks** (SHOULD) — отложено, polling достаточен для MVP.

## Метрики (Success Criteria)

| ID | Критерий | Как проверить |
|----|----------|--------------|
| SC-001 | Установка ≤30 мин | `time bash tools/install-tracker.sh` |
| SC-002 | `list-issues` ≤5 сек @ 1000 задач | `time ./tools/tracker.sh list-issues --limit 1000` |
| SC-004 | 100% закрытых задач с отчётом | `grep "## Что сделано" logs/tracker-agent.log` |
| SC-005 | ≥99% данных сохраняются при restart | `docker restart openproject openproject-db` + проверка |
| SC-006 | 0 секретов в git | `git ls-files | grep tracker | grep -iE '\.env\|\.key'` — пусто |
| SC-008 | Healthcheck ≤3 сек | `time ./tools/tracker.sh healthcheck` |
| SC-009 | Бэкап ≤10 мин @ 1 GB | `time /home/nsa/Karaoke/deploy/tracker-db-backup.sh` |
| SC-011 | 1 JSON-запись на вызов CLI | `cat logs/tracker-agent.log | jq -c .` |
| SC-012 | Retry на 429 с backoff 2-4-8s | Ручной тест с mock 429 |

## Связанные документы

- [Spec 295 — основная спецификация](../../specs/295-jira-local-integration/spec.md)
- [.jira-archived/ — старая спека на Jira DC](../../specs/295-jira-local-integration/.jira-archived/) (для истории и сравнения)
- [tracker-setup.md — пошаговое руководство для пользователя](../tracker-setup.md)
- [OpenProject REST API v3 docs](https://www.openproject.org/docs/api/introduction/)
