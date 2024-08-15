<template>
  <div :style="styleRoot">
    <song-table-head :width="width"/>
    <song-table-body :width="width" :songs="songs" />
    <song-table-footer :width="width"/>
  </div>
</template>

<script>

// import { mapActions } from "vuex";

import SongTableBody from './components/SongTableBody.vue'
import SongTableHead from './components/SongTableHead.vue'
import SongTableFooter from './components/SongTableFooter.vue'

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
    width() {
      return this.$store.getters.getSongFieldsParamsWidth
    },
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: this.width+25+'px',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'lightblue',
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
      }
    }
  },
  methods: {
  }
}
</script>

<style scoped>

</style>