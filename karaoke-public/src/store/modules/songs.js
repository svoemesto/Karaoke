import { apiGet } from '../../services/api'

let latestSearchId = 0
let latestLoadMoreId = 0
let latestSongId = 0

/**
 * Компонент «Songs».
 *
 * @see AGENTS.md
 * @see specs/262-search-pagination/spec.md
 */

export default {
  namespaced: true,
  state: {
    authors: [],
    searchResults: [],
    searchIsLoading: false,
    // Spec 262-search-pagination: состояние пагинации для бесконечной подгрузки
    // (FR-008 спеки). `error` — флаг для retry-блока (T013).
    searchPagination: {
      page: 1,
      pageSize: 35,
      totalCount: 0,
      hasMore: false,
      isLoadingMore: false,
      error: false,
    },
    currentSong: null,
    currentSongIsLoading: false,
  },
  getters: {
    authors: (state) => state.authors,
    searchResults: (state) => state.searchResults,
    searchIsLoading: (state) => state.searchIsLoading,
    searchPagination: (state) => state.searchPagination,
    searchHasMore: (state) => state.searchPagination.hasMore,
    searchIsLoadingMore: (state) => state.searchPagination.isLoadingMore,
    searchTotalCount: (state) => state.searchPagination.totalCount,
    searchPaginationError: (state) => state.searchPagination.error,
    currentSong: (state) => state.currentSong,
    currentSongIsLoading: (state) => state.currentSongIsLoading,
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
    // Spec 262-search-pagination: заменяет searchPagination целиком
    // (используется при успехе search/loadMoreSearchResults — T008).
    setSearchPagination(state, pagination) {
      state.searchPagination = { ...state.searchPagination, ...pagination }
    },
    // Spec 262-search-pagination: дописывает items к searchResults для page>1;
    // для page===1 — перезаписывает (FR-009 спеки).
    appendSearchResults(state, items) {
      state.searchResults = state.searchResults.concat(items || [])
    },
    setSearchPaginationLoadingMore(state, value) {
      state.searchPagination.isLoadingMore = value
    },
    setSearchPaginationError(state, value) {
      state.searchPagination.error = value
    },
    setCurrentSong(state, song) {
      state.currentSong = song
    },
    setCurrentSongIsLoading(state, value) {
      state.currentSongIsLoading = value
    },
  },
  actions: {
    async loadAuthors({ commit }) {
      const authors = await apiGet('/api/public/authors')
      commit('setAuthors', authors)
    },
    async search({ commit }, params) {
      // Spec 262-search-pagination: params теперь может включать page/pageSize.
      // При смене фильтров (songName/author/text/album) — page сбрасывается в 1,
      // searchResults очищаются (FR-011 спеки).
      const requestId = ++latestSearchId
      commit('setSearchIsLoading', true)
      commit('setSearchPaginationError', false)
      try {
        // Если переданы фильтры и page/pageSize не указаны явно — сбрасываем page в 1.
        const page = params.page != null ? params.page : 1
        const pageSize = params.pageSize != null ? params.pageSize : 35
        const apiParams = { ...params, page, pageSize }
        const result = await apiGet('/api/public/songs', apiParams)
        if (requestId !== latestSearchId) return
        // Бэкенд может вернуть либо массив (старый формат), либо объект { items, ... }.
        // Нормализуем к объекту для единообразной обработки (FR-016 спеки).
        const items = Array.isArray(result) ? result : result.items || []
        commit('setSearchResults', items)
        if (!Array.isArray(result)) {
          commit('setSearchPagination', {
            page: result.page,
            pageSize: result.pageSize,
            totalCount: result.totalCount,
            hasMore: result.hasMore,
            isLoadingMore: false,
          })
        } else {
          // Старый формат (без page/pageSize) — searchPagination не обновляется,
          // кнопка «Загрузить ещё» остаётся скрытой (hasMore=false).
        }
      } catch (e) {
        if (requestId === latestSearchId) {
          commit('setSearchPaginationError', true)
        }
      } finally {
        if (requestId === latestSearchId) commit('setSearchIsLoading', false)
      }
    },
    async loadMoreSearchResults({ commit, state }, currentFilters = {}) {
      // Spec 262-search-pagination (FR-010): подгрузка следующей порции.
      // currentFilters — фильтры текущего поиска (songName/author/text/album),
      // передаются из компонента (SearchView.vue) из $route.query.
      const pagination = state.searchPagination
      // Guards: rapid-click protection + окончание списка.
      if (!pagination.hasMore || pagination.isLoadingMore) return
      const requestId = ++latestLoadMoreId
      commit('setSearchPaginationLoadingMore', true)
      commit('setSearchPaginationError', false)
      try {
        const result = await apiGet('/api/public/songs', {
          songName: currentFilters.songName,
          author: currentFilters.author,
          text: currentFilters.text,
          album: currentFilters.album,
          page: pagination.page + 1,
          pageSize: pagination.pageSize,
        })
        if (requestId !== latestLoadMoreId) return
        const items = Array.isArray(result) ? result : result.items || []
        commit('appendSearchResults', items)
        if (!Array.isArray(result)) {
          commit('setSearchPagination', {
            page: result.page,
            pageSize: result.pageSize,
            totalCount: result.totalCount,
            hasMore: result.hasMore,
            isLoadingMore: false,
          })
        }
      } catch (e) {
        if (requestId === latestLoadMoreId) {
          commit('setSearchPaginationError', true)
          commit('setSearchPaginationLoadingMore', false)
        }
      } finally {
        if (requestId === latestLoadMoreId) commit('setSearchPaginationLoadingMore', false)
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
    },
  },
}
