#!/usr/bin/env bash
# tools/verify-kotlin-refs.sh
#
# Проверяет, что ссылки вида Class#method (или Class.kt:NNN) в per-feature
# документах указывают на реально существующие символы в Kotlin-коде.
#
# Использование:
#   ./tools/verify-kotlin-refs.sh [docs/features/*.md]
#
# Использует grep по исходникам (Kotlin), не требует отдельного индексатора.
# Ограничения: проверяет наличие имени класса/файла; для точного
# определения method#нужно ввести Kotlin-парсер (P3 — TODO).

set -uo pipefail

TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then
  TARGETS=(docs/features/*.md)
fi

if ! command -v rg >/dev/null 2>&1; then
  echo "INFO: ripgrep (rg) не установлен — пропускаю проверку Kotlin-ссылок."
  echo "      Установка: apt install ripgrep / brew install ripgrep"
  exit 0
fi

errors=0

# Сканируем каждый per-feature документ, ищем ссылки Class#method
for doc in "${TARGETS[@]}"; do
  if [ ! -f "$doc" ]; then
    continue
  fi

  # Извлекаем все ClassName#methodName из секции '## Ссылки'
  refs=$(awk '/^## Ссылки/{flag=1; next} /^## /{flag=0} flag' "$doc" \
    | grep -oE '[A-Z][A-Za-z0-9]+#[a-zA-Z_][A-Za-z0-9_]*' \
    | sort -u || true)

  if [ -z "$refs" ]; then
    continue
  fi

  while IFS= read -r ref; do
    [ -z "$ref" ] && continue
    class="${ref%#*}"
    method="${ref#*#}"

    # Ищем class declaration (data class / class / object / interface)
    if ! rg -q "^\s*(data\s+)?(class|object|interface|sealed\s+class|enum\s+class)\s+$class\b" \
        karaoke-app/src/main/kotlin karaoke-web/src/main/kotlin 2>/dev/null; then
      echo "WARN [$doc]: класс '$class' (из ссылки '$ref') не найден в karaoke-app/karaoke-web"
      errors=$((errors + 1))
      continue
    fi

    # Ищем method declaration (fun methodName(...))
    # Может быть false-positive для имён, совпадающих с другими классами.
    if ! rg -q "fun\s+$method\s*\(" \
        karaoke-app/src/main/kotlin karaoke-web/src/main/kotlin 2>/dev/null; then
      echo "WARN [$doc]: метод '$method' (из ссылки '$ref') не найден как fun $method(...)"
      errors=$((errors + 1))
    fi
  done <<< "$refs"
done

if [ "$errors" -gt 0 ]; then
  echo ""
  echo "==> Найдено $errors потенциально битых Kotlin-ссылок (WARN, не блокер)"
  echo "    Проверьте вручную при code review."
  exit 0  # WARN, не блокер
fi

echo "OK: Kotlin-ссылки валидны (или не найдены для проверки)"
exit 0
