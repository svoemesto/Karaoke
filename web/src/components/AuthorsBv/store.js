import {promisedXMLHttpRequest} from "@/lib/utils";

export default {
    state: {
        authorsDigest: [],
        authorsDigestIsLoading: false,
    },
    getters: {
        getAuthorsDigest(state) { return state.authorsDigest },
        getAuthorsDigestIsLoading(state) { return state.authorsDigestIsLoading }
    },
    mutations: {
        updateAuthorsDigests(state, result) {
            state.authorsDigest = result.authorsDigests;
        },
        setAuthorsDigestIsLoading(state, isLoading) { state.authorsDigestIsLoading = isLoading }
    },
    actions: {
        loadAuthorsDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/authorsdigests", params: params };
            ctx.commit('setAuthorsDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateAuthorsDigests', result)
                ctx.commit('setAuthorsDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        }
    }
}