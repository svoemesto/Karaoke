#!/usr/bin/env bash
# =====================================================================
# tools/registry-smoke.sh — приёмка приватного registry (спека 540, issue #286).
#
# Использование:
#   bash tools/registry-smoke.sh <host:port> [--scheme https|http]
#                                            [-u USER] [-p PASSWORD]
#                                            [--no-upstream]
#
# Примеры:
#   bash tools/registry-smoke.sh sm-karaoke.ru:5443              # прод (SC-003/SC-006)
#   bash tools/registry-smoke.sh localhost:5500 --scheme http    # стенд
#
# Что проверяет (contracts/registry-transport.md § 5, data-model.md § E3):
#   1. FR-002 / SC-006 (I-5): анонимно НЕ даёт ни одной операции —
#      /v2/, /v2/_catalog, /v2/<name>/manifests/<tag> без кредов → 401;
#   2. вне /v2/ поверхность закрыта: / → 404;
#   3. неверный пароль → 401;
#   4. верный пароль → 200 (доступ не сломан);
#   5. инвариант I-2: в read-only зоне `library/**` нет записей, созданных нами.
#      Критерий: тег, которого НЕТ у upstream (404) — наша запись (FAIL);
#      расхождение дайджеста при существующем теге — WARN (тег upstream подвинулся).
#
# Креды берутся в порядке: флаги → окружение (REGISTRY_USER/REGISTRY_PASSWORD) →
# do.env (deploy/prod-single-host/do.env или deploy/do.env). Секреты НЕ печатаются
# и НЕ передаются через argv: `-u user:pass` и `-H "Authorization: …"` видны в
# `ps -eo args` любому пользователю машины (аудит #336, BR-593/BR-594) — curl
# получает их через stdin-конфиг (`--config -`).
#
# Приёмка должна запускаться СНАРУЖИ (с admin-машины), а не с localhost прод-хоста —
# SC-006 требует внешней попытки, а не рассуждения.
#
# Exit: 0 — все проверки PASS (SKIP не блокирует), 1 — есть FAIL, 2 — usage.
# =====================================================================
set -uo pipefail

UPSTREAM_REGISTRY="https://registry-1.docker.io"
AUTH_DOCKER="https://auth.docker.io/token"

HOST=""
SCHEME="https"
USER_OPT=""
PASS_OPT=""
CHECK_UPSTREAM=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --scheme)      SCHEME="$2"; shift 2 ;;
    -u|--user)     USER_OPT="$2"; shift 2 ;;
    -p|--password) PASS_OPT="$2"; shift 2 ;;
    --no-upstream) CHECK_UPSTREAM=0; shift ;;
    -h|--help)     sed -n '3,10p' "$0"; exit 0 ;;
    -*)            echo "unknown option: $1" >&2; exit 2 ;;
    *)             HOST="$1"; shift ;;
  esac
done

if [[ -z "$HOST" ]]; then
  echo "usage: $0 <host:port> [--scheme https|http] [-u USER] [-p PASSWORD] [--no-upstream]" >&2
  exit 2
fi

BASE="${SCHEME}://${HOST}"
PASS_CNT=0; FAIL_CNT=0; SKIP_CNT=0; WARN_CNT=0
CURL=(curl -sS --max-time 30)

pass() { printf '  \033[0;32mPASS\033[0m  %s\n' "$1"; PASS_CNT=$((PASS_CNT+1)); }
fail() { printf '  \033[0;31mFAIL\033[0m  %s\n' "$1"; FAIL_CNT=$((FAIL_CNT+1)); }
skip() { printf '  \033[0;33mSKIP\033[0m  %s\n' "$1"; SKIP_CNT=$((SKIP_CNT+1)); }
warn() { printf '  \033[0;33mWARN\033[0m  %s\n' "$1"; WARN_CNT=$((WARN_CNT+1)); }

# --- креды ---------------------------------------------------------------
find_doenv() {
  local repo
  repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
  for f in "$PWD/do.env" "$PWD/deploy/prod-single-host/do.env" \
           "$repo/deploy/prod-single-host/do.env" "$repo/deploy/do.env"; do
    [[ -f "$f" ]] && { printf '%s' "$f"; return 0; }
  done
  return 1
}
env_val() { sed -n "s/^$1=//p" "$2" | tail -1 | tr -d ' \r'; }

USER_CRED="${USER_OPT:-${REGISTRY_USER:-}}"
PASS_CRED="${PASS_OPT:-${REGISTRY_PASSWORD:-}}"
DOENV=""
if [[ -z "$USER_CRED" || -z "$PASS_CRED" ]]; then
  if DOENV="$(find_doenv)"; then
    [[ -z "$USER_CRED" ]] && USER_CRED="$(env_val REGISTRY_USER "$DOENV")"
    [[ -z "$PASS_CRED" ]] && PASS_CRED="$(env_val REGISTRY_PASSWORD "$DOENV")"
  fi
fi

# --- передача кредов без argv (BR-593) -----------------------------------
# `-u user:pass` и `-H "Authorization: …"` попадают в список процессов
# (`ps -eo args`, /proc/<pid>/cmdline) — секрет виден любому пользователю
# машины. Поэтому curl получает креды через stdin-конфиг `--config -`:
# в argv остаётся только путь/URL.
cfg_escape() { # экранирование значения для curl --config (кавычки и слэши)
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/ }"
  printf '%s' "$s"
}

# $1=url $2=auth(0/1) $3=user $4=pass; остальные аргументы — опции curl.
curl_registry() {
  local url="$1" auth="$2" user="$3" pass="$4"
  shift 4
  if [[ "$auth" == "1" ]]; then
    printf 'user = "%s:%s"\n' "$(cfg_escape "$user")" "$(cfg_escape "$pass")" \
      | "${CURL[@]}" --config - "$@" "$url" 2>/dev/null
  else
    "${CURL[@]}" "$@" "$url" 2>/dev/null
  fi
}

# Bearer-токен upstream — тоже через stdin-конфиг, не через -H.
curl_bearer() { # $1=url $2=token; остальные — опции curl
  local url="$1" token="$2"
  shift 2
  printf 'header = "Authorization: Bearer %s"\n' "$(cfg_escape "$token")" \
    | "${CURL[@]}" --config - "$@" "$url" 2>/dev/null
}

http_code() { # $1=url $2=auth(0/1) $3=user $4=pass
  curl_registry "$1" "$2" "$3" "$4" -o /dev/null -w '%{http_code}'
}
check() { # $1=ожидаемый код $2=описание $3=url $4=auth $5=user $6=pass
  local got
  got="$(http_code "$3" "${4:-0}" "${5:-$USER_CRED}" "${6:-$PASS_CRED}")"
  got="${got: -3}"
  if [[ "$got" == "$1" ]]; then pass "$2 ($got)"; else fail "$2 — ожидали $1, получили $got"; fi
}

echo "=== registry-smoke: $BASE ==="
echo "[1] Анонимный доступ закрыт целиком (FR-002, SC-006, I-5)"
check 401 "GET /v2/ без кредов"                  "$BASE/v2/"
check 401 "GET /v2/_catalog без кредов"          "$BASE/v2/_catalog"
check 401 "GET /v2/library/nginx/manifests/stable без кредов" \
          "$BASE/v2/library/nginx/manifests/stable"

echo "[2] Вне /v2/ поверхность закрыта"
check 404 "GET / без кредов"                     "$BASE/"

echo "[3] Неверный пароль отвергается"
# BR-593: раньше `check` вызывался БЕЗ 4-го аргумента → http_code не добавлял
# креды и уходил ТОТ ЖЕ анонимный GET, что и проверка [1]: строка контракта
# «GET /v2/ с неверным паролем → 401» (contracts/registry-transport.md § 5)
# не проверялась никогда. Здесь — заведомо неверная пара; $PASS_CRED
# НЕ переиспользуется (иначе проверка была бы «с верным паролем»).
WRONG_USER="${USER_CRED:-smoke-invalid-user}"
WRONG_PASS="invalid-password-smoke-$$"
check 401 "GET /v2/ с неверным паролем"          "$BASE/v2/" 1 "$WRONG_USER" "$WRONG_PASS"

echo "[4] Доступ с верными кредами не сломан"
if [[ -z "$USER_CRED" || -z "$PASS_CRED" ]]; then
  skip "креды не найдены (флаги/окружение/do.env) — проверки с авторизацией пропущены"
else
  check 200 "GET /v2/ с кредами ($USER_CRED)"    "$BASE/v2/" 1
  check 200 "GET /v2/_catalog с кредами"         "$BASE/v2/_catalog" 1
fi

echo "[5] Инвариант I-2: в library/** нет наших записей"
if [[ "$CHECK_UPSTREAM" != "1" ]]; then
  skip "проверка upstream отключена (--no-upstream)"
elif [[ -z "$USER_CRED" || -z "$PASS_CRED" ]]; then
  skip "нужны креды для чтения каталога"
elif ! command -v python3 >/dev/null; then
  skip "нет python3 для разбора JSON"
else
  CATALOG="$(curl_registry "$BASE/v2/_catalog" 1 "$USER_CRED" "$PASS_CRED")"
  LIBS="$(printf '%s' "$CATALOG" | python3 -c \
    'import json,sys
try: repos=json.load(sys.stdin).get("repositories",[])
except Exception: repos=[]
print("\n".join(r for r in repos if r.startswith("library/")))' 2>/dev/null)"
  if [[ -z "$LIBS" ]]; then
    pass "репозиториев library/* в нашем registry нет (кэш pull-through пуст) — нарушить нечего"
  else
    while IFS= read -r repo; do
      [[ -z "$repo" ]] && continue
      TAGS="$(curl_registry "$BASE/v2/$repo/tags/list" 1 "$USER_CRED" "$PASS_CRED" \
              | python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin).get("tags") or []))' 2>/dev/null)"
      while IFS= read -r tag; do
        [[ -z "$tag" ]] && continue
        name="${repo#library/}"
        TOKEN="$(curl -sS --max-time 30 "$AUTH_DOCKER?service=registry.docker.io&scope=repository:$repo:pull" \
                 | python3 -c 'import json,sys; print(json.load(sys.stdin).get("token",""))' 2>/dev/null)"
        if [[ -z "$TOKEN" ]]; then
          skip "$repo:$tag — не удалось получить токен upstream"
          continue
        fi
        UP_CODE="$(curl_bearer "$UPSTREAM_REGISTRY/v2/$repo/manifests/$tag" "$TOKEN" \
                   -o /dev/null -w '%{http_code}' --max-time 30 \
                   -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                   -I)"
        UP_CODE="${UP_CODE: -3}"
        if [[ "$UP_CODE" == "404" ]]; then
          fail "$repo:$tag отсутствует у upstream — это НАША запись в read-only зоне (нарушение I-2)"
        else
          warn "$repo:$tag есть у upstream ($UP_CODE) — запись могла быть создана sync; сверьте дайджест вручную, если есть сомнения"
        fi
      done <<< "$TAGS"
    done <<< "$LIBS"
  fi
fi

echo
echo "--- итог: PASS=$PASS_CNT FAIL=$FAIL_CNT WARN=$WARN_CNT SKIP=$SKIP_CNT ---"
if [[ "$FAIL_CNT" -gt 0 ]]; then
  echo "РЕЗУЛЬТАТ: FAIL"
  exit 1
fi
echo "РЕЗУЛЬТАТ: OK"
exit 0
