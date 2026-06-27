<template>
  <div class="km-home">
    <!-- Хедер с переключателями -->
    <header class="km-header">
      <div class="km-header-inner">
        <span class="km-brand">🎵 Своё Место</span>
        <div class="km-controls">
          <div class="km-toggle-group" title="Дизайн">
            <button :class="['km-toggle-btn', design === 'classic' ? 'active' : '']" @click="setDesign('classic')">Классика</button>
            <button :class="['km-toggle-btn', design === 'modern' ? 'active' : '']" @click="setDesign('modern')">Новый</button>
          </div>
          <div class="km-toggle-group km-theme-toggle" title="Тема">
            <button :class="['km-toggle-btn', theme === 'light' ? 'active' : '']" @click="setTheme('light')" title="Светлая">☀</button>
            <button :class="['km-toggle-btn', theme === 'system' ? 'active' : '']" @click="setTheme('system')" title="Авто">⬡</button>
            <button :class="['km-toggle-btn', theme === 'dark' ? 'active' : '']" @click="setTheme('dark')" title="Тёмная">🌙</button>
          </div>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <main class="km-main">
      <div class="km-hero">
        <img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" />
        <p class="km-subtitle">Каraoke на «Своём Месте»</p>
      </div>

      <!-- Статистика -->
      <div v-if="!isLoading" class="km-stats">
        <div class="km-stat-card">
          <div class="km-stat-number">{{ formatNum(onSponsr) }}</div>
          <div class="km-stat-label">Песен в коллекции</div>
        </div>
        <div class="km-stat-card">
          <div class="km-stat-number">{{ formatNum(onAir) }}</div>
          <div class="km-stat-label">В открытом доступе</div>
        </div>
        <div class="km-stat-card">
          <div class="km-stat-number">{{ formatNum(exclusive) }}</div>
          <div class="km-stat-label">Эксклюзивно на Sponsr</div>
        </div>
      </div>
      <div v-else class="km-stats-loading">Загрузка статистики...</div>

      <!-- Описание -->
      <p class="km-desc">
        Каждый день в открытый доступ (в «эфир») выходит до 10 песен на 5 площадках:
        Sponsr, Dzen, VK, Max и Telegram. Вся коллекция доступна по подпискам на Sponsr.
      </p>

      <!-- Навигация -->
      <div class="km-nav-cards">
        <RouterLink to="/zakroma" class="km-nav-card">
          <div class="km-nav-icon">📚</div>
          <div class="km-nav-title">Закрома</div>
          <div class="km-nav-desc">Каталог по исполнителям</div>
        </RouterLink>
        <RouterLink to="/filter" class="km-nav-card">
          <div class="km-nav-icon">🔍</div>
          <div class="km-nav-title">Поиск песен</div>
          <div class="km-nav-desc">По исполнителю, названию и тексту</div>
        </RouterLink>
      </div>

      <!-- Соцсети -->
      <div class="km-social-section">
        <p class="km-social-label">Соцсети</p>
        <div class="km-social-row">
          <div v-for="link in socialLinks" :key="link.name" class="km-social-item" @click="openLink(link)">
            <SvgIcon :name="link.icon" :active="true" :size="48" />
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import SvgIcon from '../../components/SvgIcon.vue'
import { useDesign } from '../../composables/useDesign'
import { trackLinkToSocialNetwork } from '../../services/tracking'

const socialLinks = [
  { name: 'vkgroup',  icon: 'vkgroup', url: 'https://vk.com/svoemestokaraoke' },
  { name: 'sponsr',   icon: 'sponsr',  url: 'https://sponsr.ru/smkaraoke' },
  { name: 'dzen',     icon: 'dzen',    url: 'https://dzen.ru/svoemesto' },
  { name: 'vkvideo',  icon: 'vk',      url: 'https://vkvideo.ru/video/@nsasvoemesto' },
  { name: 'tg',       icon: 'tg',      url: 'https://t.me/svoemestokaraoke' },
  { name: 'max',      icon: 'max',     url: 'https://max.ru/join/hYGH-mbcExUtzP5o4zq38uwb0xL9iwL80uSeEBO7Bu0' },
]

export default {
  name: 'HomeModern',
  components: { SvgIcon },
  setup() {
    const { design, theme, applyTheme } = useDesign()
    function setDesign(val) { design.value = val }
    function setTheme(val)  { theme.value = val; applyTheme(val) }
    return { design, theme, setDesign, setTheme }
  },
  data() {
    return { socialLinks }
  },
  computed: {
    ...mapGetters('stats', ['onSponsr', 'onAir', 'exclusive', 'isLoading'])
  },
  mounted() {
    this.loadStats()
    document.title = 'Каraoke на «Своём Месте»'
  },
  methods: {
    ...mapActions('stats', ['loadStats']),
    openLink(link) {
      trackLinkToSocialNetwork(link.name)
      window.open(link.url, '_blank')
    },
    formatNum(n) {
      return n != null ? Number(n).toLocaleString('ru-RU') : ''
    }
  }
}
</script>

<style scoped>
.km-home {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
  display: flex;
  flex-direction: column;
}

/* Хедер */
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.6rem 1rem;
  position: sticky;
  top: 0;
  z-index: 100;
}
.km-header-inner {
  max-width: 700px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}
.km-brand {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--km-text);
  white-space: nowrap;
}
.km-controls {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* Pill-toggle */
.km-toggle-group {
  display: flex;
  border: 1px solid var(--km-border);
  border-radius: 20px;
  overflow: hidden;
}
.km-toggle-btn {
  background: transparent;
  color: var(--km-text2);
  border: none;
  padding: 0.25rem 0.75rem;
  font-size: 0.8rem;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.km-toggle-btn:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
.km-toggle-btn.active {
  background: var(--km-accent);
  color: #fff;
}
.km-theme-toggle .km-toggle-btn {
  padding: 0.25rem 0.6rem;
  font-size: 1rem;
}

/* Основной контент */
.km-main {
  flex: 1;
  max-width: 700px;
  margin: 0 auto;
  padding: 2rem 1rem 3rem;
  width: 100%;
}

/* Hero */
.km-hero {
  text-align: center;
  margin-bottom: 2rem;
}
.km-logo {
  width: min(320px, 80vw);
  display: block;
  margin: 0 auto 1rem;
}
.km-subtitle {
  color: var(--km-text2);
  font-size: 1rem;
  margin: 0;
}

/* Статистика */
.km-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.km-stat-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.2rem 0.8rem;
  text-align: center;
}
.km-stat-number {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--km-accent2);
  line-height: 1.1;
}
.km-stat-label {
  font-size: 0.75rem;
  color: var(--km-text2);
  margin-top: 0.3rem;
}
.km-stats-loading {
  text-align: center;
  color: var(--km-text2);
  padding: 1.5rem;
}

/* Описание */
.km-desc {
  color: var(--km-text2);
  font-size: 0.9rem;
  text-align: center;
  margin-bottom: 2rem;
  line-height: 1.6;
}

/* Навигационные карточки */
.km-nav-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin-bottom: 2.5rem;
}
.km-nav-card {
  display: block;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
  padding: 1.5rem 1rem;
  text-align: center;
  text-decoration: none;
  color: var(--km-text);
  transition: background 0.2s, border-color 0.2s, transform 0.15s;
}
.km-nav-card:hover {
  background: var(--km-hover);
  border-color: var(--km-accent);
  transform: translateY(-2px);
  color: var(--km-text);
  text-decoration: none;
}
.km-nav-icon {
  font-size: 2.5rem;
  margin-bottom: 0.5rem;
  line-height: 1;
}
.km-nav-title {
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: 0.3rem;
  color: var(--km-accent);
}
.km-nav-desc {
  font-size: 0.8rem;
  color: var(--km-text2);
}

/* Соцсети */
.km-social-section {
  text-align: center;
}
.km-social-label {
  color: var(--km-text2);
  font-size: 0.85rem;
  margin-bottom: 0.75rem;
}
.km-social-row {
  display: flex;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
}
.km-social-item {
  cursor: pointer;
  opacity: 0.85;
  transition: opacity 0.15s, transform 0.15s;
}
.km-social-item:hover {
  opacity: 1;
  transform: scale(1.12);
}

/* Мобильная адаптация */
@media (max-width: 500px) {
  .km-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .km-stat-exclusive {
    grid-column: 1 / -1;
  }
  .km-nav-cards {
    grid-template-columns: 1fr;
  }
  .km-stat-number {
    font-size: 1.5rem;
  }
  .km-controls {
    gap: 0.4rem;
  }
}
</style>
