<template>
  <div class="processestable">
    <ProcessesTable/>
  </div>
</template>

<script>
import ProcessesTable from '../components/Processes/ProcessesTable.vue'
export default {
  name: "ProcessesView",
  components: {
    ProcessesTable
  }
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