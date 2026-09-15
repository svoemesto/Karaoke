#!/usr/bin/env bash
# =====================================================================
# tools/check-no-jpa-imports.sh — guard для правила NON-NEGOTIABLE
# о запрете JPA/Hibernate в исходниках (см. .specify/memory/constitution.md,
# Principle II «Сырой JDBC + дифф по хэшам»).
#
# Прецедент (Pass 379, OP #105, wayfinder #101): правило «никакого JPA»
# было в constitution.md, но не было machine-readable guard'а — агент
# мог случайно добавить `import org.springframework.data.jpa...` или
# `import javax.persistence.Entity` в новый код.
#
# Этот guard сканирует .kt/.java файлы в karaoke-app/src и karaoke-web/src
# на наличие запрещённых JPA/Hibernate импортов.
#
# Использование:
#   bash tools/check-no-jpa-imports.sh              # guard-режим: exit 1 если найдены JPA-импорты
#   bash tools/check-no-jpa-imports.sh --quiet      # для pre-commit: только exit code
#   bash tools/check-no-jpa-imports.sh --help       # help
#
# Что проверяет:
#   В karaoke-app/src/**/*.kt и karaoke-app/src/**/*.java,
#   а также в karaoke-web/src/**/*.kt и karaoke-web/src/**/*.java,
#   ищет grep-паттерны:
#     - org.springframework.data.jpa
#     - javax.persistence
#     - hibernate
#   Исключения (НЕ проверяются):
#     - */test/* — тестовые директории (там могут быть моки для JPA)
#     - */build/* — сборочные артефакты (Gradle)
#     - */node_modules/* — npm-зависимости
#
# Exit codes:
#   0 — JPA/Hibernate импортов не найдено
#   1 — найдены запрещённые импорты
#   2 — usage error
# =====================================================================

set -uo pipefail

# Корни для проверки (только main src — не test, не build).
SEARCH_ROOTS=(
  "karaoke-app/src/main"
  "karaoke-web/src/main"
)

# Запрещённые паттерны (regex для grep -E).
FORBIDDEN_PATTERNS=(
  'org\.springframework\.data\.jpa'
  'javax\.persistence'
  'hibernate'
)

# Исключения — пути, которые НЕ проверяются (тесты + build-артефакты).
EXCLUDE_PATTERNS=(
  ':!*/test/*'
  ':!*/build/*'
  ':!*/node_modules/*'
)

color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

check_no_jpa_imports() {
  local violation_count=0
  local violation_lines=()

  # Проверяем, что хотя бы один корень существует.
  local any_root_exists=0
  for root in "${SEARCH_ROOTS[@]}"; do
    if [[ -d "$root" ]]; then
      any_root_exists=1
      break
    fi
  done
  if [[ $any_root_exists -eq 0 ]]; then
    color_yellow "⚠️  Ни один из SEARCH_ROOTS не существует: ${SEARCH_ROOTS[*]}"
    color_yellow "   (возможно, вы запускаете не из корня Karaoke)"
    return 0
  fi

  # Собираем grep-команду для каждого паттерна.
  for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
    # shellcheck disable=SC2086
    local matches
    matches="$(grep -rEn --include='*.kt' --include='*.java' \
      --exclude-dir=test --exclude-dir=build --exclude-dir=node_modules \
      "${pattern}" \
      "${SEARCH_ROOTS[@]}" 2>/dev/null || true)"

    if [[ -n "$matches" ]]; then
      # Человеко-читаемая форма (без backslashes).
      local pattern_display="${pattern//\\/}"
      color_red "❌ НАРУШЕНИЕ: паттерн «$pattern_display» найден в исходниках:"
      while IFS= read -r line; do
        color_red "   $line"
      done <<< "$matches"
      violation_count=$((violation_count + 1))
      while IFS= read -r line; do
        violation_lines+=("$line")
      done <<< "$matches"
    fi
  done

  if [[ $violation_count -eq 0 ]]; then
    color_green "✅ JPA/Hibernate импортов не найдено в ${SEARCH_ROOTS[*]}"
    color_green "   (Constitution Principle II соблюдена)"
    return 0
  else
    echo ""
    color_red "Найдено нарушений: $violation_count"
    color_yellow "См. .specify/memory/constitution.md, Principle II:"
    color_yellow "  «Доступ к БД — только через сырой JDBC. Никакого JPA/Hibernate/Exposed.»"
    color_yellow ""
    color_yellow "Если импорты в тестах/test-fixtures — это не violation,"
    color_yellow "переместите их в */test/* (исключено автоматически)."
    return 1
  fi
}

case "${1:-}" in
  --help|-h)
    cat <<EOF
tools/check-no-jpa-imports.sh — guard для Constitution Principle II
(запрет JPA/Hibernate в исходниках Karaoke).

Использование:
  bash tools/check-no-jpa-imports.sh              # guard-режим
  bash tools/check-no-jpa-imports.sh --quiet      # для pre-commit
  bash tools/check-no-jpa-imports.sh --help       # эта справка

Сканирует:
  karaoke-app/src/main/**/*.kt
  karaoke-app/src/main/**/*.java
  karaoke-web/src/main/**/*.kt
  karaoke-web/src/main/**/*.java

Ищет паттерны:
  - org.springframework.data.jpa
  - javax.persistence
  - hibernate

Исключения:
  - */test/*      (тестовые директории)
  - */build/*     (Gradle артефакты)
  - */node_modules/* (npm)

Exit codes:
  0 — JPA/Hibernate импортов не найдено
  1 — найдены запрещённые импорты
  2 — usage error

См. .specify/memory/constitution.md, Principle II.
EOF
    exit 0
    ;;
  "")
    check_no_jpa_imports
    exit $?
    ;;
  --quiet)
    check_no_jpa_imports >/dev/null 2>&1
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-no-jpa-imports.sh [--quiet|--help]"
    exit 2
    ;;
esac
