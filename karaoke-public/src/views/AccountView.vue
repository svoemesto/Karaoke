<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <button class="km-logout-btn" @click="onLogout">Выйти</button>
      </div>
    </header>

    <div class="km-content">
      <h1 class="km-title">
        Личный кабинет
        <span v-if="isPremium" class="km-premium-badge" title="Премиум-подписчик">🪙 Премиум</span>
      </h1>

      <div class="km-tabs">
        <button class="km-tab" :class="{ 'km-tab-active': activeTab === 'overview' }" @click="activeTab = 'overview'">Обзор</button>
        <button class="km-tab" :class="{ 'km-tab-active': activeTab === 'profile' }" @click="activeTab = 'profile'">Профиль и пароль</button>
      </div>

      <template v-if="activeTab === 'overview'">
        <div class="km-form-card" v-if="premiumSourceText">
          <h2 class="km-subtitle">Премиум-доступ</h2>
          <p class="km-premium-source">{{ premiumSourceText }}</p>
        </div>
        <div class="km-form-card" v-else>
          <h2 class="km-subtitle">Премиум-доступ</h2>
          <p class="km-hint-text">У вас пока нет активной подписки.</p>
          <RouterLink to="/premium" class="km-submit-btn km-link-btn">Оформить подписку →</RouterLink>
        </div>

        <RouterLink to="/account/playlists" class="km-nav-card">
          <span class="km-nav-card-title">🎵 Мои плейлисты</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>

        <RouterLink to="/account/chat" class="km-nav-card">
          <span class="km-nav-card-title">💬 Чат с автором проекта</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>

        <RouterLink to="/account/subscriptions" class="km-nav-card">
          <span class="km-nav-card-title">💳 Мои подписки</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>

        <RouterLink to="/account/stemjobs" class="km-nav-card">
          <span class="km-nav-card-title">🎚️ Создать минусовку</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>

        <RouterLink to="/account/cart" class="km-nav-card">
          <span class="km-nav-card-title">🛒 Корзина{{ cartCount > 0 ? ` (${cartCount})` : '' }}</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>

        <RouterLink v-if="isEditor" to="/account/editor" class="km-nav-card">
          <span class="km-nav-card-title">🎤 Редактор караоке</span>
          <span class="km-nav-card-arrow">→</span>
        </RouterLink>
      </template>

      <template v-else>
        <div class="km-form-card">
          <h2 class="km-subtitle">Профиль</h2>
          <div class="km-field">
            <label class="km-label">Email</label>
            <input :value="user && user.email" type="email" class="km-input" disabled />
          </div>
          <div class="km-field">
            <label class="km-label">Имя</label>
            <input v-model="profileForm.displayName" type="text" class="km-input" required />
          </div>
          <div class="km-field">
            <label class="km-label">Sponsr UID <span class="km-hint">(если вы подписчик sponsr.ru)</span></label>
            <input v-model="profileForm.sponsrUid" type="text" class="km-input" placeholder="Например, 357500" />
          </div>
          <div v-if="personalDiscountPercent > 0" class="km-discount-badge">
            🏷️ Ваша постоянная скидка: {{ personalDiscountPercent }}% — применяется к любой подписке.
          </div>
          <p v-if="profileMessage" :class="['km-message', profileError ? 'km-error' : 'km-success']">{{ profileMessage }}</p>
          <button class="km-submit-btn" :disabled="profileLoading" @click="onSaveProfile">
            {{ profileLoading ? 'Сохраняем...' : 'Сохранить' }}
          </button>
        </div>

        <div class="km-form-card">
          <h2 class="km-subtitle">Смена пароля</h2>
          <div class="km-field">
            <label class="km-label">Текущий пароль</label>
            <input v-model="passwordForm.oldPassword" type="password" class="km-input" autocomplete="current-password" />
          </div>
          <div class="km-field">
            <label class="km-label">Новый пароль</label>
            <input v-model="passwordForm.newPassword" type="password" class="km-input" autocomplete="new-password" @keyup.enter="onChangePassword" />
          </div>
          <p v-if="passwordMessage" :class="['km-message', passwordError ? 'km-error' : 'km-success']">{{ passwordMessage }}</p>
          <button class="km-submit-btn" :disabled="passwordLoading" @click="onChangePassword">
            {{ passwordLoading ? 'Сохраняем...' : 'Сменить пароль' }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import { authPost } from '../services/authApi'
import { useAuth } from '../composables/useAuth'
import { useCart } from '../composables/useCart'

export default {
  name: 'AccountView',
  setup() {
    const { token, user, setSession, clearSession, fetchMe } = useAuth()
    const cart = useCart()
    cart.load()
    return { token, user, setSession, clearSession, fetchMe, cart }
  },
  computed: {
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    personalDiscountPercent() {
      return (this.user && this.user.personalDiscountPercent) || 0
    },
    cartCount() {
      return this.cart.count.value
    },
    isEditor() {
      return !!(this.user && this.user.editor)
    },
    // Показываем ИСТОЧНИК премиума и до какой даты он активен — permanentPremium/isPremium
    // (ручной грант админа) не имеют срока, поэтому для них не показываем дату.
    premiumSourceText() {
      const u = this.user
      if (!u) return ''
      if (u.permanentPremium || u.premium) return 'Премиум активен (предоставлен администрацией сайта).'
      const parts = []
      if (u.sponsrPremiumUntil) parts.push(`через Sponsr — до ${this.formatDate(u.sponsrPremiumUntil)}`)
      if (u.sitePremiumUntil) parts.push(`подписка на сайт — до ${this.formatDate(u.sitePremiumUntil)}`)
      if (parts.length === 0) return ''
      return 'Премиум активен: ' + parts.join('; ') + '.'
    }
  },
  data() {
    return {
      activeTab: 'overview',
      profileForm: { displayName: '', sponsrUid: '' },
      profileLoading: false,
      profileMessage: '',
      profileError: false,
      passwordForm: { oldPassword: '', newPassword: '' },
      passwordLoading: false,
      passwordMessage: '',
      passwordError: false,
    }
  },
  async mounted() {
    await this.fetchMe()
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account' } })
      return
    }
    if (this.user) {
      this.profileForm.displayName = this.user.displayName || ''
      this.profileForm.sponsrUid = this.user.sponsrUid || ''
    }
  },
  methods: {
    // sponsrPremiumUntil/sitePremiumUntil приходят как java.sql.Timestamp.toString()
    // ("yyyy-MM-dd HH:mm:ss.SSS") — заменяем пробел на T, чтобы Date его распарсил.
    formatDate(tsString) {
      try {
        const d = new Date(tsString.replace(' ', 'T'))
        return d.toLocaleDateString('ru-RU')
      } catch (e) { return tsString }
    },
    async onSaveProfile() {
      this.profileMessage = ''
      if (!this.profileForm.displayName.trim()) {
        this.profileMessage = 'Заполните имя'
        this.profileError = true
        return
      }
      this.profileLoading = true
      try {
        const { status, body } = await authPost('/api/public/account/profile', {
          displayName: this.profileForm.displayName,
          sponsrUid: this.profileForm.sponsrUid,
        }, this.token)
        if (status === 200 && body) {
          this.setSession(this.token, body)
          this.profileMessage = 'Сохранено'
          this.profileError = false
        } else if (body && body.error === 'display_name_required') {
          this.profileMessage = 'Заполните имя'
          this.profileError = true
        } else {
          this.profileMessage = 'Не удалось сохранить'
          this.profileError = true
        }
      } catch (e) {
        this.profileMessage = 'Не удалось связаться с сервером'
        this.profileError = true
      } finally {
        this.profileLoading = false
      }
    },
    async onChangePassword() {
      this.passwordMessage = ''
      if (!this.passwordForm.oldPassword || !this.passwordForm.newPassword) {
        this.passwordMessage = 'Заполните оба поля'
        this.passwordError = true
        return
      }
      this.passwordLoading = true
      try {
        const { status, body } = await authPost('/api/public/account/change-password', {
          oldPassword: this.passwordForm.oldPassword,
          newPassword: this.passwordForm.newPassword,
        }, this.token)
        if (status === 200) {
          this.passwordMessage = 'Пароль изменён'
          this.passwordError = false
          this.passwordForm.oldPassword = ''
          this.passwordForm.newPassword = ''
        } else if (body && body.error === 'invalid_old_password') {
          this.passwordMessage = 'Неверный текущий пароль'
          this.passwordError = true
        } else {
          this.passwordMessage = 'Пароль должен быть не короче 6 символов'
          this.passwordError = true
        }
      } catch (e) {
        this.passwordMessage = 'Не удалось связаться с сервером'
        this.passwordError = true
      } finally {
        this.passwordLoading = false
      }
    },
    async onLogout() {
      const currentToken = this.token
      this.clearSession()
      if (currentToken) {
        try { await authPost('/api/public/auth/logout', {}, currentToken) } catch (e) { /* сессия уже очищена локально */ }
      }
      this.$router.push('/')
    }
  }
}
</script>

<style scoped>
.km-page { min-height: 100vh; background: var(--km-bg); color: var(--km-text); }
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner { max-width: 700px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between; }
.km-header-left { display: flex; align-items: center; gap: 0.75rem; }
.km-back { color: var(--km-accent); text-decoration: none; font-size: 0.85rem; white-space: nowrap; }
.km-back:hover { text-decoration: underline; }
.km-logo { height: 36px; width: auto; }
.km-logout-btn {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text2);
  border-radius: 8px;
  padding: 0.35rem 0.8rem;
  font-size: 0.85rem;
  cursor: pointer;
}
.km-logout-btn:hover { background: var(--km-hover); color: var(--km-text); }

.km-content { max-width: 500px; margin: 0 auto; padding: 2rem 1rem; }
.km-title { font-size: 1.4rem; margin: 0 0 1.25rem; display: flex; align-items: center; gap: 0.6rem; }
.km-premium-badge {
  font-size: 0.75rem;
  font-weight: 700;
  color: #7a5c00;
  background: linear-gradient(135deg, #ffe27a, #d4af37);
  border-radius: 20px;
  padding: 0.2rem 0.7rem;
  white-space: nowrap;
}
.km-tabs {
  display: flex;
  gap: 0.4rem;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid var(--km-border);
}
.km-tab {
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--km-text2);
  font-size: 0.9rem;
  font-weight: 600;
  padding: 0.6rem 0.9rem;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.km-tab:hover { color: var(--km-text); }
.km-tab-active { color: var(--km-accent); border-bottom-color: var(--km-accent); }
.km-form-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.5rem;
  margin-bottom: 1.25rem;
}
.km-nav-card {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--km-card); border: 1px solid var(--km-border); border-radius: 14px;
  padding: 1rem 1.5rem; margin-bottom: 1.25rem; text-decoration: none; color: var(--km-text);
}
.km-nav-card:hover { background: var(--km-hover); }
.km-nav-card-title { font-size: 1rem; font-weight: 600; }
.km-nav-card-arrow { color: var(--km-accent); font-size: 1.1rem; }
.km-subtitle { font-size: 1rem; margin: 0 0 1rem; color: var(--km-text); }
.km-field { display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 0.9rem; }
.km-label { font-size: 0.75rem; color: var(--km-text2); font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.km-hint { text-transform: none; font-weight: 400; letter-spacing: normal; }
.km-input {
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  font-size: 0.95rem;
  width: 100%;
}
.km-input:disabled { opacity: 0.6; }
.km-input:focus { outline: none; border-color: var(--km-accent); }
.km-message { font-size: 0.85rem; margin: 0.5rem 0; }
.km-error { color: #e05555; }
.km-success { color: #3fae5b; }
.km-submit-btn {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.5rem 1.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
}
.km-submit-btn:hover { opacity: 0.88; }
.km-submit-btn:disabled { opacity: 0.6; cursor: default; }
.km-link-btn { display: inline-block; text-decoration: none; text-align: center; }
.km-premium-source { font-size: 0.9rem; color: var(--km-text); margin: 0; }
.km-hint-text { font-size: 0.9rem; color: var(--km-text2); margin: 0 0 1rem; }
.km-discount-badge {
  font-size: 0.85rem;
  color: #5a3c00;
  background: linear-gradient(135deg, #f6c94b, #d99413);
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  margin-bottom: 1rem;
}
</style>
