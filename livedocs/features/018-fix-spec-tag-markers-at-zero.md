---
status: Active
slug: 018-fix-spec-tag-markers-at-zero
related:
  - ../domain/catalog.md
  - ../domain/editorial.md
  - ../features/017-editor-status-bypass.md
  - ../features/019-fix-setcontent-resets-position.md
  - ../../specs/018-fix-spec-tag-markers-at-zero/spec.md
---

# 018 — Spec-tag маркеры в нулевой позиции (LiveDoc)

> Drill-down — [specs/018-fix-spec-tag-markers-at-zero/spec.md](../../specs/018-fix-spec-tag-markers-at-zero/spec.md).

## Что делает

После `017-editor-status-bypass` (Pass 29) **первый слой** бага был
исправлен — маркеры на правильных позициях. Но оставался **второй слой**:
**красные newline-маркеры в позиции 0** накладывались сверху на правильные
маркеры (толстая красная линия на старте таймлайна).

**Пример**: «Костёр» Машина Времени — правильные syllables/eol/beat-маркеры
распределены по таймкодам, но красная линия в нуле оставалась.

**Фикс**:
- В `syncMarkersFromSpecTags()` фильтровать `time <= 0` (отбрасывать spec-tag
  маркеры с временем ≤ 0).
- Safeguard в SubsEdit: проверка gap после `syncMarkersFromSpecTags()` →
  если gap тонкий, добавление пустого маркера на границе.

## User Stories (краткий список)

- **US1** (P1): Нет красной линии в нулевой позиции.

## Functional Requirements (указатель)

- **FR-001**: Фильтр `time <= 0` в `syncMarkersFromSpecTags()`.
- **FR-002**: Safeguard для тонких gap'ов.

## Acceptance Criteria

- [ ] **AC1**: Открыть SubsEdit для «Костёр» — нет красной линии в нуле.
- [ ] **AC2**: Маркеры распределены правильно.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (lyrics/spec tags), [editorial.md](../domain/editorial.md) (SubsEdit)
- Feature: [017-editor-status-bypass.md](../features/017-editor-status-bypass.md), [019-fix-setcontent-resets-position.md](../features/019-fix-setcontent-resets-position.md)

## Код

- Frontend: `webvue3/src/components/Songs/SubsEdit.vue` — `syncMarkersFromSpecTags()`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14