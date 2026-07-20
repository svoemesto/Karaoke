#!/usr/bin/env bash
# tools/generate-eslint-baseline.sh
#
# Генерирует baseline-файл для ESLint (webvue3 или karaoke-public) из текущих нарушений.
# ESLint не имеет встроенного baseline, поэтому мы сохраняем все текущие
# нарушения в JSON-массив.
#
# Использование:
#   ./tools/generate-eslint-baseline.sh webvue3
#   ./tools/generate-eslint-baseline.sh karaoke-public
#
# Требования: jq, npm-зависимости установлены (npm install).

set -uo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <webvue3|karaoke-public>"
  exit 2
fi

MODULE="$1"
BASELINE_FILE="${MODULE}/.eslint-baseline.json"

if [ ! -d "$MODULE" ]; then
  echo "ERROR: директория '$MODULE' не существует"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq не установлен (apt install jq / brew install jq)"
  exit 1
fi

if [ ! -d "$MODULE/node_modules" ]; then
  echo "INFO: $MODULE/node_modules не существует, запускаю npm install..."
  (cd "$MODULE" && npm install --no-audit --no-fund)
fi

echo "==> Генерация baseline для $MODULE..."

# Запускаем eslint с JSON-форматом, парсим нарушения, сохраняем в baseline.
# max-warnings 99999 чтобы получить ВСЕ нарушения, не падать.
cd "$MODULE"

# eslint может вернуть ненулевой код — это OK (есть нарушения).
# Отключаем set -e временно.
set +e
npx eslint . --ext .vue,.js,.ts --format json --max-warnings 99999 > /tmp/eslint-output.json 2>/dev/null
ESLINT_EXIT=$?
set -e

# Преобразуем в плоский массив {ruleId, file, line, column, message, severity}
jq '[.[] | .messages[] | {
  ruleId: .ruleId,
  file: .filePath,
  line: .line,
  column: .column,
  message: .message,
  severity: .severity
}]' /tmp/eslint-output.json > ".eslint-baseline.json"

rm -f /tmp/eslint-output.json

count=$(jq 'length' ".eslint-baseline.json")
echo "==> Записано $count нарушений в $BASELINE_FILE"

cd ..
echo "Готово. Для проверки, что новых нарушений нет:"
echo "  ./tools/check-eslint-baseline.sh $MODULE"
