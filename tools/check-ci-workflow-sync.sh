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
# Вторая проверка того же guard'а — ВАЛИДНОСТЬ YAML обеих копий.
# Прецедент (KARAOKE-334, 2026-10-29): неэкранированное двоеточие в значении
# `- name: Guard: ...` сделало обе копии невалидными — CI падал за 0 секунд,
# ни один джоб не стартовал, при этом все локальные гейты были зелёными
# (сравнение наборов команд проходило, т.к. обе копии были одинаково сломаны).
# Невалидный workflow роняет весь прогон до старта джобов, поэтому валидность
# проверяется локально, до push.
#
# Использование:
#   ./tools/check-ci-workflow-sync.sh [--root <dir>]
#
#   --root <dir>   корень дерева с копиями workflow (по умолчанию — корень
#                  репозитория); нужен для прогона на фикстурах
#                  tools/fixtures/ci-workflow-sync/.
#
# Коды возврата:
#   0 — наборы гейтовых команд совпадают, обе копии — валидный YAML;
#   1 — расхождение (выведены строки, присутствующие только в одной копии)
#       либо одна из копий не является валидным YAML;
#   2 — проверка не выполнена (нет git/файла копии, корень не найден,
#       PyYAML недоступен — проверить валидность нечем).
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

# Валидность YAML: обе копии обязаны разбираться как YAML.
# BR-597-подход: «проверить не удалось» ≠ «нарушений нет» → отдельный код 2.
# PyYAML — фактическая зависимость репозитория (его импортирует
# tools/check-ssot-impact.py, который тоже работает в CI).
validate_yaml() {
  python3 - "$1" <<'PY'
import sys

try:
    import yaml
except ImportError:
    print("PyYAML недоступен — валидность workflow проверить не удалось.")
    sys.exit(2)

try:
    with open(sys.argv[1], encoding="utf-8") as fh:
        yaml.safe_load(fh)
except Exception as exc:  # yaml.YAMLError и любые ошибки чтения
    print(f"файл не разбирается как YAML: {exc}")
    sys.exit(1)

sys.exit(0)
PY
}

for f in "$FORGEJO" "$GITHUB"; do
  yaml_out="$(validate_yaml "$f" 2>&1)"
  yaml_rc=$?
  case "$yaml_rc" in
    0)
      ;;
    1)
      echo "[FAIL] Файл workflow не является валидным YAML: $f"
      echo "       $yaml_out"
      echo "       Невалидный workflow роняет CI целиком ещё до старта джобов."
      exit 1
      ;;
    *)
      echo "[FAIL] Валидность workflow проверить не удалось: $f"
      echo "       $yaml_out"
      echo "       Проверка не выполнена (BR-597: неизвестно ≠ нарушений нет)."
      exit 2
      ;;
  esac
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
