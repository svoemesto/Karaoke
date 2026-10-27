#!/usr/bin/env bash
# =====================================================================
# tools/tracker-lib.sh — функции CLI трекера (Plane API v1)
# =====================================================================
# Используется через source из tools/tracker.sh и других tracker-скриптов.
# Не предназначен для прямого вызова.
#
# Контракт (см. .scratch/karaoke/wayfinding/tracker-migration/migration/
# PLANE-CONTRACT.md и docs/tracker-setup.md):
#   - авторизация: заголовок X-API-Key: $PLANE_AI_TOKEN (токен не попадает
#     в argv — curl получает его через stdin, `--config -`, как в BR-605);
#   - TRACKER_URL остаётся экспортированной и равен PLANE_URL (для
#     обратной совместимости со старым tracker-poll/документами);
#   - ID задачи принимается как: `42` (sequence_id проекта по умолчанию),
#     `KARAOKE-42` (полная ссылка) или `op:403` (номер OpenProject —
#     разрешается по таблице соответствия TRACKER_OP_MAP);
#   - коды выхода: 0 успех, 1 ошибка использования/валидации, 2 трекер
#     недоступен, 3 401/403, 4 429, 5 прочие 4xx, 6 5xx;
#   - 429: пауза (Retry-After, иначе нарастающий backoff) и повтор,
#     а не мгновенный цикл.
#
# Содержит:
#   - tracker_load_env()        — загрузка .env.local-tracker + валидация
#   - tracker_require_tools()   — проверка curl, jq (+ python3 для markdown)
#   - tracker_log_audit()       — JSON-логирование в logs/tracker-agent.log
#   - tracker_http_request()    — HTTP wrapper с retry на 429
#   - tracker_md_to_text()      — markdown → HTML (Plane принимает HTML)
#   - tracker_healthcheck()     — GET /users/me/ + GET .../issues/?per_page=1
#   - tracker_list_projects()   — GET /workspaces/{ws}/projects/
#   - tracker_list_issues()     — GET .../issues/ + фильтрация на клиенте
#   - tracker_get_issue()       — GET .../issues/{id}/ + комментарии
#   - tracker_create_issue()    — POST .../issues/
#   - tracker_claim_issue()     — PATCH .../issues/{id}/ (assignee + In progress)
#   - tracker_add_comment()     — POST .../issues/{id}/comments/
#   - tracker_close_issue()     — PATCH .../issues/{id}/ (state → Done)
#   - tracker_mark_review()     — PATCH .../issues/{id}/ (state → In review)
#   - tracker_reopen_issue()    — PATCH .../issues/{id}/ (state → In progress)
#   - tracker_delete_issue()    — DELETE .../issues/{id}/ (для smoke-теста)
#
# BR-611: библиотека ПОДКЛЮЧАЕТСЯ через `source` — она не имеет права
# менять настройки вызывающего шелла. Каждый вызывающий скрипт задаёт
# режим шелла сам:
#   - tracker.sh, tracker-poll.sh, tracker-smoke-test.sh → `set -euo pipefail`
#   - tracker-bootstrap.sh, tracker-implement-done.sh    → `set -uo pipefail`
# Библиотека использует только локальные проверки кодов возврата.
# =====================================================================

# --- Константы ---
TRACKER_LIB_VERSION="296.0.0"
TRACKER_LOG_FILE_DEFAULT="${REPO_ROOT:-/home/nsa/Karaoke}/logs/tracker-agent.log"
TRACKER_HTTP_RETRIES=3
TRACKER_HTTP_BACKOFFS=(2 4 8)  # секунды, если сервер не прислал Retry-After

# BR-614: предупреждение о недоступности аудит-лога печатается ОДИН раз.
TRACKER_AUDIT_WARNED=0

# Кэши метаданных в рамках одного процесса (каждый вызов tracker.sh —
# отдельный процесс, но list-issues/claim делают по несколько запросов).
TRACKER_PROJECT_ID_CACHE="${TRACKER_PROJECT_ID_CACHE:-}"
TRACKER_STATES_JSON_CACHE="${TRACKER_STATES_JSON_CACHE:-}"
TRACKER_LABELS_JSON_CACHE="${TRACKER_LABELS_JSON_CACHE:-}"
TRACKER_MEMBERS_JSON_CACHE="${TRACKER_MEMBERS_JSON_CACHE:-}"

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

    # Убираем завершающий слэш у PLANE_URL, иначе в путях будет `//api/v1`.
    PLANE_URL="${PLANE_URL%/}"

    # Валидация обязательных переменных Plane.
    local missing=()
    [ -z "${PLANE_URL:-}" ] && missing+=("PLANE_URL")
    [ -z "${PLANE_WORKSPACE:-}" ] && missing+=("PLANE_WORKSPACE")
    [ -z "${PLANE_AI_TOKEN:-}" ] && missing+=("PLANE_AI_TOKEN")

    if [ ${#missing[@]} -gt 0 ]; then
        echo "${C_RED}ERROR${C_RESET}: обязательные переменные не заданы в ${env_file}: ${missing[*]}" >&2
        return 1
    fi

    # Defaults
    TRACKER_AGENT_USER="${TRACKER_AGENT_USER:-ai-agent}"
    TRACKER_LOG_LEVEL="${TRACKER_LOG_LEVEL:-info}"
    TRACKER_HTTP_TIMEOUT="${TRACKER_HTTP_TIMEOUT:-30}"
    # Необязательная пауза перед каждым HTTP-запросом (секунды, дробные
    # допустимы). Нужна smoke-тесту и ручным прогонам, чтобы не упираться
    # в лимит Plane (60 запросов/мин на пользователя).
    TRACKER_HTTP_MIN_INTERVAL="${TRACKER_HTTP_MIN_INTERVAL:-0}"
    # Идентификатор проекта по умолчанию (KARAOKE); используется, когда
    # PLANE_PROJECT_ID не задан в окружении.
    PLANE_PROJECT_IDENTIFIER="${PLANE_PROJECT_IDENTIFIER:-KARAOKE}"
    # Таблица соответствия OpenProject → Plane для формы `op:403`.
    TRACKER_OP_MAP="${TRACKER_OP_MAP:-${REPO_ROOT:-.}/knowledge/system/infra/op-plane-mapping.csv}"
    # Идентификаторы состояний/меток проекта (необязательный кэш T3/T5).
    TRACKER_PLANE_IDS="${TRACKER_PLANE_IDS:-${REPO_ROOT:-.}/knowledge/system/infra/plane-ids.json}"
    LOG_FILE="${LOG_FILE:-${TRACKER_LOG_FILE_DEFAULT}}"

    # TRACKER_URL — обратная совместимость: часть скриптов и документации
    # читает его. На Plane это тот же базовый URL, что и PLANE_URL.
    TRACKER_URL="${PLANE_URL}"
    export TRACKER_URL
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
# tracker_audit_warn — предупреждение об аудит-логе, один раз за сессию
# =====================================================================
tracker_audit_warn() {
    if [ "${TRACKER_AUDIT_WARNED}" -eq 1 ]; then
        return 0
    fi
    TRACKER_AUDIT_WARNED=1
    echo "${C_YELLOW}WARN${C_RESET}: $*" >&2
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

    # BR-614: ошибка записи аудит-лога больше не проглатывается молча —
    # в stderr уходит предупреждение (один раз за сессию).
    local log_dir
    log_dir="$(dirname "$LOG_FILE")"
    if ! mkdir -p "$log_dir" 2>/dev/null; then
        tracker_audit_warn "не удалось создать каталог аудит-лога: ${log_dir} — аудит не записан"
        return 0
    fi
    if ! echo "$entry" >> "$LOG_FILE" 2>/dev/null; then
        tracker_audit_warn "не удалось записать аудит-лог: ${LOG_FILE} — аудит не записан"
        return 0
    fi
}

# =====================================================================
# tracker_http_request — HTTP wrapper с retry на 429 + аудит-логирование
# =====================================================================
# $1 method, $2 endpoint (относительно /api/v1), $3 body, $4 имя команды.
# В stdout — тело ответа. В stderr — диагностика. Код возврата — контракт.
tracker_http_request() {
    local method="$1"
    local endpoint="$2"
    local body="${3:-}"
    local cmd_name="${4:-http}"

    local url="${PLANE_URL%/}/api/v1${endpoint}"
    local start_ts end_ts duration_ms http_status attempt retry_count=0
    local response_file headers_file
    response_file=$(mktemp)
    headers_file=$(mktemp)
    trap "rm -f '$response_file' '$headers_file'" RETURN

    # Пауза перед запросом, если задана (защита от 429 при серийных вызовах).
    if [ "${TRACKER_HTTP_MIN_INTERVAL}" != "0" ] && [ "${TRACKER_HTTP_MIN_INTERVAL}" != "" ]; then
        sleep "$TRACKER_HTTP_MIN_INTERVAL"
    fi

    for attempt in $(seq 1 $((TRACKER_HTTP_RETRIES + 1))); do
        start_ts=$(date +%s%N)

        # Токен НЕ передаётся через argv (BR-605): только через stdin в
        # curl --config -, иначе PAT виден в `ps -eo args` любому юзеру.
        local curl_rc=0
        if [ -n "$body" ]; then
            http_status=$(curl -sS -D "$headers_file" -o "$response_file" -w "%{http_code}" \
                --config - \
                -X "$method" \
                --max-time "$TRACKER_HTTP_TIMEOUT" \
                -H "Content-Type: application/json" \
                -H "Accept: application/json" \
                --data-binary "$body" \
                "$url" 2>/dev/null <<<"header = \"X-API-Key: ${PLANE_AI_TOKEN}\"") || curl_rc=$?
        else
            http_status=$(curl -sS -D "$headers_file" -o "$response_file" -w "%{http_code}" \
                --config - \
                -X "$method" \
                --max-time "$TRACKER_HTTP_TIMEOUT" \
                -H "Accept: application/json" \
                "$url" 2>/dev/null <<<"header = \"X-API-Key: ${PLANE_AI_TOKEN}\"") || curl_rc=$?
        fi

        # При недоступном сервере статус всегда приводим к "000".
        if [ "${curl_rc}" -ne 0 ] || ! [[ "${http_status}" =~ ^[0-9]{3}$ ]]; then
            http_status="000"
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
                echo "${C_RED}ERROR${C_RESET}: PLANE_AI_TOKEN истёк или отозван. Обновите токен в .env.local-tracker" >&2
                echo "  Создать новый: Plane → Workspace settings → API tokens" >&2
                return 3
                ;;
            429)
                if [ $retry_count -lt $TRACKER_HTTP_RETRIES ]; then
                    local retry_after
                    retry_after=$(awk -F': *' 'tolower($1)=="retry-after"{gsub("\r", "", $2); print $2}' "$headers_file" 2>/dev/null | tail -1)
                    local backoff=${TRACKER_HTTP_BACKOFFS[$retry_count]}
                    if [[ "${retry_after}" =~ ^[0-9]+$ ]] && [ "${retry_after}" -gt 0 ]; then
                        backoff=$((retry_after > 60 ? 60 : retry_after))
                    fi
                    echo "${C_YELLOW}WARN${C_RESET}: HTTP 429 (лимит Plane), retry $((retry_count + 1))/${TRACKER_HTTP_RETRIES} через ${backoff}s..." >&2
                    sleep "$backoff"
                    retry_count=$((retry_count + 1))
                    continue
                fi
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Rate limit exhausted" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: Plane API rate-limit (60 запросов/мин), попробуйте позже или уменьшите частоту polling" >&2
                return 4
                ;;
            4*)
                local err_msg
                err_msg=$(jq -r '.error_message // .message // .error // empty' "$response_file" 2>/dev/null | head -3 | paste -sd ';' -)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "${err_msg:-Client error}" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: HTTP ${http_status} ${method} ${endpoint}" >&2
                [ -n "$err_msg" ] && echo "  $err_msg" >&2
                return 5
                ;;
            5*)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "$http_status" "$duration_ms" "Server error" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: Plane ответил ${http_status}, попробуйте позже" >&2
                return 6
                ;;
            000)
                tracker_log_audit "$cmd_name" "${method} ${endpoint}" "000" "$duration_ms" "Connection failed" "$retry_count"
                echo "${C_RED}ERROR${C_RESET}: Plane недоступен по ${PLANE_URL}, проверьте сеть/URL (PLANE_URL)" >&2
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
# tracker_md_to_text — markdown → HTML
# =====================================================================
# Plane не принимает markdown: и `description_html`, и `comment_html` —
# это HTML. Историческое имя функции сохранено (её вызывают снаружи), но
# результат — HTML. Основной конвертер — python3 + markdown (те же
# расширения, что у переноса T3: таблицы, fenced code, списки). Без
# python3/markdown — деградация до экранированных абзацев, без падения.
tracker_md_to_text() {
    local md_file="$1"
    if [ ! -f "$md_file" ]; then
        echo "${C_RED}ERROR${C_RESET}: файл не найден: $md_file" >&2
        return 1
    fi

    if command -v python3 >/dev/null 2>&1 && python3 -c 'import markdown' >/dev/null 2>&1; then
        python3 - "$md_file" <<'PY'
import sys

import markdown

with open(sys.argv[1], encoding="utf-8") as fh:
    text = fh.read()
print(markdown.markdown(
    text,
    extensions=["fenced_code", "tables", "sane_lists", "nl2br"],
))
PY
        return $?
    fi

    # Fallback: экранирование и абзацы. Форматирование теряется, но текст
    # не превращается в HTML-инъекцию и команда остаётся рабочей.
    awk '
        BEGIN { buf = "" }
        {
            line = $0
            gsub(/&/, "\\&amp;", line)
            gsub(/</, "\\&lt;", line)
            gsub(/>/, "\\&gt;", line)
            if (line ~ /^[[:space:]]*$/) {
                if (buf != "") { print "<p>" buf "</p>"; buf = "" }
            } else {
                buf = (buf == "" ? line : buf " " line)
            }
        }
        END { if (buf != "") print "<p>" buf "</p>" }
    ' "$md_file"
}

# =====================================================================
# tracker_ws / tracker_project_id / tracker_project_path
# =====================================================================
tracker_ws() {
    printf '%s' "${PLANE_WORKSPACE}"
}

# tracker_project_id — UUID проекта по умолчанию.
tracker_project_id() {
    if [ -n "${TRACKER_PROJECT_ID_CACHE}" ]; then
        printf '%s' "${TRACKER_PROJECT_ID_CACHE}"
        return 0
    fi

    local pid="${PLANE_PROJECT_ID:-}"

    if [ -z "$pid" ] && [ -f "${TRACKER_PLANE_IDS}" ]; then
        pid=$(jq -r '.project_id // empty' "$TRACKER_PLANE_IDS" 2>/dev/null || true)
    fi

    if [ -z "$pid" ]; then
        local resp
        resp=$(tracker_http_request GET "/workspaces/$(tracker_ws)/projects/?per_page=100" "" "resolve-project") || return $?
        pid=$(printf '%s' "$resp" | jq -r --arg i "${PLANE_PROJECT_IDENTIFIER}" \
            '[.results[]? | select(((.identifier // "") | ascii_downcase) == ($i | ascii_downcase))][0].id // empty')
    fi

    if [ -z "$pid" ]; then
        echo "${C_RED}ERROR${C_RESET}: проект '${PLANE_PROJECT_IDENTIFIER}' не найден в workspace '$(tracker_ws)'" >&2
        echo "  Задайте PLANE_PROJECT_ID в .env.local-tracker (UUID проекта)" >&2
        return 5
    fi

    TRACKER_PROJECT_ID_CACHE="$pid"
    printf '%s' "$pid"
}

# tracker_project_path — префикс путей API для проекта.
tracker_project_path() {
    local pid
    pid=$(tracker_project_id) || return $?
    printf '/workspaces/%s/projects/%s' "$(tracker_ws)" "$pid"
}

# =====================================================================
# Метаданные проекта: состояния, метки, участники
# =====================================================================
tracker_states_json() {
    if [ -n "${TRACKER_STATES_JSON_CACHE}" ]; then
        printf '%s' "${TRACKER_STATES_JSON_CACHE}"
        return 0
    fi
    local pp resp
    pp=$(tracker_project_path) || return $?
    resp=$(tracker_http_request GET "${pp}/states/?per_page=100" "" "states") || return $?
    TRACKER_STATES_JSON_CACHE=$(printf '%s' "$resp" | jq -c 'if type == "array" then . else (.results // []) end')
    printf '%s' "${TRACKER_STATES_JSON_CACHE}"
}

tracker_labels_json() {
    if [ -n "${TRACKER_LABELS_JSON_CACHE}" ]; then
        printf '%s' "${TRACKER_LABELS_JSON_CACHE}"
        return 0
    fi
    local pp resp
    pp=$(tracker_project_path) || return $?
    resp=$(tracker_http_request GET "${pp}/labels/?per_page=100" "" "labels") || return $?
    TRACKER_LABELS_JSON_CACHE=$(printf '%s' "$resp" | jq -c 'if type == "array" then . else (.results // []) end')
    printf '%s' "${TRACKER_LABELS_JSON_CACHE}"
}

tracker_members_json() {
    if [ -n "${TRACKER_MEMBERS_JSON_CACHE}" ]; then
        printf '%s' "${TRACKER_MEMBERS_JSON_CACHE}"
        return 0
    fi
    local resp
    resp=$(tracker_http_request GET "/workspaces/$(tracker_ws)/members/?per_page=100" "" "members") || return $?
    TRACKER_MEMBERS_JSON_CACHE=$(printf '%s' "$resp" | jq -c 'if type == "array" then . else (.results // []) end')
    printf '%s' "${TRACKER_MEMBERS_JSON_CACHE}"
}

# tracker_state_id_by_name <имя> [группа] — UUID состояния или пусто.
tracker_state_id_by_name() {
    local name="$1"
    local group="${2:-}"
    local states
    states=$(tracker_states_json) || return $?
    printf '%s' "$states" | jq -r --arg n "$name" --arg g "$group" \
        '[.[] | select((.name // "") == $n and ($g == "" or (.group // "") == $g))][0].id // empty'
}

# tracker_state_name_by_id <uuid> — имя состояния или пусто.
tracker_state_name_by_id() {
    local id="$1"
    local states
    states=$(tracker_states_json) || return $?
    printf '%s' "$states" | jq -r --arg i "$id" \
        '[.[] | select(.id == $i)][0].name // empty'
}

# tracker_label_id <имя|uuid> — UUID метки.
# Принимает `type task`, `type:task`, `task` или UUID.
tracker_label_id() {
    local ref="$1"
    local labels
    labels=$(tracker_labels_json) || return $?

    if [[ "$ref" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
        printf '%s' "$ref"
        return 0
    fi

    local id
    id=$(printf '%s' "$labels" | jq -r --arg r "$ref" '
        [.[] | select(((.name // "") == $r) or ((.name // "") == ("type:" + $r)))][0].id // empty')
    if [ -z "$id" ]; then
        echo "${C_RED}ERROR${C_RESET}: метка '${ref}' не найдена в проекте" >&2
        echo "  Доступные: $(printf '%s' "$labels" | jq -r '[.[].name] | join(", ")')" >&2
        return 5
    fi
    printf '%s' "$id"
}

# tracker_member_id <uuid|display_name|email> — UUID участника workspace.
tracker_member_id() {
    local ref="$1"
    if [[ "$ref" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
        printf '%s' "$ref"
        return 0
    fi

    local members id
    members=$(tracker_members_json) || return $?
    id=$(printf '%s' "$members" | jq -r --arg r "$ref" '
        [.[] | select((((.display_name // "") | ascii_downcase) == ($r | ascii_downcase))
                      or (((.email // "") | ascii_downcase) == ($r | ascii_downcase)))][0].id // empty')
    if [ -z "$id" ]; then
        echo "${C_RED}ERROR${C_RESET}: участник '${ref}' не найден в workspace '$(tracker_ws)'" >&2
        echo "  Участники: $(printf '%s' "$members" | jq -r '[.[].display_name] | join(", ")')" >&2
        return 5
    fi
    printf '%s' "$id"
}

# tracker_member_name <uuid> — display_name или сам uuid.
tracker_member_name() {
    local id="$1"
    local members name
    members=$(tracker_members_json 2>/dev/null) || { printf '%s' "$id"; return 0; }
    name=$(printf '%s' "$members" | jq -r --arg i "$id" \
        '[.[] | select(.id == $i)][0].display_name // empty')
    printf '%s' "${name:-$id}"
}

# =====================================================================
# Соответствие OpenProject → Plane (форма `op:403`)
# =====================================================================
# tracker_map_lookup <op|seq> <ключ> — issue_id (UUID) или пусто.
# Поддерживаются CSV (по умолчанию knowledge/system/infra/op-plane-mapping.csv)
# и JSON вида {"<op_id>": {"issue_id": ..., "sequence_id": ...}}.
tracker_map_lookup() {
    local mode="$1"
    local key="$2"
    local map_file="${TRACKER_OP_MAP}"

    [ -f "$map_file" ] || return 0

    if ! command -v python3 >/dev/null 2>&1; then
        return 0
    fi

    python3 - "$map_file" "$mode" "$key" <<'PY' 2>/dev/null || true
import csv
import json
import sys

path, mode, key = sys.argv[1], sys.argv[2], sys.argv[3]
rows = {}

if path.endswith(".json"):
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    for op_id, value in data.items():
        if isinstance(value, dict):
            rows[str(op_id)] = (value.get("issue_id") or value.get("plane_id"),
                                value.get("sequence_id"))
        else:
            rows[str(op_id)] = (value, None)
else:
    with open(path, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            op_id = (row.get("op_id") or row.get("op") or row.get("openproject_id")
                     or row.get("openproject"))
            plane_id = (row.get("plane_id") or row.get("issue_id")
                        or row.get("plane_uuid") or row.get("issue"))
            seq = row.get("sequence_id") or row.get("plane_seq") or row.get("seq")
            if op_id:
                rows[str(op_id)] = (plane_id, seq)

if mode == "op":
    value = rows.get(key, (None, None))[0]
else:
    value = next((v[0] for v in rows.values() if str(v[1]) == str(key)), None)
print(value or "")
PY
}

# =====================================================================
# tracker_find_issue_by_sequence — UUID по sequence_id (скан страниц)
# =====================================================================
# Фильтры списка задач этого инстанса Plane игнорируются (проверено:
# ?assignees=/?state=/?priority= не сужают выдачу), поэтому и поиск, и
# list-issues фильтруют выдачу на клиенте.
tracker_find_issue_by_sequence() {
    local seq="$1"
    local pp cursor="" pages=0 resp id

    pp=$(tracker_project_path) || return $?

    while :; do
        local query="per_page=100"
        if [ -n "$cursor" ]; then
            query="${query}&cursor=$(printf '%s' "$cursor" | jq -sRr @uri)"
        fi
        resp=$(tracker_http_request GET "${pp}/issues/?${query}" "" "resolve-issue") || return $?
        id=$(printf '%s' "$resp" | jq -r --argjson s "$seq" \
            '[(if type == "array" then .[] else (.results // [])[] end) | select(.sequence_id == $s)][0].id // empty')
        if [ -n "$id" ]; then
            printf '%s' "$id"
            return 0
        fi

        [ "$(printf '%s' "$resp" | jq -r '.next_page_results // false')" = "true" ] || break
        cursor=$(printf '%s' "$resp" | jq -r '.next_cursor // empty')
        [ -n "$cursor" ] || break
        pages=$((pages + 1))
        [ $pages -lt 20 ] || break
    done

    echo "${C_RED}ERROR${C_RESET}: задача KARAOKE-${seq} не найдена в проекте" >&2
    return 5
}

# =====================================================================
# tracker_resolve_issue_id — ссылка → UUID задачи
# =====================================================================
# Принимает `42`, `KARAOKE-42`, `#42`, UUID, `op:403`.
tracker_resolve_issue_id() {
    local ref="$1"

    if [[ "$ref" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
        printf '%s' "$ref"
        return 0
    fi

    if [[ "$ref" =~ ^[Oo][Pp]:([0-9]+)$ ]]; then
        local op_id="${BASH_REMATCH[1]}"
        local mapped
        mapped=$(tracker_map_lookup op "$op_id")
        if [ -z "$mapped" ]; then
            echo "${C_RED}ERROR${C_RESET}: OpenProject #${op_id} не найден в таблице соответствия" >&2
            echo "  TRACKER_OP_MAP=${TRACKER_OP_MAP} (CSV или JSON; см. docs/tracker-setup.md)" >&2
            return 1
        fi
        printf '%s' "$mapped"
        return 0
    fi

    local seq=""
    if [[ "$ref" =~ ^[Kk][Aa][Rr][Aa][Oo][Kk][Ee]-?([0-9]+)$ ]]; then
        seq="${BASH_REMATCH[1]}"
    elif [[ "$ref" =~ ^#?([0-9]+)$ ]]; then
        seq="${BASH_REMATCH[1]}"
    else
        echo "${C_RED}ERROR${C_RESET}: неверный ID задачи: '${ref}'" >&2
        echo "  Ожидается: 42 | KARAOKE-42 | #42 | op:403 | UUID" >&2
        return 1
    fi

    # Сначала таблица соответствия (без запросов к API), затем скан списка.
    local mapped
    mapped=$(tracker_map_lookup seq "$seq")
    if [ -n "$mapped" ]; then
        printf '%s' "$mapped"
        return 0
    fi

    tracker_find_issue_by_sequence "$seq"
}

# =====================================================================
# tracker_healthcheck — GET /users/me/ + GET .../issues/?per_page=1
# =====================================================================
tracker_healthcheck() {
    local me pp resp who
    local start_ts end_ts duration_ms

    start_ts=$(date +%s%N)
    me=$(tracker_http_request GET "/users/me/" "" "healthcheck-me") || return $?
    pp=$(tracker_project_path) || return $?
    resp=$(tracker_http_request GET "${pp}/issues/?per_page=1" "" "healthcheck-issues") || return $?
    end_ts=$(date +%s%N)
    duration_ms=$(( (end_ts - start_ts) / 1000000 ))

    who=$(printf '%s' "$me" | jq -r '.display_name // .email // "unknown"')

    echo "${C_GREEN}OK${C_RESET}: Plane UP at ${PLANE_URL} (user: ${who}, workspace: $(tracker_ws), response: ${duration_ms}ms)"
    return 0
}

# =====================================================================
# tracker_list_projects — GET /workspaces/{ws}/projects/
# =====================================================================
tracker_list_projects() {
    local cursor="" pages=0 resp out="[]"

    while :; do
        local query="per_page=100"
        if [ -n "$cursor" ]; then
            query="${query}&cursor=$(printf '%s' "$cursor" | jq -sRr @uri)"
        fi
        resp=$(tracker_http_request GET "/workspaces/$(tracker_ws)/projects/?${query}" "" "list-projects") || return $?
        out=$(printf '%s' "$resp" | jq -c --argjson acc "$out" '$acc + (if type == "array" then . else (.results // []) end)') || return 5
        [ "$(printf '%s' "$resp" | jq -r '.next_page_results // false')" = "true" ] || break
        cursor=$(printf '%s' "$resp" | jq -r '.next_cursor // empty')
        [ -n "$cursor" ] || break
        pages=$((pages + 1))
        [ $pages -lt 10 ] || break
    done

    printf '%s' "$out" | jq -r '
        ["ID", "IDENTIFIER", "NAME", "CREATED"] as $headers |
        ($headers, (.[] | [.id, (.identifier // "-"), (.name // "-"), (.created_at // "-")])) | @tsv
    ' | column -t -s $'\t'
}

# =====================================================================
# tracker_list_issues — список задач проекта
# =====================================================================
# Аргументы (позиционные, как в старом CLI):
#   $1 project_id (UUID; пусто — проект по умолчанию)
#   $2 assignee   (display_name/email/UUID; пусто — все)
#   $3 status     (open|closed|in-review|<имя состояния>; пусто — все)
#   $4 limit
#   $5 raw        (1 — TSV без `column -t`)
tracker_list_issues() {
    local project_id="${1:-}"
    local assignee="${2:-}"
    local status="${3:-}"
    local limit="${4:-50}"
    local raw="${5:-0}"

    local pid pp
    if [ -n "$project_id" ]; then
        pid="$project_id"
    else
        pid=$(tracker_project_id) || return $?
    fi
    pp="/workspaces/$(tracker_ws)/projects/${pid}"

    local states labels members
    states=$(tracker_states_json) || return $?
    labels=$(tracker_labels_json) || return $?

    local allowed_json="[]"
    case "$status" in
        ""|all|"*")
            allowed_json="[]"
            ;;
        open|to_do|"To Do")
            allowed_json=$(printf '%s' "$states" | jq -c \
                '[.[] | select((.group // "") != "completed" and (.group // "") != "cancelled") | .id]')
            ;;
        closed|done|"Done"|"Closed")
            allowed_json=$(printf '%s' "$states" | jq -c \
                '[.[] | select((.group // "") == "completed") | .id]')
            ;;
        in-review|in_review)
            # Состояние In review появляется только после первого mark-review
            # (тикет 16). Если его ещё нет — задач в нём нет: пустой список,
            # а не ошибка (poll не должен шуметь до первого mark-review).
            local rid
            rid=$(printf '%s' "$states" | jq -r '[.[] | select(.name == "In review")][0].id // empty')
            if [ -z "$rid" ]; then
                if [ "$raw" = "1" ]; then
                    printf 'ID\tSUBJECT\tSTATUS\tASSIGNEE\tTYPE\n'
                else
                    printf 'ID\tSUBJECT\tSTATUS\tASSIGNEE\tTYPE\n' | column -t -s $'\t'
                fi
                return 0
            fi
            allowed_json=$(jq -cn --arg i "$rid" '[$i]')
            ;;
        *)
            local sid
            sid=$(printf '%s' "$states" | jq -r --arg s "$status" \
                '[.[] | select((.name // "") == $s)][0].id // empty')
            if [ -z "$sid" ]; then
                echo "${C_RED}ERROR${C_RESET}: состояние '${status}' не найдено в проекте" >&2
                echo "  Доступные: $(printf '%s' "$states" | jq -r '[.[].name] | join(", ")')" >&2
                return 5
            fi
            allowed_json=$(jq -cn --arg i "$sid" '[$i]')
            ;;
    esac

    local assignee_id=""
    if [ -n "$assignee" ]; then
        assignee_id=$(tracker_member_id "$assignee") || return $?
    fi

    local state_map member_map label_map
    state_map=$(printf '%s' "$states" | jq -c 'map({key: .id, value: .name}) | from_entries')
    label_map=$(printf '%s' "$labels" | jq -c 'map({key: .id, value: .name}) | from_entries')
    members=$(tracker_members_json) || return $?
    member_map=$(printf '%s' "$members" | jq -c 'map({key: .id, value: .display_name}) | from_entries')

    local rows_file
    rows_file=$(mktemp)

    local cursor="" pages=0 resp count=0
    while :; do
        local query="per_page=100"
        if [ -n "$cursor" ]; then
            query="${query}&cursor=$(printf '%s' "$cursor" | jq -sRr @uri)"
        fi
        resp=$(tracker_http_request GET "${pp}/issues/?${query}" "" "list-issues") || { rm -f "$rows_file"; return $?; }

        printf '%s' "$resp" | jq -r \
            --argjson allowed "$allowed_json" \
            --arg aid "$assignee_id" \
            --argjson states "$state_map" \
            --argjson members "$member_map" \
            --argjson labels "$label_map" '
            (if type == "array" then .[] else (.results // [])[] end) as $r
            | select(($allowed | length) == 0 or ($allowed | index($r.state)) != null)
            | select($aid == "" or (([$r.assignees[]?] | index($aid)) != null))
            | [ ($r.sequence_id | tostring),
                (($r.name // "-") | gsub("[\t\n\r]"; " ")),
                ($states[$r.state] // "-"),
                (([$r.assignees[]? | $members[.] // "-"] | join(","))
                    | if . == "" then "unassigned" else . end),
                (([$r.labels[]? | $labels[.] // empty | select(startswith("type:")) | ltrimstr("type:")][0]) // "-")
              ] | @tsv
        ' >> "$rows_file" || { rm -f "$rows_file"; return 5; }

        count=$(wc -l < "$rows_file")
        [ "$count" -ge "$limit" ] && break

        [ "$(printf '%s' "$resp" | jq -r '.next_page_results // false')" = "true" ] || break
        cursor=$(printf '%s' "$resp" | jq -r '.next_cursor // empty')
        [ -n "$cursor" ] || break
        pages=$((pages + 1))
        [ $pages -lt 20 ] || break
    done

    {
        printf 'ID\tSUBJECT\tSTATUS\tASSIGNEE\tTYPE\n'
        head -n "$limit" "$rows_file"
    } | if [ "$raw" = "1" ]; then
            cat
        else
            column -t -s $'\t'
        fi

    rm -f "$rows_file"
    return 0
}

# =====================================================================
# tracker_issue_comments_json — комментарии задачи (JSON-массив)
# =====================================================================
tracker_issue_comments_json() {
    local id="$1"
    local pp cursor="" pages=0 resp out="[]"

    pp=$(tracker_project_path) || return $?

    while :; do
        local query="per_page=100"
        if [ -n "$cursor" ]; then
            query="${query}&cursor=$(printf '%s' "$cursor" | jq -sRr @uri)"
        fi
        resp=$(tracker_http_request GET "${pp}/issues/${id}/comments/?${query}" "" "issue-comments") || return $?
        out=$(printf '%s' "$resp" | jq -c --argjson acc "$out" '$acc + (if type == "array" then . else (.results // []) end)') || return 5
        [ "$(printf '%s' "$resp" | jq -r '.next_page_results // false')" = "true" ] || break
        cursor=$(printf '%s' "$resp" | jq -r '.next_cursor // empty')
        [ -n "$cursor" ] || break
        pages=$((pages + 1))
        [ $pages -lt 10 ] || break
    done

    printf '%s' "$out"
}

# =====================================================================
# tracker_get_issue — JSON задачи + комментарии (совместимые поля)
# =====================================================================
tracker_get_issue() {
    local ref="$1"
    local id pp resp comments states labels members url

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    resp=$(tracker_http_request GET "${pp}/issues/${id}/" "" "get-issue") || return $?
    comments=$(tracker_issue_comments_json "$id") || return $?
    states=$(tracker_states_json) || return $?
    labels=$(tracker_labels_json) || return $?
    members=$(tracker_members_json) || return $?

    url="${PLANE_URL}/${PLANE_WORKSPACE}/projects/${PLANE_PROJECT_IDENTIFIER}/issues/$(printf '%s' "$resp" | jq -r '.sequence_id')"

    local state_map label_map member_map
    state_map=$(printf '%s' "$states" | jq -c 'map({key: .id, value: .name}) | from_entries')
    label_map=$(printf '%s' "$labels" | jq -c 'map({key: .id, value: .name}) | from_entries')
    member_map=$(printf '%s' "$members" | jq -c 'map({key: .id, value: .display_name}) | from_entries')

    printf '%s' "$resp" | jq \
        --argjson comments "$comments" \
        --argjson states "$state_map" \
        --argjson labels "$label_map" \
        --argjson members "$member_map" \
        --arg url "$url" '
        {
            id: .id,
            key: ("KARAOKE-" + (.sequence_id | tostring)),
            sequence_id: .sequence_id,
            subject: .name,
            description: (.description_html // ""),
            description_html: (.description_html // ""),
            type: (([.labels[]? | $labels[.] // empty | select(startswith("type:")) | ltrimstr("type:")][0]) // "-"),
            status: ($states[.state] // .state // "unknown"),
            priority: (.priority // "none"),
            assignee: (([.assignees[]? | $members[.] // empty] | join(",")) | if . == "" then null else . end),
            author: ($members[.created_by] // .created_by),
            createdAt: .created_at,
            updatedAt: .updated_at,
            project: .project,
            url: $url,
            comments: ($comments | map({
                id: .id,
                author: ($members[.actor] // .actor),
                createdAt: .created_at,
                body_html: (.comment_html // "")
            }))
        }'
}

# =====================================================================
# tracker_create_issue — POST .../issues/
# =====================================================================
tracker_create_issue() {
    local project_id="$1"
    local type_id="$2"
    local subject="$3"
    local description="${4:-}"

    local pid pp label_uuid desc_html=""
    if [ -n "$project_id" ]; then
        pid="$project_id"
    else
        pid=$(tracker_project_id) || return $?
    fi
    pp="/workspaces/$(tracker_ws)/projects/${pid}"

    label_uuid=$(tracker_label_id "$type_id") || return $?

    if [ -n "$description" ]; then
        local tmp_md
        tmp_md=$(mktemp)
        printf '%s' "$description" > "$tmp_md"
        desc_html=$(tracker_md_to_text "$tmp_md") || { rm -f "$tmp_md"; return 1; }
        rm -f "$tmp_md"
    fi

    # Plane отвечает 400 на пустой description_html — поле добавляется
    # только когда описание реально есть.
    local payload resp seq uuid
    if [ -n "$desc_html" ]; then
        payload=$(jq -n \
            --arg name "$subject" \
            --arg html "$desc_html" \
            --arg label "$label_uuid" \
            '{name: $name, description_html: $html, priority: "medium", labels: [$label]}')
    else
        payload=$(jq -n \
            --arg name "$subject" \
            --arg label "$label_uuid" \
            '{name: $name, priority: "medium", labels: [$label]}')
    fi

    resp=$(tracker_http_request POST "${pp}/issues/" "$payload" "create-issue") || return $?

    seq=$(printf '%s' "$resp" | jq -r '.sequence_id // empty')
    uuid=$(printf '%s' "$resp" | jq -r '.id // empty')

    if [ -z "$uuid" ]; then
        echo "${C_RED}ERROR${C_RESET}: Plane не вернул id созданной задачи" >&2
        return 6
    fi

    echo "${C_GREEN}OK${C_RESET}: issue KARAOKE-${seq} created (#${seq}, id: ${uuid})"
}

# =====================================================================
# tracker_claim_issue — assignee + состояние In progress
# =====================================================================
tracker_claim_issue() {
    local ref="$1"
    local id pp agent_id state_id

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    agent_id=$(tracker_member_id "${TRACKER_AGENT_USER}") || return $?

    state_id=$(tracker_state_id_by_name "In progress") || return $?
    if [ -z "$state_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: состояние 'In progress' не найдено в проекте" >&2
        return 5
    fi

    local payload
    payload=$(jq -n --arg a "$agent_id" --arg s "$state_id" \
        '{assignees: [$a], state: $s}')

    tracker_http_request PATCH "${pp}/issues/${id}/" "$payload" "claim-issue" >/dev/null || return $?

    echo "${C_GREEN}OK${C_RESET}: #${ref} claimed by ${TRACKER_AGENT_USER} (status: In progress)"
}

# =====================================================================
# tracker_add_comment — POST .../issues/{id}/comments/
# =====================================================================
tracker_add_comment() {
    local ref="$1"
    local file="$2"

    if [ ! -f "$file" ]; then
        echo "${C_RED}ERROR${C_RESET}: файл не найден: $file" >&2
        return 1
    fi

    local id pp comment_html payload resp comment_id body_length
    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    comment_html=$(tracker_md_to_text "$file") || return 1

    payload=$(jq -n --arg html "$comment_html" '{comment_html: $html}')
    resp=$(tracker_http_request POST "${pp}/issues/${id}/comments/" "$payload" "add-comment") || return $?

    comment_id=$(printf '%s' "$resp" | jq -r '.id // "?"')
    body_length=$(wc -c < "$file")

    echo "${C_GREEN}OK${C_RESET}: comment added to #${ref} (id: ${comment_id}, html: ${#comment_html} bytes, md: ${body_length} bytes)"
}

# =====================================================================
# tracker_close_issue — состояние Done
# =====================================================================
tracker_close_issue() {
    local ref="$1"
    local id pp resp current_name state_id

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    resp=$(tracker_http_request GET "${pp}/issues/${id}/" "" "close-issue-get") || return $?
    current_name=$(tracker_state_name_by_id "$(printf '%s' "$resp" | jq -r '.state')") || return $?

    if [ "$current_name" = "Done" ]; then
        echo "${C_YELLOW}WARN${C_RESET}: #${ref} уже в состоянии Done" >&2
        return 0
    fi

    state_id=$(tracker_state_id_by_name "Done") || return $?
    if [ -z "$state_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: состояние 'Done' не найдено в проекте" >&2
        return 5
    fi

    local payload
    payload=$(jq -n --arg s "$state_id" '{state: $s}')
    tracker_http_request PATCH "${pp}/issues/${id}/" "$payload" "close-issue" >/dev/null || return $?

    echo "${C_GREEN}OK${C_RESET}: #${ref} closed"
}

# =====================================================================
# tracker_mark_review — состояние In review
# =====================================================================
# Решение тикета 16: «работа готова к проверке» — отдельное состояние
# In review (группа started). Если в проекте его ещё нет — создаётся
# здесь (повторный вызов находит существующее).
tracker_mark_review() {
    local ref="$1"
    local id pp resp current_name state_id

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    resp=$(tracker_http_request GET "${pp}/issues/${id}/" "" "mark-review-get") || return $?
    current_name=$(tracker_state_name_by_id "$(printf '%s' "$resp" | jq -r '.state')") || return $?

    if [ "$current_name" = "In review" ]; then
        echo "${C_YELLOW}WARN${C_RESET}: #${ref} уже в состоянии In review" >&2
        return 0
    fi

    state_id=$(tracker_state_id_by_name "In review") || return $?
    if [ -z "$state_id" ]; then
        # По умолчанию состояние НЕ создаётся: tools на этом этапе не меняют
        # настройки проекта. Включить автосоздание можно осознанно —
        # TRACKER_CREATE_REVIEW_STATE=1 (решение тикета 16: группа started).
        if [ "${TRACKER_CREATE_REVIEW_STATE:-0}" != "1" ]; then
            echo "${C_RED}ERROR${C_RESET}: состояние 'In review' не найдено в проекте" >&2
            echo "  Создайте его один раз: Plane → проект → States → + State 'In review' (группа Started)," >&2
            echo "  либо повторите с TRACKER_CREATE_REVIEW_STATE=1 (тогда состояние создаст CLI)." >&2
            return 5
        fi
        local create_payload create_resp
        create_payload=$(jq -n '{name: "In review", group: "started", color: "#f59e0b"}')
        create_resp=$(tracker_http_request POST "${pp}/states/" "$create_payload" "mark-review-create-state") || return $?
        state_id=$(printf '%s' "$create_resp" | jq -r '.id // empty')
        if [ -z "$state_id" ]; then
            echo "${C_RED}ERROR${C_RESET}: не удалось создать состояние 'In review'" >&2
            return 5
        fi
        # Кэш метаданных устарел — сбрасываем.
        TRACKER_STATES_JSON_CACHE=""
    fi

    local payload
    payload=$(jq -n --arg s "$state_id" '{state: $s}')
    tracker_http_request PATCH "${pp}/issues/${id}/" "$payload" "mark-review" >/dev/null || return $?

    echo "${C_GREEN}OK${C_RESET}: #${ref} → In review (готов к проверке пользователем)"
}

# =====================================================================
# tracker_reopen_issue — состояние In progress (fallback New)
# =====================================================================
tracker_reopen_issue() {
    local ref="$1"
    local id pp state_id

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    state_id=$(tracker_state_id_by_name "In progress") || return $?
    if [ -z "$state_id" ]; then
        state_id=$(tracker_state_id_by_name "New") || return $?
    fi
    if [ -z "$state_id" ]; then
        echo "${C_RED}ERROR${C_RESET}: состояние для reopen ('In progress'/'New') не найдено" >&2
        return 5
    fi

    local payload
    payload=$(jq -n --arg s "$state_id" '{state: $s}')
    tracker_http_request PATCH "${pp}/issues/${id}/" "$payload" "reopen-issue" >/dev/null || return $?

    echo "${C_GREEN}OK${C_RESET}: #${ref} reopened"
}

# =====================================================================
# tracker_delete_issue — DELETE .../issues/{id}/
# =====================================================================
# Нужен smoke-тесту: тестовая задача удаляется, а не закрывается, чтобы
# не засорять проект (и не попадать в отчёты сверки T3).
tracker_delete_issue() {
    local ref="$1"
    local id pp

    id=$(tracker_resolve_issue_id "$ref") || return $?
    pp=$(tracker_project_path) || return $?

    tracker_http_request DELETE "${pp}/issues/${id}/" "" "delete-issue" >/dev/null || return $?

    echo "${C_GREEN}OK${C_RESET}: #${ref} deleted"
}
