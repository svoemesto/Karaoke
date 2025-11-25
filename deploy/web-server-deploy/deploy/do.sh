#!/usr/bin/env bash

clear

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BASE_DIR="$(cd ${DEPLOY_DIR} && cd .. && pwd)"
APP_VERSION=1
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)

if [[ -z "${BUILD_VERSION}" ]]; then
  BUILD_VERSION="$(touch .version && cat .version)"
  if [[ -z "${BUILD_VERSION}" ]]; then
    echo "${APP_VERSION}" >.version
  fi
else
  echo "${BUILD_VERSION}" >.version
fi

source ${DEPLOY_DIR}/do.env

function build_web() {
  echo "Building WEB module"
  ${DOCKER} image build $DEPLOY_DIR/karaoke-web-jar/ \
  --build-arg VERSION=1 \
  --build-arg APP_VERSION=1 \
  -t "$DOCKER_REGISTRY/karaoke-web:1" \
  -f $DEPLOY_DIR/karaoke-web/Dockerfile
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
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U NsAkArAoKeUsEr --file="/dumps/karaoke_clear_dump.sql" -d postgres'
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Create clear DB!"
}

cmd=$1

case ${cmd} in
build_web) build_web ;;
start_db) do_start_db ;;
start_web) do_start_web ;;
start_storage) do_start_storage ;;
stop_db) do_stop_db ;;
stop_web) do_stop_web ;;
stop_storage) do_stop_storage ;;
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