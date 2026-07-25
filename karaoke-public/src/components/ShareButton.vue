<template>
  <div class="share-button">
    <button class="share-trigger" @click="menuOpen = !menuOpen">📤 Поделиться</button>

    <div v-if="menuOpen" class="share-backdrop" @click="menuOpen = false" />
    <div v-if="menuOpen" class="share-menu">
      <button
        v-for="target in shareTargets"
        :key="target.name"
        class="share-item"
        @click="openTarget(target)"
      >
        <span class="share-icon">{{ target.emoji }}</span>
        {{ target.label }}
      </button>
      <button class="share-item" @click="copyLink">
        <span class="share-icon">{{ copied ? '✅' : '🔗' }}</span>
        {{ copied ? 'Ссылка скопирована' : 'Скопировать ссылку' }}
      </button>
    </div>
  </div>
</template>

<script>
import { trackUi } from '../services/tracking'

// Стандартные share-URL соцсетей (без SDK/API-ключей, без новых npm-зависимостей).
// Иконки — emoji, а не SvgIcon: WhatsApp/Odnoklassniki нет в SvgIcon.viewBoxMap, а emoji уже
// используются в CTA-кнопках этой же страницы (см. AboutView.vue) — стилистически консистентно.
const SHARE_TARGETS = [
  {
    name: 'vk',
    emoji: '🔵',
    label: 'ВКонтакте',
    urlTemplate: 'https://vk.com/share.php?url={url}',
  },
  {
    name: 'telegram',
    emoji: '✈️',
    label: 'Telegram',
    urlTemplate: 'https://t.me/share/url?url={url}&text={title}',
  },
  {
    name: 'whatsapp',
    emoji: '💬',
    label: 'WhatsApp',
    urlTemplate: 'https://wa.me/?text={title}%20{url}',
  },
  {
    name: 'ok',
    emoji: '🟠',
    label: 'Одноклассники',
    urlTemplate: 'https://connect.ok.ru/offer?url={url}&title={title}',
  },
]

/**
 * Кнопка «Поделиться» с меню соцсетей (VK/Telegram/WhatsApp/Odnoklassniki) и копированием
 * ссылки. Переиспользует существующий трекинг (`trackUi`), без новых event-типов.
 *
 * @see specs/003-about-page/research.md — Decision 2 (почему emoji, не SVG; почему без SDK)
 */
export default {
  name: 'ShareButton',
  data() {
    return { menuOpen: false, copied: false, shareTargets: SHARE_TARGETS }
  },
  methods: {
    openTarget(target) {
      const url = encodeURIComponent(window.location.href)
      const title = encodeURIComponent(document.title)
      const shareUrl = target.urlTemplate.replace('{url}', url).replace('{title}', title)
      trackUi('share', target.name)
      window.open(shareUrl, '_blank', 'noopener,noreferrer')
      this.menuOpen = false
    },
    async copyLink() {
      trackUi('share', 'copy-link')
      try {
        await navigator.clipboard.writeText(window.location.href)
      } catch (e) {
        // Старые браузеры / отсутствие clipboard API — fallback через скрытый textarea.
        const textarea = document.createElement('textarea')
        textarea.value = window.location.href
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        try {
          document.execCommand('copy')
        } catch (e2) {
          /* ignore — копирование недоступно, пользователь скопирует ссылку из адресной строки */
        }
        document.body.removeChild(textarea)
      }
      this.copied = true
      setTimeout(() => {
        this.copied = false
        this.menuOpen = false
      }, 1200)
    },
  },
}
</script>

<style scoped>
.share-button {
  position: relative;
  display: inline-block;
}
.share-trigger {
  display: block;
  text-align: center;
  padding: 0.9rem 1rem;
  border-radius: 10px;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--km-accent);
  background: transparent;
  color: var(--km-accent);
}
.share-trigger:hover {
  background: var(--km-hover);
}
.share-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10;
}
.share-menu {
  position: absolute;
  bottom: calc(100% + 0.5rem);
  left: 50%;
  transform: translateX(-50%);
  z-index: 11;
  min-width: 220px;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 0.5rem;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}
.share-item {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.6rem 0.75rem;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--km-text);
  font-size: 0.9rem;
  text-align: left;
  cursor: pointer;
}
.share-item:hover {
  background: var(--km-hover);
}
.share-icon {
  font-size: 1.1rem;
  line-height: 1;
}
</style>
