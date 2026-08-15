<template>
  <div class="hrl-table-body">
    <health-report-table-row
      v-for="healthReport in healthReportList"
      :key="healthReport.songId"
      :health-report="healthReport"
    />
  </div>
</template>

<script>
import HealthReportTableRow from './HealthReportTableRow.vue'

/**
 * Компонент «Health Report Table Body».
 *
 * @see archive/docs/features/monitoring.md
 */

export default {
  name: 'HealthReportTableBody',
  components: { HealthReportTableRow },
  props: {
    healthReportList: {
      type: Array,
      required: true,
      defaults: [],
    },
  },
  data() {
    return {}
  },
  computed: {
    healthReportListFields() {
      return [
        {
          key: 'description',
          label: 'Описание',
        },
      ]
    },
  },
  methods: {
    repair(item) {
      this.$store.dispatch('repairOneRecord', item)
    },
  },
}
</script>

<style scoped>
.hrl-table-body {
  width: fit-content;
}
</style>
