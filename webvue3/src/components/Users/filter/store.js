import { setWebvueProp } from "../../../lib/utils";
export default {
    state: {
        usersFilterId: '',
        usersFilterLogin: '',
        usersFilterEmail: '',
        usersFilterFirstName: '',
        usersFilterLastName: '',
        usersFilterGroups: ''
    },
    getters: {
        getUsersFilterId(state) { return state.usersFilterId},
        getUsersFilterLogin(state) { return state.usersFilterLogin},
        getUsersFilterEmail(state) { return state.usersFilterEmail},
        getUsersFilterFirstName(state) { return state.usersFilterFirstName},
        getUsersFilterLastName(state) { return state.usersFilterLastName},
        getUsersFilterGroups(state) { return state.usersFilterGroups},

    },
    mutations: {
        setUsersFilterId(state, value) {
            setWebvueProp(state.usersFilterId, 'usersFilterId', value);
            state.usersFilterId = value
        },
        setUsersFilterLogin(state, value) {
            setWebvueProp(state.usersFilterLogin, 'usersFilterLogin', value);
            state.usersFilterLogin = value;
        },
        setUsersFilterEmail(state, value) {
            setWebvueProp(state.usersFilterEmail, 'usersFilterEmail', value);
            state.usersFilterEmail = value;
        },
        setUsersFilterFirstName(state, value) {
            setWebvueProp(state.usersFilterFirstName, 'usersFilterFirstName', value);
            state.usersFilterFirstName = value;
        },
        setUsersFilterLastName(state, value) {
            setWebvueProp(state.usersFilterLastName, 'usersFilterLastName', value);
            state.usersFilterLastName = value;
        },
        setUsersFilterGroups(state, value) {
            setWebvueProp(state.usersFilterGroups, 'usersFilterGroups', value);
            state.usersFilterGroups = value;
        }
    },
    actions: {
        setUsersFilterId(ctx, payload) { ctx.commit('setUsersFilterId', payload.value) },
        setUsersFilterLogin(ctx, payload) { ctx.commit('setUsersFilterLogin', payload.value) },
        setUsersFilterEmail(ctx, payload) { ctx.commit('setUsersFilterEmail', payload.value) },
        setUsersFilterFirstName(ctx, payload) { ctx.commit('setUsersFilterFirstName', payload.value) },
        setUsersFilterLastName(ctx, payload) { ctx.commit('setUsersFilterLastName', payload.value) },
        setUsersFilterGroups(ctx, payload) { ctx.commit('setUsersFilterGroups', payload.value) }
    }
}