<template>
  <div class="processesbv">
    <ProcessesBvTable/>
  </div>
</template>

<script>
import ProcessesBvTable from '@/components/ProcessesBv/ProcessesBvTable.vue'
export default {
  name: "ProcessesBvView",
  components: {
    ProcessesBvTable
  }
}
</script>

<style scoped>

.processesbv {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>