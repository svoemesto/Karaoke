<template>
  <div class="km-auth-widget">
    <RouterLink to="/news" class="km-auth-link km-auth-link-news">Новости</RouterLink>
    <template v-if="isLoggedIn">
      <RouterLink to="/account" class="km-auth-link">
        <span v-if="isPremium" class="km-premium-badge" title="Премиум-подписчик">🪙</span
        >{{ displayName }}
      </RouterLink>
      <RouterLink to="/account/playlists" class="km-auth-link km-auth-link-playlists"
        >Плейлисты</RouterLink
      >
      <!-- Корзино показываем только если в ней что-то есть (spec 250). -->
      <RouterLink v-if="cartCount > 0" to="/account/cart" class="km-auth-link"
        >🛒<span class="km-cart-count">{{ cartCount }}</span></RouterLink
      >
      <!-- Выйти — перенесён в личный кабинет (spec 250, /account). -->
    </template>
    <template v-else>
      <RouterLink to="/login" class="km-auth-link">Войти</RouterLink>
      <RouterLink to="/register" class="km-auth-link km-auth-accent">Регистрация</RouterLink>
    </template>
  </div>
</template>

<script>
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'

/**
 * Компонент «Auth Status Widget» — набор ссылок для авторизованного/анонимного
 * пользователя в правом слоте `<AppHeader>` (spec 250).
 *
 * Состав:
 * - Ссылка «Новости» (всегда).
 * - Для залогиненного: имя (с 🪙-бейджем если премиум), «Плейлисты»,
 *   🛒 (только если в корзине что-то есть).
 * - Для анонима: «Войти», «Регистрация».
 *
 * Кнопка «Выйти» намеренно отсутствует — перенесена в личный кабинет
 * (`AccountView.onLogout`) по запросу (spec 250, шапка).
 *
 * Live-логика premium (LiveDoc `162-fix-header-stale-premium-status`)
 * — реактивность на `auth.isPremium` через `usePremiumLiveSync`.
 *
 * @see specs/250-unify-site-header FR-001..FR-016
 * @see livedocs/features/250-unify-site-header
 * @see livedocs/features/162-fix-header-stale-premium-status
 */

export default {
  name: 'AuthStatusWidget',
  setup() {
    const { user, isLoggedIn } = useAuth()
    const { count: cartCount, load: loadCart } = useCart()
    if (isLoggedIn.value) loadCart()
    return { user, isLoggedIn, cartCount }
  },
  computed: {
    displayName() {
      return (this.user && this.user.displayName) || (this.user && this.user.email) || ''
    },
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
}
</script>

<style scoped>
.km-auth-widget {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.85rem;
}
.km-auth-link {
  color: var(--km-text2);
  text-decoration: none;
  white-space: nowrap;
}
.km-auth-link:hover {
  color: var(--km-text);
  text-decoration: underline;
}
/* Узкие экраны (≤ 700px): «Новости» и «Плейлисты» прячем — не помещаются. */
@media (max-width: 700px) {
  .km-auth-link-news,
  .km-auth-link-playlists {
    display: none;
  }
}
.km-auth-accent {
  color: var(--km-accent);
  font-weight: 600;
}
.km-premium-badge {
  margin-right: 0.3em;
}
.km-cart-count {
  display: inline-block;
  background: #7c3aed;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
  border-radius: 10px;
  padding: 0 0.35em;
  margin-left: 0.2em;
  vertical-align: top;
}
</style>
