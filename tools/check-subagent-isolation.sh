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
#   2 — usage error
# =====================================================================

set -uo pipefail

MODE="${1:-}"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[1;33m%s\033[0m\n' "$*"; }
color_cyan() { printf '\033[0;36m%s\033[0m\n' "$*"; }

# Получить список commits текущей ветки (последние N, которые не в master).
get_branch_commits() {
  local branch="$1"
  # Берём commits, которые есть в branch, но не в master.
  git log "master..${branch}" --pretty=format:'%H' 2>/dev/null
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
  current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
  if [[ -z "$current_branch" ]] || [[ "$current_branch" == "master" ]]; then
    # На master нечего проверять (это не feature-ветка).
    color_green "✅ На master — isolation check not applicable"
    return 0
  fi

  color_cyan "🔍 Subagent isolation check for branch: $current_branch"

  # Получаем список commits текущей ветки, которых НЕТ в master.
  # Эти commits принадлежат этой ветке. Если какой-то из них есть в другой
  # PR-ветке — это симптом race condition.
  local current_commits
  current_commits=$(git log "master..${current_branch}" --pretty=format:'%H %s' 2>/dev/null)

  if [[ -z "$current_commits" ]]; then
    color_yellow "⚠️  Ветка $current_branch не содержит новых commits относительно master"
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
