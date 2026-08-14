---
status: Active
slug: 089-auto-news-song-release
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../features/092-fix-auto-news-triggers.md
  - ../architecture/L3-components.md
  - ../../specs/089-auto-news-song-release/spec.md
---

# 089 — Авто-новости о выходе песни в эфир (LiveDoc)

> Drill-down — [specs/089-auto-news-song-release/spec.md](../../specs/089-auto-news-song-release/spec.md).

## Что делает

Автоматические новости:
- Когда песня выходит «в эфир» (наступил `publish_at` + песня готова).
- Когда на сайте появляется песня со статусом 6 (премиум-доступна).

Механизм работает на проде в момент синхронизации таблиц LOCAL↔SERVER.
Создаёт записи в `tbl_news` (одна на песню, идемпотентно через `news` флаг —
см. `101-song-news-flag`).

**Известный side-effect**: эта фича создала 19000+ новостей на проде (по одной
на каждую уже готовую песню в эфире вместо снапшота). См. `090-news-pagination`.

## User Stories (краткий список)

- **US1** (P1): Автоматическое оповещение о новой доступной песне.
- **US2** (P1): Новость о выходе песни в эфир по расписанию.

## Functional Requirements (указатель)

- **FR-001**: Две категории новостей: `air` (в эфире), `premium` (в коллекции).
- **FR-002**: Триггеры из sync + scheduler (см. `092-fix-auto-news-triggers`).

## Acceptance Criteria

- [ ] **AC1**: При достижении `publish_at` → новость создаётся.
- [ ] **AC2**: При `idStatus >= 6` + readiness → новость «в коллекции».
- [ ] **AC3**: Идемпотентность через `news` флаг (нет дубликатов).

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md), [catalog.md](../domain/catalog.md)
- Feature: [092-fix-auto-news-triggers.md](../features/092-fix-auto-news-triggers.md), [101-song-news-flag.md](../features/101-song-news-flag.md)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Backend: `karaoke-app/.../service/SongReleaseAnnouncementService.kt`
- Backend: `karaoke-app/.../schedulers/SongAirScheduler.kt`
- SQL: `tbl_news` schema

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14