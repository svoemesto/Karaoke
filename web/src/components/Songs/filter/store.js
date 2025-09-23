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
        songsFilterVersionBoosty: '',
        songsFilterVersionBoostyFiles: '',
        songsFilterVersionSponsr: '',
        songsFilterVersionDzenKaraoke: '',
        songsFilterVersionVkKaraoke: '',
        songsFilterVersionTelegramKaraoke: '',
        songsFilterVersionPlKaraoke: '',
        songsFilterRate: '',
        songsFilterStatusProcessLyrics: '',
        songsFilterStatusProcessKaraoke: '',
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
        getSongsFilterResultVersion(state) { return state.songsFilterResultVersion},
        getSongsFilterVersionBoosty(state) { return state.songsFilterVersionBoosty},
        getSongsFilterVersionBoostyFiles(state) { return state.songsFilterVersionBoostyFiles},
        getSongsFilterVersionSponsr(state) { return state.songsFilterVersionSponsr},
        getSongsFilterVersionDzenKaraoke(state) { return state.songsFilterVersionDzenKaraoke},
        getSongsFilterVersionVkKaraoke(state) { return state.songsFilterVersionVkKaraoke},
        getSongsFilterVersionTelegramKaraoke(state) { return state.songsFilterVersionTelegramKaraoke},
        getSongsFilterVersionPlKaraoke(state) { return state.songsFilterVersionPlKaraoke},
        getSongsFilterRate(state) { return state.songsFilterRate},
        getSongsFilterStatusProcessLyrics(state) { return state.songsFilterStatusProcessLyrics},
        getSongsFilterStatusProcessKaraoke(state) { return state.songsFilterStatusProcessKaraoke}
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
        setSongsFilterResultVersion(state, songsFilterResultVersion) { state.songsFilterResultVersion = songsFilterResultVersion },
        setSongsFilterVersionBoosty(state, songsFilterVersionBoosty) { state.songsFilterVersionBoosty = songsFilterVersionBoosty },
        setSongsFilterVersionBoostyFiles(state, songsFilterVersionBoostyFiles) { state.songsFilterVersionBoostyFiles = songsFilterVersionBoostyFiles },
        setSongsFilterVersionSponsr(state, songsFilterVersionSponsr) { state.songsFilterVersionSponsr = songsFilterVersionSponsr },
        setSongsFilterVersionDzenKaraoke(state, songsFilterVersionDzenKaraoke) { state.songsFilterVersionDzenKaraoke = songsFilterVersionDzenKaraoke },
        setSongsFilterVersionVkKaraoke(state, songsFilterVersionVkKaraoke) { state.songsFilterVersionVkKaraoke = songsFilterVersionVkKaraoke },
        setSongsFilterVersionTelegramKaraoke(state, songsFilterVersionTelegramKaraoke) { state.songsFilterVersionTelegramKaraoke = songsFilterVersionTelegramKaraoke },
        setSongsFilterVersionPlKaraoke(state, songsFilterVersionPlKaraoke) { state.songsFilterVersionPlKaraoke = songsFilterVersionPlKaraoke },
        setSongsFilterRate(state, songsFilterRate) { state.songsFilterRate = songsFilterRate },
        setSongsFilterStatusProcessLyrics(state, songsFilterStatusProcessLyrics) { state.songsFilterStatusProcessLyrics = songsFilterStatusProcessLyrics },
        setSongsFilterStatusProcessKaraoke(state, songsFilterStatusProcessKaraoke) { state.songsFilterStatusProcessKaraoke = songsFilterStatusProcessKaraoke }
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
        setSongsFilterResultVersion(ctx, payload) { ctx.commit('setSongsFilterResultVersion', payload.songsFilterResultVersion) },
        setSongsFilterVersionBoosty(ctx, payload) { ctx.commit('setSongsFilterVersionBoosty', payload.songsFilterVersionBoosty) },
        setSongsFilterVersionBoostyFiles(ctx, payload) { ctx.commit('setSongsFilterVersionBoostyFiles', payload.songsFilterVersionBoostyFiles) },
        setSongsFilterVersionSponsr(ctx, payload) { ctx.commit('setSongsFilterVersionSponsr', payload.songsFilterVersionSponsr) },
        setSongsFilterVersionDzenKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionDzenKaraoke', payload.songsFilterVersionDzenKaraoke) },
        setSongsFilterVersionVkKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionVkKaraoke', payload.songsFilterVersionVkKaraoke) },
        setSongsFilterVersionTelegramKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionTelegramKaraoke', payload.songsFilterVersionTelegramKaraoke) },
        setSongsFilterVersionPlKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionPlKaraoke', payload.songsFilterVersionPlKaraoke) },
        setSongsFilterRate(ctx, payload) { ctx.commit('setSongsFilterRate', payload.songsFilterRate) },
        setSongsFilterStatusProcessLyrics(ctx, payload) { ctx.commit('setSongsFilterStatusProcessLyrics', payload.songsFilterStatusProcessLyrics) },
        setSongsFilterStatusProcessKaraoke(ctx, payload) { ctx.commit('setSongsFilterStatusProcessKaraoke', payload.songsFilterStatusProcessKaraoke) }
    }
}