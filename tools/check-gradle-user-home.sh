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
#   bash tools/check-gradle-user-home.sh --quiet          # для pre-commit: только exit code
#
# Что проверяет:
#   1) Если $GRADLE_USER_HOME задан → должен = проектный <repo-root>/.gradle
#      (или writable-путь ВНУТРИ проекта, чтобы избежать read-only ~/.gradle).
#   2) Если $GRADLE_USER_HOME НЕ задан → проектный <repo-root>/.gradle
#      должен существовать и быть writable (иначе wrapper упадёт).
#   3) Если $JAVA_HOME не задан → warning (не failure), т.к. JAVA_HOME иногда
#      задаётся через /etc/profile.d (не всегда нужен явный export).
#
# BR-581: ожидаемый путь НЕ зашит как /home/nsa/Karaoke/.gradle, а выводится
# из корня репозитория — на второй машине матрицы AGENTS.md (dev-pc/dev)
# такого каталога нет, и pre-commit-хук `gradle-user-home-guard` падал на
# каждом коммите. Переопределение — переменная окружения
# KARAOKE_GRADLE_USER_HOME (по умолчанию — <main-repo-root>/.gradle).
#
# Exit codes:
#   0 — правило соблюдено (или проектная .gradle writable)
#   1 — правило нарушено (read-only FS → ./gradlew упадёт)
#   2 — usage error
# =====================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Основной корень репозитория (общий для всех worktree'ов) — см.
# check-docker-config.sh (BR-581).
MAIN_ROOT="$REPO_ROOT"
_common_dir="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
if [[ -n "$_common_dir" && -d "$_common_dir" ]]; then
  MAIN_ROOT="$(dirname "$_common_dir")"
fi

EXPECTED_GRADLE_USER_HOME="${KARAOKE_GRADLE_USER_HOME:-$MAIN_ROOT/.gradle}"
PROJECT_GRADLE_DIR="$EXPECTED_GRADLE_USER_HOME"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

is_expected_path() {
  local candidate="$1"
  [[ "$candidate" == "$EXPECTED_GRADLE_USER_HOME" ]] && return 0
  [[ "$candidate" == "$REPO_ROOT/.gradle" ]] && return 0
  return 1
}

check_grade_user_home() {
  local status=0
  local gradle_user_home="${GRADLE_USER_HOME:-}"

  if [[ -n "$gradle_user_home" ]]; then
    # Случай 1: переменная задана.
    if is_expected_path "$gradle_user_home"; then
      color_green "✅ GRADLE_USER_HOME=$gradle_user_home (ожидаемый)"
    elif [[ "$gradle_user_home" == "$HOME/.gradle" ]]; then
      if [[ -d "$gradle_user_home" && -w "$gradle_user_home" ]]; then
        color_yellow "⚠️  GRADLE_USER_HOME=$gradle_user_home (системный путь; в DSH-sandbox он read-only)"
        color_yellow "   Рекомендуется: export GRADLE_USER_HOME=$EXPECTED_GRADLE_USER_HOME"
      else
        color_red "❌ GRADLE_USER_HOME=$gradle_user_home — ЭТО READ-ONLY В SANDBOX DSH"
        color_red "   wrapper попытается писать в $gradle_user_home/wrapper/dists/... → FileNotFoundException"
        color_red "   ОБЯЗАТЕЛЬНО установи: export GRADLE_USER_HOME=$EXPECTED_GRADLE_USER_HOME"
        status=1
      fi
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
    local fallback=""
    for candidate in "$EXPECTED_GRADLE_USER_HOME" "$REPO_ROOT/.gradle"; do
      if [[ -d "$candidate" && -w "$candidate" ]]; then
        fallback="$candidate"
        break
      fi
    done
    if [[ -n "$fallback" ]]; then
      color_yellow "⚠️  GRADLE_USER_HOME не задан, но проектная $fallback существует и writable"
      color_yellow "   wrapper может НЕ найти её без export — лучше установить явно"
    else
      color_red "❌ GRADLE_USER_HOME не задан И проектная $EXPECTED_GRADLE_USER_HOME не существует/writable"
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
