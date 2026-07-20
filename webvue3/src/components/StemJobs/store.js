import { promisedXMLHttpRequest } from '../../lib/utils'

// Премиум-фича «Создать минусовку из аудиофайла» — админ-панель (управление заданиями пользователей
// публичного сайта). stemJobsTarget ('local'|'remote') — реальные задания создают пользователи на
// прод-сайте (karaoke-web), поэтому по умолчанию 'remote'; 'local' — для локальной отладки очереди
// (тот же паттерн, что siteUsersTarget/chatTarget).
export default {
  state: {
    stemJobs: [],
    stemJobsIsLoading: false,
    stemJobsTarget: 'remote',
  },
  getters: {
    getStemJobs(state) {
      return state.stemJobs
    },
    getStemJobsIsLoading(state) {
      return state.stemJobsIsLoading
    },
    getStemJobsTarget(state) {
      return state.stemJobsTarget
    },
  },
  mutations: {
    setStemJobs(state, result) {
      state.stemJobs = result
    },
    setStemJobsIsLoading(state, isLoading) {
      state.stemJobsIsLoading = isLoading
    },
    setStemJobsTarget(state, target) {
      state.stemJobsTarget = target
    },
    removeStemJob(state, id) {
      state.stemJobs = state.stemJobs.filter((j) => j.id !== id)
    },
    updateStemJob(state, job) {
      const index = state.stemJobs.findIndex((j) => j.id === job.id)
      if (index !== -1) state.stemJobs.splice(index, 1, job)
    },
  },
  actions: {
    setStemJobsTarget(ctx, target) {
      ctx.commit('setStemJobsTarget', target)
    },
    loadStemJobs(ctx) {
      const request = {
        method: 'POST',
        url: '/api/stemjobs/list',
        params: { target: ctx.state.stemJobsTarget },
      }
      ctx.commit('setStemJobsIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setStemJobs', result.stemJobs || [])
          ctx.commit('setStemJobsIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setStemJobsIsLoading', false)
          console.log(error)
        })
    },
    stopStemJob(ctx, id) {
      const request = {
        method: 'POST',
        url: `/api/stemjobs/${id}/stop`,
        params: { target: ctx.state.stemJobsTarget },
      }
      return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadStemJobs'))
    },
    deleteStemJob(ctx, id) {
      const request = {
        method: 'POST',
        url: `/api/stemjobs/${id}/delete`,
        params: { target: ctx.state.stemJobsTarget },
      }
      return promisedXMLHttpRequest(request).then(() => ctx.commit('removeStemJob', id))
    },
  },
}
