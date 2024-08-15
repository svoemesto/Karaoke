<template>
  <div class="unpublications-root">
    <unpublications-table-body :publications="publications" />
    <unpublications-table-footer />
  </div>
</template>

<script>

import UnpublicationsTableBody from './components/UnpublicationsTableBody.vue'
import UnpublicationsTableFooter from './components/UnpublicationsTableFooter.vue'

export default {
  name: "UnpublicationsTable",
  components: {
    UnpublicationsTableBody,
    UnpublicationsTableFooter
  },
  computed: {
    publications() {
      return this.$store.getters.getUnpublications
    },
  }
}
</script>

<style scoped>

.unpublications-root {
  display: flex;
  flex-direction: column;
  width: calc(100vw - 50px);
  height: calc(100vh - 95px);
}
</style>