import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        propertiesDigest: [],
        propertiesDigestIsLoading: false,
    },
    getters: {
        getPropertiesDigest(state) { return state.propertiesDigest },
        getPropertiesDigestIsLoading(state) { return state.propertiesDigestIsLoading }
    },
    mutations: {
        updatePropertiesDigests(state, result) {
            state.propertiesDigest = result.propertiesDigests;
        },
        setPropertiesDigestIsLoading(state, isLoading) { state.propertiesDigestIsLoading = isLoading }
    },
    actions: {
        loadPropertiesDigests(ctx, params) {
            let request = { method: 'POST', url: "/apis/propertiesdigests", params: params };
            ctx.commit('setPropertiesDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updatePropertiesDigests', result)
                ctx.commit('setPropertiesDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        setPropertyValuePromise(ctx, payload) {
            let params = { key: payload.propertyKey, stringValue: payload.propertyValue };
            let request = { method: 'POST', url: "/apis/properties/setproperty", params: params };
            return promisedXMLHttpRequest(request);
        },
    }
}