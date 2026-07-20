<template>
  <button
    class="btn-round-double chat-notify-btn"
    :class="{ 'chat-notify-blink': hasUnread }"
    :title="hasUnread ? `Непрочитанных сообщений в чате: ${unread}` : 'Чат с пользователями сайта'"
    @click.left="goToChat"
  >
    💬
    <span v-if="hasUnread" class="chat-notify-badge">{{ unread }}</span>
  </button>
</template>

<script>
// Отдельная кнопка-уведомление в шапке (рядом со светофором MonitorLight) — непрочитанные
// сообщения от пользователей в «Чате с автором проекта». chatUnreadTotal уже опрашивается
// периодически в App.vue (loadChatUnreadCount, см. CHAT_UNREAD_POLL_INTERVAL_MS) — здесь только
// презентация, отдельный опрос не нужен. Клик — переход в раздел «Чат» из любой страницы админки.
export default {
  name: 'ChatNotifyButton',
  computed: {
    unread() {
      return this.$store.getters.getChatUnreadTotal
    },
    hasUnread() {
      return this.unread > 0
    },
  },
  methods: {
    goToChat() {
      if (this.$route.path !== '/chat') this.$router.push('/chat')
    },
  },
}
</script>

<style scoped>
/* Тот же визуальный стиль круглой кнопки хедера, что и у MonitorLight/ResourceLimitToggle/
   ProcessWorker (btn-round-double scoped per-component - Vue не расшаривает scoped-стили). */
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin: 0;
  background-color: antiquewhite;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.3rem;
  cursor: pointer;
  position: relative;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}

.chat-notify-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background-color: #2c6bd5;
  color: #fff;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  padding: 0 4px;
  border: 1px solid #fff;
}

@keyframes chat-notify-blink {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(44, 107, 213, 0.7);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(44, 107, 213, 0);
  }
}
.chat-notify-blink {
  animation: chat-notify-blink 1.2s ease-in-out infinite;
  border-color: #2c6bd5;
}
</style>
