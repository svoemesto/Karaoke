import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { statsRequestGuard } from './request-guard.js'

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
 * Текст ошибки загрузки данных статистики (BR-465, аудит #336, пакет PKG-S3-33).
 *
 * @param {any} e — исключение/ошибка отклонённого промиса.
 * @param {string} what — что именно не удалось загрузить.
 * @returns {string} текст для показа пользователю.
 */
function statsErrorText(e, what) {
  const detail = e && e.message ? e.message : String(e)
  return `Не удалось загрузить ${what}: ${detail}`
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
    // BR-465 (аудит #336, пакет PKG-S3-33, OpenProject #405): отказ drill-down —
    // отдельное состояние. Пустой список событий и «не удалось загрузить» — разные
    // утверждения о данных.
    userEventsError: null,
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
    // BR-465: см. userEventsError.
    songEventsError: null,
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
    // BR-465: ошибка drill-down по пользователю (null — ошибки нет).
    getStatsUserEventsError(state) {
      return state.userEventsError
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
    // BR-465: ошибка drill-down по песне (null — ошибки нет).
    getStatsSongEventsError(state) {
      return state.songEventsError
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
    // BR-465: текст ошибки drill-down по пользователю.
    setStatsUserEventsError(state, v) {
      state.userEventsError = v
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
    // BR-465: текст ошибки drill-down по песне.
    setStatsSongEventsError(state, v) {
      state.songEventsError = v
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
      // BR-459: фиксируем поколение запроса — ответ применяем, только если он последний.
      const seq = statsRequestGuard.begin('summary')
      ctx.commit('setStatsSummaryIsLoading', true)
      return getJson(`/api/stats/summary?target=${ctx.state.statsTarget}`)
        .then((r) => {
          if (!statsRequestGuard.isCurrent('summary', seq)) return false
          ctx.commit('setStatsSummary', r.summary)
          ctx.commit('setStatsSummaryIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('summary', seq)) return false
          console.log(e)
          ctx.commit('setStatsSummaryIsLoading', false)
          return false
        })
    },
    loadStatsTimeSeries(ctx, { mode } = {}) {
      // BR-459: при быстрой смене периода/режима ответ старого запроса не должен
      // перетирать данные нового (поколение на ключ 'timeseries').
      const seq = statsRequestGuard.begin('timeseries')
      const m = mode !== undefined ? mode : ctx.state.timeSeriesMode
      ctx.commit('setStatsTimeSeriesMode', m)
      ctx.commit('setStatsTimeSeriesIsLoading', true)
      return getJson(
        `/api/stats/timeseries?target=${ctx.state.statsTarget}&days=${ctx.state.statsDays}&mode=${m}`,
      )
        .then((r) => {
          if (!statsRequestGuard.isCurrent('timeseries', seq)) return false
          ctx.commit('setStatsTimeSeries', r.items)
          ctx.commit('setStatsTimeSeriesIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('timeseries', seq)) return false
          console.log(e)
          ctx.commit('setStatsTimeSeriesIsLoading', false)
          return false
        })
    },
    loadStatsBreakdown(ctx) {
      // BR-459: поколение на всю вкладку «Разбивки» — ответы трёх endpoint'ов
      // применяются, только если с момента запуска не было новой загрузки.
      const seq = statsRequestGuard.begin('breakdown')
      ctx.commit('setStatsBreakdownIsLoading', true)
      // Endpoint'ы вкладки грузятся НЕЗАВИСИМО: таймаут/ошибка одного не должен
      // стирать данные остальных и не должен оставлять вкладку в вечной загрузке
      // (OP #184, spec 478, FR-006). На ошибке прежние данные сохраняются.
      const load = (url, commitName) =>
        getJson(url)
          .then((r) => {
            if (!statsRequestGuard.isCurrent('breakdown', seq)) return false
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
      // BR-459: именно этот action давал подмену данных при переключении БД —
      // медленный ответ «Географии» старой БД приходил после смены target.
      const seq = statsRequestGuard.begin('geo')
      ctx.commit('setStatsGeoIsLoading', true)
      // independent-загрузка (см. комментарий выше) + таймаут: «География» не
      // должна висеть вечно, если страны не отвечают, а внешние источники при
      // этом доступны (OP #184, spec 478, FR-007).
      const load = (url, commitName) =>
        getJson(url)
          .then((r) => {
            if (!statsRequestGuard.isCurrent('geo', seq)) return false
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
      const seq = statsRequestGuard.begin('top-users')
      ctx.commit('setStatsTopUsersIsLoading', true)
      return getJson(
        `/api/stats/top-users?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          if (!statsRequestGuard.isCurrent('top-users', seq)) return false
          ctx.commit('setStatsTopUsers', r.items)
          ctx.commit('setStatsTopUsersTotalCount', r.totalCount)
          ctx.commit('setStatsTopUsersIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('top-users', seq)) return false
          console.log(e)
          ctx.commit('setStatsTopUsersIsLoading', false)
          return false
        })
    },
    // Drill-down по пользователю: залогиненный (siteUserId>0) ЛИБО аноним (anonId).
    //
    // BR-465 (аудит #336, пакет PKG-S3-33, OpenProject #405):
    //   - состояние очищается ДО запроса (прежние события принадлежали ДРУГОМУ
    //     пользователю: модалка показывала события A под именем B);
    //   - действие возвращает промис (`Promise<Boolean>`) — вызывающий может
    //     отличить успех от отказа;
    //   - отказ кладётся в отдельное состояние ошибки, а не проглатывается.
    loadStatsUserEvents(ctx, { siteUserId = 0, anonId = '', page = 1, pageSize = 50 }) {
      const seq = statsRequestGuard.begin('user-events')
      ctx.commit('setStatsUserEventsIsLoading', true)
      ctx.commit('setStatsUserEventsError', null)
      ctx.commit('setStatsUserEvents', [])
      ctx.commit('setStatsUserEventsTotalCount', 0)
      let url = `/api/stats/user-events?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`
      if (siteUserId) url += `&siteUserId=${siteUserId}`
      if (anonId) url += `&anonId=${encodeURIComponent(anonId)}`
      return getJson(url)
        .then((r) => {
          if (!statsRequestGuard.isCurrent('user-events', seq)) return false
          ctx.commit('setStatsUserEvents', r.items)
          ctx.commit('setStatsUserEventsTotalCount', r.totalCount)
          ctx.commit('setStatsUserEventsIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('user-events', seq)) return false
          console.log(e)
          ctx.commit('setStatsUserEventsIsLoading', false)
          ctx.commit('setStatsUserEventsError', statsErrorText(e, 'события пользователя'))
          return false
        })
    },
    loadStatsBySong(ctx, { page = 1, pageSize = 50 } = {}) {
      const seq = statsRequestGuard.begin('by-song')
      ctx.commit('setStatsBySongIsLoading', true)
      return getJson(
        `/api/stats/by-song?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          if (!statsRequestGuard.isCurrent('by-song', seq)) return false
          ctx.commit('setStatsBySong', r.items)
          ctx.commit('setStatsBySongTotalCount', r.totalCount)
          ctx.commit('setStatsBySongIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('by-song', seq)) return false
          console.log(e)
          ctx.commit('setStatsBySongIsLoading', false)
          return false
        })
    },
    // Drill-down по песне: все события конкретной песни (клик по строке «Топ песен»).
    //
    // BR-465: см. loadStatsUserEvents — очистка состояния до запроса, промис
    // вовне, отдельное состояние ошибки.
    loadStatsSongEvents(ctx, { songId, page = 1, pageSize = 2000 } = {}) {
      const seq = statsRequestGuard.begin('song-events')
      ctx.commit('setStatsSongEventsIsLoading', true)
      ctx.commit('setStatsSongEventsError', null)
      ctx.commit('setStatsSongEvents', [])
      ctx.commit('setStatsSongEventsTotalCount', 0)
      const url = `/api/stats/song-events?target=${ctx.state.statsTarget}&songId=${songId}&page=${page}&pageSize=${pageSize}`
      return getJson(url)
        .then((r) => {
          if (!statsRequestGuard.isCurrent('song-events', seq)) return false
          ctx.commit('setStatsSongEvents', r.items)
          ctx.commit('setStatsSongEventsTotalCount', r.totalCount)
          ctx.commit('setStatsSongEventsIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('song-events', seq)) return false
          console.log(e)
          ctx.commit('setStatsSongEventsIsLoading', false)
          ctx.commit('setStatsSongEventsError', statsErrorText(e, 'события песни'))
          return false
        })
    },
    loadWebEvents(ctx, { page = 1, pageSize = 50, eventType = '', days = 0 } = {}) {
      const seq = statsRequestGuard.begin('webevents')
      ctx.commit('setWebEventsIsLoading', true)
      let url = `/api/webevents?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`
      if (eventType) url += `&eventType=${eventType}`
      if (days) url += `&days=${days}`
      return getJson(url)
        .then((r) => {
          if (!statsRequestGuard.isCurrent('webevents', seq)) return false
          ctx.commit('setWebEvents', r.items)
          ctx.commit('setWebEventsTotalCount', r.totalCount)
          ctx.commit('setWebEventsIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('webevents', seq)) return false
          console.log(e)
          ctx.commit('setWebEventsIsLoading', false)
          return false
        })
    },
    loadMonetizationSummary(ctx) {
      const seq = statsRequestGuard.begin('monetization')
      ctx.commit('setMonetizationSummaryIsLoading', true)
      return getJson(`/api/stats/monetization?target=${ctx.state.statsTarget}`)
        .then((r) => {
          if (!statsRequestGuard.isCurrent('monetization', seq)) return false
          ctx.commit('setMonetizationSummary', r.summary)
          ctx.commit('setMonetizationSummaryIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('monetization', seq)) return false
          console.log(e)
          ctx.commit('setMonetizationSummaryIsLoading', false)
          return false
        })
    },
    loadMonetizationTopSongs(ctx, { limit = 20 } = {}) {
      // BR-460: action был объявлен, но нигде не вызывался — `state.monetizationTopSongs`
      // всегда оставался пустым, и блок «Топ песен по подпискам» вечно показывал
      // «Пока нет оплаченных подписок». Возвращаем Promise<Boolean>, чтобы TTL
      // вкладки взводился по факту загрузки (контракт loadDataForActiveTab).
      const seq = statsRequestGuard.begin('monetization-top-songs')
      ctx.commit('setMonetizationTopSongsIsLoading', true)
      return getJson(
        `/api/stats/monetization/top-songs?target=${ctx.state.statsTarget}&limit=${limit}`,
      )
        .then((r) => {
          if (!statsRequestGuard.isCurrent('monetization-top-songs', seq)) return false
          ctx.commit('setMonetizationTopSongs', r.items)
          ctx.commit('setMonetizationTopSongsIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('monetization-top-songs', seq)) return false
          console.log(e)
          ctx.commit('setMonetizationTopSongsIsLoading', false)
          return false
        })
    },
    // Топ песен, которые реально слушают в онлайн-плеере (≥75% или до конца).
    // Метрика «дослушали» = (progress='75' OR ended), сортировка по числу таких событий DESC.
    loadTopListened(ctx, { page = 1, pageSize = 50 } = {}) {
      const seq = statsRequestGuard.begin('top-listened')
      ctx.commit('setTopListenedIsLoading', true)
      return getJson(
        `/api/stats/top-listened?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`,
      )
        .then((r) => {
          if (!statsRequestGuard.isCurrent('top-listened', seq)) return false
          ctx.commit('setTopListened', r.items)
          ctx.commit('setTopListenedTotalCount', r.totalCount)
          ctx.commit('setTopListenedIsLoading', false)
          return true
        })
        .catch((e) => {
          if (!statsRequestGuard.isCurrent('top-listened', seq)) return false
          console.log(e)
          ctx.commit('setTopListenedIsLoading', false)
          return false
        })
    },
  },
}
