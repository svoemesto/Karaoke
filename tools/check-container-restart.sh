#!/usr/bin/env bash
# =====================================================================
# tools/check-container-restart.sh — guard для правила NON-NEGOTIABLE
# о перезапуске контейнеров (см. AGENTS.md § «Перезапуск контейнеров
# через deploy/do.sh (Pass 374)»).
#
# Прецедент (Pass 374, OP #83 follow-up): правила о перезапуске были
# разбросаны по 3 местам и не machine-readable. Агент мог случайно
# использовать прямые `docker restart` / `docker stop` + `docker start`
# для контейнеров, которые требуют согласия (karaoke-app).
#
# Этот guard ловит **в git diff HEAD** попытки закоммитить прямые вызовы
# docker restart/stop/start контейнеров Karaoke.
#
# Использование:
#   bash tools/check-container-restart.sh                  # guard-режим
#   bash tools/check-container-restart.sh --quiet          # для pre-commit
#   bash tools/check-container-restart.sh --self-test      # фикстуры «до/после»
#
# Что проверяет:
#   В git diff HEAD (staged + unstaged) ищет запрещённые паттерны:
#     - `docker restart <container>` где container в KARAOKE_CONTAINERS
#       (включая вариант с опциями: `docker restart -t 30 <container>`)
#     - `docker stop <container>` для контейнеров, требующих consent
#     - `docker start <container>` (обход do.sh, вкл. вариант с опциями)
#     - `docker-compose ... restart ...` И `docker compose ... restart ...`
#       (обе формы — v1 через дефис и v2 через пробел; минует do.sh)
#   Игнорирует строки внутри markdown (blockquotes `>` и codeblocks).
#
# Exit codes:
#   0 — запрещённых паттернов не найдено
#   1 — найдены нарушения
#   2 — usage error
#
# BR-575 (аудит #336, пакет PKG-S3-30): проверка `docker-compose … restart`
# находилась ВНУТРИ цикла по контейнерам, хотя контейнер в ней не участвует.
# Одна запрещённая строка в diff давала 13 нарушений («Найдено нарушений: 13»).
# Теперь паттерн без контейнера проверяется ОДИН РАЗ на файл, а счётчик
# показывает реальное число нарушений.
# =====================================================================

set -uo pipefail

# Счётчик нарушений, заполняемый count_violations (BR-575).
VIOLATION_COUNT=0

# Список контейнеров Karaoke.
KARAOKE_CONTAINERS=(
  "karaoke-db"
  "karaoke-web"
  "karaoke-webvue"
  "karaoke-webvue3"
  "karaoke-public"
  "karaoke-app"
  "karaoke-minio"
  "karaoke-minio-proxy"
  "karaoke-storage"
  "karaoke-telegram-proxy"
  "karaoke-nginx"
  "karaoke-db-local"
  "nginx"
)

# Контейнеры, требующие явного согласия (Pass 282):
CONSENT_REQUIRED=(
  "karaoke-app"
  "minio"
  "nginx"
  "karaoke-nginx"
)

color_red() { printf '[0;31m%s[0m\n' "$*"; }
color_green() { printf '[0;32m%s[0m\n' "$*"; }
color_yellow() { printf '[0;33m%s[0m\n' "$*"; }

check_restart_patterns() {
  # Получаем список ИЗМЕНЁННЫХ файлов (staged + unstaged + untracked-new).
  # Проверяем ТОЛЬКО shell-исполняемые файлы (.sh/.bash/.zsh + shell-блоки в
  # build/ci конфигах). Markdown (.md) и ADR-файлы НЕ проверяем — там могут
  # быть примеры запрещённых команд в документации.
  local changed_files
  changed_files="$( (git diff --cached --name-only; git diff --name-only; git ls-files --others --exclude-standard) 2>/dev/null | sort -u )"

  # Фильтруем только .sh/.bash файлы + конфиги (deploy/do.sh, Jenkinsfile, .github/workflows/*.yml).
  # Исключаем сам guard-скрипт (он по определению содержит примеры нарушений в комментариях/error messages).
  local checkable_files=()
  while IFS= read -r f; do
    if [[ -z "$f" ]]; then continue; fi
    if [[ "$f" == "tools/check-container-restart.sh" ]]; then continue; fi
    if [[ "$f" =~ \.(sh|bash|zsh)$ ]] || [[ "$f" =~ ^deploy/do\.sh$ ]] || [[ "$f" =~ ^\.github/workflows/.+\.yml$ ]]; then
      checkable_files+=("$f")
    fi
  done <<< "$changed_files"

  if [[ ${#checkable_files[@]} -eq 0 ]]; then
    color_green "✅ Нет shell-файлов в изменении (markdown и ADR не проверяются)"
    return 0
  fi

  local violation_count=0
  local violation_files=""

  for f in "${checkable_files[@]}"; do
    # Получаем содержимое добавленных строк (`+` в начале).
    local added_lines
    if [[ -f "$f" ]] && git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      added_lines="$(git diff HEAD -- "$f" 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+' || true)"
    elif [[ -f "$f" ]]; then
      # Untracked файл — проверяем всё содержимое как "+".
      added_lines="$(cat "$f" | sed 's/^/+/')"
    fi

    count_violations "$f" "$added_lines"
    local file_violations="${VIOLATION_COUNT}"
    if [[ "$file_violations" -gt 0 ]]; then
      violation_count=$((violation_count + file_violations))
      violation_files="${violation_files} ${f}"
    fi
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ Запрещённых паттернов перезапуска не найдено в shell-файлах"
    return 0
  else
    color_red "Найдено нарушений: $violation_count (файлы:$violation_files)"
    color_yellow "См. AGENTS.md § «Перезапуск контейнеров через deploy/do.sh (Pass 374)»."
    return 1
  fi
}

# =====================================================================
# count_violations <метка-файла> <добавленные-строки>
#   Печатает сообщения о нарушениях; число нарушений кладёт в VIOLATION_COUNT.
#   $2 — строки с префиксом '+' (как в git diff).
#
# BR-575: паттерн «docker-compose … restart» НЕ использует $container,
# поэтому он вынесен из цикла по контейнерам и считается ОДИН РАЗ на файл.
# Раньше он был внутри цикла → одна запрещённая строка давала 13 нарушений.
# =====================================================================
count_violations() {
  local label="$1"
  local added_lines="$2"
  local count=0
  local container

  for container in "${KARAOKE_CONTAINERS[@]}"; do
    # Паттерн 1: `docker restart <container>`.
    # В т.ч. с опциями между глаголом и именем: `docker restart -t 30 <c>`.
    if echo "$added_lines" | grep -qE "^\\+.*\\bdocker\\s+restart\\s+(-[^\\s]+\\s+)*${container}\\b" 2>/dev/null; then
      color_red "❌ НАРУШЕНИЕ: $label содержит 'docker restart $container'"
      color_yellow "   Правило (Pass 374): используй bash deploy/do.sh restart_* (см. AGENTS.md § Перезапуск контейнеров)"
      count=$((count + 1))
    fi

    # Паттерн 2: `docker stop <container>` для контейнеров, требующих согласия.
    if [[ " ${CONSENT_REQUIRED[@]} " =~ " ${container} " ]]; then
      if echo "$added_lines" | grep -qE "^\\+.*\\bdocker\\s+stop\\s+${container}\\b" 2>/dev/null; then
        color_red "❌ НАРУШЕНИЕ: $label содержит 'docker stop $container'"
        color_yellow "   Правило (Pass 282): требуется явное согласие пользователя для $container"
        count=$((count + 1))
      fi
    fi

    # Паттерн 2b: `docker start <container>` — обход deploy/do.sh (заявлен
    # в шапке guard'а, но ранее не проверялся). Опции после глагола
    # допускаются: `docker start -a <c>`.
    if echo "$added_lines" | grep -qE "^\\+.*\\bdocker\\s+start\\s+(-[^\\s]+\\s+)*${container}\\b" 2>/dev/null; then
      color_red "❌ НАРУШЕНИЕ: $label содержит 'docker start $container'"
      color_yellow "   Правило (Pass 374): используй bash deploy/do.sh start_* (см. AGENTS.md § Перезапуск контейнеров)"
      count=$((count + 1))
    fi
  done

  # Паттерн 3: `docker-compose ... restart ...` (v1, через дефис) И
  # `docker compose ... restart ...` (v2, через пробел) — обе формы
  # минуют do.sh и должны ловиться (BR-574).
  # BR-575: паттерн без контейнера → ОДИН раз на файл, не по разу на контейнер.
  if echo "$added_lines" | grep -qE "^\\+.*\\bdocker(-|\\s+)compose\\b.*\\brestart\\b" 2>/dev/null; then
    color_red "❌ НАРУШЕНИЕ: $label содержит 'docker-compose/docker compose ... restart'"
    color_yellow "   Правило (Pass 374): используй bash deploy/do.sh restart_* вместо прямого docker-compose"
    count=$((count + 1))
  fi

  VIOLATION_COUNT="${count}"
}

# =====================================================================
# Самопроверка (фикстуры «до/после»)
# =====================================================================
self_test() {
  local rc=0
  local n

  # 1. Чистый контент → 0 нарушений.
  count_violations "fixtures/clean.sh" "+bash deploy/do.sh restart_karaoke-app
+cd webvue3 && npm run build"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "0" ]]; then
    echo "self-test: чистый контент должен давать 0 нарушений, получено $n" >&2
    rc=1
  else
    echo "self-test: ✅ чистый контент → 0 нарушений"
  fi

  # 2. `docker restart karaoke-app` → ровно 1 нарушение.
  count_violations "fixtures/restart.sh" "+docker restart karaoke-app"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "1" ]]; then
    echo "self-test: 'docker restart karaoke-app' должен давать 1 нарушение, получено $n" >&2
    rc=1
  else
    echo "self-test: ✅ docker restart → 1 нарушение"
  fi

  # 3. BR-575: `docker-compose … restart` → ровно 1 нарушение (НЕ 13).
  count_violations "fixtures/compose.sh" "+docker-compose -f deploy/docker-compose.yml restart"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "1" ]]; then
    echo "self-test (BR-575): 'docker-compose … restart' должен давать 1 нарушение, получено $n (счётчик умножается на число контейнеров)" >&2
    rc=1
  else
    echo "self-test: ✅ docker-compose … restart → 1 нарушение (BR-575, без умножения на 13)"
  fi

  # 4. BR-575: `docker compose … restart` (v2, через пробел) → 1 нарушение.
  count_violations "fixtures/compose2.sh" "+docker compose -f deploy/docker-compose.yml restart"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "1" ]]; then
    echo "self-test (BR-575): 'docker compose … restart' должен давать 1 нарушение, получено $n" >&2
    rc=1
  else
    echo "self-test: ✅ docker compose … restart → 1 нарушение (BR-575)"
  fi

  # 5. `docker stop karaoke-app` (требует согласия) → 1 нарушение.
  count_violations "fixtures/stop.sh" "+docker stop karaoke-app"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "1" ]]; then
    echo "self-test: 'docker stop karaoke-app' должен давать 1 нарушение, получено $n" >&2
    rc=1
  else
    echo "self-test: ✅ docker stop (consent-required) → 1 нарушение"
  fi

  # 6. `docker start karaoke-app` → 1 нарушение.
  count_violations "fixtures/start.sh" "+docker start karaoke-app"
  n="${VIOLATION_COUNT}"
  if [[ "$n" != "1" ]]; then
    echo "self-test: 'docker start karaoke-app' должен давать 1 нарушение, получено $n" >&2
    rc=1
  else
    echo "self-test: ✅ docker start → 1 нарушение"
  fi

  if [[ $rc -ne 0 ]]; then
    echo "SELFTEST FAIL: фикстуры не дали ожидаемых счётчиков" >&2
    return 1
  fi
  echo "SELFTEST OK: 6/6 фикстур дали ожидаемые счётчики нарушений"
  return 0
}

case "${1:-}" in
  --help|-h|"")
    check_restart_patterns
    exit $?
    ;;
  --quiet)
    check_restart_patterns >/dev/null 2>&1
    exit $?
    ;;
  --self-test)
    self_test
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-container-restart.sh [--quiet|--self-test]"
    exit 2
    ;;
esac