<template>
  <div class="chart-card">
    <div class="chart-head">
      <h6 class="chart-title">
        Детализация событий
        <span v-if="selectedType" class="filter-badge">
          {{ selectedTypeLabel }}
          <button class="reset-btn" title="Показать все" @click="$emit('clear-type')">×</button>
        </span>
      </h6>
    </div>
    <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
    <apexchart
      v-else-if="filtered.length"
      type="bar"
      :height="chartHeight"
      :options="options"
      :series="series"
    />
    <div v-else class="text-muted text-center py-4">Нет данных</div>
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'
import { typeLabel } from './eventLabels'

function colorForLabel(label) {
  if (label.startsWith('Плеер')) return '#59a14f'
  if (label.startsWith('Просмотр')) return '#4e79a7'
  if (label.startsWith('Соцсеть') || label.startsWith('Ссылка') || label.startsWith('Клик'))
    return '#f28e2b'
  if (label.startsWith('UI')) return '#af7aa1'
  if (label.startsWith('Видео')) return '#e15759'
  if (label.startsWith('Время')) return '#76b7b2'
  return '#9c9c9c'
}

/**
 * Компонент «Detail Breakdown».
 *
 * @see AGENTS.md
 */

export default {
  name: 'DetailBreakdown',
  components: { BSpinner },
  props: {
    items: { type: Array, default: () => [] },
    isLoading: { type: Boolean, default: false },
    selectedType: { type: String, default: '' }, // сырой event_type для drill-down
  },
  emits: ['clear-type'],
  computed: {
    selectedTypeLabel() {
      return typeLabel(this.selectedType)
    },
    filtered() {
      if (!this.selectedType) return this.items
      return this.items.filter((i) => i.eventType === this.selectedType)
    },
    chartHeight() {
      return Math.max(220, this.filtered.length * 26 + 40)
    },
    series() {
      return [{ name: 'События', data: this.filtered.map((i) => i.count) }]
    },
    options() {
      return {
        chart: { toolbar: { show: false } },
        colors: this.filtered.map((i) => colorForLabel(i.name)),
        plotOptions: {
          bar: { horizontal: true, borderRadius: 3, distributed: true, barHeight: '70%' },
        },
        dataLabels: {
          enabled: true,
          formatter: (v) => v.toLocaleString('ru-RU'),
          style: { fontSize: '11px' },
        },
        xaxis: { categories: this.filtered.map((i) => i.name) },
        yaxis: { labels: { style: { fontSize: '12px' } } },
        legend: { show: false },
        grid: { borderColor: '#eee' },
        tooltip: { y: { formatter: (v) => v.toLocaleString('ru-RU') } },
      }
    },
  },
}
</script>

<style scoped>
.chart-card {
  background: #fff;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.chart-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}
.filter-badge {
  margin-left: 8px;
  font-size: 0.78rem;
  font-weight: 500;
  background: #eef4ff;
  color: #2b5fb3;
  padding: 2px 8px;
  border-radius: 12px;
}
.reset-btn {
  border: none;
  background: none;
  color: #2b5fb3;
  font-weight: 700;
  cursor: pointer;
  padding: 0 0 0 4px;
  line-height: 1;
}
</style>
