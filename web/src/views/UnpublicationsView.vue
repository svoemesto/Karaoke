<template>
  <div class="unpublications">
    <UnpublicationsTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import UnpublicationsTable from "@/components/Unpublications/UnpublicationsTable.vue";

export default {
  name: 'UnpublicationsView',
  components: {
    UnpublicationsTable
  }
}
</script>

<style scoped>

.unpublications {
  display: flex;
  flex-direction: column;
  margin: 10px;
  justify-content: start;
}
</style>