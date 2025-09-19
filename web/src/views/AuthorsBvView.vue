<template>
  <div class="authorsbv">
    <AuthorsBvTable/>
  </div>
</template>

<script>
import AuthorsBvTable from '@/components/AuthorsBv/AuthorsBvTable.vue'
export default {
  name: "AuthorsBvView",
  components: {
    AuthorsBvTable
  }
}
</script>

<style scoped>

.authorsbv {
  display: flex;
  flex-direction: column;
  margin: 10px auto;
  justify-content: center;
  align-items: center;
}

</style>