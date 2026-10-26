import { promisedXMLHttpRequest } from '../../lib/utils.js'

/**
 * Текст ошибки загрузки данных синхронизации (BR-465/BR-466, аудит #336,
 * пакет PKG-S3-33).
 *
 * @param {any} error — исключение/ошибка отклонённого промиса.
 * @param {string} what — что именно не удалось загрузить.
 * @returns {string} текст для показа пользователю.
 */
function syncErrorText(error, what) {
  const detail = error && error.message ? error.message : String(error)
  return `Не удалось загрузить ${what}: ${detail}`
}

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    entities: [],
    entitiesIsLoading: false,
    // BR-466 (аудит #336, пакет PKG-S3-33, OpenProject #405): отказ загрузки —
    // отдельное состояние. Пустая таблица и «не удалось загрузить» — разные
    // утверждения; раньше отказ уходил в unhandled rejection, и админ видел пустую
    // таблицу без сообщения (неотличимо от «нет сущностей»).
    entitiesError: null,
    // spec 235: статус автозапуска «Синхронизации в 1 клик» для UI-блока «Автозапуск»
    // в SyncTable.vue. Загружается через loadSyncAutoStatusPromise в mounted().
    // Структура: { enabled: bool, intervalMs: long, initialDelayMs: long,
    //              lastRun: AutoOneClickSyncRunDto|null, last10: AutoOneClickSyncRunDto[],
    //              nextRunEstimate: ISO-8601|null }.
    autoStatus: null,
    // BR-466: отказ загрузки статуса автозапуска.
    autoStatusError: null,
  },
  getters: {
    getSyncEntities(state) {
      return state.entities
    },
    getSyncEntitiesIsLoading(state) {
      return state.entitiesIsLoading
    },
    // BR-466: текст ошибки загрузки списка сущностей (null — ошибки нет).
    getSyncEntitiesError(state) {
      return state.entitiesError
    },
    // spec 235: getter для UI-блока «Автозапуск» в SyncTable.vue
    getSyncAutoStatus(state) {
      return state.autoStatus
    },
    // BR-466: текст ошибки загрузки статуса автозапуска (null — ошибки нет).
    getSyncAutoStatusError(state) {
      return state.autoStatusError
    },
  },
  mutations: {
    setSyncEntities(state, entities) {
      state.entities = entities
    },
    setSyncEntitiesIsLoading(state, isLoading) {
      state.entitiesIsLoading = isLoading
    },
    // BR-466: текст ошибки загрузки списка сущностей.
    setSyncEntitiesError(state, error) {
      state.entitiesError = error
    },
    // spec 235: кладёт DTO в state. Вызывается из loadSyncAutoStatusPromise.
    setSyncAutoStatus(state, autoStatus) {
      state.autoStatus = autoStatus
    },
    // BR-466: текст ошибки загрузки статуса автозапуска.
    setSyncAutoStatusError(state, error) {
      state.autoStatusError = error
    },
  },
  actions: {
    loadSyncEntitiesPromise(ctx) {
      ctx.commit('setSyncEntitiesIsLoading', true)
      ctx.commit('setSyncEntitiesError', null)
      return promisedXMLHttpRequest({ method: 'GET', url: '/api/sync/entities' })
        .then((data) => {
          ctx.commit('setSyncEntities', JSON.parse(data))
          ctx.commit('setSyncEntitiesIsLoading', false)
          return JSON.parse(data)
        })
        .catch((error) => {
          ctx.commit('setSyncEntitiesIsLoading', false)
          // BR-466: прежний список сущностей очищается, а отказ попадает в
          // состояние ошибки — таблица не показывает устаревшие данные как
          // актуальные. Отказ по-прежнему пробрасывается вызывающему.
          ctx.commit('setSyncEntities', [])
          ctx.commit('setSyncEntitiesError', syncErrorText(error, 'список сущностей синхронизации'))
          throw error
        })
    },
    // spec 235: загрузка статуса автозапуска для UI-блока «Автозапуск». Вызывается
    // в mounted() SyncTable.vue. Не делает auto-refresh — UI обновляет по F5 (по дизайну,
    // см. spec 235, FR-009, Q2 в Clarifications: SSE-push НЕ используется).
    loadSyncAutoStatusPromise(ctx) {
      ctx.commit('setSyncAutoStatusError', null)
      return promisedXMLHttpRequest({ method: 'GET', url: '/api/sync/auto-status' })
        .then((data) => {
          const autoStatus = JSON.parse(data)
          ctx.commit('setSyncAutoStatus', autoStatus)
          return autoStatus
        })
        .catch((error) => {
          // BR-466: комментарий «Не throw» не соответствовал коду — промис
          // отклонялся, и `mounted()` без `.catch` давал unhandled rejection.
          // Теперь отказ гасится, а текст ошибки попадает в состояние.
          ctx.commit('setSyncAutoStatus', null)
          ctx.commit('setSyncAutoStatusError', syncErrorText(error, 'статус автозапуска'))
          console.warn('[Sync] не удалось загрузить статус автозапуска:', error)
          return null
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
