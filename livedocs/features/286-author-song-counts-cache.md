---
status: Active
slug: 286-author-song-counts-cache
related:
  - ./248-authors-tiles-cache.md
  - ./270-db-indexes-verification.md
  - ./022-song-status-lifecycle.md
  - ../../specs/286-author-song-counts-cache/spec.md
  - ../../specs/286-author-song-counts-cache/plan.md
  - ../../specs/286-author-song-counts-cache/data-model.md
  - ../../specs/286-author-song-counts-cache/quickstart.md
---

# 286 — Кэш счётчиков песен автора в tbl_authors (LiveDoc)

> Drill-down — [specs/286-author-song-counts-cache/spec.md](../../specs/286-author-song-counts-cache/spec.md).
> Parent — [248-authors-tiles-cache](./248-authors-tiles-cache.md) (L2-кеш `authorsTilesCache`, TTL=30 мин) и [270-db-indexes-verification](./270-db-indexes-verification.md) (индексы на `tbl_songs.song_author` и `id_status`).
> Связано с [022-song-status-lifecycle](./022-song-status-lifecycle.md) — порог `id_status >= 6` для публичной готовности.

## Что делает

Денормализация: хранит количество готовых (`ready_songs_count`) и общее (`total_songs_count`) песен автора прямо в `tbl_authors`. Эндпоинт `/api/public/authors-tiles` читает эти числа одной точечной выборкой вместо GROUP BY по `tbl_songs`.

Счётчики поддерживаются актуальными **DB-триггером `trg_tbl_songs_update_author_counts`** на `tbl_songs` (INSERT/UPDATE/DELETE) — атомарно в той же транзакции, что и изменение песни.

## Effect

* **GROUP BY по `tbl_songs` убран** с горячего пути `/zakroma` — cold-cache `/api/public/authors-tiles` теперь делает 1 SQL вместо 3.
* Латентность cold-cache: с X мс (GROUP BY по ~18k строк) до Y мс (точечная выборка ~100 авторов).
* Запись дороже на O(1) UPDATE (триггер), но это дешевле, чем регулярный GROUP BY.
* L2-кеш `authorsTilesCache` (TTL=30 мин) **сохраняется** — он работает поверх нового источника.

## User Stories (краткий список)

- **US1** (P1): `/api/public/authors-tiles` отдаёт данные без GROUP BY; числа идентичны предыдущей реализации.
- **US2** (P1): изменение статуса песни (готово/не готово) атомарно обновляет счётчики через триггер; cache инвалидируется через `notifyStatsDirty()`.
- **US3** (P1): sync LOCAL → SERVER прокатывает счётчики и `recordhash` на SERVER.
- **US4** (P2): миграция заполняет счётчики для существующих авторов за один проход (backfill).

## Functional Requirements (указатель)

- **FR-001..FR-011**: см. спек 286. Ключевые:
  - **FR-002**: триггер на `tbl_songs` (AFTER INSERT/UPDATE/DELETE).
  - **FR-006**: `Author.loadAuthorTilesWithCounts` — один SQL вместо 3.
  - **FR-007**: cache invalidation через `notifyStatsDirty()` при изменении `id_status`.
  - **FR-009**: `recordhash` для `tbl_authors` пересоздан с новыми колонками.
  - **FR-011**: L2-кеш `authorsTilesCache` сохраняется.

## Реализация

### DB

- `deploy/karaoke-db/44_author_song_counts.sql`:
  - `ALTER TABLE tbl_authors ADD COLUMN ready_songs_count BIGINT NOT NULL DEFAULT 0, total_songs_count BIGINT NOT NULL DEFAULT 0`.
  - `CREATE OR REPLACE FUNCTION update_tbl_authors_recordhash()` — пересоздана с включением новых колонок в md5 (Constitution III).
  - Backfill одним UPDATE с подзапросом `COUNT(*) FROM tbl_songs GROUP BY song_author` + `COUNT(*) FILTER (WHERE id_status >= 6)`.
  - Backfill `recordhash` для существующих строк.
  - `CREATE TRIGGER trg_tbl_songs_update_author_counts` AFTER INSERT/UPDATE/DELETE на `tbl_songs`. Покрывает:
    - INSERT: `+1` total, `+1` ready если `id_status >= 6`.
    - DELETE: `-1` total, `-1` ready если `OLD.id_status >= 6`.
    - UPDATE `id_status`: пересечение границы `>= 6` инкрементирует/декрементирует только `ready_songs_count`.
    - UPDATE `song_author` (перенос песни): декремент у OLD-автора + инкремент у NEW-автора с учётом OLD/NEW `id_status`.
    - Skip-автор: счётчики обновляются нормально (UI скрывает автора отдельно).
    - «Висящая» песня (song_author без `tbl_authors`): UPDATE 0 строк (no-op), без `RAISE EXCEPTION`.

### Backend

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt`:
  - `data class AuthorTileRow(id, author, readySongsCount, totalSongsCount, isSpecialOrder)` — DTO для одной строки тайла.
  - `companion object fun loadAuthorTilesWithCounts(onlyPublished, isSpecialOrder, database)` — один SELECT с условной фильтрацией.
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`:
  - `authorsTiles():258` — 3 SQL заменены на `Author.loadAuthorTilesWithCounts(...)`.
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`:
  - `:3034` `val idStatusBefore = songValue.idStatus` — снимок до save.
  - `:3170` `if (songValue.idStatus != idStatusBefore) notifyStatsDirty()` — cache invalidation при изменении `id_status`.

## Acceptance Criteria

- [ ] **AC1** (SC-001): `/api/public/authors-tiles` НЕ выполняет SQL `group by song_author` — 0 выполнений за 100 запросов подряд.
- [ ] **AC2** (SC-002): числа на плашках авторов идентичны предыдущей реализации (для всех scope'ов).
- [ ] **AC3** (SC-004): INSERT/UPDATE/DELETE в `tbl_songs` атомарно обновляет счётчики в `tbl_authors` (8 тестов триггера из `quickstart.md` шаг 2).
- [ ] **AC4** (SC-005): sync LOCAL → SERVER прокатывает счётчики и `recordhash` на SERVER (sync-флаги `sync_authors_*_update_allowed` остаются `true`).

## Связанные LiveDocs

- [248-authors-tiles-cache.md](./248-authors-tiles-cache.md) — L2-кеш `authorsTilesCache` (TTL=30 мин).
- [270-db-indexes-verification.md](./270-db-indexes-verification.md) — индексы на `tbl_songs.song_author` и `id_status`.
- [022-song-status-lifecycle.md](./022-song-status-lifecycle.md) — жизненный цикл `id_status` (значения 0..6).
- [013-song-status-filter](../../specs/013-song-status-filter/spec.md) — порог `id_status >= 6` для публичной готовности.
- [017-editor-status-bypass](../../specs/017-editor-status-bypass/spec.md) — для редактора `songCount` = `total_songs_count`.

## Код

- DB: `deploy/karaoke-db/44_author_song_counts.sql`.
- Backend:
  - `karaoke-app/.../model/Author.kt` (`AuthorTileRow`, `loadAuthorTilesWithCounts`).
  - `karaoke-web/.../controllers/PublicApiController.kt:258` (`authorsTiles`).
  - `karaoke-app/.../controllers/ApiController.kt:3034,3170` (`idStatusBefore`, `notifyStatsDirty`).
- Frontend: без изменений (`AuthorTilePublicDto` сохранён).

## История

- Создан: 2026-08-31 (Pass 282).
- Последнее обновление: 2026-08-31.