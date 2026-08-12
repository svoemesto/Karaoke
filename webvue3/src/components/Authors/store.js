import { promisedXMLHttpRequest } from '../../lib/utils'

/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */

export default {
  state: {
    authorsDigest: [],
    authorsDigestIsLoading: false,
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
    setAuthorsTableCurrentPage(state, page) {
      state.authorsTableCurrentPage = page
    },
    setAuthorsWithNewAlbumCount(state, count) {
      state.authorsWithNewAlbumCount = count
    },
  },
  actions: {
    loadOneRecord(ctx, id) {
      const params = { filter_id: id }
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
    loadAuthorsDigests(ctx, params) {
      let request = { method: 'POST', url: '/api/authors/authorsdigests', params: params }
      ctx.commit('setAuthorsDigestIsLoading', true)
      promisedXMLHttpRequest(request)
        .then((data) => {
          let result = JSON.parse(data)
          ctx.commit('setAuthorsDigests', result)
          ctx.commit('setAuthorsDigestIsLoading', false)
        })
        .catch((error) => {
          console.log(error)
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
