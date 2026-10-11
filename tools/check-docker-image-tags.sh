#!/usr/bin/env bash
# =====================================================================
# tools/check-docker-image-tags.sh — guard для правил NON-NEGOTIABLE
# R-04 (nginx:stable) и R-05 (node:22-alpine) из
# knowledge/guidelines/architecture-conventions.md L20-21.
#
# Прецедент (Pass 379, OP #106): правила о тегах Docker-образов были
# разбросаны по CLAUDE.md § TOP-10, architecture-conventions L20-21 и
# AGENTS.md § Ограничения агента. Не было guard-скрипта, который бы
# автоматически ловил `FROM nginx:alpine` или `FROM node:latest` в
# Dockerfile'ах до merge.
#
# Pass 540 (issue #286, FR-014/FR-017/SC-009): имена образов переехали в наш
# registry (`FROM sm-karaoke.ru:5443/library/node:22-alpine`). Старый шаблон
# `^FROM nginx:` такие строки НЕ видел — guard ослеп бы молча и остался зелёным
# (до правки он находил ровно 4 строки в 5 Dockerfile; после смены имён нашёл бы
# 0 и напечатал «N/A», вернув 0). Теперь имя нормализуется: срезается
# registry-хост и порт, отрезается @sha256:, берётся последний компонент пути + тег.
# Плюс режим `--selftest` — постоянная самопроверка матчера (SC-009 требует ловить
# подставной запрещённый тег И ПОСЛЕ смены имён).
#
# Whitelist (LTS / stable / pinned):
#   nginx:stable, nginx:stable-alpine, nginx:1.27-alpine
#   node:22-alpine, node:20-alpine, node:20-bookworm-slim
#
# Forbidden (см. CLAUDE.md § TOP-10 ловушек):
#   nginx:alpine (без stable), nginx:latest, nginx без тега,
#   node:alpine (без version), node:latest, node:lts (без pinned version)
#
# Использование:
#   bash tools/check-docker-image-tags.sh            # guard-режим: pretty-print + exit 1 на violation
#   bash tools/check-docker-image-tags.sh --quiet    # для pre-commit: только exit code
#   bash tools/check-docker-image-tags.sh --selftest # самопроверка матчера (fixtures, диск не читается)
#
# Exit codes:
#   0 — правило соблюдено (или нет Dockerfile'ов)
#   1 — найдены forbidden FROM теги либо нераспознанная nginx/node-строка
#   2 — usage error
# =====================================================================

set -uo pipefail

# Цвета (ANSI).
color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
color_cyan() { printf '\033[0;36m%s\033[0m\n' "$*"; }

# Whitelist разрешённых image:tag (R-04 + R-05 LTS).
ALLOWED_NGINX_TAGS=(
  "nginx:stable"
  "nginx:stable-alpine"
  "nginx:1.27-alpine"
)
ALLOWED_NODE_TAGS=(
  "node:22-alpine"
  "node:20-alpine"
  "node:20-bookworm-slim"
)

is_allowed() {
  local image_tag="$1"; shift
  local allowed
  for allowed in "$@"; do
    [[ "$image_tag" == "$allowed" ]] && return 0
  done
  return 1
}

# Извлечь ссылку на образ из FROM-строки (без `AS <stage>` и без `@digest`).
parse_from() {
  local from_line="$1"
  local stripped="${from_line#FROM }"
  local without_stage="${stripped%% AS *}"
  local first_word="${without_stage%% *}"
  printf '%s' "${first_word%%@*}"
}

# Нормализовать ссылку до "<последний компонент пути>:<тег>".
# Registry-хост (часть до первого `/`, содержащая `.` или `:`) отбрасывается.
#   "nginx:stable"                            → "nginx:stable"
#   "sm-karaoke.ru:5443/library/nginx:stable" → "nginx:stable"
#   "mirror/library/node:22-alpine"           → "node:22-alpine"
normalize_ref() {
  local ref="$1" head
  case "$ref" in
    */*)
      head="${ref%%/*}"
      case "$head" in
        *.*|*:*|localhost) ref="${ref#*/}" ;;
      esac
      ;;
  esac
  printf '%s' "${ref##*/}"
}

# Вердикт по нормализованному имени: allow | deny | n/a (не наш случай).
verdict_for() {
  local norm="$1" base="${1%%:*}"
  case "$base" in
    nginx)
      [[ "$norm" == *:* ]] || { printf 'deny'; return; }   # nginx без тега
      if is_allowed "$norm" "${ALLOWED_NGINX_TAGS[@]}"; then printf 'allow'; else printf 'deny'; fi
      ;;
    node)
      [[ "$norm" == *:* ]] || { printf 'deny'; return; }
      if is_allowed "$norm" "${ALLOWED_NODE_TAGS[@]}"; then printf 'allow'; else printf 'deny'; fi
      ;;
    *) printf 'n/a' ;;
  esac
}

# Похожа ли строка на nginx/node-образ (после нормализации, без тега).
looks_like_watched() {
  local base
  base="$(normalize_ref "$1")"; base="${base%%:*}"
  [[ "$base" == "nginx" || "$base" == "node" ]]
}

# ---- Самопроверка матчера (SC-009) -----------------------------------
run_selftest() {
  local fails=0 total=0
  local cases=(
    "FROM nginx:stable AS production-stage|allow"
    "FROM node:22-alpine|allow"
    "FROM nginx:alpine|deny"
    "FROM node:latest|deny"
    "FROM nginx|deny"
    "FROM sm-karaoke.ru:5443/library/nginx:alpine|deny"
    "FROM sm-karaoke.ru:5443/library/node:latest|deny"
    "FROM sm-karaoke.ru:5443/library/nginx:stable|allow"
    "FROM sm-karaoke.ru:5443/mirror/library/node:20-bookworm-slim AS build|allow"
    "FROM mirror/library/node:22-alpine@sha256:deadbeef AS build|allow"
    "FROM sm-karaoke.ru:5443/library/node:20-alpine|allow"
    "FROM ubuntu:22.04|n/a"
  )
  local c line expected norm got
  for c in "${cases[@]}"; do
    line="${c%|*}"; expected="${c##*|}"
    total=$((total + 1))
    norm="$(normalize_ref "$(parse_from "$line")")"
    got="$(verdict_for "$norm")"
    if [[ "$got" == "$expected" ]]; then
      color_green "  ✅ $line → $norm → $got"
    else
      fails=$((fails + 1))
      color_red "  ❌ $line → $norm → $got (ожидали $expected)"
    fi
  done
  printf '\n'
  if [[ "$fails" -gt 0 ]]; then
    color_red "SELFTEST FAIL: $fails из $total фикстур не совпали — матчер сломан"
    return 1
  fi
  color_green "SELFTEST OK: $total/$total — матчер распознаёт registry-квалифицированные имена"
  return 0
}

# ---- Основная проверка ----------------------------------------------
check_docker_image_tags() {
  local quiet="${1:-no}"
  local dockerfiles
  dockerfiles=$(git ls-files 2>/dev/null \
    | grep -E '(^|/)Dockerfile[^/]*$' \
    | grep -vE '(DockerfileBackup|DockerfileOld|DockerfileFull|DockerfileDemucs)' \
    || true)

  if [[ -z "$dockerfiles" ]]; then
    [[ "$quiet" != "yes" ]] && color_yellow "No Dockerfile found (excluding backups/legacy variants)."
    return 0
  fi

  local violations=0 checked=0 file from_line
  for file in $dockerfiles; do
    while IFS= read -r from_line; do
      local stripped="${from_line#*:}"          # lineno:FROM ...
      local lineno="${stripped%%:*}"
      local rest="${stripped#*:}"               # FROM ...
      local ref norm verdict
      ref="$(parse_from "$rest")"
      norm="$(normalize_ref "$ref")"
      verdict="$(verdict_for "$norm")"

      case "$verdict" in
        allow)
          checked=$((checked + 1))
          [[ "$quiet" != "yes" ]] && color_green "✅ $file:$lineno  $norm"
          ;;
        deny)
          checked=$((checked + 1))
          violations=$((violations + 1))
          if [[ "$quiet" != "yes" ]]; then
            color_red "❌ FORBIDDEN: $file:$lineno  $norm"
            case "${norm%%:*}" in
              nginx) color_red "   (nginx must be: stable | stable-alpine | 1.27-alpine, тег обязателен)" ;;
              node)  color_red "   (node must: 22-alpine | 20-alpine | 20-bookworm-slim, тег обязателен)" ;;
            esac
          fi
          ;;
        n/a)
          # Строка не про nginx/node — но если имя на них похоже, матчер ослеп:
          # это регресс защиты, а не «правило неприменимо».
          if looks_like_watched "$ref"; then
            violations=$((violations + 1))
            [[ "$quiet" != "yes" ]] && \
              color_red "❌ UNRECOGNIZED: $file:$lineno  '$ref' похоже на nginx/node, но не распознано"
          fi
          ;;
      esac
    done < <(grep -Hn '^FROM ' "$file" 2>/dev/null || true)
  done

  if [[ "$quiet" != "yes" ]]; then
    if [[ $checked -eq 0 && $violations -eq 0 ]]; then
      color_yellow "No 'FROM nginx:' or 'FROM node:' found in tracked Dockerfile* — ноль проверяемых строк."
      color_yellow "Если это неожиданно (в репозитории есть nginx/node-образы) — guard ослеп: см. --selftest."
    else
      printf '\n'
      color_cyan "Checked $checked FROM nginx:/node: line(s) across $(echo "$dockerfiles" | wc -l) Dockerfile file(s)."
    fi
  fi

  if [[ $violations -gt 0 ]]; then
    if [[ "$quiet" != "yes" ]]; then
      printf '\n'
      color_red "FAIL: $violations forbidden/unrecognized Docker image tag(s) (см. CLAUDE.md § TOP-10, architecture-conventions L20-21)"
    fi
    return 1
  fi
  if [[ "$quiet" != "yes" ]] && [[ $checked -gt 0 ]]; then
    color_green "OK: all $checked FROM nginx:/node: line(s) are in whitelist."
  fi
  return 0
}

case "${1:-}" in
  --help|-h|"")
    check_docker_image_tags "no"
    exit $?
    ;;
  --quiet)
    check_docker_image_tags "yes"
    exit $?
    ;;
  --selftest)
    run_selftest
    exit $?
    ;;
  *)
    color_red "Usage: bash tools/check-docker-image-tags.sh [--quiet|--selftest]"
    exit 2
    ;;
esac
