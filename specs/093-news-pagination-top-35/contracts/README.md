# Contracts: Новости — пагинация над таблицей, не больше 35 строк

**Branch**: `093-news-pagination-top-35` | **Date**: 2026-07-30 | **Plan**: [plan.md](./plan.md)

**Замечание**: фича **не вводит новых API-контрактов** и **не изменяет
существующих**. Папка `contracts/` создаётся согласно шаблону `plan.md`,
но содержимое ограничено этим README с обоснованием «контрактов нет».

## Почему пусто

- UI-only правка: меняется положение `<b-pagination>` в `NewsTable.vue` и
  значение `NEWS_PER_PAGE` в `store.js`. Бэкенд (Kotlin) и публичный фронт
  (`karaoke-public`) не затрагиваются.
- Контракт `/api/news/list?target=…&page=…&pageSize=…` уже принимает нужный
  `pageSize` (см. `specs/090-news-pagination/contracts/`, `data-model.md`,
  `quickstart.md`). Эта фича просто передаёт туда `35` вместо `50` из стора
  `webvue3/src/components/News/store.js`.
- Vuex-стор (`state.newsPerPage`, `getter getNewsPerPage`, mutation
  `setNewsTotalCount`/etc.) — внутренний контракт внутри `webvue3`, изменения
  ограничены одной константой и задокументированы в `data-model.md`.

## Существующие контракты (без изменений, для справки)

- `/api/news/list` — `POST`, params: `target`, `page` (0-based), `pageSize`.
  Возвращает `{ news: NewsDto[], total: number }`. Реализация —
  `NewsController` + `News.loadAll(limit, offset)` + `News.countAll()`
  (см. `specs/090-news-pagination/contracts/`).
- `/api/public/news` — `POST`, params: `page`, `size`. Возвращает массив
  новостей. Реализация — `PublicNewsController` + `PublicNewsView`.
  Свой `pageSize`, не связан с `newsPerPage` админки. **Не меняется.**
- `/api/news/create`, `/api/news/update`, `/api/news/delete` —
  без изменений.
- `/api/public/news/since` — без изменений (бейдж «непрочитанные»).
