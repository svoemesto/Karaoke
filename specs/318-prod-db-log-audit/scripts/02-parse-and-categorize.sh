#!/usr/bin/env bash
# scripts/02-parse-and-categorize.sh
#
# Парсинг + категоризация data/raw.log → data/logs.jsonl + incidents.jsonl +
# metrics.json. Использует Python для горячего пути (parse + categorize).
# См. spec.md FR-002 + research.md R-4.
#
# Note: НЕ используем `set -o pipefail` — sort | head -1 порождает SIGPIPE
# на больших файлах (sort продолжает писать после закрытия pipe), а pipefail
# трактует это как ошибку. Без pipefail sort-завершение игнорируется.
#
# License: MIT.

set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/_lib.sh"

INPUT="${1:-$DATA_DIR/raw.log}"
[[ -f "$INPUT" ]] || die "Input file not found: $INPUT"

OUT_JSONL="$DATA_DIR/logs.jsonl"
OUT_INCIDENTS="$DATA_DIR/incidents.jsonl"
OUT_METRICS="$DATA_DIR/metrics.json"
OUT_WARNINGS="$DATA_DIR/parse-warnings.log"
OUT_UNCLASSIFIED="$DATA_DIR/unclassified-samples.txt"

> "$OUT_JSONL"
> "$OUT_INCIDENTS"
> "$OUT_WARNINGS"
> "$OUT_UNCLASSIFIED"

LINE_COUNT=$(wc -l < "$INPUT")
log "Parsing $INPUT ($LINE_COUNT lines)"

PARSER="$HERE/_parse_logs.py"
[[ -f "$PARSER" ]] || die "Parser script not found: $PARSER"

python3 "$PARSER" \
  --input "$INPUT" \
  --categories "$DATA_DIR/categories.json" \
  --out-jsonl "$OUT_JSONL" \
  --out-warnings "$OUT_WARNINGS" \
  --out-unclassified "$OUT_UNCLASSIFIED"

JSONL_COUNT=$(wc -l < "$OUT_JSONL")
WARN_COUNT=$(wc -l < "$OUT_WARNINGS")
log "Wrote $OUT_JSONL ($JSONL_COUNT records), parse warnings: $WARN_COUNT"

log "Computing metrics..."

TOTAL_BYTES=$(wc -c < "$OUT_JSONL")
TOTAL_LINES=$JSONL_COUNT
TOTAL_ERRORS=$(jq -c 'select(.level|IN("ERROR","FATAL","PANIC"))' "$OUT_JSONL" | wc -l)
ERROR_PCT=$(LC_ALL=C awk -v e="$TOTAL_ERRORS" -v t="$TOTAL_LINES" 'BEGIN{printf "%.4f", (t>0 ? e/t*100 : 0)}')

BY_CATEGORY=$(jq -s -c "
  group_by(.message_category) | map({
    category: .[0].message_category,
    lines: length,
    unique_messages: (map(.raw_message) | unique | length),
    share_pct: ((length / $TOTAL_LINES) * 100 | . * 10000 | round / 10000)
  }) | sort_by(-.lines)
" "$OUT_JSONL")

# Top-10 with examples — single jq call (avoids per-category overhead)
TOP10_CATS=$(echo "$BY_CATEGORY" | jq -c '.[0:10]')
# Build a samples map: {category: first_raw_message_short}
SAMPLES=$(jq -s -c '
  group_by(.message_category) |
  map({key: .[0].message_category, value: (.[0].raw_message[0:120] // "")}) |
  from_entries
' "$OUT_JSONL")
# Merge
TOP10=$(jq -c --argjson s "$SAMPLES" '
  map(. + {example: ($s[.category] // "")})
' <<<"$TOP10_CATS")

PER_DAY=$(jq -s -c '
  map(select(.timestamp != null)) |
  group_by(.timestamp[0:10]) |
  map({
    date: .[0].timestamp[0:10],
    bytes: (map(.raw_message | length) | add),
    lines: length
  }) | sort_by(.date)
' "$OUT_JSONL")

BYTES_DAY_MEDIAN=$(echo "$PER_DAY" | jq '[.[].bytes] | sort | if length==0 then 0 else .[length/2|floor] end')
BYTES_DAY_MAX=$(echo "$PER_DAY" | jq '[.[].bytes] | max // 0')

WINDOW_START=$(jq -r '.timestamp' "$OUT_JSONL" | grep -v '^null$' | sort | head -1)
WINDOW_END=$(jq -r '.timestamp' "$OUT_JSONL" | grep -v '^null$' | sort | tail -1)
TRUNC=$(jq -r '.truncation_warning' "$DATA_DIR/raw.metadata.json")

jq -n \
  --arg ws "$(awk -v ts="$WINDOW_START" 'BEGIN{print ts}' | sed 's/T.*//')T00:00:00Z" \
  --arg we "$WINDOW_END" \
  --arg aws "$WINDOW_START" \
  --arg awe "$WINDOW_END" \
  --arg drv "json-file" \
  --arg trunc "$TRUNC" \
  --argjson total_lines "$TOTAL_LINES" \
  --argjson total_bytes "$TOTAL_BYTES" \
  --argjson bpd_med "$BYTES_DAY_MEDIAN" \
  --argjson bpd_max "$BYTES_DAY_MAX" \
  --argjson err_count "$TOTAL_ERRORS" \
  --argjson err_pct "$ERROR_PCT" \
  --argjson warn "$WARN_COUNT" \
  --argjson bycat "$BY_CATEGORY" \
  --argjson top10 "$TOP10" \
  '{
    window_start: $ws,
    window_end: $we,
    actual_window_start: $aws,
    actual_window_end: $awe,
    source_log_driver: $drv,
    truncation_warning: $trunc,
    totals: {
      total_lines: $total_lines,
      total_bytes: $total_bytes,
      bytes_per_day_median: $bpd_med,
      bytes_per_day_max: $bpd_max,
      error_count: $err_count,
      error_share_pct: $err_pct,
      unparseable_count: $warn
    },
    by_category: $bycat,
    by_category_top10: $top10
  }' > "$OUT_METRICS"

# Build incidents.jsonl
jq -c 'select(.level|IN("ERROR","FATAL","PANIC"))' "$OUT_JSONL" | \
  jq -s -c '
    group_by(.raw_message) |
    map({
      level: .[0].level,
      raw_message: .[0].raw_message,
      count: length,
      first_timestamp: (map(.timestamp) | min),
      last_timestamp: (map(.timestamp) | max),
      source_line_numbers: (map(.source_line_number) | sort | .[0:5]),
      category: .[0].message_category
    }) | sort_by(-.count)
  ' | jq -c '.[]' > "$OUT_INCIDENTS"

INC_COUNT=$(wc -l < "$OUT_INCIDENTS")
log "Wrote $OUT_INCIDENTS ($INC_COUNT unique incidents)"

UNCL_COUNT=$(wc -l < "$OUT_UNCLASSIFIED")
log "Unclassified samples: $UNCL_COUNT"
log "Errors: $TOTAL_ERRORS / $TOTAL_LINES ($ERROR_PCT %)"
log "Done. Next: scripts/03-generate-report.sh"