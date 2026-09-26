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
# Baseline — по СОДЕРЖИМОМУ строки, НЕ по номеру строки (Pass 455).
#   Причина: file:line-база ломалась от любой вставки строк выше базовой.
#   Прецеденты Pass 451, 452, 454 — три раза за одну сессию приходилось
#   вручную перенумеровывать записи, и каждый раз это выглядело как
#   «новое нарушение R-11», хотя упоминания не добавлялись.
#   Теперь ключ — <путь>:sha256(trim(содержимого строки))[:16].
#
#   Следствия (намеренные):
#     - вставка/удаление строк ВЫШЕ упоминания базу больше не ломает;
#     - ИЗМЕНЕНИЕ содержимого строки с упоминанием базу ломает — это
#       правильно: правка такого текста = новое решение, его смотрит человек
#       (перефразировать без MP4 либо перегенерировать базу);
#     - смена отступа (trim) и перенос строки в другую часть файла — не ломают.
#
# Использование:
#   bash tools/check-no-mp4-mentions.sh                   # guard-режим
#   bash tools/check-no-mp4-mentions.sh --quiet           # для pre-commit
#   bash tools/check-no-mp4-mentions.sh --update-baseline # перегенерировать базу
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
#   - упоминания, чей ключ есть в tools/check-no-mp4-mentions.baseline
#
# Exit codes:
#   0 — нет запрещённых упоминаний (после baseline)
#   1 — найдены нарушения (после baseline)
#   2 — usage error
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE_FILE="${PROJECT_ROOT}/tools/check-no-mp4-mentions.baseline"

# Длина префикса sha256 в ключе. 16 hex = 64 бита: на ~150 записях
# вероятность коллизии ~6e-16, при этом запись остаётся читаемой.
HASH_LEN=16

# Сканируемые пути и расширения.
SEARCH_PATHS=("webvue3/src" "karaoke-public/src" "karaoke-app/src" "karaoke-web/src")
FILE_PATTERNS=("*.vue" "*.js" "*.ts" "*.kt")
SEARCH_PATTERN='(\bmp4\b|скачивани)'

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

# trim ведущих/хвостовых пробельных символов.
trim() {
  printf '%s' "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

# Ключ baseline для пары (путь, содержимое строки).
baseline_key() {
  local file="$1"
  local content="$2"
  local hash
  hash="$(trim "$content" | sha256sum | cut -c1-"$HASH_LEN")"
  printf '%s:%s' "$file" "$hash"
}

# Загружаем baseline в ассоциативный массив.
declare -A BASELINE_SET=()
BASELINE_COUNT=0

load_baseline() {
  BASELINE_SET=()
  BASELINE_COUNT=0
  [[ -f "$BASELINE_FILE" ]] || return 0
  local raw key
  while IFS= read -r raw; do
    # Ключ идёт до первого '#': после него — человекочитаемый сниппет-комментарий.
    key="$(trim "${raw%%#*}")"
    [[ -z "$key" ]] && continue
    BASELINE_SET["$key"]=1
    BASELINE_COUNT=$((BASELINE_COUNT + 1))
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

# Собираем сырые совпадения в RAW_HITS (формат grep: путь:строка:содержимое).
RAW_HITS=()

collect_hits() {
  RAW_HITS=()
  [[ ${#ALL_FILES[@]} -eq 0 ]] && return 0
  while IFS= read -r hit; do
    [[ -z "$hit" ]] && continue
    RAW_HITS+=("$hit")
  done < <(
    grep -nEi "$SEARCH_PATTERN" "${ALL_FILES[@]}" 2>/dev/null || true
  )
}

# ---------------------------------------------------------------------
# Режим перегенерации baseline.
# ---------------------------------------------------------------------
update_baseline() {
  cd "$PROJECT_ROOT" || return 2
  collect_files
  collect_hits

  local -A seen=()
  local out=()
  local hit file rest content key snippet
  for hit in "${RAW_HITS[@]:-}"; do
    [[ -z "$hit" ]] && continue
    file="${hit%%:*}"
    rest="${hit#*:}"
    content="${rest#*:}"
    key="$(baseline_key "$file" "$content")"
    [[ -n "${seen["$key"]:-}" ]] && continue
    seen["$key"]=1
    snippet="$(trim "$content")"
    [[ ${#snippet} -gt 120 ]] && snippet="${snippet:0:117}..."
    out+=("$key  # $snippet")
  done

  {
    echo "# Baseline для tools/check-no-mp4-mentions.sh (правило R-11)."
    echo "#"
    echo "# Формат: <путь>:<sha256(trim(содержимого строки))[:$HASH_LEN]>  # <сниппет>"
    echo "# Всё после '#' — комментарий-сниппет, на сопоставление не влияет."
    echo "#"
    echo "# Номер строки НЕ используется (Pass 455): раньше база была file:line и"
    echo "# ломалась от любой вставки строк выше — это выглядело как новое"
    echo "# нарушение R-11. Теперь вставка/удаление строк и смена отступа базу не"
    echo "# ломают; изменение самого текста упоминания — ломает (намеренно)."
    echo "#"
    echo "# Перегенерировать: bash tools/check-no-mp4-mentions.sh --update-baseline"
    echo ""
    printf '%s\n' "${out[@]:-}" | LC_ALL=C sort
  } > "$BASELINE_FILE"

  color_green "✅ Baseline перегенерирован: ${#seen[@]} записей → $BASELINE_FILE"
  return 0
}

# ---------------------------------------------------------------------
# Guard-режим.
# ---------------------------------------------------------------------
check_mp4_mentions() {
  cd "$PROJECT_ROOT" || return 2
  load_baseline
  collect_files
  collect_hits

  if [[ ${#ALL_FILES[@]} -eq 0 ]]; then
    color_yellow "⚠️  Нет файлов для сканирования"
    return 0
  fi

  if [[ ${#RAW_HITS[@]} -eq 0 ]]; then
    color_green "✅ OK: no MP4 mentions in production code"
    return 0
  fi

  local remaining=()
  local hit file rest lineno content key
  for hit in "${RAW_HITS[@]}"; do
    file="${hit%%:*}"
    rest="${hit#*:}"
    lineno="${rest%%:*}"
    content="${rest#*:}"
    key="$(baseline_key "$file" "$content")"
    [[ -z "${BASELINE_SET["$key"]:-}" ]] && remaining+=("$hit")
  done

  if [[ ${#remaining[@]} -eq 0 ]]; then
    color_green "✅ OK: all ${#RAW_HITS[@]} MP4 mentions are in baseline (grandfathered)"
    return 0
  fi

  color_red "❌ НАРУШЕНИЕ R-11: найдено ${#remaining[@]} упоминаний MP4/скачивания"
  color_yellow "   Правило (Pass 379, wayfinder #107): оферта «доступ только онлайн» запрещает"
  color_yellow "   упоминания MP4/скачивания в публичных компонентах и комментариях."
  color_yellow "   Baseline: ${BASELINE_COUNT} записей legacy-исключений (сопоставление по содержимому)."
  color_yellow ""
  for hit in "${remaining[@]}"; do
    file="${hit%%:*}"
    rest="${hit#*:}"
    content="${rest#*:}"
    [[ ${#content} -gt 200 ]] && content="${content:0:197}..."
    printf '  \033[0;31m%s\033[0m: \033[0;33m%s\033[0m\n' "${file}:${rest%%:*}" "$content"
  done
  color_yellow ""
  color_yellow "Как исправить:"
  color_yellow "  (a) Перефразировать без упоминания MP4/скачивания."
  color_yellow "  (b) Если legitimate — bash tools/check-no-mp4-mentions.sh --update-baseline"
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
  --update-baseline)
    update_baseline
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-no-mp4-mentions.sh [--quiet | --update-baseline]"
    exit 2
    ;;
esac
