<template>
  <div class="authorstable">
    <AuthorsTable/>
  </div>
</template>

<script>
import AuthorsTable from '@/components/Authors/AuthorsTable.vue'
export default {
  name: "AuthorsView",
  components: {
    AuthorsTable
  }
}
</script>

<style scoped>

.table {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>