import { promisedXMLHttpRequest } from '../../lib/utils'

// Тарифы подписки (монетизация, tbl_price_tariffs). tariffsTarget ('local'|'remote') — тот же
// паттерн, что siteUsersTarget/publicSettingsTarget: реальные тарифы правятся на прод-БД
// (target=remote), т.к. платёжный конвейер karaoke-web читает их оттуда.
export default {
  state: {
    tariffsList: [],
    tariffsIsLoading: false,
    tariffsTarget: 'local',
  },
  getters: {
    getTariffsList(state) {
      return state.tariffsList
    },
    getTariffsIsLoading(state) {
      return state.tariffsIsLoading
    },
    getTariffsTarget(state) {
      return state.tariffsTarget
    },
  },
  mutations: {
    setTariffsList(state, list) {
      state.tariffsList = list
    },
    setTariffsIsLoading(state, v) {
      state.tariffsIsLoading = v
    },
    setTariffsTarget(state, target) {
      state.tariffsTarget = target
    },
  },
  actions: {
    loadTariffsList(ctx) {
      ctx.commit('setTariffsIsLoading', true)
      let request = {
        method: 'POST',
        url: '/api/tariffs/list',
        params: { target: ctx.state.tariffsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setTariffsList', result.tariffs)
          ctx.commit('setTariffsIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setTariffsIsLoading', false)
          console.log(error)
        })
    },
    setTariffsTarget(ctx, target) {
      ctx.commit('setTariffsTarget', target)
    },
    createTariff(ctx, payload) {
      let request = {
        method: 'POST',
        url: '/api/tariffs/create',
        params: Object.assign({ target: ctx.state.tariffsTarget }, payload),
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadTariffsList'))
    },
    saveTariff(ctx, payload) {
      let request = {
        method: 'POST',
        url: '/api/tariffs/update',
        params: Object.assign({ target: ctx.state.tariffsTarget }, payload),
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadTariffsList'))
    },
    deleteTariff(ctx, id) {
      let request = {
        method: 'POST',
        url: '/api/tariffs/delete',
        params: { id, target: ctx.state.tariffsTarget },
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadTariffsList'))
    },
  },
}
