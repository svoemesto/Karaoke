#!/usr/bin/env bash

clear

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
APP_VERSION=1
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)

function do_start_db() {
  do_stop_db
  echo "Старт DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-db-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "DATABASE запущен"
}

function do_stop_db() {
  echo "Остановка DATABASE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-db-new-comp.yml down
}

function do_start_web() {
  do_stop_web
  echo "Старт WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "WEB запущен"
}

function do_stop_web() {
  echo "Остановка WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web-new-comp.yml down
}

function do_start_webvue() {
  do_stop_webvue
  echo "Старт WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "WEBVUE запущен"
}

function do_stop_webvue() {
  echo "Остановка WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue-new-comp.yml down
}

function do_start_app() {
  do_stop_webvue
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "APP запущен"
}

function do_start_app2() {
  do_stop_webvue
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml up
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "APP запущен"
}

function do_stop_app() {
  echo "Остановка APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml down
}

function do_pull_app() {
  echo "Pulling APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml pull
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Pulling APP!"
}

function do_pull_web() {
  echo "Pulling WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web-new-comp.yml pull
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Pulling WEB!"
}

function do_pull_webvue() {
  echo "Pulling WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue-new-comp.yml pull
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Pulling WEBVUE!"
}

function do_restore_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U postgres --file="/dumps/karaoke_dump.sql" karaoke'
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Restore DB!"
}

cmd=$1

case ${cmd} in

start_db) do_start_db ;;
start_app) do_start_app ;;
start_app2) do_start_app2 ;;
start_web) do_start_web ;;
start_webvue) do_start_webvue ;;
stop_db) do_stop_db ;;
stop_app) do_stop_app ;;
stop_web) do_stop_web ;;
stop_webvue) do_stop_webvue ;;
pull_app) do_push_app ;;
pull_web) do_push_web ;;
pull_webvue) do_push_webvue ;;
restore_db) do_restore_db ;;
*)
  echo "Описание команды:
    $(basename $0) <command> <param>
    Commands are:

    restore_db - создание БД в контейнере из беэкап-файла

    start_db - (пере)запуск контейнера БД
    stop_db - остановка контейнера БД

    start_app - (пере)запуск контейнера приложения
    start_app2 - (пере)запуск контейнера приложения в режиме мониторинга
    stop_app - остановка контейнера приложения
    pull_app - pull контейнера приложения

    start_web - (пере)запуск контейнера WEB
    stop_web - остановка контейнера WEB
    pull_web - pull контейнера WEB

    start_webvue - (пере)запуск контейнера WEBVUE
    stop_webvue - остановка контейнера WEBVUE
    pull_webvue - pull контейнера WEBVUE
    "
  exit
  ;;
esac