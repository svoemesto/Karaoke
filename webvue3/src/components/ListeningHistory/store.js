import { promisedXMLHttpRequest } from '../../lib/utils'

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
    setListeningHistoryTarget(state, target) {
      state.listeningHistoryTarget = target
    },
    setListeningHistoryTableCurrentPage(state, page) {
      state.listeningHistoryTableCurrentPage = page
    },
  },
  actions: {
    loadListeningHistoryDigest(ctx, params = {}) {
      const fullParams = Object.assign({}, params, { target: ctx.state.listeningHistoryTarget })
      const request = { method: 'POST', url: '/api/listeninghistory/digest', params: fullParams }
      ctx.commit('setListeningHistoryDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setListeningHistoryDigest', result.listeningHistoryDigest)
          ctx.commit('setListeningHistoryDigestTotalCount', result.totalCount)
          ctx.commit('setListeningHistoryDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setListeningHistoryDigestIsLoading', false)
          console.log(error)
        })
    },
    setListeningHistoryTarget(ctx, target) {
      ctx.commit('setListeningHistoryTarget', target)
    },
  },
}
