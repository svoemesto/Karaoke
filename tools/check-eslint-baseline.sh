#!/usr/bin/env bash
# tools/check-eslint-baseline.sh
#
# Проверяет, что текущие нарушения ESLint в webvue3 или karaoke-public
# НЕ превышают baseline (т.е. нет новых нарушений).
#
# Использование:
#   ./tools/check-eslint-baseline.sh webvue3
#   ./tools/check-eslint-baseline.sh karaoke-public
#
# Логика:
#   1. Запустить eslint, получить текущие нарушения в /tmp/eslint-current.json
#   2. Прочитать baseline из .eslint-baseline.json
#   3. Найти записи, которых нет в baseline → это "новые нарушения"
#   4. Если новых нарушений > 0 → exit 1 (блокер)
#   5. Иначе exit 0

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

if [ ! -f "$BASELINE_FILE" ]; then
  echo "ERROR: baseline '$BASELINE_FILE' не существует. Сначала запустите:"
  echo "  ./tools/generate-eslint-baseline.sh $MODULE"
  exit 1
fi

if [ ! -d "$MODULE/node_modules" ]; then
  echo "INFO: $MODULE/node_modules не существует, запускаю npm install..."
  (cd "$MODULE" && npm install --no-audit --no-fund)
fi

echo "==> Проверка $MODULE..."

cd "$MODULE"

# Получаем текущие нарушения
set +e
npx eslint . --ext .vue,.js,.ts --format json --max-warnings 99999 > /tmp/eslint-current.json 2>/dev/null
set -e

# Преобразуем в плоский массив
jq '[.[] | .messages[] | {
  ruleId: .ruleId,
  file: .filePath,
  line: .line,
  column: .column,
  message: .message,
  severity: .severity
}]' /tmp/eslint-current.json > /tmp/eslint-current-flat.json

current_count=$(jq 'length' /tmp/eslint-current-flat.json)
baseline_count=$(jq 'length' ".eslint-baseline.json")

echo "Текущих нарушений: $current_count"
echo "В baseline:        $baseline_count"

# Сравниваем: находим записи в current, которых нет в baseline
# Ключ сравнения: ruleId + file + line + column
new_violations=$(jq --slurpfile baseline ".eslint-baseline.json" '
  . as $current |
  $baseline[0] as $baseline |
  $current | map(
    . as $c |
    ($baseline | map(
      select(
        .ruleId == $c.ruleId and
        .file == $c.file and
        .line == $c.line and
        .column == $c.column
      )
    ) | length) as $match_count |
    if $match_count == 0 then
      {ruleId: .ruleId, file: .file, line: .line, column: .column, message: .message}
    else
      empty
    end
  )
' /tmp/eslint-current-flat.json)

new_count=$(echo "$new_violations" | jq 'length')

if [ "$new_count" -gt 0 ]; then
  echo ""
  echo "==> ОБНАРУЖЕНО $new_count НОВЫХ НАРУШЕНИЙ (сверх baseline):"
  echo "$new_violations" | jq -r '.[] | "  \(.file):\(.line):\(.column)  \(.ruleId)  \(.message)"'
  echo ""
  echo "Исправьте новые нарушения или (если намеренно) добавьте в baseline:"
  echo "  ./tools/generate-eslint-baseline.sh $MODULE"
  exit 1
fi

if [ "$current_count" -lt "$baseline_count" ]; then
  reduced=$((baseline_count - current_count))
  echo ""
  echo "==> 🎉 Baseline сократился на $reduced нарушений! Регенерируйте:"
  echo "  ./tools/generate-eslint-baseline.sh $MODULE"
fi

echo "OK: новых нарушений нет"
rm -f /tmp/eslint-current.json /tmp/eslint-current-flat.json
exit 0
