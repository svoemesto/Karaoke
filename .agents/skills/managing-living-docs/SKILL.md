---
name: managing-living-docs
description: Use when updating, creating, or validating the project's Living Documentation (SSoT) in `knowledge/` in response to code or requirement changes.
variant: karaoke
---
---

# Managing Living Documentation (Karaoke-adapted)

> **Karaoke-вариант.** Намеренное отличие от общего навыка living-docs (структура `knowledge/` вместо `docs/`, линтер `tools/lint-knowledge.py`). Панель скиллов показывает его как «проектный вариант», а не как устаревшую копию.

> **Karaoke-adapted** версия. Исходный skill (generic-bootstrap) предполагал
> структуру `docs/` и каталог `scripts/`. В Karaoke SSoT живёт в
> `knowledge/`, а линтеры — в `tools/`. Все остальные отличия — ниже.

## Overview

Living Documentation — Single Source of Truth (SSoT) для проекта.
Любое изменение поведения системы, структуры данных или бизнес-правил
**обязано** отражаться в документации **немедленно** в том же PR.

**Core Principle**: документация описывает **текущее** состояние системы,
не историю изменений.

## Karaoke-overrides (явные отклонения от generic)

Перед использованием этого skill **обязательно** прочитай
[`AGENTS.md`](../../../AGENTS.md) — особенно секции:

- **«Ограничения агента (NON-NEGOTIABLE)»** — запреты/разрешения для AI.
- **«Git — CI-gate для master»** — workflow git-цикла.
- **«Knowledge SSoT CI / pre-commit»** — обязательные проверки перед commit.

Ключевые отличия Karaoke от generic-bootstrap:

| Тема | Generic (docs/) | Karaoke (knowledge/) |
| --- | --- | --- |
| SSoT каталог | `docs/` | `knowledge/` |
| Линтер | `python3 scripts/lint-docs.py` | `python3 tools/lint-knowledge.py` |
| Cross-links | `scripts/lint-docs.py` | `bash tools/check-knowledge-cross-links.sh` |
| Структура | `scripts/lint-docs.py` | `bash tools/check-knowledge-structure.sh` |
| Governance | `CLAUDE.md == AGENTS.md` (симметрия) | **намеренно разные** (см. AGENTS.md § Karaoke-overrides) |
| Git workflow | человек коммитит | **агент делает полный цикл** (см. AGENTS.md) |
| Base branch | `develop`/`staging`/`master` | **только `master`** |

- **Не запускать `bootstrap-living-docs`** — SSoT в Karaoke уже развёрнут
  на `knowledge/`. Навык подключён как общий и нужен только для новых проектов;
  для обновления существующего `knowledge/` — этот skill.

## Когда использовать

Используй этот skill **при каждом изменении**, которое:

- Меняет бизнес-логику (новый AR, новое состояние, новая роль).
- Меняет структуру БД (новое поле, новый constraint, новая таблица).
- Меняет API-контракт (новый endpoint, новое поле в DTO).
- Создаёт новый компонент или домен.
- Делает **рутинный аудит** каталога `knowledge/`.

## The Mutation Workflow (Recipe)

### 1. Impact Surface Analysis

Перед записью **любой** документации определи все уровни knowledge base,
затронутые изменением.

#### Identifying Code Changes (The Isolation Rule)

Чтобы не захватить чужие изменения, изолируй **только свои** изменения
относительно точки ветвления от main.

**Mandatory Protocol:**

1. **Определи Base Branch**: в Karaoke это **всегда `master`**
   (см. AGENTS.md § «Git — CI-gate для master»).
2. **Use Merge-Base Diff**: используй triple-dot синтаксис:
   - `git diff master...HEAD --name-only`
3. **Constraint**: **НЕ используй** `HEAD~N` или double-dot — они могут
   захватить чужие изменения, вмерженные в master.
4. **Exception**: только по явному запросу владельца — broader history.

Получив изолированный список изменённых файлов, проанализируй:

- **L3 (Domain)**: меняет ли это бизнес-правило, статус или термин
  в Ubiquitous Language? → Обнови `knowledge/domains/<domain>/domain.md`.
- **L4 (Component)**: меняет ли это поле БД, интерфейс класса или
  алгоритм? → Обнови `knowledge/domains/<domain>/components/<component>.md`.
- **Public Projection**: меняет ли это внешний API или user-facing guide?
  → Обнови `knowledge/public/`.
- **Guidelines**: вводит ли это новую конвенцию или изменяет style?
  → Обнови `knowledge/guidelines/`.
- **ADR**: фиксирует ли это архитектурное решение? → Создай новый ADR
  в `knowledge/adr/` (через `knowledge/templates/adr.md`).

### 2. The "Current State" Mutation

Обнови файлы используя **State-Based подход**.

- ❌ **BAD (Changelog Style)**: "Added `last_processed_at` field to `job_json` table."
- ✅ **GOOD (State Style)**: просто добавь поле в список в `domain.md`.

**Rule**: документ должен читаться так, как будто он написан **сегодня**
для нового разработчика без знания истории проекта.

### 3. Template Compliance

При создании новых документов:

- Всегда используй соответствующий шаблон из `knowledge/templates/`.
- Не создавай "ad-hoc" specs — следуй установленной структуре.
- **ADR-нумерация**: используй следующий свободный ID
  (`ls knowledge/adr/` — проверь, какие ID заняты).
- **Локальные ADR** имеют префикс `local-` (см. `knowledge/adr/README.md`).

### 4. Final Validation (The Quality Gate)

Задача **не считается "Done"** пока документация не проходит
архитектурную проверку стиля:

```bash
# 1. Структура knowledge/
bash tools/check-knowledge-structure.sh

# 2. Cross-links
bash tools/check-knowledge-cross-links.sh

# 3. Markdown style (эмодзи, mandatory headers, L2 → L1 linking)
python3 tools/lint-knowledge.py
```

Все три скрипта должны вернуть `OK`. **Любой** FAIL — блокирует merge.

**Karaoke-отличие от generic**: в Karaoke **агент делает commit и push**
(см. AGENTS.md § «Git — CI-gate для master»). Используй:

```bash
git add knowledge/<изменённые файлы>
git commit -m "<spec-id>: <краткое описание>"
git push -u origin "${N}-<slug>"  # N из tools/reserve-branch-number.sh
gh pr create --base master
gh pr checks  # все 9 проверок должны быть PASS
gh pr merge --merge  # БЕЗ --delete-branch
```

## Common Failures & Counters

| Baseline Failure | Counter-Measure |
| :--- | --- |
| **Partial Update** | Impact Surface Analysis первый. Проверь L3 → L4 → Public → Guidelines → ADR. |
| **Changelog Style** | Прочитай обновлённую секцию. Описывает *что есть* или *что было*? |
| **Linter Ignored** | `python3 tools/lint-knowledge.py` — обязательный pre-commit шаг. Без исключений. |
| **Forgotten Domain** | Изменил поле БД → спроси: "Это новое бизнес-понятие?" Если да → `domain.md`. |
| **Emoji в markdown** | Линтер запрещает. Используй `[WARN]` вместо эмодзи. |
| **Прямой commit в master** | Создай feature-ветку через `tools/reserve-branch-number.sh` (см. AGENTS.md). |
| **Symmetric CLAUDE.md/AGENTS.md** | В Karaoke **намеренно разные** (Karaoke-overrides p.1 в AGENTS.md). Не «чини» симметрию. |

## Quick Reference: SSoT Checklist

- [ ] Impact Surface identified (L3, L4, Public, Guidelines, ADR).
- [ ] Файлы обновлены в "Current State" стиле.
- [ ] Шаблоны использованы для новых файлов.
- [ ] `bash tools/check-knowledge-structure.sh` PASS.
- [ ] `bash tools/check-knowledge-cross-links.sh` PASS.
- [ ] `python3 tools/lint-knowledge.py` PASS.
- [ ] CI (все 9 проверок в `Knowledge SSoT structure`) PASS.
- [ ] Commit через feature-ветку + PR + CI + merge.
- [ ] **AGENTS.md НЕ изменён** без согласования (governance).

## См. также

- [`AGENTS.md`](../../../AGENTS.md) — runtime-инструкции для AI-агента.
- [`audit-living-docs`](../audit-living-docs/SKILL.md) — глубокий аудит
  консистентности (для больших изменений).
- [`bootstrap-living-docs`](../bootstrap-living-docs/SKILL.md) — **НЕ
  применять в Karaoke** (проект уже бутстрапнут на `knowledge/`).
