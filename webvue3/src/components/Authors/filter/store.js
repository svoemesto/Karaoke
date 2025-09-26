export default {
    state: {
        authorsFilterId: '',
        authorsFilterAuthor: '',
        authorsFilterYmId: '',
        authorsFilterLastAlbumYm: '',
        authorsFilterLastAlbumProcessed: '',
        authorsFilterWatched: '',
        authorsFilterSkip: '',
        authorsFilterHaveNewAlbum: ''
    },
    getters: {
        getAuthorsFilterId(state) { return state.authorsFilterId},
        getAuthorsFilterAuthor(state) { return state.authorsFilterAuthor},
        getAuthorsFilterYmId(state) { return state.authorsFilterYmId},
        getAuthorsFilterLastAlbumYm(state) { return state.authorsFilterLastAlbumYm},
        getAuthorsFilterLastAlbumProcessed(state) { return state.authorsFilterLastAlbumProcessed},
        getAuthorsFilterWatched(state) { return state.authorsFilterWatched},
        getAuthorsFilterSkip(state) { return state.authorsFilterSkip},
        getAuthorsFilterHaveNewAlbum(state) { return state.authorsFilterHaveNewAlbum}

    },
    mutations: {
        setAuthorsFilterId(state, authorsFilterId) { state.authorsFilterId = authorsFilterId },
        setAuthorsFilterAuthor(state, authorsFilterAuthor) { state.authorsFilterAuthor = authorsFilterAuthor },
        setAuthorsFilterYmId(state, authorsFilterYmId) { state.authorsFilterYmId = authorsFilterYmId },
        setAuthorsFilterLastAlbumYm(state, authorsFilterLastAlbumYm) { state.authorsFilterLastAlbumYm = authorsFilterLastAlbumYm },
        setAuthorsFilterLastAlbumProcessed(state, authorsFilterLastAlbumProcessed) { state.authorsFilterLastAlbumProcessed = authorsFilterLastAlbumProcessed },
        setAuthorsFilterWatched(state, authorsFilterWatched) { state.authorsFilterWatched = authorsFilterWatched },
        setAuthorsFilterSkip(state, authorsFilterSkip) { state.authorsFilterSkip = authorsFilterSkip },
        setAuthorsFilterHaveNewAlbum(state, authorsFilterHaveNewAlbum) { state.authorsFilterHaveNewAlbum = authorsFilterHaveNewAlbum }
    },
    actions: {
        setAuthorsFilterId(ctx, payload) { ctx.commit('setAuthorsFilterId', payload.authorsFilterId) },
        setAuthorsFilterAuthor(ctx, payload) { ctx.commit('setAuthorsFilterAuthor', payload.authorsFilterAuthor) },
        setAuthorsFilterYmId(ctx, payload) { ctx.commit('setAuthorsFilterYmId', payload.authorsFilterYmId) },
        setAuthorsFilterLastAlbumYm(ctx, payload) { ctx.commit('setAuthorsFilterLastAlbumYm', payload.authorsFilterLastAlbumYm) },
        setAuthorsFilterLastAlbumProcessed(ctx, payload) { ctx.commit('setAuthorsFilterLastAlbumProcessed', payload.authorsFilterLastAlbumProcessed) },
        setAuthorsFilterWatched(ctx, payload) { ctx.commit('setAuthorsFilterWatched', payload.authorsFilterWatched) },
        setAuthorsFilterSkip(ctx, payload) { ctx.commit('setAuthorsFilterSkip', payload.authorsFilterSkip) },
        setAuthorsFilterHaveNewAlbum(ctx, payload) { ctx.commit('setAuthorsFilterHaveNewAlbum', payload.authorsFilterHaveNewAlbum) }
    },
}