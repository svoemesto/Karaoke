#!/usr/bin/env bash
# tools/check-md-structure.sh
# Проверяет структуру Markdown файлов в LiveDocs:
# - Заголовки идут последовательно (нет H1→H3 без H2).
# - Нет висячих пробелов.
# - Mermaid блоки закрыты.
#
# Использование:
#   bash tools/check-md-structure.sh                → все файлы в livedocs/
#   bash tools/check-md-structure.sh <file.md>      → один файл
#
# Exit code: 0 если OK, 1 если есть problems.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

PROBLEMS=0

check_file() {
    local file="$1"

    # 1. Заголовки последовательны (нет skip-level: H1→H3)
    local skip_lines
    skip_lines=$(awk '
        BEGIN { prev_level = 0; in_code = 0 }
        /^```/{ in_code = !in_code; next }
        in_code { next }
        /^# / {
            level = length($0); sub(/^#+/, "", level)
            if (prev_level > 0 && level > prev_level + 1) {
                printf("L%d:H%d after H%d\n", NR, level, prev_level)
            }
            prev_level = level
        }
    ' "$file")
    if [ -n "$skip_lines" ]; then
        while IFS= read -r line; do
            echo "  $file: STRUCTURAL_SKIP: $line"
            PROBLEMS=$((PROBLEMS+1))
        done <<< "$skip_lines"
    fi

    # 2. Висячие пробелы (trailing whitespace)
    local trail_lines
    trail_lines=$(grep -nE ' +$' "$file" 2>/dev/null || true)
    if [ -n "$trail_lines" ]; then
        local count
        count=$(echo "$trail_lines" | wc -l)
        echo "  $file: TRAILING_WHITESPACE: $count line(s)"
        PROBLEMS=$((PROBLEMS+count))
    fi

    # 3. Mermaid блоки закрыты
    local open_mermaid
    open_mermaid=$(grep -c '^```mermaid$' "$file" 2>/dev/null)
    open_mermaid=${open_mermaid:-0}
    local all_fences
    all_fences=$(grep -c '^```' "$file" 2>/dev/null)
    all_fences=${all_fences:-0}
    if [ "$open_mermaid" -gt 0 ]; then
        local required_fences=$((open_mermaid * 2))
        if [ "$all_fences" -lt "$required_fences" ]; then
            echo "  $file: UNCLOSED_MERMAID: $open_mermaid mermaid block(s) but only $all_fences total fence(s)"
            PROBLEMS=$((PROBLEMS+1))
        fi
    fi
}

if [ $# -eq 1 ] && [ -f "$1" ]; then
    check_file "$1"
elif [ $# -eq 0 ]; then
    for f in $(find livedocs -name '*.md' -not -path '*/templates/*' | sort); do
        check_file "$f"
    done
else
    echo "Usage: bash $0 [file.md]"
    exit 2
fi

echo ""
echo "---"
if [ "$PROBLEMS" -eq 0 ]; then
    echo "OK: All Markdown structure checks passed"
    exit 0
else
    echo "FOUND: $PROBLEMS structural issue(s)"
    exit 1
fi