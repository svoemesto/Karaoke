#!/usr/bin/env bash
# tools/check-marker-escape-guard.sh
#
# Проверка «до/после» для регрессионного теста на экранирование метки
# комментария в formatText (аудит #336, BR-523, пакет PKG-S3-02,
# OpenProject #387).
#
# ЗАЧЕМ. Дефект BR-312 (S2, OpenProject #377, волна D) — метка пользователя
# («COMMENT|<img src=x onerror=alert(1)>») попадает в HTML-буфер без
# экранирования, а результат уходит в v-html. Сам дефект закрывается в другой
# волне, поэтому регрессионный тест (BR-523) пока ЖДЁТ: падающий тест в CI
# ронял бы master задолго до фикса.
#
# Чтобы «ожидающий» тест не превратился в мёртвый код, этот guard проверяет
# три вещи:
#
#   1. ТЕСТ ВЗВЕДЁН. На уязвимом снимке кода (tools/fixtures/marker-escape/
#      vulnerable/) тест обязан ПАДАТЬ — иначе он ничего не проверяет.
#   2. ОЖИДАНИЕ ТЕСТА ВЕРНО. На исправленном снимке кода (…/fixed/) тест
#      обязан ПРОХОДИТЬ — иначе фикс BR-312 не закроет тест.
#   3. СОСТОЯНИЕ СООТВЕТСТВУЕТ КОДУ. Прогон теста против НАСТОЯЩЕГО кода
#      репозитория говорит, экранирует код или нет:
#        - код экранирует  → тест в репозитории обязан быть ВКЛЮЧЁН (skip снят);
#        - код не экранирует → тест в репозитории обязан ЖДАТЬ (skip на месте).
#      Расхождение = «тест говорит одно, код делает другое» → guard падает.
#      Именно этот пункт заставит волну D снять skip вместе с фиксом.
#
# Использование:
#   bash tools/check-marker-escape-guard.sh
#
# Возвращает 0, если всё ожидаемо, 1 — иначе. Требуется Node (node --test).

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}" || exit 1

FIXTURES="${REPO_ROOT}/tools/fixtures/marker-escape"

PUBLIC_IMPL="karaoke-public/src/composables/useKaraokeEditor.js"
ADMIN_IMPL="webvue3/src/components/SongEditor/useReviewModalFormat.js"
PUBLIC_TEST="karaoke-public/src/composables/__tests__/formatText-escape.test.js"
ADMIN_TEST="webvue3/src/components/SongEditor/__tests__/useReviewModalFormat.test.js"

errors_total=0
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# Копирует тест в каталог $2, СНИМАЯ ожидание (skip) — так тест реально
# выполняется. Константа SKIP_UNTIL_BR312 — единственная точка переключения.
stage_test() {
  local src="$1" dest_dir="$2"
  mkdir -p "${dest_dir}/__tests__"
  sed 's|^const SKIP_UNTIL_BR312 = .*|const SKIP_UNTIL_BR312 = false|' \
    "${src}" > "${dest_dir}/__tests__/$(basename "${src}")"
}

# Прогоняет staged-тесты в каталоге $1; печатает код возврата node --test.
run_staged() {
  local dir="$1"
  ( cd "${dir}" && node --test "__tests__/*.test.js" >/dev/null 2>&1 )
  echo $?
}

# Пассивность теста в репозитории: 0 — тест ЖДЁТ (skip на месте),
# 1 — тест ВКЛЮЧЁН (skip снят).
test_is_active() {
  if grep -qE "^const SKIP_UNTIL_BR312 = false" "$1"; then
    echo 1
  else
    echo 0
  fi
}

echo "=== Проверка «до/после» для теста экранирования метки (BR-523) ==="
echo

# --- 1. Уязвимый снимок кода → тест обязан упасть ------------------------------
cp -a "${FIXTURES}/vulnerable" "${TMP}/vulnerable"
stage_test "${PUBLIC_TEST}" "${TMP}/vulnerable/karaoke-public"
stage_test "${ADMIN_TEST}" "${TMP}/vulnerable/webvue3"

for case in "karaoke-public:${PUBLIC_TEST}" "webvue3:${ADMIN_TEST}"; do
  sub="${case%%:*}"
  test_file="${case#*:}"
  rc="$(run_staged "${TMP}/vulnerable/${sub}")"
  if [ "${rc}" -eq 0 ]; then
    echo "FAIL: тест ${test_file} прошёл на УЯЗВИМОМ коде (ожидался отказ) — тест ничего не проверяет"
    errors_total=$((errors_total + 1))
  else
    echo "OK: тест ${test_file} ловит уязвимый код (exit ${rc})"
  fi
done

# --- 2. Исправленный снимок кода → тест обязан пройти -------------------------
cp -a "${FIXTURES}/fixed" "${TMP}/fixed"
stage_test "${PUBLIC_TEST}" "${TMP}/fixed/karaoke-public"
stage_test "${ADMIN_TEST}" "${TMP}/fixed/webvue3"

for case in "karaoke-public:${PUBLIC_TEST}" "webvue3:${ADMIN_TEST}"; do
  sub="${case%%:*}"
  test_file="${case#*:}"
  rc="$(run_staged "${TMP}/fixed/${sub}")"
  if [ "${rc}" -ne 0 ]; then
    echo "FAIL: тест ${test_file} не прошёл на ИСПРАВЛЕННОМ коде (ожидался успех)"
    errors_total=$((errors_total + 1))
  else
    echo "OK: тест ${test_file} проходит на исправленном коде (exit 0)"
  fi
done

# --- 3. Настоящий код репозитория → skip-состояние теста обязано совпадать ---
mkdir -p "${TMP}/repo/karaoke-public" "${TMP}/repo/webvue3"
cp "${PUBLIC_IMPL}" "${TMP}/repo/karaoke-public/useKaraokeEditor.js"
cp "${ADMIN_IMPL}" "${TMP}/repo/webvue3/useReviewModalFormat.js"
stage_test "${PUBLIC_TEST}" "${TMP}/repo/karaoke-public"
stage_test "${ADMIN_TEST}" "${TMP}/repo/webvue3"

for case in "karaoke-public:${PUBLIC_IMPL}:${PUBLIC_TEST}" "webvue3:${ADMIN_IMPL}:${ADMIN_TEST}"; do
  sub="$(echo "${case}" | cut -d: -f1)"
  impl="$(echo "${case}" | cut -d: -f2)"
  test_file="$(echo "${case}" | cut -d: -f3)"

  rc="$(run_staged "${TMP}/repo/${sub}")"
  active="$(test_is_active "${test_file}")"

  if [ "${rc}" -eq 0 ]; then
    # Код экранирует → тест должен быть включён, иначе фикс не покрыт.
    if [ "${active}" -eq 1 ]; then
      echo "OK: ${impl} экранирует метку, тест ${test_file} включён"
    else
      echo "FAIL: ${impl} экранирует метку, но тест ${test_file} ЖДЁТ — снимите skip (SKIP_UNTIL_BR312 → false)"
      errors_total=$((errors_total + 1))
    fi
  else
    # Код не экранирует → тест должен ждать, иначе CI падает на живом дефекте.
    if [ "${active}" -eq 0 ]; then
      echo "OK: ${impl} ещё не экранирует метку (дефект BR-312 открыт), тест ${test_file} ждёт фикса"
    else
      echo "FAIL: ${impl} НЕ экранирует метку, а тест ${test_file} включён — CI упал бы на живом дефекте BR-312"
      errors_total=$((errors_total + 1))
    fi
  fi
done

echo
if [ "${errors_total}" -eq 0 ]; then
  echo "PASS: тест экранирования взведён и соответствует состоянию кода."
  exit 0
fi
echo "FAIL: ошибок: ${errors_total}"
exit 1
