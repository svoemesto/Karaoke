import { promisedXMLHttpRequest } from '../../lib/utils'

const PAGE_SIZE = 10
const POLL_BATCH_LIMIT = 500 // защита от аномального залпа между двумя поллами, не реальный лимит

// «Чат с автором проекта» (сторона автора). Таблица tbl_site_chat_messages в норме живёт на PROD-БД
// (реальные пользователи пишут через karaoke-web на проде) — chatTarget по умолчанию 'remote', как
// siteUsersTarget/sitePlaylistsTarget. Переключатель в UI (ChatPanel.vue) нужен для локальной отладки
// (target=local) без похода на прод. Live-обновление — не SSE (сообщения создаёт karaoke-web, а не
// karaoke-app, поэтому save()/createDbInstance() из karaoke-app для них не вызывается), а polling
// (см. ChatPanel.vue и бейдж в App.vue).
/**
 * Компонент «Store».
 *
 * @see AGENTS.md
 */
export default {
  state: {
    chatThreads: [],
    chatThreadsIsLoading: false,
    chatCurrentUserId: 0,
    chatMessages: [],
    chatMessagesTotal: 0,
    chatMessagesIsLoading: false,
    chatUnreadTotal: 0,
    chatTarget: 'remote',
  },
  getters: {
    getChatThreads(state) {
      return state.chatThreads
    },
    getChatThreadsIsLoading(state) {
      return state.chatThreadsIsLoading
    },
    getChatCurrentUserId(state) {
      return state.chatCurrentUserId
    },
    getChatMessages(state) {
      return state.chatMessages
    },
    getChatMessagesTotal(state) {
      return state.chatMessagesTotal
    },
    getChatMessagesIsLoading(state) {
      return state.chatMessagesIsLoading
    },
    getChatUnreadTotal(state) {
      return state.chatUnreadTotal
    },
    getChatTarget(state) {
      return state.chatTarget
    },
  },
  mutations: {
    setChatThreads(state, threads) {
      state.chatThreads = threads
    },
    setChatThreadsIsLoading(state, isLoading) {
      state.chatThreadsIsLoading = isLoading
    },
    setChatCurrentUserId(state, id) {
      state.chatCurrentUserId = id
    },
    setChatMessages(state, messages) {
      state.chatMessages = messages
    },
    setChatMessagesTotal(state, total) {
      state.chatMessagesTotal = total
    },
    prependChatMessages(state, messages) {
      state.chatMessages = messages.concat(state.chatMessages)
    },
    appendChatMessages(state, messages) {
      state.chatMessages = state.chatMessages.concat(messages)
    },
    setChatMessagesIsLoading(state, isLoading) {
      state.chatMessagesIsLoading = isLoading
    },
    setChatUnreadTotal(state, count) {
      state.chatUnreadTotal = count
    },
    setChatTarget(state, target) {
      state.chatTarget = target
    },
  },
  actions: {
    loadChatThreads(ctx) {
      const request = {
        method: 'POST',
        url: '/api/chat/threads',
        params: { target: ctx.state.chatTarget },
      }
      ctx.commit('setChatThreadsIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setChatThreads', result.threads || [])
          ctx.commit('setChatThreadsIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setChatThreadsIsLoading', false)
          console.log(error)
        })
    },
    openChatThread(ctx, siteUserId) {
      ctx.commit('setChatCurrentUserId', siteUserId)
      return ctx.dispatch('loadChatMessages', siteUserId)
    },
    // При смене target (local/remote) siteUserId открытого треда мог бы указывать на совсем
    // другого пользователя в другой БД — закрываем текущую переписку, не показываем чужую.
    clearChatThread(ctx) {
      ctx.commit('setChatCurrentUserId', 0)
      ctx.commit('setChatMessages', [])
      ctx.commit('setChatMessagesTotal', 0)
    },
    // Открытие треда — только последние PAGE_SIZE сообщений (не вся история разом), остальное — по
    // кнопкам "Подгрузить ещё"/"Подгрузить все" (loadMoreChatMessages) в ChatPanel.vue.
    loadChatMessages(ctx, siteUserId) {
      const request = {
        method: 'POST',
        url: '/api/chat/messages',
        params: { siteUserId, target: ctx.state.chatTarget, limit: PAGE_SIZE },
      }
      ctx.commit('setChatMessagesIsLoading', true)
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('setChatMessages', result.messages || [])
          ctx.commit('setChatMessagesTotal', result.total || 0)
          ctx.commit('setChatMessagesIsLoading', false)
        })
        .catch((error) => {
          ctx.commit('setChatMessagesIsLoading', false)
          console.log(error)
        })
    },
    // Подгрузка старых сообщений (курсор beforeId = id самого старого уже загруженного) — вызывается
    // кнопками истории в ChatPanel.vue, count — сколько запросить (PAGE_SIZE или весь остаток).
    loadMoreChatMessages(ctx, count) {
      const beforeId = ctx.state.chatMessages[0]?.id
      if (!beforeId || count <= 0) return Promise.resolve()
      const request = {
        method: 'POST',
        url: '/api/chat/messages',
        params: {
          siteUserId: ctx.state.chatCurrentUserId,
          target: ctx.state.chatTarget,
          beforeId,
          limit: count,
        },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          ctx.commit('prependChatMessages', result.messages || [])
          ctx.commit('setChatMessagesTotal', result.total || ctx.state.chatMessagesTotal)
        })
        .catch((error) => console.log(error))
    },
    // Поллинг новых сообщений (курсор afterId = id самого свежего уже загруженного) — вместо
    // перезагрузки всего треда целиком на каждый тик, см. ChatPanel.vue.
    pollChatMessages(ctx) {
      const messages = ctx.state.chatMessages
      if (!messages.length) return Promise.resolve()
      const afterId = messages[messages.length - 1].id
      const request = {
        method: 'POST',
        url: '/api/chat/messages',
        params: {
          siteUserId: ctx.state.chatCurrentUserId,
          target: ctx.state.chatTarget,
          afterId,
          limit: POLL_BATCH_LIMIT,
        },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          const result = JSON.parse(data)
          if (result.messages && result.messages.length)
            ctx.commit('appendChatMessages', result.messages)
          ctx.commit('setChatMessagesTotal', result.total || ctx.state.chatMessagesTotal)
        })
        .catch((error) => console.log(error))
    },
    sendChatReply(ctx, body) {
      const params = { siteUserId: ctx.state.chatCurrentUserId, body, target: ctx.state.chatTarget }
      const request = { method: 'POST', url: '/api/chat/reply', params }
      return promisedXMLHttpRequest(request).then(() => {
        return ctx.dispatch('pollChatMessages')
      })
    },
    loadChatUnreadCount(ctx) {
      const request = {
        method: 'POST',
        url: '/api/chat/unreadcount',
        params: { target: ctx.state.chatTarget },
      }
      return promisedXMLHttpRequest(request)
        .then((data) => {
          ctx.commit('setChatUnreadTotal', Number(data) || 0)
        })
        .catch((error) => {
          console.log(error)
        })
    },
    setChatTarget(ctx, target) {
      ctx.commit('setChatTarget', target)
    },
    searchChatUsers(ctx, term) {
      const request = {
        method: 'POST',
        url: '/api/chat/searchusers',
        params: { term, target: ctx.state.chatTarget },
      }
      return promisedXMLHttpRequest(request).then((data) => JSON.parse(data).users || [])
    },
  },
}
