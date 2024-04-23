<template>
  <div class="td-wrapper">
    <div class="publish" v-if="publish">
      <div class="publish-name" :style="styleSongName" v-text="publish.songName"></div>
      <div class="publish-column">
        <div class="publish-column-cell-top" :style="processColorBoosty" ></div>
        <div class="publish-column-cell-bottom" :style="processColorVk" ></div>
      </div>
      <div class="publish-column">
        <div class="publish-column-cell-top" :style="processColorDzenLyrics" ></div>
        <div class="publish-column-cell-bottom" :style="processColorDzenKaraoke" ></div>
      </div>
      <div class="publish-column">
        <div class="publish-column-cell-top" :style="processColorVkLyrics" ></div>
        <div class="publish-column-cell-bottom" :style="processColorVkKaraoke" ></div>
      </div>
      <div class="publish-column">
        <div class="publish-column-cell-top" :style="processColorTelegramLyrics" ></div>
        <div class="publish-column-cell-bottom" :style="processColorTelegramKaraoke" ></div>
      </div>
      <div class="publish-column">
        <div class="publish-column-cell-top" :style="processColorMeltLyrics" ></div>
        <div class="publish-column-cell-bottom" :style="processColorMeltKaraoke" ></div>
      </div>
    </div>
    <div class="empty" v-else></div>
  </div>
</template>

<script>
export default {
  name: "PublicationsTableBodyTd",
  props: {
    publish: {
      type: Object,
      required: false,
      default: () => null
    }
  },
  computed: {
    styleSongName() { return { backgroundColor: this.publish.color } },
    processColorBoosty() { return { backgroundColor: this.publish.processColorBoosty } },
    processColorVk() { return { backgroundColor: this.publish.processColorVk } },
    processColorVkLyrics() { return { backgroundColor: this.publish.processColorVkLyrics } },
    processColorVkKaraoke() { return { backgroundColor: this.publish.processColorVkKaraoke } },
    processColorDzenLyrics() { return { backgroundColor: this.publish.processColorDzenLyrics } },
    processColorDzenKaraoke() { return { backgroundColor: this.publish.processColorDzenKaraoke } },
    processColorMeltLyrics() { return { backgroundColor: this.publish.processColorMeltLyrics } },
    processColorMeltKaraoke() { return { backgroundColor: this.publish.processColorMeltKaraoke } },
    processColorTelegramLyrics() { return { backgroundColor: this.publish.processColorTelegramLyrics } },
    processColorTelegramKaraoke() { return { backgroundColor: this.publish.processColorTelegramKaraoke } },
  },
  methods: {

  }
}
</script>

<style scoped>

.td-wrapper {
  min-width: 200px;
  max-width: 200px;
  min-height: 20px;
  max-height: 20px;
  /*border: thin solid black;*/
  font-size: x-small;
}
.publish {
  display: flex;
  flex-direction: row;
}
.publish-name {
  display: block;
  width: 150px;
  text-align: left;
  border-width: thin thin 0 thin;
  border-style: solid solid none solid;
  overflow: hidden;
  white-space: nowrap;
  padding: 2px 4px;
}
.publish-column {
  font-size: 0;
  display: flex;
  flex-direction: column;
  width: 10px;
  height: 20px;
  border-width: thin thin 0 0;
  border-style: solid solid none none;
  background-color: transparent;
}
.publish-column-cell-top {
  font-size: 0;
  display: block;
  width: 10px;
  height: 10px;
  border-width: 0 thin thin 0;
  border-style: none solid solid none;
}
.publish-column-cell-bottom {
  font-size: 0;
  display: block;
  width: 10px;
  height: 10px;
  border-width: 0 thin 0 0;
  border-style: none solid none none;
}
.empty {
  font-size: 0;
  width: 200px;
  height: 20px;
  background-color: grey;
  border: thin black;
  border-style: dashed dashed none none
}
</style>