#!/usr/bin/env bash
# =====================================================================
# tools/check-log-hygiene.sh — KARAOKE-387: не плодить новые стектрейсы в логах
# =====================================================================
#
# Зачем. Логи `karaoke-app` исторически печатают Java-стектрейсы двумя способами:
#   * `e.printStackTrace()` — прямой вывод стека в stdout;
#   * `log.warn(...)`/`log.error(...)` с объектом исключения последним аргументом —
#     SLF4J печатает стек после сообщения.
#
# Инцидент 2026-11-02: вокруг одной ошибки БД в логе появлялись десятки строк
# `at org.springframework...`, а полезное (код, операция, хост) отсутствовало. Задача
# KARAOKE-387 ввела `AppLog` (одна строка `event=… code=… key=value … cause=…`, стек
# только при `LOG_STACKTRACES=true`).
#
# Этот guard останавливает рост: он НЕ требует немедленно переписать весь код (это
# отдельные проходы), но запрещает появление новых источников шума.
#
# Модель — как у eslint-baseline: в `config/log-hygiene-baseline.txt` лежат текущие
# счётчики по файлам. Проверка падает, если:
#   * появился файл с паттернами, которого нет в baseline;
#   * в существующем файле счётчик вырос.
# Уменьшение счётчика — не ошибка (но baseline стоит обновить: `--update-baseline`).
#
# Использование:
#   bash tools/check-log-hygiene.sh                  # проверка (exit 1 при регрессе)
#   bash tools/check-log-hygiene.sh --update-baseline # пересобрать baseline
#   bash tools/check-log-hygiene.sh --report          # только показать счётчики
#
# Область: `karaoke-app/src/main/kotlin` (там живут фоновые задачи и API админки).
# =====================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCOPE="${ROOT}/karaoke-app/src/main/kotlin"
BASELINE="${ROOT}/config/log-hygiene-baseline.txt"

MODE="check"
case "${1:-}" in
  --update-baseline) MODE="update" ;;
  --report) MODE="report" ;;
  "") MODE="check" ;;
  *)
    echo "Использование: $0 [--update-baseline|--report]" >&2
    exit 2
    ;;
esac

if [ ! -d "${SCOPE}" ]; then
  echo "ОШИБКА: не найден каталог ${SCOPE}" >&2
  exit 2
fi

# Счётчики по файлу: "printstacktrace logexc" (две эвристики, см. заголовок).
count_file() {
  local file="$1"
  local ps logexc
  ps=$(grep -c "printStackTrace" "${file}" 2>/dev/null || true)
  # log.<level>(...) с исключением последним аргументом. Эвристика по строке вызова:
  # ", e)" / ", ex)" / ", err)" / ", throwable)". Многострочные вызовы ловятся, когда
  # аргумент стоит в той же строке; остальные — предметом отдельных проходов.
  logexc=$(grep -cE "log\.(trace|debug|info|warn|error)\([^)]*,[[:space:]]*(e|ex|err|exception|throwable|cause|t)\)" "${file}" 2>/dev/null || true)
  printf '%s %s\n' "${ps:-0}" "${logexc:-0}"
}

declare -A CURRENT_PS=()
declare -A CURRENT_LEXC=()

while IFS= read -r -d '' file; do
  read -r ps logexc < <(count_file "${file}")
  if [ "${ps}" -gt 0 ] || [ "${logexc}" -gt 0 ]; then
    rel="${file#"${ROOT}/"}"
    CURRENT_PS["${rel}"]="${ps}"
    CURRENT_LEXC["${rel}"]="${logexc}"
  fi
done < <(find "${SCOPE}" -name '*.kt' -print0)

print_report() {
  printf '%-70s %8s %8s\n' "файл" "pst" "logexc"
  for rel in $(printf '%s\n' "${!CURRENT_PS[@]}" | sort); do
    printf '%-70s %8s %8s\n' "${rel}" "${CURRENT_PS[${rel}]}" "${CURRENT_LEXC[${rel}]}"
  done
}

if [ "${MODE}" = "report" ]; then
  print_report
  exit 0
fi

if [ "${MODE}" = "update" ]; then
  mkdir -p "$(dirname "${BASELINE}")"
  {
    echo "# config/log-hygiene-baseline.txt — KARAOKE-387"
    echo "# Текущие счётчики стектрейсов в karaoke-app/src/main/kotlin (baseline, как eslint)."
    echo "# Формат: <путь> <printStackTrace> <лог-с-исключением>"
    echo "# Пересобрать: bash tools/check-log-hygiene.sh --update-baseline"
    echo "# Правило: счётчики могут только уменьшаться; новый файл с паттернами — ошибка."
    for rel in $(printf '%s\n' "${!CURRENT_PS[@]}" | sort); do
      echo "${rel} ${CURRENT_PS[${rel}]} ${CURRENT_LEXC[${rel}]}"
    done
  } >"${BASELINE}"
  echo "baseline обновлён: ${BASELINE#"${ROOT}/"} (${#CURRENT_PS[@]} файлов)"
  exit 0
fi

# --- проверка -----------------------------------------------------------------
if [ ! -f "${BASELINE}" ]; then
  echo "ОШИБКА: нет baseline ${BASELINE} — соберите: bash tools/check-log-hygiene.sh --update-baseline" >&2
  exit 2
fi

declare -A BASE_PS=()
declare -A BASE_LEXC=()
while read -r rel ps logexc; do
  case "${rel}" in ""|\#*) continue ;; esac
  BASE_PS["${rel}"]="${ps}"
  BASE_LEXC["${rel}"]="${logexc}"
done <"${BASELINE}"

fail=0
for rel in $(printf '%s\n' "${!CURRENT_PS[@]}" | sort); do
  cur_ps="${CURRENT_PS[${rel}]}"
  cur_lexc="${CURRENT_LEXC[${rel}]}"
  if [ -z "${BASE_PS[${rel}]+x}" ]; then
    echo "❌ НОВЫЙ источник стектрейсов: ${rel} (printStackTrace=${cur_ps}, log+exception=${cur_lexc})"
    echo "   Используйте AppLog (одна строка с кодом, см. knowledge/guidelines/runbooks/log-correlation.md §1a)."
    fail=1
    continue
  fi
  if [ "${cur_ps}" -gt "${BASE_PS[${rel}]}" ]; then
    echo "❌ ${rel}: printStackTrace вырос ${BASE_PS[${rel}]} → ${cur_ps}"
    fail=1
  fi
  if [ "${cur_lexc}" -gt "${BASE_LEXC[${rel}]}" ]; then
    echo "❌ ${rel}: лог с исключением вырос ${BASE_LEXC[${rel}]} → ${cur_lexc}"
    fail=1
  fi
done

if [ "${fail}" -ne 0 ]; then
  echo ""
  echo "Итог: регресс по гигиене логов (KARAOKE-387)."
  echo "Правило: новые ошибки — через AppLog (event=/code=/cause= без стека); стек — только LOG_STACKTRACES=true."
  echo "Если правка уменьшила счётчики — обновите baseline: bash tools/check-log-hygiene.sh --update-baseline"
  exit 1
fi

echo "OK: гигиена логов не ухудшилась (файлов в baseline: ${#BASE_PS[@]}, с паттернами сейчас: ${#CURRENT_PS[@]})"
exit 0
