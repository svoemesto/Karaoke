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
        <h1 class="km-title">Регистрация</h1>

        <div class="km-field">
          <label class="km-label">Email</label>
          <input v-model="email" type="email" class="km-input" autocomplete="username" />
        </div>
        <div class="km-field">
          <label class="km-label">Имя</label>
          <input v-model="displayName" type="text" class="km-input" autocomplete="nickname" />
        </div>
        <div class="km-field">
          <label class="km-label">Пароль</label>
          <input v-model="password" type="password" class="km-input" autocomplete="new-password" />
        </div>
        <div class="km-field">
          <label class="km-label">Повторите пароль</label>
          <input
            v-model="passwordConfirm"
            type="password"
            class="km-input"
            autocomplete="new-password"
            @keyup.enter="onSubmit"
          />
        </div>

        <div v-if="captchaClientKey" id="km-captcha-container" class="km-captcha"></div>

        <p v-if="errorMessage" class="km-error">{{ errorMessage }}</p>

        <button class="km-submit-btn" :disabled="loading" @click="onSubmit">
          {{ loading ? 'Регистрируем...' : 'Зарегистрироваться' }}
        </button>

        <p class="km-alt-link">Уже есть аккаунт? <RouterLink to="/login">Войти</RouterLink></p>
      </div>
    </div>
  </div>
</template>

<script>
import { authGet, authPost } from '../services/authApi'
import { useAuth } from '../composables/useAuth'

const CAPTCHA_SCRIPT_SRC = 'https://smartcaptcha.yandexcloud.net/captcha.js'

export default {
  name: 'RegisterView',
  setup() {
    const { setSession } = useAuth()
    return { setSession }
  },
  data() {
    return {
      email: '',
      displayName: '',
      password: '',
      passwordConfirm: '',
      loading: false,
      errorMessage: '',
      captchaClientKey: '',
      captchaToken: '',
      captchaWidgetId: null,
    }
  },
  async mounted() {
    try {
      const { status, body } = await authGet('/api/public/auth/config')
      if (status === 200 && body && body.captchaClientKey) {
        this.captchaClientKey = body.captchaClientKey
        await this.loadCaptchaWidget()
      }
    } catch (e) {
      // Капча не настроена/недоступна — регистрация всё равно пройдёт, бэкенд пропускает
      // проверку токена, если у него самого не задан серверный ключ.
    }
  },
  methods: {
    loadCaptchaWidget() {
      return new Promise((resolve) => {
        const existing = document.querySelector(`script[src="${CAPTCHA_SCRIPT_SRC}"]`)
        const onReady = () => {
          this.$nextTick(() => {
            if (window.smartCaptcha && document.getElementById('km-captcha-container')) {
              this.captchaWidgetId = window.smartCaptcha.render('km-captcha-container', {
                sitekey: this.captchaClientKey,
                callback: (token) => {
                  this.captchaToken = token
                },
              })
            }
            resolve()
          })
        }
        if (existing) {
          onReady()
          return
        }
        const script = document.createElement('script')
        script.src = CAPTCHA_SCRIPT_SRC
        script.defer = true
        script.onload = onReady
        script.onerror = () => resolve()
        document.head.appendChild(script)
      })
    },
    async onSubmit() {
      this.errorMessage = ''
      if (!this.email || !this.password) {
        this.errorMessage = 'Заполните email и пароль'
        return
      }
      if (!this.displayName.trim()) {
        this.errorMessage = 'Заполните имя'
        return
      }
      if (this.password !== this.passwordConfirm) {
        this.errorMessage = 'Пароли не совпадают'
        return
      }
      if (this.password.length < 6) {
        this.errorMessage = 'Пароль должен быть не короче 6 символов'
        return
      }
      if (this.captchaClientKey && !this.captchaToken) {
        this.errorMessage = 'Подтвердите, что вы не робот'
        return
      }

      this.loading = true
      try {
        const { status, body } = await authPost('/api/public/auth/register', {
          email: this.email,
          password: this.password,
          passwordConfirm: this.passwordConfirm,
          displayName: this.displayName,
          captchaToken: this.captchaToken,
        })
        if (status === 201 && body && body.token) {
          this.setSession(body.token, body.user)
          this.$router.push('/account')
          return
        }
        this.errorMessage = this.describeError(body && body.error)
        if (window.smartCaptcha && this.captchaWidgetId !== null) {
          window.smartCaptcha.reset(this.captchaWidgetId)
          this.captchaToken = ''
        }
      } catch (e) {
        this.errorMessage = 'Не удалось связаться с сервером'
      } finally {
        this.loading = false
      }
    },
    describeError(code) {
      switch (code) {
        case 'email_taken':
          return 'Этот email уже зарегистрирован'
        case 'invalid_email':
          return 'Некорректный email'
        case 'display_name_required':
          return 'Заполните имя'
        case 'weak_password':
          return 'Пароль должен быть не короче 6 символов'
        case 'password_mismatch':
          return 'Пароли не совпадают'
        case 'captcha':
          return 'Проверка «не робот» не пройдена'
        default:
          return 'Не удалось зарегистрироваться'
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
.km-captcha {
  margin: 0.75rem 0;
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
