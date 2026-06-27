<template>
  <div style="width: 800px">
    <a href="/"><img src="/KARAOKE_LOGO.png" style="width: 150px; display: block; margin: auto" alt="Karaoke logo" /></a>
    <select
      v-model="selectedAuthor"
      class="form-select"
      style="font-size: 14px; padding: 0 2rem 0 0.5rem; height: 34px"
      @change="onAuthorChange"
    >
      <option value="">(Выберите автора)</option>
      <option v-for="a in authors" :key="a" :value="a">{{ a }}</option>
    </select>

    <div style="overflow-y: scroll; max-height: calc(100vh - 165px); background: #d5e6ff">
    <div v-if="isLoading" style="padding: 10px">Загрузка...</div>

    <div v-for="zak in zakroma" :key="zak.author" class="mb-4">
      <div class="author" style="display:flex; justify-content:center; align-items:center; gap:6px">
        <img
          v-if="zak.authorPictureUrl"
          :src="zak.authorPictureUrl"
          style="height:50px; width:auto; flex-shrink:0; background-color:black"
          @error="$event.target.style.display='none'"
          alt=""
        />
        <span>{{ zak.author }}</span>
      </div>
      <div v-for="alb in zak.albums" :key="alb.albumName">
        <div class="album" style="display:flex; align-items:center; gap:6px">
          <img
            v-if="alb.albumPictureUrl"
            :src="alb.albumPictureUrl"
            style="height:50px; width:50px; object-fit:cover; flex-shrink:0; background-color:black"
            @error="$event.target.style.display='none'"
            alt=""
          />
          <span>{{ alb.year }} - {{ alb.albumName }}</span>
        </div>
        <table class="table-sm table-hover" style="width: 780px; table-layout: fixed">
          <colgroup>
            <col style="width: 25px" />
            <col style="width: 378px" />
            <col style="width: 25px" />
            <col v-for="i in 16" :key="i" style="width: 22px" />
          </colgroup>
          <thead>
            <tr>
              <td class="td_cell" style="padding: 0"><div class="head_songtrack" style="text-align: center">№</div></td>
              <td class="td_cell" style="padding: 0" colspan="2"><div class="head_songname">Композиция</div></td>
              <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Karaoke</div></td>
              <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Lyrics</div></td>
              <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">TABS</div></td>
              <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Chords</div></td>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sett in alb.albumSettings" :key="sett.id">
              <td class="td_cell" style="padding: 0; width: 25px"><div class="songtrack">{{ sett.track }}</div></td>
              <td class="td_cell" style="padding: 0; border-right-width: 0">
                <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="songname">{{ sett.songName }}</RouterLink>
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; width: 25px; text-align: center; vertical-align: middle">
                <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
              </td>
              <template v-if="sett.onAir">
                <td class="td_cell" style="padding: 0; border-right-width: 0; width: 22px"><PlatformLink link-name="dzen" :link-value="sett.linkDzenKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="max" :link-value="sett.linkMaxKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="vk" :link-value="sett.linkVkKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="tg" :link-value="sett.linkTgKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; width: 22px"><PlatformLink link-name="dzen" :link-value="sett.linkDzenLyrics" :song-id="sett.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="max" :link-value="sett.linkMaxLyrics" :song-id="sett.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="vk" :link-value="sett.linkVkLyrics" :song-id="sett.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="tg" :link-value="sett.linkTgLyrics" :song-id="sett.id" song-version="lyrics" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; width: 22px"><PlatformLink link-name="dzen" :link-value="sett.linkDzenTabs" :song-id="sett.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="max" :link-value="sett.linkMaxTabs" :song-id="sett.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="vk" :link-value="sett.linkVkTabs" :song-id="sett.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="tg" :link-value="sett.linkTgTabs" :song-id="sett.id" song-version="tabs" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; width: 22px"><PlatformLink link-name="dzen" :link-value="sett.linkDzenChords" :song-id="sett.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="max" :link-value="sett.linkMaxChords" :song-id="sett.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="vk" :link-value="sett.linkVkChords" :song-id="sett.id" song-version="chords" /></td>
                <td class="td_cell" style="padding: 0; border-left-width: 0; width: 22px"><PlatformLink link-name="tg" :link-value="sett.linkTgChords" :song-id="sett.id" song-version="chords" /></td>
              </template>
              <td v-else class="td_cell" colspan="16" style="padding: 0">
                <div class="date_publish">{{ sett.datePublish }}</div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'

export default {
  name: 'ZakromaClassic',
  components: { PlatformLink },
  data() {
    return {
      selectedAuthor: this.$route.query.author || ''
    }
  },
  computed: {
    ...mapGetters('zakroma', ['authors', 'zakroma', 'isLoading']),
  },
  mounted() {
    this.loadAuthors()
    this.loadZakroma(this.selectedAuthor)
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthors', 'loadZakroma']),
    onAuthorChange() {
      const author = this.selectedAuthor
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      this.loadZakroma(author)
    }
  }
}
</script>

<style scoped>
.td_cell { border-style: solid; border-width: thin; }
.head_songtrack { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; border-width: thin; background-color: #0c5460; color: white; }
.head_songname  { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; border-width: thin; background-color: #0c5460; color: white; }
.author    { padding: 0; font-size: medium; text-align: center; font-weight: bold; border-style: none; }
.album     { padding: 0; padding-top: 10px; font-size: small; font-weight: bold; min-width: 300px; max-width: 300px; border-style: none; }
.songtrack { height: 100%; padding: 2px; font-size: small; text-align: center; min-width: 20px; max-width: 20px; border-style: none; }
.songname  { height: 100%; padding: 2px; font-size: small; min-width: 300px; max-width: 300px; border-style: none; }
.date_publish { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }
</style>
