#!/usr/bin/env bash
# tools/check-endpoint-field-coverage.sh
#
# Общий чек покрытия UI↔backend для всех пар из tools/endpoint-pairs.yml.
# Спека: specs/302-fix-censored-name-loss (FR-007/008).
#
# Использование:
#   bash tools/check-endpoint-field-coverage.sh            # guard-режим
#   bash tools/check-endpoint-field-coverage.sh --self-test # фикстуры «до/после»
#
# Exit codes:
#   0 — все пары зелёные
#   1 — хотя бы одна пара имеет missing-поля ИЛИ пару не удалось проверить
#   2 — внутренняя ошибка (yml invalid, файл не найден, etc)
#
# BR-573 (аудит #336, пакет PKG-S3-30): «нет данных» ≠ «нет нарушений».
# Раньше отсутствующий component/controller (или компонент без v-model полей)
# приводил к `continue` — пара не попадала ни в PASS, ни в FAIL, а guard
# завершался с кодом 0. Теперь такая пара считается НЕПРОВЕРЕННОЙ: печатается
# ошибка, счётчик расходится и guard возвращает ненулевой код.
#
# BR-577 (аудит #336, пакет PKG-S3-30): список поддерживаемых полей брался из
# одного жёстко зашитого SongUpdateMapper.kt ДЛЯ ЛЮБОЙ пары, а fallback на
# @RequestParam был недостижим (файл маппера всегда существует). Теперь
# источник полей — контроллер пары (controller/controller_method из
# endpoint-pairs.yml); маппер подключается только к тем парам, чей метод
# контроллера на него ссылается.

set -uo pipefail

REPO_ROOT="${KARAOKE_COVERAGE_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"

# --- Цвета ---
if [ -t 1 ]; then
  C_RED=$'\033[0;31m'; C_GREEN=$'\033[0;32m'; C_YELLOW=$'\033[0;33m'; C_RESET=$'\033[0m'
else
  C_RED=""; C_GREEN=""; C_YELLOW=""; C_RESET=""
fi

# =====================================================================
# check_pairs <repo-root> — основная проверка. Возвращает код возврата.
# =====================================================================
check_pairs() {
  local root="$1"
  local endpoint_pairs="${root}/tools/endpoint-pairs.yml"
  local global_whitelist="${root}/tools/check-endpoint-field-coverage.whitelist.yml"
  local kotlin_src_root="${root}/karaoke-app/src/main/kotlin"

  if [ ! -f "${endpoint_pairs}" ]; then
    echo "ERROR: ${endpoint_pairs} не найден" >&2
    return 2
  fi

  local tmp_dir pairs_file global_wl_file
  tmp_dir="$(mktemp -d)"
  pairs_file="${tmp_dir}/pairs.txt"
  : > "${pairs_file}"

  # Парсим endpoint-pairs.yml через grep + awk.
  # Извлекаем строки вида "  - component:", "    endpoint:", и т.д.
  grep -E "^\s*(- )?(component|endpoint|method|controller|controller_method):" "${endpoint_pairs}" \
    | sed -E 's/^\s*-?\s*([a-z_]+):\s*(.*)$/\1=\2/' \
    > "${tmp_dir}/raw_pairs.txt"

  awk -F'=' '
    $1 == "component" { comp=$2; epi=""; mth=""; ctrl=""; meth=""; next }
    $1 == "endpoint" { epi=$2; next }
    $1 == "method" { mth=$2; next }
    $1 == "controller" { ctrl=$2; next }
    $1 == "controller_method" { meth=$2; if (comp != "" && epi != "") print comp "|" epi "|" mth "|" ctrl "|" meth; comp=""; next }
  ' "${tmp_dir}/raw_pairs.txt" > "${pairs_file}"

  local pair_count
  pair_count=$(wc -l < "${pairs_file}")
  if [ "${pair_count}" -eq 0 ]; then
    # BR-573: пустой набор пар — это «нет данных», а не «нарушений нет».
    echo "ERROR: нет пар для проверки в ${endpoint_pairs} — проверка не могла быть выполнена" >&2
    rm -rf "${tmp_dir}"
    return 2
  fi

  # Глобальный whitelist.
  global_wl_file="${tmp_dir}/global_wl.txt"
  : > "${global_wl_file}"
  if [ -f "${global_whitelist}" ]; then
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
    ' "${global_whitelist}" > "${global_wl_file}"
  fi

  local total_pairs=0 passed_pairs=0 failed_pairs=0 uncheckable_pairs=0
  local component endpoint method controller controller_method
  local component_path controller_path vmodel_file supported_file method_body
  local total_fields covered missing_tmp missing_count
  local local_wl_file component_base component_stem component_slug pair_wl
  local mapper_name mapper_file

  while IFS='|' read -r component endpoint method controller controller_method; do
    total_pairs=$((total_pairs + 1))
    component_path="${root}/${component}"
    controller_path="${root}/${controller}"

    if [ ! -f "${component_path}" ]; then
      # BR-573: «нет данных» — пара не проверена, а не «нарушений нет».
      echo "[ERROR] component не найден: ${component_path} — пару проверить невозможно" >&2
      uncheckable_pairs=$((uncheckable_pairs + 1))
      continue
    fi
    if [ ! -f "${controller_path}" ]; then
      echo "[ERROR] controller не найден: ${controller_path} — пару проверить невозможно" >&2
      uncheckable_pairs=$((uncheckable_pairs + 1))
      continue
    fi

    vmodel_file="${tmp_dir}/vmodel_${total_pairs}.txt"
    : > "${vmodel_file}"

    # Поддерживаем разные модели: song.*, album.*, author.*, user.*, picture.*, sub.*, dict.*
    local model
    for model in song album author user picture sub dict; do
      grep -oE "v-model=\"${model}\\.[a-zA-Z]+\"" "${component_path}" 2>/dev/null \
        | sed -E "s/v-model=\"${model}\\.([a-zA-Z]+)\"/\\1/" >> "${vmodel_file}"
    done
    sort -u "${vmodel_file}" -o "${vmodel_file}"

    total_fields=$(wc -l < "${vmodel_file}")
    if [ "${total_fields}" -eq 0 ]; then
      # BR-573: компонент без v-model полей — проверять нечего, это «нет данных».
      echo "[ERROR] component ${component} не содержит v-model полей — пару проверить невозможно" >&2
      uncheckable_pairs=$((uncheckable_pairs + 1))
      continue
    fi

    supported_file="${tmp_dir}/supported_${total_pairs}.txt"
    : > "${supported_file}"

    # BR-577: источник полей — тело метода контроллера ПАРЫ.
    method_body="${tmp_dir}/method_${total_pairs}.txt"
    extract_method_body "${controller_path}" "${controller_method}" > "${method_body}"

    if [ ! -s "${method_body}" ]; then
      echo "[ERROR] метод ${controller_method} не найден в ${controller} — пару проверить невозможно" >&2
      uncheckable_pairs=$((uncheckable_pairs + 1))
      continue
    fi

    # (1) Мапперы, на которые ССЫЛАЕТСЯ метод контроллера пары.
    #     Только они и дают список поддерживаемых полей.
    grep -oE '\b[A-Z][A-Za-z0-9]*Mapper\b' "${method_body}" | sort -u > "${tmp_dir}/mappers_${total_pairs}.txt"
    while IFS= read -r mapper_name; do
      [ -n "${mapper_name}" ] || continue
      mapper_file="$(find "${kotlin_src_root}" -name "${mapper_name}.kt" -print -quit 2>/dev/null || true)"
      if [ -z "${mapper_file}" ]; then
        echo "[WARN] маппер ${mapper_name} упомянут в ${controller_method}, но файл не найден" >&2
        continue
      fi
      extract_mapper_fields "${mapper_file}" >> "${supported_file}"
    done < "${tmp_dir}/mappers_${total_pairs}.txt"

    # (2) @RequestParam-параметры самого метода контроллера.
    grep -oE '@RequestParam[^)]+\)\s+[a-zA-Z]+:' "${method_body}" \
      | sed -E 's/.*\s+([a-zA-Z]+):/\1/' >> "${supported_file}"

    # (3) Явные обращения к карте параметров вида all["name"].
    grep -oE 'all\["[a-zA-Z]+"\]' "${method_body}" \
      | sed -E 's/all\["([a-zA-Z]+)"\]/\1/' >> "${supported_file}"

    sort -u "${supported_file}" -o "${supported_file}"

    # Per-pair whitelist (BR-577): отдельный файл на пару —
    # tools/check-<component-slug>-field-coverage.whitelist.yml
    # (для SongEdit.vue это check-songedit-field-coverage.whitelist.yml).
    local_wl_file="${tmp_dir}/local_wl_${total_pairs}.txt"
    : > "${local_wl_file}"
    component_base="$(basename "${component}")"
    component_stem="${component_base##*.}"
    component_stem="${component_stem%%.*}"
    component_slug="$(printf '%s' "${component_stem}" | tr '[:upper:]' '[:lower:]')"
    pair_wl="${root}/tools/check-${component_slug}-field-coverage.whitelist.yml"
    if [ -f "${pair_wl}" ]; then
      awk '
        /^whitelist:/ { in_section=1; next }
        in_section && /^[^ ]/ { exit }
        in_section && /^  "[^"]+"/ {
          match($0, /"[^"]+"/)
          print substr($0, RSTART+1, RLENGTH-2)
        }
      ' "${pair_wl}" > "${local_wl_file}"
    fi
    # Глобальный whitelist — по имени компонента (последний сегмент пути).
    if [ -f "${global_whitelist}" ]; then
      awk -v comp="${component_base}" '
        index($0, "\"" comp "/") > 0 { in_pair=1; next }
        in_pair && /^[^ ]/ { in_pair=0; next }
        in_pair && /^  "[^"]+"/ {
          match($0, /"[^"]+"/)
          key = substr($0, RSTART+1, RLENGTH-2)
          n = split(key, parts, "/")
          print parts[n]
        }
      ' "${global_whitelist}" >> "${local_wl_file}"
    fi
    sort -u "${local_wl_file}" -o "${local_wl_file}"

    covered=0
    missing_tmp="${tmp_dir}/missing_${total_pairs}.txt"
    : > "${missing_tmp}"

    local key
    while IFS= read -r key; do
      if grep -qx "${key}" "${supported_file}" 2>/dev/null \
         || grep -qx "${key}" "${local_wl_file}" 2>/dev/null \
         || grep -qx "${key}" "${global_wl_file}" 2>/dev/null; then
        covered=$((covered + 1))
      else
        echo "${key}" >> "${missing_tmp}"
      fi
    done < "${vmodel_file}"

    missing_count=$(wc -l < "${missing_tmp}")

    if [ "${missing_count}" -eq 0 ]; then
      echo "[PASS] ${component} ↔ ${endpoint} (${covered}/${total_fields})"
      passed_pairs=$((passed_pairs + 1))
    else
      echo "[FAIL] ${component} ↔ ${endpoint} (${covered}/${total_fields}, MISSING=${missing_count}):" >&2
      while IFS= read -r key; do
        echo "  MISSING: ${key}" >&2
      done < "${missing_tmp}"
      failed_pairs=$((failed_pairs + 1))
    fi
  done < "${pairs_file}"

  local checked_pairs=$((total_pairs - uncheckable_pairs))

  echo ""
  echo "=== ИТОГО: ${passed_pairs}/${checked_pairs} пар PASS (из ${total_pairs} в endpoint-pairs.yml) ==="
  if [ "${pair_count}" -eq 1 ]; then
    echo "INFO: только одна пара покрыта (MVP scope)"
  fi

  rm -rf "${tmp_dir}"

  if [ "${uncheckable_pairs}" -gt 0 ]; then
    # BR-573: «нет данных» ≠ «нет нарушений» — guard обязан падать.
    echo "FAIL: ${uncheckable_pairs} пар невозможно проверить (нет component/controller/method или нет v-model полей)." >&2
    echo "      Пустой набор данных — не «нарушений нет»: проверка должна быть выполнена." >&2
    return 1
  fi

  if [ "${failed_pairs}" -eq 0 ]; then
    return 0
  fi
  return 1
}

# =====================================================================
# Вспомогательные функции разбора
# =====================================================================

# Тело метода контроллера пары: скобочный баланс от строки
# `fun <controller_method>(` до закрывающей скобки метода.
extract_method_body() {
  local controller_file="$1"
  local method="$2"
  awk -v m="${method}" '
    !started && index($0, "fun " m "(") > 0 { started=1; depth=0; opened=0 }
    started {
      print
      tmp = $0
      o = gsub(/\{/, "", tmp)
      c = gsub(/\}/, "", tmp)
      if (!opened) {
        if (o > 0) { opened=1; depth=o-c; if (depth <= 0) exit }
      } else {
        depth += o - c
        if (depth <= 0) exit
      }
    }
  ' "${controller_file}"
}

# Имена полей, которые маппер (SongUpdateMapper-подобный) принимает.
extract_mapper_fields() {
  local mapper_file="$1"
  [ -f "${mapper_file}" ] || return 0
  # (a) прямые соответствия "<field>" to SongField.X
  grep -oE '"[a-zA-Z]+"\s+to\s+SongField\.' "${mapper_file}" \
    | sed -E 's/"([a-zA-Z]+)".*/\1/'
  # (b) directSetters
  awk '
    /private val directSetters:/ { in_section=1; next }
    in_section && /^[[:space:]]*\)/ { in_section=0; next }
    in_section {
      if (match($0, /"[^"]+"/)) print substr($0, RSTART+1, RLENGTH-2)
    }
  ' "${mapper_file}"
  # (c) specialCaseKeys
  awk '
    /private val specialCaseKeys:/ { in_section=1; next }
    in_section && /^[[:space:]]*\)/ { in_section=0; next }
    in_section {
      if (match($0, /"[^"]+"/)) print substr($0, RSTART+1, RLENGTH-2)
    }
  ' "${mapper_file}"
}

# =====================================================================
# Самопроверка (фикстуры «до/после»)
# =====================================================================
self_test() {
  local tmp rc=0
  tmp="$(mktemp -d)"
  trap 'rm -rf "${tmp}"' EXIT

  # --- Фикстура 1: корректное дерево → PASS -------------------------
  local good="${tmp}/good"
  mkdir -p "${good}/tools" "${good}/webvue3/src/components" \
           "${good}/karaoke-app/src/main/kotlin/pkg"
  cat > "${good}/tools/endpoint-pairs.yml" <<'EOF'
pairs:
  - component: webvue3/src/components/Good.vue
    endpoint: /api/good/update
    method: POST
    controller: karaoke-app/src/main/kotlin/pkg/FakeController.kt
    controller_method: goodUpdate
EOF
  cat > "${good}/webvue3/src/components/Good.vue" <<'EOF'
<template><input v-model="song.alpha" /></template>
EOF
  cat > "${good}/karaoke-app/src/main/kotlin/pkg/FakeController.kt" <<'EOF'
class FakeController {
    fun goodUpdate(
        @RequestParam(required = false) beta: String,
    ): Boolean {
        FakeMapper.apply(beta)
        return true
    }
}
EOF
  cat > "${good}/karaoke-app/src/main/kotlin/pkg/FakeMapper.kt" <<'EOF'
class FakeMapper {
    fun apply(value: String) {
        val mapping = "alpha" to SongField.X
    }
}
EOF

  check_pairs "${good}" >/dev/null 2>&1
  if [ $? -ne 0 ]; then
    echo "self-test: корректное дерево должно давать PASS, а не ошибку" >&2
    rc=1
  else
    echo "self-test: ✅ корректное дерево → PASS"
  fi

  # --- Фикстура 2 (BR-577): контроллер БЕЗ ссылки на маппер ---------
  # Поле song.alpha покрывается маппером FakeMapper, но метод контроллера
  # его не вызывает → поддерживаемые поля = только @RequestParam.
  # Старый guard брал поля из маппера всегда → PASS (ложный зелёный).
  local bad_source="${tmp}/bad-source"
  mkdir -p "${bad_source}/tools" "${bad_source}/webvue3/src/components" \
           "${bad_source}/karaoke-app/src/main/kotlin/pkg"
  cat > "${bad_source}/tools/endpoint-pairs.yml" <<'EOF'
pairs:
  - component: webvue3/src/components/Good.vue
    endpoint: /api/good/update
    method: POST
    controller: karaoke-app/src/main/kotlin/pkg/FakeController.kt
    controller_method: goodUpdate
EOF
  cat > "${bad_source}/webvue3/src/components/Good.vue" <<'EOF'
<template><input v-model="song.alpha" /></template>
EOF
  cat > "${bad_source}/karaoke-app/src/main/kotlin/pkg/FakeController.kt" <<'EOF'
class FakeController {
    fun goodUpdate(
        @RequestParam(required = false) beta: String,
    ): Boolean {
        return true
    }
}
EOF
  cat > "${bad_source}/karaoke-app/src/main/kotlin/pkg/FakeMapper.kt" <<'EOF'
class FakeMapper {
    fun apply(value: String) {
        val mapping = "alpha" to SongField.X
    }
}
EOF

  check_pairs "${bad_source}" >/dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "self-test: контроллер без ссылки на маппер должен давать FAIL (поле song.alpha не покрыто)" >&2
    rc=1
  else
    echo "self-test: ✅ контроллер без ссылки на маппер → FAIL (источник полей — контроллер пары)"
  fi

  # --- Фикстура 3 (BR-573): controller не найден → FAIL -------------
  local missing="${tmp}/missing"
  mkdir -p "${missing}/tools" "${missing}/webvue3/src/components"
  cat > "${missing}/tools/endpoint-pairs.yml" <<'EOF'
pairs:
  - component: webvue3/src/components/Good.vue
    endpoint: /api/good/update
    method: POST
    controller: karaoke-app/src/main/kotlin/pkg/AbsentController.kt
    controller_method: goodUpdate
EOF
  cat > "${missing}/webvue3/src/components/Good.vue" <<'EOF'
<template><input v-model="song.alpha" /></template>
EOF

  check_pairs "${missing}" >/dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "self-test: отсутствующий controller должен давать FAIL («нет данных» ≠ «нет нарушений»)" >&2
    rc=1
  else
    echo "self-test: ✅ controller не найден → FAIL (пустой набор данных не считается успехом)"
  fi

  # --- Фикстура 4 (BR-573): пустой endpoint-pairs.yml → FAIL --------
  local empty="${tmp}/empty"
  mkdir -p "${empty}/tools"
  cat > "${empty}/tools/endpoint-pairs.yml" <<'EOF'
pairs:
EOF

  check_pairs "${empty}" >/dev/null 2>&1
  if [ $? -eq 0 ]; then
    echo "self-test: пустой endpoint-pairs.yml должен давать FAIL" >&2
    rc=1
  else
    echo "self-test: ✅ пустой endpoint-pairs.yml → FAIL"
  fi

  rm -rf "${tmp}"
  trap - EXIT

  if [ "$rc" -ne 0 ]; then
    echo "SELFTEST FAIL: фикстуры не дали ожидаемых кодов возврата" >&2
    return 1
  fi
  echo "SELFTEST OK: 4/4 фикстуры дали ожидаемые коды возврата"
  return 0
}

case "${1:-}" in
  --self-test)
    self_test
    exit $?
    ;;
  --help|-h|"")
    check_pairs "${REPO_ROOT}"
    exit $?
    ;;
  *)
    echo "Usage: bash tools/check-endpoint-field-coverage.sh [--self-test]" >&2
    exit 2
    ;;
esac
