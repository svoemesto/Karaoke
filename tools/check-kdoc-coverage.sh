#!/usr/bin/env bash
# tools/check-kdoc-coverage.sh
#
# Проверяет KDoc/JSDoc покрытие публичных top-level деклараций.
# Метрика — процент классов/объектов/интерфейсов с KDoc-блоком.
#
# FR-006 из specs/001-code-standards-docs/spec.md:
#   «Публичные API MUST сопровождаться KDoc/JSDoc-комментариями»
#
# Использование:
#   ./tools/check-kdoc-coverage.sh          # отчёт по всем модулям
#   ./tools/check-kdoc-coverage.sh --strict  # exit 1 если < 50%
#
# Возвращает 0 если покрытие >= 50% (или нестрогий режим), 1 если ниже.

set -uo pipefail

STRICT=false
if [ "${1:-}" = "--strict" ]; then
  STRICT=true
fi

# Модули для проверки
MODULES=(
  "karaoke-app:src/main/kotlin"
  "karaoke-web:src/main/kotlin"
)

# Регулярка для top-level деклараций (без ведущих пробелов)
DECL_PATTERN='^(class|object|interface|enum class|sealed class|data class)\s+[A-Z]'

total_all=0
kdoc_all=0
exit_code=0

for entry in "${MODULES[@]}"; do
  module="${entry%%:*}"
  src_dir="${entry##*:}"

  if [ ! -d "$module/$src_dir" ]; then
    continue
  fi

  total=0
  kdoc=0
  while IFS= read -r f; do
    [ -s "$f" ] || continue
    # Найти все top-level декларации (без ведущих пробелов)
    # и проверить KDoc в предыдущих 10 строках
    while IFS=: read -r line_num _; do
      total=$((total + 1))
      start=$((line_num - 10))
      [ $start -lt 1 ] && start=1
      end=$((line_num - 1))
      if sed -n "${start},${end}p" "$f" | grep -qE '^\s*/\*\*'; then
        kdoc=$((kdoc + 1))
      fi
    done < <(grep -nE "$DECL_PATTERN" "$f" 2>/dev/null)
  done < <(find "$module/$src_dir" -name "*.kt" 2>/dev/null)

  if [ "$total" -gt 0 ]; then
    pct=$(awk "BEGIN {printf \"%.1f\", $kdoc * 100 / $total}" | tr ',' '.')
  else
    pct="0.0"
  fi

  # Цвет: ≥50% green, ≥30% yellow, иначе red
  pct_int=${pct%%.*}
  if [ "$pct_int" -ge 50 ]; then
    color='\033[0;32m'
  elif [ "$pct_int" -ge 30 ]; then
    color='\033[1;33m'
  else
    color='\033[0;31m'
  fi
  printf "${color}%-20s\033[0m  %5s%%  (%3d/%3d)\n" "$module" "$pct" "$kdoc" "$total"

  total_all=$((total_all + total))
  kdoc_all=$((kdoc_all + kdoc))
done

echo ""
if [ "$total_all" -gt 0 ]; then
  pct_all=$(awk "BEGIN {printf \"%.1f\", $kdoc_all * 100 / $total_all}" | tr ',' '.')
else
  pct_all="0.0"
fi
pct_all_int=${pct_all%%.*}
if [ "$pct_all_int" -ge 50 ]; then
  color='\033[0;32m'
elif [ "$pct_all_int" -ge 30 ]; then
  color='\033[1;33m'
else
  color='\033[0;31m'
fi
printf "${color}%-20s\033[0m  %5s%%  (%3d/%3d)\n" "TOTAL" "$pct_all" "$kdoc_all" "$total_all"
echo ""
echo "Целевой KDoc-покрытие: ≥ 50% (FR-006, spec.md)"

if [ "$STRICT" = true ] && [ "$pct_all_int" -lt 50 ]; then
  exit_code=1
fi

exit $exit_code
