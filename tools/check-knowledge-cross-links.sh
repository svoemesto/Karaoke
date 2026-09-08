#!/usr/bin/env bash
# tools/check-knowledge-cross-links.sh
# Валидирует cross-links внутри knowledge/:
# 1. Все относительные пути `../X.md` валидны (без угловых скобок).
# 2. Все `related:` ссылки соответствуют файлам.
#
# Исключения:
# - `*/templates/*` — содержат плейсхолдеры `<NNN-slug>`, игнорируются.
# - `system/01-context.md`, `system/02-containers.md` — плейсхолдеры без content.
#
# Exit code: 0 если все валидны, 1 если есть broken links.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FAIL=0
CHECKED=0
PLACEHOLDERS=0

# Все .md файлы внутри knowledge/ — НЕ templates, НЕ system/01-02 (плейсхолдеры),
# НЕ README/INDEX/STATS/FAQ/ARCHIVED, НЕ ADR (legacy формат, ссылки могут
# вести в архивную livedocs/ — это исторические ссылки).
TARGETS=$(find knowledge -name '*.md' \
    -not -path '*/templates/*' \
    -not -path '*/system/01-*' \
    -not -path '*/system/02-*' \
    -not -path '*/adr/*' \
    -not -name 'README.md' \
    -not -name 'INDEX.md' \
    -not -name 'STATS.md' \
    -not -name 'FAQ.md' \
    -not -name 'ARCHIVED.md')

echo "[1/2] Проверка относительных путей в Markdown..."
for src in $TARGETS; do
  # Извлекаем все [label](../path/file.md) ссылки
  for target in $(grep -oE '\[[^]]*\]\(\.\./[^)]+\)' "$src" \
        | sed -E 's/.*\(([^)]+)\)/\1/' \
        | grep '\.md$'); do
    # Игнорировать плейсхолдеры (содержат <...>)
    if echo "$target" | grep -q '<.*>'; then
      PLACEHOLDERS=$((PLACEHOLDERS+1))
      continue
    fi
    CHECKED=$((CHECKED+1))
    src_dir=$(dirname "$src")
    abs_target=$(realpath -m "$src_dir/$target" 2>/dev/null || echo "$src_dir/$target")
    abs_target=$(echo "$abs_target" | sed -E 's#/\./#/#g')
    if [ ! -f "$abs_target" ]; then
      echo "BROKEN LINK: $src → $target"
      FAIL=$((FAIL+1))
    fi
  done
done

echo "[2/2] Проверка related: slugs (basic)..."
for src in $TARGETS; do
  # Извлекаем related: ссылки из frontmatter (только между ---).
  related=$(awk '
    /^---$/ {c++; if (c==2) exit; next}
    c==1 && /^related:/ {flag=1; next}
    flag && /^  - / {sub(/^  - /, ""); print; next}
    flag && !/^  / && NF>0 {flag=0}
  ' "$src")
  for ref in $related; do
    [ -z "$ref" ] && continue
    [ "$ref" = "null" ] && continue
    src_dir=$(dirname "$src")
    abs_target=$(realpath -m "$src_dir/$ref" 2>/dev/null || echo "$src_dir/$ref")
    abs_target=$(echo "$abs_target" | sed -E 's#/\./#/#g')
    if [ ! -f "$abs_target" ]; then
      echo "BROKEN RELATED: $src → $ref"
      FAIL=$((FAIL+1))
    fi
  done
done

echo "---"
echo "Total link checks: $CHECKED (skipped $PLACEHOLDERS placeholder(s))"
if [ "$FAIL" -eq 0 ]; then
  echo "OK: All $CHECKED cross-links valid"
  exit 0
else
  echo "FAILED: $FAIL broken link(s)"
  exit 1
fi
