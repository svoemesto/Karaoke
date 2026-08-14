#!/usr/bin/env bash
# tools/search-livedocs.sh
# Поиск по LiveDocs (grep wrapper).
# Принимает query (multiple words или regex), выводит совпадения с file:line:context.
#
# Используется AI-агентами (opencode/Claude Code/Cursor) для быстрого поиска по
# каталогу LiveDocs без ручного обхода.
#
# Использование:
#   bash tools/search-livedocs.sh "query" [--type topic|feature|domain|adr|all]
#                                   [--path SUBPATH]
#
# Examples:
#   bash tools/search-livedocs.sh "render Mp4"
#   bash tools/search-livedocs.sh "nginx" --path livedocs/architecture/
#   bash tools/search-livedocs.sh "KaraokeConnection" --type topic
#
# Exit code: 0 если найдены совпадения, 1 если нет, 2 если ошибка.

set -uo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Парсинг аргументов
QUERY=""
TYPE="all"
SUBPATH=""

while [ $# -gt 0 ]; do
  case "$1" in
    --type)
      TYPE="$2"
      shift 2
      ;;
    --path)
      SUBPATH="$2"
      shift 2
      ;;
    --help|-h)
      echo "Usage: bash $0 'query' [--type type] [--path subpath]"
      echo ""
      echo "Types: feature, domain, architecture, adr, runbook, template, all"
      echo ""
      echo "Examples:"
      echo "  bash $0 'nginx proxy_buffering'"
      echo "  bash $0 'KaraokeConnection' --path livedocs/architecture/"
      exit 0
      ;;
    *)
      QUERY="$1"
      shift
      ;;
  esac
done

if [ -z "$QUERY" ]; then
  echo "Usage: bash $0 'query' [--type type] [--path subpath]" >&2
  echo "Run with --help for details." >&2
  exit 2
fi

# Построить путь поиска на основе --type
case "$TYPE" in
  feature)        SEARCH_PATH="livedocs/features" ;;
  domain)         SEARCH_PATH="livedocs/domain" ;;
  architecture)   SEARCH_PATH="livedocs/architecture" ;;
  adr)            SEARCH_PATH="livedocs/architecture/decisions" ;;
  runbook)        SEARCH_PATH="livedocs/runbooks" ;;
  template)       SEARCH_PATH="livedocs/templates" ;;
  all)            SEARCH_PATH="livedocs" ;;
  *)
    echo "Unknown type: $TYPE (allowed: feature, domain, architecture, adr, runbook, template, all)" >&2
    exit 2
    ;;
esac

# Если --path задан — он RELATIVE к REPO_ROOT (а не к SEARCH_PATH).
if [ -n "$SUBPATH" ]; then
  SEARCH_PATH="${SUBPATH%/}"
fi

if [ ! -d "$SEARCH_PATH" ]; then
  echo "Path not found: $SEARCH_PATH" >&2
  exit 2
fi

# Поиск через grep (POSIX). `grep -n` = номера строк, `-i` = case-insensitive,
# `grep -r` = recursive.
RESULT=$(grep -rn \
  --include="*.md" \
  -i \
  "$QUERY" \
  "$SEARCH_PATH" 2>/dev/null)

FOUND=$?
if [ $FOUND -ne 0 ]; then
  echo "No matches for: $QUERY" >&2
  echo "Searched in: $SEARCH_PATH" >&2
  exit 1
fi

# Подсчёт совпадений
COUNT=$(echo "$RESULT" | wc -l)

# Печать результата
echo "Found $COUNT match(es) for: $QUERY"
echo "Searched in: $SEARCH_PATH"
echo "---"
echo "$RESULT" | awk -F: '{
  file = $1
  line = $2
  # Выводить первые 120 символов матча
  text = $3
  print file ":" line ": " text
}'

exit 0