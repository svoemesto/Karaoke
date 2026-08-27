# Contract: `useNewsUnreadCount` composable

> Файл: `karaoke-public/src/composables/useNewsUnreadCount.js`
> Назначение: singleton-state с polling-ом `/api/public/news/since`, computed-полями для бейджа, и **smart reset логикой** (immediate на `/news`, auto-read через 10 сек на `/` — US4).

## Public API

```js
import { useNewsUnreadCount } from '@/composables/useNewsUnreadCount'

const {
  count,         // Ref<number>: 0..50 (cap бэкенда)
  badgeText,     // ComputedRef<string>: '' | 'N' | '50+'
  ariaLabel,     // ComputedRef<string>: '' | 'N непрочитанных {plural}'
  showBadge,     // ComputedRef<boolean>: count > 0
  pollingPaused, // Ref<boolean>: true на скрытых маршрутах / для анонимов
  markRead,      // () => void: записать lastSeenId = max(items.id), count = 0 (FR-015/FR-016, sync)
  reset,         // () => Promise<void>: ручной reset (для тестов / debug)
} = useNewsUnreadCount()
```

### Поля

| Поле | Тип | Описание |
|------|-----|----------|
| `count` | `Ref<number>` | Текущее число непрочитанных новостей (от последнего успешного polling-а). `0` по умолчанию. |
| `badgeText` | `ComputedRef<string>` | Готовый текст для бейджа: `''` (скрыт), `'1'..'49'` (число), `'50+'` (cap). |
| `ariaLabel` | `ComputedRef<string>` | Готовый текст для screen reader: `''` если бейдж скрыт, иначе `'{count} непрочитанных {pluralForm}'`. |
| `showBadge` | `ComputedRef<boolean>` | `count > 0`. Удобно для `v-if=".showBadge"`. |
| `pollingPaused` | `Ref<boolean>` | `true` если polling приостановлен (на скрытом маршруте или для анонима). Полезно для debug. |
| `markRead` | `() => void` (sync) | Записать `localStorage['km_news_last_seen_id'] = String(Math.max(...items.map(i => i.id)))`, `count.value = 0`, отменить `autoReadTimer`. **Идемпотентна** — вызов при пустых `items` это no-op (без записи в localStorage). Используется internally: (a) immediate reset при route change на `/news`; (b) auto-read timer через 10 сек на `/`; (c) может быть вызван из внешнего кода при необходимости. |
| `reset` | `() => Promise<void>` | Сбрасывает `localStorage['km_news_last_seen_id']` и перезапускает polling (используется в тестах; в продакшене не нужен). |

### Module-level state (не экспортируется)

- `items: Ref<News[]>` — `items` из последнего успешного `/since`-ответа. Нужен для `markRead()` (R-011).
- `pollTimer: setInterval handle | null`
- `autoReadTimer: setTimeout handle | null` — 10-секундный таймер для US4 FR-016.
- `initialized: boolean`
- `currentRoute: RouteLocationNormalized | null` — captured в `init()` для watcher на count.

### Константы

```js
const POLL_INTERVAL_MS = 45000       // polling каждые 45 сек (FR-005)
const STORAGE_KEY = 'km_news_last_seen_id'
const COUNT_CAP = 50                  // defensive clamp на count
const HIDDEN_ROUTE_NAMES = new Set(['news', 'player', 'share'])
const NEWS_SHOWN_ROUTES = new Set(['/'])  // страницы с видимыми последними новостями (FR-016, R-009)
const AUTO_READ_DELAY_MS = 10000     // 10 секунд для auto-read (FR-016)
```

## Поведение (state machine)

### Инициализация (lazy, при первом вызове `useNewsUnreadCount()`)

1. Прочитать `localStorage.getItem('km_news_last_seen_id')`:
   - `null` (первый визит, cleared storage, новое устройство):
     - Выполнить `GET /api/public/news?page=0&size=1`.
     - Если успешно (`body.items[0].id` существует) → записать в `localStorage['km_news_last_seen_id']` = `String(body.items[0].id)`.
     - Если ошибка → НЕ писать в `localStorage`, продолжить с `lastSeenId = 0` (fallback на текущее поведение `NewsBell`).
   - существует → `lastSeenId = Number(value)` (или `0` если `NaN`).
2. Определить `isAuthenticated = !!localStorage.getItem('km_auth_token')`.
3. Если `isAuthenticated`:
   - Запустить polling (см. ниже).
   - Если нет — `pollingPaused = true`, polling не запускается.

### Polling loop

```
setInterval(async () => {
  if (pollingPaused) return
  try {
    const data = await fetchNewsSince(lastSeenId)
    if (data && typeof data.count === 'number') {
      count.value = Math.min(Math.max(0, Math.floor(data.count)), COUNT_CAP)
    }
    if (data && Array.isArray(data.items)) {
      items.value = data.items.slice(0, COUNT_CAP)
    }
  } catch (e) {
    // keep last value (count и items оба)
  }
}, 45000)
```

### Route change handler (обновлено с US4)

Watch `$route.name` через `useRoute()`:

```
applyRouteState(route) {
  const hidden = isHiddenRoute(route)
  const isNewsShown = NEWS_SHOWN_ROUTES.has(route?.path)

  pollingPaused.value = hidden

  if (hidden) {
    // Polling pause
    stopPolling()

    if (route?.name === 'news') {
      // US4 FR-015: immediate reset на /news (в ту же секунду, до HTTP-запроса NewsView)
      markRead()
    } else {
      cancelAutoRead()
    }
  } else {
    if (isAuthenticated()) {
      pollOnce()
      startPolling()
    }
    if (isNewsShown && count.value > 0) {
      startAutoRead()  // US4 FR-016
    } else {
      cancelAutoRead()
    }
  }
}
```

### Auto-read timer (US4 FR-016)

```
startAutoRead() {
  cancelAutoRead()  // idempotent
  if (!NEWS_SHOWN_ROUTES.has(currentRoute?.path)) return
  if (count.value === 0) return
  if (!isAuthenticated()) return  // защита: только для залогиненных
  autoReadTimer = setTimeout(() => {
    markRead()        // записать lastSeenId, count = 0
    autoReadTimer = null
  }, AUTO_READ_DELAY_MS)
}

cancelAutoRead() {
  if (autoReadTimer) {
    clearTimeout(autoReadTimer)
    autoReadTimer = null
  }
}
```

**Триггеры запуска таймера**:
1. `count` переходит 0 → >0 (watch на count) — если `currentRoute.path === '/'`.
2. Route change на `/` (пользователь только что зашёл на главную) — если `count > 0`.
3. `count` увеличивается пока таймер активен (новые новости поверх существующих) — cancel предыдущего, start нового.

**Триггеры отмены**:
1. Route change с `/` на любой другой маршрут.
2. Route change на `/news` (выполняется immediate reset вместо auto-read).
3. `count → 0` (новости пропали — например, после reset).
4. Initial mount на `/` с `count === 0` (нечего «прочитывать»).

### `markRead()` (sync, US4)

```js
function markRead() {
  cancelAutoRead()
  if (!items.value.length) return  // fallback: не знаем maxId, NewsView.markAllSeen() подхватит после загрузки
  const maxId = Math.max(...items.value.map((i) => Number(i.id) || 0))
  if (maxId <= 0) return
  try {
    localStorage.setItem(STORAGE_KEY, String(maxId))
  } catch (_) {
    // localStorage недоступен — fallback
  }
  count.value = 0  // реактивно скрывает бейдж
}
```

**Идемпотентна**. Вызывается:
- Internally в route watcher при `route.name === 'news'` (FR-015).
- Internally в `autoReadTimer` callback (FR-016).
- Может быть вызвана из внешнего кода (например, другой view, который показывает новости).

### `reset()` (только для тестов / debug)

```js
async function reset() {
  localStorage.removeItem('km_news_last_seen_id')
  count.value = 0
  items.value = []
  cancelAutoRead()
  await pollOnce()  // polling после reset, не silent reset
}
```

## Side effects

| Действие | Когда | Effect |
|----------|-------|--------|
| `localStorage.setItem('km_news_last_seen_id', ...)` | (a) silent-reset (FR-013); (b) `markRead()` (FR-015/FR-016) | Запись максимального id |
| HTTP `GET /api/public/news/since?id={lastSeenId}` | Каждые 45 сек для залогиненных | Обновление `count` и `items` |
| HTTP `GET /api/public/news?page=0&size=1` | Один раз при первом монтировании (если `lastSeenId` отсутствует) | Silent reset (FR-013) |
| `setTimeout(markRead, 10000)` | На `/` при `count > 0` | Auto-read через 10 сек (FR-016) |

## Lifecycle / ownership

- **Singleton**: state живёт на module-level (как `useAuth`, `useCart`).
- **Инициализация**: lazy — только при первом вызове `useNewsUnreadCount()`.
- **Cleanup**: при `beforeUnmount` последнего consumer-а — `clearInterval` + `clearTimeout`. В SPA с `vue-router` обычно consumer не размонтируется (хедер всегда на странице), но defensive cleanup важен для тестов с `unmount()`.

## Guarantees (контрактные обязательства)

1. **Thread-safe (single-thread JS)**: setInterval/setTimeout callback не выполняются параллельно — если `poll()` зависнет, следующий тик пропустится (это OK, polling не догонит).
2. **No memory leak**: оба таймера (polling interval + auto-read timeout) очищаются при unmount, при смене маршрута на скрытый и при необходимости.
3. **No state corruption on error**: `try/catch` гарантирует, что `count` и `items` остаются в последнем известном состоянии при сетевых ошибках.
4. **Reactive**: `count`, `items`, `badgeText`, `ariaLabel`, `showBadge` — Vue `ref` / `computed`, обновляются реактивно (любой компонент, использующий `badgeText.value`, перерендерится при изменении).
5. **No global side effects on import**: composable не делает HTTP-запросов на module-load — только при первом вызове функции.
6. **markRead() идемпотентна**: можно вызывать несколько раз подряд без побочных эффектов (после первого вызова `items` могут быть устаревшими, но `count = 0` уже).
7. **Route change на `/news` → immediate reset в ту же секунду**: `applyRouteState()` синхронно вызывает `markRead()` ДО того как `NewsView` начнёт HTTP-запрос. Бейдж исчезает в том же фрейме, что и смена маршрута.

## Test surface (для ручного тестирования, см. quickstart.md)

### Базовые сценарии (без изменений)

| Тест | Ожидание |
|------|----------|
| Чистый `localStorage` (первый визит) | `lastSeenId` устанавливается, `count = 0`, бейдж скрыт |
| Залогиненный с 3 непрочитанными | `count = 3`, `badgeText = '3'`, `ariaLabel = '3 непрочитанных новости'` |
| Залогиненный с 50 непрочитанными | `count = 50`, `badgeText = '50+'`, `ariaLabel = '50+ непрочитанных новостей'` |
| Анонимный | `pollingPaused = true`, `count = 0`, бейдж скрыт |
| Backend 5xx | `count` остаётся в последнем известном значении |

### US4 сценарии (новые)

| Тест | Ожидание |
|------|----------|
| Клик по ссылке «Новости» с `count = 3` на `/` | Бейдж исчезает в течение < 100 мс (до HTTP `/api/public/news`). `localStorage.km_news_last_seen_id` обновлён. |
| На `/` с `count = 0`, polling → `count = 5` | Бейдж появляется «5», стартует 10-сек таймер. Через 10 сек — `count = 0`, бейдж скрывается. |
| На `/`, таймер 5 сек из 10, navigation на `/zakroma` | Таймер отменяется. На `/zakroma` polling продолжается, бейдж остаётся (если `count > 0`). |
| На `/`, таймер 5 сек из 10, polling → `count = 7` (новые новости) | Таймер перезапускается на 10 сек с момента нового polling. |
| На `/`, навигация на `/news` | Immediate reset (FR-015), таймер отменяется. |
| Открытие `/news` через прямой URL | Same: immediate reset в `applyRouteState()`. |
| Возврат с `/news` на `/` | `count = 0` (уже сброшено), таймер НЕ запускается. |

> **Уточнение pluralize для 1**: число «1» склоняется особым образом — «1 непрочитанная новость» (женский род, им.п.). Для `count >= 2` — общая форма «{count} непрочитанных {pluralForm}». Это расходится с упрощённым текстом в data-model.md и уточняется здесь.

## KDoc обязательство

Composable MUST содержать JSDoc-комментарий с `@see` ссылками на:
- `specs/257-header-news-unread-badge/spec.md`
- `livedocs/features/257-header-news-unread-badge.md`
- `livedocs/features/250-unify-site-header.md` (родительский header-context)

Это соответствует Constitution § VI FR-006.
