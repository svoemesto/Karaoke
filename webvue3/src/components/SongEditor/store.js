import { promisedXMLHttpRequest } from "../../lib/utils";

// Задания онлайн-редактора караоке-разметки (tbl_song_assignments + tbl_song_assignment_drafts).
// target ('local'|'remote') добавляется в запросы просмотра (digest/byId). assign/approve/reject/delete
// на бэкенде всегда работают с LOCAL (authority назначений — локальная БД, см. SongEditorController),
// поэтому им target не нужен.
export default {
    state: {
        assignmentsDigest: [],
        assignmentsIsLoading: false,
        assignmentsTarget: 'local',
        // Пользователи сайта для выпадающего списка при назначении.
        editorSiteUsers: [],
        // Текущее задание (с черновиком) для модалки ревью.
        assignmentCurrent: undefined,
    },
    getters: {
        getAssignmentsDigest(state) { return state.assignmentsDigest },
        getAssignmentsIsLoading(state) { return state.assignmentsIsLoading },
        getAssignmentsTarget(state) { return state.assignmentsTarget },
        getEditorSiteUsers(state) { return state.editorSiteUsers },
        getAssignmentCurrent(state) { return state.assignmentCurrent },
    },
    mutations: {
        setAssignmentsDigest(state, result) { state.assignmentsDigest = result },
        setAssignmentsIsLoading(state, v) { state.assignmentsIsLoading = v },
        setAssignmentsTarget(state, t) { state.assignmentsTarget = t },
        setEditorSiteUsers(state, list) { state.editorSiteUsers = list },
        setAssignmentCurrent(state, a) { state.assignmentCurrent = a },
    },
    actions: {
        loadAssignmentsDigest(ctx, params) {
            const fullParams = Object.assign({}, params, { target: ctx.state.assignmentsTarget });
            ctx.commit('setAssignmentsIsLoading', true);
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/digest", params: fullParams })
                .then(data => {
                    const result = JSON.parse(data);
                    ctx.commit('setAssignmentsDigest', result.songAssignmentsDigest || []);
                    ctx.commit('setAssignmentsIsLoading', false);
                })
                .catch(error => { ctx.commit('setAssignmentsIsLoading', false); console.log(error); });
        },
        loadAssignmentById(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/byId", params: { id, target: ctx.state.assignmentsTarget } })
                .then(data => {
                    const result = data ? JSON.parse(data) : null;
                    ctx.commit('setAssignmentCurrent', result);
                    return result;
                });
        },
        loadEditorSiteUsers(ctx) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/siteusers/digest", params: { target: ctx.state.assignmentsTarget } })
                .then(data => {
                    const result = JSON.parse(data);
                    ctx.commit('setEditorSiteUsers', result.siteUsersDigest || []);
                })
                .catch(error => console.log(error));
        },
        setAssignmentsTarget(ctx, target) { ctx.commit('setAssignmentsTarget', target); },
        assignSong(ctx, { songId, assigneeId, voice }) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/assign", params: { songId, assigneeId, voice: voice || 0 } })
                .then(data => JSON.parse(data));
        },
        approveAssignment(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/approve", params: { id } })
                .then(data => JSON.parse(data));
        },
        rejectAssignment(ctx, { id, comment }) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/reject", params: { id, comment: comment || '' } })
                .then(data => JSON.parse(data));
        },
        deleteAssignment(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/delete", params: { id } })
                .then(data => JSON.parse(data));
        },
    }
}
