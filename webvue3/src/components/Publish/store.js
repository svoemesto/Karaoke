import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        publishDigest: [],
        publishDigestIsLoading: false
    },
    getters: {
        getPublishDigest(state) {
            // console.log('publishDigest: ', state.publishDigest);
            return state.publishDigest
        },
        getPublishDigestIsLoading(state) { return state.publishDigestIsLoading }
    },
    mutations: {
        updatePublishDigest(state, result) {
            state.publishDigest = result;
        },
        setPublishDigestIsLoading(state, isLoading) { state.publishDigestIsLoading = isLoading },
        updatePublishDigestByIds(state, songsAndIndexesForUpdate) {
            // console.log('songsAndIndexesForUpdate', songsAndIndexesForUpdate)

            for (let songsAndIndexesFromRest of songsAndIndexesForUpdate) {
                for (let i = 0; i < state.publishDigest.length; i++) {
                    let publishRow = state.publishDigest[i];
                    for (let j = 0; j < publishRow.csrCells.length; j++) {
                        let csrCell = publishRow.csrCells[j];
                        if (csrCell.settingsDTO && csrCell.settingsDTO.id === songsAndIndexesFromRest.song.id) {
                            csrCell.settingsDTO = songsAndIndexesFromRest.song;
                            state.publishDigest.splice(i,1, publishRow);
                        }
                    }
                }
            }
        },
        updatePublishDigestByUserEvent(state, userEventData) {
            let songId = userEventData.recordId;
            for (let i = 0; i < state.publishDigest.length; i++) {
                let publishRow = state.publishDigest[i];
                for (let j = 0; j < publishRow.csrCells.length; j++) {
                    let csrCell = publishRow.csrCells[j];
                    if (csrCell.settingsDTO && csrCell.settingsDTO.id === songId) {
                        csrCell.settingsDTO = userEventData.record;
                        state.publishDigest.splice(i,1, publishRow);
                    }
                }
            }
        },
        addPublishDigestByUserEvent(state, userEventData) {
            console.log('Событие добавления песни из публикаций: ', userEventData)
        },
        deletePublishDigestByUserEvent(state, userEventData) {
            console.log('Событие удаления песни из публикаций: ', userEventData)
        }
    },
    actions: {
        updatePublishDigestByUserEvent(ctx, userEventData) {
            ctx.commit('updatePublishDigestByUserEvent', userEventData);
        },
        addPublishDigestByUserEvent(ctx, userEventData) {
            ctx.commit('addPublishDigestByUserEvent', userEventData);
        },
        deletePublishDigestByUserEvent(ctx, userEventData) {
            ctx.commit('deletePublishDigestByUserEvent', userEventData);
        },
        updatePublishDigestByIds(ctx, payload) {
            ctx.commit('updatePublishDigestByIds', payload.songsAndIndexesForUpdate);
        },
        async getPublicationsDateFrom(ctx, param) {
            let request = { method: 'POST', url: "/api/publications/date", params: param };
            return await promisedXMLHttpRequest(request);
        },
        loadPublishDigest(ctx, params) {
            let request = { method: 'POST', url: "/api/publicationsdigest", params: params };
            ctx.commit('setPublishDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                // console.log('result: ', result);
                ctx.commit('updatePublishDigest', result.publicationsDigest)
                ctx.commit('setPublishDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        }
    }
}