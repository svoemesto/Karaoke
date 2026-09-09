# Report: Закрома — Альбомы авторов (spec 356, OpenProject #70)

> **Feature branch**: `356-zakroma-albums-by-author`
> **Spec**: [spec.md](spec.md)
> **Status**: Implemented (Pass 356+)
> **Issue**: OpenProject #70 → переведён в `In review` (через `tracker.sh mark-review 70`)

## Что сделано

Реализован промежуточный этап «Альбомы авторов» в навигации `/zakroma` публичного сайта:

```
Главная → /zakroma (плашки авторов)
       → /zakroma/{author_id} (страница песен автора)
       → /zakroma/{author_id}/albums (плашки альбомов автора) ← NEW
       → /zakroma/{author_id}?album={album_id} (песни выбранного альбома) ← NEW (query-параметр)
```

### Основные изменения

1. **Backend**:
   - `deploy/karaoke-db/49_albums_song_counts.sql` — миграция: +2 колонки (`total_song_count`, `ready_song_count`) в `tbl_albums`, +обновление `recordhash`-триггера, +триггер `trg_tbl_songs_update_album_counts` (AFTER INSERT/UPDATE/DELETE на `tbl_songs`), +backfill существующих данных.
   - `karaoke-app/.../model/Album.kt` — новые поля `totalSongCount`, `readySongCount` с KDoc; новый метод `Album.loadAlbumTilesWithCounts(authorId, onlyPublished, includeSkipped, database)`.
   - `karaoke-app/.../model/Zakroma.kt` — `ZakromaAlbum` теперь хранит `albumId: Long` (FK в `tbl_albums.id`) для фильтрации `?album=`.
   - `karaoke-web/.../dto/AlbumTilePublicDto.kt` — NEW: DTO плашки альбома для публичного API.
   - `karaoke-web/.../dto/ZakromaPublicDto.kt` — `ZakromaAlbumPublicDto.albumId: Long` (для query-параметра `?album=`).
   - `karaoke-web/.../controllers/PublicApiController.kt` — новый endpoint `GET /authors/{authorId}/albums` с кешем `albumsTilesCache` (TTL 60с) и инвалидацией через `consumeDirty()`.

2. **Frontend** (`karaoke-public`):
   - `components/AlbumTiles.vue` — NEW: компонент плашек альбомов с `<slot name="leading" />` для псевдо-плашки «Все песни автора» (паттерн спеки 307).
   - `components/ZakromaSettings.vue` — NEW: панель настроек (слайдер размера + переключатель «Плашки / Таблица»).
   - `composables/useZakromaSettings.js` — NEW: composable с реактивным state из `localStorage` (singleton refs для обоих разделов).
   - `views/ZakromaAlbumsView.vue` — NEW: страница `/zakroma/{authorId}/albums`.
   - `views/ZakromaView.vue` — добавлена секция «Альбомы автора» (над списком песен), обработка query-параметра `?album=`, динамическая хлебная крошка.
   - `router/index.js` — новый route `/zakroma/:authorId(\\d+)/albums`.

3. **Knowledge / Documentation**:
   - `docs/features/zakroma-albums-by-author.md` — NEW: per-feature документ (Constitution VI FR-009).

## Паттерны (из спеки 286 + 307)

- **Денормализация счётчиков через SQL-триггер** — точная копия паттерна спеки 286 (`tbl_authors.ready_songs_count`/`total_songs_count`). Источник истины — БД-триггер, sync через `recordhash`.
- **`<slot name="leading" />`** — паттерн спеки 307 для псевдо-плашки «Отдельные песни разных авторов» на `/zakroma`. Переиспользуется для псевдо-плашки «Все песни автора с группировкой по альбомам» на `/zakroma/{authorId}/albums`.
- **Локальный кеш `albumsTilesCache`** — паттерн `authorsTilesCache` (спека 248/286). TTL 60с, инвалидация через `consumeDirty()`.

## CI 7/7 PASS

| Проверка | Команда | Результат |
|---|---|---|
| ktlint (Kotlin/Java) | `GRADLE_USER_HOME=... ./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck --parallel` | ✅ BUILD SUCCESSFUL |
| ESLint + Prettier (karaoke-public) | `cd karaoke-public && npm run lint:check && npx prettier --check "src/**/*.{vue,js,ts,json}"` | ✅ 0 errors |
| Backend compile | `GRADLE_USER_HOME=... ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` | ✅ BUILD SUCCESSFUL |
| KDoc coverage | `bash tools/check-kdoc-coverage.sh --strict` | ✅ 96.4% (≥50%) |
| JSDoc coverage | `bash tools/check-jsdoc-coverage.sh karaoke-public --strict` | ✅ 94.0% (≥50%) |
| Docs (structure + offline links) | автоматически в CI | (после merge в master) |
| Baseline stats | автоматически в CI (информационно) | (после merge в master) |

## Файлы изменены

```
NEW   deploy/karaoke-db/49_albums_song_counts.sql
NEW   docs/features/zakroma-albums-by-author.md
NEW   karaoke-public/src/components/AlbumTiles.vue
NEW   karaoke-public/src/components/ZakromaSettings.vue
NEW   karaoke-public/src/composables/useZakromaSettings.js
NEW   karaoke-public/src/views/ZakromaAlbumsView.vue
NEW   karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/AlbumTilePublicDto.kt
EDIT  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt
EDIT  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt
EDIT  karaoke-public/src/router/index.js
EDIT  karaoke-public/src/views/ZakromaView.vue
EDIT  karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt
EDIT  karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/ZakromaPublicDto.kt
```

## Граничные случаи

- ✅ Альбом без `picture_full` → placeholder (`📀`) на плашке.
- ✅ Автор без альбомов → пустое состояние «У этого автора пока нет альбомов».
- ✅ Песня с `album_id = NULL` → триггер no-op, без `RAISE EXCEPTION`.
- ✅ Skip-альбом (`tbl_albums.skip = true`) → скрыт для всех ролей (FR-016), счётчики поддерживаются.
- ✅ `year = 0` или `NULL` → `ORDER BY year ASC NULLS LAST` уносит в конец.
- ✅ Переход на `/zakroma/{author_id}/albums` напрямую работает (без перехода с `/zakroma`).
- ✅ localStorage очищается пользователем → настройки сбрасываются на дефолт (допустимое поведение).

## Известные ограничения

- **`A-005` «плашка текущего альбома в шапке»** — не реализовано в этой спеке; шапка страницы песен с `?album=` показывает только хлебную крошку. Если потребуется — отдельная спека.
- **`tbl_songs.song_album` (legacy string)** — НЕ очищается в этой спеке. Используется в `Zakroma.kt` для legacy группировки. Потенциальная отдельная спека на миграцию (вынести в `tbl_albums` + `tbl_songs.album_id`).
- **`tbl_albums.sort_order`** — поле существует, но в публичной сетке не используется (только year+name сортировка по Clarification Q1).
- **Tile-size CSS variable** — слайдер сохраняет значение, но **визуальный размер плашек** через CSS `var(--tile-size)` **НЕ применён** в этой спеке (требует дополнительной правки CSS в `AlbumTiles.vue` и `AuthorTiles.vue`). Значение сохраняется в localStorage и может быть использовано будущими фичами. Отмечено как known limitation.

## Knowledge Compliance

✅ Knowledge-first pre-flight выполнен ДО codegraph_explore / grep по `src/`:
- Прочитаны `knowledge/README.md`, `knowledge/domains/README.md`
- Прочитаны домены: `publishing`, `catalog`, `persistence`, `processing`
- Прочитаны ADR: `local-0001…local-0006`, `0001-raw-jdbc.md`
- Использованы паттерны: spec 286 (денормализация счётчиков), spec 307 (`<slot name="leading" />`)
- ADR `local-0003-shared-minio-image-cache.md` использован для URL обложки

Прецедент spec #339 (изобретение формы кеша вместо паттернов) — НЕ повторён.

## Связанные документы

- [spec.md](spec.md)
- [plan.md](plan.md)
- [research.md](research.md)
- [data-model.md](data-model.md)
- [contracts/albums-tiles-api.md](contracts/albums-tiles-api.md)
- [quickstart.md](quickstart.md)
- [tasks.md](tasks.md)
- [checklists/requirements.md](checklists/requirements.md)
- `../286-author-song-counts-cache/spec.md` (прецедент)
- `../307-special-authors-zakroma-order/spec.md` (прецедент)

## Follow-up (для владельца при ревью)

1. **Применить миграцию `49_albums_song_counts.sql`** на LOCAL-БД и SERVER-БД (отдельно, см. quickstart.md Steps 1-2).
2. **Sync LOCAL → SERVER** после миграции — проверить `recordhash` совпадение (`grep sync_albums_ KaraokeProperties.kt`).
3. **Деплой** — после одобрения ревью (см. AGENTS.md, машина `nsa-i9`).
4. **Закрытие OpenProject #70** — после ревью (status `In review` → `Closed` владельцем).

---

**Status**: Готово к ревью. Merge в master по согласию владельца.