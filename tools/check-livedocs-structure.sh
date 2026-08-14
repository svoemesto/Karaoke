#!/usr/bin/env bash
# tools/check-livedocs-structure.sh
# CI-валидация структуры LiveDocs (см. specs/189-live-documentation/quickstart.md).
# Проверяет: обязательные директории/файлы, ≥ 5 фич, ≥ 5 bounded contexts,
# L1+L2+L3 C4, frontmatter в каждом LiveDoc, длину AGENTS.md (≤ 100 строк),
# наличие шага в .github/workflows/lint.yml.
#
# Exit code: 0 если OK, 1 если есть failures.
# Запускается в GitHub Actions как часть lint.yml.

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

echo "[1/7] Проверка структуры (директории + манифесты)..."
for d in livedocs/features livedocs/domain livedocs/architecture livedocs/templates; do
  check_dir "$d"
done
for f in livedocs/README.md livedocs/INDEX.md \
         livedocs/features/README.md livedocs/domain/README.md \
         livedocs/architecture/README.md livedocs/templates/README.md; do
  check_file "$f"
done

echo "[2/7] Проверка features/ (≥ 5)..."
features_count=$(ls -1 livedocs/features/*.md 2>/dev/null | grep -v README.md | wc -l)
test "$features_count" -ge 5 || { echo "NEED >= 5 features, found $features_count"; FAIL=$((FAIL+1)); }

echo "[3/7] Проверка domain/ (≥ 5)..."
domain_count=$(ls -1 livedocs/domain/*.md 2>/dev/null | grep -v README.md | wc -l)
test "$domain_count" -ge 5 || { echo "NEED >= 5 domains, found $domain_count"; FAIL=$((FAIL+1)); }

echo "[4/7] Проверка C4 L1+L2+L3..."
for level in L1-system-context.md L2-containers.md L3-components.md; do
  f="livedocs/architecture/$level"
  test -f "$f" || { echo "MISSING: $f"; FAIL=$((FAIL+1)); }
  if [ -f "$f" ]; then
    grep -q '^```mermaid' "$f" || { echo "NO MERMAID: $f"; FAIL=$((FAIL+1)); }
  fi
done

echo "[5/7] Проверка frontmatter..."
frontmatter_total=0
frontmatter_fail=0
for f in $(find livedocs -name '*.md' -not -name 'README.md' -not -name 'INDEX.md' -not -path '*/templates/*'); do
  frontmatter_total=$((frontmatter_total+1))
  if ! head -1 "$f" | grep -q '^---$'; then
    echo "NO FRONTMATTER: $f"
    FAIL=$((FAIL+1))
    frontmatter_fail=$((frontmatter_fail+1))
    continue
  fi
  if ! head -10 "$f" | grep -q '^status:'; then
    echo "NO STATUS: $f"
    FAIL=$((FAIL+1))
    frontmatter_fail=$((frontmatter_fail+1))
    continue
  fi
  if ! head -15 "$f" | grep -q '^slug:'; then
    echo "NO SLUG: $f"
    FAIL=$((FAIL+1))
    frontmatter_fail=$((frontmatter_fail+1))
  fi
done

echo "[6/7] Проверка AGENTS.md (<= 100 строк)..."
agents_lines=$(wc -l < AGENTS.md)
test "$agents_lines" -le 100 || { echo "AGENTS.md = $agents_lines lines, need <= 100"; FAIL=$((FAIL+1)); }

echo "[7/7] Проверка CI integration..."
if [ -f .github/workflows/lint.yml ]; then
  grep -q 'check-livedocs-structure' .github/workflows/lint.yml || { echo "CI NOT CONFIGURED: check-livedocs-structure not in lint.yml"; FAIL=$((FAIL+1)); }
else
  echo "MISSING: .github/workflows/lint.yml"
  FAIL=$((FAIL+1))
fi

echo "---"
echo "Files with frontmatter: $((frontmatter_total - frontmatter_fail))/$frontmatter_total"
if [ "$FAIL" -eq 0 ]; then
  echo "OK: LiveDocs structure valid (7/7 checks passed)"
  exit 0
else
  echo "FAILED: $FAIL check(s) failed"
  exit 1
fi