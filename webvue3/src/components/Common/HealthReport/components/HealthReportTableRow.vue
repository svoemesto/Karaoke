<template>

    <div class="hrl-row">
      <div class="hrl-fld-caption" v-text="caption" :style="{backgroundColor: healthReport.color}"></div>
      <div class="hrl-subrow">
        <div class="hrl-column">
          <div class="hrl-label-and-field">
            <div class="hrl-label">Проблема:</div>
            <div class="hrl-fld-problemText" v-text="healthReport.problemText"></div>
          </div>
          <div class="hrl-label-and-field">
            <div class="hrl-label">Решение:</div>
            <div class="hrl-fld-solutionText" v-text="healthReport.solutionText"></div>
          </div>
        </div>
        <div class="hrl-column">
          <button :disabled="!healthReport.canResolve" class="hrl-row-button-repair" @click.left="repair(healthReport)" v-text="'repair'"></button>
        </div>
      </div>
    </div>

</template>

<script>

export default {
  name: "HealthReportTableRow",
  props: {
    healthReport: {
      type: Object,
      required: true
    }
  },
  data() {
    return {}
  },
  computed: {
    caption() {
      return this.healthReport.healthReportTypeName + ' / ' + this.healthReport.healthReportStatusName + ' / ' + this.healthReport.description;
    },
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

.hrl-fld-caption {
  max-width: 650px;
  min-width: 650px;
  text-align: center;
  font-size: small;
  font-weight: bold;
  cursor: default;
  text-decoration: none;
}

.hrl-label-and-field {
  display: flex;
  flex-direction: row;
}
.hrl-label {
  max-width: 75px;
  min-width: 75px;
  text-align: right;
  padding: 0 5px;
  font-size: small;
  font-weight: bold;
}

.hrl-row {
  display: flex;
  flex-direction: column;
  align-items: start;
}
.hrl-subrow {
  display: flex;
  flex-direction: row;
  align-items: start;
}
.hrl-column {
  display: flex;
  flex-direction: column;
  align-items: start;
}
.hrl-row-button-repair {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
}
.hrl-fld-problemText {
  min-width: 525px;
  max-width: 525px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: break-spaces;
}
.hrl-fld-solutionText {
  min-width: 525px;
  max-width: 525px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: break-spaces;
}

</style>