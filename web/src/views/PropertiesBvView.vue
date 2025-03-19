<template>
  <div class="propertiesbv">
    <PropertiesBvTable/>
  </div>
</template>

<script>
import PropertiesBvTable from '@/components/PropertiesBv/PropertiesBvTable.vue'
export default {
  name: "PropertiesBvView",
  components: {
    PropertiesBvTable
  }
}
</script>

<style scoped>

.propertiesbv {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>