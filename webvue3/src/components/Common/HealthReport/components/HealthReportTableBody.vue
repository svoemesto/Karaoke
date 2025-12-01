<template>
  <div class="hrl-table-body">
    <health-report-table-row
        v-for="healthReport in healthReportList"
        :key="healthReport.settingsId"
        :health-report="healthReport" />
  </div>
</template>

<script>

import { BTable } from 'bootstrap-vue-next'
import HealthReportTableRow from "./HealthReportTableRow.vue";

export default {
  name: "HealthReportTableBody",
  components: {HealthReportTableRow, BTable },
  props: {
    healthReportList: {
      type: Array,
      required: true,
      defaults: []
    }
  },
  data() {
    return {}
  },
  computed: {
    healthReportListFields() {
      return [
        {
          key: 'description',
          label: 'Описание'
        }
      ]
    }
  },
  methods: {
    repair(item) {
      this.$store.dispatch('repairOneRecord', item);
    }
  },

}

</script>

<style scoped>

.hrl-table-body {
  width: fit-content;
}

</style>