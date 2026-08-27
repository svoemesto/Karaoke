import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchNews, fetchNewsSince } from '../services/newsApi'

/**
 * Module-level singleton для inline-бейджа непрочитанных новостей в шапке сайта.
 * Заменил floating `NewsBell.vue` (specs/257-header-news-unread-badge): та же polling-логика
 * (45 сек `/api/public/news/since`, suppression на `/news`/`/player`/`/share`, opt-out для анонимов),
 * но consumer получает `count` напрямую для отображения inline `<span>` рядом с ссылкой «Новости».
 *
 * Поля:
 * - `count: Ref<number>` — текущее число непрочитанных (0..50, cap бэкенда).
 * - `badgeText: ComputedRef<string>` — `''` (скрыт), `'1'..'49'` (число), `'50+'` (cap).
 * - `ariaLabel: ComputedRef<string>` — `''` или `'{count} непрочитанных {pluralForm}'`.
 * - `showBadge: ComputedRef<boolean>` — `count > 0`.
 * - `pollingPaused: Ref<boolean>` — true на скрытых маршрутах / для анонимов.
 * - `markRead: () => void` (sync) — записать lastSeenId = max(items.id), count = 0 (US4 / FR-015, FR-016).
 * - `reset: () => Promise<void>` — для тестов/debug, в production UI не вызывается.
 *
 * Lifecycle:
 * - Init ленивый, при первом вызове `useNewsUnreadCount()`.
 * - При отсутствии `localStorage['km_news_last_seen_id']` (новый пользователь, cleared storage,
 *   новое устройство) делает один доп. запрос `/api/public/news?page=0&size=1` и записывает
 *   максимальный id в localStorage (FR-013 / Clarification Q1 2026-08-27, option B — silent reset).
 * - На network error silent reset'а — fallback на поведение `NewsBell` (count до 50), что явно
 *   принято в A-001 / FR-013.
 *
 * Smart reset (US4, итерация 2 — 2026-08-27):
 * - При переходе на `/news` immediate reset (FR-015) — `markRead()` в route watcher ДО HTTP-запроса
 *   `NewsView`. Бейдж исчезает в ту же секунду.
 * - На `/` (где `<LatestNewsSection>` показывает последние новости) — auto-read через 10 сек
 *   (FR-016). Стартует при `count 0 → >0` или при входе на `/` с `count > 0`. Отменяется при
 *   уходе с `/`, при переходе на `/news` (выполняется immediate), при `count → 0`. Перезапускается
 *   при новых новостях (`count > 0 → newCount > oldCount`).
 *
 * Pass 52 protection: для анонимов (нет `km_auth_token`) polling НЕ запускается — defense in depth
 * против 3.5 MB ответа `/since?id=0` каждые 45 сек × N вкладок (см. PublicNewsController.kt:60-66).
 *
 * @see specs/257-header-news-unread-badge/spec.md
 * @see specs/257-header-news-unread-badge/contracts/useNewsUnreadCount-composable.md
 * @see livedocs/features/250-unify-site-header.md (родительский header-context)
 */

const POLL_INTERVAL_MS = 45000
const STORAGE_KEY = 'km_news_last_seen_id'
const COUNT_CAP = 50
const HIDDEN_ROUTE_NAMES = new Set(['news', 'player', 'share'])
// Страницы, где пользователь «видит» последние новости (R-009). Пока только главная —
// ZakromaView не содержит <LatestNewsSection>. Расширяемо через добавление путей.
const NEWS_SHOWN_ROUTES = new Set(['/'])
const AUTO_READ_DELAY_MS = 10000

const count = ref(0)
const items = ref([]) // US4 / R-011: хранит items из последнего /since для markRead()
const pollingPaused = ref(false)
let pollTimer = null
let autoReadTimer = null
let initialized = false
let currentRoute = null

function readLastSeenId() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const n = Number(raw)
    return Number.isFinite(n) && n >= 0 ? n : 0
  } catch (_) {
    return 0
  }
}

function isAuthenticated() {
  try {
    return !!localStorage.getItem('km_auth_token')
  } catch (_) {
    return false
  }
}

function isHiddenRoute(route) {
  if (!route) return false
  if (HIDDEN_ROUTE_NAMES.has(route.name)) return true
  if (route.query && route.query.share === '1') return true
  return false
}

function isNewsShownRoute(route) {
  return !!(route && NEWS_SHOWN_ROUTES.has(route.path))
}

async function pollOnce() {
  try {
    const data = await fetchNewsSince(readLastSeenId())
    if (data && typeof data.count === 'number') {
      const capped = Math.min(Math.max(0, Math.floor(data.count)), COUNT_CAP)
      count.value = capped
    }
    if (data && Array.isArray(data.items)) {
      items.value = data.items.slice(0, COUNT_CAP)
    }
  } catch (_) {
    // Сетевой сбой (xhr.onerror) — оставляем count/items как есть: временная недоступность
    // не должна сбрасывать бейдж в 0, иначе пользователь подумает, что новостей нет
    // (Edge Cases в spec).
  }
}

async function silentReset() {
  try {
    const data = await fetchNews(0, 1)
    const first = data && data.items && data.items[0]
    if (first && typeof first.id === 'number') {
      localStorage.setItem(STORAGE_KEY, String(first.id))
    }
  } catch (_) {
    // Fallback: не пишем в localStorage, polling покажет NewsBell-поведение («50+» до первого
    // открытия /news). Явно допустимо по FR-013 / A-001.
  }
}

function startPolling() {
  if (pollTimer) return
  if (!isAuthenticated()) return
  pollTimer = setInterval(pollOnce, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function cancelAutoRead() {
  if (autoReadTimer) {
    clearTimeout(autoReadTimer)
    autoReadTimer = null
  }
}

// US4 / FR-015 + FR-016: sync сброс бейджа. Идемпотентна.
function markRead() {
  cancelAutoRead()
  if (!items.value.length) return // fallback: NewsView.markAllSeen() подхватит после загрузки
  let maxId = 0
  for (const it of items.value) {
    const id = Number(it && it.id) || 0
    if (id > maxId) maxId = id
  }
  if (maxId <= 0) return
  try {
    localStorage.setItem(STORAGE_KEY, String(maxId))
  } catch (_) {
    // localStorage недоступен — fallback
  }
  count.value = 0 // реактивно скрывает бейдж
}

function startAutoRead() {
  cancelAutoRead() // idempotent — рестарт таймера на полные 10 сек
  if (!isNewsShownRoute(currentRoute)) return
  if (count.value === 0) return
  if (!isAuthenticated()) return
  autoReadTimer = setTimeout(() => {
    markRead()
    autoReadTimer = null
  }, AUTO_READ_DELAY_MS)
}

function pluralize(n, forms) {
  const absN = Math.abs(n) % 100
  const n1 = absN % 10
  if (absN > 10 && absN < 20) return forms[2]
  if (n1 > 1 && n1 < 5) return forms[1]
  if (n1 === 1) return forms[0]
  return forms[2]
}

function applyRouteState(route) {
  const hidden = isHiddenRoute(route)
  const newsShown = isNewsShownRoute(route)

  pollingPaused.value = hidden

  if (hidden) {
    stopPolling()
    if (route && route.name === 'news') {
      // US4 / FR-015: immediate reset в ту же секунду, до HTTP-запроса NewsView.
      // Намеренно вызываем ДО stopPolling, хотя порядок не критичен (markRead sync).
      markRead()
    } else {
      cancelAutoRead()
    }
  } else {
    if (isAuthenticated()) {
      pollOnce()
      startPolling()
    }
    // US4 / FR-016: auto-read только на страницах с видимыми новостями.
    if (newsShown && count.value > 0) {
      startAutoRead()
    } else {
      cancelAutoRead()
    }
  }
}

function init(route) {
  if (initialized) return
  initialized = true
  currentRoute = route || null

  const startAfterReset = () => {
    if (currentRoute) applyRouteState(currentRoute)
    else if (isAuthenticated()) {
      pollOnce()
      startPolling()
    }
  }

  let needsReset = false
  try {
    needsReset = localStorage.getItem(STORAGE_KEY) === null
  } catch (_) {
    // localStorage недоступен — polling будет работать с lastSeenId=0 (NewsBell-фоллбэк).
  }

  if (needsReset) {
    silentReset().then(startAfterReset)
  } else {
    startAfterReset()
  }

  if (currentRoute) {
    watch(() => currentRoute.name, () => applyRouteState(currentRoute))
    watch(() => (currentRoute.query && currentRoute.query.share) || null, () => applyRouteState(currentRoute))
  }

  // US4 / FR-016: watch на count для триггера auto-read при появлении новых новостей.
  // Guard `oldCount === undefined` — игнорируем initial trigger (Vue watch по умолчанию не
  // вызывает callback при initial, но защищаемся явно на случай будущих изменений API).
  watch(count, (newCount, oldCount) => {
    if (oldCount === undefined) return
    if (newCount === 0) {
      cancelAutoRead()
    } else if (newCount > 0 && isNewsShownRoute(currentRoute)) {
      // Рестарт таймера на полные 10 сек (новые новости поверх существующих — US4 AC4).
      startAutoRead()
    }
  })
}

async function reset() {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (_) {
    // ignore
  }
  count.value = 0
  items.value = []
  cancelAutoRead()
  await pollOnce()
}

export function useNewsUnreadCount() {
  let route = null
  try {
    route = useRoute()
  } catch (_) {
    // Вызов вне setup() (например, в модульных тестах) — работаем без route watcher.
  }
  init(route)

  const badgeText = computed(() => {
    const c = count.value
    if (c === 0) return ''
    if (c >= COUNT_CAP) return '50+'
    return String(c)
  })

  const ariaLabel = computed(() => {
    const c = count.value
    if (c === 0) return ''
    return `${c} непрочитанных ${pluralize(c, ['новость', 'новости', 'новостей'])}`
  })

  const showBadge = computed(() => count.value > 0)

  return { count, badgeText, ariaLabel, showBadge, pollingPaused, markRead, reset }
}
