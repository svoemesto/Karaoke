#!/usr/bin/env bash
# =====================================================================
# deploy/tracker-db-backup.sh — ежедневный бэкап Postgres OpenProject
# =====================================================================
# Запускается через systemd-timer tracker-db-backup.timer (03:00 UTC daily).
# Retention: 7 дней (удаляем дампы старше 7 дней).
#
# Секреты НЕ хранятся в этом скрипте — берутся из ../.env.local-tracker
# (переменная TRACKER_DB_PASSWORD). См. Constitution § VIII.
#
# Алгоритм:
#   1. Проверить, что .env.local-tracker существует.
#   2. Загрузить TRACKER_DB_PASSWORD.
#   3. Проверить, что контейнер openproject-db запущен.
#   4. pg_dump в custom format (-Fc) → /backups/tracker-YYYY-MM-DD.dump
#   5. Удалить дампы старше 7 дней.
# =====================================================================

set -euo pipefail

# --- Настройки ---
BACKUP_DIR="/backups"
BACKUP_NAME="tracker"
RETENTION_DAYS=7
CONTAINER_NAME="openproject-db"
DB_NAME="openproject"
DB_USER="openproject"
DB_PORT="5435"
TIMESTAMP=$(date +%Y-%m-%d)
DUMP_FILE="${BACKUP_DIR}/${BACKUP_NAME}-${TIMESTAMP}.dump"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}" && cd .. && pwd)"
ENV_FILE="${REPO_ROOT}/.env.local-tracker"

# --- Логирование (без вывода секретов) ---
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

# --- Проверки ---
if [ ! -f "$ENV_FILE" ]; then
    log "ERROR: ${ENV_FILE} не найден"
    exit 1
fi

# Загружаем креды БД из .env (НЕ выводим в лог)
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ -z "${TRACKER_DB_PASSWORD:-}" ]; then
    log "ERROR: TRACKER_DB_PASSWORD не задан в ${ENV_FILE}"
    exit 1
fi

# Проверяем, что контейнер openproject-db запущен
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log "ERROR: контейнер ${CONTAINER_NAME} не запущен"
    log "  Запустите: cd ${REPO_ROOT}/deploy && docker compose -f tracker-docker-compose.yml up -d"
    exit 1
fi

# --- Создание директории ---
mkdir -p "$BACKUP_DIR" 2>/dev/null || true

# --- Бэкап ---
log "Начало бэкапа БД ${DB_NAME} → ${DUMP_FILE}"

# pg_dump через docker exec (используем TCP-соединение через хост)
# -Fc — custom format (сжатый, совместим с pg_restore --clean --if-exists)
# -h localhost -p 5435 — подключение к Postgres через host network
if ! docker exec \
    -e PGPASSWORD="$TRACKER_DB_PASSWORD" \
    "$CONTAINER_NAME" \
    pg_dump \
        -h localhost \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        --dbname="$DB_NAME" \
        -Fc \
        --no-owner \
        --no-privileges \
        --no-password > "$DUMP_FILE.tmp" 2>"$DUMP_FILE.err"; then
    log "ERROR: pg_dump завершился с ошибкой:"
    sed 's/^/  /' "$DUMP_FILE.err" >&2
    rm -f "$DUMP_FILE.tmp" "$DUMP_FILE.err"
    exit 1
fi

rm -f "$DUMP_FILE.err"
mv "$DUMP_FILE.tmp" "$DUMP_FILE"

# Проверка, что файл не пустой
if [ ! -s "$DUMP_FILE" ]; then
    log "ERROR: бэкап пустой — возможно БД недоступна"
    rm -f "$DUMP_FILE"
    exit 1
fi

DUMP_SIZE=$(du -h "$DUMP_FILE" | cut -f1)
log "Бэкап создан: ${DUMP_FILE} (${DUMP_SIZE})"

# --- Retention: удаляем бэкапы старше 7 дней ---
log "Очистка бэкапов старше ${RETENTION_DAYS} дней..."
DELETED=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}-*.dump" -type f -mtime +$RETENTION_DAYS -print -delete | wc -l)
log "Удалено старых бэкапов: ${DELETED}"

# --- Сводка ---
TOTAL=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}-*.dump" -type f | wc -l)
DISK_USAGE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "unknown")
log "Готово. Бэкапов в хранилище: ${TOTAL}, занято: ${DISK_USAGE}"
