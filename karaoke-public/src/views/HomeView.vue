<template>
  <div class="km-home">
    <!-- Хедер с переключателями -->
    <header class="km-header">
      <div class="km-header-inner">
        <img src="/KARAOKE_LOGO.png" class="km-brand-logo" alt="Своё Место" />
        <div class="km-controls">
          <AuthStatusWidget />
          <div class="km-toggle-group km-theme-toggle" title="Тема">
            <button
              :class="['km-toggle-btn', theme === 'light' ? 'active' : '']"
              title="Светлая"
              @click="setTheme('light')"
            >
              ☀
            </button>
            <button
              :class="['km-toggle-btn', theme === 'system' ? 'active' : '']"
              title="Авто"
              @click="setTheme('system')"
            >
              ⬡
            </button>
            <button
              :class="['km-toggle-btn', theme === 'dark' ? 'active' : '']"
              title="Тёмная"
              @click="setTheme('dark')"
            >
              🌙
            </button>
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
      <div v-if="!isLoading" class="km-stats km-stats-4">
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
          <div class="km-stat-label">По подписке</div>
        </div>
        <div class="km-stat-card">
          <div class="km-stat-number">{{ formatNum(inWork) }}</div>
          <div class="km-stat-label">В работе</div>
        </div>
      </div>
      <div v-else class="km-stats-loading">Загрузка статистики...</div>

      <!-- Описание -->
      <p class="km-desc">
        Каждый день в открытый доступ (в «эфир») выходит до 10 песен на 5 площадках: Sponsr, Dzen,
        VK, Max и Telegram. Вся коллекция доступна по подписке на сайте.
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
        <RouterLink to="/account/playlists" class="km-nav-card">
          <div class="km-nav-icon">🔖</div>
          <div class="km-nav-title">Избранное и плейлисты</div>
          <div class="km-nav-desc">Ваши сохранённые песни и плейлисты</div>
        </RouterLink>
        <RouterLink to="/premium" class="km-nav-card">
          <div class="km-nav-icon">🪙</div>
          <div class="km-nav-title">Премиум-подписка</div>
          <div class="km-nav-desc">Подписка на всю коллекцию или на одну песню</div>
        </RouterLink>
      </div>

      <!-- Соцсети -->
      <div class="km-social-section">
        <p class="km-social-label">Соцсети</p>
        <div class="km-social-row">
          <div
            v-for="link in socialLinks"
            :key="link.name"
            class="km-social-item"
            @click="openLink(link)"
          >
            <SvgIcon :name="link.icon" :active="true" :size="48" />
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script>
/**
 * Главная страница karaoke-public.
 *
 * Отображает:
 * - Хедер с логотипом, навигацией, `AuthStatusWidget` (кнопка входа/профиля).
 * - Каталог песен (лента с пагинацией) — через `ClassicHomeView` или `ModernHomeView`
 *   в зависимости от выбранного дизайна (`localStorage.km-design = 'classic' | 'modern'`).
 * - Футер с контактами.
 *
 * **Dual design** (см. AGENTS.md#karaoke-public-dual-design):
 * - `classic/` — традиционный дизайн, похожий на legacy Thymeleaf.
 * - `modern/` — современный, с градиентами и анимациями.
 * - CSS-переменные `--km-*` для настройки обоих дизайнов.
 *
 * @route /
 * @see ClassicHomeView
 * @see ModernHomeView
 */
import { mapGetters, mapActions } from 'vuex'
import SvgIcon from '../components/SvgIcon.vue'
import AuthStatusWidget from '../components/AuthStatusWidget.vue'
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { trackLinkToSocialNetwork } from '../services/tracking'

const socialLinks = [
  { name: 'vkgroup', icon: 'vkgroup', url: 'https://vk.com/svoemestokaraoke' },
  { name: 'sponsr', icon: 'sponsr', url: 'https://sponsr.ru/smkaraoke' },
  { name: 'dzen', icon: 'dzen', url: 'https://dzen.ru/svoemesto' },
  { name: 'vkvideo', icon: 'vk', url: 'https://vkvideo.ru/video/@nsasvoemesto' },
  { name: 'tg', icon: 'tg', url: 'https://t.me/svoemestokaraoke' },
  {
    name: 'max',
    icon: 'max',
    url: 'https://max.ru/join/hYGH-mbcExUtzP5o4zq38uwb0xL9iwL80uSeEBO7Bu0',
  },
]

/**
 * Компонент «vkgroup».
 *
 * @see AGENTS.md
 */

export default {
  name: 'HomeView',
  components: { SvgIcon, AuthStatusWidget },
  setup() {
    useEngagementTracking('home')
    const { theme, applyTheme } = useDesign()
    function setTheme(val) {
      theme.value = val
      applyTheme(val)
    }
    return { theme, setTheme }
  },
  data() {
    return { socialLinks }
  },
  computed: {
    ...mapGetters('stats', ['onSponsr', 'onAir', 'exclusive', 'inWork', 'total', 'isLoading']),
  },
  mounted() {
    this.loadStats()
    document.title = 'Караоке на «Своём Месте»'
  },
  methods: {
    ...mapActions('stats', ['loadStats']),
    openLink(link) {
      trackLinkToSocialNetwork(link.name)
      window.open(link.url, '_blank')
    },
    formatNum(n) {
      return n != null ? Number(n).toLocaleString('ru-RU') : ''
    },
  },
}
</script>

<style scoped>
.km-home {
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
.km-brand-logo {
  height: 32px;
  width: auto;
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
  transition:
    background 0.15s,
    color 0.15s;
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
.km-stats-4 {
  grid-template-columns: repeat(4, 1fr);
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
/* 4 карточки — ровная сетка 2×2, особый full-width случай (был для 3-й карточки) больше не нужен. */
.km-nav-card {
  display: block;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
  padding: 1.5rem 1rem;
  text-align: center;
  text-decoration: none;
  color: var(--km-text);
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.15s;
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
  transition:
    opacity 0.15s,
    transform 0.15s;
}
.km-social-item:hover {
  opacity: 1;
  transform: scale(1.12);
}

/* Мобильная адаптация */
@media (max-width: 700px) {
  .km-stats-4 {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 500px) {
  .km-stats {
    grid-template-columns: repeat(2, 1fr);
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
