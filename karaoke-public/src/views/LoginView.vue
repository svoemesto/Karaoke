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
      <div class="km-form-card">
        <h1 class="km-title">Вход</h1>
        <p v-if="redirectNotice" class="km-notice">Войдите, чтобы открыть личный кабинет.</p>

        <div class="km-field">
          <label class="km-label">Email</label>
          <input v-model="email" type="email" class="km-input" autocomplete="username" />
        </div>
        <div class="km-field">
          <label class="km-label">Пароль</label>
          <input
            v-model="password"
            type="password"
            class="km-input"
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </div>

        <p v-if="errorMessage" class="km-error">{{ errorMessage }}</p>

        <button class="km-submit-btn" :disabled="loading" @click="onSubmit">
          {{ loading ? 'Входим...' : 'Войти' }}
        </button>

        <p class="km-alt-link">
          Нет аккаунта? <RouterLink to="/register">Зарегистрироваться</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
import { authPost } from '../services/authApi'
import { useAuth } from '../composables/useAuth'

/**
 * View-страница «Login» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'LoginView',
  setup() {
    const { setSession } = useAuth()
    return { setSession }
  },
  data() {
    return {
      email: '',
      password: '',
      loading: false,
      errorMessage: '',
    }
  },
  computed: {
    redirectNotice() {
      return !!this.$route.query.redirect
    },
  },
  methods: {
    async onSubmit() {
      this.errorMessage = ''
      if (!this.email || !this.password) {
        this.errorMessage = 'Заполните email и пароль'
        return
      }
      this.loading = true
      try {
        const { status, body } = await authPost('/api/public/auth/login', {
          email: this.email,
          password: this.password,
        })
        if (status === 200 && body && body.token) {
          this.setSession(body.token, body.user)
          this.$router.push(this.$route.query.redirect || '/')
          return
        }
        if (status === 403 && body && body.error === 'banned') {
          this.errorMessage = `Аккаунт заблокирован${body.reason ? ': ' + body.reason : ''}`
        } else {
          this.errorMessage = 'Неверный email или пароль'
        }
      } catch (e) {
        this.errorMessage = 'Не удалось связаться с сервером'
      } finally {
        this.loading = false
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
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.km-header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
  white-space: nowrap;
}
.km-back:hover {
  text-decoration: underline;
}
.km-logo {
  height: 36px;
  width: auto;
}

.km-content {
  max-width: 420px;
  margin: 0 auto;
  padding: 2.5rem 1rem;
}
.km-form-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.5rem;
}
.km-title {
  margin: 0 0 1rem;
  font-size: 1.3rem;
  color: var(--km-text);
}
.km-notice {
  font-size: 0.85rem;
  color: var(--km-text2);
  margin-bottom: 1rem;
}
.km-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.9rem;
}
.km-label {
  font-size: 0.75rem;
  color: var(--km-text2);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.km-input {
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  font-size: 0.95rem;
  width: 100%;
}
.km-input:focus {
  outline: none;
  border-color: var(--km-accent);
}
.km-error {
  color: #e05555;
  font-size: 0.85rem;
  margin: 0.5rem 0;
}
.km-submit-btn {
  width: 100%;
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.6rem 1rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  margin-top: 0.5rem;
}
.km-submit-btn:hover {
  opacity: 0.88;
}
.km-submit-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.km-alt-link {
  text-align: center;
  font-size: 0.85rem;
  color: var(--km-text2);
  margin-top: 1rem;
}
.km-alt-link a {
  color: var(--km-accent);
}
</style>
