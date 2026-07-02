import {promisedXMLHttpRequest} from "../../lib/utils";

// Пользователи ПУБЛИЧНОГО САЙТА (tbl_site_users) — не путать с components/Users (tbl_users, админские
// логины webvue3). siteUsersTarget ('local'|'remote') добавляется в каждый запрос к бэкенду и заставляет
// SiteUsersController явно выбрать Connection.local()/remote() — реальные посетители сайта регистрируются
// на боевой БД сервера, а не в локальной dev-БД.
export default {
    state: {
        siteUsersDigest: [],
        siteUsersDigestIsLoading: false,
        siteUserCurrent: undefined,
        siteUserSnapshot: undefined,
        siteUserCurrentId: 0,
        siteUsersTarget: 'local'
    },
    getters: {
        getSiteUsersDigest(state) { return state.siteUsersDigest },
        getSiteUsersDigestIsLoading(state) { return state.siteUsersDigestIsLoading },
        getSiteUserCurrent(state) { return state.siteUserCurrent },
        getSiteUserSnapshot(state) { return state.siteUserSnapshot },
        getSiteUsersTarget(state) { return state.siteUsersTarget },
        getSiteUserDiff(state) {
            let result = [];
            if (state.siteUserCurrent && state.siteUserSnapshot) {
                for (let key of Object.keys(state.siteUserCurrent)) {
                    let oldValue = state.siteUserSnapshot[key];
                    let newValue = state.siteUserCurrent[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result;
        },
    },
    mutations: {
        updateSiteUsersDigest(state, result) {
            const usersToUpdate = Array.isArray(result) ? result : [result];
            usersToUpdate.forEach(updatedUser => {
                const index = state.siteUsersDigest.findIndex(u => u.id === updatedUser.id);
                if (index !== -1) state.siteUsersDigest.splice(index, 1, updatedUser);
            });
        },
        setSiteUsersDigest(state, result) { state.siteUsersDigest = result },
        setSiteUsersDigestIsLoading(state, isLoading) { state.siteUsersDigestIsLoading = isLoading },
        setSiteUserCurrentId(state, id) { state.siteUserCurrentId = id },
        setSiteUserCurrent(state, user) { state.siteUserCurrent = Object.assign({}, user) },
        setSiteUserSnapshot(state, user) { state.siteUserSnapshot = Object.assign({}, user) },
        saveSiteUser(state) {
            state.siteUserSnapshot = !state.siteUserCurrent ? undefined : Object.assign({}, state.siteUserCurrent)
        },
        setSiteUsersTarget(state, target) { state.siteUsersTarget = target }
    },
    actions: {
        loadSiteUsersDigest(ctx, params) {
            const fullParams = Object.assign({}, params, { target: ctx.state.siteUsersTarget });
            let request = { method: 'POST', url: "/api/siteusers/digest", params: fullParams };
            ctx.commit('setSiteUsersDigestIsLoading', true);
            return promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setSiteUsersDigest', result.siteUsersDigest);
                ctx.commit('setSiteUsersDigestIsLoading', false);
            }).catch(error => {
                ctx.commit('setSiteUsersDigestIsLoading', false);
                console.log(error);
            });
        },
        loadOneSiteUserRecord(ctx, id) {
            let request = { method: 'POST', url: "/api/siteusers/byId", params: { id, target: ctx.state.siteUsersTarget } };
            return promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                if (result) ctx.commit('updateSiteUsersDigest', result);
            }).catch(error => { console.log(error) });
        },
        setSiteUserCurrent(ctx, user) { ctx.commit('setSiteUserCurrent', user) },
        setSiteUserSnapshot(ctx, user) { ctx.commit('setSiteUserSnapshot', user) },
        setSiteUsersTarget(ctx, target) { ctx.commit('setSiteUsersTarget', target) },
        saveSiteUser(ctx, diffs) {
            let params = { id: ctx.state.siteUserCurrentId, target: ctx.state.siteUsersTarget };
            if (diffs.displayName !== undefined) params.displayName = diffs.displayName;
            if (diffs.sponsrUid !== undefined) params.sponsrUid = diffs.sponsrUid;
            if (diffs.premium !== undefined) params.isPremium = diffs.premium;
            let request = { method: 'POST', url: "/api/siteusers/update", params: params };
            return promisedXMLHttpRequest(request).then(() => {
                ctx.commit('saveSiteUser');
                ctx.commit('updateSiteUsersDigest', ctx.state.siteUserCurrent);
            });
        },
        banSiteUserCurrent(ctx, reason) {
            let request = { method: 'POST', url: "/api/siteusers/ban", params: { id: ctx.state.siteUserCurrentId, target: ctx.state.siteUsersTarget, reason: reason || '' } };
            return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadOneSiteUserRecord', ctx.state.siteUserCurrentId));
        },
        unbanSiteUserCurrent(ctx) {
            let request = { method: 'POST', url: "/api/siteusers/unban", params: { id: ctx.state.siteUserCurrentId, target: ctx.state.siteUsersTarget } };
            return promisedXMLHttpRequest(request).then(() => ctx.dispatch('loadOneSiteUserRecord', ctx.state.siteUserCurrentId));
        },
        deleteSiteUserCurrent(ctx) {
            let request = { method: 'POST', url: "/api/siteusers/delete", params: { id: ctx.state.siteUserCurrentId, target: ctx.state.siteUsersTarget } };
            return promisedXMLHttpRequest(request);
        }
    }
}
