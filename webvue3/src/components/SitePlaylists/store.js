import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration } from '../../lib/target-request-guard.js'

// BR-414 (аудит #336, пакет PKG-S3-32, OpenProject #404): поколение запросов detail.
// Ответ применяет только владелец последнего запроса — при быстром переключении
// плейлистов приходил ответ на предыдущий, и модалка показывала не тот плейлист.
const sitePlaylistDetailGeneration = createRequestGeneration()

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
    // BR-415: владелец данных в дайджесте (id пользователя, по которому он загружен;
    // null — весь список без фильтра). Модалка «Плейлисты пользователя» показывает
    // данные только своего пользователя — иначе под её именем оказывался список
    // другого пользователя (или весь список из таблицы).
    sitePlaylistsDigestOwnerId: null,
    // BR-415: текст ошибки загрузки дайджеста.
    sitePlaylistsDigestError: '',
    sitePlaylistsTarget: 'local',
    sitePlaylistDetail: undefined,
    // BR-414: id плейлиста, к которому относится `sitePlaylistDetail`.
    sitePlaylistDetailId: null,
    // BR-414: явное состояние загрузки/ошибки detail (иначе после отказа модалка
    // навсегда показывала «Загрузка...»).
    sitePlaylistDetailIsLoading: false,
    sitePlaylistDetailError: '',
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
    // BR-415: владелец данных дайджеста.
    getSitePlaylistsDigestOwnerId(state) {
      return state.sitePlaylistsDigestOwnerId
    },
    // BR-415: текст ошибки загрузки дайджеста.
    getSitePlaylistsDigestError(state) {
      return state.sitePlaylistsDigestError
    },
    getSitePlaylistsTarget(state) {
      return state.sitePlaylistsTarget
    },
    getSitePlaylistDetail(state) {
      return state.sitePlaylistDetail
    },
    // BR-414: id плейлиста, которому принадлежит detail.
    getSitePlaylistDetailId(state) {
      return state.sitePlaylistDetailId
    },
    // BR-414: состояние загрузки detail.
    getSitePlaylistDetailIsLoading(state) {
      return state.sitePlaylistDetailIsLoading
    },
    // BR-414: текст ошибки загрузки detail.
    getSitePlaylistDetailError(state) {
      return state.sitePlaylistDetailError
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
    // BR-415: владелец данных дайджеста (ставится ДО запроса — чтобы чужие данные
    // не показывались под текущим пользователем).
    setSitePlaylistsDigestOwnerId(state, ownerId) {
      state.sitePlaylistsDigestOwnerId = ownerId === undefined ? null : ownerId
    },
    // BR-415: текст ошибки загрузки дайджеста.
    setSitePlaylistsDigestError(state, message) {
      state.sitePlaylistsDigestError = message || ''
    },
    setSitePlaylistsTarget(state, target) {
      state.sitePlaylistsTarget = target
    },
    setSitePlaylistDetail(state, detail) {
      state.sitePlaylistDetail = detail
    },
    // BR-414: id плейлиста detail.
    setSitePlaylistDetailId(state, id) {
      state.sitePlaylistDetailId = id
    },
    setSitePlaylistDetailIsLoading(state, isLoading) {
      state.sitePlaylistDetailIsLoading = !!isLoading
    },
    setSitePlaylistDetailError(state, message) {
      state.sitePlaylistDetailError = message || ''
    },
    setSitePlaylistsTableCurrentPage(state, page) {
      state.sitePlaylistsTableCurrentPage = page
    },
  },
  actions: {
    loadSitePlaylistsDigest(ctx, params) {
      const fullParams = Object.assign({}, params, { target: ctx.state.sitePlaylistsTarget })
      const request = { method: 'POST', url: '/api/siteplaylists/digest', params: fullParams }
      // BR-415: владелец данных фиксируется ДО запроса — пока грузится, модалка
      // не должна показывать данные ПРЕДЫДУЩЕГО пользователя.
      ctx.commit('setSitePlaylistsDigestOwnerId', fullParams.filterOwnerId || null)
      ctx.commit('setSitePlaylistsDigestError', '')
      ctx.commit('setSitePlaylistsDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setSitePlaylistsDigest', result.sitePlaylistsDigest)
          ctx.commit('setSitePlaylistsDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setSitePlaylistsDigestIsLoading', false)
          // BR-415: ошибка видна пользователю (пустой список больше не выглядит
          // как «плейлистов нет»).
          ctx.commit('setSitePlaylistsDigestError', error.message || 'Ошибка загрузки плейлистов')
          console.log(error)
        })
    },
    /**
     * BR-414: загрузка detail плейлиста.
     *
     * Ответ применяется только к ПОСЛЕДНЕМУ запрошенному id: при быстром
     * переключении плейлистов приходил ответ на предыдущий, и модалка показывала
     * не тот плейлист. Есть явные состояния загрузки и ошибки — после отказа
     * модалка больше не показывает «Загрузка...» навсегда.
     */
    loadSitePlaylistDetail(ctx, id) {
      const ticket = sitePlaylistDetailGeneration.next()
      const request = {
        method: 'POST',
        url: '/api/siteplaylists/byId',
        params: { id, target: ctx.state.sitePlaylistsTarget },
      }
      ctx.commit('setSitePlaylistDetailId', id)
      ctx.commit('setSitePlaylistDetailIsLoading', true)
      ctx.commit('setSitePlaylistDetailError', '')
      return promisedXMLHttpRequest(request)
        .then((data) => {
          if (!sitePlaylistDetailGeneration.isCurrent(ticket))
            return { applied: false, stale: true }
          if (ctx.state.sitePlaylistDetailId !== id) return { applied: false, stale: true }
          ctx.commit('setSitePlaylistDetail', JSON.parse(data))
          return { applied: true, stale: false }
        })
        .catch((error) => {
          if (sitePlaylistDetailGeneration.isCurrent(ticket)) {
            ctx.commit('setSitePlaylistDetailError', error.message || 'Ошибка загрузки плейлиста')
          }
          throw error
        })
        .finally(() => {
          if (sitePlaylistDetailGeneration.isCurrent(ticket)) {
            ctx.commit('setSitePlaylistDetailIsLoading', false)
          }
        })
    },
    setSitePlaylistsTarget(ctx, target) {
      ctx.commit('setSitePlaylistsTarget', target)
    },
    // BR-414: очистка detail сбрасывает и id, и состояния загрузки/ошибки.
    clearSitePlaylistDetail(ctx) {
      ctx.commit('setSitePlaylistDetail', undefined)
      ctx.commit('setSitePlaylistDetailId', null)
      ctx.commit('setSitePlaylistDetailIsLoading', false)
      ctx.commit('setSitePlaylistDetailError', '')
    },
  },
}
