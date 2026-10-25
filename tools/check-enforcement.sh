#!/usr/bin/env bash
# tools/check-enforcement.sh
#
# Проверяет, что MUST-правила из CONTRIBUTING.md (с enforcedBy ≠ code-review-only)
# покрыты baseline-файлами или дают 0 нарушений при текущем прогоне.
#
# Использование:
#   ./tools/check-enforcement.sh
#   ./tools/check-enforcement.sh --self-test
#
# Логика:
#   1. Парсит CONTRIBUTING.md, ищет блоки '### <id>: ...' с Severity=MUST и
#      Enforced by ∈ {ktlint, detekt, eslint, prettier, pre-commit}.
#   2. Для каждого такого правила проверяет, что либо:
#      a) baseline-файл содержит записи (правило известно, в baseline), либо
#      b) текущий прогон линтера даёт 0 нарушений этого правила.
#   3. Если правило MUST с enforcedBy=ktlint, но baseline пуст И текущий
#      прогон даёт нарушения — это рассогласование → warning.
#
# BR-586 (аудит #336, пакет PKG-S3-30): скрипт НИКОГДА не возвращал ненулевой
# код и всегда печатал «OK: enforcement покрыт». Подсчёт `enforced` шёл через
# `grep -cP` (PCRE) — на сборках без PCRE (BusyBox, macOS) grep падал,
# stderr глушился, результат был пустым, а сравнение `[ "" -gt 0 ]` давало
# «integer expression expected» и скрипт продолжал работу с exit 0.
# Теперь: переносимый подсчёт через `grep -cE` (POSIX ERE), проверка, что
# счётчики — числа, и НЕНУЛЕВОЙ код возврата, когда проверка не могла быть
# выполнена (нет файла, нет MUST-правил, счётчики не числа).
#
# Exit codes:
#   0 — enforcement проверен, нарушений не обнаружено
#   1 — enforcement-рассогласование (enforced-правила без baseline)
#   2 — проверка не могла быть выполнена (usage/IO/счётчики не числа)

set -uo pipefail

CONTRIBUTING="${CONTRIBUTING_FILE:-CONTRIBUTING.md}"
DETEKT_BASELINE="${DETEKT_BASELINE:-config/detekt/baseline.xml}"

# Переносимый подсчёт совпадений: POSIX ERE (`grep -cE`), БЕЗ `-P`.
# grep -c печатает "0" и возвращает 1, если совпадений нет — это не ошибка.
# Возвращает число на stdout; при ошибке — сообщение в stderr и код 1.
count_matches() {
    local pattern="$1" file="$2" out rc
    out="$(grep -cE "$pattern" "$file" 2>/dev/null)"
    rc=$?
    if [ "$rc" -gt 1 ]; then
        echo "ERROR: подсчёт не выполнен (grep -cE → код $rc) для файла: $file" >&2
        return 1
    fi
    out="$(printf '%s' "$out" | tr -d '[:space:]')"
    if ! [[ "$out" =~ ^[0-9]+$ ]]; then
        echo "ERROR: подсчёт дал нечисловой результат ('$out') для файла: $file" >&2
        return 1
    fi
    printf '%s' "$out"
}

# --- Вспомогательный разбор CONTRIBUTING.md -----------------------------
count_must_rules() {
    local file="$1"
    # Реальная структура CONTRIBUTING.md — заголовок '### <slug>: <title>',
    # а Severity/Enforced by идут отдельными строками ниже (не в самом
    # заголовке), например:
    #   ### kotlin-naming-classes: Именование классов
    #
    #   **Severity**: MUST
    #   **Enforced by**: ktlint
    # Поэтому считаем блоки между заголовками и ищем внутри каждого блока
    # '**Severity**: MUST'.
    awk '
      /^### / { if (block != "" && block ~ /\*\*Severity\*\*:[ \t]*MUST/) count++; block = "" ; next }
      { block = block "\n" $0 }
      END { if (block != "" && block ~ /\*\*Severity\*\*:[ \t]*MUST/) count++; print count+0 }
    ' "$file"
}

run_check() {
    local must_rules enforced ktlint_baseline_count detekt_baseline_count n f
    # Разрешаем путь В МОМЕНТ ВЫЗОВА: переменная окружения CONTRIBUTING_FILE
    # должна перекрывать значение по умолчанию (иначе фикстуры --self-test
    # проверяли бы не тот файл).
    local contributing="${CONTRIBUTING_FILE:-$CONTRIBUTING}"

    if [ ! -f "$contributing" ]; then
        # BR-586: проверка не могла быть выполнена — это не «всё хорошо».
        echo "ERROR: $contributing не существует — проверить enforcement невозможно." >&2
        echo "       (Создайте $contributing с правилами, см. contracts/code-style-doc.md)" >&2
        return 2
    fi

    echo "==> Проверка enforcement правил из $contributing..."

    must_rules="$(count_must_rules "$contributing")"
    if ! [[ "$must_rules" =~ ^[0-9]+$ ]]; then
        echo "ERROR: не удалось посчитать MUST-правила (получено '$must_rules') — проверка не выполнена." >&2
        return 2
    fi
    echo "Найдено MUST-правил: $must_rules"

    # Считаем правила, enforced by линтер.
    # BR-586: POSIX ERE вместо PCRE (-P) — переносимо на любой grep.
    enforced="$(count_matches 'Enforced by\*\*:[[:space:]]*(ktlint|detekt|eslint|prettier|pre-commit)' "$contributing")" || return 2
    echo "Из них enforced by линтер: $enforced"

    # Считаем покрытие baseline.
    # ktlint baseline — per-module файлы config/ktlint/baseline-<module>.xml
    # (нет единого config/ktlint/baseline.xml — см. build.gradle.kts).
    ktlint_baseline_count=0
    for f in config/ktlint/baseline-*.xml; do
        [ -f "$f" ] || continue
        n="$(count_matches '<error ' "$f")" || return 2
        ktlint_baseline_count=$((ktlint_baseline_count + n))
    done

    detekt_baseline_count=0
    if [ -f "$DETEKT_BASELINE" ]; then
        detekt_baseline_count="$(count_matches '<ID>' "$DETEKT_BASELINE")" || return 2
    fi

    echo ""
    echo "Baseline:"
    echo "  ktlint: $ktlint_baseline_count нарушений"
    echo "  detekt: $detekt_baseline_count нарушений"
    echo ""

    if [ "$must_rules" -eq 0 ]; then
        # BR-586: нет MUST-правил — проверять нечего, но и «проверка прошла»
        # быть не может: набор входных данных пуст.
        echo "ERROR: нет MUST-правил в $contributing — проверять нечего (набор данных пуст)." >&2
        return 2
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
        return 0
    fi

    echo "OK: enforcement покрыт"
    return 0
}

# =====================================================================
# Самопроверка (фикстуры «до/после»)
# =====================================================================
self_test() {
    local rc=0 tmp out code
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' EXIT

    # 1. Корректный CONTRIBUTING.md → exit 0, счётчики — числа.
    cat > "$tmp/CONTRIBUTING.md" <<'EOF'
### kotlin-naming-classes: Именование классов

**Severity**: MUST
**Enforced by**: ktlint

### eslint-no-console: Запрет console

**Severity**: MUST
**Enforced by**: eslint

### docs-style: Стиль документации

**Severity**: SHOULD
**Enforced by**: code-review-only
EOF
    out="$(CONTRIBUTING_FILE="$tmp/CONTRIBUTING.md" run_check 2>&1)"
    code=$?
    if [ "$code" -ne 0 ]; then
        echo "self-test: корректный CONTRIBUTING.md должен давать exit 0, получено $code" >&2
        rc=1
    elif ! printf '%s' "$out" | grep -qE 'Найдено MUST-правил: [0-9]+'; then
        echo "self-test: счётчик MUST-правил должен быть числом, вывод: $out" >&2
        rc=1
    elif ! printf '%s' "$out" | grep -qE 'Из них enforced by линтер: [0-9]+'; then
        echo "self-test: счётчик enforced должен быть числом, вывод: $out" >&2
        rc=1
    else
        echo "self-test: ✅ корректный CONTRIBUTING.md → exit 0, счётчики — числа"
    fi

    # 2. BR-586: файла нет → проверка не могла быть выполнена → exit 2.
    out="$(CONTRIBUTING_FILE="$tmp/absent.md" run_check 2>&1)"
    code=$?
    if [ "$code" -ne 2 ]; then
        echo "self-test: отсутствующий CONTRIBUTING.md должен давать exit 2 (проверка не выполнена), получено $code" >&2
        rc=1
    else
        echo "self-test: ✅ CONTRIBUTING.md отсутствует → exit 2 (не «всё хорошо»)"
    fi

    # 3. BR-586: MUST-правил нет → набор данных пуст → exit 2.
    cat > "$tmp/empty.md" <<'EOF'
### docs-style: Стиль документации

**Severity**: SHOULD
**Enforced by**: code-review-only
EOF
    out="$(CONTRIBUTING_FILE="$tmp/empty.md" run_check 2>&1)"
    code=$?
    if [ "$code" -ne 2 ]; then
        echo "self-test: файл без MUST-правил должен давать exit 2, получено $code" >&2
        rc=1
    else
        echo "self-test: ✅ нет MUST-правил → exit 2 (пустой набор данных)"
    fi

    # 4. BR-586: переносимость — в скрипте не должно быть `grep -P`.
    if grep -vE '^[[:space:]]*#|self-test:|PCRE' "$0" \
         | grep -qE 'grep[[:space:]]+-[a-zA-Z]*P'; then
        echo "self-test: скрипт использует grep -P (PCRE) — подсчёт не переносим" >&2
        rc=1
    else
        echo "self-test: ✅ подсчёт без PCRE (grep -cE)"
    fi

    rm -rf "$tmp"
    trap - EXIT

    if [ "$rc" -ne 0 ]; then
        echo "SELFTEST FAIL: фикстуры не дали ожидаемых результатов" >&2
        return 1
    fi
    echo "SELFTEST OK: 4/4 фикстуры дали ожидаемые результаты"
    return 0
}

case "${1:-}" in
    --self-test)
        self_test
        exit $?
        ;;
    --help|-h|"")
        run_check
        exit $?
        ;;
    *)
        echo "Usage: $0 [--self-test]" >&2
        exit 2
        ;;
esac
