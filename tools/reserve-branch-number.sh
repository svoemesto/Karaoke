#!/usr/bin/env bash
# tools/reserve-branch-number.sh
#
# Атомарно резервирует следующий свободный порядковый номер для feature-ветки
# (конвенция NNN-slug, см. AGENTS.md, раздел "Git").
#
# Зачем. Несколько агентов на разных машинах могут одновременно посчитать
# "следующий номер" одинаково — конфликт возникает не на git-уровне (имена
# веток разные, push не падает), а по смыслу конвенции (два разных PR с
# одним и тем же NNN). Резервация — push уникального lightweight-тега
# refs/tags/seq/NNN в origin: git отклоняет push, если тег с таким именем
# уже существует, что даёт бесплатный distributed compare-and-swap без
# отдельной инфраструктуры (сервера/БД/лок-файла).
#
# Использование:
#   ./tools/reserve-branch-number.sh                 # зарезервировать, напечатать номер в stdout
#   ./tools/reserve-branch-number.sh my-feature-slug  # зарезервировать + git checkout -b NNN-my-feature-slug

set -euo pipefail

SLUG="${1:-}"
MAX_ATTEMPTS=30

git fetch origin --quiet --tags

# Максимальный уже использованный номер: локальные ветки, ветки на origin,
# теги-резервации на origin (теги переживают удаление смерженной ветки —
# самый надёжный источник истории после внедрения этой конвенции; ветки —
# бэкфилл истории до её внедрения).
max_num() {
  # `|| true`: под set -e -o pipefail пустой grep (ни одной NNN-ветки/тега —
  # валидный случай на «пустом» репозитории) иначе уронит весь скрипт.
  {
    git for-each-ref --format='%(refname:short)' refs/heads/ refs/remotes/origin/ 2>/dev/null
    git ls-remote --tags origin 'refs/tags/seq/*' 2>/dev/null | sed -E 's#.*refs/tags/seq/##'
  } | grep -oE '^[0-9]+' | sort -n | tail -1 || true
}

max=$(max_num)
: "${max:=0}"
# 10#-префикс: числа с ведущим нулём (029) иначе трактуются bash как
# восьмеричные и валят арифметику на невалидных для octal цифрах (8/9).
next=$((10#$max + 1))

reserved=""
for _ in $(seq 1 "$MAX_ATTEMPTS"); do
  padded=$(printf '%03d' "$next")
  if git push origin "HEAD:refs/tags/seq/${padded}" >/dev/null 2>&1; then
    reserved="$padded"
    break
  fi
  next=$((next + 1))
done

if [ -z "$reserved" ]; then
  echo "ERROR: не удалось зарезервировать номер за $MAX_ATTEMPTS попыток" >&2
  exit 1
fi

echo "Зарезервирован номер: $reserved (тег seq/$reserved запушен в origin)" >&2
echo "$reserved"

if [ -n "$SLUG" ]; then
  branch="${reserved}-${SLUG}"
  git checkout -b "$branch"
  echo "Создана и переключена ветка: $branch" >&2
fi
