#!/usr/bin/env bash
# tools/check-livedocs-coverage.sh
#
# Расширенная проверка покрытия LiveDocs:
# [1] specs → LiveDoc: для каждой спеки в specs/ должен быть livedocs/features/<NNN-slug>.md
# [2] LiveDoc → spec: каждый LiveDoc в features/ должен иметь corresponding spec
# [3] frontmatter: каждый LiveDoc имеет status, slug, related
# [4] bounded contexts: ≥ 5 BC в livedocs/domain/
# [5] cross-links: не должно быть broken (базовая проверка)
# [6] size: каждый LiveDoc ≤ 200 строк (warning)
# [7] archive/docs/features: legacy docs cleanup
#
# Использование:
#   bash tools/check-livedocs-coverage.sh
#
# Exit code:
#   0 — все проверки PASS
#   1 — есть FAIL (broken link, missing LiveDoc, etc.)

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

TOTAL_FAIL=0

# === [1] specs → LiveDoc ===
echo "[1/7] Сканирование specs/ → проверка наличия LiveDoc..."
SPECS=$(ls -1 specs/ | grep -E '^[0-9]+-' | sort)
# Исключаем 189-live-documentation (мета-спека — LiveDoc не нужен)
SPECS=$(echo "$SPECS" | grep -v '^189-live-documentation$')
SPECS_TOTAL=$(echo "$SPECS" | wc -l)

MISSING=0
for spec in $SPECS; do
    if [ ! -f "livedocs/features/${spec}.md" ]; then
        echo "MISSING: specs/${spec}/  →  livedocs/features/${spec}.md (НЕТ)"
        MISSING=$((MISSING+1))
        TOTAL_FAIL=$((TOTAL_FAIL+1))
    fi
done

echo "  specs/: $SPECS_TOTAL директорий, missing LiveDoc: $MISSING"
echo ""

# === [2] LiveDoc → spec (orphan check) ===
echo "[2/7] Сканирование livedocs/features/ → проверка наличия spec..."
ORPHAN=0
for live in livedocs/features/*.md; do
    if [ "$(basename "$live")" = "README.md" ]; then
        continue
    fi
    name=$(basename "$live" .md)
    if [ ! -d "specs/${name}" ]; then
        echo "ORPHAN: livedocs/features/${name}.md (нет corresponding spec)"
        ORPHAN=$((ORPHAN+1))
        TOTAL_FAIL=$((TOTAL_FAIL+1))
    fi
done

echo "  Orphan LiveDocs: $ORPHAN"
echo ""

# === [3] frontmatter валидация ===
echo "[3/7] Проверка frontmatter (status, slug)..."
FM_FAIL=0
for live in livedocs/features/*.md; do
    if [ "$(basename "$live")" = "README.md" ]; then
        continue
    fi
    if ! head -1 "$live" | grep -q '^---$'; then
        echo "  NO FRONTMATTER: $live"
        FM_FAIL=$((FM_FAIL+1))
        TOTAL_FAIL=$((TOTAL_FAIL+1))
        continue
    fi
    if ! head -10 "$live" | grep -q '^status:'; then
        echo "  NO STATUS: $live"
        FM_FAIL=$((FM_FAIL+1))
        TOTAL_FAIL=$((TOTAL_FAIL+1))
        continue
    fi
    if ! head -15 "$live" | grep -q '^slug:'; then
        echo "  NO SLUG: $live"
        FM_FAIL=$((FM_FAIL+1))
        TOTAL_FAIL=$((TOTAL_FAIL+1))
    fi
done

echo "  Frontmatter failures: $FM_FAIL"
echo ""

# === [4] bounded contexts ===
echo "[4/7] Проверка bounded contexts (≥ 5)..."
BC_COUNT=$(ls -1 livedocs/domain/*.md 2>/dev/null | grep -v README | wc -l)
if [ "$BC_COUNT" -lt 5 ]; then
    echo "  FAIL: только $BC_COUNT bounded contexts в livedocs/domain/ (требуется ≥ 5)"
    TOTAL_FAIL=$((TOTAL_FAIL+1))
else
    echo "  OK: $BC_COUNT bounded contexts"
fi
echo ""

# === [5] cross-links (базовая быстрая проверка) ===
echo "[5/7] Проверка cross-links (быстрая)..."
CL_FAIL=0
for src in $(find livedocs -name '*.md' \
    -not -path '*/templates/*' \
    -not -path '*/runbooks/*' \
    -not -path '*/decisions/*' \
    -not -name 'README.md' \
    -not -name 'INDEX.md' 2>/dev/null); do
    for target in $(grep -oE '\[[^]]*\]\(\.\./[^)]+\.md\)' "$src" 2>/dev/null \
        | sed -E 's/.*\(([^)]+)\)/\1/'); do
        if echo "$target" | grep -q '<.*>'; then
            continue
        fi
        src_dir=$(dirname "$src")
        abs_target=$(realpath -m "$src_dir/$target" 2>/dev/null || echo "$src_dir/$target")
        if [ ! -f "$abs_target" ]; then
            CL_FAIL=$((CL_FAIL+1))
        fi
    done
done

if [ "$CL_FAIL" -gt 0 ]; then
    echo "  FAIL: $CL_FAIL broken cross-links (use tools/check-livedocs-cross-links.sh для деталей)"
    TOTAL_FAIL=$((TOTAL_FAIL+1))
else
    echo "  OK: cross-links valid"
fi
echo ""

# === [6] size (warning) ===
echo "[6/7] Проверка размера LiveDoc (≤ 200 строк)..."
SIZE_WARN=0
SIZE_OVER=0
for live in livedocs/features/*.md; do
    if [ "$(basename "$live")" = "README.md" ]; then
        continue
    fi
    lines=$(wc -l < "$live")
    if [ "$lines" -gt 200 ]; then
        echo "  OVER: $(basename $live) — $lines строк (лимит 200)"
        SIZE_OVER=$((SIZE_OVER+1))
    elif [ "$lines" -gt 150 ]; then
        SIZE_WARN=$((SIZE_WARN+1))
    fi
done

echo "  Over 200 lines: $SIZE_OVER, warning 150-200: $SIZE_WARN"
echo ""

# === [7] archive/docs/features cleanup ===
echo "[7/7] Проверка archive/docs/features/..."
ARCHIVE_COUNT=$(ls -1 archive/docs/features/*.md 2>/dev/null | grep -v README | wc -l)
if [ "$ARCHIVE_COUNT" -gt 0 ]; then
    # Найти archive без corresponding LiveDoc — informational (warning)
    # Archive может содержать документы от старых фич без specs/, это OK
    ORPHAN_ARCH=0
    for arch in archive/docs/features/*.md; do
        if [ "$(basename "$arch")" = "README.md" ]; then
            continue
        fi
        name=$(basename "$arch" .md)
        if [ ! -f "livedocs/features/${name}.md" ]; then
            # Не считаем FAIL — это informational
            :
        fi
    done
    echo "  Archive: $ARCHIVE_COUNT файлов (informational, не блокирует)"
else
    echo "  Archive пуст (нет legacy docs/)"
fi
echo ""

# === Итог ===
echo "==============================="
echo "ИТОГ:"
echo "  specs/: $SPECS_TOTAL директорий"
echo "  live docs/features/: $(ls -1 livedocs/features/*.md | grep -v README | wc -l) файлов"
echo "  live domain/: $BC_COUNT BC"
echo "  archive/docs/features/: $ARCHIVE_COUNT legacy"
echo "  cross-links broken: $CL_FAIL"
echo "  Total FAIL: $TOTAL_FAIL"
echo "==============================="

if [ "$TOTAL_FAIL" -eq 0 ]; then
    echo "OK: все проверки PASS"
    exit 0
else
    echo "FAILED: $TOTAL_FAIL проверок не прошли"
    exit 1
fi