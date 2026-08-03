<template>
  <div class="hrl-table-footer">
    <button
      type="button"
      class="hrl-table-header-button-repair-all"
      :disabled="healthReportListCanRepair.length === 0"
      @click="repairAll"
    >
      Repair All ({{ healthReportListCanRepair.length }})
    </button>
  </div>
</template>

<script>
/**
 * Компонент «Health Report Table Header».
 *
 * @see AGENTS.md
 */
export default {
  name: 'HealthReportTableHeader',
  props: {
    healthReportList: {
      type: Array,
      required: true,
      defaults: [],
    },
  },
  computed: {
    healthReportListCanRepair() {
      return this.healthReportList.filter((healthReport) => healthReport.canResolve)
    },
  },
  methods: {
    repairAll() {
      if (this.healthReportList.length === 0) return
      const songId = this.healthReportList[0].songId
      this.$store.dispatch('repairAllPromise', songId)
    },
  },
}
</script>

<style scoped>
.hrl-table-footer {
  width: fit-content;
}

.hrl-table-header-button-repair-all {
  min-width: 140px;
  max-width: 140px;
  text-align: center;
  padding: 0 3px;
  font-size: small;
}
</style>
