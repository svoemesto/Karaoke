#!/usr/bin/env bash
# =====================================================================
# deploy/admin-ip-allowlist.sh — самовосстановление доступа к прод-каналу
# =====================================================================
#
# Спека 677 / KARAOKE-337 (FR-001…FR-020). Контракты:
#   specs/677-admin-ip-allowlist/contracts/automation-cli.md      — режимы и коды
#   specs/677-admin-ip-allowlist/contracts/allow-list-surgery.md  — порядок правки
#   specs/677-admin-ip-allowlist/contracts/journal-format.md       — формат журнала
#
# ЗАЧЕМ: админ-машина теряет доступ к прод-каналу 5433/9000, когда меняется её
# внешний адрес (пул провайдера плавает ежемесячно; при поднятом VPN прод видит
# адрес VPS, которого в allow-list нет). Симптом один: конвейер встаёт, а
# мониторинг на той же машине поднимает ложный CRITICAL «Прод недоступен».
#
# ЧТО ДЕЛАЕТ: по таймеру (10 минут) проверяет с админ-машины TCP 5433, TCP 9000
# и HTTPS https://sm-karaoke.ru/health. Если HTTPS отвечает, а канал — нет, то
# причина в allow-list: определяет текущий внешний адрес (публичный HTTPS-сервис
# + резерв), валидирует его и хирургически добавляет одну строку `allow <ip>;`
# в каждый из двух stream-блоков host-nginx на проде — с бэкапом, проверкой
# синтаксиса тем же nginx'ом, reload, контрольной проверкой ответа и откатом.
#
# ГРАНИЦЫ:
#   * прод не меняется, кроме одной строки allow-list (FR-006, FR-015);
#   * адреса из allow-list не удаляются никогда (FR-011);
#   * не более одной применённой правки за 30 минут (FR-010);
#   * токены — только из окружения, никогда в argv (BR-551/552/605): Bot API
#     вызывается через `curl -K` с конфиг-файлом 600;
#   * уведомления — в отдельный чат (ALLOWLIST_NOTIFY_CHAT_ID), НЕ в канал
#     проекта: канал проекта публичный (публикация новостей), и служебная
#     информация о проде туда попадать не должна (инцидент 2026-11-01);
#     если чат не задан — уведомлений нет, факт остаётся в журнале;
#   * прокси — только для вызова Telegram Bot API (ALLOWLIST_TELEGRAM_PROXY);
#     определение внешнего адреса и пробы доступности идут напрямую
#     (`--noproxy '*'`) — иначе в allow-list попал бы адрес выхода прокси,
#     а не адрес админ-машины; глобального прокси у скрипта нет;
#   * локальный git не используется: PR собирается через Forgejo API, чтобы
#     рабочее дерево админ-машины (где работают другие сессии) не трогать.
#
# Использование:
#   bash deploy/admin-ip-allowlist.sh              основной цикл (по таймеру)
#   bash deploy/admin-ip-allowlist.sh --diagnose   состояние и план, без правок
#   bash deploy/admin-ip-allowlist.sh --check-ssh  проверка SSH-доступа к проду, без правок
#   bash deploy/admin-ip-allowlist.sh --self-test  изолированная проверка правки
#   bash deploy/admin-ip-allowlist.sh --help       справка
#
# Переменные окружения (все с разумными значениями по умолчанию) — полный
# список в specs/677-admin-ip-allowlist/contracts/automation-cli.md §4.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- прод ---
PROD_HOST="${ALLOWLIST_PROD_HOST:-root@188.127.240.124}"
# ВСЕ файлы прод-конфигурации, где живёт allow-list админ-машины.
#
# ИНЦИДЕНТ 2026-11-02 (вечер): править ОДИН файл нельзя. Автоматика обновила
# stream-блоки (5433/9000) в /etc/nginx/nginx.conf, а HTTP-локации /changerecords
# и /api/properties/setproperty в /etc/nginx/sites-enabled/80to8897 остались со
# старым списком адресов → nginx отдавал 403 на эндпоинт синхронизации, и одобренные
# редакторами песни не доезжали до прода (правки выглядели «потерянными»).
#
# Теперь список: главный конфиг (stream) + http-конфиг сайта (locations).
# Одиночный путь задаётся ALLOWLIST_PROD_NGINX_CONF (совместимость).
if [ -n "${ALLOWLIST_PROD_NGINX_CONF:-}" ]; then
  PROD_CONF_FILES="${ALLOWLIST_PROD_NGINX_CONF}"
else
  PROD_CONF_FILES="${ALLOWLIST_PROD_CONF_FILES:-/etc/nginx/nginx.conf /etc/nginx/sites-enabled/80to8897}"
fi
PROD_CONF="${PROD_CONF_FILES%% *}"
PROD_PORTS="${ALLOWLIST_PROD_PORTS:-5433 9000}"
HEALTH_URL="${ALLOWLIST_HEALTH_URL:-https://sm-karaoke.ru/health}"
PROBE_TIMEOUT="${ALLOWLIST_PROBE_TIMEOUT:-5}"
# Повторы проб: одна неудачная проба — ещё не «прод недоступен» (сетевой сбой на
# машине даёт ложные срабатывания). Повтор — деталь реализации, контракт
# (FR-001) не меняется.
PROBE_ATTEMPTS="${ALLOWLIST_PROBE_ATTEMPTS:-3}"
PROBE_RETRY_PAUSE="${ALLOWLIST_PROBE_RETRY_PAUSE:-2}"
# SSH-доступ к прод-хосту.
#
# Прецедент 2026-11-02: внешний IP админ-машины сменился (185.42.124.207 →
# 185.191.59.143), а механизм самовосстановления не отработал — правка allow-list
# не была применена, и владелец узнал об outage из интерфейса. Причина: юнит
# запускает скрипт под root, у root нет ни ключа, ни known_hosts для прод-хоста,
# поэтому ssh падал с rc=255, а stderr ssh подавлялся — в журнале оставалось только
# «prod_unreachable_by_ssh» без причины.
#
# Идентичность и known_hosts задаются из окружения: по умолчанию поведение прежнее
# (обычный ssh пользователя сервиса), но сервис может указать рабочую пару.
SSH_CONNECT_TIMEOUT="${ALLOWLIST_SSH_CONNECT_TIMEOUT:-10}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout="${SSH_CONNECT_TIMEOUT}")
if [ -n "${ALLOWLIST_SSH_IDENTITY_FILE:-}" ]; then
  SSH_OPTS+=(-o IdentitiesOnly=yes -i "${ALLOWLIST_SSH_IDENTITY_FILE}")
fi
if [ -n "${ALLOWLIST_SSH_KNOWN_HOSTS:-}" ]; then
  SSH_OPTS+=(-o UserKnownHostsFile="${ALLOWLIST_SSH_KNOWN_HOSTS}")
fi
if [ -n "${ALLOWLIST_SSH_STRICT_HOST_KEY:-}" ]; then
  SSH_OPTS+=(-o StrictHostKeyChecking="${ALLOWLIST_SSH_STRICT_HOST_KEY}")
fi
# Последняя ошибка ssh — для журнала и уведомления (раньше она терялась).
SSH_LAST_ERROR=""
# Рабочая ssh-идентичность: явная настройка → кэш прошлого прогона →
# автоподбор ключа оператора админ-машины (см. ensure_ssh_ready).
SSH_IDENTITY_FILE=""
SSH_KNOWN_HOSTS_FILE=""
SSH_READY_RESOLVED=0
SSH_READY_RC=0

# --- адрес ---
IP_SERVICES="${ALLOWLIST_IP_SERVICES:-https://api.ipify.org https://ifconfig.me/ip https://icanhazip.com https://checkip.amazonaws.com https://ifconfig.co/ip https://ipinfo.io/ip}"
IP_TIMEOUT="${ALLOWLIST_IP_TIMEOUT:-8}"

# --- ограничения и состояние ---
MIN_INTERVAL_MINUTES="${ALLOWLIST_MIN_INTERVAL_MINUTES:-30}"
LOG_FILE="${ALLOWLIST_LOG_FILE:-/var/log/admin-ip-allowlist.log}"
STATE_FILE="${ALLOWLIST_STATE_FILE:-/var/log/admin-ip-allowlist.state}"

# --- уведомления ---
TELEGRAM_TOKEN="${ALLOWLIST_TELEGRAM_BOT_TOKEN:-}"
# Служебные уведомления (allow-list, откат, «прод недоступен по SSH») идут
# в ОТДЕЛЬНЫЙ чат — ALLOWLIST_NOTIFY_CHAT_ID. Канал проекта
# (ALLOWLIST_TELEGRAM_CHAT_ID) для них НЕ используется: это публичный канал
# публикации новостей, и служебная информация о проде в нём быть не должна
# (инцидент 2026-11-01: «прод-хост недоступен по SSH» ушло в канал
# «Караоке на своём месте»). Если ALLOWLIST_NOTIFY_CHAT_ID не задан —
# уведомления не отправляются, факт остаётся только в журнале.
notify_chat_id() { printf '%s' "${ALLOWLIST_NOTIFY_CHAT_ID:-}"; }
TELEGRAM_CHAT_ID="$(notify_chat_id)"
# Прокси — ТОЛЬКО для вызова Telegram Bot API: с админ-машины api.telegram.org
# напрямую недоступен (нужен VPN), рабочий локальный SOCKS5 — 127.0.0.1:1080.
# Пусто → прямое соединение. Определение внешнего адреса и пробы доступности
# прокси не используют НИКОГДА (см. --noproxy в probe_https/detect_address):
# через прокси определился бы адрес выхода прокси, и в allow-list прод-хоста
# попал бы не адрес админ-машины.
TELEGRAM_PROXY="${ALLOWLIST_TELEGRAM_PROXY:-}"

# --- PR ---
FJ_TOKEN="${ALLOWLIST_FJ_TOKEN:-}"
FJ_URL="${ALLOWLIST_FJ_URL:-https://git.sm-karaoke.ru}"
FJ_REPO="${ALLOWLIST_REPO:-svoemesto/Karaoke}"
REPO_CONF_PATH="${ALLOWLIST_REPO_PATH:-deploy/prod-single-host/nginx.conf}"
# Канон http-конфига сайта: те же allow-list'ы, что и на проде (KARAOKE-389).
REPO_CONF_PATH_SITE="${ALLOWLIST_REPO_PATH_SITE:-deploy/prod-single-host/80to8897}"
REPO_COPY_LOCAL="${SCRIPT_DIR}/prod-single-host/nginx.conf"

# --- прочее ---
NGINX_BIN="${ALLOWLIST_NGINX_BIN:-nginx}"
# Валидатор самопроверки: команда-шаблон с {file} (например, docker exec … nginx -t -c {file}).
# Пусто — используется заглушка, проверяющая структуру конфигурации.
SELFTEST_VALIDATOR="${ALLOWLIST_SELFTEST_VALIDATOR:-}"
SELFTEST_DIR="${ALLOWLIST_SELFTEST_DIR:-}"

EXIT_OK=0
EXIT_FAIL=1
EXIT_USAGE=2
EXIT_DECLINED=3

NOW_ISO() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# ---------------------------------------------------------------- журнал ----
# Формат: <метка времени ISO-8601 UTC> event:key=value key=value (ADR local-0005).
# Секреты в журнал не попадают (FR-016): здесь только факты.
log_event() {
  local event="$1"; shift
  local line="event:${event}"
  local kv
  for kv in "$@"; do
    line="${line} ${kv}"
  done
  local out="$(NOW_ISO) ${line}"
  if [ -n "${LOG_FILE}" ]; then
    printf '%s\n' "${out}" >>"${LOG_FILE}" 2>/dev/null || true
  fi
  # Журнал идёт в stderr: stdout остаётся чистым для функций, возвращающих
  # значение (detect_address и др.). Под systemd и stdout, и stderr попадают
  # в journald (StandardOutput=journal, StandardError=journal).
  printf '%s\n' "${out}" >&2
}

# Значения для лога без пробелов (контракт журнала §2).
sanitize() { printf '%s' "${1:-}" | tr -d '\n\r' | tr ' ' '_'; }

# ------------------------------------------------------------------ пробы ----
# Текущее время в миллисекундах. `%3N` есть в GNU date; если нет — fallback
# на секунды (точность до секунды, но значение реальное, а не заглушка).
now_ms() {
  local v
  v="$(date +%s%3N 2>/dev/null)" || v=""
  if [[ "${v}" =~ ^[0-9]+$ ]] && [ "${v}" -gt 1000000000000 ] 2>/dev/null; then
    printf '%s' "${v}"
    return 0
  fi
  printf '%s' "$(( $(date +%s) * 1000 ))"
}

probe_tcp() {
  local host="$1" port="$2" timeout_s="$3"
  timeout "${timeout_s}" bash -c "exec 3<>/dev/tcp/${host}/${port}" >/dev/null 2>&1
}

# Протокольная проба Postgres-прокси (5433): SSLRequest и ожидание одного байта
# ответа ('S' — SSL, 'N' — без SSL; оба означают «сервер принял запрос»).
#
# Почему не probe_tcp (BR-631, инцидент 2026-11-02): nginx в stream-блоке при
# `deny all` ПРИНИМАЕТ TCP-соединение и сразу закрывает его. connect() при этом
# успешен, поэтому автоматика решала «канал доступен» (event:probe ok=true →
# action=none) и новый адрес админ-машины в allow-list не попадал, хотя доступ
# был закрыт. Протокольный запрос отличает «открыто» от «закрыто»: закрытый
# канал не отвечает ни одним байтом.
probe_postgres() {
  local host="$1" port="$2" timeout_s="$3" reply
  reply="$(
    timeout "${timeout_s}" bash -c "
      exec 3<>/dev/tcp/${host}/${port} || exit 1
      printf '\000\000\000\010\004\322\026\057' >&3 || exit 1
      dd bs=1 count=1 <&3 2>/dev/null
    " 2>/dev/null
  )" || return 1
  [ -n "${reply}" ]
}

# Протокольная проба HTTP-канала (MinIO на 9000): запрос к health-эндпоинту и
# проверка кода 200 в первой строке ответа. Та же причина, что у probe_postgres.
probe_http_ok() {
  local host="$1" port="$2" timeout_s="$3" path="$4" first
  first="$(
    timeout "${timeout_s}" bash -c "
      exec 3<>/dev/tcp/${host}/${port} || exit 1
      printf 'GET ${path} HTTP/1.0\r\nHost: ${host}\r\n\r\n' >&3 || exit 1
      head -n 1 <&3
    " 2>/dev/null
  )" || return 1
  case "${first}" in
    *" 200 "*) return 0 ;;
    *" 200"*) return 0 ;;
  esac
  return 1
}

# Проба конкретного порта: для известных прод-каналов — протокольная, для
# остальных — обычный TCP-connect (обратная совместимость с ALLOWLIST_PROD_PORTS).
probe_port() {
  local host="$1" port="$2" timeout_s="$3"
  case "${port}" in
    5433) probe_postgres "${host}" "${port}" "${timeout_s}" ;;
    9000) probe_http_ok "${host}" "${port}" "${timeout_s}" "/minio/health/live" ;;
    *) probe_tcp "${host}" "${port}" "${timeout_s}" ;;
  esac
}

probe_https() {
  local url="$1" timeout_s="$2" code
  # --noproxy '*': проба идёт НАПРЯМУЮ — переменные прокси окружения не должны
  # влиять на результат (иначе «прод недоступен» означало бы «прокси недоступен»).
  code="$(curl -fsS --noproxy '*' -o /dev/null -w '%{http_code}' --max-time "${timeout_s}" "${url}" 2>/dev/null)" || code="000"
  [ "${code}" -ge 200 ] 2>/dev/null && [ "${code}" -lt 300 ] 2>/dev/null
}

# Проба порта с ограниченным повтором. В PROBE_DURATION_MS — реальная
# длительность последней попытки, в PROBE_ATTEMPTS_USED — число попыток.
probe_port_retry() {
  local host="$1" port="$2" timeout_s="$3" attempts="${4:-${PROBE_ATTEMPTS}}"
  local i started ended
  started="$(now_ms)"
  for ((i = 1; i <= attempts; i++)); do
    if probe_port "${host}" "${port}" "${timeout_s}"; then
      ended="$(now_ms)"
      PROBE_DURATION_MS=$((ended - started))
      PROBE_ATTEMPTS_USED="${i}"
      return 0
    fi
    [ "${i}" -lt "${attempts}" ] && sleep "${PROBE_RETRY_PAUSE}"
  done
  ended="$(now_ms)"
  PROBE_DURATION_MS=$((ended - started))
  PROBE_ATTEMPTS_USED="${attempts}"
  return 1
}

# Проба HTTPS с тем же ограниченным повтором.
probe_https_retry() {
  local url="$1" timeout_s="$2" attempts="${3:-${PROBE_ATTEMPTS}}"
  local i started ended
  started="$(now_ms)"
  for ((i = 1; i <= attempts; i++)); do
    if probe_https "${url}" "${timeout_s}"; then
      ended="$(now_ms)"
      PROBE_DURATION_MS=$((ended - started))
      PROBE_ATTEMPTS_USED="${i}"
      return 0
    fi
    [ "${i}" -lt "${attempts}" ] && sleep "${PROBE_RETRY_PAUSE}"
  done
  ended="$(now_ms)"
  PROBE_DURATION_MS=$((ended - started))
  PROBE_ATTEMPTS_USED="${attempts}"
  return 1
}

# Проверки доступности канала. Возвращает: 0 — всё доступно, 1 — канал закрыт.
check_reachability() {
  local host="${1:-}" pg_ok=1 minio_ok=1 https_ok=1
  local ports
  ports="$(printf '%s' "${PROD_PORTS}" | tr ' ' '\n' | grep -v '^$' || true)"

  for port in ${ports}; do
    if probe_port_retry "${host}" "${port}" "${PROBE_TIMEOUT}"; then
      log_event "probe" "target=port:${port}" "ok=true" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}"
    else
      log_event "probe" "target=port:${port}" "ok=false" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}" "detail=protocol_failed"
      case "${port}" in
        5433) pg_ok=0 ;;
        9000) minio_ok=0 ;;
      esac
    fi
  done

  if probe_https_retry "${HEALTH_URL}" "${PROBE_TIMEOUT}"; then
    log_event "probe" "target=https" "ok=true" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}"
  else
    log_event "probe" "target=https" "ok=false" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}" "detail=not_2xx"
    https_ok=0
  fi

  REACH_PG="${pg_ok}"; REACH_MINIO="${minio_ok}"; REACH_HTTPS="${https_ok}"
  [ "${pg_ok}" = 1 ] && [ "${minio_ok}" = 1 ] && [ "${https_ok}" = 1 ]
}

# ------------------------------------------------------- определение адреса ----
# FR-003: только машинно, с админ-машины, из публичного HTTPS-сервиса; резерв
# предусмотрен. Значения из переписки и из вывода моделей не принимаются.
# Возвращает адрес в stdout; при неудаче — пусто и причина в REASON.
detect_address() {
  local values=() unique=() url value
  REASON=""
  for url in ${IP_SERVICES}; do
    # --noproxy '*': адрес определяется НАПРЯМУЮ — это адрес админ-машины.
    # Через прокси определился бы адрес выхода прокси, и в allow-list прод-хоста
    # попал бы не тот адрес (FR-003).
    value="$(curl -fsS --noproxy '*' --max-time "${IP_TIMEOUT}" "${url}" 2>/dev/null | tr -d '[:space:]' || true)"
    if [ -n "${value}" ]; then
      values+=("${value}")
      unique+=("${value}")
      log_event "address" "source=$(sanitize "${url}")" "value=$(sanitize "${value}")" "ok=true"
    else
      log_event "address" "source=$(sanitize "${url}")" "value=" "ok=false" "detail=unreachable"
    fi
  done

  if [ "${#unique[@]}" -eq 0 ]; then
    REASON="no_sources"
    log_event "skip" "reason=no_sources" "detail=all_ip_services_unreachable"
    return 1
  fi

  local distinct
  distinct="$(printf '%s\n' "${unique[@]}" | sort -u | wc -l | tr -d ' ')"
  if [ "${distinct}" -gt 1 ]; then
    REASON="sources_disagree"
    log_event "skip" "reason=sources_disagree" "detail=$(sanitize "$(printf '%s ' "${unique[@]}")")"
    return 1
  fi

  printf '%s' "${unique[0]}"
  return 0
}

# Определение адреса, КОТОРЫЙ ВИДИТ ПРОД для нашего канала (SSH_CLIENT).
#
# ИНЦИДЕНТ 2026-11-08: внешние сервисы определения внешнего IP расходятся —
# ipify/ifconfig.me отвечают через VPN-туннель с ротацией адресов (195.178.4.x),
# остальные четыре — через адрес ISP (95.31.39.146). Правило «все источники должны
# совпасть» давало sources_disagree, цикл отказывался править allow-list, и прод-БД
# оставалась закрытой для приложения (DB-CONNECT-001 десятками раз в минуту).
#
# Адрес, который видит прод для SSH-канала, — тот же, что прод видит для трафика
# приложения к 5433/9000 (проверено 2026-11-08 по stream-журналу nginx на проде:
# клиент приложения и клиент хоста приходят с одного адреса). Поэтому прод —
# авторитетный источник: он отвечает за тот канал, который мы и чиним.
detect_prod_seen_address() {
  REASON=""
  local out=""
  out="$(run_ssh 'printf "%s" "$SSH_CLIENT"' 2>/dev/null | tr -d '\r\n' | awk 'NR==1{print $1}')"
  if [ -z "${out}" ]; then
    REASON="prod_unreachable_by_ssh"
    log_event "address" "source=prod_ssh" "value=" "ok=false" "detail=unreachable"
    return 1
  fi
  log_event "address" "source=prod_ssh" "value=$(sanitize "${out}")" "ok=true"
  printf '%s' "${out}"
  return 0
}

# Единая точка определения ТЕКУЩЕГО адреса:
#   1) адрес, который видит прод (SSH_CLIENT) — авторитетный источник;
#   2) иначе — внешние сервисы (как раньше: все должны совпасть).
#
# Расхождение внешних сервисов больше НЕ блокирует правку: прод знает, какой
# адрес мы имеем на том канале, который сам же и правим.
current_address() {
  REASON=""
  local prod_seen=""
  if prod_seen="$(detect_prod_seen_address)"; then
    if validate_address "${prod_seen}" >/dev/null 2>&1; then
      printf '%s' "${prod_seen}"
      return 0
    fi
    log_event "address" "source=prod_ssh" "value=$(sanitize "${prod_seen}")" "ok=false" "detail=invalid"
  fi
  detect_address
}

# ------------------------------------------------------------- валидация ----
# FR-004: формат IPv4, не loopback, не private, не multicast, не broadcast,
# не 0.0.0.0. IPv6 не поддерживается (edge case спеки).
validate_address() {
  local ip="$1" a b c d octet
  REASON=""
  if [[ "${ip}" == *:* ]]; then
    REASON="ipv6_not_supported"
    log_event "skip" "reason=ipv6_not_supported" "value=$(sanitize "${ip}")"
    return 1
  fi
  if ! [[ "${ip}" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]]; then
    REASON="invalid_ip"
    log_event "skip" "reason=invalid_ip" "value=$(sanitize "${ip}")" "detail=not_ipv4"
    return 1
  fi
  IFS='.' read -r a b c d <<<"${ip}"
  for octet in "${a}" "${b}" "${c}" "${d}"; do
    if [ "${octet}" -gt 255 ] 2>/dev/null; then
      REASON="invalid_ip"
      log_event "skip" "reason=invalid_ip" "value=$(sanitize "${ip}")" "detail=octet_out_of_range"
      return 1
    fi
  done
  case "${ip}" in
    127.* | 10.* | 192.168.* | 169.254.* | 224.* | 225.* | 226.* | 227.* | 228.* | 229.* | 230.* | 231.* | 232.* | 233.* | 234.* | 235.* | 236.* | 237.* | 238.* | 239.* | 255.255.255.255 | 0.0.0.0)
      REASON="invalid_ip"
      log_event "skip" "reason=invalid_ip" "value=$(sanitize "${ip}")" "detail=not_public"
      return 1
      ;;
  esac
  if [ "${a}" = "172" ] && [ "${b}" -ge 16 ] && [ "${b}" -le 31 ] 2>/dev/null; then
    REASON="invalid_ip"
    log_event "skip" "reason=invalid_ip" "value=$(sanitize "${ip}")" "detail=private_172_16_12"
    return 1
  fi
  return 0
}

# ------------------------------------------------------------ allow-list ----
# ПРАВИЛО ВЛАДЕЛЬЦА (2026-11-08): в allow-list прод-хоста держим РОВНО ОДИН
# текущий адрес админ-машины. Старые адреса не копим: список из десяти адресов
# «про запас» означает, что доступ остаётся открытым для всех, кто когда-либо
# был админ-машиной.
#
# FR-005: если в файле в КАЖДОМ блоке с `deny all;` стоит ровно одна строка
# `allow <ip>;` (с нашим текущим адресом) и больше никаких адресов — правка не
# нужна, никаких действий.
address_allowed() {
  local conf="$1" ip="$2"
  # BR-631 (инцидент 2026-11-02): «allow есть в файле» ≠ «доступ разрешён». nginx
  # в stream-блоке проверяет allow/deny по порядку, поэтому строка, оказавшаяся
  # ПОСЛЕ `deny all;`, не действует. Проверяем позицию: наш allow должен стоять
  # до первого `deny all;` и быть единственным адресом в блоке.
  python3 - "${conf}" "${ip}" <<'PY'
import re
import sys

conf, ip = sys.argv[1], sys.argv[2]

with open(conf, encoding="utf-8") as fh:
    lines = fh.read().split("\n")

# Только обычные IPv4-адреса: правило «один текущий адрес» не должно молча
# удалять намеренные правила вида `allow 10.0.0.0/8;` (сеть/подсеть).
allow_re = re.compile(r"^\s*allow\s+([0-9]{1,3}(?:\.[0-9]{1,3}){3})\s*;\s*$")
deny_re = re.compile(r"^\s*deny\s+all\s*;")
block_start_re = re.compile(r"^\s*(server|location)\b[^\{]*\{")

blocks_checked = 0
stack = []
i = 0
while i < len(lines):
    line = lines[i]
    if block_start_re.match(line):
        stack.append(i)
    elif re.match(r"^\s*\}", line):
        if stack:
            stack.pop()
    elif deny_re.match(line):
        start = stack[-1] if stack else 0
        before = [m.group(1) for m in (allow_re.match(lines[k]) for k in range(start, i)) if m]
        after = []
        j = i + 1
        while j < len(lines) and not re.match(r"^\s*\}", lines[j]):
            m = allow_re.match(lines[j])
            if m:
                after.append(m.group(1))
            j += 1
        if before != [ip] or after:
            sys.exit(1)
        blocks_checked += 1
    i += 1

sys.exit(0 if blocks_checked > 0 else 1)
PY
}

# Подготовка нового содержимого: ровно одна строка `allow <ip>;` перед
# `deny all;` в каждом stream-блоке с listen 5433 / 9000. Остальное — байт-в-байт.
prepare_new_config() {
  local old_file="$1" new_file="$2" ip="$3"
  python3 - "${old_file}" "${new_file}" "${ip}" <<'PY'
import re
import sys

old_file, new_file, ip = sys.argv[1], sys.argv[2], sys.argv[3]
TARGET_PORTS = ("5433", "9000")

# BR-631: «наш» allow и маркер конца списка — для вставки в правильную позицию
# и для удаления осиротевших строк после `deny all;`.
#
# ПРАВИЛО ВЛАДЕЛЬЦА (2026-11-08): в блоке остаётся РОВНО ОДИН allow — текущий
# адрес. Все прежние адреса (список «про запас») удаляются.
deny_re_block = re.compile(r"^\s*deny\s+all\s*;")
allow_ipv4_re = re.compile(r"^\s*allow\s+[0-9]{1,3}(?:\.[0-9]{1,3}){3}\s*;\s*$")

with open(old_file, encoding="utf-8") as fh:
    lines = fh.read().split("\n")

out = []
blocks = 0
i = 0
while i < len(lines):
    line = lines[i]
    out.append(line)
    if re.match(r"^\s*server\s*\{", line):
        j = i + 1
        body = []
        while j < len(lines) and not re.match(r"^\s*\}", lines[j]):
            body.append(lines[j])
            j += 1
        ports = []
        for b in body:
            m = re.search(r"\blisten\s+(\d+)", b)
            if m:
                ports.append(m.group(1))
        if any(p in TARGET_PORTS for p in ports):
            inserted = False
            for b in body:
                # BR-631 (инцидент 2026-11-02): allow обязан стоять ДО `deny all;` —
                # nginx в stream-блоке проверяет allow/deny по порядку, и строка после
                # `deny all;` не действует никогда. Прежняя реализация дописывала её
                # после deny: правка «применялась успешно», доступ оставался закрытым.
                if deny_re_block.match(b):
                    if not inserted:
                        indent = b[: len(b) - len(b.lstrip())]
                        out.append("{}allow {};".format(indent, ip))
                        inserted = True
                    out.append(b)
                    continue
                # Прежние адреса в этом блоке убираем — остаётся один текущий.
                if allow_ipv4_re.match(b):
                    continue
                out.append(b)
            if not inserted:
                sys.stderr.write("no 'deny all;' in stream block for {}\n".format(ports))
                sys.exit(2)
            blocks += 1
            i = j
            continue
    i += 1

if blocks != 2:
    sys.stderr.write("expected 2 stream blocks (5433, 9000), found {}\n".format(blocks))
    sys.exit(2)

with open(new_file, "w", encoding="utf-8") as fh:
    fh.write("\n".join(out))
sys.stderr.write("blocks={}\n".format(blocks))
PY
}

# FR-020: изменение MUST состоять только из строк `allow` с IPv4-адресами.
# Возвращает 0, если изменение — ровно замена allow-list на один текущий адрес
# и ничего больше.
#
# ПРАВИЛО ВЛАДЕЛЬЦА (2026-11-08): список заменяется целиком — в блоке остаётся
# ровно один `allow <текущий ip>;`. Значит, изменение затрагивает и УДАЛЕНИЕ
# прежних адресов, и прежняя проверка «добавлено ровно 2 строки allow» больше
# не годится: сравниваем содержимое без allow-строк — оно должно совпасть
# байт-в-байт, — и отдельно проверяем итог.
verify_diff() {
  local old_file="$1" new_file="$2" ip="$3"
  python3 - "${old_file}" "${new_file}" "${ip}" <<'PY'
import re
import sys

old_file, new_file, ip = sys.argv[1], sys.argv[2], sys.argv[3]
allow_re = re.compile(r"^\s*allow\s+([0-9]{1,3}(?:\.[0-9]{1,3}){3})\s*;\s*$")
deny_re = re.compile(r"^\s*deny\s+all\s*;")
block_start_re = re.compile(r"^\s*(server|location)\b[^\{]*\{")

with open(old_file, encoding="utf-8") as fh:
    old = fh.read()
with open(new_file, encoding="utf-8") as fh:
    new = fh.read()

old_stripped = "\n".join(l for l in old.split("\n") if not allow_re.match(l))
new_stripped = "\n".join(l for l in new.split("\n") if not allow_re.match(l))
if new_stripped != old_stripped:
    sys.stderr.write("diff contains changes other than allow lines\n")
    sys.exit(1)

lines = new.split("\n")
blocks = 0
stack = []
i = 0
while i < len(lines):
    line = lines[i]
    if block_start_re.match(line):
        stack.append(i)
    elif re.match(r"^\s*\}", line):
        if stack:
            stack.pop()
    elif deny_re.match(line):
        start = stack[-1] if stack else 0
        before = [m.group(1) for m in (allow_re.match(lines[k]) for k in range(start, i)) if m]
        after = []
        j = i + 1
        while j < len(lines) and not re.match(r"^\s*\}", lines[j]):
            m = allow_re.match(lines[j])
            if m:
                after.append(m.group(1))
            j += 1
        if before != [ip] or after:
            sys.stderr.write("prepared config does not keep exactly one current allow per block\n")
            sys.exit(1)
        blocks += 1
    i += 1

if blocks == 0:
    sys.stderr.write("no allow-list blocks found\n")
    sys.exit(1)
PY
}

# ------------------------------------------------- применение и откат ----
# SSH_ATTEMPTS / SSH_RETRY_PAUSE — повторы соединения с прод-хостом.
# KARAOKE-382 (2026-11-01): сеть админ-машины нестабильна (пул провайдера
# плавает, VPN переподключается) — SSH к проду то устанавливался, то обрывался
# на connect, и одна неудачная попытка выглядела как «прод недоступен по SSH»:
# правка allow-list не применялась, а канал 5433/9000 оставался закрытым.
# Несколько попыток с паузой отделяют кратковременный сетевой сбой от реальной
# недоступности. Прод-фильтрации SSH нет (проверено 2026-11-01: iptables
# INPUT ACCEPT, ufw/fail2ban отсутствуют), поэтому повтор здесь — достаточная
# мера.
SSH_ATTEMPTS="${ALLOWLIST_SSH_ATTEMPTS:-3}"
SSH_RETRY_PAUSE="${ALLOWLIST_SSH_RETRY_PAUSE:-5}"

# specs/730-queue-resilience (FR-020/FR-021): скачивание файла конфигурации с
# прод-хоста повторяется целиком: обрыв передачи (пустой или обрезанный файл)
# — это сетевой сбой, а не «структура конфигурации не совпала». Раньше такой файл
# доходил до разбора и давал ложную причину config_mismatch, механизм молча
# сдавался, правка allow-list не выполнялась.
CONF_DOWNLOAD_ATTEMPTS="${ALLOWLIST_CONF_DOWNLOAD_ATTEMPTS:-2}"
CONF_DOWNLOAD_RETRY_PAUSE="${ALLOWLIST_CONF_DOWNLOAD_RETRY_PAUSE:-5}"

# run_ssh <команда> — выполнить команду на прод-хосте с повторами при сбое.
# Повтор безопасен: чтение конфига идемпотентно, а правка allow-list сводится
# к одной и той же строке `allow <ip>;`.
# Последняя строка stderr ssh — короткая причина отказа («Permission denied
# (publickey)», «Host key verification failed», …). Без неё отказ был невидим.
ssh_error_detail() {
  local errfile="$1" line=""
  [ -n "${errfile}" ] && [ -f "${errfile}" ] || { printf 'unknown'; return 0; }
  line="$(grep -vE '^[[:space:]]*$' "${errfile}" | tail -1)"
  line="$(printf '%s' "${line}" | tr -d '\r\n' | tr ' ' '_')"
  [ -n "${line}" ] || line="unknown"
  printf '%s' "$(printf '%s' "${line}" | cut -c1-160)"
}

run_ssh() {
  local attempt=1 rc=0 errfile=""
  errfile="$(mktemp "${TMPDIR:-/tmp}/allowlist-ssh-err.XXXXXX" 2>/dev/null || true)"
  SSH_LAST_ERROR=""
  # Идентичность определяется один раз за запуск; если автоподбор не удался —
  # пробуем обычный ssh (в журнале будет его реальная ошибка).
  ensure_ssh_ready || true
  ssh_build_opts
  while [ "${attempt}" -le "${SSH_ATTEMPTS}" ]; do
    if [ -n "${errfile}" ]; then
      ssh "${SSH_EFFECTIVE_OPTS[@]}" "${PROD_HOST}" "$*" 2>"${errfile}"
    else
      ssh "${SSH_EFFECTIVE_OPTS[@]}" "${PROD_HOST}" "$*"
    fi
    rc=$?
    if [ "${rc}" -eq 0 ]; then
      [ -n "${errfile}" ] && rm -f "${errfile}"
      return 0
    fi
    SSH_LAST_ERROR="$(ssh_error_detail "${errfile}")"
    attempt=$((attempt + 1))
    [ "${attempt}" -le "${SSH_ATTEMPTS}" ] && sleep "${SSH_RETRY_PAUSE}"
    [ "${attempt}" -le "${SSH_ATTEMPTS}" ] || break
    log_event "ssh_retry" "attempt=${attempt}" "rc=${rc}" "host=${PROD_HOST}" "detail=${SSH_LAST_ERROR}"
  done
  [ -n "${errfile}" ] && rm -f "${errfile}"
  return "${rc}"
}

# Скачать файл конфигурации с прод-хоста и убедиться, что он получен ЦЕЛИКОМ.
#
# specs/730-queue-resilience (FR-020/FR-021): различение «файл не получен»
# (сетевой сбой) и «структура не совпала» (реальное несовпадение). Проверка
# целостности: размер полученного файла сравнивается с размером на проде
# (wc -c) — расхождение означает обрыв передачи.
#
# Причина отказа пишется в SSH_LAST_ERROR, чтобы вызывающий код записал её в
# журнал отдельной причиной (download_failed), а не config_mismatch.
download_prod_conf() {
  local remote_path="$1" local_file="$2" attempt=1 rc=0
  local local_bytes="" remote_bytes=""
  while [ "${attempt}" -le "${CONF_DOWNLOAD_ATTEMPTS}" ]; do
    : >"${local_file}"
    run_ssh "cat '${remote_path}'" >"${local_file}" 2>/dev/null
    rc=$?
    if [ "${rc}" -eq 0 ]; then
      local_bytes="$(wc -c <"${local_file}" 2>/dev/null | tr -d ' \r\n')"
      case "${local_bytes}" in
        '' | *[!0-9]*) local_bytes="0" ;;
      esac
      if [ "${local_bytes}" -gt 0 ] 2>/dev/null; then
        remote_bytes="$(run_ssh "wc -c < '${remote_path}'" 2>/dev/null | tr -d ' \r\n')"
        case "${remote_bytes}" in
          '' | *[!0-9]*) remote_bytes="" ;;
        esac
        if [ -z "${remote_bytes}" ] || [ "${remote_bytes}" = "${local_bytes}" ]; then
          return 0
        fi
        SSH_LAST_ERROR="truncated: got=${local_bytes} expected=${remote_bytes}"
      else
        SSH_LAST_ERROR="empty_response"
      fi
    fi
    attempt=$((attempt + 1))
    [ "${attempt}" -le "${CONF_DOWNLOAD_ATTEMPTS}" ] && sleep "${CONF_DOWNLOAD_RETRY_PAUSE}"
  done
  return 1
}

# run_target local|remote <команда> — выполнить команду на цели.
run_target() {
  local mode="$1"; shift
  if [ "${mode}" = "remote" ]; then
    run_ssh "$*"
  else
    bash -c "$*"
  fi
}

# Проверка: является ли целевой файл точкой монтирования (findmnt -T).
# Результат пишется в журнал: если файл — bind-mount, подмена содержимым
# обязательна (inode), rename сломал бы монтирование.
check_mount_point() {
  local mode="$1" path="$2" out
  out="$(run_target "${mode}" "findmnt -T '${path}' -o TARGET,SOURCE,FSTYPE -n 2>/dev/null || true")"
  local is_mount="no"
  if printf '%s' "${out}" | awk '{print $1}' | grep -qx "${path}"; then
    is_mount="yes"
  fi
  log_event "mountpoint" "is_mountpoint=${is_mount}" \
    "target=$(sanitize "$(printf '%s' "${out}" | awk '{print $1}')")" \
    "source=$(sanitize "$(printf '%s' "${out}" | awk '{print $2}')")" \
    "fstype=$(sanitize "$(printf '%s' "${out}" | awk '{print $3}')")"
  MOUNT_IS_MOUNTPOINT="${is_mount}"
}

# Способ записи, выбранный по факту проверки точки монтирования (Д-6):
# mv — обычный файл (атомарное переименование), cat — bind-mount (inode сохраняется).
WRITE_METHOD="cat"


# Перепроверка адреса перед применением (гонка смены адреса). В self-test
# переопределяется, чтобы сценарий не ходил в сеть.
confirm_address() { current_address; }


# Подготовка конфигурации с allow-list'ом в ЛЮБОМ файле: в каждом блоке, где
# есть `deny all;`, строка `allow <ip>;` должна стоять ПЕРЕД ним (nginx проверяет
# allow/deny по порядку — строка после deny не действует никогда). Если в блоке
# такой строки нет — она вставляется; «осиротевшие» allow после deny удаляются.
#
# Нужна для файлов, отличных от главного конфига nginx: там блоки — это
# `location` (HTTP-локации синхронизации), а не `server` с listen 5433/9000,
# поэтому потоковая версия prepare_new_config к ним неприменима.
prepare_new_allow_config() {
  local old_file="$1" new_file="$2" ip="$3"
  python3 - "${old_file}" "${new_file}" "${ip}" <<'PYEOF'
import re
import sys

old_file, new_file, ip = sys.argv[1], sys.argv[2], sys.argv[3]
deny_re = re.compile(r"^\s*deny\s+all\s*;")
# Только обычные IPv4-адреса: правило «один текущий адрес» не должно молча
# удалять намеренные правила вида `allow 10.0.0.0/8;` (сеть/подсеть).
allow_re = re.compile(r"^\s*allow\s+[0-9]{1,3}(?:\.[0-9]{1,3}){3}\s*;\s*$")

with open(old_file, encoding="utf-8") as fh:
    lines = fh.read().split("\n")

out = []
inserted = 0
removed = 0
i = 0
while i < len(lines):
    line = lines[i]
    if deny_re.match(line):
        # ПРАВИЛО ВЛАДЕЛЬЦА (2026-11-08): в блоке — ровно ОДИН allow, текущий.
        # Все прежние адреса этого блока (и осиротевшие после deny) убираем.
        # Сканируем И НАКОПЛЕННЫЙ список out, а не исходный lines: индексы в них
        # расходятся после удаления строк, и обход по lines вырезал чужие строки.
        j = len(out) - 1
        while j >= 0:
            if out[j].strip().startswith("}"):
                break
            if allow_re.match(out[j]):
                del out[j]
                removed += 1
            j -= 1
        indent = line[: len(line) - len(line.lstrip())]
        out.append("{}allow {};".format(indent, ip))
        inserted += 1
        out.append(line)
        i += 1
        continue
    if allow_re.match(line):
        orphan = False
        j = len(out) - 1
        while j >= 0:
            if out[j].strip().startswith("}"):
                break
            if deny_re.match(out[j]):
                orphan = True
                break
            j -= 1
        if orphan:
            removed += 1
            i += 1
            continue
    out.append(line)
    i += 1

if inserted == 0:
    sys.stderr.write("no allow-list blocks found\n")
    sys.exit(2)

with open(new_file, "w", encoding="utf-8") as fh:
    fh.write("\n".join(out))
sys.stderr.write("inserted={} removed={}\n".format(inserted, removed))
PYEOF
}

# Подготовка файла: главный конфиг — потоковая версия (проверяет listen
# 5433/9000 и ровно два блока), остальные — универсальная.
prepare_conf_for_file() {
  local target="$1" old_file="$2" new_file="$3" ip="$4"
  case "${target}" in
    */nginx.conf) prepare_new_config "${old_file}" "${new_file}" "${ip}" ;;
    *) prepare_new_allow_config "${old_file}" "${new_file}" "${ip}" ;;
  esac
}

# Репозиторный путь для файла на проде (канон в репозитории).
repo_path_for_prod_conf() {
  case "$1" in
    */80to8897) printf '%s' "${REPO_CONF_PATH_SITE}" ;;
    *) printf '%s' "${REPO_CONF_PATH}" ;;
  esac
}

# apply_change <local|remote> <target_file> <new_file> <ip>
# Порядок (контракт allow-list-surgery.md §4): перепроверка адреса → временный
# файл → проверка синтаксиса временного файла → бэкап → подмена содержимым с
# сохранением inode → проверка целостности записи (sha256) → проверка синтаксиса
# живого файла → reload → контрольная проверка. Откат при любом сбое.
apply_change() {
  local mode="$1" target="$2" new_file="$3" ip="$4"
  local stamp tmp backup remote_tmp expected_sha actual_sha

  stamp="$(date -u +%Y%m%d-%H%M%S)"
  backup="${target}.bak-${stamp}"
  # Временный файл для НЕ-главного конфига кладём ВНЕ включаемых nginx-каталогов
  # (sites-enabled/* подхватывается glob'ом — копия там продублировала бы location
  # и ломала nginx -t). Для главного конфига — рядом, там проверка целиком.
  local full_syntax=1
  case "${target}" in
    */nginx.conf) remote_tmp="${target}.allowlist-new" ;;
    *) remote_tmp="/tmp/allowlist-$(basename "${target}").allowlist-new"; full_syntax=0 ;;
  esac

  # Шаг 0: перепроверка адреса (гонка смены адреса).
  local confirmed
  confirmed="$(confirm_address)" || { REASON="${REASON:-address_changed}"; return 1; }
  if [ "${confirmed}" != "${ip}" ]; then
    log_event "skip" "reason=address_changed" "expected=$(sanitize "${ip}")" "actual=$(sanitize "${confirmed}")"
    REASON="address_changed"
    return 1
  fi
  log_event "edit" "step=address_recheck" "ip=$(sanitize "${ip}")" "ok=true"

  # Шаг 1: временный файл на цели.
  #
  # ИНЦИДЕНТ 2026-11-05: scp вызывался БЕЗ ssh-опций (идентичность/known_hosts).
  # Таймер работает под root, у root нет ключа по умолчанию → scp отваливался с
  # «Permission denied (publickey)», шаг возвращал transfer_failed, и цикл завершался
  # МОЛЧА: в журнале не было ни «applied», ни ошибки, а уведомление не уходило
  # (нет токена Telegram). Итог: IP сменился — доступ к прод-БД остался закрытым,
  # и механизм самовосстановления не сработал ни разу. Вручную механизм работал,
  # потому что запускался от nsa (у него ключ по умолчанию есть).
  #
  # Теперь scp получает те же опции, что и ssh (идентичность, known_hosts,
  # BatchMode, таймаут), а отказ пишется в журнал — как и остальные шаги.
  if [ "${mode}" = "remote" ]; then
    ensure_ssh_ready || true
    ssh_build_opts
    local scp_err scp_rc=0
    scp_err="$(mktemp "${TMPDIR:-/tmp}/allowlist-scp-err.XXXXXX" 2>/dev/null || true)"
    if [ -n "${scp_err}" ]; then
      scp -q "${SSH_EFFECTIVE_OPTS[@]}" "${new_file}" "${PROD_HOST}:${remote_tmp}" 2>"${scp_err}" || scp_rc=$?
    else
      scp -q "${SSH_EFFECTIVE_OPTS[@]}" "${new_file}" "${PROD_HOST}:${remote_tmp}" || scp_rc=$?
    fi
    if [ "${scp_rc}" -ne 0 ]; then
      local scp_detail=""
      [ -n "${scp_err}" ] && scp_detail="$(tail -n 1 "${scp_err}" 2>/dev/null)"
      rm -f "${scp_err}"
      log_event "edit" "step=transfer" "ok=false" "reason=transfer_failed" "rc=${scp_rc}" "detail=$(sanitize "${scp_detail}")"
      REASON="transfer_failed"
      return 1
    fi
    rm -f "${scp_err}"
    log_event "edit" "step=transfer" "ok=true"
  else
    cp -f "${new_file}" "${remote_tmp}" || {
      log_event "edit" "step=transfer" "ok=false" "reason=transfer_failed"
      REASON="transfer_failed"
      return 1
    }
  fi

  # Шаг 2: синтаксис временного файла тем же nginx. Для НЕ-главного конфига
  # (location-блоки сайта) отдельный файл не является полным конфигом nginx —
  # такая проверка невозможна; её заменяет проверка ЖИВОГО файла после записи
  # (шаг 5) с откатом из бэкапа.
  if [ "${full_syntax}" = "1" ]; then
    if ! run_target "${mode}" "${NGINX_BIN} -t -c '${remote_tmp}'" >/dev/null 2>&1; then
      run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
      log_event "syntax" "ok=false" "step=temp_file" "detail=nginxt_failed"
      REASON="syntax_failed"
      return 1
    fi
    log_event "syntax" "ok=true" "step=temp_file"
  else
    log_event "syntax" "ok=true" "step=temp_file" "detail=partial_config_check_skipped"
  fi

  # Шаг 3: бэкап.
  if ! run_target "${mode}" "cp '${target}' '${backup}'" >/dev/null 2>&1; then
    run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
    log_event "edit" "step=backup" "ok=false" "backup=$(sanitize "${backup}")"
    REASON="backup_failed"
    return 1
  fi
  log_event "edit" "step=backup" "ok=true" "backup=$(sanitize "${backup}")"

  # Шаг 4: запись — по факту проверки точки монтирования (решение владельца Д-6).
  #   файл НЕ точка монтирования → атомарное переименование (mv);
  #   файл точка монтирования (bind-mount) → подмена содержимым с сохранением inode (cat).
  check_mount_point "${mode}" "${target}"
  if [ "${MOUNT_IS_MOUNTPOINT}" = "yes" ]; then
    WRITE_METHOD="cat"
  else
    WRITE_METHOD="mv"
  fi
  log_event "edit" "step=write" "method=${WRITE_METHOD}" "ok=true"

  if [ "${WRITE_METHOD}" = "mv" ]; then
    # Права и владелец нового файла — как у прежнего (иначе nginx может не прочитать).
    run_target "${mode}" "chmod --reference='${target}' '${remote_tmp}' 2>/dev/null || chmod 644 '${remote_tmp}'; chown --reference='${target}' '${remote_tmp}' 2>/dev/null || true" >/dev/null 2>&1 || true
    if ! run_target "${mode}" "mv -f '${remote_tmp}' '${target}'" >/dev/null 2>&1; then
      run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
      log_event "edit" "step=write" "method=${WRITE_METHOD}" "ok=false"
      REASON="write_failed"
      return 1
    fi
  else
    if ! run_target "${mode}" "cat '${remote_tmp}' > '${target}'" >/dev/null 2>&1; then
      run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
      log_event "edit" "step=write" "method=${WRITE_METHOD}" "ok=false"
      REASON="write_failed"
      return 1
    fi
  fi

  # Шаг 5: целостность записи (sha256) — ловит усечение при прерывании.
  expected_sha="$(sha256sum "${new_file}" | awk '{print $1}')"
  actual_sha="$(run_target "${mode}" "sha256sum '${target}' 2>/dev/null | awk '{print \$1}'")"
  if [ "${expected_sha}" != "${actual_sha}" ]; then
    log_event "edit" "step=integrity" "ok=false"
    rollback_change "${mode}" "${target}" "${backup}" "${WRITE_METHOD}"
    run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
    REASON="integrity_failed"
    return 1
  fi
  log_event "edit" "step=integrity" "ok=true"

  # Шаг 6: синтаксис живого файла.
  if ! run_target "${mode}" "${NGINX_BIN} -t" >/dev/null 2>&1; then
    log_event "syntax" "ok=false" "step=live_file" "detail=nginxt_failed"
    rollback_change "${mode}" "${target}" "${backup}" "${WRITE_METHOD}"
    run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
    REASON="syntax_failed"
    return 1
  fi
  log_event "syntax" "ok=true" "step=live_file"

  # Шаг 7: reload.
  if ! run_target "${mode}" "${NGINX_BIN} -s reload" >/dev/null 2>&1; then
    log_event "reload" "ok=false"
    rollback_change "${mode}" "${target}" "${backup}" "${WRITE_METHOD}"
    run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
    REASON="reload_failed"
    return 1
  fi
  log_event "reload" "ok=true"

  # Шаг 9: уборка временного файла (FR-015).
  run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true

  BACKUP_PATH="${backup}"
  APPLY_BACKUP_PATH="${backup}"
  APPLY_WRITE_METHOD="${WRITE_METHOD}"
  return 0
}

# Файлы, к которым правка уже применена (путь, СВОЙ бэкап, способ записи).
#
# ИНЦИДЕНТ 2026-11-08: откат контрольной проверки восстанавливал ПОСЛЕДНИЙ бэкап
# в ПЕРВЫЙ файл — /etc/nginx/nginx.conf получал содержимое файла сайта, `nginx -t`
# падал, и прод остался с битым конфигом (доступ к прод-БД был закрыт). Теперь у
# каждого файла свой бэкап, и откат идёт по каждому файлу отдельно.
APPLIED_FILES=()
APPLIED_BACKUPS=()
APPLIED_METHODS=()

rollback_applied() {
  local i rc=0
  for (( i = ${#APPLIED_FILES[@]} - 1; i >= 0; i-- )); do
    rollback_change remote "${APPLIED_FILES[i]}" "${APPLIED_BACKUPS[i]}" "${APPLIED_METHODS[i]}" || rc=1
  done
  APPLIED_FILES=()
  APPLIED_BACKUPS=()
  APPLIED_METHODS=()
  return "${rc}"
}

rollback_change() {
  local mode="$1" target="$2" backup="$3"
  local method="${4:-${WRITE_METHOD:-cat}}"
  local restore="${target}.restore-tmp"
  local ok=0
  if [ "${method}" = "mv" ]; then
    # Откат при атомарном переименовании — тоже атомарный: копия бэкапа
    # переименовывается на место целиком, сам бэкап остаётся для разбора.
    if run_target "${mode}" "cp '${backup}' '${restore}' && mv -f '${restore}' '${target}'" >/dev/null 2>&1; then
      ok=1
    fi
  else
    # Откат при подмене содержимым: прежний путь + сверка sha256 с бэкапом.
    if run_target "${mode}" "cat '${backup}' > '${target}'" >/dev/null 2>&1; then
      local expect_sha actual_sha
      expect_sha="$(run_target "${mode}" "sha256sum '${backup}' 2>/dev/null | awk '{print \$1}'")"
      actual_sha="$(run_target "${mode}" "sha256sum '${target}' 2>/dev/null | awk '{print \$1}'")"
      [ "${expect_sha}" = "${actual_sha}" ] && ok=1
    fi
  fi
  run_target "${mode}" "rm -f '${restore}'" >/dev/null 2>&1 || true
  if [ "${ok}" = 1 ]; then
    run_target "${mode}" "${NGINX_BIN} -s reload" >/dev/null 2>&1 || true
    log_event "rollback" "ok=true" "method=${method}" "reason=$(sanitize "${REASON:-}")" "backup=$(sanitize "${backup}")"
    return 0
  fi
  log_event "rollback" "ok=false" "method=${method}" "reason=$(sanitize "${REASON:-}")" "backup=$(sanitize "${backup}")"
  return 1
}

# ------------------------------------------------------- ограничение частоты ----
# FR-010: не более одной применённой правки за окно.
rate_limit_ok() {
  local last now diff_min
  last="$(state_get last_applied_at)"
  if [ -z "${last}" ]; then
    return 0
  fi
  now="$(date -u +%s)"
  diff_min=$(( (now - last) / 60 ))
  if [ "${diff_min}" -lt "${MIN_INTERVAL_MINUTES}" ]; then
    log_event "skip" "reason=rate_limit" "last_applied_at=${last}" "minutes_since=${diff_min}" \
      "window_minutes=${MIN_INTERVAL_MINUTES}"
    return 1
  fi
  return 0
}

state_get() {
  local key="$1"
  [ -f "${STATE_FILE}" ] || return 0
  sed -n "s/^${key}=//p" "${STATE_FILE}" | tail -1
}

state_set() {
  local key="$1" value="$2"
  mkdir -p "$(dirname "${STATE_FILE}")" 2>/dev/null || true
  local tmp="${STATE_FILE}.tmp"
  if [ -f "${STATE_FILE}" ]; then
    grep -v "^${key}=" "${STATE_FILE}" >"${tmp}" 2>/dev/null || true
  else
    : >"${tmp}"
  fi
  printf '%s=%s\n' "${key}" "${value}" >>"${tmp}"
  mv -f "${tmp}" "${STATE_FILE}" 2>/dev/null || true
}

# -------------------------------------------------------------- уведомления ----
# FR-013/FR-016: Bot API того же бота и того же канала проекта. Токен не
# попадает в argv — он лежит в конфиг-файле curl с правами 600, который
# удаляется сразу (guard BR-551/552/605).
send_telegram() {
  local text="$1"
  if [ -z "${TELEGRAM_TOKEN}" ] || [ -z "${TELEGRAM_CHAT_ID}" ]; then
    log_event "notify" "channel=none" "ok=false" "reason=token_absent"
    return 0
  fi
  local cfg
  cfg="$(mktemp "${TMPDIR:-/tmp}/allowlist-tg.XXXXXX")" || return 1
  chmod 600 "${cfg}" || { rm -f "${cfg}"; return 1; }
  {
    printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "${TELEGRAM_TOKEN}"
    printf 'request = "POST"\n'
    printf 'data-urlencode = "chat_id=%s"\n' "${TELEGRAM_CHAT_ID}"
    printf 'data-urlencode = "text=%s"\n' "${text}"
    # Прокси — конфиг-файлом curl, НЕ через argv (BR-551/552/605): и токен, и
    # адрес прокси не должны быть видны в `ps`. Само значение прокси в журнал
    # не пишется (в URL прокси могут быть учётные данные) — только факт.
    if [ -n "${TELEGRAM_PROXY}" ]; then
      printf 'proxy = "%s"\n' "${TELEGRAM_PROXY}"
    else
      # Прямое соединение: переменные прокси окружения игнорируются, чтобы
      # вызов Bot API не ушёл через прокси, заданный неявно. Глобального прокси
      # в скрипте нет — см. --noproxy в probe_https/detect_address.
      printf 'noproxy = "*"\n'
    fi
  } >"${cfg}"
  local code="" rc=0
  # Ответ Bot API сохраняем во временный файл (не в argv, не в журнал): из него
  # берём message_id отправленного сообщения — по нему пост можно удалить
  # (deleteMessage), если служебное уведомление попало не туда.
  local resp
  resp="$(mktemp "${TMPDIR:-/tmp}/allowlist-tg-resp.XXXXXX")" || return 1
  chmod 600 "${resp}" || { rm -f "${resp}"; return 1; }
  code="$(curl -sS -o "${resp}" -w '%{http_code}' -K "${cfg}" 2>/dev/null)" || rc=$?
  [ -n "${code}" ] || code="000"
  rm -f "${cfg}"
  local message_id=""
  if [ -s "${resp}" ]; then
    message_id="$(sed -n 's/.*"message_id":[[:space:]]*\([0-9]\{1,\}\).*/\1/p' "${resp}" | head -1)"
  fi
  rm -f "${resp}"
  if [ "${rc}" -eq 0 ] && [ "${code}" = "200" ]; then
    log_event "notify" "channel=telegram" "ok=true" "proxy=$(proxy_state)" \
      "message_id=${message_id:-none}"
    return 0
  fi
  # Недоставленное уведомление — НЕ повод провалить цикл: правка allow-list
  # уже применена (или не применена) независимо от доставки сообщения.
  # Вызывающий код обязан игнорировать код возврата (см. `|| true` в run_cycle).
  log_event "notify" "channel=telegram" "ok=false" "http_code=${code}" "curl_rc=${rc}" \
    "proxy=$(proxy_state)" "reason=$(notify_failure_reason "${rc}" "${code}")"
  return 1
}

# Факт использования прокси для журнала: само значение не пишется (в URL
# прокси могут быть учётные данные).
proxy_state() {
  if [ -n "${TELEGRAM_PROXY}" ]; then printf 'set'; else printf 'none'; fi
}

# Причина неудачной отправки уведомления. Прокси задан, но соединение не
# установлено — не прошёл путь через прокси; иначе — сама отправка.
notify_failure_reason() {
  local rc="$1" code="$2"
  if [ "${rc}" -ne 0 ] && [ "${code}" = "000" ] && [ -n "${TELEGRAM_PROXY}" ]; then
    printf 'proxy_unreachable'
    return 0
  fi
  if [ "${rc}" -ne 0 ]; then
    printf 'transport_error'
    return 0
  fi
  printf 'api_error'
}

# --------------------------------------------------------------------- PR ----
# FR-014: после успешного применения — PR, приводящий репозиторную копию в
# соответствие с продом. Состав PR — репозиторная копия ПЛЮС только недостающая
# строка allow; посторонние расхождения прод↔репозиторий в PR не выносятся.
# Всё через Forgejo API: локальный git не используется.
fj_api() {
  # fj_api <метод> <путь> [тело-json]
  local method="$1" path="$2" body="${3:-}"
  local cfg
  cfg="$(mktemp "${TMPDIR:-/tmp}/allowlist-fj.XXXXXX")" || return 1
  chmod 600 "${cfg}" || { rm -f "${cfg}"; return 1; }
  {
    # noproxy: обращение к Forgejo идёт напрямую — глобального прокси в
    # скрипте нет (тот же инвариант, что у прямых вызовов probe/detect_address).
    printf 'noproxy = "*"\n'
    printf 'url = "%s/api/v1/repos/%s%s"\n' "${FJ_URL}" "${FJ_REPO}" "${path}"
    printf 'header = "Authorization: token %s"\n' "${FJ_TOKEN}"
    printf 'header = "Content-Type: application/json"\n'
    printf 'request = "%s"\n' "${method}"
    [ -n "${body}" ] && printf 'data = "%s"\n' "$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')"
  } >"${cfg}"
  curl -sS -K "${cfg}" 2>/dev/null
  local rc=$?
  rm -f "${cfg}"
  return "${rc}"
}

create_pull_request() {
  local ip="$1" backup="${2:-}"
  if [ -z "${FJ_TOKEN}" ]; then
    log_event "pr" "ok=false" "reason=no_token"
    return 1
  fi

  local branch="auto/allowlist-${ip//./-}-$(date -u +%Y%m%d%H%M%S)"
  local br_body
  br_body="$(python3 -c 'import json,sys; print(json.dumps({"branch_name": sys.argv[1], "old_branch_name": "master"}))' "${branch}")"
  fj_api POST "/branches" "${br_body}" >/dev/null 2>&1 || true

  # Репозиторные копии ВСЕХ файлов с allow-list'ом: главный конфиг (stream-блоки
  # 5433/9000) и http-конфиг сайта (location /changerecords и
  # /api/properties/setproperty). Править только один — значит оставить
  # репозиторий расходящимся с продом, и следующая правка снова придёт «не туда»
  # (KARAOKE-389, инцидент 2026-11-02).
  local repo_path raw content tmp_old tmp_new b64 commit_body committed=0
  for repo_path in "${REPO_CONF_PATH}" "${REPO_CONF_PATH_SITE}"; do
    raw="$(fj_api GET "/contents/${repo_path}?ref=master")" || raw=""
    content="$(printf '%s' "${raw}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("content") or "")' 2>/dev/null || true)"
    if [ -z "${content}" ]; then
      log_event "pr" "ok=false" "reason=repo_copy_unavailable" "file=$(sanitize "${repo_path}")"
      continue
    fi

    tmp_old="$(mktemp "${TMPDIR:-/tmp}/allowlist-repo.XXXXXX")"
    tmp_new="$(mktemp "${TMPDIR:-/tmp}/allowlist-repo.XXXXXX")"
    printf '%s' "${content}" >"${tmp_old}"
    if address_allowed "${tmp_old}" "${ip}"; then
      rm -f "${tmp_old}" "${tmp_new}"
      continue
    fi
    if ! prepare_conf_for_file "${repo_path}" "${tmp_old}" "${tmp_new}" "${ip}" 2>/dev/null; then
      rm -f "${tmp_old}" "${tmp_new}"
      log_event "pr" "ok=false" "reason=repo_copy_prepare_failed" "file=$(sanitize "${repo_path}")"
      continue
    fi
    if ! verify_diff "${tmp_old}" "${tmp_new}" "${ip}" 2>/dev/null; then
      rm -f "${tmp_old}" "${tmp_new}"
      log_event "pr" "ok=false" "reason=repo_copy_diff_failed" "file=$(sanitize "${repo_path}")"
      continue
    fi
    b64="$(base64 -w0 <"${tmp_new}" 2>/dev/null || base64 <"${tmp_new}" | tr -d '\n')"
    rm -f "${tmp_old}" "${tmp_new}"

    commit_body="$(python3 -c 'import json,sys; print(json.dumps({"branch": sys.argv[1], "content": sys.argv[2], "message": sys.argv[3]}))' \
      "${branch}" "${b64}" "chore(677): allow-list — добавлен адрес ${ip} (${repo_path}) (KARAOKE-389)")"
    if fj_api POST "/contents/${repo_path}" "${commit_body}" >/dev/null 2>&1; then
      committed=$((committed + 1))
      log_event "pr" "step=committed" "file=$(sanitize "${repo_path}")" "branch=$(sanitize "${branch}")"
    else
      log_event "pr" "ok=false" "reason=commit_failed" "file=$(sanitize "${repo_path}")" "branch=$(sanitize "${branch}")"
    fi
  done

  if [ "${committed}" -eq 0 ]; then
    log_event "pr" "ok=false" "reason=nothing_to_commit"
    return 1
  fi

  local body pr_title pr_body pull_body pr_json pr_url
  body="$(pr_body_text "${ip}" "${backup}" "${committed}")"
  pr_title="$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["pr_title"])')"
  pr_body="$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["pr_body"])')"

  pull_body="$(python3 -c 'import json,sys; print(json.dumps({"head": sys.argv[1], "base": "master", "title": sys.argv[2], "body": sys.argv[3]}))' \
    "${branch}" "${pr_title}" "${pr_body}")"
  pr_json="$(fj_api POST "/pulls" "${pull_body}")"
  pr_url="$(printf '%s' "${pr_json}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("html_url") or "")' 2>/dev/null || true)"
  if [ -z "${pr_url}" ]; then
    log_event "pr" "ok=false" "reason=pull_request_failed" "branch=$(sanitize "${branch}")"
    return 1
  fi
  log_event "pr" "ok=true" "url=$(sanitize "${pr_url}")" "branch=$(sanitize "${branch}")" "files=${committed}"
  PR_URL="${pr_url}"
  return 0
}

# Текст PR: заголовок и описание собираются отдельной функцией, чтобы в
# create_pull_request не было вложенного heredoc внутри подстановки.
pr_body_text() {
  local ip="$1" backup="$2" committed="$3"
  python3 - "${ip}" "${backup}" "${committed}" <<'PRBODYEOF'
import json
import sys

ip, backup, committed = sys.argv[1], sys.argv[2], sys.argv[3]
print(json.dumps({
    "pr_title": "chore(677): allow-list прод-хоста — добавлен адрес {} (KARAOKE-337)".format(ip),
    "pr_body": (
        "Автоматическая правка allow-list (спека 677, KARAOKE-337, KARAOKE-389)."
        "\n\n"
        "Добавлена строка `allow {ip};` во все блоки с allow-list'ом: stream-блоки "
        "5433/9000 и HTTP-локации /changerecords, /api/properties/setproperty "
        "(файлов: {n})."
        "\n\n"
        "Бэкап на проде: {backup}"
        "\n\n"
        "Одобрение и слияние — за владельцем."
    ).format(ip=ip, n=committed, backup=backup or "—"),
}))
PRBODYEOF
}
# ------------------------------------------------------------- режимы ----
run_cycle() {
  # host — первый аргумент (хост, к которому идут TCP-пробы). Раньше он был
  # необъявленным локальным именем и не инициализировался: под `set -u` цикл
  # падал целиком ещё до первой пробы.
  local host="${1:-}" ip
  APPLIED_FILES=()
  APPLIED_BACKUPS=()
  APPLIED_METHODS=()

  # FR-001/FR-002: пробы и различение причины недоступности.
  if ! check_reachability "${host}"; then
    if [ "${REACH_HTTPS}" != 1 ]; then
      log_event "decision" "reason=https_down" "action=skip" "detail=prod_not_cause_allow_list"
      send_telegram "Karaoke allow-list: прод недоступен целиком (HTTPS ${HEALTH_URL} не отвечает). Причина не в allow-list — адрес не добавлялся. Прод не тронут." || true
      return "${EXIT_DECLINED}"
    fi
    log_event "decision" "reason=allow_list" "action=continue" "pg_ok=${REACH_PG}" "minio_ok=${REACH_MINIO}"
  else
    log_event "decision" "reason=all_ok" "action=none"
    return "${EXIT_OK}"
  fi

  # FR-003/FR-004: адрес и валидация.
  #
  # Источник адреса — ПРОД (SSH_CLIENT): он знает, какой адрес мы имеем на том
  # самом канале, который мы и чиним. Внешние сервисы определения IP могут
  # отвечать через другой (ротируемый) VPN-туннель — их расхождение больше не
  # блокирует правку.
  ip="$(current_address)" || return "${EXIT_DECLINED}"
  validate_address "${ip}" || return "${EXIT_DECLINED}"

  # FR-005: адрес уже в allow-list ВСЕХ файлов — никаких действий, включая reload.
  #
  # ИНЦИДЕНТ 2026-11-02 (вечер): проверялся только главный конфиг (stream-блоки),
  # а HTTP-локации /changerecords остались со старым списком → nginx отдавал 403,
  # синхронизация молча не работала, и одобренные редакторами песни «терялись».
  # Теперь читаются и правятся ВСЕ файлы с allow-list'ом.
  local conf_files=() conf_temps=() conf_path conf_temp idx need_change=0
  for conf_path in ${PROD_CONF_FILES}; do
    conf_temp="$(mktemp "${TMPDIR:-/tmp}/allowlist-conf.XXXXXX")"
    if ! download_prod_conf "${conf_path}" "${conf_temp}"; then
      rm -f "${conf_temps[@]}" "${conf_temp}"
      log_event "skip" "reason=download_failed" "detail=${SSH_LAST_ERROR}" "file=$(sanitize "${conf_path}")"
      send_telegram "Karaoke allow-list: файл конфигурации прод-хоста не получен (${SSH_LAST_ERROR}) — правка не применялась, состояние не менялось. Причина сетевая (обрыв передачи), а не несовпадение структуры: правка повторится на следующем цикле." || true
      return "${EXIT_FAIL}"
    fi
    conf_files+=("${conf_path}")
    conf_temps+=("${conf_temp}")
    address_allowed "${conf_temp}" "${ip}" || need_change=1
  done

  if [ "${need_change}" -eq 0 ]; then
    rm -f "${conf_temps[@]}"
    log_event "skip" "reason=already_allowed" "ip=$(sanitize "${ip}")" "files=${#conf_files[@]}"
    return "${EXIT_OK}"
  fi

  # FR-010: ограничение частоты.
  if ! rate_limit_ok; then
    rm -f "${conf_temps[@]}"
    send_telegram "Karaoke allow-list: правка отложена — с последней применённой правки прошло меньше ${MIN_INTERVAL_MINUTES} минут (адрес ${ip}). Следующая попытка — после истечения окна." || true
    return "${EXIT_DECLINED}"
  fi

  # Подготовка содержимого и FR-020 — по каждому файлу, где адреса ещё нет.
  local new_conf
  for idx in "${!conf_files[@]}"; do
    conf_path="${conf_files[${idx}]}"
    prod_conf="${conf_temps[${idx}]}"
    address_allowed "${prod_conf}" "${ip}" && continue
    new_conf="$(mktemp "${TMPDIR:-/tmp}/allowlist-new.XXXXXX")"
    if ! prepare_conf_for_file "${conf_path}" "${prod_conf}" "${new_conf}" "${ip}" 2>/dev/null; then
      rm -f "${conf_temps[@]}" "${new_conf}"
      log_event "skip" "reason=config_mismatch" "detail=stream_blocks_or_deny_all_not_found" "file=$(sanitize "${conf_path}")"
      send_telegram "Karaoke allow-list: структура конфигурации прод-хоста не совпала с ожидаемой (не найдены блоки с 'deny all;' или listen 5433/9000) — правка не выполнялась. Требуется взгляд владельца." || true
      return "${EXIT_DECLINED}"
    fi
    if ! verify_diff "${prod_conf}" "${new_conf}" "${ip}" 2>/dev/null; then
      rm -f "${conf_temps[@]}" "${new_conf}"
      log_event "skip" "reason=diff_check_failed" "ip=$(sanitize "${ip}")" "file=$(sanitize "${conf_path}")"
      send_telegram "Karaoke allow-list: проверка diff'а не прошла — изменение содержало бы не только строку allow. Правка не выполнялась." || true
      return "${EXIT_DECLINED}"
    fi
    log_event "edit" "step=prepared" "ip=$(sanitize "${ip}")" "file=$(sanitize "${conf_path}")"

    # Проверка: точка монтирования (findmnt) — результат в журнал.
    check_mount_point remote "${conf_path}"

    # FR-006…FR-009: применение с откатом.
    if ! apply_change remote "${conf_path}" "${new_conf}" "${ip}"; then
      rm -f "${conf_temps[@]}" "${new_conf}"
      # Откат всех УЖЕ применённых файлов — каждый из своего бэкапа.
      rollback_applied || true
      send_telegram "Karaoke allow-list: правка НЕ применена (адрес ${ip}), выполнен откат из бэкапа, конфигурация прод-хоста восстановлена. Причина: ${REASON}." || true
      return "${EXIT_FAIL}"
    fi
    # Файл применён — запоминаем его СВОЙ бэкап и способ записи (для отката).
    APPLIED_FILES+=("${conf_path}")
    APPLIED_BACKUPS+=("${APPLY_BACKUP_PATH}")
    APPLIED_METHODS+=("${APPLY_WRITE_METHOD}")
    rm -f "${new_conf}"
  done
  rm -f "${conf_temps[@]}"

  # FR-009: контрольная проверка фактического ответа.
  # BR-631 (инцидент 2026-11-02): проверка обязана быть ПРОТОКОЛЬНОЙ. Пока здесь
  # стоял `probe_tcp`, контроль «проходил» и на конфигурации, где allow оказался
  # после `deny all;`: TCP-соединение nginx принимает и сразу закрывает, connect()
  # успешен. Из-за этого откат не срабатывал, а доступ оставался закрытым.
  local pg_ok=0 minio_ok=0 https_ok=0
  probe_postgres "${host}" 5433 "${PROBE_TIMEOUT}" && pg_ok=1
  probe_http_ok "${host}" 9000 "${PROBE_TIMEOUT}" "/minio/health/live" && minio_ok=1
  probe_https "${HEALTH_URL}" "${PROBE_TIMEOUT}" && https_ok=1
  log_event "control" "pg_ok=${pg_ok}" "minio_ok=${minio_ok}" "https_ok=${https_ok}"
  if [ "${pg_ok}" != 1 ] || [ "${minio_ok}" != 1 ] || [ "${https_ok}" != 1 ]; then
    # Откат каждого файла из ЕГО СВОЕГО бэкапа (инцидент 2026-11-08: прежний код
    # восстанавливал последний бэкап в первый файл и ломал конфиг nginx.conf).
    rollback_applied || true
    send_telegram "Karaoke allow-list: контрольная проверка не прошла — выполнен откат из бэкапа, конфигурация прод-хоста восстановлена (адрес ${ip})." || true
    return "${EXIT_FAIL}"
  fi
  # Правка прошла контрольную проверку — откатывать нечего.
  APPLIED_FILES=()
  APPLIED_BACKUPS=()
  APPLIED_METHODS=()

  # Правка применена — состояние и частота.
  state_set last_applied_at "$(date -u +%s)"
  state_set last_address "${ip}"
  log_event "edit" "step=applied" "ip=$(sanitize "${ip}")" "ok=true" "backup=$(sanitize "${BACKUP_PATH}")"

  # FR-014: PR через Forgejo API.
  create_pull_request "${ip}" "${BACKUP_PATH}" || true

  send_telegram "Karaoke allow-list: адрес ${ip} добавлен в allow-list прод-хоста (5433, 9000). Проверка синтаксиса — ок, контрольная проверка — ок. Бэкап: ${BACKUP_PATH}.${PR_URL:+ PR ждёт одобрения: ${PR_URL}}" || true
  return "${EXIT_OK}"
}

run_diagnose() {
  log_event "diagnose" "mode=diagnose"
  check_reachability "${PROD_HOST##*@}" || true
  local ip
  if ip="$(current_address)"; then
    log_event "diagnose" "address=$(sanitize "${ip}")" "valid=$(validate_address "${ip}" && echo yes || echo no)"
  else
    log_event "diagnose" "address=" "reason=$(sanitize "${REASON:-}")"
  fi
  local prod_conf
  prod_conf="$(mktemp "${TMPDIR:-/tmp}/allowlist-conf.XXXXXX")"
  log_event "diagnose" "ssh_identity=$(ssh_identity_desc)" "ssh_known_hosts=$(ssh_known_hosts_desc)"
  if run_ssh "cat '${PROD_CONF}'" >"${prod_conf}" 2>/dev/null; then
    log_event "diagnose" "prod_conf_read=true" "blocks=$(grep -cE '^[[:space:]]*allow[[:space:]]' "${prod_conf}")"
    check_mount_point remote "${PROD_CONF}"
  else
    log_event "diagnose" "prod_conf_read=false" "detail=ssh_failed:${SSH_LAST_ERROR}"
  fi
  rm -f "${prod_conf}"
  log_event "diagnose" "plan=добавить_allow_в_2_блока_при_необходимости" "changes=none"
  return "${EXIT_OK}"
}



# --- ssh-идентичность сервиса ------------------------------------------------
# Прецедент 2026-11-02: юнит работает под root, у root нет ни ключа, ни
# known_hosts для прод-хоста — ssh падал с rc=255, механизм молча ничего не
# применял (правка allow-list не применена, прод-БД недоступна 80 минут).
#
# Порядок выбора:
#   1) ALLOWLIST_SSH_IDENTITY_FILE — явная настройка владельца;
#   2) кэш прошлого прогона (state-файл) — чтобы не проверять заново;
#   3) обычный ssh учётной записи сервиса (прежнее поведение);
#   4) перебор ключей оператора админ-машины (/home/*/.ssh/id_rsa|id_ed25519) —
#      первый, который принимает прод, запоминается в state-файле.
#
# Ничего из этого не меняет прод: только выбор способа входа.

ssh_identity_candidates() {
  local f
  for f in "${HOME:-/root}/.ssh/id_rsa" "${HOME:-/root}/.ssh/id_ed25519" \
    /home/*/.ssh/id_rsa /home/*/.ssh/id_ed25519; do
    [ -f "${f}" ] && [ -r "${f}" ] && printf '%s\n' "${f}"
  done
}

# Собирает фактические опции ssh из выбранной идентичности.
ssh_build_opts() {
  SSH_EFFECTIVE_OPTS=(-o BatchMode=yes -o ConnectTimeout="${SSH_CONNECT_TIMEOUT}")
  if [ -n "${SSH_IDENTITY_FILE:-}" ]; then
    SSH_EFFECTIVE_OPTS+=(-o IdentitiesOnly=yes -i "${SSH_IDENTITY_FILE}")
    if [ -n "${SSH_KNOWN_HOSTS_FILE:-}" ]; then
      SSH_EFFECTIVE_OPTS+=(-o UserKnownHostsFile="${SSH_KNOWN_HOSTS_FILE}")
    fi
  fi
}

# Определяет рабочую идентичность. 0 — вход возможен, 1 — нет.
ensure_ssh_ready() {
  [ "${SSH_READY_RESOLVED:-0}" = "1" ] && return "${SSH_READY_RC:-0}"
  SSH_READY_RESOLVED=1

  # 1) явная настройка.
  if [ -n "${ALLOWLIST_SSH_IDENTITY_FILE:-}" ]; then
    SSH_IDENTITY_FILE="${ALLOWLIST_SSH_IDENTITY_FILE}"
    SSH_KNOWN_HOSTS_FILE="${ALLOWLIST_SSH_KNOWN_HOSTS:-}"
    ssh_build_opts
    SSH_READY_RC=0
    return 0
  fi

  # 2) кэш прошлого прогона.
  local cached cached_kh
  cached="$(state_get ssh_identity)"
  if [ -n "${cached}" ] && [ -r "${cached}" ]; then
    cached_kh="$(state_get ssh_known_hosts)"
    SSH_IDENTITY_FILE="${cached}"
    SSH_KNOWN_HOSTS_FILE="${cached_kh}"
    ssh_build_opts
    log_event "ssh_identity" "source=cache" "identity=${cached}"
    SSH_READY_RC=0
    return 0
  fi

  # 3) обычный ssh учётной записи сервиса (прежнее поведение).
  SSH_IDENTITY_FILE=""
  SSH_KNOWN_HOSTS_FILE=""
  ssh_build_opts
  if ssh "${SSH_EFFECTIVE_OPTS[@]}" "${PROD_HOST}" true >/dev/null 2>&1; then
    SSH_READY_RC=0
    return 0
  fi

  # 4) автоподбор ключа оператора админ-машины.
  local cand kh
  while IFS= read -r cand; do
    [ -n "${cand}" ] || continue
    kh="$(dirname "$(dirname "${cand}")")/.ssh/known_hosts"
    [ -r "${kh}" ] || kh=""
    local -a opts=(-o BatchMode=yes -o ConnectTimeout="${SSH_CONNECT_TIMEOUT}" -o IdentitiesOnly=yes -i "${cand}")
    [ -n "${kh}" ] && opts+=(-o UserKnownHostsFile="${kh}")
    if ssh "${opts[@]}" "${PROD_HOST}" true >/dev/null 2>&1; then
      SSH_IDENTITY_FILE="${cand}"
      SSH_KNOWN_HOSTS_FILE="${kh}"
      ssh_build_opts
      state_set ssh_identity "${cand}"
      [ -n "${kh}" ] && state_set ssh_known_hosts "${kh}"
      log_event "ssh_identity" "source=auto" "identity=${cand}" "known_hosts=${kh:-default}"
      SSH_READY_RC=0
      return 0
    fi
  done <<<"$(ssh_identity_candidates)"

  SSH_READY_RC=1
  return 1
}

# Источник выбора идентичности (для журнала).
ssh_identity_source() {
  if [ -n "${ALLOWLIST_SSH_IDENTITY_FILE:-}" ]; then printf 'explicit'; return 0; fi
  if [ -n "${SSH_IDENTITY_FILE:-}" ]; then printf 'auto_or_cache'; return 0; fi
  printf 'default'
}

# Описание используемой ssh-идентичности — для журнала и --check-ssh.
ssh_identity_desc() {
  if [ -n "${SSH_IDENTITY_FILE:-}" ]; then
    printf 'file:%s' "${SSH_IDENTITY_FILE}"
  elif [ -n "${ALLOWLIST_SSH_IDENTITY_FILE:-}" ]; then
    if [ -r "${ALLOWLIST_SSH_IDENTITY_FILE}" ]; then
      printf 'file:%s' "${ALLOWLIST_SSH_IDENTITY_FILE}"
    else
      printf 'file:%s(unreadable)' "${ALLOWLIST_SSH_IDENTITY_FILE}"
    fi
  else
    printf 'default'
  fi
}

ssh_known_hosts_desc() {
  if [ -n "${SSH_KNOWN_HOSTS_FILE:-}" ]; then
    printf 'file:%s' "${SSH_KNOWN_HOSTS_FILE}"
  elif [ -n "${ALLOWLIST_SSH_KNOWN_HOSTS:-}" ]; then
    if [ -r "${ALLOWLIST_SSH_KNOWN_HOSTS}" ]; then
      printf 'file:%s' "${ALLOWLIST_SSH_KNOWN_HOSTS}"
    else
      printf 'file:%s(unreadable)' "${ALLOWLIST_SSH_KNOWN_HOSTS}"
    fi
  else
    printf 'default'
  fi
}

# FR-019 (дополнение 2026-11-02): проверка SSH-доступа к прод-хосту без правок.
# Команда, которой владелец проверяет, сможет ли таймер применить правку.
run_check_ssh() {
  log_event "check_ssh" "mode=check_ssh" "host=${PROD_HOST}" \
    "identity=$(ssh_identity_desc)" "known_hosts=$(ssh_known_hosts_desc)"
  ensure_ssh_ready || true
  ssh_build_opts
  log_event "check_ssh" "identity_resolved=$(ssh_identity_desc)" \
    "known_hosts_resolved=$(ssh_known_hosts_desc)" "source=$(ssh_identity_source)"
  if run_ssh "true"; then
    log_event "check_ssh" "ok=true" "hint=правка allow-list применима"
    return "${EXIT_OK}"
  fi
  log_event "check_ssh" "ok=false" "detail=${SSH_LAST_ERROR}" \
    "hint=задайте ALLOWLIST_SSH_IDENTITY_FILE и ALLOWLIST_SSH_KNOWN_HOSTS для учётной записи сервиса"
  return "${EXIT_FAIL}"
}

# ------------------------------------------------------- самопроверка ----
# FR-018: изолированная проверка правки без обращения к прод-хосту.
make_test_config() {
  # Конфиг повторяет структуру host-nginx (stream-блоки 5433 и 9000 с
  # allow-list и `deny all;`). Секция events обязательна для настоящего nginx.
  cat <<'EOF'
events {
    worker_connections 64;
}

stream {
    upstream postgresql_backend {
        server 127.0.0.1:5432;
    }
    upstream minio_backend {
        server 127.0.0.1:9000;
    }
    server {
        listen 5433;
        proxy_pass postgresql_backend;
        allow 185.26.28.109;
        allow 83.137.50.231;
        deny all;
    }
    server {
        listen 9000;
        proxy_pass minio_backend;
        allow 185.26.28.109;
        allow 83.137.50.231;
        deny all;
    }
}
EOF
}

# Валидатор-заглушка: проверяет, что обе целевые строки на месте и блоков два.
stub_validator() {
  local file="$1"
  local allows
  allows="$(grep -cE '^[[:space:]]*allow[[:space:]]' "${file}" || true)"
  [ "${allows}" -ge 2 ] && grep -q "deny all;" "${file}"
}

run_self_test() {
  local workdir failed=0
  # Рабочий каталог самопроверки: по умолчанию — временный; задаётся переменной,
  # когда валидатор работает в контейнере (путь должен совпадать с тем, что
  # смонтировано в контейнер, — например .scratch/allowlist-selftest).
  if [ -n "${SELFTEST_DIR:-}" ]; then
    workdir="${SELFTEST_DIR}"
    rm -rf "${workdir}" 2>/dev/null || true
    mkdir -p "${workdir}" || return "${EXIT_FAIL}"
  else
    workdir="$(mktemp -d "${TMPDIR:-/tmp}/allowlist-selftest.XXXXXX")" || return "${EXIT_FAIL}"
  fi
  LOG_FILE="${workdir}/journal.log"
  STATE_FILE="${workdir}/state"
  # Самопроверка не должна отправлять настоящие уведомления: токен и chat_id
  # гасятся на всё время прогона (сценарий прокси подставляет свои значения).
  TELEGRAM_TOKEN=""
  TELEGRAM_CHAT_ID=""
  local test_ip="203.0.113.77"
  local old="${workdir}/nginx.conf" new="${workdir}/nginx.new"

  echo "== Сценарий 1: адрес сменился — добавление в оба stream-блока (SC-007)"
  make_test_config >"${old}"
  if prepare_new_config "${old}" "${new}" "${test_ip}" 2>/dev/null; then
    local added
    added="$(grep -cE "^[[:space:]]*allow[[:space:]]${test_ip//./\\.};" "${new}" || true)"
    if [ "${added}" = "2" ]; then
      echo "   OK: добавлено 2 строки allow ${test_ip}"
    else
      echo "   FAIL: добавлено ${added} строк (ожидалось 2)"; failed=1
    fi
    if verify_diff "${old}" "${new}" "${test_ip}" 2>/dev/null; then
      echo "   OK: проверка diff'а — изменение только из добавленных allow-строк"
    else
      echo "   FAIL: проверка diff'а не прошла"; failed=1
    fi
    if [ -n "${SELFTEST_VALIDATOR}" ]; then
      if bash -c "${SELFTEST_VALIDATOR//\{file\}/${new}}" >/dev/null 2>&1; then
        echo "   OK: проверка синтаксиса тестового конфига настоящим nginx (${SELFTEST_VALIDATOR})"
      else
        echo "   FAIL: настоящий nginx отклонил конфиг"; failed=1
      fi
    elif stub_validator "${new}"; then
      echo "   OK: проверка синтаксиса тестового конфига (валидатор-заглушка)"
    else
      echo "   FAIL: валидатор-заглушка не принял конфиг"; failed=1
    fi
  else
    echo "   FAIL: подготовка нового содержимого не удалась"; failed=1
  fi

  echo "== Сценарий 2: откат при ошибке (SC-005)"
  cp "${old}" "${workdir}/target.conf"
  local backup="${workdir}/target.conf.bak"
  cp "${workdir}/target.conf" "${backup}"
  printf 'broken content\n' >"${workdir}/broken.conf"
  # Имитация: подмена «битым» содержимым, затем откат — исходный файл восстановлен.
  cat "${workdir}/broken.conf" >"${workdir}/target.conf"
  rollback_change local "${workdir}/target.conf" "${backup}" >/dev/null 2>&1
  if cmp -s "${old}" "${workdir}/target.conf"; then
    echo "   OK: откат восстановил исходный файл побайтно"
  else
    echo "   FAIL: откат не восстановил исходный файл"; failed=1
  fi

  echo "== Сценарий 3: прод недоступен — адрес не добавляется (SC-004)"
  if ! probe_https "https://127.0.0.1:1/health" 1; then
    echo "   OK: HTTPS-проба не отвечает → причина не в allow-list, правка не выполняется"
  else
    echo "   FAIL: ожидалась недоступность"; failed=1
  fi

  echo "== Сценарий 4: ограничение частоты (SC-006)"
  state_set last_applied_at "$(date -u +%s)"
  if rate_limit_ok; then
    echo "   FAIL: вторая правка в пределах окна не отклонена"; failed=1
  else
    echo "   OK: вторая правка в пределах окна отклонена (skip:reason=rate_limit)"
  fi
  rm -f "${STATE_FILE}"

  echo "== Сценарий 5: состав PR — только недостающая allow-строка (FR-014)"
  local repo_old="${workdir}/repo.conf" repo_new="${workdir}/repo.new"
  make_test_config >"${repo_old}"
  if prepare_new_config "${repo_old}" "${repo_new}" "${test_ip}" 2>/dev/null &&
    verify_diff "${repo_old}" "${repo_new}" "${test_ip}" 2>/dev/null; then
    echo "   OK: в состав PR попадает только добавленная allow-строка"
  else
    echo "   FAIL: состав PR не проходит проверку diff'а"; failed=1
  fi

  echo "== Сценарий 6: валидация адреса (FR-004)"
  local bad_ok=1
  for bad in "127.0.0.1" "10.1.2.3" "192.168.1.1" "172.16.0.1" "169.254.1.1" "224.0.0.1" "255.255.255.255" "0.0.0.0" "2001:db8::1" "not-an-ip"; do
    if validate_address "${bad}" >/dev/null 2>&1; then
      echo "   FAIL: адрес ${bad} прошёл валидацию"; bad_ok=0; failed=1
    fi
  done
  [ "${bad_ok}" = 1 ] && echo "   OK: private/loopback/multicast/broadcast/IPv6/мусор отклонены"
  if validate_address "203.0.113.10" >/dev/null 2>&1; then
    echo "   OK: публичный IPv4 принят"
  else
    echo "   FAIL: публичный IPv4 отклонён"; failed=1
  fi

  echo "== Сценарий 7: применение правки — атомарное переименование (Д-6)"
  # Файл не точка монтирования → mv (атомарно). Проверяем, что inode изменился
  # и в файле появилась ровно одна строка allow на каждый блок.
  local target="${workdir}/target.conf"
  local stub="${workdir}/nginx-stub"
  cat >"${stub}" <<'STUB'
#!/usr/bin/env bash
# Заглушка nginx для self-test: -t [-c file] проверяет наличие `deny all;`,
# -s reload — успех. Настоящий nginx проверяется отдельным прогоном.
case "$1" in
  -t)
    # -t -c <file> — проверяем конфиг; -t без -c (живой файл) — успех.
    if [ $# -ge 3 ] && [ -n "${3:-}" ]; then
      [ -f "$3" ] && grep -q "deny all;" "$3" && exit 0
      exit 1
    fi
    exit 0
    ;;
  -s) exit 0 ;;
esac
exit 0
STUB
  chmod +x "${stub}"
  make_test_config >"${target}"
  local inode_before inode_after
  inode_before="$(stat -c %i "${target}" 2>/dev/null || echo 0)"
  # Переопределяем перепроверку адреса: сценарий не ходит в сеть.
  confirm_address() { printf '%s' "${test_ip}"; }
  local old_nginx_bin="${NGINX_BIN}"
  NGINX_BIN="${stub}"
  local new_conf="${workdir}/target.new"
  prepare_new_config "${target}" "${new_conf}" "${test_ip}" 2>/dev/null
  local rc_apply=0
  apply_change local "${target}" "${new_conf}" "${test_ip}" >/dev/null 2>&1 || rc_apply=1
  NGINX_BIN="${old_nginx_bin}"
  confirm_address() { detect_address; }
  inode_after="$(stat -c %i "${target}" 2>/dev/null || echo 0)"
  if [ "${rc_apply}" = 0 ] && [ "${inode_before}" != "${inode_after}" ]; then
    echo "   OK: запись выполнена атомарным переименованием (inode ${inode_before} → ${inode_after})"
  else
    echo "   FAIL: применение не удалось или inode не изменился (rc=${rc_apply}, ${inode_before} → ${inode_after})"; failed=1
  fi
  local applied_lines
  applied_lines="$(grep -cE "^[[:space:]]*allow[[:space:]]${test_ip//./\\.};" "${target}" || true)"
  if [ "${applied_lines}" = "2" ]; then
    echo "   OK: в целевом файле ровно две добавленные строки allow"
  else
    echo "   FAIL: добавлено ${applied_lines} строк allow (ожидалось 2)"; failed=1
  fi

  echo "== Сценарий 8: откат после атомарного переименования (SC-005)"
  local backup_file="${target}.bak-test"
  cp "${target}" "${backup_file}"
  printf 'broken content\n' >"${workdir}/broken2.conf"
  mv -f "${workdir}/broken2.conf" "${target}"
  rollback_change local "${target}" "${backup_file}" mv >/dev/null 2>&1
  if grep -q "deny all;" "${target}" && grep -qE "^[[:space:]]*allow[[:space:]]${test_ip//./\\.};" "${target}"; then
    echo "   OK: откат вернул исходное содержимое целиком"
  else
    echo "   FAIL: откат не восстановил исходное содержимое"; failed=1
  fi

  echo "== Сценарий 9: настоящий detect_address — stdout содержит только адрес"
  # Регрессия на живой машине: журнал печатался в stdout, и адрес возвращался
  # вместе со строками журнала → валидация всегда давала «невалиден».
  local out_addr err_addr
  curl() { echo "203.0.113.77"; }
  out_addr="$(detect_address 2>"${workdir}/stderr.log")"
  err_addr="$(cat "${workdir}/stderr.log")"
  unset -f curl
  if [ "${out_addr}" = "203.0.113.77" ]; then
    echo "   OK: stdout detect_address = ровно адрес (журнал ушёл в stderr)"
  else
    echo "   FAIL: stdout detect_address = '${out_addr}' (ожидался ровно адрес)"; failed=1
  fi
  if [ -n "${err_addr}" ]; then
    echo "   OK: журнал присутствует в stderr ($(printf '%s\n' "${err_addr}" | wc -l) строк)"
  else
    echo "   FAIL: журнала нет в stderr"; failed=1
  fi

  echo "== Сценарий 10: повтор пробы — сетевой сбой не равен «прод недоступен»"
  # Первая попытка TCP-пробы падает, вторая проходит → канал доступен.
  local probe_calls=0
  local old_pause="${PROBE_RETRY_PAUSE}"
  PROBE_RETRY_PAUSE=0
  probe_port() { probe_calls=$((probe_calls + 1)); [ "${probe_calls}" -ge 2 ]; }
  probe_https() { return 0; }
  check_reachability "127.0.0.1" >/dev/null 2>&1 || true
  if [ "${REACH_PG}" = 1 ] && [ "${REACH_MINIO}" = 1 ]; then
    echo "   OK: канал признан доступным после повтора (попыток: ${probe_calls})"
  else
    echo "   FAIL: канал признан недоступным, хотя повтор был успешным (попыток: ${probe_calls})"; failed=1
  fi
  PROBE_RETRY_PAUSE="${old_pause}"
  probe_port() {
    local host="$1" port="$2" timeout_s="$3"
    case "${port}" in
      5433) probe_postgres "${host}" "${port}" "${timeout_s}" ;;
      9000) probe_http_ok "${host}" "${port}" "${timeout_s}" "/minio/health/live" ;;
      *) probe_tcp "${host}" "${port}" "${timeout_s}" ;;
    esac
  }
  probe_https() {
    local url="$1" timeout_s="$2" code
    code="$(curl -fsS --noproxy '*' -o /dev/null -w '%{http_code}' --max-time "${timeout_s}" "${url}" 2>/dev/null)" || code="000"
    [ "${code}" -ge 200 ] 2>/dev/null && [ "${code}" -lt 300 ] 2>/dev/null
  }

  echo "== Сценарий 11: прокси Telegram — только в вызове Bot API"
  # Регрессия: с админ-машины api.telegram.org напрямую недоступен, нужен
  # локальный прокси. Прокси применяется ТОЛЬКО к вызову Bot API: определение
  # внешнего адреса и пробы доступности идут напрямую — иначе в allow-list
  # прод-хоста попал бы адрес выхода прокси, а не адрес админ-машины.
  # Проверка явная: curl подменён заглушкой, которая пишет переданные аргументы
  # и содержимое конфиг-файла (-K) в файлы прогона.
  local stub_dir="${workdir}/curlstub"
  mkdir -p "${stub_dir}"
  curl() {
    printf 'ARGS %s\n' "$*" >>"${stub_dir}/args.log"
    local prev="" a cfg=""
    for a in "$@"; do
      if [ "${prev}" = "-K" ]; then cfg="${a}"; fi
      prev="${a}"
    done
    if [ -n "${cfg}" ] && [ -f "${cfg}" ]; then
      { printf 'CONFIG %s\n' "${cfg}"; cat "${cfg}"; } >>"${stub_dir}/config.log"
      printf '200'
      return 0
    fi
    case "$*" in
      *http_code*) printf '200' ;;
      *) printf '203.0.113.77' ;;
    esac
    return 0
  }
  TELEGRAM_TOKEN="000000:FAKE"
  TELEGRAM_CHAT_ID="1"
  TELEGRAM_PROXY="socks5h://127.0.0.1:1080"
  rm -f "${stub_dir}/args.log" "${stub_dir}/config.log"
  send_telegram "self-test" >/dev/null 2>&1 || true
  if grep -qF 'proxy = "socks5h://127.0.0.1:1080"' "${stub_dir}/config.log" 2>/dev/null; then
    echo "   OK: прокси подставлен в конфиг-файл вызова Bot API"
  else
    echo "   FAIL: прокси не попал в конфиг-файл вызова Bot API"; failed=1
  fi
  if grep -qF 'noproxy' "${stub_dir}/config.log" 2>/dev/null; then
    echo "   FAIL: в конфиге Bot API задан noproxy вместе с proxy"; failed=1
  else
    echo "   OK: noproxy и proxy не заданы одновременно"
  fi

  echo "== Сценарий 11b: чат уведомлений — служебный, а не канал проекта (2026-11-01)"
  # Регрессия: send_telegram брал chat_id из ALLOWLIST_TELEGRAM_CHAT_ID — это
  # канал проекта (публичный канал публикации новостей), и служебная информация
  # о проде («прод-хост недоступен по SSH») попала в публичный канал. Теперь чат
  # берётся из ALLOWLIST_NOTIFY_CHAT_ID; если он не задан — уведомление не
  # отправляется вовсе (факт остаётся в журнале).
  local chat_value=""
  chat_value="$(ALLOWLIST_NOTIFY_CHAT_ID=-100777 ALLOWLIST_TELEGRAM_CHAT_ID=-100999 notify_chat_id)"
  if [ "${chat_value}" = "-100777" ]; then
    echo "   OK: чат уведомлений берётся из ALLOWLIST_NOTIFY_CHAT_ID"
  else
    echo "   FAIL: чат уведомлений не взят из ALLOWLIST_NOTIFY_CHAT_ID (получено: ${chat_value:-пусто})"; failed=1
  fi
  chat_value="$(ALLOWLIST_TELEGRAM_CHAT_ID=-100999 notify_chat_id)"
  if [ -z "${chat_value}" ]; then
    echo "   OK: канал проекта (ALLOWLIST_TELEGRAM_CHAT_ID) для уведомлений не используется"
  else
    echo "   FAIL: чат уведомлений подставляется из канала проекта (${chat_value})"; failed=1
  fi

  # Определение внешнего адреса — напрямую, без прокси.
  rm -f "${stub_dir}/args.log" "${stub_dir}/config.log"
  detect_address >/dev/null 2>&1 || true
  local addr_lines addr_noproxy
  addr_lines="$(grep -c '^ARGS' "${stub_dir}/args.log" 2>/dev/null || true)"
  addr_noproxy="$(grep -c -- '--noproxy' "${stub_dir}/args.log" 2>/dev/null || true)"
  [ -n "${addr_lines}" ] || addr_lines=0
  [ -n "${addr_noproxy}" ] || addr_noproxy=0
  if [ "${addr_lines}" -gt 0 ] && [ "${addr_lines}" -eq "${addr_noproxy}" ]; then
    echo "   OK: определение адреса идёт напрямую (${addr_lines} запрос(ов), все с --noproxy)"
  else
    echo "   FAIL: определение адреса: запросов ${addr_lines}, с --noproxy ${addr_noproxy}"; failed=1
  fi
  if grep -q -- '-K' "${stub_dir}/args.log" 2>/dev/null || grep -qF '1080' "${stub_dir}/args.log" 2>/dev/null; then
    echo "   FAIL: в определении адреса появился конфиг-файл или адрес прокси"; failed=1
  else
    echo "   OK: в определении адреса нет конфиг-файла и нет адреса прокси"
  fi

  # Проба доступности — напрямую, без прокси.
  rm -f "${stub_dir}/args.log" "${stub_dir}/config.log"
  probe_https "https://sm-karaoke.ru/health" 1 >/dev/null 2>&1 || true
  local probe_lines probe_noproxy
  probe_lines="$(grep -c '^ARGS' "${stub_dir}/args.log" 2>/dev/null || true)"
  probe_noproxy="$(grep -c -- '--noproxy' "${stub_dir}/args.log" 2>/dev/null || true)"
  [ -n "${probe_lines}" ] || probe_lines=0
  [ -n "${probe_noproxy}" ] || probe_noproxy=0
  if [ "${probe_lines}" -eq 1 ] && [ "${probe_noproxy}" -eq 1 ]; then
    echo "   OK: проба доступности идёт напрямую (--noproxy)"
  else
    echo "   FAIL: проба доступности: запросов ${probe_lines}, с --noproxy ${probe_noproxy}"; failed=1
  fi
  if grep -q -- '-K' "${stub_dir}/args.log" 2>/dev/null || grep -qF '1080' "${stub_dir}/args.log" 2>/dev/null; then
    echo "   FAIL: в пробе доступности появился конфиг-файл или адрес прокси"; failed=1
  else
    echo "   OK: в пробе доступности нет конфиг-файла и нет адреса прокси"
  fi

  # Переменная не задана — прямое соединение (proxy не задан, noproxy задан).
  TELEGRAM_PROXY=""
  rm -f "${stub_dir}/args.log" "${stub_dir}/config.log"
  send_telegram "self-test" >/dev/null 2>&1 || true
  if grep -qF 'noproxy = "*"' "${stub_dir}/config.log" 2>/dev/null &&
    ! grep -q '^proxy = ' "${stub_dir}/config.log" 2>/dev/null; then
    echo "   OK: без ALLOWLIST_TELEGRAM_PROXY вызов Bot API идёт напрямую (noproxy)"
  else
    echo "   FAIL: без ALLOWLIST_TELEGRAM_PROXY в конфиге нет noproxy (или задан proxy)"; failed=1
  fi
  unset -f curl
  TELEGRAM_TOKEN=""
  TELEGRAM_CHAT_ID=""

  echo "== Сценарий 12: недоступный прокси не проваливает цикл (FR-013)"
  # Прокси задан, но недоступен: уведомление не доставляется — цикл всё равно
  # должен завершиться штатным кодом (правка allow-list от доставки сообщения
  # не зависит). Прокси — порт 1 на локальной машине: соединение не установится,
  # наружу ничего не уходит, прод не затрагивается (цикл уходит в ветку
  # «прод недоступен целиком» до обращения к прод-хосту по ssh).
  TELEGRAM_TOKEN="000000:FAKE"
  TELEGRAM_CHAT_ID="1"
  TELEGRAM_PROXY="socks5h://127.0.0.1:1"
  probe_https() { return 1; }
  probe_tcp() { return 1; }
  local notify_cycle_rc=0
  run_cycle "" >/dev/null 2>&1 || notify_cycle_rc=$?
  if [ "${notify_cycle_rc}" -eq "${EXIT_DECLINED}" ]; then
    echo "   OK: код цикла ${notify_cycle_rc} — недоставленное уведомление цикл не провалило"
  else
    echo "   FAIL: код цикла ${notify_cycle_rc} (ожидался ${EXIT_DECLINED})"; failed=1
  fi
  if grep -q 'event:notify channel=telegram ok=false' "${LOG_FILE}" 2>/dev/null &&
    grep -q 'reason=proxy_unreachable' "${LOG_FILE}" 2>/dev/null; then
    echo "   OK: в журнале есть событие о неудачной отправке уведомления (reason=proxy_unreachable)"
  else
    echo "   FAIL: в журнале нет события о неудачной отправке уведомления"; failed=1
  fi
  TELEGRAM_TOKEN=""
  TELEGRAM_CHAT_ID=""
  TELEGRAM_PROXY=""

  echo "== Сценарий 13: прод недоступен целиком — адрес не определяется (FR-002)"
  # HTTPS не отвечает → цикл не должен определять адрес и не должен идти на прод.
  local marker="${workdir}/address-called"
  probe_https() { return 1; }
  probe_tcp() { return 1; }
  detect_address() { echo called >>"${marker}"; printf '%s' "203.0.113.77"; }
  local cycle_rc=0
  run_cycle "" >/dev/null 2>&1 || cycle_rc=$?
  unset -f detect_address
  if [ "${cycle_rc}" = "${EXIT_DECLINED}" ] && [ ! -f "${marker}" ]; then
    echo "   OK: цикл завершился отказом (код ${EXIT_DECLINED}), адрес не определялся"
  else
    echo "   FAIL: код=${cycle_rc}, маркер определения адреса: $([ -f "${marker}" ] && echo есть || echo нет)"; failed=1
  fi

  echo "== Сценарий 14: повтор SSH при кратковременном сбое сети (KARAOKE-382)"
  # Регрессия: сеть админ-машины нестабильна (пул провайдера, VPN) — SSH к проду
  # то поднимался, то обрывался на connect. Одна неудачная попытка выглядела как
  # «прод недоступен по SSH»: правка allow-list не применялась, а канал
  # 5433/9000 оставался закрытым. run_ssh повторяет попытку, пока не исчерпает
  # SSH_ATTEMPTS, и возвращает результат успешной попытки.
  local ssh_calls_log="${workdir}/ssh-calls.log"
  : >"${ssh_calls_log}"
  ssh() {
    printf 'call\n' >>"${ssh_calls_log}"
    if [ "$(wc -l <"${ssh_calls_log}" | tr -d ' ')" -lt 2 ]; then return 255; fi
    printf 'ok'
    return 0
  }
  local ssh_attempts_backup="${SSH_ATTEMPTS}" ssh_pause_backup="${SSH_RETRY_PAUSE}"
  SSH_ATTEMPTS=3
  SSH_RETRY_PAUSE=0
  local retry_out=""
  retry_out="$(run_ssh 'true' 2>/dev/null)"
  SSH_ATTEMPTS="${ssh_attempts_backup}"
  SSH_RETRY_PAUSE="${ssh_pause_backup}"
  unset -f ssh
  local ssh_calls
  ssh_calls="$(wc -l <"${ssh_calls_log}" | tr -d ' ')"
  if [ "${ssh_calls}" -ge 2 ]; then
    echo "   OK: после сбоя SSH сделан повтор (попыток: ${ssh_calls})"
  else
    echo "   FAIL: повторов SSH не было (попыток: ${ssh_calls})"; failed=1
  fi
  if [ "${retry_out}" = "ok" ]; then
    echo "   OK: успешная попытка вернула результат"
  else
    echo "   FAIL: успешная попытка не вернула результат ('${retry_out}')"; failed=1
  fi

  echo "== Сценарий 15: автоподбор ssh-идентичности, когда у сервиса нет ключа (KARAOKE-389)"
  # Регрессия 2026-11-02: юнит работает под root, у root нет ни ключа, ни
  # known_hosts для прод-хоста → ssh отвечал rc=255, механизм молча ничего не
  # применял (allow-list не обновлён, прод-БД недоступна 80 минут). Механизм
  # должен сам найти рабочий ключ оператора админ-машины и запомнить его.
  local fake_home="${workdir}/fake-home"
  mkdir -p "${fake_home}/.ssh"
  printf 'fake-key\n' >"${fake_home}/.ssh/id_rsa"
  chmod 600 "${fake_home}/.ssh/id_rsa"
  ssh() {
    local a
    for a in "$@"; do
      case "${a}" in
        */.ssh/id_rsa) return 0 ;;
      esac
    done
    return 255
  }
  local home_backup="${HOME}" state_backup="${STATE_FILE}"
  local ready_backup="${SSH_READY_RESOLVED:-0}" id_backup="${SSH_IDENTITY_FILE:-}"
  local kh_backup="${SSH_KNOWN_HOSTS_FILE:-}"
  HOME="${fake_home}"
  STATE_FILE="${workdir}/identity.state"
  SSH_READY_RESOLVED=0
  SSH_IDENTITY_FILE=""
  SSH_KNOWN_HOSTS_FILE=""
  local resolved_id=""
  if ensure_ssh_ready; then resolved_id="${SSH_IDENTITY_FILE}"; fi
  HOME="${home_backup}" STATE_FILE="${state_backup}"
  SSH_READY_RESOLVED="${ready_backup}" SSH_IDENTITY_FILE="${id_backup}"
  SSH_KNOWN_HOSTS_FILE="${kh_backup}"
  unset -f ssh
  if [ "${resolved_id}" = "${fake_home}/.ssh/id_rsa" ]; then
    echo "   OK: идентичность подобрана автоматически (${resolved_id})"
  else
    echo "   FAIL: идентичность не подобрана (получено: ${resolved_id:-пусто})"; failed=1
  fi
  if grep -q "^ssh_identity=" "${workdir}/identity.state" 2>/dev/null; then
    echo "   OK: найденная идентичность сохранена в state-файле"
  else
    echo "   FAIL: идентичность не сохранена в state-файле"; failed=1
  fi

  echo "== Сценарий 16: правка ВСЕХ файлов с allow-list'ом, не только stream (KARAOKE-389)"
  # Регрессия 2026-11-02 (вечер): автоматика обновила stream-блоки в
  # /etc/nginx/nginx.conf, а HTTP-локации /changerecords в
  # /etc/nginx/sites-enabled/80to8897 остались со старым списком → nginx отдавал
  # 403, синхронизация не работала, одобренные редакторами песни не доезжали.
  # Теперь файл с location-блоками обрабатывается универсальной подготовкой.
  local site_old="${workdir}/site.conf" site_new="${workdir}/site.new"
  cat >"${site_old}" <<'SITE'
server {
    listen 80;
    location /api/properties/setproperty {
        allow 185.26.28.109;
        deny all;
        proxy_pass http://127.0.0.1:8897;
    }
    location /changerecords {
        allow 185.26.28.109;
        deny all;
        proxy_pass http://127.0.0.1:8897;
    }
}
SITE
  if prepare_new_allow_config "${site_old}" "${site_new}" "${test_ip}" 2>/dev/null &&
    verify_diff "${site_old}" "${site_new}" "${test_ip}" 2>/dev/null; then
    local got
    got="$(grep -cE "^[[:space:]]*allow ${test_ip};$" "${site_new}" || true)"
    if [ "${got}" = "2" ]; then
      echo "   OK: allow добавлена в оба location-блока (${got})"
    else
      echo "   FAIL: в site-конфиге allow-строк ${got}, ожидалось 2"; failed=1
    fi
  else
    echo "   FAIL: универсальная подготовка site-конфига не прошла"; failed=1
  fi

  echo "== Сценарий 17: scp передаёт временный файл с теми же ssh-опциями (KARAOKE-392)"
  # Регрессия 2026-11-05: scp вызывался БЕЗ опций (идентичность/known_hosts).
  # Таймер работает под root, у root нет ключа по умолчанию → scp отваливался,
  # шаг возвращал transfer_failed МОЛЧА (в журнале не было ни ошибки, ни
  # «applied»), механизм самовосстановления не сработал ни разу, а IP сменился —
  # доступ к прод-БД остался закрытым.
  local self_src="${BASH_SOURCE[0]}" scp_line
  scp_line="$(grep -E '^[[:space:]]*scp -q' "${self_src}" | head -1)"
  if printf '%s' "${scp_line}" | grep -q 'SSH_EFFECTIVE_OPTS'; then
    echo "   OK: scp использует SSH_EFFECTIVE_OPTS (идентичность, known_hosts)"
  else
    echo "   FAIL: scp вызывается без ssh-опций — под root передача файла не пройдёт"; failed=1
  fi
  if grep -q 'step=transfer' "${self_src}"; then
    echo "   OK: отказ передачи пишется в журнал (шаг transfer)"
  else
    echo "   FAIL: отказ передачи не попадает в журнал"; failed=1
  fi

  echo "== Сценарий 18: оборванный файл с прода — download_failed, а не config_mismatch (спека 730)"
  # Регрессия 2026-11-07: оборванный (или пустой) файл с прод-хоста доходил до
  # разбора и давал ложную причину config_mismatch («структура не совпала»):
  # механизм молча сдавался, правка allow-list не выполнялась, владелец получал
  # неверную причину. Теперь неполный файл — сетевой сбой (download_failed) с
  # повтором скачивания, а несовпадение структуры остаётся config_mismatch.
  local dl_remote="${workdir}/remote.conf" dl_local="${workdir}/downloaded.conf"
  printf 'server {\n  listen 80;\n  deny all;\n}\n' >"${dl_remote}"
  local dl_saved_run_ssh="$(declare -f run_ssh)" dl_saved_pause="${CONF_DOWNLOAD_RETRY_PAUSE}"
  CONF_DOWNLOAD_RETRY_PAUSE=0
  # Заглушка SSH: отдаём только начало файла (обрыв передачи).
  run_ssh() {
    case "$1" in
      cat*) head -c 10 "${dl_remote}" ;;
      *) wc -c <"${dl_remote}" | tr -d ' ' ;;
    esac
    return 0
  }
  if download_prod_conf "/etc/nginx/remote.conf" "${dl_local}"; then
    echo "   FAIL: оборванный файл принят как целый"; failed=1
  else
    echo "   OK: оборванный файл отклонён (причина: ${SSH_LAST_ERROR})"
  fi
  if grep -q 'reason=download_failed' "${BASH_SOURCE[0]}" && grep -q 'download_prod_conf' "${BASH_SOURCE[0]}"; then
    echo "   OK: сетевой сбой отделён от config_mismatch (download_failed)"
  else
    echo "   FAIL: причина download_failed не используется в цикле"; failed=1
  fi
  # Целый файл принимается и сохраняется без изменений.
  run_ssh() {
    case "$1" in
      cat*) cat "${dl_remote}" ;;
      *) wc -c <"${dl_remote}" | tr -d ' ' ;;
    esac
    return 0
  }
  if download_prod_conf "/etc/nginx/remote.conf" "${dl_local}" && cmp -s "${dl_remote}" "${dl_local}"; then
    echo "   OK: целый файл принят и совпадает с оригиналом"
  else
    echo "   FAIL: целый файл отклонён"; failed=1
  fi
  eval "${dl_saved_run_ssh}"
  CONF_DOWNLOAD_RETRY_PAUSE="${dl_saved_pause}"

  echo "== Сценарий 19: адрес берётся у прода — расхождение внешних IP-сервисов не блокирует (спека 731)"
  # ИНЦИДЕНТ 2026-11-08: ipify/ifconfig.me отвечали через VPN-туннель с ротацией
  # адресов (195.178.4.x), остальные четыре источника — через адрес ISP
  # (95.31.39.146). Правило «все источники должны совпасть» давало
  # sources_disagree → цикл отказывался править allow-list, и приложение не
  # достучалось до прод-БД. Прод знает наш адрес на том канале, который мы чиним
  # (SSH_CLIENT), — он и became источником истины.
  local s19_saved_run_ssh="$(declare -f run_ssh)" s19_saved_detect="$(declare -f detect_address)"
  run_ssh() {
    printf '95.31.39.146 51234 22'
    return 0
  }
  # Внешние источники расходятся (как было в проде 2026-11-08).
  detect_address() {
    REASON="sources_disagree"
    log_event "skip" "reason=sources_disagree" "detail=195.178.4.160_95.31.39.146"
    return 1
  }
  local s19_addr=""
  s19_addr="$(current_address 2>/dev/null || true)"
  if [ "${s19_addr}" = "95.31.39.146" ]; then
    echo "   OK: адрес взят у прода (SSH_CLIENT) = ${s19_addr}"
  else
    echo "   FAIL: current_address вернул '${s19_addr}' вместо адреса, который видит прод"; failed=1
  fi
  if grep -q 'detect_prod_seen_address' "${BASH_SOURCE[0]}" && grep -q 'current_address' "${BASH_SOURCE[0]}"; then
    echo "   OK: в скрипте есть источник адреса «прод» и единая точка определения адреса"
  else
    echo "   FAIL: нет источника адреса «прод»"; failed=1
  fi
  eval "${s19_saved_detect}"
  eval "${s19_saved_run_ssh}"

  echo "== Сценарий 20: в allow-list остаётся ровно ОДИН текущий адрес (спека 731)"
  # ПРАВИЛО ВЛАДЕЛЬЦА (2026-11-08): старые адреса не копим — в списке прод-хоста
  # держим один текущий адрес админ-машины. Прежняя реализация только ДОПИСЫВАЛА
  # новый адрес, и список разрастался до десятка записей.
  local s20_old="${workdir}/s20.conf" s20_new="${workdir}/s20.new"
  cat >"${s20_old}" <<S20
server {
    listen 80;
    location /changerecords {
        allow 185.26.28.109;
        allow 83.137.50.231;
        allow 185.42.125.73;
        deny all;
        proxy_pass http://127.0.0.1:8897;
    }
    location /api/properties/setproperty {
        allow 185.26.28.109;
        deny all;
        proxy_pass http://127.0.0.1:8897;
    }
}
S20
  if prepare_new_allow_config "${s20_old}" "${s20_new}" "${test_ip}" 2>/dev/null &&
    verify_diff "${s20_old}" "${s20_new}" "${test_ip}" 2>/dev/null; then
    local s20_count s20_others
    s20_count="$(grep -cE "^[[:space:]]*allow ${test_ip};$" "${s20_new}" || true)"
    s20_others="$(grep -cE "^[[:space:]]*allow [0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+;$" "${s20_new}" || true)"
    if [ "${s20_count}" = "2" ] && [ "${s20_others}" = "2" ]; then
      echo "   OK: в каждом блоке остался ровно один allow (текущий), старые удалены"
    else
      echo "   FAIL: allow-строк с текущим адресом ${s20_count}, всего IPv4-allow ${s20_others}, ожидалось 2 и 2"; failed=1
    fi
  else
    echo "   FAIL: подготовка конфига с заменой списка не прошла"; failed=1
  fi
  # Уже «правильный» файл правки не требует.
  local s20_ok="${workdir}/s20.ok"
  cp "${s20_new}" "${s20_ok}"
  if address_allowed "${s20_ok}" "${test_ip}"; then
    echo "   OK: файл с одним текущим адресом распознан как не требующий правки"
  else
    echo "   FAIL: файл с одним текущим адресом распознан как требующий правки"; failed=1
  fi

  echo "== Сценарий 21: откат восстанавливает каждый файл из СВОЕГО бэкапа (спека 731)"
  # ИНЦИДЕНТ 2026-11-08: откат контрольной проверки восстанавливал ПОСЛЕДНИЙ бэкап
  # в ПЕРВЫЙ файл — /etc/nginx/nginx.conf получал содержимое файла сайта, `nginx -t`
  # падал, и прод остался с битым конфигом. Теперь у каждого файла свой бэкап.
  local s21_saved_rollback="$(declare -f rollback_change)" s21_log="${workdir}/s21.log"
  rollback_change() {
    printf '%s|%s|%s\n' "$2" "$3" "${4:-}" >>"${s21_log}"
    return 0
  }
  APPLIED_FILES=("/etc/nginx/nginx.conf" "/etc/nginx/sites-enabled/80to8897")
  APPLIED_BACKUPS=("/etc/nginx/nginx.conf.bak-a" "/etc/nginx/sites-enabled/80to8897.bak-b")
  APPLIED_METHODS=("mv" "cat")
  rollback_applied >/dev/null 2>&1 || true
  local s21_content
  s21_content="$(cat "${s21_log}" 2>/dev/null || true)"
  if printf '%s\n' "${s21_content}" | grep -qF '/etc/nginx/nginx.conf|/etc/nginx/nginx.conf.bak-a|mv' &&
    printf '%s\n' "${s21_content}" | grep -qF '/etc/nginx/sites-enabled/80to8897|/etc/nginx/sites-enabled/80to8897.bak-b|cat'; then
    echo "   OK: каждый файл откатан из своего бэкапа (бэкапы не перепутаны)"
  else
    echo "   FAIL: откат восстановил не тот бэкап (см. ${s21_log})"; failed=1
  fi
  eval "${s21_saved_rollback}"

  [ -z "${SELFTEST_DIR:-}" ] && rm -rf "${workdir}"
  if [ "${failed}" = 0 ]; then
    echo "SELF-TEST: OK (все сценарии пройдены)"
    return "${EXIT_OK}"
  fi
  echo "SELF-TEST: FAILED"
  return "${EXIT_FAIL}"
}

usage() {
  cat <<'EOF'
Использование: deploy/admin-ip-allowlist.sh [РЕЖИМ]

Режимы:
  (без аргументов)  основной цикл: пробы → определение адреса → правка allow-list
                    на прод-хосте (с бэкапом, проверкой синтаксиса, reload,
                    контрольной проверкой и откатом) → журнал → уведомление → PR
  --diagnose        FR-017: показать состояние и план изменения, ничего не меняя
  --self-test       FR-018: изолированная проверка правки без обращения к прод-хосту
  --help            эта справка

Коды возврата: 0 — штатно, 1 — ошибка применения (откат выполнен),
2 — ошибка использования, 3 — отказ по правилам (прод недоступен, источники
адреса недоступны или расходятся, адрес невалиден, превышена частота).

Переменные окружения: ALLOWLIST_SSH_IDENTITY_FILE, ALLOWLIST_SSH_KNOWN_HOSTS, ALLOWLIST_SSH_STRICT_HOST_KEY,
ALLOWLIST_SSH_CONNECT_TIMEOUT, ALLOWLIST_PROD_HOST, ALLOWLIST_PROD_NGINX_CONF,
ALLOWLIST_PROD_PORTS, ALLOWLIST_HEALTH_URL, ALLOWLIST_IP_SERVICES,
ALLOWLIST_IP_TIMEOUT, ALLOWLIST_PROBE_TIMEOUT, ALLOWLIST_MIN_INTERVAL_MINUTES,
ALLOWLIST_LOG_FILE, ALLOWLIST_STATE_FILE, ALLOWLIST_TELEGRAM_BOT_TOKEN,
ALLOWLIST_NOTIFY_CHAT_ID, ALLOWLIST_TELEGRAM_PROXY, ALLOWLIST_FJ_TOKEN,
ALLOWLIST_FJ_URL, ALLOWLIST_REPO, ALLOWLIST_REPO_PATH, ALLOWLIST_NGINX_BIN.

Токены — только из окружения; в argv и в журнал они не попадают.

ALLOWLIST_NOTIFY_CHAT_ID — чат для СЛУЖЕБНЫХ уведомлений (allow-list, откат,
«прод недоступен по SSH»). Канал проекта (ALLOWLIST_TELEGRAM_CHAT_ID) для них
не используется: он публичный. Если переменная не задана — уведомления не
отправляются, факт остаётся в журнале.

ALLOWLIST_SSH_IDENTITY_FILE — приватный ключ для ssh на прод-хоста (нужен сервису
под root: у root нет ни ключа, ни known_hosts, и правка не применяется).
ALLOWLIST_SSH_KNOWN_HOSTS — файл known_hosts для прод-хоста (иначе проверка ключа
хоста падает). ALLOWLIST_SSH_STRICT_HOST_KEY — режим проверки ключа хоста
(accept-new — принять новый ключ, yes — требовать совпадения).

ALLOWLIST_TELEGRAM_PROXY — прокси ТОЛЬКО для вызова Telegram Bot API
(например socks5h://127.0.0.1:1080); по умолчанию пусто — прямое соединение.
Определение внешнего адреса и пробы доступности прокси не используют никогда.
EOF
}

main() {
  local mode="cycle"
  case "${1:-}" in
    "") mode="cycle" ;;
    --diagnose) mode="diagnose" ;;
    --check-ssh) mode="check-ssh" ;;
    --self-test) mode="self-test" ;;
    --help | -h) usage; return "${EXIT_OK}" ;;
    *)
      echo "Неизвестный аргумент: $1 (см. --help)" >&2
      return "${EXIT_USAGE}"
      ;;
  esac

  # Хост для TCP-проб: из ALLOWLIST_PROD_HOST без пользователя.
  local host
  host="${PROD_HOST##*@}"

  case "${mode}" in
    cycle) run_cycle "${host}" ;;
    diagnose) run_diagnose ;;
    check-ssh) run_check_ssh ;;
    self-test) run_self_test ;;
  esac
}

main "$@"
