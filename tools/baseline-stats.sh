#!/usr/bin/env bash
# tools/baseline-stats.sh
#
# Считает количество нарушений в baseline-файлах ktlint и ESLint.
# Печатает таблицу с текущим количеством и (если возможно) diff с прошлым релизом.
#
# Использование:
#   ./tools/baseline-stats.sh
#
# Требования: jq (для подсчёта ESLint baseline).
# Установка: apt install jq  /  brew install jq
#
# Структура baseline-файлов:
#   config/ktlint/baseline-<module>.xml   (ktlint — per Gradle module)
#   webvue3/.eslint-baseline.json         (ESLint — per SPA)
#   karaoke-public/.eslint-baseline.json  (ESLint — per SPA)

set -uo pipefail

KTLINT_DIR="${KTLINT_DIR:-config/ktlint}"
DETEKT_DIR="${DETEKT_DIR:-config/detekt}"
WEBVUE3_BASELINE="${WEBVUE3_BASELINE:-webvue3/.eslint-baseline.json}"
PUBLIC_BASELINE="${PUBLIC_BASELINE:-karaoke-public/.eslint-baseline.json}"

count_ktlint_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "0"
    return
  fi
  # grep -c возвращает "<count>" + newline; убираем newline.
  # При 0 совпадений grep возвращает exit-code 1, поэтому fallback через || n=0.
  local n
  n=$(grep -cE '<error ' "$file" 2>/dev/null) || n=0
  echo "$n"
}

count_detekt_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "0"
    return
  fi
  local n
  n=$(grep -cE '<ID>' "$file" 2>/dev/null) || n=0
  echo "$n"
}

count_eslint() {
  local file="$1"
  if [ ! -f "$file" ]; then
    echo "0"
    return
  fi
  if ! command -v jq >/dev/null 2>&1; then
    echo "0"
    return
  fi
  local n
  n=$(jq 'length' "$file" 2>/dev/null) || n=0
  echo "$n"
}

printf "%-40s %15s\n" "Baseline" "Count"
printf "%-40s %15s\n" "----------------------------------------" "---------------"

# ktlint — per-module baselines
ktlint_total=0
if [ -d "$KTLINT_DIR" ]; then
  for f in "$KTLINT_DIR"/baseline-*.xml; do
    [ -f "$f" ] || continue
    module=$(basename "$f" .xml | sed 's/^baseline-//')
    count=$(count_ktlint_file "$f")
    printf "%-40s %15s\n" "ktlint  $module" "$count"
    ktlint_total=$((ktlint_total + ${count:-0}))
  done
else
  printf "%-40s %15s\n" "ktlint  (no dir)" "N/A"
fi

# detekt — per-module baselines (пока отключён)
detekt_total=0
if [ -d "$DETEKT_DIR" ]; then
  for f in "$DETEKT_DIR"/baseline-*.xml; do
    [ -f "$f" ] || continue
    module=$(basename "$f" .xml | sed 's/^baseline-//')
    count=$(count_detekt_file "$f")
    printf "%-40s %15s\n" "detekt  $module" "$count"
    detekt_total=$((detekt_total + ${count:-0}))
  done
else
  printf "%-40s %15s\n" "detekt  (no dir)" "N/A"
fi

# ESLint — per-SPA baselines
webvue3_count=$(count_eslint "$WEBVUE3_BASELINE")
public_count=$(count_eslint "$PUBLIC_BASELINE")
printf "%-40s %15s\n" "eslint  webvue3" "$webvue3_count"
printf "%-40s %15s\n" "eslint  karaoke-public" "$public_count"

# Сумма
total=$(( ktlint_total + detekt_total + ${webvue3_count:-0} + ${public_count:-0} ))
printf "%-40s %15s\n" "----------------------------------------" "---------------"
printf "%-40s %15s\n" "TOTAL" "$total"

echo ""
echo "Целевой темп сокращения: ≥10%/мес (SC-002 spec.md)"
echo "Текущий baseline фиксирует $total известных нарушений."
