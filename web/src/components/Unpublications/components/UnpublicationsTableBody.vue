<template>
  <div class="table-publications-body">
    <div class="body-center-column">
      <unpublications-table-head :unpublications="publications[0]"/>
      <div class="tr-center-block" v-for="unpublication in publications" :key="unpublication.id" >
        <unpublications-table-body-td
            v-for="publication in unpublication" :key="publication.id"
            :publish="publication.publish10" :publish-text="publication.publish10text" />
      </div>
    </div>
  </div>
</template>

<script>
import UnpublicationsTableBodyTd from "@/components/Unpublications/components/UnpublicationsTableBodyTd.vue";
import UnpublicationsTableHead from "@/components/Unpublications/components/UnpublicationsTableHead.vue";

export default {
  name: "UnpublicationsTableBody",
  components: {
    UnpublicationsTableBodyTd,
    UnpublicationsTableHead
  },
  props: {
    publications: {
      type: Array,
      required: false,
      default: () => [[]]
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