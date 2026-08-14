---
status: Active
slug: documentation-conventions
type: topic
related:
  - ../architecture/L3-components.md
---

# Документация — конвенции (KDoc / JSDoc / git)

> Drill-down из `AGENTS.md` Q&A и Constitution.
> Этот LiveDoc — полная версия. В AGENTS.md осталась только короткая ссылка.

## KDoc для Kotlin

### KDoc с backticks ломает парсер — что делать

Заменить `` `multitrack` `` на «multitrack», `` `MltProp` `` на «MltProp» и т.п.
Парсер ktlint видит `*` после `/` как начало нового комментария — `Unclosed comment`
ошибка.

**Допустимые backticks**: одиночные слова вне KDoc, или внутри markdown-ссылок.

### Обязательные KDoc-теги

Каждый публичный класс / функция / интерфейс MUST иметь KDoc с `@see`-ссылкой
на соответствующий per-feature документ (`docs/features/<slug>.md`) И/или LiveDoc
(`livedocs/...`). Это требование Constitution § VI (FR-006).

## JSDoc для Vue/JS

### Vue 3 SFC — coverage

`bash tools/check-jsdoc-coverage.sh webvue3` проверяет, что **100%** экспортов
Vue-компонентов имеют JSDoc-описание. CI падает на новые нарушения.

### Markdown в JSDoc

JSDoc поддерживает Markdown. Используйте fenced code blocks для примеров:
```
/**
 * Computes total price including tax.
 *
 * ```ts
 * const total = computeTotal(100, 0.2);  // 120
 * ```
 */
```

## git-blame-ignore-revs

`git blame` показывает чужих авторов после prettier/baseline коммитов. Решение:
```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

Файл `.git-blame-ignore-revs` содержит хэши **всех** коммитов, которые меняли
сотни файлов без изменения логики (prettier formatting, baseline healing,
авто-KDoc/JSDoc, документация). После настройки `git blame` показывает автора
оригинальной строки, а не автора рефакторинга.

См. Constitution § VII.2.

## per-feature документы

Per-feature документы живут в `docs/features/<slug>.md`. Структура —
см. `tools/check-feature-doc.sh`. Каждый документ MUST содержать секции:

- `## Что делает`
- `## Зачем`
- `## Как работает`
- `## Инварианты / правила`
- `## Известные ловушки`
- `## Ссылки`

При правке кода одной из 9 ключевых подсистем (`docs/features/README.md`)
разработчик MUST в том же PR обновить соответствующий per-feature документ
(Constitution § VI FR-009).

## .gitattributes

`.gitattributes` MUST нормализовать line endings (`* text=auto eol=lf`) и
помечать бинарные файлы (`*.png binary`, `*.jar binary`). Без этого
разработчики на Windows получают `git diff` «всё изменилось» в каждом PR.

См. Constitution § VII.3.

## Генерация документации

- **Kotlin → Dokka**: `./tools/generate-docs.sh` → `docs/api/dokka/`.
- **Vue/JS → typedoc**: `docs/api/typedoc-*/`.

CI/pre-commit MUST падать при `missing description`. См. Constitution § VI FR-006.

## Связанные LiveDocs

- Architecture: [L3-components.md](../architecture/L3-components.md) (где живёт код)

## Код

- Скрипт coverage: `tools/check-kdoc-coverage.sh`, `tools/check-jsdoc-coverage.sh`
- CI: `.github/workflows/lint.yml` (запускает coverage-скрипты)
- `.git-blame-ignore-revs`: репозиторий root

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14