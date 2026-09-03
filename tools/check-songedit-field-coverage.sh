#!/usr/bin/env bash
# tools/check-songedit-field-coverage.sh
#
# Чек покрытия UI↔backend для пары SongEdit.vue ↔ /api/song/update.
# Спека: specs/302-fix-censored-name-loss
#
# Exit codes:
#   0 — все поля покрыты
#   1 — есть missing-поля
#   2 — внутренняя ошибка

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
SONGEDIT="${REPO_ROOT}/webvue3/src/components/Songs/edit/SongEdit.vue"
APICONTROLLER="${REPO_ROOT}/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt"
MAPPER="${REPO_ROOT}/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt"
WHITELIST="${REPO_ROOT}/tools/check-songedit-field-coverage.whitelist.yml"

# Temp files (cleanup через trap).
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
VMODEL_FILE="${TMP_DIR}/vmodel.txt"
SUPPORTED_FILE="${TMP_DIR}/supported.txt"
WHITELIST_FILE="${TMP_DIR}/whitelist.txt"
MISSING_FILE="${TMP_DIR}/missing.txt"
: > "${MISSING_FILE}"

if [ ! -f "${SONGEDIT}" ]; then
  echo "ERROR: ${SONGEDIT} не найден" >&2
  exit 2
fi
if [ ! -f "${APICONTROLLER}" ]; then
  echo "ERROR: ${APICONTROLLER} не найден" >&2
  exit 2
fi

# Извлечение v-model полей из SongEdit.vue.
grep -oE 'v-model="song\.[a-zA-Z]+"' "${SONGEDIT}" \
  | sed -E 's/v-model="song\.([a-zA-Z]+)"/\1/' \
  | sort -u > "${VMODEL_FILE}"

# Извлечение SUPPORTED полей из эндпоинта.
: > "${SUPPORTED_FILE}"

# Источник 1: SongUpdateMapper.fieldLookup (после FR-011).
if [ -f "${MAPPER}" ]; then
  grep -oE '"[a-zA-Z]+"\s+to\s+SongField\.' "${MAPPER}" \
    | sed -E 's/"([a-zA-Z]+)".*/\1/' >> "${SUPPORTED_FILE}"
fi

# Источник 2: прямые setter'ы в SongUpdateMapper (tags, rootFolder, и т.д.).
# Robust парсинг: находим "private val directSetters:" и собираем строки до ближайшей ")".
if [ -f "${MAPPER}" ]; then
  awk '
    /private val directSetters:/ { in_section=1; next }
    in_section && /^[[:space:]]*\)/ { in_section=0; next }
    in_section {
      if (match($0, /"[^"]+"/)) {
        key = substr($0, RSTART+1, RLENGTH-2)
        print key
      }
    }
  ' "${MAPPER}" >> "${SUPPORTED_FILE}"
fi

# Источник 2b: special-case ключи (fileName, albumId, songType — обрабатываются в Phase A).
if [ -f "${MAPPER}" ]; then
  awk '
    /private val specialCaseKeys:/ { in_section=1; next }
    in_section && /^[[:space:]]*\)/ { in_section=0; next }
    in_section {
      if (match($0, /"[^"]+"/)) {
        key = substr($0, RSTART+1, RLENGTH-2)
        print key
      }
    }
  ' "${MAPPER}" >> "${SUPPORTED_FILE}"
fi

# Источник 3 (fallback): @RequestParam в ApiController.songs2Update.
if [ ! -f "${MAPPER}" ] && [ -f "${APICONTROLLER}" ]; then
  awk '/fun songs2Update\(/,/^    \): SongUpdateResultDto \{/' "${APICONTROLLER}" \
    | grep -oE '@RequestParam[^)]+\)\s+[a-zA-Z]+:' \
    | sed -E 's/.*\s+([a-zA-Z]+):/\1/' >> "${SUPPORTED_FILE}"
fi

sort -u "${SUPPORTED_FILE}" -o "${SUPPORTED_FILE}"

# Чтение whitelist.
: > "${WHITELIST_FILE}"
if [ -f "${WHITELIST}" ]; then
  awk '
    /^whitelist:/ { in_section=1; next }
    in_section && /^[^ ]/ { exit }
    in_section && /^  "[^"]+":/ {
      match($0, /"[^"]+"/)
      key = substr($0, RSTART+1, RLENGTH-2)
      print key
    }
  ' "${WHITELIST}" > "${WHITELIST_FILE}"
  sort -u "${WHITELIST_FILE}" -o "${WHITELIST_FILE}"
fi

# Проверка покрытия.
TOTAL=$(wc -l < "${VMODEL_FILE}")
COVERED=0

while IFS= read -r key; do
  if grep -qx "${key}" "${SUPPORTED_FILE}" 2>/dev/null \
     || grep -qx "${key}" "${WHITELIST_FILE}" 2>/dev/null; then
    COVERED=$((COVERED + 1))
  else
    echo "${key}" >> "${MISSING_FILE}"
  fi
done < "${VMODEL_FILE}"

MISSING_COUNT=$(wc -l < "${MISSING_FILE}")
WHITELIST_COUNT=$(wc -l < "${WHITELIST_FILE}")

if [ "${MISSING_COUNT}" -eq 0 ]; then
  echo "OK: ${COVERED}/${TOTAL} полей покрыты (whitelist: ${WHITELIST_COUNT})"
  exit 0
else
  echo "FAIL: ${COVERED}/${TOTAL} полей покрыты, MISSING (${MISSING_COUNT}):" >&2
  while IFS= read -r key; do
    echo "  MISSING: ${key}" >&2
  done < "${MISSING_FILE}"
  exit 1
fi
