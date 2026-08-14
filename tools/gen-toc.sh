#!/usr/bin/env bash
# tools/gen-toc.sh
# Генерирует блок "## Содержание" для указанного .md файла в LiveDocs.
# Выводит в stdout (можно скопировать в начало файла после # Title).
#
# Использование:
#   bash tools/gen-toc.sh livedocs/architecture/mlt-pipeline.md    → TOC для одного файла
#   bash tools/gen-toc.sh --all                                   → TOC для всех файлов LiveDocs > 30 строк
#   bash tools/gen-toc.sh --missing                               → список файлов БЕЗ TOC
#
# Exit code: 0 если OK, 1 если ошибка.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

generate_toc() {
    local file="$1"
    if [ ! -f "$file" ]; then
        echo "ERROR: file not found: $file" >&2
        return 1
    fi
    if grep -q "^## Содержание" "$file"; then
        return 2
    fi
    local lines
    lines=$(wc -l < "$file")
    if [ "$lines" -lt 30 ]; then
        return 3
    fi

    echo ""
    echo "========== TOC для $file ($lines строк) =========="
    echo ""
    # Собираем заголовки в awk, затем печатаем единый блок.
    awk '
    BEGIN { in_code=0 }
    /^```/{ in_code = !in_code; next }
    in_code { next }
    /^## / {
        text = $0
        sub(/^## /, "", text)
        gsub(/[`*_]/, "", text)
        printf("H2\t%s\n", text)
    }
    /^### / {
        text = $0
        sub(/^### /, "", text)
        gsub(/[`*_]/, "", text)
        printf("H3\t%s\n", text)
    }
    ' "$file" | awk -F'\t' '
    BEGIN { print "## Содержание\n" }
    /^H2/ {
        text = $2
        slug = tolower(text)
        gsub(/[^a-z0-9а-я -]/, "", slug)
        gsub(/ /, "-", slug)
        printf("- [%s](#%s)\n", text, slug)
    }
    /^H3/ {
        text = $2
        slug = tolower(text)
        gsub(/[^a-z0-9а-я -]/, "", slug)
        gsub(/ /, "-", slug)
        printf("  - [%s](#%s)\n", text, slug)
    }
    '
    echo ""
    echo "====================================="
    echo ""
}

if [ $# -eq 0 ]; then
    echo "Usage: bash $0 <file.md> | --all | --missing"
    exit 2
fi

case "$1" in
    --missing)
        echo "Files in livedocs/ > 30 lines WITHOUT '## Содержание':"
        echo ""
        for f in $(find livedocs -name '*.md' -not -path '*/templates/*'); do
            lines=$(wc -l < "$f")
            if [ "$lines" -ge 30 ] && ! grep -q "^## Содержание" "$f"; then
                echo "  $f ($lines lines)"
            fi
        done | sort
        ;;
    --all)
        for f in $(find livedocs -name '*.md' -not -path '*/templates/*' | sort); do
            generate_toc "$f"
        done
        ;;
    *.md)
        generate_toc "$1"
        ;;
    *)
        echo "ERROR: unknown arg: $1" >&2
        exit 2
        ;;
esac