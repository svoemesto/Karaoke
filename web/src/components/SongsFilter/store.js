export default {
    state: {
        songsFilterId: '',
        songsFilterSongName: '',
        songsFilterSongAuthor: '',
        songsFilterSongAlbum: '',
        songsFilterPublishDate: '',
        songsFilterPublishTime: '',
        songsFilterIdStatus: '',
        songsFilterTags: '',
        songsFilterResultVersion: ''
    },
    getters: {
        getSongsFilterId(state) { return state.songsFilterId},
        getSongsFilterSongName(state) { return state.songsFilterSongName},
        getSongsFilterSongAuthor(state) { return state.songsFilterSongAuthor},
        getSongsFilterSongAlbum(state) { return state.songsFilterSongAlbum},
        getSongsFilterPublishDate(state) { return state.songsFilterPublishDate},
        getSongsFilterPublishTime(state) { return state.songsFilterPublishTime},
        getSongsFilterIdStatus(state) { return state.songsFilterIdStatus},
        getSongsFilterTags(state) { return state.songsFilterTags},
        getSongsFilterResultVersion(state) { return state.songsFilterResultVersion}
    },
    mutations: {
        setSongsFilterId(state, songsFilterId) { state.songsFilterId = songsFilterId },
        setSongsFilterSongName(state, songsFilterSongName) { state.songsFilterSongName = songsFilterSongName },
        setSongsFilterSongAuthor(state, songsFilterSongAuthor) { state.songsFilterSongAuthor = songsFilterSongAuthor },
        setSongsFilterSongAlbum(state, songsFilterSongAlbum) { state.songsFilterSongAlbum = songsFilterSongAlbum },
        setSongsFilterPublishDate(state, songsFilterPublishDate) { state.songsFilterPublishDate = songsFilterPublishDate },
        setSongsFilterPublishTime(state, songsFilterPublishTime) { state.songsFilterPublishTime = songsFilterPublishTime },
        setSongsFilterIdStatus(state, songsFilterIdStatus) { state.songsFilterIdStatus = songsFilterIdStatus },
        setSongsFilterTags(state, songsFilterTags) { state.songsFilterTags = songsFilterTags },
        setSongsFilterResultVersion(state, songsFilterResultVersion) { state.songsFilterResultVersion = songsFilterResultVersion }
    },
    actions: {
        setSongsFilterId(ctx, payload) { ctx.commit('setSongsFilterId', payload.songsFilterId) },
        setSongsFilterSongName(ctx, payload) { ctx.commit('setSongsFilterSongName', payload.songsFilterSongName) },
        setSongsFilterSongAuthor(ctx, payload) { ctx.commit('setSongsFilterSongAuthor', payload.songsFilterSongAuthor) },
        setSongsFilterSongAlbum(ctx, payload) { ctx.commit('setSongsFilterSongAlbum', payload.songsFilterSongAlbum) },
        setSongsFilterPublishDate(ctx, payload) { ctx.commit('setSongsFilterPublishDate', payload.songsFilterPublishDate) },
        setSongsFilterPublishTime(ctx, payload) { ctx.commit('setSongsFilterPublishTime', payload.songsFilterPublishTime) },
        setSongsFilterIdStatus(ctx, payload) { ctx.commit('setSongsFilterIdStatus', payload.songsFilterIdStatus) },
        setSongsFilterTags(ctx, payload) { ctx.commit('setSongsFilterTags', payload.songsFilterTags) },
        setSongsFilterResultVersion(ctx, payload) { ctx.commit('setSongsFilterResultVersion', payload.songsFilterResultVersion) }
    }
}