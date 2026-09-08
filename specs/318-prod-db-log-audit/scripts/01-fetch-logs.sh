#!/usr/bin/env bash
# scripts/01-fetch-logs.sh
#
# Сбор docker logs контейнера karaoke-db за последние 168 часов
# через SSH на прод-сервер. Поддерживает log drivers:
#   - json-file: docker logs --since=168h --timestamps
#   - journald:  journalctl -u docker --since='7 days ago' | grep
#   - syslog:    (best effort) docker logs --since=168h
#   - none:      завершается с ошибкой
#
# Output:
#   data/raw.log         — скачанный лог (НЕ в git, см. data/.gitignore)
#   data/raw.metadata.json — метаданные (driver, размер, окно)
#
# License: MIT.

set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/_lib.sh"

OUT_LOG="$DATA_DIR/raw.log"
OUT_META="$DATA_DIR/raw.metadata.json"
HOURS="${HOURS:-168}"

require_ssh
require_container

DRIVER=$(get_log_driver)
log "Log driver: $DRIVER"
COLLECTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)

case "$DRIVER" in
  json-file|"")
    log "Fetching via 'docker logs --since=${HOURS}h --timestamps $CONTAINER_NAME'"
    ssh -o BatchMode=yes "$PROD_HOST" \
      "docker logs --since=${HOURS}h --timestamps $CONTAINER_NAME" \
      > "$OUT_LOG" 2>/dev/null || die "docker logs failed on $PROD_HOST"
    ;;
  journald)
    log "Fetching via 'journalctl -u docker --since=${HOURS}h ago' | grep $CONTAINER_NAME"
    ssh -o BatchMode=yes "$PROD_HOST" \
      "journalctl -u docker --since='${HOURS} hours ago' --no-pager 2>/dev/null | grep -F '$CONTAINER_NAME'" \
      > "$OUT_LOG" || die "journalctl failed on $PROD_HOST"
    ;;
  syslog)
    log "Fetching via 'docker logs --since=${HOURS}h --timestamps' (syslog driver)"
    ssh -o BatchMode=yes "$PROD_HOST" \
      "docker logs --since=${HOURS}h --timestamps $CONTAINER_NAME" \
      > "$OUT_LOG" 2>/dev/null || die "docker logs failed on $PROD_HOST"
    ;;
  none)
    die "Log driver is 'none' — логирование полностью отключено, анализ невозможен"
    ;;
  *)
    log "Unknown driver '$DRIVER' — fallback to docker logs"
    ssh -o BatchMode=yes "$PROD_HOST" \
      "docker logs --since=${HOURS}h --timestamps $CONTAINER_NAME" \
      > "$OUT_LOG" 2>/dev/null || die "docker logs failed on $PROD_HOST"
    ;;
esac

# Sanity-check
LINE_COUNT=$(wc -l < "$OUT_LOG")
BYTE_COUNT=$(wc -c < "$OUT_LOG")
log "Collected $LINE_COUNT lines, $BYTE_COUNT bytes"

# Determine actual window from data
FIRST_TS=$(head -1 "$OUT_LOG" | awk '{print $1}' || echo "")
LAST_TS=$(tail -1 "$OUT_LOG" | awk '{print $1}' || echo "")

# Get container PID/status fresh
INFO=$(ssh -o BatchMode=yes "$PROD_HOST" "docker inspect $CONTAINER_NAME --format '{{.State.Pid}} {{.State.Status}} {{.State.StartedAt}}'" 2>/dev/null)
read PID STATUS STARTED <<<"$INFO"

# Build metadata JSON
cat > "$OUT_META" <<EOF
{
  "collected_at_utc": "$COLLECTED_AT",
  "log_driver": "$DRIVER",
  "container_name": "$CONTAINER_NAME",
  "container_pid": $PID,
  "container_status": "$STATUS",
  "container_started_at": "$STARTED",
  "requested_hours": $HOURS,
  "line_count": $LINE_COUNT,
  "byte_count": $BYTE_COUNT,
  "first_timestamp_in_data": "$FIRST_TS",
  "last_timestamp_in_data": "$LAST_TS",
  "truncation_warning": "$(if [ "$DRIVER" = "json-file" ]; then echo "json-file ring buffer — доступны только последние ~30 МБ, не полные 168 ч"; else echo "none"; fi)"
}
EOF

jq . "$OUT_META" > /dev/null || die "Generated $OUT_META is invalid JSON"
log "Wrote metadata to $OUT_META"
log "Done. Next: scripts/02-parse-and-categorize.sh"