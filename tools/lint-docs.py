import os
import sys
import re

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

def lint_markdown_file(file_path, mandatory_headers_map):
    """Basic linter for Living Documentation files."""
    errors = []
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except (FileNotFoundError, UnicodeDecodeError):
        return []

    # Check for emojis (forbidden by project rules)
    emoji_pattern = r'[\U00010000-\U0010ffff]|[✀-➿]|[☀-⛿]'
    if re.search(emoji_pattern, content):
        errors.append("Forbidden characters (potentially emojis) detected.")

    # README files in navigational directories (adr/, domains/, epics/,
    # system/, guidelines/, public/) are indices, not specs. Skip template
    # check for them; the structural audit handles their linking role.
    if os.path.basename(file_path) == "README.md":
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

def audit_structural_links():
    """
    Verifies structural integrity of documentation levels (L1 -> L2 -> L3)
    and Epic linkage.
    """
    errors = []
    domains_root = 'docs/domains'
    readme_path = 'docs/README.md'
    epics_dir = 'docs/epics'

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
        errors.append("L1: docs/README.md not found")

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

def main():
    docs_dir = "docs"
    template_dir = "docs/templates"

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

    all_errors = {}

    if not os.path.exists(docs_dir):
        print(f"Error: {docs_dir} directory not found.")
        sys.exit(1)

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

    if all_errors:
        for file, errs in all_errors.items():
            print(f"FILE/SCOPE: {file}")
            for e in errs:
                print(f"  - {e}")
        sys.exit(1)
    else:
        print("All documentation passes architectural style and structural checks.")
        sys.exit(0)

if __name__ == "__main__":
    main()
