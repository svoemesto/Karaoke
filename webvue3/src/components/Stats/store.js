import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        statsBySong: [],
        statsBySongIsLoading: false,
        webEvents: [],
        webEventsIsLoading: false,
    },
    getters: {
        getStatsBySong(state) { return state.statsBySong },
        getStatsBySongIsLoading(state) { return state.statsBySongIsLoading },
        getWebEvents(state) { return state.webEvents },
        getWebEventsIsLoading(state) { return state.webEventsIsLoading },
    },
    mutations: {
        setStatsBySong(state, data) { state.statsBySong = data },
        setStatsBySongIsLoading(state, v) { state.statsBySongIsLoading = v },
        setWebEvents(state, data) { state.webEvents = data },
        setWebEventsIsLoading(state, v) { state.webEventsIsLoading = v },
    },
    actions: {
        loadStatsBySong(ctx) {
            ctx.commit('setStatsBySongIsLoading', true);
            let request = { method: 'GET', url: '/api/stats/by-song', params: {} };
            promisedXMLHttpRequest(request).then(data => {
                ctx.commit('setStatsBySong', JSON.parse(data));
                ctx.commit('setStatsBySongIsLoading', false);
            }).catch(error => {
                console.log(error);
                ctx.commit('setStatsBySongIsLoading', false);
            });
        },
        loadWebEvents(ctx, limit = 500) {
            ctx.commit('setWebEventsIsLoading', true);
            let request = { method: 'GET', url: `/api/webevents?limit=${limit}`, params: {} };
            promisedXMLHttpRequest(request).then(data => {
                ctx.commit('setWebEvents', JSON.parse(data));
                ctx.commit('setWebEventsIsLoading', false);
            }).catch(error => {
                console.log(error);
                ctx.commit('setWebEventsIsLoading', false);
            });
        },
    }
}
