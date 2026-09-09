# Frontend overview (webvue3 + karaoke-public)

> **Домен**: system (cross-cutting)
> **Компонента**: общий обзор frontend-стека. Детали по конкретным
> компонентам — TODO Pass 343.

## Ответственность

Два независимых Vue 3 + Vite приложения:

- **`webvue3`** — админка (admin SPA). Доступ через прямой вход
  (`permitAll()` в `SecurityConfig.kt`, без авторизации). Хранит
  состояние через Vuex-модули.
- **`karaoke-public`** — публичный сайт (для посетителей и зарегистрированных
  пользователей). Авторизация через `useAuth` + `useAuthBootstrap`.
  Хранит состояние через composables (`useCart`, `usePremiumModal`, ...).

**Архитектурное решение**: НЕ смешивать ответственности между admin и
public (Constitution V).

## webvue3 (admin)

### Структура

```
webvue3/src/
├── views/                  # 26 .vue страниц (Songs, Authors, ...)
├── components/<Entity>/    # 22 entity-модуля
│   ├── <Name>View.vue
│   ├── store.js           # Vuex-модуль для entity
│   └── filter/
│       └── store.js       # Vuex-модуль для фильтров (setWebvueProp)
├── router/                 # Vue Router
├── store/                  # Глобальный Vuex (index.js)
├── player/                 # KaraokePlayer.js (headless рендер)
├── lib/                    # sockjs-client, etc.
└── utils/                  # Helpers
```

### Vuex-модули (22)

Каждая entity-компонента имеет свой `store.js`:

`Albums`, `Authors`, `Chat`, `Dictionaries`, `ListeningHistory`, `News`,
`NewsTemplates`, `Pictures`, `Processes`, `Promotions`, `Properties`,
`PublicSettings`, `Publish`, `ShareLinks`, `SitePlaylists`, `SiteUsers`,
`SongEditor`, `Songs`, `SponsrSync`, `Stats`, `StemJobs`, `Subscriptions`,
`Sync`, `Tariffs`.

### Фильтры (паттерн)

`<Entity>/filter/store.js` — Vuex-модуль для хранения фильтров таблицы
(поиск, сортировка, страница, выбранные строки). Персистится через
`setWebvueProp`/`getWebvueProp` (server-side key/value, переживает F5).

### Routes

26 routes (одна на `<Entity>View`). Доступ через `/<entity>` URL.

### Хранилище

`webvue3/src/store/index.js` — глобальный Vuex (настройки UI, текущий
пользователь, модалки).

### KaraokePlayer

`webvue3/src/player/KaraokePlayer.js` — основной плеер. Используется в
`<PlayerView>`. Headless-режим (`?render=1`) для рендера через
Playwright (см. [processing/playwright-rendering](../../domains/processing/components/playwright-rendering.md)).

### Бандлер

Vite. `npm run build` → dist. Запускается через nginx (`webvue3`
контейнер, порт 7906).

## karaoke-public (public)

### Структура

```
karaoke-public/src/
├── views/                  # главная, плейлисты, личный кабинет
├── components/             # общие компоненты
├── composables/            # 18 composables
├── router/
├── store/
└── ...
```

### Composables (18)

| Composable | Что делает |
|---|---|
| `useAuth` | Авторизация (логин/регистрация/выход) |
| `useAuthBootstrap` | Bootstrap авторизации при загрузке |
| `useCart` | Корзина (CartItem) |
| `useDesign` | Classic/modern дизайн (`localStorage`) |
| `useEngagementTracking` | Tracking скролла/времени для вебвизора |
| `useKaraokeEditor` | Editor-функционал (загрузка маркеров) |
| `usePremiumModal` | Модалка «Стать Premium» |
| `usePlaylistPlayer` | Управление плеером в плейлисте |
| `+ ещё 10` | см. code-graph |

### Дизайн

CSS-переменные `--km-*`. Два дизайна: `classic` / `modern`, выбор в
`localStorage` (см. `useDesign`).

### Бандлер

Vite. `npm run build` → dist. Запускается через nginx (`karaoke-public`
контейнер, порт 7905).

## Связь между frontend и backend

| Frontend | Backend | API |
|---|---|---|
| `webvue3` | `karaoke-app` (admin) | `/api/...` |
| `karaoke-public` | `karaoke-web` (прод) | `/api/public/...` |

`webvue3` НЕ вызывает `karaoke-web` напрямую — только через
`karaoke-app`.

## Известные TODO (Pass 343+)

- [ ] **Каждый Vuex-модуль**: state, mutations, actions, getters.
- [ ] **`Songs/store.js`** — самый большой (~1000+ строк), есть
      пагинация, фильтры, bulk-actions.
- [ ] **Player.js API**: методы `_ready`, `_preroll`, `renderFrameAt(dt)`,
      `_forcedTime`, `_renderBackground` (см.
      [processing/playwright-rendering](../../domains/processing/components/playwright-rendering.md)).
- [ ] **18 composables**: каждый с описанием.
- [ ] **Bootstrap flow** для авторизации (`useAuthBootstrap.onLogin`).
- [ ] **Player gesture unlock** (`PlayerGestureUnlockService.kt` на
      karaoke-web).
- [ ] **Sampling/dedup** для событий (`SamplingFilter`, `EventsBuffer`).
- [ ] **i18n** — есть ли, или весь UI на русском?
- [ ] **CSS-переменные `--km-*`** — какие определены, как настраиваются.

## Код (физическая реализация)

### webvue3

- `webvue3/src/views/` — 26 .vue
- `webvue3/src/components/<Entity>/` — 22 директории × ~3 файла
  (view + store.js + filter/store.js)
- `webvue3/src/player/KaraokePlayer.js`
- `webvue3/src/router/`
- `webvue3/src/store/index.js`

### karaoke-public

- `karaoke-public/src/views/`
- `karaoke-public/src/composables/` — 18 файлов
- `karaoke-public/src/components/`

## Связанные компоненты

- [Caching / web-caches](../../domains/caching/components/web-caches.md) —
  `DedupCache` + `PollingCache` используются в karaoke-web.
- [SSE](../../domains/sse/domain.md) — real-time события
  (`recordChange`, `processWorkerState`).

## Changelog

- **Pass 341 P3b** (2026-09-09): Initial overview. Автор: agent (Karaoke).