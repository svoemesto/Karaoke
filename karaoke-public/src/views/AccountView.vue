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
      <h1 class="km-title">Личный кабинет</h1>

      <div class="km-form-card">
        <h2 class="km-subtitle">Профиль</h2>
        <div class="km-field">
          <label class="km-label">Email</label>
          <input :value="user && user.email" type="email" class="km-input" disabled />
        </div>
        <div class="km-field">
          <label class="km-label">Имя</label>
          <input v-model="profileForm.displayName" type="text" class="km-input" />
        </div>
        <div class="km-field">
          <label class="km-label">Sponsr UID <span class="km-hint">(если вы подписчик sponsr.ru)</span></label>
          <input v-model="profileForm.sponsrUid" type="text" class="km-input" placeholder="Например, 357500" />
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
    </div>
  </div>
</template>

<script>
import { authPost } from '../services/authApi'
import { useAuth } from '../composables/useAuth'

export default {
  name: 'AccountView',
  setup() {
    const { token, user, setSession, clearSession, fetchMe } = useAuth()
    return { token, user, setSession, clearSession, fetchMe }
  },
  data() {
    return {
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
    async onSaveProfile() {
      this.profileMessage = ''
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
.km-title { font-size: 1.4rem; margin: 0 0 1.25rem; }
.km-form-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.5rem;
  margin-bottom: 1.25rem;
}
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
</style>
