<template>
  <div class="albumstable">
    <AlbumsTable />
  </div>
</template>

<script>
import AlbumsTable from '../components/Albums/AlbumsTable.vue'

/**
 * View-страница «Albums» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'AlbumsView',
  components: {
    AlbumsTable,
  },
}
</script>

<style scoped>
.albumstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
