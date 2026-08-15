#!/usr/bin/env bash
# tools/gen-spec-skeleton.sh
#
# Генерирует LiveDoc-skeleton (livedocs/features/<NNN-slug>.md) на основе
# specs/<NNN-slug>/spec.md. Используется когда новая фича начинается:
# создаёт пустой LiveDoc по шаблону, чтобы разработчик сразу мог его заполнить.
#
# Использование:
#   bash tools/gen-spec-skeleton.sh <NNN>           → создать LiveDoc для спеки NNN
#   bash tools/gen-spec-skeleton.sh --all           → создать LiveDoc для всех спек
#                                                     без LiveDoc (кроме 189)
#   bash tools/gen-spec-skeleton.sh --missing       → показать спеки без LiveDoc
#
# Exit code: 0 = OK, 1 = ошибка, 2 = уже существует.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

create_skeleton() {
    local spec_dir="$1"
    local spec_path="$spec_dir/spec.md"

    if [ ! -f "$spec_path" ]; then
        echo "ERROR: spec.md не найден: $spec_path" >&2
        return 1
    fi

    # Извлечь NNN-slug из пути
    local name=$(basename "$spec_dir")
    local num=$(echo "$name" | cut -d- -f1)
    local slug=$(echo "$name" | cut -d- -f2-)

    # Определить путь LiveDoc
    local live_path="livedocs/features/${name}.md"

    if [ -f "$live_path" ]; then
        echo "EXISTS: $live_path"
        return 2
    fi

    # Извлечь title из spec.md (первая строка # Feature Specification: ...)
    local title=$(head -1 "$spec_path" | sed 's/^# Feature Specification: //')

    # Извлечь spec path для related
    local spec_rel="../../specs/${name}/spec.md"

    # Создать LiveDoc-skeleton (по шаблону livedocs/templates/feature-summary.md)
    cat > "$live_path" <<EOF
---
status: Active
slug: $name
related:
  - $spec_rel
---

# $num — $title (LiveDoc)

> Drill-down — [specs/$name/spec.md]($spec_rel).

## Что делает

[1-2 абзаца: что делает фича, кому нужна, какой проблемы решает]

## User Stories (краткий список)

- **US1** [заголовок] — [1 строка сути]
- **US2** [заголовок] — [1 строка сути]

## Functional Requirements (указатель)

- **FR-001**: [1 строка описания]
- **FR-002**: [1 строка описания]

## Acceptance Criteria

- [ ] **AC1**: [Given ...] When ... Then ...
- [ ] **AC2**: ...

## Связанные LiveDocs

- Domain: [bounded-context](../domain/<bounded-context>.md)
- Architecture: [topic](../architecture/<topic>.md)

## Код

- Backend: \`karaoke-app/src/main/kotlin/.../<package>/<Class>.kt\`
- Frontend: \`webvue3/src/.../...vue\`
- API: \`POST /api/.../...\`

## История

- Создан: $(date +%Y-%m-%d) (Pass 2+ спеки 189, auto-skeleton)
EOF

    echo "CREATED: $live_path"
}

if [ $# -eq 0 ]; then
    echo "Usage: bash tools/gen-spec-skeleton.sh <NNN>|--all|--missing"
    exit 1
fi

if [ "$1" = "--all" ]; then
    CREATED=0
    SKIPPED=0
    for spec_dir in specs/[0-9]*-*; do
        name=$(basename "$spec_dir")
        num=$(echo "$name" | cut -d- -f1)
        # Исключаем 189 (мета-спека)
        [ "$num" = "189" ] && continue
        # Проверить, есть ли LiveDoc
        if [ ! -f "livedocs/features/${name}.md" ]; then
            create_skeleton "$spec_dir"
            CREATED=$((CREATED+1))
        else
            SKIPPED=$((SKIPPED+1))
        fi
    done
    echo "=== ИТОГ: создано $CREATED skeletons, пропущено $SKIPPED ==="
elif [ "$1" = "--missing" ]; then
    echo "=== Спеки без LiveDoc ==="
    MISSING=0
    for spec_dir in specs/[0-9]*-*; do
        name=$(basename "$spec_dir")
        num=$(echo "$name" | cut -d- -f1)
        [ "$num" = "189" ] && continue
        if [ ! -f "livedocs/features/${name}.md" ]; then
            echo "  specs/$name/  →  livedocs/features/$name.md"
            MISSING=$((MISSING+1))
        fi
    done
    echo "=== ИТОГ: missing: $MISSING ==="
elif [[ "$1" =~ ^[0-9]+$ ]]; then
    # Найти спеку по NNN
    found=$(find specs -maxdepth 1 -type d -name "${1}-*" 2>/dev/null | head -1)
    if [ -z "$found" ]; then
        echo "ERROR: спека ${1}-* не найдена" >&2
        exit 1
    fi
    create_skeleton "$found"
else
    echo "Usage: bash tools/gen-spec-skeleton.sh <NNN>|--all|--missing"
    exit 1
fi