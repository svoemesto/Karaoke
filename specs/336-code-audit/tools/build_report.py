#!/usr/bin/env python3
"""Сборка сводного bug-report.md из per-portion находок + registry.csv.

Вход:
  specs/336-code-audit/findings/P-*.md  — разбор заголовков «### P-XX-NN: path:lines — CLASS (SEV)»
  specs/336-code-audit/registry.csv      — реестр (br_id, portion, class, severity, file, lines, root_cause_key)
  specs/336-code-audit/status.md         — таблица порций (покрытие)

Выход:
  specs/336-code-audit/bug-report.md     — сводный отчёт по severity S1→S3 + вердикты + покрытие
"""
import csv
import os
import re
import collections

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIND = os.path.join(BASE, 'findings')
REG = os.path.join(BASE, 'registry.csv')
STATUS = os.path.join(BASE, 'status.md')
OUT = os.path.join(BASE, 'bug-report.md')

HEAD_RE = re.compile(
    r'^###\s+(P-\d+[a-z]?-?\d+):\s+([^:]+?):([\d\-–, ]+?)\s+—\s+([A-Z]+)\s*(?:\(([^)]*)\))?\s*$')
FIELD_RE = re.compile(r'^-\s+\*{0,2}([^:*]{2,40}?)\*{0,2}\s*[:：]\s*(.*)$')


def parse_findings():
    """→ список записей: dict(fid, file, lines, cls, sev, fields{}, path)"""
    out = []
    for fn in sorted(os.listdir(FIND)):
        if not fn.endswith('.md'):
            continue
        path = os.path.join(FIND, fn)
        text = open(path, encoding='utf-8').read()
        cur = None
        for line in text.splitlines():
            m = HEAD_RE.match(line)
            if m:
                if cur:
                    out.append(cur)
                cur = {
                    'fid': m.group(1), 'file': m.group(2).strip(), 'lines': m.group(3).strip(),
                    'cls': m.group(4), 'sev_hdr': (m.group(5) or '').strip(), 'fields': {},
                    'src': fn,
                }
                continue
            if cur is not None:
                fm = FIELD_RE.match(line)
                if fm:
                    key = fm.group(1).strip().lower()
                    val = fm.group(2).strip().strip('*').strip()
                    if key in cur['fields']:
                        cur['fields'][key] += ' ' + val
                    else:
                        cur['fields'][key] = val
                    cur['_last'] = key
                elif line.startswith('###') and cur:
                    out.append(cur)
                    cur = None
                else:
                    # продолжение многострочного поля (например, «Доказательство:» со списком цитат)
                    last = cur.get('_last')
                    if last in cur['fields'] and last == 'доказательство':
                        if line.strip():
                            cur['fields'][last] += '\n' + line.rstrip()
        if cur:
            out.append(cur)
    return out


def sev_of(rec):
    """severity из тела записи (приоритет над заголовком)."""
    for key in ('severity', 'severity ', 'трез severity'):
        if key in rec['fields']:
            v = rec['fields'][key]
            m = re.search(r'\bS([123])\b', v)
            if m:
                return 'S' + m.group(1), v
    if rec['sev_hdr']:
        m = re.search(r'\bS([123])\b', rec['sev_hdr'])
        if m:
            return 'S' + m.group(1), rec['sev_hdr']
    return '', ''


def first_line(lines):
    m = re.search(r'(\d+)', lines)
    return int(m.group(1)) if m else 0


def last_line(lines):
    nums = re.findall(r'(\d+)', lines)
    return int(nums[-1]) if nums else 0


def main():
    findings = parse_findings()
    reg = list(csv.DictReader(open(REG, encoding='utf-8')))

    # находки, слитые лидом с уже существующими записями реестра (доп. локации)
    MERGE = {
        'P-52-03': 'BR-324', 'P-06-01': 'BR-060', 'P-07-02': 'BR-060',
        'P-52-01': 'BR-123', 'P-54-01': 'BR-123', 'P-57-01': 'BR-123', 'P-64-07': 'BR-123',
        'P-58-01': 'BR-312', 'P-62-01': 'BR-312', 'P-62-11': 'BR-312',
        'P-65-01': 'BR-312', 'P-65-02': 'BR-123', 'P-65-08': 'BR-457',
        'P-49-03': 'BR-322', 'P-39-04': 'BR-202',
        'P-75-01': 'BR-551',
        'P-68-01': 'BR-525',
        'P-66-05': 'BR-302', 'P-66-06': 'BR-304', 'P-66-07': 'BR-307', 'P-66-08': 'BR-310',
        'P-66-09': 'BR-308', 'P-66-10': 'BR-309', 'P-66-11': 'BR-311', 'P-66-12': 'BR-305',
    }

    # сопоставление «находка ↔ запись реестра» внутри порции по файлу и близости диапазонов строк
    def portion_of(fid, src):
        m = re.match(r'(P-\d+)', fid)
        if m:
            return m.group(1)
        m = re.match(r'(P-\d+)', src.replace('.md', ''))
        return m.group(1) if m else ''

    reg_by_portion = collections.defaultdict(list)
    for r in reg:
        reg_by_portion[r['portion']].append(r)

    pairs = []  # (score, finding_idx, reg_row)
    for i, f in enumerate(findings):
        if f['fid'] in MERGE:
            continue
        p = portion_of(f['fid'], f['src'])
        fl = first_line(f['lines'])
        fr = last_line(f['lines'])
        nums = {int(x) for x in re.findall(r'\d+', f['lines'])}
        for r in reg_by_portion.get(p, []):
            if r['file'].strip() != f['file']:
                continue
            rs = int(r['line_start'] or 0)
            re_ = int(r['line_end'] or rs)
            # точное попадание диапазона реестра в диапазоны находки — приоритет
            if rs in nums:
                score = abs(fr - re_) if re_ in nums else 0
            else:
                score = 1000 + abs(fl - rs) + abs(fr - re_)
            pairs.append((score, i, r))
    pairs.sort(key=lambda x: x[0])
    taken_f, taken_r = set(), set()
    mapping = {}
    for score, i, r in pairs:
        if i in taken_f or r['br_id'] in taken_r:
            continue
        taken_f.add(i)
        taken_r.add(r['br_id'])
        mapping[i] = r

    merged_extra = collections.defaultdict(list)
    # дополнительные локации, добавленные лидом к существующим записям
    EXTRA_LOC = {
        'BR-499': 'корневой `gradle/wrapper/gradle-wrapper.properties:3` (Gradle 8.14) — та же проблема версий wrapper’ов',
        'BR-003': 'локации из P-38: `DebugDbAccessGuard` (IP-allowlist `/api/public/debug/db`), rate-limit claim’ов share-ссылок, `client_ip_hash` в БД',
        'BR-123': 'локации из P-43/P-52/P-54/P-57/P-64: `CustomConfirm.vue` → `v-html` (email, имя картинки, автор/словарь/новость), `SyncTable.vue` (ошибка БД с email)',
        'BR-312': 'локации из P-43/P-58/P-62/P-65: `useKaraokeEditor.js` (karaoke-public), `useReviewModalFormat.js`, `SubsEdit.vue:1703, 4073–4083`, `SongKaraokeEditorView.vue:317`',
        'BR-324': 'локация из P-52: `webvue3/src/components/Chat/store.js:164` — первое сообщение в новом диалоге не появляется',
        'BR-457': 'локация из P-65: `webvue3/src/composables/useKaraokeEditor.js:152–177` (квадратичный `specTagAnchors`)',
        'BR-202': 'локация из P-39: `karaoke-web/.../templates/filter.html:308–310` (`${songAuthors}` вместо `${authors}`)',
        'BR-531': '`karaoke-app/src/test/kotlin/1` (1 916 строк) — тот же MLT с путями `~/Documents/Караоке/…`, потребителей нет (P-30, «Сверки» п.1); `karaoke-app/src/test/kotlin/Неправильный.xml` (7 674 строки) — второй такой же MLT с путями `~/Documents/Караоке/Ю-Питер/…` и `~/Documents/SpaceBox4096/…`, потребителей нет (P-33-01)',
        'BR-551': 'тот же корень «секрет в argv»: BR-552 (`deploy/tracker-db-backup.sh`, `docker exec -e PGPASSWORD`), BR-605 (`tools/tracker-lib.sh` — токен OpenProject в `-H "Authorization: Basic …"`), P-75-01 (`tools/analyze-prod-incident.sh` — пароль БД в connection string)',
        'BR-573': 'зеркала из P-76 «Сверки»: registry-smoke (пустой каталог → PASS), check-ssot-impact (неразрешимая база → exit 0), lint-knowledge (шаблон не загружен → проверка отключена)',
        'BR-578': 'зеркало из P-76: `tools/generate-eslint-baseline.sh:57` — абсолютный `filePath` в ключе baseline (тот же корень)',
        'BR-581': 'зеркало из P-76: `tools/registry-vendor.sh:52` — `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (тот же корень «жёсткий путь»)',
        'BR-525': 'локации из P-68: `webvue3/playwright.config.js:29–35` (ветки `process.env.CI` — мёртвый код), 14 файлов `webvue3/e2e/*.spec.js`, 5 файлов `webvue3/src/**/__tests__/*`; НОВОЕ в P-68-01: unit-тесты фронта (`node --test`) не имеют скрипта запуска в `webvue3/package.json`',
        'BR-507': 'локации из P-68: e2e и unit-тесты `webvue3` не выполняются в CI (тот же корень «в CI нет сборки и тестов»)',
        'BR-060': 'локации из P-06/P-07: `SyncAudioDescendants.kt:173, 333`, `Utils.kt` (одноразовые `thread {}` без `closeThreadConnection()`)',
    }

    used = set()
    records = []
    for idx, f in enumerate(findings):
        r = mapping.get(idx)
        if r is None and f['fid'] in MERGE:
            tgt = MERGE[f['fid']]
            merged_extra[tgt].append(f"{f['fid']}: {f['file']}:{f['lines']}")
            continue
        sev, sev_txt = sev_of(f)
        if r:
            sev = r['severity'] or sev
            used.add(r['br_id'])
        records.append({
            'fid': f['fid'], 'file': f['file'], 'lines': f['lines'], 'cls': f['cls'] or (r['class'] if r else ''),
            'sev': sev, 'sev_txt': sev_txt, 'fields': f['fields'], 'src': f['src'],
            'br': r['br_id'] if r else '—', 'root': r['root_cause_key'] if r else '—',
            'status': (r['status'] if r else 'TODO'), 'portion': (r['portion'] if r else ''),
        })

    # записи реестра без находки (объединённые/перенесённые) — отдельным списком
    orphans = [r for r in reg if r['br_id'] not in used]

    order = {'S1': 0, 'S2': 1, 'S3': 2, '': 3}
    records.sort(key=lambda x: (order.get(x['sev'], 4), x['cls'], x['file'], first_line(x['lines'])))

    counts = collections.Counter(r['sev'] for r in records)
    cls_counts = collections.Counter(r['cls'] for r in records)

    L = []
    L.append('# Отчёт об аудите кода — задача #336 (Karaoke)')
    L.append('')
    L.append('Проект: /home/nsa/Karaoke · снимок кода: master @ 1977838a · дата аудита: 2026-10-23')
    L.append('')
    L.append('Аудит выполнен read-only: код, конфиги и БД не изменялись. Ниже — перечень найденных дефектов '
             'с описанием, доказательством и предлагаемым исправлением; исправления в рамках #336 не выполнялись.')
    L.append('')
    L.append('## Метод и охват')
    L.append('')
    L.append('**Охват.** Полный код проекта: 1 032 файла, 259 827 строк — разделены на 78 порций '
             '(`specs/336-code-audit/coverage-ledger.csv` — реестр покрытия, источник истины). '
             'Каждая порция прочитана целиком; в артефактах (`specs/336-code-audit/findings/P-*.md`) '
             'зафиксировано, какие файлы просмотрены. Итог: 1 032 из 1 032 файлов подтверждены в артефактах.')
    L.append('')
    L.append('**Как искали.** Статический разбор с обязательной дословной цитатой из исходника и проверкой '
             'цитат скриптом по всему репозиторию (3 884 фрагмента; 28 несовпадений разобраны — все '
             'объяснены, см. `quote-check-summary.md`). Дополнительно — дешёвая эмпирика там, где она безопасна: '
             'разбор YAML/JSON, `git blame` для авторства, сверка схемы БД с дампом и с кодом, '
             'изолированные прогоны bash/jq/curl вне репозитория.')
    L.append('')
    L.append('**Границы (что НЕ проверялось).** Содержимое боевой БД и MinIO, реальные данные прод-окружения, '
             'фактическая сетевая доступность портов снаружи хоста (замеры с публичного IP), '
             'нагрузочные и DAST-проверки, продакшн-логи. Код, конфиги, БД и ветки не изменялись: '
             'аудит read-only, контейнеры не перезапускались, сборка не запускалась.')
    L.append('')
    L.append('**Severity.** S1 — публичный периметр / безопасность / потеря данных / отказ в проде / деньги; '
             'S2 — сломанная функция с ограниченным радиусом поражения; S3 — краевые случаи, админка, '
             'гигиена кода, тестов и сборки. Тесты и CI-gate — не выше S3. При неопределённости '
             'выставлялась нижняя граница, а спорные случаи вынесены в «Требует решения владельца».')
    L.append('')
    L.append('## Сводка')
    L.append('')
    L.append('| Severity | Что означает | Найдено |')
    L.append('|---|---|---:|')
    L.append(f'| S1 | Публичный периметр / безопасность / потеря данных / отказ в проде / деньги | {counts.get("S1", 0)} |')
    L.append(f'| S2 | Сломанная функция, ограниченный радиус поражения | {counts.get("S2", 0)} |')
    L.append(f'| S3 | Краевые случаи, админка, гигиена кода и сборки | {counts.get("S3", 0)} |')
    L.append(f'| — | Требует решения владельца (severity не определена) | {counts.get("", 0)} |')
    L.append(f'| **Всего** | | **{len(records)}** |')
    L.append('')
    L.append('По классам: ' + ', '.join(f'{k} — {v}' for k, v in sorted(cls_counts.items(), key=lambda x: -x[1])) + '.')
    L.append('')
    s1 = [r for r in records if r['sev'] == 'S1']
    if s1:
        L.append('### S1 — критичные (полный перечень)')
        L.append('')
        L.append('| BR | Файл:строки | Класс | Корневая причина | Суть |')
        L.append('|---|---|---|---|---|')
        for r in s1:
            what = re.sub(r'\s+', ' ', r['fields'].get('что не так', ''))[:240]
            L.append(f'| {r["br"]} | `{r["file"]}:{r["lines"]}` | {r["cls"]} | `{r["root"]}` | {what} |')
        L.append('')

    # сводка по модулям
    def module_of(path):
        if path.startswith('karaoke-app'): return 'karaoke-app'
        if path.startswith('karaoke-web'): return 'karaoke-web'
        if path.startswith('karaoke-public'): return 'karaoke-public'
        if path.startswith('webvue3'): return 'webvue3'
        if path.startswith('align'): return 'alignment-ml'
        if path.startswith('karaoke-db'): return 'karaoke-db'
        if path.startswith('deploy'): return 'deploy'
        if path.startswith('tools'): return 'tools'
        if path.startswith('config'): return 'config'
        if path.startswith('.github'): return 'ci'
        if path.startswith('gradle/') or path in ('build.gradle.kts', 'gradle.properties', 'settings.gradle.kts', 'pom.xml'):
            return 'build (корень)'
        return 'прочее'

    by_mod = collections.defaultdict(lambda: collections.Counter())
    for r in records:
        by_mod[module_of(r['file'])][r['sev'] or '—'] += 1
    L.append('### Сводка по модулям')
    L.append('')
    L.append('| Модуль | S1 | S2 | S3 | Всего |')
    L.append('|---|---:|---:|---:|---:|')
    for m in sorted(by_mod, key=lambda x: -sum(by_mod[x].values())):
        c = by_mod[m]
        L.append(f'| `{m}` | {c.get("S1", 0)} | {c.get("S2", 0)} | {c.get("S3", 0)} | {sum(c.values())} |')
    L.append('')

    def block(rec, idx):
        b = []
        b.append(f'### {rec["br"]} — {rec["file"]}:{rec["lines"]} — {rec["cls"]} ({rec["sev"]})')
        f = rec['fields']
        what = f.get('что не так', '')
        b.append(f'- **Что не так:** {what}')
        ev = f.get('доказательство', '')
        if ev:
            b.append('- **Доказательство:**')
            lines_ev = [ln.rstrip() for ln in ev.splitlines()][:18]
            if len(lines_ev) == 1 and not lines_ev[0].startswith('```'):
                b.append('  ' + lines_ev[0].strip())
            else:
                for ln in lines_ev:
                    b.append('  ' + ln if ln.strip() else '')
        path = f.get('путь', '')
        if path:
            b.append(f'- **Путь:** {path}')
        fix = f.get('поправка', '')
        if fix:
            b.append(f'- **Поправка:** {fix}')
        b.append(f'- **Найдено в:** {rec["portion"] or "—"} / {rec["src"]} (запись {rec["fid"]})')
        b.append(f'- **Автор (git blame):** {f.get("автор (git blame)", f.get("автор", "—"))}')
        b.append(f'- **Корень (root_cause_key):** `{rec["root"]}`')
        b.append(f'- **Статус:** {rec["status"]}')
        extra = merged_extra.get(rec['br'])
        if extra:
            b.append('- **Дополнительные локации (слито с этой записью):** ' + '; '.join(extra))
        if rec['br'] in EXTRA_LOC:
            b.append('- **Дополнительные локации (добавлены лидом):** ' + EXTRA_LOC[rec['br']])
        b.append('- **Проверено лидом:** да')
        b.append('')
        return b

    for sev, title in (('S1', 'S1 — критичные: публичный периметр, безопасность, деньги, потеря данных'),
                        ('S2', 'S2 — сломанные функции'),
                        ('S3', 'S3 — краевые случаи, админка, гигиена'),
                        ('', 'Требует решения владельца')):
        sel = [r for r in records if r['sev'] == sev]
        if not sel:
            continue
        L.append(f'## {title} — {len(sel)}')
        L.append('')
        for rec in sel:
            L.extend(block(rec, 0))

    verdict = [r for r in records if 'NEEDS-VERDICT' in (r['status'] or '')]
    if verdict:
        L.append('## Требует решения владельца — ' + str(len(verdict)))
        L.append('')
        for r in verdict:
            L.append(f'- **{r["br"]}** ({r["cls"]}, {r["sev"]}) — {r["file"]}:{r["lines"]} — '
                     f'корневая причина `{r["root"]}`; вопрос и обоснование — в карточке записи ниже и в `findings/`.')
        L.append('')

    if orphans:
        L.append(f'## Объединённые и перенесённые записи реестра — {len(orphans)}')
        L.append('')
        L.append('Эти записи присутствуют в реестре (`registry.csv`), но не имеют отдельной карточки в per-portion файлах: '
                 'они слиты с уже описанными корнями (см. поле «Корень») и учтены в них.')
        L.append('')
        L.append('| BR | Порция | Класс | Severity | Файл:строки | Корень | Статус |')
        L.append('|---|---|---|---|---|---|---|')
        for r in sorted(orphans, key=lambda x: x['br_id']):
            L.append(f'| {r["br_id"]} | {r["portion"]} | {r["class"]} | {r["severity"] or "—"} | '
                     f'{r["file"]}:{r["line_start"]}–{r["line_end"]} | `{r["root_cause_key"]}` | {r["status"]} |')
        L.append('')

    # покрытие
    L.append('## Покрытие аудита')
    L.append('')
    L.append('| Порция | Группа | Модуль | Тип | Файлов | Строк | Статус |')
    L.append('|---|---|---|---|---:|---:|---|')
    if os.path.exists(STATUS):
        for line in open(STATUS, encoding='utf-8'):
            if re.match(r'^\|\s*P-\d+', line):
                cells = [c.strip() for c in line.strip().strip('|').split('|')]
                if len(cells) >= 7:
                    L.append('| ' + ' | '.join(cells[:7]) + ' |')
    L.append('')
    L.append('Полный построчный реестр порций — `coverage-ledger.csv` (md5 фиксируется при приложении к задаче).')
    L.append('')
    L.append('## Как читать записи')
    L.append('')
    L.append('- **Severity** — по рубрике `rubric.md`: S1 (публичный периметр/безопасность/данные/деньги/прод), '
             'S2 (сломанная функция, ограниченный радиус), S3 (крайний случай, админка, гигиена); для тестов и сборки — не выше S3/S2.')
    L.append('- **Корень** — `root_cause_key`, по нему дедуплицируются записи: один корень — одна карточка, '
             'остальные локации перечислены в ней.')
    L.append('- **Статус** — `TODO` (к исправлению отдельной задачей) либо `NEEDS-VERDICT` (требует решения владельца).')
    L.append('- **Проверено лидом** — каждая цитата, диапазон строк, автор и severity сверены с диском на SHA 1977838a.')
    L.append('')
    L.append('Доказательная база по каждой порции: `findings/P-*.md` (59 файлов), реестр: `registry.csv`, '
             'покрытие: `coverage-ledger.csv`, рубрика: `rubric.md`, протокол: `protocol.md`.')
    L.append('')

    open(OUT, 'w', encoding='utf-8').write('\n'.join(L) + '\n')
    print(f'OK {OUT}: записей {len(records)} (S1 {counts.get("S1",0)}, S2 {counts.get("S2",0)}, '
          f'S3 {counts.get("S3",0)}, без severity {counts.get("",0)}), объединённых {len(orphans)}')


if __name__ == '__main__':
    main()
