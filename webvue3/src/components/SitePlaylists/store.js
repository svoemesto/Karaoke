import { promisedXMLHttpRequest } from '../../lib/utils'

// Плейлисты/«Избранное» пользователей ПУБЛИЧНОГО САЙТА (tbl_site_playlists/items). Read-only просмотр.
// target ('local'|'remote') — как в SiteUsers: реальные плейлисты создаются на боевой БД сервера.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    sitePlaylistsDigest: [],
    sitePlaylistsDigestIsLoading: false,
    sitePlaylistsTarget: 'local',
    sitePlaylistDetail: undefined,
    // Текущая страница пагинации в SitePlaylistsTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    sitePlaylistsTableCurrentPage: 1,
  },
  getters: {
    getSitePlaylistsDigest(state) {
      return state.sitePlaylistsDigest
    },
    getSitePlaylistsDigestIsLoading(state) {
      return state.sitePlaylistsDigestIsLoading
    },
    getSitePlaylistsTarget(state) {
      return state.sitePlaylistsTarget
    },
    getSitePlaylistDetail(state) {
      return state.sitePlaylistDetail
    },
    getSitePlaylistsTableCurrentPage(state) {
      return state.sitePlaylistsTableCurrentPage
    },
  },
  mutations: {
    setSitePlaylistsDigest(state, result) {
      state.sitePlaylistsDigest = result
    },
    setSitePlaylistsDigestIsLoading(state, isLoading) {
      state.sitePlaylistsDigestIsLoading = isLoading
    },
    setSitePlaylistsTarget(state, target) {
      state.sitePlaylistsTarget = target
    },
    setSitePlaylistDetail(state, detail) {
      state.sitePlaylistDetail = detail
    },
    setSitePlaylistsTableCurrentPage(state, page) {
      state.sitePlaylistsTableCurrentPage = page
    },
  },
  actions: {
    loadSitePlaylistsDigest(ctx, params) {
      const fullParams = Object.assign({}, params, { target: ctx.state.sitePlaylistsTarget })
      const request = { method: 'POST', url: '/api/siteplaylists/digest', params: fullParams }
      ctx.commit('setSitePlaylistsDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setSitePlaylistsDigest', result.sitePlaylistsDigest)
          ctx.commit('setSitePlaylistsDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setSitePlaylistsDigestIsLoading', false)
          console.log(error)
        })
    },
    loadSitePlaylistDetail(ctx, id) {
      const request = {
        method: 'POST',
        url: '/api/siteplaylists/byId',
        params: { id, target: ctx.state.sitePlaylistsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSitePlaylistDetail', JSON.parse(data))
        })
        .catch((error) => {
          console.log(error)
        })
    },
    setSitePlaylistsTarget(ctx, target) {
      ctx.commit('setSitePlaylistsTarget', target)
    },
    clearSitePlaylistDetail(ctx) {
      ctx.commit('setSitePlaylistDetail', undefined)
    },
  },
}
