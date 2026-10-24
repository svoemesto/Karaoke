#!/usr/bin/env python3
"""Проверка цитат из карточек находок по диску (полный корпус, один проход).

Для каждой карточки (### P-XX-NN) собирает фрагменты в «…» из поля «Доказательство»
и проверяет, что они встречаются в файлах репозитория (coverage-ledger.csv → 1032 файла).
Корпус нормализуется один раз: сравнение идёт и по «плотному» тексту без пробелов.
Результат: число проверенных фрагментов и список ненайденных (требуют ручной правки).
"""
import csv
import os
import re
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIND = os.path.join(BASE, 'findings')
LEDGER = os.path.join(BASE, 'coverage-ledger.csv')
ROOT = os.path.dirname(os.path.dirname(BASE))

HEAD_RE = re.compile(r'^###\s+(P-\d+[a-z]?-?\d+):\s+([^:]+?):([\d\-–, ]+?)\s+—\s+([A-Z]+)\s*(?:\(([^)]*)\))?\s*$')
FRAG_RE = re.compile(r'«([^»]{4,600})»')
CODE_HINT = re.compile(r'[=(){};]|->|\?\.|\bfun\b|\bval\b|\bif\b|\bimport\b|\bclass\b|\bdef\b')
FILE_REF = re.compile(r'^[\w./-]*\.(kt|js|vue|sql|yml|yaml|conf|properties|json|md|sh|py|txt|css|html)(:\d+[–-]?\d*)?$')
LINE_REF = re.compile(r'^:\d+[–-]?\d*$')


def norm(s):
    return re.sub(r'\s+', ' ', s).strip()


def load_corpus():
    corpus = []
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
                raw = open(fp, encoding='utf-8', errors='ignore').read()
            except OSError:
                continue
            corpus.append(norm(raw))
            corpus.append(re.sub(r'\s+', '', raw))
    return corpus


def main():
    corpus = load_corpus()
    total = 0
    missing = []
    for fn in sorted(os.listdir(FIND)):
        if not fn.endswith('.md'):
            continue
        text = open(os.path.join(FIND, fn), encoding='utf-8').read()
        for blk in re.split(r'\n(?=###\s+P-)', text):
            first = blk.splitlines()[0] if blk.splitlines() else ''
            m = HEAD_RE.match(first)
            if not m:
                continue
            fid = m.group(1)
            ev = re.search(r'^-\s*Доказательство[:：]\s*(.*)$', blk, re.M)
            if not ev:
                continue
            evtext = ev.group(1)
            frags = list(FRAG_RE.findall(evtext))
            frags += re.findall(r'`([^`\n]{8,300})`', evtext)
            if not frags:
                continue
            for fr in frags:
                parts = [x for x in re.split(r'\s+/\s+', fr) if len(norm(x)) >= 6] or [fr]
                for part in parts:
                    needle = norm(part)
                    needle = re.sub(r'\s*\([^)]*(SHA|коммит|commit)[^)]*\)\s*$', '', needle).strip()
                    needle = needle.split('…')[0].split('...')[0].strip()
                    needle = needle.strip('`"«»')
                    if len(needle) < 6:
                        continue
                    if FILE_REF.match(needle) or LINE_REF.match(needle):
                        continue
                    # фрагменты, содержащие ссылку «файл:строка», — это указание места, а не цитата
                    if re.search(r'\.(kt|js|vue|sql|yml|conf|properties|json|md|sh|py|txt|css|html):\d', needle):
                        continue
                    if not CODE_HINT.search(needle):
                        continue
                    if len(needle.split()) > 14:
                        continue
                    # фрагменты с кириллицей — почти всегда проза/пересказ, а не дословная цитата
                    if re.search(r'[\u0400-\u04FF]', needle):
                        continue
                    # команды/логи экспериментов — не цитаты исходника
                    if re.match(r'(curl|grep|python3|node|npm|bash|sh |resolve\(|cd |ls |cat |\./)', needle):
                        continue
                    if needle.endswith(');') or needle.endswith(',') and len(needle) < 12:
                        continue
                    total += 1
                    tight = re.sub(r'\s+', '', needle)
                    if not any(needle in c or tight in c for c in corpus):
                        missing.append((fn, fid, needle[:120]))
    print(f'фрагментов проверено: {total}; не найдено на диске: {len(missing)}')
    for fn, fid, needle in missing:
        print(f'  НЕ НАЙДЕНО {fid} ({fn}): {needle}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
