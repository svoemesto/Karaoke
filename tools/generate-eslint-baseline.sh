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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ ! -d "$MODULE" ]; then
  echo "ERROR: диретория '$MODULE' не существует"
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

# BR-578: путь пишем ОТНОСИТЕЛЬНО корня репозитория ("webvue3/src/..."),
# а не абсолютный путь ESLint — иначе baseline не переносится между
# машинами и worktree'ами. Пути вычисляем ДО смены каталога.
MODULE_ABS="$(cd "$MODULE" && pwd)"
case "$MODULE_ABS" in
  "$REPO_ROOT"/*) MODULE_REL="${MODULE_ABS#"$REPO_ROOT"/}" ;;
  *)              MODULE_REL="$MODULE" ;;
esac
MODULE_BASE="$(basename "$MODULE_ABS")"

# Запускаем eslint с JSON-форматом, парсим нарушения, сохраняем в baseline.
# max-warnings 99999 чтобы получить ВСЕ нарушения, не падать.
cd "$MODULE"

# eslint может вернуть ненулевой код — это OK (есть нарушения).
# Отключаем set -e временно.
set +e
npx eslint . --ext .vue,.js,.ts --format json --max-warnings 99999 > /tmp/eslint-output.json 2>/dev/null
ESLINT_EXIT=$?
set -e

# Преобразуем в плоский массив {ruleId, file, line, column, message, severity}.
jq --arg repo_root "$REPO_ROOT" --arg module_rel "$MODULE_REL" --arg module_base "$MODULE_BASE" '
  def norm:
    . as $p
    | if ($p | startswith($repo_root + "/")) then ($p | ltrimstr($repo_root + "/"))
      elif ($p | startswith("/")) then
        (($p | split("/" + $module_base + "/")) as $parts
         | if ($parts | length) > 1 then ($module_rel + "/" + ($parts | last)) else $p end)
      elif ($p | startswith($module_rel + "/")) then ($module_rel + "/" + ($p | ltrimstr($module_rel + "/")))
      else ($module_rel + "/" + $p)
      end;
  [.[] | .filePath as $fp | .messages[] | {
  ruleId: .ruleId,
  file: ($fp | norm),
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
