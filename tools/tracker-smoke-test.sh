#!/usr/bin/env bash
# =====================================================================
# tools/tracker-smoke-test.sh — end-to-end проверка Plane
# =====================================================================
# Запускается после установки для проверки работоспособности.
#
# 8 шагов:
#   1. healthcheck (GET /users/me/ + задачи проекта)
#   2. list-projects (проект PLANE_PROJECT_IDENTIFIER найден)
#   3. create-issue (тестовая задача, метка type:task)
#   4. get-issue по sequence_id (проверка JSON и состояния New)
#   5. claim-issue (assignee + состояние In progress)
#   6. add-comment (markdown → HTML: таблица и блок кода сохраняются)
#   7. delete-issue (задача удалена, GET даёт ошибку)
#   8. final healthcheck
#
# Тестовая задача УДАЛЯЕТСЯ (DELETE), а не закрывается: закрытая задача
# засоряла бы проект и попадала в отчёты сверки переноса.
#
# BR-606 (аудит #336, пакет PKG-S3-30): под `set -euo pipefail` присваивание
# `PROJECTS_LINES=$(… | grep -v … | wc -l)` при пустом выводе УБИВАЛО скрипт:
# grep возвращает 1, когда ничего не напечатал, pipefail propagates код в
# пайп, errexit — в скрипт. Теперь пустой результат — диагностируемое
# состояние.
#
# Троттлинг: TRACKER_HTTP_MIN_INTERVAL (default 1.2 с) — у Plane лимит
# 60 запросов/мин на пользователя, тест делает ~30 запросов.
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

if [ -z "${PLANE_AI_TOKEN:-}" ] || [ "$PLANE_AI_TOKEN" = "placeholder_will_be_set_after_first_run_ui_setup" ]; then
    echo "${C_RED}ERROR${C_RESET}: PLANE_AI_TOKEN не заполнен в ${ENV_FILE}" >&2
    echo "  Создайте API token в Plane (Workspace settings → API tokens) и обновите .env.local-tracker" >&2
    exit 1
fi

# Троттлинг: не более ~50 запросов/мин, чтобы не упираться в лимит Plane.
export TRACKER_HTTP_MIN_INTERVAL="${TRACKER_HTTP_MIN_INTERVAL:-1.2}"

PLANE_PROJECT_IDENTIFIER="${PLANE_PROJECT_IDENTIFIER:-KARAOKE}"

PASS=0
FAIL=0
CREATED_ID=""
CREATED_UUID=""
CLEANED=0

cleanup() {
    # Страховка: если тест упал после create-issue — удалить тестовую задачу.
    if [ -n "$CREATED_UUID" ] && [ "$CLEANED" -eq 0 ]; then
        echo "${C_YELLOW}cleanup${C_RESET}: удаляю тестовую задачу ${CREATED_UUID}" >&2
        "$TRACKER_SH" delete-issue "$CREATED_UUID" >/dev/null 2>&1 || true
        CLEANED=1
    fi
}
trap cleanup EXIT

step_pass() { echo "${C_GREEN}[$1/$TOTAL]${C_RESET} $2: ${C_GREEN}OK${C_RESET}"; PASS=$((PASS + 1)); }
step_fail() { echo "${C_RED}[$1/$TOTAL]${C_RESET} $2: ${C_RED}FAIL${C_RESET}"; [ -n "${3:-}" ] && echo "  $3"; FAIL=$((FAIL + 1)); }

TOTAL=8

echo "${C_YELLOW}=== Plane smoke-test v296.0.0 ===${C_RESET}"
echo ""

# --- Шаг 1: healthcheck ---
if "$TRACKER_SH" healthcheck >/dev/null 2>&1; then
    step_pass 1 "healthcheck"
else
    step_fail 1 "healthcheck" "Plane недоступен. Проверьте PLANE_URL и сеть"
    exit 1
fi

# --- Шаг 2: list-projects ---
PROJECTS=$("$TRACKER_SH" list-projects 2>/dev/null || echo "")
# BR-606: `grep` без совпадений возвращает 1 → под pipefail это убивало
# скрипт без диагностики. `|| true` + пустой результат = «проектов нет».
PROJECTS_LINES=$(printf '%s\n' "${PROJECTS}" | tail -n +2 | grep -v '^$' | wc -l || true)

if [ "${PROJECTS_LINES}" -gt 0 ]; then
    step_pass 2 "list-projects (найдено проектов: $PROJECTS_LINES)"
    FIRST_PROJECT_ID=$(printf '%s\n' "${PROJECTS}" | awk -v ident="$PLANE_PROJECT_IDENTIFIER" \
        'NR > 1 && $2 == ident { print $1; exit }' || true)
    if [ -z "$FIRST_PROJECT_ID" ]; then
        FIRST_PROJECT_ID=$(printf '%s\n' "${PROJECTS}" | tail -n +2 | head -1 | awk '{print $1}' || true)
    fi
else
    step_fail 2 "list-projects (нет проектов)" "Создайте проект в Plane UI"
    exit 1
fi

if [ -z "${FIRST_PROJECT_ID:-}" ]; then
    step_fail 2 "list-projects (не удалось разобрать ID проекта)" "$PROJECTS"
    exit 1
fi

# --- Шаг 3: create-issue ---
SUBJECT="smoke-test-$(date +%Y%m%d-%H%M%S)"
DESCRIPTION="Автотестовая задача, созданная через tools/tracker-smoke-test.sh. Должна быть удалена после теста."

CREATE_OUTPUT=$("$TRACKER_SH" create-issue \
    --project-id "$FIRST_PROJECT_ID" \
    --type-id "type:task" \
    --subject "$SUBJECT" \
    --description "$DESCRIPTION" 2>&1 || echo "")

if grep -qE "OK: issue KARAOKE-[0-9]+ created" <<< "$CREATE_OUTPUT"; then
    CREATED_ID=$(grep -oE "#[0-9]+" <<< "$CREATE_OUTPUT" | head -1 | tr -d '#' || true)
    CREATED_UUID=$(grep -oE "id: [0-9a-f-]{36}" <<< "$CREATE_OUTPUT" | head -1 | sed 's/^id: //' || true)
    step_pass 3 "create-issue (KARAOKE-${CREATED_ID})"
else
    step_fail 3 "create-issue" "$CREATE_OUTPUT"
    exit 1
fi

if [ -z "$CREATED_ID" ] || [ -z "$CREATED_UUID" ]; then
    step_fail 3 "create-issue (не удалось разобрать ответ)" "$CREATE_OUTPUT"
    exit 1
fi

# --- Шаг 4: get-issue по sequence_id---
GET_OUTPUT=$("$TRACKER_SH" get-issue "$CREATED_ID" 2>&1 || echo "")

GET_SUBJECT=$(printf '%s' "$GET_OUTPUT" | jq -r '.subject // empty' 2>/dev/null || true)
GET_STATUS=$(printf '%s' "$GET_OUTPUT" | jq -r '.status // empty' 2>/dev/null || true)

if [ "$GET_SUBJECT" = "$SUBJECT" ] && [ "$GET_STATUS" = "New" ]; then
    step_pass 4 "get-issue KARAOKE-${CREATED_ID} (subject, status=New)"
else
    step_fail 4 "get-issue KARAOKE-${CREATED_ID}" "$GET_OUTPUT"
fi

# --- Шаг 5: claim-issue ---
# Дальше работаем по UUID: он уже известен из ответа create-issue, лишний
# скан списка задач не нужен (экономия запросов к API).
CLAIM_OUTPUT=$("$TRACKER_SH" claim-issue "$CREATED_UUID" 2>&1 || echo "")

if grep -qE "OK: #.* claimed by" <<< "$CLAIM_OUTPUT"; then
    CLAIMED_JSON=$("$TRACKER_SH" get-issue "$CREATED_UUID" 2>/dev/null || echo "")
    CLAIMED_STATUS=$(printf '%s' "$CLAIMED_JSON" | jq -r '.status // empty' 2>/dev/null || true)
    CLAIMED_ASSIGNEE=$(printf '%s' "$CLAIMED_JSON" | jq -r '.assignee // empty' 2>/dev/null || true)
    if [ "$CLAIMED_STATUS" = "In progress" ] && [ "$CLAIMED_ASSIGNEE" = "ai-agent" ]; then
        step_pass 5 "claim-issue (status=In progress, assignee=ai-agent)"
    else
        step_fail 5 "claim-issue (проверка полей)" "status=${CLAIMED_STATUS} assignee=${CLAIMED_ASSIGNEE}"
    fi
else
    step_fail 5 "claim-issue" "$CLAIM_OUTPUT"
fi

# --- Шаг 6: add-comment (markdown: таблица + блок кода) ---
TMP_REPORT=$(mktemp)
cat > "$TMP_REPORT" <<EOF
## Что сделано

Smoke-test: задача создана и взята через CLI.

| Шаг | Результат |
|---|---|
| healthcheck | OK |
| create-issue | OK |

\`\`\`bash
./tools/tracker.sh get-issue ${CREATED_ID}
\`\`\`

## Изменённые файлы

- tools/tracker.sh

## Известные ограничения

Нет.
EOF

COMMENT_OUTPUT=$("$TRACKER_SH" add-comment "$CREATED_UUID" --file "$TMP_REPORT" 2>&1 || echo "")

rm -f "$TMP_REPORT"

if grep -qE "OK: comment added to #" <<< "$COMMENT_OUTPUT"; then
    COMMENT_JSON=$("$TRACKER_SH" get-issue "$CREATED_UUID" 2>/dev/null || echo "")
    # Комментарии могут отдаваться в любом порядке — склеиваем все тела.
    COMMENT_HTML=$(printf '%s' "$COMMENT_JSON" | jq -r '[.comments[]?.body_html // ""] | join("\n")' 2>/dev/null || true)
    if grep -q "<table>" <<< "$COMMENT_HTML" && grep -qE "<pre>|<code>" <<< "$COMMENT_HTML"; then
        step_pass 6 "add-comment (HTML: таблица и блок кода сохранены)"
    else
        step_fail 6 "add-comment (markdown не сохранил таблицу/код)" "$COMMENT_HTML"
    fi
else
    step_fail 6 "add-comment" "$COMMENT_OUTPUT"
fi

# --- Шаг 7: delete-issue ---
DELETE_OUTPUT=$("$TRACKER_SH" delete-issue "$CREATED_UUID" 2>&1 || echo "")

if grep -qE "OK: #.* deleted" <<< "$DELETE_OUTPUT"; then
    CLEANED=1
    if "$TRACKER_SH" get-issue "$CREATED_UUID" >/dev/null 2>&1; then
        step_fail 7 "delete-issue (задача всё ещё читается)" "$DELETE_OUTPUT"
    else
        step_pass 7 "delete-issue (задача удалена, GET → ошибка)"
    fi
else
    step_fail 7 "delete-issue" "$DELETE_OUTPUT"
fi

# --- Шаг 8: final healthcheck ---
if "$TRACKER_SH" healthcheck >/dev/null 2>&1; then
    step_pass 8 "final healthcheck"
else
    step_fail 8 "final healthcheck" "Plane упал во время теста"
fi

# --- Сводка ---
echo ""
echo "${C_YELLOW}══════════════════════════════════════════════════════${C_RESET}"
if [ $FAIL -eq 0 ]; then
    echo "${C_GREEN}  ALL PASS — Plane полностью функционален${C_RESET}"
    echo "${C_GREEN}  $PASS/$TOTAL шагов OK${C_RESET}"
    exit 0
else
    echo "${C_RED}  $FAIL шагов провалилось${C_RESET}"
    echo "${C_RED}  $PASS/$TOTAL шагов OK${C_RESET}"
    echo ""
    echo "Логи CLI: logs/tracker-agent.log"
    exit 1
fi
