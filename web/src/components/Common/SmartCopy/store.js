
export default {
    state: {
        smartCopySongVersion: 'KARAOKE',
        smartCopySongResolution: '1080p',
        smartCopyCreateSubfoldersAuthors: false,
        smartCopyRenameTemplate: '[{author}] ({year}) {name} [{key}]',
        smartCopyPath: ''
    },
    getters: {
        getSmartCopySongVersion(state) { return state.smartCopySongVersion},
        getSmartCopySongResolution(state) { return state.smartCopySongResolution},
        getSmartCopyCreateSubfoldersAuthors(state) { return state.smartCopyCreateSubfoldersAuthors},
        getSmartCopyRenameTemplate(state) { return state.smartCopyRenameTemplate},
        getSmartCopyPath(state) { return state.smartCopyPath}
    },
    mutations: {
        setSmartCopySongVersion(state, smartCopySongVersion) { state.smartCopySongVersion = smartCopySongVersion },
        setSmartCopySongResolution(state, smartCopySongResolution) { state.smartCopySongResolution = smartCopySongResolution },
        setSmartCopyCreateSubfoldersAuthors(state, smartCopyCreateSubfoldersAuthors) { state.smartCopyCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors },
        setSmartCopyRenameTemplate(state, smartCopyRenameTemplate) { state.smartCopyRenameTemplate = smartCopyRenameTemplate },
        setSmartCopyPath(state, smartCopyPath) { state.smartCopyPath = smartCopyPath }
    },
    actions: {
        setSmartCopySongVersion(ctx, payload) { ctx.commit('setSmartCopySongVersion', payload.smartCopySongVersion) },
        setSmartCopySongResolution(ctx, payload) { ctx.commit('setSmartCopySongResolution', payload.smartCopySongResolution) },
        setSmartCopyCreateSubfoldersAuthors(ctx, payload) { ctx.commit('setSmartCopyCreateSubfoldersAuthors', payload.smartCopyCreateSubfoldersAuthors) },
        setSmartCopyRenameTemplate(ctx, payload) { ctx.commit('setSmartCopyRenameTemplate', payload.smartCopyRenameTemplate) },
        setSmartCopyPath(ctx, payload) { ctx.commit('setSmartCopyPath', payload.smartCopyPath) },
    }
}