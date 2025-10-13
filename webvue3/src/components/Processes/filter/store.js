import { setWebvueProp } from "../../../lib/utils";

export default {
    state: {
        processesFilterId: '',
        processesFilterName: '',
        processesFilterStatus: '',
        processesFilterPriority: '',
        processesFilterDescription: '',
        processesFilterType: ''
    },
    getters: {
        getProcessesFilterId(state) { return state.processesFilterId},
        getProcessesFilterName(state) { return state.processesFilterName},
        getProcessesFilterStatus(state) { return state.processesFilterStatus},
        getProcessesFilterPriority(state) { return state.processesFilterPriority},
        getProcessesFilterDescription(state) { return state.processesFilterDescription},
        getProcessesFilterType(state) { return state.processesFilterType}

    },
    mutations: {
        setProcessesFilterId(state, value) {
            setWebvueProp(state.processesFilterId, 'processesFilterId', value);
            state.processesFilterId = value;
        },
        setProcessesFilterName(state, value) {
            setWebvueProp(state.processesFilterName, 'processesFilterName', value);
            state.processesFilterName = value;
        },
        setProcessesFilterStatus(state, value) {
            setWebvueProp(state.processesFilterStatus, 'processesFilterStatus', value);
            state.processesFilterStatus = value;
        },
        setProcessesFilterPriority(state, value) {
            setWebvueProp(state.processesFilterPriority, 'processesFilterPriority', value);
            state.processesFilterPriority = value;
        },
        setProcessesFilterDescription(state, value) {
            setWebvueProp(state.processesFilterDescription, 'processesFilterDescription', value);
            state.processesFilterDescription = value;
        },
        setProcessesFilterType(state, value) {
            setWebvueProp(state.processesFilterType, 'processesFilterType', value);
            state.processesFilterType = value;
        }
    },
    actions: {
        setProcessesFilterId(ctx, payload) { ctx.commit('setProcessesFilterId', payload.value) },
        setProcessesFilterName(ctx, payload) { ctx.commit('setProcessesFilterName', payload.value) },
        setProcessesFilterStatus(ctx, payload) { ctx.commit('setProcessesFilterStatus', payload.value) },
        setProcessesFilterPriority(ctx, payload) { ctx.commit('setProcessesFilterPriority', payload.value) },
        setProcessesFilterDescription(ctx, payload) { ctx.commit('setProcessesFilterDescription', payload.value) },
        setProcessesFilterType(ctx, payload) { ctx.commit('setProcessesFilterType', payload.value) }
    },
}