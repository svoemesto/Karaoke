import { promisedXMLHttpRequest } from '../../lib/utils'

// Словари (tbl_dictionaries: пары dict_name/dict_value — "Слова с Ё", "Censored", "Sync Ids").
// Только LOCAL (см. DictionariesController) — правки уходят на сервер штатной синхронизацией
// (Sync, key=dictionaries), отдельного target=local|remote тут не требуется.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    dictionariesDigest: [],
    dictionariesDigestIsLoading: false,
    dictNames: [],
    // Текущая страница пагинации в DictionariesTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    dictionariesTableCurrentPage: 1,
  },
  getters: {
    getDictionariesDigest(state) {
      return state.dictionariesDigest
    },
    getDictionariesDigestIsLoading(state) {
      return state.dictionariesDigestIsLoading
    },
    getDictNames(state) {
      return state.dictNames
    },
    getDictionariesTableCurrentPage(state) {
      return state.dictionariesTableCurrentPage
    },
  },
  mutations: {
    setDictionariesDigests(state, result) {
      state.dictionariesDigest = result.dictionaries
    },
    updateDictionariesDigests(state, result) {
      const itemsToUpdate = Array.isArray(result) ? result : [result]
      itemsToUpdate.forEach((updatedItem) => {
        const index = state.dictionariesDigest.findIndex((item) => item.id === updatedItem.id)
        if (index !== -1) {
          state.dictionariesDigest.splice(index, 1, updatedItem)
        }
      })
    },
    removeDictionariesDigest(state, id) {
      const index = state.dictionariesDigest.findIndex((item) => item.id === id)
      if (index !== -1) {
        state.dictionariesDigest.splice(index, 1)
      }
    },
    setDictionariesDigestIsLoading(state, isLoading) {
      state.dictionariesDigestIsLoading = isLoading
    },
    setDictNames(state, names) {
      state.dictNames = names
    },
    setDictionariesTableCurrentPage(state, page) {
      state.dictionariesTableCurrentPage = page
    },
  },
  actions: {
    loadDictionariesDigests(ctx, params) {
      let request = { method: 'POST', url: '/api/dictionaries/list', params: params || {} }
      ctx.commit('setDictionariesDigestIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setDictionariesDigests', result)
          ctx.commit('setDictionariesDigestIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setDictionariesDigestIsLoading', false)
          console.log(error)
        })
    },
    loadOneRecord(ctx, id) {
      let request = { method: 'POST', url: '/api/dictionaries/list', params: { id } }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('updateDictionariesDigests', result.dictionaries)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    loadDictNames(ctx) {
      let request = { method: 'POST', url: '/api/dictionaries/names', params: {} }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setDictNames', result.names)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    createDictionaryItemPromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/dictionaries/create', params: payload }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
    },
    saveDictionaryItemPromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/dictionaries/update', params: payload }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
    },
    deleteDictionaryItemPromise(ctx, id) {
      let request = { method: 'POST', url: '/api/dictionaries/delete', params: { id } }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data))
    },
  },
}
