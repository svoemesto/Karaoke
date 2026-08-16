import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    entities: [],
    entitiesIsLoading: false,
    // spec 235: статус автозапуска «Синхронизации в 1 клик» для UI-блока «Автозапуск»
    // в SyncTable.vue. Загружается через loadSyncAutoStatusPromise в mounted().
    // Структура: { enabled: bool, intervalMs: long, initialDelayMs: long,
    //              lastRun: AutoOneClickSyncRunDto|null, last10: AutoOneClickSyncRunDto[],
    //              nextRunEstimate: ISO-8601|null }.
    autoStatus: null,
  },
  getters: {
    getSyncEntities(state) {
      return state.entities
    },
    getSyncEntitiesIsLoading(state) {
      return state.entitiesIsLoading
    },
    // spec 235: getter для UI-блока «Автозапуск» в SyncTable.vue
    getSyncAutoStatus(state) {
      return state.autoStatus
    },
  },
  mutations: {
    setSyncEntities(state, entities) {
      state.entities = entities
    },
    setSyncEntitiesIsLoading(state, isLoading) {
      state.entitiesIsLoading = isLoading
    },
    // spec 235: кладёт DTO в state. Вызывается из loadSyncAutoStatusPromise.
    setSyncAutoStatus(state, autoStatus) {
      state.autoStatus = autoStatus
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
    // spec 235: загрузка статуса автозапуска для UI-блока «Автозапуск». Вызывается
    // в mounted() SyncTable.vue. Не делает auto-refresh — UI обновляет по F5 (по дизайну,
    // см. spec 235, FR-009, Q2 в Clarifications: SSE-push НЕ используется).
    loadSyncAutoStatusPromise(ctx) {
      return promisedXMLHttpRequest({ method: 'GET', url: '/api/sync/auto-status' }).then(
        (data) => {
          const autoStatus = JSON.parse(data)
          ctx.commit('setSyncAutoStatus', autoStatus)
          return autoStatus
        },
      )
      // Не throw — failure не должен ломать монтирование SyncTable.vue.
      // UI покажет fallback «не удалось получить статус» если autoStatus === null.
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
