import { promisedXMLHttpRequest } from "../../lib/utils";

// «Новости» проекта (tbl_news). Готовятся на LOCAL, уходят на прод штатной синхронизацией (Sync,
// key=news) — как Словари (components/Dictionaries/store.js). target оставлен переключаемым (как
// у Чата) для локальной отладки, дефолт — LOCAL.
export default {
    state: {
        newsList: [],
        newsListIsLoading: false,
        newsTarget: 'local',
    },
    getters: {
        getNewsList(state) { return state.newsList },
        getNewsListIsLoading(state) { return state.newsListIsLoading },
        getNewsTarget(state) { return state.newsTarget },
    },
    mutations: {
        setNewsList(state, list) { state.newsList = list },
        setNewsListIsLoading(state, isLoading) { state.newsListIsLoading = isLoading },
        setNewsTarget(state, target) { state.newsTarget = target },
        removeNewsItem(state, id) {
            const index = state.newsList.findIndex(item => item.id === id);
            if (index !== -1) state.newsList.splice(index, 1);
        },
    },
    actions: {
        loadNews(ctx) {
            const request = { method: 'POST', url: "/api/news/list", params: { target: ctx.state.newsTarget } };
            ctx.commit('setNewsListIsLoading', true);
            return promisedXMLHttpRequest(request).then(data => {
                const result = JSON.parse(data);
                ctx.commit('setNewsList', result.news || []);
                ctx.commit('setNewsListIsLoading', false);
            }).catch(error => {
                ctx.commit('setNewsListIsLoading', false);
                console.log(error);
            });
        },
        setNewsTarget(ctx, target) { ctx.commit('setNewsTarget', target) },
        createNewsPromise(ctx, payload) {
            const params = { ...payload, target: ctx.state.newsTarget };
            const request = { method: 'POST', url: "/api/news/create", params };
            return promisedXMLHttpRequest(request).then(data => Number(data) || 0);
        },
        updateNewsPromise(ctx, payload) {
            const params = { ...payload, target: ctx.state.newsTarget };
            const request = { method: 'POST', url: "/api/news/update", params };
            return promisedXMLHttpRequest(request).then(data => Number(data) || 0);
        },
        deleteNewsPromise(ctx, id) {
            const request = { method: 'POST', url: "/api/news/delete", params: { id, target: ctx.state.newsTarget } };
            return promisedXMLHttpRequest(request).then(data => data === 'true');
        },
    }
}
