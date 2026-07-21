<template>
  <div class="propertiestable">
    <PropertiesTable />
  </div>
</template>

<script>
import PropertiesTable from '../components/Properties/PropertiesTable.vue'

/**
 * View-страница «Properties» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'PropertiesView',
  components: {
    PropertiesTable,
  },
}
</script>

<style scoped>
.propertiestable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
