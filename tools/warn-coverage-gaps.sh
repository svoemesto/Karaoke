#!/usr/bin/env bash
# tools/warn-coverage-gaps.sh
# Показывает список спека-фич без LiveDoc-сводки как рекомендацию
# для следующих follow-up PR.
#
# Использование:
#   bash tools/warn-coverage-gaps.sh                → stdout
#   bash tools/warn-coverage-gaps.sh --github-issue → создаёт GitHub issue
#
# Exit code: 0 если 100% coverage, 1 если есть gaps.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "[1/1] Поиск спека-фич без LiveDoc-сводки..."

MISSING=()
for spec in $(ls -1 specs/ | grep -E '^[0-9]+-' | sort); do
    if [ ! -f "livedocs/features/${spec}.md" ]; then
        MISSING+=("$spec")
    fi
done

if [ ${#MISSING[@]} -eq 0 ]; then
    echo ""
    echo "OK: 100% coverage — все спеки имеют LiveDoc-сводки."
    exit 0
fi

echo ""
echo "=== Missing LiveDoc-сводки (${#MISSING[@]}) ==="
echo ""
echo "Следующие спеки не имеют LiveDoc-сводки в livedocs/features/:"
echo ""
for spec in "${MISSING[@]}"; do
    echo "  - $spec"
done

echo ""
echo "=== Рекомендация ==="
echo ""
echo "Для каждой missing спеки — создать \`livedocs/features/<spec>.md\` по шаблону:"
echo "  cp livedocs/templates/feature-summary.md livedocs/features/<spec>.md"
echo "  # отредактировать"
echo ""
echo "=== Готово ==="

case "${1:-}" in
    --github-issue)
        # Создать GitHub issue через gh CLI
        if command -v gh >/dev/null 2>&1; then
            body=$(printf "LiveDocs coverage gap — %d спека(и) без LiveDoc-сводки.\n\n" "${#MISSING[@]}")
            body+=$(printf "\`\`\`\n%s\n\`\`\`\n\n" "$(printf '%s\n' "${MISSING[@]}")")
            body+="## Как фиксить\n\nСоздать LiveDoc-сводку по [шаблону](../../livedocs/templates/feature-summary.md):\n\n"
            body+='```bash
cp livedocs/templates/feature-summary.md livedocs/features/<NNN-slug>.md
# отредактировать (frontmatter + body <= 2 страниц)
```'
            gh issue create \
                --title "LiveDocs coverage: ${#MISSING[@]} spec(s) without summary" \
                --body "$body" \
                --label "livedocs" \
                --label "documentation"
            echo "OK: GitHub issue created"
        else
            echo "ERROR: gh CLI not installed" >&2
            exit 1
        fi
        ;;
    "")
        :  # Already printed
        ;;
    *)
        echo "Usage: bash $0 [--github-issue]" >&2
        exit 2
        ;;
esac

exit 0  # advisory — есть gaps, но не CI-gate