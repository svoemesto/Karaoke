# Data Model: 257-header-news-unread-badge

> Phase 1 — модель данных для composable `useNewsUnreadCount`. UI-уровень, без новых таблиц БД.

## Entities (frontend state)

### 1. `useNewsUnreadCount` composable state

Module-level singleton state в `karaoke-public/src/composables/useNewsUnreadCount.js`:

| Поле | Тип | Initial | Описание |
|------|-----|---------|----------|
| `count` | `Ref<number>` | `0` | Текущее число непрочитанных новостей (от polling-а). `0..50` (cap бэкенда). |
| `items` | `Ref<News[]>` | `[]` | `items` из последнего успешного `/since`-ответа. Нужен для `markRead()` (R-011). |
| `pollingPaused` | `Ref<boolean>` | `false` | Приостановлен ли polling (на `/news`, `/player`, `/share`, для анонимов). |
| `lastPollAt` | `Ref<number \| null>` | `null` | Timestamp последнего успешного polling-а (для debugging / future metrics). |

Внутренние (не экспортируются) переменные:
- `pollTimer: setInterval handle | null`
- `autoReadTimer: setTimeout handle | null` (US4, FR-016)
- `initialized: boolean`
- `currentRoute: RouteLocationNormalized | null` (для watcher на count)
- `NEWS_SHOWN_ROUTES: Set<string>` (paths)

### 2. `localStorage` keys (existing, semantics уточнены)

| Key | Type | Writer | Reader | Описание |
|-----|------|--------|--------|----------|
| `km_news_last_seen_id` | `string` (число) | (a) `useNewsUnreadCount` при silent reset (FR-013); (b) `useNewsUnreadCount` при immediate reset на `/news` (FR-015); (c) `useNewsUnreadCount` при auto-read timer (FR-016); (d) `NewsView.markAllSeen()` existing — defense-in-depth | `useNewsUnreadCount` | Максимальный `id` новости, которую пользователь **уже видел**. Используется как `id` параметр `/api/public/news/since`. Запись идемпотентна (max id), несколько writers не конфликтуют. |
| `km_auth_token` | `string` | `useAuth.setSession()` | `useNewsUnreadCount` (для проверки `isAuthenticated`) | Существующий токен авторизации. Polling отключён при отсутствии (Pass 52 protection). |

### 3. Computed values (derived state)

| Computed | Зависимость | Возвращает |
|----------|-------------|------------|
| `badgeText` | `count` | `''` если `count === 0`, `String(count)` если `0 < count < 50`, `'50+'` если `count >= 50` |
| `ariaLabel` | `count` | `''` если `count === 0`, иначе `{count} непрочитанных {pluralForm(count)}` |
| `showBadge` | `count` | `count > 0` |

**Plural form function** (`pluralize(n, ['новость', 'новости', 'новостей'])`):
- `n % 10 === 1 && n % 100 !== 11` → `'новость'` (1, 21, 31, …, 101)
- `n % 10 ∈ [2,3,4] && n % 100 ∉ [12,13,14]` → `'новости'` (2-4, 22-24, …, 102-104)
- иначе → `'новостей'` (0, 5-20, 25-30, …, 50+)

Примеры `ariaLabel`:
- `count = 1` → `"1 непрочитанная новость"` (с предлогом «непрочитанная», женский род)
- `count = 3` → `"3 непрочитанных новости"` (предлог + родительный, затем им.п. мн.ч.)
- `count = 50` → `"50+ непрочитанных новостей"`

> **Уточнение формулировки aria-label**: используется шаблон `"{count} непрочитанных {pluralForm}"` (без согласования «непрочитанная/непрочитанных» с родом — это упрощает текст и сохраняет смысл для screen reader). Это совместимо с e2e сценарием: пользователь слышит «3 непрочитанных новости», понимает, что есть 3 новых.

## State transitions (обновлено с US4)

```
App startup
  ↓
[composable mount]
  ↓
Check localStorage 'km_news_last_seen_id'
  ├─ null → fetch /api/public/news?page=0&size=1 → write id to localStorage → set count = 0 (FR-013)
  └─ exists → read lastSeenId → continue
  ↓
Check isAuthenticated (km_auth_token exists)
  ├─ no → polling disabled, count stays 0
  └─ yes → start polling
  ↓
Every 45 sec:
  GET /api/public/news/since?id=<lastSeenId>
  → response.count → set count = response.count
  → response.items → set items = response.items (для markRead)
  → on error: keep last value (try/catch) — и count, и items
  ↓
[NEW, FR-015] On route change to /news (route.name === 'news'):
  if items.length > 0:
    write localStorage['km_news_last_seen_id'] = max(items.id)
  count.value = 0       ← реактивно скрывает бейдж в ту же секунду
  cancelAutoRead()       ← таймер не нужен
  stopPolling()          ← уже было
  ↓
[NEW, FR-016] On count transition 0 → >0:
  if currentRoute?.path === '/':
    start 10-sec setTimeout(performAutoRead)
  ↓
[NEW, FR-016] On route change to /:
  if count.value > 0:
    start 10-sec setTimeout(performAutoRead)
  else:
    cancelAutoRead()
  ↓
[NEW, FR-016] On route change AWAY from /:
  cancelAutoRead()
  ↓
[NEW, FR-016] On count transition > 0 → 0:
  cancelAutoRead()
  ↓
[NEW, FR-016] On count increment while timer active:
  cancel previous timer
  start new 10-sec setTimeout(performAutoRead)
  ↓
[NEW, FR-016] On setTimeout fire (performAutoRead):
  if items.length > 0:
    write localStorage['km_news_last_seen_id'] = max(items.id)
  count.value = 0
  cancelAutoRead() (timer self-cleanup)
  ↓
On route change to /player, /share, /news:
  stop polling, cancel auto-read
  ↓
On route change back to non-hidden, non-news-shown:
  resume polling (immediate poll + restart interval)
  ↓
On unmount of last consumer:
  clearInterval (defensive — обычно не срабатывает в SPA)
```

## Public API additions (US4)

| Поле/метод | Тип | Описание |
|------------|-----|----------|
| `markRead()` | `() => void` (sync) | Записать `max(items.id)` в `localStorage`, `count.value = 0`, отменить `autoReadTimer`. Используется internally immediate reset и auto-read. Внешний код может вызвать при необходимости (например, из других view, которые показывают новости). |

## Validation rules

| Rule | Где проверяется | Failure mode |
|------|----------------|--------------|
| `lastSeenId` ≥ 0 | composable internal | Если `Number(localStorage.getItem(...))` даёт `NaN` → fallback на `0` |
| `count` ∈ [0, 50] | после `pollOnce` | `Math.min(Math.max(0, Math.floor(data.count)), COUNT_CAP)` (defensive clamp) |
| `count` — integer | бэкенд enforced | `Math.floor(count)` для отображения |
| `isAuthenticated` — boolean | localStorage check | Отсутствие токена = `false` (не exception) |
| `items.length === 0` при `markRead()` | composable internal | `markRead()` без записи в localStorage (fallback на `NewsView.markAllSeen`) |

## Backend contract (reference, not new)

Бэкенд **не меняется**. Используются существующие endpoints:

- `GET /api/public/news/since?id=<long>` → `{count: number, items: Array<News>}` (cap `count ≤ 50`)
- `GET /api/public/news?page=0&size=1` → `{items: Array<News>, total: number, hasMore: boolean}` (для FR-013 — silent reset)

Оба endpoint-а описаны в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicNewsController.kt`.
