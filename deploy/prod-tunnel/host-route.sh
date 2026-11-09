#!/usr/bin/env bash
# Маршрут хоста админ-машины в подсеть туннеля к проду + SNAT в контейнере.
#
# Спека 733 (FR-031, FR-032): туннель живёт в сетевом пространстве контейнера
# `karaoke-app` (туннелем владеет приложение — FR-027). Чтобы оператор с хоста
# (DBeaver) ходил в прод-БД по ТОМУ ЖЕ постоянному внутреннему адресу 10.77.0.2,
# что и приложение, хосту нужен маршрут в подсеть туннеля через контейнер.
#
# SNAT (в контейнере, на выходе в wg0) обязателен: прод-сторона WireGuard
# принимает пакеты только с адреса пира (AllowedIPs = 10.77.0.1/32), а трафик
# хоста приходит в контейнер с адреса docker-шлюза (172.18.0.1). Без SNAT
# пакеты доходят до контейнера и молча отбрасываются на проде — снаружи это
# выглядит как «маршрут есть, а связи нет».
#
# Идемпотентен: можно вызывать по таймеру. Пересоздание контейнера меняет его
# IP и сбрасывает iptables внутри — поэтому маршрут и SNAT пересоздаются.

set -uo pipefail

SUBNET="${PROD_TUNNEL_SUBNET:-10.77.0.0/24}"
CONTAINER="${PROD_TUNNEL_CONTAINER:-karaoke-app}"
SNAT_TO="${PROD_TUNNEL_LOCAL_ADDRESS:-10.77.0.1/24}"
SNAT_TO="${SNAT_TO%/*}"

log() { printf '%s\n' "$*"; }

case "${1:-}" in
    --check)
        log "== маршрут:"
        ip -4 route show "${SUBNET}" || true
        log "== SNAT в контейнере:"
        docker exec "${CONTAINER}" sh -c "iptables -t nat -S POSTROUTING | grep wg0" 2>/dev/null || log "   нет"
        exit 0
        ;;
    --remove)
        ip -4 route del "${SUBNET}" 2>/dev/null && log "маршрут снят" || log "маршрута не было"
        docker exec "${CONTAINER}" sh -c "iptables -t nat -D POSTROUTING -o wg0 -j SNAT --to-source ${SNAT_TO}" 2>/dev/null && log "SNAT снят" || log "SNAT не было"
        exit 0
        ;;
    --apply | "") ;;
    *) log "использование: $0 [--apply|--check|--remove]"; exit 2 ;;
esac

if [ "$(id -u)" != "0" ]; then
    log "ОШИБКА: нужен root"
    exit 1
fi

# Контейнер должен существовать: без него маршрут ведёт в никуда.
gateway="$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "${CONTAINER}" 2>/dev/null | head -1)"
if [ -z "${gateway}" ]; then
    log "контейнер ${CONTAINER} не найден — маршрут не ставим"
    exit 1
fi

# Устройство выбираем по адресу, который реально держит docker-шлюз подсети
# контейнера: у машины много погашенных bridge-интерфейсов, и «первый попавшийся»
# уводит маршрут в никуда (проверено 2026-11-09).
prefix="$(printf '%s' "${gateway}" | cut -d. -f1-2)"
dev="$(ip -4 -o addr show | awk -v p="${prefix}." '$4 ~ "^"p && $2 != "lo" {print $2; exit}')"
if [ -z "${dev}" ]; then
    log "не найден интерфейс с адресом ${prefix}.x — маршрут не ставим"
    exit 1
fi

ip route replace "${SUBNET}" via "${gateway}" dev "${dev}"
log "маршрут: $(ip -4 route show "${SUBNET}")"

# SNAT идемпотентно
if docker exec "${CONTAINER}" sh -c "iptables -t nat -C POSTROUTING -o wg0 -j SNAT --to-source ${SNAT_TO}" 2>/dev/null; then
    log "SNAT уже стоит"
else
    docker exec "${CONTAINER}" sh -c "iptables -t nat -A POSTROUTING -o wg0 -j SNAT --to-source ${SNAT_TO}" \
        && log "SNAT добавлен: -o wg0 → ${SNAT_TO}" \
        || log "ОШИБКА: не удалось добавить SNAT"
fi
