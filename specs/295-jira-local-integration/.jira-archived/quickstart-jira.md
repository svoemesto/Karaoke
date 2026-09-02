# Quickstart: Локальная Jira для AI-агента

**Date**: 2026-09-02
**Spec**: [spec.md](./spec.md)
**Data Model**: [data-model.md](./data-model.md)
**CLI Contract**: [contracts/jira-cli.md](./contracts/jira-cli.md)

## Что проверяет этот quickstart

Полный end-to-end сценарий от нуля до рабочей Jira + первый claim agent'ом.
После прохождения всех шагов вы должны иметь:
1. Работающую Jira на `http://localhost:8080` (или `http://localhost:8090` если 8080 занят).
2. Проект `KARAOKE` с workflow `To Do → In Progress → Done`.
3. Учётку `ai-agent` с правами developer.
4. CLI `tools/jira.sh` который может забирать задачи.
5. Systemd-timer для ежедневного бэкапа.
6. Один закрытый тестовый issue как доказательство работы всего pipeline.

**Требования к системе**:
- Linux x86_64 (Ubuntu 22.04+, Debian 11+, RHEL 9+).
- Docker 20.10+ с Docker Compose v2.
- Bash 4.4+, `curl`, `jq` 1.6+.
- ≥8 GB свободной RAM, ≥10 GB диска.
- Права на запуск systemd-timer (или crontab) для backup.

**Ожидаемое время**: ≤30 минут (SC-001).

---

## Этап 1: Установка (один раз)

### 1.1 Клонировать / перейти в репозиторий

```bash
cd /home/nsa/Karaoke
git checkout 295-jira-local-integration  # фича-ветка
git pull
```

### 1.2 Создать `.env.local-jira` (НЕ в git)

```bash
# Placeholder — будет заполнен после первого запуска Jira
cat > .env.local-jira <<'EOF'
JIRA_URL=http://localhost:8080
JIRA_USER=admin
JIRA_TOKEN=<будет заполнено после первого запуска>
JIRA_DB_PASSWORD=jira_local_strong_password_change_me
JIRA_AGENT_USER=ai-agent
EOF

# Убедиться, что .env.local-jira в .gitignore
grep -q '.env.local-jira' .gitignore || echo '.env.local-jira' >> .gitignore
```

**Проверка**:
```bash
git ls-files | grep -iE '\.env|\.key|\.pem' | grep -i jira
# Ожидаемый вывод: пусто (FR-009, SC-006)
```

### 1.3 Запустить Jira

```bash
docker compose -f deploy/jira-docker-compose.yml up -d
```

**Что происходит**:
- Pull образа `atlassian/jira-software:9.12.x` (~1 GB, 5-10 минут при первом запуске).
- Создаётся Postgres контейнер `jira-db` с named-volume `jira-postgres-data`.
- Создаётся named-volume `jira-backups` для дампов.
- Jira запускается, инициализирует схему в Postgres (5-10 минут).

**Проверка состояния**:
```bash
docker ps | grep -E 'jira|jira-db'
# Ожидаемый вывод:
# CONTAINER ID   IMAGE                              STATUS                    PORTS
# abc123         atlassian/jira-software:9.12.x    Up 5 min (healthy)        0.0.0.0:8080->8080/tcp
# def456         postgres:13                        Up 5 min (healthy)        5432/tcp

docker logs jira --tail 50 | grep -i 'started\|ready\|setup'
# Ожидаемый вывод: "Jira started successfully" или подобное
```

### 1.4 Открыть UI и пройти first-run setup

```bash
xdg-open http://localhost:8080 2>/dev/null || open http://localhost:8080
```

**В UI**:
1. Выбрать язык: English (или Russian — UI локализован Atlassian'ом).
2. Выбрать "I'll set it up myself" (не Atlassian Cloud).
3. Ввести license key:
   - **Evaluation license**: получить бесплатно на https://my.atlassian.com/license/evaluation
   - Вставить в поле "License key", нажать "Next".
4. Указать admin-аккаунт:
   - Username: `admin`
   - Password: `<придумать сильный пароль, например 32-символьный>`.
   - Email: `admin@localhost`.
5. **Setup email notifications**: skip (нет SMTP).
6. **Integration with Atlassian Cloud**: skip.

**Время**: ~3 минуты.

### 1.5 Создать API token для `admin`

1. В Jira UI: **Profile menu (top right) → Profile → Personal Access Tokens**.
2. Click "Create token".
3. Name: `karaoke-cli`.
4. Скопировать token (показывается ОДИН раз).

```bash
# Сохранить токен в .env.local-jira
vim .env.local-jira
# Заменить <placeholder> на скопированный токен
```

**Проверка**:
```bash
source .env.local-jira
curl -fsS -u "$JIRA_USER:$JIRA_TOKEN" "$JIRA_URL/rest/api/3/myself" | jq '.name'
# Ожидаемый вывод: "admin"
```

### 1.6 Создать пользователя `ai-agent`

```bash
source .env.local-jira
ADMIN_TOKEN="$JIRA_TOKEN"

# Создать пользователя
curl -fsS -u "$JIRA_USER:$ADMIN_TOKEN" \
  -X POST "$JIRA_URL/rest/api/3/user" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "ai-agent",
    "password": "ai_agent_local_strong_password_change_me",
    "emailAddress": "ai-agent@localhost",
    "displayName": "AI Agent",
    "applicationKeys": ["jira-software"]
  }' | jq .

# Назначить в группу jira-developers
curl -fsS -u "$JIRA_USER:$ADMIN_TOKEN" \
  -X POST "$JIRA_URL/rest/api/3/group/user" \
  -H "Content-Type: application/json" \
  -d '{"groupName":"jira-developers","username":"ai-agent"}'
```

**Создать API token для `ai-agent`**:
1. Залогиниться в Jira UI как `ai-agent` (URL: `http://localhost:8080`, тот же `Personal Access Tokens`).
2. Скопировать token.

```bash
# Сохранить новый токен в .env.local-jira
JIRA_TOKEN_NEW="<token для ai-agent>"
# В .env.local-jira заменить JIRA_TOKEN на токен ai-agent
# JIRA_USER=ai-agent
```

**Проверка**:
```bash
source .env.local-jira
./tools/jira.sh list-projects  # (пока пустой — нет проектов)
# Ожидаемый вывод: KEY NAME TYPE LEAD (пустая таблица)
```

### 1.7 Создать проект `KARAOKE`

```bash
source .env.local-jira
ADMIN_TOKEN=$(grep '^JIRA_ADMIN_TOKEN=' .env.local-jira | cut -d= -f2)
# Альтернатива: использовать JIRA_TOKEN если ещё admin

curl -fsS -u "admin:$ADMIN_TOKEN" \
  -X POST "$JIRA_URL/rest/api/3/project" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "KARAOKE",
    "name": "Karaoke Project",
    "projectTypeKey": "software",
    "projectTemplateKey": "com.pyxis.greenhopper.jira:gh-scrum-template",
    "lead": "admin"
  }' | jq .
```

**В UI**: проверить, что проект появился в `Projects → Karaoke Project`.

### 1.8 Зарегистрировать systemd-timer для backup

```bash
# Скопировать unit-файлы в ~/.config/systemd/user/ (без root)
mkdir -p ~/.config/systemd/user
cp deploy/jira-db-backup.{service,timer} ~/.config/systemd/user/
sed -i 's|karaoke-db-backup|jira-db-backup|g' ~/.config/systemd/user/jira-db-backup.{service,timer}

# Активировать
systemctl --user daemon-reload
systemctl --user enable jira-db-backup.timer
systemctl --user start jira-db-backup.timer

# Проверить
systemctl --user list-timers | grep jira
# Ожидаемый вывод: jira-db-backup.timer активен, next run завтра в 03:00
```

---

## Этап 2: Проверка end-to-end (5 минут)

### 2.1 Создать тестовую задачу в UI

1. Открыть `http://localhost:8080/projects/KARAOKE`.
2. Click "Create" → Issue type `Task`.
3. Summary: `Quickstart test issue`.
4. Description: `Это задача для проверки quickstart-сценария. Должна быть взята, прокомментирована и закрыта.`
5. Assignee: `ai-agent`.
6. Priority: `Medium`.
7. Click "Create".

**Запомнить KEY** (например, `KARAOKE-1`).

### 2.2 CLI: список задач агента

```bash
cd /home/nsa/Karaoke
source .env.local-jira
./tools/jira.sh list-issues --assignee ai-agent --status "To Do"
```

**Ожидаемый вывод**:
```
KEY         SUMMARY                       STATUS   PRIORITY   ASSIGNEE
KARAOKE-1   Quickstart test issue          To Do    Medium     ai-agent
```

### 2.3 CLI: взять задачу

```bash
./tools/jira.sh claim-issue KARAOKE-1
```

**Ожидаемый вывод**:
```
OK: KARAOKE-1 claimed by ai-agent (status: In Progress)
```

**В UI**: открыть задачу → убедиться, что status = `In Progress`.

### 2.4 CLI: добавить комментарий-отчёт

```bash
cat > /tmp/quickstart-report.md <<'EOF'
## Что сделано

Установлена Jira Data Center 9.12.x в Docker на nsa-i9. Создан проект KARAOKE,
учётка ai-agent, настроен systemd-timer для ежедневного бэкапа.

## Изменённые файлы

- deploy/jira-docker-compose.yml
- deploy/jira-db-backup.sh
- tools/jira.sh
- docs/jira-setup.md

## Прогон проверок

```
$ ./tools/jira.sh list-projects
KEY      NAME             TYPE       LEAD
KARAOKE  Karaoke Project  software   admin

$ ./tools/jira.sh list-issues --assignee ai-agent --status "In Progress"
KEY         SUMMARY                  STATUS      ASSIGNEE
KARAOKE-1   Quickstart test issue    In Progress ai-agent
```

## Известные ограничения

Jira evaluation license истекает через 30 дней. Для production нужна покупка DC-лицензии.
EOF

./tools/jira.sh add-comment KARAOKE-1 --file /tmp/quickstart-report.md
```

**Ожидаемый вывод**:
```
OK: comment added to KARAOKE-1 (id: 10001, length: 432 chars)
```

**В UI**: задача → раздел Comments → новый комментарий с отформатированным markdown.

### 2.5 CLI: закрыть задачу

```bash
./tools/jira.sh close-issue KARAOKE-1
```

**Ожидаемый вывод**:
```
OK: KARAOKE-1 closed (status: Done)
```

**В UI**: задача → status = `Done`.

### 2.6 Проверить healthcheck

```bash
./tools/jira.sh healthcheck
```

**Ожидаемый вывод**:
```
OK: Jira RUNNING at http://localhost:8080 (response: 87ms)
```

### 2.7 Проверить логи

```bash
cat logs/jira-agent.log | jq -c '.'
```

**Ожидаемый вывод** (5 записей — по одной на каждый вызов CLI):
```json
{"ts":"2026-09-02T15:00:01Z","cmd":"list-issues","req_id":"...","endpoint":"POST /rest/api/3/search","http_status":200,"duration_ms":234}
{"ts":"2026-09-02T15:00:15Z","cmd":"claim-issue","req_id":"...","endpoint":"PUT /rest/api/3/issue/KARAOKE-1/assignee","http_status":204,"duration_ms":345}
...
```

### 2.8 Проверить бэкап

```bash
# Ручной запуск (для проверки, что скрипт работает)
./tools/jira-backup.sh

# Проверить, что дамп создан
docker exec jira-db ls -la /var/lib/postgresql/data/backups/
# Ожидаемый вывод: jira-2026-09-02.dump (или аналогичный)
```

**Или** (если volume смонтирован в хост):
```bash
ls -la /var/lib/docker/volumes/karaoke_jira-backups/_data/
# Ожидаемый вывод: jira-YYYY-MM-DD.dump
```

### 2.9 Smoke-test

```bash
./tools/jira-smoke-test.sh
```

**Ожидаемый вывод** (если всё OK):
```
[1/9] healthcheck: OK
[2/9] list-projects: OK (found KARAOKE)
[3/9] create-issue: OK (KARAOKE-2)
[4/9] get-issue KARAOKE-2: OK
[5/9] claim-issue KARAOKE-2: OK
[6/9] add-comment KARAOKE-2: OK
[7/9] close-issue KARAOKE-2: OK
[8/9] final healthcheck: OK
[9/9] cleanup: OK
ALL PASS — Jira is fully functional
```

---

## Этап 3: Проверка Edge Cases (опционально, 5 минут)

### 3.1 Пустое описание

```bash
./tools/jira.sh create-issue --project KARAOKE --type Task --summary "Empty desc test"
# Должно создать KARAOKE-3 с пустым description
# При list-issues CLI должен вывести WARN
```

### 3.2 Истёкший токен

```bash
JIRA_TOKEN="invalid_token_xxx" ./tools/jira.sh list-projects
# Ожидаемый вывод: "JIRA_TOKEN истёк или отозван. Обновите токен в .env.local-jira..."
# Exit code: 3
```

### 3.3 Jira недоступна

```bash
docker stop jira
./tools/jira.sh list-projects
# Ожидаемый вывод: "Jira недоступна по http://localhost:8080, проверьте `docker ps`"
# Exit code: 2

docker start jira
# Дождаться healthcheck (≤10 минут)
```

### 3.4 Restore из бэкапа (DR drill)

```bash
# Создать временную задачу
./tools/jira.sh create-issue --project KARAOKE --type Task --summary "Will be lost in restore"

# Убедиться, что она есть
./tools/jira.sh list-issues --project KARAOKE

# Restore (требует подтверждения yes)
./tools/jira-restore.sh

# Должно показать warning, что KARAOKE-3 (временная) исчезла, остальные задачи на месте
./tools/jira.sh list-issues --project KARAOKE
```

---

## Acceptance Checklist

После прохождения всех шагов убедитесь:

- [ ] (SC-001) Установка заняла ≤30 минут (с учётом скачивания Docker-образа).
- [ ] (SC-002) `list-issues` отрабатывает за ≤5 секунд.
- [ ] (SC-004) Закрытая задача `KARAOKE-1` содержит комментарий-отчёт с секцией "Что сделано" и ссылкой на изменённые файлы.
- [ ] (SC-005) После `docker restart jira jira-db` все задачи на месте (named-volume работает).
- [ ] (SC-006) `git ls-files | grep jira | grep -iE '\.env|\.key'` возвращает пусто.
- [ ] (SC-008) Healthcheck возвращает OK за ≤3 секунды.
- [ ] (SC-009) `tools/jira-backup.sh` создаёт дамп за ≤2 минуты (пустая БД) или ≤10 минут (1 GB).
- [ ] (SC-011) Каждый вызов CLI добавляет ровно одну JSON-запись в `logs/jira-agent.log`.
- [ ] (FR-005) Все 8 подкоманд CLI работают: `list-projects`, `list-issues`, `get-issue`, `claim-issue`, `add-comment`, `close-issue`, `reopen-issue`, `create-issue`.

---

## Что дальше

После успешного quickstart:
- ✅ Прочитать [contracts/jira-cli.md](./contracts/jira-cli.md) для полного описания CLI.
- ✅ Настроить polling (cron / systemd-timer для `list-issues` + автоклейм).
- ✅ Написать `docs/jira-setup.md` с пошаговой документацией (FR-010).
- ✅ Передать пользователю для тестирования в реальных условиях.

**Следующая фаза**: `/speckit.tasks` для декомпозиции в tasks.md.
