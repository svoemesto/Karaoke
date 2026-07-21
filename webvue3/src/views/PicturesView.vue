<template>
  <div class="picturestable">
    <PicturesTable />
  </div>
</template>

<script>
import PicturesTable from '../components/Pictures/PicturesTable.vue'

/**
 * View-страница «Pictures» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'PicturesView',
  components: {
    PicturesTable,
  },
}
</script>

<style scoped>
.picturestable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
