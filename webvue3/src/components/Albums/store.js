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
    // Лёгкий дайджест (без автора/картинок/счётчика песен, specs/022) — отдельный от albumsDigest
    // слот стора, чтобы пикер «Альбом (ссылка)» в SongEdit.vue не толкался в один кеш с
    // "тяжёлым" дайджестом AlbumsTable.vue (иначе кто первый загрузился — того и кеш).
    albumsDigestLite: [],
    albumsDigestLiteIsLoading: false,
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
    getAlbumsDigestLite(state) {
      return state.albumsDigestLite
    },
    getAlbumsDigestLiteIsLoading(state) {
      return state.albumsDigestLiteIsLoading
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
    setAlbumsDigestsLite(state, result) {
      state.albumsDigestLite = result.albumsDigests
    },
    setAlbumsDigestLiteIsLoading(state, isLoading) {
      state.albumsDigestLiteIsLoading = isLoading
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
    /**
     * Возвращает id «главной» песни альбома для контекста AlbumCoverModal.
     * Используется в AlbumsTable.vue при клике по preview `(альбом)` или по названию
     * альбома — модалка привязана к конкретной песне через `currentSongId`.
     * Возвращает 0, если у альбома нет песен (UI должен блокировать клик в этом случае).
     *
     * @param {object} ctx — Vuex action context
     * @param {number} albumId — id альбома из albumsDigest
     * @return {Promise<number>} id песни альбома или 0, если песен нет
     * @see specs/014-album-cell-album-cover-modal/contracts/api.md
     */
    getFirstSongIdByAlbumIdPromise(ctx, albumId) {
      let request = {
        method: 'POST',
        url: '/api/albums/firstsongid',
        params: { albumId },
      }
      return promisedXMLHttpRequest(request).then((data) => Number(data))
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
    // Лёгкий дайджест (без автора/картинок/счётчика песен) — для пикеров, которым нужны только
    // id/authorId/year/name (см. SongEdit.vue albumsForSongAuthor). Отдельный экшен/эндпоинт —
    // не толкается в общий кеш albumsDigest с AlbumsTable.vue.
    loadAlbumsDigestsLite(ctx) {
      let request = { method: 'POST', url: '/api/albums/albumsdigestslite', params: {} }
      ctx.commit('setAlbumsDigestLiteIsLoading', true)
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setAlbumsDigestsLite', result)
          ctx.commit('setAlbumsDigestLiteIsLoading', false)
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
