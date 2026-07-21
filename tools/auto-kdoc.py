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
import sys
from pathlib import Path

# Top-level декларации
DECL_RE = re.compile(
    r'^(public\s+)?(class|object|interface|enum class|sealed class|data class)\s+(\w+)',
)
ANNOTATION_RE = re.compile(r'^@\w+(\([^)]*\))?$')

DIR_TO_SLUG = {
    'model': 'docs/features/dual-db-sync.md',
    'services': 'docs/features/async-process-queue.md',
    'controllers': 'AGENTS.md',
    'mlt': 'docs/features/mlt-generator.md',
    'mlt/mko': 'docs/features/mlt-generator.md',
    'mlt/mko2': 'docs/features/mlt-generator.md',
    'monitor': 'docs/features/monitoring.md',
    'monitor/checks': 'docs/features/monitoring.md',
    'llm': 'docs/features/llm-lyrics-search.md',
    'sync': 'docs/features/dual-db-sync.md',
    'textfiledictionary': 'AGENTS.md',
    'textfilehistory': 'AGENTS.md',
    'propertiesfiledictionary': 'AGENTS.md',
    'config': 'AGENTS.md',
    'util': 'AGENTS.md',
    'dto': 'AGENTS.md',
}

CLASS_SLUG_OVERRIDE = {
    'SseNotification': 'docs/features/sse-notifications.md',
    'SseNotificationType': 'docs/features/sse-notifications.md',
    'MonitorAlert': 'docs/features/monitoring.md',
    'HealthReportDTO': 'docs/features/monitoring.md',
    'LyricsFinder': 'docs/features/llm-lyrics-search.md',
    'ScraperAgent': 'docs/features/llm-lyrics-search.md',
    'Mp4Render': 'docs/features/mp4-render.md',
    'Mlt': 'docs/features/mlt-generator.md',
    'MltNode': 'docs/features/mlt-generator.md',
    'MltNodeBuilder': 'docs/features/mlt-generator.md',
    'PropertiesMltNodeBuilder': 'docs/features/mlt-generator.md',
    'ProducerType': 'docs/features/mlt-generator.md',
    'SettingField': 'docs/features/mlt-generator.md',
    'SettingVoiceLine': 'docs/features/mlt-generator.md',
    'SettingVoiceLineElement': 'docs/features/mlt-generator.md',
    'SettingVoiceLineElementTypes': 'docs/features/mlt-generator.md',
    'Settings': 'docs/features/mlt-generator.md',
    'SettingsDTO': 'docs/features/mlt-generator.md',
    'SettingsDTOdigest': 'docs/features/mlt-generator.md',
    'SettingsDTOupdate': 'docs/features/mlt-generator.md',
    'SongType': 'docs/features/mlt-generator.md',
    'Song2': 'docs/features/mlt-generator.md',
    'SongOutputFile': 'docs/features/mlt-generator.md',
    'Marker': 'docs/features/mlt-generator.md',
    'Markertype': 'docs/features/mlt-generator.md',
    'SourceMarker': 'docs/features/mlt-generator.md',
    'TelegramAutoPublish': 'docs/features/telegram-auto-publish.md',
    'TelegramUpdates': 'docs/features/telegram-auto-publish.md',
    'SyncTarget': 'docs/features/dual-db-sync.md',
    'KaraokeDbTable': 'docs/features/dual-db-sync.md',
    'KaraokeDbTableDto': 'docs/features/dual-db-sync.md',
    'KaraokeDbTableField': 'docs/features/dual-db-sync.md',
    'RecordHash': 'docs/features/dual-db-sync.md',
    'RecordDiff': 'docs/features/dual-db-sync.md',
    'ApplicationContextProvider': 'docs/features/async-process-queue.md',
    'KaraokeProcessWorker': 'docs/features/async-process-queue.md',
    'KaraokeProcessThread': 'docs/features/async-process-queue.md',
    'MonitoringService': 'docs/features/monitoring.md',
    'HealthReport': 'docs/features/monitoring.md',
    'AudioAnalysisResult': 'docs/features/llm-lyrics-search.md',
    'AudioCompareHistoryEntry': 'docs/features/monitoring.md',
    'SearchResponseFormat': 'docs/features/llm-lyrics-search.md',
    'SearchResult': 'docs/features/llm-lyrics-search.md',
    'SearchAsync': 'docs/features/llm-lyrics-search.md',
    'SearchResultDTO': 'docs/features/llm-lyrics-search.md',
    'SearchAsyncDTO': 'docs/features/llm-lyrics-search.md',
    'MltProp': 'docs/features/mlt-generator.md',
    'MltKaraokeObject': 'docs/features/mlt-generator.md',
    'KaraokeConnection': 'docs/features/dual-db-sync.md',
    'KaraokeProperties': 'docs/features/async-process-queue.md',
    'KaraokeProcess': 'docs/features/async-process-queue.md',
    'KaraokeAppService': 'docs/features/async-process-queue.md',
    'KaraokeWebService': 'docs/features/async-process-queue.md',
    'KaraokeStorageService': 'docs/features/premium-stems.md',
    'WebKaraokeStorageServiceImpl': 'docs/features/premium-stems.md',
    'SseNotificationService': 'docs/features/sse-notifications.md',
    'RecordChangeMessage': 'docs/features/dual-db-sync.md',
    'RecordAddMessage': 'docs/features/dual-db-sync.md',
    'RecordDeleteMessage': 'docs/features/dual-db-sync.md',
    'ProcessWorkerStateMessage': 'docs/features/async-process-queue.md',
    'ProcessCountWaitingMessage': 'docs/features/async-process-queue.md',
    'MessageMessage': 'docs/features/sse-notifications.md',
    'ErrorMessage': 'docs/features/sse-notifications.md',
    'LogMessage': 'docs/features/sse-notifications.md',
    'CrudMessage': 'docs/features/dual-db-sync.md',
    'SyncMessage': 'docs/features/dual-db-sync.md',
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
        return f'JPA-сущность для {split_camel(name[:-6]).lower()}.'
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
    return [
        '/**',
        f' * {desc}',
        ' *',
        f' * @see {slug}',
        ' */',
    ]


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
    dir_slug = DIR_TO_SLUG.get(parts[0] if parts else '', 'docs/features/dual-db-sync.md')

    # 2) Собираем план вставок: (insert_at_line, kdoc_lines, original_idx)
    # insert_at_line — куда вставить (перед какой строкой)
    insertions = []
    for decl_line, kind, name in candidates:
        # Проверяем KDoc выше
        has_kdoc = False
        for j in range(max(0, decl_line - 8), decl_line):
            l = lines[j].lstrip()
            if l.startswith('/**') and not l.startswith('//'):
                has_kdoc = True
                break
        if has_kdoc:
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


def main():
    dry_run = '--dry-run' in sys.argv
    roots = [a for a in sys.argv[1:] if not a.startswith('--')]
    total = 0
    for root in roots:
        for r, _, files in os.walk(root):
            for f in files:
                if not f.endswith('.kt'):
                    continue
                added = process(os.path.join(r, f), dry_run)
                if added:
                    total += added
                    print(f'  +{added} {os.path.join(r, f)}')
    print(f'\nTotal: {total} KDoc blocks {"would be" if dry_run else ""} added')


if __name__ == '__main__':
    main()
