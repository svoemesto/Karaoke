#!/usr/bin/env bash
# =====================================================================
# tools/check-tracker-workflow.sh — сверка шагов Plane-workflow.
#
# Что делает. Проверяет, что задача прошла обязательные шаги
# (AGENTS.md Tier-1 «Plane Workflow»):
#   1. claim       — исполнитель назначен;
#   2. add-comment — комментарий-отчёт опубликован;
#   3. mark-review — задача была переведена в In review.
# Плюс мягкий сигнал: были ли изменения в knowledge/ за время жизни задачи.
#
# Поведение (решение владельца, KARAOKE-346): сверка НЕ блокирует закрытие.
# Нарушения фиксируются следом (журнал ~/.dsh/ledger/workflow.ndjson) и
# отчётом в stdout; тикет-нарушение типа «governance-violation» заводит
# агент по факту (KARAOKE-345).
#
# Использование:
#   bash tools/check-tracker-workflow.sh --issue 338         # живая проверка
#   bash tools/check-tracker-workflow.sh --sweep --limit 25  # разбор закрытых
#   bash tools/check-tracker-workflow.sh --step claim --issue 338
#
# Коды возврата:
#   0 — нарушений нет (или режим --step)
#   1 — найдены нарушения
#   2 — usage/IO ошибка
#
# Ограничение разборного режима: Plane не отдаёт историю состояний
# (эндпоинты history/activities — 404), поэтому «был ли mark-review» в
# разборе известен только по локальному журналу шагов, который ведётся с
# момента появления этого скрипта. Для более старых задач — «не подтверждено».
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LEDGER_DIR="${DSH_LEDGER_DIR:-$HOME/.dsh/ledger}"
LEDGER="${LEDGER_DIR}/workflow.ndjson"

MODE="issue"
ISSUE=""
STEP=""
LIMIT=10

while [ "$#" -gt 0 ]; do
    case "$1" in
        --issue) ISSUE="${2:-}"; shift 2 ;;   # режим не перетирается: --step задаёт его сам
        --sweep) MODE="sweep"; shift ;;
        --step) MODE="step"; STEP="${2:-}"; shift 2 ;;
        --limit) LIMIT="${2:-10}"; shift 2 ;;
        -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
        *) echo "ERROR: неизвестный аргумент: $1" >&2; exit 2 ;;
    esac
done

# shellcheck source=tools/tracker-lib.sh
source "${REPO_ROOT}/tools/tracker-lib.sh"
tracker_load_env >/dev/null 2>&1 || true

now_iso() { date -u +%Y-%m-%dT%H:%M:%S.%3NZ; }

ledger_append() {
    local record="$1"
    mkdir -p "$LEDGER_DIR" 2>/dev/null || return 0
    chmod 700 "$LEDGER_DIR" 2>/dev/null || true
    printf '%s\n' "$record" >> "$LEDGER" 2>/dev/null || true
    chmod 600 "$LEDGER" 2>/dev/null || true
}

# --- Доказательства из журнала шагов ----------------------------------------
# Шаг записан, если в ~/.dsh/ledger/workflow.ndjson есть запись с этим issue.
step_recorded() {
    local ref="$1" step="$2"
    [ -f "$LEDGER" ] || return 1
    grep -q "\"step\":\"${step}\"[^\n]*\"issue\":\"KARAOKE-${ref}\"" "$LEDGER" 2>/dev/null
}

# Есть ли вообще хоть какая-то запись по задаче — чтобы отличать «не выполнено»
# от «не подтверждено» (задача старше журнала).
has_any_step_record() {
    local ref="$1"
    [ -f "$LEDGER" ] || return 1
    grep -q "\"src\":\"workflow-step\"[^\n]*\"issue\":\"KARAOKE-${ref}\"" "$LEDGER" 2>/dev/null
}

# --- Режим --step: запись шага в след ---------------------------------------
if [ "$MODE" = "step" ]; then
    [ -n "$STEP" ] || { echo "ERROR: --step требует имя шага" >&2; exit 2; }
    ledger_append "$(jq -nc --arg ts "$(now_iso)" --arg step "$STEP" --arg issue "${ISSUE:-}" --arg actor "${TRACKER_AGENT_USER:-unknown}" \
        '{ts:$ts, src:"workflow-step", step:$step, issue:$issue, actor:$actor}')"
    exit 0
fi

# --- Проверка одной задачи --------------------------------------------------
check_issue() {
    local raw_ref="$1" live="$2" ref
    local json subject status assignee comments_count created knowledge_commits
    local violations="" signals=""

    # Номер задачи приходит в разных форматах (355, KARAOKE-355, #355) —
    # приводим к голому числу, иначе в выводе будет «KARAOKE-KARAOKE-355».
    ref=$(printf '%s' "$raw_ref" | sed -E 's/^(KARAOKE-|#|op:)//')

    json=$(tracker_get_issue "$ref" 2>/dev/null) || { echo "  ? не удалось получить задачу $ref" >&2; return 2; }
    subject=$(printf '%s' "$json" | jq -r '.subject // ""')
    status=$(printf '%s' "$json" | jq -r '.status // ""')
    assignee=$(printf '%s' "$json" | jq -r '.assignee // ""')
    comments_count=$(printf '%s' "$json" | jq -r '(.comments // []) | length')
    created=$(printf '%s' "$json" | jq -r '.createdAt // ""')

    echo "== Сверка Plane-workflow: KARAOKE-${ref} — ${subject} =="

    # 1. claim. В живой проверке доказательство — исполнитель в Plane.
    # В разборе текущий исполнитель ненадёжен (Plane может не хранить его после
    # закрытия), поэтому доказательство — журнал шагов.
    if [ "$live" = "live" ]; then
        if [ -n "$assignee" ] && [ "$assignee" != "null" ]; then
            echo "  claim (исполнитель назначен): OK — ${assignee}"
        elif step_recorded "$ref" "claim"; then
            echo "  claim: OK — найден в журнале шагов (исполнитель в Plane не отображается)"
        else
            echo "  ! НАРУШЕНИЕ claim: исполнитель не назначен (claim-issue не выполнен)"
            violations="${violations}claim:исполнитель не назначен;"
        fi
    else
        if step_recorded "$ref" "claim"; then
            echo "  claim: OK — найден в журнале шагов"
        elif has_any_step_record "$ref"; then
            echo "  ! НАРУШЕНИЕ claim: в журнале шагов нет отметки claim"
            violations="${violations}claim:claim-issue не выполнялся;"
        else
            echo "  claim: не подтверждено — журнал шагов не содержит отметок (задача закрыта до появления журнала)"
        fi
    fi

    # 2. комментарий-отчёт
    if [ "${comments_count:-0}" -ge 1 ]; then
        echo "  add-comment (отчёт опубликован): OK — комментариев: ${comments_count}"
    else
        echo "  ! НАРУШЕНИЕ add-comment: комментарий-отчёт не опубликован"
        violations="${violations}add-comment:нет комментария-отчёта;"
    fi

    # 3. mark-review
    if [ "$live" = "live" ]; then
        if [ "$status" = "In review" ]; then
            echo "  mark-review (задача в In review): OK"
        else
            echo "  ! НАРУШЕНИЕ mark-review: состояние «${status}», а не «In review»"
            violations="${violations}mark-review:состояние ${status}, а не In review;"
        fi
    else
        if step_recorded "$ref" "mark-review"; then
            echo "  mark-review: OK — найден в журнале шагов"
        elif has_any_step_record "$ref"; then
            echo "  ! НАРУШЕНИЕ mark-review: в журнале шагов нет отметки mark-review"
            violations="${violations}mark-review:mark-review не выполнялся;"
        else
            echo "  mark-review: не подтверждено — журнал шагов не содержит отметок (история состояний Plane недоступна)"
            signals="${signals}mark-review не подтверждён;"
        fi
    fi

    # 4. мягкий сигнал по knowledge/
    knowledge_commits=0
    if [ -n "$created" ] && [ -d "${REPO_ROOT}/knowledge" ]; then
        knowledge_commits=$(git -C "$REPO_ROOT" log --since="$created" --oneline -- knowledge 2>/dev/null | wc -l | tr -d ' ')
    fi
    echo "  knowledge (изменения за время жизни задачи): ${knowledge_commits} коммитов — сигнал, не проверка"

    local verdict="ok"
    [ -n "$violations" ] && verdict="violation"
    echo "ИТОГ: ${verdict}${violations:+ (${violations%;})}"

    ledger_append "$(jq -nc --arg ts "$(now_iso)" --arg issue "KARAOKE-${ref}" --arg mode "$live" \
        --arg status "$status" --arg assignee "$assignee" --argjson comments "${comments_count:-0}" \
        --arg knowledge "${knowledge_commits:-0}" --arg verdict "$verdict" --arg violations "${violations%;}" \
        '{ts:$ts, src:"workflow-check", issue:$issue, mode:$mode, status:$status, assignee:$assignee, comments:$comments, knowledgeCommits:($knowledge|tonumber), verdict:$verdict, violations:$violations}')"

    [ "$verdict" = "violation" ] && return 1
    return 0
}

case "$MODE" in
    issue)
        [ -n "$ISSUE" ] || { echo "ERROR: --issue требует номер задачи" >&2; exit 2; }
        check_issue "$ISSUE" "live"
        ;;
    sweep)
        echo "== Разбор закрытых задач (до ${LIMIT}) =="
        found=0
        total=0
        while IFS=$'\t' read -r id subject rest; do
            case "$id" in
                ''|*[!0-9]*) continue ;;
            esac
            total=$((total + 1))
            if ! check_issue "$id" "sweep"; then
                found=$((found + 1))
            fi
            echo
        done < <(tracker_list_issues "" "" "closed" "$LIMIT" "1" 2>/dev/null)
        echo "== Разбор завершён: проверено ${total}, нарушений ${found} =="
        ;;
    *)
        echo "ERROR: не указан режим" >&2
        exit 2
        ;;
esac
