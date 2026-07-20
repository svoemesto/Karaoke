<template>
  <div class="km-auth-widget">
    <RouterLink to="/news" class="km-auth-link">Новости</RouterLink>
    <template v-if="isLoggedIn">
      <RouterLink to="/account" class="km-auth-link">
        <span v-if="isPremium" class="km-premium-badge" title="Премиум-подписчик">🪙</span
        >{{ displayName }}
      </RouterLink>
      <RouterLink to="/account/playlists" class="km-auth-link">Плейлисты</RouterLink>
      <RouterLink to="/account/cart" class="km-auth-link"
        >🛒<span v-if="cartCount > 0" class="km-cart-count">{{ cartCount }}</span></RouterLink
      >
      <button class="km-auth-btn" @click="onLogout">Выйти</button>
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
import { authPost } from '../services/authApi'

export default {
  name: 'AuthStatusWidget',
  setup() {
    const { user, token, isLoggedIn, clearSession } = useAuth()
    const { count: cartCount, load: loadCart } = useCart()
    if (isLoggedIn.value) loadCart()
    return { user, token, isLoggedIn, clearSession, cartCount }
  },
  computed: {
    displayName() {
      return (this.user && this.user.displayName) || (this.user && this.user.email) || ''
    },
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  methods: {
    async onLogout() {
      const currentToken = this.token
      this.clearSession()
      if (currentToken) {
        try {
          await authPost('/api/public/auth/logout', {}, currentToken)
        } catch (e) {
          /* сессия уже очищена локально */
        }
      }
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
.km-auth-btn {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text2);
  border-radius: 8px;
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
  cursor: pointer;
}
.km-auth-btn:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
</style>
