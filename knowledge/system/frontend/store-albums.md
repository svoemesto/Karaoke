# Vuex store: Albums

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Albums/store.js`.

## Файл

`webvue3/src/components/Albums/store.js` (155 строк)

## State

```javascript
state: {
    albumsDigest: [],                // полный дайджест (с автором, картинками, счётчиком)
    albumsDigestIsLoading: false,
    // Лёгкий дайджест (без автора/картинок/счётчика песен, specs/022)
    albumsDigestLite: [],
    albumsDigestLiteIsLoading: false,
    albumsTableCurrentPage: 1,        // persistent
}
```

**Два разных кеша** для двух use-cases:

- `albumsDigest` — **тяжёлый** дайджест для `AlbumsTable.vue`.
- `albumsDigestLite` — **лёгкий** для пикера «Альбом (ссылка)» в
  `SongEdit.vue`.

Отдельные слоты, чтобы пикер не толкался в один кеш с
"тяжёлым" дайджестом (иначе кто первый загрузился — того и кеш).

## Hot paths

- **`/api/albums/list`** — полный.
- **`/api/albums/digestLite`** (или подобный) — лёгкий.

## Связь

- **Catalog** ([entities-catalog.md#album](../../domains/catalog/components/entities-catalog.md#album)) —
  Album entity.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).