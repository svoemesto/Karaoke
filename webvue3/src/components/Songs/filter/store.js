import {promisedXMLHttpRequest} from "../../../lib/utils";

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
            // console.log('songHistory:', result.history);
        },
        setSongsHistoryIsLoading(state, isLoading) { state.songsHistoryIsLoading = isLoading },

        setSongsFilterId(state, value) {
            if (state.songsFilterId !== undefined && state.songsFilterId !== null && value !== undefined && value !== null) {
                const key = 'songsFilterId';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterId = value
        },
        setSongsFilterSongName(state, value) {
            if (state.songsFilterSongName !== undefined && state.songsFilterSongName !== null && value !== undefined && value !== null) {
                const key = 'songsFilterSongName';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterSongName = value
        },
        setSongsFilterSongAuthor(state, value) {
            console.log('setSongsFilterSongAuthor', value);
            if (state.songsFilterSongAuthor !== undefined && state.songsFilterSongAuthor !== null && value !== undefined && value !== null) {
                const key = 'songsFilterSongAuthor';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterSongAuthor = value
        },
        setSongsFilterSongAlbum(state, value) {
            console.log('setSongsFilterSongAlbum', value);
            if (state.songsFilterSongAlbum !== undefined && state.songsFilterSongAlbum !== null && value !== undefined && value !== null) {
                const key = 'songsFilterSongAlbum';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterSongAlbum = value
        },
        setSongsFilterPublishDate(state, value) {
            if (state.songsFilterPublishDate !== undefined && state.songsFilterPublishDate !== null && value !== undefined && value !== null) {
                const key = 'songsFilterPublishDate';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterPublishDate = value
        },
        setSongsFilterPublishTime(state, value) {
            if (state.songsFilterPublishTime !== undefined && state.songsFilterPublishTime !== null && value !== undefined && value !== null) {
                const key = 'songsFilterPublishTime';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterPublishTime = value
        },
        setSongsFilterIdStatus(state, value) {
            if (state.songsFilterIdStatus !== undefined && state.songsFilterIdStatus !== null && value !== undefined && value !== null) {
                const key = 'songsFilterIdStatus';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterIdStatus = value
        },
        setSongsFilterCountVoices(state, value) {
            if (state.songsFilterCountVoices !== undefined && state.songsFilterCountVoices !== null && value !== undefined && value !== null) {
                const key = 'songsFilterCountVoices';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterCountVoices = value
        },
        setSongsFilterTags(state, value) {
            if (state.songsFilterTags !== undefined && state.songsFilterTags !== null && value !== undefined && value !== null) {
                const key = 'songsFilterTags';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterTags = value
        },
        setSongsFilterResultVersion(state, value) {
            if (state.songsFilterResultVersion !== undefined && state.songsFilterResultVersion !== null && value !== undefined && value !== null) {
                const key = 'songsFilterResultVersion';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterResultVersion = value
        },
        setSongsFilterVersionBoosty(state, value) {
            if (state.songsFilterVersionBoosty !== undefined && state.songsFilterVersionBoosty !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionBoosty';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionBoosty = value
        },
        setSongsFilterVersionBoostyFiles(state, value) {
            if (state.songsFilterVersionBoostyFiles !== undefined && state.songsFilterVersionBoostyFiles !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionBoostyFiles';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionBoostyFiles = value
        },
        setSongsFilterVersionSponsr(state, value) {
            if (state.songsFilterVersionSponsr !== undefined && state.songsFilterVersionSponsr !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionSponsr';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionSponsr = value
        },
        setSongsFilterVersionDzenKaraoke(state, value) {
            if (state.songsFilterVersionDzenKaraoke !== undefined && state.songsFilterVersionDzenKaraoke !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionDzenKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionDzenKaraoke = value
        },
        setSongsFilterVersionVkKaraoke(state, value) {
            if (state.songsFilterVersionVkKaraoke !== undefined && state.songsFilterVersionVkKaraoke !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionVkKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionVkKaraoke = value
        },
        setSongsFilterVersionTelegramKaraoke(state, value) {
            if (state.songsFilterVersionTelegramKaraoke !== undefined && state.songsFilterVersionTelegramKaraoke !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionTelegramKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionTelegramKaraoke = value
        },
        setSongsFilterVersionPlKaraoke(state, value) {
            if (state.songsFilterVersionPlKaraoke !== undefined && state.songsFilterVersionPlKaraoke !== null && value !== undefined && value !== null) {
                const key = 'songsFilterVersionPlKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterVersionPlKaraoke = value
        },
        setSongsFilterRate(state, value) {
            if (state.songsFilterRate !== undefined && state.songsFilterRate !== null && value !== undefined && value !== null) {
                const key = 'songsFilterRate';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterRate = value
        },
        setSongsFilterStatusProcessLyrics(state, value) {
            if (state.songsFilterStatusProcessLyrics !== undefined && state.songsFilterStatusProcessLyrics !== null && value !== undefined && value !== null) {
                const key = 'songsFilterStatusProcessLyrics';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterStatusProcessLyrics = value
        },
        setSongsFilterStatusProcessKaraoke(state, value) {
            if (state.songsFilterStatusProcessKaraoke !== undefined && state.songsFilterStatusProcessKaraoke !== null && value !== undefined && value !== null) {
                const key = 'songsFilterStatusProcessKaraoke';
                promisedXMLHttpRequest({
                    method: 'POST',
                    url: "/apis/setwebvueprop",
                    params: {key: key, value: value}
                });
            }
            state.songsFilterStatusProcessKaraoke = value
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
        setSongsFilterStatusProcessLyrics(ctx, payload) { ctx.commit('setSongsFilterStatusProcessLyrics', payload.value) },
        setSongsFilterStatusProcessKaraoke(ctx, payload) { ctx.commit('setSongsFilterStatusProcessKaraoke', payload.value) }
    }
}