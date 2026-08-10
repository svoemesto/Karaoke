import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Vuex-модуль share-ссылок пользователя (add-song-share-link). Регистрируется в общем
 * store webvue3 рядом с SiteUsers — держит список ссылок и список сессий по выбранной ссы
лке.
 * target-aware (`local`/`remote`). См. SiteUsersController + SiteShareLinksController.kt.
 *
 * @see AGENTS.md
 */
export default {
  state: {
    siteUserShareLinks: [],
    siteUserShareLinksIsLoading: false,
    siteUserShareLinkSessions: [],
    siteUserShareLinkSessionsIsLoading: false,
  },
  getters: {
    getSiteUserShareLinks(state) {
      return state.siteUserShareLinks
    },
    getSiteUserShareLinksIsLoading(state) {
      return state.siteUserShareLinksIsLoading
    },
    getSiteUserShareLinkSessions(state) {
      return state.siteUserShareLinkSessions
    },
    getSiteUserShareLinkSessionsIsLoading(state) {
      return state.siteUserShareLinkSessionsIsLoading
    },
  },
  mutations: {
    setSiteUserShareLinks(state, links) {
      state.siteUserShareLinks = links || []
    },
    setSiteUserShareLinksIsLoading(state, isLoading) {
      state.siteUserShareLinksIsLoading = isLoading
    },
    setSiteUserShareLinkSessions(state, sessions) {
      state.siteUserShareLinkSessions = sessions || []
    },
    setSiteUserShareLinkSessionsIsLoading(state, isLoading) {
      state.siteUserShareLinkSessionsIsLoading = isLoading
    },
  },
  actions: {
    loadSiteUserShareLinks(ctx, { siteUserId, activeOnly = false, limit = 50, target = 'local' }) {
      ctx.commit('setSiteUserShareLinksIsLoading', true)
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/siteusers/share/links',
        params: { siteUserId, activeOnly, limit, target },
      })
        .then((data) => {
          const body = JSON.parse(data)
          ctx.commit('setSiteUserShareLinks', body.links || [])
          ctx.commit('setSiteUserShareLinksIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setSiteUserShareLinksIsLoading', false)
          console.log(error)
        })
    },
    revokeSiteUserShareLink(ctx, { shareLinkId, reason = 'admin', target = 'local' }) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/siteusers/share/links/revoke',
        params: { shareLinkId, reason, target },
      })
    },
    loadSiteUserShareLinkSessions(ctx, { shareLinkId, target = 'local' }) {
      ctx.commit('setSiteUserShareLinkSessionsIsLoading', true)
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/siteusers/share/sessions',
        params: { shareLinkId, target },
      })
        .then((data) => {
          const body = JSON.parse(data)
          ctx.commit('setSiteUserShareLinkSessions', body.sessions || [])
          ctx.commit('setSiteUserShareLinkSessionsIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setSiteUserShareLinkSessionsIsLoading', false)
          console.log(error)
        })
    },
  },
}
