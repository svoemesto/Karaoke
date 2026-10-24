<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader :back="{ to: '/', label: '← Главная' }" />

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

        <div v-if="captchaClientKey" id="km-captcha-container" class="km-captcha" />

        <!-- BR-340: капча не загрузилась — честно предупреждаем. Отправку не блокируем
             (клиентский гейт без виджета невыполним), решение за серверной проверкой. -->
        <p v-if="captchaUnavailable" class="km-error">
          Проверка «не робот» недоступна. Обновите страницу, если кнопка регистрации не сработает.
        </p>

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
import AppHeader from '../components/AppHeader.vue'
import {
  CAPTCHA_STATE,
  captchaScriptState,
  captchaClientGateEnabled,
} from '../utils/captchaReady.js'

const CAPTCHA_SCRIPT_SRC = 'https://smartcaptcha.yandexcloud.net/captcha.js'
// Сколько ждём появления window.smartCaptcha после того, как <script> уже в DOM.
// По истечении считаем капчу недоступной (не блокируем отправку, решает сервер).
const CAPTCHA_READY_TIMEOUT_MS = 10000

/**
 * View-страница «Register» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'RegisterView',
  components: { AppHeader },
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
      // BR-340 (аудит #336, PKG-S2-14): состояние скрипта капчи — load | wait | ready | unavailable.
      captchaState: CAPTCHA_STATE.LOAD,
    }
  },
  computed: {
    captchaUnavailable() {
      return this.captchaState === CAPTCHA_STATE.UNAVAILABLE
    },
  },
  async mounted() {
    try {
      const { status, body } = await authGet('/api/public/auth/config')
      if (status === 200 && body && body.captchaClientKey) {
        this.captchaClientKey = body.captchaClientKey
        await this.loadCaptchaWidget()
      }
    } catch (e) {
      // Капча не настроена/недоступна. Спека 564 (OpenProject #362): проверка капчи на бэкенде
      // fail-closed — если серверный ключ не задан, регистрация отклоняется с кодом
      // captcha_not_configured, а НЕ проходит «просто так». Пользователь увидит сообщение о
      // временной недоступности регистрации (describeError ниже).
    }
  },
  methods: {
    loadCaptchaWidget() {
      return new Promise((resolve) => {
        // BR-340 (аудит #336, PKG-S2-14): наличие <script> в DOM — НЕ признак готовности.
        // Раньше ветка `if (existing) { onReady(); return }` звала onReady() немедленно:
        // если скрипт ещё грузился или упал раньше (элемент оставался в head навсегда),
        // window.smartCaptcha не был определён, виджет не рендерился, captchaToken
        // оставался пустым, а гейт отправки требовал невыполнимое «Подтвердите, что вы
        // не робот» — регистрация блокировалась до полной перезагрузки страницы.
        const decide = (failed, timedOut) => {
          const state = captchaScriptState({
            scriptPresent: !!document.querySelector(`script[src="${CAPTCHA_SCRIPT_SRC}"]`),
            globalReady: !!window.smartCaptcha,
            failed,
            timedOut,
          })
          this.captchaState = state
          if (state === CAPTCHA_STATE.READY) {
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
            return
          }
          if (state === CAPTCHA_STATE.UNAVAILABLE) {
            // Упавший/зависший тег убираем, чтобы следующий заход создал его заново.
            const broken = document.querySelector(`script[src="${CAPTCHA_SCRIPT_SRC}"]`)
            if (broken) broken.remove()
            resolve()
            return
          }
          if (state === CAPTCHA_STATE.WAIT) {
            // Тег есть, глобал ещё не определён — ждём load с таймаутом.
            const waiting = document.querySelector(`script[src="${CAPTCHA_SCRIPT_SRC}"]`)
            let settled = false
            const finish = (failed, timedOut) => {
              if (settled) return
              settled = true
              waiting.removeEventListener('load', onLoad)
              clearTimeout(timer)
              decide(failed, timedOut)
            }
            const onLoad = () => finish(false, false)
            const timer = setTimeout(() => finish(false, true), CAPTCHA_READY_TIMEOUT_MS)
            waiting.addEventListener('load', onLoad)
            return
          }
          // LOAD — тега нет: создаём и грузим.
          const script = document.createElement('script')
          script.src = CAPTCHA_SCRIPT_SRC
          script.defer = true
          script.onload = () => decide(false, false)
          script.onerror = () => decide(true, false)
          document.head.appendChild(script)
        }
        decide(false, false)
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
      // BR-340: клиентский гейт включается ТОЛЬКО когда виджет реально отрисован.
      // Если капча недоступна — не требуем невыполнимое, решение остаётся за серверной
      // проверкой (она fail-closed: captcha_not_configured / captcha).
      if (
        captchaClientGateEnabled({
          clientKey: this.captchaClientKey,
          token: this.captchaToken,
          state: this.captchaState,
        })
      ) {
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
        case 'captcha_not_configured':
          // Спека 564: серверный ключ проверки капчи не настроен — регистрация отклоняется
          // (fail-closed). Это не ошибка посетителя, а временная недоступность регистрации.
          return 'Регистрация временно недоступна. Попробуйте позже.'
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
