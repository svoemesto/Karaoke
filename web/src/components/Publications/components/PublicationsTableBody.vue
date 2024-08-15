<template>
  <div class="table-publications-body">
    <div class="body-center-column">
      <publications-table-head />
      <div class="tr-center-block" v-for="publication in publications" :key="publication.id" >
        <div class="tr-left-block" v-text="publication.publishDate"></div>
        <publications-table-body-td :publish="publication.publish10" :publish-text="publication.publish10text" />
        <publications-table-body-td :publish="publication.publish11" :publish-text="publication.publish11text" />
        <publications-table-body-td :publish="publication.publish12" :publish-text="publication.publish12text" />
        <publications-table-body-td :publish="publication.publish13" :publish-text="publication.publish13text" />
        <publications-table-body-td :publish="publication.publish14" :publish-text="publication.publish14text" />
        <publications-table-body-td :publish="publication.publish15" :publish-text="publication.publish15text" />
        <publications-table-body-td :publish="publication.publish16" :publish-text="publication.publish16text" />
        <publications-table-body-td :publish="publication.publish17" :publish-text="publication.publish17text" />
        <publications-table-body-td :publish="publication.publish18" :publish-text="publication.publish18text" />
        <publications-table-body-td :publish="publication.publish19" :publish-text="publication.publish19text" />
        <publications-table-body-td :publish="publication.publish20" :publish-text="publication.publish20text" />
        <publications-table-body-td :publish="publication.publish21" :publish-text="publication.publish21text" />
        <publications-table-body-td :publish="publication.publish22" :publish-text="publication.publish22text" />
        <publications-table-body-td :publish="publication.publish23" :publish-text="publication.publish23text" />
        <div class="tr-right-block" v-text="publication.publishDate"></div>
      </div>
      <publications-table-head />
    </div>
  </div>
</template>

<script>
import PublicationsTableBodyTd from "@/components/Publications/components/PublicationsTableBodyTd.vue";
import PublicationsTableHead from "@/components/Publications/components/PublicationsTableHead.vue";

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
  }
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