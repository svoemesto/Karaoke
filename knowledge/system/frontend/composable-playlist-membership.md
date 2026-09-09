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

- **`/api/public/playlist/membership`** — какие плейлисты содержат
  песню.
- **`/api/public/playlist/{id}/add`** — добавить.
- **`/api/public/playlist/{id}/remove`** — удалить.

## Связь

- **SitePlaylist** ([entities-catalog.md#siteplaylist--siteplaylistitem](../../domains/catalog/components/entities-catalog.md#siteplaylist--siteplaylistitem)) —
  entity.
- **PublicPlaylistController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — endpoint.

## Changelog

- **Pass 387** (2026-09-09): Initial. Автор: agent (Karaoke).