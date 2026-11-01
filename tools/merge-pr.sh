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
#   3. Проверяет агрегат CI: статус-чек `ci-summary` — единственный сигнал
#      решения (FR-010/D-05). Все контексты, содержащие 'ci-summary', должны
#      быть 'success'; ни одного такого контекста — отказ (значит, агрегат не
#      отработал: CI не запускался, либо это прогон workflow_dispatch, который
#      статусов не создаёт). Зелёного статуса по КАЖДОЙ проверке из манифеста
#      не требуется: манифест — источник ожидаемого состава для диагностики,
#      а не условие решения.
#      Статус-чеки собираются ПОСТРАНИЧНО и сверяются с X-Total-Count:
#      без параметра limit Forgejo отдаёт 30 контекстов из 33, и усечение
#      скрыло бы красный контекст (SC-006).
#   3a. Пересчитывает применимость правилом tools/ci-checks.py --list по
#      head-коммиту PR: перечень ожидаемых и пропущенных проверок — для
#      диагностики. Если пересчёт невозможен (нет локального коммита, нет
#      данных — код 2), это ОТКАЗ, а не «зелёно»: иначе получается тихий
#      обход гейта.
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
#   bash tools/merge-pr.sh --self-test                # фикстуры гейта (без сети)
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
SELF_TEST=0
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
    --self-test) SELF_TEST=1; shift ;;
    -h|--help) sed -n '2,50p' "$0"; exit 0 ;;
    *) [[ "$1" =~ ^[0-9]+$ ]] || die "ожидался номер PR, получено: $1"; PR="$1"; shift ;;
  esac
done
[[ -n "$PR" || "$SELF_TEST" == "1" ]] || die "не указан номер PR"

# --- self-test гейта: фикстуры без сети -----------------------------------
# Проверяем решение по агрегату ci-summary на выборке БОЛЕЕ 30 контекстов
# (реальный предел Forgejo без limit — 30 из 33; усечение скрыло бы красный
# контекст, SC-006).
read -r -d '' GATE_PY <<'PYEOF_PY' || true
import json
import re
import sys

try:
    statuses = json.load(sys.stdin)
except Exception as exc:
    print(f"ОШИБКА: не удалось разобрать статус-чеки: {exc}", file=sys.stderr)
    sys.exit(1)

if not statuses:
    print("ОШИБКА: CI по head-коммиту не отработал (0 статус-чеков) — merge не выполняю.", file=sys.stderr)
    print("       «прогон не запускался» — не «прогон зелёный».", file=sys.stderr)
    sys.exit(1)

def is_summary(context):
    """Контекст агрегата: 'ci-summary' в любом написании/регистре.

    Forgejo формирует контекст из отображаемого имени джоба
    ('ci / CI summary (applicable checks)'), поэтому сравнение идёт по
    нормализованной форме: буквы/цифры -> '-', регистр не важен.
    """
    norm = re.sub(r"[^a-z0-9]+", "-", (context or "").lower()).strip("-")
    return "ci-summary" in norm


summary = [s for s in statuses if is_summary(s.get("context"))]
skipped = [s for s in statuses if "skipped" in (s.get("description") or "").lower()]

if not summary:
    print(f"ОШИБКА: среди {len(statuses)} статус-чеков нет ни одного контекста "
          f"'ci-summary' — агрегат CI не отработал, merge не выполняю.", file=sys.stderr)
    print("       Возможные причины: CI не запускался на этом коммите; прогон был", file=sys.stderr)
    print("       workflow_dispatch (он не создаёт статус-чеков); или ветка устарела —", file=sys.stderr)
    print("       тогда нужен новый push (или перезапуск прогона).", file=sys.stderr)
    sys.exit(1)

bad = [s for s in summary if (s.get("status") or "") != "success"]
if bad:
    for s in bad:
        print(f"ОШИБКА: агрегат CI красный: контекст '{s.get('context')}' -> "
              f"{s.get('status')} ({s.get('description') or 'без описания'})", file=sys.stderr)
    print("       merge не выполняю.", file=sys.stderr)
    sys.exit(1)

print(f"  агрегат CI: {len(summary)} контекст(ов) ci-summary — все success; "
      f"всего статус-чеков {len(statuses)}, из них пропущенных джобов {len(skipped)}")
sys.exit(0)
PYEOF_PY

status_gate() { # JSON-массив статус-чеков на stdin; 0 = можно, 1 = отказ
  python3 -c "$GATE_PY"
}

if [ "$SELF_TEST" = "1" ]; then
  fail=0
  # фикстура 1: 35 контекстов, агрегат зелёный -> пропускаем
  out="$(python3 - <<'PY' | status_gate 2>&1 2>&1
import json
ctx = [f"ci / job-{i}" for i in range(1, 35)]
ctx.insert(17, "ci / CI summary (applicable checks)")
print(json.dumps([{"context": c, "status": "success", "description": ""} for c in ctx]))
PY
)" && rc=0 || rc=$?
  if [ "$rc" = "0" ]; then echo "ok: 35 контекстов, агрегат зелёный -> merge разрешён"
  else echo "FAIL: 35 контекстов, агрегат зелёный -> ожидался rc=0, получен $rc" >&2; fail=1; fi
  # фикстура 2: 35 контекстов, агрегат красный -> отказ с именем контекста
  out="$(python3 - <<'PY' | status_gate 2>&1 2>&1
import json
ctx = [f"ci / job-{i}" for i in range(1, 35)]
ctx.insert(17, "ci / CI summary (applicable checks)")
print(json.dumps([{"context": c, "status": ("failure" if c.startswith("ci / CI summary") else "success"),
                   "description": ""} for c in ctx]))
PY
)" && rc=0 || rc=$?
  if [ "$rc" = "1" ] && printf '%s' "$out" | grep -q "CI summary"; then
    echo "ok: 35 контекстов, агрегат красный -> отказ с указанием контекста"
  else echo "FAIL: 35 контекстов, агрегат красный -> ожидался отказ с именем контекста (rc=$rc)" >&2; fail=1; fi
  # фикстура 3: 35 контекстов БЕЗ агрегата -> отказ
  out="$(python3 - <<'PY' | status_gate 2>&1 2>&1
import json
ctx = [f"ci / job-{i}" for i in range(1, 36)]
print(json.dumps([{"context": c, "status": "success", "description": ""} for c in ctx]))
PY
)" && rc=0 || rc=$?
  if [ "$rc" = "1" ] && printf '%s' "$out" | grep -q "ci-summary"; then
    echo "ok: 35 контекстов без агрегата -> отказ"
  else echo "FAIL: 35 контекстов без агрегата -> ожидался отказ (rc=$rc)" >&2; fail=1; fi
  # фикстура 4: пустой список -> отказ
  printf '[]' | status_gate >/dev/null 2>&1 && rc=0 || rc=$?
  if [ "$rc" = "1" ]; then echo "ok: 0 статус-чеков -> отказ"
  else echo "FAIL: 0 статус-чеков -> ожидался отказ (rc=$rc)" >&2; fail=1; fi
  if [ "$fail" != "0" ]; then echo "SELF-TEST: red" >&2; exit 1; fi
  echo "SELF-TEST: green"
  exit 0
fi

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

# --- постраничный сбор статус-чеков ---------------------------------------
# Forgejo без параметра limit отдаёт 30 контекстов из 33 (X-Total-Count: 33):
# усечение списка скрыло бы красный контекст (SC-006). Собираем страницы и
# сверяем фактическое число счётчика; недобор — отказ, а не «почти всё зелёное».
fetch_statuses() { # $1 = sha; печатает JSON-массив контекстов; код 3 = недобор
  local sha="$1" page=1 total="" n dir
  dir="$(mktemp -d)"
  : >"$dir/page-000.json"
  while [ "$page" -le 50 ]; do
    printf 'header = "Authorization: token %s"\n' "$(cfg_escape "$FJ_TOKEN")" \
      | curl -sS --max-time 60 -H 'Content-Type: application/json' --config - \
          -D "$dir/hdr" -o "$dir/body" \
          "$FJ_URL/api/v1/repos/$REPO/commits/$sha/status?limit=100&page=$page" \
      || { rm -rf "$dir"; die "не удалось получить статус-чеки коммита $sha"; }
    if [ -z "$total" ]; then
      total="$(awk 'tolower($1)=="x-total-count:" {gsub(/[^0-9]/,"",$2); print $2}' "$dir/hdr" | tail -1)"
    fi
    n="$(python3 -c 'import json,sys
try:
    d=json.load(open(sys.argv[1], encoding="utf-8"))
except Exception:
    print(0); raise SystemExit(0)
print(len(d.get("statuses") or []))' "$dir/body" 2>/dev/null || echo 0)"
    cp "$dir/body" "$dir/page-$(printf '%03d' "$page").json"
    [ "$n" -gt 0 ] || break
    if [ -n "$total" ] && [ "$n" -ge "$total" ]; then break; fi
    page=$((page + 1))
  done
  # KARAOKE-377: сбор статусов вынесен в tools/merge-gate-statuses.py —
  # полнота выборки определяется фактом пагинации, а не сырым X-Total-Count
  # (тот считает записи с дублями от нескольких прогонов одного коммита).
  local more_pages=""
  [ "$page" -gt 50 ] && more_pages="--more-pages"
  python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/merge-gate-statuses.py" \
    --dir "$dir" --total "$total" ${more_pages}
  local rc=$?
  rm -rf "$dir"
  return $rc
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

# --- 1. CI: агрегат ci-summary — единственный сигнал решения ---------------
# FR-010/D-05: решение принимается по агрегату, а не по зелёному статусу каждой
# проверки из манифеста: у пропущенной (неприменимой) проверки статуса «skipped»
# в Forgejo не существует, и требовать её зелёного нельзя. Манифест при этом
# используется для ДИАГНОСТИКИ: перечень ожидаемых и пропущенных проверок.
statuses_json="$(fetch_statuses "$head_sha")" || {
  rc=$?
  if [ "$rc" = "3" ]; then
    die "сбор статус-чеков неполный (X-Total-Count не сошёлся) — merge не выполняю:
    усечение выборки может скрыть красный контекст (SC-006)."
  fi
  die "не удалось получить статус-чеки коммита $head_sha"
}

status_total="$(python3 -c 'import json,sys; print(len(json.loads(sys.stdin.read())))' <<<"$statuses_json")"
info "  собрано статус-чеков: $status_total"

printf '%s' "$statuses_json" | status_gate || {
  cat >&2 <<'HINT'
  Что делать:
    1. Разобрать причину. Если код — починить и запушить в ветку PR.
    2. Если причина в сети машины (джобы качают себя: lychee-action, gradle) —
       проверить те же проверки локально на этом коммите и перезапустить прогон.
       ПЕРЕЗАПУСК ПРОГОНА (создаёт новые статус-чеки):
         printf 'header = "Authorization: token %s"\n' "$FJ_TOKEN" \
           | curl -X POST --config - \
               "$FJ_URL/api/v1/repos/<owner>/<repo>/actions/runs/<run_id>/rerun"
       где <run_id> — ВНУТРЕННИЙ id прогона из GET /actions/runs (не число из
       интерфейса; см. knowledge/guidelines/runbooks/forgejo-access.md).
       Если rerun недоступен — обычный прогон: пустой коммит в ветку PR
       (git commit --allow-empty; git push) — push создаёт статус-чеки.
       ВНИМАНИЕ: прогон workflow_dispatch статус-чеков НЕ создаёт — агрегат
       ci-summary появится только после обычного прогона (push/PR или rerun).
       Если после перезапуска всё ещё красно — разбор с владельцем, не merge.
HINT
  exit 1
}

# --- 1a. пересчёт применимости правилом (fail-closed) ---------------------
# Если пересчёт невозможен (нет локального коммита, нет merge-base, код 2) —
# это ОТКАЗ, а не «зелёно»: иначе получается тихий обход гейта.
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
[[ -n "$REPO_ROOT" ]] || die "не удалось определить корень репозитория — пересчёт применимости невозможен"
git -C "$REPO_ROOT" rev-parse --verify "${head_sha}^{commit}" >/dev/null 2>&1 \
  || die "локального коммита $head_sha нет — пересчёт применимости невозможен (нужен checkout ветки PR)"
base_commit="$(git -C "$REPO_ROOT" merge-base origin/master "$head_sha" 2>/dev/null || true)"
[[ -n "$base_commit" ]] || die "не удалось найти merge-base с origin/master — пересчёт применимости невозможен"
applicable_list="$(python3 "$REPO_ROOT/tools/ci-checks.py" \
      --manifest "$REPO_ROOT/config/ci/checks.yml" \
      --list --base "$base_commit" --head "$head_sha" 2>&1)" && list_rc=0 || list_rc=$?
case "$list_rc" in
  0) ;;
  2) die "правило применимости не выполнено (нет данных, код 2) — merge не выполняю:
    $applicable_list" ;;
  *) die "правило применимости вернуло код $list_rc — merge не выполняю:
    $applicable_list" ;;
esac
applicable_ids="$(awk '$2=="applicable" {print $1}' <<<"$applicable_list" | tr '\n' ' ')"
skipped_ids="$(awk '$2=="not_applicable" {print $1}' <<<"$applicable_list" | tr '\n' ' ')"
info "  применимость по head-коммиту (диагностика, не условие решения):"
info "    применимо:   ${applicable_ids:-—}"
info "    неприменимо: ${skipped_ids:-—}"

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
