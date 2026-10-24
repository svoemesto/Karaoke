#!/usr/bin/env bash
# tools/check-confirm-escape-fixtures.sh
#
# Проверка «до/после» для guard'а tools/check-confirm-escape.py
# (спека #558, OpenProject #360, дефект BR-123 из аудита #336).
#
# FR-014 требует, чтобы проверка:
#   (а) на коде ДО правки находила перечисленные места сборки текста
#       подтверждения из пользовательских данных и завершалась отказом;
#   (б) после правки подтверждала их отсутствие.
#
# Сама фикстура «до/после» и есть проверка этого требования: на уязвимом
# снимке кода guard обязан упасть, на исправленном — пройти. Если guard
# начнёт молчать (например, из-за поломки разбора), эта проверка упадёт.
#
# Использование:
#   bash tools/check-confirm-escape-fixtures.sh
#
# Возвращает 0 если обе половины ведут себя ожидаемо, 1 — иначе.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GUARD="${REPO_ROOT}/tools/check-confirm-escape.py"
FIXTURES="${REPO_ROOT}/tools/fixtures/confirm-escape"

errors_total=0

echo "=== Проверка «до/после» для guard'а экранирования подтверждений ==="
echo

# --- 1. Уязвимый снимок кода → guard обязан завершиться отказом ---------------
out="$(python3 "${GUARD}" --src "${FIXTURES}/vulnerable/webvue3/src" 2>&1)"
rc=$?
if [ "${rc}" -eq 0 ]; then
  echo "FAIL: guard принял уязвимый код (ожидался отказ)"
  errors_total=$((errors_total + 1))
else
  echo "OK: уязвимый код отклонён (exit ${rc})"
fi

# Проверяем, что guard нашёл именно дефектные места, а не «что-то ещё».
if echo "${out}" | grep -q 'R3.*songName'; then
  echo "OK: найдено место сборки текста из имени песни без экранирования"
else
  echo "FAIL: guard не нашёл неэкранированное имя песни"
  echo "${out}" | sed 's/^/    /'
  errors_total=$((errors_total + 1))
fi
if echo "${out}" | grep -q 'R1.*allowHtml'; then
  echo "OK: найдено, что общий компонент принимает сырую разметку по умолчанию"
else
  echo "FAIL: guard не нашёл отсутствие текстового режима по умолчанию"
  errors_total=$((errors_total + 1))
fi

# --- 2. Исправленный снимок кода → guard обязан пройти ------------------------
out="$(python3 "${GUARD}" --src "${FIXTURES}/fixed/webvue3/src" 2>&1)"
rc=$?
if [ "${rc}" -ne 0 ]; then
  echo "FAIL: guard отверг исправленный код (ожидался успех)"
  echo "${out}" | sed 's/^/    /'
  errors_total=$((errors_total + 1))
else
  echo "OK: исправленный код принят (exit 0)"
fi

# --- 3. Настоящий код репозитория → guard обязан пройти ----------------------
out="$(python3 "${GUARD}" 2>&1)"
rc=$?
if [ "${rc}" -ne 0 ]; then
  echo "FAIL: guard нашёл нарушения в коде репозитория"
  echo "${out}" | sed 's/^/    /'
  errors_total=$((errors_total + 1))
else
  echo "OK: в коде репозитория нарушений нет (exit 0)"
fi

echo
if [ "${errors_total}" -eq 0 ]; then
  echo "PASS: guard ведёт себя ожидаемо до и после правки."
  exit 0
fi
echo "FAIL: ошибок: ${errors_total}"
exit 1
