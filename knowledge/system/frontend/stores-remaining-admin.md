# Vuex stores: remaining admin (6 stores)

> **Домен**: system (frontend)
> **Компонента**: 6 оставшихся admin stores (ListeningHistory,
> ShareLinks, Properties, NewsTemplates, SponsrSync, Chat).

## Файлы (6 stores)

| Store | Файл | Строк | Что |
|---|---|---|---|
| `ListeningHistory` | `webvue3/.../ListeningHistory/store.js` | 88 | История прослушиваний (admin) |
| `ShareLinks` | `webvue3/.../ShareLinks/store.js` | 104 | Share-ссылки (admin) |
| `Publish` | `webvue3/.../Publish/store.js` | 201 | Публикации (admin) — самый сложный из этих |
| `SponsrSync` | `webvue3/.../SponsrSync/store.js` | 92 | Sync с Sponsr (status) |
| `Chat` | `webvue3/.../Chat/store.js` | 218 | Чат (admin ↔ public) |
| `Properties` | `webvue3/.../Properties/store.js` | 81 | Properties UI (уже есть — [store-properties.md](store-properties.md)) |

## Общий паттерн (как везде)

```javascript
state: {
    <name>Digest: [],
    <name>DigestIsLoading: false,
    <name>DigestTotalCount: 0,        // для пагинации
    <name>TableCurrentPage: 1,      // persistent
    <name>Target: 'local',          // 'local' | 'remote'
}
```

## Hot paths

- **ListeningHistory**: `/api/listening-history/list`.
- **ShareLinks**: `/api/sharelinks/list` (см.
  [SongShareLinkService](../../domains/karaoke-web/components/song-share-link-service.md)).
- **Publish**: `/api/publish/list`, `/api/publish/templates` (см.
  [publishing-services.md](../../domains/publishing/components/publishing-services.md)).
- **SponsrSync**: `/api/sponsr-sync/status`.
- **Chat**: `/api/chat/threads`, `/api/chat/messages` (см.
  [monitor-checks.md](../../domains/monitoring/components/monitor-checks.md)).
- **Properties**: `/api/properties/digest` (см.
  [store-properties.md](store-properties.md)).

## Уникальные особенности

### `Chat` (218 строк)

- `chatThreads: []` — список тредов.
- `chatThreadsIsLoading`, `messages` per-thread, `unreadCount`.
- `UnreadChatMessagesCheck` (monitor) — алерт если unread > 1 час.

### `SponsrSync` (92 строки)

- `sponsrSyncStatus: null` — статус SponsrSyncScheduler (Pass 344).
- `sponsrSyncIsLoading`, `lastRun`, `last10` (history).

### `Publish` (201 строка)

- Самый сложный: templates + digest + current + filters.

## Связь

- [entities-catalog.md](../../domains/catalog/components/entities-catalog.md) —
  entities.
- [Schedulers](../../domains/processing/components/schedulers.md) — Chat, SponsrSync.

## Changelog

- **Pass 397-399** (2026-09-09): Initial. Автор: agent (Karaoke).