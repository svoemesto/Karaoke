#!/usr/bin/env bash
# tools/check-livedocs-cross-links.sh
# Валидирует cross-links внутри LiveDocs:
# 1. Все относительные пути `../X.md` валидны (без угловых скобок).
# 2. Все `related:` ссылки соответствуют файлам (c учитом wildcard и spec/).
#
# Исключения:
# - `*/templates/*` — содержат плейсхолдеры `<NNN-slug>`, игнорируются.
# - `*/runbooks/*` и `*/decisions/*` — без frontmatter (другая конвенция).
#
# Exit code: 0 если все валидны, 1 если есть broken links.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FAIL=0
CHECKED=0
PLACEHOLDERS=0

# Все .md файлы внутри LiveDocs — НЕ templates/runbooks/decisions/READMEs/INDEX
TARGETS=$(find livedocs -name '*.md' \
    -not -path '*/templates/*' \
    -not -path '*/runbooks/*' \
    -not -path '*/decisions/*' \
    -not -name 'README.md' \
    -not -name 'INDEX.md' \
    -not -name 'INDEX_CARD.md')

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
  # Извлекаем строки вида `  - ../path/file.md` (related: frontmatter)
  for related in $(grep -E '^\s*-\s+\.\./.*\.md$' "$src" 2>/dev/null \
        | awk '{print $2}'); do
    # Игнорировать плейсхолдеры
    if echo "$related" | grep -q '<.*>'; then
      PLACEHOLDERS=$((PLACEHOLDERS+1))
      continue
    fi
    CHECKED=$((CHECKED+1))
    src_dir=$(dirname "$src")
    abs_target=$(realpath -m "$src_dir/$related" 2>/dev/null || echo "$src_dir/$related")
    abs_target=$(echo "$abs_target" | sed -E 's#/\./#/#g')
    # Ссылка может указывать на specs/ — это drill-down
    if [[ "$abs_target" == *"/specs/"* ]]; then
      spec_dir=$(dirname "$abs_target")
      if [ ! -d "$spec_dir" ]; then
        echo "BROKEN SPECS: $src → $related"
        FAIL=$((FAIL+1))
      fi
    elif [ ! -f "$abs_target" ]; then
      echo "BROKEN RELATED: $src → $related"
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