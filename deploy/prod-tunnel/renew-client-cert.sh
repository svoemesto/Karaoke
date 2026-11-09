#!/usr/bin/env bash
# Автопродление клиентского сертификата mTLS к прод-БД (спека 733, FR-014).
#
# Владелец (2026-11-09): срок клиентского сертификата 90 дней — подходит,
# настроить автопродление.
#
# Как устроено:
#   * приватный ключ клиента генерируется НА АДМИНСКОЙ МАШИНЕ и её не покидает;
#   * на прод-хост уходит только CSR (запрос на подпись), возвращается
#     подписанный сертификат — ключ CA с прода не снимается никогда;
#   * на клиенте держим ключ в PEM (канон, для продления) и в PKCS-8 DER
#     (в этом виде его читает драйвер pgjdbc — `sslkey`);
#   * старые файлы сохраняются как `.prev` — откат в одну команду.
#
# Использование:
#   bash deploy/prod-tunnel/renew-client-cert.sh --check     # сколько дней осталось
#   bash deploy/prod-tunnel/renew-client-cert.sh --apply     # продлить, если мало
#   bash deploy/prod-tunnel/renew-client-cert.sh --force     # продлить немедленно
#
# Переменные окружения:
#   PROD_TUNNEL_SECRET_DIR     каталог секретов (по умолчанию /etc/karaoke/tunnel)
#   PROD_TUNNEL_RENEW_DAYS     порог продления в днях (по умолчанию 21)
#   PROD_TUNNEL_CERT_DAYS      срок нового сертификата (по умолчанию 90)
#   PROD_TUNNEL_CERT_CN        CN сертификата (по умолчанию имя хоста)
#   PROD_TUNNEL_PROD_HOST      прод-хост (по умолчанию 188.127.240.124)
#   PROD_TUNNEL_CA_DIR         каталог CA на проде (/root/Karaoke/deploy/certs/postgres)
#
# Коды возврата: 0 — ок (в т.ч. «продление не требуется»); 1 — ошибка; 2 — использование.

set -uo pipefail

SECRET_DIR="${PROD_TUNNEL_SECRET_DIR:-/etc/karaoke/tunnel}"
RENEW_DAYS="${PROD_TUNNEL_RENEW_DAYS:-21}"
CERT_DAYS="${PROD_TUNNEL_CERT_DAYS:-90}"
CERT_CN="${PROD_TUNNEL_CERT_CN:-$(hostname -s)}"
PROD_HOST="${PROD_TUNNEL_PROD_HOST:-188.127.240.124}"
CA_DIR="${PROD_TUNNEL_CA_DIR:-/root/Karaoke/deploy/certs/postgres}"

CRT="${SECRET_DIR}/db-client.crt"
KEY_PEM="${SECRET_DIR}/db-client.key.pem"
KEY_DER="${SECRET_DIR}/db-client.key"
CA_CRT="${SECRET_DIR}/ca.crt"

log() { printf '%s\n' "$*"; }
die() { log "ОШИБКА: $1"; exit 1; }

MODE="check"
case "${1:-}" in
    --check) MODE="check" ;;
    --apply) MODE="apply" ;;
    --force) MODE="apply"; RENEW_DAYS=99999 ;;
    --help | -h) sed -n '2,25p' "$0"; exit 0 ;;
    *) sed -n '2,25p' "$0"; exit 2 ;;
esac

command -v openssl >/dev/null 2>&1 || die "нет openssl"
[ -f "${CRT}" ] || die "нет ${CRT} — сначала установите mTLS (runbook §3.5)"

days_left() {
    local end epoch now
    end="$(openssl x509 -in "${CRT}" -noout -enddate 2>/dev/null | cut -d= -f2)"
    [ -n "${end}" ] || { echo "-1"; return; }
    epoch="$(date -d "${end}" +%s 2>/dev/null || echo 0)"
    now="$(date +%s)"
    echo $(( (epoch - now) / 86400 ))
}

LEFT="$(days_left)"
log "== клиентский сертификат mTLS"
log "   файл: ${CRT}"
log "   субъект: $(openssl x509 -in "${CRT}" -noout -subject 2>/dev/null | sed 's/^subject=//')"
log "   истекает: $(openssl x509 -in "${CRT}" -noout -enddate 2>/dev/null | cut -d= -f2) (осталось дней: ${LEFT})"
log "   порог продления: ${RENEW_DAYS} дней"

if [ "${MODE}" = "check" ]; then
    if [ "${LEFT}" -lt 0 ]; then
        log "   СОСТОЯНИЕ: сертификат просрочен"
        exit 1
    elif [ "${LEFT}" -le "${RENEW_DAYS}" ]; then
        log "   СОСТОЯНИЕ: требуется продление (--apply)"
    else
        log "   СОСТОЯНИЕ: продление не требуется"
    fi
    exit 0
fi

if [ "${LEFT}" -gt "${RENEW_DAYS}" ]; then
    log "== продление не требуется (осталось ${LEFT} дней > ${RENEW_DAYS})"
    exit 0
fi

log "== продление: выпускаем новый ключ и сертификат"
if [ ! -f "${KEY_PEM}" ]; then
    if [ -f "${KEY_DER}" ]; then
        # миграция: раньше каноном был DER
        openssl pkcs8 -inform DER -in "${KEY_DER}" -out "${KEY_PEM}" 2>/dev/null \
            || die "не удалось прочитать ${KEY_DER}"
        chmod 600 "${KEY_PEM}"
        log "   ключ приведён к PEM-канону: ${KEY_PEM}"
    else
        die "нет ключа клиента (${KEY_PEM} или ${KEY_DER})"
    fi
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/tunnel-renew.XXXXXX")" || die "mktemp"
trap 'rm -rf "${WORK}"' EXIT

# Новый ключ создаётся здесь и на прод не уходит.
(umask 077 && openssl genrsa -out "${WORK}/new.key.pem" 2048 2>/dev/null) || die "генерация ключа"
openssl req -new -key "${WORK}/new.key.pem" -subj "/CN=${CERT_CN}" -out "${WORK}/new.csr" 2>/dev/null \
    || die "создание CSR"

log "   CSR создан на админ-машине (CN=${CERT_CN}); ключ остаётся здесь"
log "   подпись на ${PROD_HOST} ключом CA (${CA_DIR})"

SSH_CMD=(ssh -o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=accept-new -i "${SSH_IDENTITY:-/root/.ssh/id_ed25519_karaoke_tunnel}" "${PROD_HOST}")
"${SSH_CMD[@]}" "cat > /tmp/tunnel-renew.csr && cd ${CA_DIR} && openssl x509 -req -in /tmp/tunnel-renew.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days ${CERT_DAYS} -sha256 -out /tmp/tunnel-renew.crt 2>/dev/null && cat /tmp/tunnel-renew.crt && rm -f /tmp/tunnel-renew.csr /tmp/tunnel-renew.crt" \
    < "${WORK}/new.csr" > "${WORK}/new.crt" 2>"${WORK}/ssh.err" \
    || die "подпись на проде не удалась: $(tail -1 "${WORK}/ssh.err" 2>/dev/null)"

head -1 "${WORK}/new.crt" | grep -q "BEGIN CERTIFICATE" \
    || die "прод вернул не сертификат: $(head -1 "${WORK}/new.crt")"

openssl x509 -in "${WORK}/new.crt" -noout -subject >/dev/null 2>&1 || die "получен невалидный сертификат"
openssl verify -CAfile "${CA_CRT}" "${WORK}/new.crt" >/dev/null 2>&1 \
    || die "новый сертификат не проверяется CA из ${CA_CRT}"

log "   подпись проверена по ${CA_CRT}"

# Установка: старые файлы — .prev (откат), DER — из нового PEM.
for f in "${CRT}" "${KEY_DER}" "${KEY_PEM}"; do
    [ -f "${f}" ] && cp -a "${f}" "${f}.prev"
done
install -m 600 "${WORK}/new.key.pem" "${KEY_PEM}"
openssl pkcs8 -topk8 -inform PEM -outform DER -in "${KEY_PEM}" -nocrypt -out "${KEY_DER}" 2>/dev/null \
    || die "конвертация ключа в PKCS-8 DER"
chmod 600 "${KEY_DER}"
install -m 644 "${WORK}/new.crt" "${CRT}"

NEW_LEFT="$(days_left)"
log "== готово: новый сертификат установлен (осталось дней: ${NEW_LEFT})"
log "   канон ключа: ${KEY_PEM} (600), для драйвера: ${KEY_DER} (600, PKCS-8 DER)"
log "   предыдущие файлы: *.prev — откат: mv db-client.crt.prev db-client.crt"
log "   приложение подхватит сертификат на новых соединениях;"
log "   если нужен немедленный переход — перезапуск karaoke-app по Pass 380"
