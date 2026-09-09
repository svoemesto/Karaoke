#!/usr/bin/env bash
# =====================================================================
# tools/tracker-bootstrap.sh — auto-claim OpenProject issue from
# /speckit.specify arguments.
#
# Это BEFORE_SPECIFY hook (Pass 350). Вызывается из:
#   1. tools/specify-bootstrap.sh (после резервирования NNN, ДО старта
#      спецификации) — auto-detect Issue ID в $1 (slug или description),
#      автоматически вызывать `tracker.sh claim-issue <NN>`.
#   2. Вручную агентом: `bash tools/tracker-bootstrap.sh`.
#   3. В скриптах типа `tracker-poll` через wrapper.
#
# Алгоритм:
#   1. Сканирует все аргументы $@ на предмет паттернов OpenProject ID:
#      `#69`, `№69`, `OP #69`, `task 69`, `задача 69`, `OpenProject 69`.
#   2. Если найден — вызывает `tracker.sh claim-issue <NN>` (idempotent: если
#      уже In progress, claim-issue no-op либо возвращает существующий статус).
#   3. Если НЕ найден — выводит подсказку и завершает с кодом 0 (no-op).
#
# Exit codes:
#   0 — успех (claim выполнен ИЛИ no-op).
#   1 — найден Issue ID, но tracker.sh claim-issue упал (token истёк, network).
#   2 — usage / parser error.
# =====================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
TRACKER_SH="${SCRIPT_DIR}/tracker.sh"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/tracker-lib.sh"

log() { printf '[tracker-bootstrap] %s\n' "$*" >&2; }

# Regex детектит Issue ID в произвольном тексте. Возвращает первое совпадение.
# Поддерживает формы:
#   * "#69" / "№69"
#   * "task 69" / "таск 69" / "Task#69"
#   * "задача 69" / "задачи 69" / "задачу 69" / "задачей 69"
#   * "OP #69" / "OP#69" / "ОР #69" / "openproject 69" / "OpenProject #69"
# Не триггерится на: "%69%", "issue42" (без пробела), "timeout 30s", "retry 3".
extract_issue_id() {
    local input="$1"
    # Robust pattern: supports #69, №69, задача/задачей 69, task 69,
    # OP#69, OpenProject 69. Case-insensitive (cyrillic + english).
    # \w в GNU grep -E = [[:alnum:]_], поддерживает кириллицу.
    local pattern='(задач\w*|task\w*|таск\w*|#|№|оп\w*|op\w*|openproject|open[ \-]?project)[[:space:]]*[#№]?[[:space:]]*([0-9]{1,5})'
    echo "$input" | tr '[:upper:]' '[:lower:]' \
        | grep -Eoi "$pattern" 2>/dev/null \
        | grep -Eo '[0-9]+' \
        | head -n 1
}

# Сканируем все аргументы + окружение.
ISSUE_ID=""
for arg in "$@"; do
    found="$(extract_issue_id "$arg" || true)"
    if [ -n "$found" ]; then
        ISSUE_ID="$found"
        break
    fi
done

# Также проверяем INPUT_MESSAGE / ARGUMENTS (от opencode skill).
if [ -z "$ISSUE_ID" ]; then
    if [ -n "${INPUT_MESSAGE:-}" ]; then
        for msg_line in $INPUT_MESSAGE; do
            found="$(extract_issue_id "$msg_line" || true)"
            if [ -n "$found" ]; then
                ISSUE_ID="$found"
                break
            fi
        done
    fi
fi

if [ -z "$ISSUE_ID" ]; then
    log "no OpenProject Issue ID detected in arguments — claim skipped (no-op)."
    log "hint: pass 'задача #NNN' or 'task N' to auto-claim."
    exit 0
fi

log "detected OpenProject Issue #$ISSUE_ID — running tracker.sh claim-issue $ISSUE_ID"

if ! command -v curl >/dev/null 2>&1; then
    log "ERROR: curl not available — cannot call OpenProject API."
    exit 2
fi

# Вызываем claim-issue. tracker.sh сам делает source на .env.local-tracker.
if [ -f "${REPO_ROOT}/.env.local-tracker" ]; then
    # shellcheck disable=SC1091
    set -a; source "${REPO_ROOT}/.env.local-tracker"; set +a
fi

if ! bash "${TRACKER_SH}" claim-issue "${ISSUE_ID}" 2>&1 | tee /dev/stderr | grep -qE "(OK|claimed|already)"; then
    log "ERROR: tracker.sh claim-issue $ISSUE_ID failed (token/issue/network)."
    exit 1
fi

log "OK: Issue #$ISSUE_ID claimed (or already in In progress)."
echo "${ISSUE_ID}" >&1  # Только число в stdout для downstream tooling.

exit 0
