import { promisedXMLHttpRequest } from '../../lib/utils'

// Небольшая key/value таблица tbl_public_settings (Postgres) — настройки, нужные сервисам, реально
// работающим на боевом сервере (karaoke-web), в отличие от ~150 файловых KaraokeProperties, которые
// живут только на локальной машине администратора (karaoke-app на сервере не разворачивается).
// publicSettingsTarget ('local'|'remote') — тот же паттерн, что и siteUsersTarget.
export default {
  state: {
    publicSettingsDigest: [],
    publicSettingsDigestIsLoading: false,
    publicSettingsTarget: 'local',
  },
  getters: {
    getPublicSettingsDigest(state) {
      return state.publicSettingsDigest
    },
    getPublicSettingsDigestIsLoading(state) {
      return state.publicSettingsDigestIsLoading
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
    setPublicSettingsTarget(state, target) {
      state.publicSettingsTarget = target
    },
  },
  actions: {
    loadPublicSettingsDigest(ctx) {
      let request = {
        method: 'POST',
        url: '/api/publicsettings/digest',
        params: { target: ctx.state.publicSettingsTarget },
      }
      ctx.commit('setPublicSettingsDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setPublicSettingsDigest', result.publicSettingsDigest)
          ctx.commit('setPublicSettingsDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setPublicSettingsDigestIsLoading', false)
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
