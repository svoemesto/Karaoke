# How to: добавить новую фичу через SDD /speckit

## Prerequisites

- Прочитать [AGENTS.md «С чего начать сессию»](../../../../AGENTS.md) —
  LiveDocs первым.
- Понимание общей архитектуры (см. [livedocs/INDEX.md](../../INDEX.md)).

## Steps

### 1. Спекa (если фича нетривиальная)

Создать ОДНУ ветку **сразу** — feature-ветку `NNN-slug` от master:

```bash
cd /path/to/Karaoke
N=$(./tools/reserve-branch-number.sh my-feature)
git checkout -b "${N}-my-feature" master
```

**Внутри AI-сессии**:
```
/speckit.specify <описание фичи>
```

Команда автоматически:
- Создаст `specs/<NNN>-my-feature/spec.md` (через шаблон).
- Вставит вопросы Clarifications (если нужны уточнения).
- Обновит [AGENTS.md](../../../../AGENTS.md) Q&A (если появилось новое).

### 2. Спецификация

Создаётся файл `specs/<NNN>-my-feature/spec.md` со структурой:
- User Scenarios & Testing (User Stories с приоритетами P1/P2/P3).
- Functional Requirements (конкретные, тестируемые).
- Success Criteria (измеримые метрики).
- Edge Cases.

**Не выбрасывать** спеку даже после реализации — это **drill-down**, а
LiveDoc — сводка (создастся в шаге 4).

### 3. План

```
/speckit.plan <NNN>
```

Создаёт `plan.md` с Technical Context, Constitution Check, research.md (для
design decisions), data-model.md, contracts/, quickstart.md.

### 4. Задачи

```
/speckit.tasks <NNN>
```

Создаёт `tasks.md` с фазами: Setup → Foundational → US1 → US2 → ...
Также есть [constitution.md](../../../../.specify/memory/constitution.md) —
проверка соответствия.

### 5. Реализация

```
/speckit.implement <NNN>
```

Автоматически:
- Выполняет задачи из `tasks.md` по фазам.
- Создаёт файлы.
- Фиксирует чеклист прогресса в tasks.md.

Создаются файлы в коде + вспомогательные документы.

### 6. LiveDoc-сводка

**В ЭТОЙ ЖЕ ветке** `NNN-my-feature` создать `livedocs/features/<NNN>-my-feature.md` —
сводку ≤ 2 страниц. Шаблон: [`livedocs/templates/feature-summary.md`](../../templates/feature-summary.md).

```bash
cp livedocs/templates/feature-summary.md livedocs/features/<NNN>-my-feature.md
# отредактировать
```

Обязательные поля frontmatter:
- `status: Active`
- `slug: <NNN-my-feature>`
- `related: [...]` — пути к смежным LiveDocs.

### 7. PR + Merge

```bash
git add -A
git commit -m "<NNN>-my-feature: <кратко>"
git push -u origin "${N}-my-feature"
gh pr create --base master

# Дождаться CI 8/8 PASS (включая livedocs-structure)
gh pr merge <PR> --merge  # БЕЗ --delete-branch (ветка живёт)
```

### 8. После merge — обновить LiveDocs

Если в merge было **новое bounded context** или **архитектурный паттерн**,
создать отдельные LiveDoc-документы в следующем follow-up PR (см.
[how-to-add-new-domain.md](how-to-add-new-domain.md)).

## Verification

- CI 8/8 PASS на GitHub Actions.
- В master фича доступна через публичный endpoint или admin-UI.
- LiveDoc-сводка (шаг 6) валидна: `bash tools/check-livedocs-structure.sh` 7/7 PASS.

## Rollback

PR можно откатить через `gh pr rollback <PR>` или revert-merge:
```bash
git revert -m 1 <merge-commit-sha>  # revert merge commit
git push origin master
```

Спека и LiveDoc **остаются в git** для истории.

## Related

- LiveDocs: [AGENTS.md](../../../../AGENTS.md), [livedocs/INDEX.md](../../INDEX.md).
- Templates: [templates/feature-summary.md](../../templates/feature-summary.md),
  [templates/bounded-context.md](../../templates/bounded-context.md).
- Constitution: § Governance «Внесение изменений».