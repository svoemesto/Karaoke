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
#   bash tools/check-frontend-build.sh --self-test      # фикстуры «до/после»
#
# Что проверяет:
#   В shell-файлах (.sh/.bash + .github/workflows/*.yml) проверяет,
#   что каждая ДОБАВЛЕННАЯ строка с `npm run <cmd>` имеет `cd webvue3` или
#   `cd karaoke-public` непосредственно перед ней (в той же строке через `&&`,
#   или в предыдущей строке через `cd`).
#
#   Markdown и ADR не проверяются (там могут быть примеры).
#   Сам guard-скрипт исключается из проверки.
#
# BR-580 (аудит #336, пакет PKG-S3-30): контекст «есть ли `cd webvue3` перед
# `npm run`» брался ТОЛЬКО из списка ДОБАВЛЕННЫХ строк. Если `cd webvue3` был
# в файле до правки (контекст diff'а, не добавленная строка), guard видел
# только `npm run lint` — и ложно срабатывал, блокируя корректный коммит.
# Теперь контекст берётся из ПОЛНОГО содержимого файла, а номер строки —
# из diff'а (нумерация новой версии файла).
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

# Счётчик нарушений, заполняемый check_added_lines.
VIOLATION_COUNT=0

# =====================================================================
# check_added_lines <файл> <файл-с-номерами-добавленных-строк>
#   Печатает сообщения о нарушениях; результат — в VIOLATION_COUNT.
#
#   Контекст (предыдущая строка) берётся из ПОЛНОГО содержимого файла —
#   иначе `cd webvue3` из контекста diff'а не виден и guard ложно срабатывает
#   (BR-580). Проверяются только строки, номера которых есть во втором
#   аргументе (добавленные строки новой версии файла).
# =====================================================================
check_added_lines() {
  local file="$1"
  local added_file="$2"
  local prev_line=""
  local line_num=0
  local line
  local -A added=()

  VIOLATION_COUNT=0

  while IFS= read -r n; do
    [ -n "${n}" ] || continue
    added["${n}"]=1
  done < "${added_file}"

  while IFS= read -r line || [ -n "${line}" ]; do
    line_num=$((line_num + 1))

    # Игнорируем markdown и комментарии (и не считаем их контекстом).
    if [[ "$line" =~ ^[[:space:]]*# ]]; then continue; fi

    if [[ -n "${added[${line_num}]:-}" ]] && echo "$line" | grep -qE "\\bnpm\\s+run\\b"; then
      # Проверяем, что в предыдущей строке есть `cd webvue3`/`cd karaoke-public`.
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
        color_red "❌ НАРУШЕНИЕ: $file строка $line_num — 'npm run' без 'cd webvue3'/'cd karaoke-public'"
        color_yellow "   Правило (Pass 375): запускай \`cd <frontend-dir> && npm run <cmd>\`"
        color_yellow "   или \`(cd webvue3 && npm run lint)\` (subshell)."
        color_yellow "   Текущая строка: $line"
        if [[ -n "$prev_line" ]]; then
          color_yellow "   Предыдущая строка: $prev_line"
        fi
        VIOLATION_COUNT=$((VIOLATION_COUNT + 1))
      fi
    fi
    prev_line="$line"
  done < "${file}"
}

# Номера строк новой версии файла, добавленные в diff (git diff -U0).
# Формат hunk-заголовка: @@ -a,b +c,d @@ → добавлены строки c..c+d-1.
added_lines_from_diff() {
  local file="$1"
  git diff HEAD -U0 -- "${file}" 2>/dev/null | awk '
    /^@@/ {
      if (match($0, /\+[0-9]+(,[0-9]+)?/)) {
        seg = substr($0, RSTART+1, RLENGTH-1)
        split(seg, p, ",")
        start = p[1] + 0
        count = (p[2] == "" ? 1 : p[2] + 0)
        for (i = 0; i < count; i++) print start + i
      }
    }
  '
}

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
  local tmp_dir idx=0

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "${tmp_dir}"' RETURN

  for f in "${checkable_files[@]}"; do
    [[ -f "$f" ]] || continue
    idx=$((idx + 1))
    local added_file="${tmp_dir}/added_${idx}.txt"

    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      added_lines_from_diff "$f" > "$added_file"
    else
      # Untracked файл — все строки «добавлены».
      awk 'END { for (i = 1; i <= NR; i++) print i }' "$f" > "$added_file"
    fi

    [[ -s "$added_file" ]] || continue

    # BR-580: контекст — полное содержимое файла; номера строк — из diff'а.
    check_added_lines "$f" "$added_file"
    if [[ "$VIOLATION_COUNT" -gt 0 ]]; then
      violation_count=$((violation_count + VIOLATION_COUNT))
      violation_files="${violation_files} ${f}"
    fi
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ Все вызовы npm run имеют правильный cd webvue3/cd karaoke-public"
    return 0
  else
    color_red "Найдено нарушений: $violation_count (файлы:$violation_files)"
    color_yellow "См. AGENTS.md § «Frontend build: cd <frontend-dir> && npm run <cmd> (Pass 375)»."
    return 1
  fi
}

# =====================================================================
# Самопроверка (фикстуры «до/после»)
# =====================================================================
self_test() {
  local rc=0
  local tmp

  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT

  # $1 — имя кейса, $2 — содержимое файла, $3 — номера добавленных строк,
  # $4 — ожидаемое число нарушений.
  run_case() {
    local name="$1" content="$2" added="$3" expected="$4"
    local file="${tmp}/case_${name}.sh"
    printf '%s\n' "${content}" > "$file"
    printf '%s\n' "${added}" | tr ',' '\n' > "${file}.added"
    check_added_lines "$file" "${file}.added"
    if [[ "$VIOLATION_COUNT" != "$expected" ]]; then
      echo "self-test: кейс '$name' — ожидалось $expected нарушений, получено $VIOLATION_COUNT" >&2
      rc=1
    else
      echo "self-test: ✅ $name → $VIOLATION_COUNT нарушений (ожидалось $expected)"
    fi
  }

  # 1. BR-580: `cd webvue3` в контексте diff'а (не добавленная строка),
  #    `npm run lint` — добавленная. Старый guard видел только добавленные
  #    строки → ложное срабатывание. Новый — PASS.
  run_case "context-cd-not-added" \
    'cd webvue3
npm run lint' "2" "0"

  # 2. Настоящее нарушение: `npm run lint` без cd перед ним.
  run_case "no-cd" \
    'echo start
npm run lint' "2" "1"

  # 3. `cd webvue3 && npm run lint` в одной строке — допустимо.
  run_case "same-line" \
    'cd webvue3 && npm run lint' "1" "0"

  # 4. Subshell `(cd karaoke-public && npm run build)` — допустимо.
  run_case "subshell" \
    '(cd karaoke-public && npm run build)' "1" "0"

  # 5. `cd` в другой каталог — нарушение.
  run_case "wrong-cd" \
    'cd /tmp
npm run build' "2" "1"

  # 6. Комментарий между cd и npm run — не разрывает контекст.
  run_case "comment-between" \
    'cd webvue3
# сборка
npm run lint' "3" "0"

  rm -rf "${tmp}"
  trap - EXIT

  if [[ $rc -ne 0 ]]; then
    echo "SELFTEST FAIL: фикстуры не дали ожидаемых результатов" >&2
    return 1
  fi
  echo "SELFTEST OK: 6/6 фикстур дали ожидаемые результаты"
  return 0
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
  --self-test)
    self_test
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-frontend-build.sh [--quiet|--self-test]"
    exit 2
    ;;
esac
