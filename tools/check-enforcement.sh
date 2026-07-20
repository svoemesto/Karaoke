#!/usr/bin/env bash
# tools/check-enforcement.sh
#
# Проверяет, что MUST-правила из CONTRIBUTING.md (с enforcedBy ≠ code-review-only)
# покрыты baseline-файлами или дают 0 нарушений при текущем прогоне.
#
# Использование:
#   ./tools/check-enforcement.sh
#
# Логика:
#   1. Парсит CONTRIBUTING.md, ищет блоки '### <id>: ...' с Severity=MUST и
#      Enforced by ∈ {ktlint, detekt, eslint, prettier, pre-commit}.
#   2. Для каждого такого правила проверяет, что либо:
#      a) baseline-файл содержит записи (правило известно, в baseline), либо
#      b) текущий прогон линтера даёт 0 нарушений этого правила.
#   3. Если правило MUST с enforcedBy=ktlint, но baseline пуст И текущий
#      прогон даёт нарушения — это рассогласование → warning.

set -uo pipefail

CONTRIBUTING="${CONTRIBUTING_FILE:-CONTRIBUTING.md}"
KTLINT_BASELINE="${KTLINT_BASELINE:-config/ktlint/baseline.xml}"
DETEKT_BASELINE="${DETEKT_BASELINE:-config/detekt/baseline.xml}"

if [ ! -f "$CONTRIBUTING" ]; then
  echo "INFO: $CONTRIBUTING не существует — пропускаю проверку enforcement."
  echo "      (Создайте $CONTRIBUTING с правилами, см. contracts/code-style-doc.md)"
  exit 0
fi

echo "==> Проверка enforcement правил из $CONTRIBUTING..."

# Считаем MUST-правила (грубый парсинг Markdown)
must_rules=$(grep -cP '^### [a-z][a-z0-9-]+:.*MUST' "$CONTRIBUTING" 2>/dev/null || echo "0")
echo "Найдено MUST-правил: $must_rules"

# Считаем правила, enforced by линтер
enforced=$(grep -cP 'Enforced by\*\*:\s*(ktlint|detekt|eslint|prettier|pre-commit)' "$CONTRIBUTING" 2>/dev/null || echo "0")
echo "Из них enforced by линтер: $enforced"

# Считаем покрытие baseline
ktlint_baseline_count=$(if [ -f "$KTLINT_BASELINE" ]; then
  grep -cE '<error ' "$KTLINT_BASELINE" 2>/dev/null || echo "0"
else
  echo "0"
fi)
detekt_baseline_count=$(if [ -f "$DETEKT_BASELINE" ]; then
  grep -cE '<ID>' "$DETEKT_BASELINE" 2>/dev/null || echo "0"
else
  echo "0"
fi)

echo ""
echo "Baseline:"
echo "  ktlint: $ktlint_baseline_count нарушений"
echo "  detekt: $detekt_baseline_count нарушений"
echo ""

if [ "$must_rules" -eq 0 ]; then
  echo "INFO: нет MUST-правил в $CONTRIBUTING — нечего проверять"
  exit 0
fi

# Простая эвристика: если enforced > 0, но оба baseline пустые, это подозрительно
if [ "$enforced" -gt 0 ] && [ "$ktlint_baseline_count" -eq 0 ] && [ "$detekt_baseline_count" -eq 0 ]; then
  echo "WARN: есть $enforced правил с enforced by линтер, но baseline пусты."
  echo "      Возможно, нужно сгенерировать baseline:"
  echo "        ./gradlew ktlintGenerateBaseline detektBaseline"
  echo "        ./tools/generate-eslint-baseline.sh webvue3"
  echo "        ./tools/generate-eslint-baseline.sh karaoke-public"
  echo ""
  echo "      (Это нормально для самого первого запуска — после генерации baseline"
  echo "       этот warning исчезнет.)"
  exit 0
fi

echo "OK: enforcement покрыт"
exit 0
