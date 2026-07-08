<template>
  <div class="sr-wrapper">
    <div class="sr-card">
      <div v-if="loading" class="sr-status">Проверяем статус оплаты...</div>

      <!-- Заказ «Корзины» — несколько песен одним платежом -->
      <template v-else-if="isCartOrder">
        <div v-if="orderSubs.length === 0" class="sr-status sr-status-fail">
          <div class="sr-icon">⚠️</div>
          <div class="sr-title">Заказ не найден</div>
          <RouterLink to="/" class="sr-btn">На главную</RouterLink>
        </div>
        <template v-else>
          <div v-if="allTerminal" class="sr-status" :class="{ 'sr-status-ok': paidSubs.length > 0 }">
            <div class="sr-icon">{{ paidSubs.length === orderSubs.length ? '✅' : '⚠️' }}</div>
            <div class="sr-title">
              {{ paidSubs.length === orderSubs.length ? 'Доступ открыт!' : `Оплачено ${paidSubs.length} из ${orderSubs.length}` }}
            </div>
            <ul class="sr-order-list">
              <li v-for="s in orderSubs" :key="s.id">
                <RouterLink v-if="s.status === 'PAID'" :to="`/song?id=${s.idSong}`">{{ s.songName || ('id ' + s.idSong) }}</RouterLink>
                <span v-else class="sr-order-failed">{{ s.songName || ('id ' + s.idSong) }} — не оплачено</span>
              </li>
            </ul>
            <RouterLink to="/account/subscriptions" class="sr-btn">Мои подписки</RouterLink>
          </div>
          <div v-else class="sr-status">
            <div class="sr-icon">⏳</div>
            <div class="sr-title">Оплата обрабатывается...</div>
            <div class="sr-hint">Обычно это занимает несколько секунд. Страница обновится автоматически.</div>
          </div>
        </template>
      </template>

      <!-- Одиночная покупка одной песни/подписка на сайт -->
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
// статус не станет терминальным (PAID/FAILED) или не кончатся попытки. Два режима: одиночная покупка
// (?subId=) — как раньше; заказ «Корзины» (?orderId=) — несколько позиций одного платежа.
export default {
  name: 'SubscriptionReturnView',
  setup() {
    const { token } = useAuth()
    return { token }
  },
  data() {
    return { loading: true, sub: null, orderSubs: [], attempts: 0 }
  },
  computed: {
    isCartOrder() { return !!this.$route.query.orderId },
    paidSubs() { return this.orderSubs.filter(s => s.status === 'PAID') },
    allTerminal() {
      return this.orderSubs.length > 0 && this.orderSubs.every(s => ['PAID', 'FAILED', 'CANCELED'].includes(s.status))
    }
  },
  mounted() {
    this.poll()
  },
  methods: {
    async poll() {
      const orderId = this.$route.query.orderId
      const subId = Number(this.$route.query.subId)
      if (!orderId && !subId) { this.loading = false; return }
      this.attempts++
      try {
        const { status, body } = await authGet('/api/public/account/subscription/list', this.token)
        if (status === 200 && Array.isArray(body)) {
          if (orderId) {
            this.orderSubs = body.filter(s => s.orderId === orderId)
          } else {
            this.sub = body.find(s => s.id === subId) || null
          }
        }
      } catch (e) { /* попробуем ещё раз ниже */ }
      const terminal = orderId
        ? this.allTerminal
        : (this.sub && (this.sub.status === 'PAID' || this.sub.status === 'FAILED' || this.sub.status === 'CANCELED'))
      if (!terminal && this.attempts < 6) {
        setTimeout(() => this.poll(), 2000)
      } else {
        this.loading = false
        // Подписка на конкретную песню (одиночная покупка) — сразу ведём на неё, не заставляя лишний
        // раз кликать «Играть». Для заказа корзины (несколько песен) автопереход невозможен — просто
        // показываем список.
        if (!orderId && this.sub && this.sub.status === 'PAID' && this.sub.scope === 'SONG' && this.sub.idSong) {
          this.$router.replace(`/song?id=${this.sub.idSong}`)
        }
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
.sr-order-list { list-style: none; padding: 0; margin: 0.75rem 0; text-align: left; font-size: 0.9rem; }
.sr-order-list li { padding: 0.3rem 0; border-bottom: 1px solid var(--km-border, #eee); }
.sr-order-list li:last-child { border-bottom: none; }
.sr-order-list a { color: var(--km-accent, #0077ff); text-decoration: none; }
.sr-order-list a:hover { text-decoration: underline; }
.sr-order-failed { color: var(--km-text2, #888); }
</style>
