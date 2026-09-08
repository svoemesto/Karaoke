#!/usr/bin/env bash
# scripts/04-sanitize-for-commit.sh
#
# Финальная санитизация data/logs.jsonl (и других артефактов) перед коммитом.
# Применяет паттерны из data/sanitize-patterns.txt, удаляя любые возможные
# остатки секретов. Также проверяет, что raw.log санитизирован.
#
# Запускать перед git add. Проверяет, что git ls-files не содержит .env/.key/.pem.
# См. spec.md FR-007, Constitution Principle VIII.
#
# License: MIT.

set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/_lib.sh"

DATA_DIR="$FEATURE_DIR/data"
RAW="$DATA_DIR/raw.log"
JSONL="$DATA_DIR/logs.jsonl"
PATTERNS="$DATA_DIR/sanitize-patterns.txt"
REPORT="$FEATURE_DIR/report.md"

[[ -f "$JSONL" ]] || die "Missing $JSONL — run 02 first"

log "Sanitizing $JSONL"

# Apply each pattern
TMP="$DATA_DIR/.sanitized.jsonl"
> "$TMP"
n_replaced=0
while IFS='|' read -r name pattern; do
  [[ -z "$name" || -z "$pattern" ]] && continue
  count=$(grep -cE "$pattern" "$JSONL" || true)
  if [[ "$count" -gt 0 ]]; then
    log "  Pattern '$name': $count matches in $JSONL — sanitizing"
    # Apply sed replacement
    sed -E "s/$pattern/[REDACTED:${name}]/gI" "$JSONL" > "$TMP"
    mv "$TMP" "$JSONL"
    > "$TMP"
    n_replaced=$((n_replaced + count))
  fi
done < "$PATTERNS"

log "Total replacements: $n_replaced"

# Verify: grep -E should return nothing
echo "=== Sanity-check after sanitization ==="
for pattern in 'password=' 'Bearer ' 'postgres(ql)?://[^:]+:[^@]+@' 'pat_[A-Za-z0-9_-]+'; do
  count=$(grep -cE "$pattern" "$JSONL" || true)
  if [[ "$count" -gt 0 ]]; then
    die "Sanitization incomplete: '$pattern' found $count times in $JSONL"
  fi
done
log "✓ $JSONL is sanitized"

# Sanity-check report.md too
for pattern in 'password=' 'Bearer ' 'postgres(ql)?://[^:]+:[^@]+@' 'pat_[A-Za-z0-9_-]+'; do
  count=$(grep -cE "$pattern" "$REPORT" || true)
  if [[ "$count" -gt 0 ]]; then
    log "⚠️ '$pattern' found $count times in $REPORT (review manually)"
  fi
done

# Check git status
echo "=== Pre-commit secret check (Constitution VIII.3) ==="
SECRETS_IN_GIT=$(git ls-files | grep -iE '\.env$|\.key$|\.pem$|do\.env$' || true)
if [[ -n "$SECRETS_IN_GIT" ]]; then
  die "Constitution VIII violation: secret files tracked in git: $SECRETS_IN_GIT"
fi
log "✓ No secret files tracked in git"

# Check raw.log not staged
RAW_STAGED=$(git ls-files --error-unmatch "$RAW" 2>/dev/null || true)
if [[ -n "$RAW_STAGED" ]]; then
  die "$RAW is tracked in git (should be gitignored!)"
fi
log "✓ $RAW is gitignored (not tracked)"

log "Done. Safe to git add + git commit."