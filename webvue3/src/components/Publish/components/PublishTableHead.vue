<template>
  <div class="table-publish-head">
    <div
        v-for="csrCell in publishRow.csrCells"
        :key="csrCell.cscId"
        class="td-text"
        v-text="csrCell.cscName"
    ></div>
  </div>
</template>

<script>
export default {
  name: "PublishTableHead",
  props: {
    publishRow: {
      type: Array,
      required: false,
      default: () => []
    }
  }
}
</script>

<style scoped>

.table-publish-head {
  display: flex;
  flex-direction: row;
  margin-left: 60px;
}

.td-text {
  min-width: 200px;
  max-width: 200px;
  border: thin solid black;
  font-size: small;
}

</style>