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
