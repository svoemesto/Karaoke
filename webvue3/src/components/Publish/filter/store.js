import { setWebvueProp } from "../../../lib/utils";

export default {
    state: {
        publishFilterDateFrom: '',
        publishFilterDateTo: '',
        publishFilterDays: 90
    },
    getters: {
        getPublishFilterDateFrom(state) { return state.publishFilterDateFrom },
        getPublishFilterDateTo(state) { return state.publishFilterDateTo },
        getPublishFilterDays(state) { return state.publishFilterDays }
    },
    mutations: {
        setPublishFilterDateFrom(state, value) {
            setWebvueProp(state.publishFilterDateFrom, 'publishFilterDateFrom', value);
            state.publishFilterDateFrom = value;
        },
        setPublishFilterDateTo(state, value) {
            setWebvueProp(state.publishFilterDateTo, 'publishFilterDateTo', value);
            state.publishFilterDateTo = value;
        },
        setPublishFilterDays(state, value) {
            setWebvueProp(state.publishFilterDays, 'publishFilterDays', value);
            state.publishFilterDays = value;
        }
    },
    actions: {
        setPublishFilterDateFrom(ctx, payload) { ctx.commit('setPublishFilterDateFrom', payload.value) },
        setPublishFilterDateTo(ctx, payload) { ctx.commit('setPublishFilterDateTo', payload.value) },
        setPublishFilterDays(ctx, payload) { ctx.commit('setPublishFilterDays', payload.value) }
    }
}
