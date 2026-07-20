import { promisedXMLHttpRequest } from '../../lib/utils'

// Sponsr-синхронизация премиума (см. SponsrSyncService в karaoke-app). sponsrSyncTarget ('local'|'remote') —
// тот же паттерн, что siteUsersTarget/tariffsTarget: реальные подписчики матчатся на прод-БД.
export default {
  state: {
    sponsrSyncStatus: null,
    sponsrSyncIsLoading: false,
    sponsrSyncLastResult: null,
    sponsrSyncTarget: 'local',
  },
  getters: {
    getSponsrSyncStatus(state) {
      return state.sponsrSyncStatus
    },
    getSponsrSyncIsLoading(state) {
      return state.sponsrSyncIsLoading
    },
    getSponsrSyncLastResult(state) {
      return state.sponsrSyncLastResult
    },
    getSponsrSyncTarget(state) {
      return state.sponsrSyncTarget
    },
  },
  mutations: {
    setSponsrSyncStatus(state, v) {
      state.sponsrSyncStatus = v
    },
    setSponsrSyncIsLoading(state, v) {
      state.sponsrSyncIsLoading = v
    },
    setSponsrSyncLastResult(state, v) {
      state.sponsrSyncLastResult = v
    },
    setSponsrSyncTarget(state, target) {
      state.sponsrSyncTarget = target
    },
  },
  actions: {
    loadSponsrSyncStatus(ctx) {
      let request = { method: 'POST', url: '/api/sponsrsync/status', params: {} }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncStatus', JSON.parse(data))
        })
        .catch((e) => console.log(e))
    },
    setSponsrSyncTarget(ctx, target) {
      ctx.commit('setSponsrSyncTarget', target)
    },
    importSponsrList(ctx, identifiers) {
      ctx.commit('setSponsrSyncIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/sponsrsync/import',
        params: { identifiers, target: ctx.state.sponsrSyncTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncLastResult', JSON.parse(data))
          ctx.commit('setSponsrSyncIsLoading', false)
        })
        .catch((e) => {
          ctx.commit('setSponsrSyncIsLoading', false)
          console.log(e)
        })
    },
    runSponsrSync(ctx) {
      ctx.commit('setSponsrSyncIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/sponsrsync/run',
        params: { target: ctx.state.sponsrSyncTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setSponsrSyncLastResult', JSON.parse(data))
          ctx.commit('setSponsrSyncIsLoading', false)
        })
        .catch((e) => {
          ctx.commit('setSponsrSyncIsLoading', false)
          console.log(e)
        })
    },
  },
}
