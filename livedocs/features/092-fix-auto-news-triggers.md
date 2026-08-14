---
status: Active
slug: 092-fix-auto-news-triggers
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../features/094-fix-approve-news-failure.md
  - ../features/089-auto-news-song-release.md
  - ../../specs/092-fix-auto-news-triggers/spec.md
---

# 092 — Триггеры авто-новостей независимо от sync + альбом/год (LiveDoc)

> Drill-down — [specs/092-fix-auto-news-triggers/spec.md](../../specs/092-fix-auto-news-triggers/spec.md).

## Что делает

До фикса единственная точка создания авто-новости — `SongReleaseAnnouncementService`
проверялся в момент **синхронизации LOCAL↔SERVER**. Это создавало два разрыва:

1. **Песня должна выйти в эфир в 12:00** — новость появлялась только после
   ручного запуска sync.
2. **Админ approve задания редактора** — новость не появлялась, пока не было
   sync.

**Фикс**:
- Триггер из `Song.saveToDb()` (`save()` метод) — если `idStatus >= 6` и
  readiness-флаги и `news == false` → создание новости.
- Scheduled job на проде — проверка песен в эфире каждые ~5 минут (не
  завязано на sync).
- Текст новости включает **альбом и год**: «Новая песня: ${author} —
  ${songName} (${album}, ${year})».

## User Stories (краткий список)

- **US1** (P1): Песня выходит в эфир в 12:00 → новость появляется без sync.
- **US2** (P1): Approve задания → новость появляется сразу (через save()).
- **US3** (P2): В тексте новости — альбом и год.

## Functional Requirements (указатель)

- **FR-001**: Хук в `Song.saveToDb()` — проверка readiness + создание новости.
- **FR-002**: Scheduled job на проде (см. `089-auto-news-song-release`).
- **FR-003**: Текст новости — `${author} — ${songName} (${album}, ${year})`.
- **FR-004**: Idempotency — `news` флаг (`specs/101-song-news-flag`).

## Acceptance Criteria

- [ ] **AC1**: Песня с `publish_at = 12:00` → новость создаётся ≤ 12:05.
- [ ] **AC2**: Approve → новость появляется ≤ 5 сек (без sync).
- [ ] **AC3**: Текст содержит альбом и год.

## Связанные LiveDocs

- Domain: [publishing.md](../publishing.md), [catalog.md](../catalog.md)
- Feature: [094-fix-approve-news-failure.md](../features/094-fix-approve-news-failure.md), [089-auto-news-song-release.md](../architecture/L2-containers.md) (исходная логика), [101-song-news-flag.md](../features/101-song-news-flag.md) (флаг news)
- Architecture: [L3-components.md](../architecture/L3-components.md) (save flow)

## Код

- Backend: `karaoke-app/.../model/Song.kt` — `saveToDb()` — вызов `createNewsIfReady()`
- Backend: `karaoke-app/.../service/SongReleaseAnnouncementService.kt` — `triggerOnSave()` (новый)
- Backend: `karaoke-app/.../schedulers/SongAirScheduler.kt` (новый)
- SQL: шаблон новости — обновить плейсхолдеры (см. `128-news-publish-templates`)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14