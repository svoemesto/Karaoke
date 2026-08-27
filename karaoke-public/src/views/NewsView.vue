<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader :back="{ to: '/', label: '← Главная' }" />

    <div class="km-content">
      <h1 class="km-title">📰 Новости проекта</h1>

      <div v-if="loading" class="km-loading">Загрузка...</div>
      <p v-else-if="!news.length" class="km-empty">Пока новостей нет — загляните позже.</p>

      <div v-else class="km-news-list">
        <div
          v-for="item in news"
          :key="item.id"
          class="km-news-card"
          :class="{ 'km-news-card-clickable': !!item.link }"
          @click="onCardClick(item)"
        >
          <div class="km-news-card-header">
            <span class="km-news-badge" :class="`km-news-badge-${item.category}`"
              >{{ categoryIcon(item.category) }} {{ categoryLabel(item.category) }}</span
            >
            <span class="km-news-date">{{ formatDate(item.publishAt) }}</span>
          </div>
          <h2 class="km-news-title">{{ item.title }}</h2>
          <p class="km-news-body">{{ item.body }}</p>
          <span v-if="item.link" class="km-news-link-hint">Подробнее →</span>
        </div>
      </div>

      <div v-if="hasMore" class="km-news-more">
        <button class="km-news-more-btn" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? 'Загрузка...' : 'Показать ещё' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { fetchNews } from '../services/newsApi'
import AppHeader from '../components/AppHeader.vue'

const CATEGORY_META = {
  air: { icon: '📻', label: 'Эфир' },
  premium: { icon: '🪙', label: 'Премиум' },
  feature: { icon: '✨', label: 'Функционал' },
  general: { icon: '📰', label: 'Новость' },
}

const PAGE_SIZE = 20

/**
 * View-страница «News» — основной layout и постраничный data-fetching (specs/090-news-pagination).
 *
 * @see archive/docs/features/dual-db-sync.md
 */

export default {
  name: 'NewsView',
  components: { AppHeader },
  data() {
    return { news: [], loading: true, page: 0, total: 0, hasMore: false, loadingMore: false }
  },
  async mounted() {
    // apiGet (services/api.js) резолвит уже распарсенным телом ответа, БЕЗ обёртки {status, body}
    // (в отличие от authGet/authPost из authApi.js, который использует chatApi.js) — новости
    // публичны и не требуют авторизованного клиента.
    try {
      const data = await fetchNews(0, PAGE_SIZE)
      this.applyPage(data, true)
      this.markAllSeen()
    } catch (e) {
      /* пустая лента при сетевой ошибке */
    }
    this.loading = false
  },
  methods: {
    // Ответ {items, total, hasMore} (specs/090-news-pagination) — append=true при первой загрузке
    // (замена пустого списка), append=false невозможен здесь, "Показать ещё" всегда дозагружает.
    applyPage(data, append) {
      const items = Array.isArray(data && data.items) ? data.items : []
      this.news = append ? [...this.news, ...items] : items
      this.total = Number(data && data.total) || 0
      this.hasMore = Boolean(data && data.hasMore)
    },
    async loadMore() {
      if (this.loadingMore || !this.hasMore) return
      this.loadingMore = true
      try {
        const nextPage = this.page + 1
        const data = await fetchNews(nextPage, PAGE_SIZE)
        this.applyPage(data, true)
        this.page = nextPage
      } catch (e) {
        /* оставляем уже показанные карточки при сетевой ошибке */
      }
      this.loadingMore = false
    },
    // Открытие ленты = все текущие опубликованные новости увидены — поднимаем last-seen id, по
    // которому NewsBell.vue (глобальный колокольчик) решает, есть ли что-то непрочитанное.
    markAllSeen() {
      if (!this.news.length) return
      const maxId = Math.max(...this.news.map((n) => n.id))
      const stored = Number(localStorage.getItem('km_news_last_seen_id')) || 0
      if (maxId > stored) localStorage.setItem('km_news_last_seen_id', String(maxId))
    },
    categoryIcon(category) {
      return (CATEGORY_META[category] || CATEGORY_META.general).icon
    },
    categoryLabel(category) {
      return (CATEGORY_META[category] || CATEGORY_META.general).label
    },
    formatDate(tsString) {
      if (!tsString) return ''
      try {
        const d = new Date(tsString.replace(' ', 'T'))
        return d.toLocaleString('ru-RU', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        })
      } catch (e) {
        return tsString
      }
    },
    onCardClick(item) {
      if (!item.link) return
      if (/^https?:\/\//.test(item.link)) {
        window.location.href = item.link
      } else {
        this.$router.push(item.link)
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
.km-loading,
.km-empty {
  color: var(--km-text2);
  text-align: center;
  padding: 2rem 0;
}

.km-news-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.km-news-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.1rem 1.4rem;
}
.km-news-card-clickable {
  cursor: pointer;
}
.km-news-card-clickable:hover {
  background: var(--km-hover);
}
.km-news-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}
.km-news-badge {
  font-size: 0.75rem;
  font-weight: 700;
  border-radius: 20px;
  padding: 0.2rem 0.7rem;
  white-space: nowrap;
}
.km-news-badge-air {
  color: #0a5cbf;
  background: linear-gradient(135deg, #cfe6ff, #9cc9ff);
}
.km-news-badge-premium {
  color: #7a5c00;
  background: linear-gradient(135deg, #ffe27a, #d4af37);
}
.km-news-badge-feature {
  color: #5a2d8f;
  background: linear-gradient(135deg, #e6d4ff, #c299f2);
}
.km-news-badge-general {
  color: var(--km-text2);
  background: var(--km-hover);
}
.km-news-date {
  font-size: 0.75rem;
  color: var(--km-text2);
  white-space: nowrap;
}
.km-news-title {
  font-size: 1.05rem;
  margin: 0 0 0.4rem;
}
.km-news-body {
  font-size: 0.9rem;
  color: var(--km-text2);
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}
.km-news-link-hint {
  display: inline-block;
  margin-top: 0.5rem;
  font-size: 0.85rem;
  color: var(--km-accent);
  font-weight: 600;
}

.km-news-more {
  display: flex;
  justify-content: center;
  margin-top: 0.5rem;
}
.km-news-more-btn {
  border: 1px solid var(--km-border);
  border-radius: 20px;
  padding: 0.5rem 1.4rem;
  background: var(--km-card);
  color: var(--km-text);
  cursor: pointer;
  font-size: 0.9rem;
}
.km-news-more-btn:hover:not(:disabled) {
  background: var(--km-hover);
}
.km-news-more-btn:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
