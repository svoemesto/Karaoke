#!/usr/bin/env python3
"""Сборка ТЗ для задач-правок и заведение work packages в OpenProject.

Вход:  specs/336-code-audit/registry.csv + findings/P-*.md (карточки)
Выход: specs/336-code-audit/work-packages/<BR>.md (ТЗ) + work package в трекере
       + колонка work_package в registry.csv

Использование:
  python3 tools/make_work_packages.py --wave A           # завести задачи волны A
  python3 tools/make_work_packages.py --br BR-011 BR-019  # точечно
  python3 tools/make_work_packages.py --wave A --dry-run # только сгенерировать ТЗ
"""
from __future__ import annotations

import argparse
import csv
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))
BASE = os.path.join(ROOT, 'specs/336-code-audit')
REG = os.path.join(BASE, 'registry.csv')
FIND = os.path.join(BASE, 'findings')
WP_DIR = os.path.join(BASE, 'work-packages')
TRACKER = os.path.join(ROOT, 'tools/tracker.sh')
PROJECT_ID = '3'
TYPE_ID = '1'  # Task

FIELD_RE = re.compile(r'^- \*{0,2}([^:]{2,40}?)\*{0,2}\s*:\s*(.*)$')


def card_for(br: str, reg: list[dict]) -> tuple[str, str] | tuple[None, None]:
    """(имя файла порции, текст карточки) для BR — по паре (файл, строка)."""
    r = next((x for x in reg if x['br_id'] == br), None)
    if not r:
        return None, None
    pid = r['portion']
    base = re.sub(r'[a-z]$', '', pid)
    cands = {pid, base, base + 'a', base + 'b'}
    for fn in sorted(os.listdir(FIND)):
        if not fn.endswith('.md'):
            continue
        p = fn[:-3]
        if p not in cands:
            continue
        text = open(os.path.join(FIND, fn), encoding='utf-8').read()
        for card in re.split(r'\n### ', text)[1:]:
            head = card.split('\n', 1)[0]
            m = re.match(r'^(?:###\s*)?(P-[\w\-]+?):\s*(.+?)\s+—\s+([A-Z]+)\s*\((\w+)\)', head)
            if not m:
                continue
            loc = m.group(2).strip()
            m2 = re.match(r'^(.*?):([0-9]+.*)$', loc)
            if not m2:
                continue
            path = m2.group(1).strip()
            nums = [int(x) for x in re.findall(r'\d+', m2.group(2))]
            same_file = (r['file'] == path) or (r['file'].split('/')[-1] == path.split('/')[-1])
            if same_file and nums and abs(int(r['line_start']) - nums[0]) <= 2:
                return fn, card
    return None, None


def field(card: str, name: str) -> str:
    """Значение поля карточки (может быть многострочным)."""
    out, capture = [], False
    for line in card.split('\n'):
        m = FIELD_RE.match(line)
        if m:
            key = m.group(1).strip().lower()
            capture = key.startswith(name)
            if capture:
                out.append(m.group(2).strip())
            continue
        if capture:
            if line.startswith('```') or line.strip() == '':
                if out and line.strip() == '':
                    break
                continue
            out.append(line.rstrip())
    return '\n'.join(out).strip()


def build_desc(br: str, r: dict, card: str, fn: str) -> tuple[str, str]:
    what = field(card, 'что не так')
    proof = field(card, 'доказательство')
    path = field(card, 'путь')
    fix = field(card, 'поправка')
    author = field(card, 'автор')
    sev = field(card, 'severity')
    title = re.sub(r'\s+', ' ', what).strip().strip('*').strip()
    title = (title[:90].rstrip(' ,;:.') + '…') if len(title) > 90 else title
    subject = f"[#336] {r['severity']}: {title} ({br})"
    fid_m = re.match(r'^(P-[\w\-]+?):', card.split('\n', 1)[0].strip())
    fid = fid_m.group(1) if fid_m else '(не определена)'
    desc = f"""Задача: устранение дефекта **{br}** ({r['severity']}) из полного аудита кода (#336).

## Что не так

{what}

## Доказательство

{proof}

## Путь достижимости

{path}

## Предлагаемая поправка

{fix}

## Автор (git blame)

{author}

Severity: {sev}

## Источник

- Аудит: **#336** (карта #337), отчёт `specs/336-code-audit/bug-report.md` — PR #646, ветка `553-336-code-audit`
- Карточка находки: `specs/336-code-audit/findings/{fn}` → запись {fid}
- Реестр: `specs/336-code-audit/registry.csv` → {br}, корень `{r['root_cause_key']}`
- Файл:строки: `{r['file']}:{r['line_start']}`

## Критерии приёмки

- [ ] Дефект устранён по «Предлагаемой поправке» (или владелец утвердил иной вариант)
- [ ] Есть регрессионный тест «до/после»: тест падает на старом коде и проходит на новом
- [ ] CI 7/7 PASS (линтеры, guard'ы, сборка)
- [ ] Прод-деплой — только с согласия владельца (матрица `nsa-i9`: рестарт `karaoke-app` — по согласию)
- [ ] Карточка аудита переведена в статус «исправлено», в `registry.csv` проставлен номер этой задачи

## Режим работы

Полный цикл: `speckit-specify` → **утверждение спеки владельцем** → план/задачи → реализация →
ревью лида по фактам на диске → PR → `claim` → `add-comment` (report.md) → `mark-review` → `close`.

## Границы

- Код, конфиги и БД в рамках задачи правятся только в своей ветке; прод-данные не трогаются без согласия владельца.
- Деплой на прод — отдельным решением владельца.
"""
    return subject, desc


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--wave', default='')
    ap.add_argument('--br', nargs='*', default=[])
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    reg = list(csv.DictReader(open(REG, encoding='utf-8')))
    os.makedirs(WP_DIR, exist_ok=True)

    targets: list[str] = list(args.br)
    if args.wave:
        backlog = open(os.path.join(BASE, 'backlog.md'), encoding='utf-8').read()
        for m in re.finditer(r'### (PKG-[\w\-]+) — (.*?)\n\n(.*?)(?=\n### |\n## )', backlog, re.S):
            if f'**Волна:** {args.wave} ' in m.group(3) or f'**Волна:** {args.wave}\n' in m.group(3):
                targets += re.findall(r'\| (BR-\d+) \|', m.group(3))
    if not targets:
        print('нечего делать: укажите --wave или --br')
        return

    for br in targets:
        r = next((x for x in reg if x['br_id'] == br), None)
        if not r:
            print(f'{br}: нет в реестре')
            continue
        fn, card = card_for(br, reg)
        if not card:
            print(f'{br}: карточка не найдена')
            continue
        subject, desc = build_desc(br, r, card, fn)
        path = os.path.join(WP_DIR, f'{br}.md')
        open(path, 'w', encoding='utf-8').write(f'# {subject}\n\n{desc}')
        if args.dry_run:
            print(f'[dry] {br} → {path}\n    {subject}')
            continue
        out = subprocess.run(
            [TRACKER, 'create-issue', '--project-id', PROJECT_ID, '--type-id', TYPE_ID,
             '--subject', subject, '--description', desc],
            capture_output=True, text=True, cwd=ROOT)
        text = (out.stdout + out.stderr).strip()
        m = re.search(r'#(\d+) created', text)
        wp = m.group(1) if m else None
        print(f'{br}: {text[:120]}')
        if wp:
            # колонка work_package в реестре
            rows = list(csv.DictReader(open(REG, encoding='utf-8')))
            fields = list(rows[0].keys())
            if 'work_package' not in fields:
                fields.append('work_package')
            for row in rows:
                if row['br_id'] == br:
                    row['work_package'] = wp
                    row['status'] = 'в работе' if row.get('status') == 'TODO' else row.get('status')
            with open(REG, 'w', newline='', encoding='utf-8') as f:
                w = csv.DictWriter(f, fieldnames=fields)
                w.writeheader()
                w.writerows(rows)


if __name__ == '__main__':
    main()
