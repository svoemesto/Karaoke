#!/usr/bin/env bash
# =====================================================================
# tools/tracker-poll.sh — polling-сводка задач OpenProject для AI-агента
# =====================================================================
# Используется в начале сессии opencode, чтобы узнать какие задачи
# ждут агента (assignee=ai-agent, status=open) или в работе.
#
# Использование:
#   source .env.local-tracker
#   bash tools/tracker-poll.sh              # human-friendly
#   bash tools/tracker-poll.sh --json       # JSON для парсинга
#   bash tools/tracker-poll.sh --quiet      # только сводка (без таблицы)
#   bash tools/tracker-poll.sh --limit 5    # ограничить вывод
#
# Exit codes:
#   0 — есть задачи (найдено N)
#   1 — есть задачи, но всё ОК (нет открытых)
#   2 — OpenProject недоступен
#   3 — token истёк
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/tracker-lib.sh"

# Парсинг аргументов
MODE="human"   # human | json | quiet
LIMIT=20
ASSIGNEE="${TRACKER_AGENT_USER:-ai-agent}"

while [ $# -gt 0 ]; do
    case "$1" in
        --json) MODE="json"; shift ;;
        --quiet) MODE="quiet"; shift ;;
        --limit) LIMIT="$2"; shift 2 ;;
        --assignee) ASSIGNEE="$2"; shift 2 ;;
        -h|--help)
            cat <<EOF
tools/tracker-poll.sh — polling-сводка задач OpenProject для AI-агента

USAGE:
    bash tools/tracker-poll.sh [OPTIONS]

OPTIONS:
    --assignee USER   имя assignee (default: \$TRACKER_AGENT_USER)
    --limit N         максимум задач в выводе (default: 20)
    --json            вывод в JSON
    --quiet           только статистика (для pipe'ов и cron)

EXAMPLES:
    bash tools/tracker-poll.sh
    bash tools/tracker-poll.sh --json | jq '.open_count'

ENVIRONMENT (source .env.local-tracker):
    TRACKER_URL          — базовый URL OpenProject
    TRACKER_USER/TRACKER_AGENT_USER/TRACKER_API_TOKEN — для CLI
EOF
            exit 0
            ;;
        *) echo "unknown flag: $1" >&2; exit 1 ;;
    esac
done

tracker_load_env
tracker_require_tools

# Цвета для human-mode
if [ -t 1 ]; then
    C_RED=$'\033[0;31m'
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_BLUE=$'\033[0;34m'
    C_BOLD=$'\033[1m'
    C_RESET=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_BOLD=""; C_RESET=""
fi

echoerr() { echo "$@" >&2; }

# 1. Открытые задачи (New/In progress/In review)
OPEN_LIST=$(./tools/tracker.sh list-issues --assignee "$ASSIGNEE" --status open --limit "$LIMIT" 2>&1 || true)
OPEN_CODE=$?

# 2. Задачи в работе (In progress, narrow status filter)
IN_PROG_LIST=$(./tools/tracker.sh list-issues --assignee "$ASSIGNEE" --status in-review --limit "$LIMIT" 2>&1 || true)

# 3. Задачи за последние 7 дней (для сводки)
RECENT_LIST=$(./tools/tracker.sh list-issues --assignee "$ASSIGNEE" --status closed --limit 5 2>&1 || true)

# Парсим вывод: 1-я строка — заголовок, дальше — данные
parse_lines() {
    echo "$1" | tail -n +2 | grep -v '^$' | head -"$LIMIT" || true
}

OPEN_COUNT=$(parse_lines "$OPEN_LIST" | wc -l)
IN_PROG_COUNT=$(parse_lines "$IN_PROG_LIST" | wc -l)
RECENT_COUNT=$(parse_lines "$RECENT_LIST" | wc -l)

if [ "$MODE" = "json" ]; then
    # JSON-вывод для парсинга
    OPEN_JSON=$(parse_lines "$OPEN_LIST" | jq -R 'split("\t") | {id: .[0], subject: .[1], status: .[2]}' 2>/dev/null | jq -s '.' 2>/dev/null || echo '[]')
    cat <<EOF
{
  "polled_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "tracker_url": "${TRACKER_URL}",
  "assignee": "${ASSIGNEE}",
  "open_count": ${OPEN_COUNT},
  "in_review_count": ${IN_PROG_COUNT},
  "recent_closed_count": ${RECENT_COUNT},
  "open": ${OPEN_JSON}
}
EOF
    exit 0
fi

if [ "$MODE" = "quiet" ]; then
    echo "${ASSIGNEE}: ${OPEN_COUNT} open, ${IN_PROG_COUNT} in-review, ${RECENT_COUNT} recent-closed"
    exit 0
fi

# Human-friendly вывод
echo "${C_BOLD}${C_BLUE}═══ Tracker poll ═══${C_RESET}"
echo "${C_BLUE}Время:${C_RESET}     $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "${C_BLUE}Tracker:${C_RESET}   ${TRACKER_URL}"
echo "${C_BLUE}Assignee:${C_RESET}  ${ASSIGNEE}"
echo ""

if [ "$OPEN_COUNT" -eq 0 ] && [ "$IN_PROG_COUNT" -eq 0 ]; then
    echo "${C_GREEN}✓${C_RESET} Все задачи закрыты. Открытых нет."
    echo ""
fi

if [ "$OPEN_COUNT" -gt 0 ]; then
    echo "${C_BOLD}${C_YELLOW}📋 Открытые задачи (${OPEN_COUNT})${C_RESET}"
    parse_lines "$OPEN_LIST"
    echo ""
fi

if [ "$IN_PROG_COUNT" -gt 0 ]; then
    echo "${C_BOLD}${C_BLUE}🔍 В ревью (${IN_PROG_COUNT})${C_RESET}"
    parse_lines "$IN_PROG_LIST"
    echo ""
fi

if [ "$RECENT_COUNT" -gt 0 ]; then
    echo "${C_BOLD}${C_GREEN}✅ Недавно закрытые (${RECENT_COUNT})${C_RESET}"
    parse_lines "$RECENT_LIST"
    echo ""
fi

# Доска
echo "${C_BLUE}Kanban-доска:${C_RESET} ${TRACKER_URL}/projects/karaoke/boards/10"
echo ""
echo "${C_BLUE}Команды:${C_RESET}"
echo "  $ ./tools/tracker.sh list-issues --assignee ${ASSIGNEE} --status open"
echo "  $ ./tools/tracker.sh claim-issue <ID>"
echo "  $ ./tools/tracker.sh mark-review <ID>"
echo "  $ ./tools/tracker.sh close-issue <ID>"

# Exit code: 0 если есть открытые, иначе 1
if [ "$OPEN_COUNT" -gt 0 ]; then
    exit 0
else
    exit 1
fi
