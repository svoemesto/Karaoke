import { apiGet } from '../../services/api'

let latestRequestId = 0

/**
 * Компонент «Zakroma».
 *
 * @see AGENTS.md
 */

export default {
  namespaced: true,
  state: {
    authors: [],
    authorTiles: [],
    zakroma: [],
    isLoading: false,
  },
  getters: {
    authors: (state) => state.authors,
    authorTiles: (state) => state.authorTiles,
    zakroma: (state) => state.zakroma,
    isLoading: (state) => state.isLoading,
  },
  mutations: {
    setAuthors(state, authors) {
      state.authors = authors
    },
    setAuthorTiles(state, tiles) {
      state.authorTiles = tiles
    },
    setZakroma(state, zakroma) {
      state.zakroma = zakroma
    },
    setLoading(state, value) {
      state.isLoading = value
    },
  },
  actions: {
    async loadAuthors({ commit }) {
      const authors = await apiGet('/api/public/authors')
      commit('setAuthors', authors)
    },
    async loadAuthorTiles({ commit }) {
      const tiles = await apiGet('/api/public/authors-tiles')
      commit('setAuthorTiles', tiles)
    },
    async loadZakroma({ commit }, author) {
      const requestId = ++latestRequestId
      commit('setLoading', true)
      try {
        const zakroma = await apiGet('/api/public/zakroma', { author })
        // Запросы могут вернуться не в том порядке, в котором были отправлены
        // (например, пустой author='' с первого рендера обгоняет быстрый клик пользователя) —
        // применяем только самый последний из отправленных.
        if (requestId === latestRequestId) commit('setZakroma', zakroma)
      } finally {
        if (requestId === latestRequestId) commit('setLoading', false)
      }
    },
  },
}
