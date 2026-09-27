# Frontend views (webvue3)

> **Домен**: system (cross-cutting)
> **Компонента**: каталог 26 view-страниц в `webvue3/src/views/`.

## Назначение

`webvue3/src/views/` — **26 .vue-файлов**, каждая соответствует
одной admin-странице (роуту). Здесь — **каталог** с кратким
назначением каждой. Подробности — TODO Pass 343+.

## Каталог views (26)

| View | Назначение | Backend endpoints |
|---|---|---|
| `HomeView.vue` | Главная (dashboard, мониторинг) | `/api/monitor/...` |
| `SongsView.vue` | **Главная таблица** — список песен | `POST /api/songs` |
| `AlbumsView.vue` | Альбомы | `POST /api/albums/albumsdigests` |
| `AuthorsView.vue` | Авторы | `POST /api/authors/authorsdigests` |
| `PicturesView.vue` | Картинки | `POST /api/pictures/picturesdigests` |
| `DictionariesView.vue` | Словари | `POST /api/dictionaries/list` |
| `NewsView.vue` | Новости | `POST /api/news/list` |
| — | Шаблоны новостей (вкладка `PublishTemplatesView.vue`, стр. 36) | `/api/news/templates` |
| `ListeningHistoryView.vue` | История прослушиваний | `/api/listeninghistory/digest` |
| `PublicSettingsView.vue` | Публичные настройки | `POST /api/publicsettings/digest` |
| `PromotionsView.vue` | Акции (PromoRule) | `POST /api/promorules/list` |
| `PropertiesView.vue` | Karaoke.properties (UI редактор) | `/api/properties/getproperty` |
| `TariffsView.vue` | Тарифы | `POST /api/tariffs/list` |
| `SubscriptionsView.vue` | Подписки | `/api/subscriptions/digest` |
| `SiteUsersView.vue` | Пользователи сайта | `/api/siteusers/digest` |
| `SitePlaylistsView.vue` | Плейлисты | `/api/siteplaylists/digest` |
| `ShareLinksView.vue` | Share-ссылки | `/api/sharelinks/digest` |
| `SongEditorView.vue` | Редактор песни (открытие SongEdit) | `/api/song/...` |
| `PlayerView.vue` | Плеер (headless-режим для рендера + обычный) | `/api/public/player/...` |
| `PublishView.vue` | Публикации | `/api/publicationsdigest` |
| `PublishTemplatesView.vue` | Шаблоны публикаций | `/api/vk/templates`, `/api/telegram/templates` |
| `SponsrSyncView.vue` | Sync с Sponsr | (см. SponsrSyncScheduler) |
| `SyncView.vue` | Двух-БД sync | `/api/sync/...` |
| `ProcessesView.vue` | Async-очередь | `/api/admin/processes` |
| `StemJobsView.vue` | Премиум StemJob | `/api/stemjobs/list` |
| `StatsView.vue` | Статистика | `/api/stats/...` |

## Связь views ↔ Vuex stores

Каждый view обычно соответствует одному Vuex store из
[vuex-patterns.md](vuex-patterns.md):

| View | Store |
|---|---|
| `SongsView.vue` | `Songs` |
| `AlbumsView.vue` | `Albums` |
| `PicturesView.vue` | `Pictures` |
| `ProcessesView.vue` | `Processes` |
| ... | ... |

## Сложные view

Несколько views особенно сложные и могут заслуживать отдельных
компонент:

- **`HomeView.vue`** — dashboard, объединяет несколько Vuex-модулей
  (Stats, Monitor, Health).
- **`PlayerView.vue`** — headless-режим для рендера через Playwright +
  обычный режим прослушивания.
- **`SongEditorView.vue`** — открывает `SongEdit.vue` (вложенный
  компонент, не view) с маркерами, lyrics, и т.д.
- **`ProcessesView.vue`** — самый длинный (управление очередью с
  bulk-операциями, см. [process-admin.md](../../domains/processing/components/process-admin.md)).

## Известные TODO

- [ ] **Каждый view** — детальный state/flow.
- [ ] **Routing** — где определён (router/index.js).
- [ ] **Navigation guards** — auth, permissions.
- [ ] **Hot reload** — dev-сервер.
- [ ] **Lazy loading** — какие views загружаются on-demand.

## Связь с другими компонентами

- **Vuex stores** ([vuex-patterns.md](vuex-patterns.md)) — каждый view
  использует соответствующий store.
- **KaraokeProcessAdmin** ([process-admin.md](../../domains/processing/components/process-admin.md)) —
  `ProcessesView` использует admin endpoints.
- **SSE** ([sse domain](../../domains/sse/domain.md)) — real-time
  обновления.

## Changelog

- **Pass 351** (2026-09-09): Initial. Автор: agent (Karaoke).