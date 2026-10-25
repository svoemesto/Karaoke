import { promisedXMLHttpRequest } from '../../lib/utils.js'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-413 (аудит #336, пакет PKG-S3-32, OpenProject #404): поколение запросов
// дайджеста свойств — ответ применяет только владелец последнего запроса.
const propertiesDigestGeneration = createRequestGeneration()

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    propertiesDigest: [],
    propertiesDigestIsLoading: false,
    // BR-413: текст ошибки загрузки дайджеста ('' — ошибки нет). Раньше отказ
    // ловился только в консоли, а флаг загрузки оставался true навсегда.
    propertiesDigestsError: '',
    // Текущая страница пагинации в PropertiesTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    propertiesTableCurrentPage: 1,
  },
  getters: {
    getPropertiesDigest(state) {
      return state.propertiesDigest
    },
    getPropertiesDigestIsLoading(state) {
      return state.propertiesDigestIsLoading
    },
    // BR-413: текст ошибки загрузки дайджеста свойств.
    getPropertiesDigestsError(state) {
      return state.propertiesDigestsError
    },
    getPropertiesTableCurrentPage(state) {
      return state.propertiesTableCurrentPage
    },
  },
  mutations: {
    updatePropertiesDigests(state, result) {
      state.propertiesDigest = result.propertiesDigests
    },
    updateOneProperty(state, prop) {
      if (!prop || typeof prop.key === 'undefined') {
        return // Выходим, если данные некорректны
      }
      const index = state.propertiesDigest.findIndex((item) => item.key === prop.key)
      if (index !== -1) {
        state.propertiesDigest.splice(index, 1, prop)
        // Альтернатива (в новых версиях Vue 2 и Vue 3 это также работает реактивно):
        // state.propertiesDigest[index] = prop;
      }
    },
    setPropertiesDigestIsLoading(state, isLoading) {
      state.propertiesDigestIsLoading = isLoading
    },
    // BR-413: текст ошибки загрузки дайджеста.
    setPropertiesDigestsError(state, message) {
      state.propertiesDigestsError = message || ''
    },
    setPropertiesTableCurrentPage(state, page) {
      state.propertiesTableCurrentPage = page
    },
  },
  actions: {
    updateOneProperty(ctx, prop) {
      ctx.commit('updateOneProperty', prop)
    },
    // BR-413: загрузка дайджеста — через общий помощник `loadListWith`
    // (аудит #336, пакет PKG-S3-31, OpenProject #403): промис возвращается,
    // флаг загрузки снимается в `finally` (и при ошибке), текст ошибки кладётся
    // в state, ответ применяет только владелец текущего поколения.
    loadPropertiesDigests(ctx, params) {
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: propertiesDigestGeneration,
        requestOptions: { method: 'POST', url: '/api/propertiesdigests', params },
        loadingCommit: 'setPropertiesDigestIsLoading',
        errorCommit: 'setPropertiesDigestsError',
        applyCommit: 'updatePropertiesDigests',
        errorMessage: 'Не удалось загрузить список настроек',
      })
    },
    setPropertyValuePromise(ctx, payload) {
      let params = { key: payload.propertyKey, stringValue: payload.propertyValue }
      let request = { method: 'POST', url: '/api/properties/setproperty', params: params }
      return promisedXMLHttpRequest(request)
    },
    getPropertyValuePromise(ctx, propertyKey) {
      let request = {
        method: 'POST',
        url: '/api/properties/getproperty',
        params: { key: propertyKey },
      }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data).property)
    },
  },
}
