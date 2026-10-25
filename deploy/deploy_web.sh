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

# BR-042 (аудит #336 / P-70-04, OpenProject #393): тег сборки — один на весь цикл.
# Берём его из deploy/.version (туда его пишет do.sh и при сборке, и при push) и передаём
# на прод явно: раньше прод тянул ЛИТЕРАЛ :1, и при BUILD_VERSION != 1 на сервер уезжал
# предыдущий образ. Пусто → 1 (как раньше).
if [ -z "${BUILD_VERSION:-}" ] && [ -f "${SCRIPT_DIR}/.version" ]; then
  BUILD_VERSION="$(cat "${SCRIPT_DIR}/.version" | tr -d ' \r')"
fi
export BUILD_VERSION="${BUILD_VERSION:-1}"
echo "=== Тег образов (BUILD_VERSION): ${BUILD_VERSION} ==="

echo "=== Сборка karaoke-web jar + Docker образа (под локом сборки, см. build-lock.sh) ==="
cd "$SCRIPT_DIR"
bash do.sh build_web

echo "=== Push в registry (см. do.env: DOCKER_REGISTRY; DUAL_PUSH зеркалит в Hub) ==="
bash do.sh push_web

echo "=== Pull и restart на сервере ==="
# Pass 540 (issue #286): после pull проверяем, что на сервере действительно НОВЫЙ образ.
# `Status: Downloaded newer image` — признак, что pull забрал свежий манифест;
# `Image is up to date` при новом push — сигнал, что что-то не доехало.
PULL_OUT="$(ssh root@"${PROD_HOST}" "cd Karaoke/deploy && BUILD_VERSION=$(printf '%q' "${BUILD_VERSION}") bash do.sh pull_web" 2>&1)"
printf '%s\n' "$PULL_OUT"
if printf '%s' "$PULL_OUT" | grep -q 'Downloaded newer image'; then
  echo "=== OK: на сервере образ обновлён ==="
elif printf '%s' "$PULL_OUT" | grep -q 'Image is up to date'; then
  # BR-043 (аудит #336 / P-70-05, OpenProject #371): «Image is up to date» —
  # НЕ успешный деплой. Раньше здесь был WARN, и скрипт завершался с кодом 0:
  # оператор видел «Готово», а прод работал на старом образе (push мог упасть
  # тихо — теперь это уже невозможно, do.sh проверяет код возврата push).
  # Если образ реально не изменился (сборка дала тот же digest) — деплой
  # считаем невыполненным: артефакт не доставлен, молчать об этом нельзя.
  echo "ERROR: на сервере 'Image is up to date' — новый образ не доставлен (push не прошёл или тег не совпал)" >&2
  exit 1
else
  echo "WARN: в выводе pull нет ни 'Downloaded newer image', ни 'Image is up to date' — проверьте вручную" >&2
fi

echo "=== Готово ==="
announce "WEB обновлён на сервере" "Конт+эйнер бэк+нда с+айта обновл+ён на с+ервере" "1"
