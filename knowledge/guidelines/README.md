# Guidelines

> **Статус**: наполнено в спеке 332.

Стандартные паттерны и ограничения, действующие на весь проект.

## Структура

| Документ | Назначение |
| --- | --- |
| [architecture-conventions.md](architecture-conventions.md) | Сводный справочник архитектурных конвенций |
| [code-style.md](code-style.md) | Kotlin / Vue / SQL / Markdown / Shell / Docker style |
| [security-headers.md](security-headers.md) | Заголовки безопасности в прод-nginx (host-nginx сайта `sm-karaoke.ru`) |
| [kdoc-jsdoc.md](kdoc-jsdoc.md) | Правила KDoc/JSDoc и шаблоны (перенесён из слоя `docs/api/`) |
| [runbooks/README.md](runbooks/README.md) | Индекс операционных runbook'ов |
| [knowledge-first.md](knowledge-first.md) | Правило MUST #0 — обязательный pre-flight перед любой правкой (enforcement из `AGENTS.md`) |

## Планируемое наполнение

| Документ | Назначение | Источник |
| --- | --- | --- |
| `vue-style.md` | Vue/Vite style (выжимка из CONTRIBUTING.md) | TBD |

## Связь с шаблонами

В отличие от `knowledge/templates/`, здесь лежат **руководства и паттерны**,
а не структурные шаблоны документов.

## Как использовать

- **Новый разработчик** → начать с [architecture-conventions.md](architecture-conventions.md)
  (ловушки TOP-10) и [code-style.md](code-style.md) (style guide).
- **Перед merge** → запустить линтеры согласно [code-style.md § Линтеры](code-style.md#линтеры-и-форматирование).
- **Оперативная задача** → найти runbook в [runbooks/README.md](runbooks/README.md)
  и следовать ему.
