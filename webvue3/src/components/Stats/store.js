import { promisedXMLHttpRequest } from '../../lib/utils'

// Потолок ожидания ответа статистики. Без него «зависший» endpoint (например,
// `/api/stats/countries`, который резолвит до 150 IP через внешний GeoIP)
// держал вкладку в вечной загрузке: XHR никогда не завершается → `catch` не
// срабатывает → флаг isLoading не сбрасывается, и пользователь видит пустой
// блок со спиннером (OP #184, spec 478, FR-006/FR-007).
const STATS_REQUEST_TIMEOUT_MS = 15_000

/**
 * Обернуть промис таймаутом: по истечении `timeoutMs` промис отклоняется,
 * чтобы UI вышел из состояния загрузки (сам XHR при этом не отменяется).
 *
 * @param {Promise} promise — оборачиваемый промис
 * @param {String} url — адрес (для текста ошибки)
 * @param {Number} timeoutMs — потолок ожидания в мс
 * @returns {Promise} промис с таймаутом
 */
function withTimeout(promise, url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error(`Stats request timeout ${timeoutMs}ms: ${url}`)),
      timeoutMs,
    )
    promise.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        clearTimeout(timer)
        reject(error)
      },
    )
  })
}

// Хелпер GET → JSON. promisedXMLHttpRequest не сериализует params в query-string для GET
// (устоявшийся квирк проекта), поэтому все параметры собираем в URL вручную.
function getJson(url, timeoutMs = STATS_REQUEST_TIMEOUT_MS) {
  const request = promisedXMLHttpRequest({ method: 'GET', url, params: {} }).then((data) =>
    JSON.parse(data),
  )
  return withTimeout(request, url, timeoutMs)
}

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    statsTarget: 'local',
    statsDays: 30,
    // Сводка
    summary: null,
    summaryIsLoading: false,
    // Временной ряд. mode: 'all' | 'type' | 'detail'
    timeSeries: [],
    timeSeriesMode: 'all',
    timeSeriesIsLoading: false,
    // Разбивки
    byType: [],
    channels: [],
    detailed: [],
    breakdownIsLoading: false,
    // География + внешние источники
    countries: [],
    referrers: [],
    geoIsLoading: false,
    // Топ пользователей
    topUsers: [],
    topUsersTotalCount: 0,
    topUsersIsLoading: false,
    // Drill-down событий пользователя
    userEvents: [],
    userEventsTotalCount: 0,
    userEventsIsLoading: false,
    // Топ песен
    statsBySong: [],
    statsBySongIsLoading: false,
    statsBySongTotalCount: 0,
    // Топ песен, которые реально слушают в онлайн-плеере (≥75% или до конца)
    topListened: [],
    topListenedIsLoading: false,
    topListenedTotalCount: 0,
    // Drill-down событий песни
    songEvents: [],
    songEventsTotalCount: 0,
    songEventsIsLoading: false,
    // Лог событий
    webEvents: [],
    webEventsIsLoading: false,
    webEventsTotalCount: 0,
    // Монетизация (подписки)
    monetizationSummary: null,
    monetizationSummaryIsLoading: false,
    monetizationTopSongs: [],
    monetizationTopSongsIsLoading: false,
    // Текущие страницы пагинации в StatsView. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    statsBySongPage: 1,
    webEventsPage: 1,
    topListenedPage: 1,
    // Timestamp последней загрузки каждой вкладки (ms). Используется для 60s TTL на фронте
    // (см. spec 362 — fix Element not found в StatsView). Хранится в store (singleton),
    // чтобы timestamps сохранялись между mount/unmount компонента StatsView.
    lastLoadedAt: {},
  },
  getters: {
    getStatsTarget(state) {
      return state.statsTarget
    },
    getStatsDays(state) {
      return state.statsDays
    },
    getStatsSummary(state) {
      return state.summary
    },
    getStatsSummaryIsLoading(state) {
      return state.summaryIsLoading
    },
    getStatsTimeSeries(state) {
      return state.timeSeries
    },
    getStatsTimeSeriesMode(state) {
      return state.timeSeriesMode
    },
    getStatsTimeSeriesIsLoading(state) {
      return state.timeSeriesIsLoading
    },
    getStatsByType(state) {
      return state.byType
    },
    getStatsChannels(state) {
      return state.channels
    },
    getStatsDetailed(state) {
      return state.detailed
    },
    getStatsBreakdownIsLoading(state) {
      return state.breakdownIsLoading
    },
    getStatsCountries(state) {
      return state.countries
    },
    getStatsReferrers(state) {
      return state.referrers
    },
    getStatsGeoIsLoading(state) {
      return state.geoIsLoading
    },
    getStatsTopUsers(state) {
      return state.topUsers
    },
    getStatsTopUsersTotalCount(state) {
      return state.topUsersTotalCount
    },
    getStatsTopUsersIsLoading(state) {
      return state.topUsersIsLoading
    },
    getStatsUserEvents(state) {
      return state.userEvents
    },
    getStatsUserEventsTotalCount(state) {
      return state.userEventsTotalCount
    },
    getStatsUserEventsIsLoading(state) {
      return state.userEventsIsLoading
    },
    getStatsBySong(state) {
      return state.statsBySong
    },
    getStatsBySongIsLoading(state) {
      return state.statsBySongIsLoading
    },
    getStatsBySongTotalCount(state) {
      return state.statsBySongTotalCount
    },
    getStatsSongEvents(state) {
      return state.songEvents
    },
    getStatsSongEventsTotalCount(state) {
      return state.songEventsTotalCount
    },
    getStatsSongEventsIsLoading(state) {
      return state.songEventsIsLoading
    },
    getWebEvents(state) {
      return state.webEvents
    },
    getWebEventsIsLoading(state) {
      return state.webEventsIsLoading
    },
    getWebEventsTotalCount(state) {
      return state.webEventsTotalCount
    },
    getMonetizationSummary(state) {
      return state.monetizationSummary
    },
    getMonetizationSummaryIsLoading(state) {
      return state.monetizationSummaryIsLoading
    },
    getMonetizationTopSongs(state) {
      return state.monetizationTopSongs
    },
    getMonetizationTopSongsIsLoading(state) {
      return state.monetizationTopSongsIsLoading
    },
    getStatsBySongPage(state) {
      return state.statsBySongPage
    },
    getWebEventsPage(state) {
      return state.webEventsPage
    },
    getTopListened(state) {
      return state.topListened
    },
    getTopListenedTotalCount(state) {
      return state.topListenedTotalCount
    },
    getTopListenedIsLoading(state) {
      return state.topListenedIsLoading
    },
    getTopListenedPage(state) {
      return state.topListenedPage
    },
    /**
     * Возвращает timestamp последней загрузки вкладки `tab` (ms).
     * 0 — вкладка ещё не загружалась в этой сессии.
     * @param {Number} tab — индекс вкладки (0..7)
     * @returns {Number}
     */
    getLastLoadedAt: (state) => (tab) => state.lastLoadedAt[tab] || 0,
  },
  mutations: {
    setStatsTarget(state, target) {
      state.statsTarget = target
    },
    setStatsDays(state, days) {
      state.statsDays = days
    },
    setStatsSummary(state, v) {
      state.summary = v
    },
    setStatsSummaryIsLoading(state, v) {
      state.summaryIsLoading = v
    },
    setStatsTimeSeries(state, v) {
      state.timeSeries = v
    },
    setStatsTimeSeriesMode(state, v) {
      state.timeSeriesMode = v
    },
    setStatsTimeSeriesIsLoading(state, v) {
      state.timeSeriesIsLoading = v
    },
    setStatsByType(state, v) {
      state.byType = v
    },
    setStatsChannels(state, v) {
      state.channels = v
    },
    setStatsDetailed(state, v) {
      state.detailed = v
    },
    setStatsBreakdownIsLoading(state, v) {
      state.breakdownIsLoading = v
    },
    setStatsCountries(state, v) {
      state.countries = v
    },
    setStatsReferrers(state, v) {
      state.referrers = v
    },
    setStatsGeoIsLoading(state, v) {
      state.geoIsLoading = v
    },
    setStatsTopUsers(state, v) {
      state.topUsers = v
    },
    setStatsTopUsersTotalCount(state, v) {
      state.topUsersTotalCount = v
    },
    setStatsTopUsersIsLoading(state, v) {
      state.topUsersIsLoading = v
    },
    setStatsUserEvents(state, v) {
      state.userEvents = v
    },
    setStatsUserEventsTotalCount(state, v) {
      state.userEventsTotalCount = v
    },
    setStatsUserEventsIsLoading(state, v) {
      state.userEventsIsLoading = v
    },
    setStatsBySong(state, data) {
      state.statsBySong = data
    },
    setStatsBySongIsLoading(state, v) {
      state.statsBySongIsLoading = v
    },
    setStatsBySongTotalCount(state, v) {
      state.statsBySongTotalCount = v
    },
    setStatsSongEvents(state, v) {
      state.songEvents = v
    },
    setStatsSongEventsTotalCount(state, v) {
      state.songEventsTotalCount = v
    },
    setStatsSongEventsIsLoading(state, v) {
      state.songEventsIsLoading = v
    },
    setWebEvents(state, data) {
      state.webEvents = data
    },
    setWebEventsIsLoading(state, v) {
      state.webEventsIsLoading = v
    },
    setWebEventsTotalCount(state, v) {
      state.webEventsTotalCount = v
    },
    setMonetizationSummary(state, v) {
      state.monetizationSummary = v
    },
    setMonetizationSummaryIsLoading(state, v) {
      state.monetizationSummaryIsLoading = v
    },
    setMonetizationTopSongs(state, v) {
      state.monetizationTopSongs = v
    },
    setMonetizationTopSongsIsLoading(state, v) {
      state.monetizationTopSongsIsLoading = v
    },
    setStatsBySongPage(state, v) {
      state.statsBySongPage = v
    },
    setWebEventsPage(state, v) {
      state.webEventsPage = v
    },
    setTopListened(state, data) {
      state.topListened = data
    },
    setTopListenedIsLoading(state, v) {
      state.topListenedIsLoading = v
    },
    setTopListenedTotalCount(state, v) {
      state.topListenedTotalCount = v
    },
    setTopListenedPage(state, v) {
      state.topListenedPage = v
    },
    /**
     * Записать timestamp последней загрузки вкладки.
     * @param {Object} state
     * @param {Object} payload — { tab: Number, ts: Number }
     */
    setLastLoadedAt(state, { tab, ts }) {
      state.lastLoadedAt = { ...state.lastLoadedAt, [tab]: ts }
    },
  },
  actions: {
    setStatsTarget(ctx, target) {
      ctx.commit('setStatsTarget', target)
    },
    setStatsDays(ctx, days) {
      ctx.commit('setStatsDays', days)
    },

    // Возвращает Promise<Boolean>: true — данные загружены, false — ошибка/таймаут.
    // Нужно вкладкам (StatsView.loadDataForActiveTab), чтобы взводить TTL-кеш
    // только на действительно загруженной вкладке (OP #184, spec 478, FR-004).
    loadStatsSummary(ctx) {
      ctx.commit('setStatsSummaryIsLoading', true)
      return getJson(`/api/stats/summary?target=${ctx.state.statsTarget}`)
        .then((r) => {
          ctx.commit('setStatsSummary', r.summary)
          ctx.commit('setStatsSummaryIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsSummaryIsLoading', false)
          return false
        })
    },
    loadStatsTimeSeries(ctx, { mode } = {}) {
      const m = mode !== undefined ? mode : ctx.state.timeSeriesMode
      ctx.commit('setStatsTimeSeriesMode', m)
      ctx.commit('setStatsTimeSeriesIsLoading', true)
      return getJson(
        `/api/stats/timeseries?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}&mode=${m}`,
      )
        .then((r) => {
          ctx.commit('setStatsTimeSeries', r.items)
          ctx.commit('setStatsTimeSeriesIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsTimeSeriesIsLoading', false)
          return false
        })
    },
    loadStatsBreakdown(ctx) {
      ctx.commit('setStatsBreakdownIsLoading', true)
      // Endpoint'ы вкладки грузятся НЕЗАВИСИМО: таймаут/ошибка одного не должен
      // стирать данные остальных и не должен оставлять вкладку в вечной загрузке
      // (OP #184, spec 478, FR-006). На ошибке прежние данные сохраняются.
      const load = (url, commitName) =>
        getJson(url)
          .then((r) => {
            ctx.commit(commitName, r.items || [])
            return true
          })
          .catch((e) => {
            console.warn('[Stats] breakdown endpoint failed:', url, e)
            return false
          })
      return Promise.allSettled([
        load(
          `/api/stats/by-type?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}`,
          'setStatsByType',
        ),
        load(`/api/stats/channels?target=${ctx.state.statsTarget}`, 'setStatsChannels'),
        load(
          `/api/stats/by-detail?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}`,
          'setStatsDetailed',
        ),
      ]).then((settled) => {
        ctx.commit('setStatsBreakdownIsLoading', false)
        return settled.some((r) => r.status === 'fulfilled' && r.value === true)
      })
    },
    loadStatsGeo(ctx) {
      ctx.commit('setStatsGeoIsLoading', true)
      // independent-загрузка (см. комментарий выше) + таймаут: «География» не
      // должна висеть вечно, если страны не отвечают, а внешние источники при
      // этом доступны (OP #184, spec 478, FR-007).
      const load = (url, commitName) =>
        getJson(url)
          .then((r) => {
            ctx.commit(commitName, r.items || [])
            return true
          })
          .catch((e) => {
            console.warn('[Stats] geo endpoint failed:', url, e)
            return false
          })
      return Promise.allSettled([
        load(`/api/stats/countries?target=${ctx.state.statsTarget}`, 'setStatsCountries'),
        load(`/api/stats/referrers?target=${ctx.state.statsTarget}`, 'setStatsReferrers'),
      ]).then((settled) => {
        ctx.commit('setStatsGeoIsLoading', false)
        return settled.some((r) => r.status === 'fulfilled' && r.value === true)
      })
    },
    loadStatsTopUsers(ctx, { page = 1, pageSize = 50 } = {}) {
      ctx.commit('setStatsTopUsersIsLoading', true)
      return getJson(
        `/api/stats/top-users?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          ctx.commit('setStatsTopUsers', r.items)
          ctx.commit('setStatsTopUsersTotalCount', r.totalCount)
          ctx.commit('setStatsTopUsersIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsTopUsersIsLoading', false)
          return false
        })
    },
    // Drill-down по пользователю: залогиненный (siteUserId>0) ЛИБО аноним (anonId).
    loadStatsUserEvents(ctx, { siteUserId = 0, anonId = '', page = 1, pageSize = 50 }) {
      ctx.commit('setStatsUserEventsIsLoading', true)
      let url = `/api/stats/user-events?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`
      if (siteUserId) url += `&siteUserId=${siteUserId}`
      if (anonId) url += `&anonId=${encodeURIComponent(anonId)}`
      getJson(url)
        .then((r) => {
          ctx.commit('setStatsUserEvents', r.items)
          ctx.commit('setStatsUserEventsTotalCount', r.totalCount)
          ctx.commit('setStatsUserEventsIsLoading', false)
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsUserEventsIsLoading', false)
        })
    },
    loadStatsBySong(ctx, { page = 1, pageSize = 50 } = {}) {
      ctx.commit('setStatsBySongIsLoading', true)
      return getJson(
        `/api/stats/by-song?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          ctx.commit('setStatsBySong', r.items)
          ctx.commit('setStatsBySongTotalCount', r.totalCount)
          ctx.commit('setStatsBySongIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsBySongIsLoading', false)
          return false
        })
    },
    // Drill-down по песне: все события конкретной песни (клик по строке «Топ песен»).
    loadStatsSongEvents(ctx, { songId, page = 1, pageSize = 2000 } = {}) {
      ctx.commit('setStatsSongEventsIsLoading', true)
      const url = `/api/stats/song-events?target=${ctx.state.statsTarget}&songId=${songId}&page=${page}&pageSize=${pageSize}`
      getJson(url)
        .then((r) => {
          ctx.commit('setStatsSongEvents', r.items)
          ctx.commit('setStatsSongEventsTotalCount', r.totalCount)
          ctx.commit('setStatsSongEventsIsLoading', false)
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setStatsSongEventsIsLoading', false)
        })
    },
    loadWebEvents(ctx, { page = 1, pageSize = 50, eventType = '', days = 0 } = {}) {
      ctx.commit('setWebEventsIsLoading', true)
      let url = `/api/webevents?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`
      if (eventType) url += `&eventType=${eventType}`
      if (days) url += `&days=${days}`
      return getJson(url)
        .then((r) => {
          ctx.commit('setWebEvents', r.items)
          ctx.commit('setWebEventsTotalCount', r.totalCount)
          ctx.commit('setWebEventsIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setWebEventsIsLoading', false)
          return false
        })
    },
    loadMonetizationSummary(ctx) {
      ctx.commit('setMonetizationSummaryIsLoading', true)
      return getJson(`/api/stats/monetization?target=${ctx.state.statsTarget}`)
        .then((r) => {
          ctx.commit('setMonetizationSummary', r.summary)
          ctx.commit('setMonetizationSummaryIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setMonetizationSummaryIsLoading', false)
          return false
        })
    },
    loadMonetizationTopSongs(ctx, { limit = 20 } = {}) {
      ctx.commit('setMonetizationTopSongsIsLoading', true)
      getJson(`/api/stats/monetization/top-songs?target=${ctx.state.statsTarget}&limit=${limit}`)
        .then((r) => {
          ctx.commit('setMonetizationTopSongs', r.items)
          ctx.commit('setMonetizationTopSongsIsLoading', false)
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setMonetizationTopSongsIsLoading', false)
        })
    },
    // Топ песен, которые реально слушают в онлайн-плеере (≥75% или до конца).
    // Метрика «дослушали» = (progress='75' OR ended), сортировка по числу таких событий DESC.
    loadTopListened(ctx, { page = 1, pageSize = 50 } = {}) {
      ctx.commit('setTopListenedIsLoading', true)
      return getJson(
        `/api/stats/top-listened?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          ctx.commit('setTopListened', r.items)
          ctx.commit('setTopListenedTotalCount', r.totalCount)
          ctx.commit('setTopListenedIsLoading', false)
          return true
        })
        .catch((e) => {
          console.log(e)
          ctx.commit('setTopListenedIsLoading', false)
          return false
        })
    },
  },
}
