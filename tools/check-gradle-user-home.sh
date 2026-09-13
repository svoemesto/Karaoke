#!/usr/bin/env bash
# =====================================================================
# tools/check-gradle-user-home.sh — guard для правила NON-NEGOTIABLE
# о $GRADLE_USER_HOME (см. AGENTS.md § «Gradle: запуск с GRADLE_USER_HOME»).
#
# Прецедент (Pass 372, OP #83): агент помнит правило для прямого вызова
# ./gradlew, но забывает применить к pre-commit и deploy/do.sh (которые
# внутри сами вызывают ./gradlew). Этот guard ловит оба случая:
#   - прямая команда ./gradlew: проверка через exec-обёртку;
#   - pre-commit / do.sh: проверка перед запуском, чтобы hook упал с понятной ошибкой.
#
# Использование:
#   bash tools/check-gradle-user-home.sh                  # guard-режим: exit 1 если правило нарушено
#   bash tools/check-gradle-user-home.sh --source-direct  # для pre-commit: выводит ОК или FAIL
#
# Что проверяет:
#   1) Если $GRADLE_USER_HOME задан → должен = /home/nsa/Karaoke/.gradle
#      (или writable-путь ВНУТРИ проекта, чтобы избежать read-only /home/nsa/.gradle).
#   2) Если $GRADLE_USER_HOME НЕ задан → проектная /home/nsa/Karaoke/.gradle
#      должна существовать и быть writable (иначе wrapper упадёт).
#   3) Если $JAVA_HOME не задан → warning (не failure), т.к. JAVA_HOME иногда
#      задаётся через /etc/profile.d (не всегда нужен явный export).
#
# Exit codes:
#   0 — правило соблюдено (или проектная .gradle writable)
#   1 — правило нарушено (read-only FS → ./gradlew упадёт)
#   2 — usage error
# =====================================================================

set -uo pipefail

EXPECTED_GRADLE_USER_HOME="/home/nsa/Karaoke/.gradle"
PROJECT_GRADLE_DIR="$EXPECTED_GRADLE_USER_HOME"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

check_grade_user_home() {
  local status=0
  local gradle_user_home="${GRADLE_USER_HOME:-}"

  if [[ -n "$gradle_user_home" ]]; then
    # Случай 1: переменная задана.
    if [[ "$gradle_user_home" == "$EXPECTED_GRADLE_USER_HOME" ]]; then
      color_green "✅ GRADLE_USER_HOME=$gradle_user_home (ожидаемый)"
    elif [[ "$gradle_user_home" == "/home/nsa/.gradle" ]]; then
      color_red "❌ GRADLE_USER_HOME=$gradle_user_home — ЭТО READ-ONLY В SANDBOX DSH"
      color_red "   wrapper попытается писать в $gradle_user_home/wrapper/dists/... → FileNotFoundException"
      color_red "   ОБЯЗАТЕЛЬНО установи: export GRADLE_USER_HOME=$EXPECTED_GRADLE_USER_HOME"
      status=1
    else
      # Другой путь — допустим, если он writable.
      if [[ -d "$gradle_user_home" && -w "$gradle_user_home" ]]; then
        color_yellow "⚠️  GRADLE_USER_HOME=$gradle_user_home (нестандартный, но writable)"
      else
        color_red "❌ GRADLE_USER_HOME=$gradle_user_home — не существует или read-only"
        status=1
      fi
    fi
  else
    # Случай 2: переменная НЕ задана — проверяем fallback (проектная .gradle).
    if [[ -d "$PROJECT_GRADLE_DIR" && -w "$PROJECT_GRADLE_DIR" ]]; then
      color_yellow "⚠️  GRADLE_USER_HOME не задан, но проектная $PROJECT_GRADLE_DIR существует и writable"
      color_yellow "   wrapper может НЕ найти её без export — лучше установить явно"
    else
      color_red "❌ GRADLE_USER_HOME не задан И проектная $PROJECT_GRADLE_DIR не существует/writable"
      color_red "   ОБЯЗАТЕЛЬНО установи: export GRADLE_USER_HOME=$EXPECTED_GRADLE_USER_HOME"
      status=1
    fi
  fi

  if [[ -z "${JAVA_HOME:-}" ]]; then
    color_yellow "⚠️  JAVA_HOME не задан — обычно задаётся через /etc/profile.d (это OK)"
  fi

  return $status
}

case "${1:-}" in
  --help|-h|"")
    check_grade_user_home
    exit $?
    ;;
  --quiet)
    check_grade_user_home >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-gradle-user-home.sh [--quiet]"
    exit 2
    ;;
esac