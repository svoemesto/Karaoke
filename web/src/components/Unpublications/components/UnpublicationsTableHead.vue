<template>
  <div class="table-unpublications-head">
    <div v-for="publication in unpublications" :key="publication.id" class="td-text" v-text="publication.publish10.author"></div>
  </div>
</template>

<script>
export default {
  name: "UnpublicationsTableHead",
  props: {
    unpublications: {
      type: Array,
      required: false,
      default: () => []
    }
  },
}
</script>

<style scoped>

.table-unpublications-head {
  display: flex;
  flex-direction: row;
}

.td-text {
  min-width: 200px;
  max-width: 200px;
  border: thin solid black;
  font-size: small;
}
</style>