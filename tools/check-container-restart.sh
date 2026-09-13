#!/usr/bin/env bash
# =====================================================================
# tools/check-container-restart.sh — guard для правила NON-NEGOTIABLE
# о перезапуске контейнеров (см. AGENTS.md § «Перезапуск контейнеров
# через deploy/do.sh (Pass 374)»).
#
# Прецедент (Pass 374, OP #83 follow-up): правила о перезапуске были
# разбросаны по 3 местам и не machine-readable. Агент мог случайно
# использовать прямые `docker restart` / `docker stop` + `docker start`
# для контейнеров, которые требуют согласия (karaoke-app).
#
# Этот guard ловит **в git diff HEAD** попытки закоммитить прямые вызовы
# docker restart/stop/start контейнеров Karaoke.
#
# Использование:
#   bash tools/check-container-restart.sh                  # guard-режим
#   bash tools/check-container-restart.sh --quiet          # для pre-commit
#
# Что проверяет:
#   В git diff HEAD (staged + unstaged) ищет запрещённые паттерны:
#     - `docker restart <container>` где container в KARAOKE_CONTAINERS
#     - `docker stop <container>` для контейнеров, требующих consent
#     - `docker-compose ... restart ...` (минует do.sh)
#   Игнорирует строки внутри markdown (blockquotes `>` и codeblocks).
#
# Exit codes:
#   0 — запрещённых паттернов не найдено
#   1 — найдены нарушения
#   2 — usage error
# =====================================================================

set -uo pipefail

# Список контейнеров Karaoke.
KARAOKE_CONTAINERS=(
  "karaoke-db"
  "karaoke-web"
  "karaoke-webvue"
  "karaoke-webvue3"
  "karaoke-public"
  "karaoke-app"
  "karaoke-minio"
  "karaoke-minio-proxy"
  "karaoke-storage"
  "karaoke-telegram-proxy"
  "karaoke-nginx"
  "karaoke-db-local"
  "nginx"
)

# Контейнеры, требующие явного согласия (Pass 282):
CONSENT_REQUIRED=(
  "karaoke-app"
  "minio"
  "nginx"
  "karaoke-nginx"
)

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

check_restart_patterns() {
  # Получаем список ИЗМЕНЁННЫХ файлов (staged + unstaged + untracked-new).
  # Проверяем ТОЛЬКО shell-исполняемые файлы (.sh/.bash/.zsh + shell-блоки в
  # build/ci конфигах). Markdown (.md) и ADR-файлы НЕ проверяем — там могут
  # быть примеры запрещённых команд в документации.
  local changed_files
  changed_files="$( (git diff --cached --name-only; git diff --name-only; git ls-files --others --exclude-standard) 2>/dev/null | sort -u )"

  # Фильтруем только .sh/.bash файлы + конфиги (deploy/do.sh, Jenkinsfile, .github/workflows/*.yml).
  # Исключаем сам guard-скрипт (он по определению содержит примеры нарушений в комментариях/error messages).
  local checkable_files=()
  while IFS= read -r f; do
    if [[ -z "$f" ]]; then continue; fi
    if [[ "$f" == "tools/check-container-restart.sh" ]]; then continue; fi
    if [[ "$f" =~ \.(sh|bash|zsh)$ ]] || [[ "$f" =~ ^deploy/do\.sh$ ]] || [[ "$f" =~ ^\.github/workflows/.+\.yml$ ]]; then
      checkable_files+=("$f")
    fi
  done <<< "$changed_files"

  if [[ ${#checkable_files[@]} -eq 0 ]]; then
    color_green "✅ Нет shell-файлов в изменении (markdown и ADR не проверяются)"
    return 0
  fi

  local violation_count=0
  local violation_files=""

  for f in "${checkable_files[@]}"; do
    # Получаем содержимое добавленных строк (`+` в начале).
    local added_lines
    if [[ -f "$f" ]] && git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      added_lines="$(git diff HEAD -- "$f" 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+' || true)"
    elif [[ -f "$f" ]]; then
      # Untracked файл — проверяем всё содержимое как "+".
      added_lines="$(cat "$f" | sed 's/^/+/')"
    fi

    for container in "${KARAOKE_CONTAINERS[@]}"; do
      # Паттерн 1: `docker restart <container>`.
      if echo "$added_lines" | grep -qE "^\\+.*\\bdocker\\s+restart\\s+${container}\\b" 2>/dev/null; then
        color_red "❌ НАРУШЕНИЕ: $f содержит 'docker restart $container'"
        color_yellow "   Правило (Pass 374): используй bash deploy/do.sh restart_* (см. AGENTS.md § Перезапуск контейнеров)"
        violation_count=$((violation_count + 1))
        violation_files="${violation_files} ${container}"
      fi

      # Паттерн 2: `docker stop <container>` для контейнеров, требующих согласия.
      if [[ " ${CONSENT_REQUIRED[@]} " =~ " ${container} " ]]; then
        if echo "$added_lines" | grep -qE "^\\+.*\\bdocker\\s+stop\\s+${container}\\b" 2>/dev/null; then
          color_red "❌ НАРУШЕНИЕ: $f содержит 'docker stop $container'"
          color_yellow "   Правило (Pass 282): требуется явное согласие пользователя для $container"
          violation_count=$((violation_count + 1))
          violation_files="${violation_files} ${container}"
        fi
      fi

      # Паттерн 3: `docker-compose ... restart ...` (минует do.sh).
      if echo "$added_lines" | grep -qE "^\\+.*docker-compose.*\\brestart\\b" 2>/dev/null; then
        color_red "❌ НАРУШЕНИЕ: $f содержит 'docker-compose ... restart'"
        color_yellow "   Правило (Pass 374): используй bash deploy/do.sh restart_* вместо прямого docker-compose"
        violation_count=$((violation_count + 1))
        violation_files="${violation_files} docker-compose"
      fi
    done
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ Запрещённых паттернов перезапуска не найдено в shell-файлах"
    return 0
  else
    color_red "Найдено нарушений: $violation_count (контейнеры:$violation_files)"
    color_yellow "См. AGENTS.md § «Перезапуск контейнеров через deploy/do.sh (Pass 374)»."
    return 1
  fi
}

case "${1:-}" in
  --help|-h|"")
    check_restart_patterns
    exit $?
    ;;
  --quiet)
    check_restart_patterns >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-container-restart.sh [--quiet]"
    exit 2
    ;;
esac