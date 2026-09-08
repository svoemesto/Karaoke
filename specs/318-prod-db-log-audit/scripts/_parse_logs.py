#!/usr/bin/env python3
"""scripts/_parse_logs.py

Парсер + категоризатор логов karaoke-db. Использует Python re для надёжной
работы с POSIX ERE (mawk не подходит для сложных regex — см. _lib.sh).
См. spec.md FR-002, research.md R-4.

Использование: python3 _parse_logs.py --input raw.log --categories categories.json
                       --out-jsonl logs.jsonl --out-warnings parse-warnings.log
                       --out-unclassified unclassified-samples.txt
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# Patterns
RE_TS_ISO = re.compile(
    r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z)\s+(.*)$'
)
RE_TS_PG = re.compile(
    r'^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:\s+\w+)?)\s+(.*)$'
)
RE_LEVEL_PID = re.compile(
    r'\[\d+(?:-\d+)?\]\s+(ERROR|FATAL|PANIC|WARNING|LOG|STATEMENT|INFO|DEBUG)[:\s]'
)
# Standalone level without [NNN] (e.g., "FATAL:  could not connect")
RE_LEVEL_STANDALONE = re.compile(
    r'\b(ERROR|FATAL|PANIC|WARNING|STATEMENT)[:\s]'
)
RE_LEVEL_KV = re.compile(
    r'level=(error|fatal|panic|warning|log|statement|info|debug)', re.I
)

# Sanitization (research R-3)
RE_PASS = re.compile(r'(password|passwd)[\s]*[=:][\s]*\S+', re.I)
RE_PGURL = re.compile(r'(postgres(?:ql)?://[^:]+:)[^@]+(@)')
RE_BEARER = re.compile(r'(Bearer)\s+[A-Za-z0-9._-]+')
RE_PAT = re.compile(r'(pat_)[A-Za-z0-9_-]+')
RE_TOKEN = re.compile(r'(token|secret)[\s]*[=:][\s]*[A-Za-z0-9._-]+', re.I)


def sanitize(line: str) -> str:
    line = RE_PASS.sub(r'\1=[REDACTED:password]', line)
    line = RE_PGURL.sub(r'\1[REDACTED:pgpass]\2', line)
    line = RE_BEARER.sub(r'\1 [REDACTED:bearer]', line)
    line = RE_PAT.sub(r'\1[REDACTED:pat]', line)
    line = RE_TOKEN.sub(r'\1=[REDACTED:token]', line)
    return line


def parse_line(line: str) -> tuple[str | None, str, str]:
    """Return (timestamp, level, rest). timestamp=None if unparseable."""
    m = RE_TS_ISO.match(line)
    if m:
        return m.group(1), 'LOG', m.group(2)
    m = RE_TS_PG.match(line)
    if m:
        ts = m.group(1).replace(' ', 'T')
        if not ts.endswith('Z'):
            # Append Z if not present (UTC indicator)
            ts = ts + 'Z'
        return ts, 'LOG', m.group(2)

    # Unparseable timestamp — keep whole line as rest
    return None, 'UNKNOWN', line


def extract_level(rest: str) -> str:
    m = RE_LEVEL_PID.search(rest)
    if m:
        return m.group(1)
    m = RE_LEVEL_STANDALONE.search(rest)
    if m:
        return m.group(1)
    m = RE_LEVEL_KV.search(rest)
    if m:
        return m.group(1).upper()
    return 'LOG'


def load_rules(path: Path) -> tuple[list[tuple[re.Pattern, str]], str]:
    data = json.loads(path.read_text())
    rules = sorted(data.get('rules', []), key=lambda r: r.get('priority', 999))
    compiled = [(re.compile(r['pattern']), r['category']) for r in rules]
    default = data.get('default_category', '__unclassified__')
    return compiled, default


def categorize(rest: str, rules, default):
    for pat, cat in rules:
        if pat.search(rest):
            return cat
    return default


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', required=True)
    ap.add_argument('--categories', required=True)
    ap.add_argument('--out-jsonl', required=True)
    ap.add_argument('--out-warnings', required=True)
    ap.add_argument('--out-unclassified', required=True)
    args = ap.parse_args()

    rules, default = load_rules(Path(args.categories))

    n_total = 0
    n_warn = 0
    unclassified_set: set[str] = set()

    with open(args.input, 'r', encoding='utf-8', errors='replace') as fin, \
         open(args.out_jsonl, 'w', encoding='utf-8') as fout_jsonl, \
         open(args.out_warnings, 'w', encoding='utf-8') as fout_warn:

        for lineno, raw in enumerate(fin, start=1):
            raw = raw.rstrip('\n')
            n_total += 1
            sanitized = sanitize(raw)
            ts, default_level, rest = parse_line(sanitized)
            if ts is None:
                n_warn += 1
                fout_warn.write(f'line {lineno}: {raw}\n')
                rest_full = sanitized
            else:
                rest_full = rest

            level = extract_level(rest) if ts else default_level
            cat = categorize(rest, rules, default)
            if cat == '__unclassified__':
                unclassified_set.add(rest_full)

            truncated = len(rest_full) > 4096
            if truncated:
                rest_full = rest_full[:4096]

            rec = {
                'timestamp': ts,
                'level': level,
                'message_category': cat,
                'raw_message': rest_full,
                'source_line_number': lineno,
                'raw_message_truncated': truncated,
            }
            fout_jsonl.write(json.dumps(rec, ensure_ascii=False) + '\n')

            if n_total % 50000 == 0:
                print(f'  ... processed {n_total} lines', file=sys.stderr)

    # Write unclassified samples
    with open(args.out_unclassified, 'w', encoding='utf-8') as f:
        for s in sorted(unclassified_set)[:20]:
            f.write(s + '\n')

    print(f'Processed {n_total} lines, {n_warn} parse warnings, {len(unclassified_set)} unique unclassified', file=sys.stderr)


if __name__ == '__main__':
    main()