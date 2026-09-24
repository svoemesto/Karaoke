#!/usr/bin/env bash
# Полный цикл деплоя karaoke-web: gradle build → docker build → push → pull на сервере → restart
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/announce.sh"

# PROD_HOST — единственный источник правды: deploy/.env (не хардкодим в скрипте).
# Приоритет: shell-окружение → deploy/.env. Если нигде нет — fail-fast.
if [ -z "${PROD_HOST:-}" ] && [ -f "${SCRIPT_DIR}/.env" ]; then
  PROD_HOST="$(sed -n 's/^PROD_HOST=//p' "${SCRIPT_DIR}/.env" | tail -1 | tr -d ' \r')"
fi
if [ -z "${PROD_HOST:-}" ]; then
  echo "ERROR: PROD_HOST не задан. Укажите PROD_HOST=<host> в ${SCRIPT_DIR}/.env или в окружении." >&2
  exit 1
fi

echo "=== Сборка karaoke-web jar + Docker образа (под локом сборки, см. build-lock.sh) ==="
cd "$SCRIPT_DIR"
bash do.sh build_web

echo "=== Push в Docker Hub ==="
bash do.sh push_web

echo "=== Pull и restart на сервере ==="
ssh root@"${PROD_HOST}" "cd Karaoke/deploy && bash do.sh pull_web"

echo "=== Готово ==="
announce "WEB обновлён на сервере" "Конт+эйнер бэк+нда с+айта обновл+ён на с+ервере" "1"
