import { promisedXMLHttpRequest } from "../../lib/utils";

// Задания онлайн-редактора караоке-разметки (tbl_song_assignments + tbl_song_assignment_drafts).
// target ('local'|'remote') — в запросах просмотра (digest/byId), в assign (где создать — обычно
// сервер, если весь цикл назначение→работа→апрув идёт на PROD) и в approve (читает черновик оттуда,
// где реально идёт работа, но Settings пишет всегда в LOCAL — см. SongEditorController). reject/delete
// target игнорируют: id совпадает в обеих БД, поэтому они безопасны из любого вида без уточнения.
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
        // Быстрый поиск песен-кандидатов для AssignModal (по умолчанию только id_status=1 — TEXT_CREATE).
        searchCandidateSongs(ctx, { query, author, album, onlyStatus1 }) {
            const params = {};
            if (query) params.filterSongName = query;
            if (author) params.filterAuthor = author;
            if (album) params.filterAlbum = album;
            if (onlyStatus1) params.filterStatus = '1';
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songsdigests", params })
                .then(data => {
                    const result = JSON.parse(data);
                    return result.songsDigests || [];
                });
        },
        // Задание покрывает всю песню (все голоса) — voice больше не передаётся. target — где создать
        // (по умолчанию local); реальный цикл работы часто идёт целиком на сервере.
        assignSong(ctx, { songId, assigneeId }) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/assign", params: { songId, assigneeId, target: ctx.state.assignmentsTarget } })
                .then(data => JSON.parse(data));
        },
        // target — откуда читать задание/черновик (по умолчанию local); Settings и статус задания
        // бэкенд в любом случае пишет только в LOCAL (см. SongEditorController.approve).
        approveAssignment(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/approve", params: { id, target: ctx.state.assignmentsTarget } })
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
