<template>
  <div style="width: 800px">
    <a href="/"><img src="/KARAOKE_LOGO.png" style="width: 150px; display: block; margin: auto" alt="Karaoke logo" /></a>
    <AuthorTiles
      v-if="!authorChosen"
      :tiles="authorTiles"
      :selected="selectedAuthor"
      variant="classic"
      @select="onAuthorSelect"
    />

    <button v-if="authorChosen" type="button" class="zk-back-btn" @click="backToAuthors">← К списку авторов</button>

    <div v-if="authorChosen" style="overflow-y: scroll; max-height: calc(100vh - 205px); background: #d5e6ff">
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
        <RouterLink :to="{ path: '/author-playlist', query: { author: zak.author } }" style="margin-left:10px; font-size:12px; font-weight:600; color:#0c5460; background:#fff; border:1px solid #9fbce0; border-radius:14px; padding:2px 10px; text-decoration:none; white-space:nowrap">🎧 Плейлист по песням автора «{{ zak.author }}»</RouterLink>
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
            <col style="width: 405px" />
            <col style="width: 250px" />
            <col style="width: 25px" />
            <col style="width: 25px" />
            <col style="width: 25px" />
            <col style="width: 25px" />
          </colgroup>
          <thead>
            <tr>
              <td class="td_cell" style="padding: 0"><div class="head_songtrack" style="text-align: center">№</div></td>
              <td class="td_cell" style="padding: 0"><div class="head_songname">Композиция</div></td>
              <td class="td_cell" style="padding: 0" colspan="5"><div class="head_songname">&nbsp;</div></td>
            </tr>
          </thead>
          <tbody>
            <tr v-for="sett in alb.albumSettings" :key="sett.id">
              <td class="td_cell" style="padding: 0; width: 25px"><div class="songtrack">{{ sett.track }}</div></td>
              <td class="td_cell" style="padding: 0; border-right-width: 0">
                <RouterLink :to="{ path: '/song', query: { id: sett.id } }" class="songname">{{ sett.songName }}</RouterLink>
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0">
                <div class="date_publish">
                  <span v-if="showDate(sett)" class="dp-text">{{ sett.datePublish }}</span>
                  <PremiumIcon v-if="showCoin(sett)" :state="readiness.contentReadyFor(sett.id)" />
                </div>
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; width: 25px; text-align: center; vertical-align: middle">
                <PlayerIcon :song-id="sett.id" :state="readiness.stateFor(sett.id)" />
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; width: 25px; text-align: center; vertical-align: middle">
                <PlatformLink link-name="sponsr" :link-value="sett.linkSponsrPlay" :song-id="sett.id" song-version="all" />
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; width: 25px; text-align: center; vertical-align: middle">
                <FavoriteIcon :song-id="sett.id" />
              </td>
              <td class="td_cell" style="padding: 0; border-top-width: 0; border-left-width: 0; border-right-width: 0; width: 25px; text-align: center; vertical-align: middle">
                <PlaylistIcon :song-id="sett.id" />
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
import PlayerIcon from '../../components/PlayerIcon.vue'
import PremiumIcon from '../../components/PremiumIcon.vue'
import FavoriteIcon from '../../components/FavoriteIcon.vue'
import PlaylistIcon from '../../components/PlaylistIcon.vue'
import AuthorTiles from '../../components/AuthorTiles.vue'
import { usePlayerReadiness } from '../../composables/usePlayerReadiness'
import { usePlaylistMembership } from '../../composables/usePlaylistMembership'
import { useAuth } from '../../composables/useAuth'

export default {
  name: 'ZakromaClassic',
  components: { PlatformLink, PlayerIcon, PremiumIcon, FavoriteIcon, PlaylistIcon, AuthorTiles },
  setup() {
    const { user } = useAuth()
    return { readiness: usePlayerReadiness(), membership: usePlaylistMembership(), user }
  },
  data() {
    return {
      selectedAuthor: this.$route.query.author || '',
      // Плитки-пикер видны, пока автор не выбран. После выбора (в т.ч. «Все авторы») скрываются.
      authorChosen: !!this.$route.query.author
    }
  },
  computed: {
    ...mapGetters('zakroma', ['authorTiles', 'zakroma', 'isLoading']),
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
  },
  watch: {
    // Готовность плеера подгружаем асинхронно, как только пришли данные закромов (и при их смене).
    zakroma: {
      immediate: true,
      handler(list) {
        const ids = (list || []).flatMap(z => z.albums.flatMap(a => a.albumSettings.map(s => s.id)))
        this.readiness.load(ids)
        this.membership.load(ids)
      }
    }
  },
  mounted() {
    this.loadAuthorTiles()
    // Таблицу грузим только если автор уже выбран (например, зашли по ссылке ?author=...).
    if (this.authorChosen) this.loadZakroma(this.selectedAuthor)
  },
  methods: {
    ...mapActions('zakroma', ['loadAuthorTiles', 'loadZakroma']),
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
    onAuthorSelect(author) {
      this.selectedAuthor = author
      this.authorChosen = true
      this.$router.replace({ path: '/zakroma', query: author ? { author } : {} })
      this.loadZakroma(author)
    },
    backToAuthors() {
      this.selectedAuthor = ''
      this.authorChosen = false
      this.$router.replace({ path: '/zakroma', query: {} })
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
.zk-back-btn { display: inline-flex; align-items: center; gap: 4px; margin: 4px 0 8px; padding: 5px 12px; font-size: 13px; font-weight: 600; color: #0c5460; background: #ffffff; border: 1px solid #9fbce0; border-radius: 6px; cursor: pointer; }
.zk-back-btn:hover { background: #eef5ff; box-shadow: 0 2px 6px rgba(12, 84, 96, 0.2); }
.date_publish { height: 100%; padding: 2px 6px; font-size: small; text-align: right; border-style: none; white-space: nowrap; }
.dp-text { margin-right: 5px; vertical-align: middle; }
</style>
