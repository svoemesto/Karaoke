# Guidelines

> **Статус**: наполнено в спеке 332.

Стандартные паттерны и ограничения, действующие на весь проект.

## Структура

| Документ | Назначение |
| --- | --- |
| [architecture-conventions.md](architecture-conventions.md) | Сводный справочник архитектурных конвенций |
| [code-style.md](code-style.md) | Kotlin / Vue / SQL / Markdown / Shell / Docker style |
| [runbooks/README.md](runbooks/README.md) | Индекс операционных playbook'ов |

## Планируемое наполнение

| Документ | Назначение | Источник |
| --- | --- | --- |
| `kdoc-jsdoc.md` | Правила KDoc/JSDoc и шаблоны | `docs/api/README.md` |
| `vue-style.md` | Vue/Vite style (выжимка из CONTRIBUTING.md) | TBD |

## Связь с шаблонами

В отличие от `knowledge/templates/`, здесь лежат **руководства и паттерны**,
а не структурные шаблоны документов.

## Как использовать

- **Новый разработчик** → начать с [architecture-conventions.md](architecture-conventions.md)
  (ловушки TOP-10) и [code-style.md](code-style.md) (style guide).
- **Перед merge** → запустить линтеры согласно [code-style.md § Линтеры](code-style.md#линтеры-и-форматирование).
- **Оперативная задача** → найти playbook в [runbooks/README.md](runbooks/README.md)
  и следовать ему.
