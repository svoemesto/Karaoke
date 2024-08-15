<template>
  <div class="table-unpublications-head">
    <button class="button-date-group" @click="reload">Обновить</button>
  </div>
</template>

<script>
export default {
  name: "UnpublicationsTableFooter",
  methods: {
    reload() {
      this.$store.dispatch('loadUnpublications')
    }
  }
}
</script>

<style scoped>

.table-unpublications-head {
  display: flex;
  flex-direction: row;
}
.button-date-group {
  border: solid 1px black;
  border-radius: 6px;
  width: 150px;
  height: 30px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.button-date-group:hover {
  background-color: lightpink;
}
.button-date-group:focus {
  background-color: darksalmon;
}
.button-date-group[disabled] {
  background-color: lightgray;
}

</style>