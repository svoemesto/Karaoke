---
status: Active
slug: 008-special-orders
related:
  - ../domain/catalog.md
  - ../features/012-entity-description-fields.md
  - ../../specs/008-special-orders/spec.md
---

# 008 — «Отдельные песни разных авторов» — виртуальная плашка в Закромах (LiveDoc)

> Drill-down — [specs/008-special-orders/spec.md](../../specs/008-special-orders/spec.md).

## Что делает

В Закромах добавлена **виртуальная плашка** «Отдельные песни разных
авторов» внизу списка авторов (как будто это автор). Внутри — структура
**Автор → Альбом → Песни** (только для песен, у которых `isSpecialOrder=true`).

**Концепция пересмотрена** в ходе обсуждения (см. спеку). Суть — отделить
«спецзаказные» песни от обычной группировки по авторам; удобно для
админа и редактора находить такие песни.

## User Stories (краткий список)

- **US1** (P1): Виртуальная плашка «Отдельные песни» в Закромах.

## Functional Requirements (указатель]

- **FR-001**: Виртуальный Author с id="[SPECIALS]" (или похожее).
- **FR-002**: `Song.isSpecialOrder` флаг (см. также `127-editor-self-assign-tasks.md`).

## Acceptance Criteria

- [ ] **AC1**: В Закромах внизу плашка «Отдельные песни».
- [ ] **AC2**: Внутри — песни с `isSpecialOrder=true`, сгруппированные по авторам.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md)
- Feature: [012-entity-description-fields.md](../features/012-entity-description-fields.md) (UI Закромов — общий)

## Код

- Backend: `karaoke-web/.../services/ZakromaService.kt` — `listSpecialAuthors()`
- Frontend: `karaoke-public/src/views/ZakromaView.vue` — special автор в конце

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14