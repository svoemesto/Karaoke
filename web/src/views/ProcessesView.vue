<template>
  <div class="processes">
    <ProcessesTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import ProcessesTable from '@/components/Processes/ProcessesTable.vue'

export default {
  name: 'SongView',
  components: {
    ProcessesTable
  }
}
</script>

<style scoped>

.processes {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>