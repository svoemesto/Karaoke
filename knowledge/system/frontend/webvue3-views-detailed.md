# webvue3 views (детальный)

> **Домен**: system (frontend)
> **Компонента**: детальный обзор 26 webvue3 views. Дополняет
> [webvue3-views.md](webvue3-views.md).

## Назначение

`webvue3/src/views/*.vue` — **26 view-страниц**, каждая —
**контейнер** для table-компонента из `components/<Entity>/<Entity>Table.vue`.

**Вся реальная логика** — в `components/`, не в `views/`. Views —
только layout + meta.

## Каталог (26 views)

| # | View | Файл | Контейнер для | Особенности |
|---|---|---|---|---|
| 1 | `HomeView.vue` | `views/HomeView.vue` | — | Главная (dashboard, мониторинг) |
| 2 | `SongsView.vue` | `views/SongsView.vue` | `Songs/SongsTable` | **Главная таблица** + bulk-operations |
| 3 | `AlbumsView.vue` | `views/AlbumsView.vue` | `Albums/AlbumsTable` | Список альбомов |
| 4 | `AuthorsView.vue` | `views/AuthorsView.vue` | `Authors/AuthorsTable` | Список авторов (с бейджем) |
| 5 | `PicturesView.vue` | `views/PicturesView.vue` | `Pictures/PicturesTable` | Картинки |
| 6 | `DictionariesView.vue` | `views/DictionariesView.vue` | `Dictionaries/DictionariesTable` | Словари |
| 7 | `NewsView.vue` | `views/NewsView.vue` | `News/NewsTable` | Новости (с пагинацией 35/page) |
| 8 | `NewsTemplatesView.vue` | `views/NewsTemplatesView.vue` | `NewsTemplates/NewsTemplatesTable` | Шаблоны |
| 9 | `ListeningHistoryView.vue` | `views/ListeningHistoryView.vue` | `ListeningHistory/ListeningHistoryTable` | История |
| 10 | `PublicSettingsView.vue` | `views/PublicSettingsView.vue` | `PublicSettings/PublicSettingsTable` | Публичные настройки |
| 11 | `PromotionsView.vue` | `views/PromotionsView.vue` | `Promotions/PromotionsTable` | Акции |
| 12 | `PropertiesView.vue` | `views/PropertiesView.vue` | `Properties/PropertiesTable` | 150 параметров рендера |
| 13 | `TariffsView.vue` | `views/TariffsView.vue` | `Tariffs/TariffsTable` | Тарифы |
| 14 | `SubscriptionsView.vue` | `views/SubscriptionsView.vue` | `Subscriptions/SubscriptionsTable` | Подписки |
| 15 | `SiteUsersView.vue` | `views/SiteUsersView.vue` | `SiteUsers/SiteUsersTable` | Пользователи сайта |
| 16 | `SitePlaylistsView.vue` | `views/SitePlaylistsView.vue` | `SitePlaylists/SitePlaylistsTable` | Плейлисты (read-only) |
| 17 | `ShareLinksView.vue` | `views/ShareLinksView.vue` | `ShareLinks/ShareLinksTable` | Share-ссылки |
| 18 | `SongEditorView.vue` | `views/SongEditorView.vue` | `SongEditor/SongEdit` (nested) | **Сложный** — открывает SongEdit.vue |
| 19 | `PlayerView.vue` | `views/PlayerView.vue` | `Player.vue` | Плеер (headless + обычный) |
| 20 | `PublishView.vue` | `views/PublishView.vue` | `Publish/PublishTable` | Публикации |
| 21 | `PublishTemplatesView.vue` | `views/PublishTemplatesView.vue` | — | Шаблоны публикаций |
| 22 | `SponsrSyncView.vue` | `views/SponsrSyncView.vue` | `SponsrSync/SponsrSyncView` | Sync с Sponsr |
| 23 | `SyncView.vue` | `views/SyncView.vue` | `Sync/SyncTable` | **Двух-БД sync** (manual) |
| 24 | `ProcessesView.vue` | `views/ProcessesView.vue` | `Processes/ProcessesTable` | Async-очередь (admin) |
| 25 | `StemJobsView.vue` | `views/StemJobsView.vue` | `StemJobs/StemJobsTable` | Премиум StemJob |
| 26 | `StatsView.vue` | `views/StatsView.vue` | `Stats/StatsView` (без Table) | **Сложный** — dashboard |

## Архитектура

### View-контейнер (типичный)

```vue
<template>
  <div class="songstable">
    <SongsTable />
  </div>
</template>

<script>
import SongsTable from '../components/Songs/SongsTable.vue'

export default {
  name: 'SongView',
  components: { SongsTable },
}
</script>
```

**Никакой логики** — только импорт table-компонента.

### Когда View содержит логику

Исключения — `SongEditorView` (открывает `SongEdit.vue`),
`PlayerView` (headless-режим), `StatsView` (dashboard).

## Hot paths

- **Каждое открытие** view = mount table = fetch `digest`.
- **Bulk-operations** — в `SongsView` (sync, push, pull, delete).

## Связь

- [vuex-patterns.md](vuex-patterns.md) — общие паттерны.
- [store-songs.md](store-songs.md) — главный Vuex store.
- [karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md) — backend API.

## Changelog

- **Pass 429** (2026-09-09): Initial. Автор: agent (Karaoke).