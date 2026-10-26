#!/usr/bin/env bash
# check-direct-commits.sh
#
# CI-gate: в master не должно быть ПРЯМЫХ коммитов (коммитов в обход PR).
#
# Прецедент (2026-09-09, спека #354): агент закоммитил напрямую в master
# после мержа PR #452 — владелец поймал вручную. С тех пор в workflow есть
# шаг «No direct commits to master» (Pass 353), но он:
#   * считал коммиты через `git log --pretty=format:... | wc -l` — а такой
#     вывод НЕ заканчивается переводом строки, поэтому `wc -l` занижал
#     результат на единицу: ОДИН прямой коммит давал DIRECT_COMMITS=0, и
#     guard никогда не падал (BR-509, P-78-03);
#   * жил только как инлайн-шаг в .github/workflows/lint.yml — его нельзя
#     было ни прогнать локально, ни проверить «до/после» на фикстуре.
#
# Этот скрипт — тот же guard, но отдельным файлом:
#   * считает через `git rev-list --count` (без off-by-one);
#   * умеет `--self-test` — на временном репозитории проверяет, что
#     корректная история даёт PASS, а подсовытый прямой коммит — FAIL
#     с ненулевым кодом;
#   * различает «нарушение» (1) и «проверка не выполнена» (2) — по правилу
#     пакета #402 (guard, который не смог оценить состояние, обязан вернуть
#     ненулевой код; 0 — успех, 1 — нарушение, 2 — проверка не выполнена).
#
# Использование:
#   bash tools/check-direct-commits.sh                  # origin/master, окно 30 дней
#   bash tools/check-direct-commits.sh --ref master --since 7.days
#   bash tools/check-direct-commits.sh --self-test
#
# Что считается прямым коммитом:
#   коммит в ПЕРВОМ родителе master (first-parent цепочка), у которого ОДИН
#   родитель, т.е. он НЕ merge-коммит. Merge-коммиты (результат `gh pr
#   merge --merge`) — легальны; всё остальное — прямой коммит в обход PR.
#
# Baseline: tools/check-direct-commits.baseline — известные исторические
# нарушения (хэши). Формат: <полный sha1>  # <причина>.
#
# Версия: 1.0.0 (BR-509, P-78-03, OpenProject #409).

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_REF="origin/master"
DEFAULT_SINCE="30.days"
# Каталог проверяемого репозитория (по умолчанию — корень этого репозитория).
# Нужен для --self-test: фикстура проверяется на временном репозитории.
CHECK_REPO="$REPO_ROOT"
BASELINE_FILE=""

if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    NC=''
fi

# --- разбор аргументов -----------------------------------------------------
REF="$DEFAULT_REF"
SINCE="$DEFAULT_SINCE"
SELF_TEST=0
while [ $# -gt 0 ]; do
    case "$1" in
        --ref)
            REF="${2:-}"
            shift 2 || exit 2
            ;;
        --since)
            SINCE="${2:-}"
            shift 2 || exit 2
            ;;
        --repo)
            CHECK_REPO="${2:-}"
            shift 2 || exit 2
            ;;
        --self-test)
            SELF_TEST=1
            shift
            ;;
        -h|--help)
            sed -n '2,45p' "$0"
            exit 0
            ;;
        *)
            echo "Неизвестный аргумент: $1" >&2
            exit 2
            ;;
    esac
done

# --- baseline ---------------------------------------------------------------
declare -A ALLOWED=()
if [ -z "$BASELINE_FILE" ]; then
    BASELINE_FILE="${CHECK_REPO}/tools/check-direct-commits.baseline"
fi
if [ ! -f "$BASELINE_FILE" ] && [ "$CHECK_REPO" = "$REPO_ROOT" ]; then
    BASELINE_FILE="${REPO_ROOT}/tools/check-direct-commits.baseline"
fi
if [ -f "$BASELINE_FILE" ]; then
    while IFS= read -r line; do
        case "$line" in
            ''|'#'*) continue ;;
        esac
        key="$(echo "$line" | awk '{print $1}')"
        if [ -n "$key" ]; then
            ALLOWED["$key"]=1
        fi
    done < "$BASELINE_FILE"
fi

# --- self-test --------------------------------------------------------------
if [ "$SELF_TEST" -eq 1 ]; then
    TMP_DIR="$(mktemp -d)"
    trap 'rm -rf "${TMP_DIR}"' EXIT
    WORK="${TMP_DIR}/repo"
    mkdir -p "$WORK"
    (
        cd "$WORK" || exit 2
        git init -q -b master .
        git config user.email guard@example.local
        git config user.name "guard self-test"
        git config commit.gpgsign false
        echo "base" > base.txt
        git add base.txt
        git commit -q -m "base commit"
        # корректная история: PR-ветка, слитая merge-коммитом
        git checkout -q -b feature/pr-1
        echo "feature" > feature.txt
        git add feature.txt
        git commit -q -m "feature change"
        git checkout -q master
        git merge -q --no-ff feature/pr-1 -m "Merge pull request #1 from svoemesto/feature-pr-1"
    ) || {
        echo "FAIL: не удалось собрать тестовый репозиторий" >&2
        exit 1
    }

    RC=0

    # (а) корректное дерево — PASS
    if bash "${SCRIPT_DIR}/check-direct-commits.sh" --repo "$WORK" --ref master --since "1970-01-01" > "${TMP_DIR}/ok.out" 2>&1; then
        echo "OK: корректная история (только merge-коммиты) — PASS"
    else
        echo "FAIL: корректная история должна проходить, а не падать" >&2
        cat "${TMP_DIR}/ok.out" >&2
        RC=1
    fi

    # (б) подсовытый прямой коммит — FAIL с ненулевым кодом
    (
        cd "$WORK" || exit 2
        echo "direct" > direct.txt
        git add direct.txt
        git commit -q -m "direct commit to master (bypass PR)"
    ) || {
        echo "FAIL: не удалось создать прямой коммит в тестовом репозитории" >&2
        RC=1
    }
    bash "${SCRIPT_DIR}/check-direct-commits.sh" --repo "$WORK" --ref master --since "1970-01-01" > "${TMP_DIR}/bad.out" 2>&1
    BAD_RC=$?
    if [ "$BAD_RC" -ne 0 ]; then
        echo "OK: прямой коммит пойман (код возврата $BAD_RC, ожидался != 0)"
    else
        echo "FAIL: прямой коммит НЕ пойман (код 0)" >&2
        cat "${TMP_DIR}/bad.out" >&2
        RC=1
    fi

    # (в) недоступный ref — код 2 («проверка не выполнена»), а не 0
    bash "${SCRIPT_DIR}/check-direct-commits.sh" --repo "$WORK" --ref origin/does-not-exist --since "1970-01-01" > "${TMP_DIR}/noref.out" 2>&1
    NOREF_RC=$?
    if [ "$NOREF_RC" -eq 2 ]; then
        echo "OK: недоступный ref → код 2 (проверка не выполнена)"
    else
        echo "FAIL: недоступный ref должен давать код 2, а не $NOREF_RC" >&2
        cat "${TMP_DIR}/noref.out" >&2
        RC=1
    fi

    if [ "$RC" -ne 0 ]; then
        echo "SELF-TEST: FAIL" >&2
        exit 1
    fi
    echo "SELF-TEST: PASS"
    exit 0
fi

# --- основная проверка ------------------------------------------------------
cd "$CHECK_REPO" || exit 2
if ! git rev-parse --verify --quiet "$REF" >/dev/null 2>&1; then
    echo -e "${RED}FAIL${NC}: ref '$REF' недоступен — оценить состояние master невозможно." >&2
    echo "   Ожидается полная история (checkout с fetch-depth: 0) + remote-tracking ref." >&2
    echo "   Код 2 = «проверка не выполнена» (правило пакета #402)." >&2
    exit 2
fi

# Список коммитов первой-родительской цепочки за окно.
# Формат: <sha>\t<parents>\t<subject>
LIST_FILE="$(mktemp)"
trap 'rm -f "$LIST_FILE"' EXIT
if ! git log --first-parent --since="$SINCE" --pretty=format:"%H%x09%P%x09%s" "$REF" > "$LIST_FILE" 2>/dev/null; then
    echo -e "${RED}FAIL${NC}: не удалось прочитать историю '$REF'." >&2
    exit 2
fi

VIOLATIONS=0
TOTAL=0
while IFS=$'\t' read -r sha parents subject; do
    [ -z "$sha" ] && continue
    TOTAL=$((TOTAL + 1))
    # Один родитель → коммит НЕ является merge-коммитом → прямой коммит.
    set -- $parents
    [ "$#" -eq 1 ] || continue
    # Известное историческое нарушение — пропускаем (baseline).
    [ -z "${ALLOWED[$sha]:-}" ] || continue
    VIOLATIONS=$((VIOLATIONS + 1))
    echo -e "  ${RED}✗${NC} прямой коммит в '$REF': ${sha} ${subject}"
done < "$LIST_FILE"

echo ""
if [ "$VIOLATIONS" -gt 0 ]; then
    echo -e "${RED}FAIL${NC}: найдено $VIOLATIONS прямых коммитов в '$REF' (окно: $SINCE, проверено $TOTAL коммитов)."
    echo ""
    echo "AGENTS.md § Git — CI-gate для master (NON-NEGOTIABLE): в master только"
    echo "merge-коммиты (результат merge PR). Прямой коммит обходит ревью PR."
    echo ""
    echo "Defensive layers (Pass 353):"
    echo "  1. GitHub branch protection (server-side, primary defense)"
    echo "  2. Pre-commit hook tools/git-hooks/pre-commit-block-master.sh (client-side)"
    echo "  3. Этот CI-шаг (server-side, final safety net)"
    exit 1
fi

echo -e "${GREEN}OK${NC}: $TOTAL коммитов проверено (окно: $SINCE), прямых коммитов нет."
exit 0
