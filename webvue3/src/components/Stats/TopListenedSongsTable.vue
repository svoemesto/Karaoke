<template>
  <div class="chart-card">
    <div class="chart-head">
      <h6 class="chart-title">Топ песен, которые реально слушают</h6>
      <select class="form-select form-select-sm w-auto" :value="pageSize" @change="$emit('page-size', Number($event.target.value))">
        <option :value="20">20</option>
        <option :value="50">50</option>
        <option :value="100">100</option>
      </select>
    </div>
    <p class="text-muted small mb-2 desc">
      Учитываются события <b>progress=75</b> и <b>ended</b>: песня реально дослушана до 75%. Колонка «%»
      — доля таких прослушиваний среди всех нажатий Play (чем выше — тем «доходимее» трек).
    </p>
    <div v-if="isLoading" class="text-center py-3"><BSpinner small /></div>
    <template v-else>
      <div class="table-responsive">
        <table class="table table-sm table-hover table-bordered listened-table">
          <thead class="table-dark">
            <tr>
              <th>ID</th>
              <th>Описание</th>
              <th title="Прослушано до 75% или до конца">Дослушано</th>
              <th title="Сколько уникальных посетителей дослушали до 75% или до конца (залогиненные + анонимные)">Уник.</th>
              <th title="Из них доиграли до конца (ended)">Из них 100%</th>
              <th title="Сколько раз нажали Play">Play</th>
              <th title="Доля дослушиваний от Play">%</th>
              <th title="Просмотры страницы песни">Сайт</th>
              <th title="Клики по ссылке MAX">MAX</th>
              <th title="Клики по ссылке Sponsr">Sponsr</th>
            </tr>
          </thead>
          <tbody>
            <tr
v-for="row in items" :key="row.songId" class="listened-row"
                title="Подробная статистика по песне" @click="$emit('select-song', row)">
              <td>{{ row.songId }}</td>
              <td class="text-start">{{ row.description }}</td>
              <td class="fw-bold">{{ row.listened.toLocaleString('ru-RU') }}</td>
              <td>{{ uniqListeners(row).toLocaleString('ru-RU') }}</td>
              <td>{{ row.ended.toLocaleString('ru-RU') }}</td>
              <td>{{ row.played.toLocaleString('ru-RU') }}</td>
              <td :class="rateClass(row)">{{ rateLabel(row) }}</td>
              <td>{{ row.cntSm.toLocaleString('ru-RU') }}</td>
              <td>{{ row.cntMax.toLocaleString('ru-RU') }}</td>
              <td>{{ row.cntSponsr.toLocaleString('ru-RU') }}</td>
            </tr>
            <tr v-if="!items.length">
              <td colspan="10" class="text-center text-muted">Нет песен с прослушиванием ≥75%</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="d-flex align-items-center gap-2">
        <b-pagination :model-value="page" :total-rows="totalCount" :per-page="pageSize" :limit="15" size="sm" pills @update:model-value="$emit('page', $event)" />
        <span class="text-muted small">Всего: {{ totalCount }}</span>
      </div>
    </template>
  </div>
</template>

<script>
import { BSpinner, BPagination } from 'bootstrap-vue-next'

export default {
  name: 'TopListenedSongsTable',
  components: { BSpinner, BPagination },
  props: {
    items: { type: Array, default: () => [] },
    totalCount: { type: Number, default: 0 },
    isLoading: { type: Boolean, default: false },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 50 },
  },
  emits: ['select-song', 'page', 'page-size'],
  methods: {
    // Уникальные посетители = сумма залогиненных и анонимных. Поля приходят с бэкенда раздельно,
    // потому что аноним (anon_id) и залогиненный (site_user_id) — разные сущности и считать их
    // общим distinct-ом в SQL опасно (anon_id может совпасть с id залогиненного пользователя).
    uniqListeners(row) {
      return (row.uniqUsers || 0) + (row.uniqAnon || 0)
    },
    // Доля дослушиваний — целое число процентов, чтобы легко сравнивать глазами.
    rateLabel(row) {
      const played = row.played || 0
      if (played <= 0) return '—'
      return Math.round((row.listened / played) * 100) + '%'
    },
    // Цвет ячейки «%»: зелёный — высокая доходимость (слушают), красный — низкая (бросают).
    // Пороги выбраны грубо: зелёный с 50% (половина дослушивает), жёлтый 25–50, красный ниже 25.
    rateClass(row) {
      const played = row.played || 0
      if (played <= 0) return ''
      const pct = (row.listened / played) * 100
      if (pct >= 50) return 'rate-good'
      if (pct >= 25) return 'rate-mid'
      return 'rate-bad'
    },
  },
}
</script>

<style scoped>
.chart-card {
  background: #fff; border: 1px solid #e6e6e6; border-radius: 8px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); margin-bottom: 16px;
}
.chart-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chart-title { margin: 0; font-size: 0.95rem; font-weight: 600; }
.desc { margin: 0 0 12px; }
.listened-table { font-size: 0.8rem; }
.listened-table th, .listened-table td { white-space: nowrap; vertical-align: middle; }
.listened-row { cursor: pointer; }
.listened-row:hover { background: #eef4ff; }
.rate-good { color: #2e7d32; font-weight: 600; }
.rate-mid  { color: #c9881a; }
.rate-bad  { color: #c62828; }
</style>
