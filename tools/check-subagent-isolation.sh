#!/usr/bin/env bash
# =====================================================================
# tools/check-subagent-isolation.sh — guard для правила NON-NEGOTIABLE
# о subagent workspace isolation (см. AGENTS.md § Subagent workspace
# isolation, constitution.md Principle IX.3, Pass 379 follow-up).
#
# Прецедент (Pass 379, wayfinder #101): 3 параллельных субагента
# (R-07 JPA, R-04/05 docker, R-11 MP4) в одном workspace привели к
# race condition — через `git checkout` + `git stash` субагенты
# переключались на чужие ветки, и в PR #484 оказался чужой commit
# `66946300` (JPA). Чинилось через rebase + amend + force-push вручную
# (~30 минут). Этот guard ловит такие ситуации **до** merge.
#
# Использование:
#   bash tools/check-subagent-isolation.sh                  # guard-режим: exit 1 если isolation нарушена
#   bash tools/check-subagent-isolation.sh --quiet         # для pre-commit: только exit code
#   bash tools/check-subagent-isolation.sh --status       # показать текущее состояние isolation
#
# Что проверяет:
#   1) Каждая PR-ветка содержит commits ТОЛЬКО из этой ветки.
#      Другими словами, проверяется что PR-ветка не «наследует» commits
#      от другой PR-ветки (что было симптомом Pass 379 race condition —
#      PR #484 содержал чужой commit `66946300` от PR #483).
#   2) `git worktree list` — каждый worktree в отдельной папке
#      (advisory: предупреждает, но не блокирует, если worktree'ов нет —
#      потому что и pre-commit, и CI запускаются без worktree).
#
# Когда запускать:
#   - pre-commit (passes `--quiet`, exit 1 если есть проблемы)
#   - CI (полный вывод)
#   - ручная проверка после подозрительной работы субагентов
#
# Exit codes:
#   0 — isolation OK (или advisory warnings only)
#   1 — isolation нарушена (PR-ветка содержит чужой commit)
#   2 — usage error ИЛИ проверка не могла быть выполнена (fail-closed)
#
# BR-597 (аудит #336, пакет PKG-S3-30): «не удалось вычислить» и «вычислили —
# нарушений нет» были НЕРАЗЛИЧИМЫ. `git log master..<branch>` при отсутствии
# ref'а master (или при битом клоне) молча отдавал пустоту, guard печатал
# «⚠️ ветка не содержит новых commits» и возвращал 0 — то есть fail-open на
# самом важном пути (не смог проверить → «всё хорошо»). Теперь код возврата
# git проверяется, базовая ветка разрешается явно (master → origin/master →
# main → origin/main), а невозможность проверки даёт НЕНУЛЕВОЙ код.
# =====================================================================

set -uo pipefail

MODE="${1:-}"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[1;33m%s\033[0m\n' "$*"; }
color_cyan() { printf '\033[0;36m%s\033[0m\n' "$*"; }

# Разрешить базовую ветку, относительно которой считаются «свои» коммиты.
# BR-597: жёсткое "master" в ref'е ломало guard в клонах без локального
# master. Возвращает имя ref'а или пустую строку, если базу найти нельзя.
resolve_base_ref() {
  local candidate
  for candidate in master origin/master main origin/main; do
    if git rev-parse --verify --quiet "${candidate}" >/dev/null 2>&1; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  return 1
}

# Получить список commits текущей ветки (последние N, которые не в master).
get_branch_commits() {
  local branch="$1"
  local base="$2"
  # Берём commits, которые есть в branch, но не в base.
  git log "${base}..${branch}" --pretty=format:'%H'
}

# Получить список всех открытых PR-веток из origin.
get_pr_branches() {
  # Используем git branch -r для remote-tracking branches, исключая master и HEAD.
  git branch -r 2>/dev/null | grep -v 'master' | grep -v 'HEAD' | sed 's|origin/||' | sort -u
}

check_isolation() {
  local status=0
  local current_branch
  local pr_branches=()

  # Проверка 1: текущая ветка не содержит чужие commits.
  # BR-597: код возврата git проверяется — «не смогли вычислить» ≠ «нарушений нет».
  if ! current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null); then
    color_red "❌ Не удалось определить текущую ветку (git rev-parse failed)."
    color_red "   Проверка изоляции НЕ выполнена — fail-closed, а не «всё хорошо»."
    return 2
  fi
  if [[ -z "$current_branch" ]]; then
    color_red "❌ git rev-parse вернул пустую ветку — проверка изоляции невозможна."
    return 2
  fi

  local base_ref
  if ! base_ref=$(resolve_base_ref); then
    color_red "❌ Не найдена базовая ветка (master / origin/master / main / origin/main)."
    color_red "   Проверка изоляции НЕ выполнена — fail-closed."
    return 2
  fi

  if [[ "$current_branch" == "master" ]] || [[ "$current_branch" == "main" ]]; then
    # На базовой ветке нечего проверять (это не feature-ветка).
    color_green "✅ На $current_branch — isolation check not applicable"
    return 0
  fi

  color_cyan "🔍 Subagent isolation check for branch: $current_branch (base: $base_ref)"

  # Получаем список commits текущей ветки, которых НЕТ в базовой ветке.
  # Эти commits принадлежат этой ветке. Если какой-то из них есть в другой
  # PR-ветке — это симптом race condition.
  local current_commits
  if ! current_commits=$(git log "${base_ref}..${current_branch}" --pretty=format:'%H %s' 2>&1); then
    color_red "❌ git log '${base_ref}..${current_branch}' завершился с ошибкой:"
    color_red "   $current_commits"
    color_red "   Проверка изоляции НЕ выполнена — fail-closed."
    return 2
  fi

  if [[ -z "$current_commits" ]]; then
    color_yellow "⚠️  Ветка $current_branch не содержит новых commits относительно $base_ref"
    color_yellow "   (может быть уже merged или пустая)"
    return 0
  fi

  # Получаем список remote PR-веток (исключая текущую).
  local other_branches
  other_branches=$(get_pr_branches | grep -v "^${current_branch}$")

  local found_overlap=0
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    local commit_hash
    local commit_subject
    commit_hash=$(echo "$line" | awk '{print $1}')
    commit_subject=$(echo "$line" | cut -d' ' -f2-)

    # Проверяем, есть ли этот commit в какой-то другой remote-ветке.
    # Используем git branch -r --contains для эффективной проверки.
    local containing_branches
    containing_branches=$(git branch -r --contains "$commit_hash" 2>/dev/null | grep -v 'master' | grep -v 'HEAD' | sed 's|^[[:space:]]*origin/||' | sort -u)

    # Оставляем только те ветки, которые НЕ текущая.
    # (Trim пробелов обязателен — git выводит branches с leading whitespace.)
    local foreign_branches
    foreign_branches=$(echo "$containing_branches" | sed 's|[[:space:]]*$||' | grep -v "^${current_branch}$" | grep -v '^$')

    if [[ -n "$foreign_branches" ]]; then
      color_red "❌ Commit ${commit_hash:0:8} принадлежит нескольким PR-веткам:"
      color_red "   - $current_branch (эта ветка)"
      echo "$foreign_branches" | while IFS= read -r fb; do
        [[ -z "$fb" ]] && continue
        color_red "   - $fb (ЧУЖАЯ ветка — race condition!)"
      done
      color_red "   Subject: $commit_subject"
      color_red ""
      color_red "   Это симптом Pass 379 — race condition между субагентами."
      color_red "   Решение: см. AGENTS.md § Subagent workspace isolation."
      color_red "   Возможный фикс: rebase этой ветки на master + force-push."
      found_overlap=1
      status=1
    fi
  done <<< "$current_commits"

  if [[ $found_overlap -eq 0 ]]; then
    color_green "✅ Все commits в ветке $current_branch уникальны (нет race condition)"
  fi

  # Проверка 2 (advisory): git worktree list.
  local worktree_count
  worktree_count=$(git worktree list 2>/dev/null | wc -l)
  if [[ $worktree_count -gt 1 ]]; then
    color_cyan "ℹ️  Worktrees активны: $worktree_count шт. (это OK, если каждый субагент в своём)"
  fi

  return $status
}

case "${MODE}" in
  --help|-h|"")
    check_isolation
    exit $?
    ;;
  --quiet)
    check_isolation >/dev/null 2>&1
    exit $?
    ;;
  --status)
    echo "Subagent isolation status:"
    echo "  Current branch: $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"
    echo "  Worktrees: $(git worktree list 2>/dev/null | wc -l)"
    echo "  Mode: $(case "${MODE}" in '') echo "default";; *) echo "${MODE}";; esac)"
    exit 0
    ;;
  *)
    color_red "ERROR: unknown mode: ${MODE}"
    color_yellow "Usage: bash $0 [--help | --quiet | --status]"
    exit 2
    ;;
esac
