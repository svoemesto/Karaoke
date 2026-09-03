# Research: Корректная пагинация таблиц после применения фильтра

**Дата**: 2026-09-03
**Связанная спека**: [spec.md](spec.md)
**Ветка**: `300-author-pagination-filter-bug`

## Summary

В проекте **нет общего компонента/композаба пагинации**. Каждая таблица admin SPA `webvue3` имеет свой Vuex-модуль в `webvue3/src/components/<Entity>/store.js` со своим state, `setXxxTableCurrentPage` mutation и `loadXxxDigests` action. UI пагинации реализован через `<b-pagination>` (Bootstrap-vue-next) в соответствующем `<Entity>Table.vue`.

**Эталонный правильный паттерн** уже реализован в двух местах:

1. **`News/store.js`** — при смене target (`setNewsTarget`) action явно делает `setNewsCurrentPage(1)` перед перезагрузкой. Это закрывает случай смены источника данных.
2. **`Songs/SongsTable.vue` (строки 998–1009)** — есть watcher на `countRows`, который после каждой загрузки пересчитывает `totalPages = ceil(countRows / perPage)` и сбрасывает `currentPage` в 1, если текущая страница вышла за пределы. Это закрывает случай уменьшения выборки.

**Все остальные таблицы (Authors, Albums, Pictures, SiteUsers) имеют watcher только на `currentPage` (для сохранения в стор), но НЕ имеют watcher на `countRows`. Это и есть источник бага.**

## Технический контекст (подтверждённый кодом)

### Архитектура хранения состояния

| Таблица | Store-файл | State `*CurrentPage` | `load*Digests` action | Watcher `countRows` |
|---------|-----------|---------------------|----------------------|---------------------|
| Authors | `webvue3/src/components/Authors/store.js` | `authorsTableCurrentPage` | `loadAuthorsDigests(ctx, params)` → `/api/authors/authorsdigests` | ❌ нет |
| Songs | `webvue3/src/components/Songs/store.js` | `songsTableCurrentPage` | `loadSongsDigests(ctx, params)` → `/api/songsdigests` | ✅ есть (lines 998–1009) |
| Albums | `webvue3/src/components/Albums/store.js` | `albumsTableCurrentPage` | `loadAlbumsDigests(ctx, params)` → `/api/albums/albumsdigests` | ❌ нет |
| Pictures | `webvue3/src/components/Pictures/store.js` | `picturesTableCurrentPage` | `loadPicturesDigests(ctx, params)` → `/api/pictures/picturesdigests` | ❌ нет |
| SiteUsers | `webvue3/src/components/SiteUsers/store.js` | `siteUsersTableCurrentPage` | `loadSiteUsersDigest(ctx, params)` → `/api/siteusers/digest` | ❌ нет |
| News | `webvue3/src/components/News/store.js` | `newsCurrentPage` (+ `newsTotalCount`) | `loadNews(ctx)` → `/api/news/list` | ✅ другой паттерн (сбрасывает currentPage при смене target) |

### Backend контракты (по результатам grep)

- `/api/authors/authorsdigests` (`karaoke-app/.../ApiController.kt:6188-6227`) — возвращает `mapOf("workInContainer" to ..., "authorsDigests" to List)`. **НЕТ поля `total` / `totalCount`**.
- `/api/news/list` (`News/store.js:62-79`) — возвращает `{news: [...], total: int}`. **ЕСТЬ `total`**.
- Аналогично нет `total` в backend-ответах для Albums/Pictures/SiteUsers (предположительно; не подтверждено для всех, но клиентский `countRows = list.length` подтверждает, что иначе total был бы в state).

### UI-пагинация

В каждом `<Entity>Table.vue` используется:

```vue
<b-pagination
  v-model="currentPage"
  :total-rows="countRows"
  :per-page="perPage"
  ...
/>
<b-table
  :items="<digests>"
  :per-page="perPage"
  :current-page="currentPage"
  ...
/>
```

`countRows` — **computed из `digests.length`** (т.е. это длина текущей загруженной страницы, а не общее число записей). Это и есть причина, по которой `<b-pagination>` показывает «Page 1 of 1» даже когда пользователь был на странице 3 — `b-pagination` вычисляет pages из `total-rows`, который после загрузки уменьшенной выборки стал равен размеру страницы.

## Decisions

### Decision 1: Фикс — на уровне Table.vue (UI), без изменений backend

**Выбор**: применить **паттерн из SongsTable** (`watchers.countRows`) к Authors/Albums/Pictures/SiteUsers Table.

**Rationale**:
- Минимальные изменения: только в 4 `.vue`-файлах добавляется watcher по образцу SongsTable (5-7 строк на каждый).
- Не затрагивает backend-контракты (`AGENTS.md`: «Фикс не должен менять API/контракты бэкенда»).
- Не затрагивает store-actions и существующие mutations.
- Полностью соответствует уже существующему правильному паттерну проекта (SongsTable).
- Принцип KISS: меньше мест изменений → меньше регрессий.

**Альтернативы, отклонённые**:

| Альтернатива | Почему отклонена |
|--------------|------------------|
| Добавить `total` в backend-ответ (`authorsDigests`, `albumsDigests`, …) и в store | Меняет backend-контракт → отдельная задача в OpenProject, рост scope |
| Создать общий composable `usePaginatedTable` | Refactor >5 компонентов, рост регрессий, не нужен для баг-фикса |
| Сброс `currentPage=1` в `load*Digests` при наличии `filter*` параметров | Хрупко: легко пропустить фильтр-параметр; не решает race-condition между totalPages и currentPage, если backend ответит пустым массивом |
| Сброс в `*FilterModal.ok()` перед dispatch | Дублирование в каждой модалке; не покрывает случай прямого вызова `load*Digests` (например, SongsTable фильтрует из `mounted`) |

### Decision 2: Не добавлять `totalCount` в store / backend в этой задаче

**Выбор**: только клиентский фикс; backend-`total` — отдельная задача (если потребуется для FR-005 «корректное отображение счётчика»).

**Rationale**: спека явно ограничивает scope клиентом. Для FR-005 достаточно того, что `<b-pagination>` пересчитает `total-rows` из `countRows` — кнопки навигации обновятся (Songs это подтверждает). Если в будущем потребуется правильное «Page X of Y» (с реальным Y > размеру страницы), это отдельная задача.

### Decision 3: Политика FR-006 — «всегда страница 1 после сброса фильтра»

**Выбор**: подтверждено в Clarifications 2026-09-03.

**Реализация**: watcher на `countRows` в Songs-стиле **сбрасывает на 1, если `currentPage > totalPages`**. Это покрывает и сброс фильтра (currentPage > newTotalPages → 1), и сужение фильтра (currentPage > newTotalPages → 1), и расширение фильтра (currentPage ≤ newTotalPages → остаёмся, без скачка). Также — политика KISS, согласуется с FR-006.

### Decision 4: Race condition (FR-009) — handled внутри `setXxxTableCurrentPage` + watcher

**Выбор**: оставить как есть; race в существующем коде решается тем, что `currentPage` обновляется через `setXxxTableCurrentPage` → watcher → `loadXxxDigests` → ответ записывается в `digests` → watcher на `countRows` срабатывает → clamp. Если в гонке старый ответ приходит после нового — он будет иметь старый `countRows`, но новый watcher уже отработал на актуальных данных.

**Альтернатива** (отклонена): добавить «request token» в actions для отбрасывания устаревших ответов. Сложно, нужно в каждом store, и в текущей архитектуре есть только один параллельный путь (page click). Достаточно документировать как known limitation.

## Files to be modified

| Файл | Изменение |
|------|-----------|
| `webvue3/src/components/Authors/AuthorsTable.vue` | +watcher на `countRows` (5-7 строк по образцу SongsTable.vue:998-1009) |
| `webvue3/src/components/Albums/AlbumsTable.vue` | +watcher на `countRows` |
| `webvue3/src/components/Pictures/PicturesTable.vue` | +watcher на `countRows` |
| `webvue3/src/components/SiteUsers/SiteUsersTable.vue` | +watcher на `countRows` |
| `docs/features/pagination-filter-admin-tables.md` (новый) | per-feature документ (FR-011 + Constitution FR-009) |
| `specs/300-author-pagination-filter-bug/audit.md` (новый) | результат аудита таблиц (FR-008) |

## Что НЕ меняется

- `webvue3/src/components/Songs/SongsTable.vue` — уже имеет правильный watcher (эталон).
- `webvue3/src/components/News/NewsTable.vue` + `News/store.js` — News имеет другой правильный паттерн (`setNewsTarget` → `setNewsCurrentPage(1)` + totalCount в state). Не трогаем.
- Любые `*FilterModal.vue` — фикс на уровне Table полностью покрывает их (модалка просто диспатчит `load*Digests` → store → state → watcher → clamp).
- Любые `*/store.js` — не требуют изменений.
- Backend (`karaoke-app`, `karaoke-web`) — не трогаем (по Assumptions спеки).

## Аудит таблиц (FR-008)

Предварительный список таблиц с фильтром + пагинацией в `webvue3` (по результатам `ls webvue3/src/views/` и `codegraph_explore`):

| View | Store | Filter | Пагинация | Баг? |
|------|-------|--------|-----------|------|
| `AuthorsView` | `Authors/store.js` | `AuthorsFilterModal` | `<b-pagination>` | ✅ воспроизводится |
| `SongsView` | `Songs/store.js` | `SongsFilterModal` | `<b-pagination>` | ❌ уже есть watcher |
| `AlbumsView` | `Albums/store.js` | `AlbumsFilterModal` | `<b-pagination>` | ✅ воспроизводится |
| `NewsView` | `News/store.js` | (target переключатель, не «фильтр» в строгом смысле) | `<b-pagination>` + totalCount | ❌ другой паттерн |
| `PicturesView` | `Pictures/store.js` | `PicturesFilterModal` | `<b-pagination>` | ✅ воспроизводится |
| `SiteUsersView` | `SiteUsers/store.js` | `SiteUsersFilterModal` | `<b-pagination>` | ✅ воспроизводится |
| `DictionariesView` | `Dictionaries/store.js` | (нет в выгрузке) | `<b-pagination>`? | TBD в `audit.md` |
| `ListeningHistoryView` | ? | ? | ? | TBD в `audit.md` |
| `SitePlaylistsView` | ? | ? | ? | TBD в `audit.md` |
| `ShareLinksView` | ? | ? | ? | TBD в `audit.md` |
| `SubscriptionsView` | ? | ? | ? | TBD в `audit.md` |
| `TariffsView` | ? | ? | ? | TBD в `audit.md` |
| `SitePlaylistsView` | ? | ? | ? | TBD в `audit.md` |

TBD-таблицы будут проверены в `audit.md` при выполнении задачи аудита (отдельная задача в tasks.md).

## Validation approach

- Линтеры: `./gradlew :karaoke-web:ktlintCheck` + `cd webvue3 && npm run lint:check`.
- Ручная проверка: воспроизвести сценарий User Story 1 на каждой из 4 таблиц.
- Тесты: в проекте нет unit-тестов для `webvue3` (см. AGENTS.md: «karaoke-app/src/test — @Disabled»). Финальная проверка — пользователем вручную или в production-like окружении (как требует Constitution).