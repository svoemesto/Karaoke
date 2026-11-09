#!/usr/bin/env bash
# Подготовка установки пира «prod» на прод-хосте (спека 733, FR-010, FR-017).
#
# ВАЖНО: скрипт НЕ выполняет команды на проде и НЕ подключается к нему.
# Он только готовит файлы и печатает точные команды для владельца —
# любые изменения на прод-хосте делаются по отдельному согласию владельца
# (Hard Gate «Прод deploy» в AGENTS.md).
#
# Использование:
#   bash deploy/prod-tunnel/install-prod-tunnel.sh --print-commands
#   bash deploy/prod-tunnel/install-prod-tunnel.sh --prepare <каталог>
#
# Коды возврата: 0 — ок; 2 — ошибка использования.

set -uo pipefail

PROD_HOST="${PROD_TUNNEL_PROD_HOST_SSH:-root@188.127.240.124}"
ADMIN_PUBLIC_KEY="${PROD_TUNNEL_ADMIN_PUBLIC_KEY:-}"
PROD_ADDRESS="${PROD_TUNNEL_PEER_ADDRESS:-10.77.0.2/24}"
ADMIN_ALLOWED="${PROD_TUNNEL_ADMIN_ALLOWED_IPS:-10.77.0.1/32}"
LISTEN_PORT="${PROD_TUNNEL_LISTEN_PORT:-51820}"
TEMPLATE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    sed -n '2,18p' "$0"
    exit "${1:-0}"
}

case "${1:-}" in
    --print-commands) ;;
    --prepare)
        [ -n "${2:-}" ] || usage 2
        prepare_dir="$2"
        mkdir -p "${prepare_dir}"
        log "== Подготовка файлов пира «prod» в ${prepare_dir}"
        if [ -z "${ADMIN_PUBLIC_KEY}" ]; then
            log "ВНИМАНИЕ: PROD_TUNNEL_ADMIN_PUBLIC_KEY не задан — в конфиге останется placeholder."
        fi
        key="${prepare_dir}/wg0.key"
        if command -v wg >/dev/null 2>&1; then
            (umask 077 && wg genkey >"${key}")
            log "   Приватный ключ пира «prod» создан: ${key} (600) — перенесите на прод по защищённому каналу."
            log "   Публичный ключ пира «prod»: $(wg pubkey <"${key}")"
        else
            log "   wg не найден: сгенерируйте ключ на проде командой 'wg genkey > /etc/wireguard/wg0.key'."
        fi
        sed -e "s|<PUBLIC_KEY_OF_ADMIN_PEER>|${ADMIN_PUBLIC_KEY:-<PUBLIC_KEY_OF_ADMIN_PEER>}|" \
            -e "s|10.77.0.2/24|${PROD_ADDRESS}|" \
            -e "s|10.77.0.1/32|${ADMIN_ALLOWED}|" \
            -e "s|ListenPort = 51820|ListenPort = ${LISTEN_PORT}|" \
            "${TEMPLATE_DIR}/wg0-prod.conf.template" >"${prepare_dir}/wg0.conf"
        log "   Конфиг пира «prod»: ${prepare_dir}/wg0.conf (шаблон с подставленными значениями)."
        exit 0
        ;;
    --help | -h) usage 0 ;;
    *) usage 2 ;;
esac

cat <<EOF
== Установка пира «prod» на прод-хосте (${PROD_HOST})

Требуется согласие владельца на изменение прод-хоста. Команды выполняются
вручную владельцем (или агентом — по отдельному согласованию).

1. Установить wireguard-tools:
   ssh ${PROD_HOST} 'apt-get update && apt-get install -y wireguard-tools'

2. Создать приватный ключ пира «prod» и конфиг (ключ НЕ покидает прод):
   ssh ${PROD_HOST} 'install -d -m 700 /etc/wireguard && (umask 077 && wg genkey > /etc/wireguard/wg0.key)'
   # затем отредактировать /etc/wireguard/wg0.conf по образцу deploy/prod-tunnel/wg0-prod.conf.template:
   #   Address = ${PROD_ADDRESS}
   #   ListenPort = ${LISTEN_PORT}
   #   PrivateKey = <содержимое /etc/wireguard/wg0.key>
   #   [Peer] PublicKey = <публичный ключ админского пира>
   #            AllowedIPs = ${ADMIN_ALLOWED}

3. Поднять туннель и проверить рукопожатие:
   ssh ${PROD_HOST} 'wg-quick up wg0 && wg show wg0'

4. Откат (занимает секунды, не требует перезапуска приложения):
   ssh ${PROD_HOST} 'wg-quick down wg0'

5. Allow-list прод-хоста: заменить публичный адрес админской машины на
   постоянный внутренний адрес туннеля ${ADMIN_ALLOWED%%/*} — во всех трёх местах
   (stream-блоки 5433/9000 в /etc/nginx/nginx.conf и HTTP-локации
   /changerecords, /api/properties/setproperty в /etc/nginx/sites-enabled/80to8897),
   с бэкапом, проверкой 'nginx -t' и reload. Порядок: сначала allow-list, потом
   переключение каналов приложения (иначе доступ пропадёт).

6. Только после успешной проверки туннеля и allow-list — закрыть публичные
   порты 5433/9000 (FR-018).
EOF
exit 0
