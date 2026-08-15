---
status: Active
slug: 004-reasons-to-register
related:
  - ../domain/identity.md
  - ../domain/publishing.md
  - ../../specs/004-reasons-to-register/spec.md
---

# 004 — 5 reasons to register (LiveDoc)

> Drill-down — [specs/004-reasons-to-register/spec.md](../../specs/004-reasons-to-register/spec.md).

## What it does

На главной странице публичного сайта блок «5 причин зарегистрироваться» — аргументы для conversion visitor→registered.

**На статус On Hold (2026-07-25)** — реализованы только 2 из 5 причин (для зарегистрированного). Остальные требуют доработки или удалены из scope.

## User Stories (краткий список)

- **US1** (P1): Блок виден анонимам на главной (входная точка).

## Functional Requirements (указаль]

- **FR-001**: `ReasonsBlock.vue` — 5 строк с краткими описаниями.
- **FR-002**: Триггер A/B-теста (опционально).

## Acceptance Criteria

- [ ] **AC1**: Блок виден анонимам на `/`.
- [ ] **AC2**: Минимум 2 из 5 причин реализованы (текущий baseline).

## Related LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser — регистрация), [publishing.md](../domain/publishing.md)

## Code

- Frontend: `karaoke-public/src/components/ReasonsBlock.vue` (новый)
- Frontend: `karaoke-public/src/views/HomeView.vue` — добавить `<ReasonsBlock />`
- Docs: `docs/strategy/growth.md` § 5 Top-3

## History

- Created: 2026-08-14
- Last updated: 2026-08-14