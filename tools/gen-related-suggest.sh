#!/usr/bin/env bash
# tools/gen-related-suggest.sh
#
# Генерирует suggestions для related: в frontmatter LiveDoc на основе:
# 1. Backlinks — кто ссылается на этот файл в related:?
# 2. Same-domain — другие LiveDoc в том же домене (NNN-prefix)
# 3. Spec cross-ref — если LiveDoc для спеки, предлагать связанные спеки
# 4. Directory locality — LiveDoc в том же directory
#
# Использование:
#   bash tools/gen-related-suggest.sh <file>     → suggestions для одного файла
#   bash tools/gen-related-suggest.sh --all      → suggestions для всех LiveDoc
#   bash tools/gen-related-suggest.sh --missing   → LiveDoc с < 3 related
#
# Exit code: 0 = OK, 1 = no suggestions

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

suggest_for_file() {
    local file="$1"
    local file_livedocs="${file#livedocs/}"

    # Текущие related (между строками "related:" и следующим ключом или "---")
    local current=$(awk '
        BEGIN { in_frontmatter=0; in_related=0 }
        /^---$/ {
            if (in_frontmatter == 0) { in_frontmatter = 1; next }
            if (in_frontmatter == 1) { in_frontmatter = 0; in_related = 0; exit }
        }
        in_frontmatter == 1 && /^related:/ { in_related = 1; next }
        in_related == 1 && /^  - / { print $2; next }
        in_related == 1 && /^[a-z]/ { in_related = 0 }
    ' "$file" 2>/dev/null)

    # Backlinks (кто ссылается на этот файл)
    local backlinks=$(grep -lE "(/|\.\./)+${file_livedocs#/}" livedocs/ -r 2>/dev/null | \
        grep -v "^${file}$" | \
        grep -v 'archive/' | \
        grep -v -E '(README|INDEX|CHANGELOG|SESSION-SUMMARY|INDEX_CARD|STATS|FAQ)\.md$' | \
        head -10)

    # Backlinks с related: на этот файл
    local related_backlinks=$(grep -lE "  - (\.\./)+${file_livedocs#/}" livedocs/ -r 2>/dev/null | \
        grep -v "^${file}$" | head -5)

    # Same-directory (если есть)
    local dir=$(dirname "$file")
    local same_dir=$(ls -1 "$dir" 2>/dev/null | grep -v README | grep -v "$(basename "$file")" | head -5)

    # BOUNDED CONTEXT (если LiveDoc начинается с NNN и matched BC)
    local num=$(echo "$file_livedocs" | cut -d- -f1)
    local bc_candidates=$(grep -lE "${num}-" livedocs/features/*.md 2>/dev/null | head -5)

    # ARCHITECTURE topics (если frontmatter связан)
    local arch_topics=$(ls -1 livedocs/architecture/[a-z]*.md 2>/dev/null | head -10)

    echo "=== Suggestions for $file ==="
    [ -n "$current" ] && echo "Current related:"
    [ -n "$current" ] && echo "$current" | sed 's/^/  - /'
    echo ""
    echo "1. Backlinks (кто ссылается на этот файл):"
    [ -n "$backlinks" ] && echo "$backlinks" | sed 's/^/  /' || echo "  (none)"
    echo ""
    echo "2. Related backlinks (с related: на этот файл):"
    [ -n "$related_backlinks" ] && echo "$related_backlinks" | sed 's/^/  /' || echo "  (none)"
    echo ""
    echo "3. Same directory:"
    [ -n "$same_dir" ] && echo "$same_dir" | sed 's/^/  /' || echo "  (none)"
    echo ""
    echo "4. Possible BC candidates (по NNN):"
    [ -n "$bc_candidates" ] && echo "$bc_candidates" | sed 's/^/  /' || echo "  (none)"
    echo ""
}

if [ $# -eq 0 ]; then
    echo "Usage: bash tools/gen-related-suggest.sh <file|--all|--missing>"
    exit 1
fi

if [ "$1" = "--all" ]; then
    for f in livedocs/features/*.md livedocs/architecture/[a-z]*.md; do
        [ -f "$f" ] || continue
        basename "$f" | grep -q README && continue
        echo ""
        suggest_for_file "$f"
    done
elif [ "$1" = "--missing" ]; then
    echo "LiveDoc with < 3 related:"
    for f in livedocs/features/*.md livedocs/architecture/[a-z]*.md livedocs/domain/*.md livedocs/runbooks/*.md; do
        [ -f "$f" ] || continue
        basename "$f" | grep -q README && continue
        count=$(awk '/^related:/{flag=1; next} flag && /^- /{c++; next} flag && /^[a-z]/{exit} END{print c+0}' "$f")
        if [ "$count" -lt 3 ]; then
            echo "  $f: $count related"
        fi
    done
else
    suggest_for_file "$1"
fi