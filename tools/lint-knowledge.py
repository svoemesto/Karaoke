"""Markdown linter for Living Documentation (knowledge/).

Checks mandatory sections per template (domain/component/adr), emojis,
and structural integrity (L1↔L2↔L3 + Epic linkage).

Supports baseline mode (Pass 347) to allow incremental improvement:
existing violations are recorded in a baseline file and ignored on
subsequent runs; only NEW violations fail the CI gate.

CLI:
    python3 tools/lint-knowledge.py                      # default: enforce
    python3 tools/lint-knowledge.py --baseline FILE      # ignore known violations
    python3 tools/lint-knowledge.py --generate-baseline F # capture current state,
                                                        # write file, exit 0

See also: docs/operations/knowledge-lint-baseline.md (TODO: add doc).
"""

import argparse
import hashlib
import os
import re
import sys


# ---- Mandatory headers (per template) -------------------------------

def get_mandatory_headers_from_template(template_path):
    """Extract mandatory headers from a template file.
    Headers are defined as '## Header Name | Alternative Name'
    """
    headers = []
    try:
        with open(template_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line.startswith("## "):
                    variants = [v.strip() for v in line[3:].split('|')]
                    headers.append(tuple(variants))
    except FileNotFoundError:
        return None
    return headers


# ---- Lint -----------------------------------------------------------

def lint_markdown_file(file_path, mandatory_headers_map):
    """Basic linter for Living Documentation files."""
    errors = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except (FileNotFoundError, UnicodeDecodeError):
        return []

    # Check for emojis (forbidden by project rules).
    # Karaoke-override: ADR files are append-only and may use emojis in
    # historical sections (✅/❌ markers). Skip emoji check for ADR.
    if "adr/" not in file_path:
        emoji_pattern = r'[\U00010000-\U0010ffff]|[✀-➿]|[☀-⛿]'
        if re.search(emoji_pattern, content):
            errors.append("Forbidden characters (potentially emojis) detected.")

    # README files in navigational directories (adr/, domains/, epics/,
    # system/, guidelines/, public/) are indices, not specs. Skip template
    # check for them; the structural audit handles their linking role.
    if os.path.basename(file_path) == "README.md":
        return errors

    # Karaoke-override: ADR files are append-only and may have any historical
    # format (legacy "## Context", new "## Context & Problem | Контекст и
    # проблема", or variants). Skip template mandatory-headers check for ADR;
    # cross-links and structural audit still run.
    if "adr/" in file_path and not os.path.basename(file_path) == "README.md":
        return errors

    # Determine which template to use for this file
    template_type = None
    if "adr/" in file_path:
        template_type = "adr"
    elif "domains/" in file_path:
        if "domain.md" in file_path:
            template_type = "domain"
        elif "components/" in file_path:
            template_type = "component"
        else:
            template_type = None

    if template_type and template_type in mandatory_headers_map:
        variants_list = mandatory_headers_map[template_type]
        for variants in variants_list:
            found = False
            for v in variants:
                if f"## {v}" in content:
                    found = True
                    break
            if not found:
                primary_name = variants[0]
                errors.append(f"Missing mandatory section: {primary_name} (or its aliases)")

    return errors


# ---- Structural audit -----------------------------------------------

def audit_structural_links():
    """
    Verifies structural integrity of documentation levels (L1 -> L2 -> L3)
    and Epic linkage.
    """
    errors = []
    domains_root = 'knowledge/domains'
    readme_path = 'knowledge/README.md'
    epics_dir = 'knowledge/epics'

    if not os.path.exists(domains_root):
        return ["Domains root directory not found."]

    # 1. Check L1 (README) vs L2 (Filesystem)
    if os.path.exists(readme_path):
        with open(readme_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # Extract domain links: [Name](domains/name/domain.md)
            readme_domains = set(re.findall(r'\[.*?\]\(domains/([\w-]+)/domain\.md\)', content))

        fs_domains = set([d for d in os.listdir(domains_root) if os.path.isdir(os.path.join(domains_root, d))])

        missing_in_fs = readme_domains - fs_domains
        missing_in_readme = fs_domains - readme_domains

        for d in missing_in_fs:
            errors.append(f"L1->L2: Domain {d} mentioned in README but missing in filesystem")
        for d in missing_in_readme:
            errors.append(f"L2->L1: Domain {d} exists in filesystem but missing in README")
    else:
        errors.append("L1: knowledge/README.md not found")

    # 2. Check L2 (domain.md) vs L3 (components/*.md)
    for domain in os.listdir(domains_root):
        domain_dir = os.path.join(domains_root, domain)
        if not os.path.isdir(domain_dir):
            continue

        domain_file = os.path.join(domain_dir, 'domain.md')
        components_dir = os.path.join(domain_dir, 'components')

        if not os.path.exists(domain_file):
            errors.append(f"L2: Domain {domain} is missing domain.md")
            continue

        if os.path.exists(components_dir) and os.path.isdir(components_dir):
            with open(domain_file, 'r', encoding='utf-8') as f:
                domain_content = f.read()

            components = [f for f in os.listdir(components_dir) if f.endswith('.md')]
            for comp in components:
                comp_name = comp[:-3] # remove .md
                if comp_name not in domain_content and comp not in domain_content:
                    errors.append(f"L3->L2: Component {comp} in {domain} is NOT mentioned in domain.md")

    # 3. Check Epic Linkage
    if os.path.exists(epics_dir):
        epics = [f for f in os.listdir(epics_dir)
                 if f.endswith('.md') and f != 'README.md']
        for epic in epics:
            found = False
            for root, dirs, files in os.walk(domains_root):
                for file in files:
                    if file.endswith('.md'):
                        try:
                            with open(os.path.join(root, file), 'r', encoding='utf-8', errors='ignore') as f:
                                if epic in f.read():
                                    found = True
                                    break
                        except Exception:
                            continue
                if found: break
            if not found:
                errors.append(f"Epic: {epic} is NOT referenced in any domain documentation")

    return errors


# ---- Baseline support (Pass 347) -----------------------------------

def violation_fingerprint(scope, error_text):
    """Stable hash for one violation. Used for baseline matching.

    `scope` is FILE/SCOPE label (file path or 'STRUCTURAL_INTEGRITY'),
    `error_text` is the message after '- ' marker.
    """
    raw = f"{scope}\x00{error_text}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()[:16]


def load_baseline(path):
    """Read baseline fingerprints. Returns set."""
    if not path or not os.path.exists(path):
        return set()
    with open(path, 'r', encoding='utf-8') as f:
        # Format: one fingerprint per line; lines starting with '#' are
        # comments and ignored.
        return {line.strip() for line in f
                if line.strip() and not line.startswith('#')}


def write_baseline(path, fingerprints):
    """Write set of fingerprints to baseline file with header comment."""
    os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write("# Knowledge lint baseline (Pass 347, spec #344 follow-up)\n")
        f.write("# Generated by: python3 tools/lint-knowledge.py --generate-baseline <file>\n")
        f.write("# Each line = SHA-256[:16] of '<scope>\\x00<error-message>'.\n")
        f.write("# Existing violations — pass CI but should be fixed incrementally.\n")
        f.write("# DO NOT edit manually; regenerate via --generate-baseline.\n")
        f.write(f"# Captures {len(fingerprints)} fingerprints at regeneration time.\n")
        f.write("\n")
        for fp in sorted(fingerprints):
            f.write(fp + "\n")


def collect_all_violations(mandatory_headers_map):
    """Run all checks; return dict {scope: [error, ...]} for ALL violations
    (regardless of baseline).
    """
    docs_dir = "knowledge"
    all_errors = {}

    if not os.path.exists(docs_dir):
        return {"MISSING_DOCS": [f"{docs_dir} directory not found."]}

    # 1. File-by-file linting
    for root, dirs, files in os.walk(docs_dir):
        for file in files:
            if file.endswith(".md"):
                if "templates/" in root:
                    continue
                full_path = os.path.join(root, file)
                errors = lint_markdown_file(full_path, mandatory_headers_map)
                if errors:
                    all_errors[full_path] = errors

    # 2. Structural audit
    structural_errors = audit_structural_links()
    if structural_errors:
        all_errors["STRUCTURAL_INTEGRITY"] = structural_errors

    return all_errors


def expand_to_fingerprints(all_errors):
    """Convert {scope: [err, ...]} into set of fingerprints."""
    fps = set()
    for scope, errs in all_errors.items():
        for e in errs:
            fps.add(violation_fingerprint(scope, e))
    return fps


def filter_against_baseline(all_errors, baseline_fps):
    """Remove violations whose fingerprint is in baseline; return new dict."""
    filtered = {}
    for scope, errs in all_errors.items():
        new_errs = [e for e in errs
                    if violation_fingerprint(scope, e) not in baseline_fps]
        if new_errs:
            filtered[scope] = new_errs
    return filtered


# ---- Main ----------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Lint Living Documentation.")
    parser.add_argument(
        '--baseline',
        default=None,
        help='File with known violation fingerprints to ignore (Pass 347+).',
    )
    parser.add_argument(
        '--generate-baseline',
        metavar='FILE',
        default=None,
        help='Capture all current violations as baseline, write to FILE, '
             'exit 0. Use after fixing new violations to regenerate.',
    )
    args = parser.parse_args()

    docs_dir = "knowledge"
    template_dir = "knowledge/templates"

    # Map template files to their internal type identifier
    template_map = {
        "adr.md": "adr",
        "domain.md": "domain",
        "component.md": "component",
    }

    mandatory_headers_map = {}
    for template_file, type_id in template_map.items():
        path = os.path.join(template_dir, template_file)
        headers = get_mandatory_headers_from_template(path)
        if headers:
            mandatory_headers_map[type_id] = headers
        else:
            print(f"Warning: Could not load mandatory headers from {path}", file=sys.stderr)

    all_errors = collect_all_violations(mandatory_headers_map)

    # --- Generate-baseline mode ---
    if args.generate_baseline:
        fps = expand_to_fingerprints(all_errors)
        write_baseline(args.generate_baseline, fps)
        print(f"Baseline written to {args.generate_baseline}: {len(fps)} fingerprints.")
        # Summary by file for human review.
        for scope, errs in sorted(all_errors.items()):
            print(f"  {scope}: {len(errs)} violation(s)")
        sys.exit(0)

    # --- Normal mode (with optional baseline) ---
    baseline_fps = load_baseline(args.baseline)
    if baseline_fps:
        before = sum(len(e) for e in all_errors.values())
        all_errors = filter_against_baseline(all_errors, baseline_fps)
        after = sum(len(e) for e in all_errors.values())
        ignored = before - after
        print(f"Baseline: {len(baseline_fps)} known fingerprint(s) ignored "
              f"({ignored} violation(s) suppressed).")

    if all_errors:
        for file, errs in all_errors.items():
            print(f"FILE/SCOPE: {file}")
            for e in errs:
                print(f"  - {e}")
        sys.exit(1)
    else:
        if baseline_fps:
            print("All NEW violations are absent. "
                  "Baseline check PASSED (existing violations ignored).")
        else:
            print("All documentation passes architectural style and structural checks.")
        sys.exit(0)


if __name__ == "__main__":
    main()
