---
status: Active
slug: 262-search-pagination
related:
  - ../../specs/262-search-pagination/spec.md
  - ../../specs/262-search-pagination/plan.md
  - ../../specs/262-search-pagination/data-model.md
  - ../../specs/262-search-pagination/contracts/api-songs.md
  - ../architecture/L1-system-context.md
---

# 262 — Search pagination (infinite scroll «Загрузить ещё») (LiveDoc)

> Drill-down — [specs/262-search-pagination/spec.md](../../specs/262-search-pagination/spec.md).

## Что делает

Эндпоинт `GET /api/public/songs` принимает опциональные параметры `page` и `pageSize`;
возвращает обёртку `PagedSongsDto { items, totalCount, page, pageSize, hasMore }`
вместо `List<SongPublicDto>` при наличии хотя бы одного из параметров (обратная
совместимость со старым форматом). Vuex-стор `songs` расширен `searchPagination`
slice; `SearchView.vue` показывает счётчик «Показано X из Y», кнопку
«Загрузить ещё» с inline-ошибкой и retry, синхронизирует URL `?page=N` для
F5-восстановления и shareable-ссылок.

## User Stories (краткий список)

- **US1** (P1): Поиск с большим числом результатов остаётся отзывчивым.
- **US2** (P1): Регресс спеки 261 — UI строки не ломается на любой странице.
- **US3** (P2): Бэкенд-контракт edge cases: totalCount консистентен, пересечений
  между страницами нет, hasMore на границе.
- **US4** (P3): Error UX: rapid-click protection, F5-восстановление, shareable URL.

## Acceptance Criteria (индикаторы)

- [ ] **AC1**: Поиск с ≥500 результатами — первая порция 35 + counter «X из Y».
- [ ] **AC2**: Клик «Загрузить ещё» добавляет следующие 35 без перезаписи списка.
- [ ] **AC3**: URL обновляется `?page=N` после подгрузки, F5 восстанавливает срез.
- [ ] **AC4**: Backend `totalCount` одинаков для всех страниц одного запроса.
- [ ] **AC5**: UI спеки 261 (иконки, превью, ссылки) работает на любой странице.

## Архитектура

- **Backend**: `PagedSongsDto` (новый) + расширение `PublicApiController.songs()` +
  companion `Song.countMatchingAttr(...)`. Использует существующий helper
  `getWhereList(...)` для WHERE-фильтров; limit/offset через `args["limit"]` /
  `args["offset"]` в `Song.loadListFromDb` (уже поддерживается).
- **Frontend**: `searchPagination` state + `appendSearchResults` mutation +
  `loadMoreSearchResults` action (race-condition через `latestLoadMoreId`).
  Pass 243 bugfixes — независимые v-if для counter и song-list,
  `scrollBehavior: false` для query-only изменений (страница не прыгает
  вверх при подгрузке), `onLoadMore` фильтры с fallback на `this.form`
  (URL не обновлялся фильтрами при первом поиске).

## Pass 244 (governance)

При реализации этой спеки выяснилось, что **prettier запускался только в
pre-commit и CI**, но не при работе агента. Результат: неотформатированные
файлы попали в PR и привели к лишнему раунду правок. Pass 244 добавил
`npm run format:check` в шаг 4 «Обязательной проверки кода» в AGENTS.md —
prettier теперь проверяется **при работе агента** каждый раз.

## Реализация (Pass 243)

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/PagedSongsDto.kt` —
  новый DTO с KDoc и `@see` на этот LiveDoc (FR-006 governance).
- `karaoke-web/.../PublicApiController.kt:651-784` — расширен `songs()` с
  page/pageSize, нормализация (whitelist `[10,25,35,50,100]`), обратная совместимость.
- `karaoke-app/.../model/Song.kt:7618` — `countMatchingAttr(...)` companion.
- `karaoke-public/src/store/modules/songs.js` — расширен Vuex (state, mutations, actions).
- `karaoke-public/src/views/SearchView.vue` — UI (counter, button, error block, URL-sync).
- `karaoke-public/src/router/index.js:128` — `scrollBehavior: false` для `to.path===from.path`.

## Связанные документы

- [specs/262-search-pagination/spec.md](../../specs/262-search-pagination/spec.md) — полная спека.
- [specs/262-search-pagination/plan.md](../../specs/262-search-pagination/plan.md) — research + design.
- [specs/262-search-pagination/data-model.md](../../specs/262-search-pagination/data-model.md) — entity model.
- [specs/262-search-pagination/contracts/api-songs.md](../../specs/262-search-pagination/contracts/api-songs.md) — endpoint contract.
- [livedocs/features/002-ci-lint-enforcement.md](002-ci-lint-enforcement.md) — CI gates (Pass 244 fix упомянут).
- [AGENTS.md](../../AGENTS.md) — prettier в шаге 4 (Pass 244).
- Спека-предшественник: [livedocs/features/261-search-results-ui.md](261-search-results-ui.md).
