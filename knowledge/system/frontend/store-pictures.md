# Vuex store: Pictures

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Pictures/store.js`.

## Файл

`webvue3/src/components/Pictures/store.js` (177 строк)

## State

```javascript
state: {
    picturesDigest: [],            // дайджест картинок
    picturesDigestIsLoading: false,
    pictureCurrent: undefined,     // текущая (для редактирования)
    pictureSnapshot: undefined,    // snapshot для сравнения
    pictureCurrentId: 0,
    picturesTableCurrentPage: 1,   // persistent
}
```

## Hot paths

- **`/api/pictures/list`** — дайджест.
- **`/api/pictures/getById`** — одна картинка.
- **`/api/pictures/update`** — редактировать.

## Связь

- **Pictures** ([pictures.md](../../domains/catalog/components/pictures.md)) —
  entity.
- **Storage** ([storage domain](../../domains/storage/domain.md)) —
  MinIO URLs.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).