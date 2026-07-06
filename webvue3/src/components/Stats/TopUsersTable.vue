<template>
  <div class="chart-card">
    <div class="chart-head">
      <h6 class="chart-title">Топ пользователей по активности</h6>
      <select class="form-select form-select-sm w-auto" :value="pageSize" @change="$emit('page-size', Number($event.target.value))">
        <option :value="20">20</option>
        <option :value="50">50</option>
        <option :value="100">100</option>
      </select>
    </div>
    <div v-if="isLoading" class="text-center py-3"><BSpinner small /></div>
    <template v-else>
      <table class="table table-sm table-hover table-bordered users-table">
        <thead class="table-dark">
          <tr>
            <th>ID</th>
            <th>Имя</th>
            <th>Email</th>
            <th>Премиум</th>
            <th title="Число событий">Событий</th>
            <th>Последняя активность</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(u, idx) in users" :key="rowKey(u, idx)" class="user-row"
              :class="{ 'anon-row': !u.siteUserId }" @click="$emit('select-user', u)">
            <td>{{ u.siteUserId || '—' }}</td>
            <td class="text-start">
              <span v-if="u.siteUserId">{{ u.displayName || '—' }}</span>
              <span v-else class="text-muted">Аноним {{ (u.anonId || '').slice(0, 8) }}…</span>
            </td>
            <td class="text-start">{{ u.email || '—' }}</td>
            <td>{{ u.premium ? '🪙' : '' }}</td>
            <td class="fw-bold">{{ u.eventCount.toLocaleString('ru-RU') }}</td>
            <td class="text-nowrap">{{ formatDate(u.lastActivity) }}</td>
          </tr>
          <tr v-if="!users.length"><td colspan="6" class="text-center text-muted">Нет пользователей с событиями</td></tr>
        </tbody>
      </table>
      <div class="d-flex align-items-center gap-2">
        <b-pagination :model-value="page" @update:model-value="$emit('page', $event)" :total-rows="totalCount" :per-page="pageSize" :limit="15" size="sm" pills />
        <span class="text-muted small">Всего: {{ totalCount }}</span>
      </div>
    </template>
  </div>
</template>

<script>
import { BSpinner, BPagination } from 'bootstrap-vue-next'

export default {
  name: 'TopUsersTable',
  components: { BSpinner, BPagination },
  props: {
    users: { type: Array, default: () => [] },
    totalCount: { type: Number, default: 0 },
    isLoading: { type: Boolean, default: false },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 50 },
  },
  emits: ['select-user', 'page', 'page-size'],
  methods: {
    rowKey(u, idx) { return u.siteUserId ? `u${u.siteUserId}` : `a${u.anonId || idx}` },
    formatDate(ts) {
      if (!ts) return '—'
      return new Date(ts).toLocaleString('ru-RU', { timeZone: 'Europe/Moscow' })
    }
  }
}
</script>

<style scoped>
.chart-card {
  background: #fff; border: 1px solid #e6e6e6; border-radius: 8px;
  padding: 12px 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); margin-bottom: 16px;
}
.chart-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.chart-title { margin: 0; font-size: 0.95rem; font-weight: 600; }
.users-table { font-size: 0.82rem; }
.users-table th, .users-table td { white-space: nowrap; vertical-align: middle; }
.user-row { cursor: pointer; }
.user-row:hover { background: #eef4ff; }
.anon-row { background: #fafafa; }
</style>
