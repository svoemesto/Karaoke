import {promisedXMLHttpRequest} from "../../../lib/utils";

export default {
    state: {
        fileExplorerCurrentPath: '',
        fileExplorerRootPath: '/',
        fileExplorerFiles: [],
        fileExplorerFilesIsLoading: false,
    },
    getters: {
        getFileExplorerCurrentPath(state) { return state.fileExplorerCurrentPath},
        getFileExplorerRootPath(state) { return state.fileExplorerRootPath},
        getFileExplorerFiles(state) { return state.fileExplorerFiles},
        getFileExplorerFilesIsLoading(state) { return state.fileExplorerFilesIsLoading},
    },
    mutations: {
        setFileExplorerCurrentPath(state, fileExplorerCurrentPath) { state.fileExplorerCurrentPath = fileExplorerCurrentPath },
        setFileExplorerRootPath(state, fileExplorerRootPath) { state.fileExplorerRootPath = fileExplorerRootPath },
        setFileExplorerFiles(state, fileExplorerFiles) { state.fileExplorerFiles = fileExplorerFiles },
        setFileExplorerFilesIsLoading(state, fileExplorerFilesIsLoading) { state.fileExplorerFilesIsLoading = fileExplorerFilesIsLoading },
    },
    actions: {
        setFileExplorerCurrentPath(ctx, payload) { ctx.commit('setFileExplorerCurrentPath', payload.fileExplorerCurrentPath) },
        setFileExplorerRootPath(ctx, payload) { ctx.commit('setFileExplorerRootPath', payload.fileExplorerRootPath) },
        setFileExplorerFiles(ctx, payload) { ctx.commit('setFileExplorerFiles', payload.fileExplorerFiles) },
        setFileExplorerFilesIsLoading(ctx, payload) { ctx.commit('setFileExplorerFilesIsLoading', payload.fileExplorerFilesIsLoading) },
        getFiles(ctx, params) {
            console.log('params', params);
            let request = { method: 'POST', url: "/apis/files", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                console.log('result', result);
                ctx.commit('setFileExplorerFiles', { fileExplorerFiles: result } )
                console.log('result', result);
            }).catch(error => {
                console.log(error);
            });
        }
    }
}