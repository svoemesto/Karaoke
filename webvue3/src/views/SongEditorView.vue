<template>
  <div class="songeditortable">
    <SongEditorTable />
  </div>
</template>

<script>
import SongEditorTable from '../components/SongEditor/SongEditorTable.vue'

/**
 * View-страница «Song Editor» — основной layout и data-fetching.
 *
 * @see docs/features/mlt-generator.md
 */
export default {
  name: 'SongEditorView',
  components: { SongEditorTable },
}
</script>

<style scoped>
.songeditortable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
