<template>
  <div class="publications-root">
    <publications-table-body :publications="publications" />
    <publications-table-footer />
  </div>
</template>

<script>

import PublicationsTableBody from './elements/PublicationsTableBody.vue'
import PublicationsTableFooter from './elements/PublicationsTableFooter.vue'

export default {
  name: "PublicationsTable",
  components: {
    PublicationsTableBody,
    PublicationsTableFooter
  },
  computed: {
    publications() {
      return this.$store.getters.getPublications
    },
  }
}
</script>

<style scoped>

.publications-root {
  display: flex;
  flex-direction: column;
  width: calc(100vw - 50px);
  height: calc(100vh - 70px);
}
</style>