#!/usr/bin/env bash
# check-sandbox-ready.sh — pre-flight для DSH sandbox (workspace-write).
#
# Что делает:
#   1. Probe read-only границ (gradle wrapper, docker buildx, npm cache, kotlin).
#   2. Создаёт workspace-аналоги для всего, что writable внутри проекта.
#   3. Печатает итоговую таблицу и export'ы переменных для текущей сессии.
#
# Использование:
#   source tools/check-sandbox-ready.sh    # для export переменных
#   bash   tools/check-sandbox-ready.sh    # только проверка + создание папок
#
# Exit codes:
#   0 — всё OK, рецепты применены, переменные экспортированы.
#   1 — найдена блокировка без workspace-обхода (например, SSH к прод).
#   2 — ошибка probe (FS anomaly).
#
# Зависимости: bash, coreutils (stat/mkdir), docker CLI (опционально).

set -euo pipefail

# Цвета для вывода (если tty)
if [[ -t 1 ]]; then
  RED=$'\033[0;31m'
  GREEN=$'\033[0;32m'
  YELLOW=$'\033[0;33m'
  BLUE=$'\033[0;34m'
  BOLD=$'\033[1m'
  RESET=$'\033[0m'
else
  RED="" GREEN="" YELLOW="" BLUE="" BOLD="" RESET=""
fi

# Корень проекта (где лежит tools/) — определяем по расположению скрипта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly SCRIPT_DIR PROJECT_ROOT

# === 1. PROBE READ-ONLY ГРАНИЦ ===

# Возвращает 0, если путь НЕ writable (read-only или нет).
probe_readonly() {
  local path="$1"
  if [[ ! -e "$path" ]]; then
    # Путь не существует — это не блокировка, а отсутствие; пусть caller решает.
    return 1
  fi
  local probe
  probe="$(mktemp -p "$path" .sandbox-probe.XXXXXX 2>/dev/null || true)"
  if [[ -n "$probe" && -e "$probe" ]]; then
    rm -f "$probe"
    return 1  # writable
  fi
  return 0  # read-only
}

# Печатает блок «check: <name>»
report() {
  local status="$1"; shift
  local name="$1"; shift
  local msg="$1"; shift
  case "$status" in
    OK)     printf "  ${GREEN}[OK]${RESET}     %-30s %s\n" "$name" "$msg" ;;
    FIXED)  printf "  ${GREEN}[FIX]${RESET}    %-30s %s\n" "$name" "$msg" ;;
    WARN)   printf "  ${YELLOW}[WARN]${RESET}   %-30s %s\n" "$name" "$msg" ;;
    FAIL)   printf "  ${RED}[FAIL]${RESET}   %-30s %s\n" "$name" "$msg" ;;
    SKIP)   printf "  ${BLUE}[SKIP]${RESET}   %-30s %s\n" "$name" "$msg" ;;
  esac
}

printf "${BOLD}=== DSH sandbox pre-flight ===${RESET}\n"
printf "Project: %s\n\n" "$PROJECT_ROOT"

readonly_failures=0
readonly_fixed=0

# 1a. ~/.gradle/wrapper/dists → workspace-аналог Karaoke/.gradle
if probe_readonly "$HOME/.gradle/wrapper/dists"; then
  if [[ -w "$PROJECT_ROOT/.gradle/wrapper/dists" ]] || mkdir -p "$PROJECT_ROOT/.gradle/wrapper/dists" 2>/dev/null; then
    report FIXED "gradle wrapper cache" "→ GRADLE_USER_HOME=$PROJECT_ROOT/.gradle"
    readonly_fixed=$((readonly_fixed + 1))
  else
    report FAIL "gradle wrapper cache" "cannot create $PROJECT_ROOT/.gradle/wrapper/dists"
    readonly_failures=$((readonly_failures + 1))
  fi
else
  report OK "gradle wrapper cache" "host $HOME/.gradle writable (no workaround needed)"
fi

# 1b. Karaoke/.gradle основные подпапки
mkdir -p "$PROJECT_ROOT/.gradle/caches" "$PROJECT_ROOT/.gradle/notifications" \
         "$PROJECT_ROOT/.gradle/daemon" "$PROJECT_ROOT/.gradle/native" \
         "$PROJECT_ROOT/.gradle/kotlin" 2>/dev/null || true
report OK "gradle caches (workspace)" "$PROJECT_ROOT/.gradle/{caches,daemon,native,notifications}"

# 2. ~/.docker → workspace-аналог Karaoke/.docker
if probe_readonly "$HOME/.docker"; then
  mkdir -p "$PROJECT_ROOT/.docker/buildx/activity" 2>/dev/null || true
  if [[ -w "$PROJECT_ROOT/.docker/buildx/activity" ]]; then
    report FIXED "docker buildx activity" "→ docker --config=$PROJECT_ROOT/.docker"
    readonly_fixed=$((readonly_fixed + 1))
  else
    report FAIL "docker buildx activity" "cannot create $PROJECT_ROOT/.docker/buildx/activity"
    readonly_failures=$((readonly_failures + 1))
  fi
else
  report OK "docker config" "host $HOME/.docker writable (no workaround needed)"
fi

# 3. ~/.npm + ~/.cache → workspace-аналог node_modules/.cache
if probe_readonly "$HOME/.npm"; then
  mkdir -p "$PROJECT_ROOT/webvue3/node_modules/.cache" \
           "$PROJECT_ROOT/karaoke-public/node_modules/.cache" 2>/dev/null || true
  report FIXED "npm cache (host)" "→ uses project node_modules/.cache (no host cache writes)"
  readonly_fixed=$((readonly_fixed + 1))
else
  report OK "npm cache (host)" "$HOME/.npm writable"
fi

if probe_readonly "$HOME/.cache"; then
  # prettier/eslint пишут в $HOME/.cache; проверим, что node_modules/.cache компенсирует
  report WARN "prettier/eslint cache" "$HOME/.cache read-only — ensure tools use --cache $PROJECT_ROOT/<sub>/node_modules/.cache"
else
  report OK "prettier/eslint cache" "$HOME/.cache writable"
fi

# 4. ~/.kotlin → если read-only, проекту это ОК (Kotlin Gradle plugin использует workspace-кеш)
if probe_readonly "$HOME/.kotlin"; then
  report OK "kotlin daemon cache" "project uses $PROJECT_ROOT/.gradle/kotlin (workspace-local)"
else
  report OK "kotlin daemon cache" "$HOME/.kotlin writable"
fi

# 5. SSH к прод — нет обхода через workspace; это user-approved операция
if [[ -f "$HOME/.ssh/id_rsa" ]] || [[ -f "$HOME/.ssh/id_ed25519" ]]; then
  report SKIP "SSH to prod" "keys present — sandbox-friendly only via direct user approval (Constitution § Ограничения)"
else
  report SKIP "SSH to prod" "no keys in $HOME/.ssh — prod unreachable (expected)"
fi

# 6. Docker daemon — обязательно проверить
if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    report OK "docker daemon" "reachable via /var/run/docker.sock"
  else
    report FAIL "docker daemon" "docker installed but daemon unreachable"
    readonly_failures=$((readonly_failures + 1))
  fi
else
  report FAIL "docker CLI" "docker not in PATH"
  readonly_failures=$((readonly_failures + 1))
fi

# === 2. ИТОГ И ЭКСПОРТ ПЕРЕМЕННЫХ ===

echo
if [[ $readonly_failures -eq 0 ]]; then
  printf "${GREEN}${BOLD}All sandbox boundaries have workspace workarounds.${RESET}\n"
  printf "\n${BOLD}Copy-paste recipes${RESET} (use these prefixes for every command in this session):\n\n"
  printf "  ${BOLD}# Gradle (compile / ktlint / bootJar):${RESET}\n"
  printf "  GRADLE_USER_HOME=%s/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel\n\n" "$PROJECT_ROOT"
  printf "  ${BOLD}# Docker (build / ps / restart / logs):${RESET}\n"
  printf "  docker --config=%s/.docker build ...\n" "$PROJECT_ROOT"
  printf "  docker --config=%s/.docker restart <container>\n\n" "$PROJECT_ROOT"
  printf "  ${BOLD}# npm / pnpm / vite (lint / build):${RESET}\n"
  printf "  cd webvue3 && npm run lint:check && npx prettier --check 'src/**/*.{vue,js,ts,json}'\n"
  printf "  cd webvue3 && npm run build\n\n"
  printf "  ${BOLD}Source mode${RESET} (sets env for current shell):\n"
  printf "  source %s/check-sandbox-ready.sh\n\n" "$SCRIPT_DIR"

  # Если скрипт source'ится — экспортируем переменные.
  # Надёжный детектор source: сравниваем $0 с ${BASH_SOURCE[0]} — при source они различаются.
  _sourced="no"
  if [[ "${BASH_SOURCE[0]:-}" != "${0}" && -n "${BASH_SOURCE[0]:-}" ]]; then
    _sourced="yes"
  fi
  if [[ "$_sourced" == "yes" ]]; then
    export GRADLE_USER_HOME="$PROJECT_ROOT/.gradle"
    export DOCKER_CONFIG="$PROJECT_ROOT/.docker"
    export BUILDX_CONFIG="$PROJECT_ROOT/.docker/buildx"
    # wrapper-функция на docker — экспортируемая, работает в subshell
    docker() { command docker --config="$DOCKER_CONFIG" "$@"; }
    export -f docker
    printf "${GREEN}${BOLD}Env exported. Ready.${RESET}\n"
  fi
  exit 0
else
  printf "${RED}${BOLD}FAIL: %d boundary without workaround. See [FAIL] lines above.${RESET}\n" "$readonly_failures"
  exit 1
fi