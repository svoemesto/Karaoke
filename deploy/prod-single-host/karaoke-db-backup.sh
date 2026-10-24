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

# BR-539: без пароля pg_dump не проходит аутентификацию (fe_sendauth: no
# password supplied) — падаем сразу, до создания пустого файла дампа.
if [ -z "${DB_SERVER_POSTGRES_PASSWORD:-}" ]; then
    log "ERROR: DB_SERVER_POSTGRES_PASSWORD не задан в .env — бэкап БД невозможен"
    exit 1
fi

# --- Создание директории ---
mkdir -p "$BACKUP_DIR"

# --- Бэкап ---
log "Начало бэкапа БД karaoke → $DUMP_FILE"

# pg_dump через docker exec, вывод в stdout, gzip на хосте.
# --clean --create: включает DROP + CREATE для полного restore.
# --if-exists: DROP IF EXISTS (без ошибок на несуществующих объектах).
#
# BR-539 (аудит #336 / P-73-01, OpenProject #371): пароль БД передаётся в pg_dump.
# Раньше он не передавался ВОВСЕ, а `--no-password` запрещал интерактивный
# запрос: pg_hba.conf, который initdb генерирует в образе postgres:16, требует
# scram-sha-256 для host-соединений → pg_dump завершался с
# "fe_sendauth: no password supplied", скрипт уходил в ветку ERROR и удалял
# дамп. Итог: на прод-хосте (таймер 05:00) не было НИ ОДНОГО резервного дампа
# БД — при потере PGDATA восстановление было невозможно.
#
# Секрет не попадает в argv (тот же приём, что в deploy/tracker-db-backup.sh,
# BR-552): пароль уходит в контейнер по stdin, кладётся в одноразовый .pgpass
# (права 0600) и удаляется сразу после дампа. В `ps` видны только имя
# контейнера, порт, пользователь и имя БД.
PGPASS_PATH="/tmp/.pgpass.$$"

# Экранирование спецсимволов .pgpass: разделитель полей — ':', escape — '\'.
pgpass_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//:/\\:}"
    printf '%s' "$s"
}

PGPASS_LINE="localhost:5432:karaoke:${DB_SERVER_POSTGRES_USER}:$(pgpass_escape "${DB_SERVER_POSTGRES_PASSWORD}")"

if ! docker exec -i \
    karaoke-db \
    sh -c 'pgpass="$1"
umask 077
cat > "$pgpass" || exit 1
PGPASSFILE="$pgpass" pg_dump \
    -h localhost \
    -p "$2" \
    -U "$3" \
    --dbname="$4" \
    --clean \
    --create \
    --if-exists \
    --no-password
rc=$?
rm -f "$pgpass"
exit $rc' sh "$PGPASS_PATH" "5432" "$DB_SERVER_POSTGRES_USER" "karaoke" \
    <<<"$PGPASS_LINE" 2>"$DUMP_FILE.err" | gzip > "$DUMP_FILE.tmp"; then
    log "ERROR: pg_dump завершился с ошибкой:"
    cat "$DUMP_FILE.err" >> "$LOG_FILE"
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
log "Бэкап создан: $DUMP_FILE ($DUMP_SIZE)"

# --- Retention: удаляем бэкапы старше 7 дней ---
log "Очистка бэкапов старше $RETENTION_DAYS дней..."
DELETED=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}_*.sql.gz" -type f -mtime +$RETENTION_DAYS -print -delete | wc -l)
log "Удалено старых бэкапов: $DELETED"
# BR-539: незавершённые выгрузки (.tmp) от прерванного прогона — старше суток удаляем,
# иначе они копятся рядом с валидными дампами и путают retention-сводку.
find "$BACKUP_DIR" -name "${BACKUP_NAME}_*.sql.gz.tmp" -type f -mtime +1 -delete 2>/dev/null || true

# --- Сводка ---
TOTAL=$(find "$BACKUP_DIR" -name "${BACKUP_NAME}_*.sql.gz" -type f | wc -l)
DISK_USAGE=$(du -sh "$BACKUP_DIR" | cut -f1)
log "Готово. Бэкапов в хранилище: $TOTAL, занято: $DISK_USAGE"