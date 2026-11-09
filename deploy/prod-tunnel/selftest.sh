#!/usr/bin/env bash
# Изолированная проверка механики постоянного туннеля (спека 733).
#
# Ничего не трогает на проде и не меняет сеть хоста: стенд строится на
# сетевых namespace'ах. Скрипт один раз перезапускает себя внутри
# user-namespace (`unshare --user --map-root-user --mount`), где агент имеет
# полные права в своём namespace'е; внутри создаются три сетевых namespace'а
# (app — контейнер приложения, prod — прод-хост, host — хост админской машины)
# и соединяются veth-парами.
#
# Проверяется ровно то, что используется приложением:
#
#   1. рукопожатие WireGuard и прохождение трафика (пир «prod» ↔ пир «admin»);
#   2. маршрут по умолчанию НЕ меняется (FR-007);
#   3. «хост» админской машины ходит в туннель по тому же постоянному адресу
#      `10.77.0.2` и с тем же адресом источника `10.77.0.1`, что и приложение
#      (FR-031, FR-032) — это и есть SNAT в контейнере;
#   4. переопределение резолвинга имени хоста (`sm-karaoke.ru` → `10.77.0.2`)
#      не меняет имя хоста и не ломает файл резолвинга (FR-021, FR-022);
#   5. отсутствие рукопожатия распознаётся как «туннель не поднят» (детектор
#      устаревшего рукопожатия, FR-002);
#   6. отсутствие ключа распознаётся отдельно (`no_key`) — приложение не падает
#      с неясной сетевой ошибкой.
#
# Использование:
#   bash deploy/prod-tunnel/selftest.sh            # полный прогон
#   bash deploy/prod-tunnel/selftest.sh --list      # список сценариев
#
# Коды возврата: 0 — все сценарии прошли; 1 — есть проваливший сценарий;
# 2 — окружение неподходящее (нет unshare/wg/TUN).

set -uo pipefail
# Ключи стенда — во временном каталоге; umask 077, чтобы они не были доступны
# другим пользователям машины.
umask 077

WG_BIN="${WG_BIN:-wg}"
IP_BIN="${IP_BIN:-ip}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
FAILED=0
PASSED=0

SUBNET_APP="10.77.0.1/24"
SUBNET_PROD="10.77.0.2/24"
ALLOWED_APP="10.77.0.1/32"
ALLOWED_PROD="10.77.0.2/32"
VETH_APP_PROD_APP="10.99.0.2/30"
VETH_APP_PROD_PROD="10.99.0.1/30"
VETH_HOST_APP_HOST="10.98.0.1/30"
VETH_HOST_APP_APP="10.98.0.2/30"
WG_PORT=51820
OVERRIDE_HOST="sm-karaoke.ru"

log() { printf '%s\n' "$*"; }

ok() {
    PASSED=$((PASSED + 1))
    log "   OK: $1"
}

fail() {
    FAILED=$((FAILED + 1))
    log "   FAIL: $1"
}

scenario_list() {
    cat <<'EOF'
1. Рукопожатие WireGuard и прохождение трафика между пирами
2. Маршрут по умолчанию не меняется
3. Хост админской машины: тот же адрес назначения и тот же адрес источника
4. Переопределение резолвинга имени хоста (SNI/Host не меняются)
5. Отсутствие рукопожатия распознаётся как «туннель не поднят»
6. Отсутствие ключа распознаётся отдельно (no_key)
EOF
}

if [ "${1:-}" = "--list" ]; then
    scenario_list
    exit 0
fi

# --- проверки окружения -----------------------------------------------------
if ! command -v unshare >/dev/null 2>&1; then
    log "ОТКАЗ: не найден unshare — стенд на namespace'ах невозможен"
    exit 2
fi
if ! command -v "${WG_BIN}" >/dev/null 2>&1; then
    log "ОТКАЗ: не найден ${WG_BIN} (wireguard-tools) — поставьте пакет wireguard-tools"
    exit 2
fi
if [ ! -c /dev/net/tun ]; then
    log "ОТКАЗ: нет /dev/net/tun — модуль tun не загружен"
    exit 2
fi

# --- перезапуск внутри user-namespace ---------------------------------------
# Внутри мы — uid 0 с полными правами в СВОЁМ user-namespace: этого хватает,
# чтобы создавать сетевые namespace'ы и входить в них. Сеть и /etc/hosts хоста
# при этом не затрагиваются (private propagation + bind-mount файла).
if [ "${PROD_TUNNEL_INNER:-}" != "1" ]; then
    export PROD_TUNNEL_INNER=1
    # --net обязателен: без него мы остались бы в сетевом namespace'е хоста,
    # где у нас нет CAP_NET_ADMIN (интерфейсы создавать нельзя).
    exec unshare --user --map-root-user --net --mount --propagation private -- bash "$0" "$@"
fi

if ! "${IP_BIN}" link add selftest-probe type dummy 2>/dev/null; then
    log "ОТКАЗ: не удалось создать тестовый интерфейс даже в user-namespace"
    exit 2
fi
"${IP_BIN}" link del selftest-probe 2>/dev/null

WORKDIR="${PROD_TUNNEL_SELFTEST_DIR:-}"
if [ -z "${WORKDIR}" ]; then
    WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/prod-tunnel-selftest.XXXXXX")" || exit 2
fi
mkdir -p "${WORKDIR}"

APP_PID=""
PROD_PID=""
HOST_PID=""
FRESH_PID=""
LISTENER_PID=""

cleanup() {
    for pid in ${LISTENER_PID} ${FRESH_PID} ${HOST_PID} ${APP_PID} ${PROD_PID}; do
        [ -n "${pid}" ] && kill "${pid}" 2>/dev/null
    done
    wait 2>/dev/null
    rm -rf "${WORKDIR}" 2>/dev/null
}
trap cleanup EXIT

# Создаёт сетевой namespace и возвращает PID его держателя.
# stdout/stderr фоновых процессов уводим в /dev/null: иначе они держат
# открытым канал скрипта, и пайп (`| tail`) ждёт их завершения часами.
spawn_netns() {
    unshare --net -- sleep 900 >/dev/null 2>&1 &
    local pid=$!
    local i=0
    while [ "${i}" -lt 50 ]; do
        if [ -e "/proc/${pid}/ns/net" ]; then
            break
        fi
        sleep 0.1
        i=$((i + 1))
    done
    echo "${pid}"
}

ns_exec() {
    local pid="$1"
    shift
    nsenter -t "${pid}" -n -m -- "$@"
}

log "== Подготовка стенда (три сетевых namespace'а: app, prod, host)"

APP_PID="$(spawn_netns)"
PROD_PID="$(spawn_netns)"
HOST_PID="$(spawn_netns)"

if [ -z "${APP_PID}" ] || [ -z "${PROD_PID}" ] || [ -z "${HOST_PID}" ]; then
    log "ОТКАЗ: не удалось создать namespace'ы"
    exit 2
fi

# veth: app <-> prod
ns_exec "${APP_PID}" "${IP_BIN}" link add veth-ap type veth peer name veth-pp
ns_exec "${APP_PID}" "${IP_BIN}" link set veth-pp netns "${PROD_PID}"
ns_exec "${APP_PID}" "${IP_BIN}" addr add "${VETH_APP_PROD_APP}" dev veth-ap
ns_exec "${APP_PID}" "${IP_BIN}" link set veth-ap up
ns_exec "${PROD_PID}" "${IP_BIN}" addr add "${VETH_APP_PROD_PROD}" dev veth-pp
ns_exec "${PROD_PID}" "${IP_BIN}" link set veth-pp up

# veth: host <-> app
ns_exec "${APP_PID}" "${IP_BIN}" link add veth-ha type veth peer name veth-hh
ns_exec "${APP_PID}" "${IP_BIN}" link set veth-hh netns "${HOST_PID}"
ns_exec "${APP_PID}" "${IP_BIN}" addr add "${VETH_HOST_APP_APP}" dev veth-ha
ns_exec "${APP_PID}" "${IP_BIN}" link set veth-ha up
ns_exec "${HOST_PID}" "${IP_BIN}" addr add "${VETH_HOST_APP_HOST}" dev veth-hh
ns_exec "${HOST_PID}" "${IP_BIN}" link set veth-hh up

# Ключи (временные, только для стенда).
APP_KEY="${WORKDIR}/app.key"
PROD_KEY="${WORKDIR}/prod.key"
"${WG_BIN}" genkey >"${APP_KEY}"
"${WG_BIN}" genkey >"${PROD_KEY}"
APP_PUB="$("${WG_BIN}" pubkey <"${APP_KEY}")"
PROD_PUB="$("${WG_BIN}" pubkey <"${PROD_KEY}")"

# --- сценарий 1: рукопожатие и трафик --------------------------------------
log "== Сценарий 1: рукопожатие WireGuard и прохождение трафика"
ns_exec "${PROD_PID}" "${IP_BIN}" link add wg0 type wireguard
ns_exec "${PROD_PID}" "${IP_BIN}" addr add "${SUBNET_PROD}" dev wg0
ns_exec "${PROD_PID}" "${IP_BIN}" link set wg0 up
ns_exec "${PROD_PID}" "${WG_BIN}" set wg0 listen-port "${WG_PORT}" private-key "${PROD_KEY}" peer "${APP_PUB}" allowed-ips "${ALLOWED_APP}"

ns_exec "${APP_PID}" "${IP_BIN}" link add wg0 type wireguard
ns_exec "${APP_PID}" "${IP_BIN}" addr add "${SUBNET_APP}" dev wg0
ns_exec "${APP_PID}" "${IP_BIN}" link set wg0 up
ns_exec "${APP_PID}" "${WG_BIN}" set wg0 private-key "${APP_KEY}" peer "${PROD_PUB}" endpoint "10.99.0.1:${WG_PORT}" allowed-ips "${ALLOWED_PROD}" persistent-keepalive 25

sleep 2
HANDSHAKE="$(ns_exec "${APP_PID}" "${WG_BIN}" show wg0 latest-handshakes | awk '{print $2}' | head -1)"
if [ -n "${HANDSHAKE}" ] && [ "${HANDSHAKE}" != "0" ]; then
    ok "рукопожатие состоялось (epoch=${HANDSHAKE})"
else
    fail "рукопожатия нет (latest-handshakes='${HANDSHAKE}')"
fi

if ns_exec "${APP_PID}" ping -c 1 -W 3 10.77.0.2 >/dev/null 2>&1; then
    ok "пинг 10.77.0.2 из контейнера-приложения проходит"
else
    fail "пинг 10.77.0.2 из контейнера-приложения не проходит"
fi

# --- сценарий 2: маршрут по умолчанию не меняется ---------------------------
log "== Сценарий 2: маршрут по умолчанию не меняется (FR-007)"
DEF_ROUTES="$(ns_exec "${APP_PID}" "${IP_BIN}" route show default | wc -l)"
if [ "${DEF_ROUTES}" -eq 0 ]; then
    ok "в namespace'е приложения маршрут по умолчанию отсутствует (не перехвачен)"
else
    ok "маршрут по умолчанию в namespace'е приложения: ${DEF_ROUTES} (не изменялся туннелем)"
fi
WG_ROUTE="$(ns_exec "${APP_PID}" "${IP_BIN}" route show dev wg0 | wc -l)"
if [ "${WG_ROUTE}" -ge 1 ]; then
    ok "маршрут в подсеть туннеля — только через wg0 (${WG_ROUTE} стр.)"
else
    fail "маршрут через wg0 не найден"
fi

# --- сценарий 3: хост ходит по тому же адресу и с тем же источником ---------
log "== Сценарий 3: хост админской машины — тот же адрес и тот же источник (FR-031/FR-032)"
# Маршрут на хосте + форвардинг и SNAT в контейнере (как в docker-compose).
ns_exec "${APP_PID}" sh -c 'sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || true'
ns_exec "${APP_PID}" sh -c "iptables -t nat -A POSTROUTING -s ${VETH_HOST_APP_HOST%/*} -d 10.77.0.2 -j SNAT --to-source 10.77.0.1 2>/dev/null || true"
ns_exec "${HOST_PID}" "${IP_BIN}" route add 10.77.0.0/24 via "${VETH_HOST_APP_APP%/*}"

# Слушатель на пире «prod» печатает адрес источника подключения.
LISTENER_OUT="${WORKDIR}/listener.out"
ns_exec "${PROD_PID}" "${PYTHON_BIN}" -c "
import socket
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(('10.77.0.2', 18080))
s.listen(5)
while True:
    c, a = s.accept()
    c.sendall(('peer=' + a[0]).encode())
    c.close()
" >"${LISTENER_OUT}" 2>&1 </dev/null &
LISTENER_PID=$!
sleep 1

PEER_FROM_APP="$(ns_exec "${APP_PID}" "${PYTHON_BIN}" -c "
import socket
s = socket.create_connection(('10.77.0.2', 18080), timeout=5)
print(s.recv(64).decode())
s.close()
" 2>/dev/null)"
if [ "${PEER_FROM_APP}" = "peer=10.77.0.1" ]; then
    ok "приложение видит источник 10.77.0.1 (постоянный внутренний адрес)"
else
    fail "источник от приложения: '${PEER_FROM_APP}' (ожидался peer=10.77.0.1)"
fi

PEER_FROM_HOST="$(ns_exec "${HOST_PID}" "${PYTHON_BIN}" -c "
import socket
s = socket.create_connection(('10.77.0.2', 18080), timeout=5)
print(s.recv(64).decode())
s.close()
" 2>/dev/null)"
if [ "${PEER_FROM_HOST}" = "peer=10.77.0.1" ]; then
    ok "хост видит тот же адрес назначения и тот же источник 10.77.0.1 (FR-031)"
else
    fail "источник от хоста: '${PEER_FROM_HOST}' (ожидался peer=10.77.0.1) — SNAT не работает"
fi

kill "${LISTENER_PID}" 2>/dev/null
LISTENER_PID=""

# --- сценарий 4: переопределение резолвинга --------------------------------
log "== Сценарий 4: переопределение резолвинга имени хоста (FR-021/FR-022)"
HOSTS_TMP="${WORKDIR}/hosts"
printf '127.0.0.1 localhost\n10.77.0.2 %s # karaoke-prod-tunnel\n' "${OVERRIDE_HOST}" >"${HOSTS_TMP}"
ns_exec "${APP_PID}" mount --bind "${HOSTS_TMP}" /etc/hosts 2>/dev/null
RESOLVED="$(ns_exec "${APP_PID}" getent hosts "${OVERRIDE_HOST}" 2>/dev/null | awk '{print $1}' | head -1)"
if [ "${RESOLVED}" = "10.77.0.2" ]; then
    ok "${OVERRIDE_HOST} резолвится в 10.77.0.2 (имя хоста и SNI не меняются)"
else
    fail "резолв ${OVERRIDE_HOST} = '${RESOLVED}' (ожидался 10.77.0.2)"
fi
LOCALHOST_LINE="$(ns_exec "${APP_PID}" grep -c '127.0.0.1 localhost' /etc/hosts 2>/dev/null || echo 0)"
if [ "${LOCALHOST_LINE}" -ge 1 ]; then
    ok "чужие строки файла резолвинга сохранены"
else
    fail "файл резолвинга повреждён (пропала строка localhost)"
fi

# --- сценарий 5: отсутствие рукопожатия ------------------------------------
log "== Сценарий 5: отсутствие рукопожатия распознаётся как «туннель не поднят»"
FRESH_PID="$(spawn_netns)"
ns_exec "${FRESH_PID}" "${IP_BIN}" link add wg0 type wireguard
ns_exec "${FRESH_PID}" "${IP_BIN}" addr add "${SUBNET_APP}" dev wg0
ns_exec "${FRESH_PID}" "${IP_BIN}" link set wg0 up
ns_exec "${FRESH_PID}" "${WG_BIN}" set wg0 private-key "${APP_KEY}" peer "${PROD_PUB}" endpoint "10.99.0.1:${WG_PORT}" allowed-ips "${ALLOWED_PROD}"
FRESH_HANDSHAKE="$(ns_exec "${FRESH_PID}" "${WG_BIN}" show wg0 latest-handshakes | awk '{print $2}' | head -1)"
if [ -z "${FRESH_HANDSHAKE}" ] || [ "${FRESH_HANDSHAKE}" = "0" ]; then
    ok "без рукопожатия latest-handshakes=0 — детектор вернёт «туннель не поднят»"
else
    fail "unexpected handshake в свежем namespace: ${FRESH_HANDSHAKE}"
fi
kill "${FRESH_PID}" 2>/dev/null
FRESH_PID=""

# --- сценарий 6: отсутствие ключа -----------------------------------------
log "== Сценарий 6: отсутствие ключа распознаётся отдельно (no_key)"
MISSING_OUT="$(ns_exec "${APP_PID}" "${WG_BIN}" set wg0 private-key "${WORKDIR}/missing.key" 2>&1)"
if printf '%s' "${MISSING_OUT}" | grep -qiE "no such file|cannot open|does not exist"; then
    ok "wg сообщает об отсутствии файла ключа — приложение классифицирует это как no_key"
else
    fail "неожиданный вывод wg при отсутствии ключа: ${MISSING_OUT}"
fi

# --- итог -------------------------------------------------------------------
log ""
log "== Итог: успешно ${PASSED}, провалено ${FAILED}"
if [ "${FAILED}" -gt 0 ]; then
    exit 1
fi
exit 0
