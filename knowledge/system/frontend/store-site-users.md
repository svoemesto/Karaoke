# Vuex store: SiteUsers

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/SiteUsers/store.js` —
> пользователи ПУБЛИЧНОГО сайта (НЕ админские).

## Файл

`webvue3/src/components/SiteUsers/store.js`

## State

```javascript
state: {
    siteUsersDigest: [],                // пользователи
    siteUsersDigestIsLoading: false,
    siteUserCurrent: undefined,         // текущий (для редактирования)
    siteUserSnapshot: undefined,        // snapshot для сравнения
    siteUserCurrentId: 0,
    siteUsersTarget: 'local',           // 'local' | 'remote'
    siteUserSubscriptions: [],          // подписки текущего
    siteUserSubscriptionsIsLoading: false,
    siteUsersTableCurrentPage: 1,       // persistent
}
```

## Hot paths

- **`/api/siteusers/list`** — список.
- **`/api/siteusers/getById`** — один.
- **`/api/siteusers/update`** — редактировать.
- **`/api/siteusers/{id}/subscriptions`** — подписки.

## Важно

**НЕ путать** с `components/Users` (tbl_users, админские логины
webvue3). Здесь — `tbl_site_users` (посетители сайта, см.
[identity domain](../../domains/identity/domain.md)).

`siteUsersTarget` (`'local'|'remote'`) — добавляется в каждый запрос
к бэкенду и заставляет `SiteUsersController` явно выбрать
`Connection.local()/remote()` — реальные посетители сайта
регистрируются на **боевой БД сервера**, а не в локальной dev-БД.

## Связь

- **Identity** ([identity domain](../../domains/identity/domain.md)) —
  `SiteUser`.
- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  `Subscription`.

## Changelog

- **Pass 382** (2026-09-09): Initial. Автор: agent (Karaoke).