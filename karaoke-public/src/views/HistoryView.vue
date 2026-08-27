<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader
      :back="{ to: '/', label: '← Главная' }"
      :profile-link="{ to: '/account', label: 'Профиль →' }"
    />

    <LoginRequired v-if="!isLoggedIn" />

    <div v-else class="km-content">
      <h1 class="km-title">История прослушиваний</h1>

      <div v-if="loading" class="km-loading">Загрузка...</div>

      <div v-else-if="!items.length" class="km-empty">
        Вы пока ничего не слушали.
        <RouterLink to="/zakroma">Перейти к каталогу</RouterLink>
      </div>

      <div v-else class="km-list">
        <div v-for="item in items" :key="item.songId" class="km-hist-card" @click="open(item)">
          <div class="km-hist-main">
            <div class="km-hist-name">{{ item.songName }}</div>
            <div class="km-hist-author">{{ item.songAuthor }}</div>
          </div>
          <div class="km-hist-meta">
            <div class="km-hist-date">{{ formatDate(item.lastPlayed) }}</div>
            <div v-if="item.playCount > 1" class="km-hist-count">×{{ item.playCount }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import LoginRequired from '../components/LoginRequired.vue'
import { useAuth } from '../composables/useAuth'
import { fetchHistory } from '../services/historyApi'
import AppHeader from '../components/AppHeader.vue'

/**
 * View-страница «История прослушиваний» (QW-13) — список недавно прослушанных песен
 * зарегистрированного пользователя, по образцу `PlaylistsView.vue`.
 *
 * @see specs/009-listening-history/quickstart.md
 */
export default {
  name: 'HistoryView',
  components: { LoginRequired , AppHeader },
  setup() {
    const { isLoggedIn } = useAuth()
    return { isLoggedIn }
  },
  data() {
    return { items: [], loading: true }
  },
  async mounted() {
    if (this.isLoggedIn) await this.reload()
  },
  methods: {
    async reload() {
      this.loading = true
      const { status, body } = await fetchHistory()
      if (status === 200 && body && Array.isArray(body.items)) this.items = body.items
      this.loading = false
    },
    open(item) {
      this.$router.push({ path: '/song', query: { id: item.songId } })
    },
    formatDate(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short', year: 'numeric' })
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
.km-loading {
  padding: 2rem;
  text-align: center;
  color: var(--km-text2);
}
.km-empty {
  color: var(--km-text2);
  font-size: 0.9rem;
  padding: 1rem 0;
}

.km-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.km-hist-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.85rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 0.75rem 1rem;
  cursor: pointer;
}
.km-hist-card:hover {
  background: var(--km-hover);
  border-color: var(--km-accent);
}
.km-hist-main {
  min-width: 0;
}
.km-hist-name {
  font-size: 1rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-hist-author {
  font-size: 0.82rem;
  color: var(--km-text2);
}
.km-hist-meta {
  flex-shrink: 0;
  text-align: right;
}
.km-hist-date {
  font-size: 0.78rem;
  color: var(--km-text2);
}
.km-hist-count {
  font-size: 0.75rem;
  color: var(--km-accent);
  font-weight: 600;
}
</style>
