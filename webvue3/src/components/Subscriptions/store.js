import { promisedXMLHttpRequest } from '../../lib/utils'

// Подписки пользователей ПУБЛИЧНОГО САЙТА (tbl_subscriptions). Read-only глобальный список
// для админ-SPA. target ('local'|'remote') — как в SiteUsers: реальные подписки создаются
// на боевой БД, админ может смотреть обе. JOIN к tbl_site_users / tbl_songs / tbl_price_tariffs
// делается на бэкенде одним батчем (см. AGENTS.md «Синхронизация LOCAL↔SERVER — критичные
// паттерны производительности»).
/**
 * Vuex-модуль «Подписки» (tbl_subscriptions). Глобальный read-only список для админ-SPA.
 *
 * Хранит:
 * - `subscriptionsDigest` — массив строк текущей страницы.
 * - `subscriptionsDigestTotalCount` — общее число строк (без пагинации), нужен для BPagination.
 * - `subscriptionsTarget` — 'local' | 'remote'.
 * - `subscriptionsTableCurrentPage` — персистентность страницы (см. AGENTS.md «Персистентность
 *   страницы пагинации в webvue3»).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/contracts/subscriptions-digest.md
 */
export default {
  state: {
    subscriptionsDigest: [],
    subscriptionsDigestTotalCount: 0,
    subscriptionsDigestIsLoading: false,
    subscriptionsTarget: 'local',
    subscriptionsTableCurrentPage: 1,
  },
  getters: {
    getSubscriptionsDigest(state) {
      return state.subscriptionsDigest
    },
    getSubscriptionsDigestTotalCount(state) {
      return state.subscriptionsDigestTotalCount
    },
    getSubscriptionsDigestIsLoading(state) {
      return state.subscriptionsDigestIsLoading
    },
    getSubscriptionsTarget(state) {
      return state.subscriptionsTarget
    },
    getSubscriptionsTableCurrentPage(state) {
      return state.subscriptionsTableCurrentPage
    },
  },
  mutations: {
    setSubscriptionsDigest(state, result) {
      state.subscriptionsDigest = result || []
    },
    setSubscriptionsDigestTotalCount(state, total) {
      state.subscriptionsDigestTotalCount = total || 0
    },
    setSubscriptionsDigestIsLoading(state, isLoading) {
      state.subscriptionsDigestIsLoading = isLoading
    },
    setSubscriptionsTarget(state, target) {
      state.subscriptionsTarget = target
    },
    setSubscriptionsTableCurrentPage(state, page) {
      state.subscriptionsTableCurrentPage = page
    },
  },
  actions: {
    loadSubscriptionsDigest(ctx, params = {}) {
      const fullParams = Object.assign({}, params, { target: ctx.state.subscriptionsTarget })
      const request = { method: 'POST', url: '/api/subscriptions/digest', params: fullParams }
      ctx.commit('setSubscriptionsDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setSubscriptionsDigest', result.subscriptionsDigest)
          ctx.commit('setSubscriptionsDigestTotalCount', result.totalCount)
          ctx.commit('setSubscriptionsDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setSubscriptionsDigestIsLoading', false)
          console.log(error)
        })
    },
    setSubscriptionsTarget(ctx, target) {
      ctx.commit('setSubscriptionsTarget', target)
    },
  },
}
