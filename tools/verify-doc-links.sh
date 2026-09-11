#!/usr/bin/env bash
# tools/verify-doc-links.sh
#
# Проверяет ссылки в Markdown-документах через lychee.
# Использование:
#   ./tools/verify-doc-links.sh [paths...]
# По умолчанию: docs/features/ CONTRIBUTING.md docs/
#
# Требования: lychee (https://github.com/lycheeverse/lychee).
# Установка: cargo install lychee
#            или скачать бинарь с https://github.com/lycheeverse/lychee/releases
#
# Режимы:
#   --offline  : только проверка синтаксиса и локальных файлов (без HTTP)
#   (по умолчанию) : полная проверка, включая внешние URL (HEAD-запросы)

set -euo pipefail

# По умолчанию — offline, чтобы не зависеть от сети в pre-commit
OFFLINE_FLAG="--offline"
TARGETS=()

for arg in "$@"; do
  case "$arg" in
    --online)
      OFFLINE_FLAG=""
      ;;
    --offline)
      OFFLINE_FLAG="--offline"
      ;;
    -h|--help)
      echo "Usage: $0 [--online] [paths...]"
      echo "  --online: проверять внешние URL (HEAD-запросы)"
      echo "  --offline: только локальные файлы (по умолчанию)"
      echo "  paths: файлы/директории для проверки (по умолчанию docs/features/ CONTRIBUTING.md docs/)"
      exit 0
      ;;
    *)
      TARGETS+=("$arg")
      ;;
  esac
done

if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=("docs/features/" "CONTRIBUTING.md" "docs/")
fi

if ! command -v lychee >/dev/null 2>&1; then
  echo "ERROR: lychee не установлен."
  echo "  Установка: cargo install lychee"
  echo "  Или скачайте бинарь: https://github.com/lycheeverse/lychee/releases"
  exit 1
fi

echo "==> Запуск lychee (${OFFLINE_FLAG:-online}) на: ${TARGETS[*]}"

# Lychee 0.24+ возвращает exit 0 даже при broken links (by design). Для строгого
# поведения оборачиваем в grep по выводу: если есть [ERROR] строки — exit 1.
# Это нужно, чтобы pre-commit/CI ловили регрессии, а не просто печатали warnings.
LYCHEE_OUTPUT=$(lychee $OFFLINE_FLAG --no-progress "${TARGETS[@]}" 2>&1) || true
echo "$LYCHEE_OUTPUT"

if echo "$LYCHEE_OUTPUT" | grep -q '\[ERROR\]'; then
  echo "==> verify-doc-links: найдены broken links (см. [ERROR] строки выше)"
  exit 1
fi
exit 0
