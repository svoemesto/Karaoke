<template>
  <div class="table-publications-head">
    <input type="text" v-model="dateFrom">
    <button class="button-date-group" @click="clickButtonDateGroup('all')">С начала</button>
    <button class="button-date-group" @click="clickButtonDateGroup('fromtoday')">С сегодня</button>
    <button class="button-date-group" @click="clickButtonDateGroup('fromnotpublish')">С незавершенной</button>
    <button class="button-date-group" @click="clickButtonDateGroup('fromnotcheck')">С непроверенной</button>
    <button class="button-date-group" @click="clickButtonDateGroup('fromnotdone')">С неготовой</button>
    <button disabled="disabled" class="button-date-group" @click="clickButtonDateGroup('')">Минус месяц</button>
    <button disabled="disabled" class="button-date-group" @click="clickButtonDateGroup('')">Плюс месяц</button>
  </div>
</template>

<script>
export default {
  name: "PublicationsTableFooter",
  data() {
    return {
      dateFrom: ''
    }
  },
  watch: {
    dateFrom: {
      handler() {
        console.log(this.dateFrom)
      }
    }
  },
  methods: {
    clickButtonDateGroup(param) {
      this.$store.dispatch('loadPublications', { filterCond: param})
    }
  }
}
</script>

<style scoped>

.table-publications-head {
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