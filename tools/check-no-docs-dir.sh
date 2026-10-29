#!/usr/bin/env bash
# tools/check-no-docs-dir.sh
#
# Guard: каталог `docs/` в корне репозитория запрещён.
#
# Назначение: FR-012, FR-001, SC-001, SC-005 (ADR-0011). После слияния слоя
# `docs/` в `knowledge/` второй слой проектной документации запрещён — иначе
# появится параллельная правда о документации.
#
# Использование:
#   ./tools/check-no-docs-dir.sh [--root <dir>]
#
#   --root <dir>   корень проверяемого дерева (по умолчанию — корень репозитория);
#                  нужен для прогона на фикстурах tools/fixtures/no-docs-dir/.
#
# Коды возврата:
#   0 — нарушений нет;
#   1 — нарушение найдено (перечислены до 20 отслеживаемых файлов + счётчик);
#   2 — проверка не выполнена (нет git, корень не найден).
#
# По образцу tools/check-feature-doc.sh (BR-509): коды 0/1/2 различаются, чтобы
# CI мог отличить «нарушение» от «проверка не выполнена».

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
      echo "  --root <dir>: корень проверяемого дерева (по умолчанию корень репозитория)"
      exit 0
      ;;
    *)
      echo "ERROR: неизвестный аргумент: $1" >&2
      echo "Usage: $0 [--root <dir>]" >&2
      exit 2
      ;;
  esac
done

if [ ! -d "$ROOT" ]; then
  echo "ERROR: корень не найден: $ROOT" >&2
  echo "       проверка не выполнена." >&2
  exit 2
fi

if ! command -v git >/dev/null 2>&1; then
  echo "ERROR: git не установлен — проверка не выполнена." >&2
  exit 2
fi

# ── Проверка 1: отслеживаемые файлы в docs/ ────────────────────────────────
TRACKED="$(git -C "$ROOT" ls-files -- docs/ 2>/dev/null)"
GIT_STATUS=$?
if [ $GIT_STATUS -ne 0 ]; then
  echo "ERROR: не удалось получить список отслеживаемых файлов (git -C \"$ROOT\" ls-files)." >&2
  echo "       проверка не выполнена." >&2
  exit 2
fi

VIOLATION=0

if [ -n "$TRACKED" ]; then
  VIOLATION=1
  COUNT="$(printf '%s\n' "$TRACKED" | wc -l | tr -d ' ')"
  echo "[FAIL] Каталог docs/ в корне репозитория запрещён (ADR-0011)."
  echo "       knowledge/ — единственный слой проектной документации."
  echo "       Если материал нужен — перенесите его в knowledge/ (см. knowledge/README.md § «Бывшие пути»)."
  echo "       Отслеживаемые файлы в docs/ ($COUNT):"
  printf '%s\n' "$TRACKED" | head -20 | sed 's/^/         /'
  if [ "$COUNT" -gt 20 ]; then
    echo "         … и ещё $((COUNT - 20))"
  fi
fi

# ── Проверка 2: каталог docs/ на диске ─────────────────────────────────────
if [ -d "$ROOT/docs" ]; then
  VIOLATION=1
  if [ -z "$TRACKED" ]; then
    echo "[FAIL] Каталог docs/ в корне репозитория запрещён (ADR-0011)."
    echo "       knowledge/ — единственный слой проектной документации."
    echo "       Если материал нужен — перенесите его в knowledge/ (см. knowledge/README.md § «Бывшие пути»)."
  fi
  echo "       Каталог присутствует на диске: $ROOT/docs"
  echo "       (запрещён даже тогда, когда его файлы не отслеживаются git)."
fi

if [ "$VIOLATION" -eq 1 ]; then
  exit 1
fi

echo "OK: каталога docs/ нет (ни на диске, ни среди отслеживаемых файлов)."
exit 0
