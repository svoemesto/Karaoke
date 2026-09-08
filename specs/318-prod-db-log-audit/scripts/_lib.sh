#!/usr/bin/env bash
# scripts/_lib.sh — общие функции для анализа логов karaoke-db
# (см. spec.md FR-001, research.md R-3, R-4)
#
# Note: НЕ используем `set -o pipefail` — sort | head порождает SIGPIPE
# на больших файлах (sort продолжает писать после закрытия pipe), что
# трактуется pipefail как ошибка.
#
# License: MIT (как и весь проект Karaoke).
# Файл one-shot — НЕ публичное API karaoke-app; FR-006 (KDoc/JSDoc) не применяется.

set -eu

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FEATURE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$FEATURE_DIR/data"
PROD_HOST="${PROD_HOST:-root@188.119.64.111}"
CONTAINER_NAME="${CONTAINER_NAME:-karaoke-db}"

# Log helpers (timestamp in UTC, чтобы md5 был детерминирован)
_ts() { date -u +%Y-%m-%dT%H:%M:%SZ; }
log()  { printf '[%s] %s\n' "$(_ts)" "$*" >&2; }
die()  { log "ERROR: $*"; exit 1; }

# Verify SSH access works (BatchMode=yes, passwordless)
require_ssh() {
  if ! ssh -o ConnectTimeout=10 -o BatchMode=yes "$PROD_HOST" true 2>/dev/null; then
    die "SSH access to $PROD_HOST failed (BatchMode). Check ~/.ssh/id_rsa."
  fi
  log "SSH to $PROD_HOST OK"
}

# Verify container is running
require_container() {
  local status
  status=$(ssh -o BatchMode=yes "$PROD_HOST" "docker inspect $CONTAINER_NAME --format '{{.State.Status}}'" 2>/dev/null)
  [[ "$status" == "running" ]] || die "Container $CONTAINER_NAME not running (status=$status)"
  log "Container $CONTAINER_NAME is running"
}

# Get log driver for the container (json-file / journald / syslog / none)
get_log_driver() {
  ssh -o BatchMode=yes "$PROD_HOST" "docker inspect $CONTAINER_NAME --format '{{.HostConfig.LogConfig.Type}}'" 2>/dev/null
}

# Sanitize a string: replace known secret patterns with [REDACTED:<rule>]
# See research.md R-3.
sanitize_inline() {
  local line="$1"
  # password= or password: (not in json-like context)
  line=$(echo "$line" | sed -E 's/(password[[:space:]]*[:=][[:space:]]*)([^[:space:]]+)/\1[REDACTED:password]/gI')
  # postgres://user:pass@host
  line=$(echo "$line" | sed -E 's#(postgres://[^:]+:)[^@]+(@)#\1[REDACTED:pgpass]\2#g')
  # Bearer tokens
  line=$(echo "$line" | sed -E 's/(Bearer[[:space:]]+)[A-Za-z0-9._-]+/\1[REDACTED:bearer]/g')
  # pat_*
  line=$(echo "$line" | sed -E 's/(pat_)[A-Za-z0-9_-]+/\1[REDACTED:pat]/g')
  # Token=...
  line=$(echo "$line" | sed -E 's/(token[[:space:]]*[:=][[:space:]]*)[A-Za-z0-9._-]+/\1[REDACTED:token]/gI')
  printf '%s\n' "$line"
}

# Apply rules from data/categories.json to a single line, return category name.
# Iterates rules sorted by priority. First match wins; if none — returns default.
categorize_line() {
  local line="$1"
  local rules_file="$DATA_DIR/categories.json"
  [[ -f "$rules_file" ]] || die "categorize_line: $rules_file not found"

  local default_cat
  default_cat=$(jq -r '.default_category // "__unclassified__"' "$rules_file")

  # Read all (priority, category, pattern) into an array, sorted by priority.
  local -a rows
  mapfile -t rows < <(jq -r '.rules | sort_by(.priority) | .[] | "\(.priority)\t\(.category)\t\(.pattern)"' "$rules_file")

  for row in "${rows[@]}"; do
    local prio cat pattern
    IFS=$'\t' read -r prio cat pattern <<<"$row"
    if [[ -n "$pattern" ]] && echo "$line" | grep -Eq -- "$pattern"; then
      printf '%s\n' "$cat"
      return 0
    fi
  done
  printf '%s\n' "$default_cat"
}

# Extract ISO-8601 timestamp + level from a Postgres log line.
# Format: "2026-09-08 10:02:26.123 UTC [12345] LOG:  ..." or with --timestamps prefix.
# Returns "TIMESTAMP\tLEVEL\trest_of_line" via stdout.
parse_pg_line() {
  local line="$1"
  # Strip leading timestamp if --timestamps was used (YYYY-MM-DDTHH:MM:SS.sssssssssZ ...)
  local ts rest
  if [[ "$line" =~ ^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z)\ (.*)$ ]]; then
    ts="${BASH_REMATCH[1]}"
    rest="${BASH_REMATCH[3]}"
  elif [[ "$line" =~ ^([0-9]{4}-[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?(\ ?[A-Z]+)?)\ (.*)$ ]]; then
    # Postgres-style "2026-09-08 10:02:26.123 UTC"
    local d="${BASH_REMATCH[1]// /T}"
    d="${d/UTC/Z}"
    ts="$d"
    rest="${BASH_REMATCH[4]}"
  else
    # Unparseable timestamp
    printf 'unparsed\tUNKNOWN\t%s\n' "$line"
    return 0
  fi

  # Level: [NNN] LEVEL:  or level=LEVEL
  local level="LOG"
  if [[ "$rest" =~ \[([0-9]+-[0-9]+)\ (ERROR|FATAL|PANIC|WARNING|LOG|STATEMENT|INFO|DEBUG)(:?) ]]; then
    level="${BASH_REMATCH[2]}"
  elif [[ "$rest" =~ level=(error|fatal|panic|warning|log|statement|info|debug) ]]; then
    level=$(echo "${BASH_REMATCH[1]}" | tr '[:lower:]' '[:upper:]')
  fi

  printf '%s\t%s\t%s\n' "$ts" "$level" "$rest"
}

# Exit successfully only if we're inside the feature dir.
require_feature_dir() {
  [[ "$(basename "$FEATURE_DIR")" == "318-prod-db-log-audit" ]] || \
    die "_lib.sh must be sourced from scripts/ inside specs/318-prod-db-log-audit"
}
require_feature_dir