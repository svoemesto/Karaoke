import { promisedXMLHttpRequest } from '../../lib/utils'
import { createRequestGeneration } from '../../lib/target-request-guard.js'
import { loadListWith } from '../../lib/list-loader.js'

// BR-373 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка дайджеста авторов —
// с поколением запроса, `finally` и текстом ошибки в state.
const authorsDigestGeneration = createRequestGeneration()

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    authorsDigest: [],
    authorsDigestIsLoading: false,
    // BR-373: текст ошибки загрузки дайджеста авторов ('' — ошибки нет).
    authorsDigestsError: '',
    // Текущая страница пагинации в AuthorsTable. Сохраняем в сторе, чтобы при уходе с компонента
    // и возврате — открывалась страница, на которой остановился пользователь.
    authorsTableCurrentPage: 1,
    // Количество авторов с haveNewAlbum=true — бейдж пункта меню «Авторы» в App.vue (по образцу
    // chatUnreadTotal/submittedAssignmentsCount). Источник: POST /api/authors/withnewalbumcount
    // (см. specs/176-authors-new-albums-badge/contracts/api-authors-withnewalbumcount.md).
    authorsWithNewAlbumCount: 0,
  },
  getters: {
    getAuthorsDigest(state) {
      return state.authorsDigest
    },
    getAuthorsDigestIsLoading(state) {
      return state.authorsDigestIsLoading
    },
    // BR-373: текст ошибки загрузки дайджеста авторов.
    getAuthorsDigestsError(state) {
      return state.authorsDigestsError
    },
    getAuthorsTableCurrentPage(state) {
      return state.authorsTableCurrentPage
    },
    getAuthorsWithNewAlbumCount(state) {
      return state.authorsWithNewAlbumCount
    },
  },
  mutations: {
    updateAuthorsDigests(state, result) {
      const authorsToUpdate = Array.isArray(result) ? result : [result]
      authorsToUpdate.forEach((updatedAuthor) => {
        const index = state.authorsDigest.findIndex((author) => author.id === updatedAuthor.id)
        if (index !== -1) {
          state.authorsDigest.splice(index, 1, updatedAuthor)
        }
      })
    },
    setAuthorsDigests(state, result) {
      state.authorsDigest = result.authorsDigests
    },
    setAuthorsDigestIsLoading(state, isLoading) {
      state.authorsDigestIsLoading = isLoading
    },
    setAuthorsDigestsError(state, message) {
      state.authorsDigestsError = message || ''
    },
    setAuthorsTableCurrentPage(state, page) {
      state.authorsTableCurrentPage = page
    },
    setAuthorsWithNewAlbumCount(state, count) {
      state.authorsWithNewAlbumCount = count
    },
  },
  actions: {
    loadOneRecord(ctx, id) {
      // BR-367: имя параметра — `filterId` (контракт бэкенда
      // `POST /api/authors/authorsdigests`). Раньше было `filter_id`, Spring такой
      // параметр игнорировал, и запрос возвращал ВСЕХ авторов вместо одного.
      const params = { filterId: id }
      let request = { method: 'POST', url: '/api/authors/authorsdigests', params: params }
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('updateAuthorsDigests', result.authorsDigests)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    /**
     * BR-367: читает ОДНОГО автора с сервера (свежие данные) — модалка алиасов
     * использует их, чтобы не затереть правки, сделанные другим админом/синхронизацией,
     * пока модалка была открыта.
     *
     * @param {object} ctx — контекст Vuex action
     * @param {number|string} id — id автора
     * @returns {Promise<object|null>} актуальный автор либо null.
     */
    loadOneRecordPromise(ctx, id) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/authors/authorsdigests',
        params: { filterId: id },
      })
        .then((data) => {
          const result = JSON.parse(data)
          const authors = result.authorsDigests || []
          const found = authors.find((author) => String(author.id) === String(id)) || null
          if (found) ctx.commit('updateAuthorsDigests', [found])
          return found
        })
        .catch((error) => {
          console.log(error)
          return null
        })
    },
    // BR-373: промис возвращается, флаг снимается в `finally` (в том числе при
    // ошибке — раньше он залипал навсегда, и таблица показывала «Loading...»),
    // ошибка видна в UI, а ответ применяется только владельцем текущего поколения.
    loadAuthorsDigests(ctx, params) {
      return loadListWith({
        request: promisedXMLHttpRequest,
        commit: (type, payload) => ctx.commit(type, payload),
        generation: authorsDigestGeneration,
        requestOptions: { method: 'POST', url: '/api/authors/authorsdigests', params },
        loadingCommit: 'setAuthorsDigestIsLoading',
        errorCommit: 'setAuthorsDigestsError',
        applyCommit: 'setAuthorsDigests',
        errorMessage: 'Не удалось загрузить список авторов',
      })
    },
    setAuthorValuePromise(ctx, payload) {
      let request = { method: 'POST', url: '/api/authors/updateauthor', params: payload }
      return promisedXMLHttpRequest(request)
    },
    // Бейдж пункта меню «Авторы» (App.vue, по образцу loadChatUnreadCount и
    // loadSubmittedAssignmentsCount). Polling каждые 20 сек из App.vue; при ошибке сети
    // предыдущее значение сохраняется (не сбрасывается в 0 — FR-010 спеки).
    loadAuthorsWithNewAlbumCount(ctx) {
      return promisedXMLHttpRequest({
        method: 'POST',
        url: '/api/authors/withnewalbumcount',
      })
        .then((data) => {
          ctx.commit('setAuthorsWithNewAlbumCount', parseInt(data, 10) || 0)
        })
        .catch((error) => console.log(error))
    },
  },
}
