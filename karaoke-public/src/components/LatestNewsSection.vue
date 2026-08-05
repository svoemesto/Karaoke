<template>
  <section class="km-latest-news" :class="themeClass">
    <div class="km-latest-news-header">
      <h2 class="km-latest-news-title">Последние новости</h2>
      <router-link to="/news" class="km-latest-news-all">Все новости</router-link>
    </div>
    <table v-if="visibleItems.length" class="km-latest-news-table">
      <tbody>
        <tr
          v-for="n in visibleItems"
          :key="n.id"
          class="km-latest-news-row"
          :class="rowHoverClass(n)"
          @click="goTo(n)"
        >
          <td class="km-latest-news-col-date">{{ formatDate(n.publishAt) }}</td>
          <td class="km-latest-news-col-icon">
            <SvgIcon :name="coinIconName(n)" :active="true" :size="20" />
          </td>
          <td class="km-latest-news-col-title">{{ n.title }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script>
import SvgIcon from './SvgIcon.vue'
import { trackUi } from '../services/tracking'

const SIZE = 5

/**
 * Блок «Последние новости» на главной странице сайта.
 *
 * Делает fetch `GET /api/public/news?page=0&size=5` при монтировании, показывает
 * до 5 строк (иконка категории + дата/время + заголовок). Вся строка кликабельна
 * (а не только текст заголовка), на ховере показывается `cursor: pointer`. Строки
 * с пустым `link` или пустым/пробельным `title` отбрасываются на фронте. При
 * любой ошибке запроса (HTTP != 200, сетевая ошибка, таймаут, невалидный JSON)
 * блок тихо не рендерится — посетитель либо видит 5 строк данных, либо не
 * видит блок вовсе.
 *
 * Иконка-монетка отображает тип новости по полю `News.category`:
 * - `premium` → золотая монетка (новость «В коллекции»)
 * - `air` → серебряная (новость «В эфире»)
 * - `feature`, `general` и прочие → зелёная.
 *
 * Контракт бэкенда см. {@link /specs/144-homepage-latest-news/contracts/public-news-api.md}.
 *
 * @see docs/features/homepage-latest-news.md
 */
export default {
  name: 'LatestNewsSection',
  components: { SvgIcon },
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
    coinIconName(n) {
      // News.category: "air" (эфир) | "premium" (коллекция) | "feature" (функционал) | "general".
      // Согласно UX-запросу: premium → золотая, air → серебряная, остальные → зелёная.
      const c = (n && n.category) || 'general'
      if (c === 'premium') return 'coin-gold'
      if (c === 'air') return 'coin-silver'
      return 'coin-green'
    },
    rowHoverClass(n) {
      return `km-latest-news-row-${this.coinIconName(n).replace('coin-', '')}`
    },
    goTo(n) {
      // Трекинг клика: для авто-новостей (link вида /song?id={id}) и ручных — единый
      // trackUi('click', 'homeNewsSong:{id}|homeNews:{id}'). NewsDto сейчас не содержит
      // songId, поэтому используем id новости как маркер.
      const songIdMatch = typeof n.link === 'string' && n.link.match(/^\/song\?id=(\d+)/)
      if (songIdMatch) {
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
  padding: 0.75rem 0.75rem 0.4rem;
  border-radius: 14px;
  background: var(--km-card, rgba(255, 255, 255, 0.04));
  border: 1px solid var(--km-border, rgba(127, 127, 127, 0.2));
}
.km-latest-news-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 0.5rem;
  gap: 1rem;
}
.km-latest-news-title {
  font-size: 1rem;
  font-weight: 700;
  margin: 0;
  color: var(--km-text, inherit);
}
.km-latest-news-all {
  font-size: 0.8rem;
  color: var(--km-accent, #4a90e2);
  text-decoration: none;
  white-space: nowrap;
}
.km-latest-news-all:hover {
  text-decoration: underline;
}
.km-latest-news-table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 0.85rem;
  color: var(--km-text, inherit);
}
.km-latest-news-table tbody td {
  padding: 0.5rem 0.4rem;
  border-bottom: 1px solid var(--km-border, rgba(127, 127, 127, 0.1));
  vertical-align: middle;
}
.km-latest-news-table tbody tr:last-child td {
  border-bottom: none;
}
.km-latest-news-col-icon {
  width: 28px;
  padding-left: 0;
  padding-right: 0.6rem;
  text-align: center;
}
.km-latest-news-col-date {
  width: 110px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
  color: var(--km-text2, rgba(127, 127, 127, 0.85));
  padding-right: 0.8rem;
}
.km-latest-news-col-title {
  word-break: break-word;
}
/* Вся строка кликабельна. На ховере показываем pointer + лёгкий фон */
.km-latest-news-row {
  cursor: pointer;
  transition: background-color 0.15s;
}
.km-latest-news-row:hover {
  background-color: var(--km-hover, rgba(127, 127, 127, 0.08));
}
@media (max-width: 500px) {
  .km-latest-news-col-date {
    width: 86px;
    font-size: 0.75rem;
  }
  .km-latest-news-table {
    font-size: 0.8rem;
  }
  .km-latest-news-col-icon {
    width: 22px;
  }
}
</style>
