<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader :back="{ to: '/account', label: '← Личный кабинет' }" />

    <div class="km-content">
      <h1 class="km-title">Мои подписки</h1>

      <div v-if="loading" class="km-hint">Загрузка...</div>
      <div v-else-if="subscriptions.length === 0" class="km-empty">
        Подписок пока нет. <RouterLink to="/premium">Оформить подписку →</RouterLink>
      </div>
      <div v-else class="km-sub-list">
        <!--
          BR-352 (аудит #336, PKG-S3-28): отмена автопродления проверяет ответ сервера.
          Раньше статус ответа игнорировался — при 401/404/500 интерфейс молча
          перезагружал список, и пользователь не узнавал, что карта не отвязана.
        -->
        <p v-if="cancelError" class="km-error" role="alert">{{ cancelError }}</p>
        <p v-if="cancelMessage" class="km-ok">{{ cancelMessage }}</p>
        <div v-for="s in subscriptions" :key="s.id" class="km-sub-card">
          <div class="km-sub-main">
            <div class="km-sub-title">
              {{ s.scope === 'SONG' ? s.songName || `Песня #${s.idSong}` : 'Подписка на сайт' }}
            </div>
            <div class="km-sub-meta">
              {{ s.finalPrice }} ₽ · {{ statusText(s.status) }}
              <span v-if="s.paidAt"> · оплачено {{ formatDate(s.paidAt) }}</span>
            </div>
          </div>
          <div class="km-sub-actions">
            <RouterLink
              v-if="s.scope === 'SONG' && s.status === 'PAID'"
              :to="`/song?id=${s.idSong}`"
              class="km-btn km-btn-secondary"
              >Играть</RouterLink
            >
            <template v-if="s.scope === 'SITE' && s.status === 'PAID'">
              <span v-if="!s.autoRenew" class="km-sub-canceled">Автопродление отключено</span>
              <button v-else class="km-btn km-btn-danger" @click="onCancel(s.id)">
                Отвязать карту и отключить автопродление
              </button>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { authGet, authPost } from '../services/authApi'
import { useAuth } from '../composables/useAuth'
import AppHeader from '../components/AppHeader.vue'

/**
 * View-страница «Subscriptions» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'SubscriptionsView',
  components: { AppHeader },
  setup() {
    const { token } = useAuth()
    return { token }
  },
  data() {
    return {
      loading: true,
      subscriptions: [],
      // BR-352 (аудит #336, PKG-S3-28): результат отмены автопродления — видимый.
      cancelError: '',
      cancelMessage: '',
    }
  },
  async mounted() {
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account/subscriptions' } })
      return
    }
    await this.load()
  },
  methods: {
    formatDate(tsString) {
      try {
        return new Date(tsString.replace(' ', 'T')).toLocaleDateString('ru-RU')
      } catch (e) {
        return tsString
      }
    },
    statusText(status) {
      return (
        {
          CREATED: 'создана',
          PENDING: 'ожидает оплаты',
          PAID: 'активна',
          FAILED: 'не оплачена',
          REFUNDED: 'возврат',
          CANCELED: 'отменена',
        }[status] || status
      )
    },
    async load() {
      this.loading = true
      try {
        const { status, body } = await authGet('/api/public/account/subscription/list', this.token)
        if (status === 200 && Array.isArray(body)) this.subscriptions = body
      } catch (e) {
        /* оставляем пустой список */
      }
      this.loading = false
    },
    async onCancel(id) {
      if (
        !confirm(
          'Отвязать сохранённую карту и отключить автопродление? Доступ сохранится до конца оплаченного периода, повторных списаний не будет.',
        )
      )
        return
      // BR-352 (аудит #336, PKG-S3-28): результат запроса разбирается явно.
      // Раньше `await authPost(...)` — без проверки статуса и без catch: при 401/404/500
      // (или обрыве сети) список молча перезагружался, и пользователь видел тот же экран
      // без единого слова — отвязка карты не выполнена, а выглядело как «всё ок».
      this.cancelError = ''
      this.cancelMessage = ''
      try {
        const { status } = await authPost(
          '/api/public/account/subscription/cancel',
          { id },
          this.token,
        )
        if (status !== 200) {
          this.cancelError =
            status === 401 || status === 403
              ? 'Сессия истекла — войдите заново, чтобы отключить автопродление.'
              : 'Не удалось отключить автопродление. Попробуйте ещё раз.'
          return
        }
        this.cancelMessage = 'Автопродление отключено.'
        await this.load()
      } catch (e) {
        this.cancelError = 'Не удалось связаться с сервером — автопродление не отключено.'
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
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.km-title {
  font-size: 1.4rem;
  margin: 0 0 1.25rem;
}
.km-hint,
.km-empty {
  font-size: 0.9rem;
  color: var(--km-text2);
}
/* BR-352: видимый результат отмены автопродления (ошибка / успех). */
.km-error {
  color: #c0392b;
  font-size: 0.85rem;
  margin: 0 0 0.75rem;
}
.km-ok {
  color: #1e7e34;
  font-size: 0.85rem;
  margin: 0 0 0.75rem;
}
.km-sub-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.km-sub-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
}
.km-sub-title {
  font-weight: 600;
  font-size: 0.95rem;
}
.km-sub-meta {
  font-size: 0.8rem;
  color: var(--km-text2);
  margin-top: 0.2rem;
}
.km-sub-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}
.km-sub-canceled {
  font-size: 0.78rem;
  color: var(--km-text2);
}
.km-btn {
  display: inline-block;
  border-radius: 8px;
  padding: 0.4rem 0.9rem;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  border: none;
  font-size: 0.82rem;
  white-space: nowrap;
}
.km-btn-secondary {
  background: transparent;
  color: var(--km-accent);
  border: 1px solid var(--km-accent);
}
.km-btn-secondary:hover {
  background: var(--km-hover);
}
.km-btn-danger {
  background: transparent;
  color: #e05555;
  border: 1px solid #e05555;
}
.km-btn-danger:hover {
  background: rgba(224, 85, 85, 0.1);
}
</style>
