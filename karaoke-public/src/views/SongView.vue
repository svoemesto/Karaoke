<template>
  <div>
    <div v-if="currentSongIsLoading" style="padding: 10px">Загрузка...</div>

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
        <tr>
          <td style="text-align: right; padding-right: 5px">Исполнитель:</td>
          <td><div style="font-weight: bolder">{{ currentSong.author }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Год:</td>
          <td><div style="font-weight: bolder">{{ currentSong.year }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Альбом:</td>
          <td><div style="font-weight: bolder">{{ currentSong.album }}</div></td>
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
          <td><div style="font-weight: bolder">{{ currentSong.key }}</div></td>
        </tr>
        <tr>
          <td style="text-align: right; padding-right: 5px">Темп (уд/м):</td>
          <td><div style="font-weight: bolder">{{ currentSong.bpm }}</div></td>
        </tr>
        <tr v-if="currentSong.onAir && currentSong.idVkKaraoke">
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
        <tr v-if="currentSong.onAir && currentSong.idVkLyrics">
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
        <tr v-if="currentSong.onAir && currentSong.idVkMelody">
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
        <tr v-if="currentSong.onAir && currentSong.idVkChords">
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
        <tr v-if="currentSong.formattedTextSong">
          <td colspan="2">
            <div style="background-color: black; padding: 10px; font-size: x-large; line-height: normal;" v-html="currentSong.formattedTextSong" />
          </td>
        </tr>
        <tr v-if="currentSong.formattedTextTabs">
          <td colspan="2">
            <div style="background-color: black; padding: 10px; font-size: x-large; line-height: normal;" v-html="currentSong.formattedTextTabs" />
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
import PlatformLink from '../components/PlatformLink.vue'
import { trackPlay } from '../services/tracking'

export default {
  name: 'SongView',
  components: { PlatformLink },
  computed: {
    ...mapGetters('songs', ['currentSong', 'currentSongIsLoading'])
  },
  watch: {
    '$route.query.id': {
      immediate: true,
      handler(id) {
        if (id) this.loadSong(id)
      }
    }
  },
  methods: {
    ...mapActions('songs', ['loadSong']),
    onPlay(version) {
      trackPlay(this.currentSong.id, version)
    }
  }
}
</script>

<style scoped>
.td_cell { border-style: solid; border-width: thin; text-align: center; vertical-align: middle; }
.head_songname { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; background-color: #0c5460; color: white; }
.date_publish  { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }
</style>
