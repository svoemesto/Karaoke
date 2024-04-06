<template>
  <div :style="styleRoot" class="table-song-body">
    <div v-for="song in songs" :key="song.id" @click="clickRow(song.id)" :style="styleRow">
      <song-table-body-td name="id" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong" position="left"/>
      <song-table-body-td name="songName" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="author" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="year" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="album" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="track" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="date" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="time" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="tags" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="status" :song="song" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagBoosty" :song="song" :color="song.processColorBoosty" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagVk" :song="song" :color="song.processColorVk" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagYoutubeLyrics" @dblclick="dblClickLyrics" :song="song" :color="song.processColorMeltLyrics" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagYoutubeKaraoke" @dblclick="dblClickKaraoke" :song="song" :color="song.processColorMeltKaraoke" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagVkLyrics" :song="song" :color="song.processColorVkLyrics" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagVkKaraoke" :song="song" :color="song.processColorVkKaraoke" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagTelegramLyrics" :song="song" :color="song.processColorTelegramLyrics" :isFirst="song===firstSong" :isLast="song===lastSong"/>
      <song-table-body-td name="flagTelegramKaraoke" :song="song" :color="song.processColorTelegramKaraoke" :isFirst="song===firstSong" :isLast="song===lastSong" position="right"/>
    </div>
  </div>


</template>

<script>
import SongTableBodyTd from './SongTableBodyTd'

export default {
  name: "SongTableBody",
  components: {
    SongTableBodyTd
  },
  props: {
    songs: {
      type: Array,
      required: false,
      default: () => []
    },
    firstSong: {
      type: Object,
      required: false,
      default: () => {}
    },
    lastSong: {
      type: Object,
      required: false,
      default: () => {}
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
        height: 'calc(100vh - 120px)'
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
      if (!this.$store.getters.isChanged) {
        this.$store.commit('setCurrentSongId', songId)
      }
    },
    dblClickKaraoke() {
      if (!this.$store.getters.isChanged) {
        return this.$store.getters.playKaraoke;
      }
    },
    dblClickLyrics() {
      if (!this.$store.getters.isChanged) {
        return this.$store.getters.playLyrics;
      }
    }
  }
}
</script>

<style scoped>

</style>