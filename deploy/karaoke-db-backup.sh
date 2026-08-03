#!/bin/bash
# Ежедневный бэкап Postgres БД на прод-сервере.
# Запускается через systemd-timer karaoke-db-backup.timer (05:00 UTC ежедневно).
# Retention: 7 дней (удаляем бэкапы старше 7 дней).
# Лог: /var/log/karaoke-db-backup.log
#
# См. constitution.md Principle VIII (секреты) — пароль БД НЕ хранится в этом скрипте,
# берётся из ~/Karaoke/deploy/.env (DB_SERVER_POSTGRES_PASSWORD).

set -euo pipefail

# --- Настройки ---
BACKUP_DIR="$HOME/Karaoke/dumps"
BACKUP_NAME="karaoke_db"
RETENTION_DAYS=7
LOG_FILE="/var/log/karaoke-db-backup.log"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DUMP_FILE="${BACKUP_DIR}/${BACKUP_NAME}_${TIMESTAMP}.sql.gz"

# --- Логирование ---
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

# --- Проверки ---
if [ ! -f "$HOME/Karaoke/deploy/.env" ]; then
    log "ERROR: ~/Karaoke/deploy/.env не найден"
    exit 1
fi

# Загружаем креды БД из .env (не выводим в лог)
set -a
source "$HOME/Karaoke/deploy/.env"
set +a

if [ -z "${DB_SERVER_POSTGRES_USER:-}" ]; then
    log "ERROR: DB_SERVER_POSTGRES_USER не задан в .env"
    exit 1
fi

# --- Создание директории ---
mkdir -p "$BACKUP_DIR"

# --- Бэкап ---
log "Начало бэкапа БД karaoke → $DUMP_FILE"

# pg_dump через docker exec, вывод в stdout, gzip на хосте.
# --clean --create: включает DROP + CREATE для полного restore.
# --if-exists: DROP IF EXISTS (без ошибок на несуществующих объектах).
if ! docker exec karaoke-db pg_dump \
    -h localhost \
    -U "$DB_SERVER_POSTGRES_USER" \
    --dbname=karaoke \
    --clean \
    --create \
    --if-exists \
    --no-password 2>"$LOG_FILE.err" | gzip > "$DUMP_FILE"; then
    log "ERROR: pg_dump завершился с ошибкой:"
    cat "$LOG_FILE.err" >> "$LOG_FILE"
    rm -f "$DUMP_FILE" "$LOG_FILE.err"
    exit 1
fi

rm -f "$LOG_FILE.err"

# Проверка, что файл не пустой
if [ ! -s "$DUMP_FILE" ]; then
    log "ERROR: бэкап пустой — возможно БД недоступна"
    rm -f "$DUMP_FILE"
    exit 1
fi

DUMP_SIZE=$(du -h "$DUMP_FILE" | cut -f1)
log "Бэкап создан: $DUMP_FILE ($DUMP_SIZE)"

# --- Retention: удаляем бэкапы старше 7 дней ---
log "Очистка бэкапов старше $RETENTION_DAYS дней..."
DELETED=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}_*.sql.gz" -type f -mtime +$RETENTION_DAYS -print -delete | wc -l)
log "Удалено старых бэкапов: $DELETED"

# --- Сводка ---
TOTAL=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}_*.sql.gz" -type f | wc -l)
DISK_USAGE=$(du -sh "$BACKUP_DIR" | cut -f1)
log "Готово. Бэкапов в хранилище: $TOTAL, занято: $DISK_USAGE"