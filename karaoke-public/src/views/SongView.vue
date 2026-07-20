<template>
  <div class="km-page">
    <!-- Хедер -->
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/zakroma" class="km-back">← Назад</RouterLink>
          <a href="/"><img src="/KARAOKE_LOGO.png" class="km-logo" alt="Karaoke logo" /></a>
        </div>
        <div class="km-header-right">
          <AuthStatusWidget />
          <div class="km-theme-toggle">
            <button
              :class="['km-tb', theme === 'light' ? 'active' : '']"
              @click="setTheme('light')"
              title="Светлая"
            >
              ☀
            </button>
            <button
              :class="['km-tb', theme === 'system' ? 'active' : '']"
              @click="setTheme('system')"
              title="Авто"
            >
              ⬡
            </button>
            <button
              :class="['km-tb', theme === 'dark' ? 'active' : '']"
              @click="setTheme('dark')"
              title="Тёмная"
            >
              🌙
            </button>
          </div>
        </div>
      </div>
    </header>

    <!-- Загрузка -->
    <div v-if="currentSongIsLoading" class="km-loading">Загрузка...</div>

    <!-- Удалено -->
    <div v-else-if="currentSong && currentSong.contentRemoved" class="km-removed-wrapper">
      <div class="km-removed-card">
        <div class="km-removed-icon">🔒</div>
        <div class="km-removed-title">Информация о произведении удалена</div>
        <div class="km-removed-subtitle">по требованию правообладателя</div>
        <hr class="km-removed-divider" />
        <div class="km-removed-hint">
          Страница недоступна в соответствии с обращением<br />
          об авторских правах. Если вы считаете, что это<br />
          произошло по ошибке — свяжитесь с нами.
        </div>
        <RouterLink to="/" class="km-btn-home">← На главную</RouterLink>
      </div>
    </div>

    <!-- Страница песни -->
    <div v-else-if="currentSong" class="km-song">
      <!-- Hero-баннер -->
      <div class="km-hero">
        <img
          v-if="currentSong.songPictureUrl"
          :src="currentSong.songPictureUrl"
          class="km-hero-banner"
          @error="$event.target.style.display = 'none'"
          alt=""
        />
        <div class="km-hero-overlay">
          <h1 class="km-song-title">«{{ currentSong.songName }}»</h1>
          <p class="km-song-author">{{ currentSong.author }}</p>
        </div>
      </div>

      <div class="km-content">
        <!-- Метаданные -->
        <div class="km-meta-card">
          <div class="km-meta-grid">
            <div class="km-meta-item">
              <span class="km-meta-label">Исполнитель</span>
              <span class="km-meta-value" @click="onMetaClick('author', $event)">{{
                currentSong.author
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Год</span>
              <span class="km-meta-value" @click="onMetaClick('year', $event)">{{
                currentSong.year
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Альбом</span>
              <span class="km-meta-value" @click="onMetaClick('album', $event)">{{
                currentSong.album
              }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Трек</span>
              <span class="km-meta-value">{{ currentSong.track }}</span>
            </div>
            <div v-if="currentSong.key" class="km-meta-item">
              <span class="km-meta-label">Тональность</span>
              <span class="km-meta-value" @click="onMetaClick('key', $event)">{{
                currentSong.key
              }}</span>
            </div>
            <div v-if="currentSong.bpm" class="km-meta-item">
              <span class="km-meta-label">Темп (уд/м)</span>
              <span class="km-meta-value">{{ currentSong.bpm }}</span>
            </div>
            <div class="km-meta-actions">
              <FavoriteIcon :song-id="currentSong.id" label="В избранное" />
              <PlaylistIcon :song-id="currentSong.id" label="В плейлист" />
            </div>
          </div>
        </div>

        <!-- Онлайн-плеер: между блоком информации о песне и "Ссылки на просмотр". В демо-режиме
             (playerIsDemo) это тот же iframe — сам плеер получит demo-токен и обрежется до
             фрагмента "до конца первого куплета" (см. PublicPlayerController.access/KaraokePlayer.js) -->
        <div
          v-if="playerCanWatch || playerIsDemo"
          class="km-player-card"
          :class="{ 'km-player-page-mode': playerDisplayMode === 'page' }"
        >
          <div class="km-player-label">
            🎤 Онлайн-плеер караоке<span v-if="playerIsDemo" class="km-player-demo-badge"
              >ДЕМО</span
            >
          </div>
          <div class="km-video-wrap km-player-wrap">
            <iframe
              ref="playerIframe"
              :src="`/player/${currentSong.id}`"
              allow="autoplay; fullscreen"
              frameborder="0"
              allowfullscreen
            />
          </div>
        </div>

        <!-- Демо-режим: контент есть, но полного доступа нет — вместо карточки ожидания (та
             рассчитана на "ещё не готово") сразу предлагаем подписку рядом с самим демо-плеером. -->
        <div v-if="!currentSong.onAir && playerIsDemo" class="km-waiting-card">
          <div class="km-waiting-title">Это демо-фрагмент</div>
          <div class="km-waiting-body">
            В демо-режиме доступен только небольшой фрагмент песни. Оформите подписку, чтобы слушать
            песню целиком.
          </div>

          <div v-if="!playerIsPremiumUser" class="km-waiting-offer">
            <div class="km-waiting-offer-icon">🪙</div>
            <div class="km-waiting-offer-title">Премиум-подписка</div>
            <div class="km-waiting-offer-desc">Подписка на всю коллекцию или на одну песню</div>
            <div class="km-waiting-offer-actions">
              <RouterLink to="/premium" class="km-waiting-cta"
                >Оформить премиум-подписку →</RouterLink
              >
              <button
                v-if="isLoggedIn && canOfferSongSubscription"
                class="km-waiting-cta km-song-sub-cta"
                @click="songSubscriptionModalVisible = true"
              >
                Оформить подписку на эту песню →
              </button>
            </div>
          </div>

          <div v-if="!isLoggedIn" class="km-waiting-login">
            Также вы можете <RouterLink to="/register">зарегистрироваться</RouterLink> или
            <RouterLink to="/login">войти</RouterLink> на сайте — это понадобится для оформления
            подписки.
          </div>
        </div>

        <!-- Ссылки на платформы: только для песен в эфире — иначе показывать нечего (нет ни ссылок,
             ни смысла дублировать дату/статус, который уже есть в блоке ожидания/подписки ниже). -->
        <div v-if="currentSong.onAir" class="km-links-card">
          <div class="km-links-title">Ссылки на просмотр</div>
          <div class="km-links-grid">
            <!-- Sponsr (все версии) -->
            <div class="km-link-group">
              <span class="km-link-label">Все</span>
              <div class="km-link-icons">
                <PlatformLink
                  link-name="sponsr"
                  :link-value="currentSong.linkSponsrPlay"
                  :song-id="currentSong.id"
                  song-version="all"
                />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Karaoke</span>
              <div class="km-link-icons">
                <PlatformLink
                  link-name="dzen"
                  :link-value="currentSong.linkDzenKaraoke"
                  :song-id="currentSong.id"
                  song-version="karaoke"
                />
                <PlatformLink
                  link-name="max"
                  :link-value="currentSong.linkMaxKaraoke"
                  :song-id="currentSong.id"
                  song-version="karaoke"
                />
                <PlatformLink
                  link-name="vk"
                  :link-value="currentSong.linkVkKaraoke"
                  :song-id="currentSong.id"
                  song-version="karaoke"
                />
                <PlatformLink
                  link-name="tg"
                  :link-value="currentSong.linkTgKaraoke"
                  :song-id="currentSong.id"
                  song-version="karaoke"
                />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Lyrics</span>
              <div class="km-link-icons">
                <PlatformLink
                  link-name="dzen"
                  :link-value="currentSong.linkDzenLyrics"
                  :song-id="currentSong.id"
                  song-version="lyrics"
                />
                <PlatformLink
                  link-name="max"
                  :link-value="currentSong.linkMaxLyrics"
                  :song-id="currentSong.id"
                  song-version="lyrics"
                />
                <PlatformLink
                  link-name="vk"
                  :link-value="currentSong.linkVkLyrics"
                  :song-id="currentSong.id"
                  song-version="lyrics"
                />
                <PlatformLink
                  link-name="tg"
                  :link-value="currentSong.linkTgLyrics"
                  :song-id="currentSong.id"
                  song-version="lyrics"
                />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">TABS</span>
              <div class="km-link-icons">
                <PlatformLink
                  link-name="dzen"
                  :link-value="currentSong.linkDzenTabs"
                  :song-id="currentSong.id"
                  song-version="tabs"
                />
                <PlatformLink
                  link-name="max"
                  :link-value="currentSong.linkMaxTabs"
                  :song-id="currentSong.id"
                  song-version="tabs"
                />
                <PlatformLink
                  link-name="vk"
                  :link-value="currentSong.linkVkTabs"
                  :song-id="currentSong.id"
                  song-version="tabs"
                />
                <PlatformLink
                  link-name="tg"
                  :link-value="currentSong.linkTgTabs"
                  :song-id="currentSong.id"
                  song-version="tabs"
                />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Chords</span>
              <div class="km-link-icons">
                <PlatformLink
                  link-name="dzen"
                  :link-value="currentSong.linkDzenChords"
                  :song-id="currentSong.id"
                  song-version="chords"
                />
                <PlatformLink
                  link-name="max"
                  :link-value="currentSong.linkMaxChords"
                  :song-id="currentSong.id"
                  song-version="chords"
                />
                <PlatformLink
                  link-name="vk"
                  :link-value="currentSong.linkVkChords"
                  :song-id="currentSong.id"
                  song-version="chords"
                />
                <PlatformLink
                  link-name="tg"
                  :link-value="currentSong.linkTgChords"
                  :song-id="currentSong.id"
                  song-version="chords"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Видео ВК — старое место, только когда онлайн-плеер сам не может отобразиться -->
        <div v-if="currentSong.onAir && !playerCanWatch && playerAccessLoaded" class="km-videos">
          <div v-if="currentSong.idVkKaraoke" class="km-video-block" @click="onPlay('karaoke')">
            <div class="km-video-label">Karaoke</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkKaraokeOID}&id=${currentSong.idVkKaraokeID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkLyrics" class="km-video-block" @click="onPlay('lyrics')">
            <div class="km-video-label">Lyrics</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkLyricsOID}&id=${currentSong.idVkLyricsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkMelody" class="km-video-block" @click="onPlay('tabs')">
            <div class="km-video-label">TABS</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkMelodyOID}&id=${currentSong.idVkMelodyID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkChords" class="km-video-block" @click="onPlay('chords')">
            <div class="km-video-label">Chords</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkChordsOID}&id=${currentSong.idVkChordsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
                frameborder="0"
                allowfullscreen
              />
            </div>
          </div>
        </div>

        <!-- Не в эфире (или эксклюзив/не готово) и плеер недоступен даже в демо-режиме — сообщение
             об ожидании/подписке. Тоже на старом месте видео-блока. Когда демо доступен
             (playerIsDemo) — своя отдельная карточка сразу под демо-плеером, см. выше. -->
        <div
          v-if="!currentSong.onAir && !playerCanWatch && !playerIsDemo && playerAccessLoaded"
          class="km-waiting-card"
        >
          <div class="km-waiting-title">{{ waitingTitle }}</div>
          <div class="km-waiting-body">{{ waitingBody }}</div>

          <div v-if="!playerIsPremiumUser" class="km-waiting-offer">
            <div class="km-waiting-offer-icon">🪙</div>
            <div class="km-waiting-offer-title">Премиум-подписка</div>
            <div class="km-waiting-offer-desc">Подписка на всю коллекцию или на одну песню</div>
            <div class="km-waiting-offer-actions">
              <RouterLink to="/premium" class="km-waiting-cta"
                >Оформить премиум-подписку →</RouterLink
              >
              <button
                v-if="isLoggedIn && canOfferSongSubscription"
                class="km-waiting-cta km-song-sub-cta"
                @click="songSubscriptionModalVisible = true"
              >
                Оформить подписку на эту песню →
              </button>
            </div>
          </div>

          <div v-if="!isLoggedIn" class="km-waiting-login">
            Также вы можете <RouterLink to="/register">зарегистрироваться</RouterLink> или
            <RouterLink to="/login">войти</RouterLink> на сайте — это понадобится для оформления
            подписки.
          </div>
          <div v-else-if="playerIsPremiumUser" class="km-waiting-login">
            Вы премиум-пользователь — как только материалы для плеера будут готовы, он появится
            здесь автоматически.
          </div>
        </div>

        <SongSubscriptionModal
          :visible="songSubscriptionModalVisible"
          :song-id="currentSong && currentSong.id"
          :song-name="currentSong ? `${currentSong.songName} — ${currentSong.author}` : ''"
          @close="songSubscriptionModalVisible = false"
          @activated="onSongSubscriptionActivated"
        />

        <!-- Текст / Табы / Аккорды -->
        <div v-if="currentSong.formattedTextSong" class="km-text-card">
          <div class="km-text-header">Текст песни</div>
          <div class="km-text-body" v-html="currentSong.formattedTextSong" />
        </div>
        <div v-if="currentSong.formattedTextTabs" class="km-text-card">
          <div class="km-text-header">Табулатура</div>
          <div class="km-text-body" v-html="currentSong.formattedTextTabs" />
        </div>
        <div v-if="currentSong.formattedTextChords" class="km-text-card">
          <div class="km-text-header">Аккорды</div>
          <div class="km-text-body" v-html="currentSong.formattedTextChords" />
        </div>
      </div>
    </div>

    <p v-else class="km-not-found">Песня не найдена.</p>
  </div>
</template>

<script>
import { useRoute } from 'vue-router'
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../components/PlatformLink.vue'
import AuthStatusWidget from '../components/AuthStatusWidget.vue'
import FavoriteIcon from '../components/FavoriteIcon.vue'
import PlaylistIcon from '../components/PlaylistIcon.vue'
import { useDesign } from '../composables/useDesign'
import { useEngagementTracking } from '../composables/useEngagementTracking'
import { useAuth } from '../composables/useAuth'
import { usePlayerAccess } from '../composables/usePlayerAccess'
import { usePlaylistMembership } from '../composables/usePlaylistMembership'
import { trackPlay, trackMetaClick } from '../services/tracking'
import { pluralDays } from '../utils/pluralRu'
import SongSubscriptionModal from '../components/SongSubscriptionModal.vue'
import { useCart } from '../composables/useCart'

export default {
  name: 'SongView',
  components: { PlatformLink, AuthStatusWidget, SongSubscriptionModal, FavoriteIcon, PlaylistIcon },
  setup() {
    const route = useRoute()
    useEngagementTracking('song', () => route.query.id)
    const { theme, applyTheme } = useDesign()
    function setTheme(val) {
      theme.value = val
      applyTheme(val)
    }
    const { isLoggedIn } = useAuth()
    const playerAccess = usePlayerAccess()
    const cart = useCart()
    const playlistMembership = usePlaylistMembership()
    return { theme, setTheme, isLoggedIn, playerAccess, cart, playlistMembership }
  },
  computed: {
    ...mapGetters('songs', ['currentSong', 'currentSongIsLoading']),
    playerCanWatch() {
      return this.playerAccess.canWatch.value
    },
    playerAccessLoaded() {
      return this.playerAccess.loaded.value
    },
    playerIsPremiumUser() {
      return this.playerAccess.isPremiumUser.value
    },
    playerIsDemo() {
      return this.playerAccess.isDemo.value
    },
    daysUntilAir() {
      const ts = this.currentSong?.airTimestamp
      if (!ts) return null
      return Math.ceil((ts - Date.now()) / 86400000)
    },
    waitingTitle() {
      const s = this.currentSong
      if (!s) return ''
      if (s.exclusive) return 'Эта песня доступна только по подписке'
      if (this.daysUntilAir === null) return 'Дата выхода в эфир пока не определена'
      if (this.daysUntilAir <= 0) return 'Песня скоро появится в эфире'
      return `Песня выйдет в эфир через ${this.daysUntilAir} ${pluralDays(this.daysUntilAir)}`
    },
    waitingBody() {
      const s = this.currentSong
      if (!s) return ''
      if (s.exclusive) {
        return 'В бесплатном эфире она доступна не будет — посмотреть её можно, оформив подписку.'
      }
      return 'Не хотите ждать эфир? Оформите подписку — и песня станет доступна сразу.'
    },
    // Отдельная подписка на песню (см. план монетизации) — предлагаем, только если админ пометил
    // песню как продающуюся (idTariff>0 на бэкенде -> songSubscriptionAvailable) и плеер сейчас
    // всё равно недоступен обычными путями (иначе кнопка была бы бессмысленна).
    canOfferSongSubscription() {
      return !!(
        this.currentSong &&
        this.currentSong.songSubscriptionAvailable &&
        !this.playerCanWatch &&
        this.playerAccessLoaded
      )
    },
  },
  data() {
    return { playerDisplayMode: 'embed', songSubscriptionModalVisible: false }
  },
  watch: {
    '$route.query.id': {
      immediate: true,
      handler(id) {
        if (id) this.loadSong(id)
      },
    },
    currentSong: {
      handler(song) {
        if (song) document.title = `${song.songName} — ${song.author}`
        document.body.style.background = song?.contentRemoved ? 'var(--km-bg)' : ''
        if (song?.id) this.playerAccess.checkAccess(song.id)
        if (song?.id) this.playlistMembership.load([song.id])
        this.playerDisplayMode = 'embed'
      },
    },
  },
  mounted() {
    window.addEventListener('message', this.onPlayerMessage)
  },
  beforeUnmount() {
    document.body.style.background = ''
    window.removeEventListener('message', this.onPlayerMessage)
  },
  methods: {
    ...mapActions('songs', ['loadSong']),
    // Player card starts embedded in the page; the player itself (running same-origin inside the
    // iframe) posts here when its "Широкий" button is toggled, asking us to resize the iframe's own
    // box between the small embedded card and a full-viewport overlay. Sourced-checked against our
    // own iframe's contentWindow so unrelated postMessage traffic (browser extensions etc.) is ignored.
    onPlayerMessage(event) {
      if (
        !event.data ||
        event.data.source !== 'karaoke-player' ||
        event.data.type !== 'display-mode'
      )
        return
      const iframe = this.$refs.playerIframe
      if (iframe && event.source !== iframe.contentWindow) return
      this.playerDisplayMode = event.data.mode
    },
    onPlay(version) {
      trackPlay(this.currentSong.id, version)
    },
    async onMetaClick(field, event) {
      const resp = await trackMetaClick(field, this.currentSong.id, event)
      if (resp && resp.meta) {
        sessionStorage.setItem(`kp_token_${this.currentSong.id}`, resp.meta)
        // New tab, not router.push: the player needs the full viewport (position:fixed inside it
        // isn't enough — it still inherits App.vue's .modernScreen wrapper otherwise) and a fresh
        // tab keeps the song page as-is behind it. sessionStorage is cloned into same-origin tabs
        // opened this way, so the token set just above is already there when it loads.
        window.open(`/player/${this.currentSong.id}`, '_blank')
      }
    },
    // Акция довела цену подписки на песню до нуля — доступ уже проставлен на бэкенде синхронно,
    // просто перезапрашиваем access(), чтобы плеер встроился без перезагрузки страницы.
    onSongSubscriptionActivated() {
      const id = this.currentSong?.id
      if (id) this.playerAccess.checkAccess(id)
      if (id && this.cart.isInCart(id)) this.cart.toggle(id)
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}

/* Хедер */
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
  position: sticky;
  top: 0;
  z-index: 100;
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
.km-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.km-theme-toggle {
  display: flex;
  border: 1px solid var(--km-border);
  border-radius: 20px;
  overflow: hidden;
}
.km-tb {
  background: transparent;
  color: var(--km-text2);
  border: none;
  padding: 0.2rem 0.55rem;
  font-size: 0.95rem;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.km-tb:hover {
  background: var(--km-hover);
  color: var(--km-text);
}
.km-tb.active {
  background: var(--km-accent);
  color: #fff;
}

/* Loading / not found */
.km-loading,
.km-not-found {
  text-align: center;
  color: var(--km-text2);
  padding: 4rem 1rem;
}

/* Страница «удалено» */
.km-removed-wrapper {
  min-height: calc(100vh - 60px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}
.km-removed-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 16px;
  padding: 3rem 3.5rem;
  max-width: 540px;
  width: 100%;
  text-align: center;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.4);
}
.km-removed-icon {
  font-size: 4rem;
  margin-bottom: 1.25rem;
  display: block;
  line-height: 1;
}
.km-removed-title {
  color: var(--km-text);
  font-size: 1.4rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}
.km-removed-subtitle {
  color: var(--km-text2);
  font-size: 1rem;
  margin-bottom: 1.5rem;
}
.km-removed-divider {
  border: none;
  border-top: 1px solid var(--km-border);
  margin: 1.25rem 0;
}
.km-removed-hint {
  color: var(--km-text2);
  font-size: 0.82rem;
  margin-bottom: 2rem;
  line-height: 1.6;
}
.km-btn-home {
  background: var(--km-bg2);
  border: 1px solid var(--km-border);
  color: var(--km-text);
  border-radius: 8px;
  padding: 0.55rem 1.8rem;
  font-size: 0.95rem;
  text-decoration: none;
  display: inline-block;
  transition: background 0.2s;
}
.km-btn-home:hover {
  background: var(--km-hover);
  color: var(--km-text);
  text-decoration: none;
}

/* Song content */
.km-song {
}

/* Hero */
.km-hero {
  position: relative;
  background: #000;
  max-height: 320px;
  overflow: hidden;
}
.km-hero-banner {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  opacity: 0.55;
  display: block;
}
.km-hero-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 1.5rem 1.5rem 1.2rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.85) 0%, transparent 100%);
}
.km-song-title {
  font-size: clamp(1.4rem, 4vw, 2.4rem);
  font-weight: 700;
  color: var(--km-accent2);
  margin: 0 0 0.25rem;
  line-height: 1.2;
}
.km-song-author {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

/* Content area */
.km-content {
  max-width: 900px;
  margin: 0 auto;
  padding: 1.25rem 1rem 3rem;
}

/* Метаданные */
.km-meta-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.km-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 0.75rem;
}
.km-meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}
.km-meta-label {
  font-size: 0.7rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
}
.km-meta-value {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--km-text);
}

/* Избранное / плейлисты — в той же сетке карточки метаданных, без отдельного блока */
.km-meta-actions {
  grid-column: span 2;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  align-self: end;
  gap: 0.6rem;
}
.km-meta-actions :deep(.fav-icon),
.km-meta-actions :deep(.pl-icon) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0.4rem 0.85rem;
  background: var(--km-bg);
  border: 1px solid var(--km-border);
  border-radius: 999px;
  color: var(--km-text);
  font-size: 0.82rem;
  font-weight: 600;
  text-decoration: none;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.km-meta-actions :deep(.fav-icon:hover),
.km-meta-actions :deep(.pl-icon:hover) {
  border-color: var(--km-accent);
}
.km-meta-actions :deep(.fav-icon.fav-on) {
  color: #e11d2a;
  border-color: #e11d2a;
}
.km-meta-actions :deep(.pl-icon.pl-on) {
  color: #0077ff;
  border-color: #0077ff;
}

/* Ссылки */
.km-links-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.km-links-title {
  font-size: 0.8rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: 0.75rem;
}
.km-links-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}
.km-link-group {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.km-link-label {
  font-size: 0.75rem;
  color: var(--km-text2);
  min-width: 40px;
  font-weight: 600;
}
.km-link-icons {
  display: flex;
  gap: 2px;
}

/* Онлайн-плеер, встроенный вместо видео ВК */
.km-player-card {
  margin-bottom: 1rem;
  border: 1px solid var(--km-accent);
  border-radius: 12px;
  padding: 0.75rem;
  background: var(--km-card);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--km-accent) 15%, transparent);
}
.km-player-label {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--km-accent2);
  margin-bottom: 0.5rem;
  letter-spacing: 0.02em;
}
.km-player-demo-badge {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.1rem 0.4rem;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #fff;
  background: #f80;
  border-radius: 4px;
  vertical-align: middle;
}
.km-player-wrap {
  border-radius: 8px;
}

/* "Широкий" режим — плеер (внутри iframe) сам попросил родительскую страницу растянуть его на весь
   вьюпорт вместо маленькой карточки. position:fixed игнорирует max-width родительских .km-content
   (тот не создаёт containing block), так что этого достаточно, без переноса в другое место DOM. */
.km-player-card.km-player-page-mode {
  position: fixed;
  inset: 0;
  z-index: 2000;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 0;
  box-shadow: none;
  background: #000;
}
.km-player-card.km-player-page-mode .km-player-label {
  display: none;
}
.km-player-card.km-player-page-mode .km-video-wrap {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  aspect-ratio: unset;
  border-radius: 0;
}

/* Ожидание / предложение подписки */
.km-waiting-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1rem;
  text-align: center;
}
.km-waiting-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--km-text);
  margin-bottom: 0.5rem;
}
.km-waiting-body {
  color: var(--km-text2);
  font-size: 0.95rem;
  margin-bottom: 1rem;
  line-height: 1.5;
}
.km-waiting-offer {
  background: var(--km-bg2);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1.25rem 1rem;
  margin-bottom: 0.75rem;
}
.km-waiting-offer-icon {
  font-size: 2rem;
  margin-bottom: 0.35rem;
  line-height: 1;
}
.km-waiting-offer-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--km-text);
  margin-bottom: 0.2rem;
}
.km-waiting-offer-desc {
  font-size: 0.85rem;
  color: var(--km-text2);
  margin-bottom: 0.9rem;
}
.km-waiting-offer-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.6rem;
}
.km-waiting-cta {
  display: inline-block;
  background: var(--km-accent);
  color: #fff;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font-weight: 600;
  text-decoration: none;
  border: none;
  cursor: pointer;
  font-family: inherit;
  font-size: 0.9rem;
}
.km-waiting-cta:hover {
  opacity: 0.9;
  color: #fff;
  text-decoration: none;
}
.km-song-sub-cta {
  background: transparent;
  color: var(--km-accent);
  border: 1px solid var(--km-accent);
}
.km-song-sub-cta:hover {
  background: var(--km-hover);
  color: var(--km-accent);
  opacity: 1;
}
.km-waiting-login {
  font-size: 0.82rem;
  color: var(--km-text2);
  margin-top: 0.5rem;
}
.km-waiting-login a {
  color: var(--km-accent2);
}

/* Видео */
.km-videos {
  margin-bottom: 1rem;
}
.km-video-block {
  margin-bottom: 1.25rem;
}
.km-video-label {
  font-size: 0.78rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: 0.4rem;
}
.km-video-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  overflow: hidden;
  background: #000;
}
.km-video-wrap iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* Текст песни */
.km-text-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  margin-bottom: 1rem;
}
.km-text-header {
  font-size: 0.8rem;
  color: var(--km-text2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: 600;
  margin-bottom: 0.75rem;
}
.km-text-body {
  font-size: 1rem;
  line-height: 1.7;
  color: var(--km-text);
  white-space: pre-wrap;
  font-family: monospace;
}
.km-text-body :deep(*) {
  color: var(--km-text) !important;
}

/* Мобильные правки */
@media (max-width: 600px) {
  .km-hero {
    max-height: 220px;
  }
  .km-hero-banner {
    max-height: 220px;
  }
  .km-removed-card {
    padding: 2rem 1.5rem;
  }
  .km-links-grid {
    gap: 0.5rem;
  }
}
</style>
