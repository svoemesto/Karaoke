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
#
# ВАЖНО: комментарии не считаются. Упоминание «printStackTrace» в комментарии («BR-165:
# printStackTrace() уходил в stderr…») — это документация, а не источник стектрейсов;
# без такой фильтрации baseline не может обнулиться и перестаёт быть полезным.
# Подсчёт ведёт tools/log_hygiene_scan.py (спека 732): прежняя регулярка по одной
# строке видела только логгер, названный ровно `log` (в коде их нет — все `*Log`),
# и пропускала многострочные вызовы и вызовы со скобками в аргументах
# (`consumerLog.warn("… {}", Instant.now(), e)`). Поэтому baseline был пустым, а
# стектрейсы в логах шли потоком (инцидент 2026-11-08).
#
# Сканер разбирает вызов по скобкам, снимает комментарии и строковые литералы и
# считает вызовы логгера, у которых ПОСЛЕДНИЙ аргумент — переменная исключения.
count_file() {
  local file="$1"
  local ps logexc
  ps=$(strip_comments "${file}" | grep -c "printStackTrace" || true)
  logexc=$(python3 "${ROOT}/tools/log_hygiene_scan.py" "${file}" | awk '{print $3}')
  printf '%s %s\n' "${ps:-0}" "${logexc:-0}"
}

# Печатает файл без комментариев (строковые литералы не разбираются — достаточно).
strip_comments() {
  local file="$1"
  sed -e 's://.*::' -e 's:^[[:space:]]*\*.*::' -e 's:^[[:space:]]*/\*.*::' "${file}"
}

declare -A CURRENT_PS=()
declare -A CURRENT_LEXC=()

# Один проход сканера по всему каталогу (сканер сам снимает комментарии и
# строковые литералы и разбирает вызов по скобкам).
while read -r file ps logexc; do
  [ -n "${file:-}" ] || continue
  rel="${file#"${ROOT}/"}"
  CURRENT_PS["${rel}"]="${ps}"
  CURRENT_LEXC["${rel}"]="${logexc}"
done < <(python3 "${ROOT}/tools/log_hygiene_scan.py" "${SCOPE}")

print_report() {
  printf '%-70s %8s %8s\n' "файл" "pst" "logexc"
  python3 "${ROOT}/tools/log_hygiene_scan.py" "${SCOPE}" | sort | while read -r rel ps logexc; do
    printf '%-70s %8s %8s\n' "${rel#"${ROOT}/"}" "${ps}" "${logexc}"
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
