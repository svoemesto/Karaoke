# Contract: PerFeatureDocument в `docs/features/<slug>.md`

**Branch**: `001-code-standards-docs` | **Phase**: 1 | **Date**: 2026-07-20

Контракт формата и инвариантов per-feature документа — одного `.md` файла
в `docs/features/`, описывающего одну ключевую подсистему проекта Karaoke.

## Файл

`/docs/features/<slug>.md`, где `slug` ∈ kebab-case.

**9 обязательных slug'ов** (из FR-004 spec.md):

| Slug | Полное название |
|------|-----------------|
| `mlt-generator` | MLT-генератор караоке-видео |
| `async-process-queue` | Async-очередь задач `KaraokeProcess*` |
| `dual-db-sync` | Двух-БД синхронизация LOCAL↔SERVER |
| `mp4-render` | Рендер MP4 из онлайн-плеера |
| `sse-notifications` | SSE-уведомления |
| `premium-stems` | Премиум-фича «Создать минусовку» |
| `llm-lyrics-search` | LLM-assisted поиск текстов и аккордов |
| `telegram-auto-publish` | Автопубликация в Telegram-канал |
| `monitoring` | Мониторинг ключевых моментов |

## Top-level структура

```markdown
# <Feature Name>

> **Status**: active | deprecated | experimental
> **Feature Key**: <один из 9 ключей>
> **Last Updated**: YYYY-MM-DD

## Что делает

<1-2 предложения, что это за фича.>

## Зачем

<Какую проблему решает, для кого.>

## Как работает (кратко)

<Архитектурный обзор: ключевые компоненты, поток данных, диаграмма
или список файлов.>

## Инварианты / правила

- **MUST**: <правило 1> ([AGENTS.md#anchor])
- **MUST**: <правило 2> ([DEVELOPMENT.md#anchor])
- **SHOULD**: <правило 3>
- ...

## Известные ловушки

- <Ловушка 1: что ломало прод> ([ссылка на issue или commit])
- <Ловушка 2: edge case>

## Ссылки на ключевые классы/файлы

- [`path/to/File.kt:NN`](../../path/to/File.kt) — <что тут>
- [`Class#method`](../../path/to/File.kt) — <что делает>
- [`path/to/Component.vue`](../../path/to/Component.vue) — <назначение>
```

## Инварианты формата

1. **Все 6 секций (`Что делает`, `Зачем`, `Как работает`, `Инварианты`,
   `Известные ловушки`, `Ссылки`) присутствуют и непусты**.

2. **Slug соответствует `featureKey`** kebab-case-преобразованием.
   Валидация: имя файла == `featureKey` (если `featureKey` уже kebab-case,
   что верно для всех 9 ключей).

3. **Status ∈ {active, deprecated, experimental}** — `active` по умолчанию.
   Deprecated/Experimental требуют обоснования в первой строке.

4. **Ссылки в `Ссылки на ключевые классы/файлы`** — в формате
   `[`label`](../../relative/path)`:
   - Markdown-ссылки (не просто текст), чтобы `lychee` мог их проверить.
   - Относительный путь от `docs/features/<slug>.md` к целевому файлу
     (т.е. `../../<path-from-repo-root>`).
   - Опционально `:NNN` или `#symbol` для уточнения, но без
     `lychee`-валидации (см. ограничение ниже).

5. **Каждый инвариант в секции `Инварианты / правила`** имеет хотя бы одну
   ссылку на `AGENTS.md` / `DEVELOPMENT.md` / `constitution.md` (формат
   `[<file>.md#<anchor>](<path>)`). Валидация: `lychee` + ручная проверка
   anchor'ов.

6. **Каждый пункт в `Известные ловушки`** — атомарный (одна ловушка = один
   bullet), с явной отсылкой к «что сломалось» или «где описано».

7. **Секция `Как работает`** — не пересказ кода, а архитектурный обзор.
   Если нужно показать последовательность — диаграмма (mermaid) или список
   шагов. Не вставлять большие куски кода (это в KDoc, не здесь).

8. **Last Updated** — обновляется вручную при значимых правках; формат
   ISO `YYYY-MM-DD`.

## Ограничения

- **`lychee` не понимает Kotlin-symbol-anchors** (`Class#method`).
  Валидация символов — отдельный простой скрипт
  `tools/verify-kotlin-refs.sh` (P3), который парсит `Class.method` или
  `Class#method` из ссылок и проверяет наличие в `ktlint`-индексе или
  через `kotlin-indexer`. До внедрения этого скрипта ссылки на символы
  проверяются вручную при code review.

- **Cross-doc ссылки** (per-feature документ ссылается на другой
  per-feature документ) допустимы, но требуют относительного пути
  `../<other-slug>.md`.

## Скрипт валидации (для pre-commit / CI)

`tools/check-feature-doc.sh <path-to-doc.md>`:

1. Проверить, что присутствуют все 6 секций (regex по `^## `).
2. Проверить, что slug == имя файла без `.md`.
3. Проверить, что все ссылки — Markdown-формат `[](...)`.
4. Проверить, что Status ∈ {active, deprecated, experimental}.
5. Вернуть 0 если всё ок, 1 если есть нарушения.

## Эволюция контракта

- Добавление нового per-feature документа — PR с самим документом и
  обновлением `docs/features/README.md` (оглавление).
- Изменение шаблона (новые секции) — через governance: semver MINOR для
  `constitution.md` (если меняется обязательность секций).
- Deprecation — смена Status, сохранение файла (не удалять, для истории).
