# Implementation Plan: Закрома автора — отрисовка списка песен без N×3 фоновых запросов

**Branch**: `239-zakroma-author-songs-batch-render` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/239-zakroma-author-songs-batch-render/spec.md`

## Summary

Фича устраняет зависание публичного сайта на крупных авторах (~2500 песен) в «Закромах»: убирает per-row фоновые запросы (player readiness, membership, subscriptions), которые валили MinIO/БД. Все нужные данные теперь приходят либо с NDJSON-стримом песен (флаги `id_status`/`is_in_air`/`flag_free`/`player_ready_*`), либо одним bulk-fetch на пользователя (избранное, плейлисты, подписки). После фикса — ≤ 4 HTTP-запроса на страницу автора, нет per-row спиннеров, иконки плеера/избранного/плейлистов показывают финальное состояние сразу.

## Technical Context

**Language/Version**:
- Backend: Kotlin (Spring Boot), JDK 17 (см. AGENTS.md / Constitution § Технологический стек).
- Frontend: Vue 3 + Vite, Node 22 (LTS), Bootstrap 5.
- Изменения касаются `karaoke-web` (backend public API) и `karaoke-public` (frontend).

**Primary Dependencies**:
- Backend: Spring Web (`@RestController`), Spring JDBC (`KaraokeConnection` — сырой JDBC, без JPA/Hibernate), `Subscription` model (`tbl_subscriptions`, scope=SONG), `SitePlaylist` model (`tbl_site_playlists`, `tbl_site_playlist_songs`), `Song` model (Pass 100: персистентные `player_ready_*`).
- Frontend: `usePlayerReadiness.js` (composable), `usePlaylistMembership.js` (composable), `PlayerIcon.vue` / `FavoriteIcon.vue` / `PlaylistIcon.vue` (UI components), `BroadcastChannel('km-favorites')`, NDJSON streaming (`ReadableStream.getReader()`).

**Storage**:
- `tbl_songs` — флаги `is_in_air`, `flag_free`, `player_ready_full`/`player_ready_demo` (Pass 100).
- `tbl_site_playlist_songs` — связь песня↔плейлист (НЕ включая «Избранное»).
- `tbl_subscriptions` — подписки на песни (`scope='SONG'`, `status='PAID'`, `idSong`, `siteUserId`).
- `tbl_site_playlists` — `is_favorites=true` помечает «Избранное».
- Сырой JDBC через `KaraokeConnection`, без JPA/Hibernate (Constitution § II).

**Testing**: автоматических тестов нет (см. AGENTS.md, раздел «Тесты»); ручная проверка по SC-001..SC-007. В CI нет тестов для этих эндпоинтов.

**Target Platform**:
- Backend: Linux, контейт `karaoke-web` (Spring Boot на JRE 17, образ `eclipse-temurin:22-jre-jammy`, см. Constitution).
- Frontend: evergreen browsers + mobile Safari 10+ (для `ReadableStream`); сборка через Vite + Docker (`do.sh build_start_public`).

**Project Type**: web-service (Spring Boot backend + Vue 3 SPA frontend).

**Performance Goals**:
- ≤ 4 HTTP-запросов на `/zakroma?author=...` (стрим + ≤ 3 membership/subscription), проверяется DevTools Network (SC-002).
- TTFP первой страницы песен ≤ 500 мс на типичной сети (SC-025).
- Полный список 2500 песен — ≤ 5 сек (SC-004).
- 60 FPS при прокрутке (SC-007).

**Constraints**:
- Сырой JDBC (Constitution § II — non-negotiable).
- Self-contained (Constitution § I — без внешних SaaS).
- Без JPA/Hibernate/Exposed.
- Без `nginx:alpine`/`node:latest`/JDK-вместо-JRE (Constitution § Категорически запрещено п.7).
- Никаких новых секретов, только существующий `km_auth_token` (Constitution § VIII).

**Scale/Scope**:
- ~2500 песен на крупнейшего автора («Машина Времени»).
- До ~20k песен всего в каталоге (для спеки 181 — constraint).
- ~2500 membership-проверок (одна страница), одна bulk-выборка.
- Изменения в 4 файлах frontend + 1 файл backend + 2 новых endpoint'а.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| **I. Self-contained автопайплайн** | ✅ PASS | Фича использует только локальные Postgres/MinIO/Vue. Никаких внешних SaaS в hot-path. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ PASS | Новые endpoint'ы — `WHERE song_id IN (...)` / `WHERE site_user_id=? AND scope='SONG' AND status='PAID'` через `KaraokeConnection` (сырой JDBC). Никакого JPA/Hibernate. Membership-запрос — batch-lookup (O(1) запросов), не per-row. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ PASS | Не затрагивается: новые endpoint'ы только читают существующие данные (`tbl_subscriptions`, `tbl_site_playlist_songs`, `tbl_songs`). Никаких новых записей/сущностей, не требующих sync. |
| **IV. Async-очередь задач с парсингом stdout** | ✅ PASS | Не затрагивается: фича не связана с рендер-пайплайном (ffmpeg/melt/Sheetsage). |
| **V. Двух-фронтенд: admin/public** | ✅ PASS | Изменения только в `karaoke-public` (публичный SPA) и `karaoke-web` (публичный API). `webvue3` (admin) не затрагивается. |
| **VI. Code Standards (FR-006/007/009)** | ⚠️ NEEDS PLAN | Новые публичные API (новый composable `useSongSubscriptions`, новые endpoint'ы) MUST иметь KDoc/JSDoc. Pre-commit линтеры должны пройти (см. tasks). |
| **VII. Cross-Machine Setup** | ✅ PASS | Не затрагивается. |
| **VIII. Секреты и git-гигиена** | ✅ PASS | Никаких новых секретов. Новые endpoint'ы используют существующий токен из localStorage. `.env`-файлы НЕ модифицируются. |

## Project Structure

### Documentation (this feature)

```text
specs/239-zakroma-author-songs-batch-render/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── api-public-account-favorites-ids.md
│   ├── api-public-account-song-subscriptions-ids.md
│   ├── api-public-account-playlists-membership.md
│   └── ndjson-zakroma-stream.md
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

**Изменения в `karaoke-web/`** (backend public API):

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── PublicPlaylistController.kt   # EXTEND — новые методы: GET /favorites/ids, GET /song-subscriptions/ids
└── dto/
    └── (новые DTO если потребуется — flat List<Long> JSON)
```

**Изменения в `karaoke-public/`** (frontend SPA):

```text
karaoke-public/src/
├── composables/
│   ├── usePlaylistMembership.js    # EXTEND — добавить loadAllIds(), setIdInFavorites() API
│   ├── useSongSubscriptions.js     # NEW — module-level Set<number> + bulk fetch
│   └── usePlayerReadiness.js       # (НЕ модифицируется — остаётся для SongView)
├── components/
│   ├── PlayerIcon.vue              # EXTEND — новые props (premium, inAir, flagFree, hasSubscription), убрать 'loading'
│   ├── FavoriteIcon.vue            # EXTEND — guest-icon для анонима, optimistic update
│   └── PlaylistIcon.vue            # EXTEND — guest-icon для анонима, optimistic update
├── views/
│   ├── ZakromaView.vue             # EXTEND — убрать readiness.load(), добавить вызовы новых composables
│   ├── SearchView.vue              # EXTEND — аналогично ZakromaView
│   └── AuthorPlaylistView.vue      # EXTEND — аналогично
└── services/
    └── playlistApi.js              # EXTEND — fetchFavoritesIds(), fetchSongSubscriptionsIds()
```

**Structure Decision**: Single repo, monorepo-style — `karaoke-web/` (backend) + `karaoke-public/` (frontend). Никаких новых модулей, всё внутри существующих двух. Pre-existing структура `karaoke-app` НЕ затрагивается (это admin-only).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *(нет нарушений)* | — | — |

Constitution Check проходит без нарушений. Никаких сложных tradeoffs.

## Phase 0: Research → [research.md](./research.md)

Все NEEDS CLARIFICATION из спеки закрыты в Session 2026-08-25 (см. Clarifications в spec.md):
- Q1: bulk-fetch для подписок на песни (новый endpoint `/api/public/account/song-subscriptions/ids`).
- Q2: «гостевые» иконки для анонима (серая ★ + редирект на login).
- Q3: «off» фиксируется до logout/login/reload, без retry.

Phase 0 исследует технические unknowns, оставшиеся до планирования:

- **R1**: Какие именно поля `player_ready_*` уже есть в `tbl_songs` (Pass 100) — нужно подтвердить через миграцию и модель Song.
- **R2**: Какой формат ответа у `playlists/membership` сейчас — нужно ли его расширить параметром `scope` или оставить как есть, добавив новый endpoint.
- **R3**: Какой формат NDJSON-сообщения у `zakroma/stream` сейчас (спека 181) — какие поля уже включены, какие нужно добавить.
- **R4**: Существует ли в `tbl_subscriptions` индекс по `(site_user_id, scope, status)` для быстрого lookup'а.
- **R5**: Паттерн module-level singleton (используется в `usePlaylistMembership.js`) — какие best practices в этом проекте, как тестируется cleanup при logout.

## Phase 1: Design & Contracts → [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

Дизайн:

- **data-model.md**: новые module-level store'ы (`SubscriptionSet`, расширение `FavoriteSet`, `PlaylistMembershipMap`); новые SQL-запросы для bulk-выборки; модификация `Song` projection для NDJSON.
- **contracts/**: API контракты для новых endpoint'ов и расширенного NDJSON-формата.
- **quickstart.md**: ручной сценарий проверки (3 типа юзеров × 3 типа песен + 4 регрессионных сценария) с ожидаемыми результатами в DevTools Network / Network tab / UI.

Re-evaluation Constitution Check после Phase 1:
- Принцип II (сырой JDBC): новые SQL — `WHERE id IN (...)` для membership и `WHERE site_user_id=? AND scope='SONG' AND status='PAID'` для подписок. Batch lookups, не per-row. ✅
- Принцип VI (Code Standards): новые публичные API (`PublicPlaylistController.fetchFavoritesIds`, `PublicPlaylistController.fetchSongSubscriptionsIds`, `useSongSubscriptions`) MUST иметь KDoc. Добавляется в tasks.md как P0.
- Принцип VIII (секреты): не затрагивается.