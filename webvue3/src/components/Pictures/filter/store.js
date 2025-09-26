export default {
    state: {
        picturesFilterId: '',
        picturesFilterName: ''
    },
    getters: {
        getPicturesFilterId(state) { return state.picturesFilterId},
        getPicturesFilterName(state) { return state.picturesFilterName}

    },
    mutations: {
        setPicturesFilterId(state, picturesFilterId) { state.picturesFilterId = picturesFilterId },
        setPicturesFilterName(state, picturesFilterName) { state.picturesFilterName = picturesFilterName }
    },
    actions: {
        setPicturesFilterId(ctx, payload) { ctx.commit('setPicturesFilterId', payload.picturesFilterId) },
        setPicturesFilterName(ctx, payload) { ctx.commit('setPicturesFilterName', payload.picturesFilterName) }
    },
}