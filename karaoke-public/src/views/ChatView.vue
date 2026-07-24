<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/" class="km-back">← Главная</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <RouterLink to="/account" class="km-back">Профиль →</RouterLink>
      </div>
    </header>

    <div class="km-body">
      <LoginRequired
        v-if="!isLoggedIn"
        text="Чат с автором проекта доступен только зарегистрированным пользователям. Войдите или зарегистрируйтесь — это бесплатно."
      />

      <!-- Чат целиком — премиум-функция (не только отправка, но и сама история переписки). -->
      <div v-else-if="!isPremium" class="km-chat-locked-card">
        <div class="km-locked-icon">💬</div>
        <h2 class="km-locked-title">Чат с автором проекта</h2>
        <p class="km-locked-text">
          Личная переписка с автором проекта доступна только премиум-подписчикам.
        </p>
        <RouterLink to="/premium" class="km-submit-btn">Оформить подписку →</RouterLink>
      </div>

      <div v-else class="km-chat-shell">
        <div class="km-chat-title-row">
          <h1 class="km-title">Чат с автором проекта</h1>
        </div>

        <div v-if="loading" class="km-loading">Загрузка...</div>

        <template v-else>
          <div ref="listEl" class="km-chat-messages">
            <p v-if="!messages.length" class="km-empty">Сообщений пока нет. Напишите первым!</p>
            <div v-if="hasMoreHistory" class="km-chat-history-row">
              <button
                v-if="canLoadPartial"
                class="km-chat-history-btn"
                :disabled="loadingHistory"
                @click="loadMoreHistory"
              >
                Подгрузить ещё {{ pageSize }}
              </button>
              <button class="km-chat-history-btn" :disabled="loadingHistory" @click="loadAllHistory">
                Подгрузить все {{ total }}
              </button>
            </div>
            <div
              v-for="m in messages"
              :key="m.id"
              class="km-chat-bubble"
              :class="m.fromAuthor ? 'km-chat-bubble-author' : 'km-chat-bubble-me'"
            >
              <div class="km-chat-bubble-body">{{ m.body }}</div>
              <div class="km-chat-bubble-time">{{ formatTime(m.createdAt) }}</div>
            </div>
          </div>

          <div class="km-chat-composer">
            <textarea
              ref="composerEl"
              v-model="draft"
              class="km-chat-textarea"
              placeholder="Написать сообщение..."
              rows="1"
              @input="autoGrowComposer"
              @keydown.enter.exact.prevent="onSend"
            />
            <button
              class="km-chat-send-btn"
              :disabled="sending || !draft.trim()"
              title="Отправить"
              @click="onSend"
            >
              <span v-if="!sending">➤</span>
              <span v-else class="km-chat-send-spinner" />
            </button>
          </div>
          <p v-if="error" class="km-chat-error">{{ error }}</p>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import LoginRequired from '../components/LoginRequired.vue'
import { useAuth } from '../composables/useAuth'
import { fetchMessages, sendMessage } from '../services/chatApi'

const POLL_INTERVAL_MS = 7000
const PAGE_SIZE = 10
const POLL_BATCH_LIMIT = 500 // защита от аномального залпа между двумя поллами, не реальный лимит

/**
 * View-страница «Chat» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'ChatView',
  components: { LoginRequired },
  setup() {
    const { user, isLoggedIn } = useAuth()
    return { user, isLoggedIn }
  },
  data() {
    return {
      messages: [], // окно уже загруженных сообщений (по возрастанию id), а не вся история треда
      total: 0, // общее число сообщений в треде — от бэкенда, для "Подгрузить все N" и hasMoreHistory
      loading: true,
      loadingHistory: false,
      draft: '',
      sending: false,
      error: '',
      pollTimer: null,
      pageSize: PAGE_SIZE,
    }
  },
  computed: {
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    hasMoreHistory() {
      return this.messages.length < this.total
    },
    canLoadPartial() {
      return this.total - this.messages.length > PAGE_SIZE
    },
  },
  async mounted() {
    if (!this.isLoggedIn || !this.isPremium) return
    await this.initialLoad()
    this.pollTimer = setInterval(() => this.poll(), POLL_INTERVAL_MS)
  },
  beforeUnmount() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    formatTime(tsString) {
      if (!tsString) return ''
      try {
        const d = new Date(tsString.replace(' ', 'T'))
        return d.toLocaleString('ru-RU', {
          day: '2-digit',
          month: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
        })
      } catch (e) {
        return tsString
      }
    },
    async initialLoad() {
      this.loading = true
      const { status, body } = await fetchMessages({ limit: PAGE_SIZE })
      if (status === 200 && body) {
        this.messages = body.messages || []
        this.total = body.total || 0
        this.$nextTick(this.scrollToBottom)
      }
      this.loading = false
    },
    // Поллинг подтягивает только НОВЫЕ сообщения (id > последнего загруженного) — не всю историю
    // треда заново. Автоскролл вниз — только если пользователь и так был у низа ленты (не выдёргиваем
    // его вниз, если он в этот момент читает историю выше).
    async poll() {
      if (!this.messages.length) return
      const el = this.$refs.listEl
      const wasNearBottom = el ? el.scrollHeight - el.scrollTop - el.clientHeight < 40 : true
      const afterId = this.messages[this.messages.length - 1].id
      const { status, body } = await fetchMessages({ afterId, limit: POLL_BATCH_LIMIT })
      if (status === 200 && body) {
        if (body.messages && body.messages.length) {
          this.messages = this.messages.concat(body.messages)
          if (wasNearBottom) this.$nextTick(this.scrollToBottom)
        }
        this.total = body.total || this.total
      }
    },
    scrollToBottom() {
      const el = this.$refs.listEl
      if (el) el.scrollTop = el.scrollHeight
    },
    // Подгрузка старых сообщений раскрывает список вверх — без компенсации скролла лента визуально
    // "прыгала" бы вниз (браузер сохраняет scrollTop, а не позицию просмотра при росте контента сверху).
    async loadHistory(count) {
      if (this.loadingHistory || !this.messages.length) return
      this.loadingHistory = true
      const el = this.$refs.listEl
      const prevHeight = el ? el.scrollHeight : 0
      const prevTop = el ? el.scrollTop : 0
      const beforeId = this.messages[0].id
      try {
        const { status, body } = await fetchMessages({ beforeId, limit: count })
        if (status === 200 && body) {
          this.messages = (body.messages || []).concat(this.messages)
          this.total = body.total || this.total
          this.$nextTick(() => {
            if (el) el.scrollTop = prevTop + (el.scrollHeight - prevHeight)
          })
        }
      } finally {
        this.loadingHistory = false
      }
    },
    loadMoreHistory() {
      this.loadHistory(PAGE_SIZE)
    },
    loadAllHistory() {
      this.loadHistory(this.total - this.messages.length)
    },
    // Авто-рост textarea по мере ввода многострочного текста — высота ограничена CSS
    // (max-height: 25vh, четверть экрана), дальше поле само уходит в внутренний скролл.
    autoGrowComposer() {
      const el = this.$refs.composerEl
      if (!el) return
      el.style.height = 'auto'
      el.style.height = `${el.scrollHeight}px`
    },
    async onSend() {
      const body = this.draft.trim()
      if (!body || this.sending) return
      this.error = ''
      this.sending = true
      try {
        const { status, body: resp } = await sendMessage(body)
        if (status === 200 && resp) {
          this.messages.push(resp)
          this.total += 1
          this.draft = ''
          this.$nextTick(() => {
            this.scrollToBottom()
            this.autoGrowComposer()
          })
        } else if (resp && resp.error === 'premium_required') {
          this.error = 'Отправка сообщений доступна только с активной премиум-подпиской'
        } else {
          this.error = 'Не удалось отправить сообщение'
        }
      } catch (e) {
        this.error = 'Не удалось связаться с сервером'
      } finally {
        this.sending = false
      }
    },
  },
}
</script>

<style scoped>
/* Полноэкранная раскладка мессенджера: шапка сверху фиксирована, лента сообщений скроллится
   независимо, поле ввода всегда прибито к низу видимой области (не уезжает при скролле страницы). */
.km-page {
  min-height: 100vh;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--km-bg);
  color: var(--km-text);
}
.km-header {
  flex-shrink: 0;
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner {
  max-width: 700px;
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

.km-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.km-chat-shell {
  flex: 1;
  min-height: 0;
  width: 100%;
  max-width: 700px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
}

.km-chat-locked-card {
  max-width: 440px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
  text-align: center;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
}
.km-locked-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
}
.km-locked-title {
  font-size: 1.2rem;
  font-weight: 700;
  margin: 0 0 0.6rem;
}
.km-locked-text {
  color: var(--km-text2);
  font-size: 0.92rem;
  line-height: 1.5;
  margin: 0 0 1.5rem;
}
.km-submit-btn {
  display: inline-block;
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.55rem 1.4rem;
  font-size: 0.9rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}
.km-submit-btn:hover {
  opacity: 0.9;
}

.km-chat-title-row {
  flex-shrink: 0;
  padding: 1rem 1rem 0.5rem;
}
.km-title {
  font-size: 1.2rem;
  margin: 0;
}
.km-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--km-text2);
}
.km-empty {
  color: var(--km-text2);
  font-size: 0.9rem;
  padding: 1rem 0;
  text-align: center;
}

/* Фон окна переписки — логотип в 1/8 исходного размера (1203×666 → ~150×83), непрозрачность 0.15.
   Бесшовный «кирпичный»/шахматный паттерн БЕЗ промежутков: тайл шириной ровно в один логотип
   (150×166 = 2 строки высотой 83), первая строка — один целый логотип на всю ширину тайла, вторая
   строка — два инстанса, обрезанных по левому/правому краю ровно наполовину (x=-75 и x=75), которые
   при повторении тайла стыкуются друг с другом без шва — классическая техника бесшовной плитки со
   смещением рядов на полширины. Паттерн — отдельный файл public/chat-bg-pattern.svg (лого встроен
   как base64, без внешней ссылки на PNG): встроенный <image href="..."> ВНУТРИ data:-URI SVG в
   background-image браузеры не подгружают (data:-URI не резолвит пути к внешним ресурсам) — фон не
   отображался. Отдельный SVG-файл с data:-URI логотипа ВНУТРИ него самодостаточен и лишён проблемы. */
.km-chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0.5rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  /* ?v= — cache-bust: файл переиздаётся под тем же именем при правках, без версии в query браузер
     может годами отдавать старую закэшированную копию (см. инцидент с расхождением public/webvue3
     при итерации над паттерном) — версию увеличивать при каждой правке содержимого файла. */
  background-image: url('/chat-bg-pattern.svg?v=3');
  background-repeat: repeat;
  background-size: 150px 166px;
}
.km-chat-history-row {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.2rem 0 0.4rem;
  flex-wrap: wrap;
}
.km-chat-history-btn {
  background: var(--km-card);
  color: var(--km-text2);
  border: 1px solid var(--km-border);
  border-radius: 20px;
  padding: 0.35rem 0.9rem;
  font-size: 0.8rem;
  cursor: pointer;
}
.km-chat-history-btn:hover {
  opacity: 0.85;
}
.km-chat-bubble {
  max-width: 78%;
  border-radius: 14px;
  padding: 0.5rem 0.8rem;
  font-size: 0.92rem;
  line-height: 1.4;
}
.km-chat-bubble-me {
  align-self: flex-end;
  background: var(--km-accent);
  color: #fff;
  border-bottom-right-radius: 3px;
}
.km-chat-bubble-author {
  align-self: flex-start;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  color: var(--km-text);
  border-bottom-left-radius: 3px;
}
.km-chat-bubble-body {
  white-space: pre-wrap;
  word-break: break-word;
}
.km-chat-bubble-time {
  font-size: 0.65rem;
  opacity: 0.7;
  margin-top: 0.2rem;
  text-align: right;
}

/* Композер — всегда видимая нижняя панель (как в Telegram/WhatsApp), не скроллится вместе с лентой. */
.km-chat-composer {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  padding: 0.6rem 1rem;
  border-top: 1px solid var(--km-border);
  background: var(--km-header);
}
.km-chat-textarea {
  flex: 1;
  resize: none;
  box-sizing: border-box;
  max-height: 25vh;
  overflow-y: auto;
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 20px;
  padding: 0.6rem 1rem;
  font-size: 0.95rem;
  font-family: inherit;
  line-height: 1.3;
}
.km-chat-textarea:focus {
  outline: none;
  border-color: var(--km-accent);
}
.km-chat-send-btn {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: none;
  background: var(--km-accent);
  color: #fff;
  font-size: 1.1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.km-chat-send-btn:hover {
  opacity: 0.88;
}
.km-chat-send-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.km-chat-send-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: km-chat-spin 0.7s linear infinite;
}
@keyframes km-chat-spin {
  to {
    transform: rotate(360deg);
  }
}

.km-chat-error {
  flex-shrink: 0;
  color: #e05555;
  font-size: 0.8rem;
  text-align: center;
  padding: 0 1rem 0.5rem;
  margin: 0;
}
</style>
