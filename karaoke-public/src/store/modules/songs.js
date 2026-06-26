import { apiGet } from '../../services/api'

let latestSearchId = 0
let latestSongId = 0

export default {
  namespaced: true,
  state: {
    authors: [],
    searchResults: [],
    searchIsLoading: false,
    currentSong: null,
    currentSongIsLoading: false
  },
  getters: {
    authors: state => state.authors,
    searchResults: state => state.searchResults,
    searchIsLoading: state => state.searchIsLoading,
    currentSong: state => state.currentSong,
    currentSongIsLoading: state => state.currentSongIsLoading
  },
  mutations: {
    setAuthors(state, authors) {
      state.authors = authors
    },
    setSearchResults(state, results) {
      state.searchResults = results
    },
    setSearchIsLoading(state, value) {
      state.searchIsLoading = value
    },
    setCurrentSong(state, song) {
      state.currentSong = song
    },
    setCurrentSongIsLoading(state, value) {
      state.currentSongIsLoading = value
    }
  },
  actions: {
    async loadAuthors({ commit }) {
      const authors = await apiGet('/api/public/authors')
      commit('setAuthors', authors)
    },
    async search({ commit }, params) {
      const requestId = ++latestSearchId
      commit('setSearchIsLoading', true)
      try {
        const results = await apiGet('/api/public/songs', params)
        if (requestId === latestSearchId) commit('setSearchResults', results)
      } finally {
        if (requestId === latestSearchId) commit('setSearchIsLoading', false)
      }
    },
    async loadSong({ commit }, id) {
      const requestId = ++latestSongId
      commit('setCurrentSongIsLoading', true)
      commit('setCurrentSong', null)
      try {
        const song = await apiGet(`/api/public/song/${id}`)
        if (requestId === latestSongId) commit('setCurrentSong', song)
      } finally {
        if (requestId === latestSongId) commit('setCurrentSongIsLoading', false)
      }
    }
  }
}
