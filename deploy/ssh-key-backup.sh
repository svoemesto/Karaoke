#!/usr/bin/env bash
# =====================================================================
# ssh-key-backup.sh — резервная копия SSH-ключей и доступа к прод-хосту
# =====================================================================
#
# ЗАЧЕМ. После отключения входа по паролю (PasswordAuthentication no) ключ
# становится единственным способом попасть на прод-хост по SSH. Потеря ключа
# = простой доступа до подъёма консоли провайдера. Бэкап снимается ЗАРАНЕЕ и
# хранится отдельно от машины.
#
# ЧТО СОБИРАЕТ:
#   * приватные и публичные ключи админ-машины (пользователь nsa и root),
#     которые реально пускают на прод (сверка по отпечаткам с authorized_keys);
#   * authorized_keys прод-хоста и его sshd-конфигурацию (включая drop-in);
#   * отпечатки ключей и текстовый инвентарь «что от чего» — чтобы при
#     восстановлении не гадать, какой ключ куда;
#   * итоговый архив tar.gz.
#
# ЧЕГО НЕ ДЕЛАЕТ: не шифрует. Каталог создаётся с правами 700 (только root),
# но копию для хранения вне машины владелец уносит сам — в менеджер паролей
# или на защищённый носитель. Шифрование — по желанию:
#   tar -czf - -C <каталог> . | openssl enc -aes-256-cbc -pbkdf2 -out backup.tar.gz.enc
#
# Использование:
#   bash deploy/ssh-key-backup.sh              # снять бэкап
#   bash deploy/ssh-key-backup.sh --list       # только показать, что есть
#
# Переменные окружения:
#   SSH_BACKUP_DIR   каталог бэкапа (по умолчанию /root/ssh-backup-<дата>)
#   SSH_BACKUP_PROD  прод-хост (по умолчанию 188.127.240.124)

set -uo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${SSH_BACKUP_DIR:-/root/ssh-backup-${STAMP}}"
PROD="${SSH_BACKUP_PROD:-188.127.240.124}"
MODE="backup"
[ "${1:-}" = "--list" ] && MODE="list"

log() { printf '%s\n' "$*"; }

# Под root нет пользовательского ~/.ssh/id_rsa: чтобы прочитать authorized_keys
# прода, явно берём служебный ключ (он в authorized_keys прод-хоста). Если ключа
# нет — работаем с настройками по умолчанию.
SERVICE_KEY="/root/.ssh/id_ed25519_karaoke_tunnel"
PROD_SSH=(ssh -o BatchMode=yes -o ConnectTimeout=15)
[ -f "${SERVICE_KEY}" ] && PROD_SSH+=(-i "${SERVICE_KEY}")

# Ключи, которые нас интересуют: пользовательские и служебные.
USER_KEYS=(
    "/home/nsa/.ssh/id_rsa"
    "/home/nsa/.ssh/id_rsa.pub"
    "/root/.ssh/id_ed25519_karaoke_tunnel"
    "/root/.ssh/id_ed25519_karaoke_tunnel.pub"
)

if [ "${MODE}" = "list" ]; then
    log "== Ключи на этой машине"
    for f in "${USER_KEYS[@]}"; do
        if [ -f "$f" ]; then
            fp="$(ssh-keygen -lf "$f" 2>/dev/null | awk '{print $2" ("$4")"}')"
            log "   есть: $f — ${fp:-не читается}"
        else
            log "   нет:  $f"
        fi
    done
    log "== authorized_keys на прод-хосте ${PROD}"
    "${PROD_SSH[@]}" "root@${PROD}" \
        'awk "{print NR\": \"\$1\" \"\$NF}" /root/.ssh/authorized_keys 2>/dev/null' 2>/dev/null ||
        log "   не удалось прочитать (нет доступа)"
    exit 0
fi

if [ "$(id -u)" != "0" ]; then
    log "ОШИБКА: нужен root (ключи root и /root/ssh-backup*)"
    exit 1
fi

mkdir -p "${OUT_DIR}" || { log "ОШИБКА: не удалось создать ${OUT_DIR}"; exit 1; }
chmod 700 "${OUT_DIR}"
log "== бэкап в ${OUT_DIR}"

# 1. Ключи с этой машины.
mkdir -p "${OUT_DIR}/keys/home-nsa" "${OUT_DIR}/keys/root"
chmod 700 "${OUT_DIR}/keys" "${OUT_DIR}/keys/home-nsa" "${OUT_DIR}/keys/root"
copied=0
for f in "${USER_KEYS[@]}"; do
    [ -f "$f" ] || { log "   пропуск (нет файла): $f"; continue; }
    case "$f" in
        /home/nsa/*) cp -a "$f" "${OUT_DIR}/keys/home-nsa/$(basename "$f")" ;;
        /root/*) cp -a "$f" "${OUT_DIR}/keys/root/$(basename "$f")" ;;
    esac
    copied=$((copied + 1))
done
log "   скопировано ключей: ${copied}"

# 2. Инвентарь отпечатков — чтобы при восстановлении не путать ключи.
{
    echo "# Инвентарь SSH-ключей на $(hostname) от $(date -Is)"
    echo
    echo "## Публичные отпечатки"
    for f in "${OUT_DIR}"/keys/*/*.pub "${OUT_DIR}"/keys/*/*[!b]; do
        [ -f "$f" ] || continue
        fp="$(ssh-keygen -lf "$f" 2>/dev/null)" || continue
        echo "$(basename "$f"): ${fp}"
    done
} >"${OUT_DIR}/INVENTORY.txt" 2>/dev/null
chmod 600 "${OUT_DIR}/INVENTORY.txt"

# 3. Что пускает на прод: authorized_keys + sshd-конфигурация.
if "${PROD_SSH[@]}" "root@${PROD}" true 2>/dev/null; then
    mkdir -p "${OUT_DIR}/prod"
    chmod 700 "${OUT_DIR}/prod"
    "${PROD_SSH[@]}" "root@${PROD}" \
        'cat /root/.ssh/authorized_keys 2>/dev/null' >"${OUT_DIR}/prod/authorized_keys" 2>/dev/null
    "${PROD_SSH[@]}" "root@${PROD}" \
        'cat /etc/ssh/sshd_config 2>/dev/null' >"${OUT_DIR}/prod/sshd_config" 2>/dev/null
    "${PROD_SSH[@]}" "root@${PROD}" \
        'for f in /etc/ssh/sshd_config.d/*.conf; do echo "### $f"; cat "$f"; done 2>/dev/null' \
        >"${OUT_DIR}/prod/sshd_config.d.txt" 2>/dev/null
    chmod 600 "${OUT_DIR}/prod/"*
    keys_on_prod="$(grep -c 'ssh-' "${OUT_DIR}/prod/authorized_keys" 2>/dev/null || echo 0)"
    log "   authorized_keys прода: ${keys_on_prod} ключ(ей)"
else
    log "   ВНИМАНИЕ: прод недоступен по SSH — его authorized_keys и sshd-конфиг НЕ сохранены"
fi

# 4. Архив.
if tar -czf "${OUT_DIR}.tar.gz" -C "${OUT_DIR}" . 2>/dev/null; then
    chmod 600 "${OUT_DIR}.tar.gz"
    log "   архив: ${OUT_DIR}.tar.gz ($(du -h "${OUT_DIR}.tar.gz" | cut -f1))"
else
    log "   ВНИМАНИЕ: архив не создан"
fi

log
log "== ГОТОВО"
log "   каталог: ${OUT_DIR} (700), архив: ${OUT_DIR}.tar.gz (600)"
log "   Дальше — забрать копию ВНЕ машины (менеджер паролей / защищённый носитель)."
log "   Каталог на машине не заменяет внешнюю копию: при потере машины он пропадёт вместе с ключами."
exit 0
