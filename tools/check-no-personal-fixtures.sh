#!/usr/bin/env bash
# =====================================================================
# tools/check-no-personal-fixtures.sh — guard против «бытовых» файлов,
# которые однажды уже попадали в боевой jar.
#
# Прецедент (PKG-S3-26, OpenProject #398, BR-526 / BR-531):
#   - `karaoke-app/src/main/resources/test.xml` — образец запроса ЕГРН с
#     ПДН-подобными полями (адреса, объекты). Файл жил в `src/main/resources`
#     → **попадал в `BOOT-INF/classes/` каждого боевого jar**;
#   - четыре мёртвые MLT-фикстуры (`1`, `2.xml`, `Неправильный.xml`,
#     `Правильный.xml`) в `src/test/kotlin` — с абсолютными личными путями
#     `~/Documents/Караоке/…`.
#   Оба файла удалены из репозитория, но **ничего не мешало добавить их снова**:
#   guard на «личные пути» и на «PII в ресурсах» отсутствовал.
#
# Что проверяет:
#   1. PII-подобный образец ЕГРН (`EGRNRequest`, `rosreestr.ru/services`)
#      в каталогах ресурсов `*/src/main/resources/**` и `*/src/test/resources/**`
#      — то есть в том, что уезжает в jar или в тестовые ресурсы сборки.
#   2. Личные локальные пути (`/home/…`, `~/Documents/…`, `C:\Users\…`)
#      в tracked-файлах-фикстурах:
#        - `*.mlt`, `*.kdenlive` (трекеры видео/аудио — MLT);
#        - любые файлы внутри `*/src/main/resources/**` и `*/src/test/resources/**`.
#
# НЕ проверяется (осознанно):
#   - документация (`docs/`, `knowledge/`, `specs/`, `README`, `*.md`) — там
#     эти строки нужны как описание самого дефекта;
#   - исходный код (`*.kt`, `*.js`, `*.vue`) — личный путь в коде — другой
#     класс дефекта (непереносимость), его ловит ревью;
#   - `.idea/`, `.github/` — там пути машин разработчиков допустимы.
#
# Baseline — по СОДЕРЖИМОМУ строки, НЕ по номеру строки (Pass 455):
#   ключ базы = <путь>:sha256(trim(содержимого строки))[:16].
#   Вставка строк выше упоминания базу не ломает; ИЗМЕНЕНИЕ содержимого
#   упоминания — ломает (это новое решение, его должен посмотреть человек).
#
# Использование:
#   bash tools/check-no-personal-fixtures.sh                   # guard-режим
#   bash tools/check-no-personal-fixtures.sh --quiet           # для pre-commit
#   bash tools/check-no-personal-fixtures.sh --update-baseline # пересобрать базу
#
# Exit codes:
#   0 — нарушений нет (после baseline)
#   1 — найдены нарушения
#   2 — usage error
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT" || exit 2

BASELINE_FILE="${PROJECT_ROOT}/tools/check-no-personal-fixtures.baseline"
HASH_LEN=16
QUIET=0
UPDATE_BASELINE=0

for arg in "$@"; do
  case "$arg" in
    --quiet) QUIET=1 ;;
    --update-baseline) UPDATE_BASELINE=1 ;;
    -h|--help) sed -n '2,60p' "$0"; exit 0 ;;
    *) echo "usage error: $arg" >&2; exit 2 ;;
  esac
done

# --- что считаем нарушением -------------------------------------------
# Паттерны PII-образца (только в каталогах ресурсов).
PII_PATTERN='EGRNRequest|rosreestr\.ru/services'
# Паттерны личных локальных путей (в фикстурах).
PERSONAL_PATTERN='/home/|~/Documents/|[A-Za-z]:\\\\Users\\\\'

# Каталоги ресурсов.
RESOURCE_DIRS=(
  "karaoke-app/src/main/resources"
  "karaoke-web/src/main/resources"
  "karaoke-app/src/test/resources"
  "karaoke-web/src/test/resources"
)

# Файлы-фикстуры, которые ищем по всему репозиторию.
FIXTURE_GLOBS=("*.mlt" "*.kdenlive")

tmp_list="$(mktemp)"
tmp_hits="$(mktemp)"
cleanup() { rm -f "$tmp_list" "$tmp_hits"; }
trap cleanup EXIT

# 1) PII-образец в каталогах ресурсов.
for dir in "${RESOURCE_DIRS[@]}"; do
  [ -d "$dir" ] || continue
  git ls-files -- "$dir" >>"$tmp_list"
done

# 2) Фикстуры по расширениям (по всему репозиторию).
for glob in "${FIXTURE_GLOBS[@]}"; do
  git ls-files -- "*${glob}" >>"$tmp_list"
done

# 3) Уникализируем список, убираем сам guard.
sort -u "$tmp_list" | grep -v '^tools/check-no-personal-fixtures' >"$tmp_list.u" && mv "$tmp_list.u" "$tmp_list"

while IFS= read -r file; do
  [ -n "$file" ] || continue
  [ -f "$file" ] || continue
  if [[ "$file" == */src/main/resources/* || "$file" == */src/test/resources/* ]]; then
    pattern="$PII_PATTERN"
  else
    pattern="$PERSONAL_PATTERN"
  fi
  # grep ОДИН раз на файл; хеш считается только для совпавших строк —
  # построчный цикл с двумя подпроцессами на строку давал минуты на
  # больших min.js в ресурсах.
  grep -nE "$pattern" "$file" 2>/dev/null | while IFS= read -r hit; do
    line="${hit#*:}"
    hash="$(printf '%s' "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sha256sum | cut -c1-${HASH_LEN})"
    printf '%s:%s\t%s\n' "$file" "$hash" "$line" >>"$tmp_hits"
  done
done <"$tmp_list"

total="$(wc -l <"$tmp_hits" | tr -d ' ')"

if [ "$UPDATE_BASELINE" = "1" ]; then
  cut -f1,2 "$tmp_hits" | sort -u >"$BASELINE_FILE"
  echo "baseline обновлён: $BASELINE_FILE ($(wc -l <"$BASELINE_FILE" | tr -d ' ') записей)"
  exit 0
fi

# --- фильтрация по baseline -------------------------------------------
violations=0
if [ -f "$BASELINE_FILE" ]; then
  while IFS=$'\t' read -r key line; do
    [ -n "$key" ] || continue
    if ! grep -Fxq "$key" "$BASELINE_FILE"; then
      echo "НАРУШЕНИЕ: $key"
      echo "    $line"
      violations=$((violations + 1))
    fi
  done <"$tmp_hits"
else
  while IFS=$'\t' read -r key line; do
    [ -n "$key" ] || continue
    echo "НАРУШЕНИЕ: $key"
    echo "    $line"
    violations=$((violations + 1))
  done <"$tmp_hits"
fi

if [ "$violations" -gt 0 ]; then
  echo "ОТКАЗ: $violations нарушений (проверено совпадений: $total)"
  echo "Подробности: BR-526 (PII-образец в ресурсах), BR-531 (личные пути в фикстурах)."
  exit 1
fi

if [ "$QUIET" = "1" ]; then
  echo "OK: личных фикстур и PII-образцов нет (совпадений с учётом baseline: $total)."
else
  echo "OK: личных фикстур и PII-образцов нет (совпадений с учётом baseline: $total)."
fi
exit 0
