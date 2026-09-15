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
# Этот guard проверяет все tracked Dockerfile* в репозитории и валидирует
# теги `FROM nginx:` и `FROM node:` по whitelist'у (см. ниже).
#
# Whitelist (LTS / stable / pinned):
#   nginx:stable, nginx:stable-alpine, nginx:1.27-alpine
#   node:22-alpine, node:20-alpine, node:20-bookworm-slim
#
# Forbidden (см. CLAUDE.md § TOP-10 ловушек):
#   nginx:alpine (без stable), nginx:latest,
#   node:alpine (без version), node:latest, node:lts (без pinned version)
#
# Использование:
#   bash tools/check-docker-image-tags.sh            # guard-режим: pretty-print + exit 1 на violation
#   bash tools/check-docker-image-tags.sh --quiet    # для pre-commit: только exit code
#
# Что проверяет:
#   1) Grep "^FROM nginx:" и "^FROM node:" во всех tracked Dockerfile* файлах.
#   2) Если тег не в whitelist → exit 1 + pretty-print filename:line:image:tag.
#   3) Если все теги в whitelist → exit 0 + "OK".
#   4) Если tracked Dockerfile* файлов нет → exit 0 + "No Dockerfile found".
#
# Exit codes:
#   0 — правило соблюдено (или нет Dockerfile'ов)
#   1 — найдены forbidden FROM теги
#   2 — usage error
# =====================================================================

set -uo pipefail

# Цвета (ANSI).
color_red() { printf '\033[0;31m%s\033[0m\n' "$*"; }
color_green() { printf '\033[0;32m%s\033[0m\n' "$*"; }
color_yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
color_cyan() { printf '\033[0;36m%s\033[0m\n' "$*"; }

# Whitelist разрешённых image:tag (R-04 + R-05 LTS).
# nginx:     stable, stable-alpine, 1.27-alpine
# node:      22-alpine, 20-alpine, 20-bookworm-slim
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

# Поиск тега в whitelist (точное совпадение image:tag).
is_allowed() {
  local image_tag="$1"
  shift
  local allowed
  for allowed in "$@"; do
    if [[ "$image_tag" == "$allowed" ]]; then
      return 0
    fi
  done
  return 1
}

# Извлечь image:tag из FROM-строки.
# Примеры:
#   "FROM nginx:stable AS production-stage"  → "nginx:stable"
#   "FROM node:22-alpine"                    → "node:22-alpine"
#   "FROM nginx:1.27-alpine@sha256:..."      → "nginx:1.27-alpine"
parse_from() {
  local from_line="$1"
  # Убираем ведущее "FROM " и trailing AS <stage> (опционально).
  local stripped="${from_line#FROM }"
  local without_stage="${stripped%% AS *}"
  # Берём первое "слово" (image:tag[@digest]).
  local first_word="${without_stage%% *}"
  # Если есть @digest — отрезаем.
  local without_digest="${first_word%%@*}"
  printf '%s' "$without_digest"
}

check_docker_image_tags() {
  local quiet="${1:-no}"

  # Находим все tracked Dockerfile* (исключая бэкап-файлы, которые
  # в репо уже отключены, но на всякий случай фильтруем по basename).
  local dockerfiles
  dockerfiles=$(git ls-files 2>/dev/null \
    | grep -E '(^|/)Dockerfile[^/]*$' \
    | grep -vE '(DockerfileBackup|DockerfileOld|DockerfileFull|DockerfileDemucs)' \
    || true)

  if [[ -z "$dockerfiles" ]]; then
    if [[ "$quiet" != "yes" ]]; then
      color_yellow "No Dockerfile found (excluding backups/legacy variants)."
    fi
    return 0
  fi

  local violations=0
  local checked=0
  local file
  local from_line
  local image_tag
  local line_no

  for file in $dockerfiles; do
    # grep -Hn '^FROM ' выводит filename:line:content
    while IFS= read -r from_line; do
      # В Git ls-files пути относительные к репо — мы в корне, ОК.
      # from_line имеет формат: filename:lineno:FROM ...
      local stripped="${from_line#*:}"          # lineno:FROM ...
      local lineno="${stripped%%:*}"            # lineno
      local rest="${stripped#*:}"               # FROM ...

      # Проверяем только nginx: и node: (R-04 + R-05).
      if [[ "$rest" =~ ^FROM[[:space:]]+(nginx|node): ]]; then
        checked=$((checked + 1))
        image_tag=$(parse_from "$rest")

        if [[ "$rest" =~ ^FROM[[:space:]]+nginx: ]]; then
          if ! is_allowed "$image_tag" "${ALLOWED_NGINX_TAGS[@]}"; then
            violations=$((violations + 1))
            if [[ "$quiet" != "yes" ]]; then
              color_red "❌ FORBIDDEN: $file:$lineno  $image_tag"
              color_red "   (nginx must be: stable | stable-alpine | 1.27-alpine)"
            fi
          elif [[ "$quiet" != "yes" ]]; then
            color_green "✅ $file:$lineno  $image_tag"
          fi
        elif [[ "$rest" =~ ^FROM[[:space:]]+node: ]]; then
          if ! is_allowed "$image_tag" "${ALLOWED_NODE_TAGS[@]}"; then
            violations=$((violations + 1))
            if [[ "$quiet" != "yes" ]]; then
              color_red "❌ FORBIDDEN: $file:$lineno  $image_tag"
              color_red "   (node must be: 22-alpine | 20-alpine | 20-bookworm-slim)"
            fi
          elif [[ "$quiet" != "yes" ]]; then
            color_green "✅ $file:$lineno  $image_tag"
          fi
        fi
      fi
    done < <(grep -Hn '^FROM ' "$file" 2>/dev/null || true)
  done

  if [[ "$quiet" != "yes" ]]; then
    if [[ $checked -eq 0 ]]; then
      color_yellow "No 'FROM nginx:' or 'FROM node:' found in tracked Dockerfile* (R-04/R-05 N/A)."
    else
      printf '\n'
      color_cyan "Checked $checked FROM nginx:/node: line(s) across $(echo "$dockerfiles" | wc -l) Dockerfile file(s)."
    fi
  fi

  if [[ $violations -gt 0 ]]; then
    if [[ "$quiet" != "yes" ]]; then
      printf '\n'
      color_red "FAIL: $violations forbidden Docker image tag(s) (see CLAUDE.md § TOP-10, architecture-conventions L20-21)"
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
  *)
    color_red "Usage: bash tools/check-docker-image-tags.sh [--quiet]"
    exit 2
    ;;
esac
