# Vuex stores: remaining detailed (7 stores)

> **Домен**: system (frontend)
> **Компонента**: детальный каталог 6 оставшихся stores
> (ListeningHistory, ShareLinks, Publish, SponsrSync, Chat,
> NewsTemplates, HealthReport UI, Monitor UI).

## Файлы (7 stores)

| Store | Файл | Строк | Назначение |
|---|---|---|---|
| `ListeningHistory` | `webvue3/.../ListeningHistory/store.js` | 88 | История прослушиваний (admin) |
| `ShareLinks` | `webvue3/.../ShareLinks/store.js` | 104 | Share-ссылки (admin) |
| `Publish` | `webvue3/.../Publish/store.js` | 201 | Публикации (admin) — **самый сложный** |
| `SponsrSync` | `webvue3/.../SponsrSync/store.js` | 92 | Sync с Sponsr (status) |
| `NewsTemplates` | [WARN] отдельного store нет (проверено 2026-09-27); шаблоны — компонент `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` внутри `PublishTemplatesView.vue` | — | Шаблоны новостей |
| `HealthReport` | `webvue3/.../Common/HealthReport/store.js` | 74 | HealthReport UI |
| `Monitor` | `webvue3/.../Common/Monitor/store.js` | 64 | Мониторинг UI (alerts) |

## Детальные контракты

### `ListeningHistory`

- `listeningHistoryDigest: []` — текущая страница.
- `listeningHistoryDigestTotalCount: 0` — для пагинации.
- Endpoint: `/api/listeninghistory/digest`.

См. [ListeningHistory](../../domains/catalog/components/entities-catalog.md#listeninghistory).

### `ShareLinks`

- `shareLinksDigest: []`, `shareLinksDigestTotalCount: 0`.
- Endpoint: `/api/sharelinks/digest`.

См. [SongShareLinkService](../../domains/karaoke-web/components/song-share-link-service.md).

### `Publish` (201 строка, **самый сложный**)

- `publishDigest: []`, `publishIsLoading`.
- Templates + digest + current + filters.

См. [publishing-services.md](../../domains/publishing/components/publishing-services.md).

### `SponsrSync`

- `sponsrSyncStatus: null` — статус `SponsrSyncScheduler` (см.
  [schedulers.md](../../domains/processing/components/schedulers.md)).
- `sponsrSyncIsLoading`, `lastRun`, `last10` (history).

Endpoint: `/api/sponsrsync/status`.

### `Chat` (218 строк, **самый большой** из оставшихся)

- `chatThreads: []`, `chatThreadsIsLoading`.
- Per-thread messages, unreadCount.
- `UnreadChatMessagesCheck` (monitor) — алерт если unread > 1 час
  (см. [monitor-checks-detailed.md](../../domains/monitoring/components/monitor-checks-detailed.md)).

### `HealthReport` (Common/HealthReport)

- `healthReportList: []`, `healthReportListIsLoading`.

См. [health-report.md](../../domains/health/components/health-report.md).

### `Monitor` (Common/Monitor)

- `monitorAlerts: []` — список текущих алертов.

См. [monitor-checks-detailed.md](../../domains/monitoring/components/monitor-checks-detailed.md).

## Архитектура (общая)

Все следуют единому паттерну (см. [vuex-patterns.md](vuex-patterns.md)):

- `digest` или `list` — текущая страница.
- `totalCount` — для пагинации.
- `isLoading` — флаги.
- `current`, `snapshot`, `currentId` — для редактирования.
- `tableCurrentPage` — persistent.
- `target` — для syncable entities.

## Связь

- [entities-catalog.md](../../domains/catalog/components/entities-catalog.md) — entities.
- [Schedulers](../../domains/processing/components/schedulers.md) — Chat, SponsrSync.
- [HealthReport](../../domains/health/components/health-report.md) — HealthReport UI.
- [Monitor](../../domains/monitoring/components/monitor-checks-detailed.md) — Monitor UI.

## Changelog

- **Pass 416-420** (2026-09-09): Initial. Автор: agent (Karaoke).