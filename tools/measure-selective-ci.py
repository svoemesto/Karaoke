#!/usr/bin/env python3
"""Измеритель эффекта выборочного запуска CI (спека 692, T062).

Считает по фактическим прогонам CI, сколько ТЯЖЁЛЫХ исполнений было бы
пропущено при действующем правиле применимости, и сравнивает два варианта:

  1. «строгий FR-004» — путь вне объединения зон ⇒ полный набор проверок;
  2. «с known_unmapped» — то же, но пути из секции known_unmapped манифеста
     (утверждённо не читаемые ни одной проверкой) считаются известными.

Оба варианта считаются ОДНОЙ командой: цифры нельзя «пересказать» по памяти —
они пересчитываются из тех же данных.

Входные данные:
  --records PATH   — run-records.json (сбор analyze.py: прогоны, джобы, длительности,
                     изменённые пути). По умолчанию ищется в
                     .scratch/wayfinding/ci-path-filters/run-records.json.
  --manifest PATH  — манифест зон (по умолчанию config/ci/checks.yml).

Методика повторяет базовую (KARAOKE-373, baseline-metrics.md):
  * «тяжёлое исполнение» — задание тяжёлой проверки в прогоне;
  * «busy-время» — сумма длительностей заданий (занятость раннеров);
  * «wall-clock» — модель makespan со списочным шедулером на W=8 (валидация
    модели против наблюдаемых прогонов выполнена в baseline-metrics.md);
  * категории PR — из baseline (поле category в записях прогонов).

Exit codes: 0 — ok, 2 — нет данных (records/манифест не найдены или не разобраны).
"""
from __future__ import annotations

import argparse
import collections
import datetime
import glob
import heapq
import json
import os
import statistics
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools"))

import importlib.util  # noqa: E402

_spec = importlib.util.spec_from_file_location("ci_checks", REPO_ROOT / "tools/ci-checks.py")
ci = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ci)

DEFAULT_RECORDS = REPO_ROOT / ".scratch/wayfinding/ci-path-filters/run-records.json"
CATEGORY_LABELS = {
    "1": "только знания/документы",
    "2": "только фронтенд webvue3",
    "3": "только фронтенд karaoke-public",
    "4": "только бэкенд",
    "5": "только инфраструктура",
    "6": "смешанное/прочее",
}
WORKERS = 8


# --------------------------------------------------------------------------
# Данные
# --------------------------------------------------------------------------

def load_records(path: Path) -> list[dict]:
    if not path.exists():
        print(f"НЕТ ДАННЫХ: файл прогонов не найден: {path}", file=sys.stderr)
        print("  Сбор: .scratch/wayfinding/ci-path-filters/analyze.py (Forgejo API)", file=sys.stderr)
        sys.exit(2)
    try:
        with path.open(encoding="utf-8") as fh:
            records = json.load(fh)
    except (OSError, ValueError) as exc:
        print(f"НЕТ ДАННЫХ: не удалось разобрать {path}: {exc}", file=sys.stderr)
        sys.exit(2)
    if not isinstance(records, list) or not records:
        print(f"НЕТ ДАННЫХ: {path} пуст", file=sys.stderr)
        sys.exit(2)
    return records


def load_task_durations(records_dir: Path) -> dict[int, list[tuple[int, str, float]]]:
    """Длительности заданий по прогонам (для модели makespan)."""
    by_run: dict[int, list[tuple[int, str, float]]] = collections.defaultdict(list)
    tasks: dict[int, dict] = {}
    for path in glob.glob(str(records_dir / "raw/tasks-p*.json")):
        try:
            with open(path, encoding="utf-8") as fh:
                data = json.load(fh)
        except (OSError, ValueError):
            continue
        for task in data.get("workflow_runs", []):
            tasks[task["id"]] = task

    def parse(value: str) -> datetime.datetime:
        return datetime.datetime.strptime(value.replace("Z", "+0000"), "%Y-%m-%dT%H:%M:%S%z")

    for task in tasks.values():
        if not task.get("created_at") or not task.get("updated_at"):
            continue
        by_run[task.get("run_number")].append(
            (
                int(task["id"]),
                task.get("name", ""),
                (parse(task["updated_at"]) - parse(task["created_at"])).total_seconds(),
            )
        )
    return by_run


def makespan(items: list[tuple[int, str, float]], workers: int = WORKERS) -> float:
    """Makespan списочного шедулера: W свободных рабочих мест."""
    if not items:
        return 0.0
    free = [0.0] * workers
    heapq.heapify(free)
    for _, duration in sorted((i[0], i[2]) for i in items):
        slot = heapq.heappop(free)
        heapq.heappush(free, slot + duration)
    return max(free)


# --------------------------------------------------------------------------
# Варианты правила
# --------------------------------------------------------------------------

def zone_patterns(manifest: dict) -> list[str]:
    return ci.known_patterns_of(manifest)


def unmapped_patterns(manifest: dict, force: bool = False) -> list[str]:
    """Глобы секции known_unmapped. force=True -> секция считается включённой
    (измеритель показывает ОБЕ цифры, независимо от текущего переключателя)."""
    return ci.known_unmapped_patterns_of(manifest, active_only=not force,
                                        cli_flag=True if force else None)


def applicable(check_id: str, manifest_by_id: dict, changed: list[str],
               known: list[str], unknown: bool) -> bool:
    if unknown:
        return True
    record = manifest_by_id[check_id]
    return any(ci.match_path(pattern, path) for pattern in record["paths"] for path in changed)


def job_name_map(manifest: dict) -> dict[str, str]:
    """Отображаемое имя джоба -> id проверки (из workflow, без хардкода)."""
    mapping: dict[str, str] = {}
    for workflow in (".forgejo/workflows/lint.yml", ".github/workflows/lint.yml"):
        path = REPO_ROOT / workflow
        if not path.exists():
            continue
        try:
            data = ci.load_yaml(path)
        except Exception:  # noqa: BLE001 — измеритель не должен падать на одной копии
            continue
        for job_id, job in (data.get("jobs") or {}).items():
            name = (job or {}).get("name")
            if not name:
                continue
            # Матричные джобы: имя содержит ${{ matrix.spa }} -> раскрываем
            # в конкретные имена заданий, как их называет API прогонов.
            for combo in ci.matrix_combinations(job or {}):
                mapping.setdefault(ci.substitute(str(name), combo), job_id)
    return mapping


# --------------------------------------------------------------------------
# Расчёт
# --------------------------------------------------------------------------

def measure(records: list[dict], manifest: dict, variant: str, tasks_by_run: dict,
            population: str = "suite") -> dict:
    manifest_by_id = {c["id"]: c for c in manifest["checks"]}
    heavy_ids = {c["id"] for c in manifest["checks"] if c.get("cost") == "heavy"}
    names = job_name_map(manifest)
    name_to_id = {}
    for name, job_id in names.items():
        if job_id in heavy_ids:
            name_to_id[name] = job_id

    if variant == "strict":
        known = zone_patterns(manifest)
    else:
        known = zone_patterns(manifest) + unmapped_patterns(manifest, force=True)

    per_category: dict[str, dict] = {}
    totals = {"heavy_total": 0, "heavy_dropped": 0, "busy_total": 0.0, "busy_saved": 0.0,
              "runs": 0, "runs_with_unknown": 0, "extra_due_to_unknown": 0}

    for record in records:
        if record.get("event") != "pull_request":
            continue
        if population == "suite" and int(record.get("n_canon_jobs") or 0) < 29:
            # Не «полный прогон линта» — в базовой методике такие записи не
            # входят в популяцию (диагностические и отменённые на старте).
            continue
        changed = record.get("changed_paths") or []
        unknown_paths = [p for p in changed
                         if not any(ci.match_path(pattern, p) for pattern in known)]
        has_unknown = bool(unknown_paths)

        category = str(record.get("category") or "6")
        bucket = per_category.setdefault(category, {
            "runs": 0, "heavy_total": 0, "heavy_dropped": 0, "busy_total": 0.0,
            "busy_saved": 0.0, "wall_obs": 0.0, "wall_new": 0.0, "runs_with_unknown": 0,
        })
        bucket["runs"] += 1
        totals["runs"] += 1
        if has_unknown:
            bucket["runs_with_unknown"] += 1
            totals["runs_with_unknown"] += 1

        job_names = set(record.get("job_names") or [])
        durations = record.get("job_durations_s") or {}
        dropped = []
        for name in sorted(job_names):
            check_id = name_to_id.get(name)
            if check_id is None:
                continue
            duration = float(durations.get(name, 0.0))
            bucket["heavy_total"] += 1
            totals["heavy_total"] += 1
            bucket["busy_total"] += duration
            totals["busy_total"] += duration
            if not applicable(check_id, manifest_by_id, changed, known, has_unknown):
                dropped.append(name)
                bucket["heavy_dropped"] += 1
                totals["heavy_dropped"] += 1
                bucket["busy_saved"] += duration
                totals["busy_saved"] += duration
            elif not has_unknown:
                pass
            # цена правила unknown-paths: исполнилось бы по зонам, но не выпало
            if has_unknown and not applicable(check_id, manifest_by_id, changed, known, False):
                totals["extra_due_to_unknown"] += 1

        # wall-clock: наблюдаемый span и модель makespan после пропуска
        items = tasks_by_run.get(record.get("run_number"), [])
        if items:
            bucket["wall_obs"] += makespan(items)
            kept = [(i, n, d) for i, n, d in items if n not in dropped]
            bucket["wall_new"] += makespan(kept)

    return {"per_category": per_category, "totals": totals}


def print_table(title: str, result: dict, strict: dict) -> None:
    totals = result["totals"]
    print()
    print("=" * 96)
    print(title)
    print("=" * 96)
    print(f"{'кат':>3} {'прогонов':>9} {'тяжёлых':>9} {'выпало':>7} {'%':>7} "
          f"{'busy, мин':>10} {'после, мин':>10} {'Δbusy':>7} {'wall/PR до':>11} {'после':>8}")
    for key in sorted(result["per_category"]):
        bucket = result["per_category"][key]
        if not bucket["runs"]:
            continue
        pct = 100.0 * bucket["heavy_dropped"] / bucket["heavy_total"] if bucket["heavy_total"] else 0.0
        d_busy = 100 * (1 - (bucket["busy_total"] - bucket["busy_saved"]) / bucket["busy_total"]) \
            if bucket["busy_total"] else 0.0
        wall_obs = bucket["wall_obs"] / 60 / bucket["runs"]
        wall_new = bucket["wall_new"] / 60 / bucket["runs"]
        print(f"{key:>3} {bucket['runs']:>9} {bucket['heavy_total']:>9} {bucket['heavy_dropped']:>7} "
              f"{pct:>6.1f}% {bucket['busy_total'] / 60:>10.1f} "
              f"{(bucket['busy_total'] - bucket['busy_saved']) / 60:>10.1f} {d_busy:>6.1f}% "
              f"{wall_obs:>10.1f} {wall_new:>8.1f}")
    heavy_pct = 100.0 * totals["heavy_dropped"] / totals["heavy_total"] if totals["heavy_total"] else 0.0
    busy_after = totals["busy_total"] - totals["busy_saved"]
    d_busy = 100 * (1 - busy_after / totals["busy_total"]) if totals["busy_total"] else 0.0
    print(f"  ИТОГО (PR-гейт, n={totals['runs']}): тяжёлых исполнений {totals['heavy_total']}, "
          f"выпало {totals['heavy_dropped']} ({heavy_pct:.1f} %)")
    print(f"  busy тяжёлых: {totals['busy_total'] / 60:.1f} → {busy_after / 60:.1f} мин "
          f"({d_busy:.1f} %)")
    print(f"  прогонов с неизвестным путём: {totals['runs_with_unknown']}; "
          f"тяжёлых исполнений, не выпавших из-за правила unknown-paths: "
          f"{totals['extra_due_to_unknown']}")
    extra = totals["heavy_dropped"] - strict["totals"]["heavy_dropped"]
    print(f"  разница с вариантом «строгий FR-004»: {extra:+d} выпавших тяжёлых исполнений")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Измеритель эффекта выборочного запуска CI")
    parser.add_argument("--records", default=str(DEFAULT_RECORDS),
                        help="run-records.json (по умолчанию — в .scratch/wayfinding/ci-path-filters)")
    parser.add_argument("--manifest", default="config/ci/checks.yml", help="манифест зон")
    parser.add_argument("--variant", choices=("both", "strict", "known-unmapped"), default="both",
                        help="какой вариант считать (по умолчанию — оба)")
    parser.add_argument("--population", choices=("suite", "all"), default="suite",
                        help="популяция прогонов: suite — полные прогоны линта (>=29 джобов, "
                             "как в базовой методике); all — все записи PR-гейта")
    args = parser.parse_args(argv)

    records_path = Path(args.records).resolve()
    records = load_records(records_path)
    manifest_path = (REPO_ROOT / args.manifest).resolve()
    try:
        manifest = ci.load_manifest(manifest_path)
    except Exception as exc:  # noqa: BLE001
        print(f"НЕТ ДАННЫХ: манифест не прочитан: {exc}", file=sys.stderr)
        sys.exit(2)

    tasks_by_run = load_task_durations(records_path.parent)
    unmapped = unmapped_patterns(manifest, force=True)
    print(f"манифест: {manifest_path}")
    print(f"известных зон: {len(zone_patterns(manifest))}; known_unmapped: {len(unmapped)} глоб(ов)")
    pr_runs = [r for r in records if r.get("event") == "pull_request"
               and (args.population == "all" or int(r.get("n_canon_jobs") or 0) >= 29)]
    print(f"прогонов в записях: {len(records)} (PR-гейт в популяции '{args.population}': {len(pr_runs)})")
    active = ci.known_unmapped_enabled(manifest, env=os.environ,
                                       cli_flag=True if args.variant == "known-unmapped" else None)
    print(f"секция known_unmapped в манифесте: {len(manifest.get('known_unmapped') or [])} записей; "
          f"активна сейчас: {'да' if active else 'нет'} "
          f"(переключатель: CI_CHECKS_KNOWN_UNMAPPED=1 / known_unmapped_enabled: true / --known-unmapped)")

    strict = measure(records, manifest, "strict", tasks_by_run, args.population)
    if args.variant in ("both", "strict"):
        print_table("ВАРИАНТ 1: строгий FR-004 (секция known_unmapped пуста)", strict, strict)
    if args.variant in ("both", "known-unmapped"):
        other = measure(records, manifest, "known-unmapped", tasks_by_run, args.population)
        title = "ВАРИАНТ 2: с known_unmapped"
        if unmapped:
            title += f" ({len(unmapped)} глоб(ов))"
        else:
            title += " (секция пуста → результат совпадает со строгим FR-004)"
        print_table(title, other, strict)
    return 0


if __name__ == "__main__":
    sys.exit(main())
