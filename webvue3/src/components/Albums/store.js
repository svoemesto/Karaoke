import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 * @see specs/011-album-song-rename/contracts/api.md
 */

export default {
  state: {
    albumsDigest: [],
    albumsDigestIsLoading: false,
    // Текущая страница пагинации в AlbumsTable — как у Authors, переживает уход/возврат к компоненту.
    albumsTableCurrentPage: 1,
  },
  getters: {
    getAlbumsDigest(state) {
      return state.albumsDigest
    },
    getAlbumsDigestIsLoading(state) {
      return state.albumsDigestIsLoading
    },
    getAlbumsTableCurrentPage(state) {
      return state.albumsTableCurrentPage
    },
  },
  mutations: {
    updateAlbumsDigests(state, result) {
      const albumsToUpdate = Array.isArray(result) ? result : [result]
      albumsToUpdate.forEach((updatedAlbum) => {
        const index = state.albumsDigest.findIndex((album) => album.id === updatedAlbum.id)
        if (index !== -1) {
          state.albumsDigest.splice(index, 1, updatedAlbum)
        }
      })
    },
    setAlbumsDigests(state, result) {
      state.albumsDigest = result.albumsDigests
    },
    setAlbumsDigestIsLoading(state, isLoading) {
      state.albumsDigestIsLoading = isLoading
    },
    setAlbumsTableCurrentPage(state, page) {
      state.albumsTableCurrentPage = page
    },
  },
  actions: {
    loadOneRecord(ctx, id) {
      const params = { filterId: id }
      let request = { method: 'POST', url: '/api/albums/albumsdigests', params: params }
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('updateAlbumsDigests', result.albumsDigests)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    loadAlbumsDigests(ctx, params) {
      let request = { method: 'POST', url: '/api/albums/albumsdigests', params: params }
      ctx.commit('setAlbumsDigestIsLoading', true)
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setAlbumsDigests', result)
          ctx.commit('setAlbumsDigestIsLoading', false)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    setAlbumValuePromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/albums/updatealbum', params: payload }
      return promisedXMLHttpRequest(request)
    },
    createAlbumPromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/albums/createalbum', params: payload }
      return promisedXMLHttpRequest(request)
    },
    deleteAlbumPromise(ctx, id) {
      let request = { method: 'POST', url: '/api/albums/deletealbum', params: { id } }
      return promisedXMLHttpRequest(request)
    },
    loadAlbumsByAuthorIdPromise(ctx, authorId) {
      let request = {
        method: 'POST',
        url: '/api/albums/albumsdigests',
        params: { filterAuthorId: authorId },
      }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data).albumsDigests)
    },
    reorderAlbumsPromise(ctx, ids) {
      let request = { method: 'POST', url: '/api/albums/reorderalbums', params: { ids } }
      return promisedXMLHttpRequest(request)
    },
    normalizeAlbumSortOrderPromise() {
      let request = { method: 'POST', url: '/api/utils/normalizealbumsortorder', params: {} }
      return promisedXMLHttpRequest(request)
    },
  },
}
