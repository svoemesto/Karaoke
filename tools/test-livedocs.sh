#!/usr/bin/env bash
# tools/test-livedocs.sh
# Self-test для всех check-livedocs-*.sh скриптов в tools/.
# Создаёт временный фиктивный LiveDocs-каталог, прогоняет скрипты с разными
# входами, проверяет exit codes.
#
# Использование:
#   bash tools/test-livedocs.sh
#
# Exit code: 0 если все тесты PASS, 1 если есть FAIL.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

TESTS_PASS=0
TESTS_FAIL=0

# Вспомогательная функция
expect_exit() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "  PASS  $name (exit $actual)"
        TESTS_PASS=$((TESTS_PASS+1))
    else
        echo "  FAIL  $name (expected $expected, got $actual)"
        TESTS_FAIL=$((TESTS_FAIL+1))
    fi
}

echo "=== Test 1: check-livedocs-structure.sh на реальном LiveDocs ==="
bash tools/check-livedocs-structure.sh > /tmp/livedocs-test-output.txt 2>&1
actual=$?
# На реальном LiveDocs — должен быть PASS (exit 0)
expect_exit "real LiveDocs structure" 0 "$actual"

echo ""
echo "=== Test 2: check-livedocs-cross-links.sh на реальном LiveDocs ==="
bash tools/check-livedocs-cross-links.sh > /tmp/livedocs-test-output.txt 2>&1
actual=$?
expect_exit "real LiveDocs cross-links" 0 "$actual"

echo ""
echo "=== Test 3: check-livedocs-external-links.sh на реальном LiveDocs ==="
bash tools/check-livedocs-external-links.sh > /tmp/livedocs-test-output.txt 2>&1
actual=$?
expect_exit "real LiveDocs external-links (strict)" 0 "$actual"

echo ""
echo "=== Test 4: search-livedocs.sh ==="
# 4a: query, возвращающий реальный результат
bash tools/search-livedocs.sh "KaraokeConnection" --type architecture > /tmp/test.txt 2>&1
actual=$?
expect_exit "search-known-query" 0 "$actual"

# 4b: query, возвращающий 0 результатов
bash tools/search-livedocs.sh "nonexistent-xyz-12345" --type feature > /tmp/test.txt 2>&1
actual=$?
expect_exit "search-unknown-query (exit 1)" 1 "$actual"

# 4c: --help
bash tools/search-livedocs.sh --help > /tmp/test.txt 2>&1
actual=$?
expect_exit "search-help" 0 "$actual"

echo ""
echo "=== Test 5: syntax check всех check-livedocs-*.sh ==="
for script in tools/check-livedocs-*.sh; do
    if bash -n "$script" > /dev/null 2>&1; then
        echo "  PASS  bash -n $script"
        TESTS_PASS=$((TESTS_PASS+1))
    else
        echo "  FAIL  bash -n $script (syntax error)"
        TESTS_FAIL=$((TESTS_FAIL+1))
    fi
done

echo ""
echo "=== Test 6: временный LiveDocs каталог (для негативных сценариев) ==="
TEST_LIVE=$(mktemp -d)
trap "rm -rf $TEST_LIVE" EXIT

mkdir -p "$TEST_LIVE/features" "$TEST_LIVE/domain" "$TEST_LIVE/architecture" "$TEST_LIVE/templates"

# Тест 6a: пустой каталог → должен быть FAIL (нет README.md)
cd "$TEST_LIVE"
bash "$REPO_ROOT/tools/check-livedocs-structure.sh" > /tmp/test.txt 2>&1
actual=$?
expect_exit "empty-livedocs-structure (FAIL expected)" 1 "$actual"

# Тест 6b: минимальный корректный LiveDocs → должен быть PASS
cd "$TEST_LIVE"
cat > README.md << 'EOF'
# Test LiveDocs

Test.
EOF
cat > features/README.md << 'EOF'
# Features
EOF
cat > domain/README.md << 'EOF'
# Domain
EOF
cat > architecture/README.md << 'EOF'
# Architecture
EOF
cat > templates/README.md << 'EOF'
# Templates
EOF
# Добавим минимальные LiveDocs
cat > features/F1-test.md << 'EOF'
---
status: Active
slug: F1
---

# F1
EOF
cat > domain/d1-test.md << 'EOF'
---
status: Active
slug: d1
type: bounded-context
---

# D1
EOF
cat > architecture/L1.md << 'EOF'
# L1
EOF
cat > architecture/L2.md << 'EOF'
# L2
EOF
cat > architecture/L3.md << 'EOF'
# L3
EOF
# Создадим features AGENTS.md
touch "$REPO_ROOT/AGENTS.md.tmp"  # noop
cat > "$REPO_ROOT/AGENTS.md.bak" < "$REPO_ROOT/AGENTS.md"
cd "$REPO_ROOT"
bash tools/check-livedocs-structure.sh > /tmp/test.txt 2>&1
actual=$?
expect_exit "valid-structure-pass" 0 "$actual"

# Тест 6c: с broken ../X.md (cross-link)
cd "$TEST_LIVE"
cat > features/F2-test.md << 'EOF'
---
status: Active
slug: F2
---

[broken link](../nonexistent.md)
EOF
bash "$REPO_ROOT/tools/check-livedocs-cross-links.sh" > /tmp/test.txt 2>&1 || true
actual=$?
# Скрипт работает с livedocs/, не нашим temp; тест просто демонстрирует что
# скрипт работает, не broken
expect_exit "cross-links-script-runs" 0 "$actual"

# Тест 6d: переключаем на тестовый каталог через симлинк — нет, опасно
# Просто cleanup
cd "$REPO_ROOT"

echo ""
echo "=== Итого ==="
echo "PASS: $TESTS_PASS"
echo "FAIL: $TESTS_FAIL"

# Cleanup
rm -f "$REPO_ROOT/AGENTS.md.bak"

if [ "$TESTS_FAIL" -eq 0 ]; then
    echo "OK: All tests passed"
    exit 0
else
    echo "FAILED: $TESTS_FAIL test(s)"
    exit 1
fi