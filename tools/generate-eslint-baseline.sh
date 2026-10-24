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

# BR-595: отслеживаемый baseline-файл НЕ обнуляется до успешного разбора.
# Раньше вывод eslint писался прямо в ".eslint-baseline.json" (обрезка
# отслеживаемого файла), и при падении eslint (код ≥ 2: нет node_modules,
# битый конфиг) оставался файл нулевой длины — CI-гейт frontend-lint потом
# падал с невнятной ошибкой jq. Теперь: mktemp + jq во временный файл +
# атомарный mv по месту только после успешного разбора.
ESLINT_OUT="$(mktemp)"
ESLINT_ERR="$(mktemp)"
BASELINE_TMP="$(mktemp)"
cleanup() { rm -f "$ESLINT_OUT" "$ESLINT_ERR" "$BASELINE_TMP"; }
trap cleanup EXIT

# eslint может вернуть ненулевой код — это OK (есть нарушения, код 1).
# Код ≥ 2 — это ОШИБКА eslint (нет node_modules, битый конфиг, …), а не
# «найдены нарушения»: baseline в этом случае не обновляем.
# stderr НЕ глушим — в нём причина сбоя.
npx eslint . --ext .vue,.js,.ts --format json --max-warnings 99999 >"$ESLINT_OUT" 2>"$ESLINT_ERR"
ESLINT_EXIT=$?
if [ "$ESLINT_EXIT" -ge 2 ]; then
  echo "ERROR: eslint завершился с кодом $ESLINT_EXIT — это ошибка eslint, а не «найдены нарушения»." >&2
  echo "       Baseline НЕ изменён ($BASELINE_FILE остался прежним)." >&2
  if [ -s "$ESLINT_ERR" ]; then
    echo "--- stderr eslint ---" >&2
    sed 's/^/  /' "$ESLINT_ERR" >&2
  fi
  exit 1
fi
if [ -s "$ESLINT_ERR" ]; then
  echo "WARN: eslint писал в stderr (код $ESLINT_EXIT):" >&2
  sed 's/^/  /' "$ESLINT_ERR" >&2
fi
# Пустой вывод при «успешном» коде тоже означает, что baseline писать не из чего:
# иначе jq вернёт пустой результат, и на место отслеживаемого файла ляжет пустой.
if [ ! -s "$ESLINT_OUT" ]; then
  echo "ERROR: eslint ничего не вывел в stdout (код $ESLINT_EXIT) — baseline НЕ изменён." >&2
  exit 1
fi

# Преобразуем в плоский массив {ruleId, file, line, column, message, severity}.
if ! jq --arg repo_root "$REPO_ROOT" --arg module_rel "$MODULE_REL" --arg module_base "$MODULE_BASE" '
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
}]' "$ESLINT_OUT" > "$BASELINE_TMP"; then
  echo "ERROR: не удалось разобрать вывод eslint (jq) — baseline НЕ изменён." >&2
  exit 1
fi

# Атомарная замена: файл на месте меняется только целиком и только при успехе.
mv "$BASELINE_TMP" ".eslint-baseline.json"

count=$(jq 'length' ".eslint-baseline.json")
echo "==> Записано $count нарушений в $BASELINE_FILE"

cd ..
echo "Готово. Для проверки, что новых нарушений нет:"
echo "  ./tools/check-eslint-baseline.sh $MODULE"
