<template>
  <div v-if="visible" class="nwb-wrap">
    <transition name="nwb-toast-fade">
      <div v-if="toastItem" class="nwb-toast" @click="onToastClick">
        <button class="nwb-toast-close" title="Закрыть" @click.stop="dismissToast">×</button>
        <div class="nwb-toast-badge">
          {{ categoryIcon(toastItem.category) }} {{ categoryLabel(toastItem.category) }}
        </div>
        <div class="nwb-toast-title">{{ toastItem.title }}</div>
      </div>
    </transition>
    <button class="nwb-btn" title="Новости проекта — открыть" @click="goToNews">
      📰
      <span v-if="unread > 0" class="nwb-badge">{{ unread }}</span>
    </button>
  </div>
</template>

<script>
// Глобальный ненавязчивый индикатор новостей проекта — виден ЛЮБОМУ посетителю (не только
// залогиненным, в отличие от ChatUnreadBadge.vue, чей паттерн этот компонент повторяет: плавающая
// кнопка + опрос по интервалу + скрытие на /news и на плеере). «Прочитано» — не по серверу
// (новости публичны и для анонимов), а по last-seen id в localStorage: NewsView.vue поднимает эту
// отметку до максимального id всякий раз, когда пользователь реально открывает ленту.
import { fetchNewsSince } from '../services/newsApi'

const POLL_INTERVAL_MS = 45000
const STORAGE_KEY = 'km_news_last_seen_id'
const TOAST_AUTOHIDE_MS = 7000

const CATEGORY_META = {
  air: { icon: '📻', label: 'Эфир' },
  premium: { icon: '🪙', label: 'Премиум' },
  feature: { icon: '✨', label: 'Функционал' },
  general: { icon: '📰', label: 'Новость' },
}

function lastSeenId() {
  return Number(localStorage.getItem(STORAGE_KEY)) || 0
}

export default {
  name: 'NewsBell',
  data() {
    return { unread: 0, toastItem: null, lastToastId: 0, pollTimer: null, toastTimer: null }
  },
  computed: {
    // Скрыт на самой странице новостей (там и так всё видно, см. ChatUnreadBadge) и на
    // полноэкранном плеере — плавающая кнопка поверх видео была бы отвлекающей.
    isHiddenRoute() {
      return this.$route.name === 'news' || this.$route.name === 'player'
    },
    visible() {
      return (this.unread > 0 || !!this.toastItem) && !this.isHiddenRoute
    },
  },
  watch: {
    // При переходе на /news (в т.ч. кликом по самому колокольчику) счётчик неизбежно обнулится
    // на следующем опросе — NewsView.vue сама поднимает last-seen id при открытии ленты.
    '$route.name'() {
      this.poll()
    },
  },
  mounted() {
    this.poll()
    this.pollTimer = setInterval(this.poll, POLL_INTERVAL_MS)
  },
  beforeUnmount() {
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.toastTimer) clearTimeout(this.toastTimer)
  },
  methods: {
    categoryIcon(category) {
      return (CATEGORY_META[category] || CATEGORY_META.general).icon
    },
    categoryLabel(category) {
      return (CATEGORY_META[category] || CATEGORY_META.general).label
    },
    async poll() {
      // apiGet (services/api.js) резолвит уже распарсенным телом ответа, БЕЗ обёртки {status, body}
      // (в отличие от authGet/authPost, использующихся в chatApi.js для приватных эндпоинтов).
      let data
      try {
        data = await fetchNewsSince(lastSeenId())
      } catch (e) {
        return
      }
      if (!data) return
      this.unread = data.count || 0
      const items = data.items || []
      if (!items.length) return
      // items отсортированы по publish_at DESC — items[0] самая свежая. Тост показываем один раз
      // на новый максимальный id, не на каждый опрос, пока пользователь не открыл ленту.
      const maxId = Math.max(...items.map((i) => i.id))
      if (maxId !== this.lastToastId) {
        this.lastToastId = maxId
        this.showToast(items[0])
      }
    },
    showToast(item) {
      if (document.hidden) return
      this.toastItem = item
      if (this.toastTimer) clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(this.dismissToast, TOAST_AUTOHIDE_MS)
    },
    dismissToast() {
      this.toastItem = null
      if (this.toastTimer) {
        clearTimeout(this.toastTimer)
        this.toastTimer = null
      }
    },
    onToastClick() {
      const item = this.toastItem
      this.dismissToast()
      if (item && item.link) {
        if (/^https?:\/\//.test(item.link)) {
          window.location.href = item.link
          return
        }
        this.$router.push(item.link)
        return
      }
      this.goToNews()
    },
    goToNews() {
      this.dismissToast()
      this.$router.push('/news')
    },
  },
}
</script>

<style scoped>
.nwb-wrap {
  position: fixed;
  /* 64px, не 14px — с запасом ниже шапки/AuthStatusWidget (~45-50px), иначе тост на пару секунд
     наезжает на переключатель темы в правом углу шапки. */
  top: 64px;
  /* 74px, не 14px — правый верхний угол уже занят ChatUnreadBadge.vue (48px кнопка + отступ);
     оба индикатора могут быть видны одновременно залогиненному премиум-пользователю. */
  right: 74px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}
.nwb-btn {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: var(--km-accent, #0077ff);
  color: #fff;
  font-size: 1.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.3);
  position: relative;
}
.nwb-btn:hover {
  filter: brightness(1.1);
}
.nwb-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  background: #e05555;
  color: #fff;
  border-radius: 10px;
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  padding: 0 4px;
  border: 1px solid var(--km-bg, #0f0f1a);
}

/* Компактный авто-исчезающий тост — не перекрывает контент, не требует взаимодействия. */
.nwb-toast {
  max-width: 260px;
  background: var(--km-card, #1a1a2e);
  border: 1px solid var(--km-border, #333);
  color: var(--km-text, #eee);
  border-radius: 12px;
  padding: 0.6rem 0.8rem;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  position: relative;
}
.nwb-toast-close {
  position: absolute;
  top: 2px;
  right: 6px;
  background: transparent;
  border: none;
  color: var(--km-text2, #888);
  font-size: 1rem;
  cursor: pointer;
  line-height: 1;
}
.nwb-toast-badge {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--km-accent, #0077ff);
  margin-bottom: 0.2rem;
}
.nwb-toast-title {
  font-size: 0.85rem;
  padding-right: 0.8rem;
}
.nwb-toast-fade-enter-active,
.nwb-toast-fade-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}
.nwb-toast-fade-enter-from,
.nwb-toast-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
