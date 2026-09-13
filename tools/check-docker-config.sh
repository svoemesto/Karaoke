#!/usr/bin/env bash
# =====================================================================
# tools/check-docker-config.sh — guard для правила NON-NEGOTIABLE
# о $DOCKER_CONFIG (см. AGENTS.md § «Docker build: DOCKER_CONFIG»).
#
# Прецедент (Pass 373, OP #83 follow-up): docker build в DSH-sandbox падает
# с read-only FileNotFoundException в /home/nsa/.docker/buildx/activity/
# потому что системная ~/.docker read-only. Решение — проектная
# /home/nsa/Karaoke/.docker/ writable + переменная DOCKER_CONFIG.
#
# Использование:
#   bash tools/check-docker-config.sh                  # guard-режим: exit 1 если правило нарушено
#   bash tools/check-docker-config.sh --quiet          # для pre-commit: только exit code
#
# Что проверяет:
#   1) Если $DOCKER_CONFIG задан → должен = /home/nsa/Karaoke/.docker
#      (или writable-путь ВНУТРИ проекта, чтобы избежать read-only /home/nsa/.docker).
#   2) Если $DOCKER_CONFIG НЕ задан → проектная /home/nsa/Karaoke/.docker
#      должна существовать и быть writable (иначе docker build упадёт).
#
# Exit codes:
#   0 — правило соблюдено (или проектная .docker writable)
#   1 — правило нарушено (read-only FS → docker build упадёт)
#   2 — usage error
# =====================================================================

set -uo pipefail

EXPECTED_DOCKER_CONFIG="/home/nsa/Karaoke/.docker"
PROJECT_DOCKER_DIR="$EXPECTED_DOCKER_CONFIG"

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

check_docker_config() {
  local status=0
  local docker_config="${DOCKER_CONFIG:-}"

  if [[ -n "$docker_config" ]]; then
    # Случай 1: переменная задана.
    if [[ "$docker_config" == "$EXPECTED_DOCKER_CONFIG" ]]; then
      color_green "✅ DOCKER_CONFIG=$docker_config (ожидаемый)"
    elif [[ "$docker_config" == "$HOME/.docker" ]] || [[ "$docker_config" == "/home/nsa/.docker" ]]; then
      color_red "❌ DOCKER_CONFIG=$docker_config — ЭТО READ-ONLY В SANDBOX DSH"
      color_red "   buildx попытается писать в $docker_config/buildx/activity/ → FileNotFoundException"
      color_red "   ОБЯЗАТЕЛЬНО установи: export DOCKER_CONFIG=$EXPECTED_DOCKER_CONFIG"
      status=1
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
    if [[ -d "$PROJECT_DOCKER_DIR" && -w "$PROJECT_DOCKER_DIR" ]]; then
      color_yellow "⚠️  DOCKER_CONFIG не задан, но проектная $PROJECT_DOCKER_DIR существует и writable"
      color_yellow "   docker-cli может НЕ найти её без export — лучше установить явно"
    else
      color_red "❌ DOCKER_CONFIG не задан И проектная $PROJECT_DOCKER_DIR не существует/writable"
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