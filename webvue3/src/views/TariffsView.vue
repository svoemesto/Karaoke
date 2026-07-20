<template>
  <div class="tariffsview">
    <TariffsTable />
  </div>
</template>

<script>
import TariffsTable from '../components/Tariffs/TariffsTable.vue'
export default {
  name: 'TariffsView',
  components: { TariffsTable },
}
</script>
