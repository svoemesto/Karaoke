#!/usr/bin/env bash
# install-dsh-integration.sh — устанавливает slash-команды spec-kit для DSH.
#
# Аналог `specify integration install opencode`, только целевой каталог —
# .dsh/commands/ вместо .opencode/commands/. Использует уже сгенерированные
# opencode-команды как источник (они лежат в .opencode/commands/*.md, в gitignore
# — потому что репозиторий Karaoke хранит только манифесты интеграций в
# .specify/integrations/, а файлы команд воссоздаются локально).
#
# Зачем: чтобы агенты, работающие через DeepSeek Harness (DSH), могли выполнять
# /speckit.specify, /speckit.plan, /speckit.tasks, /speckit.implement и т.д.
# так же, как это делает opencode в своей среде.
#
# Использование:
#   bash tools/install-dsh-integration.sh            # создать .dsh/commands/
#   bash tools/install-dsh-integration.sh --check    # только проверить, что установлено
#
# NON-NEGOTIABLE (AGENTS.md):
#   - Запускать ТОЛЬКО в Karaoke (cwd = корень проекта).
#   - НЕ меняет default_integration в .specify/integration.json — это opencode.
#   - НЕ правит AGENTS.md, constitution.md, конфиги линтеров.

set -euo pipefail

if [[ "${1:-}" == "--check" ]]; then
  mode="check"
else
  mode="install"
fi

# Проверки
if [[ ! -f .specify/integration.json ]]; then
  echo "ERROR: запустите из корня Karaoke (нет .specify/integration.json)" >&2
  exit 1
fi
if [[ ! -f .specify/integrations/dsh.manifest.json ]]; then
  echo "ERROR: .specify/integrations/dsh.manifest.json отсутствует." >&2
  echo "Запустите `git pull` — файл должен быть в master начиная с PR #266." >&2
  exit 1
fi
if [[ ! -d .opencode/commands ]]; then
  echo "ERROR: .opencode/commands/ отсутствует." >&2
  echo "Сначала установите opencode-интеграцию: specify integration install opencode" >&2
  exit 1
fi

commands=(
  speckit.specify.md
  speckit.plan.md
  speckit.tasks.md
  speckit.implement.md
  speckit.clarify.md
  speckit.constitution.md
  speckit.analyze.md
  speckit.checklist.md
  speckit.converge.md
  speckit.taskstoissues.md
)

if [[ "$mode" == "check" ]]; then
  missing=0
  for c in "${commands[@]}"; do
    if [[ ! -f ".dsh/commands/$c" ]]; then
      echo "MISSING: .dsh/commands/$c"
      missing=$((missing + 1))
    fi
  done
  if [[ $missing -eq 0 ]]; then
    echo "OK: все ${#commands[@]} команд на месте в .dsh/commands/"
    exit 0
  else
    echo "FAIL: отсутствует $missing из ${#commands[@]} команд"
    exit 1
  fi
fi

# Установка
mkdir -p .dsh/commands
copied=0
for c in "${commands[@]}"; do
  src=".opencode/commands/$c"
  dst=".dsh/commands/$c"
  if [[ ! -f "$src" ]]; then
    echo "WARN: источник $src не найден, пропускаю" >&2
    continue
  fi
  cp "$src" "$dst"
  copied=$((copied + 1))
done

echo "Установлено $copied из ${#commands[@]} команд в .dsh/commands/"
echo ""
echo "Проверить:"
echo "  bash tools/install-dsh-integration.sh --check"
echo ""
echo "Замечание: DSH пока не подхватывает .dsh/commands/ автоматически как"
echo "slash-команды. Используйте их в DSH-сессии вручную: попросите агента"
echo "прочитать нужный файл (например, /speckit.specify → .dsh/commands/speckit.specify.md)"
echo "и следовать инструкциям из него."
