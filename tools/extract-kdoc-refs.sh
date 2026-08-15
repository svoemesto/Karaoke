#!/usr/bin/env bash
# tools/extract-kdoc-refs.sh
#
# Извлекает @see ссылки из KDoc Kotlin-файлов и предлагает related: для LiveDoc.
# Парсит @see docs/features/X.md, @see archive/docs/features/X.md,
# @see livedocs/features/X.md и т.д.
#
# Использование:
#   bash tools/extract-kdoc-refs.sh <LiveDoc>          → suggestions для одного
#   bash tools/extract-kdoc-refs.sh --all              → все LiveDoc
#   bash tools/extract-kdoc-refs.sh --missing-refs     → @see которые не резолвятся
#
# Exit code: 0 = OK, 1 = errors

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

extract_for_file() {
    local livefile="$1"
    local livefile_rel="${livefile#livedocs/}"

    # Извлечь slug (например, 164-complete-guest-share-link → complete-guest-share-link)
    local live_basename=$(basename "$livefile" .md)
    local live_slug=$(echo "$live_basename" | sed 's/^[0-9]*-//')

    # Извлечь ВСЕ возможные suffix-варианты slug (от самого длинного к самому короткому)
    # complete-guest-share-link → [complete-guest-share-link, guest-share-link, link]
    local slug_variants=()
    IFS='-' read -ra parts <<< "$live_slug"
    local n=${#parts[@]}
    for ((i=0; i<n; i++)); do
        local variant=""
        for ((j=i; j<n; j++)); do
            if [ -z "$variant" ]; then
                variant="${parts[$j]}"
            else
                variant="$variant-${parts[$j]}"
            fi
            slug_variants+=("$variant")
        done
    done

    # Ищем @see с любым из slug-вариантов
    local all_see=""
    for try_slug in "${slug_variants[@]}"; do
        # 1) archive/docs/features/<slug>.md
        local see_archive=$(grep -rE "see archive/docs/features/${try_slug}\.md" karaoke-app/src karaoke-web/src karaoke-public/src webvue3/src 2>/dev/null | head -5)

        # 2) docs/features/<slug>.md (legacy)
        local see_legacy=$(grep -rE "see docs/features/${try_slug}\.md" karaoke-app/src karaoke-web/src karaoke-public/src webvue3/src 2>/dev/null | head -5)

        all_see="${all_see}${see_archive}
${see_legacy}
"
    done

    all_see=$(echo "$all_see" | grep -v '^$' | sort -u | head -10)

    if [ -n "$all_see" ]; then
        echo "=== $livefile ==="
        echo "$all_see" | head -5
        echo ""
    fi
}

if [ $# -eq 0 ]; then
    echo "Usage: bash tools/extract-kdoc-refs.sh <file|--all|--missing-refs>"
    exit 1
fi

if [ "$1" = "--all" ]; then
    for f in livedocs/features/*.md; do
        basename "$f" | grep -q README && continue
        extract_for_file "$f"
    done
elif [ "$1" = "--missing-refs" ]; then
    echo "=== @see ссылки, которые не резолвятся ==="
    # Извлечь все @see URLs из .kt/.vue/.js файлов
    # Используем 2 grep (без alternation, совместимость с разными grep)
    { grep -rohE "@see docs/features/[a-z0-9-]+\.md" karaoke-app/src karaoke-web/src karaoke-public/src webvue3/src 2>/dev/null | sed -E 's/^@see //'
      grep -rohE "@see archive/docs/features/[a-z0-9-]+\.md" karaoke-app/src karaoke-web/src karaoke-public/src webvue3/src 2>/dev/null | sed -E 's/^@see //'
    } | sort -u > /tmp/_see_all.txt

    FOUND=0
    MISSING=0
    while IFS= read -r ref; do
        # Извлечь имя файла (последняя часть)
        file=$(echo "$ref" | sed -E 's|.*/features/||')
        # Проверить существование
        full_path="livedocs/features/$file"
        archive_path="archive/docs/features/$file"
        if [ -f "$full_path" ]; then
            :  # OK
        elif [ -f "$archive_path" ]; then
            :  # OK
        else
            echo "  MISSING: $ref"
            MISSING=$((MISSING+1))
        fi
        FOUND=$((FOUND+1))
    done < /tmp/_see_all.txt
    rm /tmp/_see_all.txt

    echo ""
    echo "Found: $FOUND, Missing: $MISSING"
else
    extract_for_file "$1"
fi