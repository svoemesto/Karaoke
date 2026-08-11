#!/usr/bin/env bash
# specs/170-mint-dev-setup/setup-mint.sh
#
# Идемпотентная настройка Linux Mint 22.2 (Zara) для проекта Karaoke.
# Запускать от не-root пользователя с sudo. После выполнения — 8 контейнеров
# должны быть в статусе Up, smoke-test зелёный.
#
# Использование:
#   bash specs/170-mint-dev-setup/setup-mint.sh                # базовая установка
#   bash specs/170-mint-dev-setup/setup-mint.sh --with-ollama  # + Ollama (LLM)
#   bash specs/170-mint-dev-setup/setup-mint.sh --help
#
# Требования:
#   - Linux Mint 22.2 (Zara), x86_64, НЕ root (с sudo)
#   - Интернет (apt, deb.nodesource.com, download.docker.com, github.com, Docker Hub)
#   - ~5 ГБ свободного места
#
# Что делает (по секциям):
#   1. OS check (fail-fast на неподдерживаемой ОС)
#   2. sudo + git + curl check
#   3. apt install системных зависимостей (идемпотентно через dpkg-query)
#   4. Node 22 LTS через NodeSource
#   5. Docker CE из download.docker.com
#   6. git config (blame.ignoreRevsFile)
#   7. Клонирование/обновление ~/Karaoke
#   8. Создание bind-mount папок
#   9. do.sh pull + start all (контейнеры 1-7)
#  10. MinIO отдельной командой
#  11. Опционально Ollama
#  12. Smoke-test (8 контейнеров + 4 HTTP + Postgres)
#  13. Rollback-инструкции
#
# Безопасность:
#   - do.env НЕ создаётся автоматически — пользователь копирует из шаблона сам
#   - do.env должен быть chmod 600 (секреты)
#   - скрипт НЕ логирует значения переменных из do.env (только их наличие)

set -euo pipefail

# === КОНСТАНТЫ ===
SCRIPT_NAME="$(basename "$0")"
KARAOKE_REPO="https://github.com/svoemesto/Karaoke.git"
EXPECTED_OS_ID="linuxmint"
EXPECTED_OS_VERSION="22.2"
EXPECTED_OS_CODENAME="Zara"
KARAOKE_DIR="${KARAOKE_DIR:-$HOME/Karaoke}"
DEPLOY_DIR="${KARAOKE_DIR}/deploy"
SMOKE_RETRY_ATTEMPTS=5
SMOKE_RETRY_DELAY=3
LOG_PREFIX="[setup-mint]"

# === ЦВЕТА ДЛЯ ВЫВОДА ===
if [ -t 1 ]; then
  C_GREEN="\033[0;32m"
  C_YELLOW="\033[0;33m"
  C_RED="\033[0;31m"
  C_BLUE="\033[0;34m"
  C_RESET="\033[0m"
else
  C_GREEN="" C_YELLOW="" C_RED="" C_BLUE="" C_RESET=""
fi

log()    { echo -e "${C_BLUE}${LOG_PREFIX}${C_RESET} $*"; }
ok()     { echo -e "${C_GREEN}${LOG_PREFIX} ✓${C_RESET} $*"; }
warn()   { echo -e "${C_YELLOW}${LOG_PREFIX} ⚠${C_RESET} $*" >&2; }
err()    { echo -e "${C_RED}${LOG_PREFIX} ✗${C_RESET} $*" >&2; }
die()    { err "$@"; exit 1; }

# === HELP ===
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  sed -n '3,30p' "$0"
  exit 0
fi

# === ПАРСИНГ АРГУМЕНТОВ ===
WITH_OLLAMA=0
for arg in "$@"; do
  case "$arg" in
    --with-ollama) WITH_OLLAMA=1 ;;
    *) die "Unknown argument: $arg (use --help)" ;;
  esac
done

# === СЕКЦИЯ 1: OS CHECK ===
log "Секция 1/12: проверка ОС..."
if [ ! -f /etc/os-release ]; then
  die "Не найден /etc/os-release — этот скрипт работает только на Linux Mint 22.2 (Zara) / Ubuntu 24.04 Noble"
fi
. /etc/os-release
if [ "${ID:-}" != "${EXPECTED_OS_ID}" ]; then
  die "Несовместимая ОС: ${PRETTY_NAME:-unknown}. Скрипт работает ТОЛЬКО на Linux Mint ${EXPECTED_OS_VERSION} (${EXPECTED_OS_CODENAME}). См. specs/170-mint-dev-setup/spec.md § R-003."
fi
# VERSION_CODENAME на Linux Mint = lowercase ('zara'), на Ubuntu = 'noble'.
# Сравниваем case-insensitive чтобы быть устойчивыми к обоим вариантам.
if [ "${VERSION:-}" != "${EXPECTED_OS_VERSION} (${EXPECTED_OS_CODENAME})" ]; then
  warn "Нестандартная строка VERSION: '${VERSION:-}'. Ожидалось '${EXPECTED_OS_VERSION} (${EXPECTED_OS_CODENAME})'. Продолжаем — главное что ID=linuxmint и версия 22.2."
fi
ok "ОС: ${PRETTY_NAME} (поддерживается)"

# === СЕКЦИЯ 2: SUDO + БАЗОВЫЕ УТИЛИТЫ ===
log "Секция 2/12: проверка sudo, git, curl..."
command -v sudo >/dev/null 2>&1 || die "sudo не найден. Установите sudo или запустите от root."
sudo -n true 2>/dev/null || warn "потребуется ввод пароля для sudo далее"
command -v git >/dev/null 2>&1 || die "git не найден. Установите: sudo apt install -y git"
command -v curl >/dev/null 2>&1 || die "curl не найден. Установите: sudo apt install -y curl"
ok "sudo, git, curl в наличии"

# === СЕКЦИЯ 3: APT УСТАНОВКИ ===
log "Секция 3/12: apt install (идемпотентно)..."

is_installed() {
  dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q "install ok installed"
}

install_if_missing() {
  local pkg="$1"
  if is_installed "$pkg"; then
    log "  $pkg — уже установлен"
  else
    log "  $pkg — устанавливаем..."
    sudo apt install -y "$pkg"
  fi
}

sudo apt update
for pkg in openjdk-17-jdk git curl wget ca-certificates apt-transport-https \
           software-properties-common gnupg lsb-release \
           postgresql-client-16 ffmpeg python3 python3-pip jq \
           pre-commit; do
  install_if_missing "$pkg"
done

# pre-commit уже поставлен через apt install в цикле выше.
# pip-вариант ненадёжен на Ubuntu 24.04 / Mint 22.2 (PEP 668 блокирует
# глобальный `pip install`; требовался бы --break-system-packages).

# melt (MLT 7+) — через snap или сборка (на Mint 22.2 нет apt-пакета)
# Пропускаем — не критично для smoke-test (нужен только для рендера видео)

ok "apt пакеты в актуальном состоянии"

# === СЕКЦИЯ 4: NODE 22 LTS ===
log "Секция 4/12: Node.js 22 LTS через NodeSource..."
NODE_VERSION_MAJOR=$(node -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/' || echo 0)
if [ "${NODE_VERSION_MAJOR}" -ge 22 ]; then
  ok "Node.js $(node -v) уже установлен (>= 22)"
else
  log "  Текущий Node.js: $(node -v 2>/dev/null || echo 'не установлен') — обновляем до 22 LTS"
  curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
  sudo apt install -y nodejs
  ok "Node.js $(node -v) установлен"
fi

# === СЕКЦИЯ 5: DOCKER CE ===
log "Секция 5/12: Docker CE из download.docker.com..."
if command -v docker >/dev/null 2>&1 && docker --version | grep -q "Docker version"; then
  DOCKER_VER=$(docker --version | grep -oE '[0-9]+\.[0-9]+' | head -1)
  DOCKER_VER_MAJOR=$(echo "$DOCKER_VER" | cut -d. -f1)
  if [ "${DOCKER_VER_MAJOR}" -ge 24 ]; then
    ok "Docker ${DOCKER_VER} уже установлен (>= 24)"
  else
    warn "Docker ${DOCKER_VER} устарел (нужен 24+). Требуется ручное обновление."
  fi
else
  log "  Docker не найден — устанавливаем..."
  # Удаляем snap-docker и старый docker.io, если есть
  if snap list docker 2>/dev/null | grep -q docker; then
    warn "Обнаружен snap docker — удаляем (конфликтует с apt-версией)"
    sudo snap remove docker
  fi
  if is_installed docker.io; then
    warn "Обнаружен docker.io (legacy) — удаляем"
    sudo apt remove -y docker.io docker-compose
  fi
  # Подключаем репозиторий Docker
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu noble stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  sudo apt update
  for pkg in docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin; do
    install_if_missing "$pkg"
  done
  ok "Docker $(docker --version | grep -oE '[0-9]+\.[0-9.]+') установлен"
fi

# Создаём wrapper-скрипт /usr/local/bin/docker-compose → 'docker compose'.
# Зачем: deploy/do.sh:16 делает 'COMPOSE=$(which docker-compose)' и дальше
# вызывает '${COMPOSE} -f ... up -d'. На Ubuntu 24.04 docker-compose v1
# не устанавливается через apt, есть только 'docker compose' v2 plugin.
# Без wrapper-скрипта do.sh получает COMPOSE="" и падает с '-f: команда не
# найдена'. С wrapper — 'which docker-compose' находит /usr/local/bin/docker-compose,
# который exec-ит 'docker compose "$@"'. Совместимо и с v1-стилем ('docker-compose
# up'), и с v2 ('docker compose up').
if [ ! -f /usr/local/bin/docker-compose ]; then
  log "  Создаём wrapper-скрипт /usr/local/bin/docker-compose → docker compose..."
  echo '#!/bin/sh' | sudo tee /usr/local/bin/docker-compose > /dev/null
  echo 'exec docker compose "$@"' | sudo tee -a /usr/local/bin/docker-compose > /dev/null
  sudo chmod +x /usr/local/bin/docker-compose
  ok "wrapper docker-compose установлен"
fi

# Добавляем пользователя в группу docker
if id -nG "${USER}" | tr ' ' '\n' | grep -qx docker; then
  ok "Пользователь ${USER} уже в группе docker"
else
  log "  Добавляем ${USER} в группу docker..."
  sudo usermod -aG docker "${USER}"
  warn "Группа docker добавлена, но применится только после перелогина."
  warn "  Workaround для текущей сессии: exec newgrp docker"
  warn "  Или откройте новый терминал и перезапустите этот скрипт."
fi

# Авто-применение группы docker в текущей сессии.
# Если скрипт запущен из shell, где группа уже добавлена, но не активна —
# 'sg docker' создаст sub-shell с активной группой; в нём выполняется всё,
# что требует docker socket (docker ps, docker compose up, и т.д.).
# Делаем это через exec замену текущего скрипта на 'sg docker -c <тот же скрипт>'.
# Но опасно: если пользователь передал --with-ollama, аргументы теряются.
# Безопаснее: проверяем, можем ли уже ходить в docker без sudo.
if ! sg docker -c 'docker ps' >/dev/null 2>&1; then
  if id -nG "${USER}" | tr ' ' '\n' | grep -qx docker; then
    warn "Группа docker есть, но не активна в текущей сессии."
    warn "Скрипт продолжит работу через 'sg docker -c <cmd>' (sub-shell с активной группой)."
    warn "Для применения глобально: 'exec newgrp docker' и перезапуск скрипта."
  fi
fi

# === СЕКЦИЯ 6: GIT CONFIG ===
log "Секция 6/12: git config..."
git config --global blame.ignoreRevsFile .git-blame-ignore-revs 2>/dev/null || true
ok "git config blame.ignoreRevsFile настроен"

# === СЕКЦИЯ 7: КЛОНИРОВАНИЕ РЕПОЗИТОРИЯ ===
log "Секция 7/12: клонирование/обновление ${KARAOKE_DIR}..."
if [ -d "${KARAOKE_DIR}/.git" ]; then
  log "  Репозиторий уже склонирован — обновляем (git fetch, без pull)..."
  # git pull требует upstream; для свежей локальной ветки (например,
  # 170-mint-dev-setup ещё не запушенной) upstream нет, и git pull
  # интерактивно спросит 'git branch --set-upstream-to=origin/<ветка>',
  # что под `set -e` ломает скрипт. Используем fetch — обновляет refs,
  # но не трогает локальные ветки. Это безопаснее для непushed-веток.
  (cd "${KARAOKE_DIR}" && git fetch --tags --prune 2>&1 | tail -3)
  # Если текущая ветка ИМЕЕТ upstream — fast-forward merge. Иначе — skip.
  UPSTREAM=$(cd "${KARAOKE_DIR}" && git rev-parse --abbrev-ref '@{u}' 2>/dev/null || true)
  if [ -n "${UPSTREAM}" ]; then
    log "  Upstream есть (${UPSTREAM}) — fast-forward pull..."
    (cd "${KARAOKE_DIR}" && git pull --ff-only 2>&1 | tail -3)
  else
    log "  У текущей ветки нет upstream (локальная фича-ветка ещё не запушена) — пропускаем pull"
  fi
else
  log "  Клонируем ${KARAOKE_REPO}..."
  git clone "${KARAOKE_REPO}" "${KARAOKE_DIR}"
fi
ok "Репозиторий готов: ${KARAOKE_DIR}"

# === СЕКЦИЯ 8: BIND-MOUNT ПАПКИ ===
log "Секция 8/12: создание bind-mount папок..."

# В Karaoke ДВА env-файла:
#   - deploy/.env  — пути, порты, STORAGE_KEY/SECRET, DB_LOCAL_POSTGRES_PASSWORD
#   - deploy/do.env — ТОЛЬКО секреты (DOCKER_PASSWORD, VK_*, YOOKASSA_*)
# Скрипт source-ит оба. Если только один — работаем с тем, что есть, но
# предупреждаем про второй.

if [ ! -f "${DEPLOY_DIR}/.env" ] && [ ! -f "${DEPLOY_DIR}/do.env" ]; then
  die "Не найдены ОБА env-файла:
  - ${DEPLOY_DIR}/.env   (пути, порты, ключи MinIO, пароль Postgres)
  - ${DEPLOY_DIR}/do.env (секреты: Docker PAT, VK/YOOKASSA/STEMJOBS_SECRET)
Скопируйте из admin-машины или создайте из шаблона:
  cp ${DEPLOY_DIR}/do.env.template ${DEPLOY_DIR}/do.env
  chmod 600 ${DEPLOY_DIR}/do.env
  nano ${DEPLOY_DIR}/do.env"
fi
if [ ! -f "${DEPLOY_DIR}/.env" ]; then
  warn "${DEPLOY_DIR}/.env ОТСУТСТВУЕТ — без него docker-compose не найдёт пути/порты/STORAGE_KEY/DB_LOCAL_POSTGRES_PASSWORD."
  warn "  Скопируйте с admin-машины: scp admin:/path/to/deploy/.env ${DEPLOY_DIR}/.env"
fi
if [ ! -f "${DEPLOY_DIR}/do.env" ]; then
  warn "${DEPLOY_DIR}/do.env ОТСУТСТВУЕТ — без него Docker push и VK/YOOKASSA вызовы не пройдут."
  warn "  Скопируйте с admin-машины или создайте: cp ${DEPLOY_DIR}/do.env.template ${DEPLOY_DIR}/do.env"
fi
[ -f "${DEPLOY_DIR}/do.env" ] && [ "$(stat -c %a ${DEPLOY_DIR}/do.env)" != "600" ] && \
  warn "do.env имеет права $(stat -c %a ${DEPLOY_DIR}/do.env), рекомендуется 600" && \
  sudo chmod 600 "${DEPLOY_DIR}/do.env"
[ -f "${DEPLOY_DIR}/.env" ] && [ "$(stat -c %a ${DEPLOY_DIR}/.env)" != "600" ] && \
  warn ".env имеет права $(stat -c %a ${DEPLOY_DIR}/.env), рекомендуется 600" && \
  sudo chmod 600 "${DEPLOY_DIR}/.env"

# Читаем пути из env-файлов.
# В Karaoke два env-файла:
#   - deploy/.env  — пути, порты, STORAGE_KEY/SECRET, DB_LOCAL_POSTGRES_PASSWORD (полная конфигурация)
#   - deploy/do.env — ТОЛЬКО секреты (DOCKER_PASSWORD, VK_*, YOOKASSA_*, STEMJOBS_INTERNAL_SECRET)
# Оба source-ятся через `set -a; source; set +a` — порядок важен: сначала .env
# (даёт дефолты), потом do.env (поверх, добавляет секреты; не перезаписывает
# общие ключи типа STORAGE_KEY, т.к. их в do.env нет).
load_env_paths() {
  set -a
  # shellcheck disable=SC1091
  if [ -f "${DEPLOY_DIR}/.env" ]; then
    . "${DEPLOY_DIR}/.env"
  fi
  # shellcheck disable=SC1091
  if [ -f "${DEPLOY_DIR}/do.env" ]; then
    . "${DEPLOY_DIR}/do.env"
  fi
  set +a
}

load_env_paths

REQUIRED_DIRS=(
  "${APP_FOLDER_HOST:-}"
  "${APP_FOLDER_K1:-}"
  "${APP_FOLDER_K2:-}"
  "${APP_FOLDER_K3:-}"
  "${APP_FOLDER_SYSTEM:-}"
  "${APP_FOLDER_STORE:-}"
  "${STORAGE_FOLDER:-}"
  "${DB_FOLDER:-}"
)

for dir in "${REQUIRED_DIRS[@]}"; do
  if [ -z "$dir" ]; then
    warn "  Пропускаем пустую переменную из do.env"
    continue
  fi
  if [ -d "$dir" ]; then
    log "  $dir — уже существует"
  else
    log "  $dir — создаём..."
    sudo mkdir -p "$dir"
    sudo chown "${USER}:${USER}" "$dir" 2>/dev/null || sudo chown -R "${USER}" "$dir" 2>/dev/null || true
  fi
done
ok "Bind-mount папки готовы"

# === СЕКЦИЯ 9: DO.SH PULL + START ALL ===
log "Секция 9/12: загрузка образов и старт 7 контейнеров (app/web/db/searxng/fourget)..."

# Все docker-команды оборачиваем в 'sg docker -c', чтобы гарантировать
# активную группу docker даже если скрипт запущен в shell, где
# 'usermod -aG docker' уже применился, но сессия не перезагружена.
# 'sg' создаёт sub-shell с активной группой — overhead минимальный,
# зато не зависит от того, перелогинился пользователь или нет.
docker_cmd() {
  sg docker -c "$*"
}

(cd "${DEPLOY_DIR}" && docker_cmd 'bash do.sh pull' 2>&1 | tail -10) || warn "do.sh pull завершился с предупреждениями (часто OK)"
(cd "${DEPLOY_DIR}" && docker_cmd 'bash do.sh start all' 2>&1 | tail -15) || die "do.sh start all упал — смотри логи docker"
ok "7 контейнеров запущены"

# === СЕКЦИЯ 10: MINIO ===
log "Секция 10/12: старт MinIO (отдельная команда)..."
(cd "${DEPLOY_DIR}" && docker_cmd 'docker compose -f docker-compose-storage.yml up -d' 2>&1 | tail -5) || \
  die "MinIO не стартовал — проверь docker logs karaoke-storage"
ok "MinIO запущен"

# === СЕКЦИЯ 11: OLLAMA (опционально) ===
if [ "$WITH_OLLAMA" -eq 1 ]; then
  log "Секция 11/12: Ollama (LLM, опционально)..."
  (cd "${DEPLOY_DIR}" && docker_cmd 'docker compose -f docker-compose-ollama.yml up -d' 2>&1 | tail -5) || \
    warn "Ollama не стартовала — можно поднять позже: cd deploy && docker compose -f docker-compose-ollama.yml up -d"
  ok "Ollama запущена (порт 11434)"
else
  log "Секция 11/12: Ollama пропущена (запустите --with-ollama чтобы добавить)"
fi

# === СЕКЦИЯ 12: SMOKE-TEST ===
log "Секция 12/12: smoke-test (retry-loop)..."

# Helper: retry N раз с задержкой. Все docker-команды — через sg docker -c,
# чтобы не падать на permission denied если группа docker не активна.
check_container_up() {
  local name="$1"
  local attempt=1
  while [ "$attempt" -le "$SMOKE_RETRY_ATTEMPTS" ]; do
    if sg docker -c "docker ps --format '{{.Names}}'" 2>/dev/null | grep -qx "$name"; then
      STATUS=$(sg docker -c "docker ps --format '{{.Names}}\t{{.Status}}'" | grep -E "^${name}\b" | awk '{print $2}')
      if [ "$STATUS" = "Up" ] || echo "$STATUS" | grep -q "^Up "; then
        ok "  контейнер $name: $STATUS"
        return 0
      fi
    fi
    log "  контейнер $name: попытка $attempt/$SMOKE_RETRY_ATTEMPTS (ещё не Up)..."
    sleep "$SMOKE_RETRY_DELAY"
    attempt=$((attempt + 1))
  done
  err "  контейнер $name: НЕ СТАРТОВАЛ после $((SMOKE_RETRY_ATTEMPTS * SMOKE_RETRY_DELAY)) сек"
  err "  Диагностика: sg docker -c 'docker logs $name'"
  return 1
}

check_http_200() {
  local url="$1"
  local desc="$2"
  local attempt=1
  while [ "$attempt" -le "$SMOKE_RETRY_ATTEMPTS" ]; do
    CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" 2>/dev/null || echo "000")
    if [ "$CODE" = "200" ] || [ "$CODE" = "301" ] || [ "$CODE" = "302" ] || [ "$CODE" = "307" ]; then
      ok "  $desc ($url): $CODE"
      return 0
    fi
    log "  $desc ($url): попытка $attempt/$SMOKE_RETRY_ATTEMPTS (код=$CODE)..."
    sleep "$SMOKE_RETRY_DELAY"
    attempt=$((attempt + 1))
  done
  err "  $desc ($url): НЕ ОТВЕЧАЕТ после $((SMOKE_RETRY_ATTEMPTS * SMOKE_RETRY_DELAY)) сек"
  return 1
}

check_postgres_ready() {
  local attempt=1
  while [ "$attempt" -le "$SMOKE_RETRY_ATTEMPTS" ]; do
    if PGPASSWORD="${DB_LOCAL_POSTGRES_PASSWORD}" psql -h localhost -U "${DB_LOCAL_POSTGRES_USER}" -d karaoke -c "SELECT 1" >/dev/null 2>&1; then
      ok "  Postgres: подключение успешно"
      return 0
    fi
    log "  Postgres: попытка $attempt/$SMOKE_RETRY_ATTEMPTS (ещё инициализируется)..."
    sleep "$SMOKE_RETRY_DELAY"
    attempt=$((attempt + 1))
  done
  err "  Postgres: НЕ ГОТОВ после $((SMOKE_RETRY_ATTEMPTS * SMOKE_RETRY_DELAY)) сек"
  err "  Диагностика: docker logs karaoke-db"
  return 1
}

SMOKE_FAIL=0
log "--- 1/3: контейнеры ---"
for c in karaoke-app karaoke-web karaoke-webvue3 karaoke-public searxng fourget karaoke-db karaoke-storage; do
  check_container_up "$c" || SMOKE_FAIL=1
done
if [ "$WITH_OLLAMA" -eq 1 ]; then
  check_container_up ollama || SMOKE_FAIL=1
fi

log "--- 2/3: HTTP-эндпоинты ---"
check_http_200 "http://localhost:7906" "webvue3 (admin)" || SMOKE_FAIL=1
check_http_200 "http://localhost:8888" "karaoke-public (фронт)" || SMOKE_FAIL=1
check_http_200 "http://localhost:9001/minio/health/live" "MinIO health" || SMOKE_FAIL=1
check_http_200 "http://localhost:9000/minio/health/live" "MinIO S3 API" || SMOKE_FAIL=1

log "--- 3/3: Postgres ---"
check_postgres_ready || SMOKE_FAIL=1

echo
if [ "$SMOKE_FAIL" -eq 0 ]; then
  ok "===== SMOKE-TEST PASSED ====="
  echo
  log "Доступные эндпоинты:"
  echo "  • webvue3 (админка):        http://localhost:7906"
  echo "  • karaoke-public (сайт):     http://localhost:8888"
  echo "  • MinIO Console:             http://localhost:9001"
  echo "  • Postgres:                  localhost:5432 (psql -U postgres)"
  echo
  log "Rollback (если нужно снести):"
  echo "  cd ${DEPLOY_DIR}"
  echo "  bash do.sh stop all"
  echo "  docker compose -f docker-compose-storage.yml down"
  if [ "$WITH_OLLAMA" -eq 1 ]; then
    echo "  docker compose -f docker-compose-ollama.yml down"
  fi
  echo "  # Опционально (ОСТОРОЖНО — необратимо):"
  echo "  sudo rm -rf ${STORAGE_FOLDER:-/sm-karaoke/system/Караоке-storage} ${DB_FOLDER:-/sm-karaoke/system/Караоке-db}"
  exit 0
else
  err "===== SMOKE-TEST FAILED ====="
  err "См. диагностику выше. Частые причины:"
  err "  - do.env не заполнен (нет DB_LOCAL_POSTGRES_PASSWORD, STORAGE_KEY/SECRET)"
  err "  - Postgres init занимает >15 сек на свежем volume (повторите позже)"
  err "  - MinIO требует XDG_RUNTIME_DIR=/tmp (см. quickstart.md troubleshooting)"
  err "  - порт занят (ss -tlnp | grep -E ':(5432|7906|9000|9001)')"
  exit 1
fi
