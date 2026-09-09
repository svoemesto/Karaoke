# Vuex store: Authors

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Authors/store.js`.

## Файл

`webvue3/src/components/Authors/store.js` (102 строки)

## State

```javascript
state: {
    authorsDigest: [],
    authorsDigestIsLoading: false,
    authorsTableCurrentPage: 1,           // persistent
    // Бейдж «новые альбомы» — для пункта меню «Авторы» в App.vue
    authorsWithNewAlbumCount: 0,
}
```

## Бейдж «новые альбомы» (specs/176-authors-new-albums-badge)

`authorsWithNewAlbumCount` — **бейдж в App.vue** (по образцу
`chatUnreadTotal` / `submittedAssignmentsCount`).

**Источник**: `POST /api/authors/withnewalbumcount`
(specs/176-authors-new-albums-badge/contracts/api-authors-withnewalbumcount.md).

## Hot paths

- **`/api/authors/list`** — дайджест.
- **`/api/authors/withnewalbumcount`** — бейдж.
- **`/api/authors/getById`** / **`/update`** / **`/delete`**.

## Связь

- **Author** ([entities-catalog.md#author](../../domains/catalog/components/entities-catalog.md#author)) —
  entity.

## Changelog

- **Pass 396** (2026-09-09): Initial. Автор: agent (Karaoke).