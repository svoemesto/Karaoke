---
status: Active
slug: 152-fix-false-collection-news
related:
  - ../domain/publishing.md
  - ../domain/catalog.md
  - ../architecture/data-sync.md
  - ../../specs/152-fix-false-collection-news/spec.md
---

# 152 — Ложное срабатывание новости «в коллекции» после sync (LiveDoc)

> Drill-down — [specs/152-fix-false-collection-news/spec.md](../../specs/152-fix-false-collection-news/spec.md).

## What it does

На сервере по таймеру выходят новости «В эфире» для песен (правильно). После
этого при синхронизации LOCAL↔SERVER для **тех же** песен **ложно**
срабатывал триггер «Песня появилась в коллекции» (хотя песни в коллекции уже
давно). Это подрывает доверие к авто-новостям и к самой премиум-подписке
(воронка `registration → premium`).

**Корневая причина**: триггер новости «в коллекции» срабатывал на разницу
`is_premium` флага между LOCAL и SERVER, без проверки того, что песня
**реально только что** стала доступной. Sync «досылает» старые значения — это
ложит триггер.

**Фикс**:
- Новость «в коллекции» создаётся **только** при переходе песни из `unavailable` →
  `available` (= появление премиум-контента), НЕ при синхронизации.
- Триггеру новости добавлен **timestamp** `first_seen_in_collection_at`,
  который выставляется при первом появлении, и триггер сверяет —
  если timestamp уже был, новость НЕ создаётся.

## User Stories (краткий список)

- **US1** (P1): Синхронизация НЕ создаёт ложных новостей о «появлении в коллекции».
- **US2** (P1): Новость «В коллекции» только при реальном переходе unavailable → available.

## Functional Requirements (указатель)

- **FR-001**: `first_seen_in_collection_at` колонка в `tbl_settings` (legacy name для Song).
- **FR-002**: При `KaraokeDbTable.save()` — если `first_seen_in_collection_at IS NULL` и песня available → выставляем + создаём новость.
- **FR-003**: Sync через LOCAL↔SERVER НЕ трогает `first_seen_in_collection_at` (значение остаётся у того, кто первым увидел).
- **FR-004**: Sync через push от server → local: если `first_seen_in_collection_at` не NULL — НЕ триггерит новость на local.

## Acceptance Criteria

- [ ] **AC1**: Sync батч (3 песни в коллекции давно) → 0 новостей «в коллекции».
- [ ] **AC2**: Реальный переход (новая песня становится премиум) → 1 новость «в коллекции».
- [ ] **AC3**: После «В эфире» + sync — ложных новостей нет.
- [ ] **AC4**: Тест: проде «10 песен в эфире» → sync → лента без новых записей «в коллекции».

## Related LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news lifecycle), [catalog.md](../domain/catalog.md) (Song is_premium)
- Architecture: [data-sync.md](../architecture/data-sync.md) (SyncRegistry — добавление 8 флагов для `tbl_settings` уже сделано)
- Specs: `089-auto-news-song-release` (базовая логика авто-новостей)

## Code

- Backend: `karaoke-app/.../model/Song.kt` — `first_seen_in_collection_at` (Long? / Instant)
- Backend: `karaoke-app/.../service/NewsService.kt` — обновить триггер новостей
- Backend: `karaoke-app/.../sync/SyncTarget.kt` — 8 флагов + ignore `first_seen_in_collection_at`
- SQL: `deploy/karaoke-db/<NNN>_tbl_settings_first_seen_collection.sql` — миграция

## History

- Created: 2026-08-14
- Last updated: 2026-08-14