import { promisedXMLHttpRequest } from '../../lib/utils'
import { createRequestGeneration } from '../../lib/target-request-guard.js'

// BR-392 / BR-393 (аудит #336, пакет PKG-S3-31, OpenProject #403): загрузка новостей —
// с поколением запроса, `finally` и текстом ошибки в state.
const newsListGeneration = createRequestGeneration()

// «Новости» проекта (tbl_news). Готовятся на LOCAL, уходят на прод штатной синхронизацией (Sync,
// key=news) — как Словари (components/Dictionaries/store.js). target оставлен переключаемым (как
// у Чата) для локальной отладки, дефолт — LOCAL.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
const NEWS_PER_PAGE = 35

export default {
  state: {
    newsList: [],
    newsListIsLoading: false,
    // BR-392: текст ошибки загрузки списка новостей ('' — ошибки нет). Пустой
    // успешный ответ и отказ — разные состояния.
    newsListError: '',
    newsTarget: 'local',
    // Постранично (specs/090-news-pagination) — newsList хранит только текущую страницу,
    // newsTotalCount/newsCurrentPage приводят в действие <b-pagination> в NewsTable.vue.
    newsTotalCount: 0,
    newsCurrentPage: 1,
    newsPerPage: NEWS_PER_PAGE,
  },
  getters: {
    getNewsList(state) {
      return state.newsList
    },
    getNewsListIsLoading(state) {
      return state.newsListIsLoading
    },
    // BR-392: текст ошибки загрузки списка новостей.
    getNewsListError(state) {
      return state.newsListError
    },
    getNewsTarget(state) {
      return state.newsTarget
    },
    getNewsTotalCount(state) {
      return state.newsTotalCount
    },
    getNewsCurrentPage(state) {
      return state.newsCurrentPage
    },
    getNewsPerPage(state) {
      return state.newsPerPage
    },
  },
  mutations: {
    setNewsList(state, list) {
      state.newsList = list
    },
    setNewsListIsLoading(state, isLoading) {
      state.newsListIsLoading = isLoading
    },
    setNewsListError(state, message) {
      state.newsListError = message || ''
    },
    setNewsPerPage(state, perPage) {
      state.newsPerPage = perPage
    },
    setNewsTarget(state, target) {
      state.newsTarget = target
    },
    setNewsTotalCount(state, count) {
      state.newsTotalCount = count
    },
    setNewsCurrentPage(state, page) {
      state.newsCurrentPage = page
    },
  },
  actions: {
    /**
     * BR-389 / BR-392 / BR-393: загрузка новостей.
     *
     * - BR-389: принимает `page` (1-based) и `perPage` — раньше payload игнорировался,
     *   и сервер всегда получал `pageSize=35` (поле «Строк на странице» не работало).
     * - BR-392: ошибка кладётся в state (`newsListError`), а не остаётся только в консоли:
     *   админ видел «новостей нет» вместо отказа.
     * - BR-393: ответ применяет только владелец текущего поколения запроса — «побеждает
     *   последний запрос», а не последний пришедший ответ (иначе список страницы 3
     *   показывался под номером страницы 2).
     *
     * @param {object} ctx — контекст Vuex action
     * @param {{page?: number, perPage?: number}} payload — параметры загрузки.
     * @returns {Promise<{applied: boolean, stale: boolean, error?: Error}>}
     */
    loadNews(ctx, payload) {
      const requestedPage = payload && payload.page ? payload.page : ctx.state.newsCurrentPage
      const requestedPerPage = payload && payload.perPage ? payload.perPage : ctx.state.newsPerPage
      const request = {
        method: 'POST',
        url: '/api/news/list',
        params: {
          target: ctx.state.newsTarget,
          page: requestedPage - 1,
          pageSize: requestedPerPage,
        },
      }
      const ticket = newsListGeneration.next()
      ctx.commit('setNewsListIsLoading', true)
      ctx.commit('setNewsListError', '')
      return promisedXMLHttpRequest(request)
        .then((data) => {
          if (!newsListGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          const result = JSON.parse(data)
          ctx.commit('setNewsList', result.news || [])
          ctx.commit('setNewsTotalCount', Number(result.total) || 0)
          ctx.commit('setNewsListError', '')
          return { applied: true, stale: false }
        })
        .catch((error) => {
          if (!newsListGeneration.isCurrent(ticket)) return { applied: false, stale: true }
          ctx.commit('setNewsListError', (error && error.message) || 'Не удалось загрузить новости')
          console.log(error)
          return { applied: false, stale: false, error }
        })
        .finally(() => {
          if (newsListGeneration.isCurrent(ticket)) ctx.commit('setNewsListIsLoading', false)
        })
    },
    setNewsCurrentPage(ctx, page) {
      ctx.commit('setNewsCurrentPage', page)
      return ctx.dispatch('loadNews', { page })
    },
    // BR-389: размер страницы новостей синхронизируется с общей настройкой таблиц.
    // Загрузку action НЕ инициирует — вызывающий сам решает, когда перезагружать.
    setNewsPerPage(ctx, perPage) {
      ctx.commit('setNewsPerPage', perPage)
    },
    setNewsTarget(ctx, target) {
      ctx.commit('setNewsTarget', target)
      ctx.commit('setNewsCurrentPage', 1)
    },
    createNewsPromise(ctx, payload) {
      const params = { ...payload, target: ctx.state.newsTarget }
      const request = { method: 'POST', url: '/api/news/create', params }
      return promisedXMLHttpRequest(request).then((data) => Number(data) || 0)
    },
    updateNewsPromise(ctx, payload) {
      const params = { ...payload, target: ctx.state.newsTarget }
      const request = { method: 'POST', url: '/api/news/update', params }
      return promisedXMLHttpRequest(request).then((data) => Number(data) || 0)
    },
    deleteNewsPromise(ctx, id) {
      const request = {
        method: 'POST',
        url: '/api/news/delete',
        params: { id, target: ctx.state.newsTarget },
      }
      return promisedXMLHttpRequest(request).then((data) => data === 'true')
    },
  },
}
