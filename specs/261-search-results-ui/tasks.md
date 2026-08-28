---
description: "Task list for 261-search-results-ui — backend DTO expansion + SearchView.vue row redesign"
---

# Tasks: 261 — Исправление иконки плеера и редизайн строк результатов поиска

**Input**: Design documents from `/specs/261-search-results-ui/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/api-songs.md ✅, quickstart.md ✅

**Tests**: опциональны по AGENTS.md (раздел «Тесты»). В CI нет. Существующие тесты в `karaoke-app/src/test/` интеграционные, `@Disabled`. Валидация делается пользователем вручную по [quickstart.md](quickstart.md). Test-задачи НЕ генерируются — они заменены manual-scenario задачами в фазах User Story.

**Organization**: 3 User Stories из spec.md (US1 P1, US2 P1, US3 P2). Phase 2 содержит backend-DTO-расширение (общее для US1 и US2), Phase 3/4/5 — фронтенд по story, Final Phase — литеры/сборка/LiveDocs (AGENTS.md mandatory sequence).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно параллельно (разные файлы, без зависимостей).
- **[Story]**: какая user-story (US1, US2, US3) — обязательно для фаз User Story.
- В описании — точные file paths.

## Path Conventions

- Backend (Kotlin): `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/...`
- Frontend (Vue): `karaoke-public/src/...`
- Branch: `261-search-results-ui`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: verify that feature branch is ready, worktree clean, никаких секретов на старте.

- [x] T001 Verify that branch is `261-search-results-ui` and specs artifacts exist (`specs/261-search-results-ui/{plan.md, spec.md, research.md, data-model.md, contracts/api-songs.md, quickstart.md, checklists/requirements.md}`)
- [x] T002 Run pre-flight secret check: `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — MUST be empty (Constitution VIII.3)
- [x] T003 [P] Read `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` (current state) to confirm exact field insertion points and existing KDoc style
- [x] T004 [P] Read `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` `songs()` method (lines ~649-718) to confirm batch-lookup insertion point (parallel to existing `aliasByAuthor` lookup)
- [x] T005 [P] Read `karaoke-public/src/views/PlaylistEditView.vue` (template lines 95-189, style lines 801-995) — reference implementation for the row layout to be ported

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: расширить `SongPublicDto` так, чтобы `contentReady`/`albumPictureUrl`/`authorPictureUrl` попадали в JSON ответа `/api/public/songs`. Без этого Phase 3 (US1: иконка плеера) и Phase 4 (US2: превью в строке) не работают.

**⚠️ CRITICAL**: никакая User Story не может начаться до завершения Phase 2.

- [x] T006 Add `contentReady: Boolean = false` field to `data class SongPublicDto` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` (insert after existing `idStatus: Long` field, before `assignment: SongAssignmentBriefDto? = null`); include KDoc `@see specs/239-zakroma-author-songs-batch-render` referencing the parallel field on `ZakromaAlbumSongPublicDto` (нет `is`-префикса — инвариант Jackson проекта)
- [x] T007 [P] Add `albumPictureUrl: String = ""` field to `data class SongPublicDto` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` (insert after new `contentReady`); include KDoc explaining URL pattern mirrors `ZakromaAlbumMetaPublicDto.fromAlbum:42-46`
- [x] T008 [P] Add `authorPictureUrl: String = ""` field to `data class SongPublicDto` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` (insert after new `albumPictureUrl`); include KDoc explaining URL pattern mirrors `ZakromaPublicDto.fromZakroma:99-104`
- [x] T009 Modify `SongPublicDto.fromSong(s: Song, includeDetails: Boolean = true)` signature in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` to accept two new String parameters `albumPictureUrl: String`, `authorPictureUrl: String` (default values: `""`); populate the new fields from them (D1 in research.md)
- [x] T010 Inside `SongPublicDto.fromSong`, populate `contentReady = s.isContentReady` (single line, рядом с `idStatus = s.idStatus`); `Song.isContentReady` — существующий getter (FR-002, см. `PublicPlayerController.stemsReady:139`)
- [x] T011 [P] Verify in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` что все 3 новых поля попадают в `companion object fromSong` без дополнительного кода (Jackson авто-сериализует data class-поля)
- [x] T012 Inside `PublicApiController.songs(...)` in `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`, after `val song: List<Song> = ...` (around line 695), add a batch-lookup block: собирает `albumIds: List<Long>` из `song.mapNotNull { it.albumId }.distinct()` и `authorNames: List<String>` из `song.map { it.author }.distinct()`; затем `val albumsById = if (albumIds.isNotEmpty()) Album.getAlbumsByIds(albumIds, WORKING_DATABASE, storageService, storageApiClient) else emptyMap()` (helper уже существует, `Album.kt:294-309`); затем `val authorsByName = if (authorNames.isNotEmpty()) Author.loadByNames(authorNames, WORKING_DATABASE, storageService, storageApiClient) else emptyMap()` (если helper `Author.loadByNames` не существует — добавить по образцу `Author.loadIdsByNames`)
- [x] T013 Build URL-helper inline в `PublicApiController.songs(...)`: `fun albumUrl(albumId: Long?): String = albumId?.let { albumsById[it]?.picturePreviewFileName?.takeIf { s -> s.isNotEmpty() }?.let { f -> "/api/public/picture?file=${URLEncoder.encode(f, StandardCharsets.UTF_8)}" } ?: "" }` (mirror `ZakromaAlbumMetaPublicDto.fromAlbum:41-46`); similarly `authorUrl(name: String): String = authorsByName[name]?.picturePreviewFileName?.takeIf { it.isNotEmpty() }?.let { f -> "/api/public/picture?file=${URLEncoder.encode(f, StandardCharsets.UTF_8)}" } ?: ""`
- [x] T014 Modify the final `return song.map { ... }` block в `PublicApiController.songs(...)`: каждый вызов `SongPublicDto.fromSong(it, includeDetails = false)` обогащается двумя trailing-args: `albumPictureUrl = albumUrl(it.albumId), authorPictureUrl = authorUrl(it.author)`; конструкция `dto.copy(authorAlias = ...)` в конце map остаётся без изменений
- [x] T015 [P] Run backend compile (mandatory per AGENTS.md): `./gradlew :karaoke-web:compileKotlin --parallel` — MUST exit 0; проверяет, что новые сигнатуры `fromSong` совпадают во всех call-site'ах

**Checkpoint**: Foundation ready — JSON `/api/public/songs` теперь содержит `contentReady`/`albumPictureUrl`/`authorPictureUrl` для каждой песни; можно открывать User Story фазы.

---

## Phase 3: User Story 1 — Иконка плеера в поиске отражает фактическую доступность (Priority: P1) 🎯 MVP

**Goal**: зелёная/золотая/серая иконка `<PlayerIcon>` в каждой строке результата поиска соответствует фактической доступности песни (вместо постоянно серой).

**Independent Test**: открыть `/search` (см. quickstart.md Сценарий A), ввести запрос с ≥1 песней «в эфире», ≥1 готовой не в эфире, (опц.) ≥1 неготовой. Иконки соответствуют: в эфире → зелёная; готовая не в эфире → золотая; неготовая → серая. Клик по зелёной открывает плеер именно этой песни.

> **Note**: фронтовая часть уже передаёт `song.contentReady` в `<PlayerIcon>` (см. `SearchView.vue:91, 103, 143, 156`) — код НЕ меняется; достаточно, что Phase 2 доставил поле. Задачи этой фазы — manual-валидация по quickstart.md.

### Implementation for User Story 1

- [x] T016 [US1] Verify (no code change) что `SearchView.vue` строки 91, 103, 143, 156 уже корректно пробрасывают `:content-ready-state="song.contentReady ? 'ready' : 'notready'"` в `<PlayerIcon>`; в спеке FR-001 логика `PlayerIcon` уже правильная (`PlayerIcon.vue:80-95`); задача — code-read-only confirmation
- [ ] T017 [US1] Manual test scenario A from quickstart.md — выполнить все 4 шага (запрос, верификация зелёной/золотой/серой, клик по зелёной → открывает `/player/<id>` нужной песни); результат зафиксировать в PR-описании
- [ ] T018 [US1] Edge case: открыть `/search` с одним из результатов, у которого `songSubscriptionAvailable=true`, `freelyAvailableNow=false`, `contentReady=true`, и авторизоваться **НЕ премиум-аккаунтом** → иконка плеера должна быть **золотой** (демо), НЕ зелёной (защита от «обхода» через подмену условий)
- [ ] T019 [US1] Edge case: авторизоваться **премиум-аккаунтом**, тот же запрос → та же премиум-песня (не в эфире) теперь показывает **зелёную** иконку (FR-001 «премиум-пользователь видит зелёную для всех готовых»)

**Checkpoint**: US1 полностью функциональна и независимо валидируется → bug с серой иконкой закрыт.

---

## Phase 4: User Story 2 — Строка поиска выглядит как в плейлисте/избранном (Priority: P1)

**Goal**: каждая строка результата поиска визуально совпадает со строкой `PlaylistEditView` (чёрная плашка превью альбома+автора → название → подпись «Автор - год, альбом» → иконки плеер/корзина/премиум/избранное/плейлист). Применяется одинаково на десктопе и мобиле (Clarification Q1 → A).

**Independent Test**: визуальное сравнение `/search` и `/account/playlists/<favorites-id>` для одной и той же песни (quickstart.md Сценарий B). Чёрный блок превью, название, подпись, иконки справа — попиксельно одинаковые (с поправкой на отсутствие drag-handle/mute-кнопок, которых в поиске быть не должно).

> **Зависимость**: Phase 2 (поля `albumPictureUrl`/`authorPictureUrl` в JSON).

### Implementation for User Story 2

- [x] T020 [US2] В `karaoke-public/src/views/SearchView.vue` удалить `<table class="km-table km-table-songs">...</table>` блок (строки ~10-118) и `<div class="km-cards">...</div>` блок (строки ~118-164) полностью; оставить только форму поиска и `<div class="km-empty">Ничего не найдено</div>`
- [x] T021 [US2] В `karaoke-public/src/views/SearchView.vue` template добавить `<draggable>` НЕ НУЖЕН (нет drag-drop в поиске); вместо этого `<div class="km-song-list">` с `<template v-for="song in searchResults" :key="song.id">` — каждая итерация рендерит одну `<div class="km-song-row">`
- [x] T022 [P] [US2] В `karaoke-public/src/views/SearchView.vue` template, внутри `km-song-row`, добавить `km-song-pictures` div с двумя `<img>`: album (square 48×48), author (horizontal 120×48); на каждом: `loading="lazy"` + `decoding="async"` + `@error="song._albumPictureFailed = true"` (transient flag pattern) + `<div v-else class="...-fallback">♪</div>` / `👤` (D5 в research.md, D3 — структура из PlaylistEditView.vue:109-128)
- [x] T023 [US2] В `karaoke-public/src/views/SearchView.vue` template добавить `km-song-info` div: `<router-link :to="{ name: 'song', query: { id: song.id } }" class="km-song-title-link">{{ song.songName || 'Песня #' + song.id }}</router-link>`; ниже `<div class="km-song-sub">` с шаблоном «Автор - год, альбом» по `PlaylistEditView.vue:139-159` (FR-009 в спеке, разделители с условием, имя автора как router-link на `/zakroma/<authorId>` при резолве, fallback на `<span>` если автора нет в `authorTiles`)
- [x] T024 [US2] В `karaoke-public/src/views/SearchView.vue` template добавить action icons group справа от `km-song-info`: `<PlayerIcon>` (с `:content-ready-state`, `:in-air`, `:flag-free`, `:premium`, `:has-subscription` — теми же проп-именами, что в текущем коде SearchView:103-108), `<CartIcon>` при `showCartIcon(song)`, `<FavoriteIcon>`, `<PlaylistIcon>`; рядом — inline-badge «В эфире до...» / «Будет в эфире с...» по `dateLabel(song)` и `PremiumIcon` если `showCoin(song)`
- [x] T025 [US2] В `karaoke-public/src/views/SearchView.vue` `<script setup>` добавить `const authorTiles = computed(() => store.state.zakroma?.authorTiles || [])` (mirror `PlaylistEditView.vue:241`); добавить `function authorIdFor(name) { ... }` helper (mirror `PlaylistEditView.vue:249-253`) — резолв имени автора в authorId, `null` если нет
- [x] T026 [US2] В `karaoke-public/src/views/SearchView.vue` `<script setup>` добавить вызов `await store.dispatch('zakroma/loadAuthorTiles', 'main')` на `onMounted` если `authorTiles.value.length === 0` (mirror `PlaylistEditView.vue:575-582`); используется для резолва имён авторов в ссылках
- [x] T027 [US2] Инициализировать transient-флаги превью: при загрузке результатов поиска (в `watch.searchResults` или в action action.callback) — `searchResults.value = (results || []).map(s => ({ ...s, _albumPictureFailed: false, _authorPictureFailed: false }))` (mirror `PlaylistEditView.vue:379-383`)
- [x] T028 [P] [US2] В `karaoke-public/src/views/SearchView.vue` `<style scoped>` добавить CSS-блок для row-layout, скопированный из `PlaylistEditView.vue:801-995`: `.km-song-list` (display:flex column gap:0.35rem), `.km-song-row` (display:flex align-items:center gap:0.6rem padding:0 0.7rem), `.km-song-pictures` (display:flex gap:5px margin:5px padding:5px background:#000 border-radius:8px), `.km-song-cover` (width:48px height:48px), `.km-song-author` (width:120px height:48px), `.km-song-info` (flex:1), `.km-song-title-link` (display:block color:var(--km-accent) text-decoration:none overflow:hidden text-overflow:ellipsis white-space:nowrap font-size:0.92rem font-weight:600), `.km-song-author-link` (display:inline-block max-width:100% color:var(--km-accent) text-decoration:none font-size:0.76rem), `.km-song-sub` (font-size:0.76rem color:var(--km-text2) overflow:hidden text-overflow:ellipsis white-space:nowrap), `:hover`/`:focus-visible` underline (PlaylistEditView.vue:959-965)
- [x] T029 [P] [US2] В `karaoke-public/src/views/SearchView.vue` `<style scoped>` добавить стили для action icons (если ещё нет): `.km-song-play`, `.km-song-btn`, `.km-song-remove` — на базе существующих `km-ctrl-*` стилей SearchView или просто инлайн обёрткой; icons берут текущий стиль из контекста (`platform-icon` или просто без обёртки)
- [x] T030 [US2] Pre-flight: проверить, что `loading="lazy" decoding="async"` уже на превью-картинках и что `@error` фолбэк есть — финальный визуальный аудит SearchView.vue (D5 в research.md)
- [ ] T031 [US2] Manual test scenario B from quickstart.md (визуальное сравнение с PlaylistEditView, десктоп + мобила); результат зафиксировать скриншотами в PR

**Checkpoint**: US2 полностью функциональна → строка поиска попиксельно соответствует PlaylistEditView на обоих вьюпортах.

---

## Phase 5: User Story 3 — Все текущие функции поиска продолжают работать (Priority: P2)

**Goal**: ни одна из существующих функций поиска не сломана: премиум-флоу (PremiumIcon/CartIcon/SongSubscriptionModal), префилл из query, состояние «Ничего не найдено», classic/modern тема, mobile layout.

**Independent Test**: прогон quickstart.md Сценариев D/E/F/G — каждый сценарий работает идентично до-переделочному поведению.

> **Зависимость**: Phase 4 (новая разметка SearchView должна по-прежнему рендерить все существующие компоненты).

### Implementation for User Story 3

- [ ] T032 [US3] Manual test scenario D from quickstart.md: премиум-флоу — `PremiumIcon`, `CartIcon`, клик по золотой иконке → `SongSubscriptionModal`; убедиться, что `showCoin(song)`, `showCartIcon(song)`, `onSubscribeClick(song)` не сломаны
- [ ] T033 [US3] Manual test scenario E from quickstart.md: префилл из URL (`/search?author=X&songName=Y` запускает поиск автоматически) + «Ничего не найдено» при заведомо пустом запросе
- [ ] T034 [US3] Manual test scenario F from quickstart.md: mobile viewport 375×812 — адаптивность row-разметки через CSS (Clarification Q1)
- [ ] T035 [US3] Manual test scenario G from quickstart.md: переключение дизайна `classic`/`modern` — все цвета берутся из `--km-*` переменных (FR/Edge Cases)

**Checkpoint**: US3 функциональна → нет регрессий в существующих сценариях поиска.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: литеры, сборка, secret-чеки, финальная валидация по quickstart, LiveDocs-обновление.

**⚠️ Обязательная последовательность** per AGENTS.md «После ЛЮБОГО изменения в коде»:

- [x] T036 [P] Backend compile (шаг 1 обязателен): `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — MUST exit 0
- [x] T037 [P] Линтеры (шаг 2): `./gradlew :karaoke-web:ktlintCheck --parallel` И `./tools/check-eslint-baseline.sh karaoke-public` — никаких новых нарушений (baseline OK)
- [x] T038 [P] Backend bootJar (шаг 3): `./gradlew :karaoke-web:bootJar --parallel` — MUST exit 0; НЕ собирать `karaoke-app:bootJar` (Constitution «Категорически запрещено», агент не пересобирает karaoke-app)
- [x] T039 [P] Frontend (шаг 4): `cd karaoke-public && npm run build && npm run lint` — оба MUST exit 0
- [x] T040 Verify SC-006 (минимальный backend-diff): `git diff karaoke-web/src/main/kotlin` — единственный модифицированный файл `SongPublicDto.kt` плюс `PublicApiController.kt` (только внутри `songs()`); никаких изменений в `ZakromaPublicDto.kt`, `SitePlaylistItemDto.kt`, миграциях, контроллерах кроме `songs()`
- [x] T041 [P] Final secret check (Constitution VIII.3): `git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'` — MUST be empty
- [x] T042 [P] LiveDocs-обновление (FR-014): если существует `docs/features/search.md` — добавить короткую секцию про новые поля DTO (Clarification Q2 → A, 2026-08-28) и row-унификацию (Clarification Q1); если НЕ существует — создать (Constitution VI.9 требует per-feature документ)
- [x] T043 [P] Run LiveDocs CI checks: `bash tools/check-livedocs-structure.sh`, `bash tools/check-livedocs-cross-links.sh`, `bash tools/check-livedocs-external-links.sh` — все MUST exit 0 (AGENTS.md «LiveDocs CI / pre-commit»)
- [x] T044 [P] Run git pre-flight: `git status`, `git diff --stat`, `git log --oneline -5` — никаких случайно включённых файлов; staged changes — только задуманные
- [ ] T045 Manual test scenarios C, H, I from quickstart.md (ссылки, производительность на 200 песен, контрольная сверка с Plan SC-006) — зафиксировать результат в PR-описании

**Checkpoint**: 36 из 45 задач отмечены `[x]` (агент выполнил весь code/lint/build pipeline). 10 задач остаются `[ ]` и требуют ручной проверки пользователем в браузере:
- T017-T019 — визуальная валидация иконки плеера в 3 состояниях (зелёная/золотая/серая);
- T031 — попиксельное сравнение строки поиска с PlaylistEditView (скриншоты);
- T032-T035 — регресс-чеки премиум-флоу / префилла из URL / mobile-viewport / темы classic-modern;
- T045 — ссылки (FR-007/FR-008), производительность на 200 песен, контрольная сверка SC-006.

После ручной валидации (9 сценариев quickstart.md A-I) — PR готов к `gh pr checks && gh pr merge --merge`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: без зависимостей — начинается немедленно.
- **Phase 2 (Foundational)**: зависит от Phase 1 (T001–T005 дают контекст) — **BLOCKS все user stories**.
- **Phase 3 (US1, P1)**: зависит от Phase 2 (поля `contentReady` в JSON).
- **Phase 4 (US2, P1)**: зависит от Phase 2 (поля `albumPictureUrl`/`authorPictureUrl` в JSON).
- **Phase 5 (US3, P2)**: зависит от Phase 4 (новая разметка SearchView).
- **Phase 6 (Polish)**: зависит от US1, US2, US3.

### User Story Dependencies

- **US1 (P1)**: стартует после Foundational; US1 не зависит от US2/US3.
- **US2 (P1)**: стартует после Foundational; структурно независим от US1 (хотя делит `SearchView.vue`).
- **US3 (P2)**: стартует после US2 (новые row-стили должны существовать, чтобы валидировать «нет регрессий» на них).

### Within Each User Story

- Phase 2: data-class-fields (T006–T008) → `fromSong` (T009, T010) → controller batch-lookup (T012) → URL-helper (T013) → wire-into-fromSong call (T014) → compile-check (T015).
- US2: delete old branches (T020) → new list container (T021) → `km-song-pictures` (T022) → `km-song-info` (T023) → action icons (T024) → store integration (T025, T026) → transient flags (T027) → scoped CSS (T028, T029) → manual validation (T030, T031).

### Parallel Opportunities

- **T003/T004/T005** (Phase 1 read-only reconnaissance) — параллельно, разные файлы.
- **T006/T007/T008** (3 новых data-class поля) — после T003, можно параллельно править тот же файл, **но** безопаснее последовательно (один файл, риск конфликта при параллельных правках); рекомендую последовательно T006 → T007 → T008.
- **T022/T023/T024** (template-блоки `km-song-pictures`/`km-song-info`/`action-icons`) — параллельно после T020/T021 (все правки в `SearchView.vue` template, но в разных `<div>` местах).
- **T028/T029** (scoped CSS блоки) — параллельно в `<style scoped>`.
- **T036/T037/T038/T039** (Polish compile/lint/build) — последовательно per AGENTS.md mandatory order.
- **T042/T043** (LiveDocs create/check) — последовательно (T043 не имеет смысла без T042).

---

## Parallel Example: Phase 2 (Foundational)

```bash
# Recon (T003–T005 параллельно — read-only):
Task: "Read SongPublicDto.kt"
Task: "Read PublicApiController.songs()"
Task: "Read PlaylistEditView.vue (rows + styles)"

# Data-class fields (T006–T008) — лучше последовательно в 1 файл:
Task: "Add contentReady"
Task: "Add albumPictureUrl"
Task: "Add authorPictureUrl"

# Compile-проверка после — обязательная:
Task: "./gradlew :karaoke-web:compileKotlin --parallel"
```

## Parallel Example: Phase 4 (US2)

```bash
# После удаления старых блоков (T020-T021):
Task: "Добавить km-song-pictures (img + fallback)"
Task: "Добавить km-song-info (router-link + sub)"
Task: "Добавить action icons (PlayerIcon, CartIcon, ...)"

# CSS — параллельно в <style scoped>:
Task: "Стили row-layout (km-song-row, km-song-pictures, ...)"
Task: "Стили action buttons (km-song-play, km-song-btn, ...)"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2 = обе P1)

1. Phase 1 (Setup T001-T005) — ветка готова, читаем референсы.
2. Phase 2 (Foundational T006-T015) — backend DTO расширен + controller batch-lookup. **Это блокер для обоих US1 и US2**.
3. Phase 3 (US1 T016-T019) — валидация bug-фикса (зелёная иконка).
4. Phase 4 (US2 T020-T031) — редизайн row по образцу PlaylistEditView.
5. **STOP и VALIDATE**: прогон quickstart.md Сценарии A + B → MVP готов.
6. Demo/деплой если оба ✓.

### Incremental Delivery

1. Setup + Foundational → backend раздаёт 3 новых поля.
2. Phase 3 (US1) → Demo: «иконка плеера теперь зелёная для в-эфире-песен».
3. Phase 4 (US2) → Demo: «строки поиска выглядят как плейлист» (визуальный unblocker).
4. Phase 5 (US3) → Demo: «ничего из старого не сломалось» (регрессионный чек).
5. Phase 6 → PR-готовность.

### Parallel Team Strategy

С несколькими разработчиками:

1. Один разработчик делает Phase 1+Phase 2 последовательно (T001-T015).
2. После Phase 2 — параллельно:
   - Dev A: Phase 3 (US1) — manual-validation only, готов через минуты.
   - Dev B: Phase 4 (US2) — большая frontend-работа.
   - Dev C: готовит Phase 5 acceptance-сценарии (заранее).
3. Phase 6 (Polish) — после всех story.

---

## Notes

- [P] задачи = разные файлы или разные участки одного файла без runtime-зависимостей.
- [Story] метка = `US1`/`US2`/`US3` для traceability к spec.md user stories.
- Каждая User Story должна быть **независимо завершаемой и валидируемой** вручную по quickstart.md.
- Test-задачи НЕ генерируются (проект не пишет CI-тесты, см. AGENTS.md «Тесты»); manual-валидация заменяет их в фазах US.
- Коммит после каждой задачи или логической группы (T006→T008 одним коммитом, T012→T014 одним коммитом).
- **Стоп на любом чекпойнте** для независимой валидации story.
- Избегать: расплывчатых формулировок, конфликтов в одном файле (вынесены в [P]/последовательно согласно секции Parallel Opportunities), cross-story зависимостей, ломающих независимость.
- **На любом этапе**: видеть прогресс по `git log --oneline -10` и `git diff --stat` — никаких «дрейфов» в ненужные файлы.
