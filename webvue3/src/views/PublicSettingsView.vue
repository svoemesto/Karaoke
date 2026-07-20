<template>
  <div class="publicsettingsview">
    <PublicSettingsTable />
  </div>
</template>

<script>
import PublicSettingsTable from '../components/PublicSettings/PublicSettingsTable.vue'
export default {
  name: 'PublicSettingsView',
  components: {
    PublicSettingsTable,
  },
}
</script>

<style scoped>
.publicsettingsview {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}
</style>
