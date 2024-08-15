import {promisedXMLHttpRequest} from "@/lib/utils";

export default {
    state: {
        publicationsDigest: [],
        publicationsDigestIsLoading: false
    },
    getters: {
        getPublicationsDigest(state) {
            console.log('publicationsDigest: ', state.publicationsDigest);
            return state.publicationsDigest
        },
        getPublicationsDigestIsLoading(state) { return state.publicationsDigestIsLoading }
    },
    mutations: {
        updatePublicationsDigests(state, result) {
            state.publicationsDigest = result.publicationsDigest;
        },
        setPublicationsDigestIsLoading(state, isLoading) { state.publicationsDigestIsLoading = isLoading },
    },
    actions: {
        loadPublicationsDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/publicationsdigest", params: params };
            ctx.commit('setPublicationsDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                // console.log('result: ', result);
                ctx.commit('updatePublicationsDigests', result)
                ctx.commit('setPublicationsDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
    }
}