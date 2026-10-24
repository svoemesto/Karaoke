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
#
# Ключ сопоставления (BR-578): ruleId + путь ОТНОСИТЕЛЬНО корня репозитория
# + текст сообщения. Номер строки/колонки в ключ НЕ входит: вставка строк
# выше нарушения сдвигает line/column, и старое нарушение не должно
# «выглядеть как новое». Путь нормализуется (абсолютный путь ESLint →
# относительный от корня репозитория), поэтому baseline, созданный на
# другой машине или в другом worktree, совпадает.

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

if [ ! -f "$BASELINE_FILE" ]; then
  echo "ERROR: baseline '$BASELINE_FILE' не существует. Сначала запустите:"
  echo "  ./tools/generate-eslint-baseline.sh $MODULE"
  exit 1
fi

if [ ! -d "$MODULE/node_modules" ]; then
  echo "INFO: $MODULE/node_modules не существует, запускаю npm install..."
  (cd "$MODULE" && npm install --no-audit --no-fund)
fi

# --- Нормализация путей (BR-578) -------------------------------------------
# MODULE_ABS — абсолютный путь модуля, MODULE_REL — его путь относительно
# корня репозитория (например "webvue3"). Итоговый путь нарушения:
# "<MODULE_REL>/<путь внутри модуля>" — одинаково на любой машине.
MODULE_ABS="$(cd "$MODULE" && pwd)"
case "$MODULE_ABS" in
  "$REPO_ROOT"/*) MODULE_REL="${MODULE_ABS#"$REPO_ROOT"/}" ;;
  *)              MODULE_REL="$MODULE" ;;
esac
MODULE_BASE="$(basename "$MODULE_ABS")"

echo "==> Проверка $MODULE..."

cd "$MODULE"

# Получаем текущие нарушения
set +e
npx eslint . --ext .vue,.js,.ts --format json --max-warnings 99999 > /tmp/eslint-current.json 2>/dev/null
set -e

# Нормализация путей: абсолютный путь ESLint → путь относительно корня
# репозитория. Применяется и к текущим нарушениям, и к baseline (старый
# baseline мог быть записан абсолютными путями или путями от другой машины).
NORMALIZE_ARGS=(--arg repo_root "$REPO_ROOT" --arg module_rel "$MODULE_REL" --arg module_base "$MODULE_BASE")
NORM_DEF='
  def norm:
    . as $p
    | if ($p | startswith($repo_root + "/")) then ($p | ltrimstr($repo_root + "/"))
      elif ($p | startswith("/")) then
        (($p | split("/" + $module_base + "/")) as $parts
         | if ($parts | length) > 1 then ($module_rel + "/" + ($parts | last)) else $p end)
      elif ($p | startswith($module_rel + "/")) then ($module_rel + "/" + ($p | ltrimstr($module_rel + "/")))
      else ($module_rel + "/" + $p)
      end;
'

# Преобразуем в плоский массив.
# ВАЖНО: `.filePath` лежит на уровне файла, а не сообщения — путь берём
# ДО спуска в `.messages[]` (иначе он всегда null и ключ сопоставления
# вырождается в ruleId+line+column).
jq "${NORMALIZE_ARGS[@]}" "${NORM_DEF}
  [.[] | .filePath as \$fp | .messages[] | {
  ruleId: .ruleId,
  file: (\$fp | norm),
  line: .line,
  column: .column,
  message: .message,
  severity: .severity
}]" /tmp/eslint-current.json > /tmp/eslint-current-flat.json

# Baseline приводим к тому же виду (старый baseline мог быть записан
# абсолютными путями или путями от другой машины).
jq "${NORMALIZE_ARGS[@]}" "${NORM_DEF}
  map(.file = ((.file // \"\") | norm))" ".eslint-baseline.json" > /tmp/eslint-baseline-flat.json

current_count=$(jq 'length' /tmp/eslint-current-flat.json)
baseline_count=$(jq 'length' /tmp/eslint-baseline-flat.json)

echo "Текущих нарушений: $current_count"
echo "В baseline:        $baseline_count"

# Сравниваем: находим записи в current, которых нет в baseline.
# Ключ: ruleId + относительный путь + текст сообщения (без line/column —
# сдвиг строк не должен превращать старое нарушение в «новое»).
# Сопоставление мультимножественное: каждая запись baseline используется
# не более одного раза, поэтому N одинаковых нарушений требуют N записей
# в baseline.
new_violations=$(jq --slurpfile baseline "/tmp/eslint-baseline-flat.json" '
  def key: [.ruleId, .file, .message] | tojson;
  . as $current
  | ($baseline[0]
     | map(key)
     | group_by(.)
     | map({key: .[0], value: length})
     | from_entries) as $pool
  | reduce $current[] as $c
      ({pool: $pool, new: []};
       ($c | key) as $k
       | if ((.pool[$k] // 0) > 0)
         then .pool[$k] = ((.pool[$k] // 0) - 1)
         else .new += [$c]
         end)
  | .new
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
rm -f /tmp/eslint-current.json /tmp/eslint-current-flat.json /tmp/eslint-baseline-flat.json
exit 0
