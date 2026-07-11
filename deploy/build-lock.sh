#!/usr/bin/env bash
# build-lock.sh — взаимное исключение gradle-сборок jar между параллельными сессиями
# и ручными сборками на хосте, плюс пропуск сборки при неизменном отпечатке исходников.
#
# Подключается через `source` из deploy/do.sh. Публичный интерфейс:
#   bl_begin  <check_module> <label> [stamp_module ...]  — ждёт освобождения, сверяет отпечаток,
#                                                           возвращает 10 = «пропустить», 0 = «собирать»
#   bl_commit                                             — фиксирует отпечаток(и) после успешной сборки
#   bl_release                                            — снимает лок (идемпотентно; висит и на trap EXIT)
#   bl_fingerprint <module>                               — печатает sha256 входов сборки модуля
#
# Модули: karaoke-app | karaoke-web. Отпечаток web — надмножество app (web-jar бандлит app).
# FORCE=1 (env) или флаг --force (обрабатывается в do.sh) отключает пропуск, но НЕ отключает лок/ожидание.

# Каталоги вычисляем от расположения самого файла — независимо от вызывающего.
BL_DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
BL_BASE_DIR="$(cd "${BL_DEPLOY_DIR}" && cd .. && pwd)"
BL_DIR="${BL_BASE_DIR}/.build"
BL_LOCK="${BL_DIR}/jars.lock"
BL_INFO="${BL_DIR}/current.info"

BL_FD=""
BL_PENDING=()

bl_fingerprint() {
  local module="$1"
  local -a paths=(
    "${BL_BASE_DIR}/settings.gradle.kts"
    "${BL_BASE_DIR}/build.gradle.kts"
    "${BL_BASE_DIR}/karaoke-app/src"
    "${BL_BASE_DIR}/karaoke-app/build.gradle.kts"
    "${BL_DEPLOY_DIR}/karaoke-app/Dockerfile"
    "${BL_DEPLOY_DIR}/karaoke-app/files"
  )
  if [ "$module" = "karaoke-web" ]; then
    paths+=(
      "${BL_BASE_DIR}/karaoke-web/src"
      "${BL_BASE_DIR}/karaoke-web/build.gradle.kts"
      "${BL_DEPLOY_DIR}/karaoke-web/Dockerfile"
    )
  fi
  local -a existing=()
  local p
  for p in "${paths[@]}"; do [ -e "$p" ] && existing+=("$p"); done
  local ver="${BUILD_VERSION:-$(cat "${BL_DEPLOY_DIR}/.version" 2>/dev/null)}"
  {
    if [ "${#existing[@]}" -gt 0 ]; then
      find "${existing[@]}" -type f -print0 | sort -z | xargs -0 sha256sum
    fi
    echo "BUILD_VERSION=${ver}"
  } | sha256sum | awk '{print $1}'
}

bl_release() {
  [ -n "${BL_FD:-}" ] || return 0
  # убрать незафиксированные pending (сборка не дошла до bl_commit)
  local m
  for m in "${BL_PENDING[@]}"; do rm -f "${BL_DIR}/${m}.pending" 2>/dev/null; done
  flock -u "$BL_FD" 2>/dev/null
  eval "exec ${BL_FD}>&-" 2>/dev/null
  rm -f "$BL_INFO" 2>/dev/null
  unset BL_LOCK_HELD
  BL_FD=""
  BL_PENDING=()
}

bl_commit() {
  local m
  for m in "${BL_PENDING[@]}"; do
    [ -f "${BL_DIR}/${m}.pending" ] && mv -f "${BL_DIR}/${m}.pending" "${BL_DIR}/${m}.stamp"
  done
}

bl_begin() {
  local check_module="$1" label="$2"
  shift 2 || true
  local -a stamp_modules=("$@")
  [ "${#stamp_modules[@]}" -gt 0 ] || stamp_modules=("$check_module")

  mkdir -p "$BL_DIR"
  eval "exec {BL_FD}>\"$BL_LOCK\""

  # (1) flock — координация всех сборок через обёртку (сессии + ручной do.sh)
  local waited=0
  while ! flock -n "$BL_FD"; do
    if [ $((waited % 15)) -eq 0 ]; then
      echo "⏳ Идёт сборка в другом процессе: $(cat "$BL_INFO" 2>/dev/null). Жду освобождения…"
    fi
    sleep 5; waited=$((waited + 5))
  done

  # (2) проба стороннего сырого gradle-билда на хосте (минует flock)
  #     активный лаунчер держит в argv gradle-wrapper.jar; простаивающий демон (GradleDaemon) — нет
  while pgrep -f 'gradle-wrapper\.jar' >/dev/null 2>&1; do
    echo "⏳ Активна сторонняя gradle-сборка (gradle-wrapper.jar). Жду её завершения…"
    sleep 5
  done

  # с этого момента мы — единственная сборка
  printf 'pid=%s host=%s time=%s target=%s\n' "$$" "$(hostname)" "$(date '+%F %T')" "$label" > "$BL_INFO"
  # маркер для guard в gradlew: дочерний gradle (…bootJar) не должен повторно брать этот же лок → дедлок
  export BL_LOCK_HELD=1
  trap 'bl_release' EXIT

  # (3) сверка отпечатка исходников с последней успешной сборкой
  BL_PENDING=("${stamp_modules[@]}")
  local fp_now; fp_now="$(bl_fingerprint "$check_module")"
  local stamp="${BL_DIR}/${check_module}.stamp"
  if [ "${FORCE:-0}" != "1" ] && [ -f "$stamp" ] && [ "$fp_now" = "$(cat "$stamp")" ]; then
    echo "✅ Исходники '${check_module}' не менялись с последней успешной сборки — пропускаю. (FORCE=1 или --force для принудительной пересборки.)"
    bl_release
    return 10
  fi

  # зафиксировать pending-отпечатки СЕЙЧАС (то, что реально пойдёт в компиляцию),
  # чтобы правки во время сборки не «прописались» в stamp как собранные
  local m
  for m in "${stamp_modules[@]}"; do bl_fingerprint "$m" > "${BL_DIR}/${m}.pending"; done

  echo "🔒 Лок сборки захвачен (${check_module}, pid=$$)."
  return 0
}
