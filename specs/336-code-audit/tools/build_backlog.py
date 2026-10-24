#!/usr/bin/env python3
"""Сборка черновика бэклога правок из реестра аудита (#336).

Вход:  specs/336-code-audit/registry.csv  (625 записей BR-001…BR-625)
       specs/336-code-audit/findings/P-*.md (карточки с полем «Поправка»)
Выход: specs/336-code-audit/backlog.md      (черновик пакетов правки)

Пакеты = группировка записей по механизму, а не по файлу: одна правка часто
закрывает несколько BR (например, 5 SQL-инъекций в karaoke-app).

Правила группировки (детерминированные, в порядке применения):
  R1  S1-запись = отдельный пакет (риск/деньги/периметр — дробить).
  R2  Явные семейства из аудита (общий фикс в нескольких файлах/модулях).
  R3  Остальные S2 — по модулю и классу, шаг ~до 8 BR.
  R4  S3 — по модулю и подкаталогу, шаг ~до 25 BR; TEST/CI/BUILD — отдельно.
"""
from __future__ import annotations

import csv
import os
import re
import collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..'))
REG = os.path.join(ROOT, 'specs/336-code-audit/registry.csv')
FIND = os.path.join(ROOT, 'specs/336-code-audit/findings')
OUT = os.path.join(ROOT, 'specs/336-code-audit/backlog.md')

# ── R2: явные семейства (ключ семейства → список BR, если заданы явно) ──────
FAMILIES = [
    ('PKG-SEC-SQLI', 'SQL-инъекции в параметризованных запросах', 'S2',
     ['BR-028', 'BR-100', 'BR-175', 'BR-182', 'BR-237']),
    ('PKG-RES-THREADLOCAL', 'Не закрываются соединения/потоки (ThreadLocal)', 'S2',
     ['BR-006', 'BR-027', 'BR-038', 'BR-060', 'BR-116']),
    ('PKG-MONEY-IDEMPOTENCY', 'Идемпотентность платежей и автопродления', 'S2',
     ['BR-014', 'BR-020', 'BR-021', 'BR-514']),
    ('PKG-SEC-SECRET-ARGV', 'Секреты в argv процессов', 'S2',
     ['BR-551', 'BR-552', 'BR-605']),
    ('PKG-DB-RECORDHASH', 'Дрейф recordhash-триггеров и sync-таблиц', 'S2',
     ['BR-540', 'BR-541', 'BR-553', 'BR-533']),
    ('PKG-RACE-TARGET', 'Гонка чтения/записи целевого объекта', 'S3',
     ['BR-397', 'BR-407', 'BR-409']),
    ('PKG-RACE-PUBLISH', 'Гонка публикации (check-then-act)', 'S2',
     ['BR-062', 'BR-138']),
    ('PKG-SEC-CHMOD', 'Файлы с правами 666 (пароли, превью)', 'S3',
     ['BR-115', 'BR-247']),
    ('PKG-ERR-STATS-ZERO', 'Нули в статистике при ошибке БД', 'S3',
     ['BR-016', 'BR-185']),
    ('PKG-ERR-EXITCODE', 'Код возврата внешней команды игнорируется', 'S2',
     ['BR-029', 'BR-161']),
    ('PKG-SEC-XFF', 'Доверие к заголовку X-Forwarded-For', 'S2',
     ['BR-003']),
]


def module_of(path: str) -> str:
    for k in ('karaoke-app', 'karaoke-web', 'karaoke-public', 'webvue3',
              'alignment-ml', 'deploy', 'tools', '.github', 'config'):
        if path.startswith(k):
            return k
    return 'прочее'


def subdir(path: str) -> str:
    """Подкаталог внутри модуля — для нарезки больших пакетов S3."""
    m = re.match(r'^([^/]+)/([^/]+)/', path)
    if not m:
        return ''
    mod, second = m.group(1), m.group(2)
    if second in ('src', 'deploy', 'scripts', 'app'):
        rest = path.split('/')
        # …/src/main/kotlin/<pkg>/<файл> → <pkg>-последний
        if len(rest) > 5 and rest[4] == 'kotlin':
            return rest[-2]
        if len(rest) > 5 and rest[4] == 'java':
            return rest[-2]
        if len(rest) > 3 and rest[2] == 'main':
            return '/'.join(rest[3:5])
        return second
    return second


def load_fixes() -> dict[str, str]:
    """BR → краткая «Поправка» из карточки находки."""
    out: dict[str, str] = {}
    for fn in sorted(os.listdir(FIND)):
        if not fn.endswith('.md'):
            continue
        text = open(os.path.join(FIND, fn), encoding='utf-8').read()
        cards = re.split(r'\n### ', text)
        for card in cards[1:]:
            m = re.match(r'(P-\d+[a-z]?-\d+):', card)
            if not m:
                continue
            fid = m.group(1)
            fix = ''
            mm = re.search(r'- \*\*Поправка:?\*\*:?\s*(.+)', card)
            if not mm:
                mm = re.search(r'- Поправка:?\s*(.+)', card)
            if mm:
                fix = mm.group(1).strip()
            out[fid] = fix
    return out


def main() -> None:
    reg = list(csv.DictReader(open(REG, encoding='utf-8')))
    by_br = {r['br_id']: r for r in reg}
    fixes = load_fixes()

    # карта BR → ключ записи в карточке (P-XX-NN), ищем по номеру
    br_to_fid: dict[str, str] = {}
    for r in reg:
        m = re.match(r'BR-(\d+)', r['br_id'])
        br_to_fid[r['br_id']] = f"BR-{m.group(1)}"

    # фиксы лежат по P-идентификаторам; строим BR → fix через порядок карточек
    # (BR-номер = порядковый номер записи в порции, он не совпадает с P-NN),
    # поэтому фикс привязываем через реестр: portion + порядок.
    portion_order: dict[str, list[str]] = collections.defaultdict(list)
    for r in reg:
        portion_order[r['portion']].append(r['br_id'])
    fix_by_br: dict[str, str] = {}
    what_by_br: dict[str, str] = {}
    for fn in sorted(os.listdir(FIND)):
        if not fn.endswith('.md'):
            continue
        pid = fn[:-3]
        text = open(os.path.join(FIND, fn), encoding='utf-8').read()
        cards = re.split(r'\n### ', text)
        found: list[tuple[str, str, str]] = []
        for card in cards[1:]:
            m = re.match(r'(P-\d+[a-z]?-\d+):', card)
            if not m:
                continue
            fix = ''
            mm = re.search(r'- \*\*Поправка:?\*\*:?\s*(.+)', card) or \
                 re.search(r'- Поправка:?\s*(.+)', card)
            if mm:
                fix = mm.group(1).strip()
            what = ''
            mw = re.search(r'- \*\*Что не так:?\*\*:?\s*(.+)', card) or \
                  re.search(r'- Что не так:?\s*(.+)', card)
            if mw:
                what = mw.group(1).strip()
            found.append((m.group(1), fix, what))
        order = portion_order.get(pid, [])
        for idx, (_fid, fix, what) in enumerate(found):
            if idx < len(order):
                if fix:
                    fix_by_br[order[idx]] = fix
                if what:
                    what_by_br[order[idx]] = what

    # ── R1/R2: явные семейства ──────────────────────────────────────────────
    used: set[str] = set()
    packages: list[dict] = []

    def mk(pid: str, title: str, sev: str, brs: list[str], theme: str,
           note: str = '') -> None:
        brs = [b for b in brs if b in by_br and b not in used]
        if not brs:
            return
        used.update(brs)
        recs = [by_br[b] for b in brs]
        rank = {'S1': 0, 'S2': 1, 'S3': 2, '': 3}
        sev = min((r['severity'] for r in recs), key=lambda x: rank.get(x, 3))
        packages.append({
            'pid': pid, 'title': title, 'sev': sev, 'brs': brs, 'theme': theme,
            'note': note, 'recs': recs,
            'modules': sorted({module_of(r['file']) for r in recs}),
        })

    # S1 — по одной задаче
    s1 = [r['br_id'] for r in reg if r['severity'] == 'S1']
    for br in s1:
        r = by_br[br]
        title = what_by_br.get(br, r['root_cause_key'].split(':')[0].replace('-', ' '))
        title = re.sub(r'\s+', ' ', title)[:110].rstrip(' ,;:.') + '…'
        mk(f'PKG-{br}', title, 'S1', [br], r['root_cause_key'],
           'S1 — отдельная задача, полный цикл со спекой')

    for pid, title, sev, brs in FAMILIES:
        mk(pid, title, sev, brs, 'family')

    # ── R3/R4: остальные S2 и S3 — по модулю, с выделением тестов и guards ──
    rest = [r for r in reg if r['br_id'] not in used]
    test_like: dict[str, list[str]] = collections.defaultdict(list)
    normal: dict[tuple[str, str], list[str]] = collections.defaultdict(list)
    for r in rest:
        mod = module_of(r['file'])
        if r['class'] == 'TEST':
            test_like[f'TEST/{mod}'].append(r['br_id'])
        elif r['class'] in ('RULE', 'BUILD', 'COMPAT') and mod in ('tools', '.github', 'config'):
            test_like[f'guards/CI/{mod}'].append(r['br_id'])
        else:
            normal[(r['severity'], mod)].append(r['br_id'])

    idx = 1
    # тесты и guards — отдельными пакетами (они и есть «сетка доверия»)
    for (title, brs) in sorted(test_like.items()):
        sev = 'S2' if any(by_br[b]['severity'] == 'S2' for b in brs) else 'S3'
        step = 12 if sev == 'S2' else 25
        for i in range(0, len(brs), step):
            pk = f'PKG-{sev}-{idx:02d}'
            idx += 1
            chunk = brs[i:i+step]
            mk(pk, f'{title} ({sev}, {len(chunk)})', sev, chunk, 'special')
    # остальное — по модулю: S2 шаг 15, S3 шаг 40
    for (sev, mod), brs in sorted(normal.items()):
        step = 15 if sev == 'S2' else 40
        for i in range(0, len(brs), step):
            pk = f'PKG-{sev}-{idx:02d}'
            idx += 1
            chunk = brs[i:i+step]
            classes = sorted({by_br[b]['class'] for b in chunk})
            themes = []
            for b in chunk:
                t = by_br[b]['root_cause_key'].split(':')[0]
                if t not in themes:
                    themes.append(t)
            title = f"{mod}: {', '.join(classes)} — {len(chunk)} BR"
            pk_theme = ', '.join(themes[:6]) + ('…' if len(themes) > 6 else '')
            mk(pk, title, sev, chunk, 'module', pk_theme)

    # волны и зависимости
    BACKUP_BR = 'BR-539'
    backup_pkg = next((p['pid'] for p in packages if BACKUP_BR in p['brs']), None)
    for p in packages:
        if p['sev'] == 'S1':
            p['wave'] = 'A'
        elif p['theme'] == 'special':
            p['wave'] = 'B'
        elif p['sev'] == 'S2':
            p['wave'] = 'C' if ('deploy' in p['modules'] or 'karaoke-web' in p['modules']
                                or 'tools' in p['modules']) else 'D'
        else:
            p['wave'] = 'E'
        files = sorted({r['file'] for r in p['recs']})
        p['files'] = files
        flags = []
        if any('/api/' in r['file'] or 'Public' in r['file'] or 'public' in r['file'] for r in p['recs']):
            flags.append('публичный периметр')
        if any(r['class'] in ('DATA', 'SEC') for r in p['recs']):
            flags.append('данные/безопасность')
        if any(r['file'].endswith('.sql') or 'karaoke-db' in r['file'] for r in p['recs']):
            flags.append('миграции БД')
        if any(r['file'].startswith('deploy') for r in p['recs']):
            flags.append('прод-конфигурация')
        p['flags'] = flags
        deps = []
        if any('karaoke-db' in r['file'] or r['file'].endswith('.sql') for r in p['recs']) \
                and backup_pkg and p['pid'] != backup_pkg:
            deps.append(f'{backup_pkg} (рабочий бэкап БД — prerequisite)')
        p['deps'] = deps

    # ── вывод ───────────────────────────────────────────────────────────────
    order = {'S1': 0, 'S2': 1, 'S3': 2}
    packages.sort(key=lambda p: (order[p['sev']], p['pid']))

    lines: list[str] = []
    lines.append('# Черновик бэклога правок по результатам аудита (#336)')
    lines.append('')
    lines.append('> **Статус: черновик на утверждение владельца.** Ничего не заведено в трекере, код не тронут.')
    lines.append('> Пакеты — группировка записей реестра по механизму (одна правка закрывает несколько BR).')
    lines.append('')
    lines.append('## Сводка')
    lines.append('')
    lines.append('| Severity | Пакетов | Записей BR |')
    lines.append('|---|---:|---:|')
    for sev in ('S1', 'S2', 'S3'):
        pk = [p for p in packages if p['sev'] == sev]
        lines.append(f'| {sev} | {len(pk)} | {sum(len(p["brs"]) for p in pk)} |')
    lines.append(f'| **Всего** | **{len(packages)}** | **{sum(len(p["brs"]) for p in packages)}** |')
    lines.append('')
    lines.append('## Волны')
    lines.append('')
    lines.append('| Волна | Содержание | Пакетов | BR |')
    lines.append('|---|---|---:|---:|')
    wave_desc = {
        'A': 'S1 — публичный периметр, безопасность, деньги (по одной задаче, полный цикл со спекой)',
        'B': 'Сетка доверия: guard-скрипты и тесты, которые не проверяют заявленное (чиним первыми после A)',
        'C': 'Операционные S2: деплой, бэкапы, платежи, синхронизация, безопасность периметра',
        'D': 'Функциональные S2 по модулям',
        'E': 'Остаток S3 (гигиена, краевые случаи)',
    }
    for wv in ('A', 'B', 'C', 'D', 'E'):
        pk = [p for p in packages if p['wave'] == wv]
        lines.append(f'| {wv} | {wave_desc[wv]} | {len(pk)} | {sum(len(p["brs"]) for p in pk)} |')
    lines.append('')
    lines.append('## Пакеты')
    lines.append('')
    for p in packages:
        lines.append(f'### {p["pid"]} — {p["title"]}')
        lines.append('')
        lines.append(f'- **Severity:** {p["sev"]} · **модули:** {", ".join(p["modules"])} · **BR:** {len(p["brs"])}')
        lines.append(f'- **Волна:** {p["wave"]} · **Режим:** {"полный цикл со спекой" if p["sev"] == "S1" else "короткий цикл: ТЗ из карточек аудита"}')
        lines.append(f'- **Файлов:** {len(p["files"])}' + (f' · **Риск:** {", ".join(p["flags"])}' if p['flags'] else ''))
        if p.get('note') and p['theme'] == 'module':
            lines.append(f'- **Темы внутри:** {p["note"]}')
        if p.get('deps'):
            lines.append('- **Зависит от:** ' + '; '.join(p['deps']))
        lines.append('')
        lines.append('| BR | Файл:строки | Класс | Суть / поправка |')
        lines.append('|---|---|---|---|')
        for br in p['brs']:
            r = by_br[br]
            fix = fix_by_br.get(br, '')
            fix = (fix[:180] + '…') if len(fix) > 180 else fix
            loc = f"`{r['file']}:{r['line_start']}`"
            lines.append(f'| {br} | {loc} | {r["class"]} | {fix} |')
        lines.append('')

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

    covered = {b for p in packages for b in p['brs']}
    print(f'пакетов: {len(packages)}; записей покрыто: {len(covered)} из {len(reg)}')
    missing = [r['br_id'] for r in reg if r['br_id'] not in covered]
    if missing:
        print('НЕ распределены:', missing[:20], '...' if len(missing) > 20 else '')
    for p in packages:
        print(f"  {p['pid']:<14} {p['sev']} {len(p['brs']):>3} BR  {p['title'][:60]}")


if __name__ == '__main__':
    main()
