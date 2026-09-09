# Vuex stores (remaining overview)

> **Домен**: system (frontend)
> **Компонента**: компактный каталог оставшихся 14 Vuex stores (помимо
> 8 detailed).

## Назначение

8 stores детально (Pass 349, 371, 382, 386):
[Songs](store-songs.md), [SongEditor](store-song-editor.md),
[Sync](store-sync.md), [Properties](store-properties.md),
[SiteUsers](store-site-users.md), [Stats](store-stats.md),
[Albums](store-albums.md), [Pictures](store-pictures.md),
[StemJobs](store-stem-jobs.md), [News](store-news.md),
[Dictionaries](store-dictionaries.md).

Здесь — **компактный обзор** оставшихся 14, с минимальным описанием
каждого. Детальные документы — TODO Pass 343+.

## Каталог оставшихся 14

| Store | Файл | Строк | Описание |
|---|---|---|---|
| `Authors` | `webvue3/.../Authors/store.js` | 102 | Авторы (аналог Albums) |
| `SitePlaylists` | `webvue3/.../SitePlaylists/store.js` | 91 | Плейлисты пользователей |
| `Subscriptions` | `webvue3/.../Subscriptions/store.js` | 84 | Подписки (admin view) |
| `Tariffs` | `webvue3/.../Tariffs/store.js` | 86 | Тарифы (admin view) |
| `Promotions` | `webvue3/.../Promotions/store.js` | 85 | Акции (admin view) |
| `ListeningHistory` | `webvue3/.../ListeningHistory/store.js` | ? | История прослушиваний |
| `ShareLinks` | `webvue3/.../ShareLinks/store.js` | ? | Share-ссылки (admin) |
| `Publish` | `webvue3/.../Publish/store.js` | ? | Публикации (admin) |
| `SponsrSync` | `webvue3/.../SponsrSync/store.js` | ? | Sync с Sponsr |
| `Common/FileExplorer` | `webvue3/.../Common/FileExplorer/store.js` | ? | Файловый менеджер |
| `Common/HealthReport` | `webvue3/.../Common/HealthReport/store.js` | ? | HealthReport UI |
| `Common/Monitor` | `webvue3/.../Common/Monitor/store.js` | ? | Мониторинг UI |
| `Common/SmartCopy` | `webvue3/.../Common/SmartCopy/store.js` | ? | Smart Copy UI |
| `Common` | `webvue3/.../Common/store.js` | ? | Общий state |

## Архитектура (общая)

Все следуют **единому паттерну** (см.
[vuex-patterns.md](vuex-patterns.md)):

- **State** — `digest`, `current`, `currentId`, `tableCurrentPage`,
  `target`.
- **Getters** — `getXxx` для каждого state поля.
- **Mutations** — sync-присвоение (только sync).
- **Actions** — `fetchXxx` async (XHR + commit).
- **`filter/store.js`** — отдельный store для фильтров таблицы.

## Связь с другими компонентами

- **Entity** — каждый store соответствует сущности в
  [entities-catalog.md](../../domains/catalog/components/entities-catalog.md).
- **SSE** — обновления через recordChange (см.
  [sse domain](../../domains/sse/domain.md)).
- **Two-DB sync** — `target` (`local`|`remote`) для syncable entities.

## Известные TODO

- [ ] **Каждый из 14** — детальный документ (Pass 343+).
- [ ] **Common/*** — описание общего state.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).