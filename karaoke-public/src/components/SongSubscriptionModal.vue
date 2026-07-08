<template>
  <teleport to="body">
    <div v-if="visible" class="ssm-overlay" @click.self="$emit('close')">
      <div class="ssm-card" role="dialog" aria-modal="true">
        <button class="ssm-close" title="Закрыть" @click="$emit('close')">×</button>
        <h3 class="ssm-title">Подписка на песню</h3>
        <p class="ssm-subtitle">{{ songName }}</p>

        <div v-if="loadingPrice" class="ssm-loading">Расчёт цены...</div>
        <div v-else-if="error" class="ssm-error">Не удалось получить цену. Попробуйте ещё раз.</div>
        <template v-else-if="priceInfo">
          <div class="ssm-price">
            <span v-if="priceInfo.discount > 0" class="ssm-price-old">{{ priceInfo.base }} ₽</span>
            <span class="ssm-price-final">{{ priceInfo.final }} ₽</span>
            <span v-if="priceInfo.promoApplied" class="ssm-promo">{{ priceInfo.promoApplied }}</span>
            <span v-if="priceInfo.personalDiscountPercent > 0" class="ssm-promo ssm-promo-personal">Ваша скидка {{ priceInfo.personalDiscountPercent }}%</span>
          </div>
          <div class="ssm-fineprint">Бессрочный доступ к этой песне в онлайн-плеере сайта.</div>

          <label class="ssm-disclaimer-label">
            <input type="checkbox" v-model="disclaimerAccepted">
            <span>
              Я понимаю, что оплачиваю работу автора проекта (разметку, синхронизацию, плеер) — права на
              музыкальное произведение и фонограмму принадлежат правообладателям и ко мне не переходят.
              Доступ — только для личного некоммерческого использования на этом сайте, без передачи
              файлов и без возможности скачивания. Полные условия — в
              <RouterLink to="/oferta" target="_blank">публичной оферте</RouterLink>.
            </span>
          </label>

          <div v-if="submitError" class="ssm-error">{{ submitErrorText }}</div>
          <div v-if="priceInfo.final > 0 && priceInfo.paymentsEnabled === false" class="ssm-error">
            Приём платежей ещё не подключён, попробуйте позже.
          </div>

          <div class="ssm-upsell">
            Хотите слушать всю коллекцию, а не только эту песню?
            <RouterLink to="/premium" @click="$emit('close')">Оформите премиум-подписку →</RouterLink>
          </div>

          <div v-if="inCart" class="ssm-cart-row">
            <button class="ssm-btn ssm-btn-secondary" :disabled="cartBusy" @click="onToggleCart">
              {{ cartBusy ? 'Убираем...' : 'Удалить из корзины и оплатить отдельно' }}
            </button>
            <RouterLink to="/account/cart" class="ssm-btn ssm-btn-secondary" @click="$emit('close')">Перейти в корзину</RouterLink>
          </div>
          <button v-else class="ssm-btn ssm-btn-secondary ssm-btn-cart-solo" :disabled="cartBusy" @click="onToggleCart">
            {{ cartBusy ? 'Добавляем...' : 'Добавить в корзину' }}
          </button>

          <div class="ssm-actions">
            <button class="ssm-btn ssm-btn-secondary" @click="$emit('close')">Отмена</button>
            <button
              class="ssm-btn ssm-btn-primary"
              :disabled="inCart || !disclaimerAccepted || submitting || (priceInfo.final > 0 && priceInfo.paymentsEnabled === false)"
              @click="onSubmit"
            >
              {{ submitting ? 'Оформляем...' : (priceInfo.final > 0 ? 'Оплатить' : 'Активировать бесплатно') }}
            </button>
          </div>
        </template>
      </div>
    </div>
  </teleport>
</template>

<script>
import { useSongSubscription } from '../composables/useSongSubscription'
import { useCart } from '../composables/useCart'

export default {
  name: 'SongSubscriptionModal',
  props: {
    visible: { type: Boolean, default: false },
    songId: { type: [Number, String], default: null },
    songName: { type: String, default: '' },
  },
  emits: ['close', 'activated'],
  setup() {
    const { loadingPrice, priceInfo, submitting, error, loadPrice, subscribe } = useSongSubscription()
    const cart = useCart()
    return { loadingPrice, priceInfo, submitting, error, loadPrice, subscribe, cart }
  },
  data() {
    return { disclaimerAccepted: false, submitError: false, cartBusy: false }
  },
  computed: {
    inCart() {
      return !!this.songId && this.cart.isInCart(this.songId)
    },
    submitErrorText() {
      if (this.error === 'already_subscribed') return 'Подписка на эту песню уже оформлена.'
      if (this.error === 'payment_unavailable') return 'Оплата временно недоступна, попробуйте позже.'
      return 'Не удалось оформить подписку. Попробуйте ещё раз.'
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.disclaimerAccepted = false
        this.submitError = false
        this.loadPrice(this.songId)
      }
    }
  },
  methods: {
    // Один и тот же тумблер на обе кнопки-состояния: "Добавить в корзину" (её нет в корзине) и
    // "Удалить из корзины и оплатить отдельно" (уже там) — toggle сам знает, что сейчас делать.
    async onToggleCart() {
      if (this.cartBusy) return
      this.cartBusy = true
      try { await this.cart.toggle(this.songId) } finally { this.cartBusy = false }
    },
    async onSubmit() {
      this.submitError = false
      const result = await this.subscribe(this.songId, true)
      if (!result.ok) { this.submitError = true; return }
      if (result.confirmationUrl) {
        window.location.href = result.confirmationUrl
        return
      }
      // Акция довела цену до нуля — доступ уже активен, ничего ждать не нужно.
      this.$emit('activated')
      this.$emit('close')
    }
  }
}
</script>

<style scoped>
.ssm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3000;
  padding: 1rem;
}
.ssm-card {
  position: relative;
  width: 100%;
  max-width: 460px;
  background: var(--km-card, #fff);
  color: var(--km-text, #1a1a1a);
  border: 1px solid var(--km-border, #ddd);
  border-radius: 14px;
  padding: 1.5rem 1.5rem 1.25rem;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.ssm-close {
  position: absolute;
  top: 8px;
  right: 12px;
  background: transparent;
  border: none;
  font-size: 1.5rem;
  line-height: 1;
  color: var(--km-text2, #888);
  cursor: pointer;
}
.ssm-title { font-size: 1.15rem; font-weight: 700; margin: 0 0 0.25rem; }
.ssm-subtitle { font-size: 0.9rem; color: var(--km-text2, #555); margin: 0 0 0.9rem; }
.ssm-loading, .ssm-error { font-size: 0.88rem; color: var(--km-text2, #555); margin-bottom: 0.75rem; }
.ssm-error { color: #c0392b; }
.ssm-price { display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 0.4rem; }
.ssm-price-old { text-decoration: line-through; color: var(--km-text2, #888); font-size: 0.95rem; }
.ssm-price-final { font-size: 1.5rem; font-weight: 800; }
.ssm-promo { font-size: 0.78rem; background: linear-gradient(135deg, #f6c94b, #d99413); color: #5a3c00; padding: 0.15rem 0.5rem; border-radius: 12px; }
.ssm-promo-personal { background: linear-gradient(135deg, #a78bfa, #7C3AED); color: #fff; }
.ssm-fineprint { font-size: 0.8rem; color: var(--km-text2, #888); margin-bottom: 1rem; }
.ssm-disclaimer-label { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.78rem; line-height: 1.35; color: var(--km-text2, #666); margin-bottom: 1rem; cursor: pointer; }
.ssm-disclaimer-label input { margin-top: 0.2rem; flex-shrink: 0; }
.ssm-upsell {
  font-size: 0.8rem;
  color: var(--km-text2, #666);
  background: var(--km-bg2, #f5f5f7);
  border-radius: 8px;
  padding: 0.6rem 0.8rem;
  margin-bottom: 1rem;
  text-align: center;
}
.ssm-upsell a { color: var(--km-accent, #0077ff); font-weight: 600; text-decoration: none; }
.ssm-upsell a:hover { text-decoration: underline; }
.ssm-btn-cart-solo { display: block; width: 100%; text-align: center; margin-bottom: 0.75rem; }
.ssm-cart-row { display: flex; gap: 0.5rem; margin-bottom: 0.75rem; }
.ssm-cart-row .ssm-btn { flex: 1; text-align: center; text-decoration: none; }
.ssm-actions { display: flex; gap: 0.6rem; justify-content: flex-end; }
.ssm-btn { padding: 0.5rem 1.1rem; border-radius: 8px; font-size: 0.88rem; font-weight: 600; cursor: pointer; border: 1px solid transparent; }
.ssm-btn-primary { background: var(--km-accent, #0077ff); color: #fff; }
.ssm-btn-primary:hover { filter: brightness(1.08); }
.ssm-btn-primary[disabled] { opacity: 0.5; cursor: default; filter: none; }
.ssm-btn-secondary { background: transparent; color: var(--km-text2, #555); border-color: var(--km-border, #ccc); }
.ssm-btn-secondary:hover { background: var(--km-hover, rgba(0,0,0,0.05)); }
</style>
