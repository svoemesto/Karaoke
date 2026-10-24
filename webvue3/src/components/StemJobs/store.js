import { promisedXMLHttpRequest } from '../../lib/utils.js'

// Премиум-фича «Создать минусовку из аудиофайла» — админ-панель (управление заданиями пользователей
// публичного сайта). stemJobsTarget ('local'|'remote') — реальные задания создают пользователи на
// прод-сайте (karaoke-web), поэтому по умолчанию 'remote'; 'local' — для локальной отладки очереди
// (тот же паттерн, что siteUsersTarget/chatTarget).
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    stemJobs: [],
    stemJobsIsLoading: false,
    stemJobsTarget: 'remote',
    // BR-462: какой target реально соответствует показанному списку. Нужен, чтобы
    // не показывать задания local под меткой remote и не применить «Удалить» к
    // чужому заданию.
    stemJobsLoadedTarget: null,
    // BR-462: ошибка загрузки/операции (null — ошибок нет). Список при ошибке пуст.
    stemJobsError: null,
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
    getStemJobsLoadedTarget(state) {
      return state.stemJobsLoadedTarget
    },
    getStemJobsError(state) {
      return state.stemJobsError
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
    setStemJobsLoadedTarget(state, target) {
      state.stemJobsLoadedTarget = target
    },
    setStemJobsError(state, error) {
      state.stemJobsError = error
    },
  },
  actions: {
    setStemJobsTarget(ctx, target) {
      ctx.commit('setStemJobsTarget', target)
      // BR-462: список относится к конкретной БД — при смене target он невалиден.
      // Чистим его сразу, чтобы до ответа нельзя было нажать «Удалить» на строке
      // чужой БД (в прод-БД это немедленное удаление файлов из хранилища).
      ctx.commit('setStemJobs', [])
      ctx.commit('setStemJobsLoadedTarget', null)
      ctx.commit('setStemJobsError', null)
    },
    loadStemJobs(ctx) {
      const requestedTarget = ctx.state.stemJobsTarget
      const request = {
        method: 'POST',
        url: '/api/stemjobs/list',
        params: { target: requestedTarget },
      }
      // BR-462: если список на экране от другой БД — очищаем до запроса.
      if (ctx.state.stemJobsLoadedTarget !== requestedTarget) {
        ctx.commit('setStemJobs', [])
      }
      ctx.commit('setStemJobsIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          // Ответ мог прийти после очередной смены target — тогда он не наш.
          if (ctx.state.stemJobsTarget !== requestedTarget) return false
          ctx.commit('setStemJobs', result.stemJobs || [])
          ctx.commit('setStemJobsLoadedTarget', requestedTarget)
          ctx.commit('setStemJobsError', null)
          ctx.commit('setStemJobsIsLoading', false)
          return true
        })
        .catch((error) => {
          // BR-462: при ошибке список очищаем (fail-closed) — иначе под меткой
          // новой БД остаются строки прежней, и действия уходят по чужому id.
          ctx.commit('setStemJobs', [])
          ctx.commit('setStemJobsLoadedTarget', null)
          ctx.commit('setStemJobsError', String(error && error.message ? error.message : error))
          ctx.commit('setStemJobsIsLoading', false)
          console.error('[StemJobs.loadStemJobs] failed', error)
          return false
        })
    },
    stopStemJob(ctx, id) {
      const request = {
        method: 'POST',
        url: `/api/stemjobs/${id}/stop`,
        params: { target: ctx.state.stemJobsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then(() => ctx.dispatch('loadStemJobs'))
        .catch((error) => {
          ctx.commit('setStemJobsError', String(error && error.message ? error.message : error))
          console.error('[StemJobs.stopStemJob] failed', error)
          return false
        })
    },
    deleteStemJob(ctx, id) {
      const request = {
        method: 'POST',
        url: `/api/stemjobs/${id}/delete`,
        params: { target: ctx.state.stemJobsTarget },
      }
      return promisedXMLHttpRequest(request)
        .then(() => ctx.commit('removeStemJob', id))
        .catch((error) => {
          ctx.commit('setStemJobsError', String(error && error.message ? error.message : error))
          console.error('[StemJobs.deleteStemJob] failed', error)
          return false
        })
    },
  },
}
