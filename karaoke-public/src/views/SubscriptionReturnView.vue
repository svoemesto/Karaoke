<template>
  <div class="sr-wrapper">
    <div class="sr-card">
      <div v-if="loading" class="sr-status">Проверяем статус оплаты...</div>

      <!-- BR-350: сбой проверки — отдельное состояние, а НЕ «подписка не найдена».
           Оплата могла уже пройти (статус PAID ставит вебхук ЮKassa), поэтому
           предупреждаем не оплачивать повторно. -->
      <div v-else-if="view === 'unauthorized'" class="sr-status sr-status-fail">
        <div class="sr-icon">⚠️</div>
        <div class="sr-title">Не удалось проверить статус</div>
        <p class="sr-hint">Сессия истекла — войдите заново, чтобы увидеть статус оплаты.</p>
        <RouterLink to="/login" class="sr-btn">Войти</RouterLink>
      </div>
      <div v-else-if="view === 'check-error'" class="sr-status sr-status-fail">
        <div class="sr-icon">⚠️</div>
        <div class="sr-title">Не удалось проверить статус</div>
        <p class="sr-hint">
          Сервер не ответил. Если оплата прошла, доступ уже открыт — не оплачивайте повторно.
        </p>
        <button class="sr-btn" @click="retryPoll">Повторить проверку</button>
        <RouterLink to="/account/subscriptions" class="sr-btn">Мои подписки</RouterLink>
      </div>

      <!-- Заказ «Корзины» — несколько песен одним платежом -->
      <template v-else-if="isCartOrder">
        <div v-if="orderSubs.length === 0" class="sr-status sr-status-fail">
          <div class="sr-icon">⚠️</div>
          <div class="sr-title">Заказ не найден</div>
          <RouterLink to="/" class="sr-btn">На главную</RouterLink>
        </div>
        <template v-else>
          <div
            v-if="allTerminal"
            class="sr-status"
            :class="{ 'sr-status-ok': paidSubs.length > 0 }"
          >
            <div class="sr-icon">{{ paidSubs.length === orderSubs.length ? '✅' : '⚠️' }}</div>
            <div class="sr-title">
              {{
                paidSubs.length === orderSubs.length
                  ? 'Доступ открыт!'
                  : `Оплачено ${paidSubs.length} из ${orderSubs.length}`
              }}
            </div>
            <ul class="sr-order-list">
              <li v-for="s in orderSubs" :key="s.id">
                <RouterLink v-if="s.status === 'PAID'" :to="`/song?id=${s.idSong}`">{{
                  s.songName || 'id ' + s.idSong
                }}</RouterLink>
                <span v-else class="sr-order-failed"
                  >{{ s.songName || 'id ' + s.idSong }} — не оплачено</span
                >
              </li>
            </ul>
            <RouterLink to="/account/subscriptions" class="sr-btn">Мои подписки</RouterLink>
          </div>
          <div v-else class="sr-status">
            <div class="sr-icon">⏳</div>
            <div class="sr-title">Оплата обрабатывается...</div>
            <div class="sr-hint">
              Обычно это занимает несколько секунд. Страница обновится автоматически.
            </div>
          </div>
        </template>
      </template>

      <!-- Одиночная покупка одной песни/подписка на сайт -->
      <template v-else-if="sub">
        <div v-if="sub.status === 'PAID'" class="sr-status sr-status-ok">
          <div class="sr-icon">✅</div>
          <div class="sr-title">Доступ открыт!</div>
          <RouterLink
            v-if="sub.scope === 'SONG' && sub.idSong"
            :to="`/song?id=${sub.idSong}`"
            class="sr-btn"
            >Играть</RouterLink
          >
          <RouterLink v-else to="/account" class="sr-btn">Мой профиль</RouterLink>
        </div>
        <div v-else-if="sub.status === 'PENDING' || sub.status === 'CREATED'" class="sr-status">
          <div class="sr-icon">⏳</div>
          <div class="sr-title">Оплата обрабатывается...</div>
          <div class="sr-hint">
            Обычно это занимает несколько секунд. Страница обновится автоматически.
          </div>
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
import {
  POLL_OUTCOME,
  POLL_VIEW,
  classifyPollResponse,
  subscriptionPollView,
  nextPollDelay,
} from '../utils/subscriptionPoll.js'

// Попытки опроса: вебхук ЮKassa обычно приходит за секунды, 6 × 2 с — как было до правки.
const MAX_POLL_ATTEMPTS = 6
const POLL_INTERVAL_MS = 2000

// Возврат после оплаты подписки (ЮKassa redirect confirmation). Вебхук ЮKassa может прийти чуть
// позже, чем пользователь вернётся на сайт — поэтому поллим список подписок несколько раз, пока
// статус не станет терминальным (PAID/FAILED) или не кончатся попытки. Два режима: одиночная покупка
// (?subId=) — как раньше; заказ «Корзины» (?orderId=) — несколько позиций одного платежа.
/**
 * View-страница «Subscription Return» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'SubscriptionReturnView',
  setup() {
    const { token } = useAuth()
    return { token }
  },
  data() {
    return {
      loading: true,
      sub: null,
      orderSubs: [],
      attempts: 0,
      // BR-350 (аудит #336, PKG-S2-14): сколько ответов реально были 200 с массивом.
      // «Подписка не найдена» нельзя показывать, пока не было ни одного успешного 200.
      successfulPolls: 0,
      // Явное состояние экрана: checking | processing | terminal | not-found |
      // check-error | unauthorized (см. utils/subscriptionPoll.js).
      view: POLL_VIEW.CHECKING,
      // BR-351 (аудит #336, PKG-S3-28): id таймера опроса + флаг размонтирования.
      // Раньше `setTimeout` не сохранялся, `beforeUnmount` отсутствовал: таймер доживал
      // 12 секунд ПОСЛЕ ухода со страницы, а при `PAID` для `scope=SONG` выполнял
      // `$router.replace('/song?id=…')` — принудительно уводя пользователя с той
      // страницы, на которой он уже находился.
      pollTimer: null,
      isUnmounted: false,
    }
  },
  computed: {
    isCartOrder() {
      return !!this.$route.query.orderId
    },
    paidSubs() {
      return this.orderSubs.filter((s) => s.status === 'PAID')
    },
    allTerminal() {
      return (
        this.orderSubs.length > 0 &&
        this.orderSubs.every((s) => ['PAID', 'FAILED', 'CANCELED'].includes(s.status))
      )
    },
  },
  mounted() {
    this.poll()
  },
  // BR-351 (аудит #336, PKG-S3-28): уход со страницы отменяет опрос.
  beforeUnmount() {
    this.isUnmounted = true
    this.clearPollTimer()
  },
  methods: {
    // BR-351: снять таймер опроса (если он есть).
    clearPollTimer() {
      if (this.pollTimer) {
        clearTimeout(this.pollTimer)
        this.pollTimer = null
      }
    },
    async poll() {
      const orderId = this.$route.query.orderId
      const subId = Number(this.$route.query.subId)
      if (!orderId && !subId) {
        // Ни subId, ни orderId — проверять нечего, но это не «ошибка проверки».
        this.view = POLL_VIEW.NOT_FOUND
        this.loading = false
        return
      }
      this.attempts++
      let outcome = ''
      // BR-350 (аудит #336, PKG-S2-14): раньше любой ответ, кроме 200 с массивом (401 от
      // SiteAuthInterceptor, 500 от БД), и любой reject молча не меняли sub/orderSubs, а после
      // 6 попыток шаблон показывал «Подписка не найдена» — при том что оплата уже могла пройти
      // (статус PAID ставит вебхук ЮKassa, а не этот GET). Пользователь видел ложное
      // «не найдена» и мог оплатить повторно.
      try {
        const { status, body } = await authGet('/api/public/account/subscription/list', this.token)
        outcome = classifyPollResponse(null, status, body)
        if (outcome === POLL_OUTCOME.OK) {
          this.successfulPolls++
          if (orderId) {
            this.orderSubs = body.filter((s) => s.orderId === orderId)
          } else {
            this.sub = body.find((s) => s.id === subId) || null
          }
        }
      } catch (e) {
        outcome = classifyPollResponse(e, 0, null)
      }
      const terminal = orderId
        ? this.allTerminal
        : this.sub &&
          (this.sub.status === 'PAID' ||
            this.sub.status === 'FAILED' ||
            this.sub.status === 'CANCELED')
      this.view = subscriptionPollView({
        outcome,
        successfulPolls: this.successfulPolls,
        hasResult: orderId ? this.orderSubs.length > 0 : !!this.sub,
        terminal: !!terminal,
      })
      // 401 повтором не лечится — сразу показываем «войдите заново».
      const retryable = this.view !== POLL_VIEW.UNAUTHORIZED
      const delay = retryable
        ? nextPollDelay(this.attempts, MAX_POLL_ATTEMPTS, POLL_INTERVAL_MS)
        : null
      if (!terminal && delay !== null) {
        // BR-351: id таймера сохраняем — его можно снять при размонтировании.
        this.pollTimer = setTimeout(() => this.poll(), delay)
        // Ошибку предыдущего ответа не прячем за спиннером: пользователь должен видеть,
        // что проверка идёт, но последний ответ был неудачным (и что есть кнопка повтора).
        this.loading = this.view !== POLL_VIEW.CHECK_ERROR
        return
      }
      this.loading = false
      // Подписка на конкретную песню (одиночная покупка) — сразу ведём на неё, не заставляя лишний
      // раз кликать «Играть». Для заказа корзины (несколько песен) автопереход невозможен — просто
      // показываем список.
      if (
        !orderId &&
        this.sub &&
        this.sub.status === 'PAID' &&
        this.sub.scope === 'SONG' &&
        this.sub.idSong
      ) {
        // BR-351: авто-переход — только если компонент ещё смонтирован и мы всё ещё
        // на странице возврата. Иначе пользователя уводило бы с той страницы, на
        // которой он уже оказался.
        if (this.isUnmounted) return
        if (this.$route.path !== '/subscription/return') return
        this.$router.replace(`/song?id=${this.sub.idSong}`)
      }
    },
    retryPoll() {
      // Ручной повтор после «не удалось проверить статус» — начинаем отсчёт заново.
      this.clearPollTimer()
      this.attempts = 0
      this.view = POLL_VIEW.CHECKING
      this.loading = true
      this.poll()
    },
  },
}
</script>

<style scoped>
.sr-wrapper {
  display: flex;
  justify-content: center;
  padding: 3rem 1rem;
}
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
.sr-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}
.sr-title {
  font-size: 1.2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
}
.sr-hint {
  font-size: 0.85rem;
  color: var(--km-text2, #666);
}
.sr-status-fail .sr-title {
  color: #c0392b;
}
.sr-btn {
  display: inline-block;
  margin: 1rem 0.25rem 0;
  background: var(--km-accent, #0077ff);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font: inherit;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}
.sr-btn:hover {
  opacity: 0.9;
  color: #fff;
}
.sr-order-list {
  list-style: none;
  padding: 0;
  margin: 0.75rem 0;
  text-align: left;
  font-size: 0.9rem;
}
.sr-order-list li {
  padding: 0.3rem 0;
  border-bottom: 1px solid var(--km-border, #eee);
}
.sr-order-list li:last-child {
  border-bottom: none;
}
.sr-order-list a {
  color: var(--km-accent, #0077ff);
  text-decoration: none;
}
.sr-order-list a:hover {
  text-decoration: underline;
}
.sr-order-failed {
  color: var(--km-text2, #888);
}
</style>
