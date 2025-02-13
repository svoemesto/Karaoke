import {promisedXMLHttpRequest} from "@/lib/utils";

export default {
    state: {
        songsFilterId: '',
        songsFilterSongName: '',
        songsFilterSongAuthor: '',
        songsFilterSongAlbum: '',
        songsFilterPublishDate: '',
        songsFilterPublishTime: '',
        songsFilterIdStatus: '',
        songsFilterCountVoices: '',
        songsFilterTags: '',
        songsFilterResultVersion: '',
        songsHistory: [],
        songsHistoryIsLoading: false
    },
    getters: {
        getSongsHistory(state) {
            return state.songsHistory
        },
        getSongsHistoryIsLoading(state) { return state.songsHistoryIsLoading },
        getSongsFilterId(state) { return state.songsFilterId},
        getSongsFilterSongName(state) { return state.songsFilterSongName},
        getSongsFilterSongAuthor(state) { return state.songsFilterSongAuthor},
        getSongsFilterSongAlbum(state) { return state.songsFilterSongAlbum},
        getSongsFilterPublishDate(state) { return state.songsFilterPublishDate},
        getSongsFilterPublishTime(state) { return state.songsFilterPublishTime},
        getSongsFilterIdStatus(state) { return state.songsFilterIdStatus},
        getSongsFilterCountVoices(state) { return state.songsFilterCountVoices},
        getSongsFilterTags(state) { return state.songsFilterTags},
        getSongsFilterResultVersion(state) { return state.songsFilterResultVersion}
    },
    mutations: {
        updateSongsHistory(state, result) {
            state.songsHistory = result.history;
            console.log('songHistory:', result.history);
        },
        setSongsHistoryIsLoading(state, isLoading) { state.songsHistoryIsLoading = isLoading },

        setSongsFilterId(state, songsFilterId) { state.songsFilterId = songsFilterId },
        setSongsFilterSongName(state, songsFilterSongName) { state.songsFilterSongName = songsFilterSongName },
        setSongsFilterSongAuthor(state, songsFilterSongAuthor) { state.songsFilterSongAuthor = songsFilterSongAuthor },
        setSongsFilterSongAlbum(state, songsFilterSongAlbum) { state.songsFilterSongAlbum = songsFilterSongAlbum },
        setSongsFilterPublishDate(state, songsFilterPublishDate) { state.songsFilterPublishDate = songsFilterPublishDate },
        setSongsFilterPublishTime(state, songsFilterPublishTime) { state.songsFilterPublishTime = songsFilterPublishTime },
        setSongsFilterIdStatus(state, songsFilterIdStatus) { state.songsFilterIdStatus = songsFilterIdStatus },
        setSongsFilterCountVoices(state, songsFilterCountVoices) { state.songsFilterCountVoices = songsFilterCountVoices },
        setSongsFilterTags(state, songsFilterTags) { state.songsFilterTags = songsFilterTags },
        setSongsFilterResultVersion(state, songsFilterResultVersion) { state.songsFilterResultVersion = songsFilterResultVersion }
    },
    actions: {
        loadSongsHistory(ctx) {
            let request = { method: 'POST', url: "/apis/songshistory" };
            ctx.commit('setSongsHistoryIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateSongsHistory', result)
                ctx.commit('setSongsHistoryIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        setSongsFilterId(ctx, payload) { ctx.commit('setSongsFilterId', payload.songsFilterId) },
        setSongsFilterSongName(ctx, payload) { ctx.commit('setSongsFilterSongName', payload.songsFilterSongName) },
        setSongsFilterSongAuthor(ctx, payload) { ctx.commit('setSongsFilterSongAuthor', payload.songsFilterSongAuthor) },
        setSongsFilterSongAlbum(ctx, payload) { ctx.commit('setSongsFilterSongAlbum', payload.songsFilterSongAlbum) },
        setSongsFilterPublishDate(ctx, payload) { ctx.commit('setSongsFilterPublishDate', payload.songsFilterPublishDate) },
        setSongsFilterPublishTime(ctx, payload) { ctx.commit('setSongsFilterPublishTime', payload.songsFilterPublishTime) },
        setSongsFilterIdStatus(ctx, payload) { ctx.commit('setSongsFilterIdStatus', payload.songsFilterIdStatus) },
        setSongsFilterCountVoices(ctx, payload) { ctx.commit('setSongsFilterCountVoices', payload.songsFilterCountVoices) },
        setSongsFilterTags(ctx, payload) { ctx.commit('setSongsFilterTags', payload.songsFilterTags) },
        setSongsFilterResultVersion(ctx, payload) { ctx.commit('setSongsFilterResultVersion', payload.songsFilterResultVersion) }
    }
}