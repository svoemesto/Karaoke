#!/usr/bin/env bash
# tools/specify-bootstrap.sh
#
# Bootstrap feature-ветки для новой спецификации. Вызывается хуком
# `before_specify` из `.specify/extensions.yml` (или вручную агентом перед
# `/speckit.specify`).
#
# Зачем (см. AGENTS.md, «CI-gate для master»):
#   Прямая работа в master ЗАПРЕЩЕНА — каждое изменение только через
#   feature-ветку NNN-slug + PR + CI 7/7. Хук автоматизирует создание
#   ветки, чтобы агент не забыл (см. коммит-историю — повторяющиеся
#   «устал напоминать» от пользователя).
#
# Использование:
#   tools/specify-bootstrap.sh my-feature-slug
#     → резервирует NNN, создаёт ветку ${NNN}-my-feature-slug от master,
#       переключается на неё, печатает JSON в stdout.
#
#   tools/specify-bootstrap.sh
#     → без аргумента: печатает usage в stderr, exit 2.
#
# Env:
#   SPECIFY_FEATURE_DIRECTORY — если задан (export), используется как есть
#     (номер уже в имени директории спеки; иначе резервируем новый NNN).
#   GIT_BRANCH_NAME — если задан, используется как имя ветки без префикса
#     NNN- (для случая «хочу именно это имя»).
#
# Output (stdout):
#   {"BRANCH_NAME": "${NNN}-${slug}", "FEATURE_NUM": "${NNN}"}
#   JSON для downstream tools (/speckit.plan, /speckit.tasks) — см.
#   системный промпт /speckit.specify, секция «Branch creation».

set -euo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel)
cd "$REPO_ROOT"

SLUG="${1:-}"

if [ -z "$SLUG" ]; then
  cat >&2 <<EOF
Usage: tools/specify-bootstrap.sh <slug>

  <slug>  kebab-case, 2-4 слова, описывает фичу (например: fix-author-cleanup,
          add-progress-meter, search-pagination).

Поведение:
  1. Резервирует следующий свободный NNN через tools/reserve-branch-number.sh
     (push уникального lightweight-тега refs/tags/seq/NNN в origin).
  2. Создаёт ветку \${NNN}-\${slug} от master (или использует имя из
     env GIT_BRANCH_NAME, если задано).
  3. Переключается на новую ветку.
  4. Печатает JSON {BRANCH_NAME, FEATURE_NUM} в stdout — для downstream
     команд (/speckit.plan, /speckit.tasks).

Env:
  SPECIFY_FEATURE_DIRECTORY  если задан, не резервируем новый NNN, а
                             используем имя директории спеки как есть
                             (например, 'specs/180-og-seo-html').
  GIT_BRANCH_NAME            если задан, используется как имя ветки
                             без префикса NNN-.
EOF
  exit 2
fi

# Валидация slug: kebab-case, без пробелов, без спецсимволов.
if ! [[ "$SLUG" =~ ^[a-z0-9]+(-[a-z0-9]+)*$ ]]; then
  echo "ERROR: slug '$SLUG' должен быть kebab-case (буквы/цифры/дефисы)" >&2
  exit 2
fi

# Определяем имя ветки.
if [ -n "${GIT_BRANCH_NAME:-}" ]; then
  BRANCH="$GIT_BRANCH_NAME"
  # GIT_BRANCH_NAME задан — НЕ резервируем номер, предполагается что агент
  # уже зарезервировал его вручную через tools/reserve-branch-number.sh.
  FEATURE_NUM=""
  if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    git checkout -b "$BRANCH" master
  else
    git checkout "$BRANCH"
  fi
elif [ -n "${SPECIFY_FEATURE_DIRECTORY:-}" ]; then
  # SPECIFY_FEATURE_DIRECTORY задан — извлекаем NNN-slug из имени.
  # Формат: specs/NNN-slug или просто NNN-slug.
  DIR_NAME="$(basename "$SPECIFY_FEATURE_DIRECTORY")"
  if [[ "$DIR_NAME" =~ ^([0-9]+)-(.+)$ ]]; then
    FEATURE_NUM="${BASH_REMATCH[1]}"
    BRANCH="${DIR_NAME}"
    # Убеждаемся, что тег seq/FEATURE_NUM существует (защита от race).
    if ! git ls-remote --tags origin "refs/tags/seq/${FEATURE_NUM}" 2>/dev/null \
         | grep -q "refs/tags/seq/${FEATURE_NUM}"; then
      echo "WARN: тег seq/${FEATURE_NUM} не найден в origin — резерв через tools/reserve-branch-number.sh" >&2
      git push origin "HEAD:refs/tags/seq/${FEATURE_NUM}" >/dev/null 2>&1 \
        || echo "WARN: не удалось запушить тег seq/${FEATURE_NUM} (уже существует?)" >&2
    fi
    if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
      git checkout -b "$BRANCH" master
    else
      git checkout "$BRANCH"
    fi
  else
    echo "ERROR: SPECIFY_FEATURE_DIRECTORY='$SPECIFY_FEATURE_DIRECTORY' не соответствует формату NNN-slug" >&2
    exit 2
  fi
else
  # Обычный путь: передаём slug в reserve-branch-number.sh, он сам
  # резервирует NNN и создаёт ветку NNN-slug.
  # Перехватываем вывод скрипта, чтобы извлечь NNN.
  RESERVE_OUT="$(./tools/reserve-branch-number.sh "$SLUG" 2>/dev/null || true)"
  # Последняя строка stdout скрипта — это NNN (см. reserve-branch-number.sh:65).
  FEATURE_NUM="$(echo "$RESERVE_OUT" | tail -n 1)"
  BRANCH="${FEATURE_NUM}-${SLUG}"
  # reserve-branch-number.sh сам делает git checkout -b, но проверим что мы
  # действительно на нужной ветке (защита от сбоя).
  CURRENT=$(git branch --show-current)
  if [ "$CURRENT" != "$BRANCH" ]; then
    echo "WARN: после reserve-branch-number.sh мы на '$CURRENT', ожидалось '$BRANCH'" >&2
    if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
      git checkout "$BRANCH"
    else
      git checkout -b "$BRANCH" master
    fi
  fi
fi

# JSON для downstream tools.
# Используем heredoc без подстановки, чтобы {} не воспринимались как фигурные
# скобки bash. jq, если есть, отформатирует красиво, иначе — однострочный JSON.
if command -v jq >/dev/null 2>&1; then
  jq -nc --arg bn "$BRANCH" --arg fn "${FEATURE_NUM:-}" \
    '{BRANCH_NAME: $bn, FEATURE_NUM: $fn}'
else
  printf '{"BRANCH_NAME":"%s","FEATURE_NUM":"%s"}\n' "$BRANCH" "${FEATURE_NUM:-}"
fi

echo "OK: создана и активна feature-ветка '$BRANCH' (NNN=${FEATURE_NUM:-?})" >&2
