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
            <button :class="['km-tb', theme === 'light' ? 'active' : '']" @click="setTheme('light')" title="Светлая">☀</button>
            <button :class="['km-tb', theme === 'system' ? 'active' : '']" @click="setTheme('system')" title="Авто">⬡</button>
            <button :class="['km-tb', theme === 'dark' ? 'active' : '']" @click="setTheme('dark')" title="Тёмная">🌙</button>
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
        <hr class="km-removed-divider">
        <div class="km-removed-hint">
          Страница недоступна в соответствии с обращением<br>
          об авторских правах. Если вы считаете, что это<br>
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
          @error="$event.target.style.display='none'"
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
              <span class="km-meta-value" @click="onMetaClick('author', $event)">{{ currentSong.author }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Год</span>
              <span class="km-meta-value" @click="onMetaClick('year', $event)">{{ currentSong.year }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Альбом</span>
              <span class="km-meta-value" @click="onMetaClick('album', $event)">{{ currentSong.album }}</span>
            </div>
            <div class="km-meta-item">
              <span class="km-meta-label">Трек</span>
              <span class="km-meta-value">{{ currentSong.track }}</span>
            </div>
            <div v-if="currentSong.key" class="km-meta-item">
              <span class="km-meta-label">Тональность</span>
              <span class="km-meta-value" @click="onMetaClick('key', $event)">{{ currentSong.key }}</span>
            </div>
            <div v-if="currentSong.bpm" class="km-meta-item">
              <span class="km-meta-label">Темп (уд/м)</span>
              <span class="km-meta-value">{{ currentSong.bpm }}</span>
            </div>
          </div>
        </div>

        <!-- Онлайн-плеер: между блоком информации о песне и "Ссылки на просмотр" -->
        <div v-if="playerCanWatch" class="km-player-card" :class="{ 'km-player-page-mode': playerDisplayMode === 'page' }">
          <div class="km-player-label">🎤 Онлайн-плеер караоке</div>
          <div class="km-video-wrap km-player-wrap">
            <iframe ref="playerIframe" :src="`/player/${currentSong.id}`" allow="autoplay; fullscreen" frameborder="0" allowfullscreen />
          </div>
        </div>

        <!-- Ссылки на платформы -->
        <div class="km-links-card">
          <div class="km-links-title">Ссылки на просмотр</div>
          <div v-if="currentSong.onAir" class="km-links-grid">
            <!-- Sponsr (все версии) -->
            <div class="km-link-group">
              <span class="km-link-label">Все</span>
              <div class="km-link-icons">
                <PlatformLink link-name="sponsr" :link-value="currentSong.linkSponsrPlay" :song-id="currentSong.id" song-version="all" />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Karaoke</span>
              <div class="km-link-icons">
                <PlatformLink link-name="dzen" :link-value="currentSong.linkDzenKaraoke" :song-id="currentSong.id" song-version="karaoke" />
                <PlatformLink link-name="max"  :link-value="currentSong.linkMaxKaraoke"  :song-id="currentSong.id" song-version="karaoke" />
                <PlatformLink link-name="vk"   :link-value="currentSong.linkVkKaraoke"   :song-id="currentSong.id" song-version="karaoke" />
                <PlatformLink link-name="tg"   :link-value="currentSong.linkTgKaraoke"   :song-id="currentSong.id" song-version="karaoke" />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Lyrics</span>
              <div class="km-link-icons">
                <PlatformLink link-name="dzen" :link-value="currentSong.linkDzenLyrics"  :song-id="currentSong.id" song-version="lyrics" />
                <PlatformLink link-name="max"  :link-value="currentSong.linkMaxLyrics"   :song-id="currentSong.id" song-version="lyrics" />
                <PlatformLink link-name="vk"   :link-value="currentSong.linkVkLyrics"    :song-id="currentSong.id" song-version="lyrics" />
                <PlatformLink link-name="tg"   :link-value="currentSong.linkTgLyrics"    :song-id="currentSong.id" song-version="lyrics" />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">TABS</span>
              <div class="km-link-icons">
                <PlatformLink link-name="dzen" :link-value="currentSong.linkDzenTabs"    :song-id="currentSong.id" song-version="tabs" />
                <PlatformLink link-name="max"  :link-value="currentSong.linkMaxTabs"     :song-id="currentSong.id" song-version="tabs" />
                <PlatformLink link-name="vk"   :link-value="currentSong.linkVkTabs"      :song-id="currentSong.id" song-version="tabs" />
                <PlatformLink link-name="tg"   :link-value="currentSong.linkTgTabs"      :song-id="currentSong.id" song-version="tabs" />
              </div>
            </div>
            <div class="km-link-group">
              <span class="km-link-label">Chords</span>
              <div class="km-link-icons">
                <PlatformLink link-name="dzen" :link-value="currentSong.linkDzenChords"  :song-id="currentSong.id" song-version="chords" />
                <PlatformLink link-name="max"  :link-value="currentSong.linkMaxChords"   :song-id="currentSong.id" song-version="chords" />
                <PlatformLink link-name="vk"   :link-value="currentSong.linkVkChords"    :song-id="currentSong.id" song-version="chords" />
                <PlatformLink link-name="tg"   :link-value="currentSong.linkTgChords"    :song-id="currentSong.id" song-version="chords" />
              </div>
            </div>
          </div>
          <div v-else class="km-date-publish">{{ currentSong.datePublish }}</div>
        </div>

        <!-- Видео ВК — старое место, только когда онлайн-плеер сам не может отобразиться -->
        <div v-if="currentSong.onAir && !playerCanWatch && playerAccessLoaded" class="km-videos">
          <div v-if="currentSong.idVkKaraoke" class="km-video-block" @click="onPlay('karaoke')">
            <div class="km-video-label">Karaoke</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkKaraokeOID}&id=${currentSong.idVkKaraokeID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                frameborder="0" allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkLyrics" class="km-video-block" @click="onPlay('lyrics')">
            <div class="km-video-label">Lyrics</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkLyricsOID}&id=${currentSong.idVkLyricsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                frameborder="0" allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkMelody" class="km-video-block" @click="onPlay('tabs')">
            <div class="km-video-label">TABS</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkMelodyOID}&id=${currentSong.idVkMelodyID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                frameborder="0" allowfullscreen
              />
            </div>
          </div>
          <div v-if="currentSong.idVkChords" class="km-video-block" @click="onPlay('chords')">
            <div class="km-video-label">Chords</div>
            <div class="km-video-wrap">
              <iframe
                :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkChordsOID}&id=${currentSong.idVkChordsID}`"
                allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                frameborder="0" allowfullscreen
              />
            </div>
          </div>
        </div>

        <!-- Не в эфире (или эксклюзив/не готово) и плеер недоступен — сообщение об ожидании/подписке.
             Тоже на старом месте видео-блока. -->
        <div v-if="!currentSong.onAir && !playerCanWatch && playerAccessLoaded" class="km-waiting-card">
          <div class="km-waiting-title">{{ waitingTitle }}</div>
          <div class="km-waiting-body">{{ waitingBody }}</div>
          <a :href="sponsrUrl" target="_blank" rel="noopener" class="km-waiting-cta">Оформить подписку на Sponsr →</a>
          <div class="km-waiting-fineprint">
            После оформления и оплаты подписки на Sponsr доступность песни на сайте появится через некоторое время.
          </div>
          <div v-if="!isLoggedIn" class="km-waiting-login">
            Также вы можете <RouterLink to="/register">зарегистрироваться</RouterLink> или
            <RouterLink to="/login">войти</RouterLink> на сайте — это понадобится, чтобы мы могли
            узнать вас как премиум-подписчика.
          </div>
          <div v-else-if="playerIsPremiumUser" class="km-waiting-login">
            Вы премиум-пользователь — как только материалы для плеера будут готовы, он появится здесь автоматически.
          </div>
        </div>

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
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'
import AuthStatusWidget from '../../components/AuthStatusWidget.vue'
import { useDesign } from '../../composables/useDesign'
import { useAuth } from '../../composables/useAuth'
import { usePlayerAccess } from '../../composables/usePlayerAccess'
import { trackPlay, trackMetaClick } from '../../services/tracking'
import { pluralDays } from '../../utils/pluralRu'

export default {
  name: 'SongModern',
  components: { PlatformLink, AuthStatusWidget },
  setup() {
    const { theme, applyTheme } = useDesign()
    function setTheme(val) { theme.value = val; applyTheme(val) }
    const { isLoggedIn } = useAuth()
    const playerAccess = usePlayerAccess()
    return { theme, setTheme, isLoggedIn, playerAccess }
  },
  computed: {
    ...mapGetters('songs', ['currentSong', 'currentSongIsLoading']),
    playerCanWatch() { return this.playerAccess.canWatch.value },
    playerAccessLoaded() { return this.playerAccess.loaded.value },
    playerIsPremiumUser() { return this.playerAccess.isPremiumUser.value },
    daysUntilAir() {
      const ts = this.currentSong?.airTimestamp
      if (!ts) return null
      return Math.ceil((ts - Date.now()) / 86400000)
    },
    sponsrUrl() {
      const s = this.currentSong
      return (s && (s.linkSponsrPlay || s.sponsrLinkGeneral)) || 'https://sponsr.ru/smkaraoke'
    },
    waitingTitle() {
      const s = this.currentSong
      if (!s) return ''
      if (s.exclusive) return 'Эта песня — эксклюзив на Sponsr'
      if (this.daysUntilAir === null) return 'Дата выхода в эфир пока не определена'
      if (this.daysUntilAir <= 0) return 'Песня скоро появится в эфире'
      return `Песня выйдет в эфир через ${this.daysUntilAir} ${pluralDays(this.daysUntilAir)}`
    },
    waitingBody() {
      const s = this.currentSong
      if (!s) return ''
      if (s.exclusive) {
        return 'В эфире она доступна не будет — единственный способ получить доступ к ней сейчас — оформить платную подписку на Sponsr.'
      }
      return 'Не хотите ждать? Оформите платную подписку на Sponsr — и песня станет доступна сразу, не дожидаясь эфира.'
    }
  },
  data() {
    return { playerDisplayMode: 'embed' }
  },
  watch: {
    '$route.query.id': {
      immediate: true,
      handler(id) { if (id) this.loadSong(id) }
    },
    currentSong: {
      handler(song) {
        if (song) document.title = `${song.songName} — ${song.author}`
        document.body.style.background = song?.contentRemoved ? 'var(--km-bg)' : ''
        if (song?.id) this.playerAccess.checkAccess(song.id)
        this.playerDisplayMode = 'embed'
      }
    }
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
      if (!event.data || event.data.source !== 'karaoke-player' || event.data.type !== 'display-mode') return
      const iframe = this.$refs.playerIframe
      if (iframe && event.source !== iframe.contentWindow) return
      this.playerDisplayMode = event.data.mode
    },
    onPlay(version) { trackPlay(this.currentSong.id, version) },
    async onMetaClick(field, event) {
      const resp = await trackMetaClick(field, this.currentSong.id, event)
      if (resp && resp.meta) {
        sessionStorage.setItem(`kp_token_${this.currentSong.id}`, resp.meta)
        // New tab, not router.push: the player needs the full viewport (position:fixed inside it
        // isn't enough — it still inherits App.vue's classic/modern wrapper otherwise) and a fresh
        // tab keeps the song page as-is behind it. sessionStorage is cloned into same-origin tabs
        // opened this way, so the token set just above is already there when it loads.
        window.open(`/player/${this.currentSong.id}`, '_blank')
      }
    }
  }
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
.km-header-left { display: flex; align-items: center; gap: 0.75rem; }
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
  white-space: nowrap;
}
.km-back:hover { text-decoration: underline; }
.km-logo { height: 36px; width: auto; }
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
  transition: background 0.15s, color 0.15s;
}
.km-tb:hover { background: var(--km-hover); color: var(--km-text); }
.km-tb.active { background: var(--km-accent); color: #fff; }

/* Loading / not found */
.km-loading, .km-not-found {
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
  box-shadow: 0 8px 40px rgba(0,0,0,0.4);
}
.km-removed-icon { font-size: 4rem; margin-bottom: 1.25rem; display: block; line-height: 1; }
.km-removed-title { color: var(--km-text); font-size: 1.4rem; font-weight: 600; margin-bottom: 0.5rem; }
.km-removed-subtitle { color: var(--km-text2); font-size: 1rem; margin-bottom: 1.5rem; }
.km-removed-divider { border: none; border-top: 1px solid var(--km-border); margin: 1.25rem 0; }
.km-removed-hint { color: var(--km-text2); font-size: 0.82rem; margin-bottom: 2rem; line-height: 1.6; }
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
.km-btn-home:hover { background: var(--km-hover); color: var(--km-text); text-decoration: none; }

/* Song content */
.km-song {}

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
  background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 100%);
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
  color: rgba(255,255,255,0.8);
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
.km-meta-item { display: flex; flex-direction: column; gap: 0.15rem; }
.km-meta-label { font-size: 0.7rem; color: var(--km-text2); text-transform: uppercase; letter-spacing: 0.05em; font-weight: 600; }
.km-meta-value { font-size: 0.95rem; font-weight: 600; color: var(--km-text); }

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
.km-link-icons { display: flex; gap: 2px; }
.km-date-publish {
  font-size: 0.9rem;
  color: var(--km-text2);
  text-align: center;
  padding: 0.5rem;
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
.km-player-wrap { border-radius: 8px; }

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
.km-player-card.km-player-page-mode .km-player-label { display: none; }
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
.km-waiting-cta {
  display: inline-block;
  background: var(--km-accent);
  color: #fff;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font-weight: 600;
  text-decoration: none;
  margin-bottom: 0.75rem;
}
.km-waiting-cta:hover { opacity: 0.9; color: #fff; text-decoration: none; }
.km-waiting-fineprint {
  font-size: 0.72rem;
  color: var(--km-text2);
  opacity: 0.8;
  max-width: 480px;
  margin: 0 auto 0.5rem;
  line-height: 1.4;
}
.km-waiting-login {
  font-size: 0.82rem;
  color: var(--km-text2);
  margin-top: 0.5rem;
}
.km-waiting-login a { color: var(--km-accent2); }

/* Видео */
.km-videos { margin-bottom: 1rem; }
.km-video-block { margin-bottom: 1.25rem; }
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
  top: 0; left: 0;
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
  .km-hero { max-height: 220px; }
  .km-hero-banner { max-height: 220px; }
  .km-removed-card { padding: 2rem 1.5rem; }
  .km-links-grid { gap: 0.5rem; }
}
</style>
