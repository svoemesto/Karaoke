#!/usr/bin/env bash
# =====================================================================
# tools/registry-vendor.sh — перенос сторонних контейнерных образов в наш
# registry по карте пинов `deploy/prod-single-host/registry/images.lock`
# (спека 540 / issue #286; FR-008 — фиксация версий, FR-018 — служебный namespace).
#
# Использование:
#   bash tools/registry-vendor.sh [--host HOST:PORT] [--lock FILE]
#                                 [--scheme https|http] [--only SUBSTR]
#                                 [--verify] [--update-lock] [--dry-run]
#
# Режимы:
#   (по умолчанию)  вендоринг: pull upstream → tag → push в наш registry;
#                   при --update-lock записывает дайджесты в lock-файл;
#   --verify        ничего не пушит: сверяет дайджест манифеста в registry
#                   с записью в lock-файле (exit 1 при расхождении);
#   --dry-run       показывает план, ничего не делает.
#
# --host подменяет хост в `our_ref` (например, `--host localhost:5500` для стенда).
# Дайджест манифеста от хоста не зависит, поэтому пины, снятые на стенде, годны и для прода.
#
# Креды: REGISTRY_USER/REGISTRY_PASSWORD из окружения или из do.env.
# Секреты НЕ печатаются и НЕ передаются через argv (`-u user:pass` виден в
# `ps -eo args` любому пользователю машины) — curl получает их через
# stdin-конфиг `--config -`. Docker — с DOCKER_CONFIG проекта (Pass 373).
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCK_DEFAULT="$REPO_ROOT/deploy/prod-single-host/registry/images.lock"
LOCK="$LOCK_DEFAULT"
HOST_OVERRIDE=""
SCHEME="https"
ONLY=""
MODE="vendor"
UPDATE_LOCK=0
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)        HOST_OVERRIDE="$2"; shift 2 ;;
    --lock)        LOCK="$2"; shift 2 ;;
    --scheme)      SCHEME="$2"; shift 2 ;;
    --only)        ONLY="$2"; shift 2 ;;
    --verify)      MODE="verify"; shift ;;
    --update-lock) UPDATE_LOCK=1; shift ;;
    --dry-run)     DRY_RUN=1; shift ;;
    -h|--help)     sed -n '3,20p' "$0"; exit 0 ;;
    *)             echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -f "$LOCK" ]] || { echo "ERROR: lock-файл не найден: $LOCK" >&2; exit 2; }
# BR-581: DOCKER_CONFIG — из корня репозитория (общий кэш главного checkout'а),
# а не зашитый путь одной машины. Переопределение — KARAOKE_DOCKER_CONFIG.
if [[ -z "${DOCKER_CONFIG:-}" ]]; then
  _main_root="$REPO_ROOT"
  _common_dir="$(git -C "$REPO_ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
  if [[ -n "$_common_dir" && -d "$_common_dir" ]]; then
    _main_root="$(dirname "$_common_dir")"
  fi
  export DOCKER_CONFIG="${KARAOKE_DOCKER_CONFIG:-$_main_root/.docker}"
  unset _main_root _common_dir
fi

find_doenv() {
  for f in "$PWD/do.env" "$PWD/deploy/prod-single-host/do.env" \
           "$REPO_ROOT/deploy/prod-single-host/do.env" "$REPO_ROOT/deploy/do.env"; do
    [[ -f "$f" ]] && { printf '%s' "$f"; return 0; }
  done
  return 1
}
env_val() { sed -n "s/^$1=//p" "$2" | tail -1 | tr -d ' \r'; }

# BR-594: креды не должны попадать в argv curl (`-u user:pass` виден в
# `ps -eo args`). Передаём их через stdin-конфиг curl (`--config -`).
cfg_escape() { # экранирование значения для curl --config (кавычки и слэши)
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/ }"
  printf '%s' "$s"
}

USER_CRED="${REGISTRY_USER:-}"
PASS_CRED="${REGISTRY_PASSWORD:-}"
DOENV=""
if [[ -z "$USER_CRED" || -z "$PASS_CRED" ]] && DOENV="$(find_doenv)"; then
  [[ -z "$USER_CRED" ]] && USER_CRED="$(env_val REGISTRY_USER "$DOENV")"
  [[ -z "$PASS_CRED" ]] && PASS_CRED="$(env_val REGISTRY_PASSWORD "$DOENV")"
fi

# Логин в целевой registry ОБЯЗАТЕЛЕН и выполняется самим инструментом:
# `docker push` берёт креды из $DOCKER_CONFIG/config.json, а там на один хост
# приходится ОДНА запись — чужой `docker login` молча вытеснит нашу и push
# упадёт с «requested access to the resource is denied». Проверено на стенде
# 2026-10-11: три образа залились, остальные — нет, ровно после логина другого
# пользователя в тот же $DOCKER_CONFIG.
registry_login() { # $1 = host:port
  local host="$1"
  [[ "$MODE" == "verify" ]] && return 0
  [[ "$DRY_RUN" == "1" ]] && return 0
  [[ -n "$USER_CRED" && -n "$PASS_CRED" ]] || return 0
  printf '%s' "$PASS_CRED" | docker login "$host" -u "$USER_CRED" --password-stdin >/dev/null 2>&1
}

# --- разбор lock-таблицы -------------------------------------------------
# Строки вида: | upstream_ref | our_ref | digest | checked_at |
read_lock() {
  awk -F'|' '
    /^\|/ {
      u=$2; o=$3; d=$4; c=$5
      gsub(/^[ \t]+|[ \t]+$/,"",u); gsub(/^[ \t]+|[ \t]+$/,"",o)
      gsub(/^[ \t]+|[ \t]+$/,"",d); gsub(/^[ \t]+|[ \t]+$/,"",c)
      if (u ~ /^(upstream_ref|---)/ || o ~ /^---/ || o == "") next
      if (u !~ /\//) next            # у upstream_ref обязан быть хост/путь (отсекает строки истории)
      if (o !~ /\//) next            # в our_ref обязан быть namespace/repo
      print u "\t" o "\t" d "\t" c
    }' "$LOCK"
}

host_of() { printf '%s' "${1%%/*}"; }                 # host:port из our_ref
path_of() { printf '%s' "${1#*/}"; }                  # namespace/repo[:tag]
rehost()  { # $1=our_ref  → с подменённым хостом
  local ref="$1"
  [[ -z "$HOST_OVERRIDE" ]] && { printf '%s' "$ref"; return; }
  printf '%s/%s' "$HOST_OVERRIDE" "$(path_of "$ref")"
}
manifest_digest() { # $1=host:port  $2=<namespace/repo>:<tag>  → дайджест из registry
  local h="$1" ref="$2" repo tag out
  # ВАЖНО: тег в URL НЕ входит в путь репозитория — иначе получается
  # /v2/mirror/x/y:tag/manifests/tag и «манифест не читается» (проверено).
  repo="${ref%%:*}"
  tag="${ref##*:}"
  # Креды — через stdin-конфиг curl, не через argv (см. cfg_escape).
  out="$(printf 'user = "%s:%s"\n' "$(cfg_escape "$USER_CRED")" "$(cfg_escape "$PASS_CRED")" \
        | curl -sS --max-time 30 --config - -I \
        -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.oci.image.index.v1+json' \
        "$SCHEME://$h/v2/$repo/manifests/$tag" 2>/dev/null \
        | tr -d '\r' | awk -F': ' 'tolower($1)=="docker-content-digest"{print $2}')"
  printf '%s' "$out"
}

if [[ "$MODE" != "verify" && "$DRY_RUN" != "1" && ( -z "$USER_CRED" || -z "$PASS_CRED" ) ]]; then
  echo "ERROR: нет REGISTRY_USER/REGISTRY_PASSWORD (окружение или do.env) — push невозможен." >&2
  echo "       Раньше инструмент молча полагался на $DOCKER_CONFIG/config.json — это давало" >&2
  echo "       'requested access to the resource is denied' после чужого docker login." >&2
  exit 2
fi

PASS_CNT=0; FAIL_CNT=0; SKIP_CNT=0
ok()   { printf '  \033[0;32mOK\033[0m    %s\n' "$1"; PASS_CNT=$((PASS_CNT+1)); }
bad()  { printf '  \033[0;31mFAIL\033[0m  %s\n' "$1"; FAIL_CNT=$((FAIL_CNT+1)); }
skip() { printf '  \033[0;33mSKIP\033[0m  %s\n' "$1"; SKIP_CNT=$((SKIP_CNT+1)); }
info() { printf '  %s\n' "$1"; }

UPDATED="$(mktemp)"; trap 'rm -f "$UPDATED"' EXIT
: > "$UPDATED"
LOCK_UPDATED=0

while IFS=$'\t' read -r upstream our_ref digest checked_at; do
  [[ -z "$upstream" ]] && continue
  [[ -n "$ONLY" && "$our_ref" != *"$ONLY"* ]] && continue
  target="$(rehost "$our_ref")"

  if [[ "$MODE" == "verify" ]]; then
    if [[ -z "$USER_CRED" || -z "$PASS_CRED" ]]; then
      skip "$our_ref — нет кредов для сверки с registry"; continue
    fi
    if [[ "$digest" == "PENDING" ]]; then
      # BR-594: "PENDING" в колонке digest означает «пин не зафиксирован»,
      # а не «нечего проверять». Раньше это значение считалось skip — ранее
      # записанный дайджест молча переставал проверяться. Теперь это ошибка
      # проверки: пин надо восстановить вручную.
      bad "$our_ref — пин не зафиксирован (digest=PENDING): прежний дайджест утерян, восстановите пин вручную"
      continue
    fi
    if [[ -z "$digest" ]]; then
      skip "$our_ref — пин ещё не проставлен (digest пуст)"; continue
    fi
    have="$(manifest_digest "$(host_of "$target")" "$(path_of "$target")")"
    if [[ -z "$have" ]]; then
      bad "$our_ref — манифест не читается в registry"
    elif [[ "$have" == "$digest" ]]; then
      ok "$our_ref — дайджест совпал с lock"
    else
      bad "$our_ref — дайджест в registry $have ≠ в lock $digest"
    fi
    continue
  fi

  info "→ $upstream"
  registry_login "$(host_of "$target")"
  if [[ "$DRY_RUN" == "1" ]]; then
    info "  [dry-run] pull → tag $target → push"; continue
  fi
  if ! docker pull "$upstream" >/dev/null 2>&1; then
    # Upstream может исчезнуть (проверено 2026-10-11: quay.io/minio/minio отдаёт 401,
    # репозитория minio/minio нет и в Docker Hub). Если копия уже есть локально —
    # она становится ЕДИНСТВЕННЫМ источником, и это надо не молчать, а сказать.
    if docker image inspect "$upstream" >/dev/null 2>&1; then
      echo "  WARN: upstream $upstream недоступен — использую ЛОКАЛЬНУЮ копию (единственный источник!)" >&2
    else
      bad "$upstream — не удалось скачать, и локальной копии нет"; continue
    fi
  fi
  up_digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$upstream" 2>/dev/null | head -1)"
  if ! docker tag "$upstream" "$target" 2>/dev/null; then
    bad "$target — не удалось проставить тег"; continue
  fi
  if ! docker push "$target" >/dev/null 2>&1; then
    bad "$target — push не прошёл"; continue
  fi
  # Дайджест берём ИЗ REGISTRY (Docker-Content-Digest), а не из `docker image inspect`.
  # Для multi-arch образов локальный RepoDigest — это дайджест ИНДЕКСА, а в registry
  # лежит платформенный манифест; пин по индексу сломал бы pull (`@sha256:` не найден).
  # Проверено 2026-10-11: 5 из 7 записей были записаны неверно.
  new_digest="$(manifest_digest "$(host_of "$target")" "$(path_of "$target")")"
  local_digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$target" 2>/dev/null | head -1)"
  local_digest="${local_digest##*@}"
  up_digest="${up_digest##*@}"
  digest_known=1
  if [[ -z "$new_digest" ]]; then
    # BR-594: дайджест из registry не получен → пин в lock-файле НЕ трогаем.
    # Раньше здесь подставлялось "PENDING", и оно ЗАПИСЫВАЛОСЬ в колонку
    # digest, затирая ранее зафиксированный валидный дайджест (sha256:…).
    # Следующий прогон `--verify` относился к "PENDING" как к skip — то есть
    # пин, который раньше проверялся, молча переставал проверяться
    # (терялась фиксация версий, FR-008).
    skip "$target — залит, но дайджест из registry не получен; пин в lock-файле НЕ изменён"
    digest_known=0
    new_digest=""
  elif [[ -n "$local_digest" && "$new_digest" != "$local_digest" ]]; then
    ok "$target — залит; дайджест registry ($new_digest) ≠ локальный ($local_digest) — норма для multi-arch, в lock идёт registry"
  else
    ok "$target — залит, дайджест сохранён ($new_digest)"
  fi
  printf '%s\t%s\n' "$target" "${new_digest:-<digest-unknown>}" >> "$UPDATED"

  if [[ "$UPDATE_LOCK" == "1" && "$digest_known" == "1" ]]; then
    today="$(date -u +%Y-%m-%d)"
    # обновляем строку lock только для этого our_ref
    python3 - "$LOCK" "$our_ref" "$new_digest" "$today" <<'PY'
import sys, pathlib
lock, ref, dig, today = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
p = pathlib.Path(lock); out = []
for line in p.read_text(encoding='utf-8').splitlines():
    if line.startswith('|') and f'| {ref} |' in line:
        cells = [c.strip() for c in line.strip('|').split('|')]
        cells[2] = dig; cells[3] = today
        line = '| ' + ' | '.join(cells) + ' |'
    out.append(line)
p.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY
    LOCK_UPDATED=$((LOCK_UPDATED + 1))
  elif [[ "$UPDATE_LOCK" == "1" ]]; then
    echo "  WARN: lock-файл НЕ обновлён для $our_ref — прежний пин сохранён (дайджест не получен)" >&2
  fi
done < <(read_lock)

echo
echo "--- итог ($MODE): OK=$PASS_CNT FAIL=$FAIL_CNT SKIP=$SKIP_CNT ---"
if [[ "$UPDATE_LOCK" == "1" && "$MODE" != "verify" ]]; then
  if [[ "$LOCK_UPDATED" -gt 0 ]]; then
    echo "lock-файл обновлён: $LOCK (строк: $LOCK_UPDATED)"
  else
    echo "lock-файл НЕ обновлён: $LOCK (пины сохранены как были)"
  fi
fi
[[ "$FAIL_CNT" -gt 0 ]] && { echo "РЕЗУЛЬТАТ: FAIL"; exit 1; }
echo "РЕЗУЛЬТАТ: OK"
exit 0
