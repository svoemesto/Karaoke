# Frontend Vuex patterns (webvue3)

> **Домен**: system (cross-cutting)
> **Компонента**: общие паттерны Vuex-модулей в `webvue3`. Подробные
> state/mutations/actions каждого конкретного store — Pass 343+.

## Назначение

`webvue3` использует **Vuex** для state management. Каждая
entity-компонента имеет свой store.js. Здесь — **общие паттерны**,
применимые ко всем store.js. Конкретные store описаны минимально
(перечисление + ссылки), детали — TODO Pass 343+.

## Каталог Vuex stores (28 штук)

### Основные (по entity)

| Store | Файл | Что |
|---|---|---|
| `Albums` | `components/Albums/store.js` | Альбомы |
| `Authors` | `components/Authors/store.js` | Авторы |
| `Chat` | `components/Chat/store.js` | Чат (admin ↔ public) |
| `Dictionaries` | `components/Dictionaries/store.js` | Словари |
| `ListeningHistory` | `components/ListeningHistory/store.js` | История прослушиваний |
| `News` | `components/News/store.js` | Новости |
| `Pictures` | `components/Pictures/store.js` | Картинки |
| `Processes` | `components/Processes/store.js` | Async-очередь |
| `Promotions` | `components/Promotions/store.js` | Акции |
| `Properties` | `components/Properties/store.js` | Karaoke.properties (UI) |
| `PublicSettings` | `components/PublicSettings/store.js` | Публичные настройки |
| `Publish` | `components/Publish/store.js` | Публикации |
| `ShareLinks` | `components/ShareLinks/store.js` | Share-ссылки |
| `SitePlaylists` | `components/SitePlaylists/store.js` | Плейлисты |
| `SiteUsers` | `components/SiteUsers/store.js` | Пользователи сайта |
| `SongEditor` | `components/SongEditor/store.js` | Редактор песни |
| `Songs` | `components/Songs/store.js` | **Главный** — список песен |
| `SponsrSync` | `components/SponsrSync/store.js` | Sync с Sponsr |
| `Stats` | `components/Stats/store.js` | Статистика |
| `StemJobs` | `components/StemJobs/store.js` | Премиум StemJob |
| `Subscriptions` | `components/Subscriptions/store.js` | Подписки |
| `Sync` | `components/Sync/store.js` | Двух-БД sync |
| `Tariffs` | `components/Tariffs/store.js` | Тарифы |

### Общие (Common)

| Store | Файл | Что |
|---|---|---|
| `Common` | `components/Common/store.js` | Общий state |
| `FileExplorer` | `components/Common/FileExplorer/store.js` | Файловый менеджер |
| `HealthReport` | `components/Common/HealthReport/store.js` | HealthReport UI |
| `Monitor` | `components/Common/Monitor/store.js` | Мониторинг |
| `SmartCopy` | `components/Common/SmartCopy/store.js` | Smart copy UI |

## Паттерн: Vuex модуль (по `Songs/store.js`)

### State

```javascript
state: () => ({
    toSync: false,            // флаг синхронизации с сервером
    songs: [],                // текущая страница (SongDTO[])
    countRows: 0,             // total count для пагинации
    songsTableCurrentPage: 1, // current page (1-based, persistent в localStorage)
    selectedRows: [],         // ID выбранных строк (для bulk-операций)
})
```

### Mutations

Только **sync-присвоение state**. Никаких async-операций:

```javascript
SET_SONGS(state, payload) { state.songs = payload.songs; }
SET_COUNT_ROWS(state, payload) { state.countRows = payload.countRows; }
SET_CURRENT_PAGE(state, payload) { state.songsTableCurrentPage = payload.page; }
SET_SELECTED_ROWS(state, payload) { state.selectedRows = payload.ids; }
```

### Actions

Async (XHR, dispatch), `commit` после получения ответа:

```javascript
fetchSongs({ commit, state }, filterParams) {
    return promisedXMLHttpRequest({
        url: '/api/songs/list',
        data: filterParams,
    }).then(response => {
        commit('SET_SONGS', { songs: response.songs });
        commit('SET_COUNT_ROWS', { countRows: response.countRows });
    });
}
```

### HR-запросы — очередь с лимитом

`HR_MAX_CONCURRENT=3` — лимит одновременных запросов `getHealthReportList`
(см. [health-report.md](../../domains/health/components/health-report.md)).
Без лимита быстрая пагинация перегружает сервер MinIO-запросами.

### SSE подписка

`subscribeToSse()` — подписка на `recordChange`/`recordDelete`. **Не**
локальный рендеринг после мутации, а сервер-driven обновления.

## Паттерн: Filter stores

Каждая entity-компонента имеет **отдельный `filter/store.js`** для
хранения фильтров таблицы (поиск, сортировка, страница, выбранные
строки).

**Persistence**: `setWebvueProp` / `getWebvueProp` (server-side
key/value, переживает F5). Это server-side, а не `localStorage`.

## Общие архитектурные решения

### Решение 1: Vuex vs Pinia

**Vuex 4.x** — используется. Pinia НЕ принят (legacy).

### Решение 2: Sync mutations + Async actions

Стандартный Vuex паттерн. Mutations НЕ async (иначе devtools не
работают).

### Решение 3: SSE-driven updates

После мутации (`POST /api/songs/update`) сервер отправляет SSE
`recordChange` всем UI-вкладкам. UI реагирует на SSE, **не**
перезагружает данные вручную. Это экономит XHR.

### Решение 4: persistent filters через server-side key/value

Не `localStorage` (per-browser), а server-side key/value через
`setWebvueProp`/`getWebvueProp` — синхронизирует между
устройствами.

### Решение 5: HR-очередь

HealthReport-запросы — самые дорогие (MinIO round-trip). Лимит
`HR_MAX_CONCURRENT=3` защищает сервер.

## Известные TODO

- [ ] **Каждый из 28 stores** — детальный state/mutations/actions.
- [ ] **`HR_MAX_CONCURRENT=3`** — где определён, можно ли настроить.
- [ ] **`setWebvueProp`** — какой backend (KaraokeProperties?
      отдельная таблица?).
- [ ] **Подписка на SSE** — где инициализируется (root level?
      per-page?).
- [ ] **Error handling** — как store реагирует на 500?
- [ ] **Vuex devtools** — настроены ли для prod?

## Связь с другими компонентами

- **SSE** ([sse domain](../../domains/sse/domain.md)) — events.
- **HealthReport** ([health-report.md](../../domains/health/components/health-report.md)) —
  HR-очередь.
- **Storage** ([storage domain](../../domains/storage/domain.md)) —
  pictures/albums/etc.

## Changelog

- **Pass 349** (2026-09-09): Initial overview. Прецедент: задачи
  #65, #69 + общий Knowledge-аудит. Автор: agent (Karaoke).