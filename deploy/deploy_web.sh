#!/usr/bin/env bash
# Полный цикл деплоя karaoke-web: gradle build → docker build → push → pull на сервере → restart
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/announce.sh"

echo "=== Сборка karaoke-web jar + Docker образа (под локом сборки, см. build-lock.sh) ==="
cd "$SCRIPT_DIR"
bash do.sh build_web

echo "=== Push в Docker Hub ==="
bash do.sh push_web

echo "=== Pull и restart на сервере ==="
ssh root@79.174.95.69 "cd Karaoke/deploy && bash do.sh pull_web"

echo "=== Готово ==="
announce "WEB обновлён на сервере"
