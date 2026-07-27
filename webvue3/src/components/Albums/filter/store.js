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
