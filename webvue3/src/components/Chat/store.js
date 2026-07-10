import { promisedXMLHttpRequest } from "../../lib/utils";

// «Чат с автором проекта» (сторона автора). Таблица tbl_site_chat_messages в норме живёт на PROD-БД
// (реальные пользователи пишут через karaoke-web на проде) — chatTarget по умолчанию 'remote', как
// siteUsersTarget/sitePlaylistsTarget. Переключатель в UI (ChatPanel.vue) нужен для локальной отладки
// (target=local) без похода на прод. Live-обновление — не SSE (сообщения создаёт karaoke-web, а не
// karaoke-app, поэтому save()/createDbInstance() из karaoke-app для них не вызывается), а polling
// (см. ChatPanel.vue и бейдж в App.vue).
export default {
    state: {
        chatThreads: [],
        chatThreadsIsLoading: false,
        chatCurrentUserId: 0,
        chatMessages: [],
        chatMessagesIsLoading: false,
        chatUnreadTotal: 0,
        chatTarget: 'remote',
    },
    getters: {
        getChatThreads(state) { return state.chatThreads },
        getChatThreadsIsLoading(state) { return state.chatThreadsIsLoading },
        getChatCurrentUserId(state) { return state.chatCurrentUserId },
        getChatMessages(state) { return state.chatMessages },
        getChatMessagesIsLoading(state) { return state.chatMessagesIsLoading },
        getChatUnreadTotal(state) { return state.chatUnreadTotal },
        getChatTarget(state) { return state.chatTarget },
    },
    mutations: {
        setChatThreads(state, threads) { state.chatThreads = threads },
        setChatThreadsIsLoading(state, isLoading) { state.chatThreadsIsLoading = isLoading },
        setChatCurrentUserId(state, id) { state.chatCurrentUserId = id },
        setChatMessages(state, messages) { state.chatMessages = messages },
        setChatMessagesIsLoading(state, isLoading) { state.chatMessagesIsLoading = isLoading },
        setChatUnreadTotal(state, count) { state.chatUnreadTotal = count },
        setChatTarget(state, target) { state.chatTarget = target },
    },
    actions: {
        loadChatThreads(ctx) {
            const request = { method: 'POST', url: "/api/chat/threads", params: { target: ctx.state.chatTarget } };
            ctx.commit('setChatThreadsIsLoading', true);
            return promisedXMLHttpRequest(request).then(data => {
                const result = JSON.parse(data);
                ctx.commit('setChatThreads', result.threads || []);
                ctx.commit('setChatThreadsIsLoading', false);
            }).catch(error => {
                ctx.commit('setChatThreadsIsLoading', false);
                console.log(error);
            });
        },
        openChatThread(ctx, siteUserId) {
            ctx.commit('setChatCurrentUserId', siteUserId);
            return ctx.dispatch('loadChatMessages', siteUserId);
        },
        // При смене target (local/remote) siteUserId открытого треда мог бы указывать на совсем
        // другого пользователя в другой БД — закрываем текущую переписку, не показываем чужую.
        clearChatThread(ctx) {
            ctx.commit('setChatCurrentUserId', 0);
            ctx.commit('setChatMessages', []);
        },
        loadChatMessages(ctx, siteUserId) {
            const request = { method: 'POST', url: "/api/chat/messages", params: { siteUserId, target: ctx.state.chatTarget } };
            ctx.commit('setChatMessagesIsLoading', true);
            return promisedXMLHttpRequest(request).then(data => {
                const result = JSON.parse(data);
                ctx.commit('setChatMessages', result.messages || []);
                ctx.commit('setChatMessagesIsLoading', false);
            }).catch(error => {
                ctx.commit('setChatMessagesIsLoading', false);
                console.log(error);
            });
        },
        sendChatReply(ctx, body) {
            const params = { siteUserId: ctx.state.chatCurrentUserId, body, target: ctx.state.chatTarget };
            const request = { method: 'POST', url: "/api/chat/reply", params };
            return promisedXMLHttpRequest(request).then(() => {
                return ctx.dispatch('loadChatMessages', ctx.state.chatCurrentUserId);
            });
        },
        loadChatUnreadCount(ctx) {
            const request = { method: 'POST', url: "/api/chat/unreadcount", params: { target: ctx.state.chatTarget } };
            return promisedXMLHttpRequest(request).then(data => {
                ctx.commit('setChatUnreadTotal', Number(data) || 0);
            }).catch(error => { console.log(error) });
        },
        setChatTarget(ctx, target) { ctx.commit('setChatTarget', target) },
        searchChatUsers(ctx, term) {
            const request = { method: 'POST', url: "/api/chat/searchusers", params: { term, target: ctx.state.chatTarget } };
            return promisedXMLHttpRequest(request).then(data => JSON.parse(data).users || []);
        },
    }
}
