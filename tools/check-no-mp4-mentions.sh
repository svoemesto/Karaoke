#!/usr/bin/env bash
# =====================================================================
# tools/check-no-mp4-mentions.sh — guard для правила R-11
# (MP4/скачивание запрещено упоминать в production коде по оферте
# «доступ только онлайн»).
#
# Прецедент (Pass 379, wayfinder #107): правило R-11 существовало
# в `docs/strategy/growth.md` и `AGENTS.md § Стратегия проекта» (п.10
# TOP-10 ловушек: «НЕ упоминать MP4/скачивание в рекламных материалах
# и комментариях к коду»), но было **не machine-readable**.
#
# Использование:
#   bash tools/check-no-mp4-mentions.sh                  # guard-режим
#   bash tools/check-no-mp4-mentions.sh --quiet          # для pre-commit
#
# Что проверяет:
#   - webvue3/src/**/*.{vue,js,ts}
#   - karaoke-public/src/**/*.{vue,js,ts}
#   - karaoke-app/src/**/*.kt
#   - karaoke-web/src/**/*.kt
#
# Исключения:
#   - tools/check-no-mp4-mentions.{sh,baseline} (сам guard)
#   - *.test.*, *.spec.* (тесты — можно упомянуть)
#   - **/build/**, **/node_modules/**, **/dist/**, .git/**
#   - **/bootstrap*.min.{js,css} (third-party)
#   - файлы из baseline (если задан tools/check-no-mp4-mentions.baseline)
#
# Exit codes:
#   0 — нет запрещённых упоминаний (после baseline)
#   1 — найдены нарушения (после baseline)
#   2 — usage error
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE_FILE="${PROJECT_ROOT}/tools/check-no-mp4-mentions.baseline"

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

# Сканируемые пути и расширения.
SEARCH_PATHS=("webvue3/src" "karaoke-public/src" "karaoke-app/src" "karaoke-web/src")
FILE_PATTERNS=("*.vue" "*.js" "*.ts" "*.kt")
SEARCH_PATTERN='(\bmp4\b|скачивани)'

# Загружаем baseline в массив file:line.
load_baseline() {
  BASELINE_KEYS=()
  [[ -f "$BASELINE_FILE" ]] || return 0
  while IFS= read -r key; do
    [[ -z "$key" || "$key" =~ ^# ]] && continue
    BASELINE_KEYS+=("$key")
  done < "$BASELINE_FILE"
}

# Собираем файлы через find (с include-патернами и excludes).
collect_files() {
  local includes=()
  for fp in "${FILE_PATTERNS[@]}"; do
    includes+=( -name "$fp" -o )
  done
  unset 'includes[${#includes[@]}-1]'

  ALL_FILES=()
  for sp in "${SEARCH_PATHS[@]}"; do
    [[ -d "$PROJECT_ROOT/$sp" ]] || continue
    while IFS= read -r f; do
      ALL_FILES+=("$f")
    done < <(
      cd "$PROJECT_ROOT" && find "$sp" \( "${includes[@]}" \) -type f \
        ! \( -path "*/node_modules/*" -o -path "*/build/*" -o -path "*/dist/*" \
            -o -path "*/.git/*" -o -path "*/bootstrap*.min.js" \
            -o -path "*/bootstrap*.min.css" \
            -o -path "*/check-no-mp4-mentions.sh" \
            -o -path "*/check-no-mp4-mentions.baseline" \
            -o -name "*.test.js" -o -name "*.test.ts" \
            -o -name "*.spec.js" -o -name "*.spec.ts" \
            -o -name "*.test.kt" -o -name "*.spec.kt" \) 2>/dev/null
    )
  done
}

check_mp4_mentions() {
  cd "$PROJECT_ROOT" || return 2
  load_baseline
  collect_files

  if [[ ${#ALL_FILES[@]} -eq 0 ]]; then
    color_yellow "⚠️  Нет файлов для сканирования"
    return 0
  fi

  # grep -nEi: extended regex, номера строк, case-insensitive.
  local raw_hits=()
  while IFS= read -r hit; do
    [[ -z "$hit" ]] && continue
    raw_hits+=("$hit")
  done < <(
    grep -nEi "$SEARCH_PATTERN" "${ALL_FILES[@]}" 2>/dev/null || true
  )

  if [[ ${#raw_hits[@]} -eq 0 ]]; then
    color_green "✅ OK: no MP4 mentions in production code"
    return 0
  fi

  # Применяем baseline: пропускаем file:line из BASELINE_KEYS.
  local remaining=()
  for hit in "${raw_hits[@]}"; do
    local file="${hit%%:*}"
    local rest="${hit#*:}"
    local lineno="${rest%%:*}"
    local key="${file}:${lineno}"
    local in_baseline=false
    for bk in "${BASELINE_KEYS[@]:-}"; do
      if [[ "$bk" == "$key" ]]; then
        in_baseline=true
        break
      fi
    done
    [[ "$in_baseline" == false ]] && remaining+=("$hit")
  done

  if [[ ${#remaining[@]} -eq 0 ]]; then
    color_green "✅ OK: all ${#raw_hits[@]} MP4 mentions are in baseline (grandfathered)"
    return 0
  fi

  color_red "❌ НАРУШЕНИЕ R-11: найдено ${#remaining[@]} упоминаний MP4/скачивания"
  color_yellow "   Правило (Pass 379, wayfinder #107): оферта «доступ только онлайн» запрещает"
  color_yellow "   упоминания MP4/скачивания в публичных компонентах и комментариях."
  color_yellow "   Baseline: ${#BASELINE_KEYS[@]} строк legacy-исключений."
  color_yellow ""
  for hit in "${remaining[@]}"; do
    local file="${hit%%:*}"
    local rest="${hit#*:}"
    local content="${rest#*:}"
    [[ ${#content} -gt 200 ]] && content="${content:0:197}..."
    printf '  \033[0;31m%s\033[0m: \033[0;33m%s\033[0m\n' "${file}:${rest%%:*}" "$content"
  done
  color_yellow ""
  color_yellow "Как исправить:"
  color_yellow "  (a) Перефразировать без упоминания MP4/скачивания."
  color_yellow "  (b) Если legitimate — добавить file:line в $BASELINE_FILE."
  return 1
}

case "${1:-}" in
  --help|-h|"")
    check_mp4_mentions
    exit $?
    ;;
  --quiet)
    check_mp4_mentions >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-no-mp4-mentions.sh [--quiet]"
    exit 2
    ;;
esac
