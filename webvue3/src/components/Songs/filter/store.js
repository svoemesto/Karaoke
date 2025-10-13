import { promisedXMLHttpRequest, setWebvueProp } from "../../../lib/utils";

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
        songsFilterIsSync: '',
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
        getSongsFilterIsSync(state) { return state.songsFilterIsSync},
        getSongsFilterStatusProcessLyrics(state) { return state.songsFilterStatusProcessLyrics},
        getSongsFilterStatusProcessKaraoke(state) { return state.songsFilterStatusProcessKaraoke}
    },
    mutations: {
        updateSongsHistory(state, result) {
            state.songsHistory = result.history;
        },
        setSongsHistoryIsLoading(state, isLoading) { state.songsHistoryIsLoading = isLoading },

        setSongsFilterId(state, value) {
            setWebvueProp(state.songsFilterId, 'songsFilterId', value);
            state.songsFilterId = value;
        },
        setSongsFilterSongName(state, value) {
            setWebvueProp(state.songsFilterSongName, 'songsFilterSongName', value);
            state.songsFilterSongName = value;
        },
        setSongsFilterSongAuthor(state, value) {
            setWebvueProp(state.songsFilterSongAuthor, 'songsFilterSongAuthor', value);
            state.songsFilterSongAuthor = value;
        },
        setSongsFilterSongAlbum(state, value) {
            setWebvueProp(state.songsFilterSongAlbum, 'songsFilterSongAlbum', value);
            state.songsFilterSongAlbum = value;
        },
        setSongsFilterPublishDate(state, value) {
            setWebvueProp(state.songsFilterPublishDate, 'songsFilterPublishDate', value);
            state.songsFilterPublishDate = value;
        },
        setSongsFilterPublishTime(state, value) {
            setWebvueProp(state.songsFilterPublishTime, 'songsFilterPublishTime', value);
            state.songsFilterPublishTime = value;
        },
        setSongsFilterIdStatus(state, value) {
            setWebvueProp(state.songsFilterIdStatus, 'songsFilterIdStatus', value);
            state.songsFilterIdStatus = value;
        },
        setSongsFilterCountVoices(state, value) {
            setWebvueProp(state.songsFilterCountVoices, 'songsFilterCountVoices', value);
            state.songsFilterCountVoices = value;
        },
        setSongsFilterTags(state, value) {
            setWebvueProp(state.songsFilterTags, 'songsFilterTags', value);
            state.songsFilterTags = value;
        },
        setSongsFilterResultVersion(state, value) {
            setWebvueProp(state.songsFilterResultVersion, 'songsFilterResultVersion', value);
            state.songsFilterResultVersion = value;
        },
        setSongsFilterVersionBoosty(state, value) {
            setWebvueProp(state.songsFilterVersionBoosty, 'songsFilterVersionBoosty', value);
            state.songsFilterVersionBoosty = value;
        },
        setSongsFilterVersionBoostyFiles(state, value) {
            setWebvueProp(state.songsFilterVersionBoostyFiles, 'songsFilterVersionBoostyFiles', value);
            state.songsFilterVersionBoostyFiles = value;
        },
        setSongsFilterVersionSponsr(state, value) {
            setWebvueProp(state.songsFilterVersionSponsr, 'songsFilterVersionSponsr', value);
            state.songsFilterVersionSponsr = value;
        },
        setSongsFilterVersionDzenKaraoke(state, value) {
            setWebvueProp(state.songsFilterVersionDzenKaraoke, 'songsFilterVersionDzenKaraoke', value);
            state.songsFilterVersionDzenKaraoke = value;
        },
        setSongsFilterVersionVkKaraoke(state, value) {
            setWebvueProp(state.songsFilterVersionVkKaraoke, 'songsFilterVersionVkKaraoke', value);
            state.songsFilterVersionVkKaraoke = value;
        },
        setSongsFilterVersionTelegramKaraoke(state, value) {
            setWebvueProp(state.songsFilterVersionTelegramKaraoke, 'songsFilterVersionTelegramKaraoke', value);
            state.songsFilterVersionTelegramKaraoke = value;
        },
        setSongsFilterVersionPlKaraoke(state, value) {
            setWebvueProp(state.songsFilterVersionPlKaraoke, 'songsFilterVersionPlKaraoke', value);
            state.songsFilterVersionPlKaraoke = value;
        },
        setSongsFilterRate(state, value) {
            setWebvueProp(state.songsFilterRate, 'songsFilterRate', value);
            state.songsFilterRate = value;
        },
        setSongsFilterIsSync(state, value) {
            setWebvueProp(state.songsFilterIsSync, 'songsFilterIsSync', value);
            state.songsFilterIsSync = value;
        },
        setSongsFilterStatusProcessLyrics(state, value) {
            setWebvueProp(state.songsFilterStatusProcessLyrics, 'songsFilterStatusProcessLyrics', value);
            state.songsFilterStatusProcessLyrics = value;
        },
        setSongsFilterStatusProcessKaraoke(state, value) {
            setWebvueProp(state.songsFilterStatusProcessKaraoke, 'songsFilterStatusProcessKaraoke', value);
            state.songsFilterStatusProcessKaraoke = value;
        }
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
        setSongsFilterId(ctx, payload) { ctx.commit('setSongsFilterId', payload.value) },
        setSongsFilterSongName(ctx, payload) { ctx.commit('setSongsFilterSongName', payload.value) },
        setSongsFilterSongAuthor(ctx, payload) { ctx.commit('setSongsFilterSongAuthor', payload.value) },
        setSongsFilterSongAlbum(ctx, payload) { ctx.commit('setSongsFilterSongAlbum', payload.value) },
        setSongsFilterPublishDate(ctx, payload) { ctx.commit('setSongsFilterPublishDate', payload.value) },
        setSongsFilterPublishTime(ctx, payload) { ctx.commit('setSongsFilterPublishTime', payload.value) },
        setSongsFilterIdStatus(ctx, payload) { ctx.commit('setSongsFilterIdStatus', payload.value) },
        setSongsFilterCountVoices(ctx, payload) { ctx.commit('setSongsFilterCountVoices', payload.value) },
        setSongsFilterTags(ctx, payload) { ctx.commit('setSongsFilterTags', payload.value) },
        setSongsFilterResultVersion(ctx, payload) { ctx.commit('setSongsFilterResultVersion', payload.value) },
        setSongsFilterVersionBoosty(ctx, payload) { ctx.commit('setSongsFilterVersionBoosty', payload.value) },
        setSongsFilterVersionBoostyFiles(ctx, payload) { ctx.commit('setSongsFilterVersionBoostyFiles', payload.value) },
        setSongsFilterVersionSponsr(ctx, payload) { ctx.commit('setSongsFilterVersionSponsr', payload.value) },
        setSongsFilterVersionDzenKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionDzenKaraoke', payload.value) },
        setSongsFilterVersionVkKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionVkKaraoke', payload.value) },
        setSongsFilterVersionTelegramKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionTelegramKaraoke', payload.value) },
        setSongsFilterVersionPlKaraoke(ctx, payload) { ctx.commit('setSongsFilterVersionPlKaraoke', payload.value) },
        setSongsFilterRate(ctx, payload) { ctx.commit('setSongsFilterRate', payload.value) },
        setSongsFilterIsSync(ctx, payload) { ctx.commit('setSongsFilterIsSync', payload.value) },
        setSongsFilterStatusProcessLyrics(ctx, payload) { ctx.commit('setSongsFilterStatusProcessLyrics', payload.value) },
        setSongsFilterStatusProcessKaraoke(ctx, payload) { ctx.commit('setSongsFilterStatusProcessKaraoke', payload.value) }
    }
}