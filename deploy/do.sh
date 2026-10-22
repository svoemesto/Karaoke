#!/usr/bin/env bash

clear

# set -a включается ТОЛЬКО на время загрузки секретов из do.env — иначе утекают
# в env gradle/docker локальные переменные (BL_FD, BL_PENDING, DATABASE, …).
# PR #084: см. AGENTS.md, раздел «Ограничения агента» → «set -a без set +a».
set -a
echo "Starting do.sh"

# Pass 372 (OP #83): правило GRADLE_USER_HOME применяется ТРАНЗИТИВНО.
# Этот скрипт вызывает ./gradlew внутри себя (do_build_app, do_build_web, …).
# Если GRADLE_USER_HOME не задан И /home/nsa/Karaoke/.gradle не существует —
# wrapper упадёт с read-only FileNotFoundException. Устанавливаем явно.
if [[ -z "${GRADLE_USER_HOME:-}" ]]; then
  export GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle
fi
# JAVA_HOME часто приходит из /etc/profile.d, но для надёжности — fallback.
if [[ -z "${JAVA_HOME:-}" ]] && [[ -d /usr/lib/jvm/jdk-18 ]]; then
  export JAVA_HOME=/usr/lib/jvm/jdk-18
fi

# Pass 373 (OP #83 follow-up): правило DOCKER_CONFIG применяется ТРАНЗИТИВНО.
# Этот скрипт вызывает ${DOCKER} image build / buildx внутри себя (do_build_app,
# do_build_web, …). Без правильного DOCKER_CONFIG buildx падает с read-only в
# ~/.docker/buildx/activity. В DSH-sandbox нужно явно указывать на проектную
# /home/nsa/Karaoke/.docker/ (которая writable).
if [[ -z "${DOCKER_CONFIG:-}" ]]; then
  export DOCKER_CONFIG=/home/nsa/Karaoke/.docker
fi

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BASE_DIR="$(cd "${DEPLOY_DIR}" && cd .. && pwd)"
GRADLE="$BASE_DIR/gradlew"
APP_VERSION="$(cd $BASE_DIR && $GRADLE | grep KaraokeVersion | awk '/':'/{print $2}')"
DOCKER=$(which docker)
COMPOSE=$(which docker-compose)

# .version — абсолютный путь: do.sh может запускаться не из deploy/ (PR #084).
if [[ -z "${BUILD_VERSION}" ]]; then
  BUILD_VERSION="$(touch "${DEPLOY_DIR}/.version" && cat "${DEPLOY_DIR}/.version")"
  if [[ -z "${BUILD_VERSION}" ]]; then
    echo "${APP_VERSION}" >"${DEPLOY_DIR}/.version"
  fi
else
  echo "${BUILD_VERSION}" >"${DEPLOY_DIR}/.version"
fi

source "${DEPLOY_DIR}/do.env"
set +a

# GPU-override для karaoke-app подключается только при ENABLE_APP_GPU=1 (см. do.env и
# docker-compose-app.gpu.yml) — на dev-машинах без nvidia passthrough держите его в 0.
APP_GPU_COMPOSE_FILE=""
if [[ "${ENABLE_APP_GPU:-1}" == "1" ]]; then
  APP_GPU_COMPOSE_FILE="-f ${DEPLOY_DIR}/docker-compose-app.gpu.yml"
fi

# Очередь/взаимное исключение gradle-сборок (см. build-lock.sh)
source "${DEPLOY_DIR}/build-lock.sh"
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

# Pass 540 (issue #286): логин в НАШ registry.
# Было: логин БЕЗ указания сервера — уходил в Docker Hub, а имя пользователя
# подставлялось из DOCKER_REGISTRY (это namespace Hub `svoemestodev`, не хост);
# пароль при этом попадал в argv и был виден в `ps`.
# Стало: сервер указывается явно (REGISTRY_HOST), пароль подаётся через stdin.
# Значения живут в do.env (вне git), шаблон — prod-single-host/do.env.example.
function do_registry_login() {
  if [[ -z "${REGISTRY_HOST:-}" || -z "${REGISTRY_USER:-}" ]]; then
    echo "ERROR: REGISTRY_HOST/REGISTRY_USER не заданы в do.env — логин в registry невозможен" >&2
    return 1
  fi
  printf '%s' "${REGISTRY_PASSWORD:-}" \
    | ${DOCKER} login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" --password-stdin
}

# Pass 540 (issue #286): окно dual-push (FR-015, решение владельца #280).
# DUAL_PUSH=1 — публикуем И в наш registry, И в Docker Hub (namespace-архив для отката,
#   максимум до двух успешных прод-деплоев или месяц);
# DUAL_PUSH=0 — только наш registry (SC-008: доля внешних реестров в пути доставки — ноль).
# Дефолт 0: молчаливый push в Hub без явного DUAL_PUSH=1 недопустим.
# Если Hub недоступен — это WARN, а не падение: наш push уже состоялся, прод деплоится.
DUAL_PUSH="${DUAL_PUSH:-0}"
DOCKER_HUB_USER="${DOCKER_HUB_USER:-svoemestodev}"
DOCKER_HUB_PASSWORD="${DOCKER_HUB_PASSWORD:-${DOCKER_PASSWORD:-}}"

function do_hub_login() {
  [[ "$DUAL_PUSH" == "1" ]] || return 0
  printf '%s' "${DOCKER_HUB_PASSWORD}" \
    | ${DOCKER} login docker.io -u "${DOCKER_HUB_USER}" --password-stdin >/dev/null 2>&1
}

# Зеркалит уже собранный образ в Docker Hub (только в окне dual-push).
# $1 — имя образа, $2 — тег.
function do_push_hub() {
  [[ "$DUAL_PUSH" == "1" ]] || return 0
  local name="$1" tag="$2"
  if ! ${DOCKER} tag "${DOCKER_REGISTRY}/${name}:${tag}" "${DOCKER_HUB_USER}/${name}:${tag}"; then
    echo "WARN: не удалось проставить тег Hub для ${name}:${tag}" >&2
    return 0
  fi
  if ! ${DOCKER} push "${DOCKER_HUB_USER}/${name}:${tag}" >/dev/null 2>&1; then
    echo "WARN: push в Docker Hub не прошёл (${name}:${tag}) — окно dual-push, деплой не блокируем" >&2
  fi
}

function do_build() {
  bl_begin karaoke-web "build" karaoke-app karaoke-web; local rc=$?
  [ "$rc" = 10 ] && return 0
  build_jars
  build_images
  bl_commit
  bl_release
}

  # Pass 540 (issue #286): провал сборки ОБЯЗАН давать ненулевой код.
  # Раньше функции заканчивались вызовом announce, который съедал статус,
  # и деплой шёл дальше (push → pull на проде) с несуществующим образом.
  # Контракт: contracts/do-sh-cli.md § 4 — деплой успешен, только если шаг вернул 0.
function build_jars() {
  echo "Building jars"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar karaoke-web:bootJar --parallel || return $?
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
   -f $DEPLOY_DIR/karaoke-app/Dockerfile || return $?

  ${DOCKER} image build $BASE_DIR/karaoke-web/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-web/Dockerfile || return $?

  announce "Все модули собраны" "Все м+одули с+обраны"
}

function build_start_app() {
  echo "Building APP module and start"
  do_build_app || return $?
  do_start
}

function build_start_web() {
  echo "Building WEB module and start"
  do_build_web || return $?
  do_start_web
}

function build_start_webvue3() {
  echo "Building WEBVUE3 module and start"
  do_build_webvue3 || return $?
  do_start_webvue3
}

function build_start_public() {
  echo "Building PUBLIC module and start"
  do_build_public || return $?
  do_start_public
}

function do_build_app() {

  bl_begin karaoke-app "build_app"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building APP module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar || return $?
  cp $DEPLOY_DIR/karaoke-app/files/* "${BASE_DIR}/karaoke-app/build/libs/"
  ${DOCKER} image build $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile || return $?
  bl_commit
  bl_release
  # Бажное использование --no-daemon в GRADLE приводило к OOM (Kotlin 2.2 не компилируется
  # с дефолтным heap ~512 МБ gradle wrapper'а — хватает только daemon'у, у которого heap=2G по gradle.properties).
  # Фича 135: --no-daemon убран из GRADLE.
  announce "Сборка karaoke-app" "Бэк адм+инки с+обран"
}

function do_build_app_nocache() {

  # nocache — осознанная принудительная пересборка, пропуск по отпечатку отключаем
  FORCE=1 bl_begin karaoke-app "build_app_nocache"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building APP module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-app:bootJar || return $?
  cp $DEPLOY_DIR/karaoke-app/files/* "${BASE_DIR}/karaoke-app/build/libs/"
  ${DOCKER} image build --no-cache $BASE_DIR/karaoke-app/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/Dockerfile || return $?
  bl_commit
  bl_release
  announce "Сборка karaoke-app без кеша" "Бэк адм+инки с+обран без к+еша"
}

function do_build_demucs() {
  echo "Building DEMUCS module"
  # Pass 540 (FR-008): плавающий тег `latest` недопустим — образ тегируется версией сборки.
  ${DOCKER} image build $DEPLOY_DIR/karaoke-app/files/ \
   -t "$DOCKER_REGISTRY/demucs:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-app/DockerfileDemucs || return $?
  announce "Сборка demucs" "М+одуль дем+укс с+обран"
}

function do_build_web() {

  bl_begin karaoke-web "build_web"; local rc=$?
  [ "$rc" = 10 ] && return 0
  echo "Building WEB module"
  cd ${BASE_DIR} && ${GRADLE} clean karaoke-web:bootJar || return $?
  ${DOCKER} image build $BASE_DIR/karaoke-web/build/libs/ \
   --build-arg VERSION=${BUILD_VERSION} \
   --build-arg APP_VERSION=${APP_VERSION} \
   -t "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}" \
   -f $DEPLOY_DIR/karaoke-web/Dockerfile || return $?
  bl_commit
  bl_release
  announce "Сборка karaoke-web" "Бэк с+айта с+обран"
}

# Сборка фронтенд-образа с ретраями и переопределением базовых образов.
# OpenProject #327: таймауты npm/registry не должны ронять деплой.
# NODE_IMAGE/NGINX_IMAGE (env) пробрасываются в Dockerfile; FRONTEND_BUILD_ATTEMPTS
# задаёт число попыток (по умолчанию 3). Повтор — только при сетевой ошибке.
function do_build_frontend_image() {
  local image_name="$1"
  local dockerfile="$2"
  local attempts="${FRONTEND_BUILD_ATTEMPTS:-3}"
  local base_args=()
  local log
  local attempt=1
  local rc=0

  if ! [[ "${attempts}" =~ ^[0-9]+$ ]] || [[ "${attempts}" -lt 1 ]]; then
    attempts=3
  fi

  if [[ -n "${NODE_IMAGE:-}" ]]; then base_args+=(--build-arg "NODE_IMAGE=${NODE_IMAGE}"); fi
  if [[ -n "${NGINX_IMAGE:-}" ]]; then base_args+=(--build-arg "NGINX_IMAGE=${NGINX_IMAGE}"); fi

  log="$(mktemp)"
  while [[ "${attempt}" -le "${attempts}" ]]; do
    echo "Building ${image_name} image (attempt ${attempt}/${attempts})"
    ${DOCKER} image build "$BASE_DIR/" \
      --build-arg VERSION="${BUILD_VERSION}" \
      "${base_args[@]}" \
      -t "$DOCKER_REGISTRY/${image_name}:${BUILD_VERSION}" \
      -f "${dockerfile}" 2>&1 | tee "${log}"
    rc=${PIPESTATUS[0]}
    if [[ "${rc}" -eq 0 ]]; then
      rm -f "${log}"
      return 0
    fi
    if ! grep -qiE 'ETIMEDOUT|timeout awaiting response headers|failed to do request|Temporary failure in name resolution|Could not resolve host|i/o timeout|TLS handshake timeout|connection reset by peer|unexpected EOF|npm error network' "${log}"; then
      echo "Сборка ${image_name} упала не по сети — повтор не выполняется" >&2
      rm -f "${log}"
      return "${rc}"
    fi
    attempt=$((attempt + 1))
    if [[ "${attempt}" -le "${attempts}" ]]; then
      local delay=$((attempt * 10))
      echo "Сетевая ошибка; повтор через ${delay}с..." >&2
      sleep "${delay}"
    fi
  done
  rm -f "${log}"
  return "${rc}"
}

function do_build_webvue3() {
  echo "Building WEBVUE3 module"
  do_build_frontend_image "karaoke-webvue3" "$DEPLOY_DIR/karaoke-webvue3/Dockerfile" || return $?
  announce "Сборка karaoke-webvue3" "Фронт адм+инки с+обран"
}

function do_build_public() {
  echo "Building PUBLIC module"
  do_build_frontend_image "karaoke-public" "$DEPLOY_DIR/karaoke-public/Dockerfile" || return $?
  announce "Сборка karaoke-public" "Фронт с+айта с+обран"
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
  announce "Все контейнеры остановлены" "Все конт+эйнеры остан+овлены"
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
  announce "DATABASE остановлен" "Б+аза д+анных остан+овлена"
}

function do_start_web() {
  do_stop_web
  echo "Старт WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml up -d
  announce "karaoke-web запущен" "Бэк с+айта зап+ущен"
}

function do_stop_web() {
  echo "Остановка WEB"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml down
  announce "karaoke-web остановлен" "Бэк с+айта остан+овлен"
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

  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3.yml up -d
  announce "Старт karaoke-webvue3" "Фронт адм+инки зап+ущен"
}

function do_stop_webvue3() {
  echo "Остановка WEBVUE3"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3.yml down
  announce "Остановка karaoke-webvue3" "Фронт адм+инки остан+овлен"
}

function do_start_public() {
  do_stop_public
  echo "Старт PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml up -d
  announce "Старт karaoke-public" "Фронт с+айта зап+ущен"
}

function do_stop_public() {
  echo "Остановка PUBLIC"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-public.yml down
  announce "Остановка karaoke-public" "Фронт с+айта остан+овлен"
}

function do_start_app() {
  echo "Старт APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml ${APP_GPU_COMPOSE_FILE} up -d
  announce "Старт karaoke-app" "Бэк адм+инки зап+ущен"
}

function do_start_app2() {
  echo "Старт APP"
  announce "Старт karaoke-app" "Бэк адм+инки запуск+ается"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml ${APP_GPU_COMPOSE_FILE} up
}

function do_stop_app() {
  echo "Остановка APP"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml down
  announce "Остановка karaoke-app" "Бэк адм+инки остан+овлен"
}

function do_push() {
  echo "Pushing images"
  do_registry_login
  do_hub_login
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  do_push_hub karaoke-app "${BUILD_VERSION}"
  do_push_hub karaoke-web "${BUILD_VERSION}"
  do_push_hub karaoke-webvue3 "${BUILD_VERSION}"
  announce "Pushing!"
}

function do_push_app() {
  echo "Pushing APP"
  do_registry_login
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-app:${BUILD_VERSION}"
  do_push_hub karaoke-app "${BUILD_VERSION}"
  announce "П+ушинг апп"
}

function do_push_demucs() {
  echo "Pushing DEMUCS"
  do_registry_login
  ${DOCKER} image push "$DOCKER_REGISTRY/demucs:${BUILD_VERSION}"
  do_push_hub demucs "${BUILD_VERSION}"
  announce "П+ушинг дем+укс"
}

function do_push_web() {
  echo "Pushing WEB"
  do_registry_login
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-web:${BUILD_VERSION}"
  do_push_hub karaoke-web "${BUILD_VERSION}"
  announce "П+ушинг веб"
}

function do_push_webvue3() {
  echo "Pushing WEBVUE3"
  do_registry_login
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  do_push_hub karaoke-webvue3 "${BUILD_VERSION}"
  announce "П+ушинг вебвь+ю"
}

function do_push_public() {
  echo "Pushing PUBLIC"
  do_registry_login
  ${DOCKER} image push "$DOCKER_REGISTRY/karaoke-public:${BUILD_VERSION}"
  do_push_hub karaoke-public "${BUILD_VERSION}"
  announce "П+ушинг п+аблик"
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
  ${DOCKER} image rm "$DOCKER_REGISTRY/karaoke-webvue3:${BUILD_VERSION}"
  announce "Removing!"
}

function do_ps() {
  echo "Listing containers"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose.yml -f $DEPLOY_DIR/docker-compose-database.yml ps
}

function do_pull_app() {
  echo "Pulling APP"
  do_registry_login
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-app.yml pull
  announce "П+уллинг апп"
}

function do_pull_web() {
  echo "Pulling WEB"
  do_registry_login
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-web.yml pull
  announce "П+уллинг вэб"
}

function do_pull_webvue() {
  echo "Pulling WEBVUE"
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue.yml pull
  announce "П+уллинг вэбь+ю"
}

function do_pull_webvue3() {
  echo "Pulling WEBVUE3"
  do_registry_login
  ${COMPOSE} -f $DEPLOY_DIR/docker-compose-webvue3.yml pull
  announce "П+уллинг вэбь+ю"
}

function do_restore_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U postgres --file="/dumps/karaoke_dump.sql" karaoke'
  announce "Restore DB!"
}

function do_create_clear_db() {
  do_start_db
  echo "Создание базы данных из бекапа в контейнере karaoke-db..."
  ${DOCKER} exec -it karaoke-db bash -c 'psql -U postgres --file="/dumps/karaoke_clear_dump.sql" -d postgres'
  announce "Create clear DB!"
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
build_webvue3) do_build_webvue3 ;;
build_public) do_build_public ;;
build_start_app) build_start_app ;;
build_start_web) build_start_web ;;
build_start_webvue3) build_start_webvue3 ;;
build_start_public) build_start_public ;;
images) build_images ;;
start) do_start ;;
start_db) do_start_db ;;
start_app) do_start_app ;;
start_app2) do_start_app2 ;;
start_web) do_start_web ;;
start_webvue3) do_start_webvue3 ;;
start_public) do_start_public ;;
stop) do_stop ;;
stop_db) do_stop_db ;;
stop_app) do_stop_app ;;
stop_web) do_stop_web ;;
stop_webvue3) do_stop_webvue3 ;;
stop_public) do_stop_public ;;
load) do_load ;;
push) do_push ;;
push_app) do_push_app ;;
push_demucs) do_push_demucs ;;
push_web) do_push_web ;;
push_webvue3) do_push_webvue3 ;;
push_public) do_push_public ;;
pull) do_pull ;;
ps) do_ps ;;
rmi) do_rmi ;;
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
    build_demucs - builds demucs image (GPU/CUDA-enabled), tagged \$DOCKER_REGISTRY/demucs:\$BUILD_VERSION
    push_demucs - pushes demucs image to DOCKER_REGISTRY
    build_web - builds karaoke-web image
    build_start_app - builds karaoke-app image and (re)starts containers
    build_start_web - builds karaoke-web image and (re)starts containers
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
