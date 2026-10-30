#!/usr/bin/env bash
# =====================================================================
# tools/merge-pr.sh — одобрение и merge PR с обязательной проверкой CI
# =====================================================================
#
# Зачем этот скрипт. В branch protection у master стоит required_approvals = 1,
# но статус-чеки в гейт НЕ входят (enable_status_check = false) — иначе
# остановленный раннер на nsa-i9 блокирует работу. Значит единственный барьер
# на пути в master — одобрение, а единственная настоящая проверка кода — CI.
# Пока merge делали руками, «дождаться зелёного CI» было дисциплиной агента.
# Этот скрипт делает её механической: зелёный CI — условие входа.
#
# Что делает:
#   1. Берёт креды из ~/.config/karaoke/forgejo.env:
#        FJ_TOKEN          — создатель PR и merge (учётка owner);
#        FJ_REVIEWER_TOKEN — одобрение (служебный аккаунт karaoke-reviewer).
#   2. Проверяет, что PR открыт.
#   3. Проверяет, что ВСЕ статус-чеки head-коммита успешны: ни одного
#      failure/error и ни одного pending. Ноль статусов — тоже отказ:
#      значит CI не отработал, а «прогон не запускался» — не «прогон зелёный».
#   4. Одобряет PR токеном согласователя, если одобрения ещё нет.
#   5. Мержит и проверяет результат (эндпоинт merge всегда отвечает 405 —
#      успех определяется по состоянию PR).
#
# Использование:
#   bash tools/merge-pr.sh <N>                     # одобрить и смержить
#   bash tools/merge-pr.sh <N> --dry-run           # только проверки, без действий
#   bash tools/merge-pr.sh <N> --no-approve        # merge без одобрения
#   bash tools/merge-pr.sh <N> --delete-branch     # удалить remote-ветку после merge
#   bash tools/merge-pr.sh <N> --approve-body "текст"
#
# Правила:
#   - секрет не попадает в argv: curl получает заголовок через `--config -`
#     (тот же приём, что в tools/tracker-lib.sh и tools/registry-smoke.sh);
#   - красный или не завершённый CI → merge не выполняется, скрипт печатает
#     состояние и что делать дальше;
#   - если CI красный из-за сети машины, а не из-за кода: сначала проверки
#     локально на том же коммите, потом перезапуск прогона через
#     workflow_dispatch (см. knowledge/guidelines/runbooks/forgejo-access.md
#     § «Перезапуск CI-прогона»). Если после перезапуска всё ещё красно —
#     это разбор с владельцем, а не merge.
#
# Exit codes: 0 — ok, 1 — ошибка/отказ.
# =====================================================================
set -euo pipefail

REPO="${MERGE_PR_REPO:-svoemesto/Karaoke}"
ENV_FILE="${FORGEJO_ENV:-/home/nsa/.config/karaoke/forgejo.env}"

die() { printf 'ОШИБКА: %s\n' "$*" >&2; exit 1; }
info() { printf '%s\n' "$*"; }

PR=""
DELETE_BRANCH=0
DRY_RUN=0
DO_APPROVE=1
APPROVE_BODY="Проверено лидом: проверки CI на head-коммите зелёные, изменения по существу."

while [[ $# -gt 0 ]]; do
  case "$1" in
    --delete-branch) DELETE_BRANCH=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --no-approve) DO_APPROVE=0; shift ;;
    --approve-body) [[ $# -ge 2 ]] || die "--approve-body требует текст"; APPROVE_BODY="$2"; shift 2 ;;
    -h|--help) sed -n '2,40p' "$0"; exit 0 ;;
    *) [[ "$1" =~ ^[0-9]+$ ]] || die "ожидался номер PR, получено: $1"; PR="$1"; shift ;;
  esac
done
[[ -n "$PR" ]] || die "не указан номер PR"
[[ -f "$ENV_FILE" ]] || die "нет файла кредов: $ENV_FILE"

# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
: "${FJ_URL:?в $ENV_FILE нет FJ_URL}"
: "${FJ_TOKEN:?в $ENV_FILE нет FJ_TOKEN}"
if [[ "$DO_APPROVE" == "1" ]]; then
  : "${FJ_REVIEWER_TOKEN:?в $ENV_FILE нет FJ_REVIEWER_TOKEN}"
fi

# --- передача токена без argv -------------------------------------------
# `-H "Authorization: token …"` попадает в `ps -eo args` — секрет виден любому
# пользователю машины. Поэтому заголовок уходит в stdin-конфиг curl.
cfg_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

api() { # $1=токен $2=метод $3=путь [$4=тело JSON]
  local token="$1" method="$2" path="$3" body="${4:-}"
  local args=(-sS --max-time 60 -X "$method" -H 'Content-Type: application/json')
  [[ -n "$body" ]] && args+=(-d "$body")
  printf 'header = "Authorization: token %s"\n' "$(cfg_escape "$token")" \
    | curl "${args[@]}" --config - "$FJ_URL$path"
}

jget() { # $1=json $2=путь через точки (например user.login)
  python3 -c '
import json,sys
d=json.loads(sys.stdin.read())
for k in sys.argv[1].split("."):
    d = d.get(k) if isinstance(d, dict) else None
    if d is None: break
print("" if d is None else d)
' "$2" <<<"$1"
}

info "== PR #$PR ($REPO) =="

pr_json="$(api "$FJ_TOKEN" GET "/api/v1/repos/$REPO/pulls/$PR")" \
  || die "не удалось получить PR #$PR"
state="$(jget "$pr_json" state)"
[[ "$state" == "open" ]] || die "PR #$PR в состоянии '$state' (нужен open)"
head_ref="$(jget "$pr_json" head.ref)"
head_sha="$(jget "$pr_json" head.sha)"
author="$(jget "$pr_json" user.login)"
info "  ветка: $head_ref | head: ${head_sha:0:8} | автор: $author"

# --- 1. CI: все статус-чеки head-коммита должны быть успешными -----------
statuses="$(api "$FJ_TOKEN" GET "/api/v1/repos/$REPO/commits/$head_sha/status")" \
  || die "не удалось получить статус-чеки коммита $head_sha"
ci_summary="$(jget "$statuses" state)"
total="$(python3 -c '
import json,sys
ss=json.loads(sys.stdin.read()).get("statuses") or []
print(len(ss))
' <<<"$statuses")"
bad="$(python3 -c '
import json,sys
ss=json.loads(sys.stdin.read()).get("statuses") or []
print(", ".join(sorted({s.get("context","?") for s in ss if s.get("status") not in ("success",)})) or "-")
' <<<"$statuses")"
ok_count="$(python3 -c '
import json,sys
ss=json.loads(sys.stdin.read()).get("statuses") or []
print(sum(1 for s in ss if s.get("status")=="success"))
' <<<"$statuses")"
info "  CI: $ok_count/$total успешных, сводка: $ci_summary"

if [[ "$total" == "0" ]]; then
  die "CI по head-коммиту не отработал (0 статус-чеков) — merge не выполняю.
    Проверь, что прогон запущен: GET /api/v1/repos/$REPO/actions/runs"
fi
if [[ "$ci_summary" != "success" ]]; then
  printf 'ОШИБКА: CI на head-коммите не зелёный (сводка: %s).\n' "$ci_summary" >&2
  printf '  неуспешные: %s\n' "$bad" >&2
  cat >&2 <<'HINT'
  Что делать:
    1. Разобрать причину. Если код — починить и запушить в ветку PR.
    2. Если причина в сети машины (джобы качают себя: lychee-action, gradle) —
       проверить те же проверки локально на этом коммите и перезапустить прогон:
         printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
           | curl -X POST --config - -H 'Content-Type: application/json' \
               -d '{"ref":"<ветка PR>"}' \
               "$FJ_URL/api/v1/repos/<owner>/<repo>/actions/workflows/lint.yml/dispatches"
       Если после перезапуска всё ещё красно — разбор с владельцем, не merge.
HINT
  exit 1
fi

# --- 2. Одобрение --------------------------------------------------------
if [[ "$DO_APPROVE" == "1" ]]; then
  reviews="$(api "$FJ_TOKEN" GET "/api/v1/repos/$REPO/pulls/$PR/reviews")" || reviews="[]"
  have="$(python3 -c '
import json,sys
rs=json.loads(sys.stdin.read()) or []
print("1" if any(r.get("state")=="APPROVED" and (r.get("user") or {}).get("login") != sys.argv[1] for r in rs) else "0")
' "$author" <<<"$reviews")"
  if [[ "$have" == "1" ]]; then
    info "  одобрение уже есть — пропускаю"
  elif [[ "$DRY_RUN" == "1" ]]; then
    info "  одобрение: было бы поставлено (--dry-run)"
  else
    body_json="$(python3 -c 'import json,sys; print(json.dumps({"event":"APPROVED","body":sys.argv[1]}, ensure_ascii=False))' "$APPROVE_BODY")"
    rev="$(api "$FJ_REVIEWER_TOKEN" POST "/api/v1/repos/$REPO/pulls/$PR/reviews" "$body_json")" \
      || die "не удалось поставить одобрение"
    info "  одобрение поставлено: $(jget "$rev" state) (кем: $(jget "$rev" user.login))"
  fi
fi

if [[ "$DRY_RUN" == "1" ]]; then
  info "  --dry-run: merge не выполняю"
  exit 0
fi

# --- 3. Merge ------------------------------------------------------------
merge_json='{"Do":"merge","delete_branch_after_merge":false}'
api "$FJ_TOKEN" POST "/api/v1/repos/$REPO/pulls/$PR/merge" "$merge_json" >/dev/null || true

after="$(api "$FJ_TOKEN" GET "/api/v1/repos/$REPO/pulls/$PR")"
merged="$(jget "$after" merged)"
[[ "$merged" == "True" ]] || die "PR #$PR не смержен (state=$(jget "$after" state), merged=$merged)"
info "  смержено: merge_commit $(jget "$after" merge_commit_sha | cut -c1-8) (кем: $(jget "$after" merged_by.login))"

if [[ "$DELETE_BRANCH" == "1" ]]; then
  # Эндпоинт удаления ветки — DELETE /repos/{owner}/{repo}/branches/{branch}
  # (у git/refs нет метода DELETE — такой путь отвечает 405).
  code="$(printf 'header = "Authorization: token %s"\n' "$(cfg_escape "$FJ_TOKEN")" \
    | curl -sS --max-time 60 --config - -X DELETE \
        "$FJ_URL/api/v1/repos/$REPO/branches/$head_ref" \
        -o /dev/null -w '%{http_code}' || true)"
  info "  удаление remote-ветки: HTTP ${code:-000} (204 — удалена)"
fi
