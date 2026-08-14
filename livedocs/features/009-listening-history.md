---
status: Active
slug: 009-listening-history
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../features/171-admin-subscriptions-history.md
  - ../../specs/009-listening-history/spec.md
---

# 009 — История прослушиваний (LiveDoc)

> Drill-down — [specs/009-listening-history/spec.md](../../specs/009-listening-history/spec.md).

## Что делает

Для зарегистрированных пользователей на `karaoke-public` отдельная
страница/раздел **«Что вы слушали»** — список недавно прослушанных песен.

`tbl_listening_history` — таблица с записями: `userId`, `songId`,
`last_played_at`, `play_count`. Триггер при каждом проигрывании песни
(track play) в плеере.

Это пользовательская фича (для самого пользователя) — для админ-списка всех
пользователей см. `171-admin-subscriptions-history`.

## User Stories (краткий список)

- **US1** (P1): Юзер заходит в «Историю» — видит свои последние песни.

## Functional Requirements (указатель)

- **FR-001**: `tbl_listening_history` — FK на `tbl_site_users`, `tbl_settings`.
- **FR-002**: Триггер записи при старте воспроизведения.
- **FR-003**: Skip-фильтр (тегированные `SKIP` песни не появляются).
- **FR-004**: Endpoint `GET /api/public/account/history?limit=20`.

## Acceptance Criteria

- [ ] **AC1**: Юзер слушает 5 песен → в истории 5 последних.
- [ ] **AC2**: Skip-песни не появляются.
- [ ] **AC3**: Endpoint возвращает JSON.

## Связанные LiveDocs

- Domain: [catalog.md](../catalog.md) (Song), [identity.md](../identity.md) (SiteUser)
- Feature: [171-admin-subscriptions-history.md](../features/171-admin-subscriptions-history.md) (admin-вариант)

## Код

- Backend: `karaoke-app/.../model/ListeningHistoryEntry.kt`
- Backend: `karaoke-app/.../service/ListeningHistoryService.kt`
- SQL: `deploy/karaoke-db/<NNN>_tbl_listening_history.sql`
- Frontend: `karaoke-public/src/views/HistoryView.vue`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14