<template>
  <span v-if="state === 'loading'" class="pl-spinner" title="Загрузка…" />
  <a
    v-else
    href="#"
    class="pl-icon"
    :class="{ 'pl-on': state === 'on' }"
    title="Плейлисты"
    @click.prevent="openMenu"
  >
    <SvgIcon name="playlist" :active="state === 'on'" :size="18" />
  </a>

  <teleport to="body">
    <div v-if="open" class="pl-menu-backdrop" @click="close">
      <div class="pl-menu" :style="menuStyle" @click.stop>
        <div class="pl-menu-title">Плейлисты</div>

        <!-- Плейлисты, где песня уже есть -->
        <template v-if="inPlaylists.length">
          <div v-for="pl in inPlaylists" :key="'in-' + pl.id" class="pl-group">
            <button class="pl-item pl-item-toggle" @click="toggleExpanded(pl.id)">
              <span class="pl-check">✓</span>
              <span class="pl-item-name">{{ pl.name }}</span>
              <span class="pl-arrow">{{ expandedId === pl.id ? '▾' : '▸' }}</span>
            </button>
            <div v-if="expandedId === pl.id" class="pl-sub">
              <button class="pl-item pl-sub-item" @click="goTo(pl.id)">Перейти в плейлист</button>
              <button class="pl-item pl-sub-item pl-danger" :disabled="busy" @click="removeFrom(pl.id)">Удалить из плейлиста</button>
            </div>
          </div>
          <div class="pl-divider" />
        </template>

        <!-- Добавить в плейлист -->
        <button class="pl-item pl-item-toggle" @click="showAdd = !showAdd">
          <span class="pl-plus">＋</span>
          <span class="pl-item-name">Добавить в плейлист</span>
          <span class="pl-arrow">{{ showAdd ? '▾' : '▸' }}</span>
        </button>
        <div v-if="showAdd" class="pl-sub">
          <button class="pl-item pl-sub-item pl-accent" :disabled="busy" @click="addToNew">➕ В новый плейлист</button>
          <button
            v-for="pl in notInPlaylists"
            :key="'add-' + pl.id"
            class="pl-item pl-sub-item"
            :disabled="busy"
            @click="addTo(pl.id)"
          >{{ pl.name }}</button>
          <div v-if="!notInPlaylists.length" class="pl-empty">Песня уже во всех плейлистах</div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from './SvgIcon.vue'
import { useAuth } from '../composables/useAuth'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { usePremiumModal } from '../composables/usePremiumModal'
import {
  addSongToPlaylist,
  removeSongFromPlaylist,
  createPlaylist,
} from '../services/playlistApi'

export default {
  name: 'PlaylistIcon',
  components: { SvgIcon },
  props: {
    songId: { type: [Number, String], required: true }
  },
  setup(props) {
    const router = useRouter()
    const { token, user } = useAuth()
    const membership = usePlaylistMembership()
    const { openPremiumRequired, openLimit } = usePremiumModal()

    const open = ref(false)
    const menuStyle = ref({})
    const expandedId = ref(null)
    const showAdd = ref(false)
    const busy = ref(false)

    const isPremium = computed(() => !!(user.value && user.value.effectivePremium))
    const state = computed(() => membership.plStateFor(props.songId))

    // Плейлисты (без «Избранного») из кэша, разделённые на «где песня есть» / «куда добавить».
    const myPlaylists = computed(() => membership.playlists.value.filter(p => !p.favorites))
    const currentIds = computed(() => (membership.membership[String(props.songId)] || {}).playlistIds || [])
    const inPlaylists = computed(() => myPlaylists.value.filter(p => currentIds.value.includes(p.id)))
    const notInPlaylists = computed(() => myPlaylists.value.filter(p => !currentIds.value.includes(p.id)))

    async function openMenu(e) {
      if (!token.value) {
        router.push({ path: '/login' })
        return
      }
      if (!isPremium.value) {
        openPremiumRequired({ benefits: PREMIUM_BENEFITS })
        return
      }
      const rect = e.currentTarget.getBoundingClientRect()
      // Меню шириной ~230px; прижимаем правый край к иконке, не вылезая за левый край экрана.
      const left = Math.max(8, rect.right - 230)
      menuStyle.value = { top: `${rect.bottom + 4}px`, left: `${left}px` }
      expandedId.value = null
      showAdd.value = false
      await membership.loadPlaylists(true)
      open.value = true
    }
    function close() { open.value = false }
    function toggleExpanded(id) { expandedId.value = expandedId.value === id ? null : id }

    function setLocalIds(ids) { membership.setPlaylistIds(props.songId, ids) }

    async function addTo(playlistId) {
      if (busy.value) return
      busy.value = true
      try {
        const { status, body } = await addSongToPlaylist(playlistId, props.songId)
        if (status === 200) {
          setLocalIds([...currentIds.value, playlistId])
          showAdd.value = false
        } else if (body && body.error === 'limit_reached') {
          openLimit({ limit: body.limit, benefits: body.benefits })
        } else if (body && body.error === 'premium_required') {
          openPremiumRequired({ benefits: body.benefits })
        }
      } finally { busy.value = false }
    }

    async function removeFrom(playlistId) {
      if (busy.value) return
      busy.value = true
      try {
        const { status } = await removeSongFromPlaylist(playlistId, props.songId)
        if (status === 200) {
          setLocalIds(currentIds.value.filter(id => id !== playlistId))
          expandedId.value = null
        }
      } finally { busy.value = false }
    }

    async function addToNew() {
      if (busy.value) return
      busy.value = true
      try {
        const { status, body } = await createPlaylist({ songId: props.songId })
        if (status === 200 && body && body.id) {
          await membership.loadPlaylists(true)
          setLocalIds([...currentIds.value, body.id])
          close()
          router.push({ path: `/account/playlists/${body.id}` })
        } else if (body && body.error === 'limit_reached') {
          openLimit({ limit: body.limit, benefits: body.benefits })
        } else if (body && body.error === 'premium_required') {
          openPremiumRequired({ benefits: body.benefits })
        }
      } finally { busy.value = false }
    }

    function goTo(id) { close(); router.push({ path: `/account/playlists/${id}` }) }

    return {
      open, menuStyle, expandedId, showAdd, busy, state,
      inPlaylists, notInPlaylists,
      openMenu, close, toggleExpanded, addTo, removeFrom, addToNew, goTo,
    }
  }
}

const PREMIUM_BENEFITS = [
  'Свои плейлисты: создавайте, переименовывайте, меняйте порядок песен',
  'Онлайн-плеер для всех песен, а не только «в эфире»',
  'Непрерывное воспроизведение, повтор и случайный порядок',
  'Экспорт аудио-дорожек песни',
]
</script>

<style scoped>
.pl-icon { display: inline-flex; align-items: center; justify-content: center; cursor: pointer; line-height: 0; }
.pl-icon:hover { transform: scale(1.12); }
.pl-spinner {
  display: inline-block; width: 13px; height: 13px;
  border: 2px solid #b9c9e0; border-top-color: #0077ff; border-radius: 50%;
  vertical-align: middle; animation: pl-spin 0.8s linear infinite;
}
@keyframes pl-spin { to { transform: rotate(360deg); } }

.pl-menu-backdrop { position: fixed; inset: 0; z-index: 2500; }
.pl-menu {
  position: fixed;
  width: 230px;
  max-height: 60vh;
  overflow-y: auto;
  background: var(--km-card, #fff);
  color: var(--km-text, #1a1a1a);
  border: 1px solid var(--km-border, #ccc);
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
  padding: 6px;
  font-size: 0.85rem;
}
.pl-menu-title {
  font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em;
  color: var(--km-text2, #888); padding: 4px 8px 6px;
}
.pl-item {
  display: flex; align-items: center; gap: 6px; width: 100%;
  background: transparent; border: none; text-align: left;
  padding: 7px 8px; border-radius: 6px; cursor: pointer;
  color: inherit; font-size: 0.85rem;
}
.pl-item:hover:not(:disabled) { background: var(--km-hover, rgba(0,0,0,0.06)); }
.pl-item:disabled { opacity: 0.5; cursor: default; }
.pl-item-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pl-arrow { color: var(--km-text2, #999); font-size: 0.75rem; }
.pl-check { color: #0077ff; font-weight: 700; }
.pl-plus { color: #0077ff; font-weight: 700; }
.pl-sub { padding-left: 10px; border-left: 2px solid var(--km-border, #e2e2e2); margin: 2px 0 2px 12px; }
.pl-sub-item { font-size: 0.82rem; padding: 6px 8px; }
.pl-accent { color: #0077ff; font-weight: 600; }
.pl-danger { color: #d02c3a; }
.pl-divider { height: 1px; background: var(--km-border, #e2e2e2); margin: 5px 4px; }
.pl-empty { font-size: 0.78rem; color: var(--km-text2, #999); padding: 6px 8px; }
</style>
