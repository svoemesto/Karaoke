#!/usr/bin/env bash
# =====================================================================
# tools/check-actions-pinned.sh — guard для BR-508 (аудит #336,
# пакет PKG-S3-19, OpenProject #391).
#
# Проблема: сторонние GitHub Actions были закреплены на ПЛАВАЮЩЕМ теге
# (`uses: actions/checkout@v4`). Тег в чужом репозитории можно
# перенаправить на другой коммит — и CI подхватит новый код без
# изменения нашего репозитория и без ревью (supply chain).
#
# Правило: каждый `uses:` в `.github/workflows/*.yml` обязан указывать
# полный SHA коммита (40 hex) с комментарием-версией:
#
#   uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4.4.0
#
# Исключения: локальные actions (`./path`) и `docker://` (образ
# фиксируется тегом/дайджестом в другом месте).
#
# Использование:
#   bash tools/check-actions-pinned.sh              # guard-режим
#   bash tools/check-actions-pinned.sh --quiet      # для pre-commit
#   bash tools/check-actions-pinned.sh --self-test  # самопроверка матчера
#
# Exit codes:
#   0 — все сторонние actions закреплены по SHA
#   1 — найдено нарушение (или self-test не поймал «плохой» пример)
#   2 — usage error
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOWS_DIR="${PROJECT_ROOT}/.github/workflows"
FIXTURES_DIR="${PROJECT_ROOT}/tools/fixtures/actions-pinned"

# Является ли ref полным SHA коммита (40 hex).
is_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]]
}

# Проверить один workflow-файл. Печатает нарушения, возвращает 0/1.
check_file() {
  local file="$1"
  local checked=0
  local violations=0
  local line_no=0
  local line ref sha

  if [[ ! -f "$file" ]]; then
    echo "ERROR: файл '${file}' не найден"
    return 2
  fi

  while IFS= read -r line; do
    line_no=$((line_no + 1))
    # `uses:` может быть ключом шага или элементом списка (`- uses:`).
    if [[ ! "$line" =~ ^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*([^[:space:]#]+) ]]; then
      continue
    fi
    ref="${BASH_REMATCH[2]}"

    # Локальные actions и docker-образы — вне правила.
    case "$ref" in
      ./*|docker://*) continue ;;
    esac

    checked=$((checked + 1))

    if [[ "$ref" != *"@"* ]]; then
      echo "FAIL: ${file}:${line_no} — '${ref}': нет @<sha> (нужно owner/repo@<40 hex> # vX.Y.Z)"
      violations=$((violations + 1))
      continue
    fi

    sha="${ref##*@}"
    if ! is_sha "$sha"; then
      echo "FAIL: ${file}:${line_no} — '${ref}': ref '${sha}' не SHA коммита (нужно 40 hex + комментарий # vX.Y.Z)"
      violations=$((violations + 1))
    fi
  done < "$file"

  if [[ "$checked" -eq 0 ]]; then
    echo "WARN: ${file} — ни одного стороннего uses: (guard ничего не проверил)"
  fi

  [[ "$violations" -eq 0 ]]
}

run_guard() {
  local quiet="$1"
  shift
  local total=0
  local failed=0
  local file

  if [[ "$#" -gt 0 ]]; then
    # Явные файлы — для проверки снимков «до/после» в отчёте.
    :
  else
    if [[ ! -d "$WORKFLOWS_DIR" ]]; then
      echo "ERROR: каталог ${WORKFLOWS_DIR} не найден"
      return 2
    fi
    shopt -s nullglob
    set -- "${WORKFLOWS_DIR}"/*.yml "${WORKFLOWS_DIR}"/*.yaml
  fi

  for file in "$@"; do
    total=$((total + 1))
    if [[ "$quiet" == "yes" ]]; then
      # pre-commit: только код возврата, без деталей.
      check_file "$file" >/dev/null 2>&1 || failed=$((failed + 1))
    else
      check_file "$file" || failed=$((failed + 1))
    fi
  done

  if [[ "$total" -eq 0 ]]; then
    echo "ERROR: в ${WORKFLOWS_DIR} нет workflow-файлов"
    return 2
  fi

  if [[ "$failed" -gt 0 ]]; then
    [[ "$quiet" == "yes" ]] || echo "FAIL: actions не закреплены по SHA в ${failed} из ${total} workflow-файл(ов) (BR-508)"
    return 1
  fi

  [[ "$quiet" == "yes" ]] || echo "OK: все сторонние uses: в ${total} workflow-файл(ах) закреплены по SHA (BR-508)"
  return 0
}

run_self_test() {
  local failed=0

  if [[ ! -f "${FIXTURES_DIR}/before/workflow.yml" || ! -f "${FIXTURES_DIR}/after/workflow.yml" ]]; then
    echo "FAIL: self-test — нет фикстур в ${FIXTURES_DIR}"
    return 1
  fi

  # «До»: тег вместо SHA — guard обязан упасть.
  if check_file "${FIXTURES_DIR}/before/workflow.yml" >/dev/null 2>&1; then
    echo "FAIL: self-test — guard не поймал 'uses: ...@v4' на снимке «до»"
    failed=1
  else
    echo "OK: self-test — снимок «до» (тег) отвергнут"
  fi

  # «После»: SHA — guard обязан пройти.
  if check_file "${FIXTURES_DIR}/after/workflow.yml" >/dev/null 2>&1; then
    echo "OK: self-test — снимок «после» (SHA) принят"
  else
    echo "FAIL: self-test — guard отверг корректный SHA на снимке «после»"
    failed=1
  fi

  return "$failed"
}

case "${1:-}" in
  ""|--guard)
    shift || true
    run_guard "no" "$@"
    exit $?
    ;;
  --quiet)
    shift
    run_guard "yes" "$@"
    exit $?
    ;;
  --self-test)
    run_self_test
    exit $?
    ;;
  --help|-h)
    echo "Usage: bash tools/check-actions-pinned.sh [--quiet] [workflow.yml ...]"
    echo "       bash tools/check-actions-pinned.sh --self-test"
    exit 0
    ;;
  -*)
    echo "Usage: bash tools/check-actions-pinned.sh [--quiet] [workflow.yml ...]"
    exit 2
    ;;
  *)
    run_guard "no" "$@"
    exit $?
    ;;
esac
