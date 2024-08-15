<template>
  <div class="table-publications-head">
    <div class="td-date" v-text="'Дата'"></div>
    <div class="td-time" v-text="'10:00'"></div>
    <div class="td-time" v-text="'11:00'"></div>
    <div class="td-time" v-text="'12:00'"></div>
    <div class="td-time" v-text="'13:00'"></div>
    <div class="td-time" v-text="'14:00'"></div>
    <div class="td-time" v-text="'15:00'"></div>
    <div class="td-time" v-text="'16:00'"></div>
    <div class="td-time" v-text="'17:00'"></div>
    <div class="td-time" v-text="'18:00'"></div>
    <div class="td-time" v-text="'19:00'"></div>
    <div class="td-time" v-text="'20:00'"></div>
    <div class="td-time" v-text="'21:00'"></div>
    <div class="td-time" v-text="'22:00'"></div>
    <div class="td-time" v-text="'23:00'"></div>
    <div class="td-date" v-text="'Дата'"></div>
  </div>
</template>

<script>
export default {
  name: "PublicationsTableHead"
}
</script>

<style scoped>

.table-publications-head {
  display: flex;
  flex-direction: row;
}

.td-date {
  min-width: 60px;
  max-width: 60px;
  border: thin solid black;
  font-size: small;
}
.td-time {
  min-width: 200px;
  max-width: 200px;
  border: thin solid black;
  font-size: small;
}
</style>