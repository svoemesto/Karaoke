# Composable: usePlaylistMembership

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/usePlaylistMembership.js`
> — управление membership плейлиста (избранное, и т.д.).

## Файл

`karaoke-public/src/composables/usePlaylistMembership.js` (210 строк)

## Назначение

Управление membership плейлистов пользователя — добавление/удаление
песни в плейлист, избранное.

**`module-level` state** (НЕ local!) — аналогично
`usePlayerReadiness.js` (Pass 372): иначе на browser back из
/song/{id} state теряется, иконки плейлиста «моргают».

## State (module-level, по KDoc)

```javascript
// songId -> playlistId (в каких плейлистах песня)
// или songId -> {favorites: true}
const membership = reactive({})
const loading = reactive({})
```

**Объём state ограничен** — не memory leak.

## Hot paths

- **`POST /api/public/account/playlists/membership`** (рекомендуемый,
  Pass 361) — какие плейлисты содержат песню. JSON body `{"ids":[...]}`.
  Используется `usePlaylistMembership.load()` через `authPostJson()`
  (см. `services/authApi.js`).
- **`GET /api/public/account/playlists/membership`** (DEPRECATED,
  backward-compat) — старый CSV-эндпоинт, ломается на ~1350+ id (414).
  См. [ADR-0009](../../adr/0009-get-vs-post-large-payload.md) и
  [specs/361](../../../specs/361-playlists-membership-uri-length/spec.md).
- **`/api/public/playlist/{id}/add`** — добавить.
- **`/api/public/playlist/{id}/remove`** — удалить.

## Связь

- **SitePlaylist** ([entities-catalog.md#siteplaylist--siteplaylistitem](../../domains/catalog/components/entities-catalog.md#siteplaylist--siteplaylistitem)) —
  entity.
- **PublicPlaylistController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — endpoint.

## Changelog

- **Pass 361** (2026-09-10): Hot paths обновлены — `playlists/membership`
  теперь использует POST (`authPostJson`) вместо GET. См.
  [ADR-0009](../../adr/0009-get-vs-post-large-payload.md) +
  [specs/361](../../../specs/361-playlists-membership-uri-length/spec.md)
  (Issue OpenProject #77).
- **Pass 387** (2026-09-09): Initial. Автор: agent (Karaoke).