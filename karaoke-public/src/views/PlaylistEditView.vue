<template>
  <div class="km-page">
    <!-- Хедер единый (spec 250) -->
    <AppHeader :back="{ to: '/account/playlists', label: '← Мои плейлисты' }" />

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
            <!-- Контейнер с превью альбома и автора (FR-005/FR-006). Чёрный фон, margin 5px,
                 gap 5px между картинками. @error на <img> → плейсхолдер при 404/timeout/network
                 (Acceptance US2.2/3). v-if+транзиентный флаг — единый код-путь для пустого URL
                 и сетевой ошибки. -->
            <div class="km-song-pictures">
              <img
                v-if="item.albumPictureUrl && !item._albumPictureFailed"
                class="km-song-cover"
                :src="item.albumPictureUrl"
                alt=""
                @error="item._albumPictureFailed = true"
              />
              <div v-else class="km-song-cover km-song-cover-fallback" aria-hidden="true">♪</div>
              <!-- Превью автора — аспект 5:2 (горизонтальное, ширина в 2.5 раза больше высоты),
                   см. spec.md §Clarifications Q2 → A. -->
              <img
                v-if="item.authorPictureUrl && !item._authorPictureFailed"
                class="km-song-author"
                :src="item.authorPictureUrl"
                alt=""
                @error="item._authorPictureFailed = true"
              />
              <div v-else class="km-song-author km-song-author-fallback" aria-hidden="true">👤</div>
            </div>
            <div class="km-song-info">
              <!-- Спека 259 (FR-001): название песни — кликабельная ссылка на /song?id=<id>.
                   <router-link> рендерит <a href>, нативная поддержка Ctrl+клик / средней кнопки
                   (FR-005). НЕ запускает встроенный плеер — это просто SPA-навигация (FR-004). -->
              <router-link
                :to="{ name: 'song', query: { id: item.songId } }"
                class="km-song-title-link"
              >
                {{ item.songName || 'Песня #' + item.songId }}
              </router-link>
              <div class="km-song-sub">
                <!-- Шаблон подписи: "Автор - год, альбом" (пользователь уточнил 2026-08-14).
                     Год подставляется только если > 0; альбом — только если непустой.
                     Разделитель " - " показывается, только если есть хотя бы год или альбом. -->
                <template v-if="item.author || item.album">
                  <!-- Спека 259 (FR-002, FR-006): имя автора — ссылка на /zakroma/<authorId>.
                       Резолв через кэш `authorTiles`; если автора нет в кэше (Edge Case:
                       удалён из БД) — fallback на обычный <span>, без ссылки и без ошибки. -->
                  <router-link
                    v-if="item.author && authorIdFor(item.author)"
                    :to="{ name: 'zakroma-author', params: { authorId: authorIdFor(item.author) } }"
                    class="km-song-author-link"
                    >{{ item.author }}</router-link
                  >
                  <span v-else-if="item.author">{{ item.author }}</span>
                  <span v-if="(item.year && item.year > 0) || item.album"> - </span>
                  <span v-if="item.year && item.year > 0">{{ item.year }}</span>
                  <span v-if="item.year && item.year > 0 && item.album">, </span>
                  <span v-if="item.album">{{ item.album }}</span>
                </template>
              </div>
            </div>
            <button
              class="km-song-play"
              :class="{ 'km-song-play-active': item.songId === currentSongId && isPlaying }"
              :disabled="isPlayDisabled(item)"
              :title="playTitle(item)"
              :aria-label="playTitle(item)"
              @click="onSongPlay(item)"
            >
              ▶
            </button>
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
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useStore } from 'vuex'
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
import AppHeader from '../components/AppHeader.vue'

/**
 * View-страница «Playlist Edit» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'PlaylistEditView',
  components: { draggable, LoginRequired, AppHeader },
  setup() {
    const route = useRoute()
    const readiness = usePlayerReadiness()
    const { isLoggedIn } = useAuth()
    const store = useStore()
    /**
     * Спека 259 (FR-006): плоский список тайлов авторов из Vuex-стора `zakroma`.
     * Используется для резолва `item.author` (имя) → `authorId` (числовой ID) для
     * построения ссылки на «Закрома автора». Никаких новых HTTP-запросов — кэш уже
     * заполняется на роутинге /zakroma/* (см. router/index.js:147).
     */
    const authorTiles = computed(() => store.state.zakroma?.authorTiles || [])
    /**
     * Резолв имени автора в числовой authorId (строка — для path-параметра роута).
     * Возвращает `null` если автор отсутствует в кэше (Edge Case: удалён из БД) —
     * в этом случае имя рендерится как обычный текст без ссылки (FR-006 + Edge Case).
     * @param {string} name имя автора (`item.author`)
     * @returns {string|null} authorId как строка или null
     */
    function authorIdFor(name) {
      if (!name) return null
      const tile = authorTiles.value.find((t) => t && t.author === name)
      return tile && tile.id != null ? String(tile.id) : null
    }

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
      return '�'
    }

    // --- Кнопка ▶ в строке плейлиста (FR-001/FR-002/FR-003/FR-003a/FR-004) -------------------

    // Кнопка ▶ disabled для muted/locked песен (FR-004).
    function isPlayDisabled(item) {
      if (item.muted) return true
      const st = readiness.stateFor(item.songId)
      if (st !== 'active' && st !== 'loading') return true
      return false
    }

    function playTitle(item) {
      if (item.muted) return 'Эта песня пропускается — сначала включите её'
      const st = readiness.stateFor(item.songId)
      if (st !== 'active' && st !== 'loading') return 'Песня недоступна'
      if (item.songId === currentSongId.value && isPlaying.value) return 'Пауза'
      if (item.songId === currentSongId.value && !isPlaying.value) return 'Продолжить'
      return 'Воспроизвести эту песню'
    }

    /**
     * Клик по ▶ в строке плейлиста (T008/T009).
     *
     * Логика:
     * - muted/locked → ничего (кнопка disabled, click не должен ничего делать).
     * - если плеер ещё не запущен (`!started`) → запустить его с этой песней как `first`
     *   через `startPlaylist(item.songId)`. Это создаст iframe и положит `kp_pl_queue` в
     *   sessionStorage — PlayerView сам подхватит и стартанёт первый трек. Альтернативный
     *   postMessage-канал здесь не работает: до `started.value === true` iframe не существует,
     *   `send()` молча игнорирует сообщения (это и был баг «клик по ▶ ничего не делает»
     *   до того, как пользователь нажал «▶ Запустить плейлист»).
     * - клик по ▶ на текущей играющей → toggle pause/resume (FR-003a), без `playSong()` заново.
     * - иначе (плеер запущен, выбрана другая песня) → послать `setqueue` (актуальная очередь
     *   с учётом shuffle/mute), затем `playid` (handler уже есть в PlayerView.vue:139-141
     *   → `playPos(p)`).
     *
     * `pushQueue()` НЕ вызывается синхронно после playid — внутри `playPos()` плеер сам
     * формирует очередь. Drag-drop/mute (T032) шлют `setqueue` отложенно через `setTimeout(50)`,
     * чтобы не перетереть свежую очередь от плеера (race-condition FR-010).
     *
     * @see archive/docs/features/playlist-play-button-and-stems-cancel.md
     */
    function onSongPlay(item) {
      if (isPlayDisabled(item)) return
      if (!started.value) {
        // Плеер ещё не запущен — запускаем с этой песней как first. iframe создаётся реактивно,
        // PlayerView в onMounted читает sessionStorage.getItem('kp_pl_queue') и стартует плеер.
        startPlaylist(item.songId)
        return
      }
      if (item.songId === currentSongId.value && isPlaying.value) {
        // toggle pause/resume — поведение совпадает с основной кнопкой ⏯ плеера (FR-003a)
        send('toggle')
        return
      }
      // Плеер запущен, выбрана другая (или та же на паузе) песня — обновляем очередь и просим
      // плеер перейти на нужный индекс.
      send('setqueue', { ids: playableIds() })
      send('playid', { songId: item.songId })
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
      // Инициализация transient-флагов @error-fallback (T013): сбрасываем при каждой загрузке,
      // чтобы повторная загрузка плейлиста корректно показывала превью снова.
      items.value = (body.items || []).map((it) => ({
        ...it,
        _albumPictureFailed: false,
        _authorPictureFailed: false,
      }))
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

    /**
     * Запустить плейлист. По умолчанию стартует с первой доступной песни (playableIds[0]).
     * Если передан `firstSongIdOverride` — стартует именно с этой песни (FR-002 — запуск с любой).
     *
     * Создаёт iframe с плеером через реактивный `started.value = true`, поэтому postMessage-канал
     * между родителем и плеером открывается именно здесь. До этого момента `send()` молча
     * игнорирует сообщения (`playerIframe.value === null`).
     */
    async function startPlaylist(firstSongIdOverride) {
      const ids = playableIds()
      if (!ids.length) return
      let first
      if (firstSongIdOverride != null && ids.includes(firstSongIdOverride)) {
        first = firstSongIdOverride
      } else if (firstSongIdOverride != null) {
        // Выбранная песня не в playableIds (muted или locked) — fallback на первую доступную.
        first = ids[0]
      } else {
        first = ids[0]
      }
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

    // Отложенный pushQueue для случаев, которые идут **сразу после** клика ▶ (drag-drop, mute,
    // удаление в течение первых ~50 мс после onSongPlay). Прямой pushQueue мог бы перетереть
    // свежую очередь, которую плеер только что поставил в playPos() (FR-010 / Edge Case
    // «Drag-drop сразу после клика ▶»). Задержка достаточна, чтобы playPos() завершил init() и
    // применил свою очередь до нашего `setqueue`. См. spec.md, research.md §D9.
    let pushQueueDeferredTimer = null
    function pushQueueDeferred() {
      clearTimeout(pushQueueDeferredTimer)
      pushQueueDeferredTimer = setTimeout(() => {
        pushQueue()
      }, 50)
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
      // Отложенный push (FR-010): если пользователь сделал drag-drop сразу после клика ▶ —
      // даём плееру время применить свою очередь из playPos() до нашего setqueue.
      pushQueueDeferred()
    }
    function toggleMute(item) {
      item.muted = !item.muted
      setSongMute(id, item.songId, item.muted)
      pushQueueDeferred()
    }
    function removeItem(item) {
      removeSongFromPlaylist(id, item.songId).then(({ status }) => {
        if (status === 200) {
          items.value = items.value.filter((it) => it.songId !== item.songId)
          pushQueueDeferred()
        }
      })
    }

    onMounted(async () => {
      if (!isLoggedIn.value) {
        loading.value = false
        return
      }
      window.addEventListener('message', onMessage)
      // Спека 259 (FR-006): гарантируем наличие кэша `authorTiles` к моменту рендера строк —
      // иначе имя автора в первой отрисовке будет обычным текстом (Edge Case). Дедуп 30 с
      // внутри action не плодит лишних запросов (см. store/modules/zakroma.js:183).
      // back-link из SongView больше отсюда НЕ обслуживается — SongPublicDto.authorId приходит
      // прямо из бэка, см. PublicApiController.song().
      if (authorTiles.value.length === 0) {
        try {
          await store.dispatch('zakroma/loadAuthorTiles', 'main')
        } catch (e) {
          // Тихо: если tiles не загрузились, имя автора просто не станет ссылкой —
          // страница всё равно работает. Не валим основной сценарий.
        }
      }
      await load()
    })
    onBeforeUnmount(() => {
      window.removeEventListener('message', onMessage)
      clearTimeout(saveTimer)
      clearTimeout(pushQueueDeferredTimer)
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
      isPlayDisabled,
      playTitle,
      onSongPlay,
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
      // Спека 259: кликабельные название песни (FR-001) и автор (FR-002).
      authorTiles,
      authorIdFor,
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
  /* Пользователь уточнил 2026-08-14: padding 0 сверху/снизу — самый плотный список плейлиста.
     Горизонтальный padding 0.7rem (~11px) оставлен без изменений. */
  padding: 0 0.7rem;
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
/* Превью альбома/автора (T012): контейнер km-song-pictures — ЧЁРНЫЙ ФОН со скруглением, margin
   5px по краям/сверху/снизу, gap 5px между картинками. Альбом — квадрат 48×48 (cover).
   Автор — горизонтальный, аспект 5:2 (ширина 120px × высота 48px ≈ 5:2.5, пользователь
   уточнил 2026-08-14). Картинки и плейсхолдеры — без своего фона и без скругления (только
   общий div-обёртка чёрный со скруглением). */
.km-song-pictures {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 5px;
  padding: 5px;
  background: #000;
  border-radius: 8px;
  flex-shrink: 0;
}
.km-song-cover,
.km-song-cover-fallback {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.km-song-author,
.km-song-author-fallback {
  /* Аспект 5:2 — ширина в 2.5 раза больше высоты (пользователь уточнил 2026-08-14). */
  width: 120px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
}
.km-song-cover,
.km-song-author {
  object-fit: cover;
}
.km-song-cover-fallback,
.km-song-author-fallback {
  color: #888;
  font-size: 1.3rem;
  background: transparent;
}
/* Кнопка ▶ в строке (T010): прозрачная, hover-эффект, disabled с opacity. Без inline-стилей —
   иначе сломается :hover (нативный tooltip title= остаётся). */
.km-song-play {
  background: transparent;
  border: 1px solid var(--km-border);
  color: var(--km-accent, #0077ff);
  font-size: 1rem;
  line-height: 1;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
}
.km-song-play:hover:not(:disabled) {
  background: var(--km-hover);
  filter: brightness(1.1);
}
.km-song-play-active {
  background: var(--km-accent, #0077ff);
  color: #fff;
  border-color: var(--km-accent, #0077ff);
}
.km-song-play:disabled {
  opacity: 0.4;
  cursor: not-allowed;
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
/* Спека 259 (FR-001, FR-002, FR-007, FR-011): кликабельные название песни и автор
   в строке плейлиста. display:block — чтобы overflow-обрезка текста работала
   в <router-link> (он рендерит <a>, у которого по умолчанию display:inline).
   Цвет — из существующей CSS-переменной (--km-accent), работает в обоих дизайнах
   (classic/modern). Underline только по hover/focus — не агрессивный
   визуальный шум в покое. */
.km-song-title-link,
.km-song-author-link {
  display: block;
  color: var(--km-accent, #0077ff);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.km-song-title-link {
  font-size: 0.92rem;
  font-weight: 600;
}
.km-song-author-link {
  font-size: 0.76rem;
}
.km-song-title-link:hover,
.km-song-title-link:focus-visible,
.km-song-author-link:hover,
.km-song-author-link:focus-visible {
  text-decoration: underline;
  outline: none;
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
