#!/usr/bin/env python3
"""tools/check-audit-registry.py

Guard: реестр аудита #336 соответствует материалам `knowledge/`.

Назначение: FR-015, D9, SC-002, SC-009. Реестр — единственный носитель
BR-кодов аудита #336 (510 кодов). Guard следит за двумя расхождениями:

  A. Код встречается в `knowledge/**` (кроме самого реестра), но в реестре
     отсутствует  →  «код не зарегистрирован»: SSoT знает о коде, а
     реестр — нет (потеря учёта).
  B. Код есть в реестре, но не встречается в `knowledge/**` (кроме самого
     реестра) И не помечен в разделе «Коды без зеркала»  →  «код потерян»:
     либо код выпал из материалов, либо забыта пометка.

Использование:
    python3 tools/check-audit-registry.py [--root <dir>]

    --root <dir>   корень дерева (по умолчанию — корень репозитория); нужен
                   для прогона на фикстурах tools/fixtures/audit-registry/.

Коды возврата:
    0 — соответствие полное;
    1 — нарушения (перечислены все: код, файл и строка);
    2 — проверка не выполнена (реестр не найден, нет python-окружения).
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

CODE_RE = re.compile(r"\bBR-\d+\b")
# Строка таблицы § 3 реестра: | BR-NNN | суть | зеркало | пакет |
TABLE_ROW_RE = re.compile(r"^\|\s*(BR-\d+)\s*\|")
NO_MIRROR_HEADING = "## 5. Коды без зеркала"


def collect_registry_codes(registry_text: str) -> tuple[set[str], set[str]]:
    """Коды таблицы § 3 и коды из раздела «Коды без зеркала» (§ 5)."""
    table_codes: set[str] = set()
    in_no_mirror = False
    no_mirror_codes: set[str] = set()
    for line in registry_text.splitlines():
        if line.startswith("## "):
            in_no_mirror = line.strip() == NO_MIRROR_HEADING
            continue
        m = TABLE_ROW_RE.match(line)
        if m:
            table_codes.add(m.group(1))
            continue
        if in_no_mirror:
            no_mirror_codes.update(CODE_RE.findall(line))
    return table_codes, no_mirror_codes


def count_section_rows(registry_text: str, heading: str) -> int:
    """Число строк-таблиц в разделе с заданным заголовком."""
    inside = False
    count = 0
    for line in registry_text.splitlines():
        if line.startswith("## "):
            inside = line.strip() == heading
            continue
        if inside and TABLE_ROW_RE.match(line):
            count += 1
    return count


def collect_knowledge_codes(knowledge_dir: Path, registry_rel: str) -> dict[str, list[str]]:
    """Коды в knowledge/** → список «путь:строка» (реестр исключён)."""
    found: dict[str, list[str]] = {}
    for path in sorted(knowledge_dir.rglob("*.md")):
        rel = path.relative_to(knowledge_dir.parent).as_posix()
        if rel == registry_rel:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for lineno, line in enumerate(text.splitlines(), 1):
            for code in CODE_RE.findall(line):
                found.setdefault(code, []).append(f"{rel}:{lineno}")
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description="Проверка соответствия реестра аудита #336")
    default_root = Path(__file__).resolve().parent.parent
    parser.add_argument("--root", default=str(default_root), help="корень дерева")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    registry_rel = "knowledge/system/infra/audit-336-registry.md"
    registry_path = root / registry_rel
    knowledge_dir = root / "knowledge"

    if not registry_path.is_file():
        print(f"ERROR: реестр не найден: {registry_path}", file=sys.stderr)
        print("       проверка не выполнена.", file=sys.stderr)
        return 2
    if not knowledge_dir.is_dir():
        print(f"ERROR: каталог knowledge/ не найден: {knowledge_dir}", file=sys.stderr)
        print("       проверка не выполнена.", file=sys.stderr)
        return 2

    registry_text = registry_path.read_text(encoding="utf-8")
    table_codes, no_mirror_codes = collect_registry_codes(registry_text)
    # «Зарегистрирован» = код упомянут в реестре в любом разделе: § 3 (таблица
    # кодов слоя docs/features/), § 5 (коды без зеркала) и § 6 (коды, которые
    # никогда не были в слое docs/features/ и живут только в knowledge/).
    registered_codes = set(CODE_RE.findall(registry_text))
    knowledge_codes = collect_knowledge_codes(knowledge_dir, registry_rel)

    violations: list[str] = []

    # Правило A: код в knowledge/, но не зарегистрирован.
    for code in sorted(knowledge_codes, key=lambda c: int(c.split("-")[1])):
        if code not in registered_codes:
            where = knowledge_codes[code]
            shown = ", ".join(where[:3])
            more = f" (+{len(where) - 3})" if len(where) > 3 else ""
            violations.append(
                f"[FAIL] Код {code} не зарегистрирован в реестре аудита "
                f"({registry_rel}). Упоминается в: {shown}{more}"
            )

    # Правило B: код в реестре, но потерян — нет ни в knowledge/, ни в «без зеркала».
    for code in sorted(table_codes, key=lambda c: int(c.split("-")[1])):
        if code in knowledge_codes:
            continue
        if code in no_mirror_codes:
            continue
        violations.append(
            f"[FAIL] Код {code} есть в реестре, но не встречается в knowledge/ "
            f"и не помечен в разделе «Коды без зеркала» — код потерян."
        )

    if violations:
        print(
            f"[FAIL] Реестр аудита не соответствует материалам knowledge/ "
            f"(FR-015): {len(violations)} нарушение(й)."
        )
        print(f"       Реестр: {registry_rel}")
        print("       knowledge/ — единственный слой документации; код из SSoT обязан быть в реестре,")
        print("       а код из реестра — либо встречаться в knowledge/, либо быть помеченным «без зеркала».")
        for v in violations:
            print(f"       {v}")
        return 1

    print(
        f"OK: реестр соответствует knowledge/ "
        f"({count_section_rows(registry_text, '## 3. Таблица кодов')} кодов в таблице § 3, "
        f"{count_section_rows(registry_text, '## 6. Коды аудита, встречающиеся только в `knowledge/`')} в § 6, "
        f"{len(no_mirror_codes)} помечено «без зеркала», "
        f"{len(registered_codes)} зарегистрировано всего, "
        f"{len(knowledge_codes)} кодов встречается в knowledge/)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
