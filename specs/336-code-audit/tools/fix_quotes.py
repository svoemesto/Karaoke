#!/usr/bin/env python3
"""Замена недословных цитат в карточках находок на дословные строки исходников.

Вход: список «не найдено» от tools/verify_quotes.py (фрагмент + карточка).
Для каждого фрагмента ищется ближайшая строка исходника (difflib) и подставляется в карточку.
Запуск с --fix правит файлы находок; без флага — только отчёт о кандидатах.
"""
import csv
import difflib
import os
import re
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIND = os.path.join(BASE, 'findings')
LEDGER = os.path.join(BASE, 'coverage-ledger.csv')
ROOT = os.path.dirname(os.path.dirname(BASE))


def load_lines():
    out = []
    seen = set()
    with open(LEDGER, encoding='utf-8') as f:
        for r in csv.DictReader(f):
            p = r['file_path']
            if p in seen:
                continue
            seen.add(p)
            fp = os.path.join(ROOT, p)
            if not os.path.isfile(fp):
                continue
            try:
                for i, line in enumerate(open(fp, encoding='utf-8', errors='ignore'), 1):
                    s = line.strip()
                    if 8 <= len(s) <= 220:
                        out.append((p, i, s))
            except OSError:
                continue
    return out


def main():
    fix = '--fix' in sys.argv
    lines = load_lines()
    print(f'строк-кандидатов: {len(lines)}')

    # читаем список проблем, порождённый verify_quotes (в формате «  НЕ НАЙДЕНО <fid> (<file>): <frag>»)
    issues = []
    proc = subprocess_run = None
    import subprocess
    res = subprocess.run([sys.executable, os.path.join(BASE, 'tools', 'verify_quotes.py')],
                         capture_output=True, text=True)
    for line in res.stdout.splitlines():
        m = re.match(r'\s*НЕ НАЙДЕНО (\S+) \((.+?)\): (.*)$', line)
        if m:
            issues.append((m.group(1), m.group(2), m.group(3)))
    print(f'проблемных фрагментов: {len(issues)}')

    by_file = {}
    for fid, fn, frag in issues:
        by_file.setdefault(fn, []).append((fid, frag))

    for fn, items in by_file.items():
        path = os.path.join(FIND, fn)
        text = open(path, encoding='utf-8').read()
        changed = 0
        for fid, frag in items:
            core = frag.strip().rstrip(',').rstrip(')').strip()
            if len(core) < 8:
                continue
            cands = difflib.get_close_matches(core, [l[2] for l in lines], n=3, cutoff=0.86)
            if not cands:
                print(f'  ? {fid}: нет близкой строки для: {core[:80]}')
                continue
            best = cands[0]
            if fix:
                if core in text:
                    text = text.replace(core, best)
                    changed += 1
                else:
                    print(f'  ! {fid}: фрагмент не найден в тексте карточки: {core[:60]}')
        if fix and changed:
            open(path, 'w', encoding='utf-8').write(text)
            print(f'  исправлено в {fn}: {changed}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
