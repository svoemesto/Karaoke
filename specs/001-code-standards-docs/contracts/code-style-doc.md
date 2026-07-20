# Contract: CodeStyleRule в `CONTRIBUTING.md`

**Branch**: `001-code-standards-docs` | **Phase**: 1 | **Date**: 2026-07-20

Контракт формата и инвариантов `CONTRIBUTING.md` — единого документа правил
оформления кода проекта Karaoke.

## Файл

`/CONTRIBUTING.md` (в корне репозитория).

## Top-level структура

```markdown
# Contributing to Karaoke

> Краткое описание: зачем этот документ, кто его читает, как обновляется.

## TL;DR

> 3-5 строк: «правила коротко» — ссылки на разделы ниже.

## Kotlin / Spring Boot

> ### [Правило 1: Naming]
> ### [Правило 2: ...]
> ...

## Vue 3 / TypeScript

> ### [Правило N: ...]
> ...

## SQL

> ### [Правило M: ...]
> ...

## Markdown / Documentation

> ### [Правило K: ...]
> ...

## Shell / Bash

> ### [Правило J: ...]
> ...

## Pre-commit и CI

> Какие хуки запускаются, как добавить новый, как обойти (--no-verify).

## Обновление этого документа

> Governance: кто и как может amend, semver для `constitution.md`.
```

## Формат отдельного правила (одна секция `###`)

```markdown
### <Kebab-case-id>: <Краткое название>

**Severity**: MUST | SHOULD | MAY
**Section**: kotlin | vue | sql | json | markdown | shell | gradle | docker
**Enforced by**: ktlint | detekt | eslint | prettier | pre-commit | code-review-only

<Описание правила, 1-3 предложения>

**Правильно** (`path/to/file.ext:NN`):
```kotlin
// пример
```

**Неправильно**:
```kotlin
// антипример
```

**Рациональ**: <почему именно так>

**Связанные инварианты**: [`AGENTS.md#anchor`] [`DEVELOPMENT.md#anchor`] [`constitution.md#anchor`]
```

## Инварианты формата

1. **Уникальность ID**: каждый заголовок `###` имеет уникальный kebab-case-id
   в начале строки. Валидация: `rg '^### [a-z][a-z0-9-]+:' CONTRIBUTING.md`
   должен находить только уникальные ID.

2. **Severity ∈ {MUST, SHOULD, MAY}**: не допускаются «нужно», «желательно»
   в свободной форме.

3. **Enforced by ∈ {ktlint, detekt, eslint, prettier, pre-commit, code-review-only}**:
   если `enforcedBy = code-review-only`, правило НЕ покрывается baseline
   и НЕ считается автоматически проверяемым в SC-002.

4. **Каждый MUST-блок с `enforcedBy ≠ code-review-only` имеет соответствующее
   правило в baseline-файле** (например, для `enforcedBy = ktlint` — запись
   в `config/ktlint/baseline.xml`). Валидация: `tools/check-enforcement.sh`
   парсит CONTRIBUTING.md и проверяет, что все такие правила либо дают 0
   нарушений, либо есть в baseline.

5. **Ссылки на инварианты**: каждое правило в `Связанные инварианты` ссылается
   на реальные anchor'ы в `AGENTS.md` / `DEVELOPMENT.md` / `constitution.md`.
   Валидация: `lychee` на `/CONTRIBUTING.md` (offline mode) + ручная проверка
   при code review.

6. **Примеры кода в fenced code blocks** с явным языком (`kotlin`, `vue`,
   `ts`, `bash`, `sql`).

## Эволюция контракта

- Добавление нового правила: PR в `CONTRIBUTING.md` с теми же инвариантами.
- Удаление правила: PR с пометкой «DEPRECATED» в комментарии и пометкой
  в `constitution.md` если правило оттуда.
- Изменение severity: только через governance (см. constitution.md, semver
  MINOR для новых MUST).
