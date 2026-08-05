<template>
  <section class="km-latest-news" :class="themeClass">
    <h2 class="km-latest-news-title">Последние новости</h2>
    <table v-if="visibleItems.length" class="km-latest-news-table">
      <thead>
        <tr>
          <th class="km-latest-news-col-date">Дата</th>
          <th class="km-latest-news-col-title">Заголовок</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="n in visibleItems" :key="n.id">
          <td class="km-latest-news-col-date">{{ formatDate(n.publishAt) }}</td>
          <td class="km-latest-news-col-title">
            <a :href="n.link" class="km-latest-news-link" @click.prevent="goTo(n)">
              {{ n.title }}
            </a>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script>
import { trackUi } from '../services/tracking'

const SIZE = 5

/**
 * Блок «Последние новости» на главной странице сайта.
 *
 * Делает fetch `GET /api/public/news?page=0&size=5` при монтировании, показывает
 * до 5 строк (дата/время + заголовок + ссылка). Строки с пустым `link` или
 * пустым/пробельным `title` отбрасываются на фронте. При любой ошибке запроса
 * (HTTP != 200, сетевая ошибка, таймаут, невалидный JSON) блок тихо не
 * рендерится — посетитель либо видит 5 строк данных, либо не видит блок вовсе.
 *
 * Контракт бэкенда см. {@link /specs/144-homepage-latest-news/contracts/public-news-api.md}.
 *
 * @see docs/features/homepage-latest-news.md
 */
export default {
  name: 'LatestNewsSection',
  data() {
    return {
      items: [],
      // ошибка намеренно НЕ хранится — тихая деградация (FR-013 спеки):
      // не показывать ни спиннера, ни сообщения об ошибке.
    }
  },
  computed: {
    themeClass() {
      const sys = window.matchMedia('(prefers-color-scheme: dark)').matches
      const dark = this.theme === 'dark' || (this.theme === 'system' && sys)
      return dark ? 'km-latest-news-dark' : 'km-latest-news-light'
    },
    visibleItems() {
      // FR-006: только строки с непустым link и непустым title.
      return this.items
        .filter((n) => {
          if (!n) return false
          if (!n.link || String(n.link).trim() === '') return false
          if (!n.title || String(n.title).trim() === '') return false
          return true
        })
        .slice(0, SIZE)
    },
  },
  created() {
    // Берём тему из того же источника, что и HomeView (useDesign),
    // чтобы блок перекрашивался вместе со всей страницей.
    try {
      this.theme = localStorage.getItem('karaoke-theme') || 'system'
    } catch (_) {
      this.theme = 'system'
    }
    this.fetchLatest()
  },
  methods: {
    fetchLatest() {
      fetch(`/api/public/news?page=0&size=${SIZE}`, { credentials: 'same-origin' })
        .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
        .then((data) => {
          // data.items может быть undefined на неожиданных форматах — защищаемся.
          if (data && Array.isArray(data.items)) {
            this.items = data.items
          }
        })
        .catch(() => {
          // FR-013 — тихая деградация: ничего не делаем, блок просто не покажется.
        })
    },
    formatDate(value) {
      if (!value) return ''
      // FR-007 — формат dd.MM.yyyy HH:mm, нативный Intl.DateTimeFormat без зависимостей.
      const d = new Date(value)
      if (isNaN(d.getTime())) return ''
      try {
        return new Intl.DateTimeFormat('ru-RU', {
          day: '2-digit',
          month: '2-digit',
          year: 'numeric',
          hour: '2-digit',
          minute: '2-digit',
        }).format(d)
      } catch (_) {
        return ''
      }
    },
    goTo(n) {
      // Трекинг: для авто-новостей (link вида /song?id={id}) используем существующий
      // trackLinkToSong; для ручных — trackUi('click', 'homeNews:{id}'). Если ни то,
      // ни другое не применимо (например, внешний URL) — trackUi с маркером.
      const songIdMatch = typeof n.link === 'string' && n.link.match(/^\/song\?id=(\d+)/)
      if (songIdMatch) {
        // Передаём через trackUi, чтобы не зависеть от наличия songId в DTO
        // (NewsDto сейчас не содержит songId, только News имеет его).
        trackUi('click', `homeNewsSong:${songIdMatch[1]}`)
      } else {
        trackUi('click', `homeNews:${n.id}`)
      }
      window.open(n.link, '_blank', 'noopener,noreferrer')
    },
  },
}
</script>

<style scoped>
.km-latest-news {
  margin: 1.5rem 0 0.5rem;
  padding: 0.75rem 0.5rem;
  border-radius: 14px;
  background: var(--km-card, rgba(255, 255, 255, 0.04));
  border: 1px solid var(--km-border, rgba(127, 127, 127, 0.2));
}
.km-latest-news-title {
  font-size: 1rem;
  font-weight: 700;
  margin: 0 0 0.6rem;
  color: var(--km-text, inherit);
  text-align: left;
}
.km-latest-news-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 0.85rem;
  color: var(--km-text, inherit);
}
.km-latest-news-table thead th {
  text-align: left;
  font-weight: 600;
  font-size: 0.75rem;
  color: var(--km-text2, rgba(127, 127, 127, 0.8));
  padding: 0.25rem 0.4rem;
  border-bottom: 1px solid var(--km-border, rgba(127, 127, 127, 0.2));
}
.km-latest-news-table tbody td {
  padding: 0.35rem 0.4rem;
  border-bottom: 1px solid var(--km-border, rgba(127, 127, 127, 0.1));
  vertical-align: middle;
}
.km-latest-news-col-date {
  width: 110px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: var(--km-text2, rgba(127, 127, 127, 0.85));
}
.km-latest-news-col-title {
  word-break: break-word;
}
.km-latest-news-link {
  color: var(--km-accent, #4a90e2);
  text-decoration: none;
}
.km-latest-news-link:hover {
  text-decoration: underline;
}
@media (max-width: 500px) {
  .km-latest-news-col-date {
    width: 86px;
    font-size: 0.75rem;
  }
  .km-latest-news-table {
    font-size: 0.8rem;
  }
}
</style>
