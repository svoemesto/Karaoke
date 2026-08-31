# Research: Кэш счётчиков песен автора в `tbl_authors`

**Feature**: 286-author-song-counts-cache
**Date**: 2026-08-31
**Status**: Phase 0 complete — все NEEDS CLARIFICATION из спеки закрыты в Clarifications, новых не возникло

## Цель

Подтвердить техническую осуществимость фичи, выявить подводные камни, зафиксировать best-practices проекта для последующей имплементации.

---

## R1. Где живут `tbl_songs` и `tbl_authors`?

**Decision**: `tbl_songs` живёт ТОЛЬКО на LOCAL-БД. `tbl_authors` живёт на обеих БД и участвует в sync через `AuthorsSyncTarget` (зарегистрирован в `SyncRegistry.all`, см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt:281`).

**Rationale**:
- `SongSyncTarget` НЕ зарегистрирован в `SyncRegistry.all` (см. `SyncTarget.kt:221` — это bespoke sync, не в общем registry).
- `recordhash_authors.sql` существует — значит, sync для `tbl_authors` настроен.
- `recordhash_songs.sql` НЕ существует — подтверждает, что песни не синхронизируются row-by-row.

**Implication**:
- DB-триггер `trg_tbl_songs_update_author_counts` нужен **только на LOCAL-БД** — на SERVER нет песен для триггера.
- Миграция колонок `ready_songs_count`, `total_songs_count` в `tbl_authors` + backfill + пересоздание `recordhash`-триггера нужны на **ОБЕИХ БД**.
- Счётчики на SERVER обновляются **через sync**: на LOCAL триггер обновляет `tbl_authors`, sync пушит изменённые строки на SERVER.

**Альтернативы рассмотрены**:
- Триггер на обеих БД — отклонён (бессмысленно: на SERVER нет INSERT/UPDATE/DELETE в `tbl_songs`).
- Обновлять счётчики в коде (`Song.save`) — отклонён (A1 в спеке: риск пропустить операции в обход `Song.save`).

---

## R2. Как применяются SQL-миграции?

**Decision**: Положить миграцию в `deploy/karaoke-db/44_author_song_counts.sql` (нумерация по порядку после `43_song_name_censored_sync.sql`).

**Rationale**:
- `deploy/karaoke-db/` содержит 43 миграции с паттерном `NN_short_name.sql`.
- Миграции `idempotent` — используют `ADD COLUMN IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION`, `UPDATE ...` с проверками (см. `27_author_special_order.sql`).
- Миграции применяются один раз на LOCAL и на SERVER отдельно (см. шапку `32_song_status_lifecycle.sql:11`).

**Паттерн миграции** (по образцу `27_author_special_order.sql`):
1. `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (для каждой новой колонки отдельно).
2. `CREATE OR REPLACE FUNCTION update_tbl_authors_recordhash()` — пересоздание recordhash-функции с включением новых колонок.
3. `CREATE OR REPLACE TRIGGER update_recordhash_authors_trigger` — обычно триггер остаётся, пересоздаётся функция.
4. Backfill: `UPDATE tbl_authors SET ready_songs_count = ..., total_songs_count = ...` — один UPDATE с подзапросами `COUNT(*) FROM tbl_songs`.
5. Backfill recordhash: `UPDATE tbl_authors SET recordhash = md5(...)` (триггер срабатывает только на новые INSERT/UPDATE).
6. `CREATE OR REPLACE FUNCTION trg_tbl_songs_update_author_counts()` + `CREATE TRIGGER` — для триггера на `tbl_songs` (только на LOCAL).

**Альтернативы рассмотрены**:
- Класть файл в `deploy/recordhash_authors.sql` (как корневой recordhash-файл) — отклонён, recordhash-файлы не для новых фич, они для исправления схемы триггера в одном месте. Для новых колонок — миграция с backfill.
- Дробить на два файла (миграция + recordhash) — отклонён, противоречит существующему паттерну `27_author_special_order.sql` (всё в одном файле).

---

## R3. Как сбрасывается кэш `authorsTilesCache` на проде?

**Decision**: Использовать существующий механизм `StatBySong.consumeDirty()` через HTTP `POST /api/internal/stats/mark-dirty` от karaoke-app (см. `karaoke-web/.../InternalStatsController.kt:44`, `karaoke-app/.../ApiController.kt:2894` `notifyStatsDirty`).

**Rationale**:
- В `PublicApiController.getCachedAuthorsTiles()` (строка 137) уже есть вызов `StatBySong.consumeDirty()` — кэш сбрасывается, если на SERVER-БД взведён флаг.
- `notifyStatsDirty()` уже вызывается в karaoke-app:
  - при изменении `free` флага песни (`ApiController.kt:3166`);
  - при sync oneclick с пушем песен (`ApiController.kt:5329` через `notifyStatsDirtyIfSongsPushed`).

**Найденный gap**: **`notifyStatsDirty` НЕ вызывается при изменении `id_status` песни (готово/не готово)**. Это означает, что FR-007 (инвалидация кэша при изменении статуса) **не покрыт существующим кодом**.

**Implication для имплементации**:
- Добавить `notifyStatsDirty()` при изменении `id_status` в эндпоинте обновления песни (`ApiController.songUpdate` или эквивалент).
- Альтернатива: добавить вызов в `Song.saveToDb()` — но `saveToDb` вызывается часто, и не все изменения `id_status` требуют сброса кэша (например, тестовые прогоны). Лучше точечно в эндпоинте.

**Альтернативы рассмотрены**:
- WebSocket/SSE — отклонён (overhead инфраструктуры ради одной инвалидации).
- TTL 1 мин — отклонён (60-кратная нагрузка на БД).
- Кэш на LOCAL — отклонён (не решает проблему на проде).

---

## R4. Какие SQL-функции/паттерны использовать в `Author.kt` для чтения счётчиков?

**Decision**: Добавить новый companion-метод `Author.loadAuthorTilesWithCounts(scope, onlyPublished, database)` — один SQL-запрос с условной фильтрацией.

**Rationale**:
- Текущая логика `PublicApiController.authorsTiles()` (строки 277-289) делает 3 запроса: `loadAuthorSongCounts` + `loadListAuthors` + `loadIdsByNames`. После фичи — 1 запрос.
- Прямой `SELECT` из `tbl_authors` с `WHERE skip = false AND (... ready_songs_count > 0 OR total_songs_count > 0)` покрывает все scope'ы одним запросом.
- Паттерн уже использован в `Author.loadIdsByNames(names, database)` (см. `Author.kt:302`) — `WHERE name IN (?, ?, ...)` для батча.

**Что возвращает метод**:
```kotlin
data class AuthorTileRow(
    val id: Long,
    val author: String,
    val readySongsCount: Long,
    val totalSongsCount: Long,
    val isSpecialOrder: Boolean,
)

fun loadAuthorTilesWithCounts(
    onlyPublished: Boolean,
    isSpecialOrder: Boolean?,
    database: KaraokeConnection,
): List<AuthorTileRow>
```

**Implication**:
- `Author.kt` дополняется двумя новыми декларациями (data class + функция).
- `@KaraokeDbTableField` для `ready_songs_count` / `total_songs_count` НЕ нужны (эти поля только читаются напрямую через SQL, не через reflection ORM).
- `Song.loadAuthorSongCounts` остаётся в коде — может использоваться в других местах (см. вызовы в `Zakroma.kt`, `MainController.kt`); удалять нельзя.

---

## R5. Как изменится `PublicApiController.authorsTiles()`?

**Decision**: Заменить 3 запроса (`loadAuthorSongCounts` + `loadListAuthors` + `loadIdsByNames`) на 1 запрос через новый `Author.loadAuthorTilesWithCounts`. Кэш `getCachedAuthorsTiles()` остаётся без изменений (L2-кеш продолжает работать).

**Before** (3 запроса на cache miss):
```kotlin
val counts = Song.loadAuthorSongCounts(...)
val loadedAuthors = Song.loadListAuthors(...)
val authorIdsByName = Author.loadIdsByNames(loadedAuthors, ...)
// + дополнительный цикл + mapNotNull
```

**After** (1 запрос на cache miss):
```kotlin
val rows = Author.loadAuthorTilesWithCounts(onlyPublished, isSpecialOrderFilter, WORKING_DATABASE)
rows.map { row ->
    AuthorTilePublicDto.fromAuthorName(
        id = row.id,
        author = row.author,
        songCount = if (onlyPublished) row.readySongsCount else row.totalSongsCount,
        isSpecialOrder = row.isSpecialOrder,
    )
}
```

**Implication**:
- Поле `songCount` в DTO остаётся прежним: для анонимов — готовое, для редактора — общее.
- `songCount = 0` авторы фильтруются в SQL (`ready_songs_count > 0` для onlyPublished=true, `total_songs_count > 0` для редактора) — никаких дополнительных `.filter` в Kotlin.

---

## R6. Per-feature документ (Constitution VI FR-009)

**Decision**: Создать `docs/features/author-song-counts-cache.md` с KDoc на новый публичный метод (`Author.loadAuthorTilesWithCounts`).

**Rationale**:
- Constitution Principle VI FR-006: публичные API должны иметь KDoc с `@see` на per-feature документ.
- Constitution Principle VI FR-009: при правке кода одной из 9 ключевых подсистем — обновить per-feature документ в том же PR.
- `Author` — модель данных (одна из ключевых подсистем).

**Содержимое документа**:
- Описание фичи и её границ.
- Ссылка на спеку `specs/286-author-song-counts-cache/spec.md`.
- Ссылка на миграцию `deploy/karaoke-db/44_author_song_counts.sql`.
- Описание триггера `trg_tbl_songs_update_author_counts` (какие операции покрывает).
- Изменения в `PublicApiController.authorsTiles()`.
- Изменения в `ApiController` (`notifyStatsDirty` для `id_status`).

---

## R7. Тестирование (CI/tests)

**Decision**: Ручное тестирование пользователем + проверочные SQL-запросы, описанные в `quickstart.md`. Существующие тесты в `karaoke-app/src/test/` помечены `@Disabled` — не полагаемся на них.

**Rationale**:
- Constitution: «в CI тестов нет; существующие (`karaoke-app/src/test`) — `@Disabled`. Проверка — пользователем».
- Проверка фичи — серия INSERT/UPDATE/DELETE в `tbl_songs` и наблюдение за `tbl_authors.ready_songs_count` / `total_songs_count`.
- Sync проверяется через `bash deploy/do.sh sync` или эквивалентный sync-команд + сравнение значений на SERVER-БД.

**Альтернативы рассмотрены**:
- Интеграционные тесты через Testcontainers — отклонён, проект не использует Testcontainers.
- Юнит-тесты на `Author.loadAuthorTilesWithCounts` — можно, но проект игнорирует unit-тесты; оставлено за рамками.

---

## R8. Учёт машинно-специфичных исключений (`nsa-i9`/`nsa`)

**Decision**: На этой машине разрешено пересобирать `karaoke-app` (см. AGENTS.md), но НЕ перезапускать контейнер `karaoke-app` — только по прямому согласию пользователя. Миграция применяется на LOCAL-БД через psql или эквивалент (не требует перезапуска контейнера).

**Rationale**:
- Применение SQL-миграции — это просто `psql -f deploy/karaoke-db/44_author_song_counts.sql karaoke_local`. Никаких перезапусков.
- Тестирование триггера на реальных INSERT/UPDATE/DELETE — через psql, без перезапуска контейнера.
- Сборка `karaoke-app:bootJar` — разрешена на `nsa-i9`/`nsa` без явного согласия.

---

## Summary of Findings

| # | Решение | Источник в спеке |
|---|---------|-----------------|
| R1 | Триггер только на LOCAL, миграция — на обеих БД | FR-002 + research |
| R2 | Миграция в `deploy/karaoke-db/44_author_song_counts.sql` | A8 + pattern 27 |
| R3 | Reuse `consumeDirty()` + добавить `notifyStatsDirty()` для `id_status` | FR-007 + gap |
| R4 | Новый `Author.loadAuthorTilesWithCounts()` | FR-006 |
| R5 | Один SQL-запрос вместо трёх в `PublicApiController.authorsTiles()` | FR-006 + R4 |
| R6 | Создать `docs/features/author-song-counts-cache.md` | Constitution VI FR-009 |
| R7 | Ручное тестирование + проверочные SQL | Constitution + research |
| R8 | Миграция + триггер через psql без перезапуска | AGENTS.md машина |

**Все NEEDS CLARIFICATION закрыты в Clarifications спеки. Новых не возникло. Готов к Phase 1.**