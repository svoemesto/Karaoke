import { apiGet } from '../../services/api'

let latestRequestId = 0

/**
 * Компонент «Zakroma».
 *
 * @see AGENTS.md
 * @see specs/008-special-orders/spec.md — виртуальная плашка «Отдельные песни разных авторов»
 */

export default {
  namespaced: true,
  state: {
    authors: [],
    authorTiles: [],
    zakroma: [],
    /**
     * Виртуальная плашка в конце Закромов: данные по спецзаказным авторам (1-2 песни каждый).
     * @see specs/008-special-orders
     */
    specialBucket: [],
    isLoading: false,
  },
  getters: {
    authors: (state) => state.authors,
    authorTiles: (state) => state.authorTiles,
    zakroma: (state) => state.zakroma,
    specialBucket: (state) => state.specialBucket,
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
    setSpecialBucket(state, zakroma) {
      state.specialBucket = zakroma
    },
    setLoading(state, value) {
      state.isLoading = value
    },
  },
  actions: {
    async loadAuthors({ commit }, scope = 'main') {
      const authors = await apiGet('/api/public/authors', { scope })
      commit('setAuthors', authors)
    },
    async loadAuthorTiles({ commit }, scope = 'main') {
      const tiles = await apiGet('/api/public/authors-tiles', { scope })
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
    /**
     * Загружает данные для виртуальной плашки "Отдельные песни разных авторов":
     * по каждому спецзаказному автору делает запрос /api/public/zakroma?author=X.
     * Результат — список ZakromaPublicDto (как для обычных авторов).
     *
     * @see specs/008-special-orders/spec.md
     */
    async loadSpecialBucket({ commit }) {
      const specialAuthors = await apiGet('/api/public/authors', { scope: 'special' })
      if (!specialAuthors || specialAuthors.length === 0) {
        commit('setSpecialBucket', [])
        return
      }
      const all = []
      for (const author of specialAuthors) {
        if (!author) continue
        const data = await apiGet('/api/public/zakroma', { author })
        if (data && data.length > 0) all.push(...data)
      }
      commit('setSpecialBucket', all)
    },
  },
}
