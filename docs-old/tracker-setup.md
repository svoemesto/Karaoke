# Tracker Setup — пошаговое руководство (OpenProject)

**Версия**: 1.1 (Pass 295, rev. — auto-bootstrap + apikey)
**Связанные документы**: [`specs/295-jira-local-integration/spec.md`](../specs/295-jira-local-integration/spec.md), [`docs/features/tracker-local-integration.md`](features/tracker-local-integration.md)

## Что это

Локальный **OpenProject** Community Edition для AI-агента. OpenProject — это
open-source альтернатива Jira (полностью бесплатная, без лицензионных
ограничений, MIT-лицензия).

Пользователь заводит work packages (задачи) в OpenProject UI, AI-агент
через CLI (`tools/tracker.sh`) забирает их, выполняет и публикует
отчёт-комментарий с автоматическим закрытием.

## Почему OpenProject, а не Jira

Из спецификации 295 (`.jira-archived/`) сначала планировалось использовать
Jira Data Center, но:
- Atlassian блокирует регистрацию evaluation-license для пользователей из РФ/РБ.
- Jira DC без license работает в read-only mode через 30 дней.

OpenProject — drop-in замена:
- Полностью бесплатный (MIT-лицензия).
- REST API v3 с теми же операциями (CRUD для work packages).
- Поддерживает agile-доски, workflow, custom fields, attachments.
- Self-hosted через Docker.

## Требования

- Linux x86_64 (Ubuntu 22.04+, Debian 11+, RHEL 9+).
- Docker 20.10+ с Docker Compose v2.
- Bash 4.4+, `curl`, `jq` 1.6+, `openssl`.
- ≥8 GB свободной RAM, ≥5 GB диска (образ OpenProject ~2 GB).

## Этап 1: Установка (одной командой)

### 1.1 Запустить install-tracker.sh

```bash
cd /home/nsa/Karaoke
bash tools/install-tracker.sh            # bootstrap + запустить (без smoke)
# либо
bash tools/install-tracker.sh --smoke    # bootstrap + end-to-end smoke-test
```

Скрипт автоматически:

1. Создаёт `.env.local-tracker` (если нет) с авто-сгенерированными секретами
   `TRACKER_DB_PASSWORD` (32 base64 символа) и `TRACKER_SECRET_KEY_BASE`
   (openssl rand -hex 64).
2. Находит свободный порт для OpenProject UI (8080 → 7082 → 7083 → 7084 → 8082)
   и обновляет `TRACKER_URL` / `TRACKER_HOST` в `.env.local-tracker`.
3. Запускает `docker compose -f deploy/tracker-docker-compose.yml up -d`,
   передавая `.env.local-tracker` через `--env-file`.
4. Ждёт healthcheck OpenProject (`/health_check`) до 10 минут.
5. Через `rails runner` создаёт (или обновляет) пользователя `ai-agent` с
   правами admin и генерирует для него новый API-токен; **записывает plain
   токен в `.env.local-tracker`**.
6. (опционально, по флагу `--smoke`) прогоняет `tracker-smoke-test.sh`.

> **Ключевой момент**: CLI использует Basic Auth с username=`apikey`
> и password=`TRACKER_API_TOKEN` (см. OpenProject warden-стратегию
> `UserBasicAuth`). `TRACKER_USER` используется только как login для
> фильтров и логов whoami.

### 1.2 Проверка

```bash
cd /home/nsa/Karaoke
source .env.local-tracker            # подгрузить TRACKER_URL и токен
./tools/tracker.sh healthcheck       # OK: OpenProject UP at http://localhost:8080
./tools/tracker.sh list-projects     # таблица проектов (id, identifier, name)
./tools/tracker.sh list-issues --assignee ai-agent --status open
```

### 1.3 (опционально) Создать проект karaoke

Если ещё нет — через UI:
1. Projects → **+ Create project** → Name: `Karaoke`, Identifier: `karaoke`.
2. В Members добавить `ai-agent` с ролью **Member** (и **Administrator**, если
   хотите делегировать админство).

Либо через rails-runner (по аналогии с install-tracker.sh):
```bash
docker exec openproject bash -c "cd /app && bundle exec rails runner '
Project.where(identifier: \"karaoke\").first_or_initialize.tap do |p|
  p.name = \"Karaoke\"
  p.identifier = \"karaoke\"
  p.templated = false
  p.public = true
  p.enabled_module_names = Project.default_enabled_modules
  p.save!
end
'"
```

### 1.4 (опционально) Настроить backup systemd-timer

```bash
mkdir -p ~/.config/systemd/user
cp /home/nsa/Karaoke/deploy/tracker-db-backup.{service,timer} ~/.config/systemd/user/

systemctl --user daemon-reload
systemctl --user enable --now tracker-db-backup.timer

systemctl --user list-timers | grep tracker
# Должен показать next run завтра в 03:00
```

Cron-вариант для систем без systemd-user:
```bash
# crontab -e
0 3 * * * /home/nsa/Karaoke/deploy/tracker-db-backup.sh >> ~/.local/share/tracker-backup.log 2>&1
```

### 1.5 Kanban-доска «AI Pipeline» + workflow status «In review»

`install-tracker.sh` автоматически (если проект `karaoke` уже существует) вызывает
`tools/tracker-bootstrap-board.sh`, который:

1. **Переименовывает** стандартный статус «In testing» (id=9) → «In review» —
   пауза между окончанием работы агента и проверкой пользователем.
2. **Создаёт Kanban-доску** «AI Pipeline» в проекте Karaoke с 6 колонками:

| Колонка | Фильтр OpenProject |
|---------|---------------------|
| New | `status_id=1, assigned_to_id=<ai-agent>` |
| In specification | `status_id=2, assigned_to_id=<ai-agent>` |
| Specified | `status_id=3, assigned_to_id=<ai-agent>` |
| In progress | `status_id=7, assigned_to_id=<ai-agent>` |
| **In review** | `status_id=9, assigned_to_id=<ai-agent>` |
| Closed | `status_id=12, assigned_to_id=<ai-agent>` |

3. **Добавляет workflow-transitions** для статуса «In review» (для всех типов × ролей):
   - `New → In review`
   - `In progress → In review`
   - `Specified → In review`
   - `In review → In progress` (вернуть на доработку)
   - `In review → Closed` (финал после ревью)
   - `Closed → In review` (опциональный возврат)

Если нужно пересоздать доску позже:

```bash
bash tools/tracker-bootstrap-board.sh
```

Открыть доску: <http://localhost:8080/projects/karaoke/boards/10>

## Этап 2: Использование

### Создать задачу в UI

1. Открыть <http://localhost:8080/projects/karaoke>.
2. **+ Create new work package** → Type `Task`.
3. Subject: `<краткое название>`.
4. Description: `<детали>`. Если задача сложная и требует спецификации —
   пользователь не пишет спеку сразу, а агент создаёт её автоматически
   (см. `tracker-spec-for-issue.sh` ниже).
5. Assignee: `ai-agent`.
6. Save → получить числовой ID (например, `42`).

### Polling при старте сессии opencode

В начале каждой сессии агент **обязан** выполнить `tools/tracker-poll.sh`,
чтобы увидеть:

```bash
cd /home/nsa/Karaoke
source .env.local-tracker

bash tools/tracker-poll.sh
```

Пример вывода:

```
═══ Tracker poll ═══
Время:     2026-09-02 15:35:50 +0300
Tracker:   http://localhost:8080
Assignee:  ai-agent

📋 Открытые задачи (2)
42  test-mark-review-183444  -      unassigned  -
47  Fix memory leak in cache  New    ai-agent    -

🔍 В ревью (0)

✅ Недавно закрытые (3)
43  test-flow-183542          Closed   ai-agent  -
46  Refactor admin panel      Closed   ai-agent  -

Kanban-доска: http://localhost:8080/projects/karaoke/boards/10
```

Опции:

- `--json` — JSON для парсинга в другие скрипты.
- `--quiet` — только статистика для cron / системных служб.
- `--limit N` — ограничить количество задач.
- `--assignee USER` — изменить фильтр (default = `$TRACKER_AGENT_USER`).

Exit codes:
- `0` — есть открытые задачи.
- `1` — открытых нет (всё сделано).
- `2` — OpenProject недоступен.
- `3` — токен истёк.

### Polling через cron / systemd-timer (опционально)

Если нужны регулярные опросы, можно настроить таймер каждые 5 минут:

```bash
# ~/.local/bin/tracker-poll-cron.sh
#!/usr/bin/env bash
cd /home/nsa/Karaoke
set -a; source .env.local-tracker; set +a
echo "[$(date -Iseconds)] $(bash tools/tracker-poll.sh --quiet 2>&1)" \
  >> ~/.local/share/tracker-poll.log
```

```bash
# crontab -e
*/5 * * * * bash ~/.local/bin/tracker-poll-cron.sh
```

### Полный workflow (user → agent → user → closed)

```
┌──────────────────────────────────────────────────────────────────────┐
│ 1. user  http://localhost:8080/projects/karaoke → +Task          │
│           assignee = ai-agent, save → work package #42 created      │
└──────────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│ 2. agent  bash tools/tracker-poll.sh  видит #42                    │
│           • bash tools/tracker-spec-for-issue.sh 42                 │
│             → создаёт specs/045-foo/spec.md + tasks.md             │
│             → добавляет комментарий в #42 со ссылкой                │
│           • cd specs/045-foo && заполняет User Stories / FR / AC   │
│           • bash tools/tracker.sh claim-issue 42                    │
│             → assignee=ai-agent, status = "In progress"            │
└──────────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│ 3. agent  ... реализация по tasks.md (commit/PR)                  │
│           • bash tools/tracker.sh add-comment 42 --file REPORT.md   │
│             → публикует markdown-отчёт (Что сделано/Файлы/...)    │
│           • bash tools/tracker.sh mark-review 42                   │
│             → status = "In review" (готов к проверке)             │
└──────────────────────────────────────────────────────────────────────┘
                                ↓
┌──────────────────────────────────────────────────────────────────────┐
│ 4. user   Открывает доску / work package #42 / PR                  │
│           • Проверяет работу                                         │
│           • Если всё ОК:                                            │
│             bash tools/tracker.sh close-issue 42                    │
│             → status = "Closed"                                     │
│           • Если нужна доработка:                                   │
│             bash tools/tracker.sh reopen-issue 42                   │
│             → status = "In progress" → agent видит в poll           │
└──────────────────────────────────────────────────────────────────────┘
```

### Создание спеки из work package

Если задача в OpenProject нетривиальная и требует полноценной спецификации
(для архивации, для git-PR, для повторного использования в будущем),
агент вызывает:

```bash
bash tools/tracker-spec-for-issue.sh <WORK_PACKAGE_ID>
```

Что произойдёт:

1. Скрипт прочитает `subject` + `description` work package через
   `tracker.sh get-issue`.
2. Сгенерирует slug в kebab-case: `Fix memory leak in cache` → `fix-memory-leak-in-cache`.
3. Найдёт следующий свободный номер спек (`045` на 2026-09-02, например).
4. Создаст каталог `specs/045-fix-memory-leak-in-cache/` с:
   - `spec.md` — frontmatter (work_package, created, source) + исходное
     описание + чек-лист «что нужно сделать».
   - `tasks.md` — заготовка чек-листа (Research / Design / Implementation /
     Verification / Close).
5. Добавит markdown-комментарий в work package со ссылкой на спеку.

После этого агент работает по обычному workflow (см. выше).

Опции:
- `--force` — перезаписать существующий каталог спеки (осторожно).

Exit codes:
- `0` — спека создана.
- `1` — work package не найден.
- `2` — каталог уже существует (use `--force`).
- `3` — ошибка tracker.sh.

## Troubleshooting

| Проблема | Решение |
|----------|---------|
| OpenProject не стартует 10+ минут | `docker logs openproject --tail 100` — смотреть ошибки. Обычно: Postgres ещё не healthy или `TRACKER_SECRET_KEY_BASE` короче требуемого (должен быть 128 hex). |
| Порт 8080 занят | `install-tracker.sh` автоматически выберет 7082/7083/7084 (см. `ATTEMPT_PORTS` в скрипте). |
| `401 Unauthorized` | `TRACKER_API_TOKEN` в `.env.local-tracker` отозван или устарел. Пересоздайте: удалите токен в БД или запустите `install-tracker.sh` (он переустановит токен через rails-runner). |
| `429 Too Many Requests` | CLI автоматически retry с backoff 2s/4s/8s. Если постоянно — уменьшить частоту polling. |
| Пустой `list-issues --assignee ai-agent` | assignee — это **login**, CLI сам резолвит в id; проверьте что пользователь существует: `docker exec openproject-db psql -U openproject -d openproject -c "SELECT id, login FROM users"` |
| `lock_version conflict` при PATCH | OpenProject требует актуальный `lockVersion`; CLI сам делает re-GET и retry (до 3 попыток). Если стабильно — кто-то активно правит задачу параллельно. |
| HTTP 400 `bad range specification in URL` | Баг curl: фильтры должны быть URL-encoded (CLI делает это автоматически); если вы запускаете curl вручную — используйте `jq -sRr @uri` или `--data-urlencode`. |
| Postgres не подключается | `docker logs openproject-db` — проверить пароль в `.env.local-tracker` совпадает с compose. |
| Бэкап не работает | `journalctl --user -u tracker-db-backup.service` (systemd) или `cat ~/.local/share/tracker-backup.log` (cron). |
| `tracker-postgres-data` volume заполнился | Проверьте `docker system df`; OpenProject создаёт attachments в `openproject-data` — посмотрите размер через `du -sh /var/lib/docker/volumes/openproject-data/`. |

## Управление

```bash
# Остановить
cd /home/nsa/Karaoke/deploy
docker compose -f tracker-docker-compose.yml stop

# Запустить снова
docker compose -f tracker-docker-compose.yml start

# Полностью удалить контейнеры (данные СОХРАНЯЮТСЯ в volumes)
docker compose -f tracker-docker-compose.yml down

# Удалить ВСЁ включая данные (ОСТОРОЖНО!)
docker compose -f tracker-docker-compose.yml down -v

# Логи
docker logs openproject --tail 100 -f
docker logs openproject-db --tail 100 -f

# Восстановление из pg_dump
docker compose -f tracker-docker-compose.yml stop openproject
docker compose -f tracker-docker-compose.yml exec openproject-db \
    pg_restore -U openproject -d openproject --clean --if-exists \
    /backups/tracker-YYYY-MM-DD.dump
docker compose -f tracker-docker-compose.yml start openproject
```

## Что дальше

- Прочитать [`specs/295-jira-local-integration/spec.md`](../specs/295-jira-local-integration/spec.md)
  для общего контекста и Success Criteria.
- Прочитать [`docs/features/tracker-local-integration.md`](features/tracker-local-integration.md) —
  архитектурная сводка, компоненты, workflow.
- Настроить polling (cron / systemd-timer для автоматического
  `list-issues` + `claim-issue`).
- Изучить OpenProject REST API v3: <https://www.openproject.org/docs/api/introduction/>.
