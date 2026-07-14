import { apiGet } from '../../services/api'

export default {
  namespaced: true,
  state: {
    // Песен в коллекции — id_status>=3 + непустой source_markers, без SKIP.
    // Кеш бэкенда обновляется раз в час (StatsCacheScheduler), фронт дёргает /api/public/stats
    // разово при загрузке HomeView и кладёт сюда.
    onSponsr: 0,
    // В открытом доступе — подмножество «коллекции» с истёкшим publish_date/publish_time.
    onAir: 0,
    // По подписке = onSponsr − onAir (считается на бэкенде одним SQL).
    exclusive: 0,
    // Всего песен в базе — count(*) без SKIP. Используется как «из N» в подписи карточки «В работе».
    total: 0,
    // В работе = total − onSponsr (сколько ещё не дошли до стадии «можно проиграть в плеере»).
    inWork: 0,
    isLoading: false
  },
  getters: {
    onSponsr: state => state.onSponsr,
    onAir: state => state.onAir,
    exclusive: state => state.exclusive,
    total: state => state.total,
    inWork: state => state.inWork,
    isLoading: state => state.isLoading
  },
  mutations: {
    setStats(state, stats) {
      state.onSponsr = stats.onSponsr
      state.onAir = stats.onAir
      state.exclusive = stats.exclusive
      state.total = stats.total
      state.inWork = stats.inWork
    },
    setLoading(state, value) {
      state.isLoading = value
    }
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
    }
  }
}
