# Contract: CLI `tools/jira.sh`

**Date**: 2026-09-02
**Spec**: [spec.md](../spec.md) FR-004, FR-005, FR-007, FR-017
**Data Model**: [data-model.md](../data-model.md)

## Overview

CLI-утилита `tools/jira.sh` предоставляет единый интерфейс к Jira REST API для
AI-агента и пользователя. Реализована на bash + curl + jq.

## Environment Variables

| Переменная | Обязательная | Описание | Пример |
|------------|--------------|----------|--------|
| `JIRA_URL` | Да | Базовый URL Jira (без trailing slash) | `http://localhost:8080` |
| `JIRA_USER` | Да | Username для Basic Auth | `ai-agent` |
| `JIRA_TOKEN` | Да | API token (из UI Profile → Personal Access Tokens) | `<32+ chars base64>` |
| `JIRA_AGENT_USER` | Нет | Username агента (default = `$JIRA_USER`) | `ai-agent` |
| `JIRA_LOG_LEVEL` | Нет | `info` (default) / `debug` / `error` | `info` |
| `JIRA_HTTP_TIMEOUT` | Нет | curl timeout в секундах (default 30) | `60` |

**Источник переменных**: `.env.local-jira` в корне репозитория (НЕ в `deploy/`,
НЕ в git — см. Constitution § VIII). CLI запускается через:

```bash
cd /home/nsa/Karaoke
source .env.local-jira
./tools/jira.sh list-projects
```

## Общие правила для всех подкоманд

### Exit codes

| Code | Значение |
|------|----------|
| `0` | Успех |
| `1` | Общая ошибка (неверные аргументы, нет env) |
| `2` | Jira недоступна (HTTP connect timeout / refused) |
| `3` | HTTP 401 Unauthorized (токен истёк) |
| `4` | HTTP 429 (rate-limit, исчерпаны retries) |
| `5` | HTTP 4xx (другие клиентские ошибки) |
| `6` | HTTP 5xx (ошибки сервера) |

### HTTP Status handling

| Status | Поведение |
|--------|-----------|
| `2xx` | Успех, тело ответа парсится и выводится |
| `401` | Exit code 3 + сообщение "JIRA_TOKEN истёк или отозван. Обновите токен в .env.local-jira" |
| `429` | Retry с backoff 2s/4s/8s (до 3 попыток). Если исчерпано — exit code 4 |
| `4xx` (other) | Exit code 5 + сообщение с телом ошибки Jira |
| `5xx` | Exit code 6 + сообщение "Jira вернула ошибку сервера, попробуйте позже" |
| Connect refused / timeout | Exit code 2 + сообщение "Jira недоступна по $JIRA_URL, проверьте `docker ps`" |

### Логирование

Каждый вызов CLI пишет ровно одну JSON-запись в `$LOG_FILE` (default:
`$BASE_DIR/logs/jira-agent.log`, FR-017). Поля:
`ts`, `cmd`, `req_id`, `endpoint`, `http_status`, `duration_ms`,
`error` (если есть), `retry_count` (если был 429).

Пример успешного вызова:
```json
{"ts":"2026-09-02T14:40:12Z","cmd":"list-projects","req_id":"a1b2c3d4","endpoint":"GET /rest/api/3/project/search","http_status":200,"duration_ms":234}
```

Пример с ошибкой:
```json
{"ts":"2026-09-02T14:41:05Z","cmd":"claim-issue","req_id":"e5f6g7h8","endpoint":"PUT /rest/api/3/issue/KARAOKE-42","http_status":401,"duration_ms":156,"error":"Authentication failed"}
```

## Подкоманды

### `list-projects`

**Назначение**: получить список всех проектов в Jira.

**Аргументы**: нет.

**HTTP запрос**: `GET /rest/api/3/project/search?maxResults=100`

**Выход** (stdout):
```
KEY         NAME                  TYPE       LEAD
KARAOKE     Karaoke Project       software   admin
TEST        Test Project          software   admin
```

**Exit codes**: 0 (есть проекты) / 0 (нет проектов — пустой stdout) / 2/3/5/6 (ошибки).

---

### `list-issues`

**Назначение**: получить список задач с фильтрацией по проекту / assignee / status.

**Аргументы**:
- `--project KEY` — фильтр по проекту (например, `KARAOKE`).
- `--assignee USER` — фильтр по assignee (`ai-agent`, `admin`, `currentUser()`).
- `--status STATUS` — фильтр по статусу (`To Do`, `In Progress`, `Done`, `Closed`).
- `--limit N` — макс. количество (default 50, max 1000).

**HTTP запрос**:
```
POST /rest/api/3/search
{
  "jql": "project = KARAOKE AND assignee = ai-agent AND status = \"To Do\"",
  "maxResults": 50,
  "fields": ["summary", "status", "assignee", "priority", "created"]
}
```

JQL строится из переданных флагов; пустые фильтры опускаются.

**Выход** (stdout, JSON-массив):
```json
[
  {
    "key": "KARAOKE-42",
    "summary": "Spec 295: установить локальную Jira",
    "status": "To Do",
    "priority": "High",
    "assignee": "ai-agent",
    "created": "2026-09-02T14:30:00Z"
  },
  ...
]
```

**Exit codes**: 0 / 2/3/5/6.

---

### `get-issue KEY`

**Назначение**: получить полную информацию о задаче.

**Аргументы**:
- `KEY` — обязательный, например `KARAOKE-42`.

**HTTP запрос**: `GET /rest/api/3/issue/KARAOKE-42?expand=renderedFields,names`

**Выход** (stdout, JSON):
```json
{
  "key": "KARAOKE-42",
  "summary": "Spec 295: установить локальную Jira",
  "description": "<ADF JSON or plain text>",
  "issueType": "Task",
  "status": "In Progress",
  "priority": "High",
  "assignee": "ai-agent",
  "reporter": "admin",
  "created": "2026-09-02T14:30:00Z",
  "updated": "2026-09-02T14:45:12Z",
  "labels": ["spec-295"],
  "comments": [...]
}
```

**Exit codes**: 0 / 2/3/5/6. Код 5 если KEY не найден (HTTP 404).

---

### `claim-issue KEY`

**Назначение**: назначить задачу на текущего пользователя и перевести в `In Progress`.

**Аргументы**:
- `KEY` — обязательный.

**HTTP запрос**:
1. `PUT /rest/api/3/issue/KARAOKE-42/assignee` с телом `{"accountId": "<ai-agent-account-id>"}`.
2. `GET /rest/api/3/issue/KARAOKE-42/transitions` — получить ID перехода `To Do → In Progress`.
3. `POST /rest/api/3/issue/KARAOKE-42/transitions` с телом `{"transition": {"id": "<id>"}}`.

**Поведение**:
- Если текущий assignee — уже `$JIRA_AGENT_USER`, шаг 1 пропускается (идемпотентность).
- Если текущий assignee — другой пользователь, шаг 1 перезаписывает (с warning в лог).
- Если задача уже в `In Progress`, шаги 2-3 пропускаются.

**Выход**: "OK: KARAOKE-42 claimed by ai-agent (status: In Progress)"

**Exit codes**: 0 / 2/3/5/6. Код 5 если задача в `Done` (нельзя перевести в `In Progress` без Reopen).

---

### `add-comment KEY --file FILE`

**Назначение**: добавить комментарий к задаче (обычно — отчёт агента).

**Аргументы**:
- `KEY` — обязательный.
- `--file FILE` — путь к markdown-файлу (обязательный).
- `--format md|adf` — формат входного файла (default `md` → CLI конвертирует в ADF).

**HTTP запрос**:
```
POST /rest/api/3/issue/KARAOKE-42/comment
{
  "body": <ADF JSON, сконвертированный из markdown>
}
```

**Markdown → ADF конвертация** (минимальный набор для FR-008):
- `# Heading` → ADF heading level 1.
- `## Heading` → ADF heading level 2.
- Обычный текст → ADF paragraph.
- `**bold**` → ADF text с mark `strong`.
- `` `code` `` → ADF text с mark `code`.
- ```\n``` blocks → ADF codeBlock.
- `- item` → ADF bulletList / listItem.
- Ссылки `[text](url)` → ADF text с mark `link`, attrs `{href: url}`.

**Выход**: "OK: comment added to KARAOKE-42 (id: 12345, length: 567 chars)"

**Exit codes**: 0 / 2/3/5/6. Код 5 если KEY не найден.

---

### `close-issue KEY`

**Назначение**: перевести задачу в `Done`.

**Аргументы**:
- `KEY` — обязательный.

**HTTP запрос**:
1. `GET /rest/api/3/issue/KARAOKE-42/transitions` — получить ID перехода `In Progress → Done`.
2. `POST /rest/api/3/issue/KARAOKE-42/transitions` с телом `{"transition": {"id": "<id>"}}`.

**Поведение**:
- Если задача уже в `Done` или `Closed` — no-op с warning "KARAOKE-42 already in Done".
- Если задача в `To Do` — переход невозможен; CLI возвращает ошибку
  "Cannot move from To Do to Done; use `claim-issue` first".

**Выход**: "OK: KARAOKE-42 closed (status: Done)"

**Exit codes**: 0 / 2/3/5/6.

---

### `reopen-issue KEY`

**Назначение**: перевести задачу обратно в `To Do` (или `In Progress`).

**Аргументы**:
- `KEY` — обязательный.

**HTTP запрос**: аналогично `close-issue`, но переход `Done → In Progress` (Reopen).

**Выход**: "OK: KARAOKE-42 reopened (status: In Progress)"

**Exit codes**: 0 / 2/3/5/6.

---

### `create-issue --project K --type T --summary S [--description D]`

**Назначение**: создать новую задачу.

**Аргументы**:
- `--project KEY` — обязательный (например, `KARAOKE`).
- `--type TYPE` — обязательный (`Task`, `Story`, `Bug`, `Sub-task`).
- `--summary S` — обязательный (1-255 chars).
- `--description D` — текст или `@FILE` (формат markdown→ADF).
- `--assignee USER` — опциональный (default = создатель).
- `--priority P` — опциональный (default `Medium`).
- `--label LABEL` — опциональный, можно повторять для нескольких меток.

**HTTP запрос**:
```
POST /rest/api/3/issue
{
  "fields": {
    "project": {"key": "KARAOKE"},
    "summary": "...",
    "description": <ADF JSON>,
    "issuetype": {"name": "Task"},
    "assignee": {"accountId": "<id>"},
    "priority": {"name": "High"},
    "labels": ["spec-295"]
  }
}
```

**Выход**: "OK: KARAOKE-43 created (id: 10001)"

**Exit codes**: 0 / 2/3/5/6.

---

### `healthcheck`

**Назначение**: проверить доступность Jira (без аутентификации, через `/status`).

**Аргументы**: нет.

**HTTP запрос**: `GET /status` (без Basic Auth).

**Выход** (stdout):
- "OK: Jira RUNNING at $JIRA_URL (response: 245ms)"
- "FAIL: Jira unavailable at $JIRA_URL: <error>"

**Exit codes**: 0 (Jira доступна) / 1 (недоступна).

**Использование**: вызывается из `deploy/jira-docker-compose.yml` как
`HEALTHCHECK` директива, а также из `tools/jira-smoke-test.sh`.

---

## Helper scripts

### `tools/jira-backup.sh`

**Назначение**: ежедневный бэкап Postgres Jira (FR-015).

**Аргументы**: нет (всё из env).

**Алгоритм**:
1. Проверить, что контейнер `jira-db` запущен.
2. Загрузить `JIRA_DB_PASSWORD` из `.env.local-jira`.
3. Сгенерировать timestamp `YYYY-MM-DD`.
4. Выполнить `docker exec jira-db pg_dump -U jira -d jira_db -Fc > /backups/jira-YYYY-MM-DD.dump`.
5. Проверить, что файл не пустой.
6. Удалить дампы старше 7 дней: `find /backups -name 'jira-*.dump' -mtime +7 -delete`.
7. Записать summary в stdout и в systemd journal.

**Exit codes**: 0 (бэкап успешен) / 1 (ошибка).

**Запуск**: через systemd-timer `jira-db-backup.timer` (03:00 daily).

---

### `tools/jira-restore.sh <YYYY-MM-DD>`

**Назначение**: восстановить Jira из дампа (FR-016).

**Аргументы**:
- `<YYYY-MM-DD>` — дата дампа (например, `2026-09-02`). Если не указана — берётся последний дамп.

**Алгоритм**:
1. Найти файл `/backups/jira-YYYY-MM-DD.dump` (или последний, если дата не указана).
2. Подтвердить с пользователем (`read -p "Это перезапишет текущую БД. Продолжить? (yes/no)"`).
3. Остановить контейнер `jira`: `docker stop jira`.
4. Выполнить `docker exec jira-db pg_restore -U jira -d jira_db --clean --if-exists < /backups/jira-YYYY-MM-DD.dump`.
5. Если ошибка — попытаться восстановить из последнего pre-restore дампа (best-effort).
6. Запустить контейнер `jira`: `docker start jira`.
7. Дождаться healthcheck `curl -fsS http://localhost:8080/status` (≤5 минут).

**Exit codes**: 0 / 1.

**⚠️ DESTRUCTIVE**: требует явного подтверждения пользователя (FR-016).

---

### `tools/jira-smoke-test.sh`

**Назначение**: end-to-end проверка работоспособности Jira (используется при
установке и обновлении версии).

**Алгоритм**:
1. `healthcheck` — Jira доступна.
2. `list-projects` — есть проект `KARAOKE`.
3. Создать задачу: `create-issue --project KARAOKE --type Task --summary "smoke-test-$TIMESTAMP"`.
4. Получить KEY созданной задачи.
5. `get-issue $KEY` — задача существует.
6. `claim-issue $KEY` — статус `In Progress`.
7. `add-comment $KEY --file <(echo "smoke test comment")` — комментарий добавлен.
8. `close-issue $KEY` — статус `Done`.
9. Финальный `healthcheck` — всё ещё работает.

**Exit codes**: 0 (все шаги OK) / 1 (любой шаг провалился, диагностика в stderr).

---

### `tools/install-jira.sh`

**Назначение**: первоначальная установка Jira (one-shot скрипт).

**Алгоритм**:
1. Проверить, что Docker установлен и доступен.
2. Проверить, что порт 8080 свободен (если занят — фолбэк на 8090 с предупреждением).
3. Создать `.env.local-jira` если не существует (с placeholder'ами для JIRA_TOKEN).
4. `docker compose -f deploy/jira-docker-compose.yml up -d`.
5. Подождать, пока Jira ответит на `/status` (≤10 минут).
6. Получить license key через UI или REST API (CLI выводит инструкцию).
7. Создать пользователя `ai-agent` через REST API (если ещё не существует).
8. Создать проект `KARAOKE` через REST API.
9. Запустить `tools/jira-smoke-test.sh`.
10. Вывести summary с URL, credentials, next steps.

**Exit codes**: 0 / 1.

---

## Edge Cases & Error Handling

### 1. Пустое описание (`create-issue --summary X` без `--description`)

**Поведение**: CLI принимает (description опционально). Jira создаёт задачу с
пустым description.

**Спецификация**: агент не должен брать задачи без описания (см. Edge Case в spec.md).
CLI при `list-issues --assignee ai-agent --status "To Do"` выводит предупреждение:

```
WARN: KARAOKE-43 has empty description; agent should skip this issue
```

### 2. Конфликт между агентами

**Сценарий**: два агента (или два процесса) одновременно вызывают `claim-issue KARAOKE-42`.

**Поведение**: первый вызов успешен (HTTP 204). Второй получает:
- Если assignee уже другой → HTTP 400 "Issue does not have correct assignee".
- CLI интерпретирует как "уже кем-то взят" → exit code 5 с сообщением
  "KARAOKE-42 already claimed by another agent".

### 3. Jira недоступна при `healthcheck` из Docker

**Сценарий**: контейнер `jira` ещё стартует (5-10 минут при первом запуске).

**Поведение**: healthcheck возвращает `curl: (7) Failed to connect` → exit code 1 →
Docker помечает контейнер как `unhealthy`. Retry через `interval=30s` пока не OK.

### 4. pg_dump возвращает partial data (disk full)

**Сценарий**: во время `jira-backup.sh` закончилось место на диске.

**Поведение**: `pg_dump` возвращает ненулевой код; `jira-backup.sh` записывает
ошибку в journal, удаляет partial файл, exit code 1. systemd-timer `Persistent=true`
гарантирует, что следующий запуск (через 24 часа или при перезагрузке) попытается
снова.

### 5. API token отозван

**Сценарий**: пользователь удалил токен через UI Jira.

**Поведение**: CLI получает HTTP 401 → exit code 3 + сообщение
"JIRA_TOKEN истёк или отозван. Обновите токен в .env.local-jira и перезапустите CLI."

CLI НЕ пытается retry при 401 (это не временная ошибка).

---

## Testing Strategy

**Уровень**: ручное end-to-end через `tools/jira-smoke-test.sh` + интерактивная
проверка пользователем через UI.

**Не создаются**:
- ❌ unit-тесты bash-функций (Constitution: «тесты `@Disabled`»).
- ❌ integration-тесты с mock Jira (overkill, есть real Jira).
- ❌ CI-пайплайн для Jira (Jira — локальный сервис, не часть CI).

**Проверяется пользователем**:
- После `install-jira.sh` — открыть `http://localhost:8080` в браузере, убедиться, что UI рендерится.
- Создать задачу через UI → убедиться, что она видна в `list-issues`.
- Закрыть задачу через UI → убедиться, что `get-issue` показывает `Done`.

---

## Versioning

CLI версионируется через **Git tag** в репозитории Karaoke (например, `v295.1.0` —
feature 295, итерация 1). Версия выводится через `--version`:

```bash
$ ./tools/jira.sh --version
tools/jira.sh v295.1.0 (commit abc1234)
```
