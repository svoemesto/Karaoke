<template>
  <div class="geo-row">
    <!-- География по странам -->
    <div class="chart-card">
      <div class="chart-head"><h6 class="chart-title">География (по странам)</h6></div>
      <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
      <apexchart v-else-if="countries.length" type="bar" :height="countryHeight" :options="countryOptions" :series="countrySeries" />
      <div v-else class="text-muted text-center py-4">Нет данных</div>
    </div>

    <!-- Топ внешних источников (referer) -->
    <div class="chart-card">
      <div class="chart-head"><h6 class="chart-title">Топ внешних источников</h6></div>
      <div v-if="isLoading" class="text-center py-4"><BSpinner small /></div>
      <template v-else-if="referrers.length">
        <table class="table table-sm table-hover ref-table mb-0">
          <thead>
            <tr><th class="text-start">Страница-источник</th><th class="text-end">Переходов</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, idx) in referrers" :key="idx">
              <td class="text-start ref-url">
                <a :href="r.name" target="_blank" rel="noopener noreferrer" :title="r.name">{{ r.name }}</a>
              </td>
              <td class="text-end fw-bold">{{ r.count.toLocaleString('ru-RU') }}</td>
            </tr>
          </tbody>
        </table>
      </template>
      <div v-else class="text-muted text-center py-4">Пока нет заходов по внешним ссылкам</div>
    </div>
  </div>
</template>

<script>
import { BSpinner } from 'bootstrap-vue-next'

export default {
  name: 'GeoReferrers',
  components: { BSpinner },
  props: {
    countries: { type: Array, default: () => [] },
    referrers: { type: Array, default: () => [] },
    isLoading: { type: Boolean, default: false },
  },
  computed: {
    countryHeight() { return Math.max(220, this.countries.length * 26 + 40) },
    countrySeries() {
      return [{ name: 'Посетители', data: this.countries.map(c => c.count) }]
    },
    countryOptions() {
      return {
        chart: { toolbar: { show: false } },
        colors: ['#4e79a7'],
        plotOptions: { bar: { horizontal: true, borderRadius: 3, barHeight: '70%' } },
        dataLabels: { enabled: true, formatter: v => v.toLocaleString('ru-RU'), style: { fontSize: '11px' } },
        xaxis: { categories: this.countries.map(c => c.name) },
        yaxis: { labels: { style: { fontSize: '12px' } } },
        grid: { borderColor: '#eee' },
        tooltip: { y: { formatter: v => v.toLocaleString('ru-RU') } },
      }
    }
  }
}
</script>

<style scoped>
.geo-row {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px;
}
@media (max-width: 900px) { .geo-row { grid-template-columns: 1fr; } }
.chart-card {
  background: #fff; border: 1px solid #e6e6e6; border-radius: 8px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.chart-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chart-title { margin: 0; font-size: 0.95rem; font-weight: 600; }
.ref-table { font-size: 0.8rem; }
.ref-table thead th { font-weight: 600; border-bottom: 2px solid #dee2e6; }
.ref-url { max-width: 340px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ref-url a { color: #2b5fb3; text-decoration: none; }
.ref-url a:hover { text-decoration: underline; }
</style>
