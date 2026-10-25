<template>
  <div class="hrl-table-body">
    <div v-if="isLoading" class="hrl-table-placeholder">Загрузка…</div>
    <!-- BR-381: пустой успешный ответ и ошибка — разные состояния. Раньше при 500/
         таймауте показывалось «нарушений не найдено», то есть ложное «всё хорошо»:
         админ не запускал ремонт. -->
    <div v-else-if="error" class="hrl-table-placeholder hrl-table-placeholder-error">
      Не удалось загрузить Health Report: {{ error }}
    </div>
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
    // BR-381: текст ошибки загрузки ('' — ошибки нет). Показывается вместо
    // «нарушений не найдено», чтобы отказ не выглядел как «проблем нет».
    error: {
      type: String,
      default: '',
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
      // BR-380: store отказывает в ремонте, если строка относится к другой песне
      // (fail-closed). Ловим отказ здесь, чтобы он не стал unhandled rejection.
      this.$store.dispatch('repairOneRecord', item).catch((error) => {
        // eslint-disable-next-line no-console
        console.error('Ремонт отменён:', error)
      })
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

.hrl-table-placeholder-error {
  color: #a10000;
  font-weight: 600;
}
</style>
