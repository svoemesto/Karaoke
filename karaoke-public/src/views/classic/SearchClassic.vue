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
        <col style="width: 255px" />
        <col style="width: 250px" />
        <col style="width: 25px" />
        <col style="width: 25px" />
        <col style="width: 25px" />
        <col style="width: 25px" />
      </colgroup>
      <thead>
        <tr>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Исполнитель</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Год</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Альбом</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songtrack">№</div></td>
          <td class="td_cell" style="padding: 0"><div class="head_songname">Композиция</div></td>
          <td class="td_cell" style="padding: 0" colspan="5"><div class="head_songname">&nbsp;</div></td>
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
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0">
            <div class="date_publish">
              <span v-if="showDate(sett)" class="dp-text">{{ sett.datePublish }}</span>
              <PremiumIcon v-if="showCoin(sett)" :state="readiness.contentReadyFor(sett.id)" />
            </div>
          </td>
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
            <PlayerIcon :song-id="sett.id" :state="readiness.stateFor(sett.id)" />
          </td>
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
            <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
          </td>
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
            <FavoriteIcon :song-id="sett.id" />
          </td>
          <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; text-align: center; vertical-align: middle">
            <PlaylistIcon :song-id="sett.id" />
          </td>
        </tr>
      </tbody>
    </table>

    <p v-else-if="searched" style="color: #666">Ничего не найдено.</p>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import PlatformLink from '../../components/PlatformLink.vue'
import PlayerIcon from '../../components/PlayerIcon.vue'
import PremiumIcon from '../../components/PremiumIcon.vue'
import FavoriteIcon from '../../components/FavoriteIcon.vue'
import PlaylistIcon from '../../components/PlaylistIcon.vue'
import { usePlayerReadiness } from '../../composables/usePlayerReadiness'
import { usePlaylistMembership } from '../../composables/usePlaylistMembership'
import { useAuth } from '../../composables/useAuth'

export default {
  name: 'SearchClassic',
  components: { PlatformLink, PlayerIcon, PremiumIcon, FavoriteIcon, PlaylistIcon },
  setup() {
    const { user } = useAuth()
    return { readiness: usePlayerReadiness(), membership: usePlaylistMembership(), user }
  },
  data() {
    return {
      form: { songName: '', author: '', text: '' },
      searched: false
    }
  },
  computed: {
    ...mapGetters('songs', ['authors', 'searchResults', 'searchIsLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли результаты поиска (и при их смене).
    searchResults: {
      immediate: true,
      handler(list) {
        const ids = (list || []).map(s => s.id)
        this.readiness.load(ids)
        this.membership.load(ids)
      }
    }
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
    // Монетка «премиум-контент» — только не-премиум посетителю и только для контента, доступного
    // лишь премиуму (эксклюзив или ещё не в эфире). Золотая/серебряная — по contentReadyFor().
    showCoin(sett) {
      return !this.isPremium && (sett.exclusive || !sett.onAir)
    },
    // Реальную дату публикации (или «Дата пока не определена») показываем всем для ещё не вышедших
    // НЕ-эксклюзивных песен. Для не-премиума она соседствует с монеткой. Тексты «Эксклюзивно на
    // SPONSR» не выводим никому — их заменяет монетка (не-премиуму) / пустая ячейка (премиуму).
    // В эфире — пусто.
    showDate(sett) {
      return !sett.onAir && !sett.exclusive
    },
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
.date_publish { height: 100%; padding: 2px 6px; font-size: small; text-align: right; border-style: none; white-space: nowrap; }
.dp-text { margin-right: 5px; vertical-align: middle; }
</style>
