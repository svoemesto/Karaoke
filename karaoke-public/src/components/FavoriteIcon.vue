<template>
  <span v-if="state === 'loading'" class="fav-spinner" title="Загрузка…" />
  <a
    v-else
    href="#"
    class="fav-icon"
    :class="{ 'fav-on': state === 'on' }"
    :title="state === 'on' ? 'Убрать из избранного' : 'В избранное'"
    @click.prevent="onClick"
  >
    <SvgIcon name="favorite" :active="state === 'on'" :size="18" />
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

export default {
  name: 'FavoriteIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true }
  },
  setup(props) {
    const router = useRouter()
    const route = useRoute()
    const { token } = useAuth()
    const { favStateFor, setFavorited } = usePlaylistMembership()
    const { openLimit } = usePremiumModal()
    const busy = ref(false)

    const state = computed(() => favStateFor(props.songId))

    async function onClick() {
      // Аноним — предлагаем войти (после входа вернём на текущую страницу).
      if (!token.value) {
        router.push({ path: '/login', query: { redirect: route.fullPath } })
        return
      }
      if (busy.value) return
      busy.value = true
      try {
        const { status, body } = await toggleFavorite(props.songId)
        if (status === 200 && body) {
          if (body.limitReached) {
            openLimit({ limit: body.limit, benefits: body.benefits })
          } else {
            setFavorited(props.songId, !!body.favorited)
          }
        }
      } finally {
        busy.value = false
      }
    }

    return { state, onClick }
  }
}
</script>

<style scoped>
.fav-icon { display: inline-flex; align-items: center; justify-content: center; cursor: pointer; line-height: 0; }
.fav-icon:hover { transform: scale(1.12); }
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
@keyframes fav-spin { to { transform: rotate(360deg); } }
</style>
