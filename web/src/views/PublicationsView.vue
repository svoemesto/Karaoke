<template>
  <div class="publications">
    <PublicationsTable/>
  </div>
</template>

<script>
// @ is an alias to /src
import PublicationsTable from "@/components/PublicationsTable.vue";

export default {
  name: 'PublicationsView',
  components: {
    PublicationsTable
  }
}
</script>

<style scoped>

.publications {
  display: flex;
  flex-direction: column;
  margin: 10px;
  justify-content: start;
}
</style>
