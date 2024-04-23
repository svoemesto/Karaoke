<template>
  <div :style="styleRoot">
    <song-table-head :width="width"/>
    <song-table-body :width="width" :songs="songs" :firstSong="firstSong" :lastSong="lastSong"/>
    <song-table-footer :width="width"/>
  </div>
</template>

<script>

// import { mapActions } from "vuex";

import SongTableBody from './elements/SongTableBody.vue'
import SongTableHead from './elements/SongTableHead.vue'
import SongTableFooter from './elements/SongTableFooter.vue'

export default {
  name: "SongsTable",
  components: {
    SongTableBody,
    SongTableHead,
    SongTableFooter
  },
  computed: {
    songs() {
      return this.$store.getters.getSongs
    },
    firstSong() { return this.$store.getters.getFirstSong },
    lastSong() { return this.$store.getters.getLastSong },
    width() {
      return this.$store.getters.getFieldsParamsWidth
    },
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: this.width+25+'px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'lightblue'
      }
    }
  },
  methods: {
  }
}
</script>

<style scoped>

</style>