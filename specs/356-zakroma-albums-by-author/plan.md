# Implementation Plan: Закрома — Альбомы авторов

**Branch**: `356-zakroma-albums-by-author` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/356-zakroma-albums-by-author/spec.md`

## Summary

Добавить на публичный сайт (`karaoke-public`) промежуточный этап «Альбомы автора» в навигации `/zakroma` → `/zakroma/{author_id}`. Цепочка становится: `/zakroma` (плашки авторов) → `/zakroma/{author_id}` (страница песен автора) → `/zakroma/{author_id}/albums` (плашки альбомов) → `/zakroma/{author_id}?album={album_id}` (песни выбранного альбома). Альбомы — это записи из существующей таблицы `tbl_albums` (FK к `tbl_authors`). Денормализация счётчиков `total_song_count`/`ready_song_count` в `tbl_albums` через SQL-триггер (паттерн спеки 286 для авторов). UI-настройки (слайдер размера плашек, переключатель плашки/таблица) — в `localStorage`, общие для `/zakroma` и `/zakroma/{author_id}/albums`.

Технически фича — копия паттерна из спек 286 (counts) + спек 307 (zakroma-tiles) на уровень альбомов.

## Technical Context

**Language/Version**: Kotlin 2.x (JDK 17, Gradle), Vue 3 + Vite + Bootstrap 5 (karaoke-public)
**Primary Dependencies**: Spring Boot 3.x (karaoke-web), Vue 3 + Vue Router 4 + Vuex 4 (karaoke-public)
**Storage**: PostgreSQL 14 (raw JDBC через `KaraokeConnection` / `Connection.local()/remote()/virtual()`), MinIO (обложки)
**Testing**: существующие интеграционные тесты `@Disabled` (см. AGENTS.md), ручная проверка через `psql` + UI
**Target Platform**: Linux-серверы (karaoke-web на проде, karaoke-app на admin-машине), браузер публичного сайта
**Project Type**: Web application (backend `karaoke-web` + frontend `karaoke-public`); изменения также касаются `karaoke-app` (миграция + модель `Album`)
**Performance Goals**: `/api/public/authors/{author_id}/albums` ≤ 200 мс p95 (как `/api/public/authors-tiles`); UI рендер ≤ 500 мс
**Constraints**: 
  - Без JPA/Hibernate (Constitution II — сырой JDBC)
  - Без новых прав в админке (A-008)
  - Без MP4/скачивание в рекламе (CLAUDE.md оферта)
  - `redirectErrorStream(true)` для всех `ProcessBuilder` (Constitution IV)
**Scale/Scope**: ~1k авторов × ~10 альбомов = ~10k записей в `tbl_albums`; ~18k песен в `tbl_songs` (для триггера); sync LOCAL↔SERVER через `recordhash`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ PASS | Фича не затрагивает ffmpeg/melt/Demucs. Чисто БД + UI. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Доступ к БД — через `KaraokeConnection`/`Connection.local()/remote()/virtual()`, без JPA. `recordhash` через SQL-триггер (Constitution III). Diff через `associateBy { it.id }` (O(n)). |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ PASS | `Album` уже зарегистрирован в `SyncRegistry.all` (key="albums"). Добавление колонок `total_song_count`/`ready_song_count` потребует пересоздания `update_tbl_albums_recordhash` (аналогично спеке 286 для `tbl_authors`). |
| **IV. Async-очередь задач** | ✅ PASS | Не затрагивается — фича не использует ProcessBuilder. |
| **V. Двух-фронтенд: админка и публичный** | ✅ PASS | Фича только в `karaoke-public`. `webvue3` (админка) НЕ меняется. |
| **VI. Code Standards (KDoc, JSDoc, baseline)** | ✅ PASS | Новые публичные API (DTO, методы) — с KDoc/JSDoc + `@see` на `docs/features/zakroma-albums-by-author.md` (создаётся как per-feature документ, FR-009). Линтеры ktlint/ESLint — обязательны в pre-commit (CLAUDE.md). |
| **VII. Cross-Machine Setup** | ✅ PASS | Не затрагивается — никаких локальных конфигов. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Никаких секретов в коде. Миграция идемпотентна. |
| **IX. Knowledge-first** | ✅ PASS | Knowledge pre-flight выполнен в Stage 1 (см. `spec.md § Knowledge References`); пройдены 4 grep-запроса, прочитаны `domain.md` для 4 доменов + 5 ADR. |

**Все 9 принципов — PASS. Нарушений нет.**

## Project Structure

### Documentation (this feature)

```text
specs/356-zakroma-albums-by-author/
├── plan.md              # Этот файл
├── research.md          # Phase 0: технические решения и обоснования
├── data-model.md        # Phase 1: модель данных (таблицы, DTO, поля)
├── contracts/           # Phase 1: контракты API
│   └── albums-tiles-api.md
├── quickstart.md        # Phase 1: пошаговая валидация
├── checklists/
│   └── requirements.md  # Quality checklist (Knowledge Compliance ✅)
├── spec.md              # Спека (Stage 1+2)
└── tasks.md             # Phase 2: задачи для имплементации (Stage 4)
```

### Source Code (repository root)

```text
# Backend (Kotlin / Spring Boot / Gradle)
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/Album.kt                          # +totalSongCount, +readySongCount поля
└── (SQL-миграция — отдельный файл)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/PublicApiController.kt      # +endpoint /authors/{id}/albums
├── dto/
│   ├── AuthorTilePublicDto.kt              # (без изменений)
│   └── AlbumTilePublicDto.kt               # NEW
└── (кеш albumsTilesCache — в PublicApiController)

deploy/karaoke-db/
├── 49_albums_song_counts.sql               # NEW: +columns + trigger + backfill

# Frontend (Vue 3 / Vite / Bootstrap 5)
karaoke-public/src/
├── views/
│   └── ZakromaView.vue                     # (без изменений — единый view, роуты определяют поведение)
├── router/
│   └── (новые роуты /zakroma/:authorId/albums)
├── components/
│   ├── AuthorTiles.vue                     # (без изменений — переиспользуется как AlbumTiles)
│   └── ZakromaSettings.vue                 # NEW: слайдер размера + переключатель режима
├── composables/
│   └── useZakromaSettings.js               # NEW: реактивный state из localStorage
└── views/ZakromaAlbumsView.vue             # NEW (опционально) — отдельный view или внутри ZakromaView

# Knowledge (Pass 340 SSoT)
docs/features/
└── zakroma-albums-by-author.md             # NEW: per-feature документ (Constitution VI FR-009)

# ADR (Pass 340+)
knowledge/adr/
└── local-0007-album-tile-sort-order.md     # NEW (опционально) — фиксирует выбор year ASC + name ASC
```

**Structure Decision**: Вариант 2 (Web application). Изменения распределены между `karaoke-app` (модель + миграция), `karaoke-web` (endpoint + DTO), `karaoke-public` (роуты + view), `deploy/karaoke-db` (миграция). Админка `webvue3` НЕ меняется (см. A-008).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Все 9 принципов Constitution соблюдены. Нарушений нет.

## Implementation Strategy

### Этап 1: БД-слой (миграция + модель)

1. **SQL-миграция** `deploy/karaoke-db/49_albums_song_counts.sql`:
   - Добавить `total_song_count BIGINT NOT NULL DEFAULT 0` и `ready_song_count BIGINT NOT NULL DEFAULT 0` в `tbl_albums` (`ADD COLUMN IF NOT EXISTS`).
   - Обновить `update_tbl_albums_recordhash()` — включить новые колонки в md5.
   - Backfill существующих альбомов одним `UPDATE tbl_albums a SET ... FROM (SELECT album_id, ...) s WHERE a.id = s.album_id` с `LEFT JOIN + COALESCE`.
   - Создать `trg_tbl_songs_update_album_counts` (AFTER INSERT/UPDATE/DELETE на `tbl_songs` — аналог spec 286 для авторов).
   - Применить на LOCAL и SERVER отдельно.

2. **Модель `Album.kt`**: добавить `@KaraokeDbTableField(name = "total_song_count") var totalSongCount: Long = 0` и `readySongCount: Long = 0`. KDoc — обязателен (Constitution VI FR-006).

### Этап 2: Backend (DTO + endpoint + кеш)

3. **`AlbumTilePublicDto.kt`** в `karaoke-web/dto/`:
   - Поля: `id: Long`, `name: String`, `year: Int`, `pictureUrl: String`, `totalSongCount: Long`, `readySongCount: Long`, `albumType: String`.
   - KDoc + `@see` на `docs/features/zakroma-albums-by-author.md`.
   - Backward compatible (только добавление полей).

4. **`Album.loadAlbumTilesWithCounts(authorId, scope, onlyPublished, includeSkipped)`** в `karaoke-app/model/Album.kt`:
   - Загружает `tbl_albums` с `JOIN tbl_authors` для проверки skip.
   - `ORDER BY year ASC NULLS LAST, name ASC` (см. Clarification Q1).
   - Условия `WHERE`:
     - Гость (`onlyPublished=true`): `ready_song_count > 0 AND skip = false`.
     - Редактор (`onlyPublished=false`): `total_song_count > 0 AND skip = false`.
   - Логика зеркалит `Author.loadAuthorTilesWithCounts` (spec 286).

5. **`PublicApiController.kt`**:
   - Новый endpoint `@GetMapping("/authors/{authorId}/albums")`.
   - Параметры: `scope=main`, `onlyPublished` (определяется ролью пользователя, как в `/authors-tiles`).
   - Кеш `albumsTilesCache` (TTL ≤60с, аналог `authorsTilesCache` в спецификации 248).
   - Invalidation через `consumeDirty()` при sync (как у авторов).

### Этап 3: Frontend (Vue)

6. **Роутер**: добавить `/zakroma/:authorId(\\d+)/albums` (name: `zakroma-author-albums`).

7. **`ZakromaAlbumsView.vue` (или доработка `ZakromaView.vue`)**:
   - Загрузка `/api/public/authors/{authorId}/albums?scope=main`.
   - Заголовок: «Альбомы автора» + ссылка «← К списку авторов» (хлебная крошка).
   - Псевдо-плашка первой через `<slot name="leading" />` в `AuthorTiles.vue` (см. Clarification Q2).
   - Клик на плашку → `/zakroma/{authorId}?album={albumId}`.

8. **`AuthorTiles.vue`** (уже существует) — переиспользуется как для авторов, так и для альбомов (через `<slot name="leading" />`).

9. **`ZakromaSettings.vue`** + `useZakromaSettings.js` composable:
   - Слайдер размера (200..400px, шаг 50), `localStorage["zakroma_tile_size"]`.
   - Переключатель «Плашки / Таблица», `localStorage["zakroma_view_mode"]`.
   - Применяется в обоих разделах (`/zakroma` и `/zakroma/{authorId}/albums`).

10. **Доработка `ZakromaView.vue`** (страница песен автора) — обработка query-параметра `?album=`:
    - Предустановленный фильтр по альбому.
    - Хлебная крошка меняется: «← К альбомам автора» (вместо «← К списку авторов»).

### Этап 4: Knowledge (Pass 340 SSoT)

11. **`docs/features/zakroma-albums-by-author.md`** — per-feature документ (Constitution VI FR-009).

### Этап 5: Тесты и валидация

12. **Trigger test**: SQL-скрипт для ручной проверки триггера (INSERT/UPDATE/DELETE → `total_song_count`/`ready_song_count` обновляются).

13. **Sync test**: LOCAL → SERVER после миграции — `recordhash` совпадает.

14. **UI smoke test**: ручная проверка через браузер (`/zakroma`, `/zakroma/{id}`, `/zakroma/{id}/albums`, `?album=...`).

## Граничные случаи (см. spec.md § Edge Cases)

- Альбом без картинки → placeholder (паттерн из `AuthorTiles.vue`).
- Автор без альбомов → пустое состояние «У этого автора пока нет альбомов».
- Песня с `album_id = NULL` → триггер no-op (как `song_author` без соответствия в спеке 286).
- Skip-альбом (`tbl_albums.skip = true`) → скрыт в публичном API (FR-016), но счётчики поддерживаются.
- `year = 0` или `NULL` → `ORDER BY year ASC NULLS LAST` уносит в конец.

## Альтернативы (отклонённые)

- **Добавить `tbl_albums.sort_order` в SQL-сортировку** (паттерн спек 307): отклонено — пользователь явно выбрал `year ASC + name ASC` (Clarification Q1). Поле `sort_order` уже существует в `tbl_albums` (для админки), но в публичной сетке не используется.
- **Path-based URL `/zakroma/{authorId}/{albumId}`**: отклонено — `?album=` проще для клиентской фильтрации (A-004).
- **localStorage для слайдера и переключателя** vs **БД-персистентность**: выбран localStorage (A-003) — чисто клиентская UI-настройка, нет смысла в БД.
- **`process_bulk_actions` UI** vs **только D-**: пользователь не просил админку; D-вариант минимальный (A-008).