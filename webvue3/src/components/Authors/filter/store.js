import { setWebvueProp } from '../../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    authorsFilterId: '',
    authorsFilterAuthor: '',
    authorsFilterYmId: '',
    authorsFilterVkId: '',
    authorsFilterLastAlbumYm: '',
    authorsFilterLastAlbumVk: '',
    authorsFilterLastAlbumProcessed: '',
    authorsFilterWatched: '',
    authorsFilterSkip: '',
    authorsFilterHaveNewAlbum: '',
  },
  getters: {
    getAuthorsFilterId(state) {
      return state.authorsFilterId
    },
    getAuthorsFilterAuthor(state) {
      return state.authorsFilterAuthor
    },
    getAuthorsFilterYmId(state) {
      return state.authorsFilterYmId
    },
    getAuthorsFilterVkId(state) {
      return state.authorsFilterVkId
    },
    getAuthorsFilterLastAlbumYm(state) {
      return state.authorsFilterLastAlbumYm
    },
    getAuthorsFilterLastAlbumVk(state) {
      return state.authorsFilterLastAlbumVk
    },
    getAuthorsFilterLastAlbumProcessed(state) {
      return state.authorsFilterLastAlbumProcessed
    },
    getAuthorsFilterWatched(state) {
      return state.authorsFilterWatched
    },
    getAuthorsFilterSkip(state) {
      return state.authorsFilterSkip
    },
    getAuthorsFilterHaveNewAlbum(state) {
      return state.authorsFilterHaveNewAlbum
    },
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadAuthorsDigests`. Используется в
     * `AuthorsTable.onPerPageChange` чтобы сохранить текущие фильтры.
     */
    getAuthorsFilter(state) {
      const params = {}
      if (
        state.authorsFilterId !== undefined &&
        state.authorsFilterId !== '' &&
        state.authorsFilterId !== null
      )
        params.filterId = state.authorsFilterId
      if (
        state.authorsFilterAuthor !== undefined &&
        state.authorsFilterAuthor !== '' &&
        state.authorsFilterAuthor !== null
      )
        params.filterAuthor = state.authorsFilterAuthor
      if (
        state.authorsFilterYmId !== undefined &&
        state.authorsFilterYmId !== '' &&
        state.authorsFilterYmId !== null
      )
        params.filterYmId = state.authorsFilterYmId
      if (
        state.authorsFilterVkId !== undefined &&
        state.authorsFilterVkId !== '' &&
        state.authorsFilterVkId !== null
      )
        params.filterVkId = state.authorsFilterVkId
      if (
        state.authorsFilterLastAlbumYm !== undefined &&
        state.authorsFilterLastAlbumYm !== '' &&
        state.authorsFilterLastAlbumYm !== null
      )
        params.filterLastAlbumYm = state.authorsFilterLastAlbumYm
      if (
        state.authorsFilterLastAlbumVk !== undefined &&
        state.authorsFilterLastAlbumVk !== '' &&
        state.authorsFilterLastAlbumVk !== null
      )
        params.filterLastAlbumVk = state.authorsFilterLastAlbumVk
      if (
        state.authorsFilterLastAlbumProcessed !== undefined &&
        state.authorsFilterLastAlbumProcessed !== '' &&
        state.authorsFilterLastAlbumProcessed !== null
      )
        params.filterLastAlbumProcessed = state.authorsFilterLastAlbumProcessed
      if (
        state.authorsFilterWatched !== undefined &&
        state.authorsFilterWatched !== '' &&
        state.authorsFilterWatched !== null
      )
        params.filterWatched = state.authorsFilterWatched
      if (
        state.authorsFilterSkip !== undefined &&
        state.authorsFilterSkip !== '' &&
        state.authorsFilterSkip !== null
      )
        params.filterSkip = state.authorsFilterSkip
      if (
        state.authorsFilterHaveNewAlbum !== undefined &&
        state.authorsFilterHaveNewAlbum !== '' &&
        state.authorsFilterHaveNewAlbum !== null
      )
        params.filterHaveNewAlbum = state.authorsFilterHaveNewAlbum
      return params
    },
  },

  mutations: {
    setAuthorsFilterId(state, value) {
      setWebvueProp(state.authorsFilterId, 'authorsFilterId', value)
      state.authorsFilterId = value
    },
    setAuthorsFilterAuthor(state, value) {
      setWebvueProp(state.authorsFilterAuthor, 'authorsFilterAuthor', value)
      state.authorsFilterAuthor = value
    },
    setAuthorsFilterYmId(state, value) {
      setWebvueProp(state.authorsFilterYmId, 'authorsFilterYmId', value)
      state.authorsFilterYmId = value
    },
    setAuthorsFilterVkId(state, value) {
      setWebvueProp(state.authorsFilterVkId, 'authorsFilterVkId', value)
      state.authorsFilterVkId = value
    },
    setAuthorsFilterLastAlbumYm(state, value) {
      setWebvueProp(state.authorsFilterLastAlbumYm, 'authorsFilterLastAlbumYm', value)
      state.authorsFilterLastAlbumYm = value
    },
    setAuthorsFilterLastAlbumVk(state, value) {
      setWebvueProp(state.authorsFilterLastAlbumVk, 'authorsFilterLastAlbumVk', value)
      state.authorsFilterLastAlbumVk = value
    },
    setAuthorsFilterLastAlbumProcessed(state, value) {
      setWebvueProp(state.authorsFilterLastAlbumProcessed, 'authorsFilterLastAlbumProcessed', value)
      state.authorsFilterLastAlbumProcessed = value
    },
    setAuthorsFilterWatched(state, value) {
      setWebvueProp(state.authorsFilterWatched, 'authorsFilterWatched', value)
      state.authorsFilterWatched = value
    },
    setAuthorsFilterSkip(state, value) {
      setWebvueProp(state.authorsFilterSkip, 'authorsFilterSkip', value)
      state.authorsFilterSkip = value
    },
    setAuthorsFilterHaveNewAlbum(state, value) {
      setWebvueProp(state.authorsFilterHaveNewAlbum, 'authorsFilterHaveNewAlbum', value)
      state.authorsFilterHaveNewAlbum = value
    },
  },
  actions: {
    setAuthorsFilterId(ctx, payload) {
      ctx.commit('setAuthorsFilterId', payload.value)
    },
    setAuthorsFilterAuthor(ctx, payload) {
      ctx.commit('setAuthorsFilterAuthor', payload.value)
    },
    setAuthorsFilterYmId(ctx, payload) {
      ctx.commit('setAuthorsFilterYmId', payload.value)
    },
    setAuthorsFilterVkId(ctx, payload) {
      ctx.commit('setAuthorsFilterVkId', payload.value)
    },
    setAuthorsFilterLastAlbumYm(ctx, payload) {
      ctx.commit('setAuthorsFilterLastAlbumYm', payload.value)
    },
    setAuthorsFilterLastAlbumVk(ctx, payload) {
      ctx.commit('setAuthorsFilterLastAlbumVk', payload.value)
    },
    setAuthorsFilterLastAlbumProcessed(ctx, payload) {
      ctx.commit('setAuthorsFilterLastAlbumProcessed', payload.value)
    },
    setAuthorsFilterWatched(ctx, payload) {
      ctx.commit('setAuthorsFilterWatched', payload.value)
    },
    setAuthorsFilterSkip(ctx, payload) {
      ctx.commit('setAuthorsFilterSkip', payload.value)
    },
    setAuthorsFilterHaveNewAlbum(ctx, payload) {
      ctx.commit('setAuthorsFilterHaveNewAlbum', payload.value)
    },
  },
}
