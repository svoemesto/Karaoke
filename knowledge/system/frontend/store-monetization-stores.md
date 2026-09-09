# Vuex stores: Monetization (4 stores)

> **Домен**: system (frontend)
> **Компонента**: 4 Vuex stores для монетизации — все используют
> единый паттерн `target: 'local'|'remote'`.

## Файлы (4 stores)

| Store | Файл | Строк | Что |
|---|---|---|---|
| `Tariffs` | `webvue3/.../Tariffs/store.js` | 86 | Тарифы подписки (`tbl_price_tariffs`) |
| `Promotions` | `webvue3/.../Promotions/store.js` | 85 | Акции (`tbl_promo_rules`) |
| `Subscriptions` | `webvue3/.../Subscriptions/store.js` | 84 | Подписки (`tbl_subscriptions`) |
| `StemJobs` | `webvue3/.../StemJobs/store.js` | 86 | (уже есть — [store-stem-jobs.md](store-stem-jobs.md)) |

## Общий паттерн

Все 4 stores используют единый паттерн:

```javascript
state: {
    <name>List: [],                    // или digest
    <name>IsLoading: false,
    <name>Target: 'local',            // 'local' | 'remote' (для syncable entities)
    <name>TableCurrentPage: 1,        // persistent
}
```

## `target` — что значит

`tariffsTarget`, `promoRulesTarget`, `subscriptionsTarget` — все
`local` | `remote`, как и у `siteUsersTarget` (Pass 386).

**Реальные тарифы/промо правятся на прод-БД** (`target=remote`), т.к.
**платёжный конвейер `karaoke-web` читает их оттуда**. Локальная БД —
для теста/разработки.

**Subscriptions** — JOIN к `tbl_site_users` / `tbl_songs` /
`tbl_price_tariffs` делается на бэкенде **одним батчем** (см.
AGENTS.md «Синхронизация LOCAL↔SERVER — критичные паттерны
производительности»).

## Hot paths

- **`/api/tariffs/list`** / **`/create`** / **`/update`** / **`/delete`**
- **`/api/promorules/list`** / CRUD
- **`/api/subscriptions/list`** (admin view)
- **`/api/stemjobs/list`** / `/delete` / `/stop`

## Связь

- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  PriceTariff, PromoRule, Subscription, StemJob.
- **Two-DB sync** ([two-db-sync.md](../../domains/processing/components/two-db-sync.md)) —
  sync'ятся.

## Changelog

- **Pass 396** (2026-09-09): Initial. Автор: agent (Karaoke).