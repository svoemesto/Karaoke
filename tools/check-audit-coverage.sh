#!/usr/bin/env bash
# tools/check-audit-coverage.sh
#
# Проверяет, что для КАЖДОГО контроллера из
# `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/*.kt`,
# в котором есть хотя бы один `@GetMapping`/`@PostMapping`,
# есть упоминание в `specs/187-site-traffic-anomaly-investigation/research.md`.
#
# Также проверяет, что для КАЖДОГО файла в `services/*.kt`,
# содержащего `@Scheduled`, есть упоминание в research.md.
#
# Гарантирует SC-009: 100% покрытие аудитом источников нагрузки.
#
# Использование:
#   ./tools/check-audit-coverage.sh
#
# Возвращает 0 если покрытие 100%, 1 если есть unaccounted файлов.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTROLLERS_DIR="${REPO_ROOT}/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers"
SERVICES_DIR="${REPO_ROOT}/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services"
RESEARCH_MD="${REPO_ROOT}/specs/187-site-traffic-anomaly-investigation/research.md"

if [ ! -f "$RESEARCH_MD" ]; then
  echo "ERROR: research.md не найден: $RESEARCH_MD"
  exit 1
fi

unaccounted_files=()
checked_count=0

# 1. Проверка контроллеров.
for controller_file in "$CONTROLLERS_DIR"/*.kt; do
  [ -f "$controller_file" ] || continue

  # Имя файла без пути и расширения (например, PublicApiController).
  filename=$(basename "$controller_file" .kt)

  # Исключаем WebSocketConfig.kt и подобные (нет @GetMapping/@PostMapping).
  mapping_count=$(grep -cE "@(Get|Post|Put|Delete|Request)Mapping" "$controller_file" 2>/dev/null | head -1)
  mapping_count=${mapping_count:-0}
  if [ -z "$mapping_count" ] || [ "$mapping_count" -eq 0 ]; then
    continue
  fi

  checked_count=$((checked_count + 1))

  # Проверяем, что этот controller упомянут в research.md (по имени файла без .kt).
  if ! grep -qF "$filename.kt" "$RESEARCH_MD"; then
    unaccounted_files+=("controller: $filename.kt ($mapping_count mappings)")
  fi
done

# 2. Проверка @Scheduled сервисов.
scheduled_count=0
for service_file in "$SERVICES_DIR"/*.kt; do
  [ -f "$service_file" ] || continue
  filename=$(basename "$service_file" .kt)

  scheduled_in_file=$(grep -cE "@Scheduled" "$service_file" 2>/dev/null | head -1)
  scheduled_in_file=${scheduled_in_file:-0}
  if [ -z "$scheduled_in_file" ] || [ "$scheduled_in_file" -eq 0 ]; then
    continue
  fi

  scheduled_count=$((scheduled_count + 1))
  checked_count=$((checked_count + 1))

  if ! grep -qF "$filename.kt" "$RESEARCH_MD"; then
    unaccounted_files+=("service: $filename.kt ($scheduled_in_file @Scheduled)")
  fi
done

# 3. Отчёт.
unaccounted_count=${#unaccounted_files[@]}

if [ "$unaccounted_count" -eq 0 ]; then
  echo "✓ PASS (SC-009): $checked_count файлов проверено, 0 unaccounted."
  echo "    Controllers with @GetMapping/@PostMapping: $((checked_count - scheduled_count))"
  echo "    Services with @Scheduled: $scheduled_count"
  exit 0
else
  echo "✗ FAIL (SC-009): $unaccounted_count файлов не упомянуты в research.md:"
  for entry in "${unaccounted_files[@]}"; do
    echo "    - $entry"
  done
  echo ""
  echo "Добавьте упоминание этих файлов в Таблицу A (controllers) или Таблицу B (@Scheduled)"
  echo "в research.md и перезапустите скрипт."
  exit 1
fi
