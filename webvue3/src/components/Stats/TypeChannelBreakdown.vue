<template>
  <div class="breakdown-row">
    <div class="chart-card">
      <h6 class="chart-title">
        Типы событий <span class="hint">(клик по сегменту — детализация)</span>
      </h6>
      <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
      <apexchart
        v-else-if="typeSeries.length"
        type="donut"
        height="300"
        :options="typeOptions"
        :series="typeSeries"
      />
      <div v-else class="text-muted text-center py-4">Нет данных</div>
    </div>
    <div class="chart-card">
      <h6 class="chart-title">Каналы переходов</h6>
      <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
      <apexchart
        v-else-if="channelSeries[0].data.length"
        type="bar"
        height="300"
        :options="channelOptions"
        :series="channelSeries"
      />
      <div v-else class="text-muted text-center py-4">Нет данных</div>
    </div>
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'
import { typeLabel, typeColor } from './eventLabels'

/**
 * Компонент «Type Channel Breakdown».
 *
 * @see AGENTS.md
 */

export default {
  name: 'TypeChannelBreakdown',
  components: { BSpinner },
  props: {
    byType: { type: Array, default: () => [] },
    channels: { type: Array, default: () => [] },
    isLoading: { type: Boolean, default: false },
  },
  emits: ['select-type'],
  computed: {
    typeSeries() {
      return this.byType.map((i) => i.count)
    },
    typeOptions() {
      return {
        chart: {
          events: { dataPointSelection: (e, ctx, cfg) => this.onDonutClick(cfg.dataPointIndex) },
        },
        labels: this.byType.map((i) => typeLabel(i.name)),
        colors: this.byType.map((i) => typeColor(i.name)),
        legend: { position: 'bottom' },
        dataLabels: { enabled: true, formatter: (val) => `${val.toFixed(1)}%` },
        plotOptions: {
          pie: {
            donut: {
              labels: {
                show: true,
                total: {
                  show: true,
                  label: 'Всего',
                  formatter: (w) =>
                    w.globals.seriesTotals.reduce((a, b) => a + b, 0).toLocaleString('ru-RU'),
                },
              },
            },
          },
        },
        tooltip: { y: { formatter: (v) => v.toLocaleString('ru-RU') } },
      }
    },
    channelSeries() {
      return [{ name: 'Переходы', data: this.channels.map((i) => i.count) }]
    },
    channelOptions() {
      return {
        chart: { toolbar: { show: false } },
        colors: [
          '#4e79a7',
          '#f28e2b',
          '#e15759',
          '#76b7b2',
          '#59a14f',
          '#edc948',
          '#b07aa1',
          '#ff9da7',
        ],
        plotOptions: { bar: { horizontal: true, borderRadius: 3, distributed: true } },
        dataLabels: { enabled: true, formatter: (v) => v.toLocaleString('ru-RU') },
        xaxis: { categories: this.channels.map((i) => i.name) },
        legend: { show: false },
        grid: { borderColor: '#eee' },
        tooltip: { y: { formatter: (v) => v.toLocaleString('ru-RU') } },
      }
    },
  },
  methods: {
    onDonutClick(idx) {
      const item = this.byType[idx]
      if (item) this.$emit('select-type', item.name) // сырой event_type
    },
  },
}
</script>

<style scoped>
.breakdown-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
@media (max-width: 900px) {
  .breakdown-row {
    grid-template-columns: 1fr;
  }
}
.chart-card {
  background: #fff;
  border: 1px solid #e6e6e6;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.chart-title {
  margin: 0 0 8px;
  font-size: 0.95rem;
  font-weight: 600;
}
.hint {
  font-weight: 400;
  font-size: 0.75rem;
  color: #999;
}
</style>
