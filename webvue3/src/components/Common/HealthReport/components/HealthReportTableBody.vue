<template>
  <div class="hrl-table-body">
    <div v-if="isLoading" class="hrl-table-placeholder">Загрузка…</div>
    <div v-else-if="healthReportList.length === 0" class="hrl-table-placeholder">
      Записей Health Report нет — нарушений не найдено
    </div>
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
    // OpenProject #325: при нуле записей HR модалка остаётся открытой,
    // поэтому нужен явный индикатор загрузки/пустого списка.
    isLoading: {
      type: Boolean,
      default: false,
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

.hrl-table-placeholder {
  padding: 8px 12px;
  font-size: small;
  color: #666;
}
</style>
