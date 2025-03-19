import { promisedXMLHttpRequest } from '@/lib/utils'

export default {
    state: {
        songsDigest: [],
        songsDigestIsLoading: false,
    },
    getters: {
        getSongsDigest(state) {
            return state.songsDigest
        },
        getSongsDigestIds(state) {
            return state.songsDigest ? state.songsDigest.flatMap(song => song.id) : []
        },
        getSongsDigestIsLoading(state) { return state.songsDigestIsLoading },
        songAuthorsPromise() {
            let request = { method: 'POST', url: "/apis/songs/authors"};
            return promisedXMLHttpRequest(request);
        },
    },
    mutations: {
        updateSongsDigests(state, result) {
            state.songsDigest = result.songsDigests;
        },
        setSongsDigestIsLoading(state, isLoading) { state.songsDigestIsLoading = isLoading },
        updateSongsDigestByIds(state, songsAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {

                for (let i = 0; i < state.songsDigest.length; i++) {
                    let songInDigest = state.songsDigest[i];
                    if (songInDigest.id === songsAndIndexesFromRest.song.id) {
                        state.songsDigest.splice(i,1,songsAndIndexesFromRest.song);
                    }
                }
            }
        },
        updateSongByUserEvent(state, userEventData) {
            let songId = userEventData.recordId;
            for (let i = 0; i < state.songsDigest.length; i++) {
                let songInDigest = state.songsDigest[i];
                if (songInDigest.id === songId) {
                    state.songsDigest.splice(i,1,userEventData.record);
                }
            }
        },
        addSongByUserEvent(state, userEventData) {
            console.log('Событие добавления песни: ', userEventData)
        },
        deleteSongByUserEvent(state, userEventData) {
            console.log('Событие удаления песни: ', userEventData)
        }
    },
    actions: {
        updateSongsDigestByIds(ctx, payload) {
            ctx.commit('updateSongsDigestByIds', payload.songsAndIndexesForUpdate);
        },
        updateSongByUserEvent(ctx, userEventData) {
            ctx.commit('updateSongByUserEvent', userEventData);
        },
        addSongByUserEvent(ctx, userEventData) {
            ctx.commit('addSongByUserEvent', userEventData);
        },
        deleteSongByUserEvent(ctx, userEventData) {
            ctx.commit('addSongByUserEvent', userEventData);
        },
        loadSongsDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/songsdigests", params: params };
            ctx.commit('setSongsDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateSongsDigests', result)
                ctx.commit('setSongsDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        loadSongsDigestsPromise(ctx, params) {
            let request = { method: 'POST', url: "/apis/songsdigests", params: params };
            return promisedXMLHttpRequest(request)
        },
        searchTextForAll(ctx) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';') };
            let request = { method: 'POST', url: "/apis/songs/searchsongtextall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createKaraokeForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), priorLyrics: payload.priorLyrics, priorKaraoke: payload.priorKaraoke, priorChords: payload.priorChords, priorMelody: payload.priorMelody };
            let request = { method: 'POST', url: "/apis/songs/createkaraokeall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs2ForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createdemucs2all", params: params };
            return promisedXMLHttpRequest(request);
        },
        createDemucs5ForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createdemucs5all", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSheetsageForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/sheetsageall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3KaraokeForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createmp3karaokeall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createMP3LyricsForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createmp3lyricsall", params: params };
            return promisedXMLHttpRequest(request);
        },
        createSymlinksForAllPromise(ctx, payload) {
            let params = { songsIds: ctx.getters.getSongsDigestIds.join(';'), prior: payload.prior };
            let request = { method: 'POST', url: "/apis/songs/createsymlinksall", params: params };
            return promisedXMLHttpRequest(request);
        },
    }
}