---
status: Active
slug: 300-author-pagination-filter-bug
related:
  - ../../specs/300-author-pagination-filter-bug/spec.md
  - ../../specs/300-author-pagination-filter-bug/plan.md
  - ../../specs/300-author-pagination-filter-bug/audit.md
  - ../../docs/features/pagination-filter-admin-tables.md
---

# 300 — Корректная пагинация таблиц admin SPA после применения фильтра

> Drill-down — [specs/300-author-pagination-filter-bug/spec.md](../../specs/300-author-pagination-filter-bug/spec.md).

## Что делает

Исправляет баг (OpenProject #50) в admin SPA `webvue3`: после применения фильтра в таблицах **Authors, Albums, Pictures, SiteUsers** на странице N>1, сужающего выборку до меньшего числа страниц, таблица показывала **пустую страницу** вместо результатов фильтра.

Корневая причина: `<Entity>Table.vue` использовал `countRows = digests.length` (длина текущей страницы) как `:total-rows` для `<b-pagination>`, но не имел watcher на `countRows`. После фильтра массив уменьшался, а `currentPage` оставался прежним → `<b-table :current-page="N">` пытался показать страницу, которой нет.

## Где в коде

### Новые watcher'ы в 4 Table.vue (по образцу `Songs/SongsTable.vue:998-1009`)

| Файл | Изменение |
|------|-----------|
| `webvue3/src/components/Authors/AuthorsTable.vue` | +14 строк — `watch.countRows` |
| `webvue3/src/components/Albums/AlbumsTable.vue` | +12 строк — `watch.countRows` |
| `webvue3/src/components/Pictures/PicturesTable.vue` | +12 строк — `watch.countRows` |
| `webvue3/src/components/SiteUsers/SiteUsersTable.vue` | +14 строк — `watch.countRows` |

```js
countRows: {
  handler(newCount) {
    const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
    if (this.currentPage > totalPages) {
      this.currentPage = 1
    }
  },
},
```

Логика: если `currentPage > totalPages` — сбрасываем на 1. Покрывает сужение фильтра, расширение фильтра и сброс фильтра.

### Новые документы

- [`docs/features/pagination-filter-admin-tables.md`](../../docs/features/pagination-filter-admin-tables.md) — per-feature документ (Constitution FR-009).

## Почему другие таблицы не требуют фикса

| Таблица | Причина |
|---------|---------|
| Songs | Уже имеет `watch.countRows` (line 998-1009) — **эталон** |
| ShareLinks | Уже имеет `watch.countRows` (line 265) |
| Subscriptions | Уже имеет `watch.countRows` (line 256) |
| News | Другой правильный паттерн: `setNewsTarget` сбрасывает `newsCurrentPage(1)` + `totalCount` в state |
| TopListenedSongs, TopUsers | N/A — нет пагинации |
| ListeningHistory, SitePlaylists | Требуют отдельной ручной проверки (отдельная задача) |

Полный аудит — в [`specs/300-author-pagination-filter-bug/audit.md`](../../specs/300-author-pagination-filter-bug/audit.md).

## Что НЕ менялось

- Backend (`karaoke-app`, `karaoke-web`) — без изменений.
- Vuex-сторы (`*/store.js`) — без изменений.
- Контракты эндпоинтов — без изменений.
- Существующие watcher'ы на `currentPage` — без изменений.

## Как добавить этот watcher в следующую таблицу

При создании новой таблицы admin SPA с фильтром + пагинацией:

1. В `<Entity>Table.vue` добавить `watch.countRows` по шаблону выше.
2. В `data()`: `perPage: 30`, `currentPage: ... || 1`.
3. В `computed`: `countRows() { return this.<digests>?.length || 0 }`.

Полная инструкция — в [`docs/features/pagination-filter-admin-tables.md`](../../docs/features/pagination-filter-admin-tables.md).

## Future work (отдельная задача)

Добавить **реальный `total`** в backend-ответы Authors/Albums/Pictures/SiteUsers/Dictionaries/Processes/Properties:

- `mapOf(...)` в `*Controller.kt` в `karaoke-app` → + поле `total`.
- `<Entity>Table.vue` → `countRows` берёт `get<Entity>TotalCount`, а не `digests.length`.

Watcher на `countRows` продолжит работать без изменений.