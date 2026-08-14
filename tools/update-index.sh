#!/usr/bin/env bash
# tools/update-index.sh
# Перегенерирует INDEX.md на основе текущего состояния LiveDocs.
# Сканирует features/, domain/, architecture/, runbooks/, commands/, decisions/.
# Извлекает frontmatter (status, slug, type).
# Вывод в stdout (можно перенаправить в файл или сделать ручной diff).
#
# Использование:
#   bash tools/update-index.sh                  → в stdout
#   bash tools/update-index.sh --diff           → unified diff с текущим INDEX.md
#   bash tools/update-index.sh --apply          → обновить INDEX.md (commit отдельно)
#
# Exit code: 0 если OK, 1 если ошибка.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Извлечение frontmatter через awk
extract_frontmatter() {
    local file="$1"
    local field="$2"
    awk -v field="$field" '
        BEGIN { in_fm=0; }
        /^---$/{ if (in_fm == 0) { in_fm = 1; next } else { in_fm = 0; next } }
        in_fm == 1 {
            if ($0 ~ "^"field":") {
                sub(/^[^:]+: */, "")
                print
                exit
            }
        }
    ' "$file"
}

# Подсчёт для каждого слоя
count_in_dir() {
    local dir="$1"
    local pattern="${2:-*.md}"
    ls -1 "$dir"/$pattern 2>/dev/null | grep -v 'README.md' | wc -l
}

cat_new_index() {
cat << 'EOF'
# LiveDocs — Layer Map (INDEX)

> **Auto-generated** by `tools/update-index.sh`. Этот файл перегенерируется при изменении LiveDocs.
> Для ручного edit — измените `tools/update-index.sh` (или `livedocs/INDEX.md` после ручных правок).

## Decision tree

```
Task is about...
│
├─ A specific feature (NNN)?
│   └─ → livedocs/features/<NNN-slug>.md
│       (if not yet — specs/<NNN>-*/spec.md)
│
├─ A module / domain (Song, Album, KaraokeVideo, ...)?
│   └─ → livedocs/domain/<context>.md
│       (catalog | processing | rendering | publishing | stats | identity | editorial)
│
├─ Architecture (how does the system work)?
│   ├─ Overall (who uses the system) → livedocs/architecture/L1-system-context.md
│   ├─ Containers (apps + stores) → livedocs/architecture/L2-containers.md
│   └─ Components (inside karaoke-app) → livedocs/architecture/L3-components.md
│
└─ Specific pattern / pitfall / decision?
    └─ → livedocs/architecture/<topic>.md
        or livedocs/architecture/decisions/<NNNN-slug>.md
```

EOF

echo "## Layers and their contents"
echo ""
echo "### SDD — \`livedocs/features/\`"
echo ""
features_total=$(count_in_dir "livedocs/features")
echo "Всего: **$features_total файлов** (см. \`livedocs/features/README.md\` для полного списка)."
echo ""

echo "### DDD — \`livedocs/domain/\`"
echo ""
echo "| File | Bounded Context | Aggregate Roots |"
echo "|------|-----------------|-----------------|"
for f in livedocs/domain/*.md; do
    [ "$(basename "$f")" = "README.md" ] && continue
    slug=$(extract_frontmatter "$f" "slug")
    [ -z "$slug" ] && slug=$(basename "$f" .md)
    has_aggregate=$(grep -c "^## Aggregate Roots" "$f")
    ar_text="✅" || ar_text="—"
    if [ "$has_aggregate" -gt 0 ]; then ar_text="✅ Aggregate Roots"; else ar_text="—"; fi
    echo "| [\`${slug}.md\`](${slug}.md) | ${slug} | $ar_text |"
done
echo ""

echo "### C4 — \`livedocs/architecture/\`"
echo ""
echo "#### C4 Levels"
echo ""
echo "| File | Level |"
echo "|------|-------|"
for f in livedocs/architecture/L[0-9]-*.md; do
    [ -z "$f" ] || [ ! -f "$f" ] && continue
    slug=$(basename "$f" .md)
    level=$(echo "$slug" | cut -d- -f1)
    echo "| [\`${slug}.md\`](${slug}.md) | $level |"
done
echo ""

echo "#### Topics (drill-down by specific theme)"
echo ""
echo "| File | Topic |"
echo "|------|-------|"
for f in livedocs/architecture/[a-z]*.md; do
    [ -z "$f" ] || [ ! -f "$f" ] && continue
    slug=$(basename "$f" .md)
    type=$(extract_frontmatter "$f" "type")
    [ -z "$type" ] && type="topic"
    echo "| [\`${slug}.md\`](${slug}.md) | \`$type\` |"
done
echo ""

echo "#### ADR (Architecture Decision Records)"
echo ""
echo "| File | Decision |"
echo "|------|----------|"
for f in livedocs/architecture/decisions/[0-9][0-9][0-9][0-9]-*.md; do
    [ -z "$f" ] || [ ! -f "$f" ] && continue
    slug=$(basename "$f" .md)
    echo "| [\`${slug}.md\`](decisions/${slug}.md) | $(echo "$slug" | sed -E 's/^[0-9]+-//; s/-/ /g') |"
done
echo ""
echo "#### Local ADR (subsystem conventions)"
echo ""
echo "| File | Title |"
echo "|------|-------|"
for f in livedocs/architecture/decisions/local-*.md; do
    [ -z "$f" ] || [ ! -f "$f" ] && continue
    slug=$(basename "$f" .md)
    echo "| [\`${slug}.md\`](decisions/${slug}.md) | $(echo "$slug" | sed -E 's/^local-[0-9]+-//; s/-/ /g') |"
done
echo ""

echo "### Runbooks — \`livedocs/runbooks/\`"
echo ""
runbooks_total=$(count_in_dir "livedocs/runbooks")
echo "Всего: **$runbooks_total** (см. \`livedocs/runbooks/README.md\`)."
echo ""

echo "### Commands (AI-agent slash-commands) — \`livedocs/commands/\`"
echo ""
echo "| File | Команда |"
echo "|------|---------|"
for f in livedocs/commands/*.md; do
    [ "$(basename "$f")" = "README.md" ] && continue
    slug=$(basename "$f" .md)
    desc=$(extract_frontmatter "$f" "description")
    echo "| [\`${slug}.md\`](${slug}.md) | \`/${slug}\` — $desc |"
done
echo ""

echo "### Tools — \`tools/\`"
echo ""
echo "Скрипты валидации и утилиты — см. [\`tools/README.md\`](../tools/README.md)."
echo "Главные: \`search-livedocs.sh\`, \`check-livedocs-structure.sh\`, \`check-livedocs-cross-links.sh\`, \`check-livedocs-external-links.sh\`, \`gen-livedocs-stats.sh\`, \`gen-livedocs-index.sh\`."
echo ""

echo "## English mirror — \`livedocs-en/\`"
echo ""
en_total=$(find livedocs-en -name '*.md' 2>/dev/null | wc -l)
echo "Всего: **$en_total** файлов (см. \`livedocs-en/README.md\`)."
echo ""

echo "## Когда обновлять INDEX"
echo ""
echo "- Новый LiveDoc → перегенерировать через \`bash tools/update-index.sh > livedocs/INDEX.md\`."
echo "- Удалён LiveDoc → удалить строку + перегенерировать."
echo "- Изменился статус → то же."
}

case "${1:-}" in
    "")
        cat_new_index
        ;;
    --diff)
        # Показать diff
        cur=$(mktemp)
        cat_new_index > "$cur"
        diff -u livedocs/INDEX.md "$cur" || true
        rm -f "$cur"
        ;;
    --apply)
        cat_new_index > livedocs/INDEX.md
        echo "OK: INDEX.md updated"
        ;;
    *)
        echo "Usage: bash $0 [--diff | --apply]"
        exit 2
        ;;
esac