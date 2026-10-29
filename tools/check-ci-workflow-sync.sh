#!/usr/bin/env bash
# tools/check-ci-workflow-sync.sh
#
# Guard: две копии CI-конфигурации должны содержать одинаковый набор гейтов.
#
# Назначение: FR-013 — «две копии CI-конфигурации расходятся, контрактный тест
# покрывает только `.github/`». В проекте две копии одного workflow:
# `.forgejo/workflows/lint.yml` (Forgejo Actions) и `.github/workflows/lint.yml`
# (GitHub Actions). Расхождение в наборе проверок означает, что одна из копий
# проверяет меньше, чем другая, — и дефект может пройти незамеченным.
#
# Использование:
#   ./tools/check-ci-workflow-sync.sh [--root <dir>]
#
#   --root <dir>   корень дерева с копиями workflow (по умолчанию — корень
#                  репозитория); нужен для прогона на фикстурах
#                  tools/fixtures/ci-workflow-sync/.
#
# Коды возврата:
#   0 — наборы гейтовых команд совпадают;
#   1 — расхождение (выведены строки, присутствующие только в одной копии);
#   2 — проверка не выполнена (нет git/файла копии, корень не найден).
#
# Что НЕ сравнивается (по контракту guards.md C2): `runs-on:`, `timeout-minutes:`,
# `if:` / `needs:` / порядок шагов, комментарии. Это инфраструктурные и
# оформительские различия, а не состав проверок.

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$PROJECT_ROOT"

while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      ROOT="${2:-}"
      shift 2 || { echo "ERROR: --root требует значение." >&2; exit 2; }
      ;;
    -h|--help)
      echo "Usage: $0 [--root <dir>]"
      echo "  --root <dir>: корень дерева с копиями workflow (по умолчанию корень репозитория)"
      exit 0
      ;;
    *)
      echo "ERROR: неизвестный аргумент: $1" >&2
      echo "Usage: $0 [--root <dir>]" >&2
      exit 2
      ;;
  esac
done

FORGEJO="$ROOT/.forgejo/workflows/lint.yml"
GITHUB="$ROOT/.github/workflows/lint.yml"

if [ ! -d "$ROOT" ]; then
  echo "ERROR: корень не найден: $ROOT" >&2
  echo "       проверка не выполнена." >&2
  exit 2
fi

for f in "$FORGEJO" "$GITHUB"; do
  if [ ! -f "$f" ]; then
    echo "ERROR: файл копии workflow не найден: $f" >&2
    echo "       проверка не выполнена." >&2
    exit 2
  fi
done

# Извлечение набора гейтовых команд: строки с вызовом инструмента.
# Комментарии (строки и хвостовые) отбрасываются, пробелы по краям обрезаются.
extract_gates() {
  grep -E '(bash|python3|python|npx)[[:space:]]+tools/|markdownlint|actionlint' "$1" \
    | grep -vE '^[[:space:]]*#' \
    | sed -E 's/[[:space:]]+#.*$//' \
    | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' \
    | sort -u
}

FORGEJO_GATES="$(extract_gates "$FORGEJO")"
GITHUB_GATES="$(extract_gates "$GITHUB")"

ONLY_FORGEJO="$(comm -23 <(printf '%s\n' "$FORGEJO_GATES") <(printf '%s\n' "$GITHUB_GATES"))"
ONLY_GITHUB="$(comm -13 <(printf '%s\n' "$FORGEJO_GATES") <(printf '%s\n' "$GITHUB_GATES"))"

if [ -n "$ONLY_FORGEJO" ] || [ -n "$ONLY_GITHUB" ]; then
  echo "[FAIL] Наборы гейтовых команд в двух копиях workflow расходятся (FR-013)."
  echo "       Копии: .forgejo/workflows/lint.yml и .github/workflows/lint.yml"
  echo "       Состав проверок обязан совпадать — иначе одна из копий проверяет меньше."
  if [ -n "$ONLY_FORGEJO" ]; then
    echo "       Только в .forgejo/workflows/lint.yml:"
    printf '%s\n' "$ONLY_FORGEJO" | sed 's/^/         /'
  fi
  if [ -n "$ONLY_GITHUB" ]; then
    echo "       Только в .github/workflows/lint.yml:"
    printf '%s\n' "$ONLY_GITHUB" | sed 's/^/         /'
  fi
  exit 1
fi

COUNT="$(printf '%s\n' "$FORGEJO_GATES" | grep -c . || true)"
echo "OK: наборы гейтовых команд совпадают ($COUNT гейт(ов) в каждой копии)."
exit 0
