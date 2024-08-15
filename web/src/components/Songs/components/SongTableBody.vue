<template>
  <div :style="styleRoot" class="table-song-body">
    <SongEditModal v-if="isSongEditVisible" @close="closeSongEdit"/>
    <div v-for="song in songs" :key="song.id" @click="clickRow(song.id)" :style="styleRow">
      <song-table-body-td name="id" :id="song.id" :text="song.id" :color="song.color" position="left"/>
      <song-table-body-td class="song-name" name="songName" :id="song.id" :text="song.songName" :color="song.color" @click="clickSongName" />
      <song-table-body-td name="author" :text="song.author" :id="song.id" :color="song.color" />
      <song-table-body-td name="year" :text="song.year" :id="song.id" :color="song.color" />
      <song-table-body-td name="album" :text="song.album" :id="song.id" :color="song.color" />
      <song-table-body-td name="track" :text="song.track" :id="song.id" :color="song.color" />
      <song-table-body-td name="date" :text="song.date" :id="song.id" :color="song.color" />
      <song-table-body-td name="time" :text="song.time" :id="song.id" :color="song.color" />
      <song-table-body-td name="tags" :text="song.tags" :id="song.id" :color="song.color" />
      <song-table-body-td name="status" :text="song.status" :id="song.id" :color="song.color" />
      <song-table-body-td name="resultVersion" :text="song.resultVersion" :id="song.id" :color="song.color" />
      <song-table-body-td name="flagBoosty" :text="song.flagBoosty" :id="song.id" :color="song.processColorBoosty"/>
      <song-table-body-td name="flagVk" :text="song.flagVk" :id="song.id" :color="song.processColorVk"/>
      <song-table-body-td name="flagYoutubeLyrics" :text="song.flagYoutubeLyrics" @dblclick="dblClickLyrics(song.id)" :id="song.id" :color="song.processColorMeltLyrics"/>
      <song-table-body-td name="flagYoutubeKaraoke" :text="song.flagYoutubeKaraoke" @dblclick="dblClickKaraoke(song.id)" :id="song.id" :color="song.processColorMeltKaraoke"/>
      <song-table-body-td name="flagVkLyrics" :text="song.flagVkLyrics" :id="song.id" :color="song.processColorVkLyrics"/>
      <song-table-body-td name="flagVkKaraoke" :text="song.flagVkKaraoke" :id="song.id" :color="song.processColorVkKaraoke"/>
      <song-table-body-td name="flagTelegramLyrics" :text="song.flagTelegramLyrics" :id="song.id" :color="song.processColorTelegramLyrics"/>
      <song-table-body-td name="flagTelegramKaraoke" :text="song.flagTelegramKaraoke" :id="song.id" :color="song.processColorTelegramKaraoke" position="right"/>
    </div>
  </div>


</template>

<script>
import SongTableBodyTd from './SongTableBodyTd.vue'
import SongEditModal from "@/components/Songs/edit/SongEditModal.vue";

export default {
  name: "SongTableBody",
  components: {
    SongEditModal,
    SongTableBodyTd
  },
  data () {
    return {
      isSongEditVisible: false,
    };
  },
  props: {
    songs: {
      type: Array,
      required: false,
      default: () => []
    },
    width: {
      type: String,
      required: false,
      default: () => '100%'
    }
  },
  computed: {
    styleRoot() {
      return {
        margin:0,
        marginBottom: 'auto',
        overflowY: 'scroll',
        width: this.width,
        padding: 0,
        height: 'calc(100vh - 165px)'
      }
    },
    styleRow() {
      return {
        display: 'flex',
        padding: 0,
        margin:0
      }
    }
  },
  methods: {
    clickRow(songId) {
      if (!this.$store.getters.isChangedSong) {
        // console.log('Called clickRow');
        this.$store.commit('setCurrentSongId', songId)
      }
    },
    dblClickKaraoke(id) {
      if (!this.$store.getters.isChangedSong) {
        return this.$store.getters.playKaraoke(id);
      }
    },
    dblClickLyrics(id) {
      if (!this.$store.getters.isChangedSong) {
        return this.$store.getters.playLyrics(id);
      }
    },
    clickSongName() {
      this.isSongEditVisible = true;
    },
    closeSongEdit() {
      this.isSongEditVisible = false;
    }
  }
}
</script>

<style scoped>

.song-name {
  color: black;
  cursor: default;
}
.song-name:hover {
  color: red;
  cursor: pointer;
}

</style>