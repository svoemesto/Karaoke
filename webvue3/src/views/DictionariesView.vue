<template>
  <div class="dictionariesview">
    <DictionariesTable/>
  </div>
</template>

<script>
import DictionariesTable from '../components/Dictionaries/DictionariesTable.vue'
export default {
  name: "DictionariesView",
  components: {
    DictionariesTable
  }
}
</script>

<style scoped>

.dictionariesview {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>
