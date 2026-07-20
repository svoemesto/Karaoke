#!/usr/bin/env bash
# tools/check-feature-doc.sh
#
# Проверяет, что per-feature документ соответствует контракту
# contracts/per-feature-doc.md: 6 обязательных секций, slug == имя файла,
# status ∈ {active, deprecated, experimental}.
#
# Использование:
#   ./tools/check-feature-doc.sh <path-to-doc.md> [...]
#   ./tools/check-feature-doc.sh docs/features/*.md
#
# Возвращает 0 если все документы валидны, 1 если есть нарушения.

set -uo pipefail

REQUIRED_SECTIONS=(
  "## Что делает"
  "## Зачем"
  "## Как работает"
  "## Инварианты"
  "## Известные ловушки"
  "## Ссылки"
)

VALID_STATUSES=("active" "deprecated" "experimental")

errors_total=0

check_doc() {
  local file="$1"
  local errors=0

  if [ ! -f "$file" ]; then
    echo "ERROR: файл '$file' не существует"
    return 1
  fi

  # README.md (оглавление docs/features/) — не per-feature документ,
  # не проверяем.
  local filename
  filename=$(basename "$file" .md)
  if [ "$filename" = "README" ]; then
    return 0
  fi

  # 1. Имя файла == slug (kebab-case)
  local filename
  filename=$(basename "$file" .md)
  if ! [[ "$filename" =~ ^[a-z][a-z0-9-]*$ ]]; then
    echo "ERROR [$file]: slug '$filename' не в kebab-case (только [a-z0-9-])"
    errors=$((errors + 1))
  fi

  # 2. Все 6 секций присутствуют
  for section in "${REQUIRED_SECTIONS[@]}"; do
    if ! grep -qF "$section" "$file"; then
      echo "ERROR [$file]: отсутствует секция '$section'"
      errors=$((errors + 1))
    fi
  done

  # 3. Status в шапке
  local status
  status=$(grep -oP 'Status\*\*:\s*\K\S+' "$file" 2>/dev/null | head -1 || echo "")
  if [ -z "$status" ]; then
    echo "ERROR [$file]: в шапке отсутствует '> **Status**: ...'"
    errors=$((errors + 1))
  else
    local valid=0
    for s in "${VALID_STATUSES[@]}"; do
      if [ "$status" = "$s" ]; then
        valid=1
        break
      fi
    done
    if [ "$valid" -eq 0 ]; then
      echo "ERROR [$file]: Status '$status' не ∈ {active, deprecated, experimental}"
      errors=$((errors + 1))
    fi
  fi

  # 4. Feature Key в шапке
  if ! grep -qP 'Feature Key\*\*:' "$file"; then
    echo "ERROR [$file]: в шапке отсутствует '> **Feature Key**: ...'"
    errors=$((errors + 1))
  fi

  # 5. Все ссылки — Markdown-формат [text](url)
  # (грубая проверка: ищем '](<path>' в секции '## Ссылки')
  if grep -A 100 "^## Ссылки" "$file" | grep -E '\]\([^[:space:]]' >/dev/null 2>&1; then
    : # OK
  else
    # Проверим, что в секции '## Ссылки' вообще есть Markdown-ссылки
    local refs_section
    refs_section=$(awk '/^## Ссылки/{flag=1; next} /^## /{flag=0} flag' "$file")
    if ! echo "$refs_section" | grep -qE '\]\('; then
      echo "WARN [$file]: секция '## Ссылки' не содержит Markdown-ссылок (формат [text](url))"
    fi
  fi

  if [ "$errors" -eq 0 ]; then
    echo "OK [$file]"
  else
    errors_total=$((errors_total + errors))
  fi
}

if [ $# -eq 0 ]; then
  echo "Usage: $0 <path-to-doc.md> [...]"
  echo "  Пример: $0 docs/features/*.md"
  exit 2
fi

for f in "$@"; do
  check_doc "$f"
done

if [ "$errors_total" -gt 0 ]; then
  echo ""
  echo "==> ИТОГО: $errors_total ошибок"
  exit 1
fi

echo ""
echo "==> Все документы валидны"
exit 0
