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
        updateOneProperty(state, prop) {
            if (!prop || typeof prop.key === 'undefined') {
                return; // Выходим, если данные некорректны
            }
            const index = state.propertiesDigest.findIndex(item => item.key === prop.key);
            if (index !== -1) {
                state.propertiesDigest.splice(index, 1, prop);
                // Альтернатива (в новых версиях Vue 2 и Vue 3 это также работает реактивно):
                // state.propertiesDigest[index] = prop;
            }
        },
        setPropertiesDigestIsLoading(state, isLoading) { state.propertiesDigestIsLoading = isLoading }
    },
    actions: {
        updateOneProperty(ctx, prop) {
            ctx.commit('updateOneProperty', prop);
        },
        loadPropertiesDigests(ctx, params) {
            let request = { method: 'POST', url: "/api/propertiesdigests", params: params };
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
            let request = { method: 'POST', url: "/api/properties/setproperty", params: params };
            return promisedXMLHttpRequest(request);
        },
    }
}