#!/usr/bin/env bash
# tools/check-endpoint-field-coverage.sh
#
# Общий чек покрытия UI↔backend для всех пар из tools/endpoint-pairs.yml.
# Спека: specs/302-fix-censored-name-loss (FR-007/008).
#
# Exit codes:
#   0 — все пары зелёные
#   1 — хотя бы одна пара имеет missing-поля
#   2 — внутренняя ошибка (yml invalid, файл не найден, etc)

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ENDPOINT_PAIRS="${REPO_ROOT}/tools/endpoint-pairs.yml"
GLOBAL_WHITELIST="${REPO_ROOT}/tools/check-endpoint-field-coverage.whitelist.yml"

if [ ! -f "${ENDPOINT_PAIRS}" ]; then
  echo "ERROR: ${ENDPOINT_PAIRS} не найден" >&2
  exit 2
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT
PAIRS_FILE="${TMP_DIR}/pairs.txt"
: > "${PAIRS_FILE}"

# Парсим endpoint-pairs.yml через grep + awk.
# Извлекаем строки вида "  - component:", "    endpoint:", и т.д.
grep -E "^\s*(- )?(component|endpoint|method|controller|controller_method):" "${ENDPOINT_PAIRS}" \
  | sed -E 's/^\s*-?\s*([a-z_]+):\s*(.*)$/\1=\2/' \
  > "${TMP_DIR}/raw_pairs.txt"

awk -F'=' '
  $1 == "component" { comp=$2; epi=""; mth=""; ctrl=""; meth=""; next }
  $1 == "endpoint" { epi=$2; next }
  $1 == "method" { mth=$2; next }
  $1 == "controller" { ctrl=$2; next }
  $1 == "controller_method" { meth=$2; if (comp != "" && epi != "") print comp "|" epi "|" mth "|" ctrl "|" meth; comp=""; next }
' "${TMP_DIR}/raw_pairs.txt" > "${PAIRS_FILE}"

PAIR_COUNT=$(wc -l < "${PAIRS_FILE}")
if [ "${PAIR_COUNT}" -eq 0 ]; then
  echo "INFO: нет пар для проверки в ${ENDPOINT_PAIRS}" >&2
  exit 0
fi

# Глобальный whitelist.
GLOBAL_WL_FILE="${TMP_DIR}/global_wl.txt"
: > "${GLOBAL_WL_FILE}"
if [ -f "${GLOBAL_WHITELIST}" ]; then
  awk '
    /^whitelist:/ { in_section=1; next }
    in_section && /^[^ ]/ { exit }
    in_section && /^  "[^"]+"/ {
      match($0, /"[^"]+"/)
      key = substr($0, RSTART+1, RLENGTH-2)
      # Разделяем "/" и берём последний сегмент (= field name).
      n = split(key, parts, "/")
      print parts[n]
    }
  ' "${GLOBAL_WHITELIST}" > "${GLOBAL_WL_FILE}"
fi

TOTAL_PAIRS=0
PASSED_PAIRS=0
FAILED_PAIRS=0

while IFS='|' read -r component endpoint method controller controller_method; do
  TOTAL_PAIRS=$((TOTAL_PAIRS + 1))
  COMPONENT_PATH="${REPO_ROOT}/${component}"
  CONTROLLER_PATH="${REPO_ROOT}/${controller}"

  if [ ! -f "${COMPONENT_PATH}" ]; then
    echo "[WARN] component не найден: ${COMPONENT_PATH} (пропускаем)" >&2
    continue
  fi
  if [ ! -f "${CONTROLLER_PATH}" ]; then
    echo "[WARN] controller не найден: ${CONTROLLER_PATH} (пропускаем)" >&2
    continue
  fi

  VMODEL_FILE="${TMP_DIR}/vmodel_${TOTAL_PAIRS}.txt"
  : > "${VMODEL_FILE}"

  # Поддерживаем разные модели: song.*, album.*, author.*, user.*, picture.*, sub.*, dict.*
  for model in song album author user picture sub dict; do
    grep -oE "v-model=\"${model}\\.[a-zA-Z]+\"" "${COMPONENT_PATH}" 2>/dev/null \
      | sed -E "s/v-model=\"${model}\\.([a-zA-Z]+)\"/\\1/" >> "${VMODEL_FILE}"
  done
  sort -u "${VMODEL_FILE}" -o "${VMODEL_FILE}"

  SUPPORTED_FILE="${TMP_DIR}/supported_${TOTAL_PAIRS}.txt"
  : > "${SUPPORTED_FILE}"

  MAPPER="${REPO_ROOT}/karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt"
  if [ -f "${MAPPER}" ]; then
    grep -oE '"[a-zA-Z]+"\s+to\s+SongField\.' "${MAPPER}" \
      | sed -E 's/"([a-zA-Z]+)".*/\1/' >> "${SUPPORTED_FILE}"
    # Robust парсинг directSetters и specialCaseKeys (после ktlintFormat многострочные).
    awk '
      /private val directSetters:/ { in_section=1; next }
      in_section && /^[[:space:]]*\)/ { in_section=0; next }
      in_section {
        if (match($0, /"[^"]+"/)) print substr($0, RSTART+1, RLENGTH-2)
      }
    ' "${MAPPER}" >> "${SUPPORTED_FILE}"
    awk '
      /private val specialCaseKeys:/ { in_section=1; next }
      in_section && /^[[:space:]]*\)/ { in_section=0; next }
      in_section {
        if (match($0, /"[^"]+"/)) print substr($0, RSTART+1, RLENGTH-2)
      }
    ' "${MAPPER}" >> "${SUPPORTED_FILE}"
  fi

  # Fallback: @RequestParam.
  if [ ! -f "${MAPPER}" ]; then
    awk "/fun ${controller_method}\\(/,/^    \\):.*\\{? *\$/" "${CONTROLLER_PATH}" \
      | grep -oE '@RequestParam[^)]+\)\s+[a-zA-Z]+:' \
      | sed -E 's/.*\s+([a-zA-Z]+):/\1/' >> "${SUPPORTED_FILE}"
  fi
  sort -u "${SUPPORTED_FILE}" -o "${SUPPORTED_FILE}"

  # Per-pair whitelist (формат "Comp/endpoint/field: reason", извлекаем field).
  LOCAL_WL_FILE="${TMP_DIR}/local_wl_${TOTAL_PAIRS}.txt"
  : > "${LOCAL_WL_FILE}"
  if [ -f "${GLOBAL_WHITELIST}" ]; then
    awk -v comp="${component}" '
      $0 ~ "\"" comp "\"" { in_pair=1 }
      in_pair && /^[^ ]/ && !/^  / { if (count > 0) exit; else { in_pair=0; next } }
      in_pair && /^  "[^"]+"/ {
        match($0, /"[^"]+"/)
        key = substr($0, RSTART+1, RLENGTH-2)
        n = split(key, parts, "/")
        print parts[n]
        count++
      }
    ' "${GLOBAL_WHITELIST}" > "${LOCAL_WL_FILE}"
  fi

  TOTAL_FIELDS=$(wc -l < "${VMODEL_FILE}")
  COVERED=0
  MISSING_TMP="${TMP_DIR}/missing_${TOTAL_PAIRS}.txt"
  : > "${MISSING_TMP}"

  while IFS= read -r key; do
    if grep -qx "${key}" "${SUPPORTED_FILE}" 2>/dev/null \
       || grep -qx "${key}" "${LOCAL_WL_FILE}" 2>/dev/null \
       || grep -qx "${key}" "${GLOBAL_WL_FILE}" 2>/dev/null; then
      COVERED=$((COVERED + 1))
    else
      echo "${key}" >> "${MISSING_TMP}"
    fi
  done < "${VMODEL_FILE}"

  MISSING_COUNT=$(wc -l < "${MISSING_TMP}")

  if [ "${MISSING_COUNT}" -eq 0 ]; then
    echo "[PASS] ${component} ↔ ${endpoint} (${COVERED}/${TOTAL_FIELDS})"
    PASSED_PAIRS=$((PASSED_PAIRS + 1))
  else
    echo "[FAIL] ${component} ↔ ${endpoint} (${COVERED}/${TOTAL_FIELDS}, MISSING=${MISSING_COUNT}):" >&2
    while IFS= read -r key; do
      echo "  MISSING: ${key}" >&2
    done < "${MISSING_TMP}"
    FAILED_PAIRS=$((FAILED_PAIRS + 1))
  fi
done < "${PAIRS_FILE}"

echo ""
echo "=== ИТОГО: ${PASSED_PAIRS}/${TOTAL_PAIRS} пар PASS ==="
if [ "${PAIR_COUNT}" -eq 1 ]; then
  echo "INFO: только одна пара покрыта (MVP scope)"
fi

if [ "${FAILED_PAIRS}" -eq 0 ]; then
  exit 0
else
  exit 1
fi
