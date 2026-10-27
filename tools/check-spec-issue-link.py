#!/usr/bin/env python3
"""Validate that every modern spec.md has the mandatory OpenProject Tracking section.

Scope policy (Pass 349 governance amendment):
* Specs WITHOUT `## Knowledge References` section = pre-Phase-002 (old),
  grandfathered — НЕ требуется OpenProject Tracking.
* Specs WITH `## Knowledge References` (Phase-002+ modern) — ОБЯЗАНЫ
  иметь `## OpenProject Tracking` секцию с required fields и Workflow.
* Within modern specs: if Issue ID = `none`, claim/mark-review NOT required
  (spontaneous work allowed).

Used by `.github/workflows/lint.yml` (spec-tracker gate).
Exit 0 = OK, exit 1 = FAIL with detailed report.

BR-596 (аудит #336, пакет PKG-S3-30): guard ПРИНИМАЛ два формата поля
Issue ID — жирный `**Issue ID**` и заголовок `### Issue ID`, — но значение
извлекал ТОЛЬКО из жирного формата. Спека с заголовком проходила проверку
«поле есть», а Issue ID оставался None → `has_issue` = False → обязательные
команды Workflow (`tracker.sh claim-issue/add-comment/mark-review`) НЕ
проверялись. Guard fail-open на целый класс спек. Теперь значение
извлекается в обоих форматах, а поле без значения — ошибка, а не «пропуск».

@see .specify/templates/spec-template.md § OpenProject Tracking
@see AGENTS.md § Issue-tracker OpenProject
@see specs/349-tracker-must-link/spec.md (Pass 349 governance amendment).
"""

import os
import re
import sys
import tempfile
from pathlib import Path

REQUIRED_SECTION_HEADING = "## OpenProject Tracking"
MODERN_SPEC_MARKER = "## Knowledge References"

# Required field markers in the section. The field name appears as `**Issue ID**:` etc.
REQUIRED_FIELDS = ("Issue ID", "Title", "Workflow")

# Образец значения Issue ID. T4: добавлена форма OpenProject ``op:403``
# (двоеточие с числом); формы ``KARAOKE-42``, ``42``, ``#42``, ``none``
# принимались и раньше. Формы ссылок не расширяются дальше — полный URL
# задаче в поле Issue ID не поддерживается намеренно.
ISSUE_VALUE_RE = r"([A-Za-z0-9_-]+(?::[0-9]+)?)\b"

# Required commands inside Workflow table.
REQUIRED_COMMANDS = (
    "tracker.sh claim-issue",
    "tracker.sh add-comment",
    "tracker.sh mark-review",
)


def extract_issue_id(section: str) -> str | None:
    """Извлечь Issue ID из секции OpenProject Tracking.

    Поддерживаются оба формата, которые guard принимает как «поле есть»:
      1. Жирный: ``**Issue ID**: 402`` / ``**Issue ID**: `#402` `` / ``none``.
      2. Заголовок: ``### Issue ID`` — значение в той же строке
         (``### Issue ID: 402``) или на следующих непустых строках.

    Значение принимается в формах, которые понимает CLI трекера на Plane:
      * ``KARAOKE-42`` — ссылка Plane (проект KARAOKE, sequence 42);
      * ``42`` / ``#42`` — номер задачи (sequence_id) по умолчанию;
      * ``op:403`` — номер OpenProject (разрешается таблицей соответствия);
      * ``none`` — задачи нет (спонтанная работа, Workflow не требуется).

    BR-596: раньше поддерживался только формат (1) — спека с форматом (2)
    проходила проверку наличия поля, но её Issue ID не извлекался, и
    обязательные Workflow-команды не проверялись (fail-open).
    """
    # 1) Жирный формат.
    match = re.search(
        r"\*\*\s*Issue ID\s*\*\*\s*:\s*[`']?(?:#?)" + ISSUE_VALUE_RE,
        section,
    )
    if match:
        return match.group(1)

    # 2) Формат заголовка (## / ### Issue ID).
    heading = re.search(r"^\#{2,3}\s+Issue ID\b[^\S\n]*(.*)$", section, re.MULTILINE)
    if not heading:
        return None

    # Значение в той же строке (после «:», «-», «—» или просто пробел).
    tail = heading.group(1).lstrip(" \t:-\u2014\u2013")
    value = re.match(r"[`']?(?:#?)" + ISSUE_VALUE_RE, tail)
    if value:
        return value.group(1)

    # Значение — на следующих непустых строках секции.
    for line in section[heading.end():].split("\n")[1:]:
        if not line.strip():
            break
        value = re.match(r"\s*(?:[-*]\s*)?[`']?(?:#?)" + ISSUE_VALUE_RE, line)
        if value:
            return value.group(1)
    return None


def check_spec(spec_path: Path) -> list[str]:
    """Return list of error messages (empty if OK)."""
    errors: list[str] = []
    try:
        text = spec_path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError) as e:
        return [f"cannot read: {e}"]

    # Scope gate: only enforce on "modern" specs (Phase-002+, with
    # `## Knowledge References` per Constitution Principle IX).
    if MODERN_SPEC_MARKER not in text:
        # Pre-modern spec — grandfathered.
        return []

    # 1. Heading
    if REQUIRED_SECTION_HEADING not in text:
        errors.append(f"missing required heading: '{REQUIRED_SECTION_HEADING}'")
        return errors

    # Extract the section content (from heading to next `## `).
    section_start = text.index(REQUIRED_SECTION_HEADING)
    rest_after_heading = text[section_start + len(REQUIRED_SECTION_HEADING):]
    next_heading = re.search(r"\n## ", rest_after_heading)
    section_end = (section_start + len(REQUIRED_SECTION_HEADING)
                   + (next_heading.start() if next_heading else len(rest_after_heading)))
    section = text[section_start:section_end]

    # 2. Required field markers (`**FieldName**` markdown bold OR `### <FieldName>` /
    #    `## <FieldName>` heading possibly followed by other text on same line).
    #    BR-596: после имени поля в заголовке допустим разделитель «:», «-», «—»
    #    (например `### Issue ID: 402`) — иначе поле в формате заголовка
    #    сначала считалось бы «отсутствующим».
    for field in REQUIRED_FIELDS:
        pattern = (
            rf"(\*\*\s*{re.escape(field)}\s*\*\*"
            rf"|^\#{{2,3}}\s+{re.escape(field)}(?=\s|$|\(|[:\-—]))"
        )
        if not re.search(pattern, section, re.MULTILINE):
            errors.append(f"missing required field: '{field}' (use `**{field}**` or `### {field}`)")

    # 3. Issue ID — извлекаем значение В ОБОИХ поддерживаемых форматах
    #    (BR-596): жирный `**Issue ID**: NNN` и заголовок `### Issue ID`.
    #    Раньше извлекался только жирный формат, поэтому спека с заголовком
    #    молча проходила проверку Workflow-команд.
    issue_id = extract_issue_id(section)

    if issue_id is None:
        # Поле присутствует (оно проверено выше), но значение не извлеклось —
        # это не «нет issue», а неполная секция. Раньше это выглядело как
        # «issue нет → Workflow не нужен».
        errors.append(
            "cannot extract Issue ID value (expected `**Issue ID**: <value>` "
            "or `### Issue ID` + value)"
        )
        return errors

    has_issue = issue_id.lower() != "none"

    if has_issue:
        for cmd in REQUIRED_COMMANDS:
            if cmd not in section:
                errors.append(f"missing required command in Workflow: '{cmd} <NNN>'")

    return errors


def run_self_test() -> int:
    """Фикстуры «до/после» (BR-596, T4)."""
    failures = 0
    checks = 0

    def expect(name: str, section: str, want_issue: str | None) -> None:
        nonlocal failures, checks
        checks += 1
        got = extract_issue_id(section)
        if got != want_issue:
            print(
                f"  FAIL {name}: ожидали Issue ID {want_issue!r}, получено {got!r}",
                file=sys.stderr,
            )
            failures += 1
        else:
            print(f"  ok   {name} → {got!r}")

    nl = "\n"
    expect("жирный формат", "**Issue ID**: 402" + nl, "402")
    expect("жирный формат с решёткой", "**Issue ID**: `#402`" + nl, "402")
    expect("жирный формат none", "**Issue ID**: `none`" + nl, "none")
    expect(
        "заголовок, значение в той же строке",
        "### Issue ID: 402" + nl,
        "402",
    )
    expect(
        "заголовок, значение на следующей строке",
        "### Issue ID" + nl + "402" + nl + nl + "**Title**: x" + nl,
        "402",
    )
    expect(
        "заголовок ##, значение с решёткой",
        "## Issue ID" + nl + "`#77`" + nl,
        "77",
    )
    # T4: формы ссылок Plane/OpenProject (секции привязки).
    expect("Plane KARAOKE-42", "**Issue ID**: KARAOKE-42" + nl, "KARAOKE-42")
    expect(
        "Plane KARAOKE-42 в заголовке",
        "### Issue ID: KARAOKE-42" + nl,
        "KARAOKE-42",
    )
    expect("Plane KARAOKE-42 в обратных кавычках", "**Issue ID**: `KARAOKE-42`" + nl, "KARAOKE-42")
    expect("OpenProject op:403", "**Issue ID**: op:403" + nl, "op:403")
    expect(
        "OpenProject op:403 в заголовке",
        "### Issue ID: `op:403`" + nl,
        "op:403",
    )
    expect("нет поля", "**Title**: x" + nl, None)

    # Спека с заголовком должна реально проверять Workflow-команды (BR-596).
    with tempfile.TemporaryDirectory() as tmp:
        spec = Path(tmp) / "spec.md"
        spec.write_text(
            "## Knowledge References" + nl + nl
            + "- knowledge/" + nl + nl
            + "## OpenProject Tracking" + nl + nl
            + "### Issue ID: 402" + nl + nl
            + "**Title**: x" + nl + nl
            + "### Workflow" + nl + nl
            + "| Step | Command |" + nl + "|---|---|" + nl
            + "| claim | `tracker.sh claim-issue 402` |" + nl,
            encoding="utf-8",
        )
        checks += 1
        errs = check_spec(spec)
        # Спека объявляет Issue ID = 402, но содержит только команду claim.
        # Если Workflow-команды реально проверяются — guard обязан сообщить
        # про недостающие add-comment/mark-review. Если не сообщает — fail-open.
        missing = [e for e in errs if "missing required command" in e]
        if missing:
            print(
                "  ok   заголовок-формат: Workflow-команды проверяются "
                f"({len(missing)} недостающих найдено)"
            )
        else:
            print(
                "  FAIL заголовок-формат: Workflow-команды не проверяются (fail-open)",
                file=sys.stderr,
            )
            failures += 1

    # T4: ссылка Plane в заголовке тоже требует Workflow-команды.
    with tempfile.TemporaryDirectory() as tmp:
        spec = Path(tmp) / "spec.md"
        spec.write_text(
            "## Knowledge References" + nl + nl
            + "- knowledge/" + nl + nl
            + "## OpenProject Tracking" + nl + nl
            + "### Issue ID: KARAOKE-42" + nl + nl
            + "**Title**: x" + nl + nl
            + "### Workflow" + nl + nl
            + "| Step | Command |" + nl + "|---|---|" + nl
            + "| claim | `tracker.sh claim-issue 42` |" + nl,
            encoding="utf-8",
        )
        checks += 1
        errs = check_spec(spec)
        missing = [e for e in errs if "missing required command" in e]
        if missing:
            print(
                "  ok   KARAOKE-42 в заголовке: Workflow-команды проверяются "
                f"({len(missing)} недостающих найдено)"
            )
        else:
            print(
                "  FAIL KARAOKE-42 в заголовке: Workflow-команды не проверяются (fail-open)",
                file=sys.stderr,
            )
            failures += 1

    # T4: Issue ID = none не требует Workflow (прежнее поведение).
    with tempfile.TemporaryDirectory() as tmp:
        spec = Path(tmp) / "spec.md"
        spec.write_text(
            "## Knowledge References" + nl + nl
            + "- knowledge/" + nl + nl
            + "## OpenProject Tracking" + nl + nl
            + "**Issue ID**: `none`" + nl + nl
            + "**Title**: x" + nl + nl
            + "**Workflow**" + nl + nl
            + "нет задач" + nl,
            encoding="utf-8",
        )
        checks += 1
        errs = check_spec(spec)
        if not errs:
            print("  ok   Issue ID none: Workflow не требуется")
        else:
            print(
                f"  FAIL Issue ID none: неожиданные ошибки {errs}",
                file=sys.stderr,
            )
            failures += 1

    if failures:
        print(f"SELFTEST FAIL: {failures} из {checks} фикстур не совпали", file=sys.stderr)
        return 1
    print(f"SELFTEST OK: {checks}/{checks} фикстур совпали")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return run_self_test()
    repo_root = Path(os.environ.get("REPO_ROOT", "."))
    specs_dir = repo_root / "specs"
    if not specs_dir.exists():
        print(f"ERROR: specs dir not found: {specs_dir}", file=sys.stderr)
        return 1

    all_errors: dict[Path, list[str]] = {}
    spec_count = 0
    modern_count = 0
    for spec_md in sorted(specs_dir.glob("*/spec.md")):
        spec_count += 1
        try:
            text = spec_md.read_text(encoding="utf-8")
            modern = MODERN_SPEC_MARKER in text
        except Exception:
            modern = False
        if modern:
            modern_count += 1
        errs = check_spec(spec_md)
        if errs:
            all_errors[spec_md.relative_to(repo_root)] = errs

    if all_errors:
        print(
            f"FAIL: {len(all_errors)}/{modern_count} modern spec(s) missing OpenProject Tracking metadata",
            file=sys.stderr,
        )
        print(
            f"      (total scanned: {spec_count}; modern: {modern_count}; "
            f"grandfathered pre-Phase-002: {spec_count - modern_count})",
            file=sys.stderr,
        )
        for path, errs in all_errors.items():
            print(f"  {path}:", file=sys.stderr)
            for e in errs:
                print(f"    - {e}", file=sys.stderr)
        return 1

    print(
        f"OK: {modern_count}/{modern_count} modern spec(s) have OpenProject Tracking section "
        f"({spec_count - modern_count} grandfathered pre-Phase-002)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
