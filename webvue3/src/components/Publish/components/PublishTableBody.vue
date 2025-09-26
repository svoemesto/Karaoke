<template>
  <div class="table-publish-body">
    <div class="table-publish-body-center-column">
      <PublishTableHead :publish-row="publishDigest[0]" />
      <div
          class="tr-center-block"
          v-for="publishRow in publishDigest"
          :key="publishRow.csrId"
      >
        <div class="tr-left-block" v-text="publishRow.csrName"></div>
        <PublishTableBodyTd
            v-for="csrCell in publishRow.csrCells"
            :key="csrCell.cscId"
            :publish="csrCell.settingsDTO"
        />
        <div class="tr-right-block" v-text="publishRow.csrName"></div>
      </div>
      <PublishTableHead :publish-row="publishDigest[0]" />
    </div>

  </div>
</template>

<script>
import PublishTableHead from "../../../components/Publish/components/PublishTableHead.vue";
import PublishTableBodyTd from "../../../components/Publish/components/PublishTableBodyTd.vue";

export default {
  name: "PublishTableBody",
  components: {PublishTableBodyTd, PublishTableHead},
  props: {
    publishDigest: {
      type: Array,
      required: false,
      default: () => [[]]
    }
  }
}
</script>

<style scoped>

.table-publish-body {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: row;
}

.table-publish-body-center-column {
  width: auto;
  overflow-x: scroll;
  overflow-y: scroll;
}

.tr-center-block {
  display: flex;
  flex-direction: row;
  width: 100%;
}

.tr-left-block, .tr-right-block {
  font-size: x-small;
  min-width: 60px;
  max-width: 60px;
  min-height: 30px;
  max-height: 30px;
  border: thin black;
  border-style: solid solid none solid;
  padding: 2px 4px;
}
</style>