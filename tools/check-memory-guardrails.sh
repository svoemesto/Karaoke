#!/usr/bin/env bash
# =====================================================================
# tools/check-memory-guardrails.sh — guard превентивных мер против OOM
# =====================================================================
#
# BR-630 (спека 676 / KARAOKE-245, FR-006…FR-015): превентивные меры против
# исчерпания памяти в контейнере karaoke-app должны оставаться в репозитории
# согласованными между собой. Если из compose молча выкинут `mem_limit` или
# `-Xmx`, вся мера исчезает — а это ровно то, что случилось в инциденте
# 2026-10-23 (JVM с -Xmx8g без cgroup-лимита вытесняла память admin-машины).
#
# Guard проверяет ТОЛЬКО правила BR-630:
#   1. предел памяти контейнера karaoke-app (mem_limit);
#   2. максимум heap JVM (-Xmx) и его отношение к пределу: heap < предел;
#   3. порядок флагов: -Xmx6g ПОСЛЕ ${APP_JAVA_OPTS} (последний -Xmx побеждает,
#      иначе устаревшее значение из неотслеживаемого deploy/.env перебьёт лимит);
#   4. дамп состояния heap при OOM: -XX:+HeapDumpOnOutOfMemoryError и
#      -XX:HeapDumpPath=/dumps;
#   5. том дампов: ./karaoke-db/dumps:/dumps;
#   6. пороги алерта загрузки heap в environment: (75 % / 90 %);
#   7. политика хранения дампов: deploy/heap-dump-retention.sh (последние 2,
#      лимит тома 20 ГБ, защита по возрасту 60 минут, паттерн только *.hprof).
#
# Отдельный быстрый guard, а не часть tools/check-deploy-hardening.sh: тот по
# политике репозитория остаётся ручным (knowledge/system/infra/ci-tools.md),
# а правила BR-630 — смысл самой задачи, и они должны проверяться автоматически
# (pre-commit + CI). Единственная реализация правил — здесь; блок BR-630 в
# check-deploy-hardening.sh делегирует этому скрипту.
#
# Использование:
#   bash tools/check-memory-guardrails.sh            # проверка (exit 1 при нарушениях)
#   bash tools/check-memory-guardrails.sh --quiet    # только код возврата (pre-commit)
#   bash tools/check-memory-guardrails.sh --self-test # проверка самих правил
#
# Exit codes:
#   0 — все проверки пройдены;
#   1 — есть нарушения;
#   2 — usage error / self-test failed.
# =====================================================================

set -uo pipefail

SELF_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${SELF_PATH}/.." && pwd)"
MODE="check"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --repo)
            REPO="${2:?--repo требует путь}"
            shift 2
            ;;
        --list)
            MODE="list"
            shift
            ;;
        --self-test)
            MODE="self-test"
            shift
            ;;
        --quiet)
            MODE="quiet"
            shift
            ;;
        -h | --help)
            sed -n '2,40p' "${BASH_SOURCE[0]}"
            exit 0
            ;;
        *)
            echo "Usage: bash tools/check-memory-guardrails.sh [--repo PATH] [--list] [--self-test] [--quiet]" >&2
            exit 2
            ;;
    esac
done

REPO="$(cd "$REPO" && pwd)"

pass=0
fail=0

ok() {
    [[ "$MODE" == "quiet" ]] && return 0
    echo "  PASS  [BR-630] $1"
    pass=$(( pass + 1 ))
}

bad() {
    [[ "$MODE" == "quiet" ]] && return 0
    echo "  FAIL  [BR-630] $1"
    fail=$(( fail + 1 ))
}

# Строка файла без комментариев и без ведущих пробелов (правила проверяют
# исполняемый текст, а не комментарии — как в check-deploy-hardening.sh).
strip_comments() {
    grep -vE '^[[:space:]]*#' "$1" | sed -E 's/[[:space:]]+#.*$//'
}

# Значение в байтах из строки вида «8g» / «1600m» / «1024k».
to_bytes() {
    local value="$1"
    local number="${value%[gGmMkK]}"
    local unit="${value: -1}"
    case "$unit" in
        g | G) echo $(( number * 1024 * 1024 * 1024 )) ;;
        m | M) echo $(( number * 1024 * 1024 )) ;;
        k | K) echo $(( number * 1024 )) ;;
        *) echo "$value" ;;
    esac
}

COMPOSE="deploy/docker-compose-app.yml"
RETENTION="deploy/heap-dump-retention.sh"

# Строка `command:` сервиса karaoke-app (без комментариев).
compose_command() {
    strip_comments "$REPO/$COMPOSE" | grep -E '^[[:space:]]*command:' | head -1 | sed -E 's/^[[:space:]]*command:[[:space:]]*//'
}

# Значение `mem_limit:` сервиса karaoke-app.
compose_mem_limit() {
    strip_comments "$REPO/$COMPOSE" | grep -E '^[[:space:]]*mem_limit:' | head -1 | sed -E 's/^[[:space:]]*mem_limit:[[:space:]]*//' | tr -d '"'
}

# Значение `-Xmx…` из строки command.
heap_flag() {
    compose_command | grep -oE '\-Xmx[0-9]+[gGmMkK]' | head -1 | sed -E 's/^-Xmx//'
}

check_rules() {
    local command_line mem_limit heap heap_bytes mem_bytes

    command_line="$(compose_command)"
    mem_limit="$(compose_mem_limit)"
    heap="$(heap_flag)"

    # 1. Предел памяти контейнера.
    if [[ -n "$mem_limit" ]]; then
        ok "$COMPOSE: предел памяти контейнера karaoke-app — mem_limit: $mem_limit"
    else
        bad "$COMPOSE: нет mem_limit — у контейнера нет cgroup-потолка памяти (инцидент 2026-10-23)"
    fi

    # 2. Максимум heap JVM.
    if [[ -n "$heap" ]]; then
        ok "$COMPOSE: максимум heap JVM — -Xmx$heap"
    else
        bad "$COMPOSE: нет -Xmx — значение heap не зафиксировано в репозитории (FR-008)"
    fi

    # 3. Связь «heap < предел» (FR-007).
    if [[ -n "$heap" && -n "$mem_limit" ]]; then
        heap_bytes="$(to_bytes "$heap")"
        mem_bytes="$(to_bytes "$mem_limit")"
        if [[ "$heap_bytes" =~ ^[0-9]+$ && "$mem_bytes" =~ ^[0-9]+$ ]]; then
            if (( heap_bytes < mem_bytes )); then
                ok "$COMPOSE: heap ($heap_bytes байт) меньше предела контейнера ($mem_bytes байт)"
            else
                bad "$COMPOSE: heap ($heap) не меньше предела контейнера ($mem_limit) — запас на метапространство/native/потоки отсутствует"
            fi
        else
            bad "$COMPOSE: не удалось разобрать лимиты (heap=$heap, mem_limit=$mem_limit)"
        fi
    fi

    # 4. Порядок флагов: -Xmx ПОСЛЕ ${APP_JAVA_OPTS}.
    if [[ -n "$command_line" ]]; then
        if [[ "$command_line" =~ APP_JAVA_OPTS.*-Xmx ]]; then
            ok "$COMPOSE: -Xmx идёт после \${APP_JAVA_OPTS} — перекрывает устаревшее значение из deploy/.env"
        else
            bad "$COMPOSE: -Xmx не после \${APP_JAVA_OPTS} — значение из неотслеживаемого .env перебьёт лимит"
        fi
    else
        bad "$COMPOSE: не найдена строка command: у контейнера нет JVM-опций"
    fi

    # 5. Дамп состояния heap при OOM.
    if [[ "$command_line" == *"-XX:+HeapDumpOnOutOfMemoryError"* && "$command_line" == *"-XX:HeapDumpPath=/dumps"* ]]; then
        ok "$COMPOSE: дамп состояния heap при OOM пишется на том (/dumps)"
    else
        bad "$COMPOSE: нет -XX:+HeapDumpOnOutOfMemoryError/-XX:HeapDumpPath=/dumps — дамп при OOM не пишется (FR-009)"
    fi

    # 6. Том дампов.
    if strip_comments "$REPO/$COMPOSE" | grep -qE '^[[:space:]]*-[[:space:]]+\./karaoke-db/dumps:/dumps'; then
        ok "$COMPOSE: том дампов ./karaoke-db/dumps:/dumps смонтирован (FR-010)"
    else
        bad "$COMPOSE: том дампов не смонтирован — дамп теряется вместе с контейнером (FR-010)"
    fi

    # 7. Пороги алерта загрузки heap (FR-015).
    local warn critical
    warn="$(strip_comments "$REPO/$COMPOSE" | grep -E '^[[:space:]]*-[[:space:]]+MONITOR_HEAP_WARN_PERCENT=' | head -1 | sed -E 's/.*MONITOR_HEAP_WARN_PERCENT=//' | tr -d '"')"
    critical="$(strip_comments "$REPO/$COMPOSE" | grep -E '^[[:space:]]*-[[:space:]]+MONITOR_HEAP_CRITICAL_PERCENT=' | head -1 | sed -E 's/.*MONITOR_HEAP_CRITICAL_PERCENT=//' | tr -d '"')"
    if [[ "$warn" == "75" && "$critical" == "90" ]]; then
        ok "$COMPOSE: пороги алерта heap — 75 % / 90 % (FR-015)"
    else
        bad "$COMPOSE: пороги алерта heap не совпадают с решением владельца (ожидалось 75/90, найдено $warn/$critical)"
    fi

    # 8. Политика хранения дампов (FR-011).
    if [[ ! -f "$REPO/$RETENTION" ]]; then
        bad "$RETENTION: нет политики хранения дампов — том переполняется (FR-011)"
        return
    fi
    ok "$RETENTION: политика хранения дампов есть в репозитории"
    if grep -qE '^KEEP_RECENT=2$' "$REPO/$RETENTION"; then
        ok "$RETENTION: хранятся последние 2 дампа"
    else
        bad "$RETENTION: KEEP_RECENT ≠ 2 — решение владельца «последние 2 дампа» нарушено"
    fi
    if grep -qE '^MAX_TOTAL_BYTES=\$\(\(20 \* 1024 \* 1024 \* 1024\)\)$' "$REPO/$RETENTION"; then
        ok "$RETENTION: лимит тома — 20 ГБ"
    else
        bad "$RETENTION: MAX_TOTAL_BYTES ≠ 20 ГБ — решение владельца «лимит тома 20 ГБ» нарушено"
    fi
    if grep -qE '^MIN_AGE_MINUTES=60$' "$REPO/$RETENTION"; then
        ok "$RETENTION: защита по возрасту — 60 минут (недописанный дамп не удаляется)"
    else
        bad "$RETENTION: MIN_AGE_MINUTES ≠ 60 — защита недописанного дампа не зафиксирована"
    fi
    if grep -qE "^DUMP_PATTERN='\*\\.hprof'$" "$REPO/$RETENTION"; then
        ok "$RETENTION: политика трогает только *.hprof — SQL-дампы БД в общем каталоге не вытесняются"
    else
        bad "$RETENTION: DUMP_PATTERN не ограничен *.hprof — под удар попадут SQL-дампы БД"
    fi
}

if [[ "$MODE" == "list" ]]; then
    echo "Проверки guard'а tools/check-memory-guardrails.sh (BR-630):"
    echo "  1  предел памяти контейнера karaoke-app (mem_limit)"
    echo "  2  максимум heap JVM (-Xmx)"
    echo "  3  связь «heap < предел»"
    echo "  4  порядок флагов: -Xmx после \${APP_JAVA_OPTS}"
    echo "  5  дамп состояния heap при OOM (-XX:+HeapDumpOnOutOfMemoryError, -XX:HeapDumpPath=/dumps)"
    echo "  6  том дампов (./karaoke-db/dumps:/dumps)"
    echo "  7  пороги алерта загрузки heap (75 % / 90 %)"
    echo "  8  политика хранения дампов (2 / 20 ГБ / 60 мин / только *.hprof)"
    exit 0
fi

if [[ "$MODE" == "self-test" ]]; then
    # Self-test: правила обязаны ловить «плохой» пример и пропускать «хороший».
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT
    mkdir -p "$tmp/deploy" "$tmp/tools"
    : >"$tmp/deploy/docker-compose-app.yml"
    : >"$tmp/deploy/heap-dump-retention.sh"

    REPO="$tmp"
    check_rules >/dev/null 2>&1
    if [[ "$fail" -eq 0 ]]; then
        echo "❌ self-test: пустое (битое) состояние не поймано" >&2
        exit 2
    fi

    # «Хороший» пример: все правила выполняются → ни одного нарушения.
    cat >"$tmp/deploy/docker-compose-app.yml" <<'YML'
services:
  karaoke-app:
    environment:
      - MONITOR_HEAP_WARN_PERCENT=75
      - MONITOR_HEAP_CRITICAL_PERCENT=90
    volumes:
      - ./karaoke-db/dumps:/dumps
    mem_limit: 8g
    command: ${APP_JAVA_OPTS} -Xmx6g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -jar /app.jar
YML
    cat >"$tmp/deploy/heap-dump-retention.sh" <<'SH'
KEEP_RECENT=2
MAX_TOTAL_BYTES=$((20 * 1024 * 1024 * 1024))
MIN_AGE_MINUTES=60
DUMP_PATTERN='*.hprof'
SH
    pass=0
    fail=0
    check_rules >/dev/null 2>&1
    if [[ "$fail" -ne 0 ]]; then
        echo "❌ self-test: корректная конфигурация принята как нарушение" >&2
        exit 2
    fi

    echo "✅ self-test: правила ловят битое состояние и пропускают корректное"
    exit 0
fi

check_rules

if [[ "$MODE" != "quiet" ]]; then
    echo
    echo "--- итог: PASS=$pass FAIL=$fail ---"
fi

if [[ "$fail" -ne 0 ]]; then
    exit 1
fi
exit 0
