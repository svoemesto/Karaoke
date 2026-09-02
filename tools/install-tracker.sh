#!/usr/bin/env bash
# =====================================================================
# tools/install-tracker.sh — first-run setup для OpenProject
# =====================================================================
# Алгоритм:
#   1. Проверить Docker, curl, jq.
#   2. Создать .env.local-tracker из .env.local-tracker.example (если нет).
#   3. Автодетект занятого порта 8080 (фолбэк 7082).
#   4. docker compose up -d.
#   5. Ожидание healthcheck /api/v3/health_check (≤10 минут).
#   6. Вывести инструкции для first-run UI setup.
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
COMPOSE_FILE="${REPO_ROOT}/deploy/tracker-docker-compose.yml"
ENV_FILE="${REPO_ROOT}/.env.local-tracker"
ENV_EXAMPLE="${REPO_ROOT}/.env.local-tracker.example"

# --- Цвета ---
if [ -t 1 ]; then
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_RED=$'\033[0;31m'
    C_RESET=$'\033[0m'
else
    C_GREEN=""
    C_YELLOW=""
    C_RED=""
    C_RESET=""
fi

info() { echo "${C_GREEN}▸${C_RESET} $*"; }
warn() { echo "${C_YELLOW}⚠${C_RESET} $*" >&2; }
err()  { echo "${C_RED}✗${C_RESET} $*" >&2; }

# --- Шаг 1: проверка зависимостей ---
info "Шаг 1/6: проверка зависимостей..."

for cmd in docker curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
        err "Команда '$cmd' не найдена. Установите её и перезапустите скрипт."
        exit 1
    fi
done

if ! docker ps >/dev/null 2>&1; then
    err "Docker daemon недоступен. Запустите Docker и попробуйте снова."
    exit 1
fi

info "  ✓ docker, curl, jq доступны"

# --- Шаг 2: создание .env.local-tracker ---
info "Шаг 2/6: настройка .env.local-tracker..."

if [ ! -f "$ENV_FILE" ]; then
    if [ ! -f "$ENV_EXAMPLE" ]; then
        err "Шаблон ${ENV_EXAMPLE} не найден"
        exit 1
    fi
    cp "$ENV_EXAMPLE" "$ENV_FILE"

    # Генерируем секреты
    DB_PASS=$(openssl rand -base64 32 | tr -d '/+=' | cut -c1-32)
    SECRET=$(openssl rand -hex 64)
    sed -i "s|^TRACKER_DB_PASSWORD=.*|TRACKER_DB_PASSWORD=${DB_PASS}|" "$ENV_FILE"
    sed -i "s|^TRACKER_SECRET_KEY_BASE=.*|TRACKER_SECRET_KEY_BASE=${SECRET}|" "$ENV_FILE"
    sed -i "s|^TRACKER_API_TOKEN=.*|TRACKER_API_TOKEN=placeholder_set_after_first_run|" "$ENV_FILE"

    warn "Создан ${ENV_FILE} с авто-сгенерированными секретами."
    warn "  Осталось: пройти first-run setup в UI и обновить TRACKER_API_TOKEN."
    warn ""
    info "  Запускаю docker compose up..."
fi

# Загружаем env для проверки
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ -z "${TRACKER_DB_PASSWORD:-}" ] || [ "$TRACKER_DB_PASSWORD" = "openproject_local_strong_password_change_me" ]; then
    err "TRACKER_DB_PASSWORD не задан или равен placeholder. Отредактируйте ${ENV_FILE}"
    exit 1
fi

info "  ✓ ${ENV_FILE} валиден"

# --- Шаг 3: проверка порта ---
info "Шаг 3/6: проверка порта..."

REQUESTED_PORT="${TRACKER_HTTP_PORT:-8080}"

# Автодетект — ищем первый свободный порт
ATTEMPT_PORTS=(8080 7082 7083 7084 8082)
SELECTED_PORT=""
for port in "${ATTEMPT_PORTS[@]}"; do
    if ! ss -tln 2>/dev/null | grep -qE ":${port}\b" && \
       ! netstat -tln 2>/dev/null | grep -qE ":${port}\b"; then
        SELECTED_PORT=$port
        break
    fi
done

if [ -z "$SELECTED_PORT" ]; then
    err "Не удалось найти свободный порт в ${ATTEMPT_PORTS[*]}"
    exit 1
fi

if [ "$SELECTED_PORT" != "$REQUESTED_PORT" ]; then
    warn "  Порт ${REQUESTED_PORT} занят, переключаюсь на ${SELECTED_PORT}"
fi

# Обновляем TRACKER_URL и TRACKER_HOST
TRACKER_URL="http://localhost:${SELECTED_PORT}"
sed -i "s|^TRACKER_URL=.*|TRACKER_URL=${TRACKER_URL}|" "$ENV_FILE"
sed -i "s|^TRACKER_HOST=.*|TRACKER_HOST=localhost:${SELECTED_PORT}|" "$ENV_FILE"

info "  ✓ OpenProject будет доступен на http://localhost:${SELECTED_PORT}"

# --- Шаг 4: docker compose up ---
info "Шаг 4/6: запуск OpenProject + Postgres..."

cd "${REPO_ROOT}/deploy"
# CRITICAL: при network_mode: host переменные окружения из shell НЕ
# пробрасываются в compose — нужно явно передать --env-file.
docker compose \
    --env-file "${ENV_FILE}" \
    -f tracker-docker-compose.yml \
    up -d 2>&1 | grep -vE "^\s*$|level=warning msg=\"Found orphan" | tail -10 || true

info "  ✓ Контейнеры запущены"

# --- Шаг 5: ожидание healthcheck ---
info "Шаг 5/7: ожидание готовности OpenProject (≤10 минут)..."

TIMEOUT=600
ELAPSED=0
SLEEP_INTERVAL=15

while [ $ELAPSED -lt $TIMEOUT ]; do
    if curl -fsS --max-time 5 "${TRACKER_URL}/health_check" 2>/dev/null | grep -qE '"openproject"|"db"' >/dev/null 2>&1; then
        info "  ✓ OpenProject готова (за ${ELAPSED} сек)"
        break
    fi
    sleep $SLEEP_INTERVAL
    ELAPSED=$((ELAPSED + SLEEP_INTERVAL))
    # Проверяем, что хотя бы web-сервер отвечает
    HTTP_STATUS=$(curl -sS --max-time 3 -o /dev/null -w "%{http_code}" "${TRACKER_URL}/" 2>/dev/null || echo "000")
    echo "  ... ожидание ${ELAPSED}/${TIMEOUT} сек (HTTP ${HTTP_STATUS})"
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    err "OpenProject не стартовала за ${TIMEOUT} секунд. Проверьте:"
    err "  docker logs openproject --tail 100"
    err "  docker logs openproject-db --tail 100"
    exit 1
fi

# --- Шаг 6: bootstrap пользователя ai-agent + API-токен ---
info "Шаг 6/7: bootstrap пользователя 'ai-agent' + API-токен..."

RUBY_SCRIPT='
require "json"
login = ENV["AI_AGENT_LOGIN"]
email = ENV["AI_AGENT_EMAIL"]
status = 0
begin
  User.where(login: login).first_or_initialize.tap do |u|
    u.login = login
    u.firstname = "ai"
    u.lastname = "agent"
    u.mail = email
    u.admin = true
    u.status = User.statuses[:active]
    u.password = SecureRandom.hex(32)
    u.save!
  end
  # Сгенерировать новый API token, если ещё не было
  token = Token::API.where(user_id: User.find_by(login: login).id).first
  plain = Token::API.create(user: User.find_by(login: login)).plain_value
  puts JSON.dump({status: :ok, token: plain})
rescue => e
  puts JSON.dump({status: :error, message: e.message, backtrace: e.backtrace.first(5)})
end
'

BOOTSTRAP_OUT=$(docker exec -e AI_AGENT_LOGIN="$TRACKER_AGENT_USER" -e AI_AGENT_EMAIL="ai-agent@karaoke.local" \
    openproject bash -c "cd /app && bundle exec rails runner -e production \"$RUBY_SCRIPT\"" 2>/dev/null | tail -1)
TOKEN_NEW=$(echo "$BOOTSTRAP_OUT" | jq -r '.token // empty' 2>/dev/null)

if [ -z "$TOKEN_NEW" ]; then
    err "Не удалось создать пользователя и токен. Проверьте: docker logs openproject --tail 50"
    err "  ${BOOTSTRAP_OUT}"
    exit 1
fi

sed -i "s|^TRACKER_USER=.*|TRACKER_USER=${TRACKER_AGENT_USER}|" "$ENV_FILE"
sed -i "s|^TRACKER_API_TOKEN=.*|TRACKER_API_TOKEN=${TOKEN_NEW}|" "$ENV_FILE"

info "  ✓ Пользователь '${TRACKER_AGENT_USER}' создан (admin), токен записан в ${ENV_FILE}"

# --- Шаг 7: финальный smoke-test (опционально, по флагу --smoke) ---
info "Шаг 7/7: установка завершена!"
echo ""
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo "${C_GREEN}  OpenProject запущен: ${TRACKER_URL}${C_RESET}"
echo "${C_GREEN}  Пользователь: ${TRACKER_AGENT_USER} (admin)${C_RESET}"
echo "${C_GREEN}  Токен записан в ${ENV_FILE}${C_RESET}"
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo ""

if [ "${1:-}" = "--smoke" ]; then
    info "Запускаю smoke-test (--smoke)..."
    cd "${REPO_ROOT}"
    ./tools/tracker-smoke-test.sh
    exit $?
fi

echo "Следующие шаги:"
echo ""
echo "1. (опционально) Откройте ${TRACKER_URL} в браузере — войдите как admin/admin (если создавали)"
echo ""
echo "2. (опционально) Создайте проект 'karaoke' в UI, если ещё нет:"
echo "   • Projects → + Create project → Name 'Karaoke', Identifier 'karaoke'"
echo "   • Добавьте ${TRACKER_AGENT_USER} в Project members с ролью 'Member'"
echo ""
echo "3. Запустите end-to-end smoke-test:"
echo "       ./tools/tracker-smoke-test.sh"
echo ""
echo "4. (опционально) Установите systemd-таймер для ежедневного бэкапа Postgres:"
echo "       sudo cp deploy/tracker-db-backup.{service,timer} /etc/systemd/system/"
echo "       sudo systemctl enable --now tracker-db-backup.timer"
echo ""
echo "Подробная документация: docs/tracker-setup.md"
