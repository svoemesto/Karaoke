#!/usr/bin/env python3
"""
tools/check-ssot-impact.py

CI gate: проверяет, что изменения в коде сопровождаются синхронным
обновлением knowledge/ (Living Documentation v2, SSoT).

Логика:
1. Получает список изменённых файлов: git diff master...HEAD --name-only.
2. Фильтрует «значимые» файлы (исключает тесты, документацию, спецификации).
3. Для каждого значимого файла ищет правило в .ssot-map.yml.
4. Если правило есть — проверяет, что соответствующий файл в knowledge/
   тоже изменён (или хотя бы один файл в указанной директории).
5. Если правила нет — файл пропускается (advisory).

Exit code: 0 если OK, 1 если есть нарушения.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).parent.parent
SSOT_MAP_FILE = REPO_ROOT / ".ssot-map.yml"


# Пути, которые НЕ требуют обновления knowledge/ (никогда).
# Это служебные/вспомогательные файлы, не содержащие бизнес-логики.
EXCLUDE_PATTERNS = [
    r"^docs/",
    r"^specs/",
    r"^archive/",
    r"^knowledge/",          # Сами knowledge файлы.
    r"^livedocs-?.*",        # На случай, если остались legacy livedocs-*.
    r"^archive/.*\.md$",
    r".*/src/test/.*",
    r".*/test/.*Test\.kt$",
    r".*/test/.*Spec\.kt$",
    r".*/__tests__/.*",
    r".*\.test\.ts$",
    r".*\.spec\.ts$",
    r".*\.test\.js$",
    r".*\.spec\.js$",
    r"\.md$",                # README, CHANGELOG, и т.п.
    # NOTE: tools/ НЕ исключается — изменения в линтерах/скриптах
    # могут менять SSoT-проверки (например, добавить новый rule в
    # .ssot-map.yml → обновить tools/check-ssot-impact.py).
    r"^\.github/",           # Workflows (CI-конфигурация, не код).
    r"^\.pre-commit-config\.yaml$",
    r"^package(-lock)?\.json$",
    r"^yarn\.lock$",
    r"^pnpm-lock\.yaml$",
    r"^gradle/",
    r"^build\.gradle(\.kts)?$",
    r"^settings\.gradle(\.kts)?$",
    r"^deploy/",             # Docker, docker-compose.
    r"^README\.md$",
    r"^CHANGELOG\.md$",
    r"^LICENSE",
]


def is_excluded(filepath):
    """Проверяет, исключён ли файл из обязательного SSoT-обновления."""
    for pat in EXCLUDE_PATTERNS:
        if re.search(pat, filepath):
            return True
    return False


def get_changed_files(base="origin/master"):
    """Возвращает список изменённых файлов относительно base.
    Пробует base как есть, потом fallback на 'master' (если origin/master
    нет, например в первом push после создания репо).
    """
    # Список кандидатов для base — от более специфичного к менее.
    candidates = [base]
    if base != "master":
        candidates.append("master")
    if base != "origin/master":
        candidates.append("origin/master")

    for candidate in candidates:
        try:
            result = subprocess.run(
                ["git", "diff", f"{candidate}...HEAD", "--name-only"],
                capture_output=True, text=True, cwd=REPO_ROOT, check=True,
            )
            return [line.strip() for line in result.stdout.splitlines() if line.strip()]
        except subprocess.CalledProcessError:
            continue

    # Если ни один не сработал — пустой список (no-op).
    print(f"WARN: cannot resolve any of {candidates}; treating as no-op.")
    return []


def load_ssot_map():
    """Загружает .ssot-map.yml. Возвращает пустой список если файла нет."""
    if not SSOT_MAP_FILE.exists():
        return []
    try:
        with open(SSOT_MAP_FILE) as f:
            data = yaml.safe_load(f)
    except yaml.YAMLError as e:
        print(f"ERROR: invalid YAML in {SSOT_MAP_FILE}: {e}", file=sys.stderr)
        sys.exit(1)
    if not data:
        return []
    if not isinstance(data, list):
        print(f"ERROR: {SSOT_MAP_FILE} must be a list of rules", file=sys.stderr)
        sys.exit(1)
    return data


def find_matching_rule(filepath, rules):
    """Ищет правило в маппинге, которое соответствует filepath.
    Возвращает первое совпавшее правило или None.
    """
    for rule in rules:
        pattern = rule.get("code", "")
        if not pattern:
            continue
        # Преобразуем glob-like паттерн в regex.
        regex = "^" + re.escape(pattern).replace(r"\*\*", ".*").replace(r"\*", "[^/]*") + "$"
        if re.match(regex, filepath):
            return rule
    return None


def check_rule_satisfied(rule, changed_files):
    """Проверяет, что требуемое обновление knowledge/ присутствует в изменённых файлах.
    Возвращает (satisfied: bool, reason: str).
    """
    requires = rule.get("requires", "")
    if not requires:
        return True, "no 'requires' field"

    # requires может быть:
    # - относительный путь к файлу в knowledge/
    # - директория в knowledge/ (тогда достаточно одного файла в ней)
    # - glob паттерн

    # 1. Прямое совпадение файла
    if any(f == requires or f.startswith(requires + "/") for f in changed_files):
        return True, f"matching change in {requires}"

    # 2. Любой файл в указанной директории
    for f in changed_files:
        if f.startswith(requires + "/") or f.startswith("./" + requires + "/"):
            return True, f"matching change in {requires}"

    return False, f"no change in {requires}"


def main():
    # Default base — origin/master (для CI). Локально можно переопределить
    # через SSOT_BASE=master.
    base = os.environ.get("SSOT_BASE", "origin/master")
    print(f"[SSoT] Checking impact surface against base '{base}'")
    print()

    # 1. Загрузить маппинг.
    rules = load_ssot_map()
    if not rules:
        print("INFO: .ssot-map.yml not found or empty. SSoT check is no-op.")
        print("      See docs/architecture-notes.md or AGENTS.md for manual SSoT workflow.")
        sys.exit(0)

    # 2. Получить изменённые файлы.
    changed = get_changed_files(base)
    if not changed:
        print("INFO: no changed files detected.")
        sys.exit(0)

    print(f"[1/3] Found {len(changed)} changed files (base={base})")

    # 3. Фильтровать исключённые.
    significant = [f for f in changed if not is_excluded(f)]
    print(f"[2/3] Filtered to {len(significant)} significant files (excluded {len(changed) - len(significant)})")

    if not significant:
        print("INFO: no significant changes (only docs/tests/tooling).")
        sys.exit(0)

    # 4. Проверить каждое значимое изменение.
    violations = []
    matched_count = 0
    for filepath in significant:
        rule = find_matching_rule(filepath, rules)
        if not rule:
            # Нет правила — пропускаем (advisory).
            continue
        matched_count += 1
        satisfied, reason = check_rule_satisfied(rule, changed)
        if not satisfied:
            violations.append({
                "file": filepath,
                "rule": rule,
                "reason": reason,
            })

    print(f"[3/3] Checked {matched_count} rules; {len(violations)} violation(s)")
    print()

    if violations:
        print(f"FAIL: {len(violations)} SSoT-impact violation(s):")
        print()
        for v in violations:
            print(f"  - {v['file']}")
            print(f"      requires: {v['rule'].get('requires', '?')}")
            print(f"      reason:   {v['rule'].get('reason', '?')}")
            print(f"      status:   {v['reason']}")
            print()
        print("Fix: update the corresponding knowledge/ file, or add the change")
        print("to .ssot-map.yml if no SSoT update is required.")
        sys.exit(1)

    print("OK: all matched SSoT-impact rules satisfied.")
    sys.exit(0)


if __name__ == "__main__":
    main()
