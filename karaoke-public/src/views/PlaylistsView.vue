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

    <LoginRequired v-if="!isLoggedIn" />

    <div v-else class="km-content">
      <div class="km-head-row">
        <h1 class="km-title">Мои плейлисты</h1>
        <button class="km-new-btn" :disabled="creating" @click="onCreate">＋ Новый плейлист</button>
      </div>

      <div v-if="loading" class="km-loading">Загрузка...</div>

      <div v-else class="km-list">
        <div
          v-for="pl in playlists"
          :key="pl.id"
          class="km-pl-card"
          :class="{ 'km-pl-fav': pl.favorites }"
        >
          <div class="km-pl-icon">
            <SvgIcon :name="pl.favorites ? 'favorite' : 'playlist'" :active="true" :size="22" />
          </div>
          <div class="km-pl-main" @click="open(pl.id)">
            <div class="km-pl-name">{{ pl.name }}</div>
            <div class="km-pl-count">{{ pl.itemsCount }} {{ pluralSongs(pl.itemsCount) }}</div>
          </div>
          <div class="km-pl-actions">
            <button class="km-pl-btn" title="Открыть" @click="open(pl.id)">Открыть</button>
            <button
              v-if="!pl.favorites"
              class="km-pl-btn km-pl-btn-danger"
              title="Удалить плейлист"
              :disabled="busyId === pl.id"
              @click="onDelete(pl)"
            >
              Удалить
            </button>
          </div>
        </div>

        <p v-if="!playlists.length" class="km-empty">
          Пока нет плейлистов. Добавьте песни в «Избранное» кнопкой-закладкой в таблицах.
        </p>
      </div>
    </div>
  </div>
</template>

<script>
import SvgIcon from '../components/SvgIcon.vue'
import LoginRequired from '../components/LoginRequired.vue'
import { useAuth } from '../composables/useAuth'
import { usePremiumModal } from '../composables/usePremiumModal'
import { fetchPlaylists, createPlaylist, deletePlaylist } from '../services/playlistApi'

/**
 * View-страница «Playlists» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'PlaylistsView',
  components: { SvgIcon, LoginRequired },
  setup() {
    const { user, isLoggedIn } = useAuth()
    const { openPremiumRequired, openLimit } = usePremiumModal()
    return { user, isLoggedIn, openPremiumRequired, openLimit }
  },
  data() {
    return { playlists: [], loading: true, creating: false, busyId: null }
  },
  computed: {
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  async mounted() {
    if (this.isLoggedIn) await this.reload()
  },
  methods: {
    pluralSongs(n) {
      const a = Math.abs(n) % 100,
        b = a % 10
      if (a > 10 && a < 20) return 'песен'
      if (b > 1 && b < 5) return 'песни'
      if (b === 1) return 'песня'
      return 'песен'
    },
    async reload() {
      this.loading = true
      const { status, body } = await fetchPlaylists()
      if (status === 200 && Array.isArray(body)) this.playlists = body
      this.loading = false
    },
    open(id) {
      this.$router.push({ path: `/account/playlists/${id}` })
    },
    async onCreate() {
      if (!this.isPremium) {
        this.openPremiumRequired({ benefits: PREMIUM_BENEFITS })
        return
      }
      this.creating = true
      try {
        const { status, body } = await createPlaylist({})
        if (status === 200 && body && body.id) {
          this.$router.push({ path: `/account/playlists/${body.id}` })
        } else if (body && body.error === 'limit_reached') {
          this.openLimit({ limit: body.limit, benefits: body.benefits })
        } else if (body && body.error === 'premium_required') {
          this.openPremiumRequired({ benefits: body.benefits })
        }
      } finally {
        this.creating = false
      }
    },
    async onDelete(pl) {
      if (!confirm(`Удалить плейлист «${pl.name}»?`)) return
      this.busyId = pl.id
      try {
        const { status } = await deletePlaylist(pl.id)
        if (status === 200) this.playlists = this.playlists.filter((p) => p.id !== pl.id)
      } finally {
        this.busyId = null
      }
    },
  },
}

const PREMIUM_BENEFITS = [
  'Свои плейлисты: создавайте, переименовывайте, меняйте порядок песен',
  'Онлайн-плеер для всех песен, а не только «в эфире»',
  'Непрерывное воспроизведение, повтор и случайный порядок',
  'Экспорт аудио-дорожек песни',
]
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

.km-content {
  max-width: 600px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.km-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1.25rem;
  gap: 1rem;
}
.km-title {
  font-size: 1.4rem;
  margin: 0;
}
.km-new-btn {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.5rem 1rem;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}
.km-new-btn:hover {
  opacity: 0.9;
}
.km-new-btn:disabled {
  opacity: 0.6;
  cursor: default;
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
.km-pl-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 0.75rem 1rem;
}
.km-pl-fav {
  border-color: #e6a3ab;
}
.km-pl-icon {
  flex-shrink: 0;
  line-height: 0;
}
.km-pl-main {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}
.km-pl-name {
  font-size: 1rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-pl-count {
  font-size: 0.78rem;
  color: var(--km-text2);
}
.km-pl-actions {
  display: flex;
  gap: 0.4rem;
  flex-shrink: 0;
}
.km-pl-btn {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text2);
  border-radius: 8px;
  padding: 0.3rem 0.7rem;
  font-size: 0.8rem;
  cursor: pointer;
}
.km-pl-btn:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
.km-pl-btn-danger:hover {
  border-color: #d02c3a;
  color: #d02c3a;
}
.km-pl-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
