#!/usr/bin/env bash
# tools/comment-broken.sh
# Генерирует текст комментария для PR при broken cross-links.
# Можно вставить в PR через `gh pr comment`.
#
# Использование:
#   bash tools/comment-broken.sh                    → вывод в stdout
#   bash tools/comment-broken.sh --post <PR_NUMBER>  → комментирует PR через gh
#
# Exit code: 0 (advisory).

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Получить список broken links
BROKEN_LINKS=$(bash tools/check-livedocs-cross-links.sh 2>&1 | grep "^BROKEN " || true)

if [ -z "$BROKEN_LINKS" ]; then
    echo "OK: No broken links — nothing to comment"
    exit 0
fi

# Генерируем markdown comment
generate_comment() {
    cat <<'EOF'
## :warning: LiveDocs cross-links broken

Этот PR вводит broken cross-links в LiveDocs. Запустите `bash tools/check-livedocs-cross-links.sh` для деталей.

EOF

    echo "### Broken links"
    echo ""
    echo "| Source | Target |"
    echo "|--------|--------|"
    echo "$BROKEN_LINKS" | while IFS= read -r line; do
        src=$(echo "$line" | sed -E 's|^BROKEN LINK: (.+) → .*$|\1|')
        target=$(echo "$line" | sed -E 's|^BROKEN LINK: .+ → (.+)$|\1|')
        echo "| \`${src}\` | \`${target}\` |"
    done
    echo ""
    echo "### Как починить"
    echo ""
    echo "См. [\`livedocs/commands/livedocs-find.md\`](../../livedocs/commands/livedocs-find.md) или"
    echo "\`bash tools/suggest-broken-links.sh\` для кандидатов на замену."
    echo ""
    echo "После фикса — перезапустите \`bash tools/check-livedocs-cross-links.sh\` (должно быть 0 broken)."
}

case "${1:-}" in
    "")
        generate_comment
        ;;
    --post)
        pr_number="${2:-}"
        if [ -z "$pr_number" ]; then
            echo "ERROR: --post requires PR number" >&2
            exit 2
        fi
        if ! command -v gh >/dev/null 2>&1; then
            echo "ERROR: gh CLI not installed" >&2
            exit 1
        fi
        comment_body=$(generate_comment)
        echo "$comment_body" | gh pr comment "$pr_number" --body-file -
        echo "OK: Comment posted to PR #$pr_number"
        ;;
    *)
        echo "Usage: bash $0 [--post <PR_NUMBER>]" >&2
        exit 2
        ;;
esac