import { promisedXMLHttpRequest } from '../../lib/utils'
import { createRequestGeneration } from '../../lib/target-request-guard.js'

// BR-383 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка истории —
// с поколением запроса, `finally` и текстом ошибки в state.
const listeningHistoryGeneration = createRequestGeneration()

// История прослушиваний пользователей ПУБЛИЧНОГО САЙТА (tbl_listening_history). Read-only
// глобальный список для админ-SPA. target ('local'|'remote') — как в SiteUsers. JOIN к
// tbl_songs / tbl_site_users делается на бэкенде одним батчем. SKIP-фильтр на чтении
// наследуется из публичного `ListeningHistory.getForUser` — критично для того, чтобы
// правообладатель-удалённые песни не появлялись в админке (см. AGENTS.md, инвариант
// «Тег SKIP: показывается заглушка»).
/**
 * Vuex-модуль «История прослушиваний» (tbl_listening_history). Глобальный read-only
 * список для админ-SPA.
 *
 * Хранит:
 * - `listeningHistoryDigest` — массив строк текущей страницы.
 * - `listeningHistoryDigestTotalCount` — общее число строк (без пагинации).
 * - `listeningHistoryTarget` — 'local' | 'remote'.
 * - `listeningHistoryTableCurrentPage` — персистентность страницы (см. AGENTS.md).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/contracts/listeninghistory-digest.md
 */
export default {
  state: {
    listeningHistoryDigest: [],
    listeningHistoryDigestTotalCount: 0,
    listeningHistoryDigestIsLoading: false,
    // BR-383: текст ошибки загрузки истории ('' — ошибки нет).
    listeningHistoryDigestError: '',
    listeningHistoryTarget: 'local',
    listeningHistoryTableCurrentPage: 1,
  },
  getters: {
    getListeningHistoryDigest(state) {
      return state.listeningHistoryDigest
    },
    getListeningHistoryDigestTotalCount(state) {
      return state.listeningHistoryDigestTotalCount
    },
    getListeningHistoryDigestIsLoading(state) {
      return state.listeningHistoryDigestIsLoading
    },
    // BR-383: текст ошибки загрузки истории прослушиваний.
    getListeningHistoryDigestError(state) {
      return state.listeningHistoryDigestError
    },
    getListeningHistoryTarget(state) {
      return state.listeningHistoryTarget
    },
    getListeningHistoryTableCurrentPage(state) {
      return state.listeningHistoryTableCurrentPage
    },
  },
  mutations: {
    setListeningHistoryDigest(state, result) {
      state.listeningHistoryDigest = result || []
    },
    setListeningHistoryDigestTotalCount(state, total) {
      state.listeningHistoryDigestTotalCount = total || 0
    },
    setListeningHistoryDigestIsLoading(state, isLoading) {
      state.listeningHistoryDigestIsLoading = isLoading
    },
    setListeningHistoryDigestError(state, message) {
      state.listeningHistoryDigestError = message || ''
    },
    setListeningHistoryTarget(state, target) {
      state.listeningHistoryTarget = target
    },
    setListeningHistoryTableCurrentPage(state, page) {
      state.listeningHistoryTableCurrentPage = page
    },
  },
  actions: {
    /**
     * BR-382 / BR-383: загрузка истории прослушиваний.
     *
     * - BR-382: фильтры подмешиваются ЗДЕСЬ, из Vuex (`getListeningHistoryFilter`).
     *   Раньше они жили только в `data` модалки, и пагинация/«Обновить»/смена БД/
     *   смена «строк на страницу» уходили без фильтров — таблица показывала весь список.
     * - BR-383: ответ применяет только владелец текущего поколения запроса. Раньше
     *   перекрывающиеся загрузки (быстрые клики по страницам, смена БД, смена
     *   rows-per-page) приводили к тому, что таблица показывала строки старой
     *   страницы/старого target, а пагинатор — новые.
     *
     * @param {object} ctx — контекст Vuex action
     * @param {{page?: number, pageSize?: number}} params — параметры загрузки.
     * @returns {Promise<{applied: boolean, stale: boolean, error?: Error}>}
     */
    loadListeningHistoryDigest(ctx, params = {}) {
      const filter = ctx.getters.getListeningHistoryFilter || {}
      const fullParams = Object.assign({}, filter, params, {
        target: ctx.state.listeningHistoryTarget,
        page: params.page || 1,
        // specs/358-rows-per-page: пробросить pageSize в backend, чтобы размер
        // страницы применялся (раньше backend использовал дефолт 500).
        pageSize: params.pageSize,
      })
      const request = { method: 'POST', url: '/api/listeninghistory/digest', params: fullParams }
      const ticket = listeningHistoryGeneration.next()
      ctx.commit('setListeningHistoryDigestIsLoading', true)
      ctx.commit('setListeningHistoryDigestError', '')
      return promisedXMLHttpRequest(request)
        .then((data) => {
          if (!listeningHistoryGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          const result = JSON.parse(data)
          ctx.commit('setListeningHistoryDigest', result.listeningHistoryDigest)
          ctx.commit('setListeningHistoryDigestTotalCount', result.totalCount)
          ctx.commit('setListeningHistoryDigestError', '')
          return { applied: true, stale: false }
        })
        .catch((error) => {
          if (!listeningHistoryGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          ctx.commit(
            'setListeningHistoryDigestError',
            (error && error.message) || 'Не удалось загрузить историю прослушиваний',
          )
          console.log(error)
          return { applied: false, stale: false, error }
        })
        .finally(() => {
          if (listeningHistoryGeneration.isCurrent(ticket)) {
            ctx.commit('setListeningHistoryDigestIsLoading', false)
          }
        })
    },
    setListeningHistoryTarget(ctx, target) {
      ctx.commit('setListeningHistoryTarget', target)
    },
  },
}
