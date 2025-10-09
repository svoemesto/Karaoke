#!/usr/bin/env bash

clear

set -a

echo "Starting do.sh"

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
APP_VERSION=1
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)
DOCKER_REGISTRY=svoemestodev
DOCKER_PASSWORD=dckr_pat_SxLnc4cA4EChRdvQcnQjZCPOgw0
#DOCKER_PASSWORD=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


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
    # Определяем первый IP-адрес с помощью hostname -I
    IP_ADDRESS=$(hostname -I | awk '{print $1}')

    # Проверяем, что IP-адрес был получен
    if [ -z "$IP_ADDRESS" ]; then
      echo "Не удалось определить IP-адрес."
    else
        # Определяем директорию, откуда запущен скрипт
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

        # Задаем пути к файлам
        TEMPLATE_FILE="$SCRIPT_DIR/nginx.conf.template"
        OUTPUT_FILE="$SCRIPT_DIR/nginx.conf"

        # Проверяем, что файл шаблона существует
        if [ ! -f "$TEMPLATE_FILE" ]; then
          echo "Файл nginx.conf.template не найден в директории $SCRIPT_DIR"
        else
            # Заменяем MY_IP_ADDRESS на реальный IP и сохраняем в nginx.conf
            sed "s/MY_IP_ADDRESS/$IP_ADDRESS/g" "$TEMPLATE_FILE" > "$OUTPUT_FILE"
            echo "Файл nginx.conf успешно создан в $OUTPUT_FILE с IP-адресом $IP_ADDRESS"
        fi
    fi

  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "WEBVUE запущен"
}

function do_start_webvue3() {
  do_stop_webvue3
  echo "Старт WEBVUE3"
    # Определяем первый IP-адрес с помощью hostname -I
    IP_ADDRESS=$(hostname -I | awk '{print $1}')

    # Проверяем, что IP-адрес был получен
    if [ -z "$IP_ADDRESS" ]; then
      echo "Не удалось определить IP-адрес."
    else
        # Определяем директорию, откуда запущен скрипт
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

        # Задаем пути к файлам
        TEMPLATE_FILE="$SCRIPT_DIR/nginx_webvue3.conf.template"
        OUTPUT_FILE="$SCRIPT_DIR/nginx_webvue3.conf"

        # Проверяем, что файл шаблона существует
        if [ ! -f "$TEMPLATE_FILE" ]; then
          echo "Файл nginx_webvue3.conf.template не найден в директории $SCRIPT_DIR"
        else
            # Заменяем MY_IP_ADDRESS на реальный IP и сохраняем в nginx.conf
            sed "s/MY_IP_ADDRESS/$IP_ADDRESS/g" "$TEMPLATE_FILE" > "$OUTPUT_FILE"
            echo "Файл nginx_webvue3.conf успешно создан в $OUTPUT_FILE с IP-адресом $IP_ADDRESS"
        fi
    fi

  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "WEBVUE3 запущен"
}

function do_stop_webvue() {
  echo "Остановка WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue-new-comp.yml down
}

function do_stop_webvue3() {
  echo "Остановка WEBVUE3"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3-new-comp.yml down
}

function do_start_app() {
  do_stop_app
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml up -d
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "APP запущен"
}

function do_start_app2() {
  do_stop_app
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml up
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "APP запущен"
}

function do_stop_app() {
  echo "Остановка APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app-new-comp.yml down
  docker rm karaoke-app
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

function do_pull_webvue3() {
  echo "Pulling WEBVUE3"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3-new-comp.yml pull
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Pulling WEBVUE3!"
}

function do_restore_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U postgres --file="/dumps/karaoke_dump.sql" karaoke'
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Restore DB!"
}

function do_create_clear_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U postgres --file="/dumps/karaoke_clear_dump.sql" -d postgres'
  command -v paplay &> /dev/null && paplay /usr/share/sounds/freedesktop/stereo/complete.oga
  command -v notify-send &> /dev/null && notify-send -u normal "Karaoke" "Create clear DB!"
}

cmd=$1

case ${cmd} in

start_db) do_start_db ;;
start_app) do_start_app ;;
start_app2) do_start_app2 ;;
start_web) do_start_web ;;
start_webvue) do_start_webvue ;;
start_webvue3) do_start_webvue3 ;;
stop_db) do_stop_db ;;
stop_app) do_stop_app ;;
stop_web) do_stop_web ;;
stop_webvue) do_stop_webvue ;;
stop_webvue3) do_stop_webvue3 ;;
pull_app) do_pull_app ;;
pull_web) do_pull_web ;;
pull_webvue) do_pull_webvue ;;
pull_webvue3) do_pull_webvue3 ;;
restore_db) do_restore_db ;;
create_clear_db) do_create_clear_db ;;
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
