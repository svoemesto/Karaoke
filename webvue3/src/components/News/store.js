import { promisedXMLHttpRequest } from '../../lib/utils'

// «Новости» проекта (tbl_news). Готовятся на LOCAL, уходят на прод штатной синхронизацией (Sync,
// key=news) — как Словари (components/Dictionaries/store.js). target оставлен переключаемым (как
// у Чата) для локальной отладки, дефолт — LOCAL.
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
const NEWS_PER_PAGE = 50

export default {
  state: {
    newsList: [],
    newsListIsLoading: false,
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
    loadNews(ctx) {
      const request = {
        method: 'POST',
        url: '/api/news/list',
        params: {
          target: ctx.state.newsTarget,
          page: ctx.state.newsCurrentPage - 1,
          pageSize: ctx.state.newsPerPage,
        },
      }
      ctx.commit('setNewsListIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setNewsList', result.news || [])
          ctx.commit('setNewsTotalCount', Number(result.total) || 0)
          ctx.commit('setNewsListIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setNewsListIsLoading', false)
          console.log(error)
        })
    },
    setNewsCurrentPage(ctx, page) {
      ctx.commit('setNewsCurrentPage', page)
      return ctx.dispatch('loadNews')
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
