<template>
  <div class="propertiestable">
    <PropertiesTable/>
  </div>
</template>

<script>
import PropertiesTable from '../components/Properties/PropertiesTable.vue'
export default {
  name: "PropertiesView",
  components: {
    PropertiesTable
  }
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