<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/account" class="km-back">← Личный кабинет</RouterLink>
        </div>
      </div>
    </header>

    <div class="km-content">
      <div class="km-title-row">
        <h1 class="km-title">Корзина</h1>
        <button v-if="rows.length > 0" class="km-btn km-btn-danger" @click="onClear">
          Очистить корзину
        </button>
      </div>

      <div v-if="loading" class="km-hint">Загрузка...</div>
      <div v-else-if="rows.length === 0" class="km-empty">
        Корзина пуста. Добавляйте песни значком 🛒 в
        <RouterLink to="/zakroma">Закромах</RouterLink> или
        <RouterLink to="/filter">Поиске</RouterLink>.
      </div>
      <template v-else>
        <div class="km-cart-list">
          <div v-for="row in rows" :key="row.songId" class="km-cart-card">
            <div class="km-cart-main">
              <div class="km-cart-title">{{ row.songName }} — {{ row.author }}</div>
              <div class="km-cart-price">
                <span v-if="row.discount > 0" class="km-cart-price-old">{{ row.base }} ₽</span>
                <span class="km-cart-price-final">{{ row.final }} ₽</span>
                <span v-if="row.promoApplied" class="km-cart-promo">{{ row.promoApplied }}</span>
                <span
                  v-if="row.personalDiscountPercent > 0"
                  class="km-cart-promo km-cart-promo-personal"
                  >Ваша скидка {{ row.personalDiscountPercent }}%</span
                >
              </div>
            </div>
            <button class="km-btn km-btn-danger" @click="onRemove(row.songId)">Убрать</button>
          </div>
        </div>

        <div class="km-cart-summary">
          <div class="km-cart-total">
            Итого: <b>{{ total }} ₽</b>
          </div>

          <label class="km-disclaimer-label">
            <input v-model="disclaimerAccepted" type="checkbox" />
            <span>
              Я понимаю, что оплачиваю работу автора проекта (разметку, синхронизацию, плеер) —
              права на музыкальные произведения и фонограммы принадлежат правообладателям и ко мне
              не переходят. Доступ — только для личного некоммерческого использования на этом сайте,
              без передачи файлов и без возможности скачивания. Полные условия — в
              <RouterLink to="/oferta" target="_blank">публичной оферте</RouterLink>.
            </span>
          </label>

          <div v-if="checkoutError" class="km-error">{{ checkoutErrorText }}</div>
          <div v-if="total > 0 && paymentsEnabled === false" class="km-error">
            Приём платежей ещё не подключён, попробуйте позже.
          </div>

          <button
            class="km-btn km-btn-primary"
            :disabled="
              !disclaimerAccepted || checkingOut || (total > 0 && paymentsEnabled === false)
            "
            @click="onCheckout"
          >
            {{
              checkingOut ? 'Оформляем...' : total > 0 ? 'Оплатить всё' : 'Активировать бесплатно'
            }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'
import { fetchCartPrice, checkoutCart } from '../services/cartApi'

/**
 * View-страница «Cart» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'CartView',
  setup() {
    const { token } = useAuth()
    const cart = useCart()
    return { token, cart }
  },
  data() {
    return {
      loading: true,
      rows: [],
      paymentsEnabled: true,
      disclaimerAccepted: false,
      checkingOut: false,
      checkoutError: false,
      checkoutErrorText: 'Не удалось оформить заказ. Попробуйте ещё раз.',
    }
  },
  computed: {
    total() {
      return this.rows.reduce((sum, r) => sum + r.final, 0)
    },
  },
  async mounted() {
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account/cart' } })
      return
    }
    await this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const { status, body } = await fetchCartPrice()
        if (status === 200 && body) {
          this.rows = body.items || []
          this.paymentsEnabled = body.paymentsEnabled !== false
        }
      } catch (e) {
        /* оставляем пустой список */
      }
      this.loading = false
    },
    async onRemove(songId) {
      await this.cart.toggle(songId)
      await this.load()
    },
    async onClear() {
      if (!confirm('Очистить корзину полностью?')) return
      await this.cart.clear()
      await this.load()
    },
    async onCheckout() {
      this.checkoutError = false
      this.checkingOut = true
      try {
        const { status, body } = await checkoutCart(this.disclaimerAccepted)
        if (status === 200 && body) {
          if (body.confirmationUrl) {
            window.location.href = body.confirmationUrl
            return
          }
          // Акция довела весь заказ до нуля — доступ уже активирован сразу.
          await this.cart.load(true)
          this.$router.push('/account/subscriptions')
          return
        }
        this.checkoutErrorText =
          body && body.error === 'cart_empty'
            ? 'Все песни из корзины уже оплачены или более недоступны для подписки.'
            : 'Не удалось оформить заказ. Попробуйте ещё раз.'
        this.checkoutError = true
      } catch (e) {
        this.checkoutErrorText = 'Не удалось связаться с сервером.'
        this.checkoutError = true
      } finally {
        this.checkingOut = false
      }
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner {
  max-width: 700px;
  margin: 0 auto;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
}
.km-content {
  max-width: 600px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.km-title {
  font-size: 1.4rem;
  margin: 0;
}
.km-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.25rem;
}
.km-hint,
.km-empty {
  font-size: 0.9rem;
  color: var(--km-text2);
}
.km-empty a {
  color: var(--km-accent);
}

.km-cart-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}
.km-cart-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
}
.km-cart-title {
  font-weight: 600;
  font-size: 0.95rem;
}
.km-cart-price {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  margin-top: 0.25rem;
  font-size: 0.85rem;
}
.km-cart-price-old {
  text-decoration: line-through;
  color: var(--km-text2);
}
.km-cart-price-final {
  font-weight: 700;
}
.km-cart-promo {
  font-size: 0.72rem;
  background: linear-gradient(135deg, #f6c94b, #d99413);
  color: #5a3c00;
  padding: 0.1rem 0.45rem;
  border-radius: 10px;
}
.km-cart-promo-personal {
  background: linear-gradient(135deg, #a78bfa, #7c3aed);
  color: #fff;
}

.km-cart-summary {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.5rem;
}
.km-cart-total {
  font-size: 1.1rem;
  margin-bottom: 1rem;
}
.km-disclaimer-label {
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
  font-size: 0.78rem;
  line-height: 1.35;
  color: var(--km-text2);
  margin-bottom: 1rem;
  cursor: pointer;
}
.km-disclaimer-label input {
  margin-top: 0.2rem;
  flex-shrink: 0;
}
.km-error {
  font-size: 0.85rem;
  color: #e05555;
  margin-bottom: 0.75rem;
}

.km-btn {
  display: inline-block;
  border-radius: 8px;
  padding: 0.5rem 1.1rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: none;
  font-size: 0.88rem;
  white-space: nowrap;
}
.km-btn-primary {
  background: var(--km-accent);
  color: #fff;
}
.km-btn-primary:hover {
  opacity: 0.9;
}
.km-btn-primary:disabled {
  opacity: 0.5;
  cursor: default;
}
.km-btn-danger {
  background: transparent;
  color: #e05555;
  border: 1px solid #e05555;
}
.km-btn-danger:hover {
  background: rgba(224, 85, 85, 0.1);
}
</style>
