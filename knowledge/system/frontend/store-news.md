# Vuex store: News

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/News/store.js` — новости.

## Файл

`webvue3/src/components/News/store.js` (112 строк)

## State

```javascript
const NEWS_PER_PAGE = 35;

state: {
    newsList: [],
    newsListIsLoading: false,
    newsTarget: 'local',              // default
    newsTotalCount: 0,               // pagination (specs/090-news-pagination)
    newsCurrentPage: 1,
    newsPerPage: NEWS_PER_PAGE,
}
```

## Назначение

«Новости» проекта (`tbl_news`).

**`newsTarget`** (`'local'` default, как у Чата). Готовятся на LOCAL,
уходят на прод **штатной синхронизацией** (Sync, `key=news`) — как
Словари (`Dictionaries/store.js`).

Target оставлен переключаемым (как у Чата) для **локальной отладки**,
дефолт — `LOCAL`.

## Hot paths

- **`/api/news/list`** — дайджест (текущая страница).
- **`/api/news/save`** — создать/обновить.
- **`/api/news/delete`** — удалить.

## Pagination (specs/090-news-pagination)

- `newsList` хранит **только текущую страницу** (35 записей).
- `newsTotalCount` + `newsCurrentPage` управляют `<b-pagination>`
  в `NewsTable.vue`.

## Связь

- **News** ([entities-catalog.md#news](../../domains/catalog/components/entities-catalog.md#news)) —
  entity.
- **Two-DB sync** ([two-db-sync.md](../../domains/processing/components/two-db-sync.md)) —
  NewsSyncTarget.
- **NewsTemplateService** ([publishing-services.md](../../domains/publishing/components/publishing-services.md)) —
  шаблоны.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).