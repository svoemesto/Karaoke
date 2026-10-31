#!/usr/bin/env bash
# extract-commands.sh — выгружает реальные команды bash из журналов сессий DSH
# в JSONL (по строке на вызов) для офлайн-проверки матчера agent-command-guard.
#
# Использование:
#   bash tools/dsh-agent-command-guard/extract-commands.sh /tmp/commands.jsonl 200
#
# Аргументы:
#   $1 — путь вывода (по умолчанию /tmp/commands.jsonl)
#   $2 — сколько последних файлов сессий разбирать (по умолчанию 200)
#
# Только чтение: журналы сессий не изменяются.
set -uo pipefail

OUT="${1:-/tmp/commands.jsonl}"
LIMIT="${2:-200}"
SESSIONS_DIR="${DSH_SESSIONS_DIR:-$HOME/.dsh/sessions}"

if [ ! -d "$SESSIONS_DIR" ]; then
    echo "ERROR: каталог сессий не найден: $SESSIONS_DIR" >&2
    exit 2
fi

: > "$OUT"
ls -t "$SESSIONS_DIR"/*/*/session.v*.jsonl.zstd 2>/dev/null | head -"$LIMIT" | \
    xargs -P 8 -I{} bash -c '
        zstd -dc "{}" 2>/dev/null \
          | jq -c "select(.type==\"tool/call\" and .data.name==\"bash\") | (.data.arguments | fromjson | .command) as \$c | {command: \$c}" 2>/dev/null
    ' >> "$OUT" 2>/dev/null

echo "извлечено команд: $(wc -l < "$OUT") (файлы: $(ls -t "$SESSIONS_DIR"/*/*/session.v*.jsonl.zstd 2>/dev/null | head -"$LIMIT" | wc -l))"
