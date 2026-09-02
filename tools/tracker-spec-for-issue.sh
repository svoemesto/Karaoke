#!/usr/bin/env bash
# =====================================================================
# tools/tracker-spec-for-issue.sh — создать Karaoke-спеку из work package
# =====================================================================
# Алгоритм:
#   1. Получает описание work package через `tracker.sh get-issue`.
#   2. Генерирует kebab-case slug из subject, выбирает следующий номер.
#   3. Создаёт specs/<NN>-<slug>/spec.md с frontmatter + Subject + Source.
#   4. Создаёт specs/<NN>-<slug>/tasks.md с заготовкой чек-листа.
#   5. Добавляет комментарий в OpenProject со ссылкой на спеку.
#
# Использование:
#   bash tools/tracker-spec-for-issue.sh <WORK_PACKAGE_ID>
#   bash tools/tracker-spec-for-issue.sh 42
#
# Exit codes:
#   0 — спека создана
#   1 — work package не найден / нет описания
#   2 — каталог спеки уже существует (use --force для перезаписи)
#   3 — ошибка tracker.sh
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
TRACKER_SH="${SCRIPT_DIR}/tracker.sh"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/tracker-lib.sh"

# Цвета
if [ -t 1 ]; then
    C_RED=$'\033[0;31m'
    C_GREEN=$'\033[0;32m'
    C_YELLOW=$'\033[0;33m'
    C_BLUE=$'\033[0;34m'
    C_BOLD=$'\033[1m'
    C_RESET=$'\033[0m'
else
    C_RED=""; C_GREEN=""; C_YELLOW=""; C_BLUE=""; C_BOLD=""; C_RESET=""
fi

if [ $# -lt 1 ]; then
    echo "${C_RED}ERROR${C_RESET}: usage: tracker-spec-for-issue.sh WORK_PACKAGE_ID [--force]" >&2
    exit 1
fi

WP_ID="$1"
FORCE="false"
[ "${2:-}" = "--force" ] && FORCE="true"

tracker_load_env
tracker_require_tools

# 1. Получаем JSON work package
info="${C_BLUE}▸${C_RESET}"
WP_JSON=$("$TRACKER_SH" get-issue "$WP_ID" 2>&1) || {
    err="${C_RED}✗${C_RESET}"
    echo "${err} Не удалось получить work package #${WP_ID}" >&2
    echo "  $WP_JSON" >&2
    exit 1
}

# 2. Парсим subject + description
SUBJECT=$(echo "$WP_JSON" | jq -r '.subject // empty')
DESCRIPTION=$(echo "$WP_JSON" | jq -r '.description // empty')

if [ -z "$SUBJECT" ]; then
    echo "${C_RED}ERROR${C_RESET}: work package #${WP_ID} не имеет subject" >&2
    exit 1
fi

# Если description пустое — ставим placeholder
if [ -z "$DESCRIPTION" ]; then
    DESCRIPTION="(описание пустое; заполните после запуска спеки)"
fi

# 3. Генерируем slug
#    kebab-case: lowercase, replace [^a-z0-9-] → "-", collapse multiple dashes
SLUG=$(echo "$SUBJECT" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-zа-я0-9]+/-/g' | sed -E 's/-+/-/g' | sed -E 's/^-|-$//g')
[ -z "$SLUG" ] && SLUG="task-$WP_ID"

# Ограничиваем длину slug (до 60 символов)
if [ "${#SLUG}" -gt 60 ]; then
    SLUG="${SLUG:0:60}"
fi

# 4. Следующий номер спеки
NEXT_NUM=$(ls -d "$REPO_ROOT/specs"/[0-9]*-* 2>/dev/null \
            | grep -oE '/[0-9]+-' | grep -oE '[0-9]+' \
            | sort -n | tail -1)
NEXT_NUM="${NEXT_NUM:-0}"
NEXT_NUM=$((NEXT_NUM + 1))

SPEC_DIR="$REPO_ROOT/specs/$(printf '%03d' "$NEXT_NUM")-$SLUG"

if [ -d "$SPEC_DIR" ] && [ "$FORCE" != "true" ]; then
    echo "${C_RED}ERROR${C_RESET}: каталог ${SPEC_DIR} уже существует" >&2
    echo "  Используйте --force для перезаписи (осторожно — стирает существующее)" >&2
    exit 2
fi

# 5. Создаём каталог + spec.md
mkdir -p "$SPEC_DIR"

CREATED_AT=$(date '+%Y-%m-%d')

cat > "$SPEC_DIR/spec.md" <<EOF
---
status: Active
work_package: $WP_ID
created: $CREATED_AT
source: tracker-spec-for-issue.sh
---

# Feature Specification: $(printf '%03d' "$NEXT_NUM") — ${SUBJECT}

**Feature Branch**: \`$(printf '%03d' "$NEXT_NUM")-$SLUG\`
**Created**: $CREATED_AT
**Source work package**: https://localhost:8080/work_packages/$WP_ID (или \`tracker.sh get-issue $WP_ID\`)
**Input**: User description через OpenProject (см. work package #${WP_ID}).

> Спека создана автоматически скриптом \`tools/tracker-spec-for-issue.sh\`
> из work package #${WP_ID}. Заполните её по обычному Karaoke-шаблону
> (User Stories / Functional Requirements / Acceptance Criteria / и т.д.).

## Исходное описание (из work package)

\`\`\`
${DESCRIPTION}
\`\`\`

## Что нужно сделать

1. Прочитать исходное описание выше.
2. Заполнить **User Stories** (что хотим получить).
3. Заполнить **Functional Requirements** (конкретные требования).
4. Сформулировать **Acceptance Criteria** (проверяемые критерии).
5. Создать \`tasks.md\` — декомпозиция на подзадачи.
6. Реализовать по \`tasks.md\`.

## Связь с OpenProject

- Work package: #${WP_ID}
- Reporter (автор задачи): см. \`tracker.sh get-issue $WP_ID | jq -r '.author'\`
- Assignee (по умолчанию): \`ai-agent\`
- URL: \`${TRACKER_URL}/work_packages/${WP_ID}\`

## История

- Создан: $CREATED_AT (Pass из work package #${WP_ID})
EOF

# 6. tasks.md — заготовка чек-листа
cat > "$SPEC_DIR/tasks.md" <<EOF
# Tasks for $(printf '%03d' "$NEXT_NUM") — ${SUBJECT}

> Создано автоматически из work package #${WP_ID}. Заполните по ходу работы.

## Phase 1: Research
- [ ] Понять требования (прочитать work package #${WP_ID})
- [ ] Изучить существующий код (если применимо)
- [ ] Определить зависимости (модули Karaoke, OpenProject, другие компоненты)

## Phase 2: Design
- [ ] Сформулировать подход в \`design.md\`
- [ ] Согласовать с пользователем (если есть возможность — через комментарий в work package #${WP_ID})

## Phase 3: Implementation
- [ ] Реализация по дизайну
- [ ] Локальные тесты
- [ ] Линтеры (ktlint / eslint)

## Phase 4: Verification
- [ ] CI passed (lint.yml)
- [ ] Pre-commit checks OK
- [ ] Пользователь проверил отчёт

## Phase 5: Close
- [ ] \`tracker.sh add-comment ${WP_ID} --file REPORT.md\`
- [ ] \`tracker.sh mark-review ${WP_ID}\`
- [ ] (Пользователь) \`tracker.sh close-issue ${WP_ID}\`
EOF

# 7. Добавляем комментарий в OpenProject
SPEC_PATH=$(basename "$SPEC_DIR")
COMMENT_BODY=$(cat <<EOF
## 📄 Спека создана

Создал Karaoke-спеку:

- **Каталог**: \`specs/${SPEC_PATH}/\`
- **Files**: \`spec.md\` + \`tasks.md\`
- **Номер**: $(printf '%03d' "$NEXT_NUM")
- **Source work package**: #${WP_ID}

Агент приступит к реализации по обычному workflow:
1. Изучит исходное описание
2. Заполнит User Stories / Requirements / Acceptance Criteria
3. Реализует по \`tasks.md\`
4. Опубликует отчёт через \`tracker.sh add-comment ${WP_ID}\`
5. Переведёт в **In review** через \`tracker.sh mark-review ${WP_ID}\`

(После проверки задачу можно закрыть: \`tracker.sh close-issue ${WP_ID}\`)
EOF
)

TMP_COMMENT=$(mktemp)
echo "$COMMENT_BODY" > "$TMP_COMMENT"
"$TRACKER_SH" add-comment "$WP_ID" --file "$TMP_COMMENT" >/dev/null
rm -f "$TMP_COMMENT"

# 8. Вывод результата
echo ""
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo "${C_GREEN}  ✓ Спека создана из work package #${WP_ID}${C_RESET}"
echo "${C_GREEN}═══════════════════════════════════════════════════════════════════${C_RESET}"
echo ""
echo "${C_BOLD}  Каталог:${C_RESET}    ${SPEC_DIR}"
echo "${C_BOLD}  Номер:${C_RESET}      $(printf '%03d' "$NEXT_NUM")"
echo "${C_BOLD}  Slug:${C_RESET}       ${SLUG}"
echo "${C_BOLD}  Files:${C_RESET}      spec.md, tasks.md"
echo ""
echo "${C_BLUE}Следующие шаги:${C_RESET}"
echo "  1. ${C_BOLD}cd ${SPEC_DIR}${C_RESET}"
echo "  2. Открыть \`spec.md\`, заполнить User Stories / FR / AC"
echo "  3. Обновить \`tasks.md\` по мере реализации"
echo "  4. После завершения — \`tracker.sh mark-review ${WP_ID}\`"
