<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <RouterLink :to="{ path: '/zakroma', query: author ? { author } : {} }" class="km-back"
          >← Закрома</RouterLink
        >
        <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
      </div>
    </header>

    <LoginRequired
      v-if="!isLoggedIn"
      text="Плейлисты авторов доступны только зарегистрированным пользователям. Войдите или зарегистрируйтесь — это бесплатно."
    />

    <div v-else-if="loading" class="km-loading">Загрузка...</div>

    <div v-else class="km-content">
      <div class="km-name-row">
        <h1 class="km-name-fixed">🎧 Плейлист по песням автора «{{ author }}»</h1>
        <span class="km-name-count">{{ songs.length }} {{ pluralSongs(songs.length) }}</span>
      </div>
      <p class="km-note">
        Плейлист формируется автоматически. Кликните по песне, чтобы начать с неё. Недоступные вам
        сейчас песни отмечены замком и пропускаются при проигрывании.
      </p>

      <!-- Плеер (тот же /player/:id в iframe, что и на странице песни) -->
      <div class="km-player-box" :class="{ 'km-player-wide': player.playerWide.value }">
        <iframe
          v-if="player.started.value"
          ref="playerIframe"
          :src="`/player/${player.firstSongId.value}?pl=1`"
          class="km-player-iframe"
          allow="autoplay; fullscreen"
          frameborder="0"
          allowfullscreen
        />
        <div v-else class="km-player-placeholder">
          <button class="km-big-play" :disabled="!hasPlayable" @click="onStart">
            ▶ Запустить плейлист
          </button>
          <p v-if="!hasPlayable" class="km-player-hint">
            Нет доступных вам для воспроизведения песен этого автора.
          </p>
        </div>
      </div>

      <!-- Управление -->
      <div class="km-controls">
        <button
          class="km-ctrl-btn"
          title="Предыдущая"
          :disabled="!player.started.value"
          @click="player.prev"
        >
          ⏮
        </button>
        <button
          class="km-ctrl-btn km-ctrl-main"
          :title="player.isPlaying.value ? 'Пауза' : 'Играть'"
          @click="onToggle"
        >
          {{ player.isPlaying.value ? '⏸' : '▶' }}
        </button>
        <button
          class="km-ctrl-btn"
          title="Следующая"
          :disabled="!player.started.value"
          @click="player.next"
        >
          ⏭
        </button>
        <div class="km-ctrl-sep" />
        <button
          class="km-ctrl-toggle"
          :class="{ active: modes.continuous }"
          title="Непрерывное воспроизведение"
          @click="toggleContinuous"
        >
          ⇥ Непрерывно
        </button>
        <button
          class="km-ctrl-toggle"
          :class="{ active: modes.repeatMode !== 'none' }"
          :title="repeatTitle"
          @click="cycleRepeat"
        >
          {{ repeatLabel }}
        </button>
        <button
          class="km-ctrl-toggle"
          :class="{ active: modes.shuffle }"
          title="Случайный порядок"
          @click="toggleShuffle"
        >
          🔀 Случайно
        </button>
      </div>

      <!-- Список песен (read-only) -->
      <div class="km-song-list">
        <div
          v-for="(item, i) in songs"
          :key="item.songId"
          class="km-song-row"
          :class="{
            'km-song-current': item.songId === player.currentSongId.value,
            'km-song-locked': statusOf(item.songId) !== 'available',
            'km-song-clickable': clickable(item.songId),
          }"
          @click="onSongClick(item)"
        >
          <span class="km-song-num">{{
            item.songId === player.currentSongId.value && player.isPlaying.value ? '▶' : i + 1
          }}</span>
          <div class="km-song-info">
            <div class="km-song-title">{{ item.songName || 'Песня #' + item.songId }}</div>
            <div class="km-song-sub">
              {{ item.album }}<span v-if="item.year"> · {{ item.year }}</span>
            </div>
          </div>
          <span class="km-song-lock" :title="lockTitle(item.songId)">{{
            lockIcon(item.songId)
          }}</span>
        </div>
        <p v-if="!songs.length" class="km-empty">У автора нет песен.</p>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet } from '../services/api'
import LoginRequired from '../components/LoginRequired.vue'
import { useAuth } from '../composables/useAuth'
import { usePlayerReadiness } from '../composables/usePlayerReadiness'
import { usePlaylistPlayer } from '../composables/usePlaylistPlayer'
import { usePremiumModal } from '../composables/usePremiumModal'

const PREMIUM_BENEFITS = [
  'Онлайн-плеер для всех песен, а не только «в эфире»',
  'Полные плейлисты авторов без замков',
  'Непрерывное воспроизведение, повтор и случайный порядок',
  'Экспорт аудио-дорожек песни',
]

/**
 * View-страница «Author Playlist» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'AuthorPlaylistView',
  components: { LoginRequired },
  setup() {
    const route = useRoute()
    const { isLoggedIn } = useAuth()
    const readiness = usePlayerReadiness()
    const { openPremiumRequired } = usePremiumModal()

    const playerIframe = ref(null)
    const player = usePlaylistPlayer(playerIframe)

    const author = ref(route.query.author || '')
    const loading = ref(true)
    const songs = ref([])
    const modes = reactive({ continuous: true, repeatMode: 'none', shuffle: false })

    const repeatLabel = computed(() =>
      modes.repeatMode === 'one' ? '🔂 Одна' : modes.repeatMode === 'all' ? '🔁 Все' : '🔁 Повтор',
    )
    const repeatTitle = computed(() =>
      modes.repeatMode === 'one'
        ? 'Повтор одной песни'
        : modes.repeatMode === 'all'
          ? 'Повтор всего плейлиста'
          : 'Повтор выключен',
    )

    // available | premium (готова, но нужен премиум) | notready (ещё не готова) | loading
    function statusOf(songId) {
      const st = readiness.stateFor(songId)
      if (st === 'loading') return 'loading'
      if (st === 'active') return 'available'
      return readiness.contentReadyFor(songId) === 'ready' ? 'premium' : 'notready'
    }
    function lockIcon(songId) {
      const s = statusOf(songId)
      if (s === 'available') return '♪'
      if (s === 'loading') return '…'
      if (s === 'premium') return '🪙'
      return '🔒'
    }
    function lockTitle(songId) {
      const s = statusOf(songId)
      if (s === 'available') return 'Доступна'
      if (s === 'premium') return 'Доступно премиум-пользователям'
      if (s === 'notready') return 'Песня ещё готовится'
      return ''
    }
    function clickable(songId) {
      const s = statusOf(songId)
      return s === 'available' || s === 'premium'
    }
    // Клик по строке: доступную — играем с неё; премиум-песню — призыв премиума; иначе ничего.
    function onSongClick(item) {
      const s = statusOf(item.songId)
      if (s === 'available') player.playFrom(item.songId, playableIds(), modes)
      else if (s === 'premium') openPremiumRequired({ benefits: PREMIUM_BENEFITS })
    }

    const hasPlayable = computed(() => songs.value.some((s) => statusOf(s.songId) === 'available'))

    function playableIds() {
      const arr = songs.value.filter((s) => statusOf(s.songId) === 'available').map((s) => s.songId)
      if (modes.shuffle) {
        for (let k = arr.length - 1; k > 0; k--) {
          const j = Math.floor(Math.random() * (k + 1))
          ;[arr[k], arr[j]] = [arr[j], arr[k]]
        }
      }
      return arr
    }

    function pluralSongs(n) {
      const a = Math.abs(n) % 100,
        b = a % 10
      if (a > 10 && a < 20) return 'песен'
      if (b > 1 && b < 5) return 'песни'
      if (b === 1) return 'песня'
      return 'песен'
    }

    async function loadSongs() {
      loading.value = true
      const data = await apiGet('/api/public/zakroma', { author: author.value })
      const flat = []
      for (const block of data || []) {
        const albums = [...(block.albums || [])].sort((a, b) => (a.year || 0) - (b.year || 0))
        for (const alb of albums) {
          const setts = [...(alb.albumSettings || [])].sort(
            (a, b) => (a.track || 0) - (b.track || 0),
          )
          for (const s of setts)
            flat.push({
              songId: s.id,
              songName: s.songName,
              album: alb.albumName,
              year: alb.year,
              track: s.track,
            })
        }
      }
      songs.value = flat
      loading.value = false
      readiness.load(flat.map((s) => s.songId))
    }

    function onStart() {
      player.start(playableIds(), modes)
    }
    function onToggle() {
      if (!player.started.value) onStart()
      else player.toggle()
    }
    function toggleContinuous() {
      modes.continuous = !modes.continuous
      player.pushModes(modes)
    }
    function cycleRepeat() {
      modes.repeatMode =
        modes.repeatMode === 'none' ? 'all' : modes.repeatMode === 'all' ? 'one' : 'none'
      player.pushModes(modes)
    }
    function toggleShuffle() {
      modes.shuffle = !modes.shuffle
      player.pushQueue(playableIds())
    }

    onMounted(async () => {
      if (!isLoggedIn.value) {
        loading.value = false
        return
      }
      player.mount()
      await loadSongs()
    })
    onBeforeUnmount(() => player.unmount())

    return {
      author,
      isLoggedIn,
      loading,
      songs,
      modes,
      player,
      playerIframe,
      repeatLabel,
      repeatTitle,
      hasPlayable,
      statusOf,
      lockIcon,
      lockTitle,
      clickable,
      onSongClick,
      pluralSongs,
      onStart,
      onToggle,
      toggleContinuous,
      cycleRepeat,
      toggleShuffle,
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
  margin-bottom: 0.5rem;
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
.km-note {
  font-size: 0.8rem;
  color: var(--km-text2);
  margin: 0 0 1rem;
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
.km-song-locked {
  opacity: 0.55;
}
.km-song-clickable {
  cursor: pointer;
}
.km-song-clickable:hover {
  background: var(--km-hover);
  border-color: var(--km-accent);
}
.km-song-num {
  width: 1.8rem;
  text-align: center;
  color: var(--km-text2);
  font-size: 0.85rem;
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
.km-song-lock {
  font-size: 1rem;
  padding: 0.25rem 0.4rem;
  color: var(--km-text2);
  user-select: none;
}
.km-empty {
  color: var(--km-text2);
  font-size: 0.9rem;
  padding: 1rem 0;
}
</style>
