import { setWebvueProp } from "../../../lib/utils";
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
        setPropertiesFilterKey(state, value) {
            setWebvueProp(state.propertiesFilterKey, 'propertiesFilterKey', value);
            state.propertiesFilterKey = value;
        },
        setPropertiesFilterValue(state, value) {
            setWebvueProp(state.propertiesFilterValue, 'propertiesFilterValue', value);
            state.propertiesFilterValue = value;
        },
        setPropertiesFilterDefaultValue(state, value) {
            setWebvueProp(state.propertiesFilterDefaultValue, 'propertiesFilterDefaultValue', value);
            state.propertiesFilterDefaultValue = value;
        },
        setPropertiesFilterDescription(state, value) {
            setWebvueProp(state.propertiesFilterDescription, 'propertiesFilterDescription', value);
            state.propertiesFilterDescription = value;
        },
        setPropertiesFilterType(state, value) {
            setWebvueProp(state.propertiesFilterType, 'propertiesFilterType', value);
            state.propertiesFilterType = value ;
        }
    },
    actions: {
        setPropertiesFilterKey(ctx, payload) { ctx.commit('setPropertiesFilterKey', payload.value) },
        setPropertiesFilterValue(ctx, payload) { ctx.commit('setPropertiesFilterValue', payload.value) },
        setPropertiesFilterDefaultValue(ctx, payload) { ctx.commit('setPropertiesFilterDefaultValue', payload.value) },
        setPropertiesFilterDescription(ctx, payload) { ctx.commit('setPropertiesFilterDescription', payload.value) },
        setPropertiesFilterType(ctx, payload) { ctx.commit('setPropertiesFilterType', payload.value) }
    },
}