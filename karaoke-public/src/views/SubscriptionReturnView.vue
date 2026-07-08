<template>
  <div class="sr-wrapper">
    <div class="sr-card">
      <div v-if="loading" class="sr-status">Проверяем статус оплаты...</div>
      <template v-else-if="sub">
        <div v-if="sub.status === 'PAID'" class="sr-status sr-status-ok">
          <div class="sr-icon">✅</div>
          <div class="sr-title">Доступ открыт!</div>
          <RouterLink v-if="sub.scope === 'SONG' && sub.idSong" :to="`/song?id=${sub.idSong}`" class="sr-btn">Играть</RouterLink>
          <RouterLink v-else to="/account" class="sr-btn">Мой профиль</RouterLink>
        </div>
        <div v-else-if="sub.status === 'PENDING' || sub.status === 'CREATED'" class="sr-status">
          <div class="sr-icon">⏳</div>
          <div class="sr-title">Оплата обрабатывается...</div>
          <div class="sr-hint">Обычно это занимает несколько секунд. Страница обновится автоматически.</div>
        </div>
        <div v-else class="sr-status sr-status-fail">
          <div class="sr-icon">⚠️</div>
          <div class="sr-title">Оплата не прошла</div>
          <RouterLink to="/" class="sr-btn">На главную</RouterLink>
        </div>
      </template>
      <div v-else class="sr-status sr-status-fail">
        <div class="sr-icon">⚠️</div>
        <div class="sr-title">Подписка не найдена</div>
        <RouterLink to="/" class="sr-btn">На главную</RouterLink>
      </div>
    </div>
  </div>
</template>

<script>
import { authGet } from '../services/authApi'
import { useAuth } from '../composables/useAuth'

// Возврат после оплаты подписки (ЮKassa redirect confirmation). Вебхук ЮKassa может прийти чуть
// позже, чем пользователь вернётся на сайт — поэтому поллим список подписок несколько раз, пока
// статус не станет терминальным (PAID/FAILED) или не кончатся попытки.
export default {
  name: 'SubscriptionReturnView',
  setup() {
    const { token } = useAuth()
    return { token }
  },
  data() {
    return { loading: true, sub: null, attempts: 0 }
  },
  mounted() {
    this.poll()
  },
  methods: {
    async poll() {
      const subId = Number(this.$route.query.subId)
      if (!subId) { this.loading = false; return }
      this.attempts++
      try {
        const { status, body } = await authGet('/api/public/account/subscription/list', this.token)
        if (status === 200 && Array.isArray(body)) {
          this.sub = body.find(s => s.id === subId) || null
        }
      } catch (e) { /* попробуем ещё раз ниже */ }
      const terminal = this.sub && (this.sub.status === 'PAID' || this.sub.status === 'FAILED' || this.sub.status === 'CANCELED')
      if (!terminal && this.attempts < 6) {
        setTimeout(() => this.poll(), 2000)
      } else {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.sr-wrapper { display: flex; justify-content: center; padding: 3rem 1rem; }
.sr-card {
  max-width: 440px;
  width: 100%;
  background: var(--km-card, #fff);
  color: var(--km-text, #1a1a1a);
  border: 1px solid var(--km-border, #ddd);
  border-radius: 14px;
  padding: 2rem 1.5rem;
  text-align: center;
}
.sr-icon { font-size: 2.5rem; margin-bottom: 0.5rem; }
.sr-title { font-size: 1.2rem; font-weight: 700; margin-bottom: 0.5rem; }
.sr-hint { font-size: 0.85rem; color: var(--km-text2, #666); }
.sr-status-fail .sr-title { color: #c0392b; }
.sr-btn {
  display: inline-block;
  margin-top: 1rem;
  background: var(--km-accent, #0077ff);
  color: #fff;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font-weight: 600;
  text-decoration: none;
}
.sr-btn:hover { opacity: 0.9; color: #fff; }
</style>
