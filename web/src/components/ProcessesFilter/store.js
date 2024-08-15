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
        setProcessesFilterId(state, processesFilterId) { state.processesFilterId = processesFilterId },
        setProcessesFilterName(state, processesFilterName) { state.processesFilterName = processesFilterName },
        setProcessesFilterStatus(state, processesFilterStatus) { state.processesFilterStatus = processesFilterStatus },
        setProcessesFilterPriority(state, processesFilterPriority) { state.processesFilterPriority = processesFilterPriority },
        setProcessesFilterDescription(state, processesFilterDescription) { state.processesFilterDescription = processesFilterDescription },
        setProcessesFilterType(state, processesFilterType) { state.processesFilterType = processesFilterType }
    },
    actions: {
        setProcessesFilterId(ctx, payload) { ctx.commit('setProcessesFilterId', payload.processesFilterId) },
        setProcessesFilterName(ctx, payload) { ctx.commit('setProcessesFilterName', payload.processesFilterName) },
        setProcessesFilterStatus(ctx, payload) { ctx.commit('setProcessesFilterStatus', payload.processesFilterStatus) },
        setProcessesFilterPriority(ctx, payload) { ctx.commit('setProcessesFilterPriority', payload.processesFilterPriority) },
        setProcessesFilterDescription(ctx, payload) { ctx.commit('setProcessesFilterDescription', payload.processesFilterDescription) },
        setProcessesFilterType(ctx, payload) { ctx.commit('setProcessesFilterType', payload.processesFilterType) }
    },
}