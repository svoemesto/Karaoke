#!/usr/bin/env bash
# ============================================================
# tools/git-hooks/pre-commit-block-master.sh
# ============================================================
# Pre-commit hook: блокирует прямой commit в master/main.
#
# AGENTS.md § "Git — CI-gate для master (NON-NEGOTIABLE)":
#   "Прямые коммиты в master ЗАПРЕЩЕНЫ. Lifecycle: ветка живёт после мёрджа."
#
# Это дополнительный client-side layer. Главная защита —
# GitHub branch protection (server-side, admin-enforced).
#
# Установка (one-time per clone):
#   ln -s ../../tools/git-hooks/pre-commit-block-master.sh .git/hooks/pre-commit
# или через .pre-commit-config.yaml (см. корень проекта).
# ============================================================

set -euo pipefail

BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo 'unknown')"
PROTECTED_BRANCHES=("master" "main")

for protected in "${PROTECTED_BRANCHES[@]}"; do
    if [ "$BRANCH" = "$protected" ]; then
        cat >&2 <<'EOF'
============================================================
⛔  BLOCKED: прямой commit в 'BRANCH' ЗАПРЕЩЁН.
============================================================

AGENTS.md § Git — CI-gate для master (NON-NEGOTIABLE):
"Прямые коммиты в master ЗАПРЕЩЕНЫ. Lifecycle: ветка живёт после мёрджа."

Правильный workflow:

  N=$(./tools/reserve-branch-number.sh my-slug)
  git checkout -b "${N}-my-slug" master
  # ... правки ...
  git push -u origin "${N}-my-slug"
  gh pr create --base master
  gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch

Прецедент: 2026-09-09, спека #354 (Pass 353) — agent закоммитил
напрямую в master после мержа PR #452, owner поймал.

Enforcement layers (in order):
  1. GitHub branch protection (server-side, primary defense)
  2. Этот pre-commit hook (client-side, early detection)
  3. CI lint "No direct commits on master" (server-side, safety net)

EOF
        exit 1
    fi
done
