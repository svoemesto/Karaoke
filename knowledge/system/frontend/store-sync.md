# Vuex store: Sync

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Sync/store.js` — управление
> двух-БД sync.

## Файл

`webvue3/src/components/Sync/store.js`

## Назначение

Управление «Синхронизацией в 1 клик» (manual) + статус автозапуска
(`AutoOneClickSyncScheduler`, см. [schedulers.md](../../domains/processing/components/schedulers.md)).

## State

```javascript
state: {
    entities: [],                    // SyncTarget entities (см. SyncRegistry.all)
    entitiesIsLoading: false,        // флаг загрузки
    autoStatus: null,                // AutoOneClickSyncRun (in-memory history)
}
```

## `autoStatus` (DTO из spec 235)

```typescript
{
  enabled: bool,                 // autoOneClickSyncEnabled
  intervalMs: long,              // autoOneClickSyncIntervalMs
  initialDelayMs: long,
  lastRun: AutoOneClickSyncRunDto|null,  // последний тик
  last10: AutoOneClickSyncRunDto[],      // история (≤10)
  nextRunEstimate: ISO-8601|null         // расчёт
}
```

## Hot paths

- **`/api/sync/list`** — список entities.
- **`/api/sync/runOneClick`** — ручной запуск sync.
- **`/api/sync/autoStatus`** — polling статуса автозапуска.
- **`/api/sync/autoStart` / `/api/sync/autoStop`** — управление
  автозапуском.

## Связь

- **Two-DB sync** ([two-db-sync.md](../../domains/processing/components/two-db-sync.md)) —
  бэкенд.
- **AutoOneClickSyncScheduler** ([schedulers.md](../../domains/processing/components/schedulers.md)) —
  автозапуск.
- **Pass 341 P1** — spec 235.

## Changelog

- **Pass 382** (2026-09-09): Initial. Автор: agent (Karaoke).