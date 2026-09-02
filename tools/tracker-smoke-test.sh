#!/usr/bin/env bash
# =====================================================================
# tools/tracker-smoke-test.sh — end-to-end проверка OpenProject
# =====================================================================
# Запускается после установки для проверки работоспособности.
#
# 8 шагов:
#   1. healthcheck
#   2. list-projects (есть хотя бы 1?)
#   3. create-issue (smoke-test task)
#   4. get-issue
#   5. claim-issue (assignee + status → In progress)
#   6. add-comment
#   7. close-issue
#   8. final healthcheck
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
TRACKER_SH="${SCRIPT_DIR}/tracker.sh"

if [ -t 1 ]; then
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_RED=$'\033[0;31m'
    C_RESET=$'\033[0m'
else
    C_GREEN=""
    C_YELLOW=""
    C_RED=""
    C_RESET=""
fi

ENV_FILE="${REPO_ROOT}/.env.local-tracker"
if [ ! -f "$ENV_FILE" ]; then
    echo "${C_RED}ERROR${C_RESET}: ${ENV_FILE} не найден. Запустите install-tracker.sh сначала." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ -z "${TRACKER_API_TOKEN:-}" ] || [ "$TRACKER_API_TOKEN" = "placeholder_will_be_set_after_first_run_ui_setup" ]; then
    echo "${C_RED}ERROR${C_RESET}: TRACKER_API_TOKEN не заполнен в ${ENV_FILE}" >&2
    echo "  Создайте API token в OpenProject UI (My Account → Access Tokens) и обновите .env.local-tracker" >&2
    exit 1
fi

PASS=0
FAIL=0
CREATED_ID=""

step_pass() { echo "${C_GREEN}[$1/$TOTAL]${C_RESET} $2: ${C_GREEN}OK${C_RESET}"; PASS=$((PASS + 1)); }
step_fail() { echo "${C_RED}[$1/$TOTAL]${C_RESET} $2: ${C_RED}FAIL${C_RESET}"; [ -n "${3:-}" ] && echo "  $3"; FAIL=$((FAIL + 1)); }

TOTAL=8

echo "${C_YELLOW}═══ OpenProject smoke-test v295.2.0 ═══${C_RESET}"
echo ""

# --- Шаг 1: healthcheck ---
if "$TRACKER_SH" healthcheck >/dev/null 2>&1; then
    step_pass 1 "healthcheck"
else
    step_fail 1 "healthcheck" "OpenProject недоступен. Проверьте: docker ps | grep openproject"
    exit 1
fi

# --- Шаг 2: list-projects ---
PROJECTS=$("$TRACKER_SH" list-projects 2>/dev/null || echo "")
PROJECTS_LINES=$(echo "$PROJECTS" | grep -v "^ID" | grep -v "^$" | wc -l)

if [ "$PROJECTS_LINES" -gt 0 ]; then
    step_pass 2 "list-projects (найдено проектов: $PROJECTS_LINES)"
    # Берём первый project ID (число в начале строки после ID-заголовка)
    FIRST_PROJECT_ID=$(echo "$PROJECTS" | tail -n +2 | head -1 | awk '{print $1}')
else
    step_fail 2 "list-projects (нет проектов)" "Создайте хотя бы один проект в OpenProject UI"
    exit 1
fi

# --- Получаем type ID для "Task" ---
echo "  ... получаем type ID для 'Task'..."
# ВАЖНО: OpenProject Basic Auth требует username='apikey' (см. UserBasicAuth warden-strategy)
TYPES_RESPONSE=$(curl -fsS -u "apikey:${TRACKER_API_TOKEN}" \
    "${TRACKER_URL}/api/v3/types?pageSize=100" 2>/dev/null || echo "{}")
TASK_TYPE_ID=$(echo "$TYPES_RESPONSE" | jq -r '._embedded.elements[]? | select(.name == "Task" or .title == "Task") | .id' | head -1)

if [ -z "$TASK_TYPE_ID" ]; then
    # Берём первый доступный тип
    TASK_TYPE_ID=$(echo "$TYPES_RESPONSE" | jq -r '._embedded.elements[0]?.id // empty')
fi

if [ -z "$TASK_TYPE_ID" ]; then
    step_fail 3 "create-issue (нет типа Task)" "Создайте тип 'Task' в Administration → Types"
    exit 1
fi

# --- Шаг 3: create-issue ---
SUBJECT="smoke-test-$(date +%Y%m%d-%H%M%S)"
DESCRIPTION="Автотестовая задача, созданная через tools/tracker-smoke-test.sh. Должна быть удалена после теста."

CREATE_OUTPUT=$("$TRACKER_SH" create-issue \
    --project-id "$FIRST_PROJECT_ID" \
    --type-id "$TASK_TYPE_ID" \
    --subject "$SUBJECT" \
    --description "$DESCRIPTION" 2>&1 || echo "")

if echo "$CREATE_OUTPUT" | grep -qE "OK: work package #[0-9]+ created"; then
    CREATED_ID=$(echo "$CREATE_OUTPUT" | grep -oE "#[0-9]+" | head -1 | tr -d '#')
    step_pass 3 "create-issue (#${CREATED_ID})"
else
    step_fail 3 "create-issue" "$CREATE_OUTPUT"
    exit 1
fi

# --- Шаг 4: get-issue ---
GET_OUTPUT=$("$TRACKER_SH" get-issue "$CREATED_ID" 2>&1 || echo "")

if echo "$GET_OUTPUT" | grep -q "\"id\": ${CREATED_ID}"; then
    step_pass 4 "get-issue #${CREATED_ID}"
else
    step_fail 4 "get-issue #${CREATED_ID}" "$GET_OUTPUT"
fi

# --- Шаг 5: claim-issue ---
CLAIM_OUTPUT=$("$TRACKER_SH" claim-issue "$CREATED_ID" 2>&1 || echo "")

if echo "$CLAIM_OUTPUT" | grep -qE "OK: #${CREATED_ID} claimed"; then
    step_pass 5 "claim-issue #${CREATED_ID}"
else
    step_fail 5 "claim-issue #${CREATED_ID}" "$CLAIM_OUTPUT"
fi

# --- Шаг 6: add-comment ---
TMP_REPORT=$(mktemp)
cat > "$TMP_REPORT" <<EOF
## Что сделано

Smoke-test: задача создана и взята через CLI.

## Изменённые файлы

- tests/smoke/tracker.md

## Прогон проверок

- tools/tracker-smoke-test.sh — ALL PASS

## Известные ограничения

Нет.
EOF

COMMENT_OUTPUT=$("$TRACKER_SH" add-comment "$CREATED_ID" --file "$TMP_REPORT" 2>&1 || echo "")

if echo "$COMMENT_OUTPUT" | grep -qE "OK: comment added to #${CREATED_ID}"; then
    step_pass 6 "add-comment #${CREATED_ID}"
else
    step_fail 6 "add-comment #${CREATED_ID}" "$COMMENT_OUTPUT"
fi

rm -f "$TMP_REPORT"

# --- Шаг 7: close-issue ---
CLOSE_OUTPUT=$("$TRACKER_SH" close-issue "$CREATED_ID" 2>&1 || echo "")

if echo "$CLOSE_OUTPUT" | grep -qE "OK: #${CREATED_ID} closed"; then
    step_pass 7 "close-issue #${CREATED_ID}"
else
    step_fail 7 "close-issue #${CREATED_ID}" "$CLOSE_OUTPUT"
fi

# --- Шаг 8: final healthcheck ---
if "$TRACKER_SH" healthcheck >/dev/null 2>&1; then
    step_pass 8 "final healthcheck"
else
    step_fail 8 "final healthcheck" "OpenProject упала во время теста"
fi

# --- Сводка ---
echo ""
echo "${C_YELLOW}═══════════════════════════════════════════════════════${C_RESET}"
if [ $FAIL -eq 0 ]; then
    echo "${C_GREEN}  ALL PASS — OpenProject полностью функционален${C_RESET}"
    echo "${C_GREEN}  $PASS/$TOTAL шагов OK${C_RESET}"
    exit 0
else
    echo "${C_RED}  $FAIL шагов провалилось${C_RESET}"
    echo "${C_RED}  $PASS/$TOTAL шагов OK${C_RESET}"
    echo ""
    echo "Созданная задача для отладки: #${CREATED_ID}"
    echo "Логи CLI: logs/tracker-agent.log"
    exit 1
fi
