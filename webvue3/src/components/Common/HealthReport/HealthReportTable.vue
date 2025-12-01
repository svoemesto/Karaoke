<template>
  <transition name="modal-fade">
    <div class="hrt-modal-backdrop">

      <div class="health-report-table-root">
        <HealthReportTableHeader :health-report-list="healthReportList"/>
        <HealthReportTableBody :health-report-list="healthReportList"/>
        <HealthReportTableFooter @close="close"/>
      </div>
    </div>
  </transition>

</template>

<script>

import HealthReportTableBody from "./components/HealthReportTableBody.vue";
import HealthReportTableFooter from "./components/HealthReportTableFooter.vue";
import HealthReportTableHeader from "./components/HealthReportTableHeader.vue";

export default {
  name: "HealthReportTable",
  components: {HealthReportTableHeader, HealthReportTableFooter, HealthReportTableBody},
  props: {
    id: {
      type: String,
      required: true
    }
  },
  watch: {
    healthReportList: {
      handler () {
        if (this.healthReportList.length === 0) {
          this.close();
        }
      }
    }
  },
  mounted() {
    this.$store.dispatch('loadHealthReportList', this.id);
  },
  computed: {
    healthReportList() {
      return this.$store.getters.getHealthReportList;
    }
  },
  methods: {
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.hrt-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.health-report-table-root {
  display: flex;
  flex-direction: column;
  background-color: white;
  max-width: calc(100vw - 185px);
  max-height: calc(100vh - 120px);
}

</style>