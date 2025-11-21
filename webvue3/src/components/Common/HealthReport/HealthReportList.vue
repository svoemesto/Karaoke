<template>
  <div class="hrl-table-body">
    <b-table
        :items="healthReportList"
        :fields="healthReportListFields"
        small
        bordered
        hover
    >
      <template #cell(description)="data">
        <div class="hrl-row">
          <div class="hrl-fld-description" v-text="data.value"></div>
          <button :disabled="data.item.healthReportStatusName != 'ERROR'" class="hrl-row-button-repair" @click.left="repair(data.item)" v-text="'repair'"></button>
        </div>
      </template>
    </b-table>
  </div>
</template>

<script>

import { BTable } from 'bootstrap-vue-next'

export default {
  name: "HealthReportList",
  components: { BTable },
  props: {
    id: {
      type: String,
      required: true
    }
  },
  data() {
    return {}
  },
  mounted() {
    this.$store.dispatch('loadHealthReportList', this.id);
  },
  computed: {
    healthReportList() {
      return this.$store.getters.getHealthReportList;
    },
    healthReportListFields() {
      return [
        {
          key: 'description',
          label: 'Описание'
        },
        // {
        //   key: 'historyArgs',
        //   label: 'Аргументы'
        // }
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

.hrl-fld-description {
  min-width: 1000px;
  max-width: 1000px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}

.hrl-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.hrl-row-button-repair {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}

</style>