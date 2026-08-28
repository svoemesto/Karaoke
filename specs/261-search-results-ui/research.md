# Phase 0 — Research: 261-search-results-ui

## Контекст

Спека: `specs/261-search-results-ui/spec.md`. Покрывает 2 пункта пользователя:
- **Bug**: в результатах поиска иконка плеера всегда серая (контракт `SongPublicDto` не содержит `contentReady`, поэтому `<PlayerIcon :content-ready-state>` всегда трактует песню как `notready`).
- **UI**: строка результата должна повторять структуру `PlaylistEditView` (чёрная плашка превью альбома+автора, название, «Автор - год, альбом», иконки справа). Применяется одинаково на десктопе и мобилке (Clarification Q1 → A, 2026-08-28). Превью берётся из расширенного `SongPublicDto` (`albumPictureUrl` + `authorPictureUrl`, Clarification Q2 → A, 2026-08-28).

Бэкенд-изменение сведено к одному DTO-файлу (`SongPublicDto.kt`) + редизайн одной вьюхи (`SearchView.vue`).

## D1 — Источник URL превью на бэке

**Decision**: использовать существующий URL-строитель `/api/public/picture?file=<URLEncoder(picturePreviewFileName)>`, тот же, что в `ZakromaAlbumMetaPublicDto.fromAlbum:42-46` и `ZakromaPublicDto.fromZakroma:99-104`. Имя файла берётся из `Album.picturePreviewFileName` (FK через `Song.albumId`) и `Author.picturePreviewFileName` (резолв `Song.author` → `tbl_authors.id` по тому же контракту, что уже работает в спекуляции 259, FR-006, через `Author.loadIdsByNames` + затем load по id-у).

**Rationale**:
- URL-строитель уже работает в проде (закрома, страница альбомов) — никакой новой MinIO-логики.
- Цикл `Album`↔`Song` уже FK-based (`SongField.ALBUM_ID` per `SongField.kt:129`), батч-резолв `Album.getAlbumsByIds(ids)` существует (см. `Album.kt:294-309`).
- `Author.loadIdsByNames` уже используется для back-link из `SongPublicDto.authorId` (спека 259 FR-012) — резолв имени автора в id отлажен.

**Alternatives considered**:
- Добавить прямую денормализацию `song.albumPictureFileName` / `song.authorPictureFileName` в `tbl_songs` — **отклонено**: требует миграцию БД и перезаливки данных, нарушает FR-015 (минимальный diff).
- Использовать отдельный lookup-эндпоинт (`/api/public/song-pictures?ids=...`) — **отклонено**: вводит N+1 ритм и отдельный запрос ради строки, усложняет клиент.
- Оставить клиенту собирать URL по `songPictureUrl` (один) — **отклонено**: теряется превью автора (важно для визуального соответствия PlaylistEditView).

**Реализационные подробности** (Implementation Notes, не в FR):
- В `SongPublicDto.fromSong(...)` после маппинга существующих полей сделать два batch-lookup (один для `Album`, один для `Author`) и записать `albumPictureUrl`/`authorPictureUrl` в DTO.
- Batch-lookup выполняется на уровне контроллера `PublicApiController.songs()` (по аналогии с тем, как там уже делается `aliasByAuthor` через `Author.resolveByTerm`); сам `fromSong(s)` — pure-функция без БД-доступа.
- Если `Song.albumId == null` — `albumPictureUrl = ""` (фронт показывает плейсхолдер «♪»); если автора по имени нет в `tbl_authors` — `authorPictureUrl = ""` (плейсхолдер «👤»). Это согласуется с Edge Case спеки.

## D2 — Шаблон URL картинки (MinIO/локально)

**Decision**: использовать путь `/api/public/picture?file=<filename>` через endpoint `PublicApiController.picture`. Не использовать прямой `https://<host>/minio/.../` (это другой путь, что в `AuthorTilePublicDto.kt:59` — оттуда смотреть **нельзя**, он специфичен для тайлов и закромат; мы идём через публичный `/api/public/picture` по образцу `ZakromaPublicDto`).

**Rationale**: проект уже использует ровно этот эндпоинт для всех превью в публичной части; никаких новых routing/правил не появляется. Если `picturePreviewFileName` пустой/нет — вернётся `""`, фронт показывает плейсхолдер.

**Alternatives**: прямой URL на MinIO — отклонён (тащит backend-логику в nginx, нарушает минимальный диф).

## D3 — Архитектура строки в `SearchView.vue`

**Decision**: одна разметка строки (div-based, не `<table>`), которая:
1. Слева — `<div class="km-song-pictures">` (чёрная плашка с двумя `<img>` + fallback-div'ами, идентично `PlaylistEditView.vue:109-128`).
2. Далее — `<div class="km-song-info">` с `<router-link class="km-song-title-link">` для названия и `<div class="km-song-sub">` для подписи «Автор - год, альбом» (разделители по правилам спеки 259 FR-002/FR-006, см. `PlaylistEditView.vue:139-159`).
3. Справа — `<PlayerIcon>` / `<CartIcon>` / `<FavoriteIcon>` / `<PlaylistIcon>` (тот же набор, что в строках 86-117 SearchView сейчас, только встроенный в row вместо ячеек таблицы).
4. Премиум-иконка (`PremiumIcon`) и подпись эфира (`В эфире до…`) — рендерятся как inline-badge в блоке `km-song-info` или отдельный мини-блок над иконками (один блок на оба, чтобы не плодить мелких колонок).

Существующие `<table>` и `<div class="km-cards">` — обе ветки удаляются (Clarification Q1 → A, 2026-08-28). CSS — копия стилей из `PlaylistEditView.vue` (`km-song-row`, `km-song-pictures`, `km-song-cover`, `km-song-author`, `km-song-info`, `km-song-title-link`, `km-song-author-link`, `km-song-sub`, плюс бутстрап-стили для action-кнопок).

**Rationale**: соответствие референсу (`PlaylistEditView`) + решает визуальное расхождение «как в плейлисте» vs «как сейчас в поиске». Адаптивность через CSS, без двух branchей рендера.

**Alternatives considered**:
- Извлечь row в общий компонент `<SongRow :song variant="search|playlist" :show-premium-icon :show-cart-icon :show-favorite :show-playlist>` — чище, но больше кода и props; оставлено как **опциональная** гипотеза (Implementation Notes).
- Оставить `<table>` для десктопа — **отклонено**: усложняет вёрстку, не соответствует PlaylistEditView (тоже не `<table>`).

## D4 — Совместимость по `contentReady`

**Decision**: `<PlayerIcon :content-ready-state="song.contentReady ? 'ready' : 'notready'">` в `SearchView.vue` уже передаёт это значение напрямую — нужно только, чтобы DTO его содержал. Логика `PlayerIcon.vue:80-95` (Pass 239, specs/239) уже корректна: `isActive = contentReady && (inAir || flagFree || premium || hasSubscription)`. Никаких изменений в PlayerIcon не требуется.

**Rationale**: лишний «слой абстракции» (`usePlayerReadiness`) НЕ нужен — он применялся раньше из-за 2500 SQL per-load, теперь готовность приходит батчем в составе `/api/public/songs` (Pass 239 архитектура).

**Alternatives**: оставить `usePlayerReadiness` как было — отклонён, он уже удалён в Pass 239 для SearchView/ZakromaView (setup():210-222 SearchView.vue).

## D5 — Производительность / ленивая загрузка картинок

**Decision**: `<img :src="..." loading="lazy">` (нативный HTML lazy-loading) + `decoding="async"` для каждой превьюшки строки. Chrome/Firefox/Safari поддерживают с 2019+, мобильные viewport — адекватная экономия трафика для длинных результатов.

**Rationale**: для 200 песен (SC-007) и mobile-viewport — критично: без `loading="lazy"` все превью грузятся сразу, layout thrashing.

**Alternatives**: IntersectionObserver-based lazy-loading с V2/Vue plugin — отклонён, native `loading="lazy"` уже даёт нужный эффект без зависимостей.

## D6 — Файл CSS

**Decision**: добавить CSS-блок `<style scoped>` в `SearchView.vue` с копией нужных стилей из `PlaylistEditView.vue:801-995`. Scoped — чтобы не протекало в другие вьюхи. По возможности — вынести в общий CSS-файл `assets/css/km-rows.css` или подобное, если это окажется чище (Implementation Notes, не блокирует).

**Rationale**: scoped удерживает правки в одном PR; вынос в общий файл уменьшает дублирование, но требует аккуратности (применяется в обеих вьюхах). Решение по выносу делается на этапе Implementation Notes.

## Сводка решений для исполнителя

| # | Решение | Где применяется |
|---|---|---|
| D1 | Бэтч-resolve `Album`/`Author` для URL превью | `PublicApiController.songs()` + `SongPublicDto.fromSong` (через signal/передачу с контроллера, см. Implementation Notes) |
| D2 | URL через `/api/public/picture?file=<encoded>` | `SongPublicDto.kt` (новый field-initializer) |
| D3 | Div-row, адаптивно через CSS | `SearchView.vue` template + script |
| D4 | `contentReady` напрямую в `<PlayerIcon>` | `SearchView.vue:103-109` (изменение минимально: уже передаётся, нужно только поле) |
| D5 | `loading="lazy" decoding="async"` на `<img>` | `SearchView.vue` (внутри `km-song-pictures`) |
| D6 | Scoped-копия стилей PlaylistEditView | `SearchView.vue` `<style scoped>` |

## Открытые Implementation Notes (не блокируют спекy)

1. **Прокидывание резолвленного URL в `fromSong`**: `SongPublicDto.fromSong(s)` — pure-функция; `PublicApiController.songs(...)` — место с БД-доступом. Реализация либо:
   - A. Прокинуть `albumPictureUrl` и `authorPictureUrl` параметрами в `fromSong(s, ..., albumUrl, authorUrl)`, а контроллер сам делает batch-lookup (как сейчас делает `aliasByAuthor`).
   - B. Вынести URL-сборку в helper-метод, вызываемый из контроллера.
   - Конкретный выбор — Implementation Notes (оба валидны).
2. **Вынос row в общий компонент vs копия**: Implementation Notes (оба валидны, копия проще, общий компонент DRY).
3. **CSS-вынос**: Implementation Notes (scoped-копия безопасна, общий файл чище).

## Research → DONE

Все замечания из спеки «NEEDS CLARIFICATION» уже закрыты на этапе `/speckit.clarify` (Q1, Q2). Никаких новых NEEDS CLARIFICATION в Phase 0 не появилось.
