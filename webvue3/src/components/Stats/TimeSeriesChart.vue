<template>
  <div class="chart-card">
    <div class="chart-head">
      <h6 class="chart-title">Динамика событий по дням</h6>
      <select
        class="form-select form-select-sm w-auto"
        :value="mode"
        @change="$emit('change-mode', $event.target.value)"
      >
        <option value="all">Всего</option>
        <option value="type">По типам</option>
        <option value="detail">Детально</option>
      </select>
    </div>
    <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
    <apexchart v-else type="area" :height="chartHeight" :options="options" :series="series" />
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'
import { typeLabel, typeColor } from './eventLabels'

// Цвет линии детальной комбинации — по префиксу подписи (как в DetailBreakdown).
function colorForDetail(label) {
  if (label.startsWith('Плеер')) return '#59a14f'
  if (label.startsWith('Просмотр')) return '#4e79a7'
  if (label.startsWith('Соцсеть') || label.startsWith('Ссылка') || label.startsWith('Клик'))
    return '#f28e2b'
  if (label.startsWith('UI')) return '#af7aa1'
  if (label.startsWith('Видео')) return '#e15759'
  if (label.startsWith('Время')) return '#76b7b2'
  return '#9c9c9c'
}

export default {
  name: 'TimeSeriesChart',
  components: { BSpinner },
  props: {
    items: { type: Array, default: () => [] },
    mode: { type: String, default: 'all' },
    isLoading: { type: Boolean, default: false },
  },
  emits: ['change-mode'],
  computed: {
    split() {
      return this.mode !== 'all'
    },
    chartHeight() {
      return this.mode === 'detail' ? 380 : 300
    },
    categories() {
      return [...new Set(this.items.map((i) => i.date))].sort()
    },
    // Уникальные серии (значение поля eventType в items). Для 'type' это сырой event_type,
    // для 'detail' — человекочитаемая подпись комбинации.
    seriesKeys() {
      return [...new Set(this.items.map((i) => i.eventType))]
    },
    series() {
      const cats = this.categories
      if (!this.split) {
        const byDate = Object.fromEntries(this.items.map((i) => [i.date, i.count]))
        return [{ name: 'События', data: cats.map((d) => byDate[d] || 0) }]
      }
      return this.seriesKeys.map((k) => {
        const byDate = {}
        this.items
          .filter((i) => i.eventType === k)
          .forEach((i) => {
            byDate[i.date] = i.count
          })
        const name = this.mode === 'type' ? typeLabel(k) : k
        return { name, data: cats.map((d) => byDate[d] || 0) }
      })
    },
    colors() {
      if (!this.split) return ['#4e79a7']
      return this.seriesKeys.map((k) => (this.mode === 'type' ? typeColor(k) : colorForDetail(k)))
    },
    options() {
      return {
        chart: { toolbar: { show: true }, stacked: this.split, zoom: { enabled: true } },
        colors: this.colors,
        dataLabels: { enabled: false },
        stroke: { curve: 'smooth', width: 2 },
        fill: { type: 'gradient', gradient: { opacityFrom: 0.5, opacityTo: 0.05 } },
        xaxis: { categories: this.categories, type: 'category', tickAmount: 12 },
        yaxis: { labels: { formatter: (v) => Math.round(v).toLocaleString('ru-RU') } },
        legend: { show: this.split, position: 'top' },
        tooltip: { x: { show: true } },
        grid: { borderColor: '#eee' },
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
}
.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.chart-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}
</style>
