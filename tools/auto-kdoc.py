#!/usr/bin/env python3
"""
tools/auto-kdoc.py — версия 2

Улучшенная вставка KDoc:
1. Только top-level (без ведущих пробелов)
2. Вставляет KDoc после всех аннотаций (@Foo, @Bar(...))
3. Добавляет пустую строку ПЕРЕД KDoc (если предыдущая непустая строка не аннотация)
4. Добавляет пустую строку ПОСЛЕ KDoc (если предыдущая была импорт без пустой строки)
5. Не вставляет KDoc внутри других классов (вложенные)
"""
import os
import re
import subprocess
import sys
from pathlib import Path

# Top-level декларации
DECL_RE = re.compile(
    r'^(public\s+)?(class|object|interface|enum class|sealed class|data class)\s+(\w+)',
)
ANNOTATION_RE = re.compile(r'^@\w+(\([^)]*\))?$')

# BR-584: Constitution Principle II (R-07) — в проекте нет JPA/Hibernate
# (сырой JDBC + recordhash). Генератор не имеет права вставлять такие
# упоминания в KDoc: они попадали в исходники и противоречили конституции.
# Тот же набор паттернов использует guard tools/check-no-jpa-imports.sh.
FORBIDDEN_TOKENS = (
    'jpa',
    'hibernate',
    'javax.persistence',
    'org.springframework.data.jpa',
)


class GenerationError(Exception):
    """Ошибка генерации — не писать в исходники, а упасть с понятным текстом."""


def assert_no_forbidden(text, where):
    """Проверяет, что в сгенерированном тексте нет запрещённых упоминаний."""
    low = text.lower()
    for token in FORBIDDEN_TOKENS:
        if token in low:
            raise GenerationError(
                f'сгенерированный текст содержит запрещённое упоминание «{token}» ({where}); '
                f'см. Constitution Principle II / tools/check-no-jpa-imports.sh'
            )

DIR_TO_SLUG = {
    'model': 'archive/docs/features/dual-db-sync.md',
    'services': 'archive/docs/features/async-process-queue.md',
    'controllers': 'AGENTS.md',
    'mlt': 'archive/docs/features/mlt-generator.md',
    'mlt/mko': 'archive/docs/features/mlt-generator.md',
    'mlt/mko2': 'archive/docs/features/mlt-generator.md',
    'monitor': 'archive/docs/features/monitoring.md',
    'monitor/checks': 'archive/docs/features/monitoring.md',
    'llm': 'archive/docs/features/llm-lyrics-search.md',
    'sync': 'archive/docs/features/dual-db-sync.md',
    'textfiledictionary': 'AGENTS.md',
    'textfilehistory': 'AGENTS.md',
    'propertiesfiledictionary': 'AGENTS.md',
    'config': 'AGENTS.md',
    'util': 'AGENTS.md',
    'dto': 'AGENTS.md',
}

CLASS_SLUG_OVERRIDE = {
    'SseNotification': 'archive/docs/features/sse-notifications.md',
    'SseNotificationType': 'archive/docs/features/sse-notifications.md',
    'MonitorAlert': 'archive/docs/features/monitoring.md',
    'HealthReportDTO': 'archive/docs/features/monitoring.md',
    'LyricsFinder': 'archive/docs/features/llm-lyrics-search.md',
    'ScraperAgent': 'archive/docs/features/llm-lyrics-search.md',
    'Mp4Render': 'archive/docs/features/mp4-render.md',
    'Mlt': 'archive/docs/features/mlt-generator.md',
    'MltNode': 'archive/docs/features/mlt-generator.md',
    'MltNodeBuilder': 'archive/docs/features/mlt-generator.md',
    'PropertiesMltNodeBuilder': 'archive/docs/features/mlt-generator.md',
    'ProducerType': 'archive/docs/features/mlt-generator.md',
    'SettingField': 'archive/docs/features/mlt-generator.md',
    'SettingVoiceLine': 'archive/docs/features/mlt-generator.md',
    'SettingVoiceLineElement': 'archive/docs/features/mlt-generator.md',
    'SettingVoiceLineElementTypes': 'archive/docs/features/mlt-generator.md',
    'Settings': 'archive/docs/features/mlt-generator.md',
    'SettingsDTO': 'archive/docs/features/mlt-generator.md',
    'SettingsDTOdigest': 'archive/docs/features/mlt-generator.md',
    'SettingsDTOupdate': 'archive/docs/features/mlt-generator.md',
    'SongType': 'archive/docs/features/mlt-generator.md',
    'Song2': 'archive/docs/features/mlt-generator.md',
    'SongOutputFile': 'archive/docs/features/mlt-generator.md',
    'Marker': 'archive/docs/features/mlt-generator.md',
    'Markertype': 'archive/docs/features/mlt-generator.md',
    'SourceMarker': 'archive/docs/features/mlt-generator.md',
    'TelegramAutoPublish': 'archive/docs/features/telegram-auto-publish.md',
    'TelegramUpdates': 'archive/docs/features/telegram-auto-publish.md',
    'SyncTarget': 'archive/docs/features/dual-db-sync.md',
    'KaraokeDbTable': 'archive/docs/features/dual-db-sync.md',
    'KaraokeDbTableDto': 'archive/docs/features/dual-db-sync.md',
    'KaraokeDbTableField': 'archive/docs/features/dual-db-sync.md',
    'RecordHash': 'archive/docs/features/dual-db-sync.md',
    'RecordDiff': 'archive/docs/features/dual-db-sync.md',
    'ApplicationContextProvider': 'archive/docs/features/async-process-queue.md',
    'KaraokeProcessWorker': 'archive/docs/features/async-process-queue.md',
    'KaraokeProcessThread': 'archive/docs/features/async-process-queue.md',
    'MonitoringService': 'archive/docs/features/monitoring.md',
    'HealthReport': 'archive/docs/features/monitoring.md',
    'AudioAnalysisResult': 'archive/docs/features/llm-lyrics-search.md',
    'AudioCompareHistoryEntry': 'archive/docs/features/monitoring.md',
    'SearchResponseFormat': 'archive/docs/features/llm-lyrics-search.md',
    'SearchResult': 'archive/docs/features/llm-lyrics-search.md',
    'SearchAsync': 'archive/docs/features/llm-lyrics-search.md',
    'SearchResultDTO': 'archive/docs/features/llm-lyrics-search.md',
    'SearchAsyncDTO': 'archive/docs/features/llm-lyrics-search.md',
    'MltProp': 'archive/docs/features/mlt-generator.md',
    'MltKaraokeObject': 'archive/docs/features/mlt-generator.md',
    'KaraokeConnection': 'archive/docs/features/dual-db-sync.md',
    'KaraokeProperties': 'archive/docs/features/async-process-queue.md',
    'KaraokeProcess': 'archive/docs/features/async-process-queue.md',
    'KaraokeAppService': 'archive/docs/features/async-process-queue.md',
    'KaraokeWebService': 'archive/docs/features/async-process-queue.md',
    'KaraokeStorageService': 'archive/docs/features/premium-stems.md',
    'WebKaraokeStorageServiceImpl': 'archive/docs/features/premium-stems.md',
    'SseNotificationService': 'archive/docs/features/sse-notifications.md',
    'RecordChangeMessage': 'archive/docs/features/dual-db-sync.md',
    'RecordAddMessage': 'archive/docs/features/dual-db-sync.md',
    'RecordDeleteMessage': 'archive/docs/features/dual-db-sync.md',
    'ProcessWorkerStateMessage': 'archive/docs/features/async-process-queue.md',
    'ProcessCountWaitingMessage': 'archive/docs/features/async-process-queue.md',
    'MessageMessage': 'archive/docs/features/sse-notifications.md',
    'ErrorMessage': 'archive/docs/features/sse-notifications.md',
    'LogMessage': 'archive/docs/features/sse-notifications.md',
    'CrudMessage': 'archive/docs/features/dual-db-sync.md',
    'SyncMessage': 'archive/docs/features/dual-db-sync.md',
}


def split_camel(name):
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1 \2', name)
    s = re.sub(r'([a-z])([A-Z])', r'\1 \2', s)
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1 \2', s)
    return s


def describe(name, kind):
    words = split_camel(name)
    if name.endswith('Dto') or name.endswith('DTO'):
        base = name[:-3]
        if base.endswith('ies'):
            base = base[:-3] + 'y'
        elif base.endswith('s'):
            base = base[:-1]
        return f'DTO для {split_camel(base).lower()}: сериализуемое представление для API/UI.'
    if name.endswith('Entity'):
        # BR-584: без упоминания JPA — в проекте используется сырой JDBC
        # (Constitution Principle II, R-07).
        return f'Сущность домена для {split_camel(name[:-6]).lower()}.'
    if kind == 'enum class':
        return f'Перечисление возможных значений для {words.lower()}.'
    if kind == 'interface':
        return f'Интерфейс для {words.lower()}.'
    if kind == 'object':
        return f'Singleton-объект {words}.'
    if name.endswith('Service'):
        return f'Сервис для {words[:-7].lower()}.'
    if name.endswith('Controller'):
        return f'Контроллер (HTTP/WebSocket endpoints) для {words[:-10].lower()}.'
    if name.endswith('Config'):
        return f'Конфигурация для {words[:-6].lower()}.'
    if name.endswith('Builder'):
        return f'Builder для {words[:-7].lower()}.'
    if name.endswith('Factory'):
        return f'Фабрика для {words[:-7].lower()}.'
    if name.endswith('Exception'):
        return f'Исключение для случая: {words[:-9].lower()}.'
    return f'Класс {words}.'


def kdoc_block(name, kind, slug):
    desc = describe(name, kind)
    # BR-584: результат генерации проверяется ДО записи в исходник —
    # JPA/Hibernate-упоминания в KDoc недопустимы (Constitution Principle II).
    assert_no_forbidden(desc, f'описание класса {name}')
    assert_no_forbidden(slug, f'@see для {name}')
    return [
        '/**',
        f' * {desc}',
        ' *',
        f' * @see {slug}',
        ' */',
    ]


def has_existing_kdoc(lines, decl_line):
    """Есть ли KDoc непосредственно над декларацией.

    BR-585 (аудит #336, пакет PKG-S3-30): раньше KDoc искался в фиксированном
    окне в 8 строк (`range(max(0, decl_line - 8), decl_line)`). Если KDoc был
    длиннее 8 строк (например, описание с @param/@return/@throws), он не
    попадал в окно → генератор вставлял ВТОРОЙ KDoc прямо над существующим
    (клонирование KDoc). Теперь ищем закрывающий `*/` и сканируем вверх до
    `/**` — как это уже сделано в tools/auto-kdoc-quality.py.
    """
    for j in range(decl_line - 1, max(-1, decl_line - 500), -1):
        stripped = lines[j].strip()
        if stripped.endswith('*/'):
            # Нашли закрывающий `*/` — ищем `/**` выше.
            for k in range(j, max(-1, j - 500), -1):
                if lines[k].lstrip().startswith('/**'):
                    return True
                if lines[k].strip() and not lines[k].strip().startswith('*'):
                    break
            return False
        if stripped and not stripped.startswith('//') and not stripped.startswith('*'):
            # Дошли до обычной строки кода — KDoc выше не начинается.
            return False
    return False


def process(filepath, dry_run=False):
    with open(filepath) as f:
        content = f.read()
    lines = content.split('\n')

    # 1) Найти все top-level декларации
    # Top-level = без ведущих пробелов и НЕ внутри другого класса
    # Используем скобочный баланс для определения "внутри класса"
    # Но проще: top-level = на нулевом отступе (без пробелов)
    candidates = []  # список индексов строк
    for i, line in enumerate(lines):
        m = DECL_RE.match(line)
        if not m:
            continue
        candidates.append((i, m.group(2), m.group(3)))

    if not candidates:
        return 0

    # Slug
    rel_path = str(filepath)
    for prefix in ['karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/',
                   'karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/']:
        if prefix in rel_path:
            rel_path = rel_path.split(prefix, 1)[1]
            break
    parts = Path(rel_path).parts
    dir_slug = DIR_TO_SLUG.get(parts[0] if parts else '', 'archive/docs/features/dual-db-sync.md')

    # 2) Собираем план вставок: (insert_at_line, kdoc_lines, original_idx)
    # insert_at_line — куда вставить (перед какой строкой)
    insertions = []
    for decl_line, kind, name in candidates:
        # Проверяем KDoc выше (BR-585: поиск по `*/` → `/**`, а не окно в 8 строк).
        if has_existing_kdoc(lines, decl_line):
            continue
        # Определяем insert_at: KDoc должен быть ВЫШЕ всех аннотаций
        # Поднимаемся вверх от decl_line, пропуская аннотации
        first_annotation_line = decl_line
        for j in range(decl_line - 1, max(-1, decl_line - 10), -1):
            if ANNOTATION_RE.match(lines[j].strip()):
                first_annotation_line = j
            elif lines[j].strip() == '' or lines[j].lstrip().startswith('//'):
                continue
            else:
                break
        # KDoc вставляем ПЕРЕД первой аннотацией (или перед class если аннотаций нет)
        insert_at = first_annotation_line
        # Если строка выше insert_at — пустая, можно вставлять сразу после неё
        # (это даст корректное форматирование)
        # Если строка выше — непустая, добавляем пустую строку перед KDoc
        slug = CLASS_SLUG_OVERRIDE.get(name, dir_slug)
        kdoc = kdoc_block(name, kind, slug)
        insertions.append((insert_at, kdoc))

    if not insertions:
        return 0

    # Сортируем по убыванию line, чтобы вставки не сдвигали индексы
    insertions.sort(key=lambda x: -x[0])

    # 3) Применяем вставки
    # Для каждой вставки: убедимся, что перед kdoc есть пустая строка (если предыдущая непустая и не annotation)
    for insert_at, kdoc in insertions:
        # Проверяем предыдущую строку
        if insert_at > 0:
            prev = lines[insert_at - 1].strip()
            # Если предыдущая строка непустая и не аннотация, добавляем пустую строку
            if prev and not prev.startswith('@'):
                kdoc = [''] + kdoc
        # Вставляем
        lines = lines[:insert_at] + kdoc + lines[insert_at:]

    new_content = '\n'.join(lines)
    if new_content != content and not dry_run:
        with open(filepath, 'w') as f:
            f.write(new_content)
    return len(insertions)


def validate_slugs():
    """Проверить, что все @see-цели существуют.

    BR-583 (аудит #336, пакет PKG-S3-30): генератор писал в исходники
    `@see docs/features/<slug>.md`, но живые per-feature документы лежат в
    `archive/docs/features/` — ссылки были битыми. Теперь префикс правильный,
    а существование цели ПРОВЕРЯЕТСЯ: битая ссылка не уходит в код молча.
    """
    root = Path(__file__).resolve().parent.parent
    slugs = set(DIR_TO_SLUG.values()) | set(CLASS_SLUG_OVERRIDE.values())
    missing = []
    for slug in sorted(slugs):
        if slug == 'AGENTS.md':
            continue
        if not (root / slug).exists():
            missing.append(slug)
    return missing


def verify_with_jpa_guard():
    """BR-584: проверяет результат генерации guard'ом check-no-jpa-imports.sh.

    Возвращает 0 — проверка пройдена; 1 — guard нашёл нарушение (R-07);
    2 — проверку выполнить не удалось (guard не найден) — это тоже ошибка:
    молча пропускать проверку нельзя.
    """
    repo_root = Path(__file__).resolve().parent.parent
    guard = repo_root / 'tools' / 'check-no-jpa-imports.sh'
    if not guard.exists():
        print(
            f'ERROR: guard {guard} не найден — результат генерации НЕ ПРОВЕРЕН',
            file=sys.stderr,
        )
        return 2
    proc = subprocess.run(['bash', str(guard)], cwd=str(repo_root))
    if proc.returncode != 0:
        print(
            'ERROR: tools/check-no-jpa-imports.sh нашёл нарушения — результат '
            'генерации не соответствует Constitution Principle II (R-07)',
            file=sys.stderr,
        )
        return 1
    return 0


def main():
    dry_run = '--dry-run' in sys.argv
    roots = [a for a in sys.argv[1:] if not a.startswith('--')]

    # BR-583: битые @see-ссылки не должны уходить в исходники молча.
    missing = validate_slugs()
    if missing:
        print('ERROR: несуществующие @see-цели:', file=sys.stderr)
        for slug in missing:
            print(f'  {slug}', file=sys.stderr)
        sys.exit(1)

    total = 0
    for root in roots:
        for r, _, files in os.walk(root):
            for f in files:
                if not f.endswith('.kt'):
                    continue
                try:
                    added = process(os.path.join(r, f), dry_run)
                except GenerationError as exc:
                    print(f'ERROR: {os.path.join(r, f)}: {exc}', file=sys.stderr)
                    sys.exit(1)
                if added:
                    total += added
                    print(f'  +{added} {os.path.join(r, f)}')
    print(f'\nTotal: {total} KDoc blocks {"would be" if dry_run else ""} added')

    # BR-584: результат генерации проверяется guard'ом R-07.
    rc = verify_with_jpa_guard()
    if rc != 0:
        sys.exit(rc)


def run_self_test():
    """Самопроверка: @see-цели существуют + KDoc-детектор не клонирует (BR-583/BR-585)."""
    failures = 0

    missing = validate_slugs()
    if missing:
        print('SELFTEST FAIL: несуществующие @see-цели:', file=sys.stderr)
        for slug in missing:
            print(f'  {slug}', file=sys.stderr)
        failures += 1
    else:
        print('SELFTEST OK: все @see-цели существуют')

    # BR-585: длинный KDoc (>8 строк) должен быть найден, второй KDoc — не вставлен.
    long_kdoc = [
        '/**',
        ' * Описание класса.',
        ' *',
        ' * @param a первый параметр',
        ' * @param b второй параметр',
        ' * @param c третий параметр',
        ' * @param d четвёртый параметр',
        ' * @param e пятый параметр',
        ' * @return результат',
        ' */',
        'class Example',
    ]
    if not has_existing_kdoc(long_kdoc, len(long_kdoc) - 1):
        print('SELFTEST FAIL: длинный KDoc не распознан (BR-585)', file=sys.stderr)
        failures += 1
    else:
        print('SELFTEST OK: длинный KDoc (>8 строк) распознан — второй KDoc не вставляется')

    # Короткий KDoc — тоже распознаётся.
    short_kdoc = ['/**', ' * Класс.', ' */', 'class Example']
    if not has_existing_kdoc(short_kdoc, len(short_kdoc) - 1):
        print('SELFTEST FAIL: короткий KDoc не распознан', file=sys.stderr)
        failures += 1
    else:
        print('SELFTEST OK: короткий KDoc распознан')

    # Без KDoc — вставка нужна.
    no_kdoc = ['class Example', '    val x = 1']
    if has_existing_kdoc(no_kdoc, 0):
        print('SELFTEST FAIL: KDoc отсутствует, но найден', file=sys.stderr)
        failures += 1
    else:
        print('SELFTEST OK: без KDoc — вставка требуется')

    if failures:
        print(f'SELFTEST FAIL: {failures} проверок не прошли', file=sys.stderr)
        return 1
    print('SELFTEST OK: 4/4 проверок прошли')
    return 0


if __name__ == '__main__':
    if '--self-test' in sys.argv:
        sys.exit(run_self_test())
    main()
