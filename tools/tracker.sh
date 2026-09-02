#!/usr/bin/env bash
# =====================================================================
# tools/tracker.sh — главный CLI для OpenProject (AI-агент)
# =====================================================================
# Подкоманды:
#   list-projects                                  — список проектов
#   list-issues [--project-id ID] [--assignee USER] [--status open|closed]
#                  [--limit N]                     — список задач (work packages)
#   get-issue ID                                   — детали задачи (JSON)
#   claim-issue ID                                 — назначить на агента + In progress
#   add-comment ID --file FILE                     — добавить markdown-комментарий
#   close-issue ID                                 — перевести в Closed
#   reopen-issue ID                                — перевести обратно
#   create-issue --project-id ID --type-id ID --subject S
#                 [--description D]                — создать задачу
#   healthcheck                                    — проверить доступность OpenProject
#
# Использование:
#   source .env.local-tracker
#   ./tools/tracker.sh <subcommand> [args...]
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/tracker-lib.sh"

TRACKER_CLI_VERSION="295.2.0"

# --- Помощь ---
tracker_help() {
    cat <<EOF
tools/tracker.sh v${TRACKER_CLI_VERSION} — CLI для OpenProject (AI-агент)

USAGE:
    ./tools/tracker.sh <subcommand> [args...]

SUBCOMMANDS:
    list-projects
        Список всех проектов в OpenProject.

    list-issues [--project-id ID] [--assignee USER] [--status open|closed] [--limit N]
        Список задач (work packages) с фильтрацией.
        Пример: ./tools/tracker.sh list-issues --assignee ai-agent --status open

    get-issue ID
        Детали задачи в формате JSON.
        ID — это числовой ID work package (например, 42).
        Пример: ./tools/tracker.sh get-issue 42

    claim-issue ID
        Назначить задачу на агента (TRACKER_AGENT_USER) и перевести в In progress.

    add-comment ID --file FILE
        Добавить markdown-комментарий (OpenProject принимает markdown нативно).
        Пример: ./tools/tracker.sh add-comment 42 --file report.md

    close-issue ID
        Перевести задачу в Closed.

    reopen-issue ID
        Перевести задачу обратно (Closed → In progress или New).

    create-issue --project-id ID --type-id ID --subject S [--description D]
        Создать задачу.
        project-id и type-id — числовые ID (получить через list-projects и GET /api/v3/types).
        Пример: ./tools/tracker.sh create-issue --project-id 1 --type-id 1 --subject "Test task"

    healthcheck
        Проверить доступность OpenProject (без аутентификации, через /api/v3/health_check).

ENVIRONMENT (source .env.local-tracker):
    TRACKER_URL          — базовый URL OpenProject (например, http://localhost:7081)
    TRACKER_USER         — username для Basic Auth
    TRACKER_API_TOKEN    — API token (UI → My Account → Access Tokens → Generate)
    TRACKER_AGENT_USER   — username агента (default = TRACKER_USER)
    TRACKER_LOG_LEVEL    — info/debug/error (default info)
    TRACKER_HTTP_TIMEOUT — curl timeout в секундах (default 30)

EXIT CODES:
    0 — успех
    1 — общая ошибка
    2 — OpenProject недоступен
    3 — HTTP 401/403 (токен истёк)
    4 — HTTP 429 (rate-limit)
    5 — HTTP 4xx (другие клиентские ошибки)
    6 — HTTP 5xx (ошибки сервера)

DOCS:
    docs/tracker-setup.md                                      — пошаговое руководство
    specs/295-jira-local-integration/.jira-archived/           — старая Jira-спека (для истории)

EOF
}

# --- Парсинг аргументов ---
if [ $# -eq 0 ] || [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
    tracker_help
    exit 0
fi

if [ "${1:-}" = "--version" ]; then
    echo "tools/tracker.sh v${TRACKER_CLI_VERSION} (tracker-lib.sh v${TRACKER_LIB_VERSION})"
    exit 0
fi

# --- Загрузка env и проверка инструментов ---
tracker_load_env
tracker_require_tools

# --- Роутинг подкоманд ---
SUBCOMMAND="$1"
shift

case "$SUBCOMMAND" in
    list-projects)
        tracker_list_projects
        ;;

    list-issues)
        PROJECT_ID="" ASSIGNEE="" STATUS="" LIMIT="50"
        while [ $# -gt 0 ]; do
            case "$1" in
                --project-id) PROJECT_ID="$2"; shift 2 ;;
                --assignee) ASSIGNEE="$2"; shift 2 ;;
                --status) STATUS="$2"; shift 2 ;;
                --limit) LIMIT="$2"; shift 2 ;;
                *) echo "${C_RED}ERROR${C_RESET}: unknown flag: $1" >&2; exit 1 ;;
            esac
        done
        tracker_list_issues "$PROJECT_ID" "$ASSIGNEE" "$STATUS" "$LIMIT"
        ;;

    get-issue)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh get-issue ID" >&2
            exit 1
        fi
        tracker_get_issue "$1"
        ;;

    claim-issue)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh claim-issue ID" >&2
            exit 1
        fi
        tracker_claim_issue "$1"
        ;;

    add-comment)
        if [ $# -lt 3 ] || [ "$2" != "--file" ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh add-comment ID --file FILE" >&2
            exit 1
        fi
        tracker_add_comment "$1" "$3"
        ;;

    close-issue)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh close-issue ID" >&2
            exit 1
        fi
        tracker_close_issue "$1"
        ;;

    reopen-issue)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh reopen-issue ID" >&2
            exit 1
        fi
        tracker_reopen_issue "$1"
        ;;

    create-issue)
        PROJECT_ID="" TYPE_ID="" SUBJECT="" DESCRIPTION=""
        while [ $# -gt 0 ]; do
            case "$1" in
                --project-id) PROJECT_ID="$2"; shift 2 ;;
                --type-id) TYPE_ID="$2"; shift 2 ;;
                --subject) SUBJECT="$2"; shift 2 ;;
                --description) DESCRIPTION="$2"; shift 2 ;;
                *) echo "${C_RED}ERROR${C_RESET}: unknown flag: $1" >&2; exit 1 ;;
            esac
        done
        if [ -z "$PROJECT_ID" ] || [ -z "$TYPE_ID" ] || [ -z "$SUBJECT" ]; then
            echo "${C_RED}ERROR${C_RESET}: обязательные флаги: --project-id, --type-id, --subject" >&2
            exit 1
        fi
        tracker_create_issue "$PROJECT_ID" "$TYPE_ID" "$SUBJECT" "$DESCRIPTION"
        ;;

    healthcheck)
        tracker_healthcheck
        ;;

    *)
        echo "${C_RED}ERROR${C_RESET}: unknown subcommand: $SUBCOMMAND" >&2
        echo "  Run 'tracker.sh --help' for usage." >&2
        exit 1
        ;;
esac
