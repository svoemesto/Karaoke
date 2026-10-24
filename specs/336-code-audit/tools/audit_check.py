#!/usr/bin/env python3
"""Проверки артефактов аудита #336: цитаты находок и покрытие порции.

Использование:
  python3 specs/336-code-audit/tools/audit_check.py quotes findings/P-35.md
  python3 specs/336-code-audit/tools/audit_check.py coverage findings/P-35.md P-35
  python3 specs/336-code-audit/tools/audit_check.py registry
"""
import csv
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))


def parse_records(text):
    """Возвращает список записей: id, path, start, end, cls, sev, quote."""
    head = re.compile(
        r"^###\s+(P-\d+-\d+):\s+(.+?):(\d+)(?:[–\-](\d+))?\s+—\s+(\w+)\s+\((S\d)[^)]*\)",
        re.M,
    )
    matches = list(head.finditer(text))
    out = []
    for i, m in enumerate(matches):
        body = text[m.end(): matches[i + 1].start() if i + 1 < len(matches) else len(text)]
        q = re.search(r"-\s*\*{0,2}Доказательство\*{0,2}:\s*«(.*?)»", body, re.S)
        if q:
            quote = q.group(1)
        else:
            fences = re.findall(r"```[^\n]*\n(.*?)```", body, re.S)
            quote = "\n".join(fences[:3])
        out.append(
            {
                "id": m.group(1),
                "path": m.group(2).strip(),
                "start": int(m.group(3)),
                "end": int(m.group(4) or m.group(3)),
                "cls": m.group(5),
                "sev": m.group(6),
                "quote": quote,
                "body": body,
            }
        )
    return out


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def check_quotes(findings_path):
    text = open(findings_path, encoding="utf-8").read()
    records = parse_records(text)
    ok = bad = 0
    for r in records:
        path = r["path"]
        if not os.path.isabs(path):
            path = os.path.join(ROOT, path)
        if not os.path.isfile(path):
            print(f"FAIL {r['id']}: файл не найден: {r['path']}")
            bad += 1
            continue
        lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
        if r["start"] > len(lines):
            print(f"FAIL {r['id']}: строка {r['start']} вне файла ({len(lines)})")
            bad += 1
            continue
        whole = norm(" ".join(lines))
        extra = []
        for cand in re.findall(r"([\w./-]+\.(?:kt|kts|java|js|ts|vue|sql|yml|yaml|properties|xml|html|sh))(?::\d+)?", r["body"]):
            if cand == r["path"]:
                continue
            full = cand if os.path.isabs(cand) else os.path.join(ROOT, cand)
            if not os.path.isfile(full):
                ledger = os.path.join(ROOT, "specs/336-code-audit/coverage-ledger.csv")
                matches = [x["file_path"] for x in csv.DictReader(open(ledger, encoding="utf-8")) if x["file_path"].endswith(cand)]
                if len(matches) == 1:
                    full = os.path.join(ROOT, matches[0])
            if os.path.isfile(full):
                extra.append(norm(" ".join(open(full, encoding="utf-8", errors="replace").read().splitlines())))
        raw = r["quote"]
        if "`" in raw:
            frags = re.findall(r"`([^`]+)`", raw, re.S)
        else:
            frags = []
            for chunk in re.split(r"\s+/\s+|\n|…|\.\.\.", raw):
                if chunk.strip():
                    frags.append(chunk)
        frags = [norm(x) for x in frags if norm(x)]
        frags = [re.sub(r"^[.\u2026]+\s*|\s*[.\u2026]+$", "", x) for x in frags]
        missing = [x for x in frags if x and x not in whole and not any(x in e for e in extra)]
        if not frags:
            print(f"FAIL {r['id']}: пустая цитата")
            bad += 1
        elif missing:
            print(f"FAIL {r['id']}: цитата не совпала ({missing[0][:90]}…)")
            bad += 1
        else:
            print(f"OK   {r['id']} {r['cls']} {r['sev']} {r['path']}:{r['start']}")
            ok += 1
    print(f"\nИтого: OK {ok}, FAIL {bad}, записей {len(records)}")
    return 1 if bad else 0


def check_coverage(findings_path, portion):
    ledger = os.path.join(ROOT, "specs/336-code-audit/coverage-ledger.csv")
    rows = [r for r in csv.DictReader(open(ledger, encoding="utf-8")) if r["portion_id"] == portion]
    expected = {r["file_path"] for r in rows}
    text = open(findings_path, encoding="utf-8").read()
    sec = re.search(r"^##\s+Покрытие\s*$(.*?)(?=^##\s|\Z)", text, re.M | re.S)
    if not sec:
        print("FAIL: нет раздела «Покрытие»")
        return 1
    found = set()
    for line in sec.group(1).splitlines():
        if not line.strip().startswith("|"):
            continue
        cells = [c.strip().strip("`") for c in line.strip().strip("|").split("|")]
        for cand in cells:
            if cand in expected or re.search(r"\.(kt|kts|java|html|js|ts|vue|sql|yml|yaml|json|properties|xml|sh|conf)$", cand):
                found.add(cand)
    by_base = {}
    for e in expected:
        by_base.setdefault(os.path.basename(e), []).append(e)
    resolved = set()
    for f in found:
        if f in expected:
            resolved.add(f)
        else:
            cands = by_base.get(os.path.basename(f), [])
            if len(cands) == 1:
                resolved.add(cands[0])
            else:
                resolved.add(f)
    found = resolved
    missing = sorted(expected - found)
    extra = sorted(found - expected)
    print(f"{portion}: файлов в ведомости {len(expected)}, в покрытии {len(found)}")
    for m in missing:
        print("MISSING", m)
    for e in extra:
        print("EXTRA  ", e)
    print("OK" if not missing else f"FAIL: не отчитано {len(missing)}")
    return 1 if missing else 0


def check_registry():
    path = os.path.join(ROOT, "specs/336-code-audit/registry.csv")
    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    ids = [r["br_id"] for r in rows]
    keys = [r["root_cause_key"] for r in rows if r["root_cause_key"]]
    dup_ids = sorted({x for x in ids if ids.count(x) > 1})
    dup_keys = sorted({x for x in keys if keys.count(x) > 1})
    print(f"записей: {len(rows)}, дублей br_id: {len(dup_ids)}, дублей root_cause_key: {len(dup_keys)}")
    for d in dup_ids:
        print("DUP ID ", d)
    for d in dup_keys:
        print("DUP KEY", d)
    return 1 if dup_ids or dup_keys else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    cmd = sys.argv[1]
    if cmd == "quotes":
        sys.exit(check_quotes(sys.argv[2]))
    if cmd == "coverage":
        sys.exit(check_coverage(sys.argv[2], sys.argv[3]))
    if cmd == "registry":
        sys.exit(check_registry())
    print("unknown command")
    sys.exit(2)
