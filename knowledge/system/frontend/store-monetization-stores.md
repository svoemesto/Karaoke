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
- **`POST /api/subscriptions/digest`** (admin view)
- **`/api/stemjobs/list`** / `/delete` / `/stop`

## Связь

## Ошибки операций и валидация цены (пакет PKG-S2-18)

Аудит #336, BR-461 (тарифы) и BR-458 (подписки).

**Tariffs.** Мутации `createTariff`/`saveTariff`/`deleteTariff` не имели `.catch`:
при 4xx/5xx промис отклонялся, `loadTariffsList` не вызывался, сообщения пользователю
не было, а таблица продолжала показывать отредактированные значения как сохранённые
в прод-БД (деньги). Теперь каждая мутация возвращает `Promise<Boolean>`, пишет текст
ошибки в `tariffsError` (баннер в `TariffsTable.vue`) и не выдаёт отказ за успех;
`TariffsTable.save()` откатывает `editable` к подтверждённому снимку, `create()`
сбрасывает форму только после успеха. Цена проверяется `isValidTariffPrice()` до
отправки: раньше `canCreate` пропускал пустую строку (`'' >= 0` истинно), запрос
уходил с `priceRub=` и получал 400 молча.

**Subscriptions.** Таблица пагинировала клиентом одну серверную страницу: `b-pagination`
менял `currentPage`, watcher писал его в store, но запрос не уходил — страницы 2+ были
пусты. Плюс размер страницы уходил как `perPage`, а контроллер ждёт `pageSize`. Теперь
пагинация серверная: `reload()`/смена страницы/фильтр отправляют `page` и `pageSize`,
`b-table` не режет строки повторно, а смена `target` очищает дайджест до загрузки.

### Известные ловушки

- `promisedXMLHttpRequest` отклоняет промис при не-2xx; отсутствие `.catch` в мутации
  означает молчаливый отказ и «оптимистичное» состояние UI.
- `isValidTariffPrice` отвергает `null` и `''`: `Number(null)` и `Number('')` дают `0`,
  поэтому тип и пустая строка проверяются явно.
- Серверная пагинация и `b-table` с `:per-page` несовместимы: это двойная нарезка,
  страница 2+ окажется пустой.

- **Monetization** ([monetization domain](../../domains/monetization/domain.md)) —
  PriceTariff, PromoRule, Subscription, StemJob.
- **Two-DB sync** ([two-db-sync.md](../../domains/processing/components/two-db-sync.md)) —
  sync'ятся.
- **Target-aware запросы** ([target-request-guard.md](target-request-guard.md)) —
  почему `promoRulesListTarget` (БД показанного списка) главнее текущего селекта:
  BR-407, OpenProject #367.

## Changelog

- **Pass 396** (2026-09-09): Initial. Автор: agent (Karaoke).
