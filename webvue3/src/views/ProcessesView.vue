<template>
  <div class="processestable">
    <ProcessesTable />
  </div>
</template>

<script>
import ProcessesTable from '../components/Processes/ProcessesTable.vue'

/**
 * View-страница «Processes» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */
export default {
  name: 'ProcessesView',
  components: {
    ProcessesTable,
  },
}
</script>

<style scoped>
.processestable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
