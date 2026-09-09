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

@see .specify/templates/spec-template.md § OpenProject Tracking
@see AGENTS.md § Issue-tracker OpenProject
@see specs/349-tracker-must-link/spec.md (Pass 349 governance amendment).
"""

import os
import re
import sys
from pathlib import Path

REQUIRED_SECTION_HEADING = "## OpenProject Tracking"
MODERN_SPEC_MARKER = "## Knowledge References"

# Required field markers in the section. The field name appears as `**Issue ID**:` etc.
REQUIRED_FIELDS = ("Issue ID", "Title", "Workflow")

# Required commands inside Workflow table.
REQUIRED_COMMANDS = (
    "tracker.sh claim-issue",
    "tracker.sh add-comment",
    "tracker.sh mark-review",
)


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
    for field in REQUIRED_FIELDS:
        pattern = (
            rf"(\*\*\s*{re.escape(field)}\s*\*\*"
            rf"|^\#{{2,3}}\s+{re.escape(field)}(?=\s|$|\())"
        )
        if not re.search(pattern, section, re.MULTILINE):
            errors.append(f"missing required field: '{field}' (use `**{field}**` or `### {field}`)")

    # 3. Issue ID — extract value. If `none`, skip command validation.
    #    Robust against various markup formats (`#NN`, `#NN.`, `**NN**:`).
    issue_match = re.search(
        r"\*\*Issue ID\*\*\s*:\s*[`']?(?:#?)([A-Za-z0-9_-]+)\b",
        section,
    )
    issue_id = issue_match.group(1) if issue_match else None

    has_issue = bool(issue_id) and issue_id.lower() != "none"

    if has_issue:
        for cmd in REQUIRED_COMMANDS:
            if cmd not in section:
                errors.append(f"missing required command in Workflow: '{cmd} <NNN>'")

    return errors


def main() -> int:
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
