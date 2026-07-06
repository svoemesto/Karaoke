<template>
  <div class="hrl-table-footer">
    <button type="button" class="hrl-table-header-button-repair-all" @click="repairAll" :disabled="healthReportListCanRepair.length === 0">Repair All ({{healthReportListCanRepair.length}})</button>
  </div>
</template>

<script>


export default {
  name: "HealthReportTableHeader",
  props: {
    healthReportList: {
      type: Array,
      required: true,
      defaults: []
    }
  },
  computed: {
    healthReportListCanRepair() {
      return this.healthReportList.filter(healthReport => healthReport.canResolve);
    }
  },
  methods: {
    repairAll() {
      if (this.healthReportList.length === 0) return;
      const settingsId = this.healthReportList[0].settingsId;
      this.$store.dispatch('repairAllPromise', settingsId);
    }
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