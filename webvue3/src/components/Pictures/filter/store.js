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
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadPicturesDigests`. Используется в
     * `PicturesTable.onPerPageChange` чтобы сохранить текущие фильтры.
     */
    getPicturesFilter(state) {
      const params = {}
      if (
        state.picturesFilterId !== undefined &&
        state.picturesFilterId !== '' &&
        state.picturesFilterId !== null
      )
        params.filterId = state.picturesFilterId
      if (
        state.picturesFilterName !== undefined &&
        state.picturesFilterName !== '' &&
        state.picturesFilterName !== null
      )
        params.filterName = state.picturesFilterName
      return params
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
