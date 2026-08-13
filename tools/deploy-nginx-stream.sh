#!/usr/bin/env bash
# tools/deploy-nginx-stream.sh
#
# Применяет фрагмент deploy/80to8897.stream-addition.frag на прод-сервер.
#
# Зачем: по умолчанию nginx буферизует chunked-ответы (~4 KB), и без правки
# конфига real-time прогресс через NDJSON chunked-stream не работает на проде
# (см. specs/181-zakroma-author-load-progress, FR-NX-001/002).
#
# Что делает:
# 1. rsync'ит фрагмент в `/root/Karaoke/deploy/80to8897.stream-addition.frag`
#    на проде.
# 2. По SSH проверяет, есть ли блок `location /api/public/zakroma/stream`
#    в `/etc/nginx/sites-enabled/80to8897`. Если нет — добавляет фрагмент
#    в конец файла (перед закрывающим `}`, если файл site-style).
# 3. Прогоняет `nginx -t` — если syntax error, откатывает фрагмент.
# 4. `systemctl reload nginx` — если reload упал, откатывает + сигналит.
#
# Запуск:
#   bash tools/deploy-nginx-stream.sh
# (с хоста dev-машины, откуда доступен ssh root@${PROD_HOST}).
#
# ENV: PROD_HOST (default 188.119.64.111), NGINX_SITE (default 80to8897).
#
# Лимит рисков: при отказе nginx -t скрипт восстанавливает предыдущий
# конфиг из бэкапа `/etc/nginx/sites-enabled/${NGINX_SITE}.bak-<timestamp>`
# и НЕ делает reload. При успехе — удаляет бэкап.
#
# @see docs/features/zakroma-stream-progress.md
# @see AGENTS.md «nginx 80to8897» (файл, не симлинк; правка — ручная).

set -euo pipefail

PROD_HOST="${PROD_HOST:-188.119.64.111}"
NGINX_SITE="${NGINX_SITE:-80to8897}"
LOCAL_FRAG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/deploy/${NGINX_SITE}.stream-addition.frag"
REMOTE_DIR="/root/Karaoke/deploy"
REMOTE_FRAG="${REMOTE_DIR}/${NGINX_SITE}.stream-addition.frag"
REMOTE_SITE_PATH="/etc/nginx/sites-enabled/${NGINX_SITE}"
MARKER_BEGIN="# === ADDED BY tools/deploy-nginx-stream.sh (BEGIN) ==="
MARKER_END="# === ADDED BY tools/deploy-nginx-stream.sh (END) ==="

if [[ ! -f "${LOCAL_FRAG}" ]]; then
  echo "ERROR: фрагмент не найден: ${LOCAL_FRAG}" >&2
  exit 1
fi

echo "==> [1/4] rsync фрагмента на ${PROD_HOST}:${REMOTE_FRAG}"
ssh "root@${PROD_HOST}" "mkdir -p '${REMOTE_DIR}'"
rsync -avz "${LOCAL_FRAG}" "root@${PROD_HOST}:${REMOTE_FRAG}"

echo "==> [2/4] Проверка наличия location-блока на ${REMOTE_SITE_PATH}"
# Если уже добавлено (маркер присутствует) — skip вставку.
ALREADY_PRESENT=$(ssh "root@${PROD_HOST}" "grep -c '${MARKER_BEGIN}' '${REMOTE_SITE_PATH}' || true")

if [[ "${ALREADY_PRESENT}" == "0" ]]; then
  echo "    location-блок НЕ найден — добавляю фрагмент"

  # Сделать бэкап текущего конфига.
  TS=$(date +%Y%m%d-%H%M%S)
  BACKUP_PATH="${REMOTE_SITE_PATH}.bak-${TS}"
  ssh "root@${PROD_HOST}" "cp '${REMOTE_SITE_PATH}' '${BACKUP_PATH}'"
  echo "    бэкап: ${BACKUP_PATH}"

  # Сформировать блок-вставку: маркеры + фрагмент.
  # Используем heredoc, чтобы не возиться с экранированием.
  INSERT_BLOCK=$(cat <<EOF
${MARKER_BEGIN}
$(cat "${LOCAL_FRAG}")
${MARKER_END}
EOF
)

  # Записать блок в конец файла (sed -i в конце строки безопасен, т.к. nginx-конфиг
  # обычно без trailing-newline-проблем; если файл пустой / битый — будет видно
  # по результату nginx -t ниже).
  ssh "root@${PROD_HOST}" "cat >> '${REMOTE_SITE_PATH}' <<'NGINX_EOF_MARKER'
${INSERT_BLOCK}
NGINX_EOF_MARKER"
else
  echo "    location-блок УЖЕ добавлен (найден маркер) — skip"
fi

echo "==> [3/4] nginx -t (syntax check)"
if ! ssh "root@${PROD_HOST}" "nginx -t"; then
  echo "ERROR: nginx -t упал. Откатываю фрагмент..." >&2
  LATEST_BAK=$(ssh "root@${PROD_HOST}" "ls -t '${REMOTE_SITE_PATH}.bak-'* 2>/dev/null | head -1")
  if [[ -n "${LATEST_BAK}" ]]; then
    ssh "root@${PROD_HOST}" "cp '${LATEST_BAK}' '${REMOTE_SITE_PATH}' && rm '${LATEST_BAK}'"
    echo "    откатил, бэкап удалён. nginx reload НЕ выполнен." >&2
  else
    echo "    бэкап не найден — нужно чинить вручную!" >&2
  fi
  exit 1
fi

echo "==> [4/4] systemctl reload nginx"
ssh "root@${PROD_HOST}" "systemctl reload nginx"

# Успех — удаляем бэкап (он больше не нужен).
ssh "root@${PROD_HOST}" "rm -f '${REMOTE_SITE_PATH}.bak-'* 2>/dev/null || true"

echo ""
echo "OK. /api/public/zakroma/stream теперь работает в real-time на ${PROD_HOST}."
echo "Проверка: curl -N 'https://${PROD_HOST}/api/public/zakroma/stream?author=Test' | head -3"
