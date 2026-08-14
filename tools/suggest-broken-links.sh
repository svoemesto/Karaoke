#!/usr/bin/env bash
# tools/suggest-broken-links.sh
# Для каждого broken cross-link в LiveDocs предлагает возможные
# исправления (поиск похожих имён файлов в livedocs/).
#
# Использование:
#   bash tools/suggest-broken-links.sh               → suggest для всех broken
#   bash tools/suggest-broken-links.sh <file.md>     → suggest для одного файла
#
# Exit code: 0 (всегда — это advisory, не CI-gate).

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Сначала получим список broken links
BROKEN_LINKS=$(bash tools/check-livedocs-cross-links.sh 2>&1 | grep "^BROKEN ")

if [ -z "$BROKEN_LINKS" ]; then
    echo "OK: All cross-links valid — nothing to suggest"
    exit 0
fi

echo "=== Broken links suggestions ==="
echo ""

# Собираем все существующие файлы в livedocs/ (для нечёткого поиска)
ALL_FILES=$(find livedocs -name '*.md' -not -name 'README.md' | sort)

suggest_for() {
    local src="$1"
    local target="$2"
    local abs_target="$3"

    echo "BROKEN: $src → $target"

    # Извлечь имя файла без пути и расширения
    local target_basename
    target_basename=$(basename "$target" .md)

    # Найти похожие файлы (substring match)
    local candidates
    candidates=$(echo "$ALL_FILES" | grep -F "$target_basename" | head -5 || true)

    if [ -n "$candidates" ]; then
        echo "  Возможные кандидаты:"
        echo "$candidates" | while IFS= read -r cand; do
            [ -z "$cand" ] && continue
            echo "    → $cand"
        done
    else
        echo "  (похожих файлов не найдено)"
    fi
    echo ""
}

# Парсим broken links (формат: "BROKEN LINK: <src> → <target>")
echo "$BROKEN_LINKS" | while IFS= read -r line; do
    [ -z "$line" ] && continue

    # Извлекаем src и target через regex
    src=$(echo "$line" | sed -E 's|^BROKEN LINK: (.+) → .*$|\1|')
    target=$(echo "$line" | sed -E 's|^BROKEN LINK: .+ → (.+)$|\1|')

    [ -z "$src" ] || [ -z "$target" ] && continue

    src_dir=$(dirname "$src")
    abs_target=$(realpath -m "$src_dir/$target" 2>/dev/null || echo "$src_dir/$target")

    suggest_for "$src" "$target" "$abs_target"
done

echo ""
echo "---"
echo "Если кандидат не найден — нужно создать файл или исправить ссылку."