# Vuex store: Stats

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Stats/store.js` — админ-дашборд статистики.

## Файл

`webvue3/src/components/Stats/store.js` (498 строк, **самый длинный store**)

## State

```javascript
state: {
    statsTarget: 'local',              // 'local' | 'remote'
    statsDays: 30,                      // период для графиков
    summary: null,                       // сводка
    summaryIsLoading: false,
    timeSeries: [],                      // mode: 'all' | 'type' | 'detail'
    timeSeriesMode: 'all',
    timeSeriesIsLoading: false,
    byType: [],                          // разбивка по типам
    channels: [],                        // каналы (TG, VK, ...)
    detailed: [],                        // детали
    breakdownIsLoading: false,
    countries: [],                       // география (api.country.is)
    referrers: [],                       // referrers
    geoIsLoading: false,
    topUsers: [],                        // топ пользователей
    topUsersTotalCount: 0,
    topUsersIsLoading: false,
    userEvents: [],                      // drill-down событий
    userEventsTotalCount: 0,
    userEventsIsLoading: false,
    statsBySong: [],                     // топ песен
    statsBySongIsLoading: false,
    statsBySongTotalCount: 0,
    topListened: [],                     // топ реально слушаемых
    topListenedIsLoading: false,
    topListenedTotalCount: 0,
}
```

## Хелпер

```javascript
function getJson(url) {
  return promisedXMLHttpRequest({ method: 'GET', url, params: {} })
    .then((data) => JSON.parse(data));
}
```

`promisedXMLHttpRequest` **не** сериализует params в query-string для
GET (устоявшийся квирк проекта), поэтому все параметры собираем в URL
вручную.

## Hot paths

- **`/api/stats/summary`** — сводка.
- **`/api/stats/timeseries`** — временной ряд.
- **`/api/stats/bytype`** / **`/api/stats/channels`** — разбивки.
- **`/api/stats/countries`** — география (см. [GeoIp](../../integration/components/external-api-clients.md#geoipservice)).
- **`/api/stats/topusers`** / **`/api/stats/toplistened`** / **`/api/stats/statsBySong`**.

## Top listened (топ реально слушаемых)

`topListened` — **топ песен, которые реально слушают в онлайн-плеере**
(≥75% или до конца). Отличается от `statsBySong` (которое — по
event-ам).

## Domain Invariants

1. **`statsTarget`** — `local` | `remote` (см. SiteUsers / Sync
   аналогично).
2. **`statsDays`** — период для графиков.
3. **Top listened** считается на backend (сложная аналитика,
   `ListeningHistory` ≥75% play count).

## Связь

- **Stats** ([stats domain](../../domains/stats/domain.md)) — backend.
- **GeoIp** ([external-api-clients](../../integration/components/external-api-clients.md#geoipservice)) —
  countries.
- **ListeningHistory** ([entities-catalog.md#listeninghistory](../../domains/catalog/components/entities-catalog.md#listeninghistory)) —
  top listened.

## Changelog

- **Pass 386** (2026-09-09): Initial. Автор: agent (Karaoke).