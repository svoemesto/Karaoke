<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/account/playlists" class="km-back">← Мои плейлисты</RouterLink>
        </div>
        <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
      </div>
    </header>

    <LoginRequired v-if="!isLoggedIn" />
    <div v-else-if="loading" class="km-loading">Загрузка...</div>
    <div v-else-if="notFound" class="km-loading">Плейлист не найден.</div>

    <div v-else class="km-content">
      <!-- Имя плейлиста -->
      <div class="km-name-row">
        <input
          v-if="!playlist.favorites"
          v-model="nameEdit"
          class="km-name-input"
          maxlength="255"
          @change="onRename"
          @keyup.enter="onRename"
        />
        <h1 v-else class="km-name-fixed">🔴 Избранное</h1>
        <span class="km-name-count">{{ items.length }} {{ pluralSongs(items.length) }}</span>
      </div>

      <!-- Встроенный плеер (тот же /player/:id в iframe, что и на странице песни) -->
      <div class="km-player-box" :class="{ 'km-player-wide': playerWide }">
        <iframe
          v-if="started"
          ref="playerIframe"
          :src="`/player/${firstSongId}?pl=1`"
          class="km-player-iframe"
          allow="autoplay; fullscreen"
          frameborder="0"
          allowfullscreen
        />
        <div v-else class="km-player-placeholder">
          <button class="km-big-play" :disabled="!hasPlayable" @click="startPlaylist">
            ▶ Запустить плейлист
          </button>
          <p v-if="!hasPlayable" class="km-player-hint">
            Нет доступных для воспроизведения песен (не готовы или недоступны без премиума).
          </p>
        </div>
      </div>

      <!-- Панель управления плейлистом -->
      <div class="km-controls">
        <button class="km-ctrl-btn" title="Предыдущая" :disabled="!started" @click="prev">⏮</button>
        <button
          class="km-ctrl-btn km-ctrl-main"
          :title="isPlaying ? 'Пауза' : 'Играть'"
          @click="togglePlay"
        >
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <button class="km-ctrl-btn" title="Следующая" :disabled="!started" @click="next">⏭</button>

        <div class="km-ctrl-sep" />

        <button
          class="km-ctrl-toggle"
          :class="{ active: settings.continuous }"
          title="Непрерывное воспроизведение"
          @click="toggleContinuous"
        >
          ⇥ Непрерывно
        </button>
        <button
          class="km-ctrl-toggle"
          :class="{ active: settings.repeatMode !== 'none' }"
          :title="repeatTitle"
          @click="cycleRepeat"
        >
          {{ repeatLabel }}
        </button>
        <button
          class="km-ctrl-toggle"
          :class="{ active: settings.shuffle }"
          title="Случайный порядок"
          @click="toggleShuffle"
        >
          🔀 Случайно
        </button>
      </div>

      <!-- Список песен (drag-drop) -->
      <draggable
        v-model="items"
        item-key="id"
        handle=".km-drag-handle"
        class="km-song-list"
        ghost-class="km-song-ghost"
        @end="onReorder"
      >
        <template #item="{ element: item }">
          <div
            class="km-song-row"
            :class="{
              'km-song-current': item.songId === currentSongId,
              'km-song-muted': item.muted,
            }"
          >
            <span class="km-drag-handle" title="Перетащите для смены порядка">⠿</span>
            <span class="km-song-num">{{ badgeFor(item) }}</span>
            <div class="km-song-info">
              <div class="km-song-title">{{ item.songName || 'Песня #' + item.songId }}</div>
              <div class="km-song-sub">
                {{ item.author }}<span v-if="item.album"> — {{ item.album }}</span>
              </div>
            </div>
            <button
              class="km-song-btn"
              :class="{ 'km-muted-on': item.muted }"
              :title="
                item.muted
                  ? 'Включить (сейчас пропускается)'
                  : 'Приглушить (пропускать при проигрывании)'
              "
              @click="toggleMute(item)"
            >
              {{ item.muted ? '🔇' : '🔊' }}
            </button>
            <button
              class="km-song-btn km-song-remove"
              title="Убрать из плейлиста"
              @click="removeItem(item)"
            >
              ✕
            </button>
          </div>
        </template>
      </draggable>

      <p v-if="!items.length" class="km-empty">
        В плейлисте пока нет песен. Добавляйте их синей иконкой-закладкой в таблицах
        «Закрома»/«Поиск».
      </p>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import draggable from 'vuedraggable'
import { fetchPlayerToken } from '../services/playerLauncher'
import {
  fetchPlaylist,
  renamePlaylist,
  updatePlaylistSettings,
  reorderPlaylist,
  setSongMute,
  removeSongFromPlaylist,
} from '../services/playlistApi'
import { usePlayerReadiness } from '../composables/usePlayerReadiness'
import { useAuth } from '../composables/useAuth'
import LoginRequired from '../components/LoginRequired.vue'

export default {
  name: 'PlaylistEditView',
  components: { draggable, LoginRequired },
  setup() {
    const route = useRoute()
    const readiness = usePlayerReadiness()
    const { isLoggedIn } = useAuth()

    const id = Number(route.params.id)
    const loading = ref(true)
    const notFound = ref(false)
    const playlist = reactive({ id, name: '', favorites: false })
    const items = ref([])
    const nameEdit = ref('')
    const settings = reactive({ continuous: true, repeatMode: 'none', shuffle: false })

    const playerIframe = ref(null)
    const started = ref(false)
    const firstSongId = ref(null)
    const isPlaying = ref(false)
    const currentSongId = ref(null)
    const playerWide = ref(false)

    const hasPlayable = computed(() =>
      items.value.some((it) => !it.muted && readiness.stateFor(it.songId) === 'active'),
    )
    const repeatLabel = computed(() =>
      settings.repeatMode === 'one'
        ? '🔂 Одна'
        : settings.repeatMode === 'all'
          ? '🔁 Все'
          : '🔁 Повтор',
    )
    const repeatTitle = computed(() =>
      settings.repeatMode === 'one'
        ? 'Повтор одной песни'
        : settings.repeatMode === 'all'
          ? 'Повтор всего плейлиста'
          : 'Повтор выключен',
    )

    function pluralSongs(n) {
      const a = Math.abs(n) % 100,
        b = a % 10
      if (a > 10 && a < 20) return 'песен'
      if (b > 1 && b < 5) return 'песни'
      if (b === 1) return 'песня'
      return 'песен'
    }
    function badgeFor(item) {
      if (item.muted) return '🔇'
      const st = readiness.stateFor(item.songId)
      if (st === 'active') return '♪'
      if (st === 'loading') return '…'
      return '🔒'
    }

    async function load() {
      loading.value = true
      const { status, body } = await fetchPlaylist(id)
      if (status !== 200 || !body || !body.playlist) {
        notFound.value = true
        loading.value = false
        return
      }
      Object.assign(playlist, body.playlist)
      nameEdit.value = body.playlist.name
      settings.continuous = body.playlist.continuous
      settings.repeatMode = body.playlist.repeatMode || 'none'
      settings.shuffle = body.playlist.shuffle
      items.value = body.items || []
      loading.value = false
      readiness.load(items.value.map((it) => it.songId))
    }

    // Упорядоченный список воспроизводимых song_id (не muted и доступных), с учётом shuffle.
    function playableIds() {
      const arr = items.value
        .filter((it) => !it.muted && readiness.stateFor(it.songId) === 'active')
        .map((it) => it.songId)
      if (settings.shuffle) {
        for (let k = arr.length - 1; k > 0; k--) {
          const j = Math.floor(Math.random() * (k + 1))
          ;[arr[k], arr[j]] = [arr[j], arr[k]]
        }
      }
      return arr
    }

    // --- Мост к плееру в iframe ---
    function send(type, extra) {
      const win = playerIframe.value && playerIframe.value.contentWindow
      if (win) win.postMessage(Object.assign({ source: 'kp-playlist', type }, extra), '*')
    }
    async function onMessage(e) {
      const win = playerIframe.value && playerIframe.value.contentWindow
      // Сообщения от плеера-плейлиста (только из нашего iframe).
      if (e.source === win && e.data && e.data.source === 'kp-playlist-player') {
        const d = e.data
        if (d.type === 'need-token') {
          const { token } = await fetchPlayerToken(d.songId)
          if (token) sessionStorage.setItem(`kp_token_${d.songId}`, token)
          send('token', { songId: d.songId, token })
        } else if (d.type === 'track') {
          currentSongId.value = d.songId
        } else if (d.type === 'state') {
          isPlaying.value = !!d.playing
        }
        return
      }
      // Плеер (KaraokePlayer) просит родителя растянуть iframe — как на странице песни.
      if (
        e.source === win &&
        e.data &&
        e.data.source === 'karaoke-player' &&
        e.data.type === 'display-mode'
      ) {
        playerWide.value = e.data.mode === 'page'
      }
    }

    async function startPlaylist() {
      const ids = playableIds()
      if (!ids.length) return
      const first = ids[0]
      const { canWatch, token } = await fetchPlayerToken(first)
      if (!canWatch || !token) return
      sessionStorage.setItem(`kp_token_${first}`, token)
      sessionStorage.setItem(
        'kp_pl_queue',
        JSON.stringify({
          ids,
          continuous: settings.continuous,
          repeatMode: settings.repeatMode,
        }),
      )
      firstSongId.value = first
      currentSongId.value = first
      started.value = true
    }

    function pushQueue() {
      if (started.value) send('setqueue', { ids: playableIds() })
    }

    function prev() {
      if (started.value) send('prev')
    }
    function next() {
      if (started.value) send('next')
    }
    function togglePlay() {
      if (!started.value) {
        startPlaylist()
        return
      }
      send('toggle')
    }

    // --- Настройки воспроизведения (персист + пробросить в плеер) ---
    let saveTimer = null
    function persistSettings() {
      clearTimeout(saveTimer)
      saveTimer = setTimeout(() => {
        updatePlaylistSettings(id, {
          continuous: settings.continuous,
          repeatMode: settings.repeatMode,
          shuffle: settings.shuffle,
        })
      }, 300)
    }
    function toggleContinuous() {
      settings.continuous = !settings.continuous
      persistSettings()
      send('setmodes', { continuous: settings.continuous, repeatMode: settings.repeatMode })
    }
    function cycleRepeat() {
      settings.repeatMode =
        settings.repeatMode === 'none' ? 'all' : settings.repeatMode === 'all' ? 'one' : 'none'
      persistSettings()
      send('setmodes', { continuous: settings.continuous, repeatMode: settings.repeatMode })
    }
    function toggleShuffle() {
      settings.shuffle = !settings.shuffle
      persistSettings()
      pushQueue()
    }

    // --- Список: имя / порядок / mute / удаление ---
    function onRename() {
      const nm = nameEdit.value.trim()
      if (!nm || nm === playlist.name) {
        nameEdit.value = playlist.name
        return
      }
      renamePlaylist(id, nm).then(({ status }) => {
        if (status === 200) playlist.name = nm
      })
    }
    function onReorder() {
      reorderPlaylist(
        id,
        items.value.map((it) => it.songId),
      )
      pushQueue()
    }
    function toggleMute(item) {
      item.muted = !item.muted
      setSongMute(id, item.songId, item.muted)
      pushQueue()
    }
    function removeItem(item) {
      removeSongFromPlaylist(id, item.songId).then(({ status }) => {
        if (status === 200) {
          items.value = items.value.filter((it) => it.songId !== item.songId)
          pushQueue()
        }
      })
    }

    onMounted(async () => {
      if (!isLoggedIn.value) {
        loading.value = false
        return
      }
      window.addEventListener('message', onMessage)
      await load()
    })
    onBeforeUnmount(() => {
      window.removeEventListener('message', onMessage)
      clearTimeout(saveTimer)
      sessionStorage.removeItem('kp_pl_queue')
    })

    return {
      isLoggedIn,
      loading,
      notFound,
      playlist,
      items,
      nameEdit,
      settings,
      playerIframe,
      started,
      firstSongId,
      isPlaying,
      currentSongId,
      playerWide,
      hasPlayable,
      repeatLabel,
      repeatTitle,
      pluralSongs,
      badgeFor,
      startPlaylist,
      togglePlay,
      next,
      prev,
      toggleContinuous,
      cycleRepeat,
      toggleShuffle,
      onRename,
      onReorder,
      toggleMute,
      removeItem,
    }
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner {
  max-width: 900px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.km-header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
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
.km-logo {
  height: 36px;
  width: auto;
}
.km-loading {
  padding: 2rem;
  text-align: center;
  color: var(--km-text2);
}

.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1.5rem 1rem;
}
.km-name-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.km-name-input {
  flex: 1;
  font-size: 1.3rem;
  font-weight: 700;
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid transparent;
  border-radius: 8px;
  padding: 0.3rem 0.6rem;
}
.km-name-input:hover {
  border-color: var(--km-border);
}
.km-name-input:focus {
  outline: none;
  border-color: var(--km-accent);
}
.km-name-fixed {
  flex: 1;
  font-size: 1.3rem;
  font-weight: 700;
  margin: 0;
}
.km-name-count {
  font-size: 0.8rem;
  color: var(--km-text2);
  white-space: nowrap;
}

.km-player-box {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 0.75rem;
}
.km-player-iframe {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}
/* «Широкий» режим — плеер (внутри iframe) сам попросил родителя растянуть его на весь вьюпорт.
   position:fixed игнорирует max-width родительского .km-content (тот не создаёт containing block) —
   как на странице песни (SongModern .km-player-page-mode). */
.km-player-box.km-player-wide {
  position: fixed;
  inset: 0;
  z-index: 2000;
  margin: 0;
  border-radius: 0;
  aspect-ratio: unset;
  width: 100vw;
  height: 100vh;
}
.km-player-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  background: linear-gradient(135deg, #12121f, #1c1030);
}
.km-big-play {
  background: var(--km-accent, #0077ff);
  color: #fff;
  border: none;
  border-radius: 30px;
  padding: 0.7rem 1.6rem;
  font-size: 1rem;
  font-weight: 700;
  cursor: pointer;
}
.km-big-play:hover {
  filter: brightness(1.1);
}
.km-big-play:disabled {
  opacity: 0.5;
  cursor: default;
}
.km-player-hint {
  color: #b9b9c9;
  font-size: 0.82rem;
  max-width: 80%;
  text-align: center;
}

.km-controls {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 0.5rem 0.75rem;
  margin-bottom: 1.25rem;
}
.km-ctrl-btn {
  background: transparent;
  border: none;
  color: var(--km-text);
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 6px;
}
.km-ctrl-btn:hover:not(:disabled) {
  background: var(--km-hover);
}
.km-ctrl-btn:disabled {
  opacity: 0.4;
  cursor: default;
}
.km-ctrl-main {
  font-size: 1.35rem;
}
.km-ctrl-sep {
  width: 1px;
  height: 22px;
  background: var(--km-border);
  margin: 0 0.4rem;
}
.km-ctrl-toggle {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-text2);
  border-radius: 16px;
  padding: 0.3rem 0.7rem;
  font-size: 0.8rem;
  cursor: pointer;
}
.km-ctrl-toggle:hover {
  background: var(--km-hover);
}
.km-ctrl-toggle.active {
  background: var(--km-accent);
  color: #fff;
  border-color: var(--km-accent);
}

.km-song-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.km-song-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 10px;
  padding: 0.5rem 0.7rem;
}
.km-song-current {
  border-color: var(--km-accent);
  box-shadow: 0 0 0 1px var(--km-accent);
}
.km-song-muted {
  opacity: 0.55;
}
.km-song-ghost {
  opacity: 0.4;
}
.km-drag-handle {
  cursor: grab;
  color: var(--km-text2);
  font-size: 1.1rem;
  user-select: none;
}
.km-song-num {
  width: 1.4rem;
  text-align: center;
  color: var(--km-text2);
  font-size: 0.9rem;
}
.km-song-info {
  flex: 1;
  min-width: 0;
}
.km-song-title {
  font-size: 0.92rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-song-sub {
  font-size: 0.76rem;
  color: var(--km-text2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-song-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 1rem;
  padding: 0.25rem 0.4rem;
  border-radius: 6px;
  color: var(--km-text2);
}
.km-song-btn:hover {
  background: var(--km-hover);
}
.km-muted-on {
  opacity: 1;
}
.km-song-remove:hover {
  color: #d02c3a;
}
.km-empty {
  color: var(--km-text2);
  font-size: 0.9rem;
  padding: 1rem 0;
}
</style>
