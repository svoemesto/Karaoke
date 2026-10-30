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
#   bash deploy/admin-ip-allowlist.sh --self-test  изолированная проверка правки
#   bash deploy/admin-ip-allowlist.sh --help       справка
#
# Переменные окружения (все с разумными значениями по умолчанию) — полный
# список в specs/677-admin-ip-allowlist/contracts/automation-cli.md §4.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- прод ---
PROD_HOST="${ALLOWLIST_PROD_HOST:-root@188.127.240.124}"
PROD_CONF="${ALLOWLIST_PROD_NGINX_CONF:-/etc/nginx/nginx.conf}"
PROD_PORTS="${ALLOWLIST_PROD_PORTS:-5433 9000}"
HEALTH_URL="${ALLOWLIST_HEALTH_URL:-https://sm-karaoke.ru/health}"
PROBE_TIMEOUT="${ALLOWLIST_PROBE_TIMEOUT:-5}"
# Повторы проб: одна неудачная проба — ещё не «прод недоступен» (сетевой сбой на
# машине даёт ложные срабатывания). Повтор — деталь реализации, контракт
# (FR-001) не меняется.
PROBE_ATTEMPTS="${ALLOWLIST_PROBE_ATTEMPTS:-3}"
PROBE_RETRY_PAUSE="${ALLOWLIST_PROBE_RETRY_PAUSE:-2}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=10)

# --- адрес ---
IP_SERVICES="${ALLOWLIST_IP_SERVICES:-https://api.ipify.org https://ifconfig.me/ip https://icanhazip.com https://checkip.amazonaws.com https://ifconfig.co/ip https://ipinfo.io/ip}"
IP_TIMEOUT="${ALLOWLIST_IP_TIMEOUT:-8}"

# --- ограничения и состояние ---
MIN_INTERVAL_MINUTES="${ALLOWLIST_MIN_INTERVAL_MINUTES:-30}"
LOG_FILE="${ALLOWLIST_LOG_FILE:-/var/log/admin-ip-allowlist.log}"
STATE_FILE="${ALLOWLIST_STATE_FILE:-/var/log/admin-ip-allowlist.state}"

# --- уведомления ---
TELEGRAM_TOKEN="${ALLOWLIST_TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_CHAT_ID="${ALLOWLIST_TELEGRAM_CHAT_ID:-}"
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

probe_https() {
  local url="$1" timeout_s="$2" code
  # --noproxy '*': проба идёт НАПРЯМУЮ — переменные прокси окружения не должны
  # влиять на результат (иначе «прод недоступен» означало бы «прокси недоступен»).
  code="$(curl -fsS --noproxy '*' -o /dev/null -w '%{http_code}' --max-time "${timeout_s}" "${url}" 2>/dev/null)" || code="000"
  [ "${code}" -ge 200 ] 2>/dev/null && [ "${code}" -lt 300 ] 2>/dev/null
}

# Проба TCP с ограниченным повтором. В PROBE_DURATION_MS — реальная
# длительность последней попытки, в PROBE_ATTEMPTS_USED — число попыток.
probe_tcp_retry() {
  local host="$1" port="$2" timeout_s="$3" attempts="${4:-${PROBE_ATTEMPTS}}"
  local i started ended
  started="$(now_ms)"
  for ((i = 1; i <= attempts; i++)); do
    if probe_tcp "${host}" "${port}" "${timeout_s}"; then
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
    if probe_tcp_retry "${host}" "${port}" "${PROBE_TIMEOUT}"; then
      log_event "probe" "target=tcp:${port}" "ok=true" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}"
    else
      log_event "probe" "target=tcp:${port}" "ok=false" "duration_ms=${PROBE_DURATION_MS}" "attempts=${PROBE_ATTEMPTS_USED}" "detail=connect_failed"
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
# FR-005: присутствие адреса означает отсутствие любых действий.
address_allowed() {
  local conf="$1" ip="$2"
  grep -qE "^[[:space:]]*allow[[:space:]]+${ip//./\\.};[[:space:]]*$" "${conf}"
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
                out.append(b)
                if not inserted and re.match(r"^\s*deny\s+all\s*;", b):
                    indent = b[: len(b) - len(b.lstrip())]
                    out.append("{}allow {};".format(indent, ip))
                    inserted = True
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

# FR-020: изменение MUST состоять только из добавленных строк `allow <ip>;`.
# Возвращает 0, если изменение — ровно эти строки и ничего больше.
verify_diff() {
  local old_file="$1" new_file="$2" ip="$3"
  python3 - "${old_file}" "${new_file}" "${ip}" <<'PY'
import sys

old_file, new_file, ip = sys.argv[1], sys.argv[2], sys.argv[3]
with open(old_file, encoding="utf-8") as fh:
    old = fh.read()
with open(new_file, encoding="utf-8") as fh:
    new = fh.read()

needle = "allow {};".format(ip)
added = [l for l in new.split("\n") if l.strip() == needle]
stripped = "\n".join(l for l in new.split("\n") if l.strip() != needle)
if len(added) != 2:
    sys.stderr.write("expected exactly 2 added allow lines, found {}\n".format(len(added)))
    sys.exit(1)
if stripped != old:
    sys.stderr.write("diff contains changes other than the added allow lines\n")
    sys.exit(1)
PY
}

# ------------------------------------------------- применение и откат ----
# run_target local|remote <команда> — выполнить команду на цели.
run_target() {
  local mode="$1"; shift
  if [ "${mode}" = "remote" ]; then
    ssh "${SSH_OPTS[@]}" "${PROD_HOST}" "$*"
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
confirm_address() { detect_address; }

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
  remote_tmp="${target}.allowlist-new"

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
  if [ "${mode}" = "remote" ]; then
    scp -q "${new_file}" "${PROD_HOST}:${remote_tmp}" || { REASON="transfer_failed"; return 1; }
  else
    cp -f "${new_file}" "${remote_tmp}" || { REASON="transfer_failed"; return 1; }
  fi

  # Шаг 2: синтаксис временного файла тем же nginx.
  if ! run_target "${mode}" "${NGINX_BIN} -t -c '${remote_tmp}'" >/dev/null 2>&1; then
    run_target "${mode}" "rm -f '${remote_tmp}'" >/dev/null 2>&1 || true
    log_event "syntax" "ok=false" "step=temp_file" "detail=nginxt_failed"
    REASON="syntax_failed"
    return 1
  fi
  log_event "syntax" "ok=true" "step=temp_file"

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
  return 0
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
  code="$(curl -sS -o /dev/null -w '%{http_code}' -K "${cfg}" 2>/dev/null)" || rc=$?
  [ -n "${code}" ] || code="000"
  rm -f "${cfg}"
  if [ "${rc}" -eq 0 ] && [ "${code}" = "200" ]; then
    log_event "notify" "channel=telegram" "ok=true" "proxy=$(proxy_state)"
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

  # 1. Репозиторная копия (канон) из API.
  local raw sha content
  raw="$(fj_api GET "/contents/${REPO_CONF_PATH}?ref=master")" || raw=""
  content="$(printf '%s' "${raw}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("content") or "")' 2>/dev/null | base64 -d 2>/dev/null)"
  sha="$(printf '%s' "${raw}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("sha") or "")' 2>/dev/null)"
  if [ -z "${content}" ]; then
    log_event "pr" "ok=false" "reason=repo_copy_unavailable"
    return 1
  fi

  # 2. Дрейф прод↔репозиторий — информационно (не блокирует, в PR не выносится).
  if [ -f "${REPO_COPY_LOCAL}" ]; then
    if ! diff -q "${REPO_COPY_LOCAL}" <(printf '%s' "${content}") >/dev/null 2>&1; then
      log_event "drift" "detail=repo_copy_differs_from_prod_or_local" \
        "prod_conf=$(sanitize "${PROD_CONF}")"
    fi
  fi

  # 3. Вставка ТОЛЬКО недостающей allow-строки в репозиторную копию.
  local tmp_old tmp_new
  tmp_old="$(mktemp "${TMPDIR:-/tmp}/allowlist-repo.XXXXXX")"
  tmp_new="$(mktemp "${TMPDIR:-/tmp}/allowlist-repo.XXXXXX")"
  printf '%s' "${content}" >"${tmp_old}"
  if ! prepare_new_config "${tmp_old}" "${tmp_new}" "${ip}" 2>/dev/null; then
    rm -f "${tmp_old}" "${tmp_new}"
    log_event "pr" "ok=false" "reason=repo_copy_prepare_failed"
    return 1
  fi
  if ! verify_diff "${tmp_old}" "${tmp_new}" "${ip}" 2>/dev/null; then
    rm -f "${tmp_old}" "${tmp_new}"
    log_event "pr" "ok=false" "reason=repo_copy_diff_failed"
    return 1
  fi
  local b64
  b64="$(base64 -w0 <"${tmp_new}" 2>/dev/null || base64 <"${tmp_new}" | tr -d '\n')"
  rm -f "${tmp_old}" "${tmp_new}"

  # 4. Ветка + коммит + PR.
  local branch="auto/allowlist-${ip//./-}-$(date -u +%Y%m%d%H%M%S)"
  local body
  body="$(python3 - "${ip}" "${backup}" <<'PY'
import json
import sys

ip, backup = sys.argv[1], sys.argv[2]
print(json.dumps({
    "branch_name": None,
    "old_branch_name": "master",
    "pr_title": "chore(677): allow-list прод-хоста — добавлен адрес {} (KARAOKE-337)".format(ip),
    "pr_body": "Автоматическая правка allow-list (спека 677, KARAOKE-337).\n\n"
               "Добавлена строка `allow {};` в оба stream-блока (5433, 9000).\n"
               "Бэкап на проде: {}\n\n"
               "Одобрение и слияние — за владельцем.".format(ip, backup or "—"),
}))
PY
)"
  local branch_name old_name pr_title pr_body
  branch_name="$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["branch_name"])')"
  pr_title="$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["pr_title"])')"
  pr_body="$(printf '%s' "${body}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["pr_body"])')"

  local br_body
  br_body="$(python3 -c 'import json,sys; print(json.dumps({"branch_name": sys.argv[1], "old_branch_name": "master"}))' "${branch}")"
  fj_api POST "/branches" "${br_body}" >/dev/null 2>&1 || true

  local commit_body
  commit_body="$(python3 -c 'import json,sys; print(json.dumps({"branch": sys.argv[1], "content": sys.argv[2], "message": sys.argv[3], "sha": sys.argv[4]}))' "${branch}" "${b64}" "chore(677): allow-list — добавлен адрес ${ip} (KARAOKE-337) [tracker-claim-337]" "${sha}")"
  if ! fj_api POST "/contents/${REPO_CONF_PATH}" "${commit_body}" >/dev/null 2>&1; then
    log_event "pr" "ok=false" "reason=commit_failed" "branch=$(sanitize "${branch}")"
    return 1
  fi

  local pull_body
  pull_body="$(python3 -c 'import json,sys; print(json.dumps({"head": sys.argv[1], "base": "master", "title": sys.argv[2], "body": sys.argv[3]}))' "${branch}" "${pr_title}" "${pr_body}")"
  local pr_json pr_url
  pr_json="$(fj_api POST "/pulls" "${pull_body}")"
  pr_url="$(printf '%s' "${pr_json}" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("html_url") or "")' 2>/dev/null)"
  if [ -z "${pr_url}" ]; then
    log_event "pr" "ok=false" "reason=pull_request_failed" "branch=$(sanitize "${branch}")"
    return 1
  fi
  log_event "pr" "ok=true" "url=$(sanitize "${pr_url}")" "branch=$(sanitize "${branch}")"
  PR_URL="${pr_url}"
  return 0
}

# ------------------------------------------------------------- режимы ----
run_cycle() {
  # host — первый аргумент (хост, к которому идут TCP-пробы). Раньше он был
  # необъявленным локальным именем и не инициализировался: под `set -u` цикл
  # падал целиком ещё до первой пробы.
  local host="${1:-}" ip

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
  ip="$(detect_address)" || return "${EXIT_DECLINED}"
  validate_address "${ip}" || return "${EXIT_DECLINED}"

  # FR-005: адрес уже в allow-list — никаких действий, включая reload.
  local prod_conf
  prod_conf="$(mktemp "${TMPDIR:-/tmp}/allowlist-conf.XXXXXX")"
  if ! ssh "${SSH_OPTS[@]}" "${PROD_HOST}" "cat '${PROD_CONF}'" >"${prod_conf}" 2>/dev/null; then
    rm -f "${prod_conf}"
    log_event "skip" "reason=prod_unreachable_by_ssh"
    send_telegram "Karaoke allow-list: прод-хост недоступен по SSH — правка не применялась, состояние не менялось." || true
    return "${EXIT_FAIL}"
  fi

  if address_allowed "${prod_conf}" "${ip}"; then
    rm -f "${prod_conf}"
    log_event "skip" "reason=already_allowed" "ip=$(sanitize "${ip}")"
    return "${EXIT_OK}"
  fi

  # FR-010: ограничение частоты.
  if ! rate_limit_ok; then
    rm -f "${prod_conf}"
    send_telegram "Karaoke allow-list: правка отложена — с последней применённой правки прошло меньше ${MIN_INTERVAL_MINUTES} минут (адрес ${ip}). Следующая попытка — после истечения окна." || true
    return "${EXIT_DECLINED}"
  fi

  # Подготовка содержимого и FR-020.
  local new_conf
  new_conf="$(mktemp "${TMPDIR:-/tmp}/allowlist-new.XXXXXX")"
  if ! prepare_new_config "${prod_conf}" "${new_conf}" "${ip}" 2>/dev/null; then
    rm -f "${prod_conf}" "${new_conf}"
    log_event "skip" "reason=config_mismatch" "detail=stream_blocks_or_deny_all_not_found"
    send_telegram "Karaoke allow-list: структура конфигурации прод-хоста не совпала с ожидаемой (не найдены stream-блоки 5433/9000 или 'deny all;') — правка не выполнялась. Требуется взгляд владельца." || true
    return "${EXIT_DECLINED}"
  fi
  if ! verify_diff "${prod_conf}" "${new_conf}" "${ip}" 2>/dev/null; then
    rm -f "${prod_conf}" "${new_conf}"
    log_event "skip" "reason=diff_check_failed" "ip=$(sanitize "${ip}")"
    send_telegram "Karaoke allow-list: проверка diff'а не прошла — изменение содержало бы не только строку allow. Правка не выполнялась." || true
    return "${EXIT_DECLINED}"
  fi
  log_event "edit" "step=prepared" "ip=$(sanitize "${ip}")" "blocks=2"

  # Проверка: точка монтирования (findmnt) — результат в журнал.
  check_mount_point remote "${PROD_CONF}"

  # FR-006…FR-009: применение с откатом.
  if ! apply_change remote "${PROD_CONF}" "${new_conf}" "${ip}"; then
    rm -f "${prod_conf}" "${new_conf}"
    send_telegram "Karaoke allow-list: правка НЕ применена (адрес ${ip}), выполнен откат из бэкапа, конфигурация прод-хоста восстановлена. Причина: ${REASON}." || true
    return "${EXIT_FAIL}"
  fi
  rm -f "${prod_conf}" "${new_conf}"

  # FR-009: контрольная проверка фактического ответа.
  local pg_ok=0 minio_ok=0 https_ok=0
  probe_tcp "${host}" 5433 "${PROBE_TIMEOUT}" && pg_ok=1
  probe_tcp "${host}" 9000 "${PROBE_TIMEOUT}" && minio_ok=1
  probe_https "${HEALTH_URL}" "${PROBE_TIMEOUT}" && https_ok=1
  log_event "control" "pg_ok=${pg_ok}" "minio_ok=${minio_ok}" "https_ok=${https_ok}"
  if [ "${pg_ok}" != 1 ] || [ "${minio_ok}" != 1 ] || [ "${https_ok}" != 1 ]; then
    rollback_change remote "${PROD_CONF}" "${BACKUP_PATH}"
    send_telegram "Karaoke allow-list: контрольная проверка не прошла — выполнен откат из бэкапа, конфигурация прод-хоста восстановлена (адрес ${ip})." || true
    return "${EXIT_FAIL}"
  fi

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
  if ip="$(detect_address)"; then
    log_event "diagnose" "address=$(sanitize "${ip}")" "valid=$(validate_address "${ip}" && echo yes || echo no)"
  else
    log_event "diagnose" "address=" "reason=$(sanitize "${REASON:-}")"
  fi
  local prod_conf
  prod_conf="$(mktemp "${TMPDIR:-/tmp}/allowlist-conf.XXXXXX")"
  if ssh "${SSH_OPTS[@]}" "${PROD_HOST}" "cat '${PROD_CONF}'" >"${prod_conf}" 2>/dev/null; then
    log_event "diagnose" "prod_conf_read=true" "blocks=$(grep -cE '^[[:space:]]*allow[[:space:]]' "${prod_conf}")"
    check_mount_point remote "${PROD_CONF}"
  else
    log_event "diagnose" "prod_conf_read=false" "detail=ssh_failed"
  fi
  rm -f "${prod_conf}"
  log_event "diagnose" "plan=добавить_allow_в_2_блока_при_необходимости" "changes=none"
  return "${EXIT_OK}"
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
  probe_tcp() { probe_calls=$((probe_calls + 1)); [ "${probe_calls}" -ge 2 ]; }
  probe_https() { return 0; }
  check_reachability "127.0.0.1" >/dev/null 2>&1 || true
  if [ "${REACH_PG}" = 1 ] && [ "${REACH_MINIO}" = 1 ]; then
    echo "   OK: канал признан доступным после повтора (попыток: ${probe_calls})"
  else
    echo "   FAIL: канал признан недоступным, хотя повтор был успешным (попыток: ${probe_calls})"; failed=1
  fi
  PROBE_RETRY_PAUSE="${old_pause}"
  probe_tcp() {
    local host="$1" port="$2" timeout_s="$3"
    timeout "${timeout_s}" bash -c "exec 3<>/dev/tcp/${host}/${port}" >/dev/null 2>&1
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

Переменные окружения: ALLOWLIST_PROD_HOST, ALLOWLIST_PROD_NGINX_CONF,
ALLOWLIST_PROD_PORTS, ALLOWLIST_HEALTH_URL, ALLOWLIST_IP_SERVICES,
ALLOWLIST_IP_TIMEOUT, ALLOWLIST_PROBE_TIMEOUT, ALLOWLIST_MIN_INTERVAL_MINUTES,
ALLOWLIST_LOG_FILE, ALLOWLIST_STATE_FILE, ALLOWLIST_TELEGRAM_BOT_TOKEN,
ALLOWLIST_TELEGRAM_CHAT_ID, ALLOWLIST_TELEGRAM_PROXY, ALLOWLIST_FJ_TOKEN,
ALLOWLIST_FJ_URL, ALLOWLIST_REPO, ALLOWLIST_REPO_PATH, ALLOWLIST_NGINX_BIN.

Токены — только из окружения; в argv и в журнал они не попадают.

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
    self-test) run_self_test ;;
  esac
}

main "$@"
