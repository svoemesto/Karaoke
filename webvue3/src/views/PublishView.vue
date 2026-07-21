<template>
  <div class="publish">
    <PublishTable />
  </div>
</template>

<script>
import PublishTable from '../components/Publish/PublishTable.vue'

/**
 * View-страница «Publish» — основной layout и data-fetching.
 *
 * @see docs/features/telegram-auto-publish.md
 */

export default {
  name: 'PublishView',
  components: { PublishTable },
}
</script>

<style scoped>
.publish {
  display: flex;
  flex-direction: column;
  margin: 10px;
  justify-content: start;
}
</style>
