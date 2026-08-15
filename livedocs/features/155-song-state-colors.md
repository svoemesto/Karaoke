---
status: Active
slug: 155-song-state-colors
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../architecture/L3-components.md
  - ../../specs/155-song-state-colors/spec.md
  - ../../archive/docs/features/song-state-colors.md
---

# 155 — Актуализация статусов и цветов песен (LiveDoc)

> Drill-down — [specs/155-song-state-colors/spec.md](../../specs/155-song-state-colors/spec.md).

## Что делает

После перехода на онлайн-плеер и отказа от публикации в соц-сетях —
обновлены **статусы песен** для раскрашивания строк в админке:

**Остаются (с цветами)**:
- **DONE** — готова (`idStatus >= 6`).
- **TODAY** — сегодня в эфире.
- **ON_AIR** — в эфире.
- **EXCLUSIVE** — только по подписке.
- **IN_WORK** — в работе (idStatus < 6).

**Удалены (как устаревшие)**:
- Старые статусы публикации в ВК/Telegram.

## User Stories (краткий список)

- **US1** (P1): Понятная цветовая классификация строк в `/songs`.

## Functional Requirements (указатель)

- **FR-001**: Enum `SongState` в коде: `DONE | TODAY | ON_AIR | EXCLUSIVE | IN_WORK`.
- **FR-002**: Цвета в CSS (см. Acceptance).

## Acceptance Criteria

- [ ] **AC1**: Цвета строк в `/songs`:
  - DONE — зелёный.
  - TODAY — голубой/бирюзовый.
  - ON_AIR — синий.
  - EXCLUSIVE — фиолетовый.
  - IN_WORK — серый.
- [ ] **AC2**: Удалённые статусы (ВК-публикация и т.п.) нигде не отображаются.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song), [publishing.md](../domain/publishing.md) (эфир/EXCLUSIVE)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/.../model/SongState.kt` (новый enum).
- Frontend: `webvue3/src/components/Songs/SongsTable.vue` — `<tr :class="rowClass">`.
- Frontend: `webvue3/src/assets/main.css` — `.row-done`, `.row-today`, и т.п.

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14