export default {
    state: {
        propertiesFilterKey: '',
        propertiesFilterValue: '',
        propertiesFilterDefaultValue: '',
        propertiesFilterDescription: '',
        propertiesFilterType: ''
    },
    getters: {
        getPropertiesFilterKey(state) { return state.propertiesFilterKey},
        getPropertiesFilterValue(state) { return state.propertiesFilterValue},
        getPropertiesFilterDefaultValue(state) { return state.propertiesFilterDefaultValue},
        getPropertiesFilterDescription(state) { return state.propertiesFilterDescription},
        getPropertiesFilterType(state) { return state.propertiesFilterType}

    },
    mutations: {
        setPropertiesFilterKey(state, propertiesFilterKey) { state.propertiesFilterKey = propertiesFilterKey },
        setPropertiesFilterValue(state, propertiesFilterValue) { state.propertiesFilterValue = propertiesFilterValue },
        setPropertiesFilterDefaultValue(state, propertiesFilterDefaultValue) { state.propertiesFilterDefaultValue = propertiesFilterDefaultValue },
        setPropertiesFilterDescription(state, propertiesFilterDescription) { state.propertiesFilterDescription = propertiesFilterDescription },
        setPropertiesFilterType(state, propertiesFilterType) { state.propertiesFilterType = propertiesFilterType }
    },
    actions: {
        setPropertiesFilterKey(ctx, payload) { ctx.commit('setPropertiesFilterKey', payload.propertiesFilterKey) },
        setPropertiesFilterValue(ctx, payload) { ctx.commit('setPropertiesFilterValue', payload.propertiesFilterValue) },
        setPropertiesFilterDefaultValue(ctx, payload) { ctx.commit('setPropertiesFilterDefaultValue', payload.propertiesFilterDefaultValue) },
        setPropertiesFilterDescription(ctx, payload) { ctx.commit('setPropertiesFilterDescription', payload.propertiesFilterDescription) },
        setPropertiesFilterType(ctx, payload) { ctx.commit('setPropertiesFilterType', payload.propertiesFilterType) }
    },
}