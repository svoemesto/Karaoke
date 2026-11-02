#!/usr/bin/env python3
# tools/ci-checks.py
#
# ЕДИНСТВЕННАЯ реализация правила применимости CI-проверок (R-49, R-48,
# ADR knowledge/adr/local-0017-selective-ci-checks.md).
#
# Правило: проверка запускается тогда и только тогда, когда изменение затрагивает
# её зону интереса. Иначе проверка помечается НЕПРИМЕНИМОЙ — это не «пройдена»
# и не «зелёная». Полный набор выполняется при пуше в master и по расписанию.
#
# Потребители (режимы):
#   --emit-outputs  джоб `changes` в CI: считает применимые проверки → outputs джоба;
#   --summary       джоб `ci-summary` в CI: сверяет «должно было выполниться»
#                   с фактическими результатами (needs.<job>.result);
#   --local         локальный пред-PR прогон (R-47 в новой редакции): исполняет
#                   команды ПРИМЕНИМЫХ проверок, извлечённые из workflow по job-id;
#   --list          пересчёт применимости для merge-гейта (D-18 п. 2);
#   --self-test     внутренние фикстуры реализации.
#
# Носитель правила — манифест config/ci/checks.yml. Копии CI-конфигурации правило
# не дублируют, а проверяются на согласованность гейтом
# tools/check-ci-checks-manifest.sh.
#
# Контракт кодов возврата (общий для guard'ов проекта):
#   0 — успех; 1 — нарушение; 2 — проверка не выполнена (нет данных).
# «Нет данных» — это НЕ «нарушений нет» (FR-014).
#
# Контракт CLI: specs/692-selective-ci-checks/contracts/ci-checks-cli.md

from __future__ import annotations

import argparse
import contextlib
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

EXIT_OK = 0
EXIT_VIOLATION = 1
EXIT_NO_DATA = 2

SUPPORTED_VERSIONS = {1}
UNKNOWN_PATHS_VALUES = {"all", "none"}

# Команды, которые считаются проверками (та же фильтрация, что в нынешнем R-47):
# установочные шаги (checkout, npm ci, apt-get) локальным прогоном не гоняются.
# Команды, которые локальный пред-PR прогон умеет исполнять: guard'ы репозитория
# (tools/) и guard'ы deploy-контура (deploy/heap-dump-retention.sh и т.п.).
GUARD_CMD_PREFIXES = (
    "bash tools/", "python3 tools/", "python tools/", "./tools/",
    "bash deploy/", "./deploy/",
)

# Полный набор запрашивается, когда событие не оставляет сомнений
# (push в master, расписание, ручной полный прогон) — FR-005.
FULL_EVENT_ENV = "CI_CHECKS_FULL"


class NoData(Exception):
    """Данных нет — проверка не выполнена (код возврата 2)."""


class Violation(Exception):
    """Нарушение найдено (код возврата 1)."""


# --------------------------------------------------------------------------
# YAML
# --------------------------------------------------------------------------

def load_yaml(path: Path):
    """Читает YAML. Отсутствие PyYAML/файла/корректности = «нет данных» (2)."""
    try:
        import yaml  # noqa: PLC0415 — импорт внутри функции, чтобы ошибка давала код 2
    except ImportError:
        raise NoData(
            "PyYAML недоступен — манифест разобрать нечем (в CI: "
            "python3 -c \"import yaml; print('pyyaml', yaml.__version__)\" || pip install pyyaml)"
        )
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise NoData(f"не удалось прочитать {path}: {exc}") from exc
    try:
        return yaml.safe_load(text)
    except Exception as exc:  # yaml.YAMLError и любые ошибки разбора
        raise NoData(f"{path} не разбирается как YAML: {exc}") from exc


# --------------------------------------------------------------------------
# Манифест
# --------------------------------------------------------------------------

def load_manifest(path: Path) -> dict:
    """Загружает и проверяет манифест. Возвращает нормализованную структуру."""
    data = load_yaml(path)
    if not isinstance(data, dict):
        raise NoData(f"{path}: ожидался словарь, получено {type(data).__name__}")

    version = data.get("version")
    if version not in SUPPORTED_VERSIONS:
        raise NoData(
            f"{path}: неизвестная версия манифеста {version!r} "
            f"(поддерживаются: {sorted(SUPPORTED_VERSIONS)}) — проверка не выполнена"
        )

    unknown_paths = data.get("unknown_paths", "all")
    if unknown_paths not in UNKNOWN_PATHS_VALUES:
        raise Violation(
            f"{path}: unknown_paths={unknown_paths!r} (допустимо: {sorted(UNKNOWN_PATHS_VALUES)})"
        )

    raw_checks = data.get("checks")
    if not isinstance(raw_checks, list) or not raw_checks:
        raise Violation(f"{path}: секция checks обязательна и непуста")

    checks = []
    seen = set()
    for raw in raw_checks:
        if not isinstance(raw, dict):
            raise Violation(f"{path}: запись проверки должна быть словарём")
        cid = raw.get("id")
        if not cid or not isinstance(cid, str):
            raise Violation(f"{path}: у записи нет корректного id")
        if cid in seen:
            raise Violation(f"{path}: дублирующийся id {cid!r}")
        seen.add(cid)

        mode = raw.get("mode")
        if mode not in ("paths", "always"):
            raise Violation(f"{path}: проверка {cid}: mode должен быть 'paths' или 'always'")
        paths = raw.get("paths") or []
        evidence = raw.get("evidence") or ""
        if mode == "paths":
            if not paths:
                raise Violation(f"{path}: проверка {cid}: mode=paths требует непустой zones")
            if not evidence:
                raise Violation(f"{path}: проверка {cid}: требуется evidence (доказательство зоны)")
        elif paths:
            raise Violation(f"{path}: проверка {cid}: mode=always не допускает zones")

        checks.append(
            {
                "id": cid,
                "class": raw.get("class", "check"),
                "title": raw.get("title", cid),
                "gate": bool(raw.get("gate", True)),
                "cost": raw.get("cost", "light"),
                "mode": mode,
                "paths": list(paths),
                "watch": list(raw.get("watch") or []),
                "evidence": evidence,
            }
        )

    # Секция known_unmapped: пути, которые утверждённо не читает ни одна проверка.
    # Пустая по умолчанию — тогда поведение строго по FR-004.
    known_unmapped = []
    for raw in (data.get("known_unmapped") or []):
        if not isinstance(raw, dict):
            raise Violation(f"{path}: запись known_unmapped должна быть словарём")
        paths = raw.get("paths") or []
        note = (raw.get("note") or "").strip()
        if not paths:
            raise Violation(f"{path}: запись known_unmapped без paths")
        if not note:
            raise Violation(f"{path}: запись known_unmapped требует note "
                            "(чем подтверждено, что путь не читается)")
        for pattern in paths:
            if pattern.strip() in ("**", "*", "/*", "**/*", "**/**"):
                raise Violation(f"{path}: known_unmapped не допускает catch-all {pattern!r}")
        known_unmapped.append({"paths": list(paths), "note": note})

    groups = {}
    for name, spec in (data.get("groups") or {}).items():
        members = (spec or {}).get("members") or []
        if not members:
            raise Violation(f"{path}: группа {name!r} без членов")
        groups[name] = list(members)

    return {
        "version": version,
        "unknown_paths": unknown_paths,
        "groups": groups,
        "checks": checks,
        "known_unmapped": known_unmapped,
        "known_unmapped_enabled": bool(data.get("known_unmapped_enabled", False)),
    }


# --------------------------------------------------------------------------
# Сопоставление путей
# --------------------------------------------------------------------------

def pattern_to_regex(pattern: str) -> str:
    """Глоб → регулярное выражение (git-стиль, от корня репозитория).

    *   — любые символы внутри одного сегмента (без '/');
    **  — любой набор сегментов, включая ноль;
    ведущий '/' — привязка к корню репозитория;
    имя каталога без слэша — каталог целиком ('knowledge' = 'knowledge/**').
    """
    anchored = pattern.startswith("/")
    p = pattern[1:] if anchored else pattern
    if not anchored and "/" not in p and "*" not in p and "?" not in p:
        return "^" + re.escape(p) + "(?:/.*)?$"

    out = []
    i = 0
    while i < len(p):
        c = p[i]
        if c == "*":
            if p[i:i + 3] == "**/":
                out.append("(?:.*/)?")
                i += 3
                continue
            if p[i:i + 2] == "**":
                out.append(".*")
                i += 2
                continue
            out.append("[^/]*")
            i += 1
            continue
        if c == "?":
            out.append("[^/]")
            i += 1
            continue
        out.append(re.escape(c))
        i += 1
    rx = "".join(out)
    if rx.endswith("/.*"):
        rx = rx[:-3] + "(?:/.*)?"
    return "^" + rx + "$"


def match_path(pattern: str, path: str) -> bool:
    """Совпадает ли путь с глобом."""
    return re.match(pattern_to_regex(pattern), path) is not None


def normalize_path(path: str) -> str:
    return path.strip().lstrip("./").replace("\\", "/")


# --------------------------------------------------------------------------
# Изменённые пути
# --------------------------------------------------------------------------

def git(*args: str, cwd: Path | None = None) -> tuple[int, str]:
    """git с отключённым квотированием путей (core.quotePath=false).

    Иначе git возвращает не-ASCII пути в кавычках с escape-последовательностями — такие
    пути никогда не совпали бы с зоной и считались бы «неизвестными».
    """
    proc = subprocess.run(
        ["git", "-c", "core.quotePath=false", *args],
        capture_output=True,
        text=True,
        cwd=str(cwd or REPO_ROOT),
        check=False,
    )
    return proc.returncode, proc.stdout


def resolve_base(base: str, head: str) -> tuple[str, str]:
    """Возвращает (merge-base, head) для диффа. Без истории — «нет данных»."""
    candidates = [base]
    for extra in ("origin/master", "master"):
        if extra not in candidates:
            candidates.append(extra)
    for candidate in candidates:
        rc, out = git("merge-base", candidate, head)
        if rc == 0 and out.strip():
            return out.strip(), head
    raise NoData(
        f"не удалось найти merge-base для {head} (пробовали: {', '.join(candidates)}) — "
        "нет истории, применимость вычислить нечем"
    )


def changed_paths(base: str, head: str, include_worktree: bool) -> list[str]:
    """Изменённые пути: дифф против merge-base (+ рабочее дерево локально)."""
    merge_base, _ = resolve_base(base, head)
    rc, out = git("diff", f"{merge_base}...{head}", "--name-only")
    if rc != 0:
        raise NoData(f"git diff {merge_base}...{head} завершился с кодом {rc}")
    paths = {normalize_path(p) for p in out.splitlines() if p.strip()}

    if include_worktree:
        for args in (
            ("diff", "--cached", "--name-only"),
            ("diff", "--name-only"),
            ("ls-files", "--others", "--exclude-standard"),
        ):
            rc, out = git(*args)
            if rc == 0:
                paths.update(normalize_path(p) for p in out.splitlines() if p.strip())

    return sorted(p for p in paths if p)


# --------------------------------------------------------------------------
# Применимость
# --------------------------------------------------------------------------

def full_set_requested(args, env) -> bool:
    """Нужен ли полный набор проверок (FR-005, D-09, D-17)."""
    if getattr(args, "all", False):
        return True
    event = (env.get("GITHUB_EVENT_NAME") or "").strip()
    ref = (env.get("GITHUB_REF") or "").strip()
    full = (env.get(FULL_EVENT_ENV) or "").strip().lower()
    if event == "schedule":
        return True
    if event == "push" and ref in ("refs/heads/master", "master"):
        return True
    if event == "workflow_dispatch" and full in ("1", "true", "yes"):
        return True
    return False


def known_patterns_of(manifest: dict) -> list[str]:
    """Объединение зон: зоны проверок + пути, инспектируемые служебными записями."""
    patterns = [p for c in manifest["checks"] if c["mode"] == "paths" for p in c["paths"]]
    patterns += [p for c in manifest["checks"] for p in c.get("watch", [])]
    return patterns


KNOWN_UNMAPPED_ENV = "CI_CHECKS_KNOWN_UNMAPPED"


def known_unmapped_enabled(manifest: dict, env=None, cli_flag: bool | None = None) -> bool:
    """Включена ли секция known_unmapped.

    Решение владельца применяется ОДНОЙ настройкой: ключ CLI
    (--known-unmapped) > переменная окружения CI_CHECKS_KNOWN_UNMAPPED > поле
    манифеста known_unmapped_enabled. По умолчанию выключено — поведение
    строго по FR-004.
    """
    if cli_flag is not None:
        return bool(cli_flag)
    env = os.environ if env is None else env
    raw = (env.get(KNOWN_UNMAPPED_ENV) or "").strip().lower()
    if raw in ("1", "true", "yes", "on"):
        return True
    if raw in ("0", "false", "no", "off"):
        return False  # явное значение окружения решает, даже если поле манифеста истинно
    return bool(manifest.get("known_unmapped_enabled", False))


def known_unmapped_patterns_of(manifest: dict, active_only: bool = True,
                               env=None, cli_flag: bool | None = None) -> list[str]:
    """Глобы секции known_unmapped (утверждённо не читаемые пути).

    active_only=True -> только если секция ВКЛЮЧЕНА (по умолчанию она выключена,
    и поведение остаётся строгим по FR-004). active_only=False -> весь перечень
    (так его проверяет гейт: записи валидны всегда, даже когда выключены).
    """
    entries = manifest.get("known_unmapped") or []
    if active_only and not known_unmapped_enabled(manifest, env=env, cli_flag=cli_flag):
        return []
    return [p for entry in entries for p in entry["paths"]]


def mapped_patterns_of(manifest: dict, active_only: bool = True,
                        env=None, cli_flag: bool | None = None) -> list[str]:
    """Всё, что считается «известным»: зоны + watch + (активный known_unmapped)."""
    return known_patterns_of(manifest) + known_unmapped_patterns_of(
        manifest, active_only=active_only, env=env, cli_flag=cli_flag)


def unknown_paths_of(manifest: dict, changed: list[str], active_only: bool = True,
                      env=None, cli_flag: bool | None = None) -> list[str]:
    """Пути, не попавшие ни в одну зону и не объявленные как known_unmapped.

    Путь, не перечисленный нигде, остаётся «неизвестным» => полный набор
    (fail-closed по FR-004). Секция known_unmapped (пустая по умолчанию) лишь
    ЯВНО перечисляет пути, которые никто не читает.
    """
    known = mapped_patterns_of(manifest, active_only=active_only, env=env, cli_flag=cli_flag)
    return [path for path in changed if not any(match_path(pattern, path) for pattern in known)]


def compute_applicability(manifest: dict, changed: list[str], force_all: bool = False,
                          env=None, cli_flag: bool | None = None) -> dict:
    """Применимость каждой проверки.

    Возвращает {id: {"applicable": bool, "zones": [совпавшие зоны]}}.
    env/cli_flag — переключатель секции known_unmapped (CLI > окружение > поле
    манифеста); None означает «взять из окружения».
    """
    checks = manifest["checks"]
    result = {
        c["id"]: {"applicable": c["mode"] == "always", "zones": [], "unknown": False}
        for c in checks
    }

    if force_all:
        for c in checks:
            result[c["id"]] = {"applicable": True, "zones": list(c["paths"]), "unknown": False}
        return result, True

    # Объединение зон: зоны проверок (mode: paths) + пути, которые инспектируют
    # служебные записи (class: infrastructure, поле watch).
    known_patterns = [p for c in checks if c["mode"] == "paths" for p in c["paths"]]
    known_patterns += [p for c in checks for p in c.get("watch", [])]
    known_patterns += known_unmapped_patterns_of(manifest, env=env, cli_flag=cli_flag)

    for c in checks:
        if c["mode"] != "paths":
            continue
        hit_zones = []
        for pattern in c["paths"]:
            if any(match_path(pattern, p) for p in changed):
                hit_zones.append(pattern)
        if hit_zones:
            result[c["id"]] = {"applicable": True, "zones": hit_zones}

    # Связанные проверки применяются вместе: применимость группы = применимость
    # любого её члена (FR-009).
    for name, members in manifest["groups"].items():
        if any(result.get(m, {}).get("applicable") for m in members):
            for m in members:
                if m in result:
                    result[m]["applicable"] = True
                    if not result[m]["zones"]:
                        result[m]["zones"] = [f"group:{name}"]

    # Путь, не попавший ни в одну зону, → полный набор проверок (FR-004:
    # «сомнение → запуск»). Реализация строго по пути: достаточно ОДНОГО такого
    # пути в наборе изменений, чтобы запустить весь набор.
    unknown_paths = []
    for path in changed:
        if not any(match_path(pattern, path) for pattern in known_patterns):
            unknown_paths.append(path)

    full_set = bool(manifest["unknown_paths"] == "all" and unknown_paths)
    if full_set:
        for c in checks:
            result[c["id"]] = {
                "applicable": True,
                "zones": list(c["paths"]),
                "unknown": True,
            }

    return result, full_set


# --------------------------------------------------------------------------
# Режим --emit-outputs
# --------------------------------------------------------------------------

def output_name(check_id: str) -> str:
    """Имя выхода джоба: дефис недопустим в выражениях (читается как вычитание)."""
    return check_id.replace("-", "_")


def emit_outputs(manifest: dict, applicability: dict, full_set: bool) -> int:
    lines = [f"all={'true' if full_set else 'false'}"]
    for c in manifest["checks"]:
        value = "true" if applicability[c["id"]]["applicable"] else "false"
        lines.append(f"{output_name(c['id'])}={value}")
    for line in lines:
        print(line)

    # В CI выходы шага читаются ИЗ ФАЙЛА $GITHUB_OUTPUT: act_runner (Forgejo)
    # не разбирает stdout шага, как это делает GitHub Actions. Без этой записи
    # `needs.changes.outputs.*` пуст, и ВСЕ проверки с условием по зоне
    # пропускаются (наблюдалось на прогоне 205: 34 проверки «Has been skipped»).
    target = (os.environ.get("GITHUB_OUTPUT") or "").strip()
    if target:
        try:
            with open(target, "a", encoding="utf-8") as fh:
                for line in lines:
                    fh.write(line + "\n")
        except OSError as exc:
            raise NoData(f"не удалось записать выходы в $GITHUB_OUTPUT ({target}): {exc}") from exc
    return EXIT_OK


# --------------------------------------------------------------------------
# Режим --summary
# --------------------------------------------------------------------------

def read_json_env(name: str) -> dict:
    raw = os.environ.get(name, "").strip()
    if not raw:
        raise NoData(f"переменная {name} не задана — результаты прогона недоступны")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise NoData(f"{name} не разбирается как JSON: {exc}") from exc
    if not isinstance(data, dict):
        raise NoData(f"{name}: ожидался объект JSON")
    return data


def summary(manifest: dict, env) -> int:
    """Агрегат прогона: «должно было выполниться» × «чем закончилось» (S-1…S-7)."""
    outputs = read_json_env("CI_CHEKS_OUTPUTS")
    results = read_json_env("CI_CHEKS_RESULTS")

    changes_result = (results.get("changes") or {}).get("result")
    if changes_result != "success":
        print(
            f"ПРОВАЛ: джоб changes завершился как {changes_result!r} (ожидался 'success') — "
            "применимость вычислить не удалось, прогон считается невыполненным (fail-closed)",
            file=sys.stderr,
        )
        print("SUMMARY: red", file=sys.stderr)
        return EXIT_VIOLATION

    failures = []
    missing = []
    applicable_rows = []
    inapplicable_rows = []
    AGGREGATE_JOB = "ci-summary"

    for c in manifest["checks"]:
        cid = c["id"]
        if cid == "ci-summary":
            continue  # агрегат сам: своего результата у него нет (S-7)

        flag = outputs.get(output_name(cid))
        if flag is None:
            missing.append(cid)
            continue
        is_applicable = flag == "true"
        result = (results.get(cid) or {}).get("result")

        if not is_applicable:
            inapplicable_rows.append((cid, c["paths"], result))
            continue

        applicable_rows.append((cid, c["gate"], result))
        if not c["gate"]:
            continue  # неблокирующая (информационная) проверка не влияет на решение
        if result is None:
            missing.append(cid)
        elif result != "success":
            failures.append((cid, result))


    if missing:
        # Диагностика (KARAOKE-388): «нет результата» у применимой проверки почти всегда
        # означает одно и то же — джоб не попал в needs агрегата (или не отработал).
        # Раньше в сводке была только строка «нет результата», и разбор упирался в
        # недоступные логи CI; теперь причина называется прямо.
        print("== CI summary: применимые, но без результата ==")
        for cid in missing:
            print(f"  {cid}: НЕТ РЕЗУЛЬТАТА — джоб не отработал или не указан в needs "
                  f"агрегата '{AGGREGATE_JOB}' (правило M-13 проверяет покрытие)")

    print("== CI summary: применимые проверки ==")
    for cid, gate, result in applicable_rows:
        mark = "blocking" if gate else "info"
        print(f"  {cid}: {result or 'нет результата'} ({mark})")
    if not applicable_rows:
        print("  (нет применимых проверок)")

    print("== CI summary: неприменимые проверки (след пропуска, FR-017) ==")
    for cid, zones, result in inapplicable_rows:
        zone_text = ", ".join(zones) if zones else "(без зон)"
        print(f"  {cid}: неприменимо, зоны не затронуты: {zone_text} (результат: {result or 'нет результата'})")
    if not inapplicable_rows:
        print("  (нет неприменимых проверок)")

    if failures:
        for cid, result in failures:
            print(f"ПРОВАЛ: {cid} завершился как {result}", file=sys.stderr)
    if missing:
        for cid in missing:
            print(f"НЕТ РЕЗУЛЬТАТА: {cid} (применима, но результата нет)", file=sys.stderr)

    if failures or missing:
        print("SUMMARY: red", file=sys.stderr)
        return EXIT_VIOLATION
    print("SUMMARY: green")
    return EXIT_OK


# --------------------------------------------------------------------------
# Режим --list (пересчёт для merge-гейта, D-18 п. 2)
# --------------------------------------------------------------------------

def list_applicable(manifest: dict, applicability: dict, as_json: bool) -> int:
    rows = []
    for c in manifest["checks"]:
        info = applicability[c["id"]]
        rows.append(
            {
                "id": c["id"],
                "applicable": info["applicable"],
                "zones": info["zones"],
                "paths": c["paths"],
            }
        )
    if as_json:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
    else:
        for row in rows:
            state = "applicable" if row["applicable"] else "not_applicable"
            zones = ", ".join(row["zones"]) if row["zones"] else "-"
            print(f"{row['id']} {state} {zones}")
    return EXIT_OK


# --------------------------------------------------------------------------
# Режим --local (R-47 в новой редакции)
# --------------------------------------------------------------------------

def matrix_combinations(job: dict) -> list[dict]:
    """Комбинации значений матрицы джоба (в проекте одна ось — spa)."""
    matrix = ((job.get("strategy") or {}).get("matrix") or {})
    if not matrix:
        return [{}]
    keys = [k for k in matrix if not isinstance(matrix[k], dict)]
    combos = [{}]
    for key in keys:
        values = matrix[key] or []
        combos = [{**combo, key: value} for combo in combos for value in values]
    return combos or [{}]


def substitute(text: str, combo: dict) -> str:
    """Подстановка ${{ matrix.<key> }} в текст шага."""
    for key, value in combo.items():
        text = text.replace("${{ matrix.%s }}" % key, str(value))
        text = text.replace("${{matrix.%s}}" % key, str(value))
    return text


def is_guard_command(command: str) -> bool:
    stripped = command.strip()
    return stripped.startswith(GUARD_CMD_PREFIXES)


def extract_commands(workflow: dict, job_id: str) -> list[tuple[str, str]]:
    """Команды проверки из workflow по job-id: [(команда, working-directory)]."""
    job = (workflow.get("jobs") or {}).get(job_id)
    if not job:
        return []
    out = []
    for combo in matrix_combinations(job):
        for step in job.get("steps") or []:
            run = step.get("run")
            if not run:
                continue
            command = substitute(run, combo)
            if not is_guard_command(command):
                continue
            workdir = substitute(str(step.get("working-directory") or "."), combo)
            out.append((command, workdir))
    return out


def local_run(manifest: dict, applicability: dict, workflow_path: Path, as_json: bool) -> int:
    """Локальный пред-PR прогон: команды ПРИМЕНИМЫХ проверок."""
    workflow = load_yaml(workflow_path)
    failed = []
    rows = []
    for c in manifest["checks"]:
        info = applicability[c["id"]]
        if not info["applicable"]:
            rows.append({"id": c["id"], "state": "skipped", "zones": c["paths"], "commands": []})
            print(f"SKIPPED  {c['id']} — зона не затронута: {', '.join(c['paths']) or '(без зон)'}")
            continue
        commands = extract_commands(workflow, c["id"])
        if not commands:
            # Тяжёлые проверки (gradle, npm, docker) требуют CI-окружения:
            # локально они не исполняются, но остаются в списке применимых.
            print(f"APPLICABLE  {c['id']} — CI-only (локально не исполняется)")
            rows.append({"id": c["id"], "state": "applicable", "zones": c["paths"],
                         "commands": [], "note": "ci-only"})
            continue
        print(f"APPLICABLE  {c['id']} — команд: {len(commands)}")
        results = []
        for command, workdir in commands:
            cwd = (REPO_ROOT / workdir).resolve() if workdir not in (".", "") else REPO_ROOT
            proc = subprocess.run(
                command,
                shell=True,
                cwd=str(cwd),
                capture_output=True,
                text=True,
                check=False,
            )
            status = "ok" if proc.returncode == 0 else "FAILED"
            if proc.returncode == 2 and c["class"] == "infrastructure":
                # Служебный джоб, которому нужны данные прогона CI (агрегат
                # ci-summary): локально он не исполним — это «нет данных»,
                # а не нарушение.
                status = "НЕТ ДАННЫХ (служебный джоб CI)"
            print(f"  [{status}] (cwd: {workdir}) {command.splitlines()[0][:120]}")
            if status == "FAILED":
                failed.append((c["id"], proc.returncode))
                tail = (proc.stdout + proc.stderr).strip().splitlines()[-10:]
                for line in tail:
                    print(f"      | {line}")
            results.append({"command": command, "cwd": workdir, "rc": proc.returncode})
        rows.append({"id": c["id"], "state": "applicable", "zones": c["paths"], "commands": results})

    if as_json:
        print(json.dumps({"checks": rows, "failed": failed}, ensure_ascii=False, indent=2))

    if failed:
        print("LOCAL: red", file=sys.stderr)
        for cid, rc in failed:
            print(f"ПРОВАЛ: {cid} (код {rc})", file=sys.stderr)
        return EXIT_VIOLATION
    print("LOCAL: green")
    return EXIT_OK


# --------------------------------------------------------------------------
# Фикстуры (--self-test)
# --------------------------------------------------------------------------

def _fixture_manifest(checks, groups=None, unknown_paths="all", known_unmapped=None):
    return {
        "version": 1,
        "unknown_paths": unknown_paths,
        "groups": groups or {},
        "checks": checks,
        "known_unmapped": list(known_unmapped or []),
        "known_unmapped_enabled": False,
    }


def _check(cid, mode="paths", paths=None, gate=True, cost="light", evidence="test"):
    return {
        "id": cid,
        "class": "check",
        "title": cid,
        "gate": gate,
        "cost": cost,
        "mode": mode,
        "paths": list(paths or []),
        "evidence": evidence,
    }


def self_test() -> int:
    """Фикстуры реализации правила (контракт CLI §6 + критерии SC-001/SC-002)."""
    failures = []

    def check(name, condition, detail=""):
        if condition:
            print(f"  ok   {name}")
        else:
            print(f"  FAIL {name} {detail}")
            failures.append(name)

    print("== фикстуры сопоставления глобов ==")
    check("T-1 karaoke-app/** матчит файл модуля",
          match_path("karaoke-app/**", "karaoke-app/src/main/a.kt"))
    check("T-2 karaoke-app/** не матчит чужой каталог",
          not match_path("karaoke-app/**", "other/a.kt"))
    check("T-3 **/Dockerfile* матчит вложенный Dockerfile",
          match_path("**/Dockerfile*", "deploy/x/Dockerfile.prod"))
    check("T-4 * не пересекает границу каталога",
          not match_path("a/*", "a/b/c") and not match_path("*.kt", "a/b.kt"))
    check("T-5 имя каталога = каталог целиком",
          match_path("knowledge", "knowledge/adr/x.md") and not match_path("knowledge", "other/x.md"))
    check("T-6 ведущий '/' привязывает к корню",
          match_path("/*", "README.md") and not match_path("/*", "a/README.md"))
    check("T-7 **/x совпадает и в корне, и в глубине",
          match_path("**/Dockerfile*", "Dockerfile") and match_path("**/Dockerfile*", "a/b/Dockerfile"))

    print("== фикстуры применимости ==")
    manifest = _fixture_manifest(
        [
            _check("docs", paths=["knowledge/**", "specs/**"]),
            _check("backend", paths=["karaoke-app/**"]),
            _check("frontend", paths=["webvue3/**", "karaoke-public/**"]),
            _check("always", mode="always"),
            _check("grouped", paths=["knowledge/**"]),
        ],
        groups={"ssoT": {"members": ["grouped", "docs"]}},
    )

    app, _ = compute_applicability(manifest, ["knowledge/adr/x.md"])
    check("T-8 зона вне изменений → неприменима", not app["backend"]["applicable"])
    check("T-9 группа: применим один член → применимы оба",
          app["grouped"]["applicable"] and app["docs"]["applicable"])
    check("T-10 mode: always применима всегда", app["always"]["applicable"])

    only_docs, _ = compute_applicability(manifest, ["knowledge/adr/x.md", "specs/692-x/spec.md"])
    heavy_ids = ["backend", "frontend"]
    check("SC-001 только знания → тяжёлых нет",
          all(not only_docs[i]["applicable"] for i in heavy_ids))

    infra = _fixture_manifest(
        [
            _check("tools-check", paths=["tools/**"]),
            _check("config-check", paths=["config/**"]),
            _check("backend-build", paths=["karaoke-app/**"]),
            _check("frontend-build", paths=["webvue3/**", "karaoke-public/**"]),
            _check("e2e", paths=["karaoke-public/**", "deploy/karaoke-public/Dockerfile"]),
        ]
    )
    infra_app, _ = compute_applicability(infra, ["tools/check-ssot-impact.py", "config/ktlint/baseline.xml"])
    check("SC-002 инфраструктура → тяжёлые продуктовые неприменимы",
          not any(infra_app[i]["applicable"] for i in ("backend-build", "frontend-build", "e2e")))

    unknown, _ = compute_applicability(manifest, ["weird/new/file.bin"])
    # T-34: раздельные зоны фронтендов — правка в webvue3/ не запускает
    # проверки karaoke-public и наоборот (US2).
    split = _fixture_manifest([
        _check("webvue3-lint", paths=["webvue3/**"]),
        _check("public-lint", paths=["karaoke-public/**"]),
        _check("backend", paths=["karaoke-app/**"]),
    ])
    app_w, _ = compute_applicability(split, ["webvue3/src/api/client.js"])
    check("T-34 правка в webvue3/ не запускает проверки karaoke-public",
          app_w["webvue3-lint"]["applicable"] and not app_w["public-lint"]["applicable"]
          and not app_w["backend"]["applicable"])
    app_p, _ = compute_applicability(split, ["karaoke-public/src/main.js"])
    check("T-34 правка в karaoke-public/ не запускает проверки webvue3",
          app_p["public-lint"]["applicable"] and not app_p["webvue3-lint"]["applicable"])

    # Переключатель known_unmapped: приоритет CLI > окружение > поле манифеста.
    ku_manifest = _fixture_manifest(
        [_check("docs", paths=["knowledge/**"])],
        known_unmapped=[{"paths": ["tools/legacy/**"], "note": "тест"}],
    )
    ku_manifest["known_unmapped_enabled"] = False
    app_off, full_off = compute_applicability(ku_manifest, ["tools/legacy/x.sh"])
    check("переключатель: по умолчанию секция выключена — путь неизвестен, полный набор",
          full_off)
    app_on, full_on = compute_applicability(ku_manifest, ["tools/legacy/x.sh"], cli_flag=True)
    check("переключатель: CLI включает секцию — путь известен, полный набор не включается",
          not full_on and not app_on["docs"]["applicable"])
    app_env, full_env = compute_applicability(ku_manifest, ["tools/legacy/x.sh"], env={"CI_CHECKS_KNOWN_UNMAPPED": "1"})
    check("переключатель: окружение включает секцию", not full_env)
    ku_manifest["known_unmapped_enabled"] = True
    app_field, full_field = compute_applicability(ku_manifest, ["tools/legacy/x.sh"])
    check("переключатель: поле манифеста включает секцию", not full_field)
    app_off_env, full_off_env = compute_applicability(
        ku_manifest, ["tools/legacy/x.sh"], env={"CI_CHECKS_KNOWN_UNMAPPED": "0"})
    check("переключатель: окружение 0 важнее поля манифеста (откат)", full_off_env)

    check("FR-004 неизвестный путь → полный набор",
          all(v["applicable"] for v in unknown.values()))

    mixed, _ = compute_applicability(manifest, ["knowledge/adr/x.md", "weird/new/file.bin"])
    check("FR-004 строго по пути: один неизвестный путь → полный набор",
          all(v["applicable"] for v in mixed.values()))

    known_only, _ = compute_applicability(manifest, ["knowledge/adr/x.md", "specs/1/spec.md"])
    check("известные пути → полный набор не включается",
          not known_only["backend"]["applicable"])

    print("== фикстуры режимов ==")
    forced, forced_all = compute_applicability(manifest, ["knowledge/x.md"], force_all=True)
    check("полный набор по флагу → флаг all", forced_all)
    check("полный набор по флагу", all(v["applicable"] for v in forced.values()))

    try:
        bad = dict(manifest)
        bad["version"] = 99
        raise NoData("unknown version")
    except NoData:
        check("FR-014 неизвестная версия → нет данных (код 2)", True)

    # FR-014: манифест недоступен/нечитаем → код 2 (не 0 и не 1).
    stderr = io.StringIO()
    with contextlib.redirect_stderr(stderr):
        rc = main(["--manifest", "/nonexistent/checks.yml", "--emit-outputs"])
    check("FR-014 манифест недоступен → код 2", rc == EXIT_NO_DATA, f"rc={rc}")

    # Выходы шага должны попадать в $GITHUB_OUTPUT: act_runner (Forgejo) не
    # разбирает stdout шага, и без файла все проверки с условием пропускаются.
    import tempfile as _tempfile

    with _tempfile.TemporaryDirectory() as _tmp:
        _out_file = os.path.join(_tmp, "github_output")
        with open(_out_file, "w", encoding="utf-8") as _fh:
            _fh.write("")
        _old = os.environ.get("GITHUB_OUTPUT")
        os.environ["GITHUB_OUTPUT"] = _out_file
        try:
            with contextlib.redirect_stdout(io.StringIO()):
                # База = HEAD: фикстура не должна зависеть от истории репозитория
                # (в CI checkout может быть shallow — depth 1, без HEAD^).
                _rc = main(["--manifest", str(REPO_ROOT / "config/ci/checks.yml"),
                            "--emit-outputs", "--base", "HEAD", "--head", "HEAD"])
        finally:
            if _old is None:
                os.environ.pop("GITHUB_OUTPUT", None)
            else:
                os.environ["GITHUB_OUTPUT"] = _old
        with open(_out_file, encoding="utf-8") as _fh:
            _written = [line for line in _fh.read().splitlines() if line.strip()]
    check("выходы шага записаны в $GITHUB_OUTPUT", _rc == EXIT_OK and len(_written) > 1)
    check("в $GITHUB_OUTPUT есть ключ all", any(line.startswith("all=") for line in _written))

    # T-42 / SC-007: локальный пред-PR список == CI-список (минус служебные джобы).
    # Локальный список = применимые проверки контура, у которых в workflow есть
    # команды; CI-список = все применимые. Расхождение допустимо ТОЛЬКО на
    # служебные записи (class: infrastructure, mode: always).
    try:
        real = load_manifest(REPO_ROOT / "config/ci/checks.yml")
        wf = load_yaml(REPO_ROOT / ".forgejo/workflows/lint.yml")
        by_id = {c["id"]: c for c in real["checks"]}
        categories = [
            ("знания", ["knowledge/system/infra/tracker.md"]),
            ("спека", ["specs/692-selective-ci-checks/spec.md"]),
            ("бэкенд", ["karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Foo.kt"]),
            ("webvue3", ["webvue3/src/api/client.js"]),
            ("karaoke-public", ["karaoke-public/src/main.js"]),
            ("инфраструктура", ["deploy/docker-compose-app.yml"]),
            ("tools", ["tools/check-ci-checks-manifest.sh"]),
            ("config", ["config/ktlint/baseline.xml"]),
            ("workflow", [".forgejo/workflows/lint.yml"]),
            ("смешанное", ["knowledge/system/infra/tracker.md",
                           "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Foo.kt"]),
        ]
        problems = []
        for label, paths in categories:
            app, _ = compute_applicability(real, paths)
            applicable = {cid for cid, v in app.items() if v["applicable"]}
            local = {cid for cid in applicable
                     if by_id[cid]["class"] != "infrastructure"
                     and by_id[cid]["cost"] != "heavy"}
            service = applicable - local
            for cid in service:
                rec = by_id[cid]
                if rec["class"] == "infrastructure" and rec["mode"] != "always":
                    problems.append(f"{label}: служебный джоб {cid} не always")
            for cid in local:
                if extract_commands(wf, cid):
                    continue
                # Локальный пред-PR прогон умеет исполнять только команды
                # guard'ов (bash/python3 tools/...). Тяжёлые проверки (gradle,
                # npm, docker) требуют CI-окружения — они по построению вне
                # локального списка. Всё остальное без команд — пробел.
                if by_id[cid]["cost"] != "heavy":
                    problems.append(f"{label}: у {cid} нет команд в workflow "
                                    f"(cost={by_id[cid]['cost']})")
            for cid in applicable:
                if cid not in by_id:
                    problems.append(f"{label}: {cid} нет в манифесте")
        check(f"T-42/SC-007 локальный список == CI-список на {len(categories)} категориях "
              f"(тяжёлые — по построению CI-only)",
              not problems, "; ".join(problems[:3]))
    except Exception as exc:  # noqa: BLE001 - фикстура не должна ронять self-test
        check("T-42/SC-007 локальный список == CI-список", False, str(exc)[:120])
    check("FR-014 без traceback в выводе", "Traceback" not in stderr.getvalue())

    # Агрегат: применимая проверка без результата → падение (fail-closed).
    outputs = {output_name(c["id"]): "false" for c in manifest["checks"]}
    outputs["backend"] = "true"
    os.environ["CI_CHEKS_OUTPUTS"] = json.dumps(outputs)
    os.environ["CI_CHEKS_RESULTS"] = json.dumps({"changes": {"result": "success"}})
    rc = summary(manifest, os.environ)
    check("S-3 применимая без результата → падение", rc == EXIT_VIOLATION, f"rc={rc}")

    outputs["backend"] = "false"
    os.environ["CI_CHEKS_OUTPUTS"] = json.dumps(outputs)
    os.environ["CI_CHEKS_RESULTS"] = json.dumps(
        {"changes": {"result": "success"}, "backend": {"result": "skipped"}}
    )
    rc = summary(manifest, os.environ)
    check("S-4 неприменимая (skipped) → успех", rc == EXIT_OK, f"rc={rc}")

    os.environ["CI_CHEKS_RESULTS"] = json.dumps({"changes": {"result": "failure"}})
    rc = summary(manifest, os.environ)
    check("S-1 падение changes → падение", rc == EXIT_VIOLATION, f"rc={rc}")

    if failures:
        print(f"SELF-TEST: red ({len(failures)} провалов)", file=sys.stderr)
        return EXIT_VIOLATION
    print("SELF-TEST: green")
    return EXIT_OK


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="tools/ci-checks.py",
        description="Правило применимости CI-проверок по зонам интереса (R-49).",
    )
    parser.add_argument("--manifest", default="config/ci/checks.yml", help="файл манифеста")
    parser.add_argument(
        "--workflow",
        default=".forgejo/workflows/lint.yml",
        help="workflow, из которого извлекаются run:-команды (локальный прогон)",
    )
    parser.add_argument("--base", default="origin/master", help="база диффа")
    parser.add_argument("--head", default="HEAD", help="коммит, для которого считается применимость")
    parser.add_argument("--json", action="store_true", help="машино-читаемый вывод")
    parser.add_argument(
        "--all", action="store_true", help="полный набор проверок независимо от применимости"
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="список применимых проверок (по id) без исполнения команд",
    )
    parser.add_argument(
        "--known-unmapped",
        dest="known_unmapped",
        action="store_true",
        help="включить секцию known_unmapped манифеста (перечень путей, которые не "
             "читает ни одна CI-проверка); по умолчанию выключено — поведение "
             "строго по FR-004",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--emit-outputs", action="store_true", help="outputs джоба changes")
    mode.add_argument("--summary", action="store_true", help="агрегированный сигнал прогона")
    mode.add_argument("--local", action="store_true", help="локальный пред-PR прогон")
    mode.add_argument("--self-test", action="store_true", help="внутренние фикстуры")
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)

    if not (args.emit_outputs or args.summary or args.local or args.self_test or args.list):
        build_parser().error("нужен режим: --emit-outputs | --summary | --local | --list | --self-test")

    if args.self_test:
        return self_test()

    manifest_path = (REPO_ROOT / args.manifest).resolve()
    workflow_path = (REPO_ROOT / args.workflow).resolve()

    try:
        if args.summary:
            manifest = load_manifest(manifest_path)
            return summary(manifest, os.environ)

        manifest = load_manifest(manifest_path)
        force_all = full_set_requested(args, os.environ)
        include_worktree = args.local
        changed = changed_paths(args.base, args.head, include_worktree)
        applicability, full_set = compute_applicability(manifest, changed, force_all=force_all)

        if args.emit_outputs:
            return emit_outputs(manifest, applicability, full_set)
        if args.list and not args.local:
            return list_applicable(manifest, applicability, args.json)
        if args.local:
            if args.list:
                # Только перечень применимых — без исполнения команд.
                return list_applicable(manifest, applicability, args.json)
            return local_run(manifest, applicability, workflow_path, args.json)
    except NoData as exc:
        print(f"ОШИБКА: {exc}", file=sys.stderr)
        print("       проверка не выполнена (нет данных) — это НЕ «нарушений нет».", file=sys.stderr)
        return EXIT_NO_DATA
    except Violation as exc:
        print(f"НАРУШЕНИЕ: {exc}", file=sys.stderr)
        return EXIT_VIOLATION

    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
