#!/usr/bin/env bash
# Полный цикл деплоя karaoke-public: docker build (vue→nginx) → push → pull на сервере → restart
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
source "${SCRIPT_DIR}/announce.sh"

echo "=== Сборка Docker образа karaoke-public ==="
bash do.sh build_public

echo "=== Push в Docker Hub ==="
bash do.sh push_public

echo "=== Pull и restart на сервере ==="
ssh root@79.174.95.69 "cd Karaoke/deploy && bash do.sh pull_public"

echo "=== Готово ==="
announce "PUBLIC обновлён на сервере" "Конт+эйнер фронт+энда с+айта обновл+ён на с+ервере"
