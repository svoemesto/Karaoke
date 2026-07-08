<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
      </div>
    </header>

    <div class="km-content">
      <h1 class="km-title">Подписка</h1>
      <p class="km-lead">Три способа получить премиум-доступ на сайте — выберите удобный.</p>
      <p class="km-delivery-note">
        Доступ — цифровая услуга: активируется автоматически сразу после оплаты, без доставки.
        Играть можно сразу в браузере на этом сайте.
      </p>

      <!-- Путь 1: подписка на сайт (основной, рекомендуемый способ) -->
      <div class="km-card km-card-primary">
        <span class="km-recommend-badge">Рекомендуем</span>
        <h2 class="km-card-title">1. Подписка на сайт</h2>
        <p class="km-card-body">
          Самый простой и быстрый способ — оформляется прямо здесь за пару кликов. Полный
          премиум-доступ ко всем песням сайта.
        </p>

        <div v-if="!isLoggedIn" class="km-login-hint">
          Чтобы оформить подписку, сначала <RouterLink to="/login">войдите</RouterLink> или
          <RouterLink to="/register">зарегистрируйтесь</RouterLink>.
        </div>
        <template v-else>
          <div v-if="loadingTariffs" class="km-hint">Загрузка тарифов...</div>
          <div v-else-if="tariffs.length === 0" class="km-hint">Тарифы пока не настроены.</div>
          <div v-else class="km-tariff-list">
            <button
              v-for="t in tariffs" :key="t.id"
              class="km-tariff-card"
              :class="{ 'km-tariff-selected': selectedTariffId === t.id }"
              @click="selectTariff(t.id)"
            >
              <div class="km-tariff-name">{{ t.name }}</div>
              <div class="km-tariff-period">{{ t.periodDays }} дн.</div>
              <div class="km-tariff-price">{{ t.priceRub }} ₽</div>
            </button>
          </div>

          <template v-if="selectedTariffId">
            <div v-if="loadingPrice" class="km-hint">Расчёт цены...</div>
            <template v-else-if="priceInfo">
              <div class="km-price-row">
                <span v-if="priceInfo.discount > 0" class="km-price-old">{{ priceInfo.base }} ₽</span>
                <span class="km-price-final">{{ priceInfo.final }} ₽</span>
                <span v-if="priceInfo.promoApplied" class="km-promo-badge">{{ priceInfo.promoApplied }}</span>
              </div>
              <label class="km-checkbox-label">
                <input type="checkbox" v-model="autoRenew">
                <span>Автопродление (спишем автоматически по истечении срока; можно отключить в любой момент в личном кабинете)</span>
              </label>
              <label class="km-checkbox-label">
                <input type="checkbox" v-model="disclaimerAccepted">
                <span>
                  Я понимаю, что оплачиваю работу автора проекта (разметку, синхронизацию, плеер) — права на
                  музыкальные произведения и фонограммы принадлежат правообладателям и ко мне не переходят.
                  Доступ — для личного некоммерческого использования, без передачи файлов и без скачивания.
                  Полные условия — в <RouterLink to="/oferta" target="_blank">публичной оферте</RouterLink>.
                </span>
              </label>
              <p v-if="submitError" class="km-error">{{ submitErrorText }}</p>
              <p v-if="priceInfo.final > 0 && priceInfo.paymentsEnabled === false" class="km-error">
                Приём платежей ещё не подключён, попробуйте позже.
              </p>
              <button
                class="km-btn km-btn-primary"
                :disabled="!disclaimerAccepted || submitting || (priceInfo.final > 0 && priceInfo.paymentsEnabled === false)"
                @click="onSubscribe"
              >
                {{ submitting ? 'Оформляем...' : (priceInfo.final > 0 ? 'Оплатить' : 'Активировать бесплатно') }}
              </button>
            </template>
          </template>
        </template>
      </div>

      <!-- Путь 2: подписка на песню -->
      <div class="km-card">
        <h2 class="km-card-title">2. Подписка на одну песню</h2>
        <p class="km-card-body">
          Не нужна подписка на весь сайт? На странице понравившейся песни (если она доступна для подписки)
          есть кнопка «Оформить подписку только на эту песню» — бессрочный доступ без переплаты.
        </p>
        <div v-if="loadingSongTariff" class="km-hint">Загрузка цены...</div>
        <div v-else-if="songTariff" class="km-price-row">
          <span class="km-price-final">{{ songTariff.priceRub }} ₽</span>
          <span class="km-song-price-note">за песню, бессрочно</span>
        </div>
        <RouterLink to="/zakroma" class="km-btn km-btn-secondary">Найти песню →</RouterLink>
      </div>

      <!-- Путь 3: Sponsr -->
      <div class="km-card">
        <h2 class="km-card-title">3. Подписка на Sponsr</h2>
        <p class="km-card-body">
          Оформите платную подписку на <a href="https://sponsr.ru/smkaraoke" target="_blank" rel="noopener">sponsr.ru/smkaraoke</a>.
          После оплаты укажите свой Sponsr-профиль в <RouterLink to="/account">личном кабинете</RouterLink> (поле
          «Sponsr UID») — мы сверим активных подписчиков и включим премиум автоматически (обычно в течение суток).
        </p>
        <a href="https://sponsr.ru/smkaraoke" target="_blank" rel="noopener" class="km-btn km-btn-secondary">Перейти на Sponsr →</a>
      </div>
    </div>
  </div>
</template>

<script>
import { useAuth } from '../composables/useAuth'
import { useSiteSubscription } from '../composables/useSiteSubscription'
import { authGet } from '../services/authApi'

export default {
  name: 'PremiumView',
  setup() {
    const { isLoggedIn } = useAuth()
    const {
      loadingTariffs, tariffs, loadingPrice, priceInfo, submitting, error,
      loadTariffs, loadPrice, subscribe,
    } = useSiteSubscription()
    return { isLoggedIn, loadingTariffs, tariffs, loadingPrice, priceInfo, submitting, error, loadTariffs, loadPrice, subscribe }
  },
  data() {
    return {
      selectedTariffId: null, autoRenew: true, disclaimerAccepted: false, submitError: false,
      songTariff: null, loadingSongTariff: false,
    }
  },
  computed: {
    submitErrorText() {
      if (this.error === 'payment_unavailable') return 'Оплата временно недоступна, попробуйте позже.'
      return 'Не удалось оформить подписку. Попробуйте ещё раз.'
    }
  },
  mounted() {
    // Список тарифов теперь публичный (см. WebMvcConfig) — загружаем сразу, не дожидаясь входа,
    // чтобы цена мотивировала зарегистрироваться, а не пряталась за логином.
    this.loadTariffs('SITE')
    this.loadSongTariff()
  },
  methods: {
    async loadSongTariff() {
      this.loadingSongTariff = true
      try {
        const { status, body } = await authGet('/api/public/account/subscription/tariffs?scope=SONG', '')
        if (status === 200 && Array.isArray(body) && body.length > 0) {
          this.songTariff = body.find(t => t.isDefault) || body[0]
        }
      } catch (e) { /* карточка просто не покажет цену */ }
      this.loadingSongTariff = false
    },
    selectTariff(id) {
      this.selectedTariffId = id
      this.disclaimerAccepted = false
      this.submitError = false
      this.loadPrice(id)
    },
    async onSubscribe() {
      this.submitError = false
      const result = await this.subscribe(this.selectedTariffId, this.autoRenew, true)
      if (!result.ok) { this.submitError = true; return }
      if (result.confirmationUrl) {
        window.location.href = result.confirmationUrl
        return
      }
      // Акция довела цену до нуля — доступ уже активен.
      this.$router.push('/account')
    }
  }
}
</script>

<style scoped>
.km-page { min-height: 100vh; background: var(--km-bg); color: var(--km-text); }
.km-header { background: var(--km-header); border-bottom: 1px solid var(--km-border); padding: 0.5rem 1rem; }
.km-header-inner { max-width: 700px; margin: 0 auto; display: flex; align-items: center; }
.km-header-left { display: flex; align-items: center; gap: 0.75rem; }
.km-back { color: var(--km-accent); text-decoration: none; font-size: 0.85rem; }
.km-logo { height: 36px; width: auto; }
.km-content { max-width: 600px; margin: 0 auto; padding: 2rem 1rem; }
.km-title { font-size: 1.5rem; margin: 0 0 0.5rem; }
.km-lead { color: var(--km-text2); margin: 0 0 1.5rem; }
.km-delivery-note { font-size: 0.82rem; color: var(--km-text2); background: var(--km-hover); border-radius: 8px; padding: 0.6rem 0.9rem; margin: -0.75rem 0 1.5rem; }
.km-card { background: var(--km-card); border: 1px solid var(--km-border); border-radius: 14px; padding: 1.5rem; margin-bottom: 1.25rem; position: relative; }
.km-card-primary { border: 2px solid var(--km-accent); }
.km-recommend-badge {
  position: absolute; top: -0.65rem; left: 1.25rem;
  background: var(--km-accent); color: #fff; font-size: 0.72rem; font-weight: 700;
  padding: 0.15rem 0.6rem; border-radius: 20px;
}
.km-card-title { font-size: 1.1rem; margin: 0 0 0.6rem; }
.km-card-body { font-size: 0.9rem; color: var(--km-text2); line-height: 1.5; margin: 0 0 1rem; }
.km-btn { display: inline-block; border-radius: 8px; padding: 0.6rem 1.3rem; font-weight: 600; text-decoration: none; cursor: pointer; border: none; font-size: 0.9rem; }
.km-btn-primary { background: var(--km-accent); color: #fff; }
.km-btn-primary:hover { opacity: 0.9; }
.km-btn-primary:disabled { opacity: 0.5; cursor: default; }
.km-btn-secondary { background: transparent; color: var(--km-accent); border: 1px solid var(--km-accent); }
.km-btn-secondary:hover { background: var(--km-hover); }
.km-login-hint, .km-hint { font-size: 0.88rem; color: var(--km-text2); }
.km-tariff-list { display: flex; gap: 0.75rem; flex-wrap: wrap; margin-bottom: 1rem; }
.km-tariff-card {
  flex: 1; min-width: 120px; background: var(--km-input); border: 2px solid var(--km-border); border-radius: 10px;
  padding: 0.75rem; text-align: center; cursor: pointer; color: var(--km-text);
}
.km-tariff-card:hover { border-color: var(--km-accent); }
.km-tariff-selected { border-color: var(--km-accent); background: var(--km-hover); }
.km-tariff-name { font-weight: 600; font-size: 0.9rem; margin-bottom: 0.2rem; }
.km-tariff-period { font-size: 0.75rem; color: var(--km-text2); margin-bottom: 0.3rem; }
.km-tariff-price { font-weight: 700; }
.km-price-row { display: flex; align-items: baseline; gap: 0.5rem; margin-bottom: 0.75rem; }
.km-song-price-note { font-size: 0.8rem; color: var(--km-text2); }
.km-price-old { text-decoration: line-through; color: var(--km-text2); }
.km-price-final { font-size: 1.4rem; font-weight: 800; }
.km-promo-badge { font-size: 0.75rem; background: linear-gradient(135deg, #f6c94b, #d99413); color: #5a3c00; padding: 0.15rem 0.5rem; border-radius: 12px; }
.km-checkbox-label { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.8rem; color: var(--km-text2); margin-bottom: 0.75rem; cursor: pointer; }
.km-checkbox-label input { margin-top: 0.2rem; flex-shrink: 0; }
.km-error { color: #e05555; font-size: 0.85rem; }
</style>
