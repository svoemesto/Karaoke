#!/usr/bin/env bash
# =====================================================================
# tools/tracker-lib.sh — общие функции для CLI OpenProject
# =====================================================================
# Используется через source из tools/tracker.sh и других tracker-скриптов.
# Не предназначен для прямого вызова.
#
# Содержит:
#   - tracker_load_env()    — загрузка .env.local-tracker + валидация
#   - tracker_require_tools() — проверка curl, jq, bash
#   - tracker_log_audit()   — JSON-логирование в logs/tracker-agent.log
#   - tracker_http_request() — HTTP wrapper с retry на 429
#   - tracker_md_to_text()  — простой markdown конвертер для комментариев
#   - tracker_healthcheck() — GET /health_check
#   - tracker_list_projects() — GET /api/v3/projects
#   - tracker_get_issue()  — GET /api/v3/work_packages/{id}
#   - tracker_create_issue() — POST /api/v3/work_packages
#   - tracker_list_issues() — GET /api/v3/work_packages?filter
#   - tracker_claim_issue() — PATCH /api/v3/work_packages/{id} (assignee + status)
#   - tracker_add_comment() — POST /api/v3/work_packages/{id}/activities
#   - tracker_close_issue() — PATCH /api/v3/work_packages/{id} (status → closed)
#   - tracker_reopen_issue() — PATCH /api/v3/work_packages/{id} (status → open)
# =====================================================================

set -euo pipefail

# --- Константы ---
TRACKER_LIB_VERSION="295.2.0"
TRACKER_LOG_FILE_DEFAULT="${REPO_ROOT:-/home/nsa/Karaoke}/logs/tracker-agent.log"
TRACKER_HTTP_RETRIES=3
TRACKER_HTTP_BACKOFFS=(2 4 8)  # секунды

# --- Цвета (если TTY) ---
if [ -t 1 ]; then
    C_RED=$'\033[0;31m'
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_RESET=$'\033[0m'
else
    C_RED=""
    C_GREEN=""
    C_YELLOW=""
    C_RESET=""
fi

# =====================================================================
# tracker_load_env — загрузка .env.local-tracker с валидацией
# =====================================================================
tracker_load_env() {
    local env_file="${REPO_ROOT:-.}/.env.local-tracker"
    if [ ! -f "$env_file" ]; then
        echo "${C_RED}ERROR${C_RESET}: ${env_file} не найден. Скопируйте .env.local-tracker.example → .env.local-tracker" >&2
        return 1
    fi

    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a

    # Валидация обязательных переменных
    local missing=()
    [ -z "${TRACKER_URL:-}" ] && missing+=("TRACKER_URL")
    [ -z "${TRACKER_USER:-}" ] && missing+=("TRACKER_USER")
    [ -z "${TRACKER_API_TOKEN:-}" ] && missing+=("TRACKER_API_TOKEN")

    if [ ${#missing[@]} -gt 0 ]; then
        echo "${C_RED}ERROR${C_RESET}: обязательные переменные не заданы в ${env_file}: ${missing[*]}" >&2
        return 1
    fi

    # Defaults
    TRACKER_AGENT_USER="${TRACKER_AGENT_USER:-$TRACKER_USER}"
    TRACKER_LOG_LEVEL="${TRACKER_LOG_LEVEL:-info}"
    TRACKER_HTTP_TIMEOUT="${TRACKER_HTTP_TIMEOUT:-30}"
    LOG_FILE="${LOG_FILE:-${TRACKER_LOG_FILE_DEFAULT}}"
}

# =====================================================================
# tracker_require_tools — проверка зависимостей
# =====================================================================
tracker_require_tools() {
    local missing=()
    command -v curl >/dev/null 2>&1 || missing+=("curl")
    command -v jq >/dev/null 2>&1 || missing+=("jq")

    if [ ${#missing[@]} -gt 0 ]; then
        echo "${C_RED}ERROR${C_RESET}: требуемые утилиты не найдены: ${missing[*]}" >&2
        return 1
    fi
}

# =====================================================================
# tracker_log_audit — JSON-запись в logs/tracker-agent.log
# =====================================================================
tracker_log_audit() {
    local cmd="$1"
    local endpoint="$2"
    local http_status="$3"
    local duration_ms="$4"
    local error="${5:-}"
    local retry_count="${6:-0}"
    local ts req_id

    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    req_id=$(cat /proc/sys/kernel/random/uuid 2>/dev/null | cut -c1-8 || echo "no-uuid-$$")

    local entry
    if [ -n "$error" ]; then
        entry=$(jq -n -c \
            --arg ts "$ts" \
            --arg cmd "$cmd" \
            --arg req_id "$req_id" \
            --arg endpoint "$endpoint" \
            --argjson http_status "$http_status" \
            --argjson duration_ms "$duration_ms" \
            --arg error "$error" \
            --argjson retry_count "$retry_count" \
            '{ts: $ts, cmd: $cmd, req_id: $req_id, endpoint: $endpoint, http_status: $http_status, duration_ms: $duration_ms, retry_count: $retry_count, error: $error}')
    else
        entry=$(jq -n -c \
            --arg ts "$ts" \
            --arg cmd "$cmd" \
            --arg req_id "$req_id" \
            --arg endpoint "$endpoint" \
            --argjson http_status "$http_status" \
            --argjson duration_ms "$duration_ms" \
            --argjson retry_count "$retry_count" \
            '{ts: $ts, cmd: $cmd, req_id: $req_id, endpoint: $endpoint, http_status: $http_status, duration_ms: $duration_ms, retry_count: $retry_count}')
    fi

    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || true
    echo "$entry" >> "$LOG_FILE" 2>/dev/null || true
}

# =====================================================================
# tracker_http_request — HTTP wrapper с retry на 429 + аудит-логирование
# =====================================================================
tracker_http_request() {
    local method="$1"
    local endpoint="$2"
    local body="${3:-}"
    local cmd_name="${4:-http}"

    local url="${TRACKER_URL}${endpoint}"
    local start_ts end_ts duration_ms http_status attempt retry_count=0
    local response response_file
    response_file=$(mktemp)
    trap "rm -f '$response_file'" RETURN

    # OpenProject использует Basic Auth с username:api_token
    local auth_header
    auth_header=$(printf '%s:%s' "$TRACKER_USER" "$TRACKER_API_TOKEN" | base64 -w 0)

    for attempt in $(seq 1 $((TRACKER_HTTP_RETRIES + 1))); do
        start_ts=$(date +%s%N)

        if [ -n "$body" ]; then
            http_status=$(curl -sS -o "$response_file" -w "%{http_code}" \
                -X "$method" \
                --max-time "$TRACKER_HTTP_TIMEOUT" \
                -H "Authorization: Basic ${auth_header}" \
                -H "Content-Type: application/json" \
                -H "Accept: application/json" \
                --data-raw "$body" \
                "$url" 2>/dev/null || echo "000")
        else
            http_status=$(curl -sS -o "$response_file" -w "%{http_code}" \
                -X "$method" \
                --max-time "$TRACKER_HTTP_TIMEOUT" \
                -H "Authorization: Basic ${auth_header}" \
                -H "Accept: application/json" \
                "$url" 2>/dev/null || echo "000")
        fi

        end_ts=$(date +%s%N)
        duration_ms=$(( (end_ts - start_ts) / 1000000 ))

        case "$http_status" in
            2*)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "" "$retry_count"
                cat "$response_file"
                return 0
                ;;
            401|403)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Authentication failed" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: TRACKER_API_TOKEN истёк или отозван. Обновите токен в .env.local-tracker" >&2
                echo "  Создать новый: UI → My Account → Access Tokens → Generate" >&2
                return 3
                ;;
            429)
                if [ $retry_count -lt $TRACKER_HTTP_RETRIES ]; then
                    local backoff=${TRACKER_HTTP_BACKOFFS[$retry_count]}
                    echo "${C_YELLOW}WARN${C_RESET}: HTTP 429, retry $((retry_count + 1))/${TRACKER_HTTP_RETRIES} через ${backoff}s..." >&2
                    sleep "$backoff"
                    retry_count=$((retry_count + 1))
                    continue
                fi
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Rate limit exhausted" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: OpenProject API rate-limit, попробуйте позже или уменьшите частоту polling" >&2
                return 4
                ;;
            4*)
                local err_msg
                err_msg=$(jq -r '.message // .error // empty' "$response_file" 2>/dev/null | head -3 | paste -sd ';' -)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "${err_msg:-Client error}" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: HTTP ${http_status} ${method} ${endpoint}" >&2
                [ -n "$err_msg" ] && echo "  $err_msg" >&2
                return 5
                ;;
            5*)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Server error" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: OpenProject вернула ${http_status}, попробуйте позже" >&2
                return 6
                ;;
            000)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "000" "$duration_ms" "Connection failed" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: OpenProject недоступен по ${TRACKER_URL}, проверьте \`docker ps | grep openproject\`" >&2
                return 2
                ;;
            *)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Unknown status" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: неожиданный HTTP статус ${http_status}" >&2
                return 5
                ;;
        esac
    done
}

# =====================================================================
# tracker_md_to_text — markdown → plain text (для комментариев)
# =====================================================================
# OpenProject поддерживает markdown в комментариях напрямую, поэтому
# просто читаем файл и отправляем как есть.
# =====================================================================
tracker_md_to_text() {
    local md_file="$1"
    if [ ! -f "$md_file" ]; then
        echo "${C_RED}ERROR${C_RESET}: файл не найден: $md_file" >&2
        return 1
    fi
    cat "$md_file"
}

# =====================================================================
# tracker_healthcheck — GET /health_check (без auth)
# =====================================================================
tracker_healthcheck() {
    local start_ts end_ts duration_ms http_status response
    local url="${TRACKER_URL}/health_check"
    response=$(mktemp)
    trap "rm -f '$response'" RETURN

    start_ts=$(date +%s%N)
    http_status=$(curl -sS -o "$response" -w "%{http_code}" --max-time "$TRACKER_HTTP_TIMEOUT" "$url" 2>/dev/null || echo "000")
    end_ts=$(date +%s%N)
    duration_ms=$(( (end_ts - start_ts) / 1000000 ))

    tracker_log_audit "healthcheck" "GET /health_check" "$http_status" "$duration_ms" "" 0

    if [ "$http_status" = "200" ]; then
        echo "${C_GREEN}OK${C_RESET}: OpenProject UP at ${TRACKER_URL} (response: ${duration_ms}ms)"
        return 0
    fi

    echo "${C_RED}FAIL${C_RESET}: OpenProject unavailable at ${TRACKER_URL} (HTTP ${http_status})" >&2
    return 1
}

# =====================================================================
# tracker_list_projects — GET /api/v3/projects
# =====================================================================
tracker_list_projects() {
    local response
    response=$(tracker_http_request GET "/api/v3/projects?pageSize=100" "" "list-projects")

    echo "$response" | jq -r '
        ["ID", "IDENTIFIER", "NAME", "CREATED"] as $headers |
        ($headers, (.embedded.elements[]? | [
            (.id | tostring),
            .identifier,
            .name,
            .createdAt
        ])) | @tsv
    ' | column -t -s $'\t'
}

# =====================================================================
# tracker_get_issue — GET /api/v3/work_packages/{id}
# =====================================================================
tracker_get_issue() {
    local id="$1"
    local response
    response=$(tracker_http_request GET "/api/v3/work_packages/${id}" "" "get-issue")

    echo "$response" | jq '{
        id: .id,
        subject: .subject,
        description: .description.raw,
        type: .type,
        status: (.status.title // .status.name // "unknown"),
        priority: (.priority.title // .priority.name // "Normal"),
        assignee: (.assignee.name // null),
        author: (.author.name // null),
        createdAt: .createdAt,
        updatedAt: .updatedAt,
        project: (.project.title // .project.name)
    }'
}

# =====================================================================
# tracker_create_issue — POST /api/v3/work_packages
# =====================================================================
tracker_create_issue() {
    local project_id="$1"
    local type_id="$2"
    local subject="$3"
    local description="${4:-}"

    local payload
    payload=$(jq -n \
        --argjson pid "$project_id" \
        --argjson tid "$type_id" \
        --arg subj "$subject" \
        --arg desc "$description" \
        '{
            subject: $subj,
            description: {raw: $desc, format: "markdown"},
            _links: {
                project: {href: "/api/v3/projects/" + ($pid | tostring)},
                type: {href: "/api/v3/types/" + ($tid | tostring)}
            }
        }')

    local response
    response=$(tracker_http_request POST "/api/v3/work_packages" "$payload" "create-issue")

    local new_id
    new_id=$(echo "$response" | jq -r '.id')

    echo "${C_GREEN}OK${C_RESET}: work package #${new_id} created"
}

# =====================================================================
# tracker_list_issues — GET /api/v3/work_packages с фильтром
# =====================================================================
# OpenProject использует фильтры через query params:
#   filters=[{"assignee":{"operator":"=","values":["admin"]}},{"status":{"operator":"o"}}]
# =====================================================================
tracker_list_issues() {
    local project_id="${1:-}"
    local assignee="${2:-}"
    local status="${3:-}"  # "open" | "closed" | пустое (все)
    local limit="${4:-50}"

    # Строим массив фильтров
    local filters_array="[]"
    if [ -n "$assignee" ]; then
        # assignee filter
        filters_array=$(echo "$filters_array" | jq --arg a "$assignee" \
            '. + [{"assignee":{"operator":"=","values":[$a]}}]')
    fi
    if [ -n "$project_id" ]; then
        filters_array=$(echo "$filters_array" | jq --argjson pid "$project_id" \
            '. + [{"project":{"operator":"=","values":[$pid]}}]')
    fi
    # Status filter: "open" → operator "o" (open work packages), "closed" → "c"
    if [ -n "$status" ]; then
        local op
        case "$status" in
            open|to_do|"To Do"|"In Progress") op="o" ;;
            closed|done|"Done"|"Closed") op="c" ;;
            *) op="o" ;;  # default open
        esac
        filters_array=$(echo "$filters_array" | jq --arg op "$op" \
            '. + [{"status":{"operator":$op,"values":[]}}]')
    fi

    # URL-encode filters
    local filters_encoded
    filters_encoded=$(printf '%s' "$filters_array" | jq -c '.')

    local response
    response=$(tracker_http_request GET "/api/v3/work_packages?filters=${filters_encoded}&pageSize=${limit}" "" "list-issues")

    # Парсим и выводим
    echo "$response" | jq -r '
        ["ID", "SUBJECT", "STATUS", "ASSIGNEE", "TYPE"] as $headers |
        ($headers, (.embedded.elements[]? | [
            (.id | tostring),
            (.subject // "-"),
            (.status.title // .status.name // "-"),
            (.assignee.name // "unassigned"),
            (.type.title // .type.name // "-")
        ])) | @tsv
    ' | column -t -s $'\t'
}

# =====================================================================
# tracker_claim_issue — назначить на агента + перевести в "In progress"
# =====================================================================
tracker_claim_issue() {
    local id="$1"

    # Получаем текущий статус + assignee
    local response current_status current_assignee_link
    response=$(tracker_http_request GET "/api/v3/work_packages/${id}" "" "claim-issue-get")

    current_status=$(echo "$response" | jq -r '.status.title // .status.name // "unknown"')
    current_assignee_link=$(echo "$response" | jq -r '._links.assignee.href // empty')

    echo "DEBUG: #${id} current status='${current_status}'" >&2

    # Получаем ID пользователя-агента через /api/v3/users?search=username
    local agent_id
    agent_id=$(tracker_http_request GET "/api/v3/users?search=${TRACKER_AGENT_USER}&pageSize=1" "" "claim-issue-user-search" \
        | jq -r '._embedded.elements[0].id // empty')

    if [ -z "$agent_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: пользователь ${TRACKER_AGENT_USER} не найден" >&2
        return 5
    fi

    # Находим статус "In progress"
    local in_progress_status_id
    in_progress_status_id=$(tracker_http_request GET "/api/v3/statuses" "" "claim-issue-statuses" \
        | jq -r '.embedded.elements[] | select(.name == "In progress" or .title == "In progress") | .id' | head -1)

    if [ -z "$in_progress_status_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: статус 'In progress' не найден в проекте" >&2
        return 5
    fi

    # PATCH для обновления assignee и status
    local payload
    payload=$(jq -n \
        --argjson agent_id "$agent_id" \
        --argjson status_id "$in_progress_status_id" \
        '{
            lockVersion: 0,
            _links: {
                assignee: {href: "/api/v3/users/" + ($agent_id | tostring)},
                status: {href: "/api/v3/statuses/" + ($status_id | tostring)}
            }
        }')

    # Сначала делаем GET чтобы узнать lockVersion
    local lock_version
    lock_version=$(echo "$response" | jq -r '.lockVersion // 0')
    payload=$(echo "$payload" | jq --argjson lv "$lock_version" '.lockVersion = $lv')

    tracker_http_request PATCH "/api/v3/work_packages/${id}" "$payload" "claim-issue-patch" >/dev/null

    echo "${C_GREEN}OK${C_RESET}: #${id} claimed by ${TRACKER_AGENT_USER} (status: In progress)"
}

# =====================================================================
# tracker_add_comment — POST /api/v3/work_packages/{id}/activities
# =====================================================================
tracker_add_comment() {
    local id="$1"
    local file="$2"

    if [ ! -f "$file" ]; then
        echo "${C_RED}ERROR${C_RESET}: файл не найден: $file" >&2
        return 1
    fi

    # OpenProject принимает markdown в комментариях
    local comment_text
    comment_text=$(tracker_md_to_text "$file")

    local payload
    payload=$(jq -n --arg ct "$comment_text" '{
        comment: {raw: $ct, format: "markdown"}
    }')

    local response
    response=$(tracker_http_request POST "/api/v3/work_packages/${id}/activities" "$payload" "add-comment")

    local comment_id body_length
    comment_id=$(echo "$response" | jq -r '.id // "?"')
    body_length=$(wc -c < "$file")

    echo "${C_GREEN}OK${C_RESET}: comment added to #${id} (id: ${comment_id}, length: ${body_length} chars)"
}

# =====================================================================
# tracker_close_issue — PATCH status → "Closed"
# =====================================================================
tracker_close_issue() {
    local id="$1"

    # Получаем текущий статус + lockVersion
    local response current_status lock_version
    response=$(tracker_http_request GET "/api/v3/work_packages/${id}" "" "close-issue-get")
    current_status=$(echo "$response" | jq -r '.status.title // .status.name // "unknown"')
    lock_version=$(echo "$response" | jq -r '.lockVersion // 0')

    if [ "$current_status" = "Closed" ]; then
        echo "${C_YELLOW}WARN${C_RESET}: #${id} уже в статусе Closed" >&2
        return 0
    fi

    # Находим ID статуса "Closed"
    local closed_status_id
    closed_status_id=$(tracker_http_request GET "/api/v3/statuses" "" "close-issue-statuses" \
        | jq -r '.embedded.elements[] | select(.name == "Closed" or .title == "Closed") | .id' | head -1)

    if [ -z "$closed_status_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: статус 'Closed' не найден в проекте" >&2
        return 5
    fi

    local payload
    payload=$(jq -n \
        --argjson lv "$lock_version" \
        --argjson sid "$closed_status_id" \
        '{
            lockVersion: $lv,
            _links: {
                status: {href: "/api/v3/statuses/" + ($sid | tostring)}
            }
        }')

    tracker_http_request PATCH "/api/v3/work_packages/${id}" "$payload" "close-issue-patch" >/dev/null

    echo "${C_GREEN}OK${C_RESET}: #${id} closed"
}

# =====================================================================
# tracker_reopen_issue — PATCH status → "In progress" (или "New")
# =====================================================================
tracker_reopen_issue() {
    local id="$1"

    local response lock_version
    response=$(tracker_http_request GET "/api/v3/work_packages/${id}" "" "reopen-issue-get")
    lock_version=$(echo "$response" | jq -r '.lockVersion // 0')

    # Находим ID статуса "In progress" (приоритет) или "New"
    local status_id
    status_id=$(tracker_http_request GET "/api/v3/statuses" "" "reopen-issue-statuses" \
        | jq -r '.embedded.elements[] | select(.name == "In progress" or .title == "In progress") | .id' | head -1)

    if [ -z "$status_id" ]; then
        # Fallback на "New"
        status_id=$(tracker_http_request GET "/api/v3/statuses" "" "reopen-issue-statuses-2" \
            | jq -r '.embedded.elements[] | select(.name == "New" or .title == "New") | .id' | head -1)
    fi

    if [ -z "$status_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: статус для reopen не найден" >&2
        return 5
    fi

    local payload
    payload=$(jq -n \
        --argjson lv "$lock_version" \
        --argjson sid "$status_id" \
        '{
            lockVersion: $lv,
            _links: {
                status: {href: "/api/v3/statuses/" + ($sid | tostring)}
            }
        }')

    tracker_http_request PATCH "/api/v3/work_packages/${id}" "$payload" "reopen-issue-patch" >/dev/null

    echo "${C_GREEN}OK${C_RESET}: #${id} reopened"
}
