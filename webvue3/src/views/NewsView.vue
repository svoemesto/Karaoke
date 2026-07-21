<template>
  <NewsTable />
</template>

<script>
import NewsTable from '../components/News/NewsTable.vue'

/**
 * View-страница «News» — основной layout и data-fetching.
 *
 * @see docs/features/dual-db-sync.md
 */

export default {
  name: 'NewsView',
  components: { NewsTable },
}
</script>
