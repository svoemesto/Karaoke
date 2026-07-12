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

# Очередь/взаимное исключение gradle-сборок (см. build-lock.sh)
source ${DEPLOY_DIR}/build-lock.sh
# Флаг форса: FORCE=1 в окружении или --force среди аргументов do.sh
case " $* " in *" --force "*) FORCE=1 ;; esac

echo "DEPLOY_DIR = $DEPLOY_DIR"
echo "BASE_DIR = $BASE_DIR"
echo "GRADLE = $GRADLE"
echo "APP_VERSION = $APP_VERSION"
echo "BUILD_VERSION = $BUILD_VERSION"
echo "DOCKER = $DOCKER"
echo "COMPOSE = $COMPOSE"

source "${DEPLOY_DIR}/announce.sh"

function do_build() {
  bl_begin karaoke-web "build" karaoke-app karaoke-web; local rc=$?
  [ "$rc" = 10 ] && return 0
  build_jars
  build_images
  bl_commit
  bl_release
}

function build_jars() {
  echo "Building jars"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar karaoke-web:bootJar --parallel
  announce "Все модули скомпилированы" "Все м+одули скомпил+ированы"
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

  ${DOCKER} image build $BASE_DIR/ --build-arg VERSION=${BUILD_VERSION} \
    -t "$DOCKER_REGISTRY/karaoke-webvue:${BUILD_VERSION}" -f $DEPLOY_DIR/karaoke-webvue/Dockerfile

  announce "Все модули собраны" "Все м+одули с+обраны"
}

function build_start_app() {
  echo "Building APP module and start"
  do_build_app
  do_start
}

function build_start_web() {
  echo "Building WEB module and start"
  do_build_web
  do_start_web
}

function build_start_webvue() {
  echo "Building WEBVUE module and start"
  do_build_webvue
  do_start_webvue
}

function build_start_webvue3() {
  echo "Building WEBVUE3 module and start"
  do_build_webvue3
  do_start_webvue3
}

function build_start_public() {
  echo "Building PUBLIC module and start"
  do_build_public
  do_start_public
}

function do_build_app() {

  bl_begin karaoke-app "build_app"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building APP module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar
  cp $DEPLOY_DIR/karaoke-app/files/* ~/Karaoke/karaoke-app/build/libs
  ${DOCKER} image build $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile
  bl_commit
  bl_release
}

function do_build_app_nocache() {

  # nocache — осознанная принудительная пересборка, пропуск по отпечатку отключаем
  FORCE=1 bl_begin karaoke-app "build_app_nocache"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building APP module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar
  cp $DEPLOY_DIR/karaoke-app/files/* ~/Karaoke/karaoke-app/build/libs
  ${DOCKER} image build --no-cache $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile
  bl_commit
  bl_release
}

function do_build_demucs() {
  echo "Building DEMUCS module"
  ${DOCKER} image build $DEPLOY_DIR/karaoke-app/files/ \
   -t "$DOCKER_REGISTRY/demucs:latest" \
   -f $DEPLOY_DIR/karaoke-app/DockerfileDemucs
}

function do_build_web() {

  bl_begin karaoke-web "build_web"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building WEB module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-web:bootJar
  ${DOCKER} image build $BASE_DIR/karaoke-web/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-web/Dockerfile
  bl_commit
  bl_release
}

function do_build_webvue() {
  echo "Building WEBVUE module"
  ${DOCKER} image build $BASE_DIR/ --build-arg VERSION=${BUILD_VERSION} \
    -t "$DOCKER_REGISTRY/karaoke-webvue:${BUILD_VERSION}" -f $DEPLOY_DIR/karaoke-webvue/Dockerfile
}

function do_build_webvue3() {
  echo "Building WEBVUE3 module"
  ${DOCKER} image build $BASE_DIR/ --build-arg VERSION=${BUILD_VERSION} \
    -t "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}" -f $DEPLOY_DIR/karaoke-webvue3/Dockerfile
}

function do_build_public() {
  echo "Building PUBLIC module"
  ${DOCKER} image build $BASE_DIR/ --build-arg VERSION=${BUILD_VERSION} \
    -t "$DOCKER_REGISTRY/karaoke-public:${BUILD_VERSION}" -f $DEPLOY_DIR/karaoke-public/Dockerfile
}

function do_start() {
  do_stop
  echo "Старт LOCAL"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} config
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} up -d
  announce "Все контейнеры запущены" "Все конт+эйнеры зап+ущены"
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
  announce "DATABASE запущен" "Б+аза д+анных зап+ущена"
}

function do_stop_db() {
  echo "Остановка DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-database.yml down
}

function do_start_web() {
  do_stop_web
  echo "Старт WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml up -d
  announce "WEB запущен" "Бэк+энда с+айта зап+ущен"
}

function do_stop_web() {
  echo "Остановка WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml down
}

function do_start_webvue() {
  do_stop_webvue
  echo "Старт WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue.yml up -d
  announce "Старт WEBVUE" "Адм+инка зап+ущена"
}

function do_start_webvue3() {
  do_stop_webvue3
  echo "Старт WEBVUE3"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3.yml up -d
  announce "Старт WEBVUE3" "Адм+инка зап+ущена"
}

function do_stop_webvue() {
  echo "Остановка WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue.yml down
  announce "Остановка WEBVUE" "-"
}

function do_stop_webvue3() {
  echo "Остановка WEBVUE3"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3.yml down
  announce "Остановка WEBVUE3" "-"
}

function do_start_public() {
  do_stop_public
  echo "Старт PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml up -d
  announce "Старт PUBLIC" "Фронт+энд с+айта зап+ущен"
}

function do_stop_public() {
  echo "Остановка PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml down
  announce "Остановка PUBLIC" "-"
}

function do_start_app() {
  do_stop_webvue
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml up -d
  announce "Старт APP" "Бэк+энд зап+ущен"
}

function do_stop_app() {
  echo "Остановка APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml down
  announce "Остановка APP" "-"
}

function do_push() {
  echo "Pushing images"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  announce "Pushing!"
}

function do_push_app() {
  echo "Pushing APP"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  announce "Pushing APP!"
}

function do_push_demucs() {
  echo "Pushing DEMUCS"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/demucs:latest"
  announce "Pushing DEMUCS!"
}

function do_push_web() {
  echo "Pushing WEB"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
  announce "Pushing WEB!"
}

function do_push_webvue() {
  echo "Pushing WEBVUE"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue:${BUILD_VERSION}"
  announce "Pushing WEBVUE!"
}

function do_push_webvue3() {
  echo "Pushing WEBVUE3"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  announce "Pushing WEBVUE3!"
}

function do_push_public() {
  echo "Pushing PUBLIC"
  ${DOCKER} login --username ${DOCKER_REGISTRY} --password ${DOCKER_PASSWORD}
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-public:${BUILD_VERSION}"
  announce "Pushing PUBLIC!"
}

function do_pull() {
  echo "Pulling images"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml ${DATABASE} pull
  announce "Pulling!"
}

function do_rmi() {
  echo "Removing images"
  do_stop
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml -f $DEPLOY_DIR/docker-compose-database.yml rm
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-webvue:${BUILD_VERSION}"
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  announce "Removing!"
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
build_start) do_load ;;
build_app) do_build_app ;;
build_app_nocache) do_build_app_nocache ;;
build_demucs) do_build_demucs ;;
build_web) do_build_web ;;
build_webvue) do_build_webvue ;;
build_webvue3) do_build_webvue3 ;;
build_public) do_build_public ;;
build_start_app) build_start_app ;;
build_start_web) build_start_web ;;
build_start_webvue) build_start_webvue ;;
build_start_webvue3) build_start_webvue3 ;;
build_start_public) build_start_public ;;
images) build_images ;;
start) do_start ;;
start_db) do_start_db ;;
start_app) do_start_app ;;
start_web) do_start_web ;;
start_webvue) do_start_webvue ;;
start_webvue3) do_start_webvue3 ;;
start_public) do_start_public ;;
stop) do_stop ;;
stop_db) do_stop_db ;;
stop_app) do_stop_app ;;
stop_web) do_stop_web ;;
stop_webvue) do_stop_webvue ;;
stop_webvue3) do_stop_webvue3 ;;
stop_public) do_stop_public ;;
load) do_load ;;
push) do_push ;;
push_app) do_push_app ;;
push_demucs) do_push_demucs ;;
push_web) do_push_web ;;
push_webvue) do_push_webvue ;;
push_webvue3) do_push_webvue3 ;;
push_public) do_push_public ;;
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

    load, build_start - (re)builds jars, images and (re)starts containers
    build - builds jars and images
    build_app - builds karaoke-app image
    build_demucs - builds demucs image (GPU/CUDA-enabled), tagged \$DOCKER_REGISTRY/demucs:latest
    push_demucs - pushes demucs image to DOCKER_REGISTRY
    build_web - builds karaoke-web image
    build_webvue - builds karaoke-webvue image
    build_start_app - builds karaoke-app image and (re)starts containers
    build_start_web - builds karaoke-web image and (re)starts containers
    build_start_webvue - builds karaoke-webvue image and (re)starts containers
    build_public - builds karaoke-public image
    build_start_public - builds karaoke-public image and (re)starts containers
    start_public / stop_public - (re)starts/stops karaoke-public container
    push_public - pushes karaoke-public image to DOCKER_REGISTRY
    push - pushes images to DOCKER_REGISTRY
    pull - pulls images from DOCKER_REGISTRY
    ps - lists running containers
    rmi - removes images"
  exit
  ;;
esac