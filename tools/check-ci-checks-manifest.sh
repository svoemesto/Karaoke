#!/usr/bin/env bash
# tools/check-ci-checks-manifest.sh
#
# Guard: манифест зон интереса и обе копии CI-конфигурации обязаны быть
# согласованы (FR-012, FR-013, FR-019, R-47 п. 3).
#
# ЧТО ПРОВЕРЯЕТ (инварианты M-1…M-12, см.
# specs/692-selective-ci-checks/contracts/ci-checks-manifest.md):
#   M-1  версия манифеста поддерживается; unknown_paths ∈ {all, none}
#   M-2  id в манифесте ↔ job-id обеих копий — ТОТАЛЬНО, без исключений
#   M-3  title непустой
#   M-4  mode: paths ⇒ paths непустой и evidence непустой
#   M-5  mode: always ⇒ paths пуст
#   M-6  каждый глоб матчит ≥1 tracked-файл («мёртвая зона» → ошибка);
#        проверяется ТОЛЬКО для записей с mode: paths
#   M-7  cost ∈ {heavy, light, info}; gate: false ⇒ cost: info
#   M-8  члены групп существуют; проверка — максимум в одной группе
#   M-9  class: infrastructure ⇒ mode: always, без зон и evidence
#   M-10 if:-условия в двух копиях идентичны по job-id
#   M-11 needs джоба karaoke-public-e2e покрывает все записи с cost: heavy
#   M-12 needs в двух копиях идентичны по job-id (порядок выполнения одинаков)
#
# КОДЫ ВОЗВРАТА (контракт guard'ов проекта):
#   0 — расхождений нет;
#   1 — расхождение найдено (выведены нарушения);
#   2 — проверка не выполнена (нет git, нет PyYAML, нет файлов копий/манифеста).
#       «Нет данных» ≠ «нарушений нет» (FR-014).
#
# ИСПОЛЬЗОВАНИЕ:
#   bash tools/check-ci-checks-manifest.sh [--root <dir>] [--quiet]
#   bash tools/check-ci-checks-manifest.sh --self-test
#
#   --root <dir>  корень дерева с копиями workflow и манифестом (по умолчанию —
#                 корень репозитория); нужен для прогона на фикстурах
#                 tools/fixtures/ci-checks-manifest/.
#   --quiet       только код возврата (для pre-commit).
#   --self-test   прогон фикстур: согласованное дерево → 0; подсунутые нарушения →
#                 ненулевой код; недоступные данные → 2.
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$PROJECT_ROOT"
QUIET=0
SELF_TEST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      ROOT="${2:-}"
      shift 2 || { echo "ERROR: --root требует значение." >&2; exit 2; }
      ;;
    --quiet) QUIET=1; shift ;;
    --self-test) SELF_TEST=1; shift ;;
    -h|--help)
      sed -n '2,40p' "$0"
      exit 0
      ;;
    *)
      echo "ERROR: неизвестный аргумент: $1" >&2
      echo "Usage: $0 [--root <dir>] [--quiet] [--self-test]" >&2
      exit 2
      ;;
  esac
done

if [ "$SELF_TEST" = "1" ]; then
  FIXTURES="$PROJECT_ROOT/tools/fixtures/ci-checks-manifest"
  fail=0
  expect() { # $1=каталог $2=ожидаемый код
    bash "$0" --root "$FIXTURES/$1" --quiet >/dev/null 2>&1
    rc=$?
    if [ "$rc" != "$2" ]; then
      echo "SELF-TEST FAIL: фикстура '$1' → код $rc, ожидался $2" >&2
      fail=1
    else
      echo "ok: фикстура '$1' → код $rc"
    fi
  }
  expect synced 0
  expect missing-check 1
  expect divergent-condition 1
  expect dead-zone 1
  expect no-data 2
  if [ "$fail" != "0" ]; then
    echo "SELF-TEST: red" >&2
    exit 1
  fi
  echo "SELF-TEST: green"
  exit 0
fi

if [ ! -d "$ROOT" ]; then
  echo "ERROR: корень не найден: $ROOT" >&2
  echo "       проверка не выполнена." >&2
  exit 2
fi

OUT="$(python3 - "$ROOT" <<'PY'
import os
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1])

try:
    import yaml
except ImportError:
    print("NO_DATA: PyYAML недоступен — манифест и workflow проверить нечем.")
    sys.exit(0)

failures = []


def fail(code, text):
    failures.append((code, text))


def load_yaml(path, kind):
    """kind: 'manifest' -> код 2 при проблеме; 'workflow' -> код 1."""
    if not path.exists():
        print(f"NO_DATA: файл не найден: {path}")
        sys.exit(0)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        print(f"NO_DATA: не удалось прочитать {path}: {exc}")
        sys.exit(0)
    try:
        return yaml.safe_load(text)
    except Exception as exc:
        if kind == "workflow":
            fail(1, f"копия workflow не является валидным YAML: {path}: {exc}")
            return {}
        print(f"NO_DATA: манифест не разбирается как YAML: {path}: {exc}")
        sys.exit(0)


def job_table(data):
    """{job-id: {"if": str|None, "needs": list[str]}} + множество id."""
    jobs = (data or {}).get("jobs") or {}
    table = {}
    for jid, job in jobs.items():
        job = job or {}
        raw_if = job.get("if")
        if raw_if is not None:
            raw_if = " ".join(str(raw_if).split())
        needs = job.get("needs") or []
        if isinstance(needs, str):
            needs = [needs]
        table[jid] = {"if": raw_if, "needs": sorted(str(n) for n in needs)}
    return table


forgejo_path = root / ".forgejo/workflows/lint.yml"
github_path = root / ".github/workflows/lint.yml"
manifest_path = root / "config/ci/checks.yml"

for required in (forgejo_path, github_path, manifest_path):
    if not required.exists():
        print(f"NO_DATA: нет файла: {required}")
        sys.exit(0)

manifest = load_yaml(manifest_path, "manifest")
forgejo = job_table(load_yaml(forgejo_path, "workflow"))
github = job_table(load_yaml(github_path, "workflow"))

# --- M-1: версия и unknown_paths -------------------------------------------
version = manifest.get("version")
if version != 1:
    print(f"NO_DATA: неизвестная версия манифеста: {version!r} (поддерживается 1)")
    sys.exit(0)
unknown_paths = manifest.get("unknown_paths", "all")
if unknown_paths not in ("all", "none"):
    fail(1, f"M-1: unknown_paths={unknown_paths!r} (допустимо: all, none)")

raw_checks = manifest.get("checks")
if not isinstance(raw_checks, list) or not raw_checks:
    fail(1, "M-2: секция checks обязательна и непуста")
    raw_checks = []

# --- M-2: соответствие джоб ↔ записи (тотально) ----------------------------
records = {}
for raw in raw_checks:
    if not isinstance(raw, dict):
        fail(1, "M-2: запись проверки должна быть словарём")
        continue
    cid = raw.get("id")
    if not cid:
        fail(1, "M-2: запись без id")
        continue
    if cid in records:
        fail(1, f"M-2: дублирующийся id в манифесте: {cid}")
        continue
    records[cid] = raw

forgejo_ids = set(forgejo)
github_ids = set(github)
manifest_ids = set(records)

if forgejo_ids != github_ids:
    fail(1, "M-2: наборы job-id в двух копиях workflow различаются: "
            f"только .forgejo={sorted(forgejo_ids - github_ids)}, "
            f"только .github={sorted(github_ids - forgejo_ids)}")

for jid in sorted((forgejo_ids | github_ids) - manifest_ids):
    fail(1, f"M-2: job-id '{jid}' есть в workflow, но нет записи в манифесте")
for cid in sorted(manifest_ids - (forgejo_ids | github_ids)):
    fail(1, f"M-2: запись манифеста '{cid}' не соответствует ни одному job-id в workflow")

# --- M-3…M-9: инварианты записей ------------------------------------------
heavy_ids = set()
for cid, raw in sorted(records.items()):
    title = raw.get("title")
    if not title:
        fail(1, f"M-3: у записи '{cid}' пустой title")
    mode = raw.get("mode")
    paths = raw.get("paths") or []
    evidence = raw.get("evidence") or ""
    if mode == "paths":
        if not paths:
            fail(1, f"M-4: '{cid}': mode=paths требует непустой zones")
        if not evidence:
            fail(1, f"M-4: '{cid}': требуется evidence (доказательство зоны)")
    elif mode == "always":
        if paths:
            fail(1, f"M-5: '{cid}': mode=always не допускает zones")
    else:
        fail(1, f"M-4: '{cid}': mode должен быть 'paths' или 'always' (получено {mode!r})")
    cost = raw.get("cost", "light")
    if cost not in ("heavy", "light", "info"):
        fail(1, f"M-7: '{cid}': cost={cost!r} (допустимо: heavy, light, info)")
    if raw.get("gate", True) is False and cost != "info":
        fail(1, f"M-7: '{cid}': gate: false ⇒ cost: info (сейчас {cost!r})")
    if raw.get("class", "check") == "infrastructure":
        if mode != "always":
            fail(1, f"M-9: '{cid}': class=infrastructure ⇒ mode: always")
        if paths:
            fail(1, f"M-9: '{cid}': class=infrastructure ⇒ без зон")
    watch = raw.get("watch") or []
    if watch:
        # M-13: watch объявляет, ЧТО служебная запись инспектирует. Запрещён
        # catch-all: иначе объединение зон покрывает всё и неизвестных путей
        # не остаётся никогда (FR-004 обнуляется одной строкой в манифесте).
        for pattern in watch:
            if pattern.strip() in ("**", "*", "/*", "**/*", "**/**"):
                fail(1, f"M-13: '{cid}': watch не допускает catch-all паттерна {pattern!r}")
        if not (raw.get("watch_note") or "").strip():
            fail(1, f"M-13: '{cid}': для watch обязателен watch_note "
                    "(что именно служебная запись инспектирует)")
    if raw.get("cost") == "heavy":
        heavy_ids.add(cid)

# --- M-8: группы -----------------------------------------------------------
group_members = {}
for name, spec in (manifest.get("groups") or {}).items():
    members = (spec or {}).get("members") or []
    if not members:
        fail(1, f"M-8: группа '{name}' без членов")
    for member in members:
        if member not in records:
            fail(1, f"M-8: группа '{name}': член '{member}' не найден в манифесте")
        if member in group_members:
            fail(1, f"M-8: проверка '{member}' входит в две группы "
                    f"('{group_members[member]}' и '{name}')")
        group_members[member] = name

# --- M-6: мёртвая зона (только для mode: paths) -----------------------------
def tracked_files():
    """tracked-файлы дерева: tracked-files.txt (фикстуры) либо git ls-files (репозиторий).

    Приоритет у tracked-files.txt: на фикстуре git ls-files вернул бы файлы САМОЙ
    фикстуры (git отдаёт пути относительно текущего каталога), и проверка «мёртвая
    зона» считала бы зоны фикстуры мёртвыми независимо от её содержимого.
    """
    listing = root / "tracked-files.txt"
    if listing.exists():
        return [line.strip() for line in listing.read_text(encoding="utf-8").splitlines() if line.strip()]
    try:
        proc = subprocess.run(
            ["git", "-c", "core.quotePath=false", "ls-files"],
            capture_output=True, text=True, cwd=str(root), check=False,
        )
        if proc.returncode == 0 and proc.stdout.strip():
            return [line.strip() for line in proc.stdout.splitlines() if line.strip()]
    except OSError:
        pass
    print("NO_DATA: не удалось получить список tracked-файлов (нужен git или tracked-files.txt)")
    sys.exit(0)


def pattern_to_regex(pattern):
    anchored = pattern.startswith("/")
    p = pattern[1:] if anchored else pattern
    if not anchored and "/" not in p and "*" not in p and "?" not in p:
        return "^" + re.escape(p) + "(?:/.*)?$"
    out, i = [], 0
    while i < len(p):
        c = p[i]
        if c == "*":
            if p[i:i + 3] == "**/":
                out.append("(?:.*/)?"); i += 3; continue
            if p[i:i + 2] == "**":
                out.append(".*"); i += 2; continue
            out.append("[^/]*"); i += 1; continue
        if c == "?":
            out.append("[^/]"); i += 1; continue
        out.append(re.escape(c)); i += 1
    rx = "".join(out)
    if rx.endswith("/.*"):
        rx = rx[:-3] + "(?:/.*)?"
    return "^" + rx + "$"


tracked = tracked_files()
for cid, raw in sorted(records.items()):
    patterns = list(raw.get("paths") or []) if raw.get("mode") == "paths" else []
    patterns += list(raw.get("watch") or [])
    for pattern in patterns:
        rx = re.compile(pattern_to_regex(pattern))
        if not any(rx.match(path) for path in tracked):
            fail(1, f"M-6: мёртвая зона: '{cid}' → '{pattern}' не матчит ни одного tracked-файла")

# --- M-10 / M-12: идентичность условий и зависимостей между копиями --------
for jid in sorted(forgejo_ids & github_ids):
    if forgejo[jid]["if"] != github[jid]["if"]:
        fail(1, f"M-10: if:-условия джоба '{jid}' различаются между копиями:\n"
                f"        .forgejo: {forgejo[jid]['if']}\n"
                f"        .github:  {github[jid]['if']}")
    if forgejo[jid]["needs"] != github[jid]["needs"]:
        fail(1, f"M-12: needs джоба '{jid}' различаются между копиями:\n"
                f"        .forgejo: {forgejo[jid]['needs']}\n"
                f"        .github:  {github[jid]['needs']}")

# --- M-11: e2e последователен с тяжёлыми ------------------------------------
E2E_JOB = "karaoke-public-e2e"
if E2E_JOB in forgejo:
    needs = set(forgejo[E2E_JOB]["needs"])
    missing = sorted(heavy_ids - needs - {E2E_JOB})
    if missing:
        fail(1, f"M-11: needs джоба '{E2E_JOB}' не покрывает тяжёлые проверки: {missing}")
    if "changes" not in needs:
        fail(1, f"M-11: needs джоба '{E2E_JOB}' не содержит 'changes'")

# --- M-14: секция known_unmapped (утверждённо не читаемые пути) --------------
# Правила: (а) запись не пересекается ни с одной зоной проверки (иначе избыточна),
# (б) catch-all запрещён, (в) у каждой записи обязателен note, (г) размер списка
# и число покрытых tracked-файлов печатаются всегда — рост списка должен быть
# виден сразу, а не всплыть через полгода.
zone_patterns = []
for cid, raw in sorted(records.items()):
    zone_patterns += list(raw.get("paths") or []) if raw.get("mode") == "paths" else []
    zone_patterns += list(raw.get("watch") or [])

known_unmapped = manifest.get("known_unmapped") or []
ku_files = set()
ku_entries = 0
for entry in known_unmapped:
    if not isinstance(entry, dict):
        fail(1, "M-14: запись known_unmapped должна быть словарём")
        continue
    ku_entries += 1
    paths = entry.get("paths") or []
    note = (entry.get("note") or "").strip()
    if not paths:
        fail(1, "M-14: запись known_unmapped без paths")
        continue
    if not note:
        fail(1, "M-14: запись known_unmapped требует note "
                "(чем подтверждено, что путь не читается)")
    for pattern in paths:
        if pattern.strip() in ("**", "*", "/*", "**/*", "**/**"):
            fail(1, f"M-14: known_unmapped не допускает catch-all {pattern!r}")
            continue
        rx = re.compile(pattern_to_regex(pattern))
        matched = [p for p in tracked if rx.match(p)]
        if not matched:
            fail(1, f"M-14: known_unmapped → '{pattern}' не матчит ни одного tracked-файла")
            continue
        for path in matched:
            ku_files.add(path)
            for zp in zone_patterns:
                if re.compile(pattern_to_regex(zp)).match(path):
                    fail(1, f"M-14: known_unmapped → '{pattern}' пересекается с зоной "
                            f"'{zp}' (файл {path}) — запись избыточна")
                    break

print(f"known_unmapped: {ku_entries} записей, {len(ku_files)} tracked-файлов "
      f"(пустой список = строгое поведение FR-004)")

if failures:
    for code, text in failures:
        print(f"[FAIL] {text}")
    print(f"НАРУШЕНИЙ: {len(failures)}")
    sys.exit(1)

print(f"OK: манифест и обе копии workflow согласованы "
      f"({len(records)} записей, {len(forgejo_ids)} job-id, мёртвых зон нет).")
PY
)"

rc=$?
if [ -n "$OUT" ]; then
  case "$OUT" in
    NO_DATA:*)
      if [ "$QUIET" != "1" ]; then
        printf '%s\n' "$OUT" >&2
        printf '       проверка не выполнена (нет данных) — это НЕ «нарушений нет».\n' >&2
      fi
      exit 2
      ;;
    *)
      if [ "$QUIET" != "1" ]; then
        printf '%s\n' "$OUT"
      fi
      ;;
  esac
fi
exit $rc
