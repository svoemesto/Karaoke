# Composable: useNewsUnreadCount

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useNewsUnreadCount.js`
> — module-level singleton для inline-бейджа непрочитанных новостей.

## Файл

`karaoke-public/src/composables/useNewsUnreadCount.js` (295 строк)

## Назначение

**Module-level singleton** для inline-бейджа непрочитанных новостей в
**шапке сайта**. Заменил floating `NewsBell.vue`
(specs/257-header-news-unread-badge).

Та же polling-логика (45 сек `/api/public/news/since`), suppression
на `/news`/`/player`/`/share`, opt-out для анонимов — но consumer
получает `count` напрямую для отображения inline `<span>` рядом с
ссылкой «Новости».

## State (module-level)

```javascript
const count = ref(0)                       // 0..50 (cap бэкенда)
const pollingPaused = ref(false)           // на скрытых маршрутах / анонимах
const lastSeenId = parseInt(localStorage.getItem('km_news_last_seen_id') || '0')
```

**Computed**:

- `badgeText` — `''` (скрыт), `'1'..'49'`, `'50+'` (cap).
- `ariaLabel` — `''` или `'{count} непрочитанных {pluralForm}'`.
- `showBadge` — `count > 0`.

## API

```javascript
markRead()            // sync — записать lastSeenId = max(items.id), count = 0
reset()               // Promise — для тестов/debug
```

## Constants

```javascript
const POLL_INTERVAL_MS = 45000           // 45 секунд
const STORAGE_KEY = 'km_news_last_seen_id'
const COUNT_CAP = 50
const HIDDEN_ROUTE_NAMES = new Set(['news', 'player', 'share'])
const NEWS_SHOWN_ROUTES = new Set(['/'])    // R-009
```

## Lifecycle

- **Init ленивый** — при первом вызове `useNewsUnreadCount()`.
- **При отсутствии `localStorage`** (новый пользователь, cleared
  storage, новое устройство) — один доп. запрос
  `/api/public/news?page=0&size=1` и запись max id в localStorage
  (FR-013 / Clarification Q1, option B — silent reset).
- **Network error** → silent fallback на поведение `NewsBell`
  (count до 50, явно принято в A-001 / FR-013).

## Smart reset (US4, итерация 2)

- **При переходе на `/news`** — `markRead()` в route watcher **ДО**
  HTTP-запроса `NewsView` (FR-015). Бейдж исчезает в ту же секунду.
- **На `/`** (где `<LatestNewsSection>` показывает последние новости)
  — **auto-read через 10 сек** (FR-016). Стартует при `count 0 → >0`
  или при входе на `/` с `count > 0`. Отменяется при уходе с `/`,
  при переходе на `/news`, при `count → 0`. Перезапускается при
  новых новостях.

## Pass 52 protection (defense in depth)

Для **анонимов** (нет `km_auth_token`) polling **НЕ** запускается
— иначе 3.5 MB ответа `/since?id=0` каждые 45 сек × N вкладок (см.
`PublicNewsController.kt:60-66`).

## Связь

- **News** ([entities-catalog.md#news](../../domains/catalog/components/entities-catalog.md#news)) — entity.
- **PublicNewsController** ([karaoke-web/public-controllers.md](../../domains/karaoke-web/components/public-controllers.md)) — `/api/public/news/since`.
- **PollingCache** ([web-caches.md](../../domains/caching/components/web-caches.md)) — TTL=60s для `/since`.
- **useAuth** ([composable-use-auth.md](composable-use-auth.md)) — токен.

## Changelog

- **Pass 387** (2026-09-09): Initial. Автор: agent (Karaoke).