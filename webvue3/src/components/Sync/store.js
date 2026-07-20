import { promisedXMLHttpRequest } from '../../lib/utils'

export default {
  state: {
    entities: [],
    entitiesIsLoading: false,
  },
  getters: {
    getSyncEntities(state) {
      return state.entities
    },
    getSyncEntitiesIsLoading(state) {
      return state.entitiesIsLoading
    },
  },
  mutations: {
    setSyncEntities(state, entities) {
      state.entities = entities
    },
    setSyncEntitiesIsLoading(state, isLoading) {
      state.entitiesIsLoading = isLoading
    },
  },
  actions: {
    loadSyncEntitiesPromise(ctx) {
      ctx.commit('setSyncEntitiesIsLoading', true)
      return promisedXMLHttpRequest({ method: 'GET', url: '/api/sync/entities' })
        .then((data) => {
          ctx.commit('setSyncEntities', JSON.parse(data))
          ctx.commit('setSyncEntitiesIsLoading', false)
          return JSON.parse(data)
        })
        .catch((error) => {
          ctx.commit('setSyncEntitiesIsLoading', false)
          throw error
        })
    },
    runEntitySyncPromise(ctx, { key, direction }) {
      let request = { method: 'POST', url: '/api/sync/run', params: { key, direction } }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
    },
    runSyncOneClickPromise() {
      let request = { method: 'POST', url: '/api/sync/oneclick' }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
    },
    // Переключение одного флага операции сущности (направление × операция). Бэкенд возвращает
    // обновлённый объект сущности — заменяем его в списке, чтобы не перезагружать всю таблицу.
    setSyncFlagPromise(ctx, { key, direction, operation, value }) {
      let request = {
        method: 'POST',
        url: '/api/sync/setflag',
        params: { key, direction, operation, value },
      }
      return promisedXMLHttpRequest(request).then((data) => {
        const entity = JSON.parse(data)
        const list = ctx.state.entities.map((e) => (e.key === entity.key ? entity : e))
        ctx.commit('setSyncEntities', list)
        return entity
      })
    },
  },
}
