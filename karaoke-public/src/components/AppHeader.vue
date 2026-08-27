<template>
  <header :class="['km-header', { 'km-header-sticky': sticky }]">
    <div class="km-header-inner" :style="{ maxWidth }">
      <div class="km-header-left">
        <slot name="left">
          <RouterLink v-if="back" :to="backRouteTo" class="km-back">{{ back.label }}</RouterLink>
        </slot>
      </div>
      <div class="km-header-center">
        <slot name="center" />
      </div>
      <div class="km-header-right">
        <slot name="right">
          <RouterLink v-if="profileLink" :to="profileLinkRouteTo" class="km-back">
            {{ profileLink.label }}
          </RouterLink>
          <AuthStatusWidget v-if="showAuthWidget" />
          <div v-if="showThemeToggle" class="km-theme-toggle">
            <button
              :class="['km-tb', theme === 'light' ? 'active' : '']"
              title="Светлая"
              @click="setTheme('light')"
            >
              ☀
            </button>
            <button
              :class="['km-tb', theme === 'system' ? 'active' : '']"
              title="Авто"
              @click="setTheme('system')"
            >
              ⬡
            </button>
            <button
              :class="['km-tb', theme === 'dark' ? 'active' : '']"
              title="Тёмная"
              @click="setTheme('dark')"
            >
              🌙
            </button>
          </div>
        </slot>
      </div>
      <RouterLink to="/" class="km-logo-link">
        <img :src="logoSrc" :alt="logoAlt" class="km-logo" />
      </RouterLink>
    </div>
  </header>
</template>

<script>
import { useDesign } from '../composables/useDesign'
import AuthStatusWidget from './AuthStatusWidget.vue'

/**
 * Единая шапка публичного сайта karaoke-public (spec 250).
 *
 * Заменяет 20 дублированных `<header class="km-header">`-блоков в views/.
 * Логотип ВСЕГДА справа, ВСЕГДА кликабельный → '/'. Слева — back-ссылка
 * по контексту (`← Главная`, `← Закрома`, `← Мои плейлисты`, `← Мои задания`,
 * `← Личный кабинет`). В правом слоте — `profileLink` + `AuthStatusWidget` +
 * переключатель темы.
 *
 * **API**:
 * - Props `back` / `profileLink` — типизированные ссылки (`{ to, label, query? }`).
 *   Используются для 95% страниц.
 * - Slots `left` / `center` / `right` — escape hatch для кастомных шапок
 *   (например, `EditorWorkView` использует `center` для заголовка песни).
 *   Slot перебивает соответствующий prop.
 * - Props `showAuthWidget` / `showThemeToggle` — для editor-страниц и главной.
 *   `showAuthWidget` default `true`, `showThemeToggle` default `false` (только
 *   на главной — HomeView передаёт `:show-theme-toggle="true"`).
 * - Prop `sticky` — `position: sticky; top: 0` (FR-012). Default: `true`.
 *
 * **Live-логика premium** (LiveDoc `162-fix-header-stale-premium-status`)
 * наследуется автоматически через `AuthStatusWidget` — реактивность на
 * `auth.isPremium` через `usePremiumLiveSync` не затрагивается.
 *
 * **Mobile layout**: на экранах ≤ 700px шапка перестраивается в 2 строки
 * (grid-template-areas): row 1 = `left + center`, row 2 = `right + logo`.
 * На широких экранах — горизонтальный flex с логотипом справа.
 *
 * **Исключения** (A-002, A-003): `PlayerView`, `ShareView`,
 * `SubscriptionReturnView` не используют `<AppHeader>` (full-screen /
 * минималистичные layout). `EditorWorkView` использует slot-based режим.
 *
 * @see specs/250-unify-site-header FR-001..FR-016
 * @see specs/250-unify-site-header/research.md (RT-1..RT-5)
 * @see specs/250-unify-site-header/contracts/AppHeader-component.md
 * @see livedocs/features/250-unify-site-header
 * @see livedocs/features/162-fix-header-stale-premium-status (live premium)
 */
export default {
  name: 'AppHeader',
  components: { AuthStatusWidget },
  props: {
    back: { type: Object, default: null },
    profileLink: { type: Object, default: null },
    showAuthWidget: { type: Boolean, default: true },
    showThemeToggle: { type: Boolean, default: false },
    sticky: { type: Boolean, default: true },
    logoSrc: { type: String, default: '/KARAOKE_LOGO.png' },
    logoAlt: { type: String, default: 'Своё Место' },
    maxWidth: { type: String, default: '900px' },
  },
  setup() {
    const { theme, applyTheme } = useDesign()
    function setTheme(val) {
      theme.value = val
      applyTheme(val)
    }
    return { theme, setTheme }
  },
  computed: {
    // Поддерживает три формы `back`:
    // 1. { to: '/path' }                  — простой path
    // 2. { to: '/path', query: {...} }    — path + query
    // 3. { name: 'route-name', params: {...}, query?: {...} }  — named route (specs/258-zakroma-routing-refactor US2)
    backRouteTo() {
      if (!this.back) return '/'
      if (this.back.name) {
        const target = { name: this.back.name }
        if (this.back.params) target.params = this.back.params
        if (this.back.query) target.query = this.back.query
        return target
      }
      if (this.back.query) {
        return { path: this.back.to, query: this.back.query }
      }
      return this.back.to
    },
    profileLinkRouteTo() {
      if (!this.profileLink) return '/'
      if (this.profileLink.name) {
        const target = { name: this.profileLink.name }
        if (this.profileLink.params) target.params = this.profileLink.params
        if (this.profileLink.query) target.query = this.profileLink.query
        return target
      }
      if (this.profileLink.query) {
        return { path: this.profileLink.to, query: this.profileLink.query }
      }
      return this.profileLink.to
    },
  },
}
</script>

<style scoped>
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-sticky {
  position: sticky;
  top: 0;
  z-index: 100;
}
.km-header-inner {
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.km-header-left,
.km-header-center,
.km-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-header-center {
  flex: 1;
  justify-content: center;
  min-width: 0;
}
.km-header-right {
  gap: 0.6rem;
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
.km-logo-link {
  display: flex;
  align-items: center;
  text-decoration: none;
  flex-shrink: 0;
}
.km-logo {
  height: 36px;
  width: auto;
}
.km-theme-toggle {
  display: flex;
  gap: 0.25rem;
}
.km-tb {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text2);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  line-height: 1;
}
.km-tb:hover {
  background: var(--km-hover);
}
.km-tb.active {
  background: var(--km-accent);
  color: #fff;
  border-color: var(--km-accent);
}

/* Узкие экраны (≤ 700px): если контент (left + center + right + logo)
   влезает в одну строку — оставляем flex-row (одна строка); если не влезает —
   flex-wrap сам переносит на 2 строки. Никакого принудительного grid'а —
   шапка адаптируется естественно. */
@media (max-width: 700px) {
  .km-header {
    padding: 0.5rem 0.75rem;
  }
  .km-header-inner {
    flex-wrap: wrap;
    justify-content: flex-start;
    gap: 0.4rem 0.5rem;
  }
  .km-header-left,
  .km-header-right {
    flex: 0 1 auto;
  }
  .km-header-center {
    flex: 1 1 auto;
    justify-content: flex-end;
  }
  .km-logo {
    height: 32px;
  }
}
@media (max-width: 500px) {
  .km-header {
    padding: 0.4rem 0.5rem;
  }
  .km-header-inner {
    gap: 0.35rem 0.4rem;
  }
  .km-logo {
    height: 28px;
  }
  .km-tb {
    padding: 0.15rem 0.4rem;
    font-size: 0.8rem;
  }
}
</style>
