#!/usr/bin/env bash

clear

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BASE_DIR="$(cd ${DEPLOY_DIR} && cd .. && pwd)"
APP_VERSION=1
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)

# BR-042 (аудит #336 / P-70-04, OpenProject #393): единый тег образа.
#
# Было: build_* тегировали `${DOCKER_REGISTRY}/karaoke-web:${BUILD_VERSION}`, а pull_*/start_*
# и compose-файлы прода были прибиты ЛИТЕРАЛОМ `:1`. Пока BUILD_VERSION=1 они совпадали, но
# как только сборка шла с BUILD_VERSION=<номер>, push уходил в :<номер>, а прод продолжал
# тянуть :1 — то есть предыдущий (или чужой) образ, и deploy_web.sh этого не различал.
#
# Стало: IMAGE_TAG — единственный источник тега. deploy_web.sh/deploy_public.sh передают
# собранный тег на прод по ssh (`BUILD_VERSION=<тег> bash do.sh pull_web`), а compose-файлы
# подставляют `${IMAGE_TAG}` (экспортируется ниже, set -a активен). Без переменной окружения
# берётся `.version` этого хоста, а если и его нет — 1 (прежнее поведение).
PREV_VERSION="$(cat "${DEPLOY_DIR}/.version" 2>/dev/null || true)"
if [[ -z "${BUILD_VERSION:-}" ]]; then
  BUILD_VERSION="${PREV_VERSION:-${APP_VERSION}}"
fi
# Предыдущий тег — для откката: `BUILD_VERSION=$(cat .version.prev) bash do.sh pull_web`.
if [[ -n "${PREV_VERSION}" && "${PREV_VERSION}" != "${BUILD_VERSION}" ]]; then
  echo "${PREV_VERSION}" >"${DEPLOY_DIR}/.version.prev"
fi
echo "${BUILD_VERSION}" >"${DEPLOY_DIR}/.version"
IMAGE_TAG="${BUILD_VERSION}"
export IMAGE_TAG

source ${DEPLOY_DIR}/do.env

# Pass 540 (issue #286): логин в НАШ registry — явный сервер + пароль через stdin.
# Было: логин без сервера — уходил в Docker Hub, пароль был виден в `ps`
# (дефект №1 карты #274).
# Pass 540 (issue #286), инцидент 2026-10-11: страховка от «сначала снести,
# потом обнаружить, что образа нет». Порядок обязан быть таким: pull →
# проверка → и только потом остановка/старт. Здесь — вторая линия обороны:
# start_* отказывается работать, если образа нет локально.
function require_image() { # $1 = полная ссылка на контейнерный образ
  if ! ${DOCKER} image inspect "$1" >/dev/null 2>&1; then
    echo "ERROR: контейнерного образа $1 нет локально — старт отменён (сначала pull)." >&2
    return 1
  fi
}

function do_registry_login() {
  if [[ -z "${REGISTRY_HOST:-}" || -z "${REGISTRY_USER:-}" ]]; then
    echo "ERROR: REGISTRY_HOST/REGISTRY_USER не заданы в do.env" >&2
    return 1
  fi
  printf '%s' "${REGISTRY_PASSWORD:-}" \
    | ${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin
}

function build_web() {
  echo "Building WEB module"
  ${DOCKER} image build $DEPLOY_DIR/karaoke-web-jar/ \
  --build-arg VERSION=1 \
  --build-arg APP_VERSION=1 \
  -t "$DOCKER_REGISTRY/karaoke-web:${IMAGE_TAG}" \
  -f $DEPLOY_DIR/karaoke-web/Dockerfile
}

function pull_web() {
  echo "Pull WEB image from registry"
  do_registry_login || { echo "ERROR: логин в registry не удался — контейнер НЕ трогаем." >&2; return 1; }
  if ! ${DOCKER} pull "$DOCKER_REGISTRY/karaoke-web:${IMAGE_TAG}"; then
    echo "ERROR: образ $DOCKER_REGISTRY/karaoke-web:${IMAGE_TAG} не скачан — контейнер НЕ трогаем." >&2
    return 1
  fi
  require_image "$DOCKER_REGISTRY/karaoke-web:${IMAGE_TAG}" || return 1
  do_start_web
}

function do_start_public() {
  require_image "$DOCKER_REGISTRY/karaoke-public:${IMAGE_TAG}" || return 1
  do_stop_public
  echo "Старт PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml up -d
}

function do_stop_public() {
  echo "Остановка PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml down
}

function pull_public() {
  echo "Pull PUBLIC image from registry"
  do_registry_login || { echo "ERROR: логин в registry не удался — контейнер НЕ трогаем." >&2; return 1; }
  if ! ${DOCKER} pull "$DOCKER_REGISTRY/karaoke-public:${IMAGE_TAG}"; then
    echo "ERROR: образ $DOCKER_REGISTRY/karaoke-public:${IMAGE_TAG} не скачан — контейнер НЕ трогаем." >&2
    return 1
  fi
  require_image "$DOCKER_REGISTRY/karaoke-public:${IMAGE_TAG}" || return 1
  do_start_public
}

function do_start_db() {
  do_stop_db
  echo "Старт DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-database.yml up -d
}

function do_stop_db() {
  echo "Остановка DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-database.yml down
}

function do_start_web() {
  require_image "$DOCKER_REGISTRY/karaoke-web:${IMAGE_TAG}" || return 1
  do_stop_web
  echo "Старт WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml up -d
}

function do_stop_web() {
  echo "Остановка WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml down
}

function do_start_storage() {
  do_stop_storage
  echo "Старт STORAGE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-storage.yml up -d
}

function do_stop_storage() {
  echo "Остановка STORAGE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-storage.yml down
}

function do_ps() {
  echo "Listing containers"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml -f $DEPLOY_DIR/docker-compose-database.yml -f $DEPLOY_DIR/docker-compose-storage.yml ps
}

function do_create_clear_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U ${DB_SERVER_POSTGRES_USER} --file="/dumps/karaoke_clear_dump.sql" -d postgres'
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Create clear DB!"
}

cmd=$1

case ${cmd} in
build_web) build_web ;;
pull_web) pull_web ;;
start_db) do_start_db ;;
start_web) do_start_web ;;
start_storage) do_start_storage ;;
start_public) do_start_public ;;
pull_public) pull_public ;;
stop_db) do_stop_db ;;
stop_web) do_stop_web ;;
stop_storage) do_stop_storage ;;
stop_public) do_stop_public ;;
ps) do_ps ;;
create_clear_db) do_create_clear_db ;;
*)
  echo "Описание команды:
    $(basename $0) <command> <param>
    Commands are:

    start - (re)starts containers for LOCAL dev env, including DATABASE
    stop - stops containers for LOCAL dev

    start_db - (re)starts DATABASE
    stop_db - stops DATABASE

    Param for commands above:

    all - include starting/stopping DATABASE:

    $(basename $0) start all - starts containers for local developement including DATABASE
    $(basename $0) stop all - stops containers for local developement including DATABASE

    load - (re)builds jars, images and (re)starts containers
    build - builds jars and images
    build_app - builds karaoke-app image
    build_web - builds karaoke-web image
    build_start_app - builds karaoke-app image and (re)starts containers
    build_start_web - builds karaoke-web image and (re)starts containers
    push - pushes images to DOCKER_REGISTRY
    pull - pulls images from DOCKER_REGISTRY
    ps - lists running containers
    rmi - removes images"
  exit
  ;;
esac
