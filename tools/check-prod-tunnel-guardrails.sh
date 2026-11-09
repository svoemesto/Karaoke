#!/usr/bin/env bash
# =====================================================================
# tools/check-prod-tunnel-guardrails.sh — guard правил постоянного туннеля
# =====================================================================
#
# Спека 733 (KARAOKE-407): постоянный туннель админ-машина→прод (WireGuard).
# Guard проверяет ТОЛЬКО инварианты этой фичи:
#
#   1. compose контейнера `karaoke-app` объявляет всё, что нужно туннелю:
#      `NET_ADMIN`, `/dev/net/tun`, `net.ipv4.ip_forward=1`, монтирование
#      secret-каталога в режиме :ro и переменные `PROD_TUNNEL_*`;
#   2. образ `karaoke-app` содержит `wireguard-tools`, `iproute2`, `iptables`
#      (иначе приложение не сможет поднять интерфейс);
#   3. в репозитории нет приватных ключей и сертификатов туннеля
#      (FR-013, FR-030, Constitution VIII): в `deploy/prod-tunnel/` — только
#      шаблоны с плейсхолдерами;
#   4. подсеть туннеля `10.77.0.0/24` не пересекается с известными сетями
#      машины (`tun0`, docker-сети, VPN-маршруты, LAN) — FR-007;
#   5. шаблоны пиров содержат адресацию из плана: админская сторона
#      `10.77.0.1`, прод-сторона `10.77.0.2`, `AllowedIPs` пира «prod» —
#      `10.77.0.1/32` (FR-005);
#   6. автоматика allow-list умеет режим туннеля и в нём НЕ пишет публичный
#      адрес (FR-009);
#   7. в коде туннеля нет вывода приватного ключа или содержимого конфига
#      в журнал (FR-030).
#
# Использование:
#   bash tools/check-prod-tunnel-guardrails.sh              # проверка с отчётом
#   bash tools/check-prod-tunnel-guardrails.sh --quiet      # для pre-commit
#
# Коды возврата: 0 — нарушений нет; 1 — нарушения.

set -uo pipefail

MODE="report"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

for arg in "$@"; do
  case "$arg" in
    --quiet) MODE="quiet" ;;
    *) echo "Неизвестный аргумент: $arg" >&2; exit 2 ;;
  esac
done

pass=0
fail=0

ok() {
  pass=$((pass + 1))
  [ "$MODE" != "quiet" ] && echo "  OK $1"
  return 0
}

bad() {
  fail=$((fail + 1))
  echo "  FAIL $1" >&2
  return 0
}

COMPOSE="deploy/docker-compose-app.yml"
DOCKERFILE="deploy/karaoke-app/Dockerfile"
TUNNEL_DIR="deploy/prod-tunnel"
ALLOWLIST="deploy/admin-ip-allowlist.sh"
TUNNEL_SUBNET="10.77.0.0/24"

echo "== Проверка инвариантов постоянного туннеля к проду (спека 733)"

# --- 1. compose -------------------------------------------------------------
if [ ! -f "${COMPOSE}" ]; then
  bad "нет файла ${COMPOSE}"
else
  grep -q "NET_ADMIN" "${COMPOSE}" && ok "compose: NET_ADMIN объявлен" || bad "compose: нет NET_ADMIN (приложение не сможет поднять интерфейс)"
  grep -q "/dev/net/tun" "${COMPOSE}" && ok "compose: /dev/net/tun подключён" || bad "compose: нет /dev/net/tun"
  grep -q "net.ipv4.ip_forward" "${COMPOSE}" && ok "compose: ip_forward включён (доступ с хоста через туннель)" || bad "compose: нет sysctl net.ipv4.ip_forward"
  grep -q "/run/karaoke/tunnel:ro" "${COMPOSE}" && ok "compose: secret-каталог монтируется read-only" || bad "compose: secret-каталог монтируется не read-only или не в /run/karaoke/tunnel"
  for var in PROD_TUNNEL_ENABLED PROD_TUNNEL_INTERFACE PROD_TUNNEL_PEER_PUBLIC_KEY PROD_TUNNEL_ENDPOINT PROD_TUNNEL_PROD_HOST; do
    grep -q "${var}" "${COMPOSE}" && ok "compose: объявлена переменная ${var}" || bad "compose: нет переменной ${var}"
  done
fi

# --- 2. образ ---------------------------------------------------------------
if [ ! -f "${DOCKERFILE}" ]; then
  bad "нет файла ${DOCKERFILE}"
else
  for pkg in wireguard-tools iproute2 iptables; do
    grep -q "${pkg}" "${DOCKERFILE}" && ok "образ: ${pkg} установлен" || bad "образ: нет пакета ${pkg}"
  done
fi

# --- 3. секреты не в репозитории -------------------------------------------
if [ -d "${TUNNEL_DIR}" ]; then
  secret_files="$(find "${TUNNEL_DIR}" -type f \( -name "*.key" -o -name "*.pem" -o -name "*.p12" -o -name "*.pfx" -o -name "*.crt" \) 2>/dev/null | head -5)"
  if [ -z "${secret_files}" ]; then
    ok "в ${TUNNEL_DIR} нет файлов ключей/сертификатов"
  else
    bad "в ${TUNNEL_DIR} найдены файлы ключей/сертификатов: ${secret_files}"
  fi
  # В шаблонах не должно быть реальных ключей (base64 44 символа).
  if grep -REn "PrivateKey[[:space:]]*=[[:space:]]*[A-Za-z0-9+/]{43}=" "${TUNNEL_DIR}" >/dev/null 2>&1; then
    bad "в шаблонах туннеля найден приватный ключ"
  else
    ok "в шаблонах туннеля нет приватных ключей"
  fi
else
  bad "нет каталога ${TUNNEL_DIR}"
fi

# --- 4. подсеть туннеля не пересекается с известными сетями ------------------
python3 - <<'PY'
import ipaddress
import sys

tunnel = ipaddress.ip_network("10.77.0.0/24")
forbidden = [
    "172.28.3.0/24",   # tun0 (OpenVPN к pfSense)
    "172.18.0.0/16",   # docker-сеть karaokenet
    "172.21.0.0/16",
    "172.23.0.0/16",
    "10.128.0.0/16",   # VPN-маршруты
    "10.0.1.0/24",     # LAN админской машины
]
bad = [f for f in forbidden if tunnel.overlaps(ipaddress.ip_network(f))]
if bad:
    print("FAIL: подсеть туннеля пересекается с: " + ", ".join(bad))
    sys.exit(1)
print("OK: подсеть туннеля не пересекается с известными сетями машины")
PY
if [ $? -eq 0 ]; then ok "подсеть ${TUNNEL_SUBNET} свободна"; else bad "подсеть ${TUNNEL_SUBNET} пересекается с известными сетями"; fi

# --- 5. шаблоны пиров --------------------------------------------------------
ADMIN_TEMPLATE="${TUNNEL_DIR}/wg0-admin.conf.template"
PROD_TEMPLATE="${TUNNEL_DIR}/wg0-prod.conf.template"
if [ -f "${ADMIN_TEMPLATE}" ]; then
  grep -q "Address = 10.77.0.1/24" "${ADMIN_TEMPLATE}" && ok "шаблон админского пира: адрес 10.77.0.1/24" || bad "шаблон админского пира: нет Address = 10.77.0.1/24"
  grep -q "AllowedIPs = 10.77.0.2/32" "${ADMIN_TEMPLATE}" && ok "шаблон админского пира: AllowedIPs = 10.77.0.2/32" || bad "шаблон админского пира: нет AllowedIPs = 10.77.0.2/32"
else
  bad "нет файла ${ADMIN_TEMPLATE}"
fi
if [ -f "${PROD_TEMPLATE}" ]; then
  grep -q "Address = 10.77.0.2/24" "${PROD_TEMPLATE}" && ok "шаблон пира prod: адрес 10.77.0.2/24" || bad "шаблон пира prod: нет Address = 10.77.0.2/24"
  grep -q "AllowedIPs = 10.77.0.1/32" "${PROD_TEMPLATE}" && ok "шаблон пира prod: AllowedIPs = 10.77.0.1/32 (один постоянный адрес)" || bad "шаблон пира prod: нет AllowedIPs = 10.77.0.1/32"
else
  bad "нет файла ${PROD_TEMPLATE}"
fi

# --- 6. автоматика allow-list умеет режим туннеля ---------------------------
if [ -f "${ALLOWLIST}" ]; then
  grep -q 'ALLOWLIST_MODE' "${ALLOWLIST}" && ok "автоматика allow-list: есть режим ALLOWLIST_MODE" || bad "автоматика allow-list: нет режима ALLOWLIST_MODE"
  grep -q 'run_tunnel_check' "${ALLOWLIST}" && ok "автоматика allow-list: есть проверка run_tunnel_check" || bad "автоматика allow-list: нет run_tunnel_check"
  grep -q 'reason=tunnel_address_mismatch' "${ALLOWLIST}" && ok "автоматика allow-list: событие расхождения зафиксировано" || bad "автоматика allow-list: нет события tunnel_address_mismatch"
else
  bad "нет файла ${ALLOWLIST}"
fi

# --- 7. секреты не попадают в журнал ----------------------------------------
KOTLIN_TUNNEL_DIR="karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tunnel"
if [ -d "${KOTLIN_TUNNEL_DIR}" ]; then
  if grep -REn "privateKeyPath.*(println|log\.|AppLog)" "${KOTLIN_TUNNEL_DIR}" >/dev/null 2>&1 ||
    grep -REn "readText\(\).*privateKey" "${KOTLIN_TUNNEL_DIR}" >/dev/null 2>&1; then
    bad "в коде туннеля есть вывод приватного ключа в журнал"
  else
    ok "в коде туннеля нет вывода приватного ключа"
  fi
  grep -q "private-key" "${KOTLIN_TUNNEL_DIR}/WireGuardProdTunnel.kt" &&
    ok "приложение передаёт путь к ключу в wg (ключ не читается приложением)" ||
    bad "в реализации туннеля нет передачи пути к ключу в wg"
else
  bad "нет каталога ${KOTLIN_TUNNEL_DIR}"
fi

if [ "$MODE" != "quiet" ]; then
  echo
  echo "--- итог: PASS=${pass} FAIL=${fail} ---"
fi

if [ "${fail}" -ne 0 ]; then
  exit 1
fi
exit 0
