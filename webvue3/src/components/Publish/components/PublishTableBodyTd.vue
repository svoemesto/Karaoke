<template>
  <div class="td-wrapper">
    <SongEditModal
      v-if="isSongEditVisible"
      :parent-route="parentRoute"
      :publish-digest="publishDigest"
      @close="closeSongEdit"
    />
    <div v-if="publish" class="publish">
      <div
        class="publish-name"
        :style="styleSongName"
        :title="publishTitle"
        @click.left="editSong"
        v-text="publishText"
      />
    </div>
    <div v-else class="empty" />
  </div>
</template>

<script>
import SongEditModal from '../../../components/Songs/edit/SongEditModal.vue'

/**
 * Компонент «Publish Table Body Td».
 *
 * @see AGENTS.md
 */
export default {
  name: 'PublishTableBodyTd',
  components: {
    SongEditModal,
  },
  props: {
    publish: {
      type: Object,
      required: false,
      default: () => null,
    },
    publishDigest: {
      type: Array,
      required: false,
      default: () => [[]],
    },
  },
  data() {
    return {
      isSongEditVisible: false,
    }
  },
  computed: {
    parentRoute() {
      return 'Publications'
    },
    publishTitle() {
      return `${this.publish.songName} ★ ${this.publish.author} ★ ${this.publish.album}`
    },
    publishText() {
      return `${this.publish.firstSongInAlbum ? '★ ' : ''}${this.publish.songName}`
    },
    styleSongName() {
      return { backgroundColor: this.publish.color }
    },
  },
  methods: {
    editSong() {
      this.$store.dispatch('setCurrentSongId', this.publish.id)
      this.isSongEditVisible = true
    },
    closeSongEdit() {
      this.isSongEditVisible = false
    },
  },
}
</script>

<style scoped>
.td-wrapper {
}

.publish {
  display: flex;
  flex-direction: row;
  min-width: 210px;
  max-width: 210px;
  min-height: 20px;
  max-height: 20px;
  /*border: thin solid black;*/
  font-size: x-small;
}
.publish-name {
  display: block;
  width: 210px;
  text-align: left;
  border-color: black;
  border-width: thin thin 0 thin;
  border-style: solid solid none solid;
  overflow: hidden;
  white-space: nowrap;
  padding: 2px 4px;
  color: black;
  cursor: default;
}
.publish-name:hover {
  color: red;
  cursor: pointer;
}
.empty {
  font-size: 0;
  width: 210px;
  height: 20px;
  background-color: grey;
  border: thin black;
  border-style: dashed dashed none none;
}
</style>
