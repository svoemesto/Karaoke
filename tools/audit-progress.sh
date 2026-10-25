#!/usr/bin/env bash
# =====================================================================
# tools/audit-progress.sh — прогресс исправлений по аудиту #336.
#
# Источник истины — `specs/336-code-audit/registry.csv` (колонка `status`).
# Волны и пакеты — `specs/336-code-audit/packages.csv` (колонки `wave`, `issue`).
#
# Зачем: реестр — единственный источник, но его 626 строк неудобно читать
# глазами. Этот скрипт даёт сводку «сколько закрыто, по волнам и пакетам»
# и список того, что ещё не начато.
#
# Использование:
#   bash tools/audit-progress.sh            # сводка
#   bash tools/audit-progress.sh --todo     # что осталось (BR, файл, пакет)
#   bash tools/audit-progress.sh --packages # по пакетам
#
# Exit codes:
#   0 — сводка построена
#   1 — usage error
# =====================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

REGISTRY="specs/336-code-audit/registry.csv"
PACKAGES="specs/336-code-audit/packages.csv"
MODE="${1:-summary}"

[ -f "$REGISTRY" ] || { echo "ERROR: нет $REGISTRY" >&2; exit 1; }

python3 - "$REGISTRY" "$PACKAGES" "$MODE" <<'PY'
import csv, sys, collections

reg_path, pkg_path, mode = sys.argv[1], sys.argv[2], sys.argv[3]
rows = list(csv.DictReader(open(reg_path, encoding='utf-8')))

# issue -> (package, wave, severity)
pkg_by_issue = {}
try:
    for p in csv.DictReader(open(pkg_path, encoding='utf-8')):
        pkg_by_issue[p['issue'].strip()] = (p['package'], p['wave'], p['severity'])
except FileNotFoundError:
    pass

def norm(s):
    return (s or '').strip()

def status_of(r):
    return norm(r.get('status')) or 'TODO'

total = len(rows)
done = sum(1 for r in rows if status_of(r) == 'исправлено')
by_sev = collections.Counter()
by_sev_done = collections.Counter()
for r in rows:
    s = norm(r.get('severity'))
    by_sev[s] += 1
    if status_of(r) == 'исправлено':
        by_sev_done[s] += 1

if mode == '--todo':
    print(f'ОСТАЛОСЬ: {total - done} из {total}\n')
    for r in rows:
        if status_of(r) == 'исправлено':
            continue
        wp = norm(r.get('work_package'))
        issue = wp
        info = pkg_by_issue.get(issue, ('—', '—', '—'))
        print(f"  {norm(r.get('br_id')):<8} {norm(r.get('severity')):<3} {info[0]:<22} "
              f"{norm(r.get('file'))[:52]:<52} {norm(r.get('line_start'))}")
    sys.exit(0)

if mode == '--packages':
    agg = collections.defaultdict(lambda: [0, 0])
    for r in rows:
        wp = norm(r.get('work_package'))
        if not wp:
            wp = '—'
        agg[wp][0] += 1
        if status_of(r) == 'исправлено':
            agg[wp][1] += 1
    print(f'{"пакет":<8} {"волна":<6} {"записей":>8} {"исправлено":>11}  осталось')
    for wp in sorted(agg, key=lambda k: (pkg_by_issue.get(k, ('', 'Z', ''))[1], k)):
        n, d = agg[wp]
        info = pkg_by_issue.get(wp, ('—', '—', '—'))
        print(f'{wp:<8} {info[1]:<6} {n:>8} {d:>11}  {n - d:>8}')
    print(f'\nВСЕГО: {done} из {total}')
    sys.exit(0)

# summary
print(f'Аудит #336 — исправлено {done} из {total} ({done * 100 // max(total, 1)}%)')
print()
print('По серьёзности:')
for sev in sorted(by_sev):
    print(f'  {sev:<4} {by_sev_done[sev]:>4} из {by_sev[sev]:<4}')
print()

wave_agg = collections.defaultdict(lambda: [0, 0])
pkg_wave = {}
for r in rows:
    wp = norm(r.get('work_package'))
    info = pkg_by_issue.get(wp)
    wave = info[1] if info else '—'
    wave_agg[wave][0] += 1
    if status_of(r) == 'исправлено':
        wave_agg[wave][1] += 1
print('По волнам:')
for wave in sorted(wave_agg):
    n, d = wave_agg[wave]
    bar = '#' * (d * 30 // max(n, 1))
    print(f'  волна {wave:<3} {d:>4} из {n:<4} {bar}')
print()
print('Пакетов в packages.csv: %d; волн: %s' % (len(pkg_by_issue), ', '.join(sorted({v[1] for v in pkg_by_issue.values()}))))
PY
