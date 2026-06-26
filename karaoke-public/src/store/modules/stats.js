import { apiGet } from '../../services/api'

export default {
  namespaced: true,
  state: {
    onSponsr: 0,
    onAir: 0,
    exclusive: 0,
    isLoading: false
  },
  getters: {
    onSponsr: state => state.onSponsr,
    onAir: state => state.onAir,
    exclusive: state => state.exclusive,
    isLoading: state => state.isLoading
  },
  mutations: {
    setStats(state, stats) {
      state.onSponsr = stats.onSponsr
      state.onAir = stats.onAir
      state.exclusive = stats.exclusive
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
