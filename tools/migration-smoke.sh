#!/usr/bin/env bash
# =====================================================================
# tools/migration-smoke.sh — smoke-проверка нового хоста Karaoke.
#
# Использование:
#   tools/migration-smoke.sh <host>[:port]
#
# Примеры:
#   tools/migration-smoke.sh 188.127.240.124      # до DNS (по IP, с Host-заголовком)
#   tools/migration-smoke.sh sm-karaoke.ru        # после DNS
#
# Назначение: проверка приёмки переезда (wayfinder #174). Возвращает
# 0 при всех PASS, 1 при любом FAIL. Доступен из любой машины с curl.
# =====================================================================
set -uo pipefail

HOST="${1:-}"
if [ -z "$HOST" ]; then
  echo "usage: $0 <host>[:port]" >&2
  exit 2
fi

# Схема: по IP — всё равно https с SNI/Host = sm-karaoke.ru.
SERVER_NAME="sm-karaoke.ru"
CURL=(curl -sS --max-time 20)
RESOLVE_OPT=()
# Если задан IP — ходим по https://sm-karaoke.ru с --resolve IP (SNI/Host корректны).
case "${HOST%%:*}" in
  *[!0-9.]*) BASE="https://${HOST}" ;;
  *)
    BASE="https://${SERVER_NAME}"
    RESOLVE_OPT=(--resolve "${SERVER_NAME}:443:${HOST%%:*}")
    ;;
esac

# curl отдаёт "000" при ошибке соединения; санитизируем до 3 цифр.
http_code() {
  local c
  c=$("${CURL[@]}" "${RESOLVE_OPT[@]}" -o /dev/null -w "%{http_code}" "$1" 2>/dev/null)
  printf '%s' "${c: -3}"
}

PASS=0
FAIL=0

check() {
  local desc="$1" expected="$2" url="$3"
  local code
  code=$(http_code "$url")
  if [ "$code" = "$expected" ]; then
    printf "PASS  %-3s  %s  (%s)\n" "$code" "$desc" "$url"
    PASS=$((PASS + 1))
  else
    printf "FAIL  %-3s (want %s)  %s  (%s)\n" "$code" "$expected" "$desc" "$url"
    FAIL=$((FAIL + 1))
  fi
}

check_body() {
  local desc="$1" pattern="$2" url="$3"
  local body
  body=$("${CURL[@]}" "${RESOLVE_OPT[@]}" "$url" 2>/dev/null || true)
  if printf '%s' "$body" | grep -qE "$pattern"; then
    printf "PASS  body  %s  (matches '%s')\n" "$desc" "$pattern"
    PASS=$((PASS + 1))
  else
    printf "FAIL  body  %s  (no match '%s')\n" "$desc" "$pattern"
    FAIL=$((FAIL + 1))
  fi
}

echo "=== Migration smoke: ${BASE} (SNI/Host=${SERVER_NAME}) ==="
echo

# --- HTTP-поверхность ---
echo "--- HTTP ---"
check "SPA главная"                  200 "$BASE/"
check "Публичный список песен"       200 "$BASE/api/public/songs"
check "Плитки авторов"              200 "$BASE/api/public/authors-tiles"
check "Закрома"                      200 "$BASE/api/public/zakroma"
check "Новости"                      200 "$BASE/news"
check "Страница песни"               200 "$BASE/song?id=1"
check "OG-картинка песни"            200 "$BASE/api/public/og/song?id=1"
check "Альбомы автора"               200 "$BASE/api/public/authors/17/albums"
check "Статистика"                   200 "$BASE/api/public/stats"
check "MinIO через nginx /minio/"    200 "$BASE/minio/karaoke/song_banner_517.png"
check "HTTP→HTTPS redirect"          301 "http://${HOST%%:*}/"

# --- Данные (не заглушка) ---
echo
echo "--- Данные ---"
check_body "Статистика содержит total" '"total":[1-9][0-9]*' "$BASE/api/public/stats"
check_body "Плитки авторов непусты"    '"author":"' "$BASE/api/public/authors-tiles"

# --- SSE / real-time ---
echo
echo "--- SSE ---"
# /changerecords — POST (405 на GET означает, что эндпоинт жив).
check "SSE /changerecords жив"       405 "$BASE/changerecords"

# --- Внешние прокси (обход MTU) ---
echo
echo "--- Внешние прокси ---"
# /smartcaptcha/ и /yookassa/ проксируются на внешние API; ожидаем не 5xx.
SC=$(http_code "$BASE/smartcaptcha/")
if [ "${SC:0:1}" != "5" ] && [ "$SC" != "000" ]; then
  printf "PASS  %-3s  smartcaptcha-прокси жив\n" "$SC"; PASS=$((PASS + 1))
else
  printf "FAIL  %-3s  smartcaptcha-прокси\n" "$SC"; FAIL=$((FAIL + 1))
fi

echo
echo "=== Итог: PASS=${PASS}, FAIL=${FAIL} ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
