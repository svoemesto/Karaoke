---
status: Active
slug: 005-free-vs-premium
related:
  - ../domain/publishing.md
  - ../features/143-song-free-access-window.md
  - ../features/169-share-link-in-premium-compare.md
  - ../../specs/005-free-vs-premium/spec.md
  - ../architecture/monetization.md
  - ../architecture/conversion-funnel.md
---

# 005 — FREE vs PREMIUM table (LiveDoc)

> Drill-down — [specs/005-free-vs-premium/spec.md](../../specs/005-free-vs-premium/spec.md).

## What it does

На `/premium` добавлена **таблица сравнения FREE vs PREMIUM** — список фич,
доступных для free и premium пользователей. Это один из главных
инструментов conversion (free→premium) на воронке.

**Покрытие фич**:
- Онлайн-плеер, доступность эфирных, в Закромах.
- История прослушиваний (см. `009-listening-history`).
- Караоке-разметка, экспорт .srt.
- Временная ссылка (см. `169-share-link-in-premium-compare`).
- И др.

Каждая строка — ✅/❌ для free/premium.

## User Stories (краткий список)

- **US1** (P1): Free-пользователь видит, что получит за деньги.

## Functional Requirements (указатель]

- **FR-001**: `PremiumView.vue` — `COMPARISON_ROWS` массив.
- **FR-002**: Иконки CheckIcon / CrossIcon.

## Acceptance Criteria

- [ ] **AC1**: `/premium` отображает таблицу с фичами.
- [ ] **AC2**: Все 12+ фич (см. `169-share-link-in-premium-compare`).

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (premium)
- Feature: [143-song-free-access-window.md](../features/143-song-free-access-window.md), [169-share-link-in-premium-compare.md](../features/169-share-link-in-premium-compare.md)
- Specs: `docs/strategy/growth.md` (воронка `free→premium`)

## Code

- Frontend: `karaoke-public/src/views/PremiumView.vue` — `COMPARISON_ROWS`
- Frontend: `karaoke-public/src/components/PremiumFeature.vue`

## History

- Created: 2026-08-14
- Last updated: 2026-08-14