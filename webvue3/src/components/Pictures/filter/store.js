import { setWebvueProp } from '../../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    picturesFilterId: '',
    picturesFilterName: '',
  },
  getters: {
    getPicturesFilterId(state) {
      return state.picturesFilterId
    },
    getPicturesFilterName(state) {
      return state.picturesFilterName
    },
  },
  mutations: {
    setPicturesFilterId(state, value) {
      setWebvueProp(state.picturesFilterId, 'picturesFilterId', value)
      state.picturesFilterId = value
    },
    setPicturesFilterName(state, value) {
      setWebvueProp(state.picturesFilterName, 'picturesFilterName', value)
      state.picturesFilterName = value
    },
  },
  actions: {
    setPicturesFilterId(ctx, payload) {
      ctx.commit('setPicturesFilterId', payload.value)
    },
    setPicturesFilterName(ctx, payload) {
      ctx.commit('setPicturesFilterName', payload.value)
    },
  },
}
