# Data Model: Локальная Jira для AI-агента (spec 295)

**Branch**: `295-jira-local-integration`
**Date**: 2026-09-02
**Spec**: [spec.md](./spec.md)
**Research**: [research.md](./research.md)

## Overview

Data model описывает сущности, которые обрабатывает CLI (`tools/jira.sh`) и хранит
Jira. Все данные хранятся в Postgres Jira; CLI — stateless (только env-переменные
для аутентификации + локальный кэш ответов на время выполнения).

---

## Entity 1: Jira Project

**Описание**: контейнер для задач. Создаётся один раз при первом запуске Jira
через UI или REST API.

| Поле | Тип | Описание | Обязательное | Validation |
|------|-----|----------|--------------|------------|
| `key` | string (2-10 chars) | Уникальный ключ проекта, uppercase, начинается с буквы | Да | `^[A-Z][A-Z0-9]{1,9}$` |
| `name` | string (≤80 chars) | Человекочитаемое имя | Да | ≤80 chars |
| `lead` | string (username) | Лид проекта (обычно admin) | Да | Должен существовать в Jira |
| `projectTypeKey` | enum | Тип: `software`, `service_desk`, `business` | Да | Default: `software` |
| `assigneeType` | enum | `PROJECT_LEAD` / `UNASSIGNED` | Нет | Default: `UNASSIGNED` |
| `avatarId` | int64 | ID аватара | Нет | — |

**Relationships**:
- 1 Project → N Issues (один-ко-многим).
- 1 Project → N Workflows (через `workflowScheme`).

**State transitions**: нет (Project создаётся один раз, удаление через admin UI).

**Lifecycle**:
1. **Provisioning**: при первом запуске Jira + создание через REST API
   `POST /rest/api/3/project` (FR-006).
2. **Active**: основное состояние.
3. **Archived**: вне scope MVP.

**Пример**:
```json
{
  "key": "KARAOKE",
  "name": "Karaoke Project",
  "lead": "admin",
  "projectTypeKey": "software"
}
```

---

## Entity 2: Jira Issue

**Описание**: задача в проекте. Основная единица работы для AI-агента.

| Поле | Тип | Описание | Обязательное | Validation |
|------|-----|----------|--------------|------------|
| `key` | string | Уникальный ключ в формате `<PROJECT_KEY>-<N>`, например `KARAOKE-42` | Да (auto) | `^[A-Z][A-Z0-9]+-\d+$` |
| `summary` | string (≤255 chars) | Краткое название | Да | 1-255 chars |
| `description` | ADF / string | Полное описание (поддерживает markdown→ADF) | Нет | ADF валиден |
| `issueType` | enum | `Task`, `Story`, `Bug`, `Sub-task`, `Epic` | Да | Default: `Task` |
| `status` | enum | `To Do` / `In Progress` / `Done` / `Closed` (через workflow) | Да (auto) | См. State Transitions |
| `priority` | enum | `Highest`, `High`, `Medium`, `Low`, `Lowest` | Нет | Default: `Medium` |
| `assignee` | User | Текущий исполнитель | Нет | Default: `null` (= unassigned) |
| `reporter` | User | Кто создал (auto = текущий пользователь) | Да (auto) | — |
| `created` | timestamp (ISO 8601) | Дата создания (UTC) | Да (auto) | — |
| `updated` | timestamp (ISO 8601) | Дата последнего обновления | Да (auto) | ≥ created |
| `labels` | array of strings | Метки для фильтрации | Нет | каждая ≤255 chars |
| `comments[]` | array of Comment | История комментариев | Нет (auto) | — |

**Relationships**:
- N Issues → 1 Project.
- 1 Issue → N Comments (один-ко-многим).
- 1 Issue → 0..1 User (assignee).
- 1 Issue → 0..N Users через labels/watching (вне scope MVP).

**State Transitions (workflow)**:
```
To Do  ──[Start Progress / Claim]──>  In Progress  ──[Done / Close]──>  Done
  ↑                                       │                              │
  │                                       │                              │
  └──[Reopen]──────────────────────────────┴──────────────────────────────┘
```

Все переходы — через `POST /rest/api/3/issue/{key}/transitions` с `{"transition":{"id":"<id>"}}`.
ID переходов получаются через `GET /rest/api/3/issue/{key}/transitions` (зависит от
созданного workflow scheme).

**Lifecycle для задач агента (основной use-case)**:
1. **Created** (`To Do`): пользователь создал задачу через UI, assignee = `ai-agent`.
2. **Claimed** (`In Progress`): агент вызвал `claim-issue <KEY>` → assignee подтверждён,
   status = `In Progress`.
3. **Reported** (`In Progress`): агент добавил комментарий-отчёт (FR-008).
4. **Closed** (`Done`): агент вызвал `close-issue <KEY>` → status = `Done`.
5. **Reopened** (`To Do`): пользователь перетащил обратно (US-5 acceptance scenario).

**Пример (CLI вывод)**:
```json
{
  "key": "KARAOKE-42",
  "summary": "Spec 295: установить локальную Jira",
  "issueType": "Task",
  "status": "In Progress",
  "priority": "High",
  "assignee": {"username": "ai-agent"},
  "reporter": {"username": "admin"},
  "created": "2026-09-02T14:30:00Z",
  "updated": "2026-09-02T14:45:12Z",
  "labels": ["spec-295", "infrastructure"]
}
```

---

## Entity 3: Jira Comment

**Описание**: комментарий к задаче. Создаётся при добавлении через REST API или UI.

| Поле | Тип | Описание | Обязательное | Validation |
|------|-----|----------|--------------|------------|
| `id` | string (numeric) | Уникальный ID комментария в Jira | Да (auto) | — |
| `author` | User | Автор комментария | Да (auto) | — |
| `body` | ADF | Текст комментария (ADF JSON) | Да | Валидный ADF, ≤65535 chars |
| `created` | timestamp (ISO 8601) | Дата создания (UTC) | Да (auto) | — |
| `issueKey` | string | К какой задаче (FK) | Да (auto) | Issue должен существовать |

**ADF структура для отчёта агента (FR-008)**:
```json
{
  "type": "doc",
  "version": 1,
  "content": [
    {
      "type": "heading",
      "attrs": {"level": 2},
      "content": [{"type": "text", "text": "Что сделано"}]
    },
    {
      "type": "paragraph",
      "content": [{"type": "text", "text": "Развёрнута Jira 9.12.x в Docker на nsa-i9..."}]
    },
    {
      "type": "heading",
      "attrs": {"level": 2},
      "content": [{"type": "text", "text": "Изменённые файлы"}]
    },
    {
      "type": "bulletList",
      "content": [
        {
          "type": "listItem",
          "content": [{
            "type": "paragraph",
            "content": [{
              "type": "text",
              "text": "deploy/jira-docker-compose.yml (создан)"
            }]
          }]
        }
      ]
    },
    {
      "type": "heading",
      "attrs": {"level": 2},
      "content": [{"type": "text", "text": "Прогон проверок"}]
    },
    {
      "type": "codeBlock",
      "attrs": {"language": "bash"},
      "content": [{"type": "text", "text": "$ ./tools/jira.sh list-projects\nKARAOKE  Karaoke Project"}]
    },
    {
      "type": "heading",
      "attrs": {"level": 2},
      "content": [{"type": "text", "text": "Известные ограничения"}]
    },
    {
      "type": "paragraph",
      "content": [{"type": "text", "text": "Jira evaluation license истекает через 30 дней; для production нужна покупка DC-лицензии."}]
    }
  ]
}
```

**Markdown→ADF трансляция** (для CLI):
- `# Heading` → `{"type": "heading", "attrs": {"level": 1}, "content": [{"type": "text", "text": "..."}]}`
- `## Heading` → level 2
- `**bold**` → `{"type": "text", "text": "...", "marks": [{"type": "strong"}]}`
- `* item` → `{"type": "bulletList", "content": [{"type": "listItem", ...}]}`
- ```code``` → `{"type": "codeBlock", "attrs": {"language": "bash"}}`

CLI использует awk/sed для конвертации или jq-шаблоны (MVP — минимальный набор).

---

## Entity 4: Jira User

**Описание**: пользователь Jira. Два ключевых аккаунта для нашей интеграции.

| Поле | Тип | Описание | Обязательное | Validation |
|------|-----|----------|--------------|------------|
| `username` | string | Уникальное имя (login) | Да | `^[a-z0-9_-]{3,30}$` |
| `email` | string (email) | Email (для уведомлений) | Да | RFC 5322 |
| `displayName` | string (≤255 chars) | Отображаемое имя | Да | — |
| `active` | boolean | Активен ли аккаунт | Да (auto) | Default: true |
| `groups[]` | array of strings | Группы: `jira-administrators`, `jira-developers`, `jira-users` | Нет | — |
| `applicationRoles[]` | array | Роли приложений: `jira-software` | Нет | — |

**Relationships**:
- 1 User → N Issues (через `reporter`, `assignee`).
- 1 User → N Comments (через `author`).

**Ключевые пользователи**:
- `admin` — локальный администратор (создаётся при first-run setup Jira).
  Группа: `jira-administrators`.
- `ai-agent` — технический аккаунт для AI-агента (FR-006).
  Группа: `jira-developers`. Email: `ai-agent@localhost`.

**Lifecycle**:
1. **Provisioning**: `ai-agent` создаётся при первом запуске через
   `POST /rest/api/3/user` с правами admin.
2. **Active**: основное состояние.
3. **API token rotation**: пользователь генерирует новый токен через UI
   `Profile → Personal Access Tokens`. CLI использует новый токен автоматически
   (читает из env).

---

## Entity 5: Jira Workflow

**Описание**: схема переходов статусов задач. Используется стандартный Kanban Software workflow.

**Statuses** (имена из Jira Software):
- `To Do` (категория: `new`)
- `In Progress` (категория: `indeterminate`)
- `Done` (категория: `done`)
- `Closed` (категория: `done`)

**Transitions** (настраиваются через `POST /rest/api/3/workflow` или через UI):
- `To Do → In Progress` (transition id: `11` — стандартный)
- `In Progress → Done` (transition id: `21`)
- `Done → Closed` (transition id: `31`)
- `Done → In Progress` (Reopen, transition id: `41`)
- `In Progress → To Do` (Back, transition id: `51`)

**Примечание**: реальные ID переходов зависят от workflow scheme; CLI получает их
через `GET /rest/api/3/issue/{key}/transitions` перед выполнением `transition`.

---

## Entity 6: API Token

**Описание**: персональный токен для аутентификации в REST API. НЕ хранится в Jira
(только в env-переменной на хосте).

| Поле | Тип | Описание |
|------|-----|----------|
| `value` | string (≥32 chars) | Сам токен (base64-encoded random) |
| `createdBy` | User | Кто создал |
| `createdAt` | timestamp | Дата создания |
| `lastUsed` | timestamp | Последнее использование |

**Место хранения**:
- **НЕ в Jira** — Jira не хранит сами токены (только bcrypt-hash для verify).
- **В env-переменной** `$JIRA_TOKEN` — загружается из `.env.local-jira` через
  `source .env.local-jira` в CLI.
- **В git**: НИКОГДА (FR-009, Constitution § VIII).

**Lifecycle**:
1. **Создание**: пользователь через UI `Profile → Personal Access Tokens →
   Create token`, копирует значение.
2. **Сохранение**: добавляет в `.env.local-jira` как `JIRA_TOKEN=<value>`.
3. **Использование**: CLI читает `$JIRA_TOKEN` при каждом вызове.
4. **Ротация**: пользователь периодически создаёт новый токен, удаляет старый
   через UI.

---

## Entity 7: CLI Audit Log Entry

**Описание**: структурированная запись в `logs/jira-agent.log` для каждого
вызова CLI (FR-017).

| Поле | Тип | Описание | Обязательное |
|------|-----|----------|--------------|
| `ts` | ISO 8601 timestamp (UTC) | Время вызова | Да |
| `cmd` | string | Подкоманда CLI (`list-issues`, `claim-issue`, ...) | Да |
| `req_id` | string (uuid v4) | Уникальный ID запроса (для трассировки) | Да |
| `endpoint` | string | HTTP endpoint (`GET /rest/api/3/search`) | Да |
| `http_status` | int | HTTP-код ответа (200, 204, 401, 429, ...) | Да |
| `duration_ms` | int | Длительность запроса в миллисекундах | Да |
| `error` | string | Текст ошибки (если есть) | Нет |
| `retry_count` | int | Количество retry при 429 (SC-012) | Нет |

**Пример**:
```json
{"ts":"2026-09-02T14:40:12Z","cmd":"claim-issue","req_id":"a1b2c3d4","endpoint":"PUT /rest/api/3/issue/KARAOKE-42","http_status":204,"duration_ms":423,"retry_count":0}
```

**Lifecycle**: одна строка в `logs/jira-agent.log` на каждый вызов CLI. Ротация
через logrotate (daily, 7 дней, compress).

---

## Entity 8: DB Backup Artifact

**Описание**: файл-дамб Postgres, создаваемый ежедневно `tools/jira-backup.sh` (FR-015).

| Поле | Тип | Описание |
|------|-----|----------|
| `filename` | string | `jira-YYYY-MM-DD.dump` (custom format `-Fc`) |
| `size_bytes` | int | Размер (типично 50-500 MB для пустого/среднего проекта) |
| `createdAt` | timestamp | Время создания |
| `compressed` | boolean | true (pg_dump -Fc сжимает внутри) |
| `contains` | enum | `full` (вся БД) |

**Место хранения**: named-volume `jira-backups` (монтируется в `/backups` в
контейнере Postgres и в `$BACKUP_DIR` на хосте через `-v`).

**Lifecycle**:
1. **Создание**: ежедневно в 03:00 локального времени через systemd-timer.
2. **Retention**: последние 7 файлов (более старые удаляются в том же скрипте).
3. **Restore**: через `tools/jira-restore.sh <YYYY-MM-DD>` (FR-016).

---

## Validation Rules Summary

| Rule | Описание | Где проверяется |
|------|----------|-----------------|
| `JIRA_KEY` | Должен матчить `^[A-Z][A-Z0-9]+-\d+$` | В CLI перед каждым REST-вызовом |
| `SUMMARY_LENGTH` | 1-255 chars | В CLI при `create-issue` |
| `DESCRIPTION_ADF` | Валидный ADF JSON | Jira API (вернёт 400 если невалидно) |
| `TRANSITION_VALIDITY` | Transition должен быть доступен из текущего status | Jira API (вернёт 400) |
| `BACKUP_AGE` | ≤24 часа между бэкапами | systemd-timer + Persistent=true |
| `LOG_FORMAT` | Каждая строка — валидный JSON | `jq` без ошибок (SC-011) |
| `SECRETS_NOT_IN_GIT` | `.env.local-jira` НЕ в `git ls-files` | Pre-commit check (FR-009, SC-006) |

## State Diagram (Agent Workflow)

```
   ┌──────────────────────────────────────────────────────────────┐
   │                                                              │
   ▼                                                              │
[User creates issue, assignee=ai-agent, status=To Do]             │
   │                                                              │
   │  CLI: jira.sh list-issues --assignee ai-agent --status "To Do"
   ▼                                                              │
[Agent fetches list, picks first issue]                           │
   │                                                              │
   │  CLI: jira.sh claim-issue KARAOKE-42
   │  → assignee confirmed, status = In Progress
   ▼                                                              │
[Agent reads spec.md, performs work]                              │
   │                                                              │
   │  (опционально) CLI: jira.sh add-comment KARAOKE-42 --file progress.md
   ▼                                                              │
[Agent writes report]                                             │
   │                                                              │
   │  CLI: jira.sh add-comment KARAOKE-42 --file report.md (FR-008)
   │  → comment with sections: Что сделано / Изменённые файлы / ...
   ▼                                                              │
[Agent closes task]                                               │
   │                                                              │
   │  CLI: jira.sh close-issue KARAOKE-42
   │  → status = Done
   ▼                                                              │
[User reviews in Jira UI]                                         │
   │                                                              │
   │  (если недоволен) User: Reopen в UI → status = To Do
   │                                                              │
   └──────────────────────────────────────────────────────────────┘
```

## Out of Scope

- **Issue links** (`blocks`, `relates to`) — для MVP не нужны.
- **Attachments / files** — пользователь может прикладывать через UI; CLI не работает с attachments.
- **Watchers** — пользователь добавляет себя через UI.
- **Time tracking** (worklog) — не используется в workflow.
- **Custom fields** — стандартный набор полей достаточен.
- **Agile boards (filter results)** — задачи ищутся через JQL, не через board.
- **Permissions schemes** — для одного проекта и одного пользователя достаточно default scheme.
