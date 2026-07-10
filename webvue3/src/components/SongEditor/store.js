import { promisedXMLHttpRequest } from "../../lib/utils";

// Задания онлайн-редактора караоке-разметки (tbl_song_assignments + tbl_song_assignment_drafts).
// target ('local'|'remote') — в запросах просмотра (digest/byId), в assign (где создать — обычно
// сервер, если весь цикл назначение→работа→апрув идёт на PROD) и в approve (читает черновик оттуда,
// где реально идёт работа, но Settings пишет всегда в LOCAL — см. SongEditorController). reject/delete
// ТОЖЕ обязаны получать target — это запись статуса, а не просмотр: если писать не в ту БД, где реально
// работает пользователь (karaoke-web читает только свою единственную БД), правка останется невидимой
// и пользователь не сможет продолжить редактирование после отказа.
export default {
    state: {
        assignmentsDigest: [],
        assignmentsIsLoading: false,
        assignmentsTarget: 'local',
        // Пользователи сайта для выпадающего списка при назначении (только редакторы — filterIsEditor).
        editorSiteUsers: [],
        // Текущее задание (с черновиком) для модалки ревью.
        assignmentCurrent: undefined,
        // БД по умолчанию для заданий редактора (KaraokeProperty editorAssignmentDefaultTarget,
        // 'local'|'remote') — сеет и assignmentsTarget («Задание редактора»), и target для кнопки
        // «Назначить» в таблице/карточке песни.
        defaultTarget: 'remote',
        // Статус назначения по songId (батч, для кнопки «Назначить»/«Назначено» в таблице/карточке песни):
        // songId -> {assignmentId, status, assigneeName}.
        assignmentStatusBySongId: {},
        // Количество заданий "на проверке" — бейдж пункта меню «Задания редактора» в App.vue (по
        // образцу chatUnreadTotal).
        submittedAssignmentsCount: 0,
    },
    getters: {
        getAssignmentsDigest(state) { return state.assignmentsDigest },
        getAssignmentsIsLoading(state) { return state.assignmentsIsLoading },
        getAssignmentsTarget(state) { return state.assignmentsTarget },
        getEditorSiteUsers(state) { return state.editorSiteUsers },
        getAssignmentCurrent(state) { return state.assignmentCurrent },
        getEditorDefaultTarget(state) { return state.defaultTarget },
        getAssignmentStatusBySongId(state) { return state.assignmentStatusBySongId },
        getSubmittedAssignmentsCount(state) { return state.submittedAssignmentsCount },
    },
    mutations: {
        setAssignmentsDigest(state, result) { state.assignmentsDigest = result },
        setAssignmentsIsLoading(state, v) { state.assignmentsIsLoading = v },
        setAssignmentsTarget(state, t) { state.assignmentsTarget = t },
        setEditorSiteUsers(state, list) { state.editorSiteUsers = list },
        setAssignmentCurrent(state, a) { state.assignmentCurrent = a },
        setEditorDefaultTarget(state, t) { state.defaultTarget = t },
        setAssignmentStatusBySongId(state, map) { state.assignmentStatusBySongId = map },
        setSubmittedAssignmentsCount(state, v) { state.submittedAssignmentsCount = v },
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
        // Принимает как голый id (существующий вызов из SongEditorTable), так и {id, target}
        // (кнопка «Назначить» в таблице/карточке песни — там задание может лежать в defaultTarget,
        // отличном от assignmentsTarget «Задания редактора»).
        loadAssignmentById(ctx, payload) {
            const isObj = payload !== null && typeof payload === 'object';
            const id = isObj ? payload.id : payload;
            const target = (isObj && payload.target) || ctx.state.assignmentsTarget;
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/byId", params: { id, target } })
                .then(data => {
                    const result = data ? JSON.parse(data) : null;
                    ctx.commit('setAssignmentCurrent', result);
                    return result;
                });
        },
        // target — необязательное переопределение (используется кнопкой «Назначить» в таблице/карточке
        // песни, где действует defaultTarget, а не assignmentsTarget «Задания редактора»).
        loadEditorSiteUsers(ctx, target) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/siteusers/digest", params: { target: target || ctx.state.assignmentsTarget, filterIsEditor: 'true' } })
                .then(data => {
                    const result = JSON.parse(data);
                    ctx.commit('setEditorSiteUsers', result.siteUsersDigest || []);
                })
                .catch(error => console.log(error));
        },
        setAssignmentsTarget(ctx, target) { ctx.commit('setAssignmentsTarget', target); },
        // Читает KaraokeProperty editorAssignmentDefaultTarget и сеет им и defaultTarget (для кнопки
        // «Назначить» в таблице/карточке песни), и текущий assignmentsTarget («Задание редактора»,
        // пока пользователь не переключил вручную).
        async loadEditorDefaultTarget(ctx) {
            const prop = await ctx.dispatch('getPropertyValuePromise', 'editorAssignmentDefaultTarget');
            const v = prop === 'local' ? 'local' : 'remote';
            ctx.commit('setEditorDefaultTarget', v);
            ctx.commit('setAssignmentsTarget', v);
        },
        // Батч-статус назначений для видимых songId (таблица/карточка песни) — без N+1.
        loadAssignmentStatusBySongIds(ctx, { songIds, target } = {}) {
            if (!songIds || !songIds.length) { ctx.commit('setAssignmentStatusBySongId', {}); return Promise.resolve({}); }
            const params = { songIds: songIds.join(','), target: target || ctx.state.defaultTarget };
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/statusbysongids", params })
                .then(data => {
                    const result = JSON.parse(data);
                    const statuses = result.statuses || {};
                    ctx.commit('setAssignmentStatusBySongId', statuses);
                    return statuses;
                })
                .catch(error => { console.log(error); return {}; });
        },
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
        // (по умолчанию assignmentsTarget, переопределяется явным параметром — см. кнопку «Назначить» в
        // таблице/карточке песни, использующую defaultTarget). clearMarkers — см. комментарий бэкенда
        // (SongEditorController.assign): null → возможен ответ error:"markers_exist", вызывающий код
        // обязан переспросить пользователя и повторить запрос с explicit true/false.
        assignSong(ctx, { songId, assigneeId, clearMarkers, target }) {
            const params = { songId, assigneeId, target: target || ctx.state.assignmentsTarget };
            if (clearMarkers !== undefined && clearMarkers !== null) params.clearMarkers = clearMarkers;
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/assign", params })
                .then(data => JSON.parse(data));
        },
        // target — откуда читать задание/черновик (по умолчанию local); Settings и статус задания
        // бэкенд в любом случае пишет только в LOCAL (см. SongEditorController.approve).
        approveAssignment(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/approve", params: { id, target: ctx.state.assignmentsTarget } })
                .then(data => JSON.parse(data));
        },
        rejectAssignment(ctx, { id, comment }) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/reject", params: { id, comment: comment || '', target: ctx.state.assignmentsTarget } })
                .then(data => JSON.parse(data));
        },
        deleteAssignment(ctx, id) {
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/delete", params: { id, target: ctx.state.assignmentsTarget } })
                .then(data => JSON.parse(data));
        },
        // Бейдж пункта меню «Задания редактора» (App.vue, по образцу loadChatUnreadCount) — target
        // берётся из defaultTarget ('remote' по умолчанию), т.к. реальный рабочий цикл чаще всего
        // идёт целиком на PROD.
        loadSubmittedAssignmentsCount(ctx) {
            const params = { target: ctx.state.defaultTarget };
            return promisedXMLHttpRequest({ method: 'POST', url: "/api/songeditor/submittedcount", params })
                .then(data => { ctx.commit('setSubmittedAssignmentsCount', parseInt(data, 10) || 0); })
                .catch(error => console.log(error));
        },
    }
}
