#!/usr/bin/env bash
# tools/check-livedocs-coverage.sh
# Проверяет покрытие: для каждой спеки в specs/ есть ли соответствующая
# LiveDoc-сводка в livedocs/features/<NNN-slug>.md?
#
# Использование:
#   bash tools/check-livedocs-coverage.sh
#
# Exit code: 0 если все спеки покрыты, 1 если есть gaps.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "[1/2] Сканирование specs/..."
# Извлекаем NNN-slug для каждой директории в specs/
SPECS=$(ls -1 specs/ | grep -E '^[0-9]+-' | sort)
SPECS_TOTAL=$(echo "$SPECS" | wc -l)

echo "Найдено $SPECS_TOTAL директорий в specs/"

echo ""
echo "[2/2] Проверка покрытия в livedocs/features/..."
MISSING=0
for spec in $SPECS; do
    # spec = "NNN-slug"
    nnn=$(echo "$spec" | cut -d- -f1)
    # Проверяем, есть ли livedocs/features/<spec>.md
    if [ -f "livedocs/features/${spec}.md" ]; then
        :  # OK
    else
        echo "MISSING: specs/${spec}/  →  livedocs/features/${spec}.md (НЕТ)"
        MISSING=$((MISSING+1))
    fi
done

echo ""
echo "---"
echo "Total specs: $SPECS_TOTAL"
echo "Missing LiveDocs: $MISSING"

if [ "$MISSING" -eq 0 ]; then
    echo "OK: 100% coverage (all specs have LiveDoc summaries)"
    exit 0
else
    coverage=$(awk "BEGIN {printf \"%.1f\", (($SPECS_TOTAL - $MISSING) * 100.0 / $SPECS_TOTAL)}")
    echo "COVERAGE: $coverage%"
    echo "Tip: для каждой MISSING spec создай livedocs/features/<spec>.md"
    exit 1
fi