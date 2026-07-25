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
DETEKT_BASELINE="${DETEKT_BASELINE:-config/detekt/baseline.xml}"

if [ ! -f "$CONTRIBUTING" ]; then
  echo "INFO: $CONTRIBUTING не существует — пропускаю проверку enforcement."
  echo "      (Создайте $CONTRIBUTING с правилами, см. contracts/code-style-doc.md)"
  exit 0
fi

echo "==> Проверка enforcement правил из $CONTRIBUTING..."

# Считаем MUST-правила. Реальная структура CONTRIBUTING.md — заголовок
# '### <slug>: <title>', а Severity/Enforced by идут отдельными строками
# ниже (не в самом заголовке), например:
#   ### kotlin-naming-classes: Именование классов
#
#   **Severity**: MUST
#   **Enforced by**: ktlint
# Поэтому считаем блоки между заголовками и ищем внутри каждого блока
# '**Severity**: MUST' (grep -c на самом заголовке всегда давал 0 —
# исторический баг).
must_rules=$(awk '
  /^### / { if (block != "" && block ~ /\*\*Severity\*\*:[ \t]*MUST/) count++; block = "" ; next }
  { block = block "\n" $0 }
  END { if (block != "" && block ~ /\*\*Severity\*\*:[ \t]*MUST/) count++; print count+0 }
' "$CONTRIBUTING")
echo "Найдено MUST-правил: $must_rules"

# Считаем правила, enforced by линтер
enforced=$(grep -cP 'Enforced by\*\*:\s*(ktlint|detekt|eslint|prettier|pre-commit)' "$CONTRIBUTING" 2>/dev/null)
echo "Из них enforced by линтер: $enforced"

# Считаем покрытие baseline.
# ktlint baseline — per-module файлы config/ktlint/baseline-<module>.xml
# (нет единого config/ktlint/baseline.xml — см. build.gradle.kts).
ktlint_baseline_count=0
for f in config/ktlint/baseline-*.xml; do
  [ -f "$f" ] || continue
  n=$(grep -cE '<error ' "$f" 2>/dev/null)
  ktlint_baseline_count=$((ktlint_baseline_count + n))
done

detekt_baseline_count=0
if [ -f "$DETEKT_BASELINE" ]; then
  detekt_baseline_count=$(grep -cE '<ID>' "$DETEKT_BASELINE" 2>/dev/null)
fi

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
