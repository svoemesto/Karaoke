<template>
  <div :style="style">
    <table class="table table-sm table-hover">
      <tr style="padding: 0; margin: 0;">
        <div style="display: flex; padding: 0; margin: 0;">
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="id" name="id" position="left"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="songName" name="songName"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="author" name="author" :dict="dictAuthors"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="year" name="year"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="album" name="album" :dict="dictAlbums"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="track" name="track"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="date" name="date" :dict="dictDates"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="time" name="time"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="tags" name="tags"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="status" name="status"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagBoosty" name="flagBoosty"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagVk" name="flagVk"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagYoutubeLyrics" name="flagYoutubeLyrics"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagYoutubeKaraoke" name="flagYoutubeKaraoke"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagVkLyrics" name="flagVkLyrics"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagVkKaraoke" name="flagVkKaraoke"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagTelegramLyrics" name="flagTelegramLyrics"/>
          <song-table-head-td @change-field="changeField" @apply-filter="applyFilter" :fld="flagTelegramKaraoke" name="flagTelegramKaraoke" position="right"/>
        </div>
      </tr>
    </table>
  </div>


</template>

<script>
import SongTableHeadTd from './SongTableHeadTd.vue'

export default {
  name: "SongTableHead",
  components: {
    SongTableHeadTd
  },
  props: {
    width: {
      type: String,
      required: false,
      default: () => '100%'
    }
  },
  data() {
    return {
      id: '',
      songName: '',
      author: '',
      year: '',
      album: '',
      track: '',
      date: '',
      time: '',
      tags: '',
      status: '',
      flagBoosty: '',
      flagVk: '',
      flagYoutubeLyrics: '',
      flagYoutubeKaraoke: '',
      flagVkLyrics: '',
      flagVkKaraoke: '',
      flagTelegramLyrics: '',
      flagTelegramKaraoke: ''
    }
  },
  computed: {
    dictAuthors() {
      return this.$store.getters.getAuthors;
    },
    dictAlbums() {
      return this.$store.getters.getAlbums;
    },
    dictDates() {
      function addZero(num) {
        if (num >= 0 && num <= 9) {
          return '0' + num;
        } else {
          return num;
        }
      }
      const today = new Date();
      let result = [];
      for (let i = -2; i < 6; i++) {
        const date = new Date(today.getFullYear(), today.getMonth(), today.getDate()+i);
        result.push(`${addZero(date.getDate())}.${addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`);
      }
      return result;
    },
    style() {
      return {
        height: '43px',
        margin: 0,
        marginBottom: 'auto',
        padding: 0,
        width: this.width + 'px'
      }
    }
  },
  methods: {
    applyFilter() {
      let params = {};
      if (this.id) params.filter_id = this.id;
      if (this.songName) params.filter_songName = this.songName;
      if (this.author) params.filter_author = this.author;
      if (this.year) params.filter_year = this.year;
      if (this.album) params.filter_album = this.album;
      if (this.track) params.filter_track = this.track;
      if (this.date) params.filter_date = this.date;
      if (this.time) params.filter_time = this.time;
      if (this.tags) params.filter_tags = this.tags;
      if (this.status) params.filter_status = this.status;
      if (this.flagBoosty) params.flag_boosty = this.flagBoosty;
      if (this.flagVk) params.flag_vk = this.flagVk;
      if (this.flagYoutubeLyrics) params.flag_youtube_lyrics = this.flagYoutubeLyrics;
      if (this.flagYoutubeKaraoke) params.flag_youtube_karaoke = this.flagYoutubeKaraoke;
      if (this.flagVkLyrics) params.flag_vk_lyrics = this.flagVkLyrics;
      if (this.flagVkKaraoke) params.flag_vk_karaoke = this.flagVkKaraoke;
      if (this.flagTelegramLyrics) params.flag_telegram_lyrics = this.flagTelegramLyrics;
      if (this.flagTelegramKaraoke) params.flag_telegram_karaoke = this.flagTelegramKaraoke;
      this.$store.dispatch('loadSongsAndDictionaries', params)
    },
    changeField(name, fld) {
      this[name] = fld;
    }
  }
}
</script>

<style scoped>
  table {
    font-size: small;
    width: 100%;
    height: 100%;
  }
</style>