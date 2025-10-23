import {promisedXMLHttpRequest} from "../../lib/utils";

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
            const authorsToUpdate = Array.isArray(result) ? result : [result];
            authorsToUpdate.forEach(updatedAuthor => {
                const index = state.authorsDigest.findIndex(author => author.id === updatedAuthor.id);
                if (index !== -1) {
                    state.authorsDigest.splice(index, 1, updatedAuthor);
                }
            });
        },
        setAuthorsDigests(state, result) {
            state.authorsDigest = result.authorsDigests;
        },
        setAuthorsDigestIsLoading(state, isLoading) { state.authorsDigestIsLoading = isLoading }
    },
    actions: {
        loadOneRecord(ctx, id) {
            const params = {filter_id: id};
            let request = { method: 'POST', url: "/api/authors/authorsdigests", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateAuthorsDigests', result.authorsDigests)
            }).catch(error => {
                console.log(error);
            });
        },
        loadAuthorsDigests(ctx, params) {
            let request = { method: 'POST', url: "/api/authors/authorsdigests", params: params };
            ctx.commit('setAuthorsDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setAuthorsDigests', result)
                ctx.commit('setAuthorsDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        setAuthorValuePromise(ctx, payload) {
            let request = { method: 'POST', url: "/api/authors/updateauthor", params: payload };
            return promisedXMLHttpRequest(request);
        },
    }
}