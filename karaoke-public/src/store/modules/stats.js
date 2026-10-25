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
    // BR-323 (аудит #336, PKG-S3-27): сбой загрузки НЕЛЬЗЯ показывать как «0 песен».
    // Раньше catch отсутствовал, `setStats` не вызывался, `isLoading` сбрасывался — и главная
    // страница рисовала нули как реальные данные («0 Песен в коллекции»).
    // Теперь есть явные состояния: loaded (данные есть) и error (загрузка не удалась).
    loaded: false,
    error: '',
  },
  getters: {
    onSponsr: (state) => state.onSponsr,
    freeNow: (state) => state.freeNow,
    subscriptionOnly: (state) => state.subscriptionOnly,
    total: (state) => state.total,
    inWork: (state) => state.inWork,
    isLoading: (state) => state.isLoading,
    loaded: (state) => state.loaded,
    error: (state) => state.error,
    // Алиас для шаблона HomeView (там же есть другие «error»-имена).
    statsError: (state) => state.error,
  },
  mutations: {
    setStats(state, stats) {
      state.onSponsr = stats.onSponsr
      state.freeNow = stats.freeNow
      state.subscriptionOnly = stats.subscriptionOnly
      state.total = stats.total
      state.inWork = stats.inWork
      state.loaded = true
      state.error = ''
    },
    setLoading(state, value) {
      state.isLoading = value
    },
    setError(state, message) {
      state.error = message || ''
      state.loaded = false
    },
  },
  actions: {
    async loadStats({ commit }) {
      commit('setLoading', true)
      commit('setError', '')
      try {
        const stats = await apiGet('/api/public/stats')
        commit('setStats', stats)
      } catch (e) {
        // Ошибку не глотаем: показываем «Статистика недоступна» + повтор, а не нули.
        commit('setError', 'Статистика недоступна — попробуйте ещё раз')
      } finally {
        commit('setLoading', false)
      }
    },
  },
}
