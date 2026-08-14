# templates/ — шаблоны для новых LiveDocs (индекс)

> Заготовки для создания новых LiveDoc-документов. Каждый шаблон — каркас
> с плейсхолдерами `<...>`, не готовый текст.

## Шаблоны

| Шаблон | Назначение | Когда использовать |
|---------|------------|---------------------|
| [feature-summary.md](feature-summary.md) | SDD-сводка фичи | Миграция существующей спеки → `livedocs/features/<NNN-slug>.md` |
| [bounded-context.md](bounded-context.md) | DDD bounded context | Выделен новый aggregate root → `livedocs/domain/<context>.md` |
| [c4-level-L1.md](c4-level-L1.md) | C4 уровень 1 | Изменилась внешняя интеграция → `livedocs/architecture/L1-system-context.md` |
| [c4-level-L2.md](c4-level-L2.md) | C4 уровень 2 | Добавлен новый контейнер → `livedocs/architecture/L2-containers.md` |
| [c4-level-L3.md](c4-level-L3.md) | C4 уровень 3 | Изменился компонент karaoke-app → `livedocs/architecture/L3-components.md` |

## Как использовать

1. Скопировать нужный шаблон в целевую директорию (`cp template.md target.md`).
2. Переименовать (если нужно) по naming convention целевого слоя.
3. Добавить frontmatter (`status`, `slug`, `related`, опционально `type`/`level`).
4. Заменить плейсхолдеры `<...>` на реальный контент.
5. Проверить: `bash tools/check-livedocs-structure.sh` → exit 0.

## Конвенции шаблонов

- Шаблоны не имеют frontmatter (это заготовки).
- Плейсхолдеры в `<...>`-скобках показывают, что нужно заполнить.
- Каждый шаблон содержит «Историю» в конце (Создан + Последнее обновление).
- Не редактируйте шаблоны после создания — это эталон. Если шаблон не подходит,
  обновите его отдельным коммитом.

## Связь с CI

`tools/check-livedocs-structure.sh` валидирует, что новые `.md` файлы (кроме
README и templates/) имеют frontmatter. Это значит, что **любой скопированный
из templates/ файл должен получить frontmatter** перед коммитом.