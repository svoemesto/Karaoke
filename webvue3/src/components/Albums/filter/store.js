import { setWebvueProp } from '../../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    albumsFilterId: '',
    albumsFilterAuthorName: '',
    albumsFilterYear: '',
    albumsFilterName: '',
    albumsFilterAlbumType: '',
    albumsFilterSongsCountMin: '',
  },
  getters: {
    getAlbumsFilterId(state) {
      return state.albumsFilterId
    },
    getAlbumsFilterAuthorName(state) {
      return state.albumsFilterAuthorName
    },
    getAlbumsFilterYear(state) {
      return state.albumsFilterYear
    },
    getAlbumsFilterName(state) {
      return state.albumsFilterName
    },
    getAlbumsFilterAlbumType(state) {
      return state.albumsFilterAlbumType
    },
    getAlbumsFilterSongsCountMin(state) {
      return state.albumsFilterSongsCountMin
    },
    /**
     * specs/358-rows-per-page: агрегирующий геттер, возвращает объект `params`
     * в формате, который принимает `loadAlbumsDigests`. Используется в
     * `AlbumsTable.onPerPageChange` чтобы сохранить текущие фильтры.
     */
    getAlbumsFilter(state) {
      const params = {}
      if (
        state.albumsFilterId !== undefined &&
        state.albumsFilterId !== '' &&
        state.albumsFilterId !== null
      )
        params.filterId = state.albumsFilterId
      if (
        state.albumsFilterAuthorName !== undefined &&
        state.albumsFilterAuthorName !== '' &&
        state.albumsFilterAuthorName !== null
      )
        params.filterAuthorName = state.albumsFilterAuthorName
      if (
        state.albumsFilterYear !== undefined &&
        state.albumsFilterYear !== '' &&
        state.albumsFilterYear !== null
      )
        params.filterYear = state.albumsFilterYear
      if (
        state.albumsFilterName !== undefined &&
        state.albumsFilterName !== '' &&
        state.albumsFilterName !== null
      )
        params.filterName = state.albumsFilterName
      if (
        state.albumsFilterAlbumType !== undefined &&
        state.albumsFilterAlbumType !== '' &&
        state.albumsFilterAlbumType !== null
      )
        params.filterAlbumType = state.albumsFilterAlbumType
      if (
        state.albumsFilterSongsCountMin !== undefined &&
        state.albumsFilterSongsCountMin !== '' &&
        state.albumsFilterSongsCountMin !== null
      )
        params.filterSongsCountMin = state.albumsFilterSongsCountMin
      return params
    },
  },

  mutations: {
    setAlbumsFilterId(state, value) {
      setWebvueProp(state.albumsFilterId, 'albumsFilterId', value)
      state.albumsFilterId = value
    },
    setAlbumsFilterAuthorName(state, value) {
      setWebvueProp(state.albumsFilterAuthorName, 'albumsFilterAuthorName', value)
      state.albumsFilterAuthorName = value
    },
    setAlbumsFilterYear(state, value) {
      setWebvueProp(state.albumsFilterYear, 'albumsFilterYear', value)
      state.albumsFilterYear = value
    },
    setAlbumsFilterName(state, value) {
      setWebvueProp(state.albumsFilterName, 'albumsFilterName', value)
      state.albumsFilterName = value
    },
    setAlbumsFilterAlbumType(state, value) {
      setWebvueProp(state.albumsFilterAlbumType, 'albumsFilterAlbumType', value)
      state.albumsFilterAlbumType = value
    },
    setAlbumsFilterSongsCountMin(state, value) {
      setWebvueProp(state.albumsFilterSongsCountMin, 'albumsFilterSongsCountMin', value)
      state.albumsFilterSongsCountMin = value
    },
  },
  actions: {
    setAlbumsFilterId(ctx, payload) {
      ctx.commit('setAlbumsFilterId', payload.value)
    },
    setAlbumsFilterAuthorName(ctx, payload) {
      ctx.commit('setAlbumsFilterAuthorName', payload.value)
    },
    setAlbumsFilterYear(ctx, payload) {
      ctx.commit('setAlbumsFilterYear', payload.value)
    },
    setAlbumsFilterName(ctx, payload) {
      ctx.commit('setAlbumsFilterName', payload.value)
    },
    setAlbumsFilterAlbumType(ctx, payload) {
      ctx.commit('setAlbumsFilterAlbumType', payload.value)
    },
    setAlbumsFilterSongsCountMin(ctx, payload) {
      ctx.commit('setAlbumsFilterSongsCountMin', payload.value)
    },
  },
}
