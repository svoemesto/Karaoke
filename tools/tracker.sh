#!/usr/bin/env bash
# =====================================================================
# tools/tracker.sh — главный CLI трекера (Plane, AI-агент)
# =====================================================================
# Подкоманды:
#   list-projects                                  — список проектов workspace
#   list-issues [--project-id ID] [--assignee USER] [--status open|closed|in-review]
#                  [--limit N] [--raw]               — список задач
#   get-issue ID                                   — детали задачи (JSON)
#   claim-issue ID                                 — назначить на агента + In progress
#   mark-review ID                                 — перевести в In review (работа готова)
#   add-comment ID --file FILE                     — добавить markdown-комментарий
#   close-issue ID                                 — перевести в Done
#   reopen-issue ID                                — перевести обратно в In progress
#   create-issue --project-id ID --type-id ID --subject S
#                 [--description D]                — создать задачу
#   delete-issue ID                                — удалить задачу (smoke-тест)
#   healthcheck                                    — проверить доступность Plane
#
# ID задачи: `42` (sequence_id проекта), `KARAOKE-42`, `#42`, `op:403`
# (номер OpenProject — по таблице соответствия), UUID.
#
# Использование:
#   source .env.local-tracker
#   ./tools/tracker.sh <subcommand> [args...]
#
# Workflow:
#   1. user:    $ tracker create-issue --...          → состояние New
#   2. agent:   $ tracker claim-issue 42              → In progress, assignee=ai-agent
#   3. agent:   # ... работа ...
#   4. agent:   $ tracker add-comment 42 --file report.md
#   5. agent:   $ tracker mark-review 42              → In review
#   6. user:    проверяет работу
#   7. user:    $ tracker close-issue 42              → Done
#   или 5'.    $ tracker reopen-issue 42             → вернуть в In progress
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/tracker-lib.sh"

TRACKER_CLI_VERSION="296.0.0"

# --- Помощь ---
tracker_help() {
    cat <<EOF
tools/tracker.sh v${TRACKER_CLI_VERSION} — CLI трекера Plane (AI-агент)

USAGE:
    ./tools/tracker.sh <subcommand> [args...]

SUBCOMMANDS:
    list-projects
        Список проектов workspace Plane.

    list-issues [--project-id ID] [--assignee USER] [--status open|closed|in-review] [--limit N] [--raw]
        Список задач проекта с фильтрацией.
        Пример: ./tools/tracker.sh list-issues --assignee ai-agent --status open
        --status open   — все незавершённые (backlog/unstarted/started);
        --status closed — завершённые (Done);
        --status in-review — только состояние In review;
        --status "<имя>"  — конкретное состояние проекта.
        --raw — TSV без выравнивания \`column -t\` (для машинного разбора;
          по умолчанию — выровненный человекочитаемый вывод).

    get-issue ID
        Детали задачи в формате JSON (включая комментарии).
        ID: 42 | KARAOKE-42 | #42 | op:403 | UUID.
        Пример: ./tools/tracker.sh get-issue KARAOKE-42

    claim-issue ID
        Назначить задачу на агента (TRACKER_AGENT_USER) и перевести в In progress.

    mark-review ID
        Перевести задачу в In review (после публикации отчёта).
        Это сигнал пользователю, что работа завершена и готова к проверке.
        Состояние In review должно существовать в проекте (группа started);
        создать его автоматически можно с TRACKER_CREATE_REVIEW_STATE=1.

    add-comment ID --file FILE
        Добавить комментарий: markdown конвертируется в HTML (Plane хранит HTML),
        таблицы и блоки кода сохраняются.
        Пример: ./tools/tracker.sh add-comment 42 --file report.md

    close-issue ID
        Перевести задачу в Done (финал; обычно делает пользователь после ревью).

    reopen-issue ID
        Перевести задачу обратно в In progress (например, если нужно доработать).

    create-issue --project-id ID --type-id ID --subject S [--description D]
        Создать задачу.
        --project-id — UUID проекта (см. list-projects).
        --type-id — метка типа: имя (\`type:task\`, можно коротко \`task\`) или UUID.
        --description — markdown (конвертируется в HTML).
        Пример: ./tools/tracker.sh create-issue --project-id <uuid> --type-id type:task --subject "Test task"

    delete-issue ID
        Удалить задачу (используется smoke-тестом; в рабочем цикле не нужен).

    healthcheck
        Проверить доступность Plane: GET /users/me/ + список задач проекта.

ENVIRONMENT (source .env.local-tracker):
    PLANE_URL            — базовый URL Plane (например, https://tracker.sm-karaoke.ru)
    PLANE_WORKSPACE      — workspace (например, svoemesto)
    PLANE_AI_TOKEN       — API-токен агента (заголовок X-API-Key)
    PLANE_PROJECT_ID     — UUID проекта по умолчанию (необязательно;
                           иначе проект ищется по PLANE_PROJECT_IDENTIFIER, default KARAOKE)
    TRACKER_AGENT_USER   — имя участника-агента (default: ai-agent)
    TRACKER_OP_MAP       — таблица OpenProject → Plane для \`op:403\`
                           (CSV или JSON; default: knowledge/system/infra/op-plane-mapping.csv)
    TRACKER_HTTP_TIMEOUT — curl timeout в секундах (default 30)
    TRACKER_HTTP_MIN_INTERVAL — пауза перед каждым запросом, секунды (default 0)
    TRACKER_LOG_LEVEL    — info/debug/error (default info)

WORKFLOW:
    user  : create-issue → New
    agent : claim-issue  → In progress (assignee=ai-agent)
    agent : ... work + report.md ...
    agent : add-comment + mark-review → In review
    user  : review report
    user  : close-issue → Done  (или agent: reopen-issue → In progress)

EXIT CODES:
    0 — успех
    1 — ошибка использования/валидации
    2 — трекер недоступен (сеть/URL)
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
        PROJECT_ID="" ASSIGNEE="" STATUS="" LIMIT="50" RAW="0"
        while [ $# -gt 0 ]; do
            case "$1" in
                --project-id) PROJECT_ID="$2"; shift 2 ;;
                --assignee) ASSIGNEE="$2"; shift 2 ;;
                --status) STATUS="$2"; shift 2 ;;
                --limit) LIMIT="$2"; shift 2 ;;
                # BR-602: TSV без выравнивания `column -t` (машинный вывод).
                --raw) RAW="1"; shift ;;
                *) echo "${C_RED}ERROR${C_RESET}: unknown flag: $1" >&2; exit 1 ;;
            esac
        done
        tracker_list_issues "$PROJECT_ID" "$ASSIGNEE" "$STATUS" "$LIMIT" "$RAW"
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

    mark-review)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh mark-review ID" >&2
            exit 1
        fi
        tracker_mark_review "$1"
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

    delete-issue)
        if [ $# -ne 1 ]; then
            echo "${C_RED}ERROR${C_RESET}: usage: tracker.sh delete-issue ID" >&2
            exit 1
        fi
        tracker_delete_issue "$1"
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
