// Расширение `.js` — как у сторов Pictures/Processes/Promotions/Properties: без него
// Node ESM не резолвит модуль, и стор нельзя проверить юнит-тестом (node --test).
import { promisedXMLHttpRequest } from '../../lib/utils.js'

// Небольшая key/value таблица tbl_public_settings (Postgres) — настройки, нужные сервисам, реально
// работающим на боевом сервере (karaoke-web), в отличие от ~150 файловых KaraokeProperties, которые
// живут только на локальной машине администратора (karaoke-app на сервере не разворачивается).
// publicSettingsTarget ('local'|'remote') — тот же паттерн, что и siteUsersTarget.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    publicSettingsDigest: [],
    publicSettingsDigestIsLoading: false,
    // BR-413 (та же правка, что в сторе Properties): отказ загрузки кладётся в state,
    // а не остаётся только в консоли — иначе пустой список выглядит как «настроек нет».
    publicSettingsDigestError: '',
    publicSettingsTarget: 'local',
  },
  getters: {
    getPublicSettingsDigest(state) {
      return state.publicSettingsDigest
    },
    getPublicSettingsDigestIsLoading(state) {
      return state.publicSettingsDigestIsLoading
    },
    getPublicSettingsDigestError(state) {
      return state.publicSettingsDigestError
    },
    getPublicSettingsTarget(state) {
      return state.publicSettingsTarget
    },
  },
  mutations: {
    setPublicSettingsDigest(state, result) {
      state.publicSettingsDigest = result
    },
    setPublicSettingsDigestIsLoading(state, isLoading) {
      state.publicSettingsDigestIsLoading = isLoading
    },
    setPublicSettingsDigestError(state, message) {
      state.publicSettingsDigestError = message || ''
    },
    setPublicSettingsTarget(state, target) {
      state.publicSettingsTarget = target
    },
  },
  actions: {
    /**
     * Загрузка дайджеста публичных настроек.
     *
     * Флаг загрузки снимается при любом исходе, а отказ кладётся в state
     * (`publicSettingsDigestError`) — как в сторе Properties (BR-413).
     *
     * @param {object} ctx — контекст Vuex action.
     * @returns {Promise<void>}
     */
    loadPublicSettingsDigest(ctx) {
      let request = {
        method: 'POST',
        url: '/api/publicsettings/digest',
        params: { target: ctx.state.publicSettingsTarget },
      }
      ctx.commit('setPublicSettingsDigestIsLoading', true)
      ctx.commit('setPublicSettingsDigestError', '')
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setPublicSettingsDigest', result.publicSettingsDigest)
          ctx.commit('setPublicSettingsDigestIsLoading', false)
          ctx.commit('setPublicSettingsDigestError', '')
        })
        .catch((error) => {
          ctx.commit('setPublicSettingsDigestIsLoading', false)
          ctx.commit(
            'setPublicSettingsDigestError',
            (error && error.message) || 'Не удалось загрузить настройки',
          )
          console.log(error)
        })
    },
    setPublicSettingsTarget(ctx, target) {
      ctx.commit('setPublicSettingsTarget', target)
    },
    savePublicSettingValue(ctx, payload) {
      let request = {
        method: 'POST',
        url: '/api/publicsettings/update',
        params: { key: payload.key, value: payload.value, target: ctx.state.publicSettingsTarget },
      }
      return promisedXMLHttpRequest(request)
    },
  },
}
