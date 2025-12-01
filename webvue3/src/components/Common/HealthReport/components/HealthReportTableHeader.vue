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
      const lst = [...this.healthReportListCanRepair];
      for (const healthReport of lst) {
        this.$store.dispatch('repairOneRecord', healthReport);
      }
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