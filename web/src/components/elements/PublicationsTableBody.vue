<template>
  <div class="table-publications-body">
    <div class="body-center-column">
      <publications-table-head />
      <div class="tr-center-block" v-for="publication in publications" :key="publication.id" >
        <div class="tr-left-block" v-text="publication.publishDate"></div>
        <publications-table-body-td :publish="publication.publish10" />
        <publications-table-body-td :publish="publication.publish11" />
        <publications-table-body-td :publish="publication.publish12" />
        <publications-table-body-td :publish="publication.publish13" />
        <publications-table-body-td :publish="publication.publish14" />
        <publications-table-body-td :publish="publication.publish15" />
        <publications-table-body-td :publish="publication.publish16" />
        <publications-table-body-td :publish="publication.publish17" />
        <publications-table-body-td :publish="publication.publish18" />
        <publications-table-body-td :publish="publication.publish19" />
        <publications-table-body-td :publish="publication.publish20" />
        <publications-table-body-td :publish="publication.publish21" />
        <publications-table-body-td :publish="publication.publish22" />
        <publications-table-body-td :publish="publication.publish23" />
        <div class="tr-right-block" v-text="publication.publishDate"></div>
      </div>
      <publications-table-head />
    </div>
  </div>
</template>

<script>
import PublicationsTableBodyTd from "@/components/elements/PublicationsTableBodyTd.vue";
import PublicationsTableHead from "@/components/elements/PublicationsTableHead.vue";

export default {
  name: "PublicationsTableBody",
  components: {
    PublicationsTableHead,
    PublicationsTableBodyTd
  },
  props: {
    publications: {
      type: Array,
      required: false,
      default: () => []
    }
  },
}
</script>

<style scoped>

.table-publications-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: row;
}
.body-center-column {
  width: auto;
  overflow-x: scroll;
  overflow-y: scroll;
}
.tr-left-block, .tr-right-block {
  font-size: x-small;
  min-width: 60px;
  max-width: 60px;
  min-height: 20px;
  max-height: 20px;
  border: thin black;
  border-style: solid solid none solid;
  padding: 2px 4px;
}


.tr-center-block {
  display: flex;
  flex-direction: row;
  width: 100%;
}
</style>