<template>
  <button
    v-if="visible"
    class="cub-btn"
    title="Новое сообщение от автора проекта — открыть чат"
    @click="goToChat"
  >
    💬
    <span class="cub-badge">{{ unread }}</span>
  </button>
</template>

<script>
// Глобальный плавающий индикатор непрочитанных сообщений от автора («Чат с автором проекта»).
// Смонтирован в App.vue (как PremiumUpsellModal) — виден на ЛЮБОЙ странице сайта, не только внутри
// самого чата, чтобы пользователь заметил новое сообщение даже если он не открывал раздел «Чат».
// Скрывается, когда пользователь уже на странице чата (там и так видно новое сообщение).
import { useAuth } from '../composables/useAuth'
import { fetchUnreadCount } from '../services/chatApi'

const POLL_INTERVAL_MS = 20000

export default {
  name: 'ChatUnreadBadge',
  setup() {
    const { user, isLoggedIn } = useAuth()
    return { user, isLoggedIn }
  },
  data() {
    return { unread: 0, pollTimer: null }
  },
  computed: {
    // Чат целиком — премиум-функция; без активного премиума бейдж не нужен (бэкенд и так всегда
    // отдаёт count=0 для не-премиум, но не тратим лишний poll-запрос впустую).
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    // Скрыт на самой странице чата (там и так видно новое сообщение) и на плеере (полноэкранный,
    // плавающая кнопка поверх видео была бы отвлекающей).
    isHiddenRoute() {
      return this.$route.name === 'chat' || this.$route.name === 'player'
    },
    visible() {
      return this.isPremium && this.unread > 0 && !this.isHiddenRoute
    },
  },
  watch: {
    isLoggedIn: {
      immediate: true,
      handler(loggedIn) {
        loggedIn && this.isPremium ? this.startPolling() : this.stopPolling()
      },
    },
  },
  beforeUnmount() {
    this.stopPolling()
  },
  methods: {
    startPolling() {
      this.poll()
      if (this.pollTimer) return
      this.pollTimer = setInterval(this.poll, POLL_INTERVAL_MS)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      this.unread = 0
    },
    async poll() {
      const { status, body } = await fetchUnreadCount()
      if (status === 200 && body) this.unread = body.count || 0
    },
    goToChat() {
      this.$router.push('/account/chat')
    },
  },
}
</script>

<style scoped>
.cub-btn {
  position: fixed;
  top: 14px;
  right: 14px;
  z-index: 2000;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: var(--km-accent, #0077ff);
  color: #fff;
  font-size: 1.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.3);
  animation: cub-pulse 1.4s ease-in-out infinite;
}
.cub-btn:hover {
  filter: brightness(1.1);
}
.cub-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #e05555;
  color: #fff;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  padding: 0 4px;
  border: 1px solid var(--km-bg, #0f0f1a);
}
@keyframes cub-pulse {
  0%,
  100% {
    box-shadow:
      0 4px 14px rgba(0, 0, 0, 0.3),
      0 0 0 0 rgba(0, 119, 255, 0.55);
  }
  50% {
    box-shadow:
      0 4px 14px rgba(0, 0, 0, 0.3),
      0 0 0 8px rgba(0, 119, 255, 0);
  }
}
</style>
