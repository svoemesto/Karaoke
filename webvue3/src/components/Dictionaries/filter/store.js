import { setWebvueProp } from '../../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    dictionariesFilterDictName: '',
    dictionariesFilterDictValue: '',
  },
  getters: {
    getDictionariesFilterDictName(state) {
      return state.dictionariesFilterDictName
    },
    getDictionariesFilterDictValue(state) {
      return state.dictionariesFilterDictValue
    },
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadDictionariesDigests`. Используется в
     * `DictionariesTable.onPerPageChange` чтобы сохранить текущие фильтры.
     *
     * BR-384 (аудит #336, пакет PKG-S3-31, OpenProject #403): имена параметров
     * ДОЛЖНЫ совпадать с контрактом бэкенда `POST /api/dictionaries/list`
     * (`dictName`, `dictValue`). Прежние `filterDictName`/`filterDictValue` Spring
     * игнорировал, поэтому при смене «строк на страницу» фильтр не уходил на сервер
     * и таблица показывала полный список, хотя поля фильтра хранили значения.
     */
    getDictionariesFilter(state) {
      const params = {}
      if (
        state.dictionariesFilterDictName !== undefined &&
        state.dictionariesFilterDictName !== '' &&
        state.dictionariesFilterDictName !== null
      )
        params.dictName = state.dictionariesFilterDictName
      if (
        state.dictionariesFilterDictValue !== undefined &&
        state.dictionariesFilterDictValue !== '' &&
        state.dictionariesFilterDictValue !== null
      )
        params.dictValue = state.dictionariesFilterDictValue
      return params
    },
  },

  mutations: {
    setDictionariesFilterDictName(state, value) {
      setWebvueProp(state.dictionariesFilterDictName, 'dictionariesFilterDictName', value)
      state.dictionariesFilterDictName = value
    },
    setDictionariesFilterDictValue(state, value) {
      setWebvueProp(state.dictionariesFilterDictValue, 'dictionariesFilterDictValue', value)
      state.dictionariesFilterDictValue = value
    },
  },
  actions: {
    setDictionariesFilterDictName(ctx, payload) {
      ctx.commit('setDictionariesFilterDictName', payload.value)
    },
    setDictionariesFilterDictValue(ctx, payload) {
      ctx.commit('setDictionariesFilterDictValue', payload.value)
    },
  },
}
