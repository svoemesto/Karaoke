<template>
  <div style="width: 900px">
    <a href="/"><img src="/KARAOKE_LOGO.png" style="width: 150px; display: block; margin: auto" alt="Karaoke logo" /></a>
    <table class="table-sm mb-3" style="width: 580px; margin: 0 auto">
      <tr>
        <td style="text-align: right; width: 150px; padding: 0">Исполнитель:</td>
        <td>
          <input
            list="list_authors"
            v-model="form.author"
            class="form-control"
            style="font-size: 14px; padding: 0; height: 25px; width: 300px"
          />
          <datalist id="list_authors">
            <option v-for="a in authors" :key="a" :value="a" />
          </datalist>
        </td>
        <td rowspan="3" style="vertical-align: top; padding-top: 2px">
          <button class="btn btn-info" type="button" style="height: 75px; width: 80px" @click="onSearch">Искать</button>
        </td>
      </tr>
      <tr>
        <td style="text-align: right; width: 150px; padding: 0">Название песни:</td>
        <td>
          <input v-model="form.songName" class="form-control" style="font-size: 14px; padding: 0; height: 25px; width: 300px" />
        </td>
      </tr>
      <tr>
        <td style="text-align: right; width: 150px; padding: 0">Слова песни:</td>
        <td>
          <input v-model="form.text" class="form-control" style="font-size: 14px; padding: 0; height: 25px; width: 300px" />
        </td>
      </tr>
    </table>

    <div v-if="searchIsLoading" style="padding: 10px">Загрузка...</div>

    <table v-else-if="searchResults.length" class="table-sm table-hover" style="width: 880px; table-layout: fixed; background: #d5e6ff">
      <colgroup>
        <col style="width: 100px" />
        <col style="width: 35px" />
        <col style="width: 115px" />
        <col style="width: 25px" />
        <col style="width: 228px" />
        <col style="width: 25px" />
        <col v-for="i in 16" :key="i" style="width: 22px" />
      </colgroup>
      <thead>
        <tr>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Исполнитель</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Год</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Альбом</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songtrack">№</div></td>
          <td class="td_cell" style="padding: 0" colspan="2"><div class="head_songname">Композиция</div></td>
          <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Karaoke</div></td>
          <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Lyrics</div></td>
          <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">TABS</div></td>
          <td class="td_cell" style="padding: 0" colspan="4"><div class="head_songname">Chords</div></td>
        </tr>
      </thead>
      <tbody>
        <tr v-for="sett in searchResults" :key="sett.id">
          <td class="td_cell" style="padding: 0; text-align: left"><div class="author">{{ sett.author }}</div></td>
          <td class="td_cell" style="padding: 0"><div class="year">{{ sett.year }}</div></td>
          <td class="td_cell" style="padding: 0; text-align: left"><div class="album">{{ sett.album }}</div></td>
          <td class="td_cell" style="padding: 0"><div class="songtrack">{{ sett.track }}</div></td>
          <td class="td_cell" style="padding: 0; border-right-width: 0; text-align: left">
            <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="songname">{{ sett.songName }}</RouterLink>
          </td>
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
            <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
          </td>
          <template v-if="sett.onAir">
            <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="sett.linkDzenKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="sett.linkMaxKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="sett.linkVkKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
            <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="sett.linkTgKaraoke" :song-id="sett.id" song-version="karaoke" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="sett.linkDzenLyrics" :song-id="sett.id" song-version="lyrics" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="sett.linkMaxLyrics" :song-id="sett.id" song-version="lyrics" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="sett.linkVkLyrics" :song-id="sett.id" song-version="lyrics" /></td>
            <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="sett.linkTgLyrics" :song-id="sett.id" song-version="lyrics" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="sett.linkDzenTabs" :song-id="sett.id" song-version="tabs" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="sett.linkMaxTabs" :song-id="sett.id" song-version="tabs" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="sett.linkVkTabs" :song-id="sett.id" song-version="tabs" /></td>
            <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="sett.linkTgTabs" :song-id="sett.id" song-version="tabs" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0"><PlatformLink link-name="dzen" :link-value="sett.linkDzenChords" :song-id="sett.id" song-version="chords" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="max" :link-value="sett.linkMaxChords" :song-id="sett.id" song-version="chords" /></td>
            <td class="td_cell" style="padding: 0; border-right-width: 0; border-left-width: 0"><PlatformLink link-name="vk" :link-value="sett.linkVkChords" :song-id="sett.id" song-version="chords" /></td>
            <td class="td_cell" style="padding: 0; border-left-width: 0"><PlatformLink link-name="tg" :link-value="sett.linkTgChords" :song-id="sett.id" song-version="chords" /></td>
          </template>
          <td v-else class="td_cell" colspan="16" style="padding: 0">
            <div class="date_publish">{{ sett.datePublish }}</div>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else-if="searched" style="color: #666">Ничего не найдено.</p>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../components/PlatformLink.vue'

export default {
  name: 'SearchView',
  components: { PlatformLink },
  data() {
    return {
      form: { songName: '', author: '', text: '' },
      searched: false
    }
  },
  computed: {
    ...mapGetters('songs', ['authors', 'searchResults', 'searchIsLoading']),
  },
  mounted() {
    this.loadAuthors()
    const q = this.$route.query
    if (q.author || q.songName || q.text) {
      this.form.author = q.author || ''
      this.form.songName = q.songName || ''
      this.form.text = q.text || ''
      this.onSearch()
    }
  },
  methods: {
    ...mapActions('songs', ['loadAuthors', 'search']),
    onSearch() {
      this.searched = true
      this.search({ songName: this.form.songName, author: this.form.author, text: this.form.text })
    }
  }
}
</script>

<style scoped>
.td_cell { border-style: solid; border-width: thin; text-align: center; vertical-align: middle; }
.head_songtrack { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; background-color: #0c5460; color: white; }
.head_songname  { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; background-color: #0c5460; color: white; }
.author     { height: 100%; padding: 2px; font-size: small; border-style: none; }
.year       { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }
.album      { height: 100%; padding: 2px; font-size: small; border-style: none; }
.songtrack  { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }
.songname   { height: 100%; padding: 2px; font-size: small; border-style: none; }
.date_publish { height: 100%; padding: 2px; font-size: small; text-align: center; border-style: none; }
</style>
