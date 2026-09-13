#!/usr/bin/env bash
# =====================================================================
# tools/check-frontend-build.sh — guard для правила NON-NEGOTIABLE
# о frontend-сборке (см. AGENTS.md § «Frontend build: cd <frontend-dir>
# && npm run <cmd> (Pass 375)»).
#
# Прецедент (Pass 375, OP #83 follow-up): правила о frontend-сборке
# были разбросаны по 30+ файлам и НЕ machine-readable. Агент мог запустить
# `npm run lint` из корня проекта, где нет `package.json` — и получить
# падение или ложный результат.
#
# Этот guard ловит **в shell-файлах** (staged/unstaged/untracked-new) попытки
# закоммитить скрипты, где `npm run <cmd>` вызывается БЕЗ `cd webvue3`/
# `cd karaoke-public` непосредственно перед ним.
#
# Использование:
#   bash tools/check-frontend-build.sh                  # guard-режим
#   bash tools/check-frontend-build.sh --quiet          # для pre-commit
#
# Что проверяет:
#   В shell-файлах (.sh/.bash + .github/workflows/*.yml) проверяет,
#   что каждая строка с `npm run <cmd>` имеет `cd webvue3` или
#   `cd karaoke-public` непосредственно перед ней (в той же строке через `&&`,
#   или в предыдущей строке через `cd`).
#
#   Markdown и ADR не проверяются (там могут быть примеры).
#   Сам guard-скрипт исключается из проверки.
#
# Exit codes:
#   0 — запрещённых паттернов не найдено
#   1 — найдены нарушения
#   2 — usage error
# =====================================================================

set -uo pipefail

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

check_frontend_patterns() {
  # Получаем список ИЗМЕНЁННЫХ файлов (staged + unstaged + untracked-new).
  local changed_files
  changed_files="$( (git diff --cached --name-only; git diff --name-only; git ls-files --others --exclude-standard) 2>/dev/null | sort -u )"

  # Фильтруем только shell-исполняемые файлы + конфиги.
  local checkable_files=()
  while IFS= read -r f; do
    if [[ -z "$f" ]]; then continue; fi
    if [[ "$f" == "tools/check-frontend-build.sh" ]]; then continue; fi
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
    # Получаем содержимое добавленных строк (+).
    local added_lines
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      added_lines="$(git diff HEAD -- "$f" 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+' || true)"
    elif [[ -f "$f" ]]; then
      added_lines="$(cat "$f" | sed 's/^/+/')"
    fi

    # Паттерн: `npm run <cmd>` без предшествующего `cd webvue3` или `cd karaoke-public`.
    # Это сложная multi-line проверка — пройдём через файл построчно.
    if [[ -z "$added_lines" ]]; then continue; fi

    # Преобразуем diff-вывод в список добавленных строк без префикса `+`.
    local plain_lines
    plain_lines="$(echo "$added_lines" | sed 's/^+//')"

    # Найти строки с `npm run` (lint, build, format:check и т.п.).
    # Для каждой такой строки проверить, что в **той же строке** или
    # **предыдущей строке** есть `cd webvue3` или `cd karaoke-public`.
    local prev_line=""
    local line_num=0
    while IFS= read -r line; do
      line_num=$((line_num + 1))
      # Игнорируем markdown и комментарии.
      if [[ "$line" =~ ^[[:space:]]*# ]]; then continue; fi

      if echo "$line" | grep -qE "\\bnpm\\s+run\\b"; then
        # Проверяем, что в этой же строке или в предыдущей есть `cd webvue3` или `cd karaoke-public`.
        local prev_ok=false
        if [[ -n "$prev_line" ]]; then
          if echo "$prev_line" | grep -qE "\\bcd\\s+(webvue3|karaoke-public)\\b"; then
            prev_ok=true
          fi
        fi

        # Также допускаем `cd webvue3 && ... npm run ...` (в одной строке).
        local same_ok=false
        if echo "$line" | grep -qE "\\bcd\\s+(webvue3|karaoke-public)\\b.*npm\\s+run\\b"; then
          same_ok=true
        fi

        # Также допускаем subshell `(cd webvue3 && npm run lint)`.
        local subshell_ok=false
        if echo "$line" | grep -qE "\\(\\s*cd\\s+(webvue3|karaoke-public).*npm\\s+run\\b"; then
          subshell_ok=true
        fi

        if [[ "$prev_ok" == false && "$same_ok" == false && "$subshell_ok" == false ]]; then
          color_red "❌ НАРУШЕНИЕ: $f строка $line_num — 'npm run' без 'cd webvue3'/'cd karaoke-public'"
          color_yellow "   Правило (Pass 375): запускай \`cd <frontend-dir> && npm run <cmd>\`"
          color_yellow "   или \`(cd webvue3 && npm run lint)\` (subshell)."
          color_yellow "   Текущая строка: $line"
          if [[ -n "$prev_line" ]]; then
            color_yellow "   Предыдущая строка: $prev_line"
          fi
          violation_count=$((violation_count + 1))
          violation_files="${violation_files} ${f}:${line_num}"
        fi
      fi
      prev_line="$line"
    done <<< "$plain_lines"
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ Все вызовы npm run имеют правильный cd webvue3/cd karaoke-public"
    return 0
  else
    color_red "Найдено нарушений: $violation_count"
    color_yellow "См. AGENTS.md § «Frontend build: cd <frontend-dir> && npm run <cmd> (Pass 375)»."
    return 1
  fi
}

case "${1:-}" in
  --help|-h|"")
    check_frontend_patterns
    exit $?
    ;;
  --quiet)
    check_frontend_patterns >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-frontend-build.sh [--quiet]"
    exit 2
    ;;
esac