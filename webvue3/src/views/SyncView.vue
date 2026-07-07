<template>
  <div class="synctable">
    <SyncTable/>
  </div>
</template>

<script>
import SyncTable from '../components/Sync/SyncTable.vue'
export default {
  name: "SyncView",
  components: { SyncTable }
}
</script>

<style scoped>
.synctable {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 900px;
}
</style>
