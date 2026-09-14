#!/usr/bin/env bash
# =====================================================================
# tools/check-implementation-against-grilling.sh — guard для правила
# «implementation не должен отступать от grilling-решений без явного
# согласования» (Pass 374 governance amendment).
#
# Прецедент (Pass 374): в Pass 97 (grilling) был выбран SSE, а в Pass 98
# (implementation) был сделан polling со словами «проще для v1». Это нарушило
# принцип wayfinder «решения — пользователя, не агента». Guard ловит
# подобные случаи автоматически.
#
# Использование:
#   bash tools/check-implementation-against-grilling.sh                  # guard-режим
#   bash tools/check-implementation-against-grilling.sh --quiet          # для pre-commit
#
# Что делает:
#   1. Парсит все *-grilling-resolution.md файлы в specs/.
#   2. Извлекает паттерны запрета из секции ответов (например, «НЕ polling» → «polling»).
#   3. Проверяет shell-файлы в git diff HEAD и untracked-new на наличие
#      запрещённых паттернов.
#   4. Если найдено — exit 1 (FAIL).
#
# Exit codes:
#   0 — нет нарушений
#   1 — найдены нарушения
#   2 — usage error
# =====================================================================

set -uo pipefail

# Файлы, исключённые из проверки (чтобы guard не ловил сам себя).
EXCLUDED_FILES=(
  "tools/check-implementation-against-grilling.sh"
)

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

# Извлечь «запрещённое слово» из паттерна «НЕ X» или «NOT X».
# Пример: «НЕ polling» → «polling», «NOT useLocalStorage» → «useLocalStorage».
extract_prohibited() {
  echo "$1" | sed -E 's/^(НЕ|NOT)\s+//' | tr '[:upper:]' '[:lower:]'
}

check_implementation() {
  # Найти все *-grilling-resolution.md файлы в specs/.
  local grill_files
  grill_files="$(find specs -name "*-grilling-resolution.md" -type f 2>/dev/null)"

  if [[ -z "$grill_files" ]]; then
    color_yellow "⚠️  Нет *-grilling-resolution.md файлов — guard no-op"
    return 0
  fi

  # Карта: prohibited_word → source_files (где встречается).
  declare -A prohibited_to_sources
  while IFS= read -r gf; do
    # Извлекаем содержимое секции ответов: всё что после `**Ответ**:` в файле.
    local answer_section
    answer_section="$(awk '/^### Q/{flag=0} /^\*\*Ответ\*\*:/{flag=1; next} flag' "$gf" 2>/dev/null)"

    # Ищем паттерны "НЕ\s+(\w+)" или "NOT\s+(\w+)" в секции ответов.
    local patterns_in_file
    patterns_in_file="$(echo "$answer_section" | grep -oE '\b(НЕ|NOT)\s+[a-zA-Z]+' 2>/dev/null | sort -u)"
    if [[ -n "$patterns_in_file" ]]; then
      while IFS= read -r p; do
        [[ -z "$p" ]] && continue
        local word
        word="$(extract_prohibited "$p")"
        [[ -z "$word" ]] && continue
        prohibited_to_sources["$word"]="${prohibited_to_sources[$word]:-} $gf"
      done <<< "$patterns_in_file"
    fi
  done <<< "$grill_files"

  if [[ ${#prohibited_to_sources[@]} -eq 0 ]]; then
    color_green "✅ Нет запрещённых паттернов в grilling-резолюциях"
    return 0
  fi

  # Получить список shell-файлов: staged + unstaged + untracked-new.
  local changed_files
  changed_files="$( (
    git diff --cached --name-only --diff-filter=ACM
    git diff --name-only --diff-filter=ACM
    git ls-files --others --exclude-standard
  ) 2>/dev/null | sort -u )"

  local checkable_files=()
  while IFS= read -r f; do
    if [[ -z "$f" ]]; then continue; fi
    local skip=false
    for ex in "${EXCLUDED_FILES[@]}"; do
      if [[ "$f" == "$ex" ]]; then skip=true; break; fi
    done
    if [[ "$skip" == true ]]; then continue; fi
    # Проверяем только shell-файлы + deploy/do.sh + .github/workflows.
    if [[ "$f" =~ \.(sh|bash|zsh)$ ]] || [[ "$f" =~ ^deploy/do\.sh$ ]] || [[ "$f" =~ ^\.github/workflows/.+\.yml$ ]]; then
      checkable_files+=("$f")
    fi
  done <<< "$changed_files"

  if [[ ${#checkable_files[@]} -eq 0 ]]; then
    color_green "✅ Нет shell-файлов в изменении (markdown и ADR не проверяются)"
    return 0
  fi

  local violation_count=0
  local violation_details=""

  for f in "${checkable_files[@]}"; do
    local added_lines
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      # Tracked файл — берём только добавленные строки (начинающиеся с +).
      added_lines="$(git diff HEAD -- "$f" 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+' | sed 's/^+//' || true)"
    elif [[ -f "$f" ]]; then
      # Untracked файл — берём всё содержимое (это «новые» строки).
      added_lines="$(cat "$f" 2>/dev/null || true)"
    fi
    [[ -z "$added_lines" ]] && continue

    # Проверяем каждый запрещённый паттерн (case-insensitive).
    for word in "${!prohibited_to_sources[@]}"; do
      # Используем grep -iF для фиксированной строки (case-insensitive).
      local matches
      matches="$(echo "$added_lines" | grep -niF -- "$word" 2>/dev/null | grep -vE '^\s*#' | grep -vE '^\s*//' || true)"
      if [[ -n "$matches" ]]; then
        color_red "❌ НАРУШЕНИЕ: $f содержит '$word' (запрещено grilling-резолюцией)"
        color_yellow "   Источник: ${prohibited_to_sources[$word]}"
        color_yellow "   Совпадения:"
        echo "$matches" | head -3 | sed 's/^/     /'
        color_yellow "   См. AGENTS.md § «Implementation и grilling-резолюции (Pass 374)»."
        violation_count=$((violation_count + 1))
        violation_details="${violation_details} ${f}:${word}"
      fi
    done
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ Запрещённых паттернов из grilling-резолюций не найдено"
    return 0
  else
    color_red "Найдено нарушений: $violation_count"
    color_yellow "Если это намеренное отступление — обновите grilling-резолюцию через wayfinder."
    return 1
  fi
}

case "${1:-}" in
  --help|-h|"")
    check_implementation
    exit $?
    ;;
  --quiet)
    check_implementation >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-implementation-against-grilling.sh [--quiet]"
    exit 2
    ;;
esac