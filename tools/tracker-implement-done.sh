#!/usr/bin/env bash
# =====================================================================
# tools/tracker-implement-done.sh — auto-call add-comment + mark-review
# after /speckit.implement finishes.
#
# Это AFTER_IMPLEMENT hook (Pass 350). Вызывается из:
#   1. Агентом вручную после `/speckit.implement` (после merge PR):
#        bash tools/tracker-implement-done.sh
#      (можно с аргументом NNN: `bash tools/tracker-implement-done.sh 69`).
#   2. Из CI (Pass 350, future): можно интегрировать в `.github/workflows/`
#      post-merge workflow (но требует workflow_run триггер — out of scope
#      для первой итерации).
#
# Алгоритм:
#   1. Определяет spec dir по текущей ветке (например, ветка `349-...`
#      → ищет specs/349-*/spec.md, берёт NNN = 349 из имени ветки или из
#      ## Issue ID секции).
#   2. Извлекает Issue ID через tools/tracker-bootstrap.sh (тот же regex).
#   3. Проверяет: specs/<NN>-<slug>/report.md существует? Если нет —
#      РАБОТА ОСТАНАВЛИВАЕТСЯ (BR-609): stub-отчёт НЕ генерируется,
#      комментарий НЕ публикуется, задача НЕ переводится в In review.
#   4. Вызывает `tracker.sh add-comment <NN> --file report.md`.
#   5. Вызывает `tracker.sh mark-review <NN>`.
#
# Exit codes:
#   0 — успех (либо оба выполнены, либо уже In review).
#   1 — Issue ID не найден / нет report.md / ошибка tracker.sh.
#   2 — usage error.
#
# BR-609 (аудит #336, пакет PKG-S3-30): при отсутствии report.md скрипт
# генерировал stub-отчёт из `git log` и ПУБЛИКОВАЛ его комментарием, а затем
# переводил задачу в In review. В трекере оказывался отчёт-заглушка («замените
# на настоящий отчёт»), а workflow OpenProject (claim → report → mark-review)
# считался выполненным без отчёта. Теперь отсутствие report.md — ошибка с
# кодом 1: комментарий не публикуется, статус не меняется.
# =====================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
TRACKER_SH="${SCRIPT_DIR}/tracker.sh"
TRACKER_BOOTSTRAP="${SCRIPT_DIR}/tracker-bootstrap.sh"

log() { printf '[tracker-implement-done] %s\n' "$*" >&2; }

# Source env.
if [ -f "${REPO_ROOT}/.env.local-tracker" ]; then
    # shellcheck disable=SC1091
    set -a; source "${REPO_ROOT}/.env.local-tracker"; set +a
fi

# Разрешаем Issue ID: аргумент ИЛИ detection через tracker-bootstrap.
ISSUE_ID="${1:-}"
if [ -z "$ISSUE_ID" ]; then
    log "Issue ID не передан — пытаюсь определить через tracker-bootstrap.sh по текущей ветке/контексту."
    # Вычитываем имя ветки и подсовываем его как "аргумент" в tracker-bootstrap.
    BRANCH="$(git -C "${REPO_ROOT}" branch --show-current 2>/dev/null || true)"
    if [ -z "$BRANCH" ]; then
        log "ERROR: невозможно получить текущую ветку git."
        exit 1
    fi
    log "текущая ветка: $BRANCH"
    # Подсовываем как имя спека: `tracker-bootstrap <branch>` само ничего
    # не делает с аргументом, только сканирует текст. Подаём ветку как
    # "описание".
    ISSUE_ID="$("$TRACKER_BOOTSTRAP" "$BRANCH" 2>/dev/null | head -n 1)"
fi

if [ -z "$ISSUE_ID" ]; then
    log "ERROR: Issue ID не определён. Передайте его явно:"
    log "  bash tools/tracker-implement-done.sh 69"
    exit 1
fi

log "detected Issue ID: #$ISSUE_ID"

# Ищем specs/NNN-<slug>/report.md. Если нет, генерируем stub из git log.
BRANCH="$(git -C "${REPO_ROOT}" branch --show-current)"
NNN_FROM_BRANCH="$(echo "$BRANCH" | grep -Eo '^[0-9]+' | head -n 1)"

SPEC_DIR=""
if [ -n "$NNN_FROM_BRANCH" ] && [ -n "${REPO_ROOT}" ]; then
    SPEC_DIR="$(find "${REPO_ROOT}/specs" -maxdepth 1 -type d -name "${NNN_FROM_BRANCH}-*" 2>/dev/null | head -n 1 || true)"
fi
# Fallback: поиск по Issue ID как точного NNN-префикса (НЕ substring!).
# "169-..." содержит "69" но это НЕ issue #69.
if [ -z "$SPEC_DIR" ]; then
    SPEC_DIR="$(find "${REPO_ROOT}/specs" -maxdepth 1 -type d -name "${ISSUE_ID}-*" 2>/dev/null | head -n 1 || true)"
fi

REPORT_FILE=""
if [ -n "$SPEC_DIR" ]; then
    CANDIDATE="${SPEC_DIR}/report.md"
    if [ -f "$CANDIDATE" ]; then
        REPORT_FILE="$CANDIDATE"
    fi
fi

# BR-609: report.md обязателен. Stub-отчёт НЕ генерируется и НЕ публикуется:
# иначе в трекере появляется комментарий-заглушка, а задача уходит в In review
# без отчёта (workflow OpenProject считается выполненным по факту публикации).
if [ -z "$REPORT_FILE" ]; then
    if [ -n "$SPEC_DIR" ]; then
        log "ERROR: отчёт не найден: ${SPEC_DIR}/report.md"
    else
        log "ERROR: отчёт не найден: каталог спеки для Issue #${ISSUE_ID} не определён."
    fi
    log "       Комментарий НЕ публикуется, задача НЕ переводится в In review."
    log "       Создайте отчёт о работе: specs/<NNN>-<slug>/report.md"
    log "       (см. AGENTS.md § OpenProject Workflow — шаг add-comment)."
    exit 1
fi

log "report file: $REPORT_FILE"

# add-comment + mark-review. Если add-comment падает (например, уже есть),
# продолжаем к mark-review. Если mark-review падает — fail.
log "step 1/2 — tracker.sh add-comment ${ISSUE_ID} --file $REPORT_FILE"
if ! bash "${TRACKER_SH}" add-comment "${ISSUE_ID}" --file "$REPORT_FILE" 2>&1 | tee /dev/stderr | tail -n 5; then
    log "WARN: add-comment failed — continuing to mark-review (comment may already exist or be rate-limited)."
fi

log "step 2/2 — tracker.sh mark-review ${ISSUE_ID}"
if ! bash "${TRACKER_SH}" mark-review "${ISSUE_ID}" 2>&1 | tee /dev/stderr | tail -n 5; then
    log "ERROR: mark-review failed."
    exit 1
fi

log "OK: Issue #$ISSUE_ID -> In review (add-comment + mark-review complete)."
log "next: владелец reviewает PR, затем bash tools/tracker.sh close-issue ${ISSUE_ID}"

exit 0
