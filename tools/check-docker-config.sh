#!/usr/bin/env bash
# =====================================================================
# tools/check-docker-config.sh — guard для правила NON-NEGOTIABLE
# о $DOCKER_CONFIG (см. AGENTS.md § «Docker build: DOCKER_CONFIG»).
#
# Прецедент (Pass 373, OP #83 follow-up): docker build в DSH-sandbox падает
# с read-only FileNotFoundException в /home/nsa/.docker/buildx/activity/
# потому что системная ~/.docker read-only. Решение — проектная
# <repo-root>/.docker/ writable + переменная DOCKER_CONFIG.
#
# Использование:
#   bash tools/check-docker-config.sh                  # guard-режим: exit 1 если правило нарушено
#   bash tools/check-docker-config.sh --quiet          # для pre-commit: только exit code
#
# Что проверяет:
#   1) Если $DOCKER_CONFIG задан → должен = проектный <repo-root>/.docker
#      (или writable-путь ВНУТРИ проекта, чтобы избежать read-only ~/.docker).
#   2) Если $DOCKER_CONFIG НЕ задан → проектный <repo-root>/.docker
#      должен существовать и быть writable (иначе docker build упадёт).
#
# BR-581: ожидаемый путь НЕ зашит как /home/nsa/Karaoke/.docker, а выводится
# из корня репозитория — на второй машине матрицы AGENTS.md (dev-pc/dev)
# такого каталога нет, и pre-commit-хук `docker-config-guard` падал на
# каждом коммите. Переопределение — переменная окружения
# KARAOKE_DOCKER_CONFIG (по умолчанию — <main-repo-root>/.docker).
#
# Exit codes:
#   0 — правило соблюдено (или проектная .docker writable)
#   1 — правило нарушено (read-only FS → docker build упадёт)
#   2 — usage error
# =====================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Основной корень репозитория (общий для всех worktree'ов):
# git-common-dir → /home/nsa/Karaoke/.git → родитель = /home/nsa/Karaoke.
# Для обычного checkout'а он совпадает с REPO_ROOT.
MAIN_ROOT="$REPO_ROOT"
_common_dir="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
if [[ -n "$_common_dir" && -d "$_common_dir" ]]; then
  MAIN_ROOT="$(dirname "$_common_dir")"
fi

# Ожидаемый проектный каталог docker-конфига. Переопределение —
# KARAOKE_DOCKER_CONFIG; по умолчанию — общий кэш главного checkout'а.
EXPECTED_DOCKER_CONFIG="${KARAOKE_DOCKER_CONFIG:-$MAIN_ROOT/.docker}"
PROJECT_DOCKER_DIR="$EXPECTED_DOCKER_CONFIG"

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

# Ожидаемый путь = проектный каталог (общий или локальный для worktree'а).
is_expected_path() {
  local candidate="$1"
  [[ "$candidate" == "$EXPECTED_DOCKER_CONFIG" ]] && return 0
  [[ "$candidate" == "$REPO_ROOT/.docker" ]] && return 0
  return 1
}

check_docker_config() {
  local status=0
  local docker_config="${DOCKER_CONFIG:-}"

  if [[ -n "$docker_config" ]]; then
    # Случай 1: переменная задана.
    if is_expected_path "$docker_config"; then
      color_green "✅ DOCKER_CONFIG=$docker_config (ожидаемый)"
    elif [[ "$docker_config" == "$HOME/.docker" ]]; then
      if [[ -d "$docker_config" && -w "$docker_config" ]]; then
        color_yellow "⚠️  DOCKER_CONFIG=$docker_config (системный путь; в DSH-sandbox он read-only)"
        color_yellow "   Рекомендуется: export DOCKER_CONFIG=$EXPECTED_DOCKER_CONFIG"
      else
        color_red "❌ DOCKER_CONFIG=$docker_config — ЭТО READ-ONLY В SANDBOX DSH"
        color_red "   buildx попытается писать в $docker_config/buildx/activity/ → FileNotFoundException"
        color_red "   ОБЯЗАТЕЛЬНО установи: export DOCKER_CONFIG=$EXPECTED_DOCKER_CONFIG"
        status=1
      fi
    else
      # Другой путь — допустим, если он writable.
      if [[ -d "$docker_config" && -w "$docker_config" ]]; then
        color_yellow "⚠️  DOCKER_CONFIG=$docker_config (нестандартный, но writable)"
      else
        color_red "❌ DOCKER_CONFIG=$docker_config — не существует или read-only"
        status=1
      fi
    fi
  else
    # Случай 2: переменная НЕ задана — проверяем fallback (проектная .docker).
    local fallback=""
    for candidate in "$EXPECTED_DOCKER_CONFIG" "$REPO_ROOT/.docker"; do
      if [[ -d "$candidate" && -w "$candidate" ]]; then
        fallback="$candidate"
        break
      fi
    done
    if [[ -n "$fallback" ]]; then
      color_yellow "⚠️  DOCKER_CONFIG не задан, но проектная $fallback существует и writable"
      color_yellow "   docker-cli может НЕ найти её без export — лучше установить явно"
    else
      color_red "❌ DOCKER_CONFIG не задан И проектная $EXPECTED_DOCKER_CONFIG не существует/writable"
      color_red "   ОБЯЗАТЕЛЬНО установи: export DOCKER_CONFIG=$EXPECTED_DOCKER_CONFIG"
      status=1
    fi
  fi

  return $status
}

case "${1:-}" in
  --help|-h|"")
    check_docker_config
    exit $?
    ;;
  --quiet)
    check_docker_config >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-docker-config.sh [--quiet]"
    exit 2
    ;;
esac
