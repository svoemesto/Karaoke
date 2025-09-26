import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        picturesDigest: [],
        picturesDigestIsLoading: false,
        pictureCurrent: undefined,
        pictureSnapshot: undefined,
        pictureCurrentId: 0
    },
    getters: {
        getPicturesDigest(state) { return state.picturesDigest },
        getPicturesDigestIsLoading(state) { return state.picturesDigestIsLoading },
        getPictureCurrent(state) { return state.pictureCurrent },
        getPictureSnapshot(state) { return state.pictureSnapshot },
        getPictureDiff(state) {
            let result = [];
            if (state.pictureCurrent && state.pictureSnapshot) {
                for (let key of Object.keys(state.pictureCurrent)) {
                    let oldValue = state.pictureSnapshot[key];
                    let newValue = state.pictureCurrent[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result;
        },
        // getPropValue: () => async (key) => {
        //     let prop = JSON.parse(await promisedXMLHttpRequest({
        //         method: 'POST',
        //         url: "/apis/properties/getproperty",
        //         params: { key: key }
        //     }));
        //     console.log(`key ${key} prop.property`, prop.property);
        //     return prop.property.value;
        // },
        loadPictureFromDiskBase64: () => async (pathToFile) => {
            let base64 = await promisedXMLHttpRequest({
                method: 'POST',
                url: "/apis/picture/loadfromdisk",
                params: { pathToFile: pathToFile }
            });
            return base64;
        },
        getPictureValuePromise(state) {
            let request = { method: 'POST', url: "/apis/picture", params: { id: state.pictureCurrentId} };
            return promisedXMLHttpRequest(request);
        },
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
        setPicturesDigests(state, result) { state.picturesDigest = result.picturesDigests },
        setPicturesDigestIsLoading(state, isLoading) { state.picturesDigestIsLoading = isLoading },
        setPictureCurrentId(state, id) { state.pictureCurrentId = id },
        setPictureCurrent(state, picture) { state.pictureCurrent = Object.assign({}, picture) },
        setPictureSnapshot(state, picture) { state.pictureSnapshot = Object.assign({}, picture)},
        setPictureCurrentField(state, payload) { state.pictureCurrent[payload.name] = payload.value },
        savePicture(state) { state.pictureSnapshot = !state.pictureCurrent ? undefined : Object.assign({}, state.pictureCurrent) },
        async deletePictureCurrent(state) {
            let request = { method: 'POST', url: "/apis/picture/delete", params: { id: state.pictureCurrentId } };
            await promisedXMLHttpRequest(request);
        },
        async savePictureCurrentToDisk(state) {
            let request = { method: 'POST', url: "/apis/picture/savetodisk", params: { id: state.pictureCurrentId } };
            await promisedXMLHttpRequest(request);
        },
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
        setPictureCurrent(ctx, picture) { ctx.commit('setPictureCurrent', picture) },
        setPictureSnapshot(ctx, picture) { ctx.commit('setPictureSnapshot', picture) },
        setPictureCurrentField(ctx, payload) { ctx.commit('setPictureCurrentField', payload) },
        savePicture(ctx, diffs) {
            let params = { id: ctx.state.pictureCurrentId }
            if (diffs.name) params.name = diffs.name;
            if (diffs.full) params.full = diffs.full;
            let request = { method: 'POST', url: "/apis/pictures/updatepicture", params: params };
            promisedXMLHttpRequest(request).then(() => {
                ctx.commit('savePicture')
            }).catch(error => {
                console.log(error);
            });
        },
    }
}