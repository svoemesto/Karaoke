---
name: triage
description: Use when receiving raw, unstructured user requirements or a mix of bugs, features, and policy changes that need classification and integration into the SDD workflow before any planning or coding begins.
---

# Triage

## Overview

Превращает «поток сознания» владельца в структурированный список и не пускает
сырые требования в `speckit-specify` напрямую. Без triage спека может начать
писаться до того, как конфликт с конституцией / уже существующей спекой
обнаружен.

**Корневой принцип**: ни одно требование не уходит в implementation pipeline
без классификации, проверки против конституции и выбранной стратегии
фиксации (Issue / Immediate Action).

## When to Use

Использовать **сразу**, как только владелец выдаёт:

- несколько несвязанных запросов в одном сообщении;
- смесь bug-репортов, feature-идей и конфигурационных правок;
- предложения, меняющие governance или принципы (`constitution.md`);
- сырые идеи, ещё не прошедшие `speckit-specify`.

**Не использовать** для:

- одной, чётко сформулированной задачи уже внутри спек/плана;
- механических правок (опечатки), не меняющих поведение.

## Workflow

### 1. Атомизация и анализ

Разбить сырой ввод на атомарные требования. Для каждого — cross-reference:

- **Конституция** ([`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md)) — не противоречит ли MUST/SHOULD?
- **Knowledge** ([`knowledge/README.md`](../../../knowledge/README.md)) — есть ли домен, который это покрывает? Grep по `knowledge/`, минимум 3 ключевых слова задачи.
- **Спеки** (`specs/NNN-*/spec.md`) — может, уже определено в существующей спеке?
- **OpenProject** (`tools/tracker.sh list-issues --assignee ai-agent`) — есть
  ли открытый issue на эту тему?

### 2. Классификация

Каждому пункту — одна метка:

- `BUG` — наблюдаемое поведение ≠ специфицированное.
- `FEATURE` — новая функциональность, которой нет ни в одной спеке.
- `CHANGE` — модификация существующей специфицированной фичи.
- `CONSTITUTION` — изменение правил проекта / governance / конституции.
- `POLICY` — изменение CI / pre-commit / workflow / лимитов (не конституция,
  но governance-критично).

### 3. Синтез (таблица triage)

Показать владельцу таблицу:

| # | Item | Class | Status/Conflict | Recommendation |
|---|------|-------|-----------------|----------------|
| 1 | [короткое описание] | `BUG`/`FEATURE`/... | ✅ OK / ⚠️ CONFLICT / ℹ️ EXISTS | [следующий шаг] |

**Конфликты**: если пункт противоречит конституции — пометить `⚠️ CONFLICT`.
Не принимать изменение сразу. Показать противоречие и спросить владельца:

1. Сначала обновить конституцию?
2. Отказаться от требования?
3. Обсудить trade-off (создать ADR / grilling-раунд)?

### 4. Стратегия фиксации

После согласования таблицы — спросить стратегию:

- **Option A: Immediate Action** — один высокоприоритетный пункт → прямо в
  `speckit-specify` (или нужный speckit-tool).
- **Option B: Backlog (issues)** — все пункты → отдельные issues в
  OpenProject (`tools/tracker.sh create-issue ...`), брать позже.
- **Option C: Hybrid** — один сейчас, остальные в issues.

## Tool Mapping

| Class | Tool / Action | When |
|-------|---------------|------|
| `FEATURE` / `CHANGE` | `speckit-specify` | когда выбран Immediate Action |
| `CONSTITUTION` | `speckit-constitution` | после согласования конфликта |
| `POLICY` | ADR + ручная правка governance-файла | после согласования |
| `BUG` | OpenProject issue → потом `speckit-specify` | если нужно менять логику; иначе прямой fix |
| Any | `tools/tracker.sh create-issue` | когда выбран Backlog |

## Karaoke-специфика

- **OpenProject — основной backlog** (см. `knowledge/adr/0008-tracker-openproject-migration.md`,
  `tools/tracker.sh`). Labels через custom-fields пока не реализованы —
  префикс `[wayfinder:*]`, `[triage-*]` или просто пометка в subject.
- **Конституция** — единственный источник истины для governance-вопросов
  ([`.specify/memory/constitution.md`](../../../.specify/memory/constitution.md)).
  Любой `CONSTITUTION`-пункт — failure-stop, пока владелец не подтвердит.
- **Knowledge-first MUST #0** ([`AGENTS.md` MUST #0](../../../AGENTS.md))
  обязателен до классификации — без него triage решает «из памяти», а не
  по проекту.

## Common Mistakes

| Red Flag | Reality |
|----------|---------|
| «Я просто планю всё сейчас» | Пропускаешь triage. STOP. Сначала таблица. |
| «Владелец попросил — значит правим конституцию» | Ты архитектор, а не секретарь. Сначала conflict-флажок. |
| «Смешаю bug и feature в одну спеку» | Нарушение SSD. Одна спека — одна фича/изменение. |
| «Сразу починю порт и баг» | Обход процесса = недокументированные изменения. |
| «Triage без чтения конституции и Knowledge» | Решаешь по памяти, а не по проекту. |

**STOP and Start Over if**:

- начинаешь писать `plan.md` до согласования triage-таблицы;
- принимаешь изменение конституции без явного conflict-флага;
- смешиваешь bug и feature в один work-stream.

## Reference

- Источник: [`agents-team-srv/.agents/skills/triage/SKILL.md`](../../../agents-team-srv/.agents/skills/triage/SKILL.md).
- Адаптировано под Karaoke: OpenProject как трекер, наша конституция, наш
  Knowledge-first MUST #0.
