import {promisedXMLHttpRequest} from "@/lib/utils";

export default {
    state: {
        picturesDigest: [],
        picturesDigestIsLoading: false,
    },
    getters: {
        getPicturesDigest(state) { return state.picturesDigest },
        getPicturesDigestIsLoading(state) { return state.picturesDigestIsLoading }
    },
    mutations: {
        updatePicturesDigests(state, result) {
            const picturesToUpdate = Array.isArray(result) ? result : [result];
            picturesToUpdate.forEach(updatedPicture => {
                const index = state.picturesDigest.findIndex(picture => picture.id === updatedPicture.id);
                if (index !== -1) {
                    state.picturesDigest.splice(index, 1, updatedPicture);
                }
            });
        },
        setPicturesDigests(state, result) {
            state.picturesDigest = result.picturesDigests;
        },
        setPicturesDigestIsLoading(state, isLoading) { state.picturesDigestIsLoading = isLoading }
    },
    actions: {
        loadOneRecord(ctx, id) {
            const params = {filter_id: id};
            let request = { method: 'POST', url: "/apis/pictures/picturesdigests", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updatePicturesDigests', result.picturesDigests)
            }).catch(error => {
                console.log(error);
            });
        },
        loadPicturesDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/pictures/picturesdigests", params: params };
            ctx.commit('setPicturesDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setPicturesDigests', result)
                ctx.commit('setPicturesDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        setPictureValuePromise(ctx, payload) {
            let request = { method: 'POST', url: "/apis/pictures/updatepicture", params: payload };
            return promisedXMLHttpRequest(request);
        },
    }
}