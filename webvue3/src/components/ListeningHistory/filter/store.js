/**
 * Vuex-модуль фильтров таблицы «История прослушиваний».
 *
 * BR-382 (аудит #336, пакет PKG-S3-31, OpenProject #403).
 *
 * ДЕФЕКТ. Фильтры жили только в `data` модалки и применялись одноразовым `dispatch`.
 * В Vuex они не сохранялись, поэтому любой последующий путь загрузки — пагинация,
 * «Обновить», смена БД, смена «строк на страницу» — уходил БЕЗ фильтров, и таблица
 * молча показывала весь список.
 *
 * РЕШЕНИЕ. Фильтры живут в Vuex (как фильтры словарей — см. Dictionaries/filter/store.js),
 * а `loadListeningHistoryDigest` сам подмешивает их к параметрам загрузки. Страница при
 * применении фильтра сбрасывается на 1.
 *
 * @see AGENTS.md
 * @see specs/171-admin-subscriptions-history/contracts/listeninghistory-digest.md
 */

export default {
  state: {
    listeningHistoryFilterUserId: '',
    listeningHistoryFilterSongId: '',
    listeningHistoryFilterLastPlayedFrom: '',
    listeningHistoryFilterLastPlayedTo: '',
  },
  getters: {
    getListeningHistoryFilterUserId(state) {
      return state.listeningHistoryFilterUserId
    },
    getListeningHistoryFilterSongId(state) {
      return state.listeningHistoryFilterSongId
    },
    getListeningHistoryFilterLastPlayedFrom(state) {
      return state.listeningHistoryFilterLastPlayedFrom
    },
    getListeningHistoryFilterLastPlayedTo(state) {
      return state.listeningHistoryFilterLastPlayedTo
    },
    /**
     * BR-382: параметры фильтра в формате, который принимает
     * `POST /api/listeninghistory/digest` (`filterUserId`, `filterSongId`,
     * `filterLastPlayedFrom`, `filterLastPlayedTo`). Пустые значения не передаются.
     *
     * @returns {Object} объект параметров фильтра.
     */
    getListeningHistoryFilter(state) {
      const params = {}
      if (state.listeningHistoryFilterUserId !== '' && state.listeningHistoryFilterUserId != null) {
        params.filterUserId = Number(state.listeningHistoryFilterUserId)
      }
      if (state.listeningHistoryFilterSongId !== '' && state.listeningHistoryFilterSongId != null) {
        params.filterSongId = Number(state.listeningHistoryFilterSongId)
      }
      if (
        state.listeningHistoryFilterLastPlayedFrom !== '' &&
        state.listeningHistoryFilterLastPlayedFrom != null
      ) {
        params.filterLastPlayedFrom = state.listeningHistoryFilterLastPlayedFrom
      }
      if (
        state.listeningHistoryFilterLastPlayedTo !== '' &&
        state.listeningHistoryFilterLastPlayedTo != null
      ) {
        params.filterLastPlayedTo = state.listeningHistoryFilterLastPlayedTo
      }
      return params
    },
  },
  mutations: {
    setListeningHistoryFilterUserId(state, value) {
      state.listeningHistoryFilterUserId = value
    },
    setListeningHistoryFilterSongId(state, value) {
      state.listeningHistoryFilterSongId = value
    },
    setListeningHistoryFilterLastPlayedFrom(state, value) {
      state.listeningHistoryFilterLastPlayedFrom = value
    },
    setListeningHistoryFilterLastPlayedTo(state, value) {
      state.listeningHistoryFilterLastPlayedTo = value
    },
  },
  actions: {
    setListeningHistoryFilterUserId(ctx, payload) {
      ctx.commit('setListeningHistoryFilterUserId', payload.value)
    },
    setListeningHistoryFilterSongId(ctx, payload) {
      ctx.commit('setListeningHistoryFilterSongId', payload.value)
    },
    setListeningHistoryFilterLastPlayedFrom(ctx, payload) {
      ctx.commit('setListeningHistoryFilterLastPlayedFrom', payload.value)
    },
    setListeningHistoryFilterLastPlayedTo(ctx, payload) {
      ctx.commit('setListeningHistoryFilterLastPlayedTo', payload.value)
    },
  },
}
