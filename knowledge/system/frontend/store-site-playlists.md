# Vuex store: SitePlaylists

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/SitePlaylists/store.js` —
> плейлисты пользователей (admin read-only).

## Файл

`webvue3/src/components/SitePlaylists/store.js` (91 строка)

## State

```javascript
state: {
    sitePlaylistsDigest: [],
    sitePlaylistsDigestIsLoading: false,
    sitePlaylistsTarget: 'local',       // default
    sitePlaylistDetail: undefined,     // текущий (для просмотра)
    sitePlaylistsTableCurrentPage: 1,  // persistent
}
```

## Назначение

**Read-only** просмотр плейлистов пользователей **ПУБЛИЧНОГО САЙТА**
(`tbl_site_playlists` / `tbl_site_playlist_items`).

**`sitePlaylistsTarget`** (`'local'` default) — реальные плейлисты
создаются на **боевой БД** сервера.

## Hot paths

- **`/api/siteplaylists/list`** — список.
- **`/api/siteplaylists/getById`** — детали.

## Связь

- **SitePlaylist / SitePlaylistItem**
  ([entities-catalog.md#siteplaylist--siteplaylistitem](../../domains/catalog/components/entities-catalog.md#siteplaylist--siteplaylistitem)) —
  entity.
- **PublicPlaylistController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — публичная сторона.

## Changelog

- **Pass 396** (2026-09-09): Initial. Автор: agent (Karaoke).