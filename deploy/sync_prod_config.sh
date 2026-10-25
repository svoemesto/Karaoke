#!/usr/bin/env bash
# Синхронизация КОНФИГУРАЦИИ на прод.
#
# Пробел, который стоил нам BR-549 (аудит #336, пакет PKG-S3-21 / #393):
# deploy_web.sh / deploy_public.sh выкладывают ОБРАЗ, но конфиги на проде —
# каталог выкладки без git-чекаута — никто не копировал. Правка nginx/compose
# оставалась в репозитории, а на проде лежал старый файл: guard проверял
# репозиторий и рапортовал «заголовки на месте», а живой контейнер их не
# отдавал. Прод-спрайт уехал с правкой, а nginx-конфиг — нет.
#
# Что делает шаг для каждого файла:
# 1. показывает diff (прод ← репозиторий) — видно, что реально изменится;
# 2. копирует файл СОДЕРЖИМОМ (`cat new > target`, а НЕ `mv`): `mv` создаёт
#    новый inode, а bind-mount в контейнере продолжает видеть старый файл
#    (см. docs/ops/prod-config-delivery.md);
# 3. перед заменой — бэкап с меткой времени;
# 4. валидирует: compose — `docker compose config --quiet`, nginx —
#    `docker exec <контейнер> nginx -t`; при ошибке откатывает бэкап;
# 5. печатает, что обновлено.
#
# Использование (из deploy_web.sh / deploy_public.sh):
#   source "$(dirname "${BASH_SOURCE[0]}")/sync_prod_config.sh"
#   sync_prod_config web      # docker-compose-web.yml
#   sync_prod_config public   # docker-compose-public.yml + nginx_karaoke-public.conf
#   sync_prod_config db       # docker-compose-database.yml
#   sync_prod_config storage  # docker-compose-storage.yml

PROD_DEPLOY_DIR="/root/Karaoke/deploy"
PROD_CONFIG_SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/prod-single-host"

# Валидатор для конкретного файла: 0 — конфиг валиден.
_prod_validate() {
  local file="$1"
  case "$file" in
    *.conf)
      # Конфиг смонтирован в контейнер karaoke-public → проверяем тем же nginx'ом.
      ssh "root@${PROD_HOST}" \
        "docker exec karaoke-public nginx -t" >/dev/null 2>&1
      ;;
    *.yml)
      # Как на самом проде (do.sh): переменные окружения берутся из do.env —
      # без них compose не знает DOCKER_REGISTRY и падает на интерполяции.
      ssh "root@${PROD_HOST}" \
        "cd ${PROD_DEPLOY_DIR} && set -a && . ./do.env && set +a && DOCKER_CONFIG=/root/.docker docker compose -f ${file} config --quiet" >/dev/null 2>&1
      ;;
    *)
      return 0
      ;;
  esac
}

sync_prod_config() {
  local target="${1:-web}"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local files=()

  case "$target" in
    web)
      files=("docker-compose-web.yml")
      ;;
    public)
      files=("docker-compose-public.yml" "nginx_karaoke-public.conf")
      ;;
    db)
      files=("docker-compose-database.yml")
      ;;
    storage)
      files=("docker-compose-storage.yml")
      ;;
    *)
      echo "ERROR: неизвестная цель '$target' (ожидаются: web | public | db | storage)" >&2
      return 1
      ;;
  esac

  echo "=== Синхронизация конфигурации на прод (${files[*]}) ==="
  for f in "${files[@]}"; do
    local src="${PROD_CONFIG_SRC_DIR}/${f}"
    if [ ! -f "$src" ]; then
      echo "WARN: ${src} не найден — пропускаю" >&2
      continue
    fi

    echo "--- diff ${f} (прод ← репозиторий):"
    ssh "root@${PROD_HOST}" "cat ${PROD_DEPLOY_DIR}/${f} 2>/dev/null" | diff - "$src" || true

    # Копируем содержимым — inode файла сохраняется, и bind-mount в контейнере
    # сразу видит новое содержимое.
    scp -q "$src" "root@${PROD_HOST}:${PROD_DEPLOY_DIR}/${f}.new"
    ssh "root@${PROD_HOST}" \
      "set -e; cd ${PROD_DEPLOY_DIR}; cp ${f} ${f}.bak-${stamp} 2>/dev/null || true; cat ${f}.new > ${f}; rm -f ${f}.new"

    if ! _prod_validate "$f"; then
      echo "ERROR: конфиг невалиден после замены ${f} — откатываю бэкап" >&2
      ssh "root@${PROD_HOST}" "cd ${PROD_DEPLOY_DIR} && cp ${f}.bak-${stamp} ${f}" || true
      return 1
    fi
    echo "OK: ${f} на проде обновлён (бэкап ${f}.bak-${stamp})"
  done
}
