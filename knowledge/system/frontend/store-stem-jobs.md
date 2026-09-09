# Vuex store: StemJobs

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/StemJobs/store.js` — премиум
> фича «Создать минусовку» (админ-панель).

## Файл

`webvue3/src/components/StemJobs/store.js` (86 строк)

## State

```javascript
state: {
    stemJobs: [],
    stemJobsIsLoading: false,
    stemJobsTarget: 'remote',   // default
}
```

## Назначение

Премиум-фича «Создать минусовку из аудиофайла» — **админ-панель**
(управление заданиями пользователей публичного сайта).

**`stemJobsTarget`** (`'local'|'remote'`):
- `'remote'` (default) — реальные задания создают пользователи на
  **прод-сайте** (karaoke-web).
- `'local'` — для локальной отладки очереди (тот же паттерн, что
  `siteUsersTarget`/`chatTarget`).

## Hot paths

- **`/api/stemjobs/list`** — список.
- **`/api/stemjobs/{id}/delete`** — удалить.
- **`/api/stemjobs/{id}/stop`** — остановить.

## Связь

- **StemJob** ([remaining-models.md#stemjob](../../domains/catalog/components/remaining-models.md#stemjob)) —
  entity.
- **StemJobPollScheduler** ([schedulers.md](../../domains/processing/components/schedulers.md)) —
  обработка.
- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  premium-фича.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).