import {promisedXMLHttpRequest} from "../../lib/utils";

export default {
    state: {
        usersDigest: [],
        usersDigestIsLoading: false,
        userCurrent: undefined,
        userSnapshot: undefined,
        userCurrentId: 0
    },
    getters: {
        getUsersDigest(state) { return state.usersDigest },
        getUsersDigestIsLoading(state) { return state.usersDigestIsLoading },
        getUserCurrent(state) { return state.userCurrent },
        getUserSnapshot(state) { return state.userSnapshot },
        getUserDiff(state) {
            let result = [];
            if (state.userCurrent && state.userSnapshot) {
                for (let key of Object.keys(state.userCurrent)) {
                    let oldValue = state.userSnapshot[key];
                    let newValue = state.userCurrent[key];
                    if (oldValue !== newValue) {
                        result.push({name: key, new: newValue, old: oldValue});
                    }
                }
            }
            return result;
        },
        getUserValuePromise(state) {
            let request = { method: 'POST', url: "/api/users/byId", params: { id: state.userCurrentId} };
            return promisedXMLHttpRequest(request);
        },
    },
    mutations: {
        updateUsersDigest(state, result) {
            const usersToUpdate = Array.isArray(result) ? result : [result];
            usersToUpdate.forEach(updatedUser => {
                const index = state.usersDigest.findIndex(user => user.id === updatedUser.id);
                if (index !== -1) {
                    state.usersDigest.splice(index, 1, updatedUser);
                }
            });
        },
        setUsersDigest(state, result) {
            state.usersDigest = result;
        },
        setUsersDigestIsLoading(state, isLoading) { state.usersDigestIsLoading = isLoading },
        setUserCurrentId(state, id) { state.userCurrentId = id },
        setUserCurrent(state, user) { state.userCurrent = Object.assign({}, user) },
        setUserSnapshot(state, user) { state.userSnapshot = Object.assign({}, user)},
        setUserCurrentField(state, payload) { state.userCurrent[payload.name] = payload.value },
        saveUser(state) {
            state.userSnapshot = !state.userCurrent ? undefined : Object.assign({}, state.userCurrent)
        }
    },
    actions: {
        async deleteUserCurrent(ctx) {
            let request = { method: 'POST', url: "/api/users/delete", params: { id: ctx.state.userCurrentId } };
            return await promisedXMLHttpRequest(request);
        },
        async resetPasswordUserCurrent(ctx) {
            let request = { method: 'POST', url: "/api/users/resetPassword", params: { login: ctx.state.userCurrent.login } };
            return await promisedXMLHttpRequest(request);
        },
        async checkPasswordUserCurrent(ctx, password) {
            let request = { method: 'POST', url: "/api/users/checkPassword", params: { login: ctx.state.userCurrent.login, password: password } };
            return await promisedXMLHttpRequest(request);
        },
        async changePasswordUserCurrent(ctx, newPassword, oldPassword) {
            let request = { method: 'POST', url: "/api/users/changePassword", params: { login: ctx.state.userCurrent.login, newPassword: newPassword ? newPassword : '', oldPassword: oldPassword ? oldPassword : '' } };
            return await promisedXMLHttpRequest(request);
        },
        loadOneRecord(ctx, id) {
            const params = {filter_id: id};
            let request = { method: 'POST', url: "/api/users/digest", params: params };
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('updateUsersDigest', result.usersDigest)
            }).catch(error => {
                console.log(error);
            });
        },
        loadUsersDigest(ctx, params) {
            let request = { method: 'POST', url: "/api/users/digest", params: params };
            ctx.commit('setUsersDigestIsLoading', true);
            promisedXMLHttpRequest(request).then(data => {
                let result = JSON.parse(data);
                ctx.commit('setUsersDigest', result.usersDigest)
                ctx.commit('setUsersDigestIsLoading', false);
            }).catch(error => {
                console.log(error);
            });
        },
        setUserValuePromise(ctx, payload) {
            let request = { method: 'POST', url: "/api/users/update", params: payload };
            return promisedXMLHttpRequest(request);
        },
        createUserValuePromise(ctx, payload) {
            let request = { method: 'POST', url: "/api/users/create", params: payload };
            return promisedXMLHttpRequest(request);
        },
        setUserCurrent(ctx, user) { ctx.commit('setUserCurrent', user) },
        setUserSnapshot(ctx, user) { ctx.commit('setUserSnapshot', user) },
        setUserCurrentField(ctx, payload) { ctx.commit('setUserCurrentField', payload) },
        saveUser(ctx, diffs) {
            let params = { id: ctx.state.userCurrentId }
            if (diffs.login !== undefined) params.login = diffs.login;
            if (diffs.email !== undefined) params.email = diffs.email;
            if (diffs.firstName !== undefined) params.firstName = diffs.firstName;
            if (diffs.lastName !== undefined) params.lastName = diffs.lastName;
            if (diffs.groups !== undefined) params.groups = diffs.groups;
            let request = { method: 'POST', url: "/api/users/update", params: params };
            promisedXMLHttpRequest(request).then(() => {
                ctx.commit('saveUser');
                ctx.commit('updateUsersDigest', ctx.state.userCurrent);
                ctx.commit('setUserCurrent', ctx.state.userCurrent)
            }).catch(error => {
                console.log(error);
            });
        }
    }
}