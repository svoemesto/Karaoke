<template>
  <div class="chat-panel">
    <div class="chat-threads">
      <div class="chat-threads-header">
        <select v-model="target" class="chat-target-select" @change="onTargetChange">
          <option value="local">Локальная БД</option>
          <option value="remote">Сервер</option>
        </select>
        <div class="chat-threads-header-actions">
          <button class="chat-btn" title="Начать диалог с пользователем" @click="toggleSearch">+</button>
          <button class="chat-btn" title="Обновить" @click="reloadThreads">↻</button>
        </div>
      </div>

      <div v-if="isSearchVisible" class="chat-search">
        <div class="chat-search-row">
          <input
            v-model="searchQuery"
            class="chat-search-input"
            placeholder="Email или имя пользователя..."
            autofocus
            @input="onSearchInput"
          />
          <button class="chat-btn" title="Отмена" @click="closeSearch">×</button>
        </div>
        <div v-if="searching" class="chat-empty">Поиск...</div>
        <div v-else-if="searchQuery.trim() && !searchResults.length" class="chat-empty">Никого не найдено</div>
        <div v-else class="chat-thread-list">
          <div v-for="u in searchResults" :key="u.id" class="chat-thread-item" @click="startChatWith(u.id)">
            <div class="chat-thread-main">
              <div class="chat-thread-name">{{ u.displayName || u.email }}</div>
              <div class="chat-thread-preview">{{ u.email }} · id={{ u.id }}</div>
            </div>
          </div>
        </div>
      </div>

      <template v-else>
        <div v-if="threadsIsLoading && !threads.length" class="chat-empty">Загрузка...</div>
        <div v-else-if="!threads.length" class="chat-empty">Сообщений пока нет</div>
        <div v-else class="chat-thread-list">
          <div
            v-for="t in threads"
            :key="t.siteUserId"
            class="chat-thread-item"
            :class="{ 'chat-thread-active': t.siteUserId === currentUserId }"
            @click="openThread(t.siteUserId)"
          >
            <div class="chat-thread-main">
              <div class="chat-thread-name">{{ t.displayName || t.email }}</div>
              <div class="chat-thread-preview">{{ t.lastBody }}</div>
            </div>
            <div class="chat-thread-side">
              <div class="chat-thread-time">{{ formatDate(t.lastAt) }}</div>
              <div v-if="t.unreadFromUser > 0" class="chat-thread-badge">{{ t.unreadFromUser }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <div class="chat-conversation">
      <div v-if="!currentUserId" class="chat-empty chat-conversation-placeholder">Выберите переписку слева или начните новый диалог (+)</div>
      <template v-else>
        <div ref="messagesEl" class="chat-messages">
          <div v-if="messagesIsLoading && !messages.length" class="chat-empty">Загрузка...</div>
          <div v-else-if="!messages.length" class="chat-empty">Сообщений пока нет — напишите первым</div>
          <div
            v-for="m in messages"
            :key="m.id"
            class="chat-bubble"
            :class="m.fromAuthor ? 'chat-bubble-author' : 'chat-bubble-user'"
          >
            <div class="chat-bubble-body">{{ m.body }}</div>
            <div class="chat-bubble-time">{{ formatDateTime(m.createdAt) }}</div>
          </div>
        </div>
        <div class="chat-composer">
          <textarea
            ref="composerEl"
            v-model="draft"
            class="chat-composer-input"
            placeholder="Написать сообщение..."
            rows="1"
            @input="autoGrowComposer"
            @keydown.enter.exact.prevent="onSend"
          />
          <button class="chat-send-btn" :disabled="sending || !draft.trim()" title="Отправить" @click="onSend">
            <span v-if="!sending">➤</span>
            <span v-else class="chat-send-spinner"/>
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
const POLL_INTERVAL_MS = 7000
const SEARCH_DEBOUNCE_MS = 300

export default {
  name: "ChatPanel",
  data() {
    return {
      draft: '', sending: false, pollTimer: null,
      isSearchVisible: false, searchQuery: '', searchResults: [], searching: false, searchDebounceTimer: null,
    }
  },
  computed: {
    threads() { return this.$store.getters.getChatThreads },
    threadsIsLoading() { return this.$store.getters.getChatThreadsIsLoading },
    currentUserId() { return this.$store.getters.getChatCurrentUserId },
    messages() { return this.$store.getters.getChatMessages },
    messagesIsLoading() { return this.$store.getters.getChatMessagesIsLoading },
    target: {
      get() { return this.$store.getters.getChatTarget },
      set(value) { this.$store.dispatch('setChatTarget', value) }
    },
  },
  watch: {
    messages() { this.$nextTick(this.scrollToBottom) }
  },
  mounted() {
    this.reloadThreads();
    this.pollTimer = setInterval(() => {
      this.reloadThreads();
      if (this.currentUserId) this.$store.dispatch('loadChatMessages', this.currentUserId);
    }, POLL_INTERVAL_MS);
  },
  beforeUnmount() {
    if (this.pollTimer) clearInterval(this.pollTimer);
    if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer);
  },
  methods: {
    reloadThreads() { this.$store.dispatch('loadChatThreads') },
    openThread(siteUserId) {
      this.isSearchVisible = false;
      this.$store.dispatch('openChatThread', siteUserId);
    },
    onTargetChange() {
      this.closeSearch();
      this.$store.dispatch('clearChatThread');
      this.reloadThreads();
    },
    toggleSearch() {
      this.isSearchVisible = !this.isSearchVisible;
      if (!this.isSearchVisible) this.closeSearch();
    },
    closeSearch() {
      this.isSearchVisible = false;
      this.searchQuery = '';
      this.searchResults = [];
    },
    onSearchInput() {
      if (this.searchDebounceTimer) clearTimeout(this.searchDebounceTimer);
      const term = this.searchQuery.trim();
      if (!term) { this.searchResults = []; return }
      this.searchDebounceTimer = setTimeout(() => this.runSearch(term), SEARCH_DEBOUNCE_MS);
    },
    async runSearch(term) {
      this.searching = true;
      try {
        this.searchResults = await this.$store.dispatch('searchChatUsers', term);
      } finally {
        this.searching = false;
      }
    },
    startChatWith(siteUserId) {
      this.closeSearch();
      this.$store.dispatch('openChatThread', siteUserId);
    },
    scrollToBottom() {
      const el = this.$refs.messagesEl;
      if (el) el.scrollTop = el.scrollHeight;
    },
    // Авто-рост textarea по мере ввода многострочного текста — высота ограничена CSS
    // (max-height: 25vh, четверть экрана), дальше поле само уходит в внутренний скролл.
    autoGrowComposer() {
      const el = this.$refs.composerEl;
      if (!el) return;
      el.style.height = 'auto';
      el.style.height = `${el.scrollHeight}px`;
    },
    formatDate(tsString) {
      if (!tsString) return '';
      try { return new Date(tsString.replace(' ', 'T')).toLocaleDateString('ru-RU') } catch (e) { return tsString }
    },
    formatDateTime(tsString) {
      if (!tsString) return '';
      try {
        return new Date(tsString.replace(' ', 'T')).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
      } catch (e) { return tsString }
    },
    async onSend() {
      const body = this.draft.trim();
      if (!body || this.sending) return;
      this.sending = true;
      try {
        await this.$store.dispatch('sendChatReply', body);
        this.draft = '';
        this.reloadThreads();
        this.$nextTick(this.autoGrowComposer);
      } finally {
        this.sending = false;
      }
    }
  }
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: row;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  height: calc(100vh - 90px);
  border: 1px solid #ccc;
  border-radius: 8px;
  overflow: hidden;
  font-size: small;
  text-align: left;
}
.chat-threads {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ccc;
  background-color: #f8f9fa;
}
.chat-threads-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid #ccc;
}
.chat-threads-header-actions { display: flex; gap: 4px; flex-shrink: 0; }
.chat-target-select { font-size: small; padding: 2px 4px; min-width: 0; flex: 1; }
.chat-search { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.chat-search-row { display: flex; gap: 6px; padding: 8px 10px; border-bottom: 1px solid #e5e5e5; }
.chat-search-input { flex: 1; font-size: small; padding: 4px 6px; }
.chat-thread-list { overflow-y: auto; flex: 1; }
.chat-thread-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid #e5e5e5;
  cursor: pointer;
}
.chat-thread-item:hover { background-color: #eef3f2; }
.chat-thread-active { background-color: #d7ede8; }
.chat-thread-main { min-width: 0; flex: 1; }
.chat-thread-name { font-weight: bold; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-thread-preview { color: gray; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: x-small; }
.chat-thread-side { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex-shrink: 0; }
.chat-thread-time { color: gray; font-size: x-small; white-space: nowrap; }
.chat-thread-badge {
  background-color: #d02c3a;
  color: #fff;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: x-small;
  padding: 0 5px;
}

.chat-conversation { flex: 1; display: flex; flex-direction: column; min-width: 0; background-color: #fff; }
.chat-conversation-placeholder { display: flex; align-items: center; justify-content: center; flex: 1; text-align: center; padding: 0 20px; }
/* Фон окна переписки — логотип в 1/8 исходного размера, бесшовный «кирпичный» паттерн без
   промежутков (см. подробный комментарий в karaoke-public/src/views/ChatView.vue — тот же
   файл-паттерн public/chat-bg-pattern.svg, лого встроен как base64 внутри SVG). */
.chat-messages {
  flex: 1; min-height: 0; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 6px;
  /* ?v= — cache-bust, см. подробный комментарий в karaoke-public/src/views/ChatView.vue. */
  background-image: url('/chat-bg-pattern.svg?v=3');
  background-repeat: repeat;
  background-size: 150px 166px;
}
.chat-bubble { max-width: 68%; border-radius: 14px; padding: 6px 10px; font-size: small; line-height: 1.4; }
.chat-bubble-user { align-self: flex-start; background-color: #fff; border: 1px solid #ddd; border-bottom-left-radius: 3px; }
.chat-bubble-author { align-self: flex-end; background-color: #4AAE9B; color: #fff; border-bottom-right-radius: 3px; }
.chat-bubble-body { white-space: pre-wrap; word-break: break-word; }
.chat-bubble-time { font-size: x-small; opacity: 0.7; margin-top: 2px; text-align: right; }

/* Композер — прибитая к низу панель, как в мессенджерах (Telegram/WhatsApp): круглая кнопка отправки
   + скруглённое текстовое поле, не растягивающееся textarea с прямоугольной кнопкой рядом. */
.chat-composer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid #ccc;
  background-color: #f8f9fa;
}
.chat-composer-input {
  flex: 1;
  resize: none;
  box-sizing: border-box;
  max-height: 25vh;
  overflow-y: auto;
  font-family: inherit;
  font-size: small;
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 20px;
}
.chat-composer-input:focus { outline: none; border-color: #4AAE9B; }
.chat-send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background-color: #4AAE9B;
  color: #fff;
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.chat-send-btn:hover { opacity: 0.88; }
.chat-send-btn:disabled { opacity: 0.5; cursor: default; }
.chat-send-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: chat-spin 0.7s linear infinite;
}
@keyframes chat-spin { to { transform: rotate(360deg); } }

.chat-btn { border: solid 1px black; border-radius: 6px; padding: 4px 10px; background-color: antiquewhite; cursor: pointer; }
.chat-btn:hover { background-color: lightpink; }
.chat-empty { padding: 20px; text-align: center; color: gray; }
</style>
