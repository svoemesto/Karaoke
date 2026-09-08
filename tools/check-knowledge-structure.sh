#!/usr/bin/env bash
# tools/check-knowledge-structure.sh
# CI-валидация структуры knowledge/ (Living Documentation v2, SSoT).
# Проверяет: обязательные директории/файлы, наличие доменов, ADR, README'ей,
# наличие cross-link в главном README на каждый домен.
#
# Запускается в GitHub Actions как часть lint.yml.
#
# Exit code: 0 если OK, 1 если есть failures.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

FAIL=0

check_file() {
  test -f "$1" || { echo "MISSING: $1"; FAIL=$((FAIL+1)); }
}

check_dir() {
  test -d "$1" || { echo "MISSING DIR: $1"; FAIL=$((FAIL+1)); }
}

echo "[1/9] Проверка структуры (директории + манифесты)..."
for d in knowledge/system knowledge/domains knowledge/adr knowledge/epics \
         knowledge/guidelines knowledge/public knowledge/templates; do
  check_dir "$d"
done
for f in knowledge/README.md \
         knowledge/system/README.md \
         knowledge/domains/README.md \
         knowledge/adr/README.md \
         knowledge/epics/README.md \
         knowledge/guidelines/README.md \
         knowledge/public/README.md \
         knowledge/templates/README.md; do
  check_file "$f"
done

echo "[2/9] Проверка шаблонов (templates/ — domain, component, adr, epic)..."
for t in domain.md component.md adr.md epic.md; do
  check_file "knowledge/templates/$t"
done

echo "[3/9] Проверка системного слоя (system/)..."
check_file "knowledge/system/01-context.md"
check_file "knowledge/system/02-containers.md"

echo "[4/9] Проверка доменов (≥ 9: identity, catalog, rendering, processing, ...)..."
for dom in identity catalog rendering processing publishing editorial monitoring stats caching; do
  d="knowledge/domains/$dom"
  check_dir "$d"
  if [ -d "$d" ]; then
    check_file "$d/domain.md"
  fi
done

echo "[5/9] Проверка ADR (≥ 1)..."
adr_count=$(ls -1 knowledge/adr/*.md 2>/dev/null | grep -v README.md | wc -l)
test "$adr_count" -ge 1 || { echo "NEED >= 1 ADR, found $adr_count"; FAIL=$((FAIL+1)); }

echo "[6/9] Проверка cross-link в README на каждый домен (L3 → L2 → L1)..."
# Главный knowledge/README.md должен иметь ссылку [Name](domains/<name>/domain.md)
# для каждого существующего домена. Линтер audit_structural_links() в
# tools/lint-knowledge.py делает полную проверку; здесь только smoke test.
for dom in identity catalog rendering processing publishing editorial monitoring stats caching; do
  if [ -f "knowledge/README.md" ] && [ -f "knowledge/domains/$dom/domain.md" ]; then
    if ! grep -qE "\[.*\]\(domains/$dom/domain\.md\)" "knowledge/README.md"; then
      echo "MISSING LINK in knowledge/README.md → domains/$dom/domain.md"
      FAIL=$((FAIL+1))
    fi
  fi
done

echo "[7/9] Проверка списка доменов в domains/README.md..."
for dom in identity catalog rendering processing publishing editorial monitoring stats caching; do
  if [ -f "knowledge/domains/README.md" ] && [ -d "knowledge/domains/$dom" ]; then
    if ! grep -qE "\[.*\]\($dom/domain\.md\)" "knowledge/domains/README.md"; then
      echo "MISSING LINK in knowledge/domains/README.md → $dom/domain.md"
      FAIL=$((FAIL+1))
    fi
  fi
done

echo "[8/9] Проверка frontmatter (только domains/<name>/domain.md и epics/)..."
# Frontmatter обязателен только для:
# - domains/<name>/domain.md (там, где важна state-based классификация).
# - epics/EPIC-*.md (когда появятся).
# НЕ обязателен для: README, components/*, system/* (плейсхолдеры),
# guidelines/*, public/*, adr/* (старый Karaoke-формат).
fm_total=0
fm_fail=0
for f in $(find knowledge/domains -name 'domain.md' 2>/dev/null) \
         $(find knowledge/epics -name '*.md' -not -name 'README.md' 2>/dev/null); do
  fm_total=$((fm_total+1))
  if ! head -1 "$f" | grep -q '^---$'; then
    echo "NO FRONTMATTER: $f"
    fm_fail=$((fm_fail+1))
    FAIL=$((FAIL+1))
    continue
  fi
  if ! head -10 "$f" | grep -q '^status:'; then
    echo "NO STATUS: $f"
    fm_fail=$((fm_fail+1))
    FAIL=$((FAIL+1))
  fi
done

echo "[9/9] Проверка CI integration (этот скрипт должен быть в lint.yml)..."
if [ -f .github/workflows/lint.yml ]; then
  if ! grep -q 'check-knowledge-structure' .github/workflows/lint.yml; then
    echo "CI NOT CONFIGURED: check-knowledge-structure not in lint.yml"
    FAIL=$((FAIL+1))
  fi
else
  echo "MISSING: .github/workflows/lint.yml"
  FAIL=$((FAIL+1))
fi

echo "---"
echo "Files with frontmatter: $((fm_total - fm_fail))/$fm_total"
if [ "$FAIL" -eq 0 ]; then
  echo "OK: knowledge/ structure valid (9/9 checks passed)"
  exit 0
else
  echo "FAILED: $FAIL check(s) failed"
  exit 1
fi
