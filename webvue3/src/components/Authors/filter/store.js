import { setWebvueProp } from "../../../lib/utils";
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
        setAuthorsFilterId(state, value) {
            setWebvueProp(state.authorsFilterId, 'authorsFilterId', value);
            state.authorsFilterId = value
        },
        setAuthorsFilterAuthor(state, value) {
            setWebvueProp(state.authorsFilterAuthor, 'authorsFilterAuthor', value);
            state.authorsFilterAuthor = value;
        },
        setAuthorsFilterYmId(state, value) {
            setWebvueProp(state.authorsFilterYmId, 'authorsFilterYmId', value);
            state.authorsFilterYmId = value;
        },
        setAuthorsFilterLastAlbumYm(state, value) {
            setWebvueProp(state.authorsFilterLastAlbumYm, 'authorsFilterLastAlbumYm', value);
            state.authorsFilterLastAlbumYm = value;
        },
        setAuthorsFilterLastAlbumProcessed(state, value) {
            setWebvueProp(state.authorsFilterLastAlbumProcessed, 'authorsFilterLastAlbumProcessed', value);
            state.authorsFilterLastAlbumProcessed = value;
        },
        setAuthorsFilterWatched(state, value) {
            setWebvueProp(state.authorsFilterWatched, 'authorsFilterWatched', value);
            state.authorsFilterWatched = value;
        },
        setAuthorsFilterSkip(state, value) {
            setWebvueProp(state.authorsFilterSkip, 'authorsFilterSkip', value);
            state.authorsFilterSkip = value;
        },
        setAuthorsFilterHaveNewAlbum(state, value) {
            setWebvueProp(state.authorsFilterHaveNewAlbum, 'authorsFilterHaveNewAlbum', value);
            state.authorsFilterHaveNewAlbum = value;
        }
    },
    actions: {
        setAuthorsFilterId(ctx, payload) { ctx.commit('setAuthorsFilterId', payload.value) },
        setAuthorsFilterAuthor(ctx, payload) { ctx.commit('setAuthorsFilterAuthor', payload.value) },
        setAuthorsFilterYmId(ctx, payload) { ctx.commit('setAuthorsFilterYmId', payload.value) },
        setAuthorsFilterLastAlbumYm(ctx, payload) { ctx.commit('setAuthorsFilterLastAlbumYm', payload.value) },
        setAuthorsFilterLastAlbumProcessed(ctx, payload) { ctx.commit('setAuthorsFilterLastAlbumProcessed', payload.value) },
        setAuthorsFilterWatched(ctx, payload) { ctx.commit('setAuthorsFilterWatched', payload.value) },
        setAuthorsFilterSkip(ctx, payload) { ctx.commit('setAuthorsFilterSkip', payload.value) },
        setAuthorsFilterHaveNewAlbum(ctx, payload) { ctx.commit('setAuthorsFilterHaveNewAlbum', payload.value) }
    },
}