#!/usr/bin/env bash

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BASE_DIR="$(cd ${DEPLOY_DIR} && cd .. && pwd)"
GRADLE_DB="$BASE_DIR/karaoke-db/gradlew --no-daemon"
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)

source ${DEPLOY_DIR}/do.env

function do_build() {
#  build_db
}

#function build_db() {
#  echo "Building DB module"
#  cd ${BASE_DIR} && ${GRADLE_DB} clean karaoke-db:bootJar
#  ${DOCKER} image build $BASE_DIR/karaoke-db/build/libs/ -t "karaoke-db" -f $DEPLOY_DIR/karaoke-db/Dockerfile
#}

function do_start() {
  do_stop
  echo "Starting LOCAL dev environment"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml up -d
}

function do_stop() {
  echo "Stopping LOCAL dev environment"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml down
}

function do_load() {
  do_build
  do_start
}

cmd=$1

case ${cmd} in
build) do_build ;;
start) do_start ;;
stop) do_stop ;;
load) do_load ;;
esac
