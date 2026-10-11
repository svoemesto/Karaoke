#!/usr/bin/env bash
# Полный цикл деплоя karaoke-public: docker build (vue→nginx) → push → pull на сервере → restart
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
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

echo "=== Сборка Docker образа karaoke-public ==="
bash do.sh build_public

echo "=== Push в registry (см. do.env: DOCKER_REGISTRY; DUAL_PUSH зеркалит в Hub) ==="
bash do.sh push_public

echo "=== Pull и restart на сервере ==="
# Pass 540 (issue #286): после pull проверяем, что на сервере действительно НОВЫЙ образ.
# `Status: Downloaded newer image` — признак, что pull забрал свежий манифест;
# `Image is up to date` при новом push — сигнал, что что-то не доехало.
PULL_OUT="$(ssh root@"${PROD_HOST}" "cd Karaoke/deploy && bash do.sh pull_public" 2>&1)"
printf '%s\n' "$PULL_OUT"
if printf '%s' "$PULL_OUT" | grep -q 'Downloaded newer image'; then
  echo "=== OK: на сервере образ обновлён ==="
elif printf '%s' "$PULL_OUT" | grep -q 'Image is up to date'; then
  echo "WARN: на сервере 'Image is up to date' — проверьте, что push прошёл и тег совпал" >&2
else
  echo "WARN: в выводе pull нет ни 'Downloaded newer image', ни 'Image is up to date' — проверьте вручную" >&2
fi

echo "=== Готово ==="
announce "PUBLIC обновлён на сервере" "Конт+эйнер фронт+энда с+айта обновл+ён на с+ервере" "1"
