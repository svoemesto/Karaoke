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

- **`POST /api/authors/authorsdigests`** — дайджест.
- **`POST /api/authors/withnewalbumcount`** — бейдж.
- **`POST /api/authors/updateauthor`** — создать/обновить.
  Отдельных `/getById` и `/delete` у авторов в коде нет
  (проверено 2026-09-27: `ApiController` — только
  `/authors/authorsdigests`, `/authors/updateauthor`,
  `/authors/withnewalbumcount`).

## Связь

- **Author** ([entities-catalog.md#author](../../domains/catalog/components/entities-catalog.md#author)) —
  entity.

## Changelog

- **Pass 396** (2026-09-09): Initial. Автор: agent (Karaoke).