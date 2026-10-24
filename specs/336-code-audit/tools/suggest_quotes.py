#!/usr/bin/env python3
"""Подсказки дословных замен для цитат, которые не нашлись на диске."""
import csv, difflib, os, re, subprocess, sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(os.path.dirname(BASE))

lines, seen = [], set()
for r in csv.DictReader(open(os.path.join(BASE, 'coverage-ledger.csv'), encoding='utf-8')):
    p = r['file_path']
    if p in seen:
        continue
    seen.add(p)
    fp = os.path.join(ROOT, p)
    if not os.path.isfile(fp):
        continue
    for i, l in enumerate(open(fp, encoding='utf-8', errors='ignore'), 1):
        s = l.strip()
        if 8 <= len(s) <= 240:
            lines.append((p, i, s))

out = subprocess.run([sys.executable, os.path.join(BASE, 'tools', 'verify_quotes.py')],
                     capture_output=True, text=True, encoding='utf-8').stdout
frs = [(m.group(1), m.group(2), m.group(3).strip())
       for m in re.finditer(r'НЕ НАЙДЕНО (\S+) \((.+?)\): (.*)$', out, re.M)]
print('всего расхождений:', len(frs))
for fid, fn, frag in frs:
    cand = difflib.get_close_matches(frag, [l[2] for l in lines], n=1, cutoff=0.55)
    print(f'### {fid} [{fn}]')
    print(f'   БЫЛО: {frag[:140]}')
    if cand:
        src = [l for l in lines if l[2] == cand[0]][0]
        print(f'   СТАЛО: {cand[0][:140]}   ({src[0]}:{src[1]})')
    else:
        print('   (нет близкой строки)')
