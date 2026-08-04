import { apiGet } from '../../services/api'

/**
 * Компонент «Stats».
 *
 * @see AGENTS.md
 */

export default {
  namespaced: true,
  state: {
    // Песен в коллекции — id_status>=6 + непустой source_markers, без SKIP.
    // Кеш бэкенда обновляется раз в час (StatsCacheScheduler), фронт дёргает /api/public/stats
    // разово при загрузке HomeView и кладёт сюда.
    onSponsr: 0,
    // Сейчас доступны бесплатно — всегда-бесплатные (free=true) + в окне 1 месяц после эфира
    // (specs/143-song-free-access-window, Song.isFreelyAvailableNow).
    freeNow: 0,
    // По подписке = onSponsr − freeNow (считается на бэкенде одним вычитанием).
    subscriptionOnly: 0,
    // Всего песен в базе — count(*) без SKIP. Используется как «из N» в подписи карточки «В работе».
    total: 0,
    // В работе = total − onSponsr (сколько ещё не дошли до стадии «можно проиграть в плеере»).
    inWork: 0,
    isLoading: false,
  },
  getters: {
    onSponsr: (state) => state.onSponsr,
    freeNow: (state) => state.freeNow,
    subscriptionOnly: (state) => state.subscriptionOnly,
    total: (state) => state.total,
    inWork: (state) => state.inWork,
    isLoading: (state) => state.isLoading,
  },
  mutations: {
    setStats(state, stats) {
      state.onSponsr = stats.onSponsr
      state.freeNow = stats.freeNow
      state.subscriptionOnly = stats.subscriptionOnly
      state.total = stats.total
      state.inWork = stats.inWork
    },
    setLoading(state, value) {
      state.isLoading = value
    },
  },
  actions: {
    async loadStats({ commit }) {
      commit('setLoading', true)
      try {
        const stats = await apiGet('/api/public/stats')
        commit('setStats', stats)
      } finally {
        commit('setLoading', false)
      }
    },
  },
}
