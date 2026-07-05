<template>
  <div>
    <div v-if="currentSongIsLoading" style="padding: 10px">Загрузка...</div>

    <div v-else-if="currentSong && currentSong.contentRemoved" class="removed-wrapper">
      <div class="removed-card">
        <div class="removed-icon">🔒</div>
        <div class="removed-title">Информация о произведении удалена</div>
        <div class="removed-subtitle">по требованию правообладателя</div>
        <hr class="removed-divider">
        <div class="removed-hint">
          Страница недоступна в соответствии с обращением<br>
          об авторских правах. Если вы считаете, что это<br>
          произошло по ошибке — свяжитесь с нами.
        </div>
        <router-link to="/" class="btn-home">← На главную</router-link>
      </div>
    </div>

    <div v-else-if="currentSong">
      <a href="/"><img src="/KARAOKE_LOGO.png" style="width: 150px; display: block; margin: auto" alt="Karaoke logo" /></a>

      <table style="width: 100%; margin-top: 10px">
        <tr>
          <td colspan="2">
            <img v-if="currentSong.songPictureUrl" :src="currentSong.songPictureUrl"
                 style="display:block; margin:auto; background-color:black"
                 @error="$event.target.style.display='none'" alt="" />
            <div style="padding: 10px; max-width: 800px; font-weight: bold; font-size: 32pt; text-align: center; color: #ffff88; background-color: black; margin: 0 auto">
              «{{ currentSong.songName }}»
            </div>
          </td>
        </tr>
        <tr>
          <td colspan="2">
            <table style="width: 100%">
              <tr>
                <td class="td_cell" colspan="17" style="padding: 0">
                  <div style="text-align: center">Ссылки на просмотр:</div>
                </td>
              </tr>
              <tr>
                <td class="td_cell" style="padding: 0"><div style="text-align: center">All</div></td>
                <td class="td_cell" colspan="4" style="padding: 0"><div style="text-align: center">Karaoke</div></td>
                <td class="td_cell" colspan="4" style="padding: 0"><div style="text-align: center">Lyrics</div></td>
                <td class="td_cell" colspan="4" style="padding: 0"><div style="text-align: center">TABS</div></td>
                <td class="td_cell" colspan="4" style="padding: 0"><div style="text-align: center">Chords</div></td>
              </tr>
              <tr v-if="currentSong.onAir">
                <td class="td_cell" style="padding: 0; border-top-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
                  <PlatformLink link-name="sponsr" :link-value="currentSong.linkSponsrPlay" :song-id="currentSong.id" song-version="all" />
                </td>
                <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="currentSong.linkDzenKaraoke" :song-id="currentSong.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="currentSong.linkMaxKaraoke" :song-id="currentSong.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="currentSong.linkVkKaraoke" :song-id="currentSong.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="currentSong.linkTgKaraoke" :song-id="currentSong.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="currentSong.linkDzenLyrics" :song-id="currentSong.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="currentSong.linkMaxLyrics" :song-id="currentSong.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="currentSong.linkVkLyrics" :song-id="currentSong.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="currentSong.linkTgLyrics" :song-id="currentSong.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="currentSong.linkDzenTabs" :song-id="currentSong.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="currentSong.linkMaxTabs" :song-id="currentSong.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="currentSong.linkVkTabs" :song-id="currentSong.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="currentSong.linkTgTabs" :song-id="currentSong.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="currentSong.linkDzenChords" :song-id="currentSong.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="currentSong.linkMaxChords" :song-id="currentSong.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="currentSong.linkVkChords" :song-id="currentSong.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="currentSong.linkTgChords" :song-id="currentSong.id" song-version="chords" /></td>
              </tr>
              <tr v-else>
                <td class="td_cell" colspan="17" style="padding: 0">
                  <div class="date_publish">{{ currentSong.datePublish }}</div>
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <!-- Онлайн-плеер: между блоком "Ссылки на просмотр" и блоком информации о песне -->
        <tr v-if="playerCanWatch">
          <td colspan="2">
            <div :class="{ 'player-page-mode': playerDisplayMode === 'page' }">
              <div class="player-label">🎤 Онлайн-плеер караоке</div>
              <iframe
                ref="playerIframe"
                :src="`/player/${currentSong.id}`"
                width="800" height="450" style="border:0; display:block; margin:auto"
                allow="autoplay; fullscreen" allowfullscreen
              />
            </div>
          </td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Исполнитель:</td>
          <td><div style="font-weight: bolder" @click="onMetaClick('author', $event)">{{ currentSong.author }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Год:</td>
          <td><div style="font-weight: bolder" @click="onMetaClick('year', $event)">{{ currentSong.year }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Альбом:</td>
          <td><div style="font-weight: bolder" @click="onMetaClick('album', $event)">{{ currentSong.album }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Трек:</td>
          <td><div style="font-weight: bolder">{{ currentSong.track }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Композиция:</td>
          <td><div style="font-weight: bolder">{{ currentSong.songName }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Тональность:</td>
          <td><div style="font-weight: bolder" @click="onMetaClick('key', $event)">{{ currentSong.key }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Темп (уд/м):</td>
          <td><div style="font-weight: bolder">{{ currentSong.bpm }}</div></td>
        </tr>

        <!-- Видео ВК — старое место, только когда онлайн-плеер сам не может отобразиться -->
        <template v-if="currentSong.onAir && !playerCanWatch && playerAccessLoaded">
          <tr v-if="currentSong.idVkKaraoke">
            <td colspan="2">
              <div @click="onPlay('karaoke')">
                <iframe
                  :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkKaraokeOID}&id=${currentSong.idVkKaraokeID}`"
                  width="800" height="450" allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                  frameborder="0" allowfullscreen
                />
              </div>
            </td>
          </tr>
          <tr v-if="currentSong.idVkLyrics">
            <td colspan="2">
              <div @click="onPlay('lyrics')">
                <iframe
                  :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkLyricsOID}&id=${currentSong.idVkLyricsID}`"
                  width="800" height="450" allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                  frameborder="0" allowfullscreen
                />
              </div>
            </td>
          </tr>
          <tr v-if="currentSong.idVkMelody">
            <td colspan="2">
              <div @click="onPlay('tabs')">
                <iframe
                  :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkMelodyOID}&id=${currentSong.idVkMelodyID}`"
                  width="800" height="450" allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                  frameborder="0" allowfullscreen
                />
              </div>
            </td>
          </tr>
          <tr v-if="currentSong.idVkChords">
            <td colspan="2">
              <div @click="onPlay('chords')">
                <iframe
                  :src="`https://vkvideo.ru/video_ext.php?hd=3&oid=${currentSong.idVkChordsOID}&id=${currentSong.idVkChordsID}`"
                  width="800" height="450" allow="autoplay; encrypted-media; fullscreen; picture-in-picture;"
                  frameborder="0" allowfullscreen
                />
              </div>
            </td>
          </tr>
        </template>

        <!-- Не в эфире (или эксклюзив/не готово) и плеер недоступен — сообщение об ожидании/подписке.
             Тоже на старом месте видео-блока. -->
        <tr v-if="!currentSong.onAir && !playerCanWatch && playerAccessLoaded">
          <td colspan="2">
            <div class="waiting-card">
              <div class="waiting-title">{{ waitingTitle }}</div>
              <div class="waiting-body">{{ waitingBody }}</div>
              <a :href="sponsrUrl" target="_blank" rel="noopener" class="waiting-cta" @click="onSponsrClick">Оформить подписку на Sponsr →</a>
              <div class="waiting-fineprint">
                После оформления и оплаты подписки на Sponsr доступность песни на сайте появится через некоторое время.
              </div>
              <div v-if="!isLoggedIn" class="waiting-login">
                Также вы можете <router-link to="/register">зарегистрироваться</router-link> или
                <router-link to="/login">войти</router-link> на сайте — это понадобится, чтобы мы
                могли узнать вас как премиум-подписчика.
              </div>
              <div v-else-if="playerIsPremiumUser" class="waiting-login">
                Вы премиум-пользователь — как только материалы для плеера будут готовы, он появится здесь автоматически.
              </div>
            </div>
          </td>
        </tr>

        <tr v-if="currentSong.formattedTextSong">
          <td colspan="2">
            <div style="background-color: black; padding: 10px; font-size: x-large; line-height: normal;" v-html="currentSong.formattedTextSong" />
          </td>
        </tr>
        <tr v-if="currentSong.formattedTextTabs">
          <td colspan="2">
            <div style="background-color: black; padding: 10px; font-size: x-large; line-height: normal; font-family: monospace;" v-html="currentSong.formattedTextTabs" />
          </td>
        </tr>
        <tr v-if="currentSong.formattedTextChords">
          <td colspan="2">
            <div style="background-color: black; padding: 10px; font-size: x-large; line-height: normal;" v-html="currentSong.formattedTextChords" />
          </td>
        </tr>
      </table>
    </div>

    <p v-else style="color: #666">Песня не найдена.</p>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'
import { useAuth } from '../../composables/useAuth'
import { usePlayerAccess } from '../../composables/usePlayerAccess'
import { trackPlay, trackMetaClick, trackLinkToSong } from '../../services/tracking'
import { pluralDays } from '../../utils/pluralRu'

export default {
  name: 'SongClassic',
  components: { PlatformLink },
  setup() {
    const { isLoggedIn } = useAuth()
    const playerAccess = usePlayerAccess()
    return { isLoggedIn, playerAccess }
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
      handler(id) {
        if (id) this.loadSong(id)
      }
    },
    currentSong: {
      handler(song) {
        document.body.style.background = song?.contentRemoved ? '#0d0d1a' : ''
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
    // box between the small embedded card and a full-viewport overlay.
    onPlayerMessage(event) {
      if (!event.data || event.data.source !== 'karaoke-player' || event.data.type !== 'display-mode') return
      const iframe = this.$refs.playerIframe
      if (iframe && event.source !== iframe.contentWindow) return
      this.playerDisplayMode = event.data.mode
    },
    onPlay(version) {
      trackPlay(this.currentSong.id, version)
    },
    // Клик по CTA подписки Sponsr — трекаем как переход по ссылке песни (fire-and-forget, нативный
    // переход по href не блокируем). song-version='all' — как у PlatformLink sponsr.
    onSponsrClick() {
      trackLinkToSong('sponsr', this.currentSong.id, 'all')
    },
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
.td_cell { border-style: solid; border-width: thin; text-align: center; vertical-align: middle; }
.head_songname { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; background-color: #0c5460; color: white; }
.date_publish  { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }

.player-label {
  text-align: center;
  font-weight: bold;
  color: #ffff88;
  background-color: black;
  padding: 6px;
  margin-bottom: 4px;
}

/* "Широкий" режим — плеер (внутри iframe) сам попросил родительскую страницу растянуть его на
   весь вьюпорт вместо маленького 800x450 блока в таблице. */
.player-page-mode {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #000;
}
.player-page-mode .player-label { display: none; }
.player-page-mode iframe {
  width: 100vw !important;
  height: 100vh !important;
  margin: 0 !important;
}

.waiting-card {
  background-color: #16213e;
  border: 1px solid #2d2d5e;
  border-radius: 10px;
  padding: 1.5rem;
  text-align: center;
  max-width: 700px;
  margin: 0 auto;
}
.waiting-title { font-size: 1.1rem; font-weight: bold; color: #e8e8f0; margin-bottom: 0.5rem; }
.waiting-body { color: #9090b8; font-size: 0.95rem; margin-bottom: 1rem; line-height: 1.5; }
.waiting-cta {
  display: inline-block;
  background: #3a3a6e;
  border: 1px solid #5a5a9e;
  color: #c8c8f0;
  border-radius: 8px;
  padding: 0.6rem 1.4rem;
  font-weight: 600;
  text-decoration: none;
  margin-bottom: 0.75rem;
}
.waiting-cta:hover { background: #4a4a8e; border-color: #7a7abe; color: #fff; text-decoration: none; }
.waiting-fineprint { font-size: 0.72rem; color: #6a6a90; max-width: 480px; margin: 0 auto 0.5rem; line-height: 1.4; }
.waiting-login { font-size: 0.82rem; color: #9090b8; margin-top: 0.5rem; }
.waiting-login a { color: #c8c8f0; }

.removed-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}
.removed-card {
  background: linear-gradient(145deg, #1a1a2e, #16213e);
  border: 1px solid #2d2d5e;
  border-radius: 16px;
  padding: 3rem 3.5rem;
  max-width: 540px;
  width: 100%;
  text-align: center;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.6);
}
.removed-icon {
  font-size: 4rem;
  margin-bottom: 1.25rem;
  display: block;
  line-height: 1;
}
.removed-title {
  color: #e8e8f0;
  font-size: 1.45rem;
  font-weight: 600;
  margin-bottom: 0.6rem;
  line-height: 1.3;
}
.removed-subtitle {
  color: #9090b8;
  font-size: 1.05rem;
  margin-bottom: 1.5rem;
}
.removed-divider {
  border: none;
  border-top: 1px solid #2a2a4e;
  margin: 1.25rem 0;
}
.removed-hint {
  color: #5a5a80;
  font-size: 0.82rem;
  margin-bottom: 2rem;
  line-height: 1.6;
}
.btn-home {
  background: #3a3a6e;
  border: 1px solid #5a5a9e;
  color: #c8c8f0;
  border-radius: 8px;
  padding: 0.55rem 1.8rem;
  font-size: 0.95rem;
  text-decoration: none;
  transition: background 0.2s, border-color 0.2s;
  display: inline-block;
}
.btn-home:hover {
  background: #4a4a8e;
  border-color: #7a7abe;
  color: #fff;
  text-decoration: none;
}
</style>
