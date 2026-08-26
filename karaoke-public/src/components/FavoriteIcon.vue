<template>
  <!-- Pass 239 (specs/239-zakroma-author-songs-batch-render):
       - Аноним (нет токена) → "гостевая" серая звезда с tooltip + редирект на /login.
       - Залогин, но membership ещё не загружен → НЕ спиннер, а нейтральная "off" (Clarification Q3:
         "off" фиксируется до logout/login/reload, без retry). Это устраняет 2500 вечных спиннеров.
       - Optimistic update: при клике ДО запроса состояние меняется мгновенно, откат при ошибке. -->
  <span v-if="isGuest" class="fav-icon fav-guest" title="Войдите, чтобы добавить в избранное">
    <SvgIcon name="favorite" :active="false" :size="18" />
  </span>
  <a
    v-else
    href="#"
    class="fav-icon"
    :class="{ 'fav-on': state === 'on', 'has-label': label }"
    :aria-label="state === 'on' ? 'Убрать из избранного' : 'В избранное'"
    :title="state === 'on' ? 'Убрать из избранного' : 'В избранное'"
    @click.prevent="onClick"
  >
    <SvgIcon name="favorite" :active="state === 'on'" :size="18" />
    <span v-if="label" class="fav-label">{{ label }}</span>
  </a>
</template>

<script>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import SvgIcon from './SvgIcon.vue'
import { useAuth } from '../composables/useAuth'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { usePremiumModal } from '../composables/usePremiumModal'
import { toggleFavorite } from '../services/playlistApi'

/**
 * Компонент «Favorite Icon».
 *
 * @see AGENTS.md
 *
 * Pass 239 (specs/239-zakroma-author-songs-batch-render): иконка избранного в страницах списка песен
 * больше НЕ показывает спиннер «Загрузка…». Состояние читается из module-level singleton
 * (`usePlaylistMembership.favoriteIds`), который bulk-fetch'ится при логине одним запросом.
 * Аноним → гостевая иконка с редиректом на /login (Clarification Q2, 2026-08-25).
 */

export default {
  name: 'FavoriteIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true },
    label: { type: String, default: '' },
  },
  setup(props) {
    const router = useRouter()
    const route = useRoute()
    const { token } = useAuth()
    const { favStateFor, broadcastFavorited } = usePlaylistMembership()
    const { openLimit } = usePremiumModal()
    const busy = ref(false)

    const state = computed(() => favStateFor(props.songId))
    const isGuest = computed(() => !token.value)

    async function onClick() {
      // Аноним — предлагаем войти (после входа вернём на текущую страницу).
      if (!token.value) {
        router.push({ path: '/login', query: { redirect: route.fullPath } })
        return
      }
      if (busy.value) return
      busy.value = true

      // Pass 239: optimistic update — ДО сетевого запроса обновляем локальный store,
      // откатываем при ошибке или при limitReached=true.
      const prev = state.value
      const next = prev === 'on' ? 'off' : 'on'
      broadcastFavorited(props.songId, next === 'on')

      try {
        const { status, body } = await toggleFavorite(props.songId)
        if (status === 200 && body) {
          if (body.limitReached) {
            // Откатываем optimistic update, открываем premium-модалку.
            broadcastFavorited(props.songId, prev === 'on')
            openLimit({ limit: body.limit, benefits: body.benefits })
          }
          // Иначе: optimistic уже верный, broadcast'ать ещё раз НЕ нужно (иначе эхо между вкладками).
        } else {
          // Не 200, откат.
          broadcastFavorited(props.songId, prev === 'on')
        }
      } catch (e) {
        broadcastFavorited(props.songId, prev === 'on')
      } finally {
        busy.value = false
      }
    }

    return { state, isGuest, onClick }
  },
}
</script>

<style scoped>
/* Pass 239: "гостевая" иконка избранного для анонима — серая, не кликабельна напрямую
   (но клик по label родителя/контейнера тоже может вести на /login). */
.fav-guest {
  cursor: default;
  opacity: 0.5;
}
</style>

<style scoped>
.fav-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  line-height: 0;
}
.fav-icon:hover {
  transform: scale(1.12);
}
.fav-icon.has-label {
  line-height: normal;
  justify-content: flex-start;
  gap: 6px;
}
.fav-icon.has-label:hover {
  transform: none;
}
.fav-label {
  font-size: 0.9rem;
  color: inherit;
}
.fav-spinner {
  display: inline-block;
  width: 13px;
  height: 13px;
  border: 2px solid #e0b9c0;
  border-top-color: #e11d2a;
  border-radius: 50%;
  vertical-align: middle;
  animation: fav-spin 0.8s linear infinite;
}
@keyframes fav-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
