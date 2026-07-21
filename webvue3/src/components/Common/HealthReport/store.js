import { promisedXMLHttpRequest } from '../../../lib/utils.js'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    healthReportList: [],
    healthReportListIsLoading: false,
  },
  getters: {
    getHealthReportList(state) {
      return state.healthReportList
    },
  },
  mutations: {
    updateHealthReportList(state, result) {
      state.healthReportList = result
    },
    setHealthReportListIsLoading(state, isLoading) {
      state.healthReportListIsLoading = isLoading
    },
  },
  actions: {
    loadHealthReportList(ctx, id) {
      const params = { id: id }
      let request = { method: 'POST', url: '/api/song/healthReportList', params: params }
      ctx.commit('setHealthReportListIsLoading', true)
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          // console.log('loadHealthReportList result', result);
          ctx.commit('updateHealthReportList', result)
          ctx.commit('setHealthReportListIsLoading', false)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    repairOneRecord(ctx, item) {
      const params = {
        id: item.settingsId,
        healthReportTypeName: item.healthReportTypeName,
        healthReportStatusName: item.healthReportStatusName,
        description: item.description,
      }
      let request = { method: 'POST', url: '/api/song/executeHealthReportActions', params: params }
      promisedXMLHttpRequest(request)
        .then((_data) => {
          this.dispatch('loadHealthReportList', item.settingsId)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    // Каскадное «Исправить всё» одним серверным вызовом: бэкенд сам выполняет всё решаемое сейчас
    // и по мере завершения задач ставит следующие шаги цепочки. Это заменяет прежний параллельный
    // цикл repairOneRecord по каждому отчёту (в т.ч. убирает гонку, когда getHealthReport на бэке
    // возвращал null из-за смены статуса между параллельными запросами).
    repairAllPromise(ctx, id) {
      let request = { method: 'POST', url: '/api/song/repairAll', params: { id: id } }
      promisedXMLHttpRequest(request)
        .then((_data) => {
          this.dispatch('loadHealthReportList', id)
        })
        .catch((error) => {
          console.log(error)
        })
    },
  },
}
