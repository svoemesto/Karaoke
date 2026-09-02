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

## Этап 2: Использование

### Создать задачу в UI

1. Открыть `http://localhost:8080/projects/karaoke` (или ваш проект).
2. **+ Create new work package** → Type `Task`.
3. Subject: `<краткое название>`.
4. Description: `<детали, желательно ссылка на specs/<NNN>-*/spec.md>`.
5. Assignee: `ai-agent`.
6. Save → получить числовой ID (например, `42`).

### Агент берёт задачу

```bash
cd /home/nsa/Karaoke
source .env.local-tracker

# Список открытых задач, где assignee = ai-agent
./tools/tracker.sh list-issues --assignee ai-agent --status open

# Взять задачу (assignee = ai-agent + status = In progress)
./tools/tracker.sh claim-issue 42

# После выполнения работы — добавить markdown-отчёт
cat > /tmp/report.md <<'EOF'
## Что сделано

Реализована фича X.

## Изменённые файлы

- src/foo/Bar.kt
- docs/foo.md

## Прогон проверок

- `./gradlew :karaoke-web:ktlintCheck` — OK
- `./gradlew :karaoke-web:bootJar` — OK
- `./tools/check-livedocs-structure.sh` — OK

## Известные ограничения

Нет.
EOF

./tools/tracker.sh add-comment 42 --file /tmp/report.md

# Закрыть задачу (status = Closed)
./tools/tracker.sh close-issue 42
```

### Полный pipeline (пример)

```bash
# 1. Найти задачу
ID=$(./tools/tracker.sh list-issues --assignee ai-agent --status open \
        | tail -n +2 | head -1 | awk '{print $1}')

# 2. Взять в работу
./tools/tracker.sh claim-issue "$ID"

# 3. ... выполнить работу ...

# 4. Опубликовать отчёт
./tools/tracker.sh add-comment "$ID" --file ./REPORT.md

# 5. Закрыть
./tools/tracker.sh close-issue "$ID"
```

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
