#!/usr/bin/env bash
# =====================================================================
# tools/check-secret-in-argv.sh — guard «секреты не передаются через argv»
# =====================================================================
# Зачем: секрет в argv виден ЛЮБОМУ локальному пользователю машины
# (`ps -eo args`, `/proc/<pid>/cmdline`). Пароль/токен в аргументах
# команды — это утечка в список процессов.
#
# Прецедент (аудит #336, OpenProject #382, пакет PKG-SEC-SECRET-ARGV):
#   BR-551 deploy/web-server-deploy/deploy/do.sh:37  — `docker login --password ${DOCKER_PASSWORD}`
#   BR-552 deploy/tracker-db-backup.sh:73-84          — `docker exec -e PGPASSWORD=…`
#   BR-605 tools/tracker-lib.sh:161-182               — `curl -H "Authorization: Basic …"`
#   (тот же корень в tools/tracker-smoke-test.sh:89 — `curl -u apikey:$TOKEN`)
#
# Что считается нарушением (правила — id|контекст|основной regex|сообщение):
#   1. docker-exec-env-password — `-e PGPASSWORD=…` / `--env PGPASSWORD=…`
#   2. password-flag-value     — `--password <значение>` (не `--password-stdin`)
#   3. header-authorization-flag— `-H "Authorization: …"` (токен в argv)
#   4. user-password-flag      — `-u user:pass` (креды в argv)
#   5. conn-string-password    — `password=…` в строке подключения (psql/mysql)
#   6. url-inline-password     — `postgres://user:pass@host`
#
# Что НЕ является нарушением (безопасные формы):
#   - `PGPASSWORD=… pg_dump …`  — переменная окружения процесса (не argv);
#   - `--password-stdin`         — пароль подаётся на stdin;
#   - `curl --config -` c `header = "Authorization: …"` — заголовок уходит в stdin;
#   - `docker exec --env-file <файл>` — в argv путь файла, не секрет;
#   - `.pgpass`/`PGPASSFILE`     — пароль в файле 0600 / через env.
#
# Baseline — по СОДЕРЖИМОМУ строки, НЕ по номеру строки (конвенция Pass 455,
# как в tools/check-no-mp4-mentions.sh): вставка строк выше не ломает базу,
# изменение текста секрета — ломает (намеренно).
#
# Использование:
#   bash tools/check-secret-in-argv.sh                    # guard-режим
#   bash tools/check-secret-in-argv.sh --quiet            # для pre-commit
#   bash tools/check-secret-in-argv.sh --self-test        # проверка самих правил
#   bash tools/check-secret-in-argv.sh --update-baseline  # перегенерировать базу
#
# Exit codes:
#   0 — нарушений нет (после baseline)
#   1 — найдены нарушения
#   2 — usage error / self-test failed
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE_FILE="${PROJECT_ROOT}/tools/check-secret-in-argv.baseline"
SELF_FILE="${PROJECT_ROOT}/tools/check-secret-in-argv.sh"

# Длина префикса sha256 в ключе (как в check-no-mp4-mentions.sh).
HASH_LEN=16

# Правила заданы четырьмя параллельными массивами (id / контекст / regex /
# сообщение) — разбор по разделителю невозможен: сами регулярки содержат '|'.
# Совпадение = контекст И основной regex на одной строке кода.
# Строки-комментарии (первый непробельный символ — '#') не проверяются:
# в комментарии дефект не исполняется.
RULE_IDS=(
  'docker-exec-env-password'
  'password-flag-value'
  'header-authorization-flag'
  'user-password-flag'
  'conn-string-password'
  'url-inline-password'
)
RULE_CTX=(
  ''
  ''
  ''
  ''
  '(psql|postgres|mysql|host=|dbname=)'
  ''
)
RULE_MAIN=(
  '(-e|--env)[[:space:]]+PGPASSWORD='
  '--password(=[^[:space:]]+|[[:space:]]+[^[:space:]]+)'
  '(-H|--header)[[:space:]]+"?Authorization:'
  '(-u|--user)[[:space:]]+"?[A-Za-z0-9_.$-]+:[^[:space:]"]*'
  "password=[^'\"[:space:]][^[:space:]]*"
  "(postgres|postgresql|mysql|mongodb)://[^[:space:]\"']*:[A-Za-z0-9._\$@%+/=-]*[^[:space:]\"'@]@"
)
RULE_MSG=(
  'пароль БД передан в argv docker exec (-e PGPASSWORD=…) — передавать через stdin, --env-file или переменную окружения'
  'пароль передан в argv (--password …) — использовать --password-stdin'
  'заголовок Authorization с токеном передан в argv (-H …) — передавать через stdin (curl --config -) или файл'
  'креды переданы в argv (-u user:pass) — передавать через stdin или переменную окружения'
  'пароль в строке подключения передан в argv (password=…) — передавать через PGPASSWORD/.pgpass/stdin'
  'пароль в URL передан в argv (scheme://user:pass@host) — передавать через переменную окружения или .pgpass'
)
RULE_COUNT=${#RULE_IDS[@]}

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

trim() {
  local s="$1"
  s="${s#"${s%%[![:space:]]*}"}"
  s="${s%"${s##*[![:space:]]}"}"
  printf '%s' "$s"
}

# Ключ baseline для пары (путь, содержимое строки) — по содержимому, не по номеру.
baseline_key() {
  local file="$1"
  local content="$2"
  local hash
  hash="$(trim "$content" | sha256sum | cut -c1-"$HASH_LEN")"
  printf '%s:%s' "$file" "$hash"
}

declare -A BASELINE_SET=()
BASELINE_COUNT=0

load_baseline() {
  BASELINE_SET=()
  BASELINE_COUNT=0
  [[ -f "$BASELINE_FILE" ]] || return 0
  local raw key
  while IFS= read -r raw; do
    # Ключ идёт до первого '#': после него — человекочитаемый сниппет-комментарий.
    key="$(trim "${raw%%#*}")"
    [[ -z "$key" ]] && continue
    BASELINE_SET["$key"]=1
    BASELINE_COUNT=$((BASELINE_COUNT + 1))
  done < "$BASELINE_FILE"
}

# Список отслеживаемых shell-файлов (tracked). Сам guard исключён.
collect_files() {
  ALL_FILES=()
  local f rel
  while IFS= read -r f; do
    [[ -z "$f" ]] && continue
    rel="${f#"$PROJECT_ROOT"/}"
    [[ "$rel" == "$SELF_FILE" ]] && continue
    [[ "$rel" == "tools/check-secret-in-argv.sh" ]] && continue
    ALL_FILES+=("$rel")
  done < <(cd "$PROJECT_ROOT" && git ls-files -z -- '*.sh' '*.bash' 2>/dev/null | tr '\0' '\n')
}

# Сканирование файлов. Совпадения пишутся в RAW_HITS в формате
# <file>:<line>:<rule_id>:<content>.
# Матчинг — встроенным ERE-движком bash (без внешнего grep: паттерны,
# начинающиеся с '-', нельзя передавать grep как опцию).
collect_hits() {
  RAW_HITS=()
  [[ ${#ALL_FILES[@]} -eq 0 ]] && return 0
  local file lineno content i context main
  for file in "${ALL_FILES[@]}"; do
    [[ -f "$PROJECT_ROOT/$file" ]] || continue
    lineno=0
    while IFS= read -r content || [[ -n "$content" ]]; do
      lineno=$((lineno + 1))
      [[ -z "$content" ]] && continue
      # Строки-комментарии не проверяем: в комментарии дефект не исполняется.
      [[ "$content" =~ ^[[:space:]]*# ]] && continue
      for ((i = 0; i < RULE_COUNT; i++)); do
        context="${RULE_CTX[$i]}"
        main="${RULE_MAIN[$i]}"
        if [[ -n "$context" && ! "$content" =~ $context ]]; then
          continue
        fi
        if [[ "$content" =~ $main ]]; then
          RAW_HITS+=("$file:$lineno:${RULE_IDS[$i]}:$content")
        fi
      done
    done < "$PROJECT_ROOT/$file"
  done
}

# ---------------------------------------------------------------------
# Режим перегенерации baseline.
# ---------------------------------------------------------------------
update_baseline() {
  cd "$PROJECT_ROOT" || return 2
  collect_files
  collect_hits

  local -A seen=()
  local out=()
  local hit file lineno rule content key snippet
  for hit in "${RAW_HITS[@]:-}"; do
    [[ -z "$hit" ]] && continue
    file="${hit%%:*}"
    local rest="${hit#*:}"
    lineno="${rest%%:*}"
    rest="${rest#*:}"
    rule="${rest%%:*}"
    content="${rest#*:}"
    key="$(baseline_key "$file" "$content")"
    [[ -n "${seen["$key"]:-}" ]] && continue
    seen["$key"]=1
    snippet="$(trim "$content")"
    [[ ${#snippet} -gt 120 ]] && snippet="${snippet:0:117}..."
    out+=("$key  # [$rule] $snippet")
  done

  {
    echo "# Baseline для tools/check-secret-in-argv.sh (секреты не в argv)."
    echo "#"
    echo "# Формат: <путь>:<sha256(trim(содержимого строки))[:$HASH_LEN]>  # [<rule>] <сниппет>"
    echo "# Всё после '#' — комментарий-сниппет, на сопоставление не влияет."
    echo "#"
    echo "# Номер строки НЕ используется (конвенция Pass 455): вставка строк выше"
    echo "# базу не ломает; изменение текста секрета — ломает (намеренно)."
    echo "#"
    echo "# Каждая запись — известное, ещё не исправленное нарушение вне пакета"
    echo "# PKG-SEC-SECRET-ARGV (OpenProject #382). Новые нарушения не допускаются."
    echo "#"
    echo "# Перегенерировать: bash tools/check-secret-in-argv.sh --update-baseline"
    echo ""
    printf '%s\n' "${out[@]:-}" | LC_ALL=C sort
  } > "$BASELINE_FILE"

  color_green "✅ Baseline перегенерирован: ${#seen[@]} записей → $BASELINE_FILE"
  return 0
}

# ---------------------------------------------------------------------
# Guard-режим.
# ---------------------------------------------------------------------
check_secret_in_argv() {
  cd "$PROJECT_ROOT" || return 2
  load_baseline
  collect_files
  collect_hits

  if [[ ${#ALL_FILES[@]} -eq 0 ]]; then
    color_yellow "⚠️  Нет shell-файлов для сканирования"
    return 0
  fi

  if [[ ${#RAW_HITS[@]} -eq 0 ]]; then
    color_green "✅ OK: секреты не передаются через argv (${#ALL_FILES[@]} shell-файлов проверено)"
    return 0
  fi

  local remaining=()
  local hit file rest lineno rule content key
  for hit in "${RAW_HITS[@]}"; do
    file="${hit%%:*}"
    rest="${hit#*:}"
    lineno="${rest%%:*}"
    rest="${rest#*:}"
    rule="${rest%%:*}"
    content="${rest#*:}"
    key="$(baseline_key "$file" "$content")"
    [[ -z "${BASELINE_SET["$key"]:-}" ]] && remaining+=("$hit")
  done

  if [[ ${#remaining[@]} -eq 0 ]]; then
    color_green "✅ OK: все ${#RAW_HITS[@]} совпадений — в baseline (legacy, вне пакета #382)"
    return 0
  fi

  color_red "❌ НАРУШЕНИЕ: секреты передаются через argv (${#remaining[@]})"
  color_yellow "   Правило: секреты не передаются через argv — их видно в 'ps -eo args'"
  color_yellow "   любому пользователю машины (BR-551/BR-552/BR-605, OpenProject #382)."
  color_yellow "   Baseline: ${BASELINE_COUNT} записей legacy-исключений (сопоставление по содержимому)."
  color_yellow ""
  for hit in "${remaining[@]}"; do
    file="${hit%%:*}"
    rest="${hit#*:}"
    lineno="${rest%%:*}"
    rest="${rest#*:}"
    rule="${rest%%:*}"
    content="${rest#*:}"
    [[ ${#content} -gt 200 ]] && content="${content:0:197}..."
    printf '  \033[0;31m%s\033[0m:%s  \033[0;33m[%s]\033[0m\n' "$file" "$lineno" "$rule"
    printf '      %s\n' "$content"
  done
  color_yellow ""
  color_yellow "Как исправить:"
  color_yellow "  - пароль БД: PGPASSWORD в окружении / .pgpass (PGPASSFILE) / stdin;"
  color_yellow "  - пароль registry: docker login --password-stdin;"
  color_yellow "  - токен: curl --config - (header = \"Authorization: …\") — не через -H/-u."
  return 1
}

# ---------------------------------------------------------------------
# Self-test: каждое правило обязано ловить «плохой» пример и не ловить
# «хороший». Это доказывает, что guard не «молчит» (регресс-контроль).
# ---------------------------------------------------------------------
self_test() {
  local bad good rule id
  local failed=0
  # Формат: rule_id|плохой пример|хороший пример
  local cases=(
    'docker-exec-env-password|docker exec -e PGPASSWORD="$PASS" db pg_dump|PGPASSWORD="$PASS" pg_dump -h db'
    'password-flag-value|docker login --username u --password "$PASS"|docker login --username u --password-stdin'
    'header-authorization-flag|curl -H "Authorization: Basic $B64" http://h/|curl --config - http://h/'
    'user-password-flag|curl -u "apikey:$TOKEN" http://h/|ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)'
    'conn-string-password|psql "host=db user=u password=$PASS dbname=x"|PGPASSFILE=/tmp/.pgpass psql -h db -U u'
    'url-inline-password|pg_dump --dbname=postgresql://u:$PASS@db/x|pg_dump --dbname=postgresql://u@db/x'
  )

  for case_spec in "${cases[@]}"; do
    id="${case_spec%%|*}"
    local rest="${case_spec#*|}"
    bad="${rest%%|*}"
    good="${rest#*|}"

    if ! [[ "$bad" =~ $(rule_main "$id") ]]; then
      color_red "✗ self-test: правило '$id' НЕ ловит плохой пример: $bad"
      failed=1
      continue
    fi
    if [[ "$good" =~ $(rule_main "$id") ]]; then
      color_red "✗ self-test: правило '$id' ловит безопасный пример: $good"
      failed=1
      continue
    fi
    color_green "✓ self-test: $id"
  done

  if [[ $failed -eq 0 ]]; then
    color_green "✅ self-test: все правила работают (ловят плохое, не ловят хорошее)"
    return 0
  fi
  return 2
}

# Основной regex правила по его id.
rule_main() {
  local wanted="$1" i
  for ((i = 0; i < RULE_COUNT; i++)); do
    if [[ "${RULE_IDS[$i]}" == "$wanted" ]]; then
      printf '%s' "${RULE_MAIN[$i]}"
      return 0
    fi
  done
  return 1
}

case "${1:-}" in
  --help|-h|"")
    check_secret_in_argv
    exit $?
    ;;
  --quiet)
    check_secret_in_argv >/dev/null 2>&1
    exit $?
    ;;
  --self-test)
    self_test
    exit $?
    ;;
  --update-baseline)
    update_baseline
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-secret-in-argv.sh [--quiet | --self-test | --update-baseline]"
    exit 2
    ;;
esac
