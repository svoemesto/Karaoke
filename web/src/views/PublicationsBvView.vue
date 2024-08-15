<template>
  <div class="publicationsbv">
    <PublicationsBvTable/>
  </div>
</template>

<script>
import PublicationsBvTable from '@/components/PublicationsBv/PublicationsBvTable.vue'
export default {
  name: "PublicationsBvView",
  components: {
    PublicationsBvTable
  }
}
</script>

<style scoped>

.publicationsbv {
  display: flex;
  flex-direction: column;
  margin: 10px;
  justify-content: start;
}

</style>