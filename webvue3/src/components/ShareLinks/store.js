import { promisedXMLHttpRequest } from '../../lib/utils'

// Временные share-ссылки пользователей ПУБЛИЧНОГО САЙТА (tbl_song_share_links). Read-only
// глобальный список для админ-SPA. target ('local'|'remote') — как в SiteUsers. JOIN к
// tbl_songs / tbl_site_users делается на бэкенде одним батчем (см. ShareLinksAdminController.kt).
// Действие «Отозвать» диспатчит существующий `revokeSiteUserShareLink` из shareLinkStore.js —
// эндпоинт `/api/siteusers/share/links/revoke` переиспользуется 100% (см. RQ-6 в research.md).
/**
 * Vuex-модуль «Временные ссылки» (tbl_song_share_links). Глобальный read-only список
 * для админ-SPA с действием «Отозвать» (in-place обновление строки после успешного revoke).
 *
 * Хранит:
 * - `shareLinksDigest` — массив строк текущей страницы.
 * - `shareLinksDigestTotalCount` — общее число строк.
 * - `shareLinksTarget` — 'local' | 'remote'.
 * - `shareLinksTableCurrentPage` — персистентность страницы (см. AGENTS.md).
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/contracts/sharelinks-digest.md
 */
export default {
  state: {
    shareLinksDigest: [],
    shareLinksDigestTotalCount: 0,
    shareLinksDigestIsLoading: false,
    shareLinksTarget: 'local',
    shareLinksTableCurrentPage: 1,
  },
  getters: {
    getShareLinksDigest(state) {
      return state.shareLinksDigest
    },
    getShareLinksDigestTotalCount(state) {
      return state.shareLinksDigestTotalCount
    },
    getShareLinksDigestIsLoading(state) {
      return state.shareLinksDigestIsLoading
    },
    getShareLinksTarget(state) {
      return state.shareLinksTarget
    },
    getShareLinksTableCurrentPage(state) {
      return state.shareLinksTableCurrentPage
    },
  },
  mutations: {
    setShareLinksDigest(state, result) {
      state.shareLinksDigest = result || []
    },
    setShareLinksDigestTotalCount(state, total) {
      state.shareLinksDigestTotalCount = total || 0
    },
    setShareLinksDigestIsLoading(state, isLoading) {
      state.shareLinksDigestIsLoading = isLoading
    },
    setShareLinksTarget(state, target) {
      state.shareLinksTarget = target
    },
    setShareLinksTableCurrentPage(state, page) {
      state.shareLinksTableCurrentPage = page
    },
    // In-place обновление строки после успешного revoke (FR-022). Перезаписываем объект
    // в массиве по id — Vuex реактивность видит новое значение и Vue перерисовывает только
    // эту строку.
    updateShareLinksDigestItem(state, updatedItem) {
      const index = state.shareLinksDigest.findIndex((x) => x.id === updatedItem.id)
      if (index !== -1) {
        state.shareLinksDigest.splice(index, 1, updatedItem)
      }
    },
  },
  actions: {
    loadShareLinksDigest(ctx, params = {}) {
      const fullParams = Object.assign({}, params, { target: ctx.state.shareLinksTarget })
      const request = { method: 'POST', url: '/api/sharelinks/digest', params: fullParams }
      ctx.commit('setShareLinksDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setShareLinksDigest', result.shareLinksDigest)
          ctx.commit('setShareLinksDigestTotalCount', result.totalCount)
          ctx.commit('setShareLinksDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setShareLinksDigestIsLoading', false)
          console.log(error)
        })
    },
    setShareLinksTarget(ctx, target) {
      ctx.commit('setShareLinksTarget', target)
    },
    // Обёртка над revokeSiteUserShareLink для удобства компонента таблицы (используем тот же
    // action из shareLinkStore.js — НЕ дублируем логику). После успешного revoke — перезагружаем
    // одну строку через digest с постраничной выборкой (или локально патчим объект). Здесь —
    // патчим: быстрее для UX, не дёргает БД дважды.
    revokeShareLink(ctx, { shareLinkId, reason = 'admin' }) {
      return ctx.dispatch('revokeSiteUserShareLink', {
        shareLinkId,
        reason,
        target: ctx.state.shareLinksTarget,
      })
    },
  },
}
