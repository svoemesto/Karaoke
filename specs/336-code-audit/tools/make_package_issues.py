#!/usr/bin/env python3
"""Заводит задачи OpenProject по ПАКЕТАМ правок (волны B–E).

В отличие от `make_work_packages.py` (одна задача на одну запись BR, годится для
волны A, где в пакете ровно одна запись), здесь одна задача = один пакет из
`backlog.md`: в описании задачи — карточки всех BR пакета.

Вход:  specs/336-code-audit/backlog.md + registry.csv + findings/P-*.md
Выход: work package в трекере на каждый пакет + specs/336-code-audit/packages.csv
       (пакет → номер задачи) + колонка work_package в registry.csv (номер задачи
       для каждой BR пакета).

Использование:
  python3 tools/make_package_issues.py --wave B --dry-run   # только показать
  python3 tools/make_package_issues.py --wave B             # завести
  python3 tools/make_package_issues.py --wave B C D E       # несколько волн
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
BACKLOG = os.path.join(BASE, 'backlog.md')
PKG_CSV = os.path.join(BASE, 'packages.csv')
TRACKER = os.path.join(ROOT, 'tools/tracker.sh')
PROJECT_ID = '3'
TYPE_ID = '1'  # Task

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_work_packages import card_for, field  # noqa: E402


def parse_packages() -> list[dict]:
    """Пакеты из backlog.md: заголовок, метаданные, список BR."""
    text = open(BACKLOG, encoding='utf-8').read()
    pkgs: list[dict] = []
    for m in re.finditer(r'### (PKG-[\w\-]+) — (.*?)\n\n(.*?)(?=\n### |\n## |\Z)', text, re.S):
        pid, title, body = m.group(1), m.group(2).strip(), m.group(3)
        meta: dict[str, str] = {}
        for line in body.split('\n'):
            if not line.strip().startswith('- **'):
                continue
            # в строке может быть несколько пар «**ключ:** значение», разделённых « · »
            parts = re.split(r'\s·\s', line.strip().lstrip('- ').strip())
            for part in parts:
                mm = re.match(r'^\*\*([^*]+):\*\*\s*(.*)$', part.strip())
                if mm:
                    meta[mm.group(1).strip()] = mm.group(2).strip()
        brs = re.findall(r'\| (BR-\d+) \|', body)
        themes = re.search(r'\*\*Темы внутри:\*\*\s*(.*)', body)
        pkgs.append(
            {
                'id': pid,
                'title': title,
                'meta': meta,
                'brs': brs,
                'themes': themes.group(1).strip() if themes else '',
                'body': body,
            }
        )
    return pkgs


def wave_of(pkg: dict) -> str:
    return pkg['meta'].get('Волна', '').strip()


def br_line(br: str, reg: list[dict], cache: dict) -> str:
    """Строка карточки BR: файл:строки · класс · суть (из карточки аудита)."""
    r = next((x for x in reg if x['br_id'] == br), None)
    if not r:
        return f'| {br} | — | — | — |'
    if br not in cache:
        fn, card = card_for(br, reg)
        cache[br] = (fn, card)
    fn, card = cache[br]
    what = field(card, 'что не так') if card else ''
    fix = field(card, 'поправка') if card else ''
    essence = (fix or what or '').split('\n')[0].strip()
    essence = essence.replace('|', '/')
    if len(essence) > 220:
        essence = essence[:217] + '…'
    loc = f"{r['file']}:{r['line_start']}" + (f"–{r['line_end']}" if r['line_end'] and r['line_end'] != r['line_start'] else '')
    return f"| {br} | `{loc}` | {r['class']} | {essence} |"


def build_desc(pkg: dict, reg: list[dict], cache: dict) -> tuple[str, str]:
    meta = pkg['meta']
    subject = f"[#336] S2: {pkg['title']}"
    if meta.get('Severity', '').startswith('S3'):
        subject = f"[#336] S3: {pkg['title']}"
    lines = [
        f"**Пакет:** {pkg['id']} · **волна {wave_of(pkg)}** · **severity {meta.get('Severity', '?')}**",
        f"**Модули:** {meta.get('модули', '—')} · **записей BR:** {len(pkg['brs'])} · **файлов:** {meta.get('Файлов', '—')}",
    ]
    if meta.get('Риск'):
        lines.append(f"**Риск:** {meta['Риск']}")
    if pkg['themes']:
        lines.append(f"**Темы внутри:** {pkg['themes']}")
    lines += [
        "",
        "> Правка выполняется **коротким циклом**: ТЗ = карточки аудита ниже, полный цикл",
        "> со спекой обязателен для S1 (волна A). Требуется регрессионная проверка «до/после»",
        "> и обновление `knowledge/` по факту изменений.",
        "",
        "## Записи пакета (карточки аудита #336)",
        "",
        "| BR | Файл:строки | Класс | Суть / поправка |",
        "|---|---|---|---|",
    ]
    for br in pkg['brs']:
        lines.append(br_line(br, reg, cache))
    lines += [
        "",
        "## Требования к реализации",
        "",
        "1. Тест «до/после»: регрессия ловится на коде без правки.",
        "2. Не менять публичное поведение шире, чем требует запись BR.",
        "3. Значения секретов не хардкодить — только имена параметров.",
        "4. Обновить `knowledge/` (SSoT) по `.ssot-map.yml` и per-feature документ (FR-009),",
        "   если меняется поведение фичи.",
        "5. Пройти проверки проекта: gradle (с `GRADLE_USER_HOME`), линтеры фронтов, guard'ы.",
        "",
        "## Артефакты",
        "",
        f"- Реестр аудита: `specs/336-code-audit/registry.csv` (записи {', '.join(pkg['brs'])})",
        f"- Карточки: `specs/336-code-audit/findings/` (порция {meta.get('модули', '—')})",
        "- Бэклог: `specs/336-code-audit/backlog.md`",
    ]
    return subject, '\n'.join(lines)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--wave', nargs='*', default=['B', 'C', 'D', 'E'])
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    reg = list(csv.DictReader(open(REG, encoding='utf-8')))
    pkgs = parse_packages()
    cache: dict = {}

    existing: dict[str, str] = {}
    if os.path.exists(PKG_CSV):
        for row in csv.DictReader(open(PKG_CSV, encoding='utf-8')):
            existing[row['package']] = row['issue']

    for pkg in pkgs:
        if wave_of(pkg) not in args.wave:
            continue
        if pkg['id'] in existing:
            print(f"{pkg['id']}: уже заведена как задача {existing[pkg['id']]} — пропускаю")
            continue
        subject, desc = build_desc(pkg, reg, cache)
        if args.dry_run:
            print(f"[dry] {pkg['id']} (волна {wave_of(pkg)}, {len(pkg['brs'])} BR)\n    {subject}\n")
            continue
        out = subprocess.run(
            [TRACKER, 'create-issue', '--project-id', PROJECT_ID, '--type-id', TYPE_ID,
             '--subject', subject, '--description', desc],
            capture_output=True, text=True, cwd=ROOT,
        )
        text = (out.stdout + out.stderr).strip()
        m = re.search(r'#(\d+) created', text)
        if not m:
            print(f"{pkg['id']}: ОШИБКА — {text[:200]}")
            continue
        wp = m.group(1)
        print(f"{pkg['id']} → задача #{wp} ({len(pkg['brs'])} BR)")
        existing[pkg['id']] = wp

        rows = list(csv.DictReader(open(REG, encoding='utf-8')))
        fields = list(rows[0].keys())
        if 'work_package' not in fields:
            fields.append('work_package')
        for row in rows:
            if row['br_id'] in pkg['brs']:
                row['work_package'] = wp
        with open(REG, 'w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            w.writerows(rows)

    if not args.dry_run:
        rows = []
        if os.path.exists(PKG_CSV):
            rows = list(csv.DictReader(open(PKG_CSV, encoding='utf-8')))
        have = {r['package'] for r in rows}
        for pkg in pkgs:
            if pkg['id'] in have or pkg['id'] not in existing:
                continue
            rows.append({
                'package': pkg['id'],
                'issue': existing[pkg['id']],
                'wave': wave_of(pkg),
                'severity': pkg['meta'].get('Severity', ''),
                'brs': ' '.join(pkg['brs']),
                'title': pkg['title'],
            })
        with open(PKG_CSV, 'w', newline='', encoding='utf-8') as f:
            w = csv.DictWriter(f, fieldnames=['package', 'issue', 'wave', 'severity', 'brs', 'title'])
            w.writeheader()
            w.writerows(rows)
        print(f"\nпакетов заведено: {len(existing)}; карта: {PKG_CSV}")


if __name__ == '__main__':
    main()
