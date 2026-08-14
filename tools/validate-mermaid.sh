#!/usr/bin/env bash
# tools/validate-mermaid.sh
# Проверяет синтаксис Mermaid-блоков в LiveDocs:
# - Каждый блок ```mermaid имеет пару закрывающих ```.
# - Первая непустая строка в блоке — корректный diagram type
#   (graph LR/TD, flowchart, sequenceDiagram, classDiagram, stateDiagram, и т.д.).
# - Нет очевидных ошибок синтаксиса (например, "graph" без направления).
#
# Использование:
#   bash tools/validate-mermaid.sh                 → все файлы в livedocs/
#   bash tools/validate-mermaid.sh <file.md>       → один файл
#
# Exit code: 0 если OK, 1 если есть issues.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Допустимые первые непустые строки mermaid-блока (после opening fence).
# Это упрощённый список — полный список см. https://mermaid.js.org/syntax/structure.html
VALID_FIRST_WORDS="graph|flowchart|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt|pie|quadrantChart|requirementDiagram|gitGraph|infoArchitecture|piechart|block-beta|architecture-beta|radarBeta|kanban|timeline|sankey-beta|sequence-canvas"

ISSUES=0

validate_file() {
    local file="$1"
    local in_mermaid=0
    local block_start_line=0
    local block_content=""
    local line_num=0

    while IFS= read -r line; do
        line_num=$((line_num+1))

        # Открытие mermaid-блока
        if [[ "$line" == '```mermaid' ]]; then
            in_mermaid=1
            block_start_line=$line_num
            block_content=""
            continue
        fi

        # Закрытие ``` блока
        if [[ "$line" == '```' ]]; then
            if [ "$in_mermaid" -eq 1 ]; then
                # Проверяем содержимое закрытого блока
                local first_word
                first_word=$(echo "$block_content" | grep -v "^$" | head -1 | awk '{print $1}')

                if [ -z "$first_word" ]; then
                    echo "  $file: L$block_start_line: EMPTY_MERMAID_BLOCK"
                    ISSUES=$((ISSUES+1))
                elif ! echo "$first_word" | grep -qE "^($VALID_FIRST_WORDS)\$"; then
                    echo "  $file: L$block_start_line: UNKNOWN_DIAGRAM_TYPE: $first_word"
                    ISSUES=$((ISSUES+1))
                fi

                # Проверка на "graph" без направления
                if [ "$first_word" = "graph" ]; then
                    local direction
                    direction=$(echo "$block_content" | grep -v "^$" | head -1 | awk '{print $2}')
                    if [ -z "$direction" ] || [[ ! "$direction" =~ ^(LR|TD|BT|RL)$ ]]; then
                        echo "  $file: L$block_start_line: GRAPH_MISSING_DIRECTION (LR/TD/BT/RL)"
                        ISSUES=$((ISSUES+1))
                    fi
                fi

                in_mermaid=0
            fi
            continue
        fi

        # Собираем содержимое mermaid-блока
        if [ "$in_mermaid" -eq 1 ]; then
            block_content+="$line"$'\n'
        fi
    done < "$file"

    # Не закрыт mermaid-блок
    if [ "$in_mermaid" -eq 1 ]; then
        echo "  $file: L$block_start_line: UNCLOSED_MERMAID_BLOCK"
        ISSUES=$((ISSUES+1))
    fi
}

if [ $# -eq 1 ] && [ -f "$1" ]; then
    validate_file "$1"
elif [ $# -eq 0 ]; then
    # Только файлы с mermaid-блоками
    while IFS= read -r f; do
        validate_file "$f"
    done < <(grep -lr '^\`\`\`mermaid$' livedocs/ 2>/dev/null | sort)
else
    echo "Usage: bash $0 [file.md]"
    exit 2
fi

echo ""
echo "---"
if [ "$ISSUES" -eq 0 ]; then
    echo "OK: All Mermaid blocks valid"
    exit 0
else
    echo "FOUND: $ISSUES Mermaid issue(s)"
    exit 1
fi