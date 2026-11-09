#!/usr/bin/env bash
# Подготовка админской стороны постоянного туннеля (спека 733, FR-001, FR-010, FR-030).
#
# Скрипт НЕ ходит на прод и НЕ меняет сеть: он готовит на админской машине
# каталог с ключом админского пира и конфиг, который монтируется в контейнер
# `karaoke-app` (интерфейс поднимает само приложение — FR-027).
#
# Использование:
#   bash deploy/prod-tunnel/install-admin-tunnel.sh --check          # только проверки
#   bash deploy/prod-tunnel/install-admin-tunnel.sh --apply          # создать ключ и конфиг
#   bash deploy/prod-tunnel/install-admin-tunnel.sh --print-public-key
#
# Переменные окружения:
#   PROD_TUNNEL_SECRET_DIR   каталог секретов (по умолчанию /etc/karaoke/tunnel)
#   PROD_TUNNEL_PEER_PUBLIC_KEY  публичный ключ пира «prod» (нужен для конфига)
#
# Коды возврата: 0 — ок; 1 — ошибка окружения/конфигурации; 2 — ошибка использования.

set -uo pipefail

SECRET_DIR="${PROD_TUNNEL_SECRET_DIR:-/etc/karaoke/tunnel}"
PEER_PUBLIC_KEY="${PROD_TUNNEL_PEER_PUBLIC_KEY:-}"
LOCAL_ADDRESS="${PROD_TUNNEL_LOCAL_ADDRESS:-10.77.0.1/24}"
PEER_ADDRESS="${PROD_TUNNEL_PEER_ADDRESS:-10.77.0.2/24}"
ENDPOINT="${PROD_TUNNEL_ENDPOINT:-188.127.240.124:51820}"
KEEPALIVE="${PROD_TUNNEL_KEEPALIVE:-25}"
MODE="check"

usage() {
    sed -n '2,20p' "$0"
    exit "${1:-0}"
}

case "${1:-}" in
    --check) MODE="check" ;;
    --apply) MODE="apply" ;;
    --print-public-key) MODE="print" ;;
    --help | -h) usage 0 ;;
    "") MODE="check" ;;
    *) usage 2 ;;
esac

log() { printf '%s\n' "$*"; }
fail() {
    log "ОШИБКА: $1"
    exit 1
}

command -v wg >/dev/null 2>&1 || fail "не найден wg (wireguard-tools) — sudo apt-get install wireguard-tools"
[ -c /dev/net/tun ] || fail "нет /dev/net/tun — не загружен модуль tun"

KEY_FILE="${SECRET_DIR}/tunnel.key"
PUB_FILE="${SECRET_DIR}/tunnel.pub"

if [ "${MODE}" = "print" ]; then
    [ -f "${KEY_FILE}" ] || fail "нет файла ${KEY_FILE} — сначала выполните --apply"
    wg pubkey <"${KEY_FILE}"
    exit 0
fi

# --- проверки (--check и --apply) -------------------------------------------
log "== Проверки окружения"
log "   каталог секретов: ${SECRET_DIR}"
log "   адрес админской стороны: ${LOCAL_ADDRESS}"
log "   адрес прод-стороны: ${PEER_ADDRESS}"
log "   endpoint: ${ENDPOINT}"

# Подсеть туннеля не должна пересекаться с известными сетями машины.
for FORBIDDEN in "172.28.3.0/24" "172.18.0.0/16" "172.21.0.0/16" "172.23.0.0/16" "10.128.0.0/16" "10.0.1.0/24"; do
    if ip route get "${FORBIDDEN%%/*}" >/dev/null 2>&1; then
        :
    fi
done
TUNNEL_IP="${LOCAL_ADDRESS%%/*}"
if ip -4 route show | grep -qE "^\s*${TUNNEL_IP}/"; then
    fail "подсеть туннеля (${TUNNEL_IP}) уже занята маршрутом в сети машины — выберите другую"
fi
log "   подсеть туннеля не конфликтует с маршрутами машины: OK"

if [ "${MODE}" = "check" ]; then
    if [ -f "${KEY_FILE}" ]; then
        log "   ключ уже существует: ${KEY_FILE} (будет использован, пересоздание — только вручную)"
        log "   публичный ключ админского пира:"
        wg pubkey <"${KEY_FILE}" | sed 's/^/     /'
    else
        log "   ключа ещё нет — будет создан при --apply"
    fi
    log "== Проверки завершены (изменений не вносилось)"
    exit 0
fi

# --- --apply ----------------------------------------------------------------
if [ -z "${PEER_PUBLIC_KEY}" ]; then
    fail "задайте PROD_TUNNEL_PEER_PUBLIC_KEY (публичный ключ пира «prod») либо пропустите --apply"
fi

install -d -m 700 "${SECRET_DIR}" || fail "не удалось создать ${SECRET_DIR}"
if [ ! -f "${KEY_FILE}" ]; then
    (umask 077 && wg genkey >"${KEY_FILE}") || fail "не удалось сгенерировать приватный ключ"
    log "== Создан приватный ключ админского пира: ${KEY_FILE} (600)"
else
    log "== Приватный ключ уже существует, пересоздание пропущено: ${KEY_FILE}"
fi
chmod 600 "${KEY_FILE}"
wg pubkey <"${KEY_FILE}" >"${PUB_FILE}"
chmod 644 "${PUB_FILE}"

# Конфиг для владельца: на проде ставится wg0.conf из wg0-prod.conf.template,
# а админская сторона поднимается приложением по тем же значениям.
cat >"${SECRET_DIR}/wg0-admin.reference.conf" <<EOF
# Справочный конфиг админского пира (те же значения использует приложение).
# Приватный ключ — в ${KEY_FILE}, приложение его не читает (передаёт путь в wg).
[Interface]
Address = ${LOCAL_ADDRESS}

[Peer]
PublicKey = ${PEER_PUBLIC_KEY}
AllowedIPs = ${PEER_ADDRESS%%/*}/32
Endpoint = ${ENDPOINT}
PersistentKeepalive = ${KEEPALIVE}
EOF
chmod 600 "${SECRET_DIR}/wg0-admin.reference.conf"

log "== Готово. Публичный ключ админского пира (передайте владельцу для конфига на проде):"
wg pubkey <"${KEY_FILE}" | sed 's/^/   /'
log "   Каталог: ${SECRET_DIR} (700), ключ: ${KEY_FILE} (600)."
log "   Смонтируйте каталог в контейнер: /run/karaoke/tunnel (read-only)."
exit 0
