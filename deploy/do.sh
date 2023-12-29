#!/usr/bin/env bash

clear

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BASE_DIR="$(cd ${DEPLOY_DIR} && cd .. && pwd)"
GRADLE="$BASE_DIR/gradlew --no-daemon"
APP_VERSION="$(cd $BASE_DIR && $GRADLE | grep KaraokeVersion | awk '/':'/{print $2}')"
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

function do_build() {
  build_jars
  build_images
}

function build_jars() {
  echo "Building jars"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar karaoke-web:bootJar --parallel
}

function build_images() {

  echo "Building docker images"
  echo "BUILD_VERSION = ${BUILD_VERSION}"
  echo "APP_VERSION = ${APP_VERSION}"

  ${DOCKER} image build $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile

  ${DOCKER} image build $BASE_DIR/karaoke-web/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-web/Dockerfile

}

function build_start_app() {
  echo "Building APP module and start"
  build_app
  do_start
}

function build_start_web() {
  echo "Building WEB module and start"
  build_web
  do_start
}

function build_app() {

  echo "Building APP module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar
  ${DOCKER} image build $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile

}

function build_web() {

  echo "Building WEB module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-web:bootJar
  ${DOCKER} image build $BASE_DIR/karaoke-web/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-web/Dockerfile

}

function do_start() {
  do_stop
  echo "Старт LOCAL"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} config
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} up -d
}

function do_stop() {
  echo "Остановка LOCAL"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} down
}

function do_load() {
  do_build
  do_start
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
  do_stop_db
  echo "Старт DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml up -d
}

function do_stop_web() {
  echo "Остановка DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml down
}

function do_push() {
  echo "Pushing images"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
}

function do_pull() {
  echo "Pulling images"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} pull
}

function do_rmi() {
  echo "Removing images"
  do_stop
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml -f $DEPLOY_DIR/docker-compose-database.yml rm
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
}

function do_ps() {
  echo "Listing containers"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml -f $DEPLOY_DIR/docker-compose-database.yml ps
}

cmd=$1
param=$2

case $param in
all)
  DATABASE=" -f $DEPLOY_DIR/docker-compose-database.yml"
  ;;
*)
  DATABASE=""
  ;;
esac

case ${cmd} in
build) do_build ;;
build_app) build_app ;;
build_start_app) build_start_app ;;
build_web) build_web ;;
build_start_web) build_start_web ;;
images) build_images ;;
start) do_start ;;
start_db) do_start_db ;;
start_web) do_start_web ;;
stop) do_stop ;;
stop_db) do_stop_db ;;
stop_web) do_stop_web ;;
load) do_load ;;
push) do_push ;;
pull) do_pull ;;
ps) do_ps ;;
rmi) do_rmi ;;
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