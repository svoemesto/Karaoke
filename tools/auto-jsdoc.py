#!/usr/bin/env python3
"""
tools/auto-jsdoc.py

Авто-генерация базового JSDoc для Vue/TS файлов без JSDoc.

Поддерживает:
- `<script> export default { ... }</script>` (большинство компонентов)
- `<script setup>` (3 файла: App.vue, PlayerView.vue)
- `defineComponent({...})` (Composition API)

JSDoc вставляется:
- Перед `export default {...}` (с пустой строкой)
- Перед `defineComponent({...})` (с пустой строкой)
- Перед `<script setup>` (но как `// ` комментарий, не JSDoc — `<script setup>` в Vue SFC)

Содержимое JSDoc:
- Краткое описание (на основе имени файла + содержимого)
- `@prop` для каждого prop (тип определяется из Vue/Pinia)
- `@emits` для каждого emit
- `@see docs/features/<slug>.md` (из CLASS_SLUG_OVERRIDE или AGENTS.md)
"""
import os
import re
import sys
from pathlib import Path

# Регулярки
EXPORT_DEFAULT_RE = re.compile(r'^\s*export\s+default\s+\{', re.M)
DEFINE_COMPONENT_RE = re.compile(r'^\s*defineComponent\s*\(', re.M)
SCRIPT_OPEN_RE = re.compile(r'<script(\s+setup)?\s*>')

# Маппинг "имя → slug"
NAME_SLUG_OVERRIDE = {
    'SongsTable': 'docs/features/dual-db-sync.md',
    'SongsTable2': 'docs/features/dual-db-sync.md',
    'SyncTable': 'docs/features/dual-db-sync.md',
    'AuthorsTable': 'docs/features/dual-db-sync.md',
    'PicturesTable': 'docs/features/dual-db-sync.md',
    'ProcessesTable': 'docs/features/async-process-queue.md',
    'SiteUsersTable': 'docs/features/dual-db-sync.md',
    'SitePlaylistsTable': 'docs/features/dual-db-sync.md',
    'DictionariesTable': 'docs/features/dual-db-sync.md',
    'PropertiesTable': 'docs/features/mlt-generator.md',
    'HealthReportTable': 'docs/features/monitoring.md',
    'HealthReportTableBody': 'docs/features/monitoring.md',
    'StatsView': 'docs/features/monitoring.md',
    'PublishView': 'docs/features/telegram-auto-publish.md',
    'NewsView': 'docs/features/dual-db-sync.md',
    'HomeView': 'AGENTS.md',
    'SongEdit': 'docs/features/mlt-generator.md',
    'SongEditorView': 'docs/features/mlt-generator.md',
    'SongEditModal': 'docs/features/mlt-generator.md',
    'SongKaraokeEditorView': 'docs/features/mlt-generator.md',
    'SongKaraokeEditorModal': 'docs/features/mlt-generator.md',
    'SiteUserEdit': 'docs/features/dual-db-sync.md',
    'SiteUserEditModal': 'docs/features/dual-db-sync.md',
    'SearchText': 'docs/features/llm-lyrics-search.md',
    'SubsEdit': 'docs/features/mlt-generator.md',
    'PictureEdit': 'docs/features/dual-db-sync.md',
    'PictureEditModal': 'docs/features/dual-db-sync.md',
    'FamilySongsModal': 'docs/features/dual-db-sync.md',
    'AuthorAliasesModal': 'docs/features/dual-db-sync.md',
    'SiteUsersFilterModal': 'docs/features/dual-db-sync.md',
    'SiteUsersEditModal': 'docs/features/dual-db-sync.md',
    'PicturesFilterModal': 'docs/features/dual-db-sync.md',
    'SongsFilterModal': 'docs/features/dual-db-sync.md',
    'ProcessesFilterModal': 'docs/features/async-process-queue.md',
    'PropertiesFilterModal': 'docs/features/mlt-generator.md',
    'DictionariesFilterModal': 'docs/features/dual-db-sync.md',
    'PicturesEditModal': 'docs/features/dual-db-sync.md',
    'SongsEditModal': 'docs/features/dual-db-sync.md',
    'MonitorModal': 'docs/features/monitoring.md',
    'CustomConfirm': 'AGENTS.md',
    'ProcessWorker': 'docs/features/async-process-queue.md',
    'PublicSettings': 'docs/features/telegram-auto-publish.md',
    'SyncTable2': 'docs/features/dual-db-sync.md',
    'KaraokePlayer': 'docs/features/mp4-render.md',
    'SiteAuth': 'AGENTS.md',
    'SiteCart': 'docs/features/telegram-auto-publish.md',
    'SitePlaylist': 'AGENTS.md',
    'SiteFavorites': 'AGENTS.md',
    'SiteUser': 'AGENTS.md',
    'SitePayment': 'AGENTS.md',
    'Player': 'docs/features/mp4-render.md',
    'PlayerControls': 'docs/features/mp4-render.md',
    'EditorWorkView': 'AGENTS.md',
    'SitePlaylistEditView': 'AGENTS.md',
    'HomeView': 'AGENTS.md',
    'SongView': 'docs/features/mlt-generator.md',
    'AccountView': 'AGENTS.md',
    'SitePlaylistView': 'AGENTS.md',
    'SiteFavoritesView': 'AGENTS.md',
    'SiteUserView': 'AGENTS.md',
    'SitePaymentView': 'AGENTS.md',
    'SiteNewsView': 'AGENTS.md',
    'SiteSearchView': 'AGENTS.md',
    'AdminView': 'AGENTS.md',
    'NotFoundView': 'AGENTS.md',
    'App': 'AGENTS.md',
}


def split_camel(name):
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1 \2', name)
    s = re.sub(r'([a-z])([A-Z])', r'\1 \2', s)
    s = re.sub(r'([A-Z]+)([A-Z][a-z])', r'\1 \2', s)
    return s


def describe(name, kind='component'):
    words = split_camel(name)
    parts = words.split() if words else [name]
    if not parts:
        parts = [name]
    if name == 'App':
        return 'Корневой компонент приложения.'
    if name.endswith('View') and len(parts) > 1:
        base = ' '.join(parts[:-1])
        return f'View-страница «{base}» — основной layout и data-fetching.'
    if name.endswith('View'):
        return 'View-страница.'
    if name.endswith('Modal') and len(parts) > 1:
        return f'Модальное окно для {parts[-2].lower()}.'
    if name.endswith('Table') and len(parts) > 1:
        return f'Таблица со списком {parts[-2].lower()} с пагинацией, фильтрами и сортировкой.'
    if name.endswith('EditModal') and len(parts) > 1:
        return f'Модальное окно редактирования {parts[-3].lower() if len(parts) > 2 else parts[0]}.'
    if name.endswith('FilterModal') and len(parts) > 1:
        return f'Модальное окно фильтров для {parts[-3].lower() if len(parts) > 2 else parts[0]}.'
    if name.endswith('Edit') and len(parts) > 1:
        return f'Форма редактирования {parts[-2].lower()}.'
    if name.endswith('Store') and len(parts) > 1:
        return f'Vuex/Pinia store для {parts[-2].lower()}.'
    if name == 'store':
        return 'Vuex/Pinia store.'
    if name.startswith('use'):
        return f'Vue composable: {parts[-1].lower()}.'
    if name == 'KaraokePlayer':
        return 'Главный компонент караоке-плеера (canvas + Web Audio API).'
    return f'Компонент «{words}».'


def extract_name(content):
    """Извлекает name компонента из export default { name: 'X' }."""
    m = re.search(r"name:\s*['\"]([\w-]+)['\"]", content)
    if m:
        return m.group(1)
    return None


def extract_props(content):
    """Извлекает props из Vue компонента (грубо)."""
    props = []
    # props: { foo: { type: String, required: true }, bar: { type: Number, default: 0 } }
    m = re.search(r'props:\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}', content, re.DOTALL)
    if m:
        block = m.group(1)
        # Каждый prop: name: { type: ..., required: ..., default: ... }
        for pm in re.finditer(r'(\w+):\s*\{([^}]*)\}', block):
            prop_name = pm.group(1)
            prop_def = pm.group(2)
            type_m = re.search(r'type:\s*(\w+)', prop_def)
            req_m = re.search(r'required:\s*true', prop_def)
            type_name = type_m.group(1) if type_m else 'any'
            req_str = ' (required)' if req_m else ''
            props.append((prop_name, type_name, req_str))
    return props


def extract_emits(content):
    """Извлекает emits из Vue компонента (грубо)."""
    emits = []
    m = re.search(r"emits:\s*\[([^\]]+)\]", content)
    if m:
        items = re.findall(r"['\"](\w+)['\"]", m.group(1))
        emits = items
    return emits


def extract_module_name(filename):
    """Извлекает имя Vuex/Pinia модуля из имени файла (например, 'store.js' → 'Store')."""
    base = Path(filename).stem
    if base == 'store':
        return None
    return base


def generate_jsdoc(name, kind='component', props=None, emits=None, slug='AGENTS.md'):
    desc = describe(name, kind)
    lines = [
        '/**',
        f' * {desc}',
    ]
    if props:
        lines.append(' *')
        for pname, ptype, req in props[:10]:  # max 10
            lines.append(f' * @prop {{{ptype}}} {pname}{req}')
        if len(props) > 10:
            lines.append(f' * @prop ... ещё {len(props) - 10} props')
    if emits:
        lines.append(' *')
        for e in emits[:5]:
            lines.append(f' * @emits {e}')
        if len(emits) > 5:
            lines.append(f' * @emits ... ещё {len(emits) - 5} emits')
    lines += [
        ' *',
        f' * @see {slug}',
        ' */',
    ]
    return '\n'.join(lines)


def get_slug(name):
    if name in NAME_SLUG_OVERRIDE:
        return NAME_SLUG_OVERRIDE[name]
    # Дефолт по типу
    if name and (name.endswith('View') or name.endswith('Modal') or name.endswith('Table')):
        return 'AGENTS.md'
    return 'AGENTS.md'


def process_file(filepath, dry_run=False):
    """Обрабатывает один Vue/TS файл."""
    with open(filepath) as f:
        content = f.read()
    lines = content.split('\n')

    # Определяем тип файла
    has_export_default = bool(EXPORT_DEFAULT_RE.search(content))
    has_define_component = bool(DEFINE_COMPONENT_RE.search(content))
    has_script_setup = '<script setup>' in content

    if not (has_export_default or has_define_component or has_script_setup):
        return 0

    # Имя компонента
    name = extract_name(content)
    if not name:
        # Пробуем из имени файла
        stem = Path(filepath).stem
        name = stem[0].upper() + stem[1:] if stem else 'Component'

    # Для файла с одним только <script setup> (без export default / defineComponent) — JSDoc в HTML-комментарии
    if has_script_setup and not (has_export_default or has_define_component):
        # Найти <script setup> и вставить JSDoc ПЕРЕД как HTML-комментарий
        # НО для typed Vue это не идеально. Пропускаем.
        return 0

    # Проверяем наличие JSDoc
    target_re = EXPORT_DEFAULT_RE if has_export_default else DEFINE_COMPONENT_RE
    target_match = target_re.search(content)
    target_pos = target_match.start()
    # Ищем /** в предыдущих 10 строках
    target_line = content[:target_pos].count('\n')
    has_jsdoc = False
    for j in range(max(0, target_line - 10), target_line):
        if lines[j].lstrip().startswith('/**'):
            has_jsdoc = True
            break
    if has_jsdoc:
        return 0

    # Извлекаем props и emits
    props = extract_props(content)
    emits = extract_emits(content)
    slug = get_slug(name)

    jsdoc = generate_jsdoc(name, 'component', props, emits, slug)

    # Вставляем JSDoc перед target (export default или defineComponent)
    # Вставляем с пустой строкой ПЕРЕД (если предыдущая строка не пустая)
    insert_line = target_line
    if insert_line > 0:
        prev = lines[insert_line - 1].strip()
        if prev and not prev.startswith('//') and not prev.startswith('*'):
            jsdoc = '\n' + jsdoc

    jsdoc_lines = jsdoc.split('\n')
    new_lines = lines[:insert_line] + jsdoc_lines + lines[insert_line:]
    new_content = '\n'.join(new_lines)

    if new_content != content and not dry_run:
        with open(filepath, 'w') as f:
            f.write(new_content)
    return 1


def main():
    dry_run = '--dry-run' in sys.argv
    roots = [a for a in sys.argv[1:] if not a.startswith('--')]
    total = 0
    for root in roots:
        for r, _, files in os.walk(root):
            for f in files:
                if not f.endswith(('.vue', '.js', '.ts')):
                    continue
                added = process_file(os.path.join(r, f), dry_run)
                if added:
                    total += added
                    action = 'would add' if dry_run else 'added'
                    print(f'  {action} 1 JSDoc in {os.path.relpath(os.path.join(r, f), ".")}')
    print(f'\nTotal: {total} JSDoc blocks {"would be" if dry_run else ""} added')


if __name__ == '__main__':
    main()
