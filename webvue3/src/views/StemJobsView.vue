<template>
  <div class="stemjobstable">
    <StemJobsTable />
  </div>
</template>

<script>
import StemJobsTable from '../components/StemJobs/StemJobsTable.vue'
export default {
  name: 'StemJobsView',
  components: {
    StemJobsTable,
  },
}
</script>

<style scoped>
.stemjobstable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
