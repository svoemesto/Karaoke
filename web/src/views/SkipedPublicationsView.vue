<template>
  <div class="unpublications">
    <UnpublicationsTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import SkipedPublicationsTable from "@/components/Unpublications/SkipedPublicationsTable.vue";

export default {
  name: 'SkipedPublicationsView',
  components: {
    SkipedPublicationsTable
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