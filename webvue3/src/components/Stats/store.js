import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        statsBySong: [],
        statsBySongIsLoading: false,
        statsBySongTotalCount: 0,
        webEvents: [],
        webEventsIsLoading: false,
        webEventsTotalCount: 0,
        statsTarget: 'local',
    },
    getters: {
        getStatsBySong(state) { return state.statsBySong },
        getStatsBySongIsLoading(state) { return state.statsBySongIsLoading },
        getStatsBySongTotalCount(state) { return state.statsBySongTotalCount },
        getWebEvents(state) { return state.webEvents },
        getWebEventsIsLoading(state) { return state.webEventsIsLoading },
        getWebEventsTotalCount(state) { return state.webEventsTotalCount },
        getStatsTarget(state) { return state.statsTarget },
    },
    mutations: {
        setStatsBySong(state, data) { state.statsBySong = data },
        setStatsBySongIsLoading(state, v) { state.statsBySongIsLoading = v },
        setStatsBySongTotalCount(state, v) { state.statsBySongTotalCount = v },
        setWebEvents(state, data) { state.webEvents = data },
        setWebEventsIsLoading(state, v) { state.webEventsIsLoading = v },
        setWebEventsTotalCount(state, v) { state.webEventsTotalCount = v },
        setStatsTarget(state, target) { state.statsTarget = target },
    },
    actions: {
        // Обе таблицы статистики (по песням, по событиям) потенциально очень большие (тысячи
        // строк) — грузим постранично (page/pageSize), а не всё разом, как остальные digest'ы
        // в webvue3, где полный список приемлемо мал и пагинация чисто клиентская.
        loadStatsBySong(ctx, { page = 1, pageSize = 50 } = {}) {
            ctx.commit('setStatsBySongIsLoading', true);
            let url = `/api/stats/by-song?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`;
            let request = { method: 'GET', url, params: {} };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setStatsBySong', result.items);
                ctx.commit('setStatsBySongTotalCount', result.totalCount);
                ctx.commit('setStatsBySongIsLoading', false);
            }).catch(error => {
                console.log(error);
                ctx.commit('setStatsBySongIsLoading', false);
            });
        },
        loadWebEvents(ctx, { page = 1, pageSize = 50 } = {}) {
            ctx.commit('setWebEventsIsLoading', true);
            let url = `/api/webevents?target=${ctx.state.statsTarget}&page=${page}&pageSize=${pageSize}`;
            let request = { method: 'GET', url, params: {} };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setWebEvents', result.items);
                ctx.commit('setWebEventsTotalCount', result.totalCount);
                ctx.commit('setWebEventsIsLoading', false);
            }).catch(error => {
                console.log(error);
                ctx.commit('setWebEventsIsLoading', false);
            });
        },
        setStatsTarget(ctx, target) { ctx.commit('setStatsTarget', target) },
    }
}
