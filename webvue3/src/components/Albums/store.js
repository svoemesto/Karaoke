import { promisedXMLHttpRequest } from '../../lib/utils'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-363 / BR-364 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка дайджеста
// альбомов — с поколением запроса, `finally` и текстом ошибки в state.
const albumsDigestGeneration = createRequestGeneration()
const albumsDigestLiteGeneration = createRequestGeneration()

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
    // BR-363: текст ошибки загрузки дайджеста ('' — ошибки нет). Показывается в таблице.
    albumsDigestsError: '',
    // Лёгкий дайджест (без автора/картинок/счётчика песен, specs/022) — отдельный от albumsDigest
    // слот стора, чтобы пикер «Альбом (ссылка)» в SongEdit.vue не толкался в один кеш с
    // "тяжёлым" дайджестом AlbumsTable.vue (иначе кто первый загрузился — того и кеш).
    albumsDigestLite: [],
    albumsDigestLiteIsLoading: false,
    albumsDigestsLiteError: '',
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
    // BR-363: текст ошибки загрузки дайджеста альбомов.
    getAlbumsDigestsError(state) {
      return state.albumsDigestsError
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
    setAlbumsDigestsError(state, message) {
      state.albumsDigestsError = message || ''
    },
    setAlbumsDigestsLite(state, result) {
      state.albumsDigestLite = result.albumsDigests
    },
    setAlbumsDigestLiteIsLoading(state, isLoading) {
      state.albumsDigestLiteIsLoading = isLoading
    },
    setAlbumsDigestsLiteError(state, message) {
      state.albumsDigestsLiteError = message || ''
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
    // BR-363 / BR-364: промис возвращается, флаг снимается в `finally` (в том числе
    // при ошибке), ошибка видна в UI, а ответ применяется только владельцем текущего
    // поколения запроса — «побеждает последний запрос», а не последний пришедший ответ.
    loadAlbumsDigests(ctx, params) {
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: albumsDigestGeneration,
        requestOptions: { method: 'POST', url: '/api/albums/albumsdigests', params },
        loadingCommit: 'setAlbumsDigestIsLoading',
        errorCommit: 'setAlbumsDigestsError',
        applyCommit: 'setAlbumsDigests',
        errorMessage: 'Не удалось загрузить список альбомов',
      })
    },
    // Лёгкий дайджест (без автора/картинок/счётчика песен) — для пикеров, которым нужны только
    // id/authorId/year/name (см. SongEdit.vue albumsForSongAuthor). Отдельный экшен/эндпоинт —
    // не толкается в общий кеш albumsDigest с AlbumsTable.vue.
    // BR-363 (зеркало): та же схема, что у тяжёлого дайджеста — промис возвращается,
    // флаг снимается в `finally`, ошибка не остаётся только в консоли.
    loadAlbumsDigestsLite(ctx) {
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: albumsDigestLiteGeneration,
        requestOptions: { method: 'POST', url: '/api/albums/albumsdigestslite', params: {} },
        loadingCommit: 'setAlbumsDigestLiteIsLoading',
        errorCommit: 'setAlbumsDigestsLiteError',
        applyCommit: 'setAlbumsDigestsLite',
        errorMessage: 'Не удалось загрузить лёгкий дайджест альбомов',
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
