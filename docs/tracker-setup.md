# Tracker Setup — пошаговое руководство (OpenProject)

**Версия**: 1.0 (Pass 295)
**Связанные документы**: [`specs/295-jira-local-integration/spec.md`](../specs/295-jira-local-integration/spec.md)

## Что это

Локальный **OpenProject** Community Edition для AI-агента. OpenProject — это
open-source альтернатива Jira (полностью бесплатная, без лицензионных
ограничений, MIT-лицензия).

Пользователь заводит work packages (задачи) в OpenProject UI, AI-агент
через CLI (`tools/tracker.sh`) забирает их, выполняет и публикует
отчёт-комментарий с автоматическим закрытием.

## Почему OpenProject, а не Jira

Из спецификации 295 (.jira-archived/) сначала планировалось использовать Jira
Data Center, но:
- Atlassian блокирует регистрацию evaluation-license для пользователей из РФ/РБ.
- Jira DC без license работает в read-only mode через 30 дней.

OpenProject — это drop-in замена:
- Полностью бесплатный (MIT-лицензия).
- REST API v3 с теми же операциями (CRUD для work packages).
- Поддерживает agile-доски, workflow, custom fields, attachments.
- Self-hosted через Docker.

## Требования

- Linux x86_64 (Ubuntu 22.04+, Debian 11+, RHEL 9+).
- Docker 20.10+ с Docker Compose v2.
- Bash 4.4+, `curl`, `jq` 1.6+, `openssl`.
- ≥8 GB свободной RAM, ≥5 GB диска (OpenProject образ ~2 GB).

## Этап 1: Установка

### 1.1 Создать `.env.local-tracker`

```bash
cd /home/nsa/Karaoke
cp .env.local-tracker.example .env.local-tracker
# Скрипт ниже создаст с авто-секретами, либо отредактируйте вручную
vim .env.local-tracker  # заменить TRACKER_DB_PASSWORD и TRACKER_SECRET_KEY_BASE
```

**Важно**: файл `.env.local-tracker` уже в `.gitignore`. Не коммитить.

### 1.2 Запустить OpenProject

```bash
cd /home/nsa/Karaoke
bash tools/install-tracker.sh
```

Скрипт автоматически:
- Создаст `.env.local-tracker` с секретами (если нет).
- Найдёт свободный порт (8080 → 7082 → ...).
- Сделает `docker compose up -d`.
- Дождётся healthcheck (`/api/v3/health_check`).
- Выведет инструкции для first-run UI.

### 1.3 First-run setup в UI

Открыть `http://localhost:8080` (или какой порт выбрал скрипт).

**Шаги**:
1. Язык: English.
2. Создать **admin-аккаунт**:
   - Username: `admin`
   - Password: придумать сильный пароль (записать!)
   - Email: `admin@localhost`.
3. Название организации: любое (например, "Karaoke Dev").
4. OpenProject предложит создать первый проект:
   - Name: `Karaoke`
   - Identifier: `karaoke`
   - Type: `Scrum project` или `Classic project` — на ваш выбор.

### 1.4 Создать API token для admin

1. В OpenProject UI: **My Account** (правый верхний угол) → **Access Tokens** → **+ Generate**.
2. Название: `karaoke-cli`.
3. **Скопировать токен** (показывается ОДИН раз).

```bash
# Обновить TRACKER_API_TOKEN в .env.local-tracker
vim /home/nsa/Karaoke/.env.local-tracker
# Изменить:
#   TRACKER_USER=admin
#   TRACKER_API_TOKEN=<скопированный токен>
```

**Проверка**:
```bash
cd /home/nsa/Karaoke
source .env.local-tracker
./tools/tracker.sh healthcheck
# Ожидаемый вывод: "OK: OpenProject UP at http://localhost:8080"

./tools/tracker.sh list-projects
# Ожидаемый вывод: таблица с созданным проектом Karaoke
```

### 1.5 Создать пользователя `ai-agent`

1. UI → **Administration** → **Users** → **+ New user**.
2. Заполнить:
   - Username: `ai-agent`
   - Email: `ai-agent@localhost`
   - Password: придумать сильный (можно использовать тот же, что в Jira-старой спеке).
   - **Status**: Active.
3. Назначить в группу: **Project members** (или конкретный проект через Memberships).
4. Save.

### 1.6 Создать API token для `ai-agent`

1. Залогиниться как `ai-agent` (Logout в правом верхнем углу → Login).
2. **My Account → Access Tokens → + Generate**.
3. Name: `karaoke-cli-agent`.
4. Скопировать токен.

```bash
# Обновить .env.local-tracker (финальная версия — теперь от имени ai-agent)
vim /home/nsa/Karaoke/.env.local-tracker
# Изменить:
#   TRACKER_USER=ai-agent
#   TRACKER_API_TOKEN=<новый токен для ai-agent>
```

### 1.7 Узнать project_id и type_id

CLI нужен **числовой ID** проекта и типа задачи. Получить через REST:

```bash
source /home/nsa/Karaoke/.env.local-tracker

# Project ID
./tools/tracker.sh list-projects
# Первая колонка "ID" — это и есть project_id

# Type ID
curl -fsS -u "${TRACKER_USER}:${TRACKER_API_TOKEN}" \
    "${TRACKER_URL}/api/v3/types?pageSize=100" \
    | jq -r '.embedded.elements[] | "\(.id)\t\(.name)"'
# Найти строку "Task" — это и есть type_id
```

Запомните эти два числа — они нужны для `create-issue`.

### 1.8 Настроить backup (systemd-timer)

```bash
# Скопировать unit-файлы
mkdir -p ~/.config/systemd/user
cp /home/nsa/Karaoke/deploy/tracker-db-backup.{service,timer} ~/.config/systemd/user/

# Активировать
systemctl --user daemon-reload
systemctl --user enable tracker-db-backup.timer
systemctl --user start tracker-db-backup.timer

# Проверить
systemctl --user list-timers | grep tracker
# Должен показать next run завтра в 03:00
```

### 1.9 End-to-end smoke-test

```bash
cd /home/nsa/Karaoke
./tools/tracker-smoke-test.sh
# Ожидаемый вывод:
#   [1/8] healthcheck: OK
#   [2/8] list-projects (найдено проектов: N): OK
#   [3/8] create-issue: OK
#   ...
#   ALL PASS — OpenProject полностью функционален
```

## Этап 2: Использование

### Создать задачу в UI

1. Открыть `http://localhost:8080/projects/karaoke` (или ваш проект).
2. **+ Create new work package** → Type `Task`.
3. Subject: `<краткое название>`.
4. Description: `<детали, желательно ссылка на spec.md>`.
5. Assignee: `ai-agent`.
6. Save → получить ID (например, `42`).

### Агент берёт задачу

```bash
cd /home/nsa/Karaoke
source .env.local-tracker

# Список задач агента
./tools/tracker.sh list-issues --assignee ai-agent --status open

# Взять задачу
./tools/tracker.sh claim-issue 42

# После выполнения работы — добавить отчёт
cat > /tmp/report.md <<'EOF'
## Что сделано
Реализована фича X.

## Изменённые файлы
- src/foo/Bar.kt
- docs/foo.md

## Прогон проверок
- ./gradlew ktlintCheck — OK
- ./gradlew :karaoke-web:bootJar — OK

## Известные ограничения
Нет.
EOF
./tools/tracker.sh add-comment 42 --file /tmp/report.md

# Закрыть задачу
./tools/tracker.sh close-issue 42
```

## Troubleshooting

| Проблема | Решение |
|----------|---------|
| OpenProject не стартует 5+ минут | `docker logs openproject --tail 100` — смотреть ошибки. Часто: Postgres ещё не healthy. |
| Порт 8080 занят | `install-tracker.sh` автоматически выберет 7082/7083/7084. |
| `401 Unauthorized` | Токен в `.env.local-tracker` истёк или отозван. Создать новый в UI → My Account → Access Tokens. |
| `429 Too Many Requests` | CLI автоматически retry с backoff. Если постоянно — уменьшить частоту polling. |
| Postgres не подключается | `docker logs openproject-db` — проверить пароль в `.env.local-tracker` совпадает с compose. |
| Бэкап не работает | `journalctl --user -u tracker-db-backup.service` — смотреть ошибки systemd. |
| `lock_version conflict` при PATCH | OpenProject требует актуальный lockVersion. Повторите операцию через 5 секунд (новая версия будет подтянута через GET). |

## Управление

```bash
# Остановить OpenProject
cd /home/nsa/Karaoke/deploy
docker compose -f tracker-docker-compose.yml stop

# Запустить снова
docker compose -f tracker-docker-compose.yml start

# Полностью удалить (ОСТОРОЖНО — данные сохранятся в volumes, но контейнеры исчезнут)
docker compose -f tracker-docker-compose.yml down

# Удалить ВСЁ включая данные
docker compose -f tracker-docker-compose.yml down -v

# Логи
docker logs openproject --tail 100 -f
docker logs openproject-db --tail 100 -f
```

## Что дальше

- ✅ Прочитать [`specs/295-jira-local-integration/spec.md`](../specs/295-jira-local-integration/spec.md) для контекста.
- ✅ Настроить polling (cron / systemd-timer для автоматического `list-issues` + `claim-issue`).
- ✅ Изучить OpenProject REST API v3: https://www.openproject.org/docs/api/introduction/
