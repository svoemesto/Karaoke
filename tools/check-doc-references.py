#!/usr/bin/env python3
"""check-doc-references.py — проверка ссылок и упоминаний в ТЕКУЩЕЙ документации.

Что проверяется в корневых `*.md`, `knowledge/`, `docs/`, `archive/docs/`,
`.github/`, а в `specs/` —
только ссылки (спеки фиксируют прошлое состояние кода, их упоминания файлов
и эндпоинтов не переписываются):

1. **Относительные .md-ссылки** — цель обязана существовать (включая
   `specs/**`; плейсхолдеры вида `<NNN-slug>` не проверяются).
   Пропускаются: http(s)/mailto, root-absolute (`/...` — GitHub резолвит от
   корня репозитория), генерируемые каталоги `docs/api/dokka/` и
   `docs/api/typedoc-*/` (в .gitignore), шаблоны `knowledge/templates/**`
   (в них плейсхолдеры вида `components/component-name.md`).
2. **Пути к файлам кода** в backticks (`Foo.kt`, `webvue3/src/...`) — файл
   обязан существовать. Учитываются `...`-элизии (`karaoke-app/.../Foo.kt`) и
   basename-совпадения (упоминание просто `Foo.kt` разрешается, если файл с
   таким именем есть в репозитории).
3. **Эндпоинты** `/api/...` в backticks — путь обязан находиться в коде
   (учитывается композиция class-level `@RequestMapping` + method-level
   `@GetMapping/@PostMapping` и `{param}`-шаблоны). Прозаичные wildcard'ы
   (`*`, `...`, `$`) не проверяются.

Осознанные исключения — в `config/knowledge/doc-references-whitelist.txt`
(TSV: `scope<TAB>kind<TAB>value<TAB>reason`, kind ∈ path|endpoint|link;
scope — путь .md-файла или префикс каталога). Так исключение видно на ревью,
в отличие от «тихо разросшегося» baseline.

Использование:
    python3 tools/check-doc-references.py            # gate: exit 1 при находках
    python3 tools/check-doc-references.py --list     # перечислить находки с причинами
    python3 tools/check-doc-references.py --stats    # только сводка
"""

from __future__ import annotations

import argparse
import collections
import os
import re
import subprocess
import sys

ROOTS = ("knowledge", "docs", "archive/docs", ".github")
# specs/ — исторические записи о прошлом состоянии кода: проверяем ТОЛЬКО
# ссылки (навигация), но не упоминания файлов и эндпоинтов (они фиксируют,
# как код выглядел на момент спеки, и переписывать их нельзя).
SPECS_ROOT = "specs"
# корневые документы репозитория — тоже текущая документация
ROOT_FILES = ("README.md", "DEVELOPMENT.md", "CONTRIBUTING.md", "AGENTS.md", "CLAUDE.md")
SKIP_DIR_PARTS = ("/.git", "/node_modules", "/build", "/dist", "/.gradle", "/.worktrees")
SKIP_DOC_PARTS = ("knowledge/templates",)
LINK_ONLY_ROOTS = ("specs/",)  # здесь проверяются только ссылки
GENERATED_PREFIXES = ("docs/api/dokka/", "docs/api/typedoc-")
CODE_ROOTS = ("karaoke-app/src", "karaoke-web/src", "webvue3/src", "karaoke-public/src")
CODE_EXT = (
    "kt|java|vue|js|ts|mjs|json|yml|yaml|sql|sh|py|xml|html|properties|conf"
)
WHITELIST_PATH = "config/knowledge/doc-references-whitelist.txt"

PATH_RE = re.compile(rf"`([A-Za-z0-9_][A-Za-z0-9_./-]*\.(?:{CODE_EXT}))`")
API_RE = re.compile(r"`(/api/[A-Za-z0-9_/{}.$:-]+)`")
LINK_RE = re.compile(r"\]\(([^)\s]+\.md)(?:#[^)]*)?\)")


def load_whitelist(path=WHITELIST_PATH):
    """Вернуть список кортежей (scope, kind, value, reason)."""
    entries = []
    if not os.path.exists(path):
        return entries
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 4:
                continue
            entries.append(tuple(parts[:4]))
    return entries


def whitelisted(entries, md, kind, value):
    for scope, k, val, _reason in entries:
        if k != kind or val != value:
            continue
        if md == scope or md.startswith(scope.rstrip("/") + "/"):
            return True
    return False


def build_index():
    """Индекс ФАЙЛОВ ПОД КОНТРОЛЕМ ВЕРСИЙ (git ls-files) — ровно то, что видит CI.

    Важно: untracked/ignored файлы (кэши ML-моделей, локальные `config.json`)
    не должны «спасать» битую ссылку — иначе гейт зелёный локально и красный
    в свежем checkout (прецедент: PR #584, `config.json` из whisper-кэша).
    """
    all_files = []
    try:
        out = subprocess.run(
            ["git", "ls-files"], capture_output=True, text=True, check=True
        ).stdout
        all_files = [p for p in out.splitlines() if p]
    except (OSError, subprocess.CalledProcessError):
        all_files = []
    if not all_files:  # fallback без git
        for dirpath, _dirnames, filenames in os.walk("."):
            if any(part in dirpath for part in SKIP_DIR_PARTS):
                continue
            for f in filenames:
                all_files.append(os.path.normpath(os.path.join(dirpath, f)).lstrip("./"))
    by_base = collections.defaultdict(list)
    for p in all_files:
        by_base[os.path.basename(p)].append(p)
    return all_files, by_base


def path_exists(token, all_files, by_base):
    """Токен существует, если совпал с файлом под контролем версий."""
    tracked = set(all_files)
    if "..." in token:
        tail = token.split("...")[-1].lstrip("/")
        return any(p.endswith(tail) for p in all_files)
    if "/" not in token:
        return bool(by_base.get(os.path.basename(token)))
    if token in tracked:
        return True
    tail = "/".join(token.split("/")[-2:])
    return any(p.endswith(tail) for p in all_files)


def collect_code_paths():
    """Литералы путей из кода: двойные/одинарные кавычки и template literals."""
    literals = set()
    for croot in CODE_ROOTS:
        for dirpath, _dirnames, filenames in os.walk(croot):
            for f in filenames:
                if not f.endswith((".kt", ".vue", ".js", ".ts", ".mjs")):
                    continue
                try:
                    txt = open(os.path.join(dirpath, f), encoding="utf-8", errors="ignore").read()
                except OSError:
                    continue
                for pattern in (r'"(/[A-Za-z0-9_/{}.$:-]*)"', r"'(/[A-Za-z0-9_/{}.$:-]*)'",
                                r"`(/[A-Za-z0-9_/{}.$:-]*)`"):
                    for m in re.findall(pattern, txt):
                        literals.add(m.rstrip("/"))
    return literals


def segments_match(doc_segments, literal_segments):
    if len(doc_segments) != len(literal_segments):
        return False
    for a, b in zip(doc_segments, literal_segments):
        if a.startswith("{") or b.startswith("{"):
            continue
        if a != b:
            return False
    return True


def endpoint_exists(ep, literals):
    base = ep.split("?")[0].rstrip("/")
    if any(ch in base for ch in ("*", "...")):
        return True
    segs = [s for s in base.split("/") if s]
    for lit in literals:
        if segments_match(segs, [s for s in lit.split("/") if s]):
            return True
    # композиция class-level @RequestMapping + method path
    for i in range(1, len(base)):
        if base[i] != "/":
            continue
        pre, suf = base[:i], base[i:]
        if pre in literals and (suf in literals or suf.lstrip("/") in literals):
            return True
        if (pre + "/") in literals and suf in literals:
            return True
    static = [s for s in segs if not s.startswith("{")]
    if static:
        for lit in literals:
            parts = lit.split("/")
            j = 0
            for s in static:
                while j < len(parts) and parts[j] != s:
                    j += 1
                if j == len(parts):
                    break
                j += 1
            else:
                return True
    return False


def docs_files():
    out = [f for f in ROOT_FILES if os.path.exists(f)]
    for root in ROOTS + (SPECS_ROOT,):
        for dirpath, _dirnames, filenames in os.walk(root):
            if any(part in dirpath for part in SKIP_DOC_PARTS):
                continue
            out += [os.path.join(dirpath, f) for f in filenames if f.endswith(".md")]
    return sorted(out)


def main():
    parser = argparse.ArgumentParser(description="Проверка ссылок и упоминаний в документации.")
    parser.add_argument("--list", action="store_true", help="перечислить находки с причинами")
    parser.add_argument("--stats", action="store_true", help="только сводка")
    args = parser.parse_args()

    whitelist = load_whitelist()
    all_files, by_base = build_index()
    literals = collect_code_paths()

    findings = collections.defaultdict(list)  # (kind, md) -> [value]
    checked = {"link": 0, "path": 0, "endpoint": 0}

    for md in docs_files():
        txt = open(md, encoding="utf-8", errors="ignore").read()

        for m in LINK_RE.finditer(txt):
            target = m.group(1)
            if target.startswith(("http", "mailto", "/", "#")):
                continue
            if "<" in target or ">" in target:  # плейсхолдер шаблона, не ссылка
                continue
            checked["link"] += 1
            resolved = os.path.normpath(os.path.join(os.path.dirname(md), target))
            if resolved.startswith(GENERATED_PREFIXES) or os.path.exists(resolved):
                continue
            if whitelisted(whitelist, md, "link", target):
                continue
            findings[("link", md)].append(target)

        is_spec = md.startswith(SPECS_ROOT + "/")

        for m in PATH_RE.finditer(txt):
            if is_spec:
                continue
            token = m.group(1)
            if token.startswith(("http", "/api/", "api/")):
                continue
            # ADR — append-only запись о решении на момент времени: упоминания
            # файлов в историческом тексте не переписываем (ссылки при этом
            # проверяются и чинятся — они про навигацию, а не про факт).
            if "/adr/" in md:
                continue
            if token.startswith(GENERATED_PREFIXES):
                continue
            checked["path"] += 1
            if path_exists(token, all_files, by_base):
                continue
            if whitelisted(whitelist, md, "path", token):
                continue
            findings[("path", md)].append(token)

        for m in API_RE.finditer(txt):
            if is_spec:
                continue
            ep = m.group(1)
            if any(ch in ep for ch in ("*", "...", "$")):
                continue
            checked["endpoint"] += 1
            if endpoint_exists(ep, literals):
                continue
            if whitelisted(whitelist, md, "endpoint", ep):
                continue
            findings[("endpoint", md)].append(ep)

    total = sum(len(v) for v in findings.values())
    if args.stats or not args.list:
        print(
            f"Проверено: ссылок {checked['link']}, путей {checked['path']}, "
            f"эндпоинтов {checked['endpoint']}; whitelist: {len(whitelist)} записей."
        )
    if args.list and findings:
        for (kind, md), values in sorted(findings.items()):
            print(f"{kind.upper():9s} {md}")
            for v in sorted(set(values)):
                print(f"    - {v}")

    if total == 0:
        print("OK: битых ссылок, несуществующих путей и эндпоинтов нет.")
        return 0

    print(f"НАЙДЕНО: {total} проблем(ы) в {len(findings)} записях.")
    if not args.list:
        for (kind, md), values in sorted(findings.items()):
            print(f"  [{kind}] {md}: {', '.join(sorted(set(values))[:5])}")
        print("Подробнее: python3 tools/check-doc-references.py --list")
    return 1


if __name__ == "__main__":
    sys.exit(main())
