#!/usr/bin/env bash
# Маршрут хоста админской машины в подсеть постоянного туннеля (спека 733, FR-031, FR-032).
#
# Туннель живёт в сетевом пространстве контейнера `karaoke-app` (туннелем
# владеет приложение — FR-027). Чтобы оператор с хоста (DBeaver) ходил в прод-БД
# по ТОМУ ЖЕ постоянному внутреннему адресу `10.77.0.2`, что и приложение,
# хосту нужен маршрут в подсеть туннеля через контейнер. Источник трафика на
# проде при этом остаётся адресом туннеля `10.77.0.1` (SNAT в контейнере) —
# поэтому в allow-list прод-хоста достаточно одной строки (FR-005).
#
# Использование:
#   bash deploy/prod-tunnel/host-route.sh --check     # показать состояние
#   bash deploy/prod-tunnel/host-route.sh --apply     # поставить/обновить маршрут
#   bash deploy/prod-tunnel/host-route.sh --remove    # снять маршрут (откат)
#
# Переменные окружения:
#   PROD_TUNNEL_SUBNET      подсеть туннеля (по умолчанию 10.77.0.0/24)
#   PROD_TUNNEL_CONTAINER   контейнер с туннелем (по умолчанию karaoke-app)
#   PROD_TUNNEL_GATEWAY_IP  явный IP шлюза (если контейнер не найден)
#
# Коды возврата: 0 — ок; 1 — ошибка; 2 — ошибка использования.

set -uo pipefail

SUBNET="${PROD_TUNNEL_SUBNET:-10.77.0.0/24}"
CONTAINER="${PROD_TUNNEL_CONTAINER:-karaoke-app}"
GATEWAY_IP="${PROD_TUNNEL_GATEWAY_IP:-}"

usage() {
    sed -n '2,22p' "$0"
    exit "${1:-0}"
}

case "${1:-}" in
    --check) MODE="check" ;;
    --apply) MODE="apply" ;;
    --remove) MODE="remove" ;;
    --help | -h) usage 0 ;;
    *) usage 2 ;;
esac

log() { printf '%s\n' "$*"; }

current_route() {
    ip -4 route show "${SUBNET}" 2>/dev/null | head -1
}

container_ip() {
    if [ -n "${GATEWAY_IP}" ]; then
        echo "${GATEWAY_IP}"
        return 0
    fi
    docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${CONTAINER}" 2>/dev/null | head -1
}

case "${MODE}" in
    check)
        log "== Состояние маршрута хоста в подсеть туннеля"
        log "   подсеть: ${SUBNET}"
        route="$(current_route)"
        if [ -n "${route}" ]; then
            log "   маршрут есть: ${route}"
        else
            log "   маршрута нет"
        fi
        ip_addr="$(container_ip)"
        if [ -n "${ip_addr}" ]; then
            log "   IP контейнера ${CONTAINER}: ${ip_addr}"
        else
            log "   контейнер ${CONTAINER} не найден (или нет DOCKER_CONFIG) — укажите PROD_TUNNEL_GATEWAY_IP"
        fi
        exit 0
        ;;
    remove)
        if ip -4 route show "${SUBNET}" | grep -q .; then
            ip -4 route del "${SUBNET}" && log "== Маршрут ${SUBNET} снят (откат)"
        else
            log "== Маршрута ${SUBNET} не было — ничего не менялось"
        fi
        exit 0
        ;;
esac

# --- apply ------------------------------------------------------------------
if [ "$(id -u)" != "0" ]; then
    log "ОШИБКА: нужен root (маршрут в системном пространстве). Установите юнит karaoke-tunnel-host-route.service или выполните через sudo."
    exit 1
fi
gateway="$(container_ip)"
[ -n "${gateway}" ] || {
    log "ОШИБКА: не удалось определить IP контейнера ${CONTAINER} — задайте PROD_TUNNEL_GATEWAY_IP"
    exit 1
}
sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1 || true
if ip -4 route show "${SUBNET}" | grep -q .; then
    ip -4 route replace "${SUBNET}" via "${gateway}" dev "$(ip -4 route show "${SUBNET}" | head -1 | awk '{for(i=1;i<=NF;i++) if ($i=="dev") print $(i+1)}')" 2>/dev/null ||
        ip -4 route replace "${SUBNET}" via "${gateway}"
else
    ip -4 route replace "${SUBNET}" via "${gateway}"
fi
log "== Маршрут ${SUBNET} → ${gateway} (контейнер ${CONTAINER}) установлен"
log "   Проверка: ping -c1 ${SUBNET%%.*}.0.2 (внутри туннеля) либо 'wg show' в контейнере."
exit 0
